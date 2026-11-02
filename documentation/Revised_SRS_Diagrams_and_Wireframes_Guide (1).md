# VoxSight Revised SRS Diagrams & In-App Screenshot Guide

> **Document Status**: Production-Aligned Revision (v2 — Fully Verified)
> **Source Baseline**: Software Requirements Specification.md
> **Target Systems**: Kotlin Android Client (mobile/), Spring Boot OMR Backend (voxsight/), Audiveris Machine Vision, and Python music21 Analysis Engine.
> **Note**: This document leaves the original SRS file intact and provides the corrected, fully synchronized UML diagrams (PlantUML) and practical in-app screenshot instructions reflecting the real implementation.

---

## Executive Summary of Identified Discrepancies & Corrections

| Item / Dimension | Legacy SRS Specification | Current Implemented Architecture | Correction in Revised Diagrams |
| :--- | :--- | :--- | :--- |
| **Audio Synthesis Engine** | "Tone.js hosted inside Android WebView using human Aahs/Oohs soundfonts" | **Native Android MIDI & Kotlin Coroutines Pacing (NativePlaybackEngine.kt)** | WebView has **zero audio responsibility**. The audio clock runs natively via NativePlaybackEngine; Tone.js is completely replaced. |
| **Score Digitization Pipeline** | Monolithic "OMR Engine" returning MusicXML directly to playback | **Multi-Stage Pipeline**: Spring Boot (OmrController) → Audiveris → Python XML Cleaner → Python SATB Analyzer (music21) → Mobile Parser | Explicitly documents Audiveris transcription, semantic XML cleaning, SATB voice classification, confidence scoring, and event generation. |
| **Post-Upload Score Review & Editing** | Passive preview where user simply "confirms or re-uploads" | **Interactive Note Editor (ScoreReviewScreen.kt + interactive_editor.html)** — occurs **immediately after import/upload** before practice | Editing Mode is now correctly placed **directly after** the Import/Upload step in both diagrams. |
| **Dynamic Score Tracking** | Tone.js advances playhead in JS | **SyncManager.kt + HTML5 Canvas (renderer.html)** | Audio pacing loop via NativePlaybackEngine emits event IDs; SyncManager correlates with OSMD notehead coordinates; 60 FPS Canvas renders SATB glowing auras. |
| **Pitch Detection Engine** | FFT / Generic pitch engine | **YIN Algorithm (PitchComparator.kt)** | **Three-band feedback** (Green ≤ ±50¢, Yellow 51–100¢, Red > 100¢) confirmed in PitchFeedbackIndicator.kt. |
| **Practice Mode Gatekeeper** | Missing from early module flow | **SelectPracticeModeModal.kt** | Explicitly models mode selection ("Listen & Study" vs "Test Pitch & Practice") and RECORD_AUDIO permission gating. |
| **Playback Engine Class Name** | — | **MidiPlaybackEngine.kt** (composable) orchestrates NativePlaybackEngine and SyncManager internally | All diagram annotations now reference correct class names as found in codebase. |
| **Wireframes / Visuals** | Static low-fidelity wireframe sketches | **Production Screens & In-App Screenshot Guidelines** | Provides exact screen routes, component states, and capture checklists from the live mobile app. |

---

## 1. Module 1: Controlled OMR Digitization & Score Review

### 1.1 Revised Use Case Diagram

* **Boundary**: `Controlled OMR Digitization & Interactive Score Review`
* **Primary Actor**: `Choir Member`
* **Secondary Actors**: `OMR Server (Audiveris)`, `SATB Analysis Service (Python / music21)`

> **Correction from v1**: The Editing Mode (Interactive Score Review) is now correctly modeled as directly following the Upload step. After OMR processing returns the score (or after a direct MusicXML import), the system **always** routes the user through the ScoreReviewScreen before confirming and saving. This matches the actual app flow in UploadScoreScreen.kt and ScoreReviewScreen.kt.

