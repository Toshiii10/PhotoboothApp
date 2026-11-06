param(
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version = '1.0.0',
    [string]$JdkHome,
    [string]$WixDirectory,
    [switch]$Installer,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot
if ($JdkHome) {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JdkHome).Path
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
if ($WixDirectory) { $env:PATH = "$WixDirectory;$env:PATH" }

foreach ($tool in @('java', 'javac', 'jlink', 'jpackage')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "A Windows x64 JDK 25 is required to build releases ($tool is missing)."
    }
}
if (-not $SkipBuild) {
    & "$projectRoot\mvnw.cmd" -B -ntp clean package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
}

# A new directory for every build avoids deleting files or accidentally shipping stale libraries.
$buildRoot = Join-Path $projectRoot ('build\windows-' + [guid]::NewGuid().ToString('N'))
$inputRoot = Join-Path $buildRoot 'input'
$runtime = Join-Path $buildRoot 'runtime'
$images = Join-Path $buildRoot 'image'
$distribution = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force $inputRoot, "$inputRoot\backend", $distribution | Out-Null
Copy-Item -LiteralPath "$projectRoot\frontend\target\photobooth-frontend.jar" -Destination $inputRoot
Copy-Item -LiteralPath "$projectRoot\frontend\target\lib" -Destination "$inputRoot\lib" -Recurse
Copy-Item -LiteralPath "$projectRoot\backend\target\backend.jar" -Destination "$inputRoot\backend\backend.jar"
Copy-Item -LiteralPath "$projectRoot\packaging\WINDOWS-README.txt" -Destination "$inputRoot\README.txt"

# Keep java.exe: the desktop launcher uses this bundled executable for its backend child.
# Include Java SE plus security, charset and native-access providers used by the application.
# Explicit names support JDKs that link from lib/modules without shipping jmods; do not
# include development tools such as jdk.jlink in the generated runtime.
$runtimeModules = 'java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.crypto.ec,jdk.crypto.mscapi,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.management,jdk.naming.dns,jdk.security.auth,jdk.security.jgss,jdk.net,jdk.xml.dom'
& jlink --add-modules $runtimeModules --strip-debug --no-header-files --no-man-pages --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Could not build the bundled Java runtime.' }

& jpackage --type app-image --name 'Photobooth Studio' --app-version $Version `
    --vendor 'Photobooth Studio' --description 'Desktop photobooth with webcam capture and a local photo gallery' `
    --input $inputRoot --dest $images --runtime-image $runtime `
    --main-jar photobooth-frontend.jar --main-class com.photobooth.frontend.DesktopLauncher `
    --java-options '--enable-native-access=ALL-UNNAMED' `
    --java-options '--sun-misc-unsafe-memory-access=allow'
if ($LASTEXITCODE -ne 0) { throw 'Could not create the Windows application.' }

$appImage = Join-Path $images 'Photobooth Studio'
Copy-Item -LiteralPath "$projectRoot\packaging\create-portable-shortcut.cmd" -Destination "$appImage\Create Desktop Shortcut.cmd"
Copy-Item -LiteralPath "$projectRoot\packaging\WINDOWS-README.txt" -Destination "$appImage\README.txt"

# Exercise the actual native launcher, with no external Java on PATH and isolated data.
$smokeHome = Join-Path $buildRoot 'smoke-data'
$previousPath = $env:PATH
$previousJavaHome = $env:JAVA_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:PATH = "$env:SystemRoot\System32"
    $env:JAVA_HOME = ''
    $env:JAVA_TOOL_OPTIONS = '-Dphotobooth.home="' + $smokeHome + '"'
    $smokeProcess = Start-Process -FilePath "$appImage\Photobooth Studio.exe" `
        -ArgumentList '--smoke-test' -WindowStyle Hidden -Wait -PassThru
    if ($smokeProcess.ExitCode -ne 0) { throw "Packaged smoke test failed. See $smokeHome\logs" }
} finally {
    $env:PATH = $previousPath
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
Write-Host 'Native launcher, bundled runtime, photo composition, gallery and shutdown checks passed.' -ForegroundColor Green

$zip = Join-Path $distribution "Photobooth-Studio-$Version-windows-x64.zip"
Compress-Archive -LiteralPath $appImage -DestinationPath $zip -Force
if ($Installer) {
    if ($env:WIX -and (Test-Path "$env:WIX\bin")) { $env:PATH = "$env:WIX\bin;$env:PATH" }
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -and
        -not (Get-Command wix.exe -ErrorAction SilentlyContinue)) {
        throw "Portable ZIP built successfully. Install WiX Toolset and add its bin directory to PATH to build the installer. ZIP: $zip"
    }
    $installerOutput = Join-Path $buildRoot 'installer'
    & jpackage --type exe --name 'Photobooth Studio' --app-version $Version `
        --app-image $appImage --dest $installerOutput --vendor 'Photobooth Studio' `
        --win-per-user-install --win-dir-chooser --win-menu --win-menu-group 'Photobooth Studio' `
        --win-shortcut --win-upgrade-uuid 'b619be74-34b4-47ab-a9d0-1b22fe5cebe5'
    if ($LASTEXITCODE -ne 0) { throw 'Windows installer creation failed.' }
    Get-ChildItem $installerOutput -Filter '*.exe' -File | Copy-Item -Destination $distribution -Force
}
Get-ChildItem $distribution -File | Where-Object { $_.Extension -in '.zip', '.exe' } | ForEach-Object {
    $checksum = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath ($_.FullName + '.sha256') -Value "$checksum  $($_.Name)" -Encoding ASCII
}
Write-Host "Windows release files: $distribution" -ForegroundColor Green
