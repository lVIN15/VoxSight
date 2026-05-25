Team Information
Project Title: VoxSight
Project Short Description: A mobile Application for Independent Choral Sight-Reading Practice.
Team Code: 2526-sem2-it332-51
Members:
Castillo Jr., Teodoro D.
2. Lagamo Jr., Elvin O.
3. Sala, Denzel Don L.
4. Sevilla, John Emmanuel S.
5. Velasco, German Oliver E.

PART 1: Introduction
1.1 Background of the Problem
Amateur and community choirs occupy an important role in cultural and religious life, yet they are uniquely vulnerable to a persistent operational challenge: the inability of their members to independently read standard musical notation. Timoshenko (2018) demonstrates that novice singers fundamentally lack comprehension of the visual score, preventing them from navigating a musical score without external training support. Unlike professional ensembles, amateur choirs frequently recruit singers whose musical training is limited to vocal performance, leaving them without the foundational literacy necessary to decode the pitch and rhythmic content of a written score. Garisi (2024) observes this dynamic directly in an amateur ensemble context, noting that singers without formal literacy drain rehearsal time by relying entirely on conductors and more experienced peers to learn their parts.
 
This literacy gap produces a well-documented cascade of rehearsal inefficiencies. When members cannot read their parts independently, conductors are compelled to adopt rote pedagogy, a process of playing each vocal line on the piano and having singers repeat it by ear. Adokiye and Jacinta (2021) document that this 'listen and repeat' approach forces conductors to address each voice section (Soprano, Alto, Tenor, Bass) in isolation, leaving the majority of the ensemble idle and dramatically prolonging the learning phase. Erdem (2018) corroborates this, finding that even methodologically rigorous rehearsal structures are easily derailed and prolonged when rehearsal time is consumed by basic note-teaching rather than artistic work. Corbalán et al. (2018) further categorize conductors who rely exclusively on this repetition-based model as 'Traditional' in their approach, noting that such conductors typically lack the multi-modal teaching strategies that characterize efficient rehearsals.
 
The consequences extend beyond wasted time. Corbin (1986) directly identifies the habit of 'pounding out notes' on the piano as a recognized waste of rehearsal time that prolongs practice, while Demorest (2001) warns that reverting to this piano 'note-pounding' actively negates any sight-reading progress singers have developed. Frampton (2010) provides the acoustic explanation for why this dependency is so damaging, demonstrating empirically that singers rely more on sustained harmonic context than on the percussive attack of a piano tone to achieve accurate vocal pitch, meaning piano tones are an inherently flawed guide for vocal tuning. This dependency is further explained by the training gap identified in the literature: Demorest and May (1995) confirm that inexperienced, vocal-only singers lack the internal pitch generation that independent sight-reading demands, requiring prolonged external support to learn any new part. Henry (2001) reinforces this, showing that untrained singers cannot naturally find pitches without explicit, time-consuming guidance from a conductor. Daniels (1986) extends this finding to the home environment, demonstrating that singers without access to external instrumental aids such as a piano consistently struggle to read their parts, directly causing group rehearsal delays.
 
The difficulty of sight-reading is compounded by the cognitive demands of the choral context itself. Henry (2011) demonstrated that non-readers struggle with cognitive overload during sight-reading, manifesting as the sacrifice of rhythmic precision in order to maintain pitch accuracy. Nyström and Huovinen (2024) utilized eye-tracking to show that singers in a group setting do not merely read their own lines; they constantly scan neighboring parts for ensemble coordination cues, creating a reading bottleneck even among experienced singers. These findings imply a critical need for interface-level interventions that visually isolate relevant musical information to reduce cognitive overload. Sabella and Haning (n.d.) identify an important demographic dimension to this problem: vocal-only members are at a severe pedagogical disadvantage compared to singers with prior instrumental training, forcing the ensemble to slow down rehearsal pacing to accommodate them. Kim et al. (2020) add that novices are entirely unable to navigate tonal shifts independently without auditory demonstrations from the conductor, confirming that the bottleneck is not merely a matter of effort but of foundational skill.
Outside of rehearsal, amateur singers attempting independent practice face an equally difficult situation. Mok (2020) observed that adult choristers without sight-reading skills resort to informal compensatory strategies: purposive listening to recordings, mimicking stronger peers, and relying on personal keyboards. Arthurs and Petrini (2023) corroborate this, noting that non-readers inherently turn to sound technologies as a compensatory alternative to standard notation. Without a mechanism for real-time pitch verification, however, these strategies do not guarantee accuracy. Hao and Simeon (2023) stress that continuous feedback mechanisms are critical to independent improvement without them, musicians cannot self-correct and group rehearsals continue to bear the burden of remediation. Killian and Henry (2005) found that unsuccessful singers lack any independent practice strategies, forcing them to rely on external aids like the piano during group rehearsals. Carlson (2016) further confirms that novices lack self-accountable practice routines outside of rehearsal, which leads directly to rehearsal bottlenecks. Costanza (2018) identifies a motivational dimension to this crisis, finding that singers actively resist traditional sight-reading methods such as solfege and hand signs, indicating a clear need for modern, digital interventions that make independent learning more accessible and engaging. Furthermore, while Petty and Henry (2014) demonstrate that technology-based practice tools can drastically improve sight-reading, Mishra (2014) alongside Houlahan and Tacka (2008) emphasize that any successful tool must facilitate visuo-motor decoding actively linking the visual note to the auditory pitch to build true musical literacy.

