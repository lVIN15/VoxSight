# CEBU INSTITUTE OF TECHNOLOGY
# UNIVERSITY


## COLLEGE OF COMPUTER STUDIES


## Software Design Description
## for
## VoxSight


---

## Change History Signature

| Version | Description     | Date Completed |
|---------|-----------------|----------------|
| 0.1     | Initial Release | May 12, 2026   |


---

## Preface

**Project Background.**
VoxSight was conceived to address a common challenge in choral music: the steep learning curve amateur singers face when transitioning from a physical sheet of music to an accurate vocal performance. Traditional practice methods often lack immediate, objective feedback, leading to the reinforcement of pitch errors. VoxSight bridges this gap by transforming static scores into interactive, data-driven practice sessions.

**Intent of this Document.**
This Software Design Description (SDD) serves as the definitive technical roadmap for the VoxSight mobile application. It is intended to guide the development team through the implementation of complex signal processing, UI synchronization, and OMR (Optical Music Recognition) integration.

**Intended Audience:**
- **Software Developers:** For implementing the Kotlin/Android and Python/FastAPI components.
- **System Architects:** To verify the integration between the mobile client and the OMR engine.
- **Quality Assurance (QA) Teams:** To derive test cases based on the defined technical constraints (e.g., the ±10 cents pitch tolerance and <100ms latency).

**Document Organization:**
- Module 1: The digitization of physical scores via OMR.
- Module 2: The synthesis and selective isolation of SATB vocal parts.
- Module 3: The synchronization of the visual playhead with the audio clock.
- Module 4: The real-time frequency analysis and pitch feedback loop.


---

## Table of Contents

- Change History Signature
- Preface
- Table of Contents
- Introduction
  - 1.1. Purpose
  - 1.2. Scope
  - 1.3. Definitions and Acronyms
    - Acronyms
    - Definitions
  - 1.4. References
- Architectural Design
- Detailed Design
  - Module 1.
    - 1.1 Transaction name: Upload Sheet Music
    - 1.2 Transaction name: Process Score via OMR Engine.
    - 1.3 Transaction name: Generate MusicXML & Separate SATB Parts.
  - Module 2.
    - 2.1: Select Voice Part and Apply Focus
    - 2.2: Play Assigned Part ( Synthesized Vocal Tune )
    - 2.3: Toggle Audio Suppression / Visual Focus
  - Module 3.
    - 3.1: Initiate Playback with Tracking
    - 3.2 Transaction Name: Pause and Resume Tracking
  - Module 4.
    - 4.1 Transaction Name: Sight-Reading Training


---

## Introduction

### 1.1. Purpose

The purpose of this Software Design Description (SDD) is to detail the architectural and component-level design of the VoxSight mobile application. This document translates the requirements established in the VoxSight Software Requirements Specification (SRS) into a concrete technical blueprint. It serves as the primary technical reference for the development team to implement the system's front-end interfaces, back-end logic, and data structures.

### 1.2. Scope

This document covers the architectural and detailed design of VoxSight, an Android-based mobile application designed for amateur choir members. The design encompasses the client-side mobile application, the server-side API routing, integration with a third-party Optical Music Recognition (OMR) engine, and local data persistence mechanisms. It details the technical implementation for the four core modules: Controlled OMR Digitization, Audio-Visual Selective Focus, Dynamic Score Tracking, and Real-Time Pitch Feedback.

### 1.3. Definitions and Acronyms

#### Acronyms

- **API** — Application Programming Interface
- **ERD** — Entity Relationship Diagram
- **Hz** — Hertz (Unit of frequency)
- **MIDI** — Musical Instrument Digital Interface
- **MusicXML** — Music Extensible Markup Language
- **OMR** — Optical Music Recognition
- **PK / FK** — Primary Key / Foreign Key
- **SATB** — Soprano, Alto, Tenor, Bass (the four standard voice parts)
- **SDD** — Software Design Description
- **SRS** — Software Requirements Specification
- **TLS** — Transport Layer Security
- **UI / UX** — User Interface / User Experience
- **UUID** — Universally Unique Identifier

#### Definitions

- **Cents** — A logarithmic unit of measure used for musical intervals. In this system, 100 cents equal one semitone. VoxSight uses a ±10 cents tolerance for pitch matching.
- **Deskewing** — A pre-processing technique used by the OMR engine to straighten images of sheet music captured at an angle, ensuring accurate staff line detection.
- **Digitization** — The process of converting physical or image-based sheet music into a machine-readable format (MusicXML) containing metadata for pitch, duration, and rhythm.
- **NoteEvent** — A specific data object representing a musical note. It contains the MIDI value, start time in milliseconds relative to the beginning of the score, and the assigned SATB voice part.
- **Pitch Detection Engine** — A back-end component that uses frequency analysis methods, such as Fast Fourier Transform (FFT) or autocorrelation, to identify the fundamental frequency of a user's vocal input.
- **Playhead** — A visual vertical line that moves across the digital score during playback to indicate the current temporal position in the music.
- **Scoped Storage** — An Android security feature that restricts an application's access to the device's file system, requiring the use of designated folders for saving MusicXML and media files.
- **Sync Drift** — The discrepancy in timing between the audio clock (what the user hears) and the UI render clock (what the user sees). VoxSight aims to keep this drift below 100 milliseconds.
- **Target Note** — The specific pitch, measured in Hz, that a user is expected to sing at a given timestamp in the score. It serves as the benchmark for real-time vocal feedback.


### 1.4. References

[1] E. O. Lagamo Jr., T. D. Castillo Jr., D. D. L. Sala, J. E. S. Sevilla, and G. O. E. Velasco, "Software Requirements Specification for VoxSight," College of Computer Studies, Cebu Institute of Technology - University, Cebu City, Philippines, Unpublished, 2026.

