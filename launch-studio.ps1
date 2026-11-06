$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

$M2 = Join-Path $env:USERPROFILE ".m2\repository"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  PHOTOBOOTH STUDIO - Fast Compiler & Launcher" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# 1. Build Backend Classpath & Compile Backend
# ---------------------------------------------------------------------------
$backendDepDirs = @(
    "$M2\org\springframework\boot\spring-boot\4.1.1",
    "$M2\org\springframework\boot\spring-boot-autoconfigure\4.1.1",
    "$M2\org\springframework\boot\spring-boot-webmvc\4.1.1",
    "$M2\org\springframework\boot\spring-boot-web-server\4.1.1",
    "$M2\org\springframework\boot\spring-boot-tomcat\4.1.1",
    "$M2\org\springframework\boot\spring-boot-servlet\4.1.1",
    "$M2\org\springframework\boot\spring-boot-jackson\4.1.1",
    "$M2\org\springframework\boot\spring-boot-http-converter\4.1.1",
    "$M2\org\springframework\boot\spring-boot-data-jpa\4.1.1",
    "$M2\org\springframework\boot\spring-boot-data-commons\4.1.1",
    "$M2\org\springframework\boot\spring-boot-jpa\4.1.1",
    "$M2\org\springframework\boot\spring-boot-hibernate\4.1.1",
    "$M2\org\springframework\boot\spring-boot-jdbc\4.1.1",
    "$M2\org\springframework\boot\spring-boot-sql\4.1.1",
    "$M2\org\springframework\boot\spring-boot-transaction\4.1.1",
    "$M2\org\springframework\boot\spring-boot-persistence\4.1.1",
    "$M2\org\springframework\spring-core\7.0.9",
    "$M2\org\springframework\spring-beans\7.0.9",
    "$M2\org\springframework\spring-context\7.0.9",
    "$M2\org\springframework\spring-aop\7.0.9",
    "$M2\org\springframework\spring-expression\7.0.9",
    "$M2\commons-logging\commons-logging\1.3.6",
    "$M2\org\springframework\spring-web\7.0.9",
    "$M2\org\springframework\spring-webmvc\7.0.9",
    "$M2\org\springframework\spring-jdbc\7.0.9",
    "$M2\org\springframework\spring-orm\7.0.9",
    "$M2\org\springframework\spring-tx\7.0.9",
    "$M2\org\springframework\spring-aspects\7.0.9",
    "$M2\org\springframework\data",
    "$M2\org\hibernate",
    "$M2\com\h2database\h2\2.4.240",
    "$M2\com\zaxxer\HikariCP",
    "$M2\org\apache\tomcat\embed",
    "$M2\tools\jackson",
    "$M2\com\fasterxml\jackson\core\jackson-annotations\2.21",
    "$M2\com\fasterxml\jackson\core\jackson-core\2.21.2",
    "$M2\com\fasterxml\jackson\core\jackson-databind\2.21.2",
    "$M2\com\fasterxml\jackson\datatype",
    "$M2\jakarta",
    "$M2\org\slf4j\slf4j-api\2.0.18",
    "$M2\org\slf4j\jul-to-slf4j\2.0.18",
    "$M2\ch\qos\logback",
    "$M2\net\bytebuddy",
    "$M2\org\antlr",
    "$M2\org\jboss\logging",
    "$M2\com\fasterxml\classmate",
    "$M2\io\micrometer",
    "$M2\org\aspectj",
    "$M2\org\jspecify"
)

$backendJars = foreach ($d in $backendDepDirs) {
    if (Test-Path $d) {
        Get-ChildItem $d -Recurse -Filter "*.jar" |
            Where-Object { $_.Name -notmatch "sources|javadoc|test" } |
            Select-Object -ExpandProperty FullName
    }
}

$backendClasses = Join-Path $ProjectRoot "backend\target\classes"
New-Item -ItemType Directory -Force $backendClasses | Out-Null
Copy-Item (Join-Path $ProjectRoot "backend\src\main\resources\*") $backendClasses -Force

