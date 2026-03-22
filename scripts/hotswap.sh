#!/bin/bash
# Aero DevTools — Ultra-fast hot swap
# Only transpiles + compiles files that ACTUALLY changed in src/
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONFIG="$WORKSPACE/devtools.json"

if [ ! -f "$CONFIG" ]; then
    echo "devtools.json not found"; exit 1
fi

MOD_PATH=$(grep '"modPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
JDK_PATH=$(grep '"jdkPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
BASE="$WORKSPACE/$MOD_PATH"

if [ -n "$JDK_PATH" ] && [ -f "$JDK_PATH/bin/java" ]; then
    export JAVA_HOME="$JDK_PATH"
    export PATH="$JDK_PATH/bin:$PATH"
fi

cd "$BASE"

DEST="mcp/minecraft/src/net/minecraft/src"
REOBF="mcp/minecraft/reobf"
CHANGED_DIR="$BASE/temp/.changed_classes"
HASH_DIR="$BASE/temp/.hotswap_cache"
HASH_FILE="$HASH_DIR/src_hashes"
LIBS="$WORKSPACE/libraries"

mkdir -p "$HASH_DIR"

# 1. Find changed source files (mod + libraries) by comparing with cached hashes
echo "=== Detecting changes ==="

# Collect all source file paths
{
    find src/ -name "*.java" 2>/dev/null
    find "$LIBS" -name "*.java" -not -path "*/tools/*" -not -path "*/scripts/*" 2>/dev/null
} > "$HASH_FILE.files"

# Hash current sources
md5sum $(cat "$HASH_FILE.files") 2>/dev/null | sort > "$HASH_FILE.current"

# Compare with previous
CHANGED_SRC=""
if [ -f "$HASH_FILE.previous" ]; then
    CHANGED_SRC=$(diff "$HASH_FILE.previous" "$HASH_FILE.current" 2>/dev/null | grep "^>" | sed 's/^> [a-f0-9]* *//' || true)
else
    # First run — transpile everything
    CHANGED_SRC="ALL"
fi

# Save for next comparison
cp "$HASH_FILE.current" "$HASH_FILE.previous"

if [ -z "$CHANGED_SRC" ]; then
    echo "=== No source changes ==="
    exit 0
fi

# 2. Transpile only changed files (or all if first run)
if [ "$CHANGED_SRC" = "ALL" ]; then
    echo "=== First run — full transpile ==="
    bash scripts/transpile.sh
    # Collect all class names for swap
    CHANGED_CLASSES=$(find "$REOBF" -name "*.class" -exec basename {} \; 2>/dev/null | sed 's/\.class$//')
else
    COUNT=$(echo "$CHANGED_SRC" | wc -l)
    echo "=== Transpiling $COUNT changed files ==="
    CHANGED_CLASSES=""
    for src_file in $CHANGED_SRC; do
        basename_f=$(basename "$src_file")
        classname="${basename_f%.java}"
        if echo "$src_file" | grep -q "^src/"; then
            sed \
                -e 's/^package betaenergistics\(\.[a-z_]*\)\?;/package net.minecraft.src;/' \
                -e '/^import betaenergistics\./d' \
                -e '/^import static betaenergistics\./d' \
                -e '/^import net\.minecraft\.src\.\*;/d' \
                "$src_file" > "$DEST/$basename_f"
        elif echo "$src_file" | grep -q "libraries/"; then
            sed \
                -e 's/^package aero\.\([a-z_]*\);/package net.minecraft.src;/' \
                -e '/^import aero\./d' \
                -e '/^import static aero\./d' \
                -e '/^import net\.minecraft\.src\.\*;/d' \
                "$src_file" > "$DEST/$basename_f"
        fi
        CHANGED_CLASSES="$CHANGED_CLASSES $classname"
    done
fi

# 3. Build (obfuscate) — produces reobf/ classes matching runtime jar
echo "=== Building ==="
cd "$BASE/mcp"
echo "build" | java -jar RetroMCP-Java-CLI.jar
cd "$BASE"

# 4. Collect classes to swap — match by name from changed sources
rm -rf "$CHANGED_DIR"
mkdir -p "$CHANGED_DIR"
for classname in $CHANGED_CLASSES; do
    # Copy the class + any inner classes (e.g. Foo$1.class, Foo$Bar.class)
    for f in "$REOBF"/${classname}.class "$REOBF"/${classname}\$*.class; do
        [ -f "$f" ] && cp "$f" "$CHANGED_DIR/"
    done
done

SWAP_COUNT=$(find "$CHANGED_DIR" -name "*.class" 2>/dev/null | wc -l)
echo "=== OK — $SWAP_COUNT classes to swap ==="