```plantuml
@startuml
title Module 1: Controlled OMR Digitization & Score Review — Use Case Diagram

left to right direction
skinparam actorStyle awesome
skinparam packageStyle rectangle
skinparam backgroundColor #FFFFFF

skinparam usecase {
  BackgroundColor #F4F7FB
  BorderColor #2B579A
  ArrowColor #2B579A
  FontSize 11
}
skinparam actor {
  BackgroundColor #D6EAF8
  BorderColor #1B4F72
}

actor "Choir Member" as CM
actor "OMR Backend Server\n(Spring Boot / Audiveris)" as OMR
actor "SATB Analysis Engine\n(Python / music21)" as SATB

rectangle "Controlled OMR Digitization & Score Review Module" {
  ' Step 1: Import / Upload
  usecase "Upload Sheet Music" as UC1
  usecase "Capture via Camera (CameraX)" as UC1a
  usecase "Import Image / PDF from Storage" as UC1b
  usecase "Import Pre-existing MusicXML" as UC1c

  ' Step 2: OMR Pipeline (skipped for direct MusicXML)
  usecase "Process Score via OMR Pipeline" as UC2
  usecase "Transcribe Staves via Audiveris" as UC2a
  usecase "Clean MusicXML Semantics" as UC2b
  usecase "Analyze SATB Structure (music21)" as UC2c
  usecase "Generate MusicalEvent Stream" as UC2d

  ' Step 3: Editing Mode -- comes directly after Upload/Import
  usecase "Enter Editing Mode\n(ScoreReviewScreen)" as UC3
  usecase "Touch-Select Notehead (OSMD)" as UC3a
  usecase "Edit Pitch, Duration & Voice" as UC3b
  usecase "Delete Note / Multi-Rest" as UC3c

  ' Step 4: Confirm & Save
  usecase "Confirm & Save to Local Library" as UC4
}

' Actor connections
CM --> UC1
CM --> UC3
CM --> UC4

' Upload extends
UC1 ..> UC1a : <<extend>>
UC1 ..> UC1b : <<extend>>
UC1 ..> UC1c : <<extend>>

' Image/PDF path: Upload -> OMR Pipeline -> Editing Mode
UC1b ..> UC2 : <<include>>
UC1a ..> UC2 : <<include>>
UC2  ..> UC2a : <<include>>
UC2  ..> UC2b : <<include>>
UC2  ..> UC2c : <<include>>
UC2  ..> UC2d : <<include>>
UC2  ..> UC3  : <<include>>

' Direct MusicXML path: Import -> Editing Mode (bypass OMR)
UC1c ..> UC3 : <<include>>

' Editing Mode extends
UC3 ..> UC3a : <<extend>>
UC3 ..> UC3b : <<extend>>
UC3 ..> UC3c : <<extend>>

' Confirm follows Editing Mode
UC3 ..> UC4 : <<include>>

' Secondary actor connections
OMR --> UC2a
OMR --> UC2b
SATB --> UC2c
SATB --> UC2d

note right of UC2
  Concurrency controlled via
  server semaphore (max 2 permits)
  and SHA-256 deduplication cache.
end note

note bottom of UC3
  Hosted via interactive_editor.html
  with NoteEditorBottomSheet &
  MultiRestEditorBottomSheet.
  Editing Mode is entered immediately
  after Upload (image/PDF via OMR)
  or Import (direct MusicXML).
end note

@enduml
```

### 1.2 Revised Activity Diagram

* **Partitions**: `|Choir Member|`, `|Mobile Client (Android App)|`, `|Backend Server (Spring Boot + Python)|`

> **Correction from v1**: The non-standard `-> Jump to Review;` PlantUML syntax has been replaced with proper conditional flow. Both the direct MusicXML path and the OMR path now converge correctly at `Load ScoreReviewScreen`.

