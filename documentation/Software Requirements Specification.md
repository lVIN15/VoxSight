## **CEBU INSTITUTE OF TECHNOLOGY**

**UNIVERSITY**

**COLLEGE OF COMPUTER STUDIES**

# 

# 

## 

## 

## 

# **Software Requirements Specifications**

## *for*

## VoxSight

**Change History**

| Name | Date | Version |
| ----- | ----- | ----- |
| VoxSight Initial Software Requirements Specification | 04/20/2026 | 0.1 |
|  |  |  |
|  |  |  |
|  |  |  |

**Table of Contents**

[**1\.  Introduction	4**](#introduction)

[1.1.  Purpose	4](#purpose)

[1.2.  Scope	4](#scope)

[1.3.  Definitions, Acronyms and Abbreviations	4](#definitions,-acronyms-and-abbreviations)

[1.4.  References	5](#references)

[**2\.  Overall Description	6**](#overall-description)

[2.1.  Product perspective	6](#product-perspective)

[2.2.  User characteristics	6](#user-characteristics)

[2.3.  Constraints	6](#2.3.-constraints)

[2.4.  Assumptions and dependencies	6](#2.4.-assumptions-and-dependencies)

[**3\.  Specific Requirements	7**](#specific-requirements)

[3.1.  External interface requirements	7](#external-interface-requirements)

[3.1.1. Hardware interfaces	7](#3.1.1.-hardware-interfaces)

[3.1.2. Software interfaces	7](#3.1.2.-software-interfaces)

[3.1.3. Communications interfaces	7](#3.1.3.-communications-interfaces)

[3.2.  Functional requirements	7](#functional-requirements)

[3.3 Non-functional requirements	24](#3.3-non-functional-requirements)

[Performance	24](#performance)

[Security	25](#security)

[Reliability	25](#reliability)

 

1. # **Introduction** {#introduction}

   1. ## ***Purpose***  {#purpose}

The purpose of this document is to provide a detailed description of the VoxSight system, a mobile application designed to improve independent sight-reading practice efficiency and reduce rehearsal preparation difficulty for amateur choir members. This document serves as a reference for developers, project managers, and stakeholders to ensure alignment with the functional and non-functional requirements.

2. ## ***Scope*** {#scope}

VoxSight is a mobile application targeting the Android platform. The system enables amateur choir members to independently practice their vocal parts on custom sheet music. The system supports standard choral sheet music images (JPEG/PNG/PDF) and four-part SATB voice structures. The system improves independent choir rehearsal preparation through four core modules: Controlled OMR Digitization, Audio-Visual Selective Focus, Dynamic Score Tracking, and Real-Time Pitch Feedback.

3. ## ***Definitions, Acronyms and Abbreviations*** {#definitions,-acronyms-and-abbreviations}

**MIDI (Musical Instrument Digital Interface):** A technical standard and digital language that allows computers, software, and musical instruments to communicate and generate sound (e.g.**,** the underlying code instructions that tell an app to play a specific pitch for a certain duration).

**OMR (Optical Music Recognition):** A technology that scans physical sheet music and converts it into a digital format that computers can read, edit, and play back (e.g.**,** using an app camera feature to take a picture of a printed choir score and converting it into playable audio).

**SATB (Soprano, Alto, Tenor, Bass):** The standard four-part vocal arrangement used in choral music, organized from the highest to the lowest pitch.

**Soprano (S):** The highest vocal range, typically sung by females, which usually carries the main melody.

**Alto (A):** The lower female vocal range that provides harmony directly below the soprano.

**Tenor (T):** The higher male vocal range that provides mid-level harmony and support.

**Bass (B):** The lowest male vocal range that provides the foundational depth of the choir's harmony.

**MusicXML:** A digital file format used for representing musical notation in a structured, machine-readable form.

**Pitch Detection:** The process of identifying the fundamental frequency of a sound signal (e.g., a sung note).

**Frequency (Hz):** The number of sound wave cycles per second, which determines the perceived pitch of a note.

**Latency:** The delay between an input (e.g., singing) and the system’s response (e.g., visual feedback).

**Cents (Pitch Measurement):** A unit used to measure small pitch differences; 100 cents equals one semitone.

**Sight-Reading:** The ability to read and perform music from notation without prior practice.

**Score Tracking:** The synchronization of visual note highlighting with audio playback in real time.

**Selective Focus (Audio-Visual):** A system feature that prioritizes a selected vocal part by significantly suppressing non-assigned audio tracks and dimming non-relevant notation to reduce auditory and visual cognitive load during practice. 

**Pitch Feedback:** Real-time visual or auditory indication of whether a sung note matches the expected pitch.

**Vocal Synthesis:** The artificial generation of human-like singing tones using digital sound processing.

4. ## ***References*** {#references}

**This SRS is based on the following documents and sources:** 

1. VoxSight Project Proposal, Team 2526-sem2-it332-51  
2. IEEE Std 830-1998 Documentation  
3. MusicXML Documentation  
4. Android Developer Documentation (android.media API reference)


2. # **Overall Description** {#overall-description}

   1. ## ***Product perspective*** {#product-perspective}

VoxSight is a mobile-first sight-reading practice application designed to support independent rehearsal preparation for amateur choir members. The system follows a client-server architecture in which uploaded sheet music images are processed through a cloud-based Optical Music Recognition (OMR) engine that converts physical scores into MusicXML data. The mobile client manages score rendering, selective playback, synchronized visual tracking, and real-time pitch feedback during user practice sessions.

2. ## ***User characteristics*** {#user-characteristics}

**Primary Users:** Amateur choir members, particularly those without prior instrumental training, from community, church, or collegiate ensembles.

**Secondary Users:** Choir directors who direct members to use the application for independent preparation.

## ***2.3. 	Constraints*** {#2.3.-constraints}

OMR accuracy is dependent on image quality, lighting conditions, score complexity, and notation clarity; ornate or handwritten scores may reduce recognition reliability. Vocal synthesis fidelity will approximate human timbre but not replicate full live expressiveness. Pitch detection performs optimally in quiet environments and may be affected by background noise or microphone quality. 

## ***2.4. 	Assumptions and dependencies*** {#2.4.-assumptions-and-dependencies}

The primary dependency is on consistent, reliable network access for the Optical Music Recognition (OMR) engine to process and return the MusicXML data. It is assumed that users will upload clear, high-contrast, and legible sheet music images (JPEG/PNG) for optimal OMR performance. 

3. # **Specific Requirements**  {#specific-requirements}

   1. ## ***External interface requirements*** {#external-interface-requirements}

### ***3.1.1.	Hardware interfaces*** {#3.1.1.-hardware-interfaces}

**The system requires access to compatible Android device hardware components including:**

* Device Microphone (for Real-Time Pitch Feedback)   
* Camera (for OMR image capture)   
* Internal Storage/Gallery (for uploading existing sheet music images).

### ***3.1.2.	Software interfaces*** {#3.1.2.-software-interfaces}

The system interfaces with the **Android OS**, and utilizes the **MusicXML** data format for digital score representation. External software interfaces include the integrated Optical Music Recognition (OMR) processing engine and vocal synthesizer soundfonts.

### ***3.1.3.	Communications interfaces*** {#3.1.3.-communications-interfaces}

A stable Internet connection (Wi-Fi or cellular data) is required for cloud-based OMR image processing and MusicXML retrieval to upload sheet music images to the OMR engine and download the resulting digital score data.

2. ## ***Functional requirements*** {#functional-requirements}

* **Module 1: Controlled OMR Digitization Module:** Handles user upload (photo/image) of sheet music, processes the physical score into playable MusicXML data, and identifies/separates the distinct SATB parts to reduce manual score interpretation effort during independent practice.

#### *Controlled OMR Digitization Transaction*

#### *Use Case Diagram:*

@startuml  
' Module 1: OMR Score Digitization

left to right direction  
skinparam actorStyle awesome

actor "Choir Member" as CM \#lightblue  
actor "OMR Engine\\n(Server)" as OMR \#lightgray

rectangle "OMR Score Digitization" {  
  usecase "Upload Sheet Music" as UC1  
  usecase "Capture via Camera" as UC1a  
  usecase "Import from Gallery/Storage" as UC1b  
  usecase "Process Score via OMR" as UC2  
  usecase "Generate MusicXML Data" as UC3  
  usecase "Separate SATB Parts" as UC4  
  usecase "Validate Digitized Score" as UC5  
}

CM \--\> UC1  
UC1 ..\> UC1a : \<\<extend\>\>  
UC1 ..\> UC1b : \<\<extend\>\>  
UC1 ..\> UC2 : \<\<include\>\>  
UC2 ..\> UC3 : \<\<include\>\>  
UC3 ..\> UC4 : \<\<include\>\>  
UC2 ..\> UC5 : \<\<include\>\>  
OMR \--\> UC2  
OMR \--\> UC3

@enduml

##### *Use Case Description:* 	**Upload Sheet Music**

| Actors | Primary: Choir Member |
| :---- | :---- |
| **Description** | The user provides a sheet music image (JPEG/PNG/PDF) to the system either by capturing a photo with the device camera or importing an existing file from local storage/gallery. |
| **Preconditions** | App is open; internet connection is available; user has sheet music accessible. |
| **Postconditions** | Image is validated and submitted to the OMR engine for processing. |
| **Main Flow** | 1\. User opens the application and navigates to "New Score." 2\. System displays two options: "Take Photo" and "Import from Device." 3\. User selects preferred input method. 4\. System accesses camera or file system accordingly. 5\. User captures/selects the sheet music image. 6\. System performs basic image quality validation (resolution, clarity check). 7\. System submits the validated image to the OMR engine over the network. |
| **Alt / Exception** | A1 – Image fails quality check: System warns user and prompts recapture. E1 – No internet connection: System displays offline error; upload is blocked. E2 – Unsupported file format: System rejects the file and notifies the user. |

#####  **Process Score via OMR Engine**

| Actors | Secondary: OMR Engine (server-side) |
| :---- | :---- |
| **Description** | The OMR engine receives the uploaded image and performs optical recognition to extract musical semantics — pitches, rhythms, note durations, and structural elements. |
| **Preconditions** | Image has been successfully uploaded to the server. Network connection is active. |
| **Postconditions** | Raw musical data is extracted and ready for MusicXML generation. Target processing time is ≤10 seconds per standard sheet music page under stable network conditions. |
| **Main Flow** | 1\. OMR engine receives image payload from the mobile client. 2\. Engine prepares the uploaded image for optical recognition processing.  3\. Engine identifies staff lines, clefs, key/time signatures. 4\. Engine detects and classifies noteheads, rests, and accidentals per measure. 5\. Engine extracts rhythmic and pitch data for all staves. 6\. Engine returns structured musical data to the MusicXML generator. |
| **Alt / Exception** | A1 – Low-clarity image (handwritten/ornate): Engine returns partial data with confidence flags; user is warned of potential errors. E1 – Processing timeout (\>10s): System notifies user; user may retry with a clearer image. |

##### **Generate MusicXML & Separate SATB Parts**

| Actors | Secondary: OMR Engine; Primary: Choir Member (receives result) |
| :---- | :---- |
| **Description** | The system converts extracted musical data into the MusicXML standard format and structurally separates the four SATB parts into individually addressable tracks. |
| **Preconditions** | OMR processing has completed successfully under the controlled input assumption that uploaded sheet music is a standardized, high-contrast digital print without handwritten markings or severe image skew. |
| **Postconditions** | A valid MusicXML file is stored on the device with four independently accessible SATB tracks. Score is available for playback modules. |
| **Main Flow** | 1\. System receives raw musical data from the OMR engine. 2\. System maps each detected staff to an SATB voice type (S/A/T/B). 3\. System generates a MusicXML document with separate part elements per voice. 4\. System validates the generated MusicXML against the standard schema. 5\. System downloads and stores MusicXML on the device. 6\. System notifies the user that the score is ready. 7\. User can proceed to Module 2 (Selective Focus) or Module 3 (Dynamic Score Tracking). |
| **Alt / Exception** | A1 – Fewer than 4 staves detected: System flags ambiguous parts; user may manually assign voices. E1 – MusicXML validation fails: System logs error and prompts user to re-upload. |

##### 

##### 

##### 

##### 

##### 

##### *Activity Diagram*

#####  

@startuml  
' Activity Diagram — Module 1: OMR Score Digitization  
skinparam activityBackgroundColor \#EEF4FF  
skinparam activityBorderColor \#4A72C4  
skinparam ArrowColor \#4A72C4

|Choir Member|  
start

:Open app, tap "New Score";

fork  
  :Capture via Camera;  
fork again  
  :Import from Gallery/Storage;  
end fork

:Submit image for quality check;

if (Image meets quality threshold?) then (yes)  
  :Send image to OMR engine (network);

  |OMR Engine (Server)|

  :Pre-process image\\n(deskew, binarize, denoise);  
  :Detect staff lines,\\nclefs, key & time signatures;  
  :Classify noteheads,\\nrests, accidentals;  
  :Extract pitch & rhythm\\ndata per staff;

  if (Confidence ≥ 98%?) then (yes)  
    :Map staves to SATB voices;  
    :Generate MusicXML document;  
    :Validate MusicXML schema;  
  else (no — ornate/handwritten)  
    :Attach low-confidence flags\\nto affected measures;  
    :Generate partial MusicXML;  
  endif

  :Return MusicXML to mobile client;

  |Choir Member|

  :Download & store MusicXML\\non device;  
  :Display "Score ready" notification;

  if (User reviews digitized score?) then (yes)  
    :Show score preview with\\nflagged measures highlighted;  
    :User confirms or re-uploads;  
  else (skip)  
  endif

  :Proceed to Part Isolation\\n(Module 2);

else (no — low quality)  
  :Show image quality error;  
  :Prompt user to re-capture;  
  :Return to upload step;  
endif

stop  
@enduml

*Figure 1: The Digitize Score interface handling user uploads, image processing states, and local MusicXML storage.*

                                                      ** ![][image1]**

* **Module 2: Audio-Visual Selective Focus:** Plays the user’s assigned vocal part using synthesized human “Aahs/Oohs” soundfonts while suppressing non-assigned parts and visually dimming unrelated staves to reduce auditory and visual distraction during rehearsal practice. The system maintains the selected vocal line at full opacity while reducing non-assigned SATB staves to ≤20% opacity. 

#### *Audio-Visual Selective Focus Transaction*

##### *Use Case Diagram:*

##### 

@startuml  
' Module 2: Selective Part Isolation (Audio & Visual)

left to right direction  
skinparam actorStyle awesome

actor "Choir Member" as CM \#lightgreen  
actor "Audio Synth Engine" as AE \#lightgray  
actor "Choir Director" as CD \#lightyellow

rectangle "Selective Part Isolation" {  
  usecase "Select Vocal Part (SATB)" as UC1  
  usecase "Play Assigned Part\\n(Synthesized Vocal Tone)" as UC2  
  usecase "Mute Non-Assigned Tracks" as UC3  
  usecase "Dim Non-Assigned Staves\\n(≤20% Opacity)" as UC4  
  usecase "Toggle Audio Suppression" as UC5  
  usecase "Toggle Visual Focus" as UC6  
  usecase "Adjust Playback Volume" as UC7  
}

CM \--\> UC1  
CM \--\> UC7  
CD ..\> UC1 : \<\<extend\>\>  
UC1 ..\> UC2 : \<\<include\>\>  
UC1 ..\> UC3 : \<\<include\>\>  
UC1 ..\> UC4 : \<\<include\>\>  
UC2 ..\> UC5 : \<\<extend\>\>  
UC2 ..\> UC6 : \<\<extend\>\>  
AE \--\> UC2

@enduml

##### 

##### 

##### *Use Case Description:* **Select Voice Part and Apply Focus**

| Actors | Primary: Choir Member; Secondary: Choir Director (indirect via instruction) |
| :---- | :---- |
| **Description** | The user selects their assigned voice type (Soprano, Alto, Tenor, or Bass) from the digitized score. The system immediately applies audio and visual focus based on the selection. |
| **Preconditions** | Module 1 has completed successfully; MusicXML with 4 SATB tracks is loaded on the device. |
| **Postconditions** | Selected part is highlighted at 100% opacity; other parts are dimmed to ≤20% opacity; non-assigned audio tracks are significantly suppressed to minimize auditory interference. |
| **Main Flow** | System displays the digitized score with SATB part selector (S / A / T / B buttons). User taps their assigned voice part. System highlights the selected staff at 100% opacity. System reduces opacity of the three unassigned staves to ≤20%. System routes audio synthesis to the selected part's track only. System applies significant audio attenuation to non-assigned tracks.  Score remains spatially intact with measure alignment preserved.  |
| **Alt / Exception** | A1 – User changes part mid-session: System re-applies audio and visual focus to new selection immediately.  E1 – Score has fewer than 4 staves detected: System applies focus to available parts only and flags missing staves. |

##### **Play Assigned Part(Synthesized Vocal Tone)**

| Actors | Primary: Choir Member; Secondary: Audio Synthesis Engine |
| :---- | :---- |
| **Description** | The system synthesizes the assigned vocal part using the WebView-hosted Tone.js library, employing human 'Aahs/Oohs' soundfonts mapped to the MusicXML pitch and duration data. Reference tones should approximate standard musical pitch within ±10 cents deviation under optimal playback conditions.  |
| **Preconditions** | A vocal part has been selected (UC-2.1 complete); device audio is active. |
| **Postconditions** | Selected part is audible using synthesized human-vowel tones; non-assigned tracks are significantly attenuated to near-silent levels; playback is synchronized with Dynamic Score Tracking (Module 3). |
| **Main Flow** | 1\. User taps the Play button. 2\. System reads note pitch and duration data from MusicXML for the selected part. 3\. Audio synthesis engine maps pitches to 'Aah/Ooh' soundfont samples. 4\. System triggers sample playback with correct pitch frequency (approximately ±10 cents under standard playback conditions ). 5\. System simultaneously suppresses non-assigned tracks to near-silent levels. 6\. Playback continues measure by measure until user pauses or score ends. |
| **Alt / Exception** | A1 – User pauses playback: System halts audio and visual tracking at current position. E1 – Soundfont file unavailable: System falls back to MIDI tone with notification to user. |

#####  **Toggle Audio Suppression / Visual Focus**

| Actors | Primary: Choir Member |
| :---- | :---- |
| **Description** | The user can manually toggle audio suppression and visual opacity reduction on/off to switch between isolated practice mode and full-score review mode at any time. |
| **Preconditions** | A vocal part is selected and playback is active or paused. |
| **Postconditions** | Audio suppression and/or visual dimming state is toggled. All staves return to 100% opacity in full-view mode. |
| **Main Flow** | 1\. User taps "Audio Mute" toggle or "Visual Focus" toggle. 2\. System reads current toggle state. 3\. If disabling: System restores all tracks to equal volume and all staves to 100% opacity. 4\. If enabling: System re-applies significant audio suppression and ≤20% visual dimming to non-assigned parts. 5\. System updates UI indicators to reflect current mode. |
| **Alt / Exception** | A1 – Toggle triggered during active playback: Transition is applied seamlessly without interrupting playback. |

##### 

##### *Activity Diagram:*

##### @startuml

' Activity Diagram — Module 2: Selective Part Isolation  
skinparam swimlaneBackgroundColor \#F0FFF4

|Choir Member|  
start  
:Score loaded from Module 1;  
:View SATB part selector;  
:Tap assigned voice part (S/A/T/B);

|Audio Engine|  
:Load 'Aah/Ooh' soundfont\\nfor selected part;  
:Set selected track volume → 100%;  
:Apply ≥95% suppression\\nto non-assigned tracks;

|Render Engine|  
:Set assigned staff opacity → 100%;  
:Reduce non-assigned staves → ≤20%;  
:Verify measure alignment is preserved;

|Choir Member|  
:Tap Play;

fork  
  |Audio Engine|  
  :Synthesize vocal tone\\nper note (±5 cents);  
fork again  
  |Render Engine|  
  :Advance playhead\\n(Module 3 sync);  
end fork

|Choir Member|  
if (Toggle audio suppression?) then (yes)  
  :Re-apply or remove\\naudio suppression;  
endif

if (Toggle visual focus?) then (yes)  
  :Reset all staves to 100%\\nor re-apply dimming;  
endif

:Continue or pause playback;  
stop  
@enduml

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

##### 

*Figure 2: The primary Interactive Practice Screen demonstrating Audio-Visual Selective Focus (Module 2). Note: This unified screen also houses the UI elements for Dynamic Score Tracking (Module 3\) and Real-Time Pitch Feedback (Module 4).*

* **Module 3 – Sight-Reading Training: Dynamic Score Tracking** 

  Generates a synchronized visual playhead that highlights active musical notes in real-time to assist users in maintaining timing and score position during sight-reading practice. 

#### *Sight-Reading Training Transaction*

##### *Use Case Diagram:* 

@startuml  
title Dynamic Score Tracking

left to right direction

skinparam actorStyle awesome  
skinparam backgroundColor \#1a1a1a  
skinparam usecase {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 11  
  FontColor \#000000  
}  
skinparam actor {  
  BackgroundColor \#D6EAF8  
  BorderColor \#2E86C1  
  FontColor \#000000  
  FontSize 11  
}  
skinparam rectangle {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 12  
  FontStyle bold  
  FontColor \#000000  
}  
skinparam ArrowColor \#000000  
skinparam ArrowFontSize 10  
skinparam ArrowFontColor \#000000  
skinparam NoteBorderColor \#000000  
skinparam NoteBackgroundColor \#FFFDE7

actor "Choir Member" as CM  
actor "Playback\\nEngine" as PE

rectangle "Dynamic Score Tracking" {

  usecase "Initiate Playback\\nwith Tracking" as UC1  
  usecase "Highlight Active\\nNote(s)" as UC2  
  usecase "Advance Playhead\\nin Real-Time" as UC3  
  usecase "Pause and Resume\\nTracking" as UC4  
  usecase "Seek to Measure" as UC5

}

CM \--\> UC1  
CM \--\> UC4  
CM \--\> UC5

UC1 ..\> UC2 : \<\<include\>\>  
UC1 ..\> UC3 : \<\<include\>\>  
UC4 ..\> UC3 : \<\<include\>\>  
UC5 ..\> UC3 : \<\<include\>\>

PE \--\> UC2  
PE \--\> UC3

note right of UC2  
  Latency constraint:  
  highlight update \<= 0.1s  
  after audio event  
end note

note right of UC5  
  Maps tapped position  
  to MusicXML timestamp;  
  re-syncs audio \+ visual  
end note

@enduml

##### 

##### *Use Case Description:* **Initiate Playback with Tracking**

| Actors | Primary: Choir Member; Secondary: Playback Engine |
| :---- | :---- |
| **Description** | User initiates playback and the system, using the WebView-hosted OSMD library, synchronizes a visual playhead against the MusicXML timing data, highlighting the active note(s) in real-time as audio plays.  |
| **Preconditions** | Digitized score loaded; vocal part selected (Module 2); audio engine initialized. |
| **Postconditions** | Active note(s) are highlighted within ≤0.1s of the corresponding audio event. Score scrolls automatically to keep active measures visible. |
| **Main Flow** | 1\. User taps Play. 2\. System parses MusicXML timestamp and duration data for all notes. 3\. Playback engine starts internal audio clock at t=0. 4\. At each note's start timestamp, the render engine highlights the corresponding notehead on screen. 5\. System synchronizes visual note highlighting with audio playback while maintaining a target latency of ≤0.1 seconds. 6\. Previous note highlight is removed as the next note begins. 7\. Score view auto-scrolls to keep the active measure centered on screen. |
| **Alt / Exception** | E1 – Synchronization drift \>0.1s: System re-syncs playhead to audio clock position. A1 – User seeks to a different measure: Playhead jumps to target position; audio and visual re-sync. |

##### *Activity Diagram*

@startuml  
title Module 3: Dynamic Score Tracking — Activity Diagram

skinparam backgroundColor \#FFFFFF  
skinparam activity {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 11  
  FontColor \#000000  
  DiamondBackgroundColor \#FFFFFF  
  DiamondBorderColor \#000000  
  DiamondFontSize 10  
  DiamondFontColor \#000000  
  ArrowColor \#000000  
  ArrowFontSize 10  
  StartColor \#000000  
  EndColor \#000000  
}  
skinparam swimlane {  
  BorderColor \#000000  
  BorderThickness 1  
  TitleFontSize 12  
  TitleFontStyle bold  
  TitleFontColor \#000000  
  BackgroundColor \#FFFFFF  
}  
skinparam ArrowColor \#000000

|Choir Member|  
start  
:Open loaded score;  
:Tap Play;

|Playback Engine|  
:Parse MusicXML timing data\\n(timestamps \+ note durations);  
:Initialize playhead at\\nmeasure 1, beat 1;  
:Start internal audio clock\\nat t=0;

|Render Engine|  
:Initialize note highlight\\nat first note position;

|Playback Engine|

repeat

  :Read next note\\nstart timestamp;  
  :Emit audio for current note\\n(passes to Module 2 synth engine);

  |Render Engine|  
  :Highlight active notehead\\non score display;  
  :Verify latency \<= 0.1s\\nvs. audio clock;  
  :Remove previous\\nnote highlight;

  |Choir Member|  
  if (User taps Pause?) then (yes)  
    :Freeze audio clock;

    |Render Engine|  
    :Hold highlight at\\ncurrent note position;

    |Choir Member|  
    if (User seeks to a measure?) then (yes)  
      |Playback Engine|  
      :Map tapped screen position\\nto MusicXML timestamp;  
      :Seek audio clock\\nto target timestamp;

      |Render Engine|  
      :Jump playhead highlight\\nto target note;  
    else (no)  
    endif

    |Choir Member|  
    :Tap Resume;

    |Playback Engine|  
    :Resume audio clock\\nfrom current position;  
  else (no)  
  endif

  |Playback Engine|  
  :Advance clock by\\ncurrent note duration;

repeat while (More notes remaining?) is (yes)  
\-\> no;

|Render Engine|  
:Clear active note highlight;

|Choir Member|  
:Score playback complete;  
:View session summary;

stop

@enduml

### ***Architectural Specification: Hybrid WebView (OSMD \+ Tone.js)***

The ScoreRenderer and AudioSynthesisEngine are implemented using a hybrid architecture relying on an Android WebView to host front-end JavaScript libraries, ensuring platform-agnostic rendering and utilizing modern web audio standards.  
**1\. Hybrid Core Libraries:**

* **Score Rendering:** Open Sheet Music Display (OSMD) is used within the WebView to parse the MusicXML data and render the score as an SVG.  
* **Audio Synthesis:** Tone.js, leveraging the Web Audio API, is used for note playback and soundfont management.

**2\. JavaScript-Kotlin Bridge (*VoxSightJsBridge*):**  
A defined interface, *VoxSightJsBridge*, facilitates two-way communication between the native Kotlin/Android layer (which handles mic input, storage, and UI controls) and the WebView's JavaScript environment (which handles rendering and audio).

| Direction | Kotlin Method | JS Function Invoked | Purpose |
| :---: | ----- | ----- | ----- |
| K → JS | *setPartFocus(partId, opacity)* | *VoxSight.applyVisualFocus(partId, opacity)* | Triggers visual dimming/highlighting of staves based on user selection (Module 2). |
| K → JS | *startPlayback(musicXML)* | *ToneEngine.loadAndPlay(musicXMLData)* | Initiates note scheduling and synthesis in Tone.js, starting the internal clock. |
| JS → K | *postNoteCoordinates(x, y, width, height, time)* | *VoxSightJsBridge.updatePlayheadPosition(jsonString)* | Transmits the bounding box of the active note for native canvas overlay. |
| JS → K | *postReadyState(status)* | *VoxSightJsBridge.onEngineReady(boolean)* | Signals to the native layer that OSMD and Tone.js are fully initialized and the score is rendered. |

**3\. Coordinates Transfer Format (JS to K):**  
When the Tone.js/OSMD playhead advances (Module 3), the JavaScript environment retrieves the bounding box coordinates of the active note's notehead element (via the OSMD API). These coordinates are serialized into a JSON string and transferred to the native Kotlin layer using the *updatePlayheadPosition* bridge function.

* **Format:** A JSON string containing screen-relative coordinates:

  *{"x": \[Float\], "y": \[Float\], "w": \[Float\], "h": \[Float\], "time": \[ms\]}*

  *x*, *y*: Top-left corner of the note bounding box (normalized to WebView dimensions).

  *w*, *h*: Width and height of the bounding box.

  *time*: Playback timestamp for synchronization checks.

**4\. Canvas Overlay Highlighting:**  
The native Kotlin layer receives the bounding box coordinates via the bridge. Since the score is an SVG inside a WebView, the native application renders a transparent **Canvas Overlay** positioned precisely above the WebView element. This overlay uses the received coordinates to draw the real-time note highlight and the color-coded pitch feedback (Module 4\) directly over the underlying OSMD-rendered SVG notes, achieving the required synchronization latency (≤0.1s for playhead, ≤0.5s for pitch feedback).

**Wireframe Note:** The Dynamic Score Tracking playhead is rendered directly on the primary practice interface. Please refer to the wireframe in Module 2 (**Figure 2 in Module 2\)** to view the visual synchronization of the playhead against the sheet music. 

* **Module 4 – Sight-Reading Training: Real-Time Pitch Feedback** Captures the user’s sung pitch via the microphone and displays an immediate visual indicator (e.g., color coding) against the digitized reference notes.

#### *Sight-Reading Training Transaction*

##### *Use Case Diagram*

@startuml  
title Real-Time Pitch Feedback

left to right direction

skinparam actorStyle awesome  
skinparam backgroundColor \#1a1a1a  
skinparam usecase {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 11  
  FontColor \#000000  
}  
skinparam actor {  
  BackgroundColor \#FADBD8  
  BorderColor \#C0392B  
  FontColor \#000000  
  FontSize 11  
}  
skinparam rectangle {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 12  
  FontStyle bold  
  FontColor \#000000  
}  
skinparam ArrowColor \#000000  
skinparam ArrowFontSize 10  
skinparam ArrowFontColor \#000000  
skinparam NoteBorderColor \#000000  
skinparam NoteBackgroundColor \#FFFDE7

actor "Choir Member" as CM  
actor "Device\\nMicrophone" as MIC  
actor "Pitch Detection\\nEngine" as PDE

rectangle "Real-Time Pitch Feedback Module" {

  usecase "Enable\\nMicrophone" as UC1  
  usecase "Capture Vocal\\nFrequency" as UC2  
  usecase "Detect\\nPitch (Hz)" as UC3  
  usecase "Compare Pitch\\nto Reference Note" as UC4  
  usecase "Display Visual\\nFeedback Indicator" as UC5  
  usecase "Review Pitch\\nAccuracy Summary" as UC6

}

CM \--\> UC1  
CM \--\> UC6

UC1 ..\> UC2 : \<\<include\>\>  
UC2 ..\> UC3 : \<\<include\>\>  
UC3 ..\> UC4 : \<\<include\>\>  
UC4 ..\> UC5 : \<\<include\>\>  
UC5 ..\> UC6 : \<\<extend\>\>

MIC \--\> UC2  
PDE \--\> UC3  
PDE \--\> UC4

note right of UC5  
  Color coding:  
  GREEN  \= deviation \<= \+/-50 cents  
  YELLOW \= deviation 51-100 cents  
  RED    \= deviation \> 100 cents  
  Render within \<= 0.5s of input  
end note

note right of UC3  
  Algorithm: FFT / YIN  
  Accuracy target:  
  \>= 85% match rate  
  vs. standard tuner  
end note

@enduml

##### *Use Case Description*

| Actors | Primary: Choir Member. Secondary: Pitch Detection Engine / Device Microphone. |
| :---- | :---- |
| **Description** | The system utilizes the device microphone to capture the user's sung audio in real-time. It processes the vocal frequency and provides an immediate visual indicator (such as correct/incorrect color coding) on the screen to show whether the sung pitch matches the target note from the digitized score.  |
| **Preconditions** | The application is open, a digitized score is loaded, and a vocal part is selected. The user has granted the application permission to access the device microphone. The user is in a relatively quiet environment for optimal detection. |
| **Postconditions** | The user's sung pitch is visually evaluated against the reference note on the screen with a display delay of ≤0.5 seconds. The system targets a pitch match accuracy of approximately ≥85% under quiet environmental conditions. |
| **Main Flow** | The user selects a digitized score from their library. The system prompts the user to select a practice mode: "Listen" (Study) or "Test Pitch" (Practice Feedback). **If "Listen" is selected:** The system disables the Pitch Detection Engine and loads the Interactive Practice Screen. The user listens to the synthesized track with dynamic score tracking, but no visual pitch indicators are rendered. **If "Test Pitch" is selected:** The system verifies microphone permissions and activates the device microphone *before* loading the Interactive Practice Screen. The user initiates playback. The device microphone continuously captures the raw audio input. The Pitch Detection Engine analyzes the audio frequency to determine the sung pitch. The system compares the detected vocal frequency to the pitch data of the current target note stored in the MusicXML file. The system renders a visual indicator (e.g., color-coded note highlights) reflecting pitch accuracy within the required ≤0.5 seconds latency. This process loops seamlessly until the user pauses playback or the score ends. |
| **Alt / Exception** | **E1 \- Background Noise Interference:** If environmental noise obscures the vocal input to the point that pitch detection falls below the optimal threshold, the system displays a "Noise Warning" and pauses visual feedback. **E2 \- Microphone Permission Denied:** If the system cannot access the microphone, it displays an error prompt directing the user to device settings; pitch tracking remains disabled. **A1 \- Pitch Deviation:** If the user's sung pitch deviates from the reference tone beyond the acceptable ±10 cents threshold, the system immediately updates the visual indicator to reflect an incorrect pitch. |

##### *Activity Diagram*

@startuml  
title Module 4: Real-Time Pitch Feedback — Activity Diagram

skinparam backgroundColor \#FFFFFF  
skinparam activity {  
  BackgroundColor \#FFFFFF  
  BorderColor \#000000  
  BorderThickness 1  
  FontSize 11  
  FontColor \#000000  
  DiamondBackgroundColor \#FFFFFF  
  DiamondBorderColor \#000000  
  DiamondFontSize 10  
  DiamondFontColor \#000000  
  ArrowColor \#000000  
  ArrowFontSize 10  
  StartColor \#000000  
  EndColor \#000000  
}  
skinparam swimlane {  
  BorderColor \#000000  
  BorderThickness 1  
  TitleFontSize 12  
  TitleFontStyle bold  
  TitleFontColor \#000000  
  BackgroundColor \#FFFFFF  
}  
skinparam ArrowColor \#000000

|Choir Member|  
start  
:Enable pitch feedback toggle;

|System|  
if (Microphone permission granted?) then (yes)  
  :Activate device microphone;  
else (no)  
  :Display permission error;  
  :Disable Module 4;  
  stop  
endif

|Choir Member|  
:Begin singing along\\nwith audio playback;

|Microphone / Signal Processor|

repeat

  :Capture audio frame\\nfrom device microphone;  
  :Apply noise filter\\n(low-pass / high-pass);

  if (Vocal signal detected?) then (yes)  
    :Perform pitch detection\\n(FFT / YIN algorithm);  
    :Extract dominant\\nfrequency (Hz);

    |Pitch Comparison Engine|  
    :Fetch reference note\\nfrom Module 3 playhead\\n(current note \+ octave);  
    :Convert reference MIDI\\npitch to Hz;  
    :Calculate cent deviation:\\n1200 x log2(sung Hz / ref Hz);

    if (Deviation \<= \+/-50 cents?) then (in-tune)  
      |Render Engine|  
      :Display GREEN indicator;  
    else if (Deviation 51-100 cents?) then (close)  
      |Render Engine|  
      :Display YELLOW indicator\\nwith sharp/flat direction;  
    else (out of range)  
      |Render Engine|  
      :Display RED indicator;  
    endif

    |Render Engine|  
    :Verify render latency \<= 0.5s\\nfrom vocal input;  
    :Log result to session buffer\\n(note, Hz, deviation, result);

    |Choir Member|  
    :Self-correct pitch\\nbased on indicator;

  else (no signal / rest)  
    |Render Engine|  
    :Display neutral\\n(inactive) indicator;  
  endif

  |Microphone / Signal Processor|

repeat while (Microphone active\\nand playback running?) is (yes)  
\-\> no;

|System|  
:Calculate overall pitch\\naccuracy % from session buffer;  
:Identify measures below\\naccuracy threshold;

|Choir Member|  
:View pitch accuracy summary\\n(overall %, flagged measures);

if (User taps a flagged measure?) then (yes)  
  :Navigate to flagged measure\\nfor repeat practice;  
else (no)  
  :End session;  
endif

stop

@enduml

	

*Figure 3: The pre-selection "Gatekeeper" modal prompting the user for their practice mode before initializing the microphone hardware, preventing race conditions.*

![][image2]

*Wireframe Note: The live green/yellow/red pitch feedback indicator is rendered directly on the active sheet music. Please refer to **Figure 2 in Module 2** to view this real-time indicator.*

*Figure 4: The post-session Practice Summary Screen generated by the Module 4 session buffer, displaying overall accuracy and specific flagged measures for review.*

![][image3]

## ***3.3	Non-functional requirements*** {#3.3-non-functional-requirements}

### ***Performance*** {#performance}

**The system must meet the following performance metrics:**

* **OMR Processing Speed:** ≤10 seconds per page.  
* **OMR Separation Accuracy:** Approximately 80–85% structural track separation accuracy under controlled input conditions using standardized high-contrast digital sheet music inputs.   
* **Audio-Visual Synchronization Latency:** ≤0.1 seconds note highlight latency.  
* **Pitch Feedback Latency:** ≤0.5 seconds visual feedback display delay.  
* **Pitch Detection Accuracy:** Approximately ≥85% pitch match accuracy against reference notes under quiet environmental conditions.   
* **Auditory Suppression:** Significant attenuation of non-assigned background tracks to minimize auditory interference during isolated vocal practice.

### ***Security*** {#security}

The system must ensure the security of user-uploaded score images and recorded microphone input data, treating them as private user data. Such data in transit shall be encrypted using TLS 1.3. Local storage of MusicXML files shall utilize Android Scoped Storage. 

### ***Reliability*** {#reliability}

The application shall maintain stable playback, synchronized score tracking, and pitch feedback functionality during active practice sessions under standard Android operating conditions. In the event of synchronization drift exceeding the ≤0.1 second threshold, the system shall automatically re-synchronize the visual playhead with the active audio clock. Pitch reference tones shall maintain an approximate frequency deviation threshold of ±10 cents under standard playback conditions. 

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAANEAAAHLCAYAAABWAH2mAAA4mElEQVR4Xu2dCbgcVZn+0Rn/jo4yzvz9/2dcZoZnNsEFdUZAHdRRdnAcCCA4AhIwDCAQIpEdEsKSXRIgyAQQsuCwyRKWAEKEQAAJkBCCskpISFiyL2QxIWfqPdVf1VdfVd97+57b91Z1v+/zvE9VV1dVb+fX73dOVXdts3GTczRNd9/b2AU0TTdmQkTTgSZENB1oQkTTgSZENB1oQkTTgSZENB1oQkTTgSZENB1oQkTTgSZENB1oQtREv71sdW4ZXS7/8spZuWWNOhiipW+9456dvTC3vEx++feL3IMzH0v88iuLcus0wx/75HbugAMPzS3vrqf+4iY37c57c8vp7vnaMfe7r39koHv1xWW5+xpxMESvvrjU7fu3p2dAOvvwn7svbjMgt25fGg0axvyIURf7+Z5MCoCKfWLfybIegnWnL+/qPvXpHf089inzdJgRAMNPvD63vFEHQyQGSKD65O9McL+8Kjwie9oaIjREzPtv9jvu9fNnnn2e+9QOO/r5y6+42k932mXX5H6dKLh91jnD/Hpo0IBRtsV6SDu7HSDDuliOx0qei4IPy4uSS/aNdfXyBx96LH3MaB7P175GPOZue+yb7GO3Pfd1Rxw5wM+PHD3OrytfKvAJJ53i94P19HZ4vUWvA4BjuX8vaut+a499ktckj4Xt5X3C/p+e81zudfa28cWPdguY7H2NuMcgQiIhfcqWQGJpJJgHPL5R1pIC8/hg314af8hYvv+Bh/gGVbQt1pX9yjwakzQc/ZgCxQ/6H5PsAyWZzJ84cLA7on/8nuHxsRwQ6ucuUOv9FT0Gnq/sV+6D8ZwFFGm8WBeQYF6Ak23l8fA8ZDtJPzxHeb4CsbyP8h5gHoBhuX1NWMe+vr7yWYdf49traHekxyDCk0ECASYkUugT62lLI0GjQKPCh6vvk29aMRomlsu3qjQOND7bkOHOINL7sNsBREArxvOz68ECsG6U+jHqQYTGLF8cch+ep6SIhUjDIdvJfZKceF8kZYog0sb+JLHli6ksDu0PwT0CEfpAuoST0s6u15eu14jlPt34ZVlREnUXIil1irazj22NsuvpuXGCyDe7gIZ5SRSddnKfNPKeggiQSxLJdp1BJM8FCSzrlsH4wp805v7c8kYdDBGeSFEfCMvtsr4yvgF1g9L3obzBcjR2fZ9vnFG/QEovaRzSiFGGIQ3QcLB/NC4sl3JFGhi+gQGANEyUStgntsf+ZD3Zn3xj6+co+8FyrIPH0vfJ9pJSvoxSrxmPJzAIjIBInpsGxT9G7bnq7WBsh8eW90WgxTzex6TvVgCK3kdZDIB8OfdEWNUUDBFNd8X4cilbKddTJkR0r7heQrWCCRHdVKM0bFV4xISIpgNNiGg60ISIpgNNiGg60ISIpgNNiGg60ISIpgNNiGg60ISIpgNNiGg60Nts3eocTdPd9zaOoqggESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhKpG2bt3qNm/e4jZt2uzWr9+Y88aNf/D3bdnyrl+XKocIUS8JcKxbt8G9vXSVe+PNFe7lF5e4KePvc2MG3+B+8NXhbu+/PtXtuu2JycWju2psc9Bnh7jzjpnkfjZsmrvvlifdypVr/WMRtN4RIWqCkBqrVq/zsMx64DnfuLsKyJ6fOMX7+zsPcwN2G+lOPmC8N+axDP7XD5+Q264jAzLAtXzFGp9iVM+KEPWQ8K2Pb380VMCDRot0sQ1aDBAAx4UnXONunXy/e+SB2W7Ok/PcCy+80GVjm6mX3eUuPuMXXYILIE8Z/yv322dei0DfZF8C1U0RogABnDffWumhGTP4xkJo0LgBSqOA9IQfmfGEmzj8lxGs43LPS6D6ySFX+NRkQnVfhKhBoWMPaCRtdKOUdLGNuUwGWEiu/bc/KwcVvgSeffpVplSDIkQNSEq1yVFJtJdJHXzj90XahBgJhf6XhQnpRJC6LkLUiVDmSPJgFE2nDso02zCravTLbNmHZMJIH0u9jkWI6gj9nbXrNvg+z9zZr2QAQurYRtgqLgIJgxHoN1HFIkRGgGf1mvVJn0fKNiSPbXCtbPSdLFAYPEEyUVkRoposPNJwMLpmG1i7GWWrHj4HTBzRS0WIXNzvkTMJhg6YlCTPuDN/kWtQ7Wokky3zMJJHEaIkfW659hF/3ITwdGwLE0byFr221L6tbaW2hUhG3XAOmzSIs35wRa7R0MUGTHp4/NZJj/gTZNtRbQkRDpjKqNuBnx3i0+e6CXflGgrdsW0qoa+EEc12U9tBJMd8Hrl/vv/gOXAQbpwBoWFqN5DaCqK16+L+D457oP/D8q3nrEs7pPviRcvs29+yahuIJIHkg26lsw3KYttPemH+IvsxtKRaHqJ172xIRt/wwU4cfkvuw6d71jaV3lyywn4sLaWWhgijRZJA8qM4+4HTPW+bSACpldWyEMkQNkbgcOoOy7fetwZp2H9N9j+Rb0W1JEQ4hQdnIOAYkJz7Zj9guvlGIunThS4+7Wb7UbWEWhIiPYiAb8Oq/c6n1ayHwF98brH9uCqvloJIzoGTEo4HUMtjHI8TkH607/iWOnm1pSCSf9hBR5YlXLlsBxuuGn6X/fgqq5aBSM7CRgrhw8KHZj9Iuu8tEMErlq6xH2Ml1RIQ4f8AABD+CJEJVG7bwYZZ9863H2flVHmIdD9IBhLsB0eXy3qgAb9LqroqD5EeicPPme0HRpfTt065PwHpiRm/sx9rpdQSECGFUCKwH1QtC0T9vzay0v8bXmmIdBlnPyC6/NZl3dc/MtCtWfmO/YgrocpCJKNx7AdV27qsA0hVVCUhktE4gQh/PGg/HLo6FojgKqqSEAlA+ENFAlR967MZzj3yGvtxl16Vg0jOzsa/zLAv1DrWIF0xdJr92EutykGEU3t4TKj1bAcZqqRKQSR9Ifl5A4e0W8v4zVcV+0aVgmjpstU8sNrC1n/BNfvXz9uPv7SqFET6BFP7AdCtYV3SVeW4UWUg+sMftsQ/c/jMuSzjWti4wLOA9MN/G22bQSlVGYhWr3mHZye0gadOuCuBqCp9o0pAJMPacqEt+8bTrWU93F0FVQIiGZXDm8r+UOtbD3dXoV9UCYj0eXLXTbgz96bTrWeBaPTJ19vmUDpVAiIA9NILi90BO5yZe7PLYl2CaO/5icFu3Fn1r3eEfyLSv/S0204cUfyPrThxE/d3df1HZsz261TlD/z161qyoNz/610ZiO67ZXYpjw0BAgvQyQeMzy1DI7bbYpkFAaNT+g89YLvddZenne8Ddjgrd1UGuz6M54T7qnLRZv16pl07yzaJUqkyEB2/zzh33+0P597svrb+sO23vD4CDyM96m1rvyA0hLtue0Lmv/P0drIMcMiyon97teuX3QN2G5U850O/MMw2iVKp9BBt2BD/n3ZZG4Bu0PbP8u1FsI7ZfWTdbe0XhB3q1WDo5bLs6dnzMss1dBeeGMNclRSC7esvc0lXeojWrl3vZj0QX5DLvtFlsP6g7bVeG4HIDpjYRqT3rZfX258uH1EyVm1U0753ZS7pSg8Rzpf72bBpuQZTFusPGtYde+mHwEX/AaG3Q8mmG74u5ywAert6y+V3VtLn0utVxbpvOKTEvzMqPUS4turQAdeWtiEUjawV3WdLPdhuhwZfdF9H8NVbjnIOQBetVxVXpV9UaojkfDmcqYBvavsml8X33vZwpgFroy/T0R/q28EHMb6F6/Vh9HqyTP9XgQxS6OdQNBIIl/l9te9NWVVqiGRQARfosiNfZXNRImnb9cUdHSeqt+2ZR8S/6oXlGJRdX1JIBiTkPg2mPG5Z31vbLyyrSg3R+vUbk5E52ykvi5FCOFYjHzQupmyP88BYxyYSjvdogNDgi7bF6Jp9XA2StqSQpI6UgnK/fg6632WH38vge2+LLxEqLqtKDRFG5gSisl7pWz7goj/RL0oYuU/OIICLksBu10jZZQHS3+h6PV0ulfFLyo7QlVWlhmjVqvhSKXgDiw4g9rV1GhQNHMD2zIWi5fYYEYzjPhZCu0492/U1LHo9fYC2COQyWL/+sooQBRilk3zA9SDS6+hGrMu2olOC4P23T8tEC0A9ywCDPgNCw6LXJUQ9I0IUYH3OWr1ys14SdQXARpNISkR7CpE+m0EvzwxQmAPFZbF+/WUVIQqwbpz2/DZY93tgfdBUD0kXpYA+xgN3pc8iB3dt3wyW/dQ7oFu0TRms34OyqtQQ6YGFMkIE22MZcNFZ3EXXj7UjbBgQKNq2Kw1cnx1h74P1PjHaN3FEWsp1Zf99Zf0+lFWlhmjdug0eIvzPHBqJfYPLZKTQ1Mvu8iUewIIx/G3XKzIaMbaV7fzB0QeK+0n1jPVhm4bW6AfJY5T9L5g5OtcDkuNEOGOhK+UM3VrWJS8h6qbktB/877Y+r4xuD+vRQ0LUTcm//OCCxo0cbKRbw7a/WVaVGiIIf1JS5t8T0c2zHgwp85/clx4iGebGSaj2TaZb2zqFBu0/wTaN0qj0EMkI3RFfHZ57k+nWtT35tMx/nVV6iKRfNHn8fZ0O3/a0x/90gjv1x2e7IWdd4E46Lh7YmDLpF27u3Ll+mSzHdO7cZ/z9Q8++0E284mq/7LHHHvfL7rpjerJPzGO/9rEGHPmj5DHgeL/xvgYceUKy/0t+erlfNuKCscn6jz0aP474sEOOjqffPTp5niMvGpt7PJnH8zztlLP9PrHuadFrnvHAr3PPR2/fbNv+UJmvElF6iCBABPf2qSlo7HfdmQIAA6J6t9EwpbHDN98Yn87TGUQjLhzrbr7pltxybWyHhjzjgQdz99WDCBDqx5DXMvTsC/y83AZsent8Sch0xowsTL1lfcpTmX/VClUKot4+4IpGDUjQSEdeGH+TdwTRSccV/xkIABgaNWgY3/IWFmmk0njrWaeHdj2I8Fi4D8ZjCODyeAAL0xPrPG/4wH//fu4194ar0h+CKgUR3tDeLOkaTSKUQnp9aaydJZGUjPDEK37ul0kpKPNYB9vJ/WKAYeETKHQSjb84fkykkC5R8XwBm05QnXYCZG9bQ/T8nIW2SZRKlYBIrpCHEbp6Zzw3w41CJN/4mEefQu7rDCJt2R79IGnMmMd2gGXAD+LjZXgs9L9ku0suvtyDgOXyuBoilGy4X7aX54J1MJXHw+vVidfXEJV5aFtUCYg2bYr/awGDCzzo2vrWZ7BfN+5+2xxKp0pABC1fsYZD3W1g+0+uVVBlIJLjRfgjR/vG061j+zuqKqgyEEEywFDW3xbR4dYA7bfd6bYJlFKVggj/hioDDGX8iyc63BqiMh9g1aoURCtWrvUQ4axu/r6o9ax/Tl+VFIIqBREkl57E9YpY1rWOMaCg/+Z48avlvZSKVeUgkv9duPeW2RzubiFXcUBBVDmIIBlgAEj2w6CrZwtQlUo5qJIQyW+M4KK/m6Kr5SoDBFUSIjmDQQ6+cqSu2tYQ/WzI7fbjLr0qCREkvzOC8Ucm9oOhq2F7zaQqqrIQQVLWzZ39Sq+e3U33jC8+M/0b5qqWclClIYIkjfp95ty6fwxPl9OtABBUeYjkanowru2qLzxMl9e6jDvkC+fZj7VSqjxEkJzh/dILi30i2Q+MLpfxX+A6hap0YLVILQERJGkEy3VM6fLZDiRUuYwTtQxEGK2TE1Qx0HDYLufnPkC6b20Pqp575DX2Y6ykWgYiaN078W+O4J+dz98dlckY9LEpVPUyTtRSEEHvvrs1AenYvX7KEbuSWMMDV/Ggaj21HERQpqz78gUEqY9tBxKqPhpn1ZIQQXK2N8wTVfvGuMiZve5sVX5o14haFiJI/moLHj34Bv+NaD9oujm216uFy/x/2iFqaYjQP5If8QlI9sOme95FgwitVsJptTREIn2yKhOpuS5KoFY4FtSR2gIiCCDpVJo8/le5BkB33+j/2PTBv5eW/S+Ae0JtAxGkEwke1O8Snv3dQ7YDCHA7AAS1FUSQTSQMg993+8O5RkF3zWf+4IocPO2SQKK2gwjCgIP8/Zb0k3i+XeMu6v+0UwKJ2hIikT5NSFKJB2Y7d1H6wBiBa5VTeRpRW0Mkkp9SiHFwljDljV+iFvV94HYWIapJn+EA4wd+HApPjdE3Cw6M/s+MW5+2b2dbiRAZWZjgi06a1LbJZIettVv1DIRGRYgKZIfC0VdCMrUTSPgbMvynnwVH0qcVz4HrrghRBwJM+o8iBSj8uKxVgaoHjsDT7qVbkQhRF7V+/cZcmQdfcvYNlf7zSBxs7qhkg3/4b6OZPB2IEDUggKQP1IofuX++u23qjMql04DdRtYdbRNPu3aWfRsoI0LUTeGvjG2pJ8YQ+TVj7yoVVEjLs+oc37FG2bZm5Tv2JVN1RIh6SABKflFbZPSlnnr8BXf/HY+7+26f1bRz9gAuYOmob1NklmzdFyHqYW3YsKlDmMQvv7jEPXTPHPfbZ192zzz9O/fog09FYD2bg6KeAQvO+Zs44pcemj0/cUqnpVmROdIWLkLUZGGED2DpX9k2atvwQzxo/wnu9mtmsVzrQRGiXtTWrVvdunUbfOm3fHn2VKOObEHojnFglInTHBGiCsgCUc/7bne6/0NEwMKk6T0RogoIZ0a349nRVREhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAESKKChQhoqhAEaI20aOPPmYXUT2khiE68qgBbqcv7+o+9sntvK30fdPvudfe3XJavXp1txvowoWL/Psp7xfeu099ekc/f8ONN9nVu63rb7jJ73P02IvtXVQPqGGIIMAhHzwakQiNSZbvHDWIdtDEK68u/DJpRPKeAZxV0fu5+177+tuzuglnkXpyX1RW3YIIsMi328mDTkmW45sUDaEIIvmG9fd9ZVc3f/5zuftkeuDBh6ot4/sl4bAtJPuCFy1alLkt0sv09gI+HkevM+an45JtJRUwxTZFydD/6DRFYHkvno1eG/atX7P+srGSdeQx5L3F/kXYp6yH5zPxqqsz2+p9JOtFz/3Agw9Jbo9RSYQUlOV4nvLa7f4gPB/5PAdGr1Hu0599O6vbEKHhSkOTkgZvLOblAxTJBzZ67Dh3zpDzkg9O5NevNW794UH4APHBQf1qDUIk39h4LngMabQieSwY+ziy1uixPiSPqxsUJPtaqNbDY1nh2122wzy2g/TzkH31M18MWrIPgQiNXd/GPvBey/tgH1MeT38xYX2RvA8aItyP90O/5wBT9iefKyQgivy6V8YQU4EQSeNDA5EUgrBMQ4TbuhHKh1Yke580gKJGLN+KAoWkjUi+0YseS/YrZY7eFxqjfw0RYOcOHWa2zKpo/7itvyTkedRreLIPnVxathHL65THEJjPHRI/V58ctcSG5AtOIOp/1DHJa4V0Wsn2mMd+9XsoUAnMVKwgiCB5g3XpgdsCEWp83O530KH+m10bkgYLSLBMf5gidIiLBjNCIJJt/be5eU4Qtu13UNq4iiCGivavXz8kjVgauZXsA19Ckra6DyOvy75/+vnKPqS/qlPJQiTQYJ2O9ofXII+L54UvS+yro9K0HdVjEOk+g21EUh4UySaC7E8kJRJkIQmBSB633giiNChb6lkV3Yfb3UkivIfS99F9NwGro8YrKYaGrlMIshDpL5B6kuckX44ygFLvi6Cd1TBEaLConWF8qHp0SveNdE0tDdF30H0fZ3CyjZQi/Y8+xt9n63s0oHOikgqN+sij4zJEJNtivwBI+kjYFo8t6+tlWnIfHhevQ/oRUg7JNv75HZUmrZbsAw1UvkgEZmyP9wOvCf1BK+xbD4rgdUKS3jDeZ9yWfeK9wyAA9qm/uHT/TCcK9o/9YrlOUwFT708PgdsvJEj2T2XVMEQURWVFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUISIogJFiCgqUN2CaP7AgXYRRbWtGoZoxaOPuvs+/nG7mKLaVg1DBIAIEUWlagiixddfT4goyqghiF4eM4YQUZQRIaKoQBEiigoUIaIqLWmTVmvmz7eLmiZCRLWE0Cbnn3yyn5+x/fZu5s47uycPPNAfkmm2CBHVEpK2CXDmHHWUhwcwCUivXXll09KpqRDhyhBikVx1DdYXnSqb8Ofv9kJl+JN3/PF7vStJUH2vBRMnJvObV6/2bRYwPbzLLm7xDTeoNXtOTYVIX6lOZC8oVTbpqyjY542rLcgVG+wVJqjyaf3ChT6BZkdpVFmIkDS4ricu4yGXAcG1ceyV3zB/zpBhHjq5dqhcJkWu7TNm7LjkEiNIAlyHB6mgr+qGtMB2ci1RuTwILtuCqaQI5rEebjdy8S4RluvLl1DlFUASI5maoV6BSL69cQErSEOERi3XLYIFCAjX95FlKK3kOke4qLC+Ng9gkW3kMSG5mJW+MJXAgf0CxI5AsfdJSUdRWr0CESTf/pCGSC6ohXXF+JaXFMGFpwQiSJLEL6tdEa4ziHTfS7bVj1ekIojwOCcNGpxZRlG9CpE0+qJyTi4pL1fZxjI9D4gAkFzGUWCKt4mvpod+Chq6PKZc4FeDIleIk6vSydTKQuSvKFeDTj8GRTUVokZkL7wry4o68EXLi5Z1JKzbyPoUVU+lgYiiqipCRFGBIkQUFShCRFGBaipEOGdJjANd9Q524f4NtUEFnN+kt+uNEwgpKkRNhUjOpoWXz5pVFwjsD6dmQJhifSyTbSmqzGoqRAKGqAgipNP6KIUAiz63Cec6UVQV1FSIHttjD59AMFQE0dz+/f3yV8aO9cklIkRUVdRUiDpLIqSQlHIwIaKqqKZCZE8937Bwod+H+K3p0zNg6R9N4RR2iqqCmgoRRbWDCBFFBYoQUVSgCBFFBYoQUVSgCBFFBYoQUVSgegWirZu3uHVvvOXWLlpM073mTavXuM3vvGObY4+r6RABnreenueWL3zDrXxzOU33mpe9vNCteHmBW7fkLdsse1RNhWjp7152a9Zuouk+9epV692aKJmapaZCZF8MTfeVV77+llv/9jLbRHtETYNoy8aNuRdC033l1W+vJEQ0HWJCRNOBJkQ0HejKQvTMb56n6V73b+e8kvOi51+rJkT224Cme8PLl6/LeRlH52g6zJUt5+wLoem+MiGi6UATIpoONCGi6UATIppuwCuWv5MzR+fa3PKei+39dNZ2eJtD3DQh6gGznGtz4/1+fP9+7rcXjfS299OdmxC1ufF+E54wE6I2NyEKNyFqcxOicBOiNvS8M872/SCYEIW7shDZ09HprvmBz38xeZ/Fjw8+K7ceXewXn3st58UvLaomRPbbgO7cS59/JQcQzCQKc2WTyL4Qumt+aKf4ws/ar9//UG49uusmRG3oJw//gYfp8f0PyN1HN25CRNOBriREkP/nyYIXRNO97ZULXm/a/3I3FSL8qfjyVxblXhBN96ZXLF7qK6NmqakQQQDprafmuZVvrqDpXvXyhW+6ZS+84tYsbN7/cENNh4iiWl2EiKIC1RBECyZOJEQUZdQQROsXLiREFGXUEETQzJ3jo+kURcVqGKLNq1e7GdtvbxdTVNuqYYgoisqKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoLoF0dY3brOLKKpt1TBEAGjzg5+ziymqbdUwRFuePIgQUZRSQxBtXfqAB4gQUVSqhiB699XLCRFFGREiigoUIaJaVls3LPZt1nvRFLd17fN2lR4RIaJaWlvX/s4Phm15fK+mHZohRFTrKEoeAJNo8xq3Zf5JbvMjXyVEFNWZAAnaJco2gPTugss9PO8umOC2RjChtMO0GWoqRI8++lhi0erVq5Nl8+c/p9Yuj/Acb7jhpszz1vctXLTILqb6UhEgHqBa0myZ2z++XQOo2WoqRGPGXux2+vKu7mOf3C5Zhtt2WZk0cNApyXPT89DEq652/Q461L8uqhyqd/C/N+ARNR2ik6OGeM6Q86L5cX7ZDTfe5A48+JBM48Q8vt0XLlzkdv7Krn4ZQPvUp3dM7u9/9AB/v2yHfcs81ptVSw08Fh4T6n/UAL8O7ocxf87QYX468cqrk+X1ZGHHc8fjEiJKq1cgQgPf+csxHIBFQ4SSafe99k22wXKUeQBGwMAybI91MY/pqqisQmkFobHLuvKYkCQJALw+Ks+wDm7D2NYmjRbWx32ja/BPv+dePyVElFWvQARJ44U0RGisGiKRBydKJTR2gUi0+577ZPbXGUS60evt6gkACzyQwIvnIOmlnw/V3moqREcefYzrd/ChPn3Q8NCfWBTNS5kkSYL5/tG66MwLUFiGbe6JEkDmkQaYxz5QlkkpBliw3blDhvmpPCbmsf7AQYOT54QyTgDF4+G5aAnggATWwOFx5TWVdVCE6n01FSI0bhh9CSQOIEAj1ssh3Hdk1H/pd9AhycgXluH2uVEfR9a3y2VdJAeWjY4SB/Oyb9lOthXJ42G5JJhIPz+7rV6GvhdFQU2FiKLaQYSIogJFiCgqUISIogJFiCgqUE2FCOv533GsfMKfIIizarEMJwPqdUT4vQdOGpT7sZ2//cZtbsvvzvKntUM4MxfL/PK5RyXbU1RfqKkQ6dPSt7w00kPQEUTvRuvg5EE8DpTAJ/dHQPpp7X4IkFFUX6qpEG3d8Lo/s3bLnP5RegxMkmjL7IPiZXPis239urUU8uvUwJAkQtr434PUTirEvF+GU94VUBTVF2oqRD6JaqnjIVg5u24SeSCQVrXSDdJJ9O6rE5JtCA5VJjUXomdPipMnAgGAQIUQ1X4PghSKF67xzpRz/vZsP0uIqDKpqRBRVDuIEFFUoAgRRQWKEFFUoAgRRQWKEFFUoAgRRQWKEFFUoJoK0btbNrn1K3/v1i5/3a1Zu4mme8Vr16yLvXq1W7vyLbd+9RLbNHtUTYUIL8C+QJruC29c/brbunWLbaI9oqZCZF8ITfeV31n5ptuyaa1toj2ipkGEUs6+ELp7Xrfk0Yzt/XTnXrdmpdu8frltpj0iQlQBy3sutvfTnZsQtbkJUbgJUZsb7/f6Fy7NLae77spC9NYbq+gesE+fZy/OLae77uVvvVlNiOy3Ad09M4nCXdkksi+E7p4JUbgJUZt57fJXk/dZTIjCTIjayGtXLcsBRIjCTYjayGuX5VOIEIWbELWZ1798bQagTU8emVuHbsyVhej115bSdClc2SHuRa+9TdOl8NIlS6oJkY1Umu4rV7acsy+EpvvKhIimA02IaDrQhIimA02IaDrQlYXozTdW0nQpvHLZ0mpCZL8NaLqvXNkksi+EpvvKhIimA02IaDrQlYQIWrNqZe7F0HRfeP2qRdX8B9R1S593a9asy70gmu5Vr1nvNm9cZZtnj6mpEEEo6zaues2tXfaK/8EZTTffC7zXLXvJbVyzxL27eb1tlj2qpkMk2hrBRNO97iaVcFq9BhFFtaoagmjrG7cRIooyagyilU8QIooyaggiaMvc/oSIopQahggCSBRFxeoWRFs3vG4XUVTbqlsQURSVihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFihBRVKAIEUUFapsNG991NE1339ts3LTVwZv+4Gia7oYJEU0HmhDRdKAJEU0HmhDRdKAJEU0HmhDRdKA9RHYhTdNdN5OIpgNNiGg60L0C0bK333G/unmOu/XqRyM/lvFt5na87PHUP7f+Tc2Pu9tr83p6+zUF0079RGO+Nva0mrFs2rWzG/CTDfipnO/QnqSmYns78p2Tno6mT/tpfc/J+a7cdG7XPPmZaPpMPC30PDUt9t1TItvbYty2y6Y86z29Nn3qwYXu+TlvuRXLNubaZE+6qX2ikSfd6A79wgXuX957rPuX9xzrp19673HR9Dg//dJ7j3c7/dHx7kva742nO/3Rj/L+4xPczolPzHiX952U+o/V/PsGJv6yTP/PyQUe5L5i/f4fJ/7q+0+pOZ3/1z8ZHM9HU8yn/knGu37g1Iy/9oHT4ukHT/Pz4l0x/eDpib/ufUbWf3pm7A+e6b7xpzV/6KxomvW/fejsZJr1OYm/KdMPn+vn/bTmb207JJoOcd/yHhrdHup22zad7vZn58XeVvnPhnnvnvj8xHt8BL5ATbPe8yMXuj3/XPuiOh7u9hL/hUxHuL3h/zsynv4FprGP+MIVbvC3/8fNnPaiB8q20Z5wjyYREufyc++IgPkv98+RE3jeU4PHgxRDlIBkIaoBVAQS4IlBAjgdgJSByIAk0Lyv5mheg5OBKQFIfIoBKgWoyyD9Sc01aDw4YgVQFiYBSIH0wRimDEAyX5vmAYoh+uaHlS08HwY8MUQAxjoFScBR0xxEqWN4BCgL0YUJSJj3ECVQXRSBYgACPN4jEoASkASiCKh9vEfV5kd5H/R3493Qw27xKWXbb3fdYxABmn9+zzG16X95YGTqYUqsk0i5lkL1AMonkYYoC1QWoE7SqAaShUmDBIDy8NRcSyQB56vvzwOUAyljwKNgAjgfSJPITyN4ZAqIkD4eoIxNGn2oPkQ2jTREFqgYnjxQGYhqBiz5JKpNE4CyEAGUdCoQ2fSJLQAlQCUgDVcJpJKoBpKGaO/aFB562K0RTItybblRB0OE9Dnk8+fXgBGAalZlXAxPFiJd1unSLgeP9wlJGuUh6gigkxJwBCINTiFA78f0x8ltXcLlQaqXQoMjMCJw6sKTplEMTzaNEoBsCpnSrhCiuklUBE99iL6ZlHN5iArLOpVGKUwpPAIToEkTKU0emS8CCWmULeFUGWddA0fbAuT90dHuJ/9+fa5NN+pgiOL0SRNIJ5EYt2No4rJOAMpAlABUDyJdzsUGWDmIolIuhSmFJ06fWgLlQEIZZ2DK9IeyJV0RRFmQupJA0jcqKOlqSaTTR1snUdo3EnDy8MTL4/ItgagGTwqVLudiaGTqrfpFqXV/qJZE28bTXL8IENVKOp1Eu2dKurRPpEGK+0B6Pl/S5YHKJ1G8TCAanfjUCKSQROr2wMItV89yX9wmggcuhEjBpEq5bAJl+0UdJpEBSLzL+/L9oZ3RJ0r6RSaFbBlX0C/SllIuhakYoiKAkEQyLYInTZ/6/aIcPNInSkACPBakszss5wSmzMBCrowTF6SPhagGkJRzuX6RpJDpD6UApWVdClJBOSfgJCBJ8iiI/OBCPoU68r5RIh38d5d0exSv20m033Zn1ICpB5Eu5bJlXUdJJH2jDEwKoI4GFuqVcylA9UbmbAoVJRHAqVPWeaAKEsjczqeSgidJoQKQkEaJVQplQMpaEugbfpov6eJyLk2kIojiJMqClEugyN8q6Bvlksj0hyxQGh4Lkk6hfBplAYrnFUh/UaeUq8Hj0yiaYn761Pm5dt4VdwuiBS8t8+AgiXwaZQYUDEh1BhVsfygFpyCJkvKtVtL5Mq64X5QHKJtIFpxsIqnROQDk+0W1gQU/jYHxo3HJNJ0vSqNieNL+kICUDG8biJA8XzN9Ikmk7ABDtj+UTyILj/SHshBlR+dUfwjlXKakU/0gP41LuVw5VyvjdH8oD5EeVOggiTJlnIZIpZIeXEj6QnEZly3lFEgeojHuyC9OdJOGz8q1987cLYjOPvwalUDWqpTLABQ7V86Z9MmB9MdpEqXlnCRRChFAKYTIl3V1RuZq6ZNLIlPG2WHufAJZ5xPJQiTpo+eLSrrMsSJJH5mXYW4NEiCqM7Cg+0RFZVzcL6pBVGdkrrCcU86mUL5flE0fk0IZiPQyA5HpEwGaTN9ISjoFksDk530CZSESkE79zg0Nl3UNQ3TfTU+7L75nQJxEBp7O0igt49KyTqeQdgKQh0mlUJ1yLtc3Un2huv0im0YKpDh9ZFpLowSoNJG04+HtPESdJpHuG9lSTkOknUmhfEkn4KCcs2mUDixIMmmY0hG5eD7bJ0pgKjjQmu8TAZ7aVI3K1S3lchAV9InswILuG+kkUn0iPbCQLenSwQUYEB3895e6McdPz7X7jtzwwMK+UV8IEAlAaTl3TARGQb8ol0a2P1RLIw9TPK9LubGDbs78PdGSV5e5846a4uf/4++HZEDyEJmzFdAPyqSQdx4aezu2TaNsCklZlwcqn0QC0t5/da47aPvhCUi5BPLzAk5+cKEohRKYzJkLNolyANkkUiVd1gqgD8cpFKeShqgohWoQyYHWukmUHieqB5AkUQKTlHJ/Hh8jyo/OZRMpD4+ar6UQUiku667MtfuO3FAS4ZjQF7cBQDFEfr5gdM7DIgdbCyDKjMwl4KRlnQD03+fd5WE59lvj3Lc+eqo7drfx7sW5i7yLIAI4Tz34YgLcUw++5A77l1H5FDIg6bIuBxGmdSDC6JwHKfLpB1+TQBSPyg12SxasSJ6L6O4pT7o3ouU6jdK+UL6cy6SSDC54mHQKxUmE4e16iQSdvM/VCUjf+8xYd+1FM3IAvbFgpZv78KvZ/pCHJ523pZxPoFoa6VSK+0KqnMslUB6kegBpeASoFCADkiRQIUAWntgaJvSNbNvvyA1BFA9rpxDZvpAt6fKlXJpEmVE5SaNaCmH6QgTK4leXZ1LpyQgQWKQhkj4RIPJD3NHt/f/hPL/eT398i4fouN0udXdO+o27/pIH3QH/OMxDM+zo69yV509PlsvAwh7//wx31bB73F2Tf+PO/+H/JBD1+6fzo2VPuBsufcidFoHT758ucFeff6978ZnF0fQ+d+GA65MEAkR3T5mdKedwG8sBz94fG+KuvuBXHqyLBtyYALTPx4b6ZTde9og7ePuR7rs7jPTgnLTXRHf31GjdY25yZx0yxRug7PfxYW761KfcTZfNcgP3viqB55oLH4iXT3i0BtHPfQIJRNBlp92dgQjyEEXQ/Psnh0fbz/EeceytCUSTLvq1u2fqXHfzhMeSFPrxfpPcPdfN9f7+58Z7eCYNf8gd9rlLE4AuP/0+D8sp357sRh83zd8+5dtT3AF/O8b97Iz73L3XPeNGHz8tAenwHSe4+66b5265/Dfu2K9flZZyuqSrlXOZJBKAMiVdClBmgEH6RL6ck0Qak2v7HbkhiCaPvb8GEBIo7hcljhJJzlToqJyrO7AgJV1tHpoYJZGGaMmCZTUv985ApJJIl3PQlcPudqceeJWfByyyPRLp6SitMH/XpCf89IgvjfYQYX7tyg0eGGjcKbd5gPy60TJAAwGiu6PbSJe7J8/2QEkiCUS6nAMcMUSn+n3gMebMfMXv6+cRUIAIy6dH6z087Tm3dtX6CJ4rPUTQnJm/d2+8ttLPA5Dv7jDapwfmH77jt365pJCs88idv/PzgEjKOkAEWLCtAIT758581S/H7ZeeeSOCYk6yPQD63mcudutWbfCwzH14gZtw2j0eJAi3sRzP7z/+ZpRf9uP9JicQvRktx3RyBNebr63yt0cff4d7ad4b0T43unt/Mc9vM3n4TA8Q1sGyV+a95ZfHKdRZn0j6RSlIWXBG1QUHlr6RbfsduaE+ESCK+0ED1EHWekmUByjTH5IEkkQyp/5A6A8lAwu5oe3iIW5dzkFIF4HpwdvmRUBN937xmdd9EgEiXdJBAtFV59/jfWcNJCTRnn95lk+hmbc/69asXB8fbH3/YA+S7RMBFkDydAQJjP4QoJJybu+/GhIl141u5rT50b42+Me48dJHPDiABkAdHKUQIDoxSqEzo+SRPhHAQyq99MySqNGu8KkDA6Sjv3KZT6X4GFGcSlAMUdwfOvTTY92g6PY9U59OAIJwGxDNiWBaG8FybZQ68C+j1Dn3e9d7kEYde5tPIcCFdZFEEOCZNPzBpJyDTokgklN+AA2SCBBBcT/oAv84k0fM9L7lZ/F73e9vx7o3F66KkugZN/T7N6UlXNIfyiZQ7viQdVFZVwekbkHUVZBkZC5fzgk8pm8kIOWAKu4XSd8I4CCFoDEJSDFMAAc676ipOYBglHuSRBBSKE2k6XHfKALmuN0vzUAU931ieDAFINIXQgIhcWZOe9aDIX0iJIacseDLtlpfSEDSSZSWc2kSQQfvMML3hbAcQhpB0g9CCQeIvrvDqAiS+5PBhTh9noyTKdqfT58PobQ7388DqP0+cUFhnwgQIYkEKiQONDIq2e6JSjeAgSkat/SFkECYYvl//M3IZHTOQxRNj9n1v5Nl0ITT7/FTlHToG31/x0v8bQ/RCCRRDJRAdMB2YzxUh+94mfcB242N4LnZw3PE5+NUypZwWZAy/aECkHJpJOVcAlEKEkbobNvvyA1BhIOsUsrFAwtFIEl/qCiNssPbCUy6nFMDC+gXQd/5u3P87d0++hP3i/EzokR5pgZNcRIJRBhYAAy7f/Q09+Dt8/z84V8a5WGCAA/WuTjqM/WL+kg+paLkATjQQ1HaHL/HhFr5tyIq42b7fez5l2dGaTTTryPHibDOCXtc7g781IXZPlG0DYDR5ZwkEXTQ9iPcGd+d5OchJA8aVf9dxvv0wbpSziGhzvzuFN9XglCqoQ8EnbT3lVEfaaqHCzAdGkEiqTRw76v9OoBITvXB/YP2RR8p7gchRTAvEA3a9xq/HNNB+1zjS67v/PUIn0jf++w4P4/UkSTCcx553G3uPz8XwzIq6vOgvMPyUcfdHqXW8365JJGGCJoV3T846h+9PO9NDwwAgo792lXuJ9+emkKUlHPxyFyupCtIoKIk8kD5JEr7RgJRU0fn4IHfmZCOztVJo+zZ3EUg6RRSgwt1zlr4n/G/9nBgihG6olIOfSI/TfpD+nhRetzogH84z+3+/05PRugAEWA64B/P94MJfmBBnfbTL1qeHmyNE+jAT10Qnzcnw9w1kA6KANr7r86pQZSWdR0dbD04gig3zB0Zgwn7fHxoBEecRHEZd4ZPJJRzgAVAyujcIZ8e7Q75zOjcyNyhnxnj9vtkmkjJMHcy1J0f4o6NBBoSAXOx+8/Pxikk/s/Pjo9gGZcdpYtAQj8IEKXHiuIy7vvRMnsWd94XusOifpA95efwKIVggNJRSZcdXAA0KVAxNFmIkhG5TBLFIP3oG5Nz7b4jN9Qngv0wd+1ga0d9IinnMmVdzbkyLnO2wo8yaZS17RdlU0hG6DIQ+ZE6daaChyc94AqI0mNFBWcsqGNFMUQFQ921g6zpwdbssSI9sJC1OYO7BtFNUdKgj4T+FCTHivCtLkI/6JBPj0ogypyxoIa2rfVZC8mJqDmA1LEiBU/uTAV1wFWODel5e7pPcrwoM9RtfwKRH+bOjMZpSyJlADJppAYXsn0hXc5ljxHdfPmTuXbfkRtOIvjor4/OABSXdQUQ5RLIAKQh0mcuAKLkbIXU6RkLHYDkodEpFCdR5jdFHqYYoiN2ikfjYqj0MaI0jWwSyTEiC5GFJwYnCw9AqQuQSiL0jS75yR0+ieTYEFLoomNu9gMM+35iWB4gOeCanD9nIdIHW+OfQVh4PEByjKjw2JCCSZ+1kKSPPdiaglQ/hfJAxQBd6NJftaozFgpASgYXEnjy/aNsfyiFSUOEFFr0+1W5Nt+RuwURXJxC6TyAsSmUA8iDpUq6pJRTSaRh6gJESRIZmJIEMkn0FYHn/bWDrf4HeVmI0vPmilIoLemKYNJncsc/C7dW8CiIJH2yZyqkZyxkzlRIIBJ4OoIohUkDJb8nSiBKnD3dRwMVzyuQajDlIUrn8+BkIcolkT13zgwuZA6y6lLOjNTlAKqVdOmAwugIoEnd+h+GcIg6G+quM6iQplBtWlDWWWeTqBgeOdBq+0VJKVeDx1rSKAOQn49LuqJz5xKAcCZ3Bh4F0QdqgwoF5ZycoZCcP5cBKIUoXhbPCzwZgJISLoXIlnECjwwsZPtGWYgyIPmzt01Z5xNIgFJJlCvlBJ7aNFfKFQNk+0Wp4+NEHpwkkdIzFrLHiNJ5ABPDpPtDupyLffPlT+XaeVfccJ9I++hvjM3BkznYCmjq9IfyMKUJlIcoPgE1/xNxkz41Z26rPlGSSJ0BpCFS8FiA8v+xIDBl+0Ppj/PyKZTAJP+zoEBKrZIokz4FP4VQgwrZ3xEVlXTF5ZyHSJdz6nw5m0a7y++IagDlk6gGlPSLahBpmOR4UdEJqAAkV86ZFMqUcxomNSqXHd7OQ3T81yfl2ndX3e0kEuOEVP3r1nR42yRRDaZ6AOX6RBqiGjT1AMqVcLk00ief5ks69I8yABX2hwpKOZ9CeZCySVQEjynlimySSJd1eYBSiOTn4fk0yiZQvf7QNz+sfwohSZQv6ZIUUhBJv6jwLO5aElmAJIUEJl2+pSWd6hfZPlEGHN0n6riUE4gA0PQp3fsxnjgoiWAcO0p/5aogUuljy7lCiFQZl0+iNJFimIr7RPkyTkMUTxtLoqJBBTWvBxfqQJRJoFwaqcGFCBSdRDEwpm+k/zLLlHJd6xOlo3NFSQRwklIuk0SmnFNJpEHKDCrUKefyZZyByPaJjG0a5SCySdQJRAcF/CxcHJxE4gnn3JHtC3kjgfJAZQAyEGmAvmQh6gCgYpiyx4iyaVQHJBlYMGVcLpEEoKSEy4Kjk6h+KWd/2VpczqXwZCGyf1KS7R91DFBmUMGWctvan4anZV0mhaw9TBYkgSgGKYbGwiR9oeIzuTMjdAJQQVlny7iklKtT0nVnEKHIPQaR9g99X0lgysKjk8hPFTxJEtlyTqVQmkR5eASg/OBCDI6GKJ7vLInSeQCTTyVbyhVBlJ6tkIzOJaN0RT/Mi8DR//aTSaLsoEK9/pCkkYVIl3X5H+OlSSRlXZxG2QTKQlQbZDDwZMs55dpxonwSSRqlAMXzWZAsPHXTyJR0On1Qui16ZXWuzYa4KRDBKPMO/XztL4RNCtnTfbK/JcqmkYaoK2lUXMpl7eFJALLwZJPIj8rVRucsPGk518HAQi6BUoAyCaTKOl3S2f5QApOfL+oTxf2hfBqlw9u5JCoAChBl00iXcmn6eGhUfyhT0tUFKQtQkj56YKGobyQgFfSJNEjxvAAUT0cfd3ePJY91cJ+IptvdTUsimm4XEyKaDjQhoulAs09E04FmEtF0oAkRTQeaENF0oLfZsPFdDxFN093z/wKUu2RCLP5lLAAAAABJRU5ErkJggg==>