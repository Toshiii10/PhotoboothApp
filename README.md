# ✨ Photobooth Studio (Spring Boot + H2 Database + JavaFX)

A full-featured multi-frame Photobooth desktop application with **built-in designer themes**, **custom transparent PNG frame uploads**, and an **embedded H2 database** gallery.

---

## 🚀 One-Click Launch (Starts Backend + Frontend Automatically)

Double-click or run **[`run-photobooth.cmd`](file:///d:/Personal%20Projects/InProgress/Photobooth/run-photobooth.cmd)**:
```powershell
.\run-photobooth.cmd
```
- Automatically starts the **Spring Boot + H2 Database backend** on `http://localhost:8080` (if not already running).
- Opens the **JavaFX Photobooth Studio** window and connects as soon as the backend is ready.

### Optional: Create a Windows Desktop Shortcut
Run **[`create-desktop-shortcut.cmd`](file:///d:/Personal%20Projects/InProgress/Photobooth/create-desktop-shortcut.cmd)** once to place a **Photobooth Studio** icon right on your Windows Desktop:
```powershell
.\create-desktop-shortcut.cmd
```

---

## 🎨 Key Features

1. **Multi-Frame Layouts (`FrameLayout`)**
   - `SINGLE` (1×1), `STRIP_2` (1×2), `STRIP_3` (1×3), `STRIP_4` (Classic 1×4 Strip), `HORIZONTAL_3` (3×1 Wide Trio), `GRID_2X2` (2×2 Grid), `GRID_3X2` (3×2 6-Shot Grid).
2. **8 Designer Frame Themes (`Theme`)**
   - **Classic White**, **Midnight Glam**, **Blush Pastel** (with confetti), **Film Strip** (with cinema sprocket holes), **Polaroid** (tilted instant-photo cards), **Neon Party**, **Golden Hour**, and **Fresh Mint**.
3. **Upload Custom PNG Frame Overlays (`CustomFrame` + `SlotDetector`)**
   - Upload any `.png` artwork containing transparent cutouts (alpha < 40).
   - The backend automatically detects every transparent window in reading order, counts how many webcam shots are needed, and composites your captured photos seamlessly underneath the custom frame overlay.
   - Includes **3 pre-seeded sample custom frames** (*Gold Elegance*, *Retro Arcade*, *Sweet Pastel Duo*) on first launch.
4. **Embedded H2 Database & Gallery (`~/Photobooth`)**
   - All composed photos, thumbnails, and custom frames are persisted in `~/Photobooth` with metadata stored in an embedded H2 database (`~/Photobooth/data/photobooth`).
   - Browse, inspect, export, or delete past sessions anytime in the **Photo Gallery** tab.
5. **Live Camera Filters & Studio Demo Fallback**
   - Includes live filters (*Natural*, *Golden Warm*, *B&W Classic*, *Vintage Sepia*, *Cool Cinema*), adjustable countdown timer, and mirror toggle.
   - Automatically falls back to an animated studio test camera if no physical webcam is connected.

---

## 🛠 Manual Commands (Terminal)

If you prefer running backend and frontend in separate terminals:

```powershell
# Terminal 1: Start Spring Boot Backend
.\mvnw.cmd -pl backend spring-boot:run

# Terminal 2: Start JavaFX Frontend
.\mvnw.cmd -pl frontend javafx:run
```