1.2 Problem Statement
Amateur choir members, particularly those with limited sight-reading skills or those without prior melodic instrumental training, are unable to independently read and verify the accuracy of their vocal parts when practicing weekly repertoire outside of scheduled rehearsals. This forces ensemble rehearsals to serve as primary note-teaching sessions rather than as opportunities for artistic refinement, resulting in slow repertoire learning and an excessive reliance on conductor-led rote instruction.



1.3 Research Gap
A review of both existing literature and available technological solutions reveals a critical convergence gap. On the software side, tools that support custom sheet music scanning, such as What's My Note, provide only MIDI piano playback and do not include microphone-based pitch validation. Conversely, tools that do offer real-time pitch assessment, such as SmartMusic, operate within closed ecosystems that prohibit custom repertoire uploads, making them unsuitable for amateur choirs who rehearse self-selected or custom music. No accessible, affordable system currently combines: (1) Optical Music Recognition (OMR) to digitize physical choir sheet music, (2) vocal-tone audio playback using synthesized human voice models rather than piano MIDI, and (3) real-time microphone-based pitch feedback displayed visually to the singer. This tripartite gap forms the direct justification for the proposed system.














PART 2: Objectives
2.1 General Objectives
This study aims to optimize the independent practice of amateur choir members by automating sheet music digitization, providing targeted audio-visual selective focus, and delivering a rigorous training environment to support independent learning.
Specifically, the system modules aim to achieve the following outcomes:
General Objective 1 (Controlled OMR Digitization Module): Automate the conversion of scanned physical sheet music into the playable MusicXML standard with a target structural recognition accuracy of approximately 80–85% under controlled input conditions. To maximize recognition reliability and prevent the generation of flawed practice data, the system establishes a strictly controlled assumption of the input: the uploaded image must be a standardized, high-contrast digital print without handwritten marks or skew.
General Objective 2 (Audio-Visual Selective Focus Module): Decrease user cognitive load by dynamically manipulating the digital interface to keep the specific vocal line fully visible at 100% opacity while dimming unassigned parts. Concurrently, the system will exclusively play the assigned vocal part and significantly suppress the volume of non-assigned background tracks to minimize auditory interference. Substantial suppression of background audio is essential, as any background audio bleed can cause pitch confusion and interfere with harmonic alignment during practice. 
General Objective 3 (Sight-Reading Training Module: Dynamic Score Tracking): Emphasize independent sight-reading familiarization by mapping timing data to a visual playhead, which dynamically highlights the active musical notes on the screen in real-time as the audio plays, maintaining a visual-audio synchronization latency of ≤0.1 seconds to build visuo-motor decoding skills.
General Objective 4 (Sight-Reading Training Module: Real-Time Pitch Feedback): Increase user self-correction success within the training environment by utilizing the device microphone to capture vocal input, processing the frequency to deliver immediate visual feedback that evaluates sung pitches against the digitized reference notes with a target pitch match rate of approximately 85% under quiet environmental conditions. 





