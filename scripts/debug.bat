@echo off
title Aero DevTools
cd /d "%~dp0"
set WORKSPACE=%~dp0..\..\..
bash "%~dp0debug.sh"
