# VoxSight Architecture, Pipeline & Engine Reference

---

## 1. Complete System Pipeline (Box Visualization)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   VOXSIGHT SYSTEM PIPELINE                                  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘

 ┌──────────────────────────────────────┐
 │ 📱 1. MOBILE SCANNING & CAPTURE      │
 │  • CameraX / Storage File Picker     │
 │  • High-res sheet music photograph   │
 └──────────────────┬───────────────────┘
                    │  (Sends Image via HTTP POST)
                    ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │ ☁️ 2. BACKEND OMR ENGINE (Spring Boot Server)                          │
 │  • Audiveris Machine Vision Pipeline                                  │
 │  • Detects staves, clefs, barlines, noteheads, accidentals & lyrics   │
 │  • Generates standardized MusicXML                                    │
 └──────────────────┬────────────────────────────────────────────────────┘
                    │  (Returns MusicXML)
                    ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │ ⚙️ 3. MOBILE PARSING & NORMALIZATION ENGINE (MusicXmlParser.kt)        │
 │  • Normalizes timing & tempo (STANDARD_TPQ = 480)                     │
 │  • Isolates SATB parts (Soprano, Alto, Tenor, Bass)                   │
 │  • Generates chronological MusicalEvent audio stream                  │
 └──────────────────┬───────────────────────────────────┬────────────────┘
                    │                                   │
                    ▼                                   ▼
 ┌──────────────────────────────────────┐  ┌──────────────────────────────────────┐
 │ ✏️ 4. INTERACTIVE EDITING ENGINE     │  │ 🎨 5. VISUAL RENDERING ENGINE       │
 │    (interactive_editor.html)         │  │    (renderer.html via OSMD)          │
 │  • Vector SVG score engraving        │  │  • Vector SVG score engraving        │
 │  • Mobile touch-tap note selection   │  │  • Displays notes, staves & lyrics   │
 │  • Live pitch, duration & voice edit │  │  • 60 FPS Canvas overlay layer       │
 └──────────────────────────────────────┘  └──────────────────┬───────────────────┘
                                                              │
                                                              ▼
 ┌──────────────────────────────────────┐  ┌──────────────────────────────────────┐
 │ 🎹 6. PLAYBACK & SYNTHESIS ENGINE    │  │ ⚡ 7. REAL-TIME HIGHLIGHT SYNC       │
 │    (NativePlaybackEngine.kt)         │  │    (SyncManager.kt + Canvas 2D)      │
 │  • Coroutine pacing loop             │──┼─> • Pairs audio event with notehead  │
 │  • Dynamic BPM & speed multipliers   │  │  • Real-time glowing SATB auras:     │
 │  • Tone.js / Native MIDI synthesis   │  │    🌸 Pink  🟣 Purple  🔵 Blue  🟢 Green│
 └──────────────────────────────────────┘  └──────────────────▲───────────────────┘
                                                              │
 ┌──────────────────────────────────────┐                     │
 │ 🎤 8. PITCH TRACKING & FEEDBACK      │                     │
 │  • Microphone stream (YIN algorithm) ├─────────────────────┘
 │  • Green (Match) / Red (Miss)        │
 └──────────────────────────────────────┘
```

---

## 2. Breakdown of Engines Used in VoxSight

| Engine / Component | Technology Stack | Location in Project | Core Function |
| :--- | :--- | :--- | :--- |
| **1. Image Scanning & Capture** | Android CameraX & File Provider | `UploadScoreScreen.kt` | Takes camera photos or imports score images/PDFs. |
| **2. Optical Music Recognition (OMR)** | Audiveris + Spring Boot REST | `AudiverisService.java`, `OmrController.java` | Analyzes image; extracts staves, notes, clefs & lyrics into MusicXML. |
| **3. MusicXML Parsing & Normalization** | Android `XmlPullParser` | `MusicXmlParser.kt` | Standardizes tempo to `STANDARD_TPQ = 480` and generates `MusicalEvent`s. |
| **4. Visual Sheet Music Rendering** | OpenSheetMusicDisplay (OSMD) on VexFlow | `renderer.html`, `interactive_editor.html` | Renders crisp SVG sheet music with notes, staves, and lyrics in WebViews. |
| **5. Interactive Note Editing** | XML DOM Parser + Android JavaScript Bridge | `interactive_editor.html`, `NoteEditorBottomSheet.kt` | Stamps `data-vx-id`, handles note taps, pitch/voice edits, and note deletions. |
| **6. Playback & Timing Engine** | Kotlin Coroutines Dispatcher + Tone.js / MIDI | `NativePlaybackEngine.kt`, `MidiPlaybackEngine.kt` | Microsecond-accurate audio pacing with BPM stepper and speed multipliers. |
| **7. Visual Highlighting Sync** | Bipartite Coordinate Matcher + HTML5 Canvas | `SyncManager.kt`, `renderer.html` | Maps audio events to visual noteheads at 60 FPS in color-coded SATB auras. |
| **8. Real-time Pitch Detection** | YIN Pitch Tracker Algorithm | `AudioRecordingService.kt`, `PitchComparator.kt` | Analyzes microphone input frequency (Hz) against target notes in real-time. |

---

## 3. Why Can't Audiveris Replace OSMD for Rendering?

1. **Audiveris is a Desktop/Server Scanner, Not a Mobile Renderer**:
   * Audiveris is a heavy Java application that runs on the server. It cannot run directly on an Android device.
2. **Audiveris Cannot Animate at 60 FPS or Color-Code Notes**:
   * It produces static output. It has no mechanism for dynamic real-time glowing SATB note animations (Pink, Purple, Blue, Green) or live pitch feedback (Green/Red).
   * Rendering on the server would require streaming 60 full-resolution images per second over the internet, causing severe lag and high battery consumption.
3. **Audiveris Cannot Support Touch Gestures on Mobile**:
   * OSMD allows direct touch-tapping on noteheads on mobile screens to open the editor bottom sheet and modify notes locally.

---

## 4. How Lyrics Rendering Works

* **OSMD natively renders lyrics** under notes.
* In both `interactive_editor.html` and `renderer.html`, the setting `drawLyrics: true` is enabled.
* **Process**:
  1. Audiveris reads text in the scanned image and saves it in MusicXML `<lyric><text>...</text></lyric>` tags.
  2. OSMD reads those `<lyric>` tags and engraves the words directly beneath their corresponding notes.

---

## 5. UI Differences: Editing Mode vs. Playback Mode

| Aspect | ✏️ Editing Mode (`interactive_editor.html`) | 🎵 Playback Mode (`renderer.html`) |
| :--- | :--- | :--- |
| **Default Zoom** | **`0.55`** (Larger noteheads for easy finger tapping) | **`0.40`** (Zoomed-out score overview for singing) |
| **Canvas Layer** | Single-note selection box | Real-time animated SATB glowing auras (S/A/T/B colors) |
| **Screen Toolbar** | Review header, CANCEL / CONFIRM & SAVE buttons | Voice chips (S, A, T, B), Mute, Visual Focus, BPM Stepper |
| **User Interaction**| Tap noteheads to edit pitch, duration, voice, or delete | Hands-free auto-scrolling visual accompaniment |