2.2 Specific Objectives
To achieve the general objectives, the specific module features are broken down as follows:
(For Module 1: Controlled OMR Digitization)
Achieve approximately 80–85% structural track separation accuracy of the four distinct SATB (Soprano, Alto, Tenor, Bass) vocal parts under controlled input conditions, strictly contingent upon user adherence to the defined input assumptions.
Process scanned sheet music pages within a maximum processing time of ≤10 seconds per page to ensure prompt access to practice materials.
(For Module 2: Audio-Visual Selective Focus)
Implement an audio suppression function that significantly suppresses the audio volume of non-assigned vocal tracks during selective playback to minimize auditory interference.
Synthesize clear auditory reference tones based on the digitized data with a maximum frequency deviation of approximately ±10 cents under standard playback conditions, ensuring reliable pitch-matching support.
Implement a visual rendering toggle that maintains the user's selected vocal line at 100% visual opacity while dynamically reducing the opacity of the unassigned SATB parts to ≤20% to minimize visual clutter.
(For Module 3: Sight-Reading Training Module: Dynamic Score Tracking)
Parse and map the temporal data (timestamps and note durations) from the digitized sheet music to generate an automated visual playhead.
Synchronize the visual note highlight with the internal audio playback engine, ensuring the visual indicator updates without exceeding the ≤0.1 seconds latency threshold.
(For Module 4: Sight-Reading Training Module: Real-Time Pitch Feedback)
Display visual pitch indicators (e.g., correct/incorrect color coding) within ≤0.5 seconds of receiving vocal input from the device microphone to prevent delays in guidance.
Detect user vocal frequencies with ≥85% accuracy against standard digital tuners to ensure reliable feedback.



2.3 Research Questions
The study aims to answer the following problem-exploration questions regarding the system's performance and its real-world impact:
To what extent does the OMR module achieve high structural recognition reliability when subjected to strict controlled input assumptions?
How effectively does the Audio-Visual Selective Focus module reduce background vocal interference to prevent harmonic interference and reduce visual cognitive load?
How effectively does the Training Module synchronize dynamic score tracking with audio playback to build associative learning?
How accurately does the Training Module's pitch feedback detect and display real-time vocal frequencies?
To what degree does using VoxSight improve the independent note accuracy of amateur choir members compared to traditional practice methods?














2.4 Traceability Matrix
To ensure strict alignment between the system modules and measurable system outcomes, VoxSight follows this traceability structure:
General Objective (System Module)
Specific Feature (System Capability)
Measurable System Outcome (Target Metric)
1. Controlled OMR Digitization Module
Vocal Part Separation
Approximate 80–85% Separation Accuracy  (Under Strict Input)
1. Controlled OMR Digitization Module
Processing Speed
≤10s Wait Time per Page
2. Audio-Visual Selective Focus Module
Auditory Interference Suppression
significant audio attenuation.
2. Audio-Visual Selective Focus Module
Visual Staff Dimming
≤20% Unassigned Staff Opacity
3. Sight-Reading Training Module: Dynamic Score Tracking
Audio-Visual Synchronization
≤0.1s Note Highlight Latency
4. Sight-Reading Training Module: Real-Time Pitch Feedback
Low-Latency Display
≤0.5s Visual Feedback Delay
4. Sight-Reading Training Module: Real-Time Pitch Feedback
Frequency Detection Reliability
≥85% Accurate Pitch Match Rate

PART 3: Methods
3.1 Proposed Solution Concept
VoxSight is proposed as a mobile-first application designed to serve as a personal, at-home practice companion for amateur choir members. The system addresses the rehearsal bottleneck problem by providing singers with three integrated capabilities in a single platform: the ability to digitize their own printed sheet music through OMR (Optical Music Recognition), the ability to hear their assigned vocal part played back using a synthesized, sustained human vowel tone (e.g., 'Aah' or 'Ooh') rather than a percussive piano tone, and the ability to sing along and receive immediate visual confirmation of their pitch accuracy via microphone. The primary target users are members of community choirs, church choirs, and collegiate amateur ensembles particularly those without formal instrumental training who currently depend on conductors or keyboard instruments to learn their parts. Secondary users include choir directors who may direct members to use the application for independent preparation between rehearsals.
 
