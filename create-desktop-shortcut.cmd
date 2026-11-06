@echo off
setlocal
cd /d "%~dp0"

echo Creating "Photobooth Studio" shortcut on your Desktop...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell;" ^
  "$desktop = [Environment]::GetFolderPath('Desktop');" ^
  "$sc = $ws.CreateShortcut((Join-Path $desktop 'Photobooth Studio.lnk'));" ^
  "$sc.TargetPath = Join-Path '%~dp0' 'run-photobooth.cmd';" ^
  "$sc.WorkingDirectory = '%~dp0';" ^
  "$sc.Description = 'Launch Photobooth Studio (Spring Boot Backend + JavaFX Frontend)';" ^
  "$sc.IconLocation = 'shell32.dll,116';" ^
  "$sc.Save();" ^
  "Write-Host 'Created shortcut:' $sc.FullName"
echo.
pause

