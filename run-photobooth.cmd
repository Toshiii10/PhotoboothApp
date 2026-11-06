@echo off
title Photobooth Studio Launcher
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch-studio.ps1"
if errorlevel 1 (
    echo.
    echo Photobooth exited with an error.
    pause
)