[2] BreezeWhite, "oemer: End-to-end Optical Music Recognition," GitHub repository, 2023. [Online]. Available: https://github.com/BreezeWhite/oemer. [Accessed: May 2026].

[3] W3C Music Notation Community Group, "MusicXML 4.0 Specification," W3C, Jun. 2021. [Online]. Available: https://www.w3.org/2021/06/musicxml40/. [Accessed: May 2026].

[4] J. Six, "TarsosDSP: A Real-Time Audio Processing Framework in Java," Ghent University. [Online]. Available: https://0110.be/releases/TarsosDSP/. [Accessed: May 2026].

[5] Google, "Jetpack Compose Documentation," Android Developers. [Online]. Available: https://developer.android.com/jetpack/compose. [Accessed: May 2026].

[6] S. Ramírez, "FastAPI," Tiangolo. [Online]. Available: https://fastapi.tiangolo.com/. [Accessed: May 2026].

[7] Supabase, "Supabase Architecture and API Reference," Supabase Inc. [Online]. Available: https://supabase.com/docs. [Accessed: May 2026].


---

## Architectural Design

**VoxSight, Block Diagram.**

```plantuml
@startuml
!theme plain

' Professional Styling & Spacing Parameters
skinparam componentStyle rectangle
skinparam linetype ortho
skinparam nodesep 60
skinparam ranksep 70
skinparam RoundCorner 8
skinparam Padding 5

' Custom Colors
skinparam component {
  BackgroundColor #F8F9FA
  BorderColor #495057
  ArrowColor #2980B9
}
skinparam database {
  BackgroundColor #E9ECEF
  BorderColor #495057
}
skinparam node {
  BorderColor #343A40
}

title VoxSight System Architecture

actor "Choir Member" as User

node "Mobile Client (Android Handset)" as Mobile {
    package "Front-End UI Layer" {
        component "Jetpack Compose\n(Digitization)" as Compose
        component "Canvas Viewer\n(Interactive Score)" as ScoreView 
    }
    
    package "Client Logic Services" {
        component "CameraX API\n(Image Capture)" as CamX
        component "AudioTrack & Mic API\n(Pitch & Playback)" as AudioAPI
        component "Supabase SDK\n(Auth & Data Sync)" as SubaSDK
    }
    
    database "Local Storage" {
        storage "Android Scoped Storage\n(.musicxml files)" as LocalFile
    }
}

node "Cloud / Server Environment" as Cloud {
    node "Back-End API (FastAPI)" as Backend {
        component "ScoreUploadController" as UploadCtrl
        component "OMRIntegrationService" as OMRLink
        component "MusicXMLBuilder" as XMLBuild
        
        ' Force vertical internal layout
        UploadCtrl -down-> OMRLink
        OMRLink -down-> XMLBuild
    }
    
    database "Supabase Cloud" as Supabase {
        folder "Auth Service" as Auth
        folder "PostgreSQL" as DB
    }
}

node "Third-Party Services" as External {
    component "External OMR Engine\n(Image-to-Data API)" as OMREngine
}

' ==========================================
' RELATIONSHIPS & EXPLICIT ROUTING
' ==========================================

' 1. User Interaction (Top-Down)
User -down-> Compose : "Interacts"
User -down-> ScoreView : "Interacts"

' 2. Mobile Internal Flow (Top-Down)
Compose -down-> CamX : "Triggers"
ScoreView -down-> AudioAPI : "Triggers Playback"
SubaSDK -down-> LocalFile : "Persists Locally"

' 3. Mobile to Cloud (Left to Right)
CamX -right-> UploadCtrl : "Upload Image Payload\n(TLS 1.3)"
SubaSDK -right-> Auth : "User Authentication"
SubaSDK -right-> DB : "Metadata Persistence"

' 4. Cloud to External (Left to Right, Bidirectional)
OMRLink <-> OMREngine : "REST Request / Raw Data Return"

' 5. Cloud back to Mobile (Right to Left)
XMLBuild -left-> SubaSDK : "Returns generated\n.musicxml"

@enduml
```


---

## Detailed Design

## Module 1.

### 1.1 Transaction name: Upload Sheet Music

**User Interface Design.**

**Front-end component(s):**

- **Component Name:** UploadScoreScreen
  - **Description and purpose:** Provides the UI buttons for "Take Photo" and "Import from Device", and handles the Android device permission requests.
  - **Component type or format:** Jetpack Compose UI (Kotlin)

- **Component Name:** ImageCaptureService
  - **Description and purpose:** Interfaces with the device camera to capture the image and performs basic resolution/clarity validation.
  - **Component type or format:** Native Android CameraX API

**Back-end component(s):**

- **Component Name:** ScoreUploadController
  - **Description and purpose:** Receives the multipart image payload via secure TLS 1.3 connection and validates the file format.
  - **Component type or format:** FastAPI Router Endpoint (Python)

**Object-Oriented Components:**

