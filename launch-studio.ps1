$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot
Write-Host 'Building Photobooth Studio from source...' -ForegroundColor Cyan
& "$projectRoot\mvnw.cmd" -B -ntp package
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check that a JDK 25 is installed and dependencies can be downloaded.' }

# Use Maven's resolved libraries instead of versions from a particular developer's cache.
$appDirectory = Join-Path $projectRoot ('build\dev-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $appDirectory, "$appDirectory\backend" | Out-Null
Copy-Item -LiteralPath "$projectRoot\frontend\target\photobooth-frontend.jar" -Destination $appDirectory
Copy-Item -LiteralPath "$projectRoot\frontend\target\lib" -Destination "$appDirectory\lib" -Recurse
Copy-Item -LiteralPath "$projectRoot\backend\target\backend.jar" -Destination "$appDirectory\backend\backend.jar"
& java '--enable-native-access=ALL-UNNAMED' '--sun-misc-unsafe-memory-access=allow' `
    -jar "$appDirectory\photobooth-frontend.jar"
exit $LASTEXITCODE
