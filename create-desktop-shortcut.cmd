@echo off
setlocal
cd /d "%~dp0"
set "PHOTOBOOTH_SOURCE_DIR=%~dp0"

echo Creating "Photobooth Studio" shortcut on your Desktop...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell;" ^
  "$desktop = [Environment]::GetFolderPath('Desktop');" ^
  "$root = $env:PHOTOBOOTH_SOURCE_DIR;" ^
  "$sc = $ws.CreateShortcut((Join-Path $desktop 'Photobooth Studio.lnk'));" ^
  "$sc.TargetPath = Join-Path $root 'run-photobooth.cmd';" ^
  "$sc.WorkingDirectory = $root;" ^
  "$sc.Description = 'Launch Photobooth Studio (Spring Boot Backend + JavaFX Frontend)';" ^
  "$sc.IconLocation = 'shell32.dll,116';" ^
  "$sc.Save();" ^
  "Write-Host 'Created shortcut:' $sc.FullName"
echo.
pause