Class Diagram: (Note: This unified class diagram represents all core objects utilized across Module 1's transactions).

**Data Design:**

ERD or schema: N/A (Data is strictly in transit during this transaction; no database persistence occurs until Transaction 1.3).

**Transaction 1.1, Class Diagram.**

```plantuml
@startuml
skinparam classAttributeIconSize 0

class UploadScoreScreen {
  +openCamera()
  +openGallery()
}
class ImageCaptureService {
  +captureImage(): File
  +validateQuality(image: File): Boolean
}
class ScoreUploadController {
  +uploadImage(image: File): Response
}
class OMRIntegrationService {
  +sendToExternalOMR(image: File): RawMusicData
}
class MusicXMLBuilder {
  +generateXML(data: RawMusicData): MusicXML
  +separateSATB(xml: MusicXML): MusicXML
}
class LocalFileStorageManager {
  +saveToScopedStorage(file: MusicXML, path: String)
}

UploadScoreScreen --> ImageCaptureService : triggers >
ImageCaptureService --> ScoreUploadController : sends image >
ScoreUploadController --> OMRIntegrationService : routes payload >
OMRIntegrationService --> MusicXMLBuilder : returns structural data >
MusicXMLBuilder --> LocalFileStorageManager : passes generated XML >
@enduml
```

**Transaction 1.1, Sequence Diagram:** (Focuses specifically on the UI capture and API upload phase).

```plantuml
@startuml
actor "Choir Member" as User
participant "UploadScoreScreen" as UI
participant "ImageCaptureService" as Camera
participant "ScoreUploadController" as API

User -> UI : Tap "Take Photo"
UI -> Camera : openCamera()
Camera --> User : Displays viewfinder
User -> Camera : Captures image
Camera -> Camera : validateQuality()
Camera --> UI : Returns image payload
UI -> API : POST /api/score/upload (TLS 1.3)
@enduml
```


---

### 1.2 Transaction name: Process Score via OMR Engine.

**User Interface Design.**

**Front-end component(s):**

- **Component Name:** ProcessingLoadingState
  - **Description and purpose:** A visual progress indicator shown to the user while the server processes the image.
  - **Component type or format:** Jetpack Compose Dialog / Overlay (Kotlin)

**Back-end component(s):**

- **Component Name:** OMRIntegrationService
  - **Description and purpose:** Securely routes the image payload to the external OMR Engine, handles deskewing/noise reduction, and awaits the structural data return. If score recognition fails or confidence levels fall below acceptable thresholds, the service returns an error response prompting the user to recapture or upload a clearer image.
  - **Component type or format:** FastAPI Asynchronous Service (Python)

**Object-Oriented Components:**

Class Diagram: (Refer to unified Module 1 Class Diagram in Section 1.1).

Sequence Diagram: (Focuses on the server-side OMR processing block).

**Data Design:**

ERD or schema: N/A (Data is being actively processed in memory; no database persistence).

**Transaction 1.2, Sequence Diagram.**

```plantuml
@startuml
participant ScoreUploadController
participant OMRIntegrationService

ScoreUploadController -> OMRIntegrationService : Route image payload to external engine
OMRIntegrationService -> OMRIntegrationService : Pre-process image (deskew, denoise)
OMRIntegrationService -> OMRIntegrationService : Extract pitch, rhythm, staves (max 10s)
OMRIntegrationService --> ScoreUploadController : Return raw structural data array
@enduml
```


---

### 1.3 Transaction name: Generate MusicXML & Separate SATB Parts.

**User Interface Design.**

UI Note: Upon successful generation of the MusicXML file, the UI dismisses the loading state, updates the Recent Scores list, and provides a standard Android success toast.

**Front-end component(s):**

- **Component Name:** LocalFileStorageManager
  - **Description and purpose:** Downloads the compiled MusicXML file from the server, saves it to Android Scoped Storage, and syncs metadata with Supabase.
  - **Component type or format:** Kotlin Coroutine / File I/O Utility (with Supabase SDK)

**Back-end component(s):**

- **Component Name:** MusicXMLBuilder
  - **Description and purpose:** Maps the raw pitch/rhythm data returned by the OMR engine into a strict MusicXML schema, ensuring SATB vocal parts are structurally separated whenever identifiable from the processed score.
  - **Component type or format:** Python XML Serialization Utility

**Object-Oriented Components:**

Class Diagram: (Refer to unified Module 1 Class Diagram in Section 1.1).

Sequence Diagram: (Focuses on the XML generation and local saving phase).

**Data Design:**

ERD or schema.

**Transaction 1.3, Sequence Diagram.**

```plantuml
@startuml
skinparam style strictuml
title Transaction 2.1: Select Voice Part and Apply Focus

actor "Choir Member" as User
participant "PartSelectorUI" as UI
participant "AudioVisualMixer" as Mixer
participant "ScoreRenderer" as Renderer
User -> UI: selectPart(partID) // e.g., 'Alto'
activate UI
UI -> Mixer: setFocusPart(partID)
activate Mixer

Mixer -> Mixer: calculateOpacityMap()
note right: Active Part = 1.0\nInactive Parts = 0.2

Mixer -> Renderer: updateDisplay(opacityMap)
activate Renderer
Renderer -> Renderer: applyAlphaBlending()
Renderer --> UI: notifyRenderComplete()
deactivate Renderer

Mixer --> UI: updateActiveButtonState()
deactivate Mixer
UI --> User: Visual Feedback (Highlighted Staff)
deactivate UI
@enduml
```

**Transaction 1.3, Class Diagram.**

```plantuml
@startuml
hide circle

class DigitizedScore {
  *score_id : UUID <<PK>>
  --
  title : String
  upload_date : Timestamp
  music_xml_path : String
  part_count : Integer
}

note right of DigitizedScore
  Stores the metadata and file paths for
  sheet music successfully processed and
  saved to the Supabase database.
end note
@enduml
```


---

## Module 2.

### 2.1: Select Voice Part and Apply Focus

**User Interface Design.**

**Front-end Component(s):**

- **Component Name:** PartSelectorUI
  - **Description and Purpose:** Provides the interactive buttons (S, A, T, B). It captures the user's touch event and notifies the mixer which part is now "Active."
  - **Component Type or Format:** Jetpack Compose UI (Kotlin).

- **Component Name:** AudioVisualMixer
  - **Description and Purpose:** The central logic controller for Module 2. It calculates the opacity values (100% for active, 20% for inactive) and passes these instructions to the renderer.
  - **Component Type or Format:** Kotlin ViewModel / Controller Class.

- **Component Name:** ScoreRenderer
  - **Description and Purpose:** The engine that draws the MusicXML onto the screen. It applies the "Alpha Blending" (dimming effect) to the staves based on the Mixer's calculations.
  - **Component Type or Format:** Jetpack Compose Canvas API.

**Object-Oriented Components:**

Sequence Diagram.

**Transaction 2.1, Sequence Diagram.**

```plantuml
@startuml
title Transaction 2.1: Select Voice Part and Apply Focus

actor "Choir Member" as User
participant PartSelectorUI
participant AudioVisualMixer
participant ScoreRenderer

User -> PartSelectorUI : selectPart(partID) // e.g., 'Alto'
activate PartSelectorUI

PartSelectorUI -> AudioVisualMixer : setFocusPart(partID)
activate AudioVisualMixer

AudioVisualMixer -> AudioVisualMixer : calculateOpacityMap()

note right of ScoreRenderer
  Active Part = 1.0
  Inactive Parts = 0.2
end note

AudioVisualMixer -> ScoreRenderer : updateDisplay(opacityMap)
activate ScoreRenderer

ScoreRenderer -> ScoreRenderer : applyAlphaBlending()

ScoreRenderer --> PartSelectorUI : notifyRenderComplete()
deactivate ScoreRenderer

AudioVisualMixer --> PartSelectorUI : updateActiveButtonState()
deactivate AudioVisualMixer

PartSelectorUI --> User : Visual Feedback (Highlighted Staff)
deactivate PartSelectorUI

@enduml
```


---

### 2.2: Play Assigned Part ( Synthesized Vocal Tune )

**User Interface Design.**

**Front-end Component(s):**

- **Component Name:** AudioSynthesisEngine
  - **Description and Purpose:** Parse the pitch and duration data from the MusicXML. It maps these to bundled human 'Aahs/Oohs' soundfonts and ensures a maximum frequency deviation of ±10 cents.
  - **Component Type or Format:** Native Android Audio Bridge (C++ / Kotlin).

- **Component Name:** PlaybackController
  - **Description and Purpose:** Manages the play, pause, and stop states. It ensures that as the music plays, the "Highlighter" on the screen remains synchronized with the audio within the acceptable latency threshold.
  - **Component Type or Format:** Kotlin StateFlow / Coroutine Controller.

**Back-end Component(s):**

- **Component Name:** LocalScopedStorage
  - **Description and Purpose:** The secure local directory where the app stores processed scores. This avoids the need to download the file every time the user practices.
  - **Component Type or Format:** Android Scoped Storage API.

**Object-Oriented Components:**

Sequence Diagram.

**Transaction 2.2, Sequence Diagram.**

```plantuml
@startuml
title Transaction 2.2: Play Assigned Part (Synthesized)

actor "Choir Member" as User
participant AudioVisualMixer
participant AudioSynthesisEngine
database "Android Scoped Storage" as Storage

User -> AudioVisualMixer : pressPlay()
activate AudioVisualMixer

AudioVisualMixer -> Storage : loadMusicXML(currentScoreID)
activate Storage

Storage --> AudioVisualMixer : MusicXML Stream
deactivate Storage

AudioVisualMixer -> AudioSynthesisEngine : initializeStream(NoteData[], targetPart)
activate AudioSynthesisEngine

AudioSynthesisEngine -> AudioSynthesisEngine : mapNotesToSoundfonts('Aahs')
AudioSynthesisEngine -> AudioSynthesisEngine : calibratePitch(±5 cents)

loop For duration of track
    AudioSynthesisEngine -> AudioSynthesisEngine : generateAudioBuffer()
    AudioSynthesisEngine -> User : Play Synthesized Tone
end

AudioSynthesisEngine --> AudioVisualMixer : playbackFinished()
deactivate AudioSynthesisEngine
deactivate AudioVisualMixer

@enduml
```


---

### 2.3: Toggle Audio Suppression / Visual Focus

**User Interface Design.**

**Front-end Component(s):**

- **Component Name:** FocusToggleSwitch
  - **Description and Purpose:** A UI toggle that allows the user to turn off the isolation. If turned OFF, it forces the AudioVisualMixer to set all staves and all audio tracks to 100%.
  - **Component Type or Format:** Jetpack Compose Switch Widget.

- **Component Name:** GlobalStateMonitor
  - **Description and Purpose:** Tracks whether the user is currently in "Isolated Practice" or "Full Choir" mode. It broadcasts this state to all other Module 2 components.
  - **Component Type or Format:** Kotlin ViewModel / StateFlow.

**Back-end Component(s):**

- **Component Name:** UserPreferencesStore
  - **Description and Purpose:** Persists the user's choice. If a user prefers to always start in "Isolation Mode," this component saves that setting to the database.
  - **Component Type or Format:** Supabase Database (Remote) / Android Jetpack DataStore (Local).

**Object-Oriented Components:**

Sequence Diagram.

**Transaction 2.3, Sequence Diagram.**

```plantuml
@startuml
title Transaction 2.3: Toggle Audio Suppression / Visual Focus

actor "Choir Member" as User
participant FocusToggleSwitch
participant AudioVisualMixer
participant AudioSynthesisEngine

User -> FocusToggleSwitch : toggleFocus(isOn)
activate FocusToggleSwitch

alt isOn == true (Isolation Mode)
    FocusToggleSwitch -> AudioVisualMixer : enableSuppression()
    AudioVisualMixer -> AudioSynthesisEngine : setMutedTracks(nonAssignedParts)
    AudioVisualMixer -> AudioVisualMixer : applyDimming(0.2)
else isOn == false (Full Score Mode)
    FocusToggleSwitch -> AudioVisualMixer : disableSuppression()
    AudioVisualMixer -> AudioSynthesisEngine : setVolumeAll(1.0)
    AudioVisualMixer -> AudioVisualMixer : resetOpacityAll(1.0)
end

AudioVisualMixer --> FocusToggleSwitch : updateToggleState()
deactivate FocusToggleSwitch

@enduml
```


---

## Module 3.

### 3.1: Initiate Playback with Tracking

**User Interface Design.**

The Initiate Playback with Tracking interface is part of the unified Interactive Practice Screen. It presents the digitized sheet music in a scrollable score viewer and overlays a real-time playhead that advances in sync with audio playback. The screen provides Play/Pause transport controls and auto-scrolls to keep the active measure centered on screen at all times.

**Front-end component(s):**

- **Component Name:** ScoreRenderer
  - **Description and purpose:** The primary canvas that draws the sheet music on the screen, managing the auto-scrolling to keep the active measure centered.
  - **Component type or format:** Jetpack Compose Canvas API.

- **Component Name:** PlayheadSynchronizer
  - **Description and purpose:** Reads the internal audio clock and updates the vertical playhead position (highlighting active notes) targeting synchronization drift below 100 milliseconds during playback.
  - **Component type or format:** Timing Synchronization Class.

- **Component Name:** PlaybackControlBar
  - **Description and purpose:** Provides the Play and Pause transport buttons. Displays a measure/beat progress indicator so the user can track their position in the score at a glance.
  - **Component type or format:** Jetpack Compose Stateful Component; dispatches play and pause commands to the back-end PlaybackEngineService.

- **Component Name:** AutoScrollController
  - **Description and purpose:** Monitors the current playhead position emitted by PlayheadSynchronizer and programmatically scrolls the ScoreRenderer to keep the active measure centered in the viewport during continuous playback.
  - **Component type or format:** Kotlin Coroutine / ScrollState Observer; subscribes to playhead position events and calls the scroll API of the ScoreRenderer.

**Back-end component(s):**

- **Component Name:** MusicXMLParser
  - **Description and purpose:** Reads the locally stored MusicXML file and produces an ordered in-memory list of NoteEvent objects, each carrying pitch, start timestamp, duration, and SATB voice assignment. This list serves as the primary playback reference consumed by the PlaybackEngineService and Module 4's Pitch Comparison Engine.
  - **Component type or format:** Kotlin utility class using Android's built-in XML pull parser; outputs List\<NoteEvent\>.

- **Component Name:** PlaybackEngineService
  - **Description and purpose:** Manages the internal audio clock. Schedules note-start events from the NoteEvent queue, passes audio data to the synthesizer (Module 2), and emits highlight-update callbacks to the front-end PlayheadSynchronizer. Re-syncs the playhead automatically if render-to-audio drift exceeds 0.1 seconds.
  - **Component type or format:** Android background Service (Kotlin); uses android.media.AudioTrack for low-latency audio scheduling and a Handler/Runnable loop for note-event dispatch.

- **Component Name:** SyncDriftMonitor
  - **Description and purpose:** Continuously compares the rendered playhead timestamp against the audio clock. If the delta exceeds 100 ms, it triggers a forced re-sync to bring the highlight back into alignment with the audio.
  - **Component type or format:** Kotlin coroutine / Flow observer running within the PlaybackEngineService lifecycle.

**Object-Oriented Components:**

- Class Diagram
- Sequence Diagram

**Data Design:**

- ERD

**Transaction 3.1, Class Diagram.**

```plantuml
@startuml
class PlaybackEngineService {
  +audioClock: Long
  +noteQueue: Queue<NoteEvent>
  +currentNote: NoteEvent
  +start()
  +pause()
  +resume()
  +seekTo(timestampMs: Long)
  +emitNoteEvent(note: NoteEvent)
}

class MusicXMLParser {
  +filePath: String
  +parse(): List<NoteEvent>
}

class SyncDriftMonitor {
  +maxDriftMs: Long
  +checkDrift(renderMs: Long, audioMs: Long): Boolean
  +forceResync()
}

class PlayheadSynchronizer {
  +activeNotePosition: Rect
  +updatePosition(note: NoteEvent)
  +clearHighlight()
}

class NoteEvent {
  +pitchMidi: Int
  +startTimeMs: Long
  +durationMs: Long
  +staffVoice: String
  +toFrequencyHz(): Double
}

class AutoScrollController {
  +viewportHeight: Int
  +scrollToNote(note: NoteEvent)
}

class ScoreRenderer {
  +musicXMLPath: String
  +render()
  +scrollTo(position: Int)
}

PlaybackEngineService --> MusicXMLParser : uses >
PlaybackEngineService --> SyncDriftMonitor : monitored by >
PlaybackEngineService --> PlayheadSynchronizer : notifies >
PlaybackEngineService ---> NoteEvent : queues >
MusicXMLParser --> NoteEvent : produces >
PlayheadSynchronizer --> AutoScrollController : triggers >
AutoScrollController --> ScoreRenderer : scrolls >
@enduml
```

**Transaction 3.2, Sequence Diagram.**

```plantuml
@startuml
actor "Choir Member" as User
participant PlaybackControlBar
participant PlaybackEngineService
participant SyncDriftMonitor
participant PlayheadSynchronizer
participant AutoScrollController
participant ScoreRenderer

User -> PlaybackControlBar : Tap Play
PlaybackControlBar -> PlaybackEngineService : start()
PlaybackEngineService -> PlaybackEngineService : Parse MusicXML; init audioClock = 0

loop For each NoteEvent
    PlaybackEngineService -> PlayheadSynchronizer : emitNoteEvent(note)
    PlayheadSynchronizer -> ScoreRenderer : updatePosition(note) [<=0.1s latency]
    
    PlaybackEngineService -> SyncDriftMonitor : checkDrift(renderMs, audioMs)
    
    alt drift > 100ms
        SyncDriftMonitor -> PlaybackEngineService : forceResync()
        PlaybackEngineService -> PlayheadSynchronizer : updatePosition(correctedNote)
    end
    
    PlayheadSynchronizer -> AutoScrollController : trigger scroll
    AutoScrollController -> ScoreRenderer : scrollTo(activePosition)
    
    PlaybackEngineService -> PlaybackEngineService : advance audioClock by durationMs
end

User -> PlaybackControlBar : Tap Pause
PlaybackControlBar -> PlaybackEngineService : pause()
PlaybackEngineService -> PlayheadSynchronizer : clearHighlight() [freeze at current note]
@enduml
```

**Transaction 3.1, ERD.**

```plantuml
@startuml
hide circle

entity "SCORE" {
  * scoreId : String <<PK>>
  --
  title : String
  musicXMLPath : String
}

entity "PLAYBACK_SESSION" {
  * sessionId : String <<PK>>
  --
  * scoreId : String <<FK>>
  * currentNoteId : String <<FK>>
  audioClockMs : Long
  isPlaying : Boolean
}

entity "NOTE_EVENT" {
  * noteId : String <<PK>>
  --
  * scoreId : String <<FK>>
  pitchMidi : Int
  startTimeMs : Long
  durationMs : Long
  staffVoice : String
}

SCORE ||--o{ PLAYBACK_SESSION : drives
SCORE ||--o{ NOTE_EVENT : contains
PLAYBACK_SESSION }o--|| NOTE_EVENT : tracks
@enduml
```


---

### 3.2 Transaction Name: Pause and Resume Tracking

**User Interface Design.**

The Pause and Resume interaction is handled within the same Interactive Practice Screen. Tapping Pause freezes the playhead highlight on the current note and toggles the Play button to a Resume state. The score remains static. Tapping anywhere on a measure while paused seeks the playhead to that position. On Resume, playback and highlighting continue from the frozen or seeked position while minimizing noticeable visual discontinuities during playback resumption.

**Front-end component(s):**

- **Component Name:** PlaybackControlBar (Pause/Resume State)
  - **Description and purpose:** Manages the toggle between Play, Paused, and Resumed states. Swaps the Play icon for a Resume icon when paused, providing clear visual feedback on the current playback status. Dispatches pause and resume commands to the PlaybackEngineService.
  - **Component type or format:** Jetpack Compose Stateful Component; maintains a local isPlaying boolean that drives the icon swap.

- **Component Name:** FrozenPlayheadIndicator
  - **Description and purpose:** When playback is paused, renders a static, outlined version of the playhead highlight on the current note to show the user exactly where the score is suspended. Visually distinguishes the paused state from the active, animated highlight.
  - **Component type or format:** Conditional Jetpack Compose Canvas DrawScope overlay within the PlayheadSynchronizer component; switches from an animated fill to a static outlined border on pause.

- **Component Name:** SeekTapHandler
  - **Description and purpose:** Detects tap gestures on the score view while playback is paused and forwards the tapped screen coordinates to the back-end SeekPositionMapper so the playhead can jump to the corresponding measure.
  - **Component type or format:** Jetpack Compose Modifier.pointerInput (Clickable) on the ScoreRenderer; active only when playback is paused.

**Back-end component(s):**

- **Component Name:** PlaybackEngineService (Pause/Resume Handler)
  - **Description and purpose:** Receives pause and resume commands from the UI. On pause, freezes the internal audio clock and stores the current position in pausedAtMs. On resume, restarts the clock from the preserved position and resumes note-event dispatch.
  - **Component type or format:** Extension of the existing PlaybackEngineService Android Service; adds pause() and resume() methods that suspend and restart the Handler/Runnable timing loop.

- **Component Name:** SeekPositionMapper
  - **Description and purpose:** Translates screen tap coordinates (x, y) into the corresponding MusicXML start timestamp. Uses a MeasureLayoutMap generated at score render time to map a tapped pixel position to the correct NoteEvent, enabling the user to jump to any measure while paused.
  - **Component type or format:** Kotlin utility class; consumes a Map\<Int, Rect\> of measure bounding boxes and returns a startTimeMs value that is passed to PlaybackEngineService.seekTo().

**Object-Oriented Components:**

- Class Diagram
- Sequence Diagram

**Data Design**

- ERD or schema

**Transaction 3.2, Class Diagram.**

```plantuml
@startuml
class SeekTapHandler {
  +isActive: Boolean
  +onTap(x: Float, y: Float)
}

class SeekPositionMapper {
  +measureLayoutMap: Map<Int, Rect>
  +tapToTimestamp(x: Float, y: Float): Long
}

class PlaybackControlBar {
  +isPlaying: Boolean
  +onPlayPauseTap()
  +onResumeTap()
}

class MeasureLayout {
  +measureId: String <<PK>>
  +scoreId: String <<FK>>
  +startTimeMs: Long
  +pixelBounds: Rect
}

class PlaybackEngineService {
  +isPaused: Boolean
  +pausedAtMs: Long
  +pause()
  +resume()
  +seekTo(timestampMs: Long)
}

class FrozenPlayheadIndicator {
  +frozenPosition: Rect
  +isPaused: Boolean
  +freeze(note: NoteEvent)
  +unfreeze()
}

SeekTapHandler --> SeekPositionMapper : forwards tap coords >
SeekPositionMapper --> MeasureLayout : reads >
SeekPositionMapper --> PlaybackEngineService : < seekTo(timestampMs)
PlaybackControlBar --> PlaybackEngineService : pause() / resume() >
PlaybackEngineService --> FrozenPlayheadIndicator : freeze() / unfreeze() >
@enduml
```

**Transaction 3.2, Sequence Diagram.**

```plantuml
@startuml
class SeekTapHandler {
  +isActive: Boolean
  +onTap(x: Float, y: Float)
}

class SeekPositionMapper {
  +measureLayoutMap: Map<Int, Rect>
  +tapToTimestamp(x: Float, y: Float): Long
}

class PlaybackControlBar {
  +isPlaying: Boolean
  +onPlayPauseTap()
  +onResumeTap()
}

class MeasureLayout {
  +measureId: String <<PK>>
  +scoreId: String <<FK>>
  +startTimeMs: Long
  +pixelBounds: Rect
}

class PlaybackEngineService {
  +isPaused: Boolean
  +pausedAtMs: Long
  +pause()
  +resume()
  +seekTo(timestampMs: Long)
}

class FrozenPlayheadIndicator {
  +frozenPosition: Rect
  +isPaused: Boolean
  +freeze(note: NoteEvent)
  +unfreeze()
}

SeekTapHandler --> SeekPositionMapper : forwards tap coords >
SeekPositionMapper --> MeasureLayout : reads >
SeekPositionMapper --> PlaybackEngineService : < seekTo(timestampMs)
PlaybackControlBar --> PlaybackEngineService : pause() / resume() >
PlaybackEngineService --> FrozenPlayheadIndicator : freeze() / unfreeze() >
@enduml
```

**Transaction 3.2, ERD.**

```plantuml
@startuml
hide circle

entity "SCORE" {
  * scoreId : String <<PK>>
  --
  title : String
  musicXMLPath : String
}

entity "PLAYBACK_SESSION" {
  * sessionId : String <<PK>>
  --
  * scoreId : String <<FK>>
  * currentNoteId : String <<FK>>
  audioClockMs : Long
  isPlaying : Boolean
  pausedAtMs : Long
  seekTargetMs : Long
  isPaused : Boolean
}

entity "MEASURE_LAYOUT" {
  * measureId : String <<PK>>
  --
  * scoreId : String <<FK>>
  startTimeMs : Long
  pixelBounds : String
}

entity "NOTE_EVENT" {
  * noteId : String <<PK>>
  --
  * scoreId : String <<FK>>
  pitchMidi : Int
  startTimeMs : Long
  durationMs : Long
  staffVoice : String
}

SCORE ||--o{ PLAYBACK_SESSION : drives
SCORE ||--o{ MEASURE_LAYOUT : has
SCORE ||--o{ NOTE_EVENT : contains
PLAYBACK_SESSION }o--|| NOTE_EVENT : tracks
MEASURE_LAYOUT }o--|| NOTE_EVENT : maps to
@enduml
```


---

## Module 4.

### 4.1 Transaction Name: Sight-Reading Training

**User Interface Design.**

**Front-end component(s)**

- **Component Name:** PracticeModePicker
  - **Description and purpose:** Allows the user to choose between "Listening Mode" (playback only) or "Test Pitch Mode" (activates the microphone for frequency tracking). Captures the user's selection and updates the ViewModel state to route the application to the corresponding practice flow.
  - **Component type or format:** Jetpack Compose Modal/View (Kotlin)

- **Component Name:** PitchDetectionEngine
  - **Description and purpose:** Analyzes the continuous raw audio stream from the device microphone using the TarsosDSP library (YIN algorithm). It extracts the fundamental vocal frequency (Hz) in real-time and continuously emits this numeric data to the UI layer for visual comparison against the target note, targeting a response latency of approximately 0.5 seconds or lower during active pitch tracking.
  - **Component type or format:** Kotlin Native Audio Utility (TarsosDSP)

- **Component Name:** VisualFeedbackRenderer
  - **Description and purpose:** Receives the active Hz data from the Pitch Detection Engine and calculates the deviation in cents against the MusicXML target note. It immediately updates the UI state to render the appropriate visual feedback (e.g., Green for ≤ ±10 cents, Red for > ±10 cents).
  - **Component type or format:** Jetpack Compose UI Modifier / StateFlow (Kotlin)

**Back-end component(s)**

- **Component Name:** SessionMetricsController
  - **Description and purpose:** Receives the final aggregated practice session payload (overall accuracy percentage, elapsed duration, and an array of flagged missed notes) once the user pauses or completes a song, persisting the historical data to the database for progress tracking.
  - **Component type or format:** FastAPI Router Endpoint (POST /api/session/save)

**Object-Oriented Components**

- Class Diagram
- Sequence Diagram

**Data Design**

- ERD or schema

**Transaction 4.1, Class Diagram.**

```plantuml
@startuml
package "Module 4: Real-Time Pitch Visualizer" {

  class PitchVisualizerController {
    -isTracking : boolean
    +startPitchTracking() : void
    +stopPitchTracking() : void
    -executionLoop() : void
  }

  class AudioCaptureService {
    -hasMicrophonePermission : boolean
    -sampleRate : int
    +checkPermissions() : boolean
    +requestPermissions() : void
    +startAudioStream() : RawAudioBuffer
    +stopAudioStream() : void
  }

  class PitchDetectionEngine {
    -noiseThresholdLimit : float
    -minConfidenceLevel : float
    +isNoiseLevelTooHigh(audio : RawAudioBuffer) : boolean
    +analyzeFrequency(audio : RawAudioBuffer) : DetectedPitch
  }

  class PitchComparator {
    -TOLERANCE_CENTS : int = 5
    +calculateCentDeviation(detected : DetectedPitch, target : TargetNote) : float
    +evaluateMatch(deviation : float) : FeedbackResult
  }

  class RenderEngine {
    -MAX_RENDER_LATENCY_MS : int = 500
    +renderIndicator(result : FeedbackResult) : void
    +displayNoiseWarning() : void
    +displayPermissionError() : void
  }

  class ScoreManager <<External (Module 1/3)>> {
    +getCurrentTargetNote(playheadPosition : long) : TargetNote
  }

  package "Data Models" {
    class RawAudioBuffer {
      +audioBytes : byte[]
      +timestamp : long
    }

    class DetectedPitch {
      +frequencyHz : float
      +confidence : float
    }

    class FeedbackResult {
      +isMatch : boolean
      +deviationCents : float
      +captureTimestamp : long
    }

    class TargetNote {
      +pitchFrequencyHz : float
      +durationMs : long
    }
  }

  ' Controller Relationships
  PitchVisualizerController -left-> AudioCaptureService : manages >
  PitchVisualizerController --> PitchDetectionEngine : coordinates >
  PitchVisualizerController --> PitchComparator : uses >
  PitchVisualizerController --> RenderEngine : updates >
  PitchVisualizerController -right-> ScoreManager : fetches target >

  ' Service / Engine Dependencies
  AudioCaptureService .right.> PitchDetectionEngine

  ' Data Model Consumptions & Productions
  PitchDetectionEngine --> "1" RawAudioBuffer : consumes >
  PitchDetectionEngine ..> "1" DetectedPitch : produces >

  PitchComparator --> "1" DetectedPitch : compares >
  PitchComparator ..> "1" FeedbackResult : produces >
  PitchComparator --> "1" TargetNote : against >

  RenderEngine ..> "1" FeedbackResult : renders >
  ScoreManager ..> TargetNote : provides >

  ' Constraint Notes
  note right of PitchComparator
    Enforces the ±5 cents
    deviation constraint
    for accurate matching.
  end note

  note right of RenderEngine
    Enforces the ≤0.5s
    display latency
    constraint.
  end note

}
@enduml
```

**Transaction 4.1, Sequence Diagram:**

```plantuml
@startuml
actor "Choir Member" as User
participant "UI & Render Engine" as UI
participant "Device Microphone\n(android.media)" as Mic
participant "Pitch Detection Engine" as PitchEngine
participant "Score Manager\n(MusicXML)" as ScoreManager

== Initialization & Permissions ==

User -> UI : 1 Select "Test Pitch"\nMode
UI -> Mic : 2 Request Microphone\nPermission

alt [Permission Denied]
    Mic --> UI : 3 Permission Denied
    UI --> User : 4 Display Permission\nError Prompt\n(Pitch tracking\ndisabled)
else [Permission Granted]
    Mic --> UI : 5 Permission Granted

    == Active Pitch Tracking Loop ==

    User -> UI : 6 Initiate Playback

    loop [Until Playback Paused or Score Ends]
        User -> Mic : 7 Sings assigned vocal\npart
        Mic -> PitchEngine : 8 Stream continuous\nraw audio
        
        alt [Environmental Noise Too High]
            PitchEngine --> UI : 9 Noise Threshold\nExceeded
            UI --> User : 10 Display "Noise\nWarning"\n(Pause visual\nfeedback)
        else [Optimal Audio Level]
            PitchEngine -> PitchEngine : 11 Analyze audio\nfrequency\n(Determine sung\npitch)
            PitchEngine -> ScoreManager : 12 Request current target\nnote data
            ScoreManager --> PitchEngine : 13 Target Pitch\nFrequency
            PitchEngine -> PitchEngine : 14 Compare detected\nfrequency\nto target note
            
            alt [Pitch Deviation ≤ ±5 cents]
                PitchEngine --> UI : 15 Match Successful
                UI --> User : 16 Render "Correct"\nvisual indicator\n(Latency ≤ 0.5s)
            else [Pitch Deviation > ±5 cents]
                PitchEngine --> UI : 17 Match Failed
                UI --> User : 18 Render "Incorrect"\nvisual indicator\n(Latency ≤ 0.5s)
            end
        end
    end
end
@enduml
```

**Transaction 4.1, ERD.**

```plantuml
@startuml
!theme plain
skinparam linetype ortho
hide circle

entity "User" as User {
  o user_id : UUID <<PK>>
  --
  username : VARCHAR
  email : VARCHAR
}

entity "DigitizedScore" as DigitizedScore {
  o score_id : UUID <<PK>>
  --
  title : VARCHAR
  upload_date : TIMESTAMP
  music_xml_path : VARCHAR
  part_count : INT
}

entity "PracticeSession" as PracticeSession {
  o session_id : UUID <<PK>>
  --
  o user_id : UUID <<FK>>
  o score_id : UUID <<FK>>
  mode : VARCHAR -- 'Listening' or 'Test Pitch'
  started_at : TIMESTAMP
  performance_score : FLOAT -- Aggregated result
}

entity "NoteEvent" as NoteEvent {
  o note_id : UUID <<PK>>
  --
  o score_id : UUID <<FK>>
  pitch_midi : INT
  pitch_hz : FLOAT -- Used by Pitch Engine
  start_time_ms : LONG
  duration_ms : LONG
  staff_voice : VARCHAR -- (S, A, T, or B)
}

entity "PitchAttempt" as PitchAttempt {
  o attempt_id : UUID <<PK>>
  --
  o session_id : UUID <<FK>>
  o note_id : UUID <<FK>> -- Links to the NoteEvent being tested
  detected_hz : FLOAT
  deviation_cents : FLOAT
  is_match : BOOLEAN
  timestamp_ms : LONG
}

User ||--o{ PracticeSession : performs
DigitizedScore ||--o{ PracticeSession : "is used in"
DigitizedScore ||--o{ NoteEvent : contains
PracticeSession ||--o{ PitchAttempt : records
NoteEvent ||--o{ PitchAttempt : "is the target for"

@enduml
```
