#!/bin/bash
# Aero DevTools — Debug loop
# Reads devtools.json from workspace root. No mod-specific files needed.
set -e

# Find workspace root (where devtools.json lives)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE="$(cd "$SCRIPT_DIR/../../.." && pwd)"
CONFIG="$WORKSPACE/devtools.json"

if [ ! -f "$CONFIG" ]; then
    echo "[Aero] devtools.json not found. Run setup first:"
    echo "  cd $WORKSPACE/libraries/devtools/tools && bash setup.sh"
    exit 1
fi

# Parse devtools.json
MOD_PATH=$(grep '"modPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
JDK_PATH=$(grep '"jdkPath"' "$CONFIG" | sed 's/.*: *"\(.*\)".*/\1/')
JDWP_PORT=$(grep '"jdwpPort"' "$CONFIG" | sed 's/.*: *\([0-9]*\).*/\1/')
HAS_DCEVM=$(grep '"dcevm"' "$CONFIG" | sed 's/.*: *\(.*\)/\1/' | tr -d ', ')

BASE="$WORKSPACE/$MOD_PATH"
if [ ! -d "$BASE" ]; then
    echo "[Aero] Mod not found: $BASE"
    exit 1
fi

# Setup JDK
if [ -n "$JDK_PATH" ] && [ -f "$JDK_PATH/bin/java" ]; then
    export JAVA_HOME="$JDK_PATH"
    export PATH="$JDK_PATH/bin:$PATH"
fi

echo ""
echo -e "\033[1;32m     ___    __________  ____ "
echo -e "    /   |  / ____/ __ \\/ __ \\"
echo -e "   / /| | / __/ / /_/ / / / /"
echo -e "  / ___ |/ /___/ _, _/ /_/ / "
echo -e " /_/  |_/_____/_/ |_|\\____/  \033[0;36mDevTools\033[0m"
echo ""
echo -e "  \033[0;33mF6\033[0m Hot Swap  \033[0;33mF7\033[0m Restart  \033[0;33mF9\033[0m Textures  \033[0;33mF10\033[0m Overlay"
echo ""
echo -e "  Mod:  \033[1;37m$MOD_PATH\033[0m"
echo -e "  JDK:  \033[0;36m$(java -version 2>&1 | head -1)\033[0m"
echo -e "  DCEVM: \033[0;$([ "$HAS_DCEVM" = "true" ] && echo "32mtrue" || echo "33mfalse")\033[0m"
echo -e "  Port: \033[0;36m$JDWP_PORT\033[0m"
echo ""

# Check DCEVM
DCEVM_FLAG=""
if [ "$HAS_DCEVM" = "true" ]; then
    java -XXaltjvm=dcevm -version 2>&1 | grep -q "Dynamic" && \
        DCEVM_FLAG="-XXaltjvm=dcevm" && echo -e "\033[0;32m[Aero] DCEVM active!\033[0m" || \
        echo -e "\033[0;33m[Aero] DCEVM not found in JDK. Hot swap limited.\033[0m"
fi

while true; do
    rm -f "$BASE/temp/.restart"
    rm -rf "$BASE/temp/.hotswap_cache"
    cd "$BASE"

    echo ""
    echo "[Aero] === Building ==="
    # Transpile
    bash scripts/transpile.sh

    # Build (compile + obfuscate → reobf/)
    cd "$BASE/mcp"
    echo "build" | java -jar RetroMCP-Java-CLI.jar
    cd "$BASE"

    # Create run jar from base (has TMI/SPC) + inject mod classes + assets
    cp tests/data/minecraft_test.jar tests/data/minecraft_run.jar
    cd mcp/minecraft/reobf && jar uf "$BASE/tests/data/minecraft_run.jar" *.class 2>/dev/null; cd "$BASE"
    if [ -d "temp/merged" ]; then
        cd temp/merged && jar uf "$BASE/tests/data/minecraft_run.jar" . 2>/dev/null; cd "$BASE"
    fi

    # Note: minecraft_test.jar must have signatures pre-stripped for JDK 8u181
    echo "[Aero] Build complete"

    mkdir -p "$BASE/tests/data/tmp"
    LIBS="mcp/libraries"

    echo ""
    echo "[Aero] === Launching Minecraft (DCEVM from bin/) ==="
    echo "[Aero] JDWP port: $JDWP_PORT"
    echo ""
    java $DCEVM_FLAG -Xms1024M -Xmx1024M \
      -Daero.dev=true \
      -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$JDWP_PORT \
      -Djava.library.path="$LIBS/natives" \
      -Djava.io.tmpdir="$BASE/tests/data/tmp" \
      -cp "temp/merged;tests/data/minecraft_run.jar;$LIBS/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar;$LIBS/net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar;$LIBS/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar;$LIBS/org/lwjgl/lwjgl/lwjgl_util/2.9.4-nightly-20150209/lwjgl_util-2.9.4-nightly-20150209.jar;$LIBS/com/paulscode/codecjorbis/20230120/codecjorbis-20230120.jar;$LIBS/com/paulscode/codecwav/20101023/codecwav-20101023.jar;$LIBS/com/paulscode/libraryjavasound/20101123/libraryjavasound-20101123.jar;$LIBS/com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar;$LIBS/com/paulscode/soundsystem/20120107/soundsystem-20120107.jar" \
      net.minecraft.client.Minecraft || true

    # Check restart flag
    if [ -f "$BASE/temp/.restart" ]; then
        echo ""
        echo -e "\033[0;33m[Aero] Restart requested (F7). Rebuilding...\033[0m"
        rm -f "$BASE/temp/.restart"
        sleep 2
        continue
    fi

    echo ""
    echo "[Aero] Minecraft closed. Press Enter to rebuild, or Ctrl+C to exit."
    read -r || break
done
