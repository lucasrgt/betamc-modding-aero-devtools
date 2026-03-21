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
BIN_DIR="mcp/minecraft/bin"
BIN_SRC="$BIN_DIR/net/minecraft/src"
LIBS_DIR="mcp/libraries"
CHANGED_DIR="$BASE/temp/.changed_classes"
HASH_FILE="$BASE/temp/.src_hashes"
LIBS="$WORKSPACE/libraries"

mkdir -p "$(dirname "$HASH_FILE")"

# 1. Find changed source files (mod + libraries) by comparing with cached hashes
echo "=== Detecting changes ==="
CHANGED_SRC=""
COUNT=0

# Scan mod sources
find src/ -name "*.java" 2>/dev/null | while read -r f; do
    echo "$f"
done > "$HASH_FILE.files"

# Scan library sources (excluding devtools/tools and devtools/scripts)
find "$LIBS" -name "*.java" -not -path "*/tools/*" -not -path "*/scripts/*" 2>/dev/null >> "$HASH_FILE.files"

# Hash current sources
md5sum $(cat "$HASH_FILE.files") 2>/dev/null | sort > "$HASH_FILE.current"

# Compare with previous
if [ -f "$HASH_FILE.previous" ]; then
    CHANGED_SRC=$(diff "$HASH_FILE.previous" "$HASH_FILE.current" 2>/dev/null | grep "^>" | sed 's/^> [a-f0-9]* *//' || true)
    COUNT=$(echo "$CHANGED_SRC" | grep -c "." 2>/dev/null || echo 0)
else
    # First run — transpile everything
    CHANGED_SRC="ALL"
    COUNT=999
fi

# Save for next comparison
cp "$HASH_FILE.current" "$HASH_FILE.previous"

if [ "$COUNT" = "0" ] || [ -z "$CHANGED_SRC" ]; then
    echo "=== No source changes ==="
    exit 0
fi

# 2. Transpile only changed files (or all if first run)
if [ "$CHANGED_SRC" = "ALL" ]; then
    echo "=== First run — full transpile ==="
    bash scripts/transpile.sh
else
    echo "=== Transpiling $COUNT changed files ==="
    for src_file in $CHANGED_SRC; do
        basename_f=$(basename "$src_file")
        # Determine package and strip it
        if echo "$src_file" | grep -q "^src/"; then
            # Mod source: strip package, convert imports
            sed \
                -e 's/^package betaenergistics\(\.[a-z_]*\)\?;/package net.minecraft.src;/' \
                -e '/^import betaenergistics\./d' \
                -e '/^import static betaenergistics\./d' \
                -e '/^import net\.minecraft\.src\.\*;/d' \
                "$src_file" > "$DEST/$basename_f"
        elif echo "$src_file" | grep -q "libraries/"; then
            # Library source: strip aero package
            sed \
                -e 's/^package aero\.\([a-z_]*\);/package net.minecraft.src;/' \
                -e '/^import aero\./d' \
                -e '/^import static aero\./d' \
                -e '/^import net\.minecraft\.src\.\*;/d' \
                "$src_file" > "$DEST/$basename_f"
        fi
    done
fi

# 3. Compile only the changed transpiled files
echo "=== Compiling $COUNT files ==="
JAVA_FILES=""
if [ "$CHANGED_SRC" = "ALL" ]; then
    # First run: compile all mod classes
    JAVA_FILES=$(find "$DEST" -name "BE_*.java" -o -name "mod_*.java" -o -name "Aero_*.java" | tr '\n' ' ')
else
    for src_file in $CHANGED_SRC; do
        basename_f=$(basename "$src_file")
        if [ -f "$DEST/$basename_f" ]; then
            JAVA_FILES="$JAVA_FILES $DEST/$basename_f"
        fi
    done
fi

# Build classpath
CP="$BIN_DIR"
for jar in $(find "$LIBS_DIR" -name "*.jar" 2>/dev/null); do
    CP="$CP;$jar"
done
[ -f "tests/data/minecraft_run.jar" ] && CP="$CP;tests/data/minecraft_run.jar"

javac -encoding UTF-8 -source 1.6 -target 1.6 -cp "$CP" -d "$BIN_DIR" $JAVA_FILES 2>&1

# 4. Collect changed .class files
rm -rf "$CHANGED_DIR"
mkdir -p "$CHANGED_DIR"
for jf in $JAVA_FILES; do
    bname=$(basename "$jf" .java)
    for cf in "$BIN_SRC/$bname.class" "$BIN_SRC/${bname}\$"*.class; do
        [ -f "$cf" ] && cp -- "$cf" "$CHANGED_DIR/" 2>/dev/null || true
    done
done

SWAP_COUNT=$(find "$CHANGED_DIR" -name "*.class" 2>/dev/null | wc -l)
echo "=== OK — $SWAP_COUNT classes to swap ==="