3.2 Development Methodology
The project will follow the Agile software development methodology, specifically using a Scrum framework with rapid one-week sprint cycles to align with the strict 4-week implementation timeline commencing on May 1st. Agile is selected because the system's core modules OMR integration, audio-visual focus, and interactive pitch training require iterative testing and refinement that is difficult to plan exhaustively in advance. Each sprint will produce a testable increment of the system: Sprint 1 will establish the controlled OMR upload module; Sprint 2 will construct the Audio-Visual Selective Focus engine; Sprints 3 and 4 will emphasize the integration of the Interactive Training modules (Dynamic Score Tracking and Pitch Feedback) and continuous debugging. A product backlog will be maintained and prioritized according to the Minimum Viable Product (MVP) requirements, and sprint retrospectives will be used to continuously improve both the product and the team's development process.
3.3 Validation Approach
System validation will be conducted in two phases: functional system testing and user acceptance testing (UAT). 
To measure the actual human learning impact and usability of the system, an A/B comparative pre-test and post-test experimental design will be implemented. Participants will first perform a baseline singing task using an unfamiliar choral excerpt (pre-test). They will then be divided into two groups:
Control Group: Practices using traditional audio recording methods.
Experimental Group: Practices using the VoxSight system.
After a set practice session, both groups will perform the same excerpt again (post-test), and their improvement in note accuracy will be measured and compared.
Evaluation Metrics 
System Functional Metrics (Validating Objectives 1, 2, 3, & 4)
OMR Output Reliability: Percentage of correctly digitized notes and structurally separated SATB parts (Target: Approximately 80–85% under controlled input conditions), contingent upon adherence to strictly controlled input physical scores.
Audio-Visual Focus Clarity: Verification of strong attenuation of non-assigned vocal tracks to reduce auditory interference to reduce harmonic interference, and successful ≤20% opacity reduction of unassigned staves to measure visual decluttering.
Training Synchronization: Verification of the ≤0.1s note highlights latency against active audio playback.
Training Pitch Accuracy & Latency: Percentage of correctly identified pitches (≥85%) and the delay (≤0.5s) from vocal input to visual display.
Human Performance Impact Metrics (Experimental Validation)
Independent Note Accuracy Improvement (%): The difference between pre-test and post-test accuracy scores, directly comparing the experimental group against the control group, targeting a ≥30% improvement for system users. This target was selected as a conservative and achievable baseline, given that prior studies on technology-assisted practice (Petty & Henry, 2014) have demonstrated potential gains exceeding 100%.
Usability Assessment Metrics (Experimental Validation)
System Usability Scale (SUS) Score: Standardized measurement of overall user experience, targeting an acceptable industry score of ≥70.
PART 4: Expected System
4.1 Minimum Viable Product (MVP) Features
The following four core features constitute the MVP of VoxSight, each directly traceable to a research gap identified in the literature review:
Feature
Description
Literature Basis
1. Controlled OMR Digitization Module (Maps to GO1)
Users upload constraint-compliant images of printed sheet music. The system parses the physical score into playable MusicXML data with approximately 80–85% structural recognition accuracy under controlled input conditions.
Petty & Henry (2014) found that technology-based individual practice tools significantly improved sight-reading; however, existing tools operate in closed-ecosystems that cannot accommodate custom weekly choir repertoire.
2. Audio-Visual Selective Focus Module (Maps to GO2)
A unified toggle that plays the user's assigned part (synthesized soundfonts) while attenuating background tracks to near-silent levels and dimming unassigned staves to ≤20% opacity.
Henry (2011) proves that non-readers suffer cognitive overload. Nyström & Huovinen (2024) utilized eye-tracking to confirm that scanning dense full 4-part SATB scores creates a severe visual reading bottleneck. Furthermore, Frampton (2010) demonstrates empirically that singers rely on sustained harmonic context rather than the percussive attack of a piano tone, making standard MIDI piano tools acoustically flawed for vocal tuning.
3. Sight-Reading Training Module: Dynamic Score Tracking (Maps to GO3)
A synchronized visual playhead dynamically highlighting the active musical notes on the screen in real-time (≤0.1s latency) as the audio plays.
Mishra (2014) emphasizes that sight-reading is a visuo-motor skill; independent practice tools must actively link the visual symbol to the auditory sound to successfully develop visuo-motor decoding.
4. Sight-Reading Training Module: Real-Time Pitch Feedback (Maps to GO4)
The microphone captures the user's sung pitch and displays an immediate (≤0.5s latency) visual indicator (e.g., correct/incorrect color coding) against the digitized reference notes.
Mok (2020) notes that informal home practice relies on unverified mimicking. Hao & Simeon (2023) stress that continuous feedback is critical, otherwise singers reinforce unverified intonation errors during solo practice.

