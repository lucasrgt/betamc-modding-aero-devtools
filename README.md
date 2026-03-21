# Aero DevTools

Hot-reload development tools for Minecraft Beta 1.7.3 mods (Forge/ModLoader).

```
     ___    __________  ____
    /   |  / ____/ __ \/ __ \
   / /| | / __/ / /_/ / / / /
  / ___ |/ /___/ _, _/ /_/ /
 /_/  |_/_____/_/ |_|\____/  DevTools
```

## What it does

- **F6 Hot Swap** — edit code, press F6, changes apply instantly without restarting
- **F7 Restart** — full rebuild + relaunch in seconds
- **F9 Textures** — edit PNGs, press F9, textures reload instantly in-game
- **F10 Overlay** — TPS, memory, debug status, all keybinds visible

## Requirements

- **JDK 8u181** (Oracle, AdoptOpenJDK, or Zulu — must be version 8u181 for DCEVM compatibility)
- **DCEVM** (Dynamic Code Evolution VM) — enables full class hot-reload
- **Git Bash** (Windows) or any bash (Linux/Mac)

## Setup (5 minutes)

### 1. Install JDK 8u181

Download and install JDK 8u181:
- [Oracle JDK 8 Archive](https://www.oracle.com/br/java/technologies/javase/javase8-archive-downloads.html) — scroll to **Java SE Development Kit 8u181**
- The version must be exactly **8u181** for DCEVM compatibility.

Set environment variables:
```
JAVA_HOME = C:\Program Files\Java\jdk1.8.0_181
PATH += %JAVA_HOME%\bin
```

### 2. Install DCEVM

Download the installer:
https://github.com/dcevm/dcevm/releases/download/light-jdk8u181%2B2/DCEVM-8u181-installer.jar

Run as administrator:
```
java -jar DCEVM-8u181-installer.jar
```

Select JDK 8u181 → click **"Install DCEVM as altjvm"**.

Verify:
```
java -XXaltjvm=dcevm -version
```
Should show: `Dynamic Code Evolution 64-Bit Server VM`

### 3. Clone DevTools

```bash
cd your-workspace/
git clone https://github.com/lucasrgt/aero-devtools.git libraries/devtools
```

### 4. Configure

**Windows:**
```
libraries\devtools\tools\setup.bat
```

**Linux/Mac:**
```bash
bash libraries/devtools/tools/setup.sh
```

A GUI opens — set your mod path, JDK path, and bash path. Click **Save & Close**.

This creates `devtools.json` in your workspace root.

### 5. Run

**Windows:**
```
libraries\devtools\scripts\debug.bat
```

**Linux/Mac:**
```bash
bash libraries/devtools/scripts/debug.sh
```

The AERO banner appears, your mod builds, and Minecraft launches with DCEVM + debug overlay.

## Usage

| Key | Action | Needs DCEVM |
|-----|--------|-------------|
| **F6** | Hot Swap — recompile + live swap classes | Yes (full) / No (method bodies only) |
| **F7** | Restart — close + rebuild + relaunch | No |
| **F9** | Textures — reload all PNGs from disk | No |
| **F10** | Overlay — toggle dev toolbar | No |

### F6 Hot Swap workflow

1. Edit any `.java` file in your mod
2. Press **F6** in-game
3. Wait ~8s (transpile + build + DCEVM swap)
4. Changes are live — no restart needed

With DCEVM you can add/remove classes, methods, and fields. Without DCEVM, only method body changes work.

### F9 Texture workflow

1. Edit any `.png` in `src/yourmod/assets/`
2. Press **F9** in-game
3. Textures update instantly

### F7 Restart workflow

1. Press **F7** in-game
2. Minecraft closes gracefully
3. Terminal auto-rebuilds and relaunches
4. Press Enter in terminal to rebuild manually, or Ctrl+C to exit

## Integration

Add these lines to your mod's constructor:

```java
// Initialize dev tools (safe to call in production — no-ops if IS_DEV is false)
Aero_DevBootstrap.init();

// Register texture tracking for F9 hot-reload
Aero_DevTextureReload.registerMod("yourmodname");
```

Add to your `OnTickInGame`:

```java
Aero_DevConsole.onTick(mc);
```

Replace `ModLoader.addOverride` with:

```java
// Instead of:
int tex = ModLoader.addOverride("/terrain.png", "/blocks/myblock.png");

// Use:
int tex = Aero_DevTextureReload.override("yourmodname", "/terrain.png", "/blocks/myblock.png");
```

The `override` method calls `ModLoader.addOverride` internally and tracks the texture for F9 reload.

## Project Structure

```
libraries/devtools/
├── lib/                    ← Java code (runs inside Minecraft)
│   ├── Aero_DevBootstrap   — IS_DEV flag, config loader
│   ├── Aero_DevConsole     — F6/F7/F9/F10 keybind handler
│   ├── Aero_DevOverlay     — in-game toolbar rendering + script execution
│   ├── Aero_DevGuiIngameProxy — flicker-free overlay via GuiIngame
│   ├── Aero_DevTextureReload — F9 texture hot-reload from filesystem
│   └── Aero_DevInfoProvider — interface for mod-specific metrics
├── tools/                  ← External tools (run outside Minecraft)
│   ├── DevToolsSetup.java  — Swing config GUI
│   ├── HotSwapAgent.java   — JDWP class redefiner for F6
│   ├── setup.bat / setup.sh
├── scripts/                ← Build/launch scripts
│   ├── debug.sh / debug.bat — main dev loop
│   └── hotswap.sh          — transpile + build (called by F6)
└── README.md
```

## Release builds

DevTools is excluded from release builds automatically. In your `transpile.sh`, add:

```bash
if [ "$AERO_RELEASE" = "1" ]; then
    find "$LIBS" -name "*.java" -not -path "*/devtools/*" | while read -r file; do
        # transpile without devtools
    done
fi
```

Build for release:
```bash
AERO_RELEASE=1 bash scripts/test.sh
```

No `Aero_Dev*` classes will be included in the final jar.

## devtools.json

Generated by the setup GUI. Example:

```json
{
  "modPath": "mods/authoral/beta-energistics",
  "jdkPath": "C:/Program Files/Java/jdk1.8.0_181",
  "bashPath": "C:/Program Files/Git/bin/bash.exe",
  "jdwpPort": 5006,
  "dcevm": true
}
```

## FAQ

**Q: Can I use a different JDK version?**
A: DCEVM requires version 8u181 specifically (any provider: Oracle, AdoptOpenJDK, Zulu). Other JDK 8 versions work for building but won't have full hot-reload.

**Q: Do I need an IDE?**
A: No. F6 handles everything (transpile + compile + DCEVM swap) without an IDE.

**Q: Does this work on Linux?**
A: Yes. Use `debug.sh` directly. DCEVM for Linux is available at https://dcevm.github.io/

**Q: What if I don't install DCEVM?**
A: F6 still recompiles, but only method body changes apply live. Structural changes (new classes/methods) need F7 restart.

## License

MIT
