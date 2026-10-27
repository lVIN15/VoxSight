# Software Requirements Specifications
## for VoxSight

**Institution:** Cebu Institute of Technology – University  
**College:** College of Computer Studies

---

## Change History

| Name | Date | Version | Description |
|------|------|---------|-------------|
| VoxSight Initial Software Requirements Specification | 04/20/2026 | 0.1 | Initial draft baseline. |
| Refactored Software Requirements Specification | 09/18/2026 | 0.2 | Aligned with updated proposal scope, native MIDI architecture, and NFR metrics. |
| Production Aligned Software Requirements Specification | 10/06/2026 | 0.3 | Integrated Dynamic Vocal Part Selection modal, Past Vocal Takes subsystem, Interactive Score Review editing use case, and corrected 3-band pitch feedback tolerances. |

---

## Table of Contents

1. [Introduction](#1-introduction)
   - 1.1 [Purpose](#11-purpose)
   - 1.2 [Scope](#12-scope)
   - 1.3 [Definitions, Acronyms and Abbreviations](#13-definitions-acronyms-and-abbreviations)
   - 1.4 [References](#14-references)
2. [Overall Description](#2-overall-description)
   - 2.1 [Product Perspective](#21-product-perspective)
   - 2.2 [User Characteristics](#22-user-characteristics)
   - 2.3 [Constraints](#23-constraints)
   - 2.4 [Assumptions and Dependencies](#24-assumptions-and-dependencies)
3. [Specific Requirements](#3-specific-requirements)
   - 3.1 [External Interface Requirements](#31-external-interface-requirements)
     - 3.1.1 [Hardware Interfaces](#311-hardware-interfaces)
     - 3.1.2 [Software Interfaces](#312-software-interfaces)
     - 3.1.3 [Communications Interfaces](#313-communications-interfaces)
   - 3.2 [Functional Requirements](#32-functional-requirements)
     - [Module 1: Controlled OMR Digitization & Score Review](#module-1-controlled-omr-digitization--score-review)
     - [Module 2: Audio-Visual Selective Focus & Voice Part Isolation](#module-2-audio-visual-selective-focus--voice-part-isolation)
     - [Module 3: Sight-Reading Training: Dynamic Score Tracking](#module-3-sight-reading-training-dynamic-score-tracking)
     - [Module 4: Sight-Reading Training: Practice Gatekeeper & Real-Time Pitch Feedback](#module-4-sight-reading-training-practice-gatekeeper--real-time-pitch-feedback)
   - 3.3 [Non-Functional Requirements](#33-non-functional-requirements)
     - 3.3.1 [Performance](#performance)
     - 3.3.2 [Security](#security)
     - 3.3.3 [Reliability](#reliability)

---

## 1. Introduction

### 1.1 Purpose

The purpose of this document is to provide a detailed description of the VoxSight system, a mobile application designed to improve independent sight-reading practice efficiency and reduce rehearsal preparation difficulty for amateur choir members. This document serves as a reference for developers, project managers, and stakeholders to ensure alignment with the functional and non-functional requirements.

---

### 1.2 Scope

VoxSight is a mobile application targeting the **Android platform**. The system enables amateur choir members to independently practice their vocal parts on custom sheet music. The system supports standard choral sheet music images (JPEG/PNG/PDF) and four-part SATB voice structures.

The system improves independent choir rehearsal preparation through a comprehensive suite of modules:

- Controlled OMR Digitization
- Automated SATB Analysis
- Interactive Score Review & Note Editing
- Score Rendering & Focus
- Rehearsal Studio Playback
- Dynamic Score Tracking
- Real-Time Pitch Feedback & Wait-for-Pitch Mode
- Practice Analytics & Performance Vocal Take Playback
- Authentication & Security
- Profile & Settings
- Version Control
- Library & Guide
- Web Showcase & DevOps

All audio playback responsibilities are moved natively to the Android application layer, restricting the WebView purely to vector sheet music rendering.

---

### 1.3 Definitions, Acronyms and Abbreviations

| Term | Definition |
|------|-----------|
| **MIDI** (Musical Instrument Digital Interface) | A technical standard and digital language that allows computers, software, and musical instruments to communicate and generate sound (e.g., the underlying code instructions that tell an app to play a specific pitch for a certain duration). |
| **OMR** (Optical Music Recognition) | A technology that scans physical sheet music and converts it into a digital format that computers can read, edit, and play back (e.g., using an app camera feature to take a picture of a printed choir score and converting it into playable audio). |
| **SATB** (Soprano, Alto, Tenor, Bass) | The standard four-part vocal arrangement used in choral music, organized from the highest to the lowest pitch. |
| **Soprano (S)** | The highest vocal range, typically sung by females, which usually carries the main melody (Pink / `#E91E63`). |
| **Alto (A)** | The lower female vocal range that provides harmony directly below the soprano (Purple / `#9C27B0`). |
| **Tenor (T)** | The higher male vocal range that provides mid-level harmony and support (Blue / `#2196F3`). |
| **Bass (B)** | The lowest male vocal range that provides the foundational depth of the choir's harmony (Green / `#4CAF50`). |
| **MusicXML** | A digital file format used for representing musical notation in a structured, machine-readable form. |
| **Pitch Detection** | The process of identifying the fundamental frequency of a sound signal (e.g., a sung note). |
| **Frequency (Hz)** | The number of sound wave cycles per second, which determines the perceived pitch of a note. |
| **Latency** | The delay between an input (e.g., singing) and the system's response (e.g., visual feedback). |
| **Cents (Pitch Measurement)** | A unit used to measure small pitch differences; 100 cents equals one semitone. |
| **Sight-Reading** | The ability to read and perform music from notation without prior practice. |
| **Score Tracking** | The synchronization of visual note highlighting with audio playback in real time. |
| **Selective Focus (Audio-Visual)** | A system feature that prioritizes a selected vocal part by significantly suppressing non-assigned audio tracks and dimming non-relevant notation to reduce auditory and visual cognitive load during practice. |
| **Pitch Feedback** | Real-time visual indication of whether a sung note matches the expected pitch via a 3-band color model (Green ≤ ±50c, Yellow 51–100c, Red > 100c). |
| **Vocal Synthesis** | The artificial generation of reference tones using digital sound processing. |
| **YIN Algorithm** | A fundamental frequency estimator used for pitch tracking over a 22,050 Hz PCM audio stream. |
| **Cent Deviation** | A logarithmic measure of pitch accuracy between a detected singing frequency and a target note frequency ($1200 \times \log_2(\text{detectedHz} / \text{targetHz})$). |
| **Octave Folding** | A normalization process that folds pitch deviation into the $[-600, +600]$ cents range to prevent vocal register disparities (e.g., female vs. male octaves) from generating false pitch errors. |
| **Score Take Recording** | An audio recording of the user's vocal performance captured synchronously with score playback, stored locally for review and playback. |

---

### 1.4 References

This SRS is based on the following documents and sources:

1. VoxSight Refactored Project Proposal, Team 2526-sem2-it332-51.
2. IEEE Std 830-1998 Recommended Practice for Software Requirements Specifications.
3. MusicXML 3.1 / 4.0 Specification.
4. Android Developer Documentation (`android.media` and `android.media.midi` API references).

---

## 2. Overall Description

### 2.1 Product Perspective

VoxSight is a mobile-first sight-reading practice application designed to support independent rehearsal preparation for amateur choir members. The system follows a **client-server architecture** in which uploaded sheet music images are processed through a cloud-based Optical Music Recognition (OMR) engine that converts physical scores into MusicXML data. The mobile client manages score rendering, selective playback, synchronized visual tracking, and real-time pitch feedback during user practice sessions.

---

### 2.2 User Characteristics

**Primary Users:** Amateur choir members, particularly those without prior instrumental training, from community, church, or collegiate ensembles.

---

### 2.3 Constraints

- OMR accuracy is dependent on image quality, lighting conditions, and notation clarity; ornate or handwritten scores are not supported.
- The application strictly requires standard "open score" SATB notation where each voice occupies an independent staff (condensed scores and complex divisi lines are excluded).
- Pitch detection covers standard vocal ranges – Soprano (C4–G5), Alto (G3–D5), Tenor (C3–G4), and Bass (E2–C4) – and performs optimally in quiet environments.
- Vocal synthesis fidelity approximates human timbre using General MIDI (GM) Piano synthesis to provide stable, unambiguous intonation reference.
- The application is exclusively developed for the Android OS; iOS is out of scope due to prohibitive development costs.
- The OMR engine requires all uploaded pages to contain readable notation. Empty or severely corrupted pages will cause the parsing sequence to fail.

---

### 2.4 Assumptions and Dependencies

- The primary dependency is on consistent, reliable network access for the cloud-based OMR engine to process and return MusicXML data.
- The system operates under the strict assumption that uploaded sheet music files are standardized, high-contrast digital prints without handwritten markings, annotations, or severe image skew.
- The system depends on **Supabase PostgreSQL** for user account management and data persistence.
- Authentication workflows rely on the **Android Credential Manager API** for Google Sign-In and the **Facebook SDK** for Facebook Login.
- Native audio playback relies on Android's built-in Native MIDI Synthesis configured with a General MIDI (GM) Piano patch.
- Client-server compatibility assumes the presence of a server-side version interceptor that returns an HTTP 426 status for outdated clients, triggering a non-dismissible blocking update UI.

---

## 3. Specific Requirements

### 3.1 External Interface Requirements

#### 3.1.1 Hardware Interfaces

The system requires access to compatible Android device hardware components including:

- **Device Microphone** – for Real-Time Pitch Feedback (sampled at 22,050 Hz PCM).
- **Camera (CameraX)** – for OMR multi-page image capture.
- **Internal Storage / Scoped Storage** – for importing existing score images, storing local MusicXML files, and saving recorded vocal takes.

#### 3.1.2 Software Interfaces

The system interfaces with the Android OS and utilizes the MusicXML data format for digital score representation. External software interfaces include the integrated Optical Music Recognition (OMR) processing engine (Audiveris, Spring Boot), Python music21 SATB analysis scripts, and OpenSheetMusicDisplay (OSMD) for vector notation rendering.

#### 3.1.3 Communications Interfaces

A stable Internet connection (Wi-Fi or cellular data) is required for cloud-based OMR image processing and MusicXML retrieval to upload sheet music images to the OMR engine and download the resulting digital score data over TLS 1.3 encrypted HTTPS.

---

### 3.2 Functional Requirements

---

### Module 1: Controlled OMR Digitization & Score Review

Handles user upload (camera photo or file import) of sheet music, processes the physical score into playable MusicXML data via a multi-stage pipeline, enables interactive post-processing score editing, and structurally isolates distinct SATB parts.

---

#### Use Case: Upload Sheet Music

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member |
| **Description** | The user provides a sheet music image or a batch of multiple images/PDF pages (JPEG/PNG/PDF) to the system either by capturing photos with the device camera or importing existing files from local storage/gallery. |
| **Preconditions** | The app is open; internet connection is available; the user has sheet music accessible. |
| **Postconditions** | Image(s) are validated and submitted as a batch payload to the OMR engine to be processed into a single continuous score. |

**Main Flow:**
1. The user opens the application and navigates to "New Score."
2. The system provides multi-page batch capture utilizing an accelerometer tilt guide, tap-to-focus, and flashlight toggles.
3. The user captures or imports the score.
4. The system applies client-side image pre-processing, including a viewfinder crop, downscaling to 300 DPI, and grayscale contrast enhancement.
5. A client-side gatekeeper evaluates the file and rejects non-vocal (non-SATB/instrumental) scores before transmission.
6. The system submits the validated payload to the backend.

**Alt / Exception:**
- **A1** – Image fails quality check: System warns user and prompts recapture.
- **E1** – No internet connection: System displays offline error; upload is blocked.
- **E2** – Unsupported file format: System rejects the file and notifies the user.
- **E3** – Blank/Unreadable Page Detected: System halts processing, rejects the batch, and notifies the user to remove the empty page before re-uploading.

---

#### Use Case: Process Score via OMR Engine

| Field | Details |
|-------|---------|
| **Actors** | Secondary: OMR Engine (server-side) |
| **Description** | The OMR engine receives the uploaded image and performs optical recognition to extract musical semantics – pitches, rhythms, note durations, and structural elements. |
| **Preconditions** | The image has been successfully uploaded to the server. Network connection is active. |
| **Postconditions** | Raw musical data is extracted and ready for MusicXML generation. Target processing time is ≤ 10 seconds per standard page under stable network conditions, with system resource load strictly capped at ≤ 75% CPU/Memory utilization during the parsing phase. |

**Main Flow:**
1. Backend processing utilizes **Audiveris OMR**, a `musicxml_cleaner.py` script, and a `satb_analyzer.py` script powered by music21.
2. The system enforces a fair FIFO semaphore that limits concurrent OMR jobs to a maximum of 2.
3. An in-memory SHA-256 hash cache checks the payload to prevent redundant transcriptions.
4. Following OMR processing, the system generates valid MusicXML and downloads the score to the client.

**Alt / Exception:**
- **A1** – Low-clarity image (handwritten/ornate): Engine returns partial data with confidence flags; user is warned of potential errors.
- **E1** – Processing timeout (>10s per page): System notifies user; user may retry with a clearer image.

---

#### Use Case: Generate MusicXML & Separate SATB Parts

| Field | Details |
|-------|---------|
| **Actors** | Secondary: OMR Engine; Primary: Choir Member (receives result) |
| **Description** | The system converts extracted musical data into the MusicXML standard format and structurally separates the four SATB parts into individually addressable tracks. |
| **Preconditions** | OMR processing has completed successfully under the controlled input assumption that uploaded sheet music is a standardized, high-contrast digital print without handwritten markings or severe image skew. |
| **Postconditions** | A valid MusicXML file is stored on the device with four independently accessible SATB tracks. Score is immediately routed to Interactive Score Review. |

**Main Flow:**
1. System receives raw musical data from the OMR engine.
2. System maps each detected staff to an SATB voice type (S/A/T/B).
3. System generates a MusicXML document with separate part elements per voice.
4. System validates the generated MusicXML against the standard schema.
5. System downloads and stores MusicXML on the device in private storage.
6. The system automatically launches the **Interactive Score Review** screen before saving to the permanent library.

**Alt / Exception:**
- **A1** – Fewer than 4 staves detected: System flags ambiguous parts; user may manually assign voices during score review.
- **E1** – MusicXML validation fails: System logs error and prompts user to re-upload.

---

#### Use Case: Interactive Score Review & Note Editing

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member |
| **Description** | Following OMR processing or direct MusicXML import, the user reviews the rendered notation in an interactive editor. The user can inspect flagged low-confidence notes, correct pitch, modify duration, change voice assignments, and delete unwanted elements before confirming the score. |
| **Preconditions** | MusicXML data has been generated or imported into client memory; `ScoreReviewScreen` is loaded. |
| **Postconditions** | Modified musical notes are reflected in the MusicXML score; updated audio events are regenerated; score is permanently saved to the local library upon user confirmation. |

**Main Flow:**
1. System renders the digitized score in `ScoreReviewScreen` using `interactive_editor.html` (OSMD).
2. The user taps a notehead to select it. The editor displays an editing panel with note parameters (pitch name, octave, duration, assigned voice).
3. The user adjusts pitch by semitone stepper or keyboard picker.
4. The user adjusts duration (whole, half, quarter, eighth, sixteenth) or toggles rests.
5. The user can tap "Delete Note" to remove erroneous OMR artifacts.
6. The user taps "Audition" to preview playback of edited bars using the native playback engine.
7. The user taps "Confirm & Save". The system commits the final MusicXML and generated event stream to the library.

**Alt / Exception:**
- **A1** – User discards changes: System confirms cancellation and returns to Home without saving.
- **A2** – Direct MusicXML Import: User bypasses OMR image processing; system routes directly to Score Review for optional inspection before saving.

> *Figure 1: The Digitize Score & Interactive Score Review interfaces handling user uploads, image processing states, and interactive note correction before local storage.*

---

### Module 2: Audio-Visual Selective Focus & Voice Part Isolation

Visual focus utilizes SATB voice color coding:
- **Pink (`#E91E63`)** – Soprano
- **Purple (`#9C27B0`)** – Alto
- **Blue (`#2196F3`)** – Tenor
- **Green (`#4CAF50`)** – Bass

CSS opacity stave dimming is applied to **≤ 20%** for non-selected parts.

---

#### Use Case: Select Voice Part and Apply Focus

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member |
| **Description** | The user selects their assigned voice type (Soprano, Alto, Tenor, or Bass) from the score. The system immediately applies audio and visual focus based on the selection. |
| **Preconditions** | Module 1 has completed successfully; MusicXML with SATB tracks is loaded on the device. |
| **Postconditions** | Selected parts are highlighted at 100% opacity within an isolation processing time of ≤ 3 seconds; non-assigned parts are dimmed to ≤ 20% opacity; non-assigned audio tracks are significantly suppressed. |

**Main Flow:**
1. The system displays the digitized score with SATB part selector (S / A / T / B chips).
2. The user taps their assigned voice part (or arrives pre-selected from the Practice Mode Gatekeeper modal).
3. The system highlights the selected staff at 100% opacity.
4. The system reduces opacity of the three unassigned staves to ≤ 20% via CSS injection in `renderer.html`.
5. The system routes audio synthesis to prioritize the selected part's track.
6. The system applies significant audio attenuation (≥ 95% attenuation) to non-assigned tracks.
7. Score remains spatially intact with measure alignment preserved.

**Alt / Exception:**
- **A1** – User changes part mid-session: System re-applies audio and visual focus to new selection immediately.
- **E1** – Score has fewer than 4 staves detected: System applies focus to available parts only and flags missing staves.

---

#### Use Case: Play Assigned Part (Native Audio Playback)

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member; Secondary: Native Playback Engine |
| **Description** | The system executes playback of the assigned vocal part using native device MIDI synthesis mapped to the MusicXML pitch and duration data. All reference tones are generated using a General MIDI (GM) Piano patch to provide clear, stable intonation targets. |
| **Preconditions** | A vocal part has been selected; device audio is active. |
| **Postconditions** | The selected part is audible using clear MIDI piano tones; non-assigned tracks are significantly attenuated (≥ 95% mute); playback is tightly synchronized with the Dynamic Score Tracking module. |

**Main Flow:**
1. The user taps the Play button on the transport controls.
2. All audio playback is handled strictly natively via the Android application layer; the WebView is entirely bypassed for audio responsibility.
3. The Native Playback Engine triggers Android's built-in Native MIDI Synthesis configured with a General MIDI (GM) Piano patch.
4. The engine maintains stability using microsecond coroutine pacing and event coalescing (grouping simultaneous chord events within a 5–10 ms window so chords strike cleanly).
5. The engine actively monitors note duration and enforces an audio safety layer, limiting maximum note sustain to 5 seconds.

**Alt / Exception:**
- **A1** – User pauses playback: System halts audio and visual tracking at current position.
- **A2** – User adjusts tempo: Engine scales event dispatch intervals dynamically without pitch shifting.

---

#### Use Case: Toggle Audio Suppression / Visual Focus

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member |
| **Description** | The user can manually toggle audio suppression and visual opacity reduction on/off to switch between isolated practice mode and full-score review mode at any time. |
| **Preconditions** | A vocal part is selected and playback is active or paused. |
| **Postconditions** | Audio suppression and/or visual dimming state is toggled. All staves return to 100% opacity in full-view mode. |

**Main Flow:**
1. The user taps "Audio Mute" toggle or "Visual Focus" toggle.
2. The system reads the current toggle state.
3. If **disabling**: System restores all tracks to equal volume and all staves to 100% opacity.
4. If **enabling**: System re-applies significant audio suppression (≥ 95%) and ≤ 20% visual dimming to non-assigned parts.
5. System updates UI indicators to reflect current mode.

**Alt / Exception:**
- **A1** – Toggle triggered during active playback: Transition is applied seamlessly without interrupting playback.

> *Figure 2: The primary Interactive Practice Screen demonstrating Audio-Visual Selective Focus (Module 2). Note: This unified screen also houses the UI elements for Dynamic Score Tracking (Module 3) and Real-Time Pitch Feedback (Module 4).*

---

### Module 3: Sight-Reading Training: Dynamic Score Tracking

The rendering engine features microsecond event dispatching matching OSMD note coordinates to render animated **60 FPS glowing SATB notehead auras** with auto-scrolling.

---

#### Use Case: Initiate Playback with Tracking

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member; Secondary: Playback Engine |
| **Description** | User initiates playback and the system synchronizes a visual playhead against the MusicXML timing data, highlighting the active note(s) in real-time as audio plays. |
| **Preconditions** | Digitized score loaded; vocal part selected (Module 2); audio engine initialized. |
| **Postconditions** | Active note(s) are highlighted within ≤ 0.1s of the corresponding audio event. Score scrolls automatically to keep active measures visible. |

**Main Flow:**
1. User taps Play.
2. System parses MusicXML timestamp and duration data for all notes.
3. Playback engine starts internal audio clock at t=0.
4. At each note's start timestamp, the render engine highlights the corresponding notehead on screen with an animated glowing aura.
5. System synchronizes visual note highlighting with audio playback while maintaining a target latency of **≤ 0.1 seconds**.
6. Previous note highlight is removed as the next note begins.
7. Score view auto-scrolls to keep the active measure centered on screen.

**Alt / Exception:**
- **E1** – Synchronization drift >0.1s: System automatically re-syncs playhead to audio clock position.
- **A1** – User seeks to a different measure / note: Playhead jumps to target position; audio pacing and visual highlighting re-sync instantly.
- **A2** – User pauses: Audio clock halts and visual note highlight freezes on active notehead.

> **Wireframe Note:** The Dynamic Score Tracking playhead is rendered directly on the primary practice interface. Please refer to Figure 2 in Module 2 to view the visual synchronization of the playhead against the sheet music.

---

### Module 4: Sight-Reading Training: Practice Gatekeeper & Real-Time Pitch Feedback

Practice mode gating uses a **two-step Gatekeeper Modal** (`SelectPracticeModeModal.kt`) enforcing dynamic SATB vocal part detection and deferred microphone permissions, plus historical audio take playback. Real-time pitch tracking uses the **YIN pitch detection algorithm** over a **22,050 Hz PCM audio stream** with a vibrato confidence threshold of **0.55**. Pitch feedback implements a **3-band cent-deviation model** and supports interactive **Wait-for-Pitch mode**.

---

#### Use Case: Pre-Practice Mode & Vocal Part Selection (Gatekeeper Modal)

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member; Secondary: Local Audio Storage |
| **Description** | Before loading the practice interface, the system gates the session with a modal presenting practice mode options ("Listen Only" vs. "Test Pitch"), dynamic score-aware vocal part selection (SATB), and a historical audio take browser with speed controls. |
| **Preconditions** | The user selects a digitized score from the library or upload flow. |
| **Postconditions** | The user's practice mode, assigned vocal part, and microphone hardware states are configured, preventing audio race conditions before entering the practice screen. |

**Main Flow:**
1. The user opens a score for practice.
2. The system displays `SelectPracticeModeModal`. The system queries `ScoreRecordingManager` for existing recorded takes for this score.
3. If the user chooses **"Past Vocal Takes"**:
   - The modal transitions to the take list view (`isViewingRecordings = true`).
   - The user browses historical audio takes displaying formatted date and accuracy percentage pills (e.g., "85% Acc").
   - The user can play/pause, scrub via seek slider, adjust playback speed (`0.8x`, `1.0x`, `1.2x`), or delete takes via `PerformanceAudioPlayer`.
   - The user taps "BACK TO MODES" to return.
4. If the user chooses **"Listen Only"**:
   - The system sets `isMicrophoneEnabled = false`.
   - The system navigates to `PracticeScreen` without microphone permissions or pitch indicators.
5. If the user chooses **"Test Pitch"**:
   - The modal transitions to the **"Select Vocal Part"** view (`isSelectingVocalPart = true`).
   - The system runs `detectAvailableParts(score)` to extract vocal parts present in the score (Soprano, Alto, Tenor, Bass) with pitch ranges and clefs.
   - The user selects their assigned vocal part (e.g., Tenor).
   - The system records `PracticeViewModel.setSelectedVoicePart(chosenPart)`.
   - The system checks Android `RECORD_AUDIO` permission. If ungranted, the system launches the permission request launcher.
   - Upon permission grant, the system sets `isMicrophoneEnabled = true` and launches `PracticeScreen`.

**Alt / Exception:**
- **A1** – Permission Denied: If the user denies microphone permission, the system displays a Toast notification ("Microphone permission required for Pitch Tracking") and keeps the user in modal or falls back to Listen Only mode.
- **A2** – Score without standard SATB names: `detectAvailableParts` falls back to general staves with descriptive labels.

---

#### Use Case: Real-Time Pitch Feedback & Session Evaluation

| Field | Details |
|-------|---------|
| **Actors** | Primary: Choir Member; Secondary: Pitch Detection Engine / Device Microphone |
| **Description** | The system captures the user's sung audio in real-time, extracts the fundamental frequency via YIN, evaluates pitch accuracy against the target notes of the user's assigned vocal part, renders immediate 3-band needle feedback, records a synchronized vocal take, and generates a post-session summary report. |
| **Preconditions** | Digitized score loaded; user completed the Gatekeeper Modal selecting "Test Pitch" and their assigned vocal part; microphone permission granted. |
| **Postconditions** | Sung pitch is evaluated and visually rendered with display latency ≤ 0.5s. Practice attempts are buffered in memory and aggregated at session end into circular accuracy %, flagged measures, and take recording. |

**Main Flow:**
1. User enters `PracticeScreen` in Test Pitch mode with their selected vocal part active.
2. User taps Play. Playback starts; the device microphone continuously streams 22,050 Hz PCM audio; `PerformanceAudioRecorder` initiates an audio take recording.
3. The Pitch Detection Engine executes the YIN algorithm on audio buffers to extract dominant frequency (Hz) and pitch clarity.
4. If valid vocal pitch is detected:
   - The system retrieves the active target note of the user's **selected vocal part** from the playback engine.
   - The system converts target note pitch to reference Hz (A4 = 440 Hz).
   - The system calculates cents deviation: $\text{cents} = 1200 \times \log_2(\text{detectedHz} / \text{targetHz})$.
   - The system applies Octave Folding into $[-600, +600]$ cents to avoid register disparity errors.
   - The system maps deviation to the **3-band visual indicator**:
     - **Green (Match / In-Tune):** $|\text{deviation}| \le \pm 50$ cents (evaluated as exact match if also $\le \pm 10$ cents tolerance).
     - **Yellow (Close / Minor Deviation):** $51 \le |\text{deviation}| \le 100$ cents.
     - **Red (Miss / Out of Tune):** $|\text{deviation}| > 100$ cents.
   - `PitchFeedbackIndicator` renders animated needle position and color within **≤ 0.5 seconds latency**.
   - The system records a `PitchAttempt(measureNumber, targetHz, detectedHz, cents, isMatch)` in the in-memory buffer.
5. If **Wait-for-Pitch mode** is enabled: The playback engine pauses at active target noteheads until the user sings within tolerance or a 5-second timeout elapses.
6. When playback finishes or user stops:
   - System finalizes audio recording via `ScoreRecordingManager`.
   - System computes session metrics: overall accuracy percentage, total notes attempted, correct notes, average cents deviation, vocal highlight (highest/lowest notes correctly sung), and top problematic measures (< 70% accuracy).
   - System navigates to `PracticeSummaryScreen` displaying metrics and take playback.

**Alt / Exception:**
- **E1** – Background Noise Interference: If ambient noise obscures vocal clarity below 0.55 threshold, the system displays a "Noise Warning" and pauses visual feedback.
- **A1** – Pitch Deviation: If the user's sung pitch deviates beyond $\pm 50$ cents, the indicator shifts to Yellow or Red; user self-corrects based on real-time visual feedback.

> *Figure 3a: The Practice Mode Selection Modal displaying "Listen Only", "Test Pitch", and "Past Vocal Takes" buttons.*  
> *Figure 3b: The Dynamic Vocal Part Selection view within the Gatekeeper Modal showing detected SATB parts with pitch ranges and clefs.*  
> *Figure 3c: The Past Vocal Takes Modal showing historical takes with accuracy badges, playback progress, and speed toggles.*  
> *Figure 4: The post-session Practice Summary Screen generated by the Module 4 session buffer, displaying overall accuracy, vocal highlights, flagged measures, and audio take playback.*

---

### 3.3 Non-Functional Requirements

#### 3.3.1 Performance

The system must meet the following performance metrics:

| Metric | Target |
|--------|--------|
| OMR Processing Speed | ≤ 10 seconds per page under stable network conditions |
| OMR System Resource Load | ≤ 75% CPU/Memory utilization during score parsing |
| OMR Structural Accuracy | ~80–85% track separation accuracy under controlled input conditions |
| Part Isolation Processing Speed | ≤ 3 seconds processing time for audio-visual part isolation |
| Audio-Visual Synchronization Latency | ≤ 0.1 seconds note highlight latency against active audio playback |
| Pitch Feedback Display Delay | ≤ 0.5 seconds visual feedback latency |
| Pitch Detection Accuracy | ≥ 85% pitch match accuracy in quiet environments |
| False Negative Threshold | ≤ 5% false negative rate for correctly sung pitches |
| System Usability Target | Minimum acceptable score of 70 on the System Usability Scale (SUS) |

---

#### 3.3.2 Security

The system must ensure the security of user-uploaded score images and recorded microphone input data, treating them as private user data.

- Data in transit shall be encrypted using **TLS 1.3**.
- Local storage of MusicXML files, image caches, and recorded vocal takes shall utilize **Android Scoped Storage** in private app directories.
- User authentication tokens and session keys shall be secured using the **Android Keystore System**.

---

#### 3.3.3 Reliability

The application shall maintain stable playback, synchronized score tracking, and pitch feedback functionality during active practice sessions under standard Android operating conditions.

- In the event of synchronization drift exceeding the ≤ 0.1 second threshold, the system shall automatically re-synchronize the visual playhead with the active audio clock.
- Pitch reference tones shall maintain an approximate frequency deviation threshold of **± 10 cents** under standard playback conditions.
- Audio safety layer shall enforce a maximum 5-second note sustain safeguard to prevent MIDI stuck-note conditions.
