#!/bin/bash
# Aero DevTools — Hot swap (transpile + recompile to bin/)
# Called by F6 overlay or manually. Reads devtools.json.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONFIG="$WORKSPACE/devtools.json"

if [ ! -f "$CONFIG" ]; then
    echo "devtools.json not found"
    exit 1
fi

MOD_PATH=$(grep '"modPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
JDK_PATH=$(grep '"jdkPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
BASE="$WORKSPACE/$MOD_PATH"

if [ -n "$JDK_PATH" ] && [ -f "$JDK_PATH/bin/java" ]; then
    export JAVA_HOME="$JDK_PATH"
    export PATH="$JDK_PATH/bin:$PATH"
fi

cd "$BASE"

# Create marker BEFORE recompile so we can find only changed classes
touch mcp/minecraft/bin/.hotswap_marker
sleep 1

echo "=== Transpiling ==="
bash scripts/transpile.sh

echo "=== Recompiling (to bin/, no obfuscation) ==="
cd "$BASE/mcp"
echo "recompile" | java -jar RetroMCP-Java-CLI.jar
cd "$BASE"

echo "=== Recompile OK ==="