```plantuml
@startuml
title Module 1: OMR Digitization & Interactive Note Review — Activity Diagram

skinparam backgroundColor #FFFFFF
skinparam activityBackgroundColor #F8FAFC
skinparam activityBorderColor #1E3A8A
skinparam ArrowColor #1E3A8A
skinparam swimlaneTitleFontStyle bold

|Choir Member|
start
:Open VoxSight app and navigate to "Upload Score";

fork
  :Capture score photo via CameraX;
fork again
  :Pick score image (JPEG/PNG) or PDF;
fork again
  :Select existing MusicXML file;
end fork

|Mobile Client (Android App)|
if (Is direct MusicXML import?) then (yes)
  :Parse XML locally via MusicXmlParser;
  :Generate MusicalEvent stream;
else (image / PDF)
  :Validate image resolution & contrast;
  if (Image passes validation check?) then (yes)
    :Package file into HTTP multipart request;
    :POST /api/analyze to backend server;
  else (low quality / unreadable)
    :Display VoxErrorDialog;
    :Prompt user to recapture with clear lighting;
    stop
  endif

  |Backend Server (Spring Boot + Python)|
  :Compute SHA-256 checksum of payload;
  if (Result exists in analysis cache?) then (cache hit)
    :Retrieve cached OmrAnalysisResponse;
  else (cache miss)
    :Acquire OMR concurrency semaphore permit;
    :Save upload to temporary disk storage;
    :Execute Audiveris in batch headless mode;
    :Locate exported .mxl / .musicxml;
    :Execute Python musicxml_cleaner.py;
    :Execute Python satb_analyzer.py (music21);
    :Extract metadata, SATB parts & MusicalEvents;
    :Cache result in memory;
    :Release semaphore permit;
  endif
  :Return OmrAnalysisResponse (JSON + MusicXML);

  |Mobile Client (Android App)|
  :Receive MusicXML and event payload;
endif

' EDITING MODE: Entered immediately after upload/import
|Mobile Client (Android App)|
:Load ScoreReviewScreen (Editing Mode);
:Render vector sheet music via interactive_editor.html (OSMD);

|Choir Member|
if (User wishes to correct or edit notes?) then (yes)
  repeat
    :Touch-tap notehead on score display;
    |Mobile Client (Android App)|
    :Highlight selected note with green bounding box;
    :Open NoteEditorBottomSheet / MultiRestEditorBottomSheet;
    |Choir Member|
    :Adjust note pitch, octave, duration, or voice part;
    :Tap "Apply Changes";
    |Mobile Client (Android App)|
    :Update XML DOM and re-engrave measure in OSMD;
  repeat while (More note edits?) is (yes)
  -> no;
else (no edits needed)
endif

:Tap "CONFIRM & SAVE";
|Mobile Client (Android App)|
:Normalize timing with STANDARD_TPQ = 480;
:Save finalized score to LocalScoreManager cache;
:Prompt user to enter practice mode;
stop
@enduml
```

---

## 2. Module 2: Audio-Visual Selective Focus & Voice Part Isolation

### 2.1 Revised Use Case Diagram

* **Boundary**: `Audio-Visual Selective Focus Module`
* **Primary Actor**: `Choir Member`
* **Secondary Actors**: `Native Playback Engine (Android MIDI)`, `OSMD Render Engine (WebView)`

> **Correction from v1**: The `Choir Director` secondary actor from the original SRS has been removed — the implemented application does not include any Choir Director role. The metronome count-in feature is retained as it is present in the UI (PracticeScreen.kt).

```plantuml
@startuml
title Module 2: Audio-Visual Selective Focus — Use Case Diagram

left to right direction
skinparam actorStyle awesome
skinparam packageStyle rectangle
skinparam backgroundColor #FFFFFF

skinparam usecase {
  BackgroundColor #F4FBF7
  BorderColor #1E7E34
  ArrowColor #1E7E34
  FontSize 11
}
skinparam actor {
  BackgroundColor #D5F5E3
  BorderColor #196F3D
}

actor "Choir Member" as CM
actor "Native Playback Engine\n(NativePlaybackEngine.kt / Android MIDI)" as NPE
actor "OSMD Render Engine\n(WebView SVG / CSS)" as ORE

rectangle "Audio-Visual Selective Focus Module" {
  usecase "Select Assigned Voice Part (SATB)" as UC1
  usecase "Play Isolated Voice Track" as UC2
  usecase "Mute Non-Assigned Tracks (>=95% Attenuation)" as UC3
  usecase "Solo Specific Voice Part" as UC4
  usecase "Dim Non-Assigned Staves (<=20% Opacity)" as UC5
  usecase "Toggle Full Score Visual Focus" as UC6
  usecase "Adjust Tempo (BPM Stepper & Multipliers)" as UC7
  usecase "Toggle Metronome Count-in" as UC8
}

CM --> UC1
CM --> UC7
CM --> UC8

UC1 ..> UC2 : <<include>>
UC1 ..> UC3 : <<include>>
UC1 ..> UC5 : <<include>>

UC2 ..> UC4 : <<extend>>
UC5 ..> UC6 : <<extend>>

NPE --> UC2
NPE --> UC3
NPE --> UC4
NPE --> UC7

ORE --> UC5
ORE --> UC6

note right of NPE
  Playback is 100% native Kotlin
  via NativePlaybackEngine.kt.
  MidiPlaybackEngine.kt composable
  orchestrates it with SyncManager.
  The Android WebView has zero
  audio playback responsibility.
end note

note right of ORE
  Spatial stave dimensions and
  lyrics placement are preserved
  at all opacity levels.
end note

@enduml
```

