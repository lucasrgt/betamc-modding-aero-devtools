#!/bin/bash
# Aero DevTools Setup — launches config GUI
cd "$(dirname "$0")"
WORKSPACE="$(cd ../../.. && pwd)"
javac DevToolsSetup.java 2>/dev/null
java DevToolsSetup "$WORKSPACE"
