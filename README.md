# VoxSight

**VoxSight** (`v1.3.0`) is an AI/OMR-assisted choral sight-reading and vocal practice platform built to help amateur and ensemble choir singers practice SATB sheet music independently. Singers can photograph physical choral scores, upload PDFs/images for Optical Music Recognition (OMR), or import MusicXML files directly—then review and edit detected notes, isolate their vocal part with synchronized audio-visual playback, and receive real-time pitch feedback while singing.

---

## System Demonstration

Watch the live end-to-end system walkthrough on YouTube:

[![VoxSight Demo](https://img.youtube.com/vi/31P9AGV1Fak/maxresdefault.jpg)](https://www.youtube.com/watch?v=31P9AGV1Fak)

- **Direct Video Link**: [https://www.youtube.com/watch?v=31P9AGV1Fak](https://www.youtube.com/watch?v=31P9AGV1Fak)
- **Latest Android APK Release**: [GitHub Releases](https://github.com/lVIN15/VoxSight/releases)

---

## Key Features

- **Choral Score Digitization & Import**
  - Capture multi-page physical choral scores with the built-in **CameraX** document scanner, upload **PDF / image** scores for server-side OMR conversion, or import `.musicxml`, `.xml`, and `.mxl` files directly.
  - Validates choral **SATB** voice structures (including divisi parts) and processes long OMR jobs reliably in the background via an Android foreground upload service with progress notifications.
- **Interactive Score Review & Note Editor**
  - Powered by **OpenSheetMusicDisplay (OSMD)** inside a touch-interactive SVG canvas (`interactive_editor.html`).
  - Tap noteheads or rests directly on mobile to adjust pitches, accidentals, durations, dotted values, lyrics, or vocal part assignments before saving to your library.
- **Selective SATB Audio & Visual Practice Mode**
  - Supports standard **Soprano, Alto, Tenor, Bass** parts as well as multi-part **divisi** (`Soprano 1/2`, `Alto 1/2`, `Tenor 1/2`, `Bass 1/2`), **Solo**, and **Accompaniment** tracks.
  - Toggle **Audio Focus** (part isolation / accompaniment muting) and **Visual Focus** (60 FPS color-coded glowing notehead auras synced to playback).
  - Control playback tempo with speed multipliers (`0.75x`, `1.0x`, `1.25x`, `1.5x`) and interactive timeline scrubbing.
- **Real-Time Pitch Detection & Vocal Recording**
  - Tracks live microphone input using the **YIN pitch detection algorithm** (`TarsosDSP`) to compare sung frequencies against target score notes in real time.
  - Provides live visual pitch match indicators, post-session accuracy summaries, and saved vocal recordings (**Vocal Takes**) for self-review.
- **Cloud Sync, Practice Streaks & Monetization**
  - Sign in with Email/Password, **Google** (Credential Manager), or **Facebook** backed by **Supabase**.
  - Manage scores across local storage and cloud sync in **My Music Sheets**, track daily **Practice Streaks**, and purchase **Upload Credits** or **VoxSight Premium** via **PayMongo** checkout integration.

---

## Repository Structure

```text
VoxSight/
├── mobile/                              # Android client (Kotlin, Jetpack Compose, OSMD, TarsosDSP)
│   └── app/src/main/
│       ├── assets/                      # OSMD WebView engines (renderer.html, interactive_editor.html)
│       └── java/com/cit/kaido/voxsight/ # UI screens, playback/MIDI engines, parser, network, auth
├── voxsight/                            # Spring Boot REST API + Audiveris OMR + Python music21 pipeline
│   ├── scripts/                         # Image normalization, MusicXML cleaning, SATB/divisi analysis
│   ├── src/main/java/                   # OMR, app-version gate, and PayMongo controllers/services
│   └── Dockerfile                       # Multi-stage production container (Ubuntu 22.04 + Audiveris 5.11)
├── web/                                 # Companion web portal & payment redirect pages (React + Vite)
├── documentation/                       # SRS, SDD, project proposal, and business model documents
└── VOXSIGHT_ARCHITECTURE_AND_PIPELINE.md # Detailed breakdown of OMR, rendering, and sync engines
```

For an in-depth technical walkthrough of the 8-stage processing, rendering, and synchronization pipeline, see [`VOXSIGHT_ARCHITECTURE_AND_PIPELINE.md`](VOXSIGHT_ARCHITECTURE_AND_PIPELINE.md).

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Mobile App (`mobile/`)** | Kotlin, Jetpack Compose (Material 3), Coroutines, CameraX `1.3.4`, Retrofit `2.11.0` & OkHttp, TarsosDSP `2.4.1`, OpenSheetMusicDisplay (OSMD / VexFlow WebView), Native MIDI/Audio Synthesis |
| **Backend API (`voxsight/`)** | Java 17, Spring Boot `3.5.14`, Spring Boot Actuator, Maven, Docker (deployed on Railway) |
| **OMR & Music Pipeline** | Audiveris `5.11.0`, Tesseract OCR (legacy + LSTM English model for lyrics), Ghostscript / Poppler, Python 3 (`music21`, `pypdfium2`, `Pillow`, `partitura`, `numpy`) |
| **Auth, Cloud & Payments** | Supabase (`auth-kt`, `postgrest-kt`, Ktor), Google Credential Manager, Facebook Login SDK, jBCrypt, PayMongo Checkout Sessions API |
| **Web Portal (`web/`)** | React `19`, TypeScript `6`, Vite `8`, Tailwind CSS `v4`, Lucide React |

---

## Prerequisites

- **Mobile Development**:
  - [Android Studio](https://developer.android.com/studio) (JDK 17 bundled or installed separately)
  - Android SDK `26+` (Android 8.0 Oreo minimum; `compileSdk` / `targetSdk` `36`)
- **Backend & OMR Pipeline** *(only needed if running the OMR server locally instead of using the hosted cloud API)*:
  - **Java 17** and **Maven 3.9+** (or use the included `./mvnw` wrapper)
  - **Audiveris 5.11+** installed locally (default Windows path: `C:\Program Files\Audiveris\Audiveris.exe`)
  - **Python 3.10+** with music/PDF processing packages:
    ```bash
    pip install music21 pypdfium2 pillow partitura numpy
    ```
  - *(Alternative)* **Docker Desktop** to build and run the self-contained Linux OMR container via `voxsight/Dockerfile`.
- **Web Portal** *(optional)*:
  - **Node.js 20+** and **npm**

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/lVIN15/VoxSight.git
cd VoxSight
```

### 2. Run the Android App (`mobile/`)

1. Open the **`mobile/`** directory as a project in **Android Studio**.
2. By default, `mobile/app/build.gradle.kts` points `BuildConfig.BASE_URL` to the hosted Railway backend (`https://voxsight-production.up.railway.app/`).
   - **To connect to a local backend instead**, update `BASE_URL` in `mobile/app/build.gradle.kts` (for example, `http://10.0.2.2:8080/` for the Android Emulator or `http://<YOUR_LAN_IP>:8080/` for a physical device on the same Wi-Fi network). Runtime URL initialization and caching are handled in `mobile/app/src/main/java/com/cit/kaido/voxsight/network/ApiClient.kt`.
3. *(Optional)* Add local keys to `mobile/local.properties` if testing payment flows directly:
   ```properties
   PAYMONGO_SECRET_KEY=sk_test_your_key_here
   ```
4. Sync Gradle and run the `app` configuration on a physical Android device (recommended for camera scanning and low-latency microphone pitch tracking) or emulator.

### 3. Run the Backend Locally (`voxsight/`)

#### Option A: Native Local Run (Maven)

Configure environment variables as needed (defaults are defined in `voxsight/src/main/resources/application.properties`):

```powershell
cd voxsight
$env:AUDIVERIS_PATH="C:\Program Files\Audiveris\Audiveris.exe"
$env:PYTHON_PATH="python"
$env:PAYMONGO_SECRET_KEY="sk_test_your_key_here"
.\mvnw.cmd spring-boot:run
```

The API will start on `http://localhost:8080` (health check at `GET /actuator/health`).

#### Option B: Docker Container

```bash
cd voxsight
docker build -t voxsight-backend .
docker run -p 8080:8080 -e PAYMONGO_SECRET_KEY=sk_test_your_key_here voxsight-backend
```

### 4. Run the Companion Web Portal (`web/`)

```bash
cd web
npm install
npm run dev
```

---

## Android Permissions

Declared in `mobile/app/src/main/AndroidManifest.xml`:

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` | Communicates with the VoxSight OMR/Payment backend and Supabase cloud services. |
| `CAMERA` | Captures sheet music photos via CameraX in the Upload screen. |
| `RECORD_AUDIO` | Streams microphone audio for real-time YIN pitch tracking and vocal take recording. |
| `POST_NOTIFICATIONS` | Displays background OMR upload progress and completion alerts on Android 13+. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Keeps `ScoreUploadService` alive while multi-page PDF/image OMR jobs process. |
| `WAKE_LOCK` | Prevents CPU/screen sleep interruptions during active score uploads and practice playback. |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` | Supports Bluetooth audio routing for headphones/speakers during vocal practice. |

---

## Troubleshooting & Common Errors

- **"Image quality is too low. Please capture a clearer photo."**
  - Capture the sheet music in bright, even lighting without harsh shadows, glare, or steep perspective skew. Prefer flat pages or clean PDF exports when available.
- **Non-SATB / Instrumental Score Rejection**
  - VoxSight is specifically tailored for **choral/vocal SATB sheet music** (including divisi vocal arrangements). Purely instrumental scores (such as solo piano, guitar tabs, or orchestral parts without vocal staves) are rejected during validation.
- **Network Timeout or Connection Refused (`ApiClient.kt`)**
  - OMR conversion of dense multi-page PDFs can take 1–3 minutes (`ApiClient` configures a 180-second read timeout). If connecting to a local backend, verify that your firewall allows port `8080`, your phone and PC share the same LAN, and `BASE_URL` in `mobile/app/build.gradle.kts` matches your machine's IPv4 address (note: `ApiClient.init()` automatically migrates stale `10.0.2.2` / `192.168.x.x` preferences back to `DEFAULT_BASE_URL` unless `DEFAULT_BASE_URL` itself is updated).
- **"Update Required" Gate (`HTTP 426 Upgrade Required`)**
  - The backend enforces a minimum client `versionCode` via `voxsight.app.min-version-code` in `application.properties`. Ensure your local build's `versionCode` in `mobile/app/build.gradle.kts` meets or exceeds the server's configured minimum.

```bash
git clone https://github.com/lVIN15/VoxSight.git
cd VoxSight
```

### 2. Run the Android App (`mobile/`)

1. Open the **`mobile/`** directory as a project in **Android Studio**.
2. By default, `mobile/app/build.gradle.kts` points `BuildConfig.BASE_URL` to the hosted Railway backend (`https://voxsight-production.up.railway.app/`).
   - **To connect to a local backend instead**, update `BASE_URL` in `mobile/app/build.gradle.kts` (for example, `http://10.0.2.2:8080/` for the Android Emulator or `http://<YOUR_LAN_IP>:8080/` for a physical device on the same Wi-Fi network). Runtime URL initialization and caching are handled in `mobile/app/src/main/java/com/cit/kaido/voxsight/network/ApiClient.kt`.
3. *(Optional)* Add local keys to `mobile/local.properties` if testing payment flows directly:
   ```properties
   PAYMONGO_SECRET_KEY=sk_test_your_key_here
   ```
4. Sync Gradle and run the `app` configuration on a physical Android device (recommended for camera scanning and low-latency microphone pitch tracking) or emulator.

### 3. Run the Backend Locally (`voxsight/`)

#### Option A: Native Local Run (Maven)

Configure environment variables as needed (defaults are defined in `voxsight/src/main/resources/application.properties`):

```powershell
cd voxsight
$env:AUDIVERIS_PATH="C:\Program Files\Audiveris\Audiveris.exe"
$env:PYTHON_PATH="python"
$env:PAYMONGO_SECRET_KEY="sk_test_your_key_here"
.\mvnw.cmd spring-boot:run
```

The API will start on `http://localhost:8080` (health check at `GET /actuator/health`).

#### Option B: Docker Container

```bash
cd voxsight
docker build -t voxsight-backend .
docker run -p 8080:8080 -e PAYMONGO_SECRET_KEY=sk_test_your_key_here voxsight-backend
```

### 4. Run the Companion Web Portal (`web/`)

```bash
cd web
npm install
npm run dev
```

---

## Android Permissions

Declared in `mobile/app/src/main/AndroidManifest.xml`:

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` | Communicates with the VoxSight OMR/Payment backend and Supabase cloud services. |
| `CAMERA` | Captures sheet music photos via CameraX in the Upload screen. |
| `RECORD_AUDIO` | Streams microphone audio for real-time YIN pitch tracking and vocal take recording. |
| `POST_NOTIFICATIONS` | Displays background OMR upload progress and completion alerts on Android 13+. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | Keeps `ScoreUploadService` alive while multi-page PDF/image OMR jobs process. |
| `WAKE_LOCK` | Prevents CPU/screen sleep interruptions during active score uploads and practice playback. |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` | Supports Bluetooth audio routing for headphones/speakers during vocal practice. |

---

## Troubleshooting & Common Errors

- **"Image quality is too low. Please capture a clearer photo."**
  - Capture the sheet music in bright, even lighting without harsh shadows, glare, or steep perspective skew. Prefer flat pages or clean PDF exports when available.
- **Non-SATB / Instrumental Score Rejection**
  - VoxSight is specifically tailored for **choral/vocal SATB sheet music** (including divisi vocal arrangements). Purely instrumental scores (such as solo piano, guitar tabs, or orchestral parts without vocal staves) are rejected during validation.
- **Network Timeout or Connection Refused (`ApiClient.kt`)**
  - OMR conversion of dense multi-page PDFs can take 1–3 minutes (`ApiClient` configures a 180-second read timeout). If connecting to a local backend, verify that your firewall allows port `8080`, your phone and PC share the same LAN, and `BASE_URL` in `mobile/app/build.gradle.kts` matches your machine's IPv4 address (note: `ApiClient.init()` automatically migrates stale `10.0.2.2` / `192.168.x.x` preferences back to `DEFAULT_BASE_URL` unless `DEFAULT_BASE_URL` itself is updated).
- **"Update Required" Gate (`HTTP 426 Upgrade Required`)**
  - The backend enforces a minimum client `versionCode` via `voxsight.app.min-version-code` in `application.properties`. Ensure your local build's `versionCode` in `mobile/app/build.gradle.kts` meets or exceeds the server's configured minimum.

