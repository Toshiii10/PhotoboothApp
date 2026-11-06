@echo off
@REM ---------------------------------------------------------------------------
@REM Minimal Maven wrapper for Photobooth.
@REM  1. Uses "mvn" from PATH if available
@REM  2. Otherwise reuses any Maven already downloaded under %USERPROFILE%\.m2\wrapper\dists
@REM  3. Otherwise downloads Apache Maven into %USERPROFILE%\.m2\wrapper\dists
@REM ---------------------------------------------------------------------------
setlocal
set "MVN_VERSION=3.9.9"
set "DISTS=%USERPROFILE%\.m2\wrapper\dists"
set "MVN_CMD="

where mvn >nul 2>nul && set "MVN_CMD=mvn"

if not defined MVN_CMD (
    for /f "delims=" %%F in ('dir /b /s "%DISTS%\mvn.cmd" 2^>nul') do set "MVN_CMD=%%F"
)

if not defined MVN_CMD (
    echo Downloading Apache Maven %MVN_VERSION% ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop';" ^
      "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
      "$dir='%DISTS%\apache-maven-%MVN_VERSION%'; New-Item -ItemType Directory -Force -Path $dir | Out-Null;" ^
      "$zip=Join-Path $dir 'maven.zip';" ^
      "Invoke-WebRequest -UseBasicParsing -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VERSION%/apache-maven-%MVN_VERSION%-bin.zip' -OutFile $zip;" ^
      "Expand-Archive -Force -Path $zip -DestinationPath $dir; Remove-Item $zip"
    if errorlevel 1 (
        echo Failed to download Maven.
        exit /b 1
    )
    set "MVN_CMD=%DISTS%\apache-maven-%MVN_VERSION%\apache-maven-%MVN_VERSION%\bin\mvn.cmd"
)

call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