### 2.2 Revised Activity Diagram

* **Partitions**: `|Choir Member|`, `|Native Playback Engine (NativePlaybackEngine.kt)|`, `|OSMD Render Engine (WebView / CSS)|`

```plantuml
@startuml
title Module 2: Audio-Visual Selective Focus — Activity Diagram

skinparam backgroundColor #FFFFFF
skinparam activityBackgroundColor #F0FFF4
skinparam activityBorderColor #15803D
skinparam ArrowColor #15803D
skinparam swimlaneTitleFontStyle bold

|Choir Member|
start
:Open saved score in Practice Screen;
:View SATB part selector chips (S, A, T, B);
:Tap assigned vocal part (e.g., Alto);

|Native Playback Engine (NativePlaybackEngine.kt)|
:Assign selected part to active solo channel (100% volume);
:Apply audio attenuation (>=95% or mute) to non-assigned parts;

|OSMD Render Engine (WebView / CSS)|
:Highlight selected staff at 100% opacity;
:Inject CSS opacity filter (<=20%) onto non-assigned SATB staves;
:Preserve measure alignment, clefs, and lyrics layout;

|Choir Member|
if (Adjust playback tempo?) then (yes)
  :Tap BPM stepper (- / +) or select speed (0.5x, 0.75x, 1x, 1.25x);
  |Native Playback Engine (NativePlaybackEngine.kt)|
  :Update coroutine microsecond timeline dispatcher delay;
else (no)
endif

|Choir Member|
if (Toggle audio mute/solo state?) then (yes)
  :Tap track Mute/Solo button on voice card;
  |Native Playback Engine (NativePlaybackEngine.kt)|
  :Dynamically route channel volume multipliers;
else (no)
endif

|Choir Member|
if (Toggle visual focus mode?) then (yes)
  :Toggle "Visual Focus" switch;
  |OSMD Render Engine (WebView / CSS)|
  :Reset all staves to 100% opacity (Full Score View);
else (no)
endif

|Choir Member|
:Tap Play button;
|Native Playback Engine (NativePlaybackEngine.kt)|
:Trigger native MIDI playback dispatch loop;
:Emit synchronized musical events to Module 3 (SyncManager);
stop
@enduml
```

---

## 3. Module 3: Dynamic Score Tracking & Real-Time Synchronization

### 3.1 Revised Use Case Diagram

* **Boundary**: `Dynamic Score Tracking Module`
* **Primary Actor**: `Choir Member`
* **Secondary Actors**: `Native Playback Engine`, `SyncManager`, `HTML5 Canvas Overlay`

> **No structural changes from v1** — this diagram accurately reflects the implementation.

```plantuml
@startuml
title Module 3: Dynamic Score Tracking — Use Case Diagram

left to right direction
skinparam actorStyle awesome
skinparam packageStyle rectangle
skinparam backgroundColor #FFFFFF

skinparam usecase {
  BackgroundColor #FEF9E7
  BorderColor #B7950B
  ArrowColor #B7950B
  FontSize 11
}
skinparam actor {
  BackgroundColor #FCF3CF
  BorderColor #7D6608
}

actor "Choir Member" as CM
actor "Native Playback Engine\n(NativePlaybackEngine.kt)" as NPE
actor "SyncManager\n(Bipartite Matcher)" as SM
actor "Canvas Overlay Engine\n(60 FPS HTML5 Canvas)" as COE

rectangle "Dynamic Score Tracking Module" {
  usecase "Initiate Synchronized Playback" as UC1
  usecase "Dispatch Microsecond Timeline Ticks" as UC2
  usecase "Match Event ID to OSMD Notehead Coordinates" as UC3
  usecase "Render Animated SATB Glowing Auras (60 FPS)" as UC4
  usecase "Auto-Scroll Viewport with Active Measure" as UC5
  usecase "Pause & Freeze Playhead Position" as UC6
  usecase "Seek to Measure / Note Scrubbing" as UC7
}

CM --> UC1
CM --> UC6
CM --> UC7

UC1 ..> UC2 : <<include>>
UC1 ..> UC3 : <<include>>
UC3 ..> UC4 : <<include>>
UC4 ..> UC5 : <<include>>

UC6 ..> UC4 : <<include>>
UC7 ..> UC3 : <<include>>

NPE --> UC2
NPE --> UC3
SM  --> UC3
COE --> UC4
COE --> UC5

note right of UC4
  Aura Theme Colors:
  Soprano: Pink
  Alto: Purple
  Tenor: Blue
  Bass: Green
  Latency target: <= 0.1s
end note

note bottom of UC7
  Tapping a measure updates
  both the native audio clock
  and visual playhead position.
end note

@enduml
```