4.2 High-Level System Workflow
The user journey through VoxSight follows five sequential steps, deeply integrated with the training modules:
Upload & Digitize (OMR): The user opens the application and photographs or imports an image of their printed choir sheet music. To maximize recognition reliability, the OMR engine assumes a strictly controlled, high-contrast digital print, processing the image and extracting the foundational musical semantics into MusicXML.
Customize (Audio-Visual Selective Focus): The user selects their voice type (Soprano, Alto, Tenor, or Bass). The system visually dims the other three staves to ≤20% opacity to reduce cognitive load and plays isolated synthesized playback tracks for the selected vocal part while lowering the volume of non-selected parts to reduce harmonic interference during practice.
Listen & Familiarize (Dynamic Tracking): The user taps play to hear their selected part synthesized with human vocal tones. As the audio plays, the system dynamically highlights the active note on the screen, facilitating associative learning between the sound and the visual symbol.
Sing & Verify (Pitch Feedback): The user sings along while the microphone is active. The system captures the vocal frequency and displays immediate visual feedback, allowing the user to self-correct in real-time.
Review: After completing a passage or piece, the user can review a summary of their pitch accuracy to identify which specific measures or notes require targeted repetition.
PART 5: Discussion
5.1 Scope
VoxSight is scoped as a mobile application targeting Android platforms. The system will support the upload and processing of standard choral sheet music files (JPEG/PNG/PDF) and will handle SATB (Soprano, Alto, Tenor, Bass) voice structures for four-part choral arrangements. The pitch detection module will cover the standard ranges for each voice type: Soprano (C4–G5), Alto (G3–D5), Tenor (C3–G4), and Bass (E2–C4). The application is designed for individual practice use and will not include multi-user or real-time ensemble synchronization features in the initial release.
 
5.2 Limitations
The following constraints are acknowledged for the current iteration of the system:
Strict OMR Input Constraints: To achieve the target recognition reliability  and prevent flawed practice data, the system strictly assumes a controlled input. The quality of pitch recognition is dependent on image clarity; therefore, highly ornate scores, handwritten notes, or skewed camera angles are not supported and will produce recognition errors.
Vocal Synthesis Fidelity: The synthesized human vocal tones will approximate the timbre of choral voices but will not replicate the full expressiveness of a live human model, which may limit their perceived naturalness for advanced singers.
Pitch Detection in Noisy Environments: The real-time pitch detection module performs optimally in quiet environments. Background noise may reduce accuracy of the feedback indicator.
Sample Size for Validation: Due to access constraints, the user acceptance testing (UAT) sample will be drawn from local community or church choirs, which may limit the generalizability of usability findings.
Standard Stave Formatting Constraint: The effectiveness of selective audio suppression is contingent upon the uploaded score utilizing a standard "open score" format, where each of the four SATB parts occupies its own independent staff. The system does not currently support the selective muting of condensed "close scores" (e.g., Soprano and Alto sharing a single treble clef) or complex divisi (e.g., Soprano 1 and Soprano 2 splitting on the same line).
5.3 Expected Contribution
VoxSight is expected to make the following contributions to both practice and research:
Practical Contribution: The application addresses the rehearsal bottleneck identified across all 25 sources in the RRL by enabling amateur choir members to independently verify their pitch accuracy at home before rehearsals. By providing continuous, real-time feedback, the mechanism Hao and Simeon (2023) identify as critical for independent improvement, VoxSight reduces the conductor's burden of rote note-teaching and allows rehearsal time to be redirected toward artistic and expressive goals, as advocated by Corbin (1986) and Houlahan and Tacka (2008).

Technical Contribution: The integration of OMR (Optical Music Recognition), vocal-tone synthesis, and live pitch detection in a single mobile application for amateur choral use represents a relatively uncommon integration not identified among the existing solutions analyzed in the comparative review. The proposed system bridges the feedback disconnect identified between tools like What's My Note (OMR (Optical Music Recognition) without pitch validation) and SmartMusic (pitch validation without custom score support). It also addresses the accessibility gap noted by Mok (2020), providing a simplified, mobile-first interface accessible to adult non-digital-natives who currently resort to informal, unverified learning strategies.

