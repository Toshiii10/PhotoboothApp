# Photobooth Studio

A Windows desktop photobooth with webcam preview, countdown capture, multi-photo layouts,
designer themes, custom transparent PNG frames, and a local photo gallery.

## For users

Download the **Windows installer EXE** from this repository's **Releases** page
(release files become available after the maintainer builds and publishes a release).
Run the installer, then double-click the **Photobooth Studio** desktop or Start menu icon.
Java and all application dependencies are bundled. No Maven, IDE, terminal, or internet
connection is needed to run the installed app.

Alternatively, download the **windows-x64.zip**, extract the entire ZIP to a permanent
folder, and open **Photobooth Studio.exe**. Run **Create Desktop Shortcut.cmd** once
if you want a desktop icon. Keep the `app` and `runtime` folders beside the EXE;
recreate the shortcut if you move the extracted folder.

The app starts and stops its own local photo service. Only one desktop instance may use
your library at a time. Select your physical webcam under **Camera Device**. If it fails,
close other camera apps and check Windows camera privacy settings.

Your database, saved photos, and frames live in `%USERPROFILE%\Photobooth`.
Logs live in `%USERPROFILE%\Photobooth\logs`. Reinstalling the application does not
remove this library. Back up that folder to preserve your photos.

The initial release builds are unsigned. Windows may show an unknown-publisher or
reputation prompt; signing is a separate release task.

## Run from source (developers)

Requires a Windows x64 JDK 25 with `java`, `javac`, `jlink`, and `jpackage` on PATH.
Maven is found on PATH or downloaded by the included wrapper. Internet is needed
for the first build. The packaged application has no development-tool requirement.

```powershell
.\run-photobooth.cmd
```

This resolves dependencies, builds both modules, and starts the desktop app and backend.
The optional [source shortcut creator](create-desktop-shortcut.cmd) points to this
developer launcher; it is different from the shortcut in the packaged release.

To run the modules independently, close the packaged app first so its database is free:

```powershell
# Terminal 1
.\mvnw.cmd -pl backend spring-boot:run
# Terminal 2
.\mvnw.cmd -pl frontend javafx:run
```

The manually started backend defaults to `http://localhost:8080`; the desktop launcher
instead assigns its own loopback port. The backend has health, layouts, themes, photos,
and custom frame endpoints under `/api`.

## Build a Windows release

```powershell
# Bundled portable application + ZIP; no WiX required
powershell -ExecutionPolicy Bypass -File .\scripts\build-windows.ps1 -Version 1.0.0

# Also build an installer with desktop and Start menu shortcuts
powershell -ExecutionPolicy Bypass -File .\scripts\build-windows.ps1 -Version 1.0.0 -Installer
```

Installer builds additionally require [WiX Toolset](https://wixtoolset.org/) on PATH
(`candle.exe` and `light.exe` for WiX 3, or `wix.exe` for supported newer versions).
Build on Windows x64 with a redistributable JDK, such as Eclipse Temurin 25.
Use `-JdkHome 'C:\path\to\jdk-25'` to select the JDK explicitly, and
`-WixDirectory 'C:\path\to\wix'` for portable WiX binaries.
The script uses `jlink` and `jpackage`, packages dependencies resolved by Maven,
and runs the native EXE in an isolated smoke test with external Java removed from PATH.
It checks JavaFX toolkit initialization, backend startup,
database creation, photo composition, PNG decoding, gallery create/read/delete,
and backend shutdown. It does not open the webcam.

Outputs are in `dist/`: a portable ZIP, an installer EXE when requested, and SHA-256
checksum files. The generated app image is under `build/windows-*/image/`.
`-SkipBuild` reuses an existing Maven package for local packaging iterations.

## Features

- Single, strip, horizontal, and grid layouts with one to six shots.
- Eight built-in frame themes and custom PNG overlays with transparent photo slots.
- Mirror, countdown, color filters, camera selection, and a demo camera fallback.
- Embedded H2 database with gallery previews, PNG export, and local storage.

Built with JavaFX, Spring Boot, H2, and webcam-capture.