### 3.2 Revised Activity Diagram

* **Partitions**: `|Choir Member|`, `|Native Playback Engine (NativePlaybackEngine.kt)|`, `|SyncManager (Correlation Engine)|`, `|Canvas Overlay & OSMD (renderer.html)|`

```plantuml
@startuml
title Module 3: Dynamic Score Tracking — Activity Diagram

skinparam backgroundColor #FFFFFF
skinparam activityBackgroundColor #FEF9E7
skinparam activityBorderColor #B7950B
skinparam ArrowColor #B7950B
skinparam swimlaneTitleFontStyle bold

|Choir Member|
start
:Open score in practice view;
:Tap Play;

|Native Playback Engine (NativePlaybackEngine.kt)|
:Initialize audio clock at t=0;
:Begin microsecond coroutine pacing loop;

|SyncManager (Correlation Engine)|
:Load pre-computed note element bounding boxes (OsmdNoteElement);
:Verify layoutHash compatibility;

repeat
  |Native Playback Engine (NativePlaybackEngine.kt)|
  :Trigger MIDI noteOn event for current tick;
  :Send active eventId to SyncManager;

  |SyncManager (Correlation Engine)|
  :Perform coordinate lookup for eventId;
  :Resolve note screen coordinates (x, y, w, h) & SATB voice;

  |Canvas Overlay & OSMD (renderer.html)|
  :Clear previous highlight frame;
  :Draw animated glowing aura around notehead\n(Pink=S, Purple=A, Blue=T, Green=B);
  :Smoothly auto-scroll WebView so active measure is centered;
  :Verify synchronization latency is <= 0.1s;

  |Choir Member|
  if (User taps Pause?) then (yes)
    |Native Playback Engine (NativePlaybackEngine.kt)|
    :Freeze audio clock;
    |Canvas Overlay & OSMD (renderer.html)|
    :Hold glowing aura at current note position;

    |Choir Member|
    if (User seeks to another measure?) then (yes)
      :Tap target measure on screen;
      |Native Playback Engine (NativePlaybackEngine.kt)|
      :Seek timeline clock to target measure timestamp;
      |SyncManager (Correlation Engine)|
      :Re-correlate notehead at new position;
      |Canvas Overlay & OSMD (renderer.html)|
      :Jump glowing aura and viewport to selected measure;
    else (no seek)
    endif

    |Choir Member|
    :Tap Resume;
    |Native Playback Engine (NativePlaybackEngine.kt)|
    :Resume timeline clock from current position;
  else (no pause)
  endif

  |Native Playback Engine (NativePlaybackEngine.kt)|
  :Advance clock to next note duration tick;

repeat while (Score has more musical events?) is (yes)
-> no;

|Canvas Overlay & OSMD (renderer.html)|
:Clear active glowing aura;

|Choir Member|
:Playback reaches score end;
:Display session complete transition;
stop
@enduml
```

---

## 4. Module 4: Real-Time Pitch Feedback & Assessment

### 4.1 Revised Use Case Diagram

* **Boundary**: `Real-Time Pitch Feedback Module`
* **Primary Actor**: `Choir Member`
* **Secondary Actors**: `Device Microphone (AudioRecord)`, `Pitch Detection Engine (YIN)`, `Pitch Comparator`

> **Correction from v1**: The feedback color bands have been corrected to **three bands** (Green / Yellow / Red) as confirmed in PitchFeedbackIndicator.kt (lines 52-54: GreenAccent, YellowAccent, RedAccent). The v1 diagram incorrectly showed only two bands (Green / Red).

