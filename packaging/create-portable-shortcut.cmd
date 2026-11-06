@echo off
setlocal
set "PHOTOBOOTH_APP_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$root=$env:PHOTOBOOTH_APP_DIR;" ^
  "$desktop=[Environment]::GetFolderPath('Desktop');" ^
  "$shell=New-Object -ComObject WScript.Shell;" ^
  "$link=$shell.CreateShortcut((Join-Path $desktop 'Photobooth Studio.lnk'));" ^
  "$link.TargetPath=Join-Path $root 'Photobooth Studio.exe';" ^
  "$link.WorkingDirectory=$root;" ^
  "$link.IconLocation=$link.TargetPath;" ^
  "$link.Save()"
if errorlevel 1 (
    echo Could not create the desktop shortcut.
) else (
    echo Desktop shortcut created. Keep the extracted application folder in this location.
)
pause