Research Contribution: The system provides an empirical platform for investigating whether vocal-tone playback produces measurably different pitch-learning outcomes than piano-tone playback, a comparison called for by Frampton (2010) but not yet tested in an accessible mobile tool. The UAT results, disaggregated by instrumental background consistent with the methodology of Demorest and May (1995) and Sabella and Haning (n.d.), will contribute to the nascent literature on AI and technology-assisted choral rehearsal (Odusanya et al., 2024).



PART 6: Traceability Matrix
The following matrix demonstrates that every proposed system feature is grounded in evidence from the Review of Related Literature and designed to address a specific identified gap.
RRL Finding / Theme
Identified Gap
Research Question
Proposed Feature / Design Decision
Reading music is a visuo-motor skill requiring targeted practice (Mishra, 2014; Houlahan & Tacka, 2008), and technology tools significantly improve this skill (Petty & Henry, 2014). However, novices lack self-accountable strategies, leading to rehearsal bottlenecks (Carlson, 2016).
Closed-ecosystem apps (e.g., SmartMusic) do not allow choirs to practice their actual weekly repertoire, while OMR-capable tools lack pitch validation.
To what extent does the OMR feature reliably automate the conversion of printed sheet music?
1. Controlled OMR Digitization Module: Require strict input constraints to achieve high structural parsing accuracy under controlled input conditions of custom choir pieces.
Singers prioritize pitch over rhythm when cognitively overloaded by dense SATB scores (Henry, 2011; Nyström & Huovinen, 2024). Furthermore, singers lacking literacy rely heavily on piano accompaniment, which is acoustically flawed for vocal tuning (Frampton, 2010).
Singers cannot easily isolate their specific line from the full score, creating a visual bottleneck, and existing tools use flawed MIDI piano tones.
How effectively does the selective focus module suppress background interference and visual load?
2. Audio-Visual Selective Focus Module: A unified toggle that visually dims unassigned parts to ≤20% opacity and strongly suppresses unassigned audio to minimize interference.
Novices fundamentally lack comprehension of the visual score, preventing them from decoding standard musical notation without external training support (Timoshenko, 2018). Effective independent practice must actively link the visual symbol to the auditory sound (Mishra, 2014).
Singers cannot naturally associate the printed symbol with the auditory pitch during independent practice, remaining dependent on rote memorization.
How effectively does real-time note highlighting synchronize with audio playback?
3. Sight-Reading Training Module: Dynamic Score Tracking: A visual playhead that highlights notes in real-time (≤0.1s latency) as the audio plays to build associative learning.
Without a conductor, amateur singers cannot verify if their informal home practice is pitch-accurate (Mok, 2020). Continuous feedback is critical for independent improvement (Hao & Simeon, 2023). Costanza (2018) confirms singers need digital interventions to motivate self-directed learning.
No accessible tool provides instant visual pitch validation for custom choir repertoire outside of closed ecosystems.
How accurately does the pitch feedback feature detect and display real-time vocal frequencies?
4. Sight-Reading Training Module: Real-Time Pitch Feedback: Microphone integration that displays a red/green indicator when the singer hits the target note (≤0.5s latency).















PART 7: References
Adokiye, O. P., & Jacinta, O. O. (2021). The effectiveness of rote and note teaching for a performance in the university choral group. IDOSR Journal of Scientific Research, 6(1), 1–7.

Arthurs, Y., & Petrini, K. (2023). Musicians' views on the role of reading music in learning, performance, and understanding. Musicae Scientiae, 27(4), 939–960.

Carlson, R. (2016). Teaching sight-reading to undergraduate choral ensemble singers: Lessons from successful learners [Doctoral dissertation, University of Maryland]. Digital Repository at the University of Maryland.

Corbalán, M., Pérez-Echeverría, M. P., Pozo, J.-I., & Casas-Mas, A. (2018). Choral conductors to stage! What kind of learning do they claim to promote during choir rehearsal? International Journal of Music Education, 36(4), 540–555.

Corbin, L. A. (1986). Enhancing learning in the choral rehearsal. Music Educators Journal, 72(6), 46–49.

Costanza, K. (2018). The perceptions of high school choral students regarding sight-singing using solfege syllables and hand signs [Doctoral dissertation, Liberty University]. Scholars Crossing.