```plantuml
@startuml
title Module 4: Real-Time Pitch Feedback — Use Case Diagram

left to right direction
skinparam actorStyle awesome
skinparam packageStyle rectangle
skinparam backgroundColor #FFFFFF

skinparam usecase {
  BackgroundColor #FDEDEC
  BorderColor #C0392B
  ArrowColor #C0392B
  FontSize 11
}
skinparam actor {
  BackgroundColor #FADBD8
  BorderColor #922B21
}

actor "Choir Member" as CM
actor "Device Microphone\n(AudioRecord Stream)" as MIC
actor "Pitch Detection Engine\n(YIN Algorithm)" as PDE
actor "Pitch Comparator &\nSession Buffer" as PCB

rectangle "Real-Time Pitch Feedback Module" {
  usecase "Select Practice Mode" as UC1
  usecase "Listen & Study Mode (Mic Disabled)" as UC1a
  usecase "Test Pitch Mode (Mic Active)" as UC1b

  usecase "Request & Verify RECORD_AUDIO Permission" as UC2
  usecase "Stream Microphone PCM Audio" as UC3
  usecase "Detect Sung Frequency (Hz) via YIN" as UC4
  usecase "Compare Sung Pitch to Target Note" as UC5
  usecase "Render 3-Band Feedback Indicator\n(Green <=+/-50c / Yellow 51-100c / Red >100c)" as UC6
  usecase "Buffer Pitch Attempts in Memory" as UC7
  usecase "Review Post-Session Pitch Summary\n(PracticeSummaryScreen)" as UC8
}

CM --> UC1
UC1 ..> UC1a : <<extend>>
UC1 ..> UC1b : <<extend>>

UC1b ..> UC2 : <<include>>
UC2  ..> UC3 : <<include>>
UC3  ..> UC4 : <<include>>
UC4  ..> UC5 : <<include>>
UC5  ..> UC6 : <<include>>
UC5  ..> UC7 : <<include>>

CM  --> UC8
UC7 ..> UC8 : <<include>>

MIC --> UC3
PDE --> UC4
PCB --> UC5
PCB --> UC7

note right of UC6
  Three-Band Feedback (confirmed in
  PitchFeedbackIndicator.kt):
  GREEN  = deviation <= +/-50 cents (match)
  YELLOW = deviation 51-100 cents (close)
  RED    = deviation > 100 cents (miss)
  Noise Warning state -> Yellow indicator
  Render latency: <= 0.5s
end note

note bottom of UC8
  Generates circular accuracy %,
  cents deviation graph, and
  flagged measure drill list.
end note

@enduml
```

### 4.2 Revised Activity Diagram

* **Partitions**: `|Choir Member|`, `|System & Practice ViewModel|`, `|AudioRecord & Pitch Engine (YIN)|`, `|Pitch Comparator & Buffer|`, `|UI Display (PracticeScreen & Summary)|`

> **Correction from v1**: Added the YELLOW (close, 51-100 cents) branch to the activity diagram, matching the three-band implementation in PitchFeedbackIndicator.kt.