Write-Host "[1/4] Compiling Spring Boot Backend..." -ForegroundColor Yellow
$backendSources = Get-ChildItem (Join-Path $ProjectRoot "backend\src\main\java") -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
$backendCpFile = Join-Path $ProjectRoot "backend\target\cp.txt"
$backendCpString = ($backendJars -join ";")
$escapedBackendCpOnly = $backendCpString.Replace('\', '\\')
Set-Content -Path $backendCpFile -Value ("-cp `"$escapedBackendCpOnly`"") -Encoding ASCII

& javac -encoding UTF-8 -parameters -d $backendClasses "@$backendCpFile" $backendSources
if ($LASTEXITCODE -ne 0) {
    Write-Host "Backend compilation failed!" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 2. Build Frontend Classpath & Compile Frontend
# ---------------------------------------------------------------------------
$frontendDepDirs = @(
    "$M2\org\openjfx\javafx-base\21.0.5",
    "$M2\org\openjfx\javafx-controls\21.0.5",
    "$M2\org\openjfx\javafx-graphics\21.0.5",
    "$M2\org\openjfx\javafx-swing\21.0.5",
    "$M2\com\github\sarxos\webcam-capture\0.3.12",
    "$M2\com\nativelibs4java\bridj\0.7.0",
    "$M2\org\slf4j\slf4j-api\2.0.18",
    "$M2\ch\qos\logback",
    "$M2\com\fasterxml\jackson\core\jackson-annotations\2.17.2",
    "$M2\com\fasterxml\jackson\core\jackson-core\2.17.2",
    "$M2\com\fasterxml\jackson\core\jackson-databind\2.17.2"
)

$frontendJars = foreach ($d in $frontendDepDirs) {
    if (Test-Path $d) {
        Get-ChildItem $d -Recurse -Filter "*.jar" |
            Where-Object { $_.Name -notmatch "sources|javadoc" } |
            Select-Object -ExpandProperty FullName
    }
}

$frontendClasses = Join-Path $ProjectRoot "frontend\target\classes"
New-Item -ItemType Directory -Force $frontendClasses | Out-Null
Copy-Item (Join-Path $ProjectRoot "frontend\src\main\resources\*") $frontendClasses -Force

Write-Host "[2/4] Compiling JavaFX Studio Frontend..." -ForegroundColor Yellow
$frontendSources = Get-ChildItem (Join-Path $ProjectRoot "frontend\src\main\java") -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
$frontendCpFile = Join-Path $ProjectRoot "frontend\target\cp.txt"
$frontendCpString = ($frontendJars -join ";")
$escapedFrontendCpOnly = $frontendCpString.Replace('\', '\\')
Set-Content -Path $frontendCpFile -Value ("-cp `"$escapedFrontendCpOnly`"") -Encoding ASCII

& javac -encoding UTF-8 -d $frontendClasses "@$frontendCpFile" $frontendSources
if ($LASTEXITCODE -ne 0) {
    Write-Host "Frontend compilation failed!" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------
# 3. Start Backend Server (if not already listening on 8080)
# ---------------------------------------------------------------------------
$backendRunning = $false
try {
    $null = Invoke-WebRequest -Uri "http://localhost:8080/api/health" -TimeoutSec 2 -UseBasicParsing
    $backendRunning = $true
} catch {
    $backendRunning = $false
}

if ($backendRunning) {
    Write-Host "[3/4] Backend is already running on http://localhost:8080" -ForegroundColor Green
} else {
    Write-Host "[3/4] Starting Spring Boot Backend + H2 Database on port 8080..." -ForegroundColor Yellow
    $backendRunArgFile = Join-Path $ProjectRoot "backend\target\run-args.txt"
    $fullBackendCp = (@($backendClasses) + $backendJars) -join ";"
    $escapedBackendCp = $fullBackendCp.Replace('\', '\\')
    Set-Content -Path $backendRunArgFile -Value @(
        "-Djava.awt.headless=true",
        "-Dnet.bytebuddy.experimental=true",
        "-cp",
        "`"$escapedBackendCp`"",
        "com.photobooth.backend.PhotoboothBackendApplication"
    ) -Encoding ASCII

    Start-Process -FilePath "java" -ArgumentList "@`"$backendRunArgFile`"" -WorkingDirectory $ProjectRoot -WindowStyle Minimized
}

# ---------------------------------------------------------------------------
# 4. Launch JavaFX Frontend
# ---------------------------------------------------------------------------
Write-Host "[4/4] Opening Photobooth Studio Window..." -ForegroundColor Green
$javafxDirs = @(
    "$M2\org\openjfx\javafx-base\21.0.5",
    "$M2\org\openjfx\javafx-controls\21.0.5",
    "$M2\org\openjfx\javafx-graphics\21.0.5",
    "$M2\org\openjfx\javafx-swing\21.0.5"
)
$javafxWinJars = foreach ($d in $javafxDirs) {
    Get-ChildItem $d -Filter "*-win.jar" | Select-Object -ExpandProperty FullName
}
$modulePath = ($javafxWinJars -join ";").Replace('\', '\\')
$fullFrontendCp = ((@($frontendClasses) + $frontendJars) -join ";").Replace('\', '\\')

$frontendRunArgFile = Join-Path $ProjectRoot "frontend\target\run-args.txt"
Set-Content -Path $frontendRunArgFile -Value @(
    "--module-path",
    "`"$modulePath`"",
    "--add-modules",
    "javafx.controls,javafx.swing,javafx.graphics,javafx.base",
    "-cp",
    "`"$fullFrontendCp`"",
    "com.photobooth.frontend.Launcher"
) -Encoding ASCII

& java "@$frontendRunArgFile"
