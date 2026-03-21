@echo off
cd /d "%~dp0"
javac DevToolsSetup.java
java DevToolsSetup "%~dp0..\.."
