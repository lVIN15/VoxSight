## **CEBU INSTITUTE OF TECHNOLOGY**

**UNIVERSITY**

COLLEGE OF COMPUTER STUDIES

# 

# 

## 

**Software Design Description**

## for

## VoxSight

# **Change History Signature** {#change-history-signature}

| Version | Description | Date Completed |
| :---- | :---- | :---- |
| 0.1 | Initial Release | May 12, 2026 |
|  |  |  |

# **Preface** {#preface}

**Project Background.**

VoxSight was conceived to address a common challenge in choral music: the steep learning curve amateur singers face when transitioning from a physical sheet of music to an accurate vocal performance. Traditional practice methods often lack immediate, objective feedback, leading to the reinforcement of pitch errors. VoxSight bridges this gap by transforming static scores into interactive, data-driven practice sessions.

**Intent of this Document**.

This Software Design Description (SDD) serves as the definitive technical roadmap for the VoxSight mobile application. It is intended to guide the development team through the implementation of complex signal processing, UI synchronization, and OMR (Optical Music Recognition) integration.

**Intended Audience**:

* **Software Developers:** For implementing the Kotlin/Android client, the Spring Boot orchestration backend, and the Python-based OMR processing utilities.   
* **System Architects:** To verify the integration between the mobile client and the OMR engine.  
* **Quality Assurance (QA) Teams:** To derive test cases based on the defined technical constraints (e.g., the ±10 cents pitch tolerance and \<100ms latency).

**Document Organization**:

**Module 1:** The digitization of physical scores via OMR.

**Module 2:** The synthesis and selective isolation of SATB vocal parts.

**Module 3:** The synchronization of the visual playhead with the audio clock.

**Module 4:** The real-time frequency analysis and pitch feedback loop.

	

# **Table of Contents** {#table-of-contents}