```plantuml
@startuml
title Module 4: Real-Time Pitch Feedback & Summary — Activity Diagram

skinparam backgroundColor #FFFFFF
skinparam activityBackgroundColor #FDEDEC
skinparam activityBorderColor #C0392B
skinparam ArrowColor #C0392B
skinparam swimlaneTitleFontStyle bold

|Choir Member|
start
:Select score from library;

|System & Practice ViewModel|
:Display SelectPracticeModeModal ("Gatekeeper");

|Choir Member|
if (Select practice mode) then ("Listen & Study")
  |System & Practice ViewModel|
  :Set isMicrophoneEnabled = false;
  :Launch PracticeScreen with pitch detection disabled;
  stop
else ("Test Pitch & Practice")
  |System & Practice ViewModel|
  :Verify RECORD_AUDIO Android permission;
  if (Permission granted?) then (yes)
    :Set isMicrophoneEnabled = true;
    :Initialize AudioRecord stream (44.1 kHz);
  else (denied)
    :Display permission rationale dialog;
    :Fall back to Listen Mode;
    stop
  endif
endif

|Choir Member|
:Begin singing along with score playback;

repeat
  |AudioRecord & Pitch Engine (YIN)|
  :Capture real-time PCM audio buffer;
  :Execute YIN algorithm to extract dominant frequency (Hz) & clarity;

  if (Valid vocal signal detected?) then (yes)
    |Pitch Comparator & Buffer|
    :Query active target note from NativePlaybackEngine;
    :Convert expected note MIDI pitch to reference Hz;
    :Calculate cents deviation = 1200 * log2(detectedHz / targetHz);

    if (Deviation <= +/- 50 cents?) then (in-tune / GREEN)
      |UI Display (PracticeScreen & Summary)|
      :Render GREEN Feedback Indicator (On-pitch / Match);
    else if (Deviation 51-100 cents?) then (close / YELLOW)
      |UI Display (PracticeScreen & Summary)|
      :Render YELLOW Feedback Indicator (Close - adjust pitch);
    else (out of tune / RED)
      |UI Display (PracticeScreen & Summary)|
      :Render RED Feedback Indicator (Miss - Sharp or Flat);
    endif

    |Pitch Comparator & Buffer|
    :Verify UI display latency is <= 0.5s;
    :Record PitchAttempt(measureNumber, targetHz, detectedHz, cents, match);

    |Choir Member|
    :Self-correct vocal pitch based on visual feedback;

  else (silence / rest)
    |UI Display (PracticeScreen & Summary)|
    :Display neutral / inactive feedback state;
  endif

repeat while (Playback active & user singing?) is (yes)
-> no;

|System & Practice ViewModel|
:Aggregate session pitch attempts via getSessionSummary();
:Calculate overall accuracy percentage;
:Identify flagged measures falling below 70% threshold;
:Navigate to PracticeSummaryScreen;

|UI Display (PracticeScreen & Summary)|
:Render Circular Accuracy Gauge (e.g., 88%);
:Render In-Tune vs Miss breakdown cards;
:Render list of Flagged Measures with repeat drill buttons;

|Choir Member|
if (Review options) then ("Practice Again")
  :Tap "Practice Again" or specific flagged measure;
  :Restart focused practice loop;
else ("Finish")
  :Tap "Done";
  :Return to Upload / Score Library Screen;
endif

stop
@enduml
```

---

## 5. In-App Screenshot Guide (Replacing Legacy Wireframes)

Because all core modules in VoxSight are fully implemented, functional screenshots captured directly from an Android device or emulator provide a far more accurate representation than conceptual mockups. Use the table below to capture and embed each screen.

### Screenshot Inventory & Capture Checklist

