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
    done
fi

# 3. Build (obfuscate) — produces reobf/ classes matching runtime jar
echo "=== Building ==="
cd "$BASE/mcp"
echo "build" | java -jar RetroMCP-Java-CLI.jar
cd "$BASE"

# 4. Collect changed .class files from reobf/
REOBF="mcp/minecraft/reobf"
rm -rf "$CHANGED_DIR"
mkdir -p "$CHANGED_DIR"
if [ -f "$HASH_DIR/reobf_before.txt" ]; then
    md5sum "$REOBF"/*.class 2>/dev/null | sort > "$HASH_DIR/reobf_after.txt"
    while IFS=' ' read -r new_hash filepath; do
        bname=$(basename "$filepath")
        old_hash=$(grep "$bname" "$HASH_DIR/reobf_before.txt" 2>/dev/null | awk '{print $1}')
        if [ "$new_hash" != "$old_hash" ]; then
            cp -- "$filepath" "$CHANGED_DIR/" 2>/dev/null || true
        fi
    done < "$HASH_DIR/reobf_after.txt"
else
    # First run — copy all
    cp "$REOBF"/*.class "$CHANGED_DIR/" 2>/dev/null || true
fi
# Save reobf hashes for next run
md5sum "$REOBF"/*.class 2>/dev/null | sort > "$HASH_DIR/reobf_before.txt"

SWAP_COUNT=$(find "$CHANGED_DIR" -name "*.class" 2>/dev/null | wc -l)
echo "=== OK — $SWAP_COUNT classes to swap ==="