Daniels, R. D. (1986). Relationships among selected factors and the sight-reading ability of high school mixed choirs. Journal of Research in Music Education, 34(4), 279–289.

Demorest, S. M. (2001). Building choral excellence: Teaching sight-singing in the choral rehearsal. Oxford University Press.

Demorest, S. M., & May, W. V. (1995). Sight-singing instruction in the choral ensemble: Factors related to individual performance. Journal of Research in Music Education, 43(2), 156–167.

Erdem, B. O. (2018). Efficiency in rehearsal: A study on choral conducting methodology [Doctoral dissertation, Istanbul Technical University]. ITU PolEN.

Frampton, T. (2010). The effect of piano accompaniment type and harmonic context on the tuning performance of college-level choral musicians [Doctoral dissertation, University of Missouri-Columbia]. ProQuest Dissertations Publishing.

Garisi, E. (2024). The effectiveness of musical and non-musical choir training processes in reading sheet music at the Student Activity Unit Vocalista Harmonic Choir, Indonesian Institute of the Arts Yogyakarta. International Journal of Multicultural and Multireligious Understanding, 11(5), 18–27.

Hao, Y., & Simeon, J. J. C. (2023). Strategies of sight-reading ability improvement: A review of literature. International Journal of Academic Research in Business and Social Sciences, 13(5).

Henry, M. L. (2001). The use of targeted pitch skills for sight-singing instruction in the choral rehearsal. Journal of Research in Music Education, 49(3), 206–217.

Henry, M. L. (2011). The effect of pitch and rhythm difficulty on vocal sight-reading performance. Journal of Research in Music Education, 59(1), 72–84.

Houlahan, M., & Tacka, P. (2008). An organic approach to teaching sight-reading in the choral rehearsal. In Kodály Today. Oxford University Press.

Killian, J. N., & Henry, M. L. (2005). A comparison of successful and unsuccessful strategies in individual sight-singing preparation and performance. Journal of Research in Music Education, 53(1), 51–65.

Kim, Y. J., Song, M. K., & Atkins, R. (2020). What is your thought process during sight-reading? Advanced sight-readers' strategies across different tonal environments. Psychology of Music, 49(5), 1182–1197.

Mishra, J. (2014). Factors related to sight-reading accuracy: A meta-analysis. Journal of Research in Music Education, 62(3), 252–276.

Mok, A. O. (2020). 'I can follow': Self-directed informal learning strategies adopted by choristers in an adult amateur choir. Music Education Research, 22(3), 335–348.

Nyström, M., & Huovinen, E. (2024). Sight-singing in a group context: An eye-tracking study with experienced choral singers. Journal of New Music Research, 1–18.

Odusanya, O. S., Aremu, D. T., Niyi-Ojo, M. A., & Alabi, A. O. (2024). Exploring the applications of artificial intelligence in choral rehearsal techniques. Independent Academic Review.

Petty, C., & Henry, M. L. (2014). The effects of technology on the sight-reading achievement of beginning choir students. Texas Music Education Research.

Sabella, M., & Haning, M. (n.d.). Relationships between instrumental experience and sight-singing proficiency. Visions of Research in Music Education, 35.

Timoshenko, M. (2018). Seeing into the music score: Eye-tracking and sight-reading in a choral context. Proceedings of the 2018 ACM Symposium on Eye Tracking Research & Applications, 1–3.






Definition of Terms 
MIDI (Musical Instrument Digital Interface): A technical standard and digital language that allows computers, software, and musical instruments to communicate and generate sound (e.g., the underlying code instructions that tell an app to play a specific pitch for a certain duration).
OMR (Optical Music Recognition): A technology that scans physical sheet music and converts it into a digital format that computers can read, edit, and play back (e.g., using an app camera feature to take a picture of a printed choir score and converting it into playable audio).
SATB (Soprano, Alto, Tenor, Bass): The standard four-part vocal arrangement used in choral music, organized from the highest to the lowest pitch.
Soprano (S): The highest vocal range, typically sung by females, which usually carries the main melody.
Alto (A): The lower female vocal range that provides harmony directly below the soprano.
Tenor (T): The higher male vocal range that provides mid-level harmony and support.
Bass (B): The lowest male vocal range that provides the foundational depth of the choir's harmony.