| Figure # | Documentation Section | Screen Name & Route in Code | Exact Source File | Specific State to Capture |
| :---: | :--- | :--- | :--- | :--- |
| **Figure 1** | Module 1: Controlled OMR Digitization | **Upload & Score Library Screen** | [UploadScoreScreen.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/upload/UploadScoreScreen.kt) | Top app bar with user profile icon & tour guide button. "Take Photo" (CameraX) and "Upload Music Sheet" action cards. Populated "Your Scores" list with saved score cards. |
| **Figure 2** | Module 1: Editing Mode | **Interactive Score Review & Note Editor Screen** | [ScoreReviewScreen.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/upload/ScoreReviewScreen.kt) / [NoteEditorBottomSheet.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/upload/NoteEditorBottomSheet.kt) | Top bar with "CANCEL" and "CONFIRM & SAVE" buttons. OSMD vector score at 0.55 zoom. One active note highlighted with green touch box. Open bottom sheet showing pitch, octave, duration chips, and "Delete Note". |
| **Figure 3** | Module 4: Real-Time Pitch Feedback | **Practice Mode Selection Modal** | [SelectPracticeModeModal.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/practice/SelectPracticeModeModal.kt) | Clean dialog overlay (90% screen width, rounded corners). "Listen & Study" card (Headphones icon). "Test Pitch & Practice" card (Microphone icon). |
| **Figure 4** | Modules 2 & 3: Focus & Tracking | **Unified Interactive Practice Screen** | [PracticeScreen.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/practice/PracticeScreen.kt) | Active playback with selected vocal part highlighted. Stave dimming visible. Real-time glowing SATB aura on active notehead. Live PitchFeedbackIndicator showing GREEN / YELLOW / RED. Bottom transport controls: BPM stepper, tempo multiplier, Play/Pause. |
| **Figure 5** | Module 4: Real-Time Pitch Feedback | **Post-Session Practice Summary Screen** | [PracticeSummaryScreen.kt](file:///c:/Users/elvin/OneDrive/Documents/Capstone%20-%20Final/VoxSight/mobile/app/src/main/java/com/cit/kaido/voxsight/ui/screens/practice/PracticeSummaryScreen.kt) | Circular Accuracy Percentage Donut (e.g., 85%). Pitch breakdown metrics (In-Tune, Miss, avg cents deviation). Scrollable Flagged Measures list. "Practice Again" and "Back to Library" buttons. |

---

### Step-by-Step Instructions to Capture Screenshots

1. **Launch the Application**:
   * Open the project in Android Studio and run on an Android device or emulator (Pixel 6/7/8 recommended).
   * Ensure the Spring Boot backend (voxsight/) is running (`.\\mvnw.cmd spring-boot:run`) or connected to the production cloud endpoint.

2. **Capturing Figure 1 (Upload & Library)**:
   * Log in to reach the main home screen (UploadScoreScreen).
   * Ensure at least one score exists in the library.
   * Use device screenshot buttons (Power + Volume Down) or Android Studio's screenshot tool.

3. **Capturing Figure 2 (Editing Mode — Score Review & Note Editor)**:
   * Upload an image or open a score to reach ScoreReviewScreen — this is the **Editing Mode** screen that appears **immediately after** OMR processing or direct MusicXML import.
   * Tap any notehead on the sheet music to bring up the green selection bounding box and trigger NoteEditorBottomSheet.
   * Take the screenshot with the bottom sheet raised and note options visible.

4. **Capturing Figure 3 (Mode Selection Gatekeeper)**:
   * From the score library, tap a score card to start practice.
   * The SelectPracticeModeModal dialog will appear on screen.
   * Take the screenshot while both "Listen & Study" and "Test Pitch & Practice" cards are displayed.

5. **Capturing Figure 4 (Unified Practice Screen)**:
   * Select "Test Pitch & Practice" (grant microphone permission if prompted).
   * Press Play and sing the target pitch so the live PitchFeedbackIndicator is active.
   * Capture the screen during active playback to demonstrate the glowing note aura, stave dimming, and the three-color feedback indicator simultaneously.

6. **Capturing Figure 5 (Practice Summary)**:
   * Allow playback to complete or tap Pause then "End Session".
   * The PracticeSummaryScreen will render with performance analytics and flagged measures.
   * Take the screenshot ensuring the circular gauge and flagged measure cards are in view.

---

## Appendix: Summary of All Corrections Made in v2

| Diagram | v1 Issue | v2 Fix |
| :--- | :--- | :--- |
| **Module 1 Use Case** | "Editing Mode" (ScoreReviewScreen) was not correctly positioned after Import/Upload | Added UC3 "Enter Editing Mode" as a mandatory step after both the OMR path (UC2 ..> UC3) and the direct MusicXML import path (UC1c ..> UC3) |
| **Module 1 Use Case** | UC4 "Confirm & Save" actor connection was incomplete | Added CM --> UC4 to reflect user explicitly tapping "CONFIRM & SAVE" |
| **Module 1 Activity** | Non-standard `-> Jump to Review;` PlantUML syntax for MusicXML direct path | Replaced with proper conditional flow that converges both paths at "Load ScoreReviewScreen" |
| **Module 2 Use Case** | Choir Director actor was listed (from original SRS) but does not exist in implementation | Removed Choir Director; kept only actors present in actual codebase |
| **Module 2 Activity** | Referenced generic "Kotlin / Android MIDI" engine | Updated all lane labels to reference NativePlaybackEngine.kt specifically |
| **Module 4 Use Case** | Only GREEN / RED two-band feedback was shown | Updated to three-band (GREEN / YELLOW / RED) confirmed in PitchFeedbackIndicator.kt |
| **Module 4 Activity** | Missing YELLOW (51-100 cents) branch in the activity flow | Added else if (Deviation 51-100 cents?) branch rendering YELLOW indicator |
| **Screenshot Guide** | Figure 2 caption did not mention "Editing Mode" or clarify when this screen appears | Updated caption and capture instructions to state Editing Mode appears immediately after upload/import |