**[Change History Signature	2](#change-history-signature)**

[**Preface	3**](#preface)

[**Table of Contents	4**](#table-of-contents)

[**Introduction	6**](#introduction)

[1.1.  Purpose	6](#purpose)

[1.2.  Scope	6](#scope)

[1.3.  Definitions and Acronyms	6](#definitions-and-acronyms)

[Acronyms	6](#acronyms)

[Definitions	7](#definitions)

[1.4.  References	8](#references)

[**Architectural Design	9**](#architectural-design)

[**Detailed Design	10**](#detailed-design)

[Module 1\.	10](#module-1.)

[1.1 Transaction name: Upload Sheet Music	10](#1.1-transaction-name:-upload-sheet-music)

[User Interface Design.	10](#user-interface-design.)

[Front-end component(s):	10](#front-end-component\(s\):)

[Back-end component(s):	10](#back-end-component\(s\):)

[Object-Oriented Components:	10](#object-oriented-components:)

[Data Design:	11](#data-design:)

[Transaction 1.1, Class Diagram.	11](#transaction-1.1,-class-diagram.)

[Transaction 1.1, Sequence Diagram: (Focuses specifically on the UI capture and API upload phase).	12](#transaction-1.1,-sequence-diagram:-\(focuses-specifically-on-the-ui-capture-and-api-upload-phase\).)

[1.2 Transaction name: Process Score via OMR Engine.	13](#1.2-transaction-name:-process-score-via-omr-engine.)

[User Interface Design.	13](#user-interface-design.-1)

[Front-end component(s):	13](#front-end-component\(s\):-1)

[Back-end component(s):	13](#back-end-component\(s\):-1)

[Object-Oriented Components:	13](#object-oriented-components:-1)

[Data Design:	13](#data-design:-1)

[Transaction 1.2, Sequence Diagram.	14](#transaction-1.2,-sequence-diagram.)

[1.3 Transaction name: Generate MusicXML & Separate SATB Parts.	15](#1.3-transaction-name:-generate-musicxml-&-separate-satb-parts.)

[User Interface Design.	15](#user-interface-design.-2)

[Front-end component(s):	15](#front-end-component\(s\):-2)

[Back-end component(s):	15](#back-end-component\(s\):-2)

[Object-Oriented Components:	15](#object-oriented-components:-2)

[Data Design:	15](#data-design:-2)

[Transaction 1.3, Sequence Diagram.	16](#transaction-1.3,-sequence-diagram.)

[Transaction 1.3, Class Diagram.	16](#transaction-1.3,-class-diagram.)

[Module 2\.	17](#module-2.)

[2.1: Select Voice Part and Apply Focus	17](#2.1:-select-voice-part-and-apply-focus)

[User Interface Design.	17](#user-interface-design.-3)

[Front-end Component(s):	17](#front-end-component\(s\):-3)

[Object-Oriented Components:	17](#object-oriented-components:-3)

[Transaction 2.1, Sequence Diagram.	18](#transaction-2.1,-sequence-diagram.)

[2.2: Play Assigned Part ( Synthesized Vocal Tune )	19](#2.2:-play-assigned-part-\(-synthesized-vocal-tune-\))

[User Interface Design.	19](#user-interface-design.-4)

[Front-end Component(s):	19](#front-end-component\(s\):-4)

[Back-end Component(s):	19](#back-end-component\(s\):-3)

[Object-Oriented Components:	19](#object-oriented-components:-4)

[Transaction 2.2, Sequence Diagram.	20](#transaction-2.2,-sequence-diagram.)

[2.3: Toggle Audio Suppression / Visual Focus	21](#2.3:-toggle-audio-suppression-/-visual-focus)

[User Interface Design.	21](#user-interface-design.-5)

[Front-end Component(s):	21](#front-end-component\(s\):-5)

[Back-end Component(s):	21](#back-end-component\(s\):-4)

[Object-Oriented Components:	21](#object-oriented-components:-5)

[Transaction 2.3, Sequence Diagram.	22](#transaction-2.3,-sequence-diagram.)

[Module 3\.	23](#heading=)

[3.1: Initiate Playback with Tracking	23](#heading=)

[User Interface Design.	23](#user-interface-design.-6)

[Front-end component(s):	23](#front-end-component\(s\):-6)

[Back-end component(s):	24](#back-end-component\(s\):-5)

[Object-Oriented Components:	24](#object-oriented-components:-6)

[Data Design:	24](#data-design:-3)

[Transaction 3.1, Class Diagram.	25](#transaction-3.1,-class-diagram.)

[Transaction 3.2, Sequence Diagram.	25](#transaction-3.2,-sequence-diagram.)

[Transaction 3.1, ERD.	26](#transaction-3.1,-erd.)

[3.2 Transaction Name: Pause and Resume Tracking	27](#3.2-transaction-name:-pause-and-resume-tracking)

[User Interface Design.	27](#user-interface-design.-7)

[Front-end component(s):	27](#front-end-component\(s\):-7)

[Back-end component(s):	28](#back-end-component\(s\):-6)

[Object-Oriented Components:	28](#object-oriented-components:-7)

[Data Design	28](#data-design)

[Transaction 3.2, Class Diagram.	28](#transaction-3.2,-class-diagram.)

[Transaction 3.2, Sequence Diagram.	29](#transaction-3.2,-sequence-diagram.-1)

[Transaction 3.2, ERD.	30](#transaction-3.2,-erd.)

[Module 4\.	31](#module-4.)

[4.1 Transaction Name: Sight-Reading Training	31](#4.1-transaction-name:-sight-reading-training)

[User Interface Design.	31](#user-interface-design.-8)

[Front-end component(s)	31](#front-end-component\(s\))

[Back-end component(s)	32](#back-end-component\(s\))

[Object-Oriented Components	32](#object-oriented-components)

[Data Design	32](#data-design-1)

[Transaction 4.1, Class Diagram.	32](#transaction-4.1,-class-diagram.)

[Transaction 4.1, Sequence Diagram:	33](#transaction-4.1,-sequence-diagram:)

[Transaction 4.1, ERD.	34](#transaction-4.1,-erd.)

# **Introduction** {#introduction}

1. ## **Purpose** {#purpose}

The purpose of this Software Design Description (SDD) is to detail the architectural and component-level design of the VoxSight mobile application. This document translates the requirements established in the VoxSight Software Requirements Specification (SRS) into a concrete technical blueprint. It serves as the primary technical reference for the development team to implement the system's front-end interfaces, back-end logic, and data structures.

2. ## **Scope** {#scope}

This document covers the architectural and detailed design of VoxSight, an Android-based mobile application designed for amateur choir members. The design encompasses the client-side mobile application, the server-side API routing, integration with a third-party Optical Music Recognition (OMR) engine, and local data persistence mechanisms. It details the technical implementation for the four core modules: Controlled OMR Digitization, Audio-Visual Selective Focus, Dynamic Score Tracking, and Real-Time Pitch Feedback.

3. ## **Definitions and Acronyms** {#definitions-and-acronyms}

## **Acronyms** {#acronyms}

**API** — Application Programming Interface

**ERD** — Entity Relationship Diagram

**Hz** — Hertz (Unit of frequency)

**MIDI** — Musical Instrument Digital Interface

**MusicXML** — Music Extensible Markup Language

**OMR** — Optical Music Recognition

**PK / FK** — Primary Key / Foreign Key

**SATB** — Soprano, Alto, Tenor, Bass (the four standard voice parts)

**SDD** — Software Design Description

**SRS** — Software Requirements Specification

**TLS** — Transport Layer Security

**UI / UX** — User Interface / User Experience

**UUID** — Universally Unique Identifier

## 

## **Definitions** {#definitions}

**Cents —** A logarithmic unit of measure used for musical intervals. In this system, 100 cents equal one semitone. VoxSight uses a ±10 cents tolerance for pitch matching.

**Deskewing** — A pre-processing technique used by the OMR engine to straighten images of sheet music captured at an angle, ensuring accurate staff line detection.

**Digitization** — The process of converting physical or image-based sheet music into a machine-readable format (MusicXML) containing metadata for pitch, duration, and rhythm.

**NoteEvent** — A specific data object representing a musical note. It contains the MIDI value, start time in milliseconds relative to the beginning of the score, and the assigned SATB voice part.

**Pitch Detection Engine** — A back-end component that uses frequency analysis methods, such as Fast Fourier Transform (FFT) or autocorrelation, to identify the fundamental frequency of a user’s vocal input.

**Playhead** — A visual vertical line that moves across the digital score during playback to indicate the current temporal position in the music.

**Scoped Storage** — An Android security feature that restricts an application’s access to the device’s file system, requiring the use of designated folders for saving MusicXML and media files.

**Sync Drift** — The discrepancy in timing between the audio clock (what the user hears) and the UI render clock (what the user sees). VoxSight aims to keep this drift below 100 milliseconds.

**Target Note** — The specific pitch, measured in Hz, that a user is expected to sing at a given timestamp in the score. It serves as the benchmark for real-time vocal feedback.

4. ## **References** {#references}

\[1\] E. O. Lagamo Jr., T. D. Castillo Jr., D. D. L. Sala, J. E. S. Sevilla, and G. O. E. Velasco, "Software Requirements Specification for VoxSight," College of Computer Studies, Cebu Institute of Technology \- University, Cebu City, Philippines, Unpublished, 2026\.

\[2\] Audiveris, "Audiveris OMR Engine," GitHub repository, 2024\. \[Online\]. Available: \[URLs not currently supported\]. \[Accessed: May 2026\].

\[3\] W3C Music Notation Community Group, "MusicXML 4.0 Specification," W3C, Jun. 2021\. \[Online\]. Available: [https://www.w3.org/2021/06/musicxml40/](https://www.w3.org/2021/06/musicxml40/). \[Accessed: May 2026\].

\[4\] J. Six, "TarsosDSP: A Real-Time Audio Processing Framework in Java," Ghent University. \[Online\]. Available: [https://0110.be/releases/TarsosDSP/](https://www.google.com/search?q=https://0110.be/releases/TarsosDSP/). \[Accessed: May 2026\].

\[5\] Google, "Jetpack Compose Documentation," Android Developers. \[Online\]. Available: [https://developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose). \[Accessed: May 2026\].

\[6\] Supabase, "Supabase Architecture and API Reference," Supabase Inc. \[Online\]. Available: [https://supabase.com/docs](https://supabase.com/docs). \[Accessed: May 2026\].

# **Architectural Design** {#architectural-design}

**VoxSight, Block Diagram. ( ANG BAG O ) NA NI NGA BLOCK DIAGRAM PLANT UML CODEEEE** 

**@startuml**

**\!theme plain**

**' Professional Styling & Spacing Parameters**

**skinparam componentStyle rectangle**

**skinparam linetype ortho**

**skinparam nodesep 50**

**skinparam ranksep 60**

**skinparam RoundCorner 8**

**' Custom Colors**

**skinparam component {**

  **BackgroundColor \#F8F9FA**

  **BorderColor \#495057**

  **ArrowColor \#2980B9**

**}**

**skinparam database {**

  **BackgroundColor \#E9ECEF**

  **BorderColor \#495057**

**}**

**skinparam node {**

  **BorderColor \#343A40**

**}**

**title VoxSight System Architecture**

**actor "Choir Member" as User**

**node "Mobile Client (Android Handset)" as Mobile {**

    **package "Front-End UI Layer" {**

        **component "Jetpack Compose\\n(Digitization)" as Compose**

        **component "Canvas Viewer\\n(Interactive Score)" as ScoreView** 

    **}**

    

    **package "Client Logic Services" {**

        **component "CameraX API\\n(Image Capture)" as CamX**

        **component "AudioTrack & Mic API\\n(Pitch & Playback)" as AudioAPI**

        **component "Supabase SDK\\n(Auth & Data Sync)" as SubaSDK**

    **}**

    

    **database "Local Storage" {**

        **storage "Android Scoped Storage\\n(.musicxml files)" as LocalFile**

    **}**

**}**

**node "Cloud / Server Environment" as Cloud {**

    **node "Back-End API (Spring Boot)" as Backend {**

        **component "ScoreUploadController" as UploadCtrl**

        **component "OMRIntegrationService" as OMRLink**

        **component "MusicXMLBuilder" as XMLBuild**

        

        **UploadCtrl \-down-\> OMRLink**

        **OMRLink \-down-\> XMLBuild**

    **}**

    

    **node "Local Server Runtime" as LocalRun {**

        **component "Java/Kotlin Process\\n(Audiveris OMR Engine)" as AudiverisEngine**

    **}**

    

    **database "Supabase Cloud" as Supabase {**

        **folder "Auth Service" as Auth**

        **folder "PostgreSQL" as DB**

    **}**

**}**

**' \==========================================**

**' RELATIONSHIPS & EXPLICIT ROUTING**

**' \==========================================**

**' 1\. User Interaction**

**User \-down-\> Compose : "Interacts"**

**User \-down-\> ScoreView : "Interacts"**

**' 2\. Mobile Internal Flow**

**Compose \-down-\> CamX : "Triggers"**

**ScoreView \-down-\> AudioAPI : "Triggers Playback"**

**SubaSDK \-down-\> LocalFile : "Persists Locally"**

**' 3\. Mobile to Cloud Orchestration**

**CamX \-left-\> UploadCtrl : "Upload Image Payload\\n(TLS 1.3)"**

**SubaSDK \-down-\> Auth : "User Authentication"**

**SubaSDK \-down-\> DB : "Metadata Persistence"**

**' 4\. Spring Boot to Python Script Execution**

**OMRLink \-left-\> AudiverisEngine : "Executes Subprocess\\n(Java ProcessBuilder)"**

**AudiverisEngine \-down-\> OMRLink : "Returns MusicXML/Page Coordinates"**

**' 5\. Cloud back to Mobile**

**XMLBuild \-up-\> SubaSDK : "Returns generated\\n.musicxml"**

**@enduml**

# **Detailed Design** {#detailed-design}

### **Module 1\.** {#module-1.}

#### **1.1 Transaction name: Upload Sheet Music** {#1.1-transaction-name:-upload-sheet-music}

##### **User Interface Design.** {#user-interface-design.}

![][image1]

##### **Front-end component(s):** {#front-end-component(s):}

* **Component Name:** UploadScoreScreen  
  * **Description and purpose:** Provides the UI buttons for "Take Photo" and "Import from Device", and handles the Android device permission requests.  
  * **Component type or format:** Jetpack Compose UI (Kotlin)

    

* **Component Name:** ImageCaptureService  
  * **Description and purpose:** Interfaces with the device camera to capture the image and performs basic resolution/clarity validation.  
  * **Component type or format:** Native Android CameraX API

##### 

##### **Back-end component(s):** {#back-end-component(s):}

* **Component Name:** ScoreUploadController  
  * **Description and purpose:** Exposes a secure REST endpoint that receives the multi-part image payload via a secure TLS 1.3 connection, performs multipart file validation, and delegates the file stream to the storage layer.  
  * **Component type or format:** Spring Boot @RestController (Java) 

##### **Object-Oriented Components:** {#object-oriented-components:}

* **Class Diagram:** (Note: This unified class diagram represents all core objects utilized across Module 1's transactions).

##### **Data Design:** {#data-design:}

* **ERD or schema:** N/A (Data is strictly in transit during this transaction; no database persistence occurs until Transaction 1.3).

##### **Transaction 1.1, Class Diagram.** {#transaction-1.1,-class-diagram.}

@startuml  
skinparam classAttributeIconSize 0

class UploadScoreScreen {  
  \+openCamera()  
  \+openGallery()  
}  
class ImageCaptureService {  
  \+captureImage(): File  
  \+validateQuality(image: File): Boolean  
}  
class ScoreUploadController {  
  \+uploadImage(image: File): Response  
}  
class OMRIntegrationService {  
  \+sendToExternalOMR(image: File): RawMusicData  
}  
class MusicXMLBuilder {  
  \+generateXML(data: RawMusicData): MusicXML  
  \+separateSATB(xml: MusicXML): MusicXML  
}  
class LocalFileStorageManager {  
  \+saveToScopedStorage(file: MusicXML, path: String)  
}

UploadScoreScreen \--\> ImageCaptureService : triggers \>  
ImageCaptureService \--\> ScoreUploadController : sends image \>  
ScoreUploadController \--\> OMRIntegrationService : routes payload \>  
OMRIntegrationService \--\> MusicXMLBuilder : returns structural data \>  
MusicXMLBuilder \--\> LocalFileStorageManager : passes generated XML \>  
@enduml

##### **Transaction 1.1, Sequence Diagram: (Focuses specifically on the UI capture and API upload phase).** {#transaction-1.1,-sequence-diagram:-(focuses-specifically-on-the-ui-capture-and-api-upload-phase).}

Code snippet  
@startuml  
actor "Choir Member" as User  
participant "UploadScoreScreen" as UI  
participant "ImageCaptureService" as Camera  
participant "ScoreUploadController" as API

User \-\> UI : Tap "Take Photo"  
UI \-\> Camera : openCamera()  
Camera \--\> User : Displays viewfinder  
User \-\> Camera : Captures image  
Camera \-\> Camera : validateQuality()  
Camera \--\> UI : Returns image payload  
UI \-\> API : POST /api/score/upload (TLS 1.3)  
@enduml

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### **1.2 Transaction name: Process Score via OMR Engine.** {#1.2-transaction-name:-process-score-via-omr-engine.}

##### **User Interface Design.** {#user-interface-design.-1}

![][image2]

##### **Front-end component(s):** {#front-end-component(s):-1}

* **Component Name:** ProcessingLoadingState  
  * **Description and purpose:** A visual progress indicator shown to the user while the server processes the image.  
  * **Component type or format:** Jetpack Compose Dialog / Overlay (Kotlin)

##### 

##### **Back-end component(s):** {#back-end-component(s):-1}

* **Component Name:** OMRIntegrationService  
  * **Description and purpose:** An asynchronous service that manages the OMR pipeline. It utilizes Java's ProcessBuilder to safely execute the Audiveris OMR engine (via ProcessBuilder) as a system subprocess, handles image deskewing/noise reduction, and parses the execution logs. If confidence scores fall below acceptable thresholds or an execution timeout occurs, it throws a custom exception to prompt a user re-upload.  
  * **Component type or format:** Spring Boot @Service with @Async execution (Java) 

**Audiveris OMR Engine Specifications**

| Parameter/Output | Detail |
| :---- | :---- |
| Invocation Parameter | CLI Argument: \-batch \-export-audiverisxml |
| Output Format (Primary) | Audiveris XML (Intermediate for MusicXMLBuilder) |
| Output Format (Secondary) | Bounding Box Coordinates (Pixel locations for each musical object) |

##### **Object-Oriented Components:** {#object-oriented-components:-1}

* **Class Diagram:** (Refer to unified Module 1 Class Diagram in Section 1.1).  
* **Sequence Diagram:** (Focuses on the server-side OMR processing block).

##### **Data Design:** {#data-design:-1}

* **ERD or schema:** N/A (Data is being actively processed in memory; no database persistence).


##### 

##### **Transaction 1.2, Sequence Diagram.** {#transaction-1.2,-sequence-diagram.}

@startuml  
participant ScoreUploadController  
participant OMRIntegrationService

ScoreUploadController \-\> OMRIntegrationService : Route image payload to external engine  
OMRIntegrationService \-\> OMRIntegrationService : Pre-process image (deskew, denoise)  
OMRIntegrationService \-\> OMRIntegrationService : Extract pitch, rhythm, staves (max 10s)  
OMRIntegrationService \--\> ScoreUploadController : Return raw structural data array  
@enduml

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### 

#### **1.3 Transaction name: Generate MusicXML & Separate SATB Parts.** {#1.3-transaction-name:-generate-musicxml-&-separate-satb-parts.}

##### **User Interface Design.** {#user-interface-design.-2}

![][image3]

UI Note: Upon successful generation of the MusicXML file, the UI dismisses the loading state, updates the Recent Scores list, and provides a standard Android success toast.

##### **Front-end component(s):** {#front-end-component(s):-2}

* **Component Name:** LocalFileStorageManager  
  * **Description and purpose:** Downloads the compiled MusicXML file from the server, saves it to Android Scoped Storage, and syncs metadata with Supabase.  
  * **Component type or format:** Kotlin Coroutine / File I/O Utility (with Supabase SDK)

##### 

##### **Back-end component(s):** {#back-end-component(s):-2}

* **Component Name:** MusicXMLBuilder  
  * **Description and purpose:** Reads the structural coordinate data output by the OMR subprocess and serializes the pitches, rhythms, and track boundaries into a strict, W3C-compliant MusicXML schema with separated SATB part elements.   
  * **Component type or format:** Java Document Object Model (DOM) / XML Serialization Utility 

##### **Object-Oriented Components:** {#object-oriented-components:-2}

* **Class Diagram:** (Refer to unified Module 1 Class Diagram in Section 1.1).  
* **Sequence Diagram:** (Focuses on the XML generation and local saving phase).

##### **Data Design:** {#data-design:-2}

* ERD or schema.

##### 

##### **Transaction 1.3, Sequence Diagram.** {#transaction-1.3,-sequence-diagram.}

@startuml  
skinparam style strictuml  
title Transaction 2.1: Select Voice Part and Apply Focus

actor "Choir Member" as User  
participant "PartSelectorUI" as UI  
participant "AudioVisualMixer" as Mixer  
participant "ScoreRenderer" as Renderer  
User \-\> UI: selectPart(partID) // e.g., 'Alto'  
activate UI  
UI \-\> Mixer: setFocusPart(partID)  
activate Mixer

Mixer \-\> Mixer: calculateOpacityMap()  
note right: Active Part \= 1.0\\nInactive Parts \= 0.2

Mixer \-\> Renderer: updateDisplay(opacityMap)  
activate Renderer  
Renderer \-\> Renderer: applyAlphaBlending()  
Renderer \--\> UI: notifyRenderComplete()  
deactivate Renderer

Mixer \--\> UI: updateActiveButtonState()  
deactivate Mixer  
UI \--\> User: Visual Feedback (Highlighted Staff)  
deactivate UI  
@enduml

##### **Transaction 1.3, Class Diagram.** {#transaction-1.3,-class-diagram.}

@startuml  
hide circle

class DigitizedScore {  
  \*score\_id : UUID \<\<PK\>\>  
  \--  
  title : String  
  upload\_date : Timestamp  
  music\_xml\_path : String  
  part\_count : Integer  
}

note right of DigitizedScore  
  Stores the metadata and file paths for  
  sheet music successfully processed and  
  saved to the Supabase database.  
end note  
@enduml

### 

### **Module 2\.** {#module-2.}

#### **2.1: Select Voice Part and Apply Focus** {#2.1:-select-voice-part-and-apply-focus}

##### **User Interface Design.** {#user-interface-design.-3}

**![][image4]![][image5]![][image6]![][image7]**

##### **Front-end Component(s):**  {#front-end-component(s):-3}

* **Component Name:** PartSelectorUI   
  * **Description and Purpose:** Provides the interactive buttons (S, A, T, B). It captures the user's touch event and notifies the mixer which part is now "Active."  
  * **Component Type or Format:** Jetpack Compose UI (Kotlin). 


* **Component Name:** AudioVisualMixer   
  * **Description and Purpose:** The central logic controller for Module 2\. It calculates the opacity values (100% for active, 20% for inactive) and passes these instructions to the renderer.   
  * **Component Type or Format:** Kotlin ViewModel / Controller Class . 


* **Component Name:** ScoreRenderer   
  * **Description and Purpose:** The engine that draws the MusicXML onto the screen. Now implemented as a hybrid WebView hosting the Open Sheet Music Display (OSMD) library to manage score rendering, auto-scrolling, and note highlighting.  
  * **Component Type or Format:** Android WebView / Open Sheet Music Display (OSMD) (JavaScript).

##### **Object-Oriented Components:** {#object-oriented-components:-3}

* Sequence Diagram.

##### **Transaction 2.1, Sequence Diagram.** {#transaction-2.1,-sequence-diagram.}

@startuml  
title Transaction 2.1: Select Voice Part and Apply Focus

actor "Choir Member" as User  
participant PartSelectorUI  
participant AudioVisualMixer  
participant ScoreRenderer

User \-\> PartSelectorUI : selectPart(partID) // e.g., 'Alto'  
activate PartSelectorUI

PartSelectorUI \-\> AudioVisualMixer : setFocusPart(partID)  
activate AudioVisualMixer

AudioVisualMixer \-\> AudioVisualMixer : calculateOpacityMap()

note right of ScoreRenderer  
  Active Part \= 1.0  
  Inactive Parts \= 0.2  
end note

AudioVisualMixer \-\> ScoreRenderer : updateDisplay(opacityMap)  
activate ScoreRenderer

ScoreRenderer \-\> ScoreRenderer : applyAlphaBlending()

ScoreRenderer \--\> PartSelectorUI : notifyRenderComplete()  
deactivate ScoreRenderer

AudioVisualMixer \--\> PartSelectorUI : updateActiveButtonState()  
deactivate AudioVisualMixer

PartSelectorUI \--\> User : Visual Feedback (Highlighted Staff)  
deactivate PartSelectorUI

@enduml

#### 

#### **2.2: Play Assigned Part ( Synthesized Vocal Tune )** {#2.2:-play-assigned-part-(-synthesized-vocal-tune-)}

##### **User Interface Design.** {#user-interface-design.-4}

**![][image8]![][image9]![][image10]![][image11]**

##### **Front-end Component(s):**  {#front-end-component(s):-4}

* **Component Name:** AudioSynthesisEngine   
  * **Description and Purpose:** Manages synthesized audio playback. Now implemented within the WebView using the Tone.js library to leverage the Web Audio API for low-latency, precisely timed musical events and part isolation.  
  * **Component Type or Format:** JavaScript / Tone.js (Web Audio API).


* **Component Name:** PlaybackController  
  * **Description and Purpose:** Manages the play, pause, and stop states. It ensures that as the music plays, the "Highlighter" on the screen remains synchronized with the audio within the acceptable latency threshold.   
  * **Component Type or Format:** Kotlin StateFlow / Coroutine Controller.   
    

##### **Back-end Component(s):**  {#back-end-component(s):-3}

* **Component Name:** LocalScopedStorage  
  * **Description and Purpose:** The secure local directory where the app stores processed scores. This avoids the need to download the file every time the user practices.  
  * **Component Type or Format:** Android Scoped Storage API.

##### **Object-Oriented Components:** {#object-oriented-components:-4}

* Sequence Diagram. 

##### **Transaction 2.2, Sequence Diagram.** {#transaction-2.2,-sequence-diagram.}

@startuml  
title Transaction 2.2: Play Assigned Part (Synthesized)

actor "Choir Member" as User  
participant AudioVisualMixer  
participant AudioSynthesisEngine  
database "Android Scoped Storage" as Storage

User \-\> AudioVisualMixer : pressPlay()  
activate AudioVisualMixer

AudioVisualMixer \-\> Storage : loadMusicXML(currentScoreID)  
activate Storage

Storage \--\> AudioVisualMixer : MusicXML Stream  
deactivate Storage

AudioVisualMixer \-\> AudioSynthesisEngine : initializeStream(NoteData\[\], targetPart)  
activate AudioSynthesisEngine

AudioSynthesisEngine \-\> AudioSynthesisEngine : mapNotesToSoundfonts('Aahs')  
AudioSynthesisEngine \-\> AudioSynthesisEngine : calibratePitch(±5 cents)

loop For duration of track  
    AudioSynthesisEngine \-\> AudioSynthesisEngine : generateAudioBuffer()  
    AudioSynthesisEngine \-\> User : Play Synthesized Tone  
end

AudioSynthesisEngine \--\> AudioVisualMixer : playbackFinished()  
deactivate AudioSynthesisEngine  
deactivate AudioVisualMixer

@enduml

#### **2.3: Toggle Audio Suppression / Visual Focus** {#2.3:-toggle-audio-suppression-/-visual-focus}

##### **User Interface Design.** {#user-interface-design.-5}

**![][image12]**

##### **Front-end Component(s):**  {#front-end-component(s):-5}

* **Component Name:** FocusToggleSwitch  
  * **Description and Purpose:** A UI toggle that allows the user to turn off the isolation. If turned OFF, it forces the AudioVisualMixer to set all staves and all audio tracks to 100%.  
  * **Component Type or Format:** Jetpack Compose Switch Widget.

* **Component Name:** GlobalStateMonitor  
  * **Description and Purpose:** Tracks whether the user is currently in "Isolated Practice" or "Full Choir" mode. It broadcasts this state to all other Module 2 components.  
  * **Component Type or Format:** Kotlin ViewModel / StateFlow.

##### **Back-end Component(s):**  {#back-end-component(s):-4}

* **Component Name:** UserPreferencesStore   
  * **Description and Purpose:** Persists the user's choice. If a user prefers to always start in "Isolation Mode," this component saves that setting to the database.  
  * **Component Type or Format:** Supabase Database (Remote) / Android Jetpack DataStore (Local).

##### **Object-Oriented Components:** {#object-oriented-components:-5}

* Sequence Diagram.

##### **Transaction 2.3, Sequence Diagram.** {#transaction-2.3,-sequence-diagram.}

@startuml  
title Transaction 2.3: Toggle Audio Suppression / Visual Focus

actor "Choir Member" as User  
participant FocusToggleSwitch  
participant AudioVisualMixer  
participant AudioSynthesisEngine

User \-\> FocusToggleSwitch : toggleFocus(isOn)  
activate FocusToggleSwitch

alt isOn \== true (Isolation Mode)  
    FocusToggleSwitch \-\> AudioVisualMixer : enableSuppression()  
    AudioVisualMixer \-\> AudioSynthesisEngine : setMutedTracks(nonAssignedParts)  
    AudioVisualMixer \-\> AudioVisualMixer : applyDimming(0.2)  
else isOn \== false (Full Score Mode)  
    FocusToggleSwitch \-\> AudioVisualMixer : disableSuppression()  
    AudioVisualMixer \-\> AudioSynthesisEngine : setVolumeAll(1.0)  
    AudioVisualMixer \-\> AudioVisualMixer : resetOpacityAll(1.0)  
end

AudioVisualMixer \--\> FocusToggleSwitch : updateToggleState()  
deactivate FocusToggleSwitch

@enduml

### 

### **Module 3\.**

#### **3.1: Initiate Playback with Tracking**

##### **User Interface Design.** {#user-interface-design.-6}

**![][image13]**

The Initiate Playback with Tracking interface is part of the unified Interactive Practice Screen. It presents the digitized sheet music in a scrollable score viewer and overlays a real-time playhead that advances in sync with audio playback. The screen provides Play/Pause transport controls and auto-scrolls to keep the active measure centered on screen at all times.

##### **Front-end component(s):** {#front-end-component(s):-6}

* **Component Name:** ScoreRenderer  
  * **Description and purpose:** The engine that draws the MusicXML onto the screen. Now implemented as a hybrid WebView hosting the Open Sheet Music Display (OSMD) library to manage score rendering, auto-scrolling, and note highlighting.  
  * **Component type or format:** Android WebView / Open Sheet Music Display (OSMD) (JavaScript).


* **Component Name**: PlayheadSynchronizer  
  * **Description and purpose:** Reads the internal audio clock and updates the vertical playhead position (highlighting active notes) targeting synchronization drift below 100 milliseconds during playback.  
  * **Component type or format:** Timing Synchronization Class.  
    

* **Component Name:** PlaybackControlBar  
  * **Description and purpose:** Provides the Play and Pause transport buttons. Displays a measure/beat progress indicator so the user can track their position in the score at a glance.  
  * **Component type or format:** Jetpack Compose Stateful Component; dispatches play and pause commands to the back-end PlaybackEngineService.  
    

* **Component Name:** AutoScrollController  
  * **Description and purpose:** Monitors the current playhead position emitted by PlayheadSynchronizer and programmatically scrolls the ScoreRenderer to keep the active measure centered in the viewport during continuous playback.  
  * **Component type or format:** Kotlin Coroutine / ScrollState Observer; subscribes to playhead position events and calls the scroll API of the ScoreRenderer.  
* **Component Name:** VoxSightJsBridge  
  * **Description and purpose:** The native Android interface that exposes Kotlin functions to the WebView's JavaScript context, enabling bi-directional communication. It facilitates core functions: 1\) Coordinates Transfer Format: Receives JSON arrays of \[{'x': float, 'y': float, 'w': float, 'h': float}\] bounding boxes from OSMD for note highlighting. 2\) Canvas Overlay Highlighting: The Kotlin side uses these coordinates to draw a transparent, animated playhead overlay over the WebView, preventing OSMD's internal rendering from causing UI jank. 3\) Function Calls: Exposes reportNoteStart(ms: Long) and reportRenderComplete() for synchronization.  
  * **Component type or format:** Kotlin @JavascriptInterface Class.

##### **Back-end component(s):** {#back-end-component(s):-5}

* **Component Name:** MusicXMLParser  
  * **Description and purpose:** Reads the locally stored MusicXML file and produces an ordered in-memory list of NoteEvent objects, each carrying pitch, start timestamp, duration, and SATB voice assignment. This list serves as the primary playback reference consumed by the PlaybackEngineService and Module 4's Pitch Comparison Engine.  
  * **Component type or format:** Kotlin utility class using Android's built-in XML pull parser; outputs List\<NoteEvent\>.


* **Component Name:** PlaybackEngineService  
  * **Description and purpose:** Manages the internal audio clock. Schedules note-start events from the NoteEvent queue, passes audio data to the synthesizer (Module 2), and emits highlight-update callbacks to the front-end PlayheadSynchronizer. Re-syncs the playhead automatically if render-to-audio drift exceeds 0.1 seconds.  
  * **Component type or format:** Android background Service (Kotlin); uses android.media.AudioTrack for low-latency audio scheduling and a Handler/Runnable loop for note-event dispatch.


* **Component Name:** SyncDriftMonitor  
  * **Description and purpose:** Continuously compares the rendered playhead timestamp against the audio clock. If the delta exceeds 100 ms, it triggers a forced re-sync to bring the highlight back into alignment with the audio.  
  * **Component type or format:** Kotlin coroutine / Flow observer running within the PlaybackEngineService lifecycle.

##### **Object-Oriented Components:** {#object-oriented-components:-6}

* Class Diagram  
* Sequence Diagram

##### **Data Design:** {#data-design:-3}

* ERD

##### **Transaction 3.1, Class Diagram.** {#transaction-3.1,-class-diagram.}

@startuml  
class PlaybackEngineService {  
  \+audioClock: Long  
  \+noteQueue: Queue\<NoteEvent\>  
  \+currentNote: NoteEvent  
  \+start()  
  \+pause()  
  \+resume()  
  \+seekTo(timestampMs: Long)  
  \+emitNoteEvent(note: NoteEvent)  
}

class MusicXMLParser {  
  \+filePath: String  
  \+parse(): List\<NoteEvent\>  
}

class SyncDriftMonitor {  
  \+maxDriftMs: Long  
  \+checkDrift(renderMs: Long, audioMs: Long): Boolean  
  \+forceResync()  
}

class PlayheadSynchronizer {  
  \+activeNotePosition: Rect  
  \+updatePosition(note: NoteEvent)  
  \+clearHighlight()  
}

class NoteEvent {  
  \+pitchMidi: Int  
  \+startTimeMs: Long  
  \+durationMs: Long  
  \+staffVoice: String  
  \+toFrequencyHz(): Double  
}

class AutoScrollController {  
  \+viewportHeight: Int  
  \+scrollToNote(note: NoteEvent)  
}

class ScoreRenderer {  
  \+musicXMLPath: String  
  \+render()  
  \+scrollTo(position: Int)  
}

PlaybackEngineService \--\> MusicXMLParser : uses \>  
PlaybackEngineService \--\> SyncDriftMonitor : monitored by \>  
PlaybackEngineService \--\> PlayheadSynchronizer : notifies \>  
PlaybackEngineService \---\> NoteEvent : queues \>  
MusicXMLParser \--\> NoteEvent : produces \>  
PlayheadSynchronizer \--\> AutoScrollController : triggers \>  
AutoScrollController \--\> ScoreRenderer : scrolls \>  
@enduml

##### **Transaction 3.2, Sequence Diagram.** {#transaction-3.2,-sequence-diagram.}

@startuml  
actor "Choir Member" as User  
participant PlaybackControlBar  
participant PlaybackEngineService  
participant SyncDriftMonitor  
participant PlayheadSynchronizer  
participant AutoScrollController  
participant ScoreRenderer

User \-\> PlaybackControlBar : Tap Play  
PlaybackControlBar \-\> PlaybackEngineService : start()  
PlaybackEngineService \-\> PlaybackEngineService : Parse MusicXML; init audioClock \= 0

loop For each NoteEvent  
    PlaybackEngineService \-\> PlayheadSynchronizer : emitNoteEvent(note)  
    PlayheadSynchronizer \-\> ScoreRenderer : updatePosition(note) \[\<=0.1s latency\]  
      
    PlaybackEngineService \-\> SyncDriftMonitor : checkDrift(renderMs, audioMs)  
      
    alt drift \> 100ms  
        SyncDriftMonitor \-\> PlaybackEngineService : forceResync()  
        PlaybackEngineService \-\> PlayheadSynchronizer : updatePosition(correctedNote)  
    end  
      
    PlayheadSynchronizer \-\> AutoScrollController : trigger scroll  
    AutoScrollController \-\> ScoreRenderer : scrollTo(activePosition)  
      
    PlaybackEngineService \-\> PlaybackEngineService : advance audioClock by durationMs  
end

User \-\> PlaybackControlBar : Tap Pause  
PlaybackControlBar \-\> PlaybackEngineService : pause()  
PlaybackEngineService \-\> PlayheadSynchronizer : clearHighlight() \[freeze at current note\]  
@enduml

##### **Transaction 3.1, ERD.** {#transaction-3.1,-erd.}

@startuml  
hide circle

entity "SCORE" {  
  \* scoreId : String \<\<PK\>\>  
  \--  
  title : String  
  musicXMLPath : String  
}

entity "PLAYBACK\_SESSION" {  
  \* sessionId : String \<\<PK\>\>  
  \--  
  \* scoreId : String \<\<FK\>\>  
  \* currentNoteId : String \<\<FK\>\>  
  audioClockMs : Long  
  isPlaying : Boolean  
}

entity "NOTE\_EVENT" {  
  \* noteId : String \<\<PK\>\>  
  \--  
  \* scoreId : String \<\<FK\>\>  
  pitchMidi : Int  
  startTimeMs : Long  
  durationMs : Long  
  staffVoice : String  
}

SCORE ||--o{ PLAYBACK\_SESSION : drives  
SCORE ||--o{ NOTE\_EVENT : contains  
PLAYBACK\_SESSION }o--|| NOTE\_EVENT : tracks  
@enduml

#### **3.2 Transaction Name: Pause and Resume Tracking** {#3.2-transaction-name:-pause-and-resume-tracking}

##### **User Interface Design.** {#user-interface-design.-7}

**![][image13]**

The Pause and Resume interaction is handled within the same Interactive Practice Screen. Tapping Pause freezes the playhead highlight on the current note and toggles the Play button to a Resume state. The score remains static. Tapping anywhere on a measure while paused seeks the playhead to that position. On Resume, playback and highlighting continue from the frozen or seeked position while minimizing noticeable visual discontinuities during playback resumption.

##### **Front-end component(s):** {#front-end-component(s):-7}

* **Component Name:** PlaybackControlBar (Pause/Resume State)  
  * **Description and purpose:** Manages the toggle between Play, Paused, and Resumed states. Swaps the Play icon for a Resume icon when paused, providing clear visual feedback on the current playback status. Dispatches pause and resume commands to the PlaybackEngineService.  
  * **Component type or format:** Jetpack Compose Stateful Component; maintains a local isPlaying boolean that drives the icon swap.


* **Component Name:** FrozenPlayheadIndicator  
  * **Description and purpose:** When playback is paused, renders a static, outlined version of the playhead highlight on the current note to show the user exactly where the score is suspended. Visually distinguishes the paused state from the active, animated highlight.  
  * **Component type or format:** Conditional Jetpack Compose Canvas DrawScope overlay within the PlayheadSynchronizer component; switches from an animated fill to a static outlined border on pause.


* **Component Name:** SeekTapHandler  
  * **Description and purpose:** Detects tap gestures on the score view while playback is paused and forwards the tapped screen coordinates to the back-end SeekPositionMapper so the playhead can jump to the corresponding measure.  
  * **Component type or format:** Jetpack Compose Modifier.pointerInput (Clickable) on the ScoreRenderer; active only when playback is paused.

##### **Back-end component(s):** {#back-end-component(s):-6}

* **Component Name:** PlaybackEngineService (Pause/Resume Handler)  
  * **Description and purpose:** Receives pause and resume commands from the UI. On pause, freezes the internal audio clock and stores the current position in pausedAtMs. On resume, restarts the clock from the preserved position and resumes note-event dispatch.  
  * **Component type or format:** Extension of the existing PlaybackEngineService Android Service; adds pause() and resume() methods that suspend and restart the Handler/Runnable timing loop.


* **Component Name:** SeekPositionMapper  
  * **Description and purpose:** Translates screen tap coordinates (x, y) into the corresponding MusicXML start timestamp. Uses a MeasureLayoutMap generated at score render time to map a tapped pixel position to the correct NoteEvent, enabling the user to jump to any measure while paused.  
  * **Component type or format:** Kotlin utility class; consumes a Map\<Int, Rect\> of measure bounding boxes and returns a startTimeMs value that is passed to PlaybackEngineService.seekTo().

##### **Object-Oriented Components:** {#object-oriented-components:-7}

* Class Diagram  
* Sequence Diagram

##### **Data Design** {#data-design}

* ERD or schema


##### **Transaction 3.2, Class Diagram.** {#transaction-3.2,-class-diagram.}

@startuml  
class SeekTapHandler {  
  \+isActive: Boolean  
  \+onTap(x: Float, y: Float)  
}

class SeekPositionMapper {  
  \+measureLayoutMap: Map\<Int, Rect\>  
  \+tapToTimestamp(x: Float, y: Float): Long  
}

class PlaybackControlBar {  
  \+isPlaying: Boolean  
  \+onPlayPauseTap()  
  \+onResumeTap()  
}

class MeasureLayout {  
  \+measureId: String \<\<PK\>\>  
  \+scoreId: String \<\<FK\>\>  
  \+startTimeMs: Long  
  \+pixelBounds: Rect  
}

class PlaybackEngineService {  
  \+isPaused: Boolean  
  \+pausedAtMs: Long  
  \+pause()  
  \+resume()  
  \+seekTo(timestampMs: Long)  
}

class FrozenPlayheadIndicator {  
  \+frozenPosition: Rect  
  \+isPaused: Boolean  
  \+freeze(note: NoteEvent)  
  \+unfreeze()  
}

SeekTapHandler \--\> SeekPositionMapper : forwards tap coords \>  
SeekPositionMapper \--\> MeasureLayout : reads \>  
SeekPositionMapper \--\> PlaybackEngineService : \< seekTo(timestampMs)  
PlaybackControlBar \--\> PlaybackEngineService : pause() / resume() \>  
PlaybackEngineService \--\> FrozenPlayheadIndicator : freeze() / unfreeze() \>  
@enduml

##### **Transaction 3.2, Sequence Diagram.** {#transaction-3.2,-sequence-diagram.-1}

@startuml  
class SeekTapHandler {  
  \+isActive: Boolean  
  \+onTap(x: Float, y: Float)  
}

class SeekPositionMapper {  
  \+measureLayoutMap: Map\<Int, Rect\>  
  \+tapToTimestamp(x: Float, y: Float): Long  
}

class PlaybackControlBar {  
  \+isPlaying: Boolean  
  \+onPlayPauseTap()  
  \+onResumeTap()  
}

class MeasureLayout {  
  \+measureId: String \<\<PK\>\>  
  \+scoreId: String \<\<FK\>\>  
  \+startTimeMs: Long  
  \+pixelBounds: Rect  
}

class PlaybackEngineService {  
  \+isPaused: Boolean  
  \+pausedAtMs: Long  
  \+pause()  
  \+resume()  
  \+seekTo(timestampMs: Long)  
}

class FrozenPlayheadIndicator {  
  \+frozenPosition: Rect  
  \+isPaused: Boolean  
  \+freeze(note: NoteEvent)  
  \+unfreeze()  
}

SeekTapHandler \--\> SeekPositionMapper : forwards tap coords \>  
SeekPositionMapper \--\> MeasureLayout : reads \>  
SeekPositionMapper \--\> PlaybackEngineService : \< seekTo(timestampMs)  
PlaybackControlBar \--\> PlaybackEngineService : pause() / resume() \>  
PlaybackEngineService \--\> FrozenPlayheadIndicator : freeze() / unfreeze() \>  
@enduml

##### **Transaction 3.2, ERD.** {#transaction-3.2,-erd.}

@startuml

hide circle

entity "SCORE" {

  \* scoreId : String \<\<PK\>\>

  \--

  title : String

  musicXMLPath : String

}

entity "PLAYBACK\_SESSION" {

  \* sessionId : String \<\<PK\>\>

  \--

  \* scoreId : String \<\<FK\>\>

  \* currentNoteId : String \<\<FK\>\>

  audioClockMs : Long

  isPlaying : Boolean

  pausedAtMs : Long

  seekTargetMs : Long

  isPaused : Boolean

}

entity "MEASURE\_LAYOUT" {

  \* measureId : String \<\<PK\>\>

  \--

  \* scoreId : String \<\<FK\>\>

  startTimeMs : Long

  pixelBounds : String

}

entity "NOTE\_EVENT" {

  \* noteId : String \<\<PK\>\>

  \--

  \* scoreId : String \<\<FK\>\>

  pitchMidi : Int

  startTimeMs : Long

  durationMs : Long

  staffVoice : String

}

SCORE ||--o{ PLAYBACK\_SESSION : drives

SCORE ||--o{ MEASURE\_LAYOUT : has

SCORE ||--o{ NOTE\_EVENT : contains

PLAYBACK\_SESSION }o--|| NOTE\_EVENT : tracks

MEASURE\_LAYOUT }o--|| NOTE\_EVENT : maps to

@enduml

### **Module 4\.** {#module-4.}

#### **4.1 Transaction Name: Sight-Reading Training** {#4.1-transaction-name:-sight-reading-training}

##### **User Interface Design.** {#user-interface-design.-8}

![][image14]![][image15]![][image16]

##### **Front-end component(s)** {#front-end-component(s)}

* **Component Name:** PracticeModePicker  
  * **Description and purpose:** Allows the user to choose between "Listening Mode" (playback only) or "Test Pitch Mode" (activates the microphone for frequency tracking). Captures the user's selection and updates the ViewModel state to route the application to the corresponding practice flow.  
  * **Component type or format:** Jetpack Compose Modal/View (Kotlin)  
* **Component Name:** PitchDetectionEngine  
  * **Description and purpose:** Analyzes the continuous raw audio stream from the device microphone using the TarsosDSP library (YIN algorithm). It extracts the fundamental vocal frequency (Hz) in real-time and continuously emits this numeric data to the UI layer for visual comparison against the target note, targeting a response latency of approximately 0.5 seconds or lower during active pitch tracking.  
  * **Component type or format:** Kotlin Native Audio Utility (TarsosDSP)  
* **Component Name:** VisualFeedbackRenderer  
  * **Description and purpose:** Receives the active Hz data from the Pitch Detection Engine and calculates the deviation in cents against the MusicXML target note. It immediately updates the UI state to render the appropriate visual feedback (e.g., Green for ≤ ±10 cents, Red for \> ±10 cents).  
  * **Component type or format:** Jetpack Compose UI Modifier / StateFlow (Kotlin)

##### **Back-end component(s)** {#back-end-component(s)}

* **Component Name:** SessionMetricsController  
  * **Description and purpose:** Exposes the POST endpoint (/api/session/save) to capture the user's performance metrics payload. It binds the incoming JSON data directly to a Session Data Transfer Object (DTO) and commits it to the database via a JPA Repository layer.   
  * **Component type or format:** Spring Boot @RestController linked to Spring Data JPA (Java) 

##### **Object-Oriented Components** {#object-oriented-components}

* Class Diagram  
* Sequence Diagram

##### **Data Design** {#data-design-1}

* ERD or schema

##### **Transaction 4.1, Class Diagram.** {#transaction-4.1,-class-diagram.}

##### @startuml

##### package "Module 4: Real-Time Pitch Visualizer" {

##### 

#####   class PitchVisualizerController {

#####     \-isTracking : boolean

#####     \+startPitchTracking() : void

#####     \+stopPitchTracking() : void

#####     \-executionLoop() : void

#####   }

##### 

#####   class AudioCaptureService {

#####     \-hasMicrophonePermission : boolean

#####     \-sampleRate : int

#####     \+checkPermissions() : boolean

#####     \+requestPermissions() : void

#####     \+startAudioStream() : RawAudioBuffer

#####     \+stopAudioStream() : void

#####   }

##### 

#####   class PitchDetectionEngine {

#####     \-noiseThresholdLimit : float

#####     \-minConfidenceLevel : float

#####     \+isNoiseLevelTooHigh(audio : RawAudioBuffer) : boolean

#####     \+analyzeFrequency(audio : RawAudioBuffer) : DetectedPitch

#####   }

##### 

#####   class PitchComparator {

#####     \-TOLERANCE\_CENTS : int \= 5

#####     \+calculateCentDeviation(detected : DetectedPitch, target : TargetNote) : float

#####     \+evaluateMatch(deviation : float) : FeedbackResult

#####   }

##### 

#####   class RenderEngine {

#####     \-MAX\_RENDER\_LATENCY\_MS : int \= 500

#####     \+renderIndicator(result : FeedbackResult) : void

#####     \+displayNoiseWarning() : void

#####     \+displayPermissionError() : void

#####   }

##### 

#####   class ScoreManager \<\<External (Module 1/3)\>\> {

#####     \+getCurrentTargetNote(playheadPosition : long) : TargetNote

#####   }

##### 

#####   package "Data Models" {

#####     class RawAudioBuffer {

#####       \+audioBytes : byte\[\]

#####       \+timestamp : long

#####     }

##### 

#####     class DetectedPitch {

#####       \+frequencyHz : float

#####       \+confidence : float

#####     }

##### 

#####     class FeedbackResult {

#####       \+isMatch : boolean

#####       \+deviationCents : float

#####       \+captureTimestamp : long

#####     }

##### 

#####     class TargetNote {

#####       \+pitchFrequencyHz : float

#####       \+durationMs : long

#####     }

#####   }

##### 

#####   ' Controller Relationships

#####   PitchVisualizerController \-left-\> AudioCaptureService : manages \>

#####   PitchVisualizerController \--\> PitchDetectionEngine : coordinates \>

#####   PitchVisualizerController \--\> PitchComparator : uses \>

#####   PitchVisualizerController \--\> RenderEngine : updates \>

#####   PitchVisualizerController \-right-\> ScoreManager : fetches target \>

##### 

#####   ' Service / Engine Dependencies

#####   AudioCaptureService .right.\> PitchDetectionEngine

##### 

#####   ' Data Model Consumptions & Productions

#####   PitchDetectionEngine \--\> "1" RawAudioBuffer : consumes \>

#####   PitchDetectionEngine ..\> "1" DetectedPitch : produces \>

##### 

#####   PitchComparator \--\> "1" DetectedPitch : compares \>

#####   PitchComparator ..\> "1" FeedbackResult : produces \>

#####   PitchComparator \--\> "1" TargetNote : against \>

##### 

#####   RenderEngine ..\> "1" FeedbackResult : renders \>

#####   ScoreManager ..\> TargetNote : provides \>

##### 

#####   ' Constraint Notes

#####   note right of PitchComparator

#####     Enforces the ±5 cents

#####     deviation constraint

#####     for accurate matching.

#####   end note

##### 

#####   note right of RenderEngine

#####     Enforces the ≤0.5s

#####     display latency

#####     constraint.

#####   end note

##### 

##### }

##### @enduml

##### 

##### **Transaction 4.1, Sequence Diagram:** {#transaction-4.1,-sequence-diagram:}

@startuml  
actor "Choir Member" as User  
participant "UI & Render Engine" as UI  
participant "Device Microphone\\n(android.media)" as Mic  
participant "Pitch Detection Engine" as PitchEngine  
participant "Score Manager\\n(MusicXML)" as ScoreManager

\== Initialization & Permissions \==

User \-\> UI : 1 Select "Test Pitch"\\nMode  
UI \-\> Mic : 2 Request Microphone\\nPermission

alt \[Permission Denied\]  
    Mic \--\> UI : 3 Permission Denied  
    UI \--\> User : 4 Display Permission\\nError Prompt\\n(Pitch tracking\\ndisabled)  
else \[Permission Granted\]  
    Mic \--\> UI : 5 Permission Granted

    \== Active Pitch Tracking Loop \==

    User \-\> UI : 6 Initiate Playback

    loop \[Until Playback Paused or Score Ends\]  
        User \-\> Mic : 7 Sings assigned vocal\\npart  
        Mic \-\> PitchEngine : 8 Stream continuous\\nraw audio  
          
        alt \[Environmental Noise Too High\]  
            PitchEngine \--\> UI : 9 Noise Threshold\\nExceeded  
            UI \--\> User : 10 Display "Noise\\nWarning"\\n(Pause visual\\nfeedback)  
        else \[Optimal Audio Level\]  
            PitchEngine \-\> PitchEngine : 11 Analyze audio\\nfrequency\\n(Determine sung\\npitch)  
            PitchEngine \-\> ScoreManager : 12 Request current target\\nnote data  
            ScoreManager \--\> PitchEngine : 13 Target Pitch\\nFrequency  
            PitchEngine \-\> PitchEngine : 14 Compare detected\\nfrequency\\nto target note  
              
            alt \[Pitch Deviation ≤ ±5 cents\]  
                PitchEngine \--\> UI : 15 Match Successful  
                UI \--\> User : 16 Render "Correct"\\nvisual indicator\\n(Latency ≤ 0.5s)  
            else \[Pitch Deviation \> ±5 cents\]  
                PitchEngine \--\> UI : 17 Match Failed  
                UI \--\> User : 18 Render "Incorrect"\\nvisual indicator\\n(Latency ≤ 0.5s)  
            end  
        end  
    end  
end  
@enduml

##### **Transaction 4.1, ERD.** {#transaction-4.1,-erd.}

@startuml  
\!theme plain  
skinparam linetype ortho  
hide circle

entity "User" as User {  
  o user\_id : UUID \<\<PK\>\>  
  \--  
  username : VARCHAR  
  email : VARCHAR  
}

entity "DigitizedScore" as DigitizedScore {  
  o score\_id : UUID \<\<PK\>\>  
  \--  
  title : VARCHAR  
  upload\_date : TIMESTAMP  
  music\_xml\_path : VARCHAR  
  part\_count : INT  
}

entity "PracticeSession" as PracticeSession {  
  o session\_id : UUID \<\<PK\>\>  
  \--  
  o user\_id : UUID \<\<FK\>\>  
  o score\_id : UUID \<\<FK\>\>  
  mode : VARCHAR \-- 'Listening' or 'Test Pitch'  
  started\_at : TIMESTAMP  
  performance\_score : FLOAT \-- Aggregated result  
}

entity "NoteEvent" as NoteEvent {  
  o note\_id : UUID \<\<PK\>\>  
  \--  
  o score\_id : UUID \<\<FK\>\>  
  pitch\_midi : INT  
  pitch\_hz : FLOAT \-- Used by Pitch Engine  
  start\_time\_ms : LONG  
  duration\_ms : LONG  
  staff\_voice : VARCHAR \-- (S, A, T, or B)  
}

entity "PitchAttempt" as PitchAttempt {  
  o attempt\_id : UUID \<\<PK\>\>  
  \--  
  o session\_id : UUID \<\<FK\>\>  
  o note\_id : UUID \<\<FK\>\> \-- Links to the NoteEvent being tested  
  detected\_hz : FLOAT  
  deviation\_cents : FLOAT  
  is\_match : BOOLEAN  
  timestamp\_ms : LONG  
}

User ||--o{ PracticeSession : performs  
DigitizedScore ||--o{ PracticeSession : "is used in"  
DigitizedScore ||--o{ NoteEvent : contains  
PracticeSession ||--o{ PitchAttempt : records  
NoteEvent ||--o{ PitchAttempt : "is the target for"

@enduml

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,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>

[image4]: <data:image/png;base64,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>

[image5]: <data:image/png;base64,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>

[image6]: <data:image/png;base64,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>

[image7]: <data:image/png;base64,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>

[image8]: <data:image/png;base64,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>

[image9]: <data:image/png;base64,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>

[image10]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJsAAAEMCAYAAADTdcTDAAAa7klEQVR4Xu2da4wk1XXH94MNiU0Af0ukfIgU2QQc5UMkO5jYjnlZUaQoEl4whmBw/BCOk/AyLxOwCRgIrwUWOU6iYBG+GHZtQozzIjj2Rma9wNrZ3Vlmd2dmp3fes/Oe6Zme7p6Zmzr31qk6de6tnp7ururuuecv/VVVt269f33uo6qrdqyWldrMpfKGKq12hvX+6KE9z5Wvk90N+7iZo2Mom/Fa3sETknZf1EolcHVDVanXlB5iOh2i6TTNw02Xofld82g6H3dtk68jyrdmL18zP5vP89ZaLi2dr5PmqZWv1jRNS9s+n0fz0Pk4Xa64uTDwpUNXAzYGWFWJRJbW193R2eYpBTadmSwoEm0mHpxcwFmwYSgU0ERbFeUGx1NhS1AZeENYE21R6+vp0c0J20ppna9DJKpbwI8LOAJbPFNgEzWjMitOHbDFkQ2atiJRM6LRzYKNRjWpq4maVSpstHyVIlTUCgFHYOSLRDaBzXsFpZku0dBNKhU2CprA5plCyDbWiSl4DQpZokWpwOa5ELT1tcBVM6TQNarllbUINmyRSp3Nc0WwBaDNDTHgWgRbjci2xpcTbVeRqLZWUarwphlS4BoVhw2c6PqQYtQzkai2FjBQOKBUdTUGTsPWYHTjsFmRrbS6rjOJPNFGHNUAMgrbGsAGKLQaNolsnsoFW8lEOR3dsoAtWWcT2LyRA7ZKXrAhcCJP1A7Y6B0EqbN5pHbAJsWop2o7bBLZ/FG7YIOhRDbPlCtsYacujWxSZ/NIOcIGtmCTyOaRMoYNbhI4i1GBzUNlDFtqnU1bilG/lCNsUoz6rgxhg6eHakY2gc0zZQgbj2wRbHgHQVqjnikn2KwndbGfTWDzSBnChqWkFdlaUYzOzs6Ku80zs2p6OvDUnJqanFMn96+rqYl5NR2MQxrMgzzWcnU4tRhNwCaRzR/lGNnAYZ2NPjwpsHmjDGGLI1sMnBXZpM7mkXKCLdFAoK3RRutsoi5ULrCl1NngDoLA5pFygg2ralKM+qycYEtEtlYUo+VKxXvPLyyoQ4cPa/ccPWrN7ziXK2q1VFGllYpaWaqogf3B9V8MposmDeZBHmu5OpwKW7A+iWwtEIKG7h8Y4Fk6S3lGttWo66P5yCayYQN3tPKELS5GJbK1Qhy03t5enqWzlBNsNbo+soFteHhYFytDwXC7amZ2tnuiGign2HKPbFB5Bi0tLbE5orapPbCF/WxNvFhmIIhatQy/dJ4mbr/7+wdVf19B9Z8oqOP7VlX/8VN6fADSgnk8f70GjqDfNhW2rBsI5XJZHT9xgieL2qX2RDZ6I771sK2tramlYlGPY53thEDXfmUIG7KEN+KtyAZutBgVdaEyhG3T1mgzdTZRFyon2JKRLUzAolTkiTKELS5GWWSjoAlsHilD2FIjm8DmqTKEDf5ekITNeW9U6mzeKEPYeGQDW5FNGggeKUfYnMWowOaR2gUbDKXO5plyhU2KUb+VI2zOOlujkQ0eIRJ3n4eGRtRQYVSdGhzTN+JPnRwLpsGjeh7PX69j2GLgcrk3KupQ5RjZnA0Egc0j5QSbDmitLEZFXaicYHPW2eRGvGfKETZnMSqweSSBTZSb2gkbAifyRBnCZt+Id0Q2gc0jZQjbppFNilHP1A7Y5D8Inion2Nh/EOQOgpfKEDZeZwMnIpsUo54pQ9jqKkblSV2PlAts+E5duV3lt3KCLRHZBDZPlQts0vUhArUbNolsHqkdsElr1FO1A7ZWFKP8Q1nizvcMfChtalZNTc6qyfFZdej1YDgWTE+YNJjXqg+lgVsGm6gLlXFko/8/cEY26WfzSBnDtmlkkwaCR8oRtpTI5jds8GUWfNv3SqnEZ28vtR02j+ts/DsGYHhF67ZVxrDRIjQqRqXrw4iDBp6YmODZto9SYKsGTFRbANumdbZGYYMXNHe7OWgatslJK9+28VJRLS4U1cJcUc3PFNXRnyypmenAs4tqdm5RzS8sqcWlJXu5Osxhc96IbxS27SAAi4LW09PDs2wvkchWCa5//4F1tbhcVaeXl9VE4OVKRVXX14NsWw9vFmw8soF9hg0E35tC2OC7DdtaBLZycO37Dqyp6aWSOjg9pg7OjKnZ1ZKqBBkAt60qho099YHASWvUM1HYShvqxIGqemtqLILt53NjanWtqtY3moGtVms0g07dSrmqCse3cUW7W+WIbIX5hQi20ZVFVW46stWALYti9FQA2jtvF3iyqN1K1NmUGgjqbMXg+i+VKmpptaJK1TU1OTLXAGrwYskMW6P8/VzUvYcG1LH/G1SDfY2/70vcesOnnU6dGlaFwWE1ODCi9r86ok72B+MnAwdphWBef++QtVw9To1sybeFt7bOtrxYCiLbuDZEt/W11q5/u4l3vWQqRz/b6lIQfOYCL5i006NzfKm6lApbss7WWhiO/WIoGgfYoO62FgDXd3iE5BKBBgsFC7apqSmerXVisJ18Q6m+X0yoI/sL6sjPCmrgyIQqzfOF6hOHzVmMthI2gAoAW5xbVnPTS3ocrGE7IrBxnejrs2DL9MvTtM5WUurIa8vq8BuFCLaeNwtqZnRZrVf5gpuLw2b1szVTZxM1LwCLwwafAs9MBLbitFKvP1+wYHvnrYIuUrcqC7Y8ilHR1kRBy/wORsaw0SJUYOtQwaNNuTxtkjFsudbZRB0uAltpcUMdeHnagm20fzZoPGy9p01HthA23ePhgk3qbB6JtUYHf6bU3HhV9R0aVwM9k2pmrKS7QjZacLsqimwInMDmmSzYNlR5ZV2Vi4EDFtaq62pjfeuggThszkeMsrg3KupQcdgOqAC2DVVZXVfVAI616obO04hs2Jx3EAQ2b+S4g9Dax8IdkS3L21WiDlbGsGFkA77KlRZ2fQwMDIi7zP2B+/oG1InjA+p474Da93IwfCeYPmbSYB5fpl5z2FKK0cZgE3WhcopsiTobjWzSGvVI7YBN/srnqdoBW6LOJrD5owxhwyqZVWeTYtRTZQjbppENLLB5pAxho99BkMgmyhQ2HtnAFmzS9eGRcoTNGdkENo+UC2zxY0YCm8/KGDbDFYNN7iB4qsxhixsIzjqbNBA8Uoaw8a/ykchmLLB5pgxhow0E0/Uhkc1v5QSbBg6LUXl40lNlCFv8Yhn4BKQJaFExKg0ED5UhbLTrIwGbFKOeKhfY5HaVCJQxbIn/ILgimxSjHilj2GgDwSpGEbhG9PgTu8Td5Cef0t6161n19FN/p3Y//Q9q/7+Mq2ef+Uf1zNPf1mlP7tpt8vFl63CyGDXtgkQDoRnYRF2onCJbAjYpRj1VTrAZ4KTO5rdyhA0+KWHBJq1Rj5QTbIl+NrTA5plygg2BsyJbo8Xoiy/tEXeV92q/tOdltXfPK+p7e36gjvz3kvr+3h/qafCePd8P8/FlN7cNm9TZ/FYukc3criJdHwKbl8oFthrFqNTZPFJOsOV6b3RhYUHddMtXExZ1gDKETYNGI9sqtkYzhq2n56ge/tqv/4Z68cU90bSozcoQttTIRqNbo7DxyOUywHbFlVdb6eL8ffOtxrfddre644571dfuul/fG73n7gfUXXfep9Ng3s233G4tW49pAyFRZ8vrLUYY2bpZ8BWWD134UX0s4PMu+B2epXuUU2RjsOVzI347wAa/WgQN3bXKCTZdjEZ1thC2LCPbT9/Yry/Mh4OoAOrWi0SjGhoaQF2pnGBLRDackH62zfXdl/ZYsHWtMoYNQUNbkU1gq0+f+/wXtbtaGcMWFaM8sglsHipD2JAlR2Qzlbgs62yiDlSGsKXW2fLq+hB1mHKCDatqVjEqsHmk3GAjxWiyziaweaOcYEsUo7QSJ5HNI+UCm3W7KvtOXVEHKhfYUiKbLkYbhO1TO68Wd5l3XnWNuvrqz6pr//Tz6vrrb1SvvXBCfe76P1fXXfdFde21f6Y+/ZnPqp1X2svVY971kYANhhLZPFPGkS3xPBuPbFC+AnAiT5QxbM46W6IYFdj8US6wxY1Pgc1n5QhbSmSTOps3aj9sEtm8UQ6wUb4s2KQ16pFygK1mZBPYPFKGsMVvC2ewyVMfnipD2FIjG8IGFtg8UjthkwaCZ2oHbFJn81Q5wga2YJPI5pFygU1uV4lAucBmQLNgQ+BEnign2JyRTWDzTLnCJnU2v5UhbDU6dY0FNs+UIWwQ2RIfSuPFqMDmmTKEDVnKpDU6PDws7kIPDY2oU4VRdWpwTB3ft6pOnRxTQwXwqJ7H89frGLYakU06dT1SlpHNaiAIbH4rQ9hS+9nk3qinygk2iWyinGCDp3VTYJPI5pFygc2AVq44YGs0shWLRXG3eamoFhcDzy+rhdlldXL/ulqYWVaLc8F0kLa4uKzzWMvVYQ4bqbMZ4CSyeaZcIhvrZ5PHwj1VLrBtJN+pK4+Fe6q8YEt7GWAWsE1MTGh37fcCtqtyhM1ZZ8sKNlC5XFaHDh9mc0VtUztgw8iWVTGKsK2USgJbJykn2NBWMdpoa3QpaO6mGT4uBsDBsLe315ovbpOh62OhqLs5oOtjIOz6WMCujwWTx1quDnPYrMjWDGy1hJENNDAwQOaI2qocI5vVGs0Ktt5jxzRwABrCJsVpBygn2JwNhKzqbCIT3eEHBi5XKnx2e5QTbC2vs4nSNTs7G4GG7gjlAlsMnMCWg6BRxGGDbqC2KxfYWKdufAeh8X42/liwOHbP0aMWbDxPuwyPfutHwAfHVN9PK+ax8MHRph8L58WoszXaKGyi2qKgQaTrCOUW2eRJXZEDtmoGsEmdTeSGbdVMr1WD2S2EzVmMCmweKQBpI7jc61UTzShskAbzWgGb86mPZhoIoi4UwhZGt8KbMWiQpmFrUAgb8uWMbI3ChncHxN3l/oGTqr9/UBv+pNzfZ8aNT1r56zVwlHj9Ao9szcAm6k5thNENIxzU0/Q0FJ8NFqEgXmeLilEETmDzUAAbAQ5BayVsqV0fApuHCsFKQNYEaKDUyEYTpDUqaoXs97NZt6sMbJpukagJIWzIl9UahS/fQoZKRaKbqDlhny1wBf+Gt+psUm8TtUIQrGjjAIeJ1ijCJkWpqBnxxgFrjdqNhHJZilLR1gVBihahyFdN2EwFT4pTUf2qVOOSkfJkwWZaDCYzLU7B3aaNGnWAWvNaoazX3ynix1ldA2ZM8QnGdgC2RBlsruhmDGUwP4d8Y1ybzU8TXc61hkbWm7YMpqfNB9WaV4/qXd6Vz5VGxefy/DCddox8mooux9NdKgWQLRPQklGtBmwY4fgDlQAceDWoy62vbwQ2EQ+2Tw/IZZyH4vn5OM1Tz3Taemqts948fJoPqfm8tKFrnXQez+tKT8xny7vG+XCzdaJ4Prz21bDIRC4MZPEjRQiZVYxSEo1NGERKcYXg4nI1OR4YhhBCYbhUrGhH48tm2kpPcep8x7p5Xj7tTKtjGUznx5BIY+nWfDYP0+i0c9thnuJKMl9iuZRt4jidjoaw3hU7b2KbruWifTDTy+E6zHgMGYUtBoxGtw21AyfoECMadPJy0OiGwNFJIo7y0ANKnIzkiYEDgLw0H12Wr4OPw8m30tjyiW1baXAx7P3l5stgXth/Mz/MF+bXxxWeI9f+0HGeD9P4dvhyLqdtgxvnJfYjnKbWeZZh+2Z/aERDR7A5i1IT0KxOXepopeEKiss2dNw43/xaKvrXhGn0V6F/uRF0FRMJwgPCfHix4vWb+fTXby9vpukyOE8Dhesj28B9QZvljRO/ejId54u3R/PiD0hfPJJu5q0ZsMN9jY8zPIZwnXyfzPGthfsbb5de9HhbfJsGEppmzgfmjZdLHhu/BrGxxwI5iaObC7gwssGMWjYnw96Y3mH9i0sWofrXwH5d8a8nvDDs10N/ZXw6sY7EeuP106GOLHDSiggn22eSV893bNuVhsvrSEbWQ+fx5egyzvXpcXvbGC1pXr5OV7prPGnYZrwstZ03PH9kndRxYMDohuDF7FDgNGwUOPyFwBc6cBx3hJ8w+ouIDgSnHQfBD4ivK3FAkJ7IH47rNPtEJ8yWc+WD48NxV/GZPCb7GKL9JPkSQ7Y8L+pjx+cQp+l+UBDptpN53PPiNNf+2vvDx13z4zRjGohohOUBK4KNg0Z3LNqQ3lhyg4tL5cQQx/kJgzTjYLxoxpPpsZeKZnm6zqST24rHjel6+fYTy7G8NA+uJ55PpsPtY148Vr4O6jg9eezJebgutq+YRw/ZsVhghsvzc8z2w0yz46L7HW2HzqP5k8fIWcEfLwdvh4aMgcZbbGaDyRPa6V5iQ5fxwjbn9p2XWseWtzkvEXgEOAMbiWYarJBm9MLiqtPzCzheUvOY5shXy+n5S1Zaet5w/kKch+fl6XzIx43j48L18+004vi82Yb18m3y/M1u3+V615mWJwkfh84At4PWyTB8LjDA5hdK2nPzgRPDFTPEcUjHNDpNHeVHY34yzZez5lPXWpdrPWT9fNpahuRz5iXLRNsl23NtO1oPTafrYNvFdOf6+TSfR+bzdaeO43K4zZXw2uM6yL4sGC7QLvgQOKjX7Rgbn1Ho0bHp2h5FT6mRwPF0nG7y4TA0pkXL4PLGdJymxelk/cFQp0frt5elefW6WN4R5zHwbSb3xTrOyMltRU6ch7RjqceuvI7jSZxjus/JcdvkuBznND7XZBnORYrHxoPhOAwNXztK0OEWmt66EotbYcqXwCbO3AKbODcLbOLcLLCJc7PAJs7NAps4Nwts4tzcEGz//sMf6eHtt9ybSH/77Z7EsJaHDyvV+1+t8fEf2Wn1erh/UY1PzCY8cXrOSmvG0JHJ05pxq9cHjxrx60O958UfROOPP7JbDQ1PqhuuvVHNzBbVV750m5XfZcrXlmB7/rnv6iHC1ts7qB4LduLttwxk33r2Ob0TQ8OnrWXRzQDC3cy6Tv581Tr5vsEG0PDrk7zeL+rhvp8c0EOADefBteb5XW4YNtwowrbvxwcC2J5Rd9xynzoWgAeQAXwYAV1eWbYvfKNuFLbjP163TjzYJ9jg/iW/NtRwXWdmlzVgGGRgHK7v898x0/W6IdjE4kYssIlzccPFqFjciAU2cS6WyCbOzQKbOFcLbOLcLLCJc7PAtgXfd8ML6sPv/kv1e+/+q8A3aV94xs3qI2feEvhWdcX7H1SFvhlrOXHMlsC2if/4N+8LIPuLBGgAmQbtjAC0M25VF515m/bv/9JXA9+uTgl0CQtsm3hqclk9dtNe9aF3UdAcsJ2JsBnQwDvPe1i98txb4ctU7HX7aIEtxYPHpzRkCBqPaiaicdAMbB/95TsC36k+9p471QNf2GOt21cLbMwQiQb7phOQuUG7NRW0j2nQ7tL++HvuVrtue9Xajm+WYtRhgO1D7/pKVEczoNmNAayjXaQh4xEtBu3j7/2a+oPAR98eU/S9sj5aYAutXxkWwPCli5+yGgM8om0GmoYsAu0e7U+c9deKvmPWRwtsofGVYRy0C6PGAIJmYDOQpYMG0YyC9omz7lX/+p2D3jYYpBiNbF6E+M9PvM5A490bt6qxwqz2zvMeikCD6fHAV53/tyFkgc9Kgnbxr9wX+OvRqz7tfdj+FthCQ1S75ncfibo3MKLxVicKYMOIhrrq/EejOloCtBC2SwLYYDs+wtaVke2PLrvCSoM/ZNTzJxuX6etdedHJQYNiE3Xlbz2suzag5RnBdsFjUVRLRjQD2qVnf0O/nwy3yfelXsOfTXganBd4hJund4q3BWxX/sl1Oq1R2LAIBQhoRNN1tDNJ90ZYR4theyTq4kB9WsPmjmiXBKBdevb96siboxFojQIHxwumfyjqdNjAXQsbRDMYPxhA9tjDuxuELY5q8KK6RKtTQ2bfGYhgC+pn2CBAXX3B486IhqBdes7f6EYCRjd7f+ozngP4oWGU63TYuj6y3XDNjfqENwsbGGD7CAMNIpmJaAY0qKOhoDGA3RsxbE9EoF1y9tdj0M4B0O5XlwWw7X+tP3q3bKN1NzgH39r9T3qIf8MT2DIwL0aHhiYbLkYxqsHFh1dx8n40V/dGDBs2Bu6JYfvgE1GrEyCLI5oB7bJzHlDjIwvRy4z5/tRrDRv7v2anwwbuOtjuYP/CR8P/VXnaZsaoBhce3vlK7wwgZLwfLYINGwMEts988EkT0Tho5z6gQbv83Af1/zQRtkaLUtefiuG87CX/XO9Edx1srXX87QeIbM/e86rjrkDyFhSKNgZi2HbZoAUR7fJzDWiXn/vNYDurusiGbfrU59aVxWirjBcai1GIbPv+7WiiMYCwYauT3uvcrNUJjQEwRDSIbADbFy76e/3NhWYjW7faW9iMk3U2eO06BQ370SzQnP1odkTDohP8ySCqTU0v6u0020DoVnsPG62zwTv8X3/lkFV0pkY0Z/dGDBpGNADtmt/erWGmsPkU2bwuRtG0nw1gm51bVle8/yED2XvdT2+kgwaQ2RHtk+97SE2entOwAdRxa9Qf2MACG+n+MF+xWVGTk3PqD3/1G5uA5mh1JkD7ZgRa/4kxNTOzpKMabRz4FNnAAlt40QEALEqhawFeJ/WpDzycgCzqsK0HtPcZ0E4cG1FTUws6YmIR6iNoYO9hQ8PFh6iDDQWozMNncIaGJ9TO8x5N3hk429xUTwftIfW//9mjhkcm1enT83p9EDXx63T0zgXfj+1qypfnsMX9bTS6TQagAHAH959QOz/wKKmfhaBhPS3qR3tQR7T/2HtQjY5NqYlJeKvjkl4fFqE+9a1xew9bHF3ihgJGt5m5opoO6lpQhxsenVSDhTH1wlP/o7588bfDaGYi2tXn71IPffl7qudwQZ0aGtdF8Omg6ARgaVSjLdByxd6X7W7vYaOmLVMADj95OAXABfBApIKIBUUrQAWGohI8Mno6gGxGgzk9s6jraPBpRFp8+lZ0cjth48DRaRzn+Xmerdi1U3Q9PM2Vz7U8X4crTyJvCAOAUYSO3qBIhW+uAjQAD0Q5qMudnppXp6cXdHcGwAX1MohkME9HsxAy/Z3NELSoBeooQvm+4D7z43cdC1+Wj9eya521xl3m66R508b1N+L1BA59NBx7KfmMG0Y5gAehA6DQ06Hx47ARZNHHXE1EWyF1NWu7Pjj8IYP1Z7sjhyfHT5tzUFo1sOkoF36+PPqiNP2qcDiOX5peXDSgYcctPa8InZcm52EHnMzI+H14Tw2wmPMQnxPs8AW4ILrNsiFCRz9bnTinvp9Xch7+H/ST9K+y513uAAAAAElFTkSuQmCC>

[image11]: <data:image/png;base64,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>

[image12]: <data:image/png;base64,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>

[image13]: <data:image/png;base64,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>

[image14]: <data:image/png;base64,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>

[image15]: <data:image/png;base64,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>

[image16]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAKkAAAGnCAYAAADMqHfrAAAsuklEQVR4Xu2dCZgV5ZnvdbJOHBNN7p3nMcnccZ5777hMYsZcF2RQ5iqCmuSOigaj44KKOwJKjIICgohAIxAFHQQRtwgyEInIEiVENkdUQMAgS0AIe0PTNNAgyHfr/1W9VW+9Vaf7dHedPt/p8/6f5//Ucqrq1Kn6nf+3VJ06xwwd+qRRq132MQcPGaNWu2yFVO28FVK181ZI1c5bIVU7b4VU7bwVUrXzVkjVzlshVTtvhVTtvBVStfNWSHN49LPjzNo/b0rMVzfc61fvNMsXb0zMz9eNhvQ/xy6ITT/R9bUm7UhDfc+994fu/Uj/xOtN9UnfP9luW87Pxy+/+rq5ouM1dihfK0fffEGFueCEbon5+brRkD58/fMhqHy8OQ2QMNyxszocJ69dt8nsqKwOxz9aujKxPp+H5HxiyPDEMuS57y4Kt5e2Pp93Q+cudhz7lC+otK1c+8rnY8j3Jdc69JqcV2puNKQw4DzzmC5FARTmYNL45R07mYvaX2aHBAnBR8vccFOXcBzDaW/OsstzSDFfrnfRxZeZj5asNGe3amPn0ZcDAMv96tqtZzgNUDAPQ+wP1sf8uX9cZOcPHjoi3F9alraJND/73DZ2PczHvmMe7RP2nRKf5tE4Shh8BgwxTa/T+zeXkaSdftT40q7kIUWi4ETgRGIeThiKWsAECDAPMNHyNKRxJCiGuSBFQsllMY33oHGaT8a+YD5Axf7lgpTmY1/xXrRNvE7bxOfhyQwoYQ4kpSWfR++Hzw7TlwCvnXL6GbH9LbRvvmCouezvH0zMz9eNhtSl4p6b6qk0jROOaV4l4JCSc0FKaSffF0ABAgKBv05FMZYDEPVBinEOKd8X/nmwLBKW7xOSFZZVHrnPML48dn2R/K670ZBKKJ+45zfN2nCC004ETvCNnW8Lp0857Qxb3CGt+InFOFISy+KkhQkc1O3wOhXZtCyWA1iUlHa8lX/i6f0AIb0/lkFRTuPYDyxPSUawoRcBUKI6QZ+BGoP882BZpCithy8D1sGy2IaEVNahJcjN5aIlqbo0TWldSlZIy8hI+Xx7G1yyQlomRnVH1p1LxQqp2nkrpGrnrZCqnbdCqnbeCqnaeSukauetkKqdt0Kqdt4Kqdp5H3P4iDFqtcs+xqhUjkshVTkvhVTlvBRSlfNSSFXOSyFVOS+FVOW8FFKV81JIVc5LIVU5L4VU5bwUUpXzUkhVzkshVTkvhVTlvBRSlfNSSJuoI0e+sD506HDCmK9quhTSBujo0aPmwIGDZk/1PrNr916zbXuVWbt6i1m2eJ1Z8M5KM3vKBwnjtcpd1XadmpoDFl5Vw6SQ5hBg2rt3v9m6bbeZ+sI8c2PrQabNN7vaJ1tnYWzr0dsmmI0bttv3UeWWQhoIKYl0BJTP9J+WCuS/HH+P6X7FSDNm0H+aWb+dZz799NO8vOSDj83UF982wx961Vx3Tv/Edsn4Inyy7DMvrQ/J3StrlT2kgLO29vMQToAiwex947Nm/juLE/A11gRtLmCxD5s+2yl3tWxVtpCiOAeYL478fQxMQInEk2AV2gC3+xUjEsBe9YO+tj5bzio7SFGko7FT0XNSDExAIsEpplGl4LBe8ncP2H0ux4ZXWUCKIh1phOTs4J1sOvEociUcLlom7Ete+pdTupYFpOgqAqBUrOOkz5/zfgIGly1BRbKWSwOrxUKK9KzeeyCWnmioyJNfasaXS6ZqS1eLhHTf/loLJzrTcSLROnetzpmFeaq+/Ou35WFoMWpxkCJBAWhFz4n2BLb/3v2Jk9tSLFO1pdZTWxSkVPdE8Y4We6k0jJpqfBEJ1KkT5puDBz+Xh6ak1SIglek58J7xiRNZDsYXk2Dd8pdd8jCVrEoeUgCKGzjQ94mTgxMlT165mBf/HX/Q1x6blqCShhS3wiFBly5eZ09MqXUrFcq9bng2bFAt/2i9PGwlp5KFlACl7iV5osrdqPJQqr439xN5+EpKJQkp1UEBaLnWP/MxB3XFkg0lW/yXJKS8DipPjDpuAhVF/6crNslDWRIqOUiRoLACmr95om7fUiUPqfMqKUjRDzrlhfnaSGqELz+1dwhqqf32qmQg5fVQTdGGG19q6vQfO2i6PLxOqyQgpWvx/bpM0IZSE01pWrm9Wh5mZ1USkFJfaEu+Dt9c/mjxxyGo4554Sx5qJ+U8pDX7/NvtcC8o7laXB13dcNPl0zbfulcebiflPKQAFL/c1BTN1pSm44fMlIfcOTkLKVqgO3busT+Uw13p8iCrm2beLeW6nIWUinm05rW7qTCm1v7UcfPk4XdKzkKqjaXmMaXps/2myVPgjJyEFHeY051N8qCqs3UpFPtOQkqXPVvCD+dKwQTprf86VJ4KJ+QcpHQLnq0rlcnPP4ptfke/i3IKUgCK6/O/7PSs9ok2o3kH/94q957w5xSk6HLSO5yK46kvvW2P+wUndHMOVKcg1dvwimtK01dGuPUbfucgRVGvddHi2NW6qTOQotsJV5c0RYtnKvIV0hxCg+muS/2HcsmDp24e8wbU4j+skqeoaHIGUqqL4rlN8uCpm88EKRpQrsgJSKlvtM03y/fBDq6YPyLdFTkB6e6qGlsfHdGr+R8Dzo0n782fs9havpbmhiyf73LFNq+XutIV5QSk6B9FfXT2G/n/o0fWHtg1uoYNt/9eT/tPI3I5GLClLZ8LQp5OuS71Yl1XbqahVv60FxbIU1UUOQFpMftGxzwxJQSIP8eUA1jX8jQ/bXn8jY6cR9PyC4l5rtw3S4/pueaf+8tTVRSVPaRpcMH4B5I0oPJZnhKVpq84rVe4HN3DyevfWF5ur5jmDz5btWSjPF3NLoU0B3S8bjZm0JR6l3951PRwPl2MoGlexPPfv9M8qjrw7RXbtI8uFPlFh/Tzz49YSHPV1QrtXNDFkzH6ZUA+y8sqA6/b8voppqn64NpVNtrHHpePkqes2VV0SPftqzUL3llRtN/Td7locAw8pBrm0/QVp/WOLS//xY7mpy1P8+qCVG7HFfPPWGwVHdLqvfvN7CmLY0VqcxpXWfjjvAkqGpd/CMGvysDUC4Bx+QcStAyvk8ri3t+G36rH+mhZw0hYua/Naf4Zi62iQ4p/qMN/esrWbnObF9ccQLkcmf/sgizB4v9qR/PCZb3XsH0ClC9HXwS8Lr8kzWX+uYqtokOKPtJ+XV5IHKTmMhpI1C+INJf/6MEBg3kXFOqR8k/A5PIyefnj0mlZat3zejktX6y6Ok/8YqvokNLtefIgNZfT4JKg8vpy2vK8JwAe0Tt+5QzbwzZgSkaCHePUM8DrrrwKwrfVXHbp8mhZQzrrt/5jJGF5Y4sET87LZ/lcJkDpJzJUdeBfBll3bW7zEqLYKiqkdGNJsSDldca0+wZkS5wvn9bQyxdSuQxtl4Nf7CRVSAMVG1KYToS8A4vqiXA+dUWeynVd3qTE5ctQvZX3AuR6n+ayQhroiy/8B+MWs+FEJwLm89HfSfPz6cxHSzxteWnqrpLLyG3SdLF+NauQMgFS/JOdPEjNaV6MS0uYYNmvSkbqpS3PnWub/GYUcrG6n2BtODH5T86bXdQTok5au6CYdlZWmynj54V3DqndMC8tiq2iQ7pnzz6z5tPN5pVRbyYOlLp45tWOYqvokB44cNDWS2W/o7q4VkiZ6Fa929oNThwodfGskAoV86ZndboJUBceB+kMpPjzBnmgsvK9d/Y0L0141Yx5dpxZunSZN+3feYR5Xb1xDBctfM90ueluu4ydXvSeXWb6mzPCbWAZvt1///ktsfeY/rsZdl28B9afPGmKndflJv9CAZbhy9M+wDSNdfh2Bw8cFmzjbu+1qeFytH+FML8w4cJzoZyB9IbWgxIHKytzOHwgol9l9u09MPYajT9w38N2mAvSpUuXmpFPjgqn57wzNxwfOXxU7D1puTRI5XS/hx+zUGIcwNL+4f0AL72G5fj6WZr/FMaFJ5k4Aan9z9Dx8wrWVwoAAApSaemSKElhDmmab/USDGnb8WfXxiD9VQAxUlOuA2MdOS8NUuwX/IS3b5imLwqGfXs/Fn5JuOW+ZG3qyNdfizLV1Pj/NFKon5AkUysd0sGP+6DASEMM05IUAAEspBrBOubZ58N1CTCaptQjEFFUo/im1+n9kJbYJm0X78eLdwKf73/W5ndzufAjPMgJSOkaPm7yKFSa5mskLSzn5+M57/whti4gwzy5jFyvPlMR3xx26UoTyQlIIWo84ZssD5y6+exS1xPJKUhnTVls01QeOHXzWSGtQ1QvLeZte+Vu/vstV+qjkDOQ0g3QSNNi10vL1fz2vM3rK+UpKpqcgRQCpHCx7kYvd7tY1ENOQYoHRbhwE3Q5mhf1fW4aL09NUeUUpIcOfW4hxa17WuQ3r10t6iGnIIUIVG3lN5/5Y4VcajCRnIMUAqT+34jnfsyNOju7WhclOQnpvv21Iaha7BfWrqco5CSkELX0C3U9X+3b9RSFnIUUT9sDpLg7Sh5YdTbmLfqfnPygPAXOyFlIIfxID6D++7kDEgdY3TTzYr7TPz8qD71TchpSujvqmQHTtNjP2LyYd63LScppSCEq9tGIkgda3Tj3utH/CxzXi3mS85BC1IjqWcQHm7UU8wexudxY4ioJSClNcSVKn3TSNPMHsZVCikIlASlEV6LwH6T6IInGmbfmh3Z/TR5iZ1UykEL4o1wq+ov9RxClZv5oSpf+JjwflRSk1NqHrz/v8cSJUKcbV+14PdTVK0u5VFKQQngsD34CbftPWxXut+ctyRxQF/7hrqEqOUhJlKi3t6/Q6/s53P3K+N/3lEpDSapkIYUIVPShaqs/bgmoC/+23FiVNKT04z34jvbRgx3K3bIOWmoNJamShhSi2/rgoT0nJv7oq9zMW/EtAVCo5CEl4dn7BOt9HZ9KnLxyMIez1It4rhYDKf0kmuqo5fQkFNTH+ZWkUm4kpanFQEri9dRyaFB1uWhwIkEfufF5eVhKWi0OUoh3+sPoT21psMobRVpK/TNNLRJSCH8Yweup+K+olgLqwK7jw78/53b9vtDGqsVCSqJ/NyEPua90W//yn6DJLaWBlEstHlISb1hRfRV3BUkQXDR/cAO3C3+60BwqG0hJdMsf95Tx7zoHbFqDiPxM3zfM3qr98qO1WJUdpBBA5bf9UbKOGzqt6F1XueqbZNee09QcKktIuWQDi4xHUL41aaGZ/caCBEhZenivV3MW52S02ltqoygflT2kXEhY+hl1Lq/65DPz/vzl5r13l5mFcz+s9w4s9Ci8Mnq6/TUBfkZcV0pKu/D3NC5IIU0RYN21y/9dVT5eu3qL2bxlp/XkcXPtPxzzfzluqNEgKqc6Z31SSPMQLg7U1h6yKYu/PpeQcqM/VkJXn1Gcvzz89/JtVYEU0oz1xvgFCQg5jEjJOVM/avF9m1lKIc1YABAgwvipBlrjBKUW4Y2TQqpyXgqpynkppCrnpZCqnJdCqnJeCqnKeSmkKuelkKqcl0Kqcl4Kqcp5KaQq56WQqpyXQqpyXgqpynkppCrnpZCqnJdCqnJeCqnKeSmkKuelkKqcl0Kqcl4Kqcp5KaQq56WQqpyXQqpyXgqpA6qurjYLFy6Ss1WB8ob0kb6PmpO+f7J1n779w/kzZs6y885u1cZ063E/W6N0NHHS63JWnbry6k7hZ77yKn/8kX7RMWmoXpv4ujnl9DPkbFWgvCGFCFJYzsOBJiEZcOLlyV+xYmXq/E2bNtl5lCZYDuMwpQy9RuMbvXXwBYFJmM+3Q8IymI91pNp1uMzuP18n136SsL9Yp2LYcDuNz86PCUT7Ru9J+y0/B/+sXPWtj+MC5Vq/JanBkFKKQEjUbj16xiDFwaPXl3sHUAINyZNK4xhSGvMvAx+HOl59jZ1e4J0YDMc8N87br2tsskFIJUzTuiQ+zufx+VQyQHvYZ+GSkOI4yPfBZ8frGMcxwT7ydaCbbulih/x40PvL9akkIzg733JbWKLR8i1VDYaUDjYdTBoSpHRg+TokWoZOMsShtsmx0U8OzKP5fBzq7oFM03TSMU3jfB9oHyGeuqS6tk2vS9H+4ws6cdJk+6UARCRaB+nG94t/efC5aX84pIAvbX36wmMdOmZURSDYW6oaDClEaYqTRPMJQDrJqKuRITqwnW/u4jk6ERASkA46nThM0zJ8HJIgQZhu1/7S2PviPV+b5AMAn3Oen7RyPb4tjFMi0zSvykA8Sam6QAkHYZrvB63PSxZ8BhKHlPYnbX1KU7wnrYP35e/dEtUoSNPG6UBS0pLoAPJ0o4Sg17EORCeornEoF6S8QUd1OZxQklyH5vH5VH3hr0vJ4h5JJtfJBQ5eQ12XfxE4pLStutan5MQ4krelKzNICTSalpDw+hWKPFofJ4gONObRycM4jIYBjZPSIKWTS8UhJZXcTyn+PhBPO14V4SJI6UtB6xBYGEdrH9OoN3PR+/Hjxb/YVM+ua30KBJQ8afvX0pQ3pDhYMNUZ6eBhml6jeWhwUOuUC68TDHJbOPBYj4u/J38P+X4kbFvOz7UvXGnrYH9yrcPfnz4D1uHbwDjWp89Los8ip+U+5Fpfrkvv35KVN6QqVbGkkKqcl0Kqcl4Kqcp5KaQq56WQqpyXQqpyXgqpynkppCrnpZCqnJdCqnJeCqnKeSmkKuelkKqcl0Kqcl4NgnTHzJlylkpVcOUN6eHqarO2okLOVqkKrrwhXXTxxQqpqijKC1Kk6OzvflchVRVFeUG6+bXXFFJV0aSQqpyXQqpqsFD9a04ppKoGa/PEiWZF9+7hNKD97Lnn2BLZSiFVNUoAFUwA0DmnnmpW9elj1g0bJhfLRAqpqtECDwB074oVdhqMQDxls1CmkMon6tET9+STRppTeKQPfzgYHk2DR//oQ2uz0YGNG2PT4IRgzUqZQgoBVDwjCc8xks8qAhwwni8K4RlQeNIdATNj1iwLFT0/Ck+RO6dVGws7hnhmEp7Ih3E89IvWwzieqFfx5Aj/jQLhETR4bpN8gh1/QJgqO6HIX96tm3WWyhxSPLyLHuHIk5Wg5Q8EwxDL4CFlgAlw4rGSWIY//AsCsPxZpPcGT7+jtKb58vlNElJIPvRW5bYyhxQCAJRyBAPSyz5Elj2zFElKRS9gAmD0rFK+LQiAckhJ8nmo/Gl1kIQU8ANwVemoIJACNP4wXBJPVZrGcoAUT49D0Y8himn+zFIsD5jTIOWPTUR1oa4kpcdO4vmoeEKzqjSUKaT0+EJ6hCM91pAeT0jT/PGP9DqtJx+BSNDKRyTKxhh/XzmfLy+nVe4rU0hVqkJIIVU5L4VU5bwUUpXzyhRSXM+FcZkMQ3m3DObxcbJKVZcyhRTL8PEDwT+QkOT6clqlSlPmkO5euDAc55DWBuP8F6f1bU+lgjKHlI9zSPFDvsUdO8aWqW97KhVUUEjnnXuuNdZb0rmznY9x3IhA4ypVfcoUUpWqEFJIVc5LIVU5L4VU5bwUUpXzUkhVzkshVTmvgkB6cHeVObCjUl1mPlS9V6KQiTKF9MjBQ2bPrhqzt+aQuky9Y+lKc3j/folGk5QZpAd3VZld6zcndlpdft61YbPEo0nKDNKaTZtN1bZdiR1Wl5/37Ez+1qwpyhTS6t1a1Kt9ZymFVF0QZymFVF0QZ6lMId2xudJs37pHrZZ4NEmZQqpJqiZnKYVUXRBnKYVUXRBnKYVUXRBnKYVUXRBnKYVUXRBnqUwhXbN8vVmzcmNZe8XsReaD8ZOs5Wvl5CyVKaSapIfMJ48PtscKlq+Vk7OUQpqxFVLfWUohzdiAdN2EVxLzy81ZSiHN2Aqp7yylkGbk3Vt2mp2r1imkgbOUQpqR8feFOEZ/PPschbTGYUi3bNxhtm2tKjvPPb9t2FiCVz33QmKZcnOWyhTSck3SOaf4KUre9v6SxDLl5iylkGZgFO8cUvl6OTpLKaQZeffmnWarJmjoLJUZpPu2bjOV6zYmdlZdft69aZvEo0nKDFJox7KViR1Wl5erq2vNzuWrJBpNUqaQQtjBqm27vaJ/n7rMvHvT9oI8aidzSFWqrKWQqpyXQqpyXnlBCuGyn0KqKobyhhT/F6qQqoqhvCGFFFJVMdQgSFWqYkghVTkvhVTlvBRSlfNSSFXOSyFVOS+FVOW8FFKV81JIVc5LIVU5L4VU5bwUUpXzahCkR6sWy1kqVcGVP6SH95ov1o+Wc1WqgitvSI98cJVCqiqK8oPUS9HDc3+okKqKorwgPbr1twqpqmhSSFXOSyFVZaYjizpYTuAspZCqMtPRqvcVUpVjChrUpCPvdTBHa/5kEzVLZQZpdXW1WbhwkbWct2LFSrZk8yrt/THN91PVcKFL8vD81nbcpqc3DkALoUwh7XxzF3PS908O582YOctOvzbxdbZk82mBB+KVV3ey+/BIv/7hfEzz/VQ1UEhQBuUX60eFwBZCmUFKqhg23PTp299Ce8rpZ8Rg6HzLbeacVm1Mtx49w+krr77GtOtwmZ3euGlTuAyE7XT0XgfsGE6c9Lp9ncZpPYxfedU1puLJEf4bBVoeJOjZ3vZoP8aMfV4hbaIKlZi5lDmkEADo3uP+MEkhFLFINXqd5m3cuMlCtMkDFFDDgJVvCwL8MM1DSmL7AJmWwRDTUtgmAYv1FNLSUsEgBRg0DqHIP+e8NjbxACWEVISxLCAd89y42Lp8fQkpCV8GTNN2HxGQImUJUECN91FIS0sFgRSwAAiIw0DjqArQNJKNIAXEGAIqAMvXQdGeBimBDSGBeSNpxqxZXvE+zjaSqIoBKaSlpUwhBXAwinAIQz4NgJComEfLY5qW2+PBC7gJcFqGqgW0fTIXbYdLriPnqUpDmUKqUhVCCqnKeSmkKuelkKqcV+aQYllTu9kfr4n+z4f/Pir2WylvWTsdrEPT+nsqFSlTSOlmgyPL7/WHSzunvNbNfLHpJXtpzU7/6WE7xKU1f+i/h72jpoCX2lSlo0whBXhHlnT24LzZTgJS3BFD9xnaeUvotWDoQXpklecASLwHTdf7fqqyUHaQeoBS8U4JmpqkH1xlIQynPUix/SMr/PTFe2AaSapSQdlBavwi20K4JB3SozvfCadtkW+i4v7In3r78+t5D1X5KVNIVapCSCFVOS+FVOW8FFKV81JIVc5LIVU5L4VU5bwyh/RA1Z9Nze7t6nJ05Xpz9MghiUSTlSmkX3g7uHfvPrO35pC6TL1/15rMQc0M0sO1e0xN1fbETqvLzzW7/iLxaJIyg/Tgns80RdW+q/dJPJokhTRjH/j0KXusYPla2VghddsK6SGF1HUD0n1bFibml5VdhnTn9l1m+9Y9Ze29y4eb3ev+kJhfVt6yU+LRJGUKqSapJqm1y0lazpDWfvK4qV3a1dQu76WQKqTu+fP3O4aNJVghVUidMwdUIT2kkLroQx/eFINUvl52Vkjd9P71k83+tS+YmqrKxGtlZ5chXb18rflkyTp1mXvNirUSjyYpU0jLOUnVzC4nqUKqtlZI1c5bIVU7b4VU7bwVUrXzdhnS7Zt3mG1bq9Rl7p3bKiUeTVKmkGqSqq1dTlKFVG2tkKqdt6uQfr5vh/6kWe179w6JR5OUGaTQ4QO7kjusLivXVK41hw/ukWg0SZlCCu2vXOPF/d7EzqvLwLu3Zg4olDmkKlXWUkhVzkshVTmvvCDFfzQppKpiKT9IPeHPvxRSVTGUN6QQ/UGYStWcahCk9Ke1KlVzqmGQqlRFkEKqcl4Kqcp5KaQq56WQqpyXQqpyXgqpynkppCrnpZCqnJdCqnJeCqnKeSmkKuelkKqcl0Kqcl4Kqcp5KaQq56WQqpyXQqpyXgqpynkppCrnpZCqnJdCqnJeCqnKeSmkKuelkKqcl0Kqcl4Kqcp5HXPw0BGjVrvsYw7UHjEHar8IXXtQrXbHYNJL0qMGPvS5UaudM9hUSNVOWyFVO2+FVO28FVK181ZI1c5bIVU7b4VU7bwVUrXzVkjVzjszSCt37Dc/Pbm3+T/H3uH7r2h4px0/yxue9aW7vOFd/jDw2V+6m/keOzzny1093xMMI5/7lXuFu5lW8Fe7R0Pm877aw/fXaHifaW19f8L/8vWewr8M3eavHxD+lTk/5gfN+d/wfcE3Hor7uF7esJdpexy5t/nXvyE/nOJHQv9f+Pg+oS88vq837GuHF34T7mcu4v7Wo76/Gfhb/U07z/5wgB23wxMwPsBcfMJjodufONC0P8HziY/743bIPch08HzJt5+w7sB8yXcGe/MG+0PPl35niB3OeHlFgpPGuMmQvjjsbdP2xB7mx8fe7kF5uz8EoASmhdQfhqBaOAWkX2agWkDjkJ775QDM2LCbD6oHJA0loK0COLklqICydQqkbf66blBDWANAUyG17mVhTYIqAX3YgzAA9Pg4oBJWAEqOgcnsQ8kMOLljoA6MOQLWNwDt8O1BPqzfhgNAwyED1Q6HWFjhpsLaJEiRnj8+9jYLpg8p0jOA9FgfUg6rbwFoCCclaRJQSlGZpjY9BaQ+mARrkKAE6lcJ0JQ0Bahfyx9SwGkhZYAmQD0uSNLAHFIywGwrQQ0hlaDGU/TC4wNQg2GUoH6Kpidpfw9CwDnAJiZP0xBQlqodJKgW0niS5kpT7tEPzknwk68bDemGNZU+oMdwSANQgyT1LVI0KO4BaryozwNUSlFW3PM0bfWVwKmAYpynaV1FfRxSCSilaCxJ04p8QOoNIzh9I0XbwgA1NVHTAA1SFICGsBKccpgs8usq6mWSyqLeT9AoTWWSRvZBjQH634ZaX+b5pjPHJDjKx42C9Na2FUGCCkhtgjJQwzppsrinJD2LAxoU8/4wmaSJIp9BKov7eJ00rahnoKYAKh0C+nVR1NdX3OdIUJ6kSeeuk4ZwIj3tdARolKJpgFKa+inqOwmqBZPSVKYo4AyGFkqbnixJKU0JVABqYcXQ99C7ZiR4qs+NgpQD6tdDveFfxeujlKIJQFnDKZmmAaQpSeoX98kk5UU+pWgIZ8z1F/ccVKSoD6ZMUkDJU7SOJE1AmoQ1F6C50lSmqEzTsI6KYj5WL/UBpeIeiSoh9Yv/lCQ9MUjSMEF9SMMkJUA5qLHi3gcUaQpPHvVhgqm63GBIH75hfJCe3D6g8UStu+GUaN0HcOYq7uMJmkxS3rqPwYmWfVjc9xCQBsNEfTRK07QiP9ayt4BGaRqvj0aQJhI1KO6TkLIkJTi98bSWvYQ0XszLhhNBmru499OT1UtlkvJGE6ub8qI+bN2zYh9gckhhyVVdbjCkZ0pAg+IeYIb1UkrRoNjnjaZ4kor6aNiq5wagSVBbfZWK+GSSRg2oAFKWpH6aihQN6qWy4VQXpJSoORtNLEnj3VBRXZRD2va4tBSNw+qD6g95ciZADQBNJimDNWzlx5M0ArMuSKOuqNAWzgjQeOPJL/Z9QCusRz+Uf0OqQZBOGbvAg7SLNU9RAjNW3LMGVKx1n6uoZ8V8apISpLmS1EIq66U8Uf0ElY2m9OKe10VlcR+ASkW+KO4vkKCmpqmsm8ZTVMIZK+alOaCsLprsgoog9aGUaSoaTqzYRws/ajDFE5Xqpbx1H2s4hXVTmaYVCb5yuUGQdvt/o0I4o0RNKepjSRov7qn7SYL6m5FzzJYNlebRm1+yUGI8UdynAJoEs+7GU2qdVNRHCdQ2lKCxJAWcQd00teEkinxZL6VWPXesmPf7Sn04ZZ2UivwgPY+Pt+xlf6lNU+Z46z4d0nijiYMaT9JYH2lKfTSCNWg8ha38inAo+crlvCFFn+iZx3opGquLxiGVnfkEa6Jeyor8YT0m25+sXvidX9o0/bf/2ccDdJedJyHl2lt1IA5qAKtfzLPiPhVSH05KVVvUe/7oj2tjoHK99eJiOxzYZWKQnklAqbgPi/3UflIOZ7xOCtH4E3dMMd0vfT4GKWRTlbqh6ivuU+qlttEk6qRUD43qpjkAzdEFhSGK+lx1Ut6650X+7sqDCc7SnDeksycv8Yv6Y3lRLyDlcIad+RxOv7j3E9SHFLrux4P84t4zF4cULXuIkrT/La9YQJ+8b0q4fLu/fchMf/F98+HcNXb6N7+eGwJKGjtglp2mLwKGHf9xYPj6lg27wySFACXVSWn6qlMHhcsP7DLJQnlvhzF2+t1pK+3wfA/SmqpaOz5v2ifm56cNMZ1OG2qWvPtnO++W1k9bUG8572l/Q4FsknqJCkgh6oaa+fISf9qDs0vrZ8Llf/GDERZQ0uTRiyyog+98w06v/XirufaHI822z6pCUP3xAWb+m6vM0nkbTM+fvWymjP6vcBs2TT3v2+Pv/7bP9iS6oBJ9pDxJ0/pLA0D9oQ/pR3M3JThLc96QUn00DmgySXk/aQxSQCkaTjASkYp81EPvuHCkueMi32lJiiGAfXPCf9n0hNr99wfN9WcNsfNg6Ir/PcAOnxsw04zoOdXcffEo0/5ve3ngHLCQrlm22dx49jCz2hsiTTueMtAmKYZUH4XGDpht7mn/TAjp4x6Uk56e70H5H6bzuSNNzZ4D5upTB1sgAeKaZVvscj8/1Rv/eLMH4VN2XqfTh5itG6q8eRiv8NarNT/57gC73jX/VGFubT3Krkf9o4Nun2KB5ikKIUW3epD94gfDzbUeoEvfXW8G3zHV9Ln2NW96pF0GkGKZy//HEO99DpqZryxjkPa340hU7MOCNz81l/99hV2v509fMv2um2yG3jnNJujC6avNnRc8b9Z9vD1KVF43DSElMHnRHwHKW/U+oP7ww7kbE5ylOX9Ixy3wi/sEqAzQIEF5qsZSlCUpwQpdiyRloMJjHn0rFVJKScAoqwA0f/qE921RP7b/TPmyFYp8fDkgghSOF/c+pCN7vuFBOCiWpIAT2uqlLvT8Y7+3sCJRrz5tsD9v4NthffTnpw/1PMTO5xp0u1/VoTopFCapBynqpPN+94n5xT89GSYrinkpwApR8qGYvy4AduYrS811YZL6Rb0d9yBFilKRz4XkRHE/+9WP7TRAJkDTrt2HgPLiXkBK9VI7XihIcRnUL+4ZoOxqUwQrr4/G66Wy4cTrpNS6/7f/hTpppVc37SuK+652OYB5Z7un7DglKYYXe2lKSQoAUS/FEMX/WC9Nr/zHAWExf+M5w7xkHW3BxDRBunrp5gSkj906MWw0QYAUQn30kpP62nECE5BScX/pSY+aSU/Nt/VSFPdIUiTXjJc+tMU8AEeCQgDzp9/zQaEkBZRI0CVeUkK8TgohUX/2d4NsYs7/3Z+C+qhf7ANQvBdgpSJ9lpem1/3w1xZSSEKK5a8/42lz5/ljzdC7fmeuPPlJWwVAggJWH1DUUdOSNAI0tXWP+igr6gvWcIIvOKFbziRNrZfKIt9a3GDiecyj082W9ZW2hQ9tXr8rASgv7lEXRaJiOHbAjDAVrz9raAgp4Fu97C8enN5J8Ip5CPNRzLf+ug/nh15yIj39zvz77TIRqAGkHpTUuodQB33rxQ+89dZ52/OTCy16JCemcbL9eQ/ZIdVLASmSE1Bh3viB71hYMcQ0LRdCapO0j4UV63BIR/1qhh2HRj04w4x+cKYH3Hpb/8SyKO4nDPqj3Zd9XnHf59qJFlDSsgBODunaj7eFrwNWJCmE1IViDadgPGzdi6I+AWg4jDecJF+53CBIX3zy7TigsY79CFJ/6EOZaDgFRT6Z95VSUR/1k0Z9pX5dVHZFJftIUdQDPmrVx27VC24wiXc/YZjeRxr2lfIuqBzX7rt6dVQM3/KSEnAA0l8/8KYt7jHPb92jVV//ZVHb9SQ68S9krfywCyps0dPVJt5XKlv2gYPWffKyKO8nTb88GqaoHaZfs5f1UQI0KuYjUCVfudwgSGH0j/I+Uup2ihX1Qf9oAlJR1EtIOagySUNYY5dGk11Qd7d72vzqqnGp3U8cVtlPGvWVpkAa68hPuQMqMOqmaOVTXbTbJc95Sfm2HSYui9YBaa4O/QjYePdT7O4n1leaADSok/I+UuqOirqhqK80DmhYxPM6aZCmvEXP01TWS7mH3vVWgq1cbjCkt7StiF8apcuiKaDGIAWUQXEfNp5SIOUpGl0OjdLUWtwFFUIqLovGIE3pKw1TlfWN8kRNuyQKU4KmgUq2V5j4ZVGepCnX7X0oRZoKQH0zQIPxeJpGSdqYu6BiV52CvtJ4P6m4j5QcpKkPZ0qRLyCVXNXlBkO64oNNtvGUdsUpum4fNZgSXVABmH7dND1F0y6LJlPUhzQCNHnlKQZqWNSnQPo1ee2+riRlV5tEh37ykmhKRz674gQg0bKPA8uv3/cRacquOKV25ucHqUzTCNDHwj7SWIqemPt+0hDUWJGfBJUDCmglV3W5wZDCnX7UPwFoLEUFpGmgcmB5ikaw8kaTSNLU4j6ol6bdrpcjSdMAlfXTJKD1geoDGo4fx296zl0njV8azXWrXgApuwsqdllUQsqL/PCGEnHV6UQfUl7cx9NUFPksUaOGUwSqTdJYR37UcEI9tN91UxM81edGQQpvWB3cmS/TVHY9fSl3ndQmagzU3Lfq+U5CGktSlqbxFI3XS2P1UQaqdFjcWz/AGk113wllAaXr9t+QkDKzYj+6ySQXoKxeaot6P00T9dGUhhMlKsFZ32+c6JY9maYyRWNFPSUpjceS1Af1xjP/I8FRPm40pPBPTu6VkqZRoto6KbuvlNdFaVwW+QQrpWeUpnFAAWQiSUNAZZKmF/cxJyDNVdwHycmdVtyH1+3jN5jwot+HUzaeAkjZMAYoT9PYb5vqBjUs7gnUGKBRcR9B6t/9RAkagQpAoyQl85Y+wRk2nGwRP8RsWrcnwVA+bhKk8ITYr0WpZR+5rha+BJOK+rq6oKI0Tbbsk2aQ2iI/HVBbzOcCNAeouVr4MUhZvTSeov7vnNIhrTtNQ0htksbro7xeahOUF/eiPiq7oMJup5Q6abKo9x0DlCAN66RRik4e9UGCm4a4yZCScUXqJ/8gfncfgOkX+37rPnd6BoDmaDzJJEUjKpamgFIU9xGkogsqBdQ0SNPvJWWA0t1QAFT+fITdmR8V+aK4TwWU/ayZUvRv4pDyH+NFLfskoMkkZUU+S1If0GCcJWmsqI+18KNiP1HkszR94fH5CU4a48wgJS96+1Nza9thYYomu6GihlM6sLlTNAFqUOwTrGGCfiVZJ41ueE6HNHdRnw4q3fQs0xRgxkBlLX0JaFjkp6Qob+HLJI2c0rKvr5+0gd1PsWI/Bml6Z/4NP3rGTH56sflw7mcJNhrrzCFVq7O2Qqp23gqp2nkrpGrnrZCqnXcMUgVV7aITkCqoatdsIcX/N0pQ1WpXDD4tpGq1y/7/o7rjNuRLnAwAAAAASUVORK5CYII=>