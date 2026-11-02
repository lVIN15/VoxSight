# MVP Validation Findings & Refactoring Priorities

## Section 1 - Project Information

1. **Team/Group Name:** 2627-sem1-it411-51
2. **Project/System Title:** VoxSight - A Mobile Application for Independent Choral Sight-Reading Practice
3. **Program / Section:** BSIT-4
4. **Team Members:**
   * Castillo Jr., Teodoro D.
   * Lagamo Jr., Elvin O.
   * Sala, Denzel Don L.
   * Sevilla, John Emmanuel S.
   * Velasco, German Oliver E.
5. **Adviser:** Mr. Patrick L. Bacalso
6. **System URL / MVP Link:** https://voxsight.vercel.app
7. **Date of MVP Validation:** 9/06/2026
8. **Number of respondents/participants:** 35
9. **Types of respondents involved:**
   * End User (amateur choir members).
   * Subject Matter Expert (experienced choristers from multiple ensembles).
   * Customer / Decision Maker (conductors were not the primary respondents).
   * **Voice distribution:** Soprano (9), Alto (8), Tenor (9), Bass (9).
   * **Ensembles represented:** Christian Voices Choir (CVC), Koro Teknoy, Jose R. Gullas Visayas Chorale, Shrine Harmonic Choir, Cantus Dei Chorale, Covenant Keeper's Choir, Angeli Cantorum, STC Chorale, and others.

---

## Section 2 - MVP Validation Overview

### 1. What was the primary purpose of your MVP validation?
The primary purpose was to determine whether the VoxSight MVP could meaningfully improve independent note accuracy and home-practice effectiveness for amateur choir members compared with traditional methods (static recordings / piano tracks). Specifically, the team sought evidence on: (a) whether Controlled OMR digitization produced usable scores under real-world conditions, (b) whether Audio-Visual Selective Focus reduced cognitive load, (c) whether Dynamic Score Tracking and Real-Time Pitch Feedback supported self-correction, and (d) overall usability (target $\text{SUS} \ge 70$). Validation results were intended to confirm or revise the project's SMART objectives and measurable outcomes before final system refinement.

### 2. What validation framework/model did your team use?
* **Framework/Model:**
  Hybrid approach combining System Usability Scale (SUS) + targeted Likert-scale items mapped to SMART objectives + open-ended qualitative feedback.
* **Key constructs/criteria evaluated:**
  * Usability (SUS 10-item instrument)
  * Perceived effectiveness of independent practice (learn-faster, more-effective Likert items)
  * Module-specific utility (dimming / isolation, upload ease, voice-part selection)
  * Behavioral intention (would use regularly / would recommend)
  * Qualitative identification of blockers affecting OMR accuracy, pitch-feedback reliability, and focus usability
* **Why appropriate:**
  SUS provides a standardized, industry-recognized usability score that directly maps to the project's $\ge 70$ SUS target. The custom Likert items and open-ended questions were designed to surface evidence against the four General Objectives (GO1–GO4) and their associated quantitative metrics ($80\text{--}85\%$ OMR accuracy, $\ge 95\%$ attenuation, $\le 0.1\text{ s}$ sync, $\ge 85\%$ pitch accuracy, etc.).

### 3. Briefly describe how the validation was conducted.
Thirty-five amateur choir members from multiple local ensembles participated between 6–14 September 2026. Participants installed the Android MVP APK, performed a realistic practice workflow ($\text{upload/scan a printed or PDF score} \rightarrow \text{review/correct} \rightarrow \text{select voice part} \rightarrow \text{isolate} \rightarrow \text{practice with tracking and/or pitch feedback}$), then completed an online evaluation form containing SUS items, objective-mapped Likert questions, and three open-ended prompts. Quantitative scores were aggregated; qualitative comments were coded thematically against the four system modules and the project's measurable outcomes.

---

## Section 3 - MVP Validation Findings

### Finding #1 - OMR Digitization Reliability Under Real-World Scores Is Below Target

1. **What did you learn from the stakeholders?**
   Multiple end users reported that the Controlled OMR pipeline frequently produced incomplete, rearranged, or incorrect MusicXML output when real choir scores (especially multi-page PDFs, scores with solos/accompaniment, or denser notation) were uploaded. Common symptoms included: progress bar stalling near $90\%$, missing or extra notes/rests, altered lyrics, insertion of dynamics/trills not present in the original, and outright rejection of non-strict SATB layouts (SSAA, TTBB, scores containing solo lines).
2. **What evidence supports this finding?**
   * **Open-ended issue reports:** "Uploading complicated sheets takes time", "Some music sheets/PDF don't read", "stuck on $90\%$", "lyrics/notes rearranged and mixed up", "rest notes are being disregarded", "extra dynamics and trills added", "Cant import Scores that has Solo parts and Accompaniments", "only a limited option for voice parts... Limited to only SATB".
   * **Quantitative:** mean "Scanning/uploading was clear and easy" $= 4.14/5$, but the qualitative volume of OMR failures indicates that the controlled-input assumption does not hold for the majority of scores users actually bring.
3. **Stakeholder type:**
   End User (amateur choristers) and experienced choristers acting as informal SMEs.
4. **SMART objective / measurable outcome affected:**
   General Objective 1 (Controlled OMR Digitization Module) — target structural recognition accuracy of approximately $80\text{--}85\%$ under controlled input conditions; $\le 10\text{ s}$ processing time per page.
5. **Measurable outcome that could be improved:**
   Accuracy (OMR structural recognition) and Reliability (successful conversion of typical choir repertoire).
6. **How the finding affects achievement of the objective:**
   If users cannot obtain a usable, correctable MusicXML score from the scores they actually rehearse, the entire downstream practice pipeline (selective focus, tracking, pitch feedback) is blocked. The current strict "high-contrast digital print, pure open-score SATB, no handwritten marks" assumption is too narrow for the target population, jeopardizing the project's claim that VoxSight can serve real weekly choir repertoire.

---

### Finding #2 - Real-Time Pitch Detection Accuracy and Stability Fall Short of the $\ge 85\%$ Target for Some Users

1. **What did you learn from the stakeholders?**
   Several participants reported that the pitch-feedback module was unstable or inaccurate: the system sometimes failed to detect singing, produced incorrect analysis results, or displayed a pitch indicator that users judged unreliable. Audio playback was also described as choppy or difficult to hear in isolation mode.
2. **What evidence supports this finding?**
   * **Open-ended comments:** "pitch detection and analysis... analysis gets a lot of my vocal test wrong", "Test pitch is also a bit unstable. Sometimes the app doesn't detect my singing", "Cannot quite hear the sound", "audio is choppy most of the time", "Fine tune the pitch detection... Change pitch indicator bar and make it more accurate".
   * While overall "learn faster" ($4.23$) and "more effective" ($4.31$) scores are positive, the specific pitch-related complaints directly threaten GO4's $\ge 85\%$ accuracy and $\le 5\%$ false-negative targets.
3. **Stakeholder type:**
   End User.
4. **SMART objective / measurable outcome affected:**
   General Objective 4 (Sight-Reading Training Module: Real-Time Pitch Feedback) — $\ge 85\%$ pitch-match accuracy in quiet environments, $\le 5\%$ false-negative rate, $\le 0.5\text{ s}$ visual feedback latency.
5. **Measurable outcome that could be improved:**
   Accuracy (pitch detection) and Effectiveness (self-correction success).
6. **How the finding affects achievement of the objective:**
   Inaccurate or intermittent pitch feedback undermines the core value proposition of independent self-correction. Users who receive false negatives or no detection at all cannot trust the system, reducing the likelihood that practice with VoxSight will produce the targeted $\ge 30\%$ improvement in independent note accuracy versus traditional methods.

---

### Finding #3 - Audio-Visual Selective Focus Controls Are Not Sufficiently Intuitive and Isolation Is Inconsistent

1. **What did you learn from the stakeholders?**
   Users found the "Visual Focus" / "Audio Mute" controls non-obvious; some had to discover that voice-part selection only became available after toggling Visual Focus. Isolation sometimes failed to attenuate non-assigned tracks, and the absence of a simple master volume control was noted. Dimming itself was generally appreciated (mean $3.97/5$) but not universally perceived as strongly helpful.
2. **What evidence supports this finding?**
   * "I needed to click 'Visual Focus' first, which wasn't very intuitive. Perhaps you could rename it...".
   * "the apps controls were buggy where u need to press one of the two buttons... isolation also doesn't work and the audio is choppy".
   * "Put volume button so the users can adjust...".
   * Isolation ease score remained high ($4.20$), indicating the concept is valued once discovered, but discoverability and reliability need work.
3. **Stakeholder type:**
   End User.
4. **SMART objective / measurable outcome affected:**
   General Objective 2 (Audio-Visual Selective Focus Module) — $\le 20\%$ opacity for unassigned staves, $\ge 95\%$ attenuation of non-assigned audio, $\le 3\text{ s}$ isolation processing, $\text{SUS} \ge 70$ (overall).
5. **Measurable outcome that could be improved:**
   Usability and Effectiveness (reduction of visual/auditory cognitive load).
6. **How the finding affects achievement of the objective:**
   If the focus mechanism is hard to activate or inconsistently applied, the intended reduction in cognitive load (supported by Henry 2011 and Nyström & Huovinen 2024) is not realized for a subset of users, weakening the module's contribution to independent practice efficiency.

---

## Section 4 - Potential Refactoring Priorities

### Refactoring Priority #1 - Strengthen OMR Robustness and Expand Supported Score Types

1. **Validation Finding:** Finding #1
2. **What needs to be improved or changed?**
   Improve OMR success rate on typical amateur-choir scores, surface clearer error messages when a page is rejected, strengthen the interactive review/edit workflow so users can more easily correct residual errors (missing rests, wrong lyrics, extra ornaments), and relax the pure-SATB constraint to accept common variants (SSAA, TTBB, scores containing a solo staff) while still mapping detected parts to addressable tracks.
3. **Type of refactoring required:**
   Functional requirement + Algorithm/method + Data/input/output + Usability
4. **Current MVP approach:**
   Audiveris + `musicxml_cleaner.py` + `satb_analyzer.py` under a strict controlled-input assumption (high-contrast digital print, open-score SATB only). Interactive editor exists but users still encounter unusable scores.
5. **Proposed refactored approach:**
   * Tighten client-side pre-processing and rejection messaging (explicit "page unreadable / non-SATB detected" feedback).
   * Extend `satb_analyzer` to classify and expose SSAA/TTBB/solo staves as selectable parts.
   * Enhance the interactive editor with bulk voice re-assignment and rest/lyric correction tools.
   * Add optional "simplified notation" mode that strips dynamics/trills for amateur practice.
6. **Why is this change necessary?**
   Without reliable digitization of the repertoire users actually bring, GO1 cannot be met and Modules 2–4 become unreachable.
7. **Expected improvement:**
   Higher percentage of successfully digitized real-world scores; reduced user abandonment at the upload stage.
8. **How will the improvement eventually be measured?**
   Structural recognition accuracy (correct notes / total printed notes) on a held-out set of typical choir PDFs; percentage of uploads that reach the review screen without critical failure; user-reported "upload was successful" rate.
9. **Priority:** High — Essential to achieving a SMART objective (GO1)

---

### Refactoring Priority #2 - Improve Pitch-Detection Robustness and Feedback Clarity

1. **Validation Finding:** Finding #2
2. **What needs to be improved or changed?**
   Calibrate YIN parameters (including vibrato confidence threshold) against a broader set of amateur voices, reduce false negatives, make the visual indicator more readable, and ensure audio reference tones remain clear when isolation is active.
3. **Type of refactoring required:**
   Algorithm/method + Performance + Usability + Non-functional requirement
4. **Current MVP approach:**
   YIN at $22,050\text{ Hz}$, $\pm$ confidence $0.55$, octave-error folding, 3-band feedback ($\le 50 / 51\text{--}100 / >100\text{ cents}$), $\pm 10\text{-cent}$ exact-match comparator.
5. **Proposed refactored approach:**
   * Collect a small calibration corpus from the same user population and re-tune detection thresholds.
   * Add a short "pitch test / microphone check" step before Test-Pitch mode.
   * Improve the visual deviation meter (larger, clearer color zones, optional numeric cents display).
   * Guarantee reference-tone volume remains audible under isolation (master volume control).
6. **Why is this change necessary?**
   GO4's $\ge 85\%$ accuracy and self-correction success depend on trustworthy real-time feedback.
7. **Expected improvement:**
   Higher measured pitch-match accuracy; lower false-negative rate; increased user confidence in the feedback.
8. **How will the improvement eventually be measured?**
   Pitch-match accuracy vs. digital tuner in quiet environment; false-negative rate; post-session accuracy summary agreement with user perception; SUS sub-item confidence.
9. **Priority:** High — Essential to achieving a SMART objective (GO4)

---

### Refactoring Priority #3 - Simplify and Stabilize Selective-Focus Interaction

1. **Validation Finding:** Finding #3
2. **What needs to be improved or changed?**
   Make voice-part selection and focus activation immediately discoverable, ensure isolation reliably applies $\ge 95\%$ attenuation, and add basic volume control.
3. **Type of refactoring required:**
   Usability + System process/workflow + Functional requirement
4. **Current MVP approach:**
   SATB chips + separate Audio Mute / Visual Focus toggles; isolation sometimes requires an extra toggle and can fail.
5. **Proposed refactored approach:**
   * Selecting a voice part automatically enables both visual dimming and audio isolation (with optional advanced toggles still available).
   * Rename or visually clarify the focus controls ("Isolate My Part").
   * Persist last-used voice + tempo + isolation state across sessions.
   * Add a simple master volume slider.
6. **Why is this change necessary?**
   GO2's cognitive-load reduction only occurs when users can activate isolation quickly and reliably.
7. **Expected improvement:**
   Higher isolation success rate, reduced time-to-first-isolated-playback, improved SUS and dimming Likert scores.
8. **How will the improvement eventually be measured?**
   Task completion time for "select my part and hear only that part"; isolation attenuation verification; SUS; "dimming helps" Likert mean.
9. **Priority:** Medium — Significant improvement to the system (supports GO2 and overall SUS)

---

## Section 5 - Refactoring Summary Matrix

| # | Validation Finding | Evidence | SMART Objective / Outcome Affected | Proposed Refactoring | Expected Improvement | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | OMR reliability below target on real-world scores | Multiple reports of stalled uploads, missing/extra notes, lyric errors, non-SATB rejection | GO1 — $80\text{--}85\%$ structural accuracy; usable digitization of weekly repertoire | Harden OMR pipeline, expand part classification, improve editor, clearer rejection UX | Higher successful digitization rate; fewer abandoned sessions | High |
| **2** | Pitch detection unstable / inaccurate for some users | Comments on missed detection, wrong analysis, unclear indicator, choppy audio | GO4 — $\ge 85\%$ pitch accuracy, $\le 5\%$ false negatives, self-correction success | Re-calibrate YIN, mic-check step, clearer visual meter, reliable reference volume | Higher measured accuracy; greater user trust in feedback | High |
| **3** | Selective-focus controls non-intuitive; isolation inconsistent | Visual Focus discoverability issues; isolation sometimes fails; missing volume control | GO2 — $\le 20\%$ opacity, $\ge 95\%$ attenuation, cognitive-load reduction; overall SUS | Auto-isolate on part selection, clearer labeling, state persistence, master volume | Faster, more reliable isolation; improved usability scores | Medium |

---

## Section 6 - Items That Are NOT Refactoring Priorities

| Item | Identified? | Action |
| :--- | :--- | :--- |
| **Bugs/errors** (e.g., login friction, progress-bar stall) | Yes | Fix during implementation / testing cycle |
| **Minor UI/UX issues** (button labels, color legend) | Yes | Improve as appropriate in next sprint |
| **Missing original requirement** | No | |
| **Missing essential functional requirement** | No (core four modules present) | |
| **Missing nonfunctional requirement** | No | |
| **New feature unrelated to SMART objectives** (iOS version, calendar, catalog of public domain scores, human-voice synthesis) | Yes (frequently requested) | Generally do not prioritize for current research scope; log as future work |
| **Improvement to measurable outcome** | Yes | Potential refactoring priority (see Section 4) |
| **Change in system approach/design** | Yes (OMR robustness, pitch calibration, focus UX) | Potential refactoring priority (see Section 4) |
| **Improvement in accuracy / efficiency / effectiveness / performance** | Yes | Potential refactoring priority (see Section 4) |

### Important Question
**If your team identified NO significant requirements or design improvements, explain why.**
Not applicable. Three significant findings that directly affect SMART objectives (GO1, GO2, GO4) were identified and prioritized above. Overall SUS (mean $74.6$) already meets the $\ge 70$ target, and high-level effectiveness scores (learn-faster $4.23$, more-effective $4.31$) are encouraging; the refactoring priorities aim to close the remaining gaps so that the measured outcomes can be reliably achieved across the full range of real-world choir scores and users.

---

## Section 7 - Final Team Reflection

### 1. What is the single most important thing your team learned from the MVP validation?
The core value proposition—selective focus + synchronized tracking + real-time pitch feedback—is strongly endorsed by users (high effectiveness and recommendation scores). However, the system's ability to deliver that value is currently gated by the reliability of the OMR front-end and the robustness of pitch detection under real amateur-choir conditions. Usability of the focus controls, while conceptually sound, still contains friction that prevents some users from experiencing the intended cognitive-load reduction.

### 2. What is the most important change your team should make based on this learning?
Prioritize hardening the Controlled OMR pipeline and interactive correction workflow so that a substantially higher percentage of typical choir scores reach a usable state, and simultaneously re-calibrate and clarify the Real-Time Pitch Feedback module. Secondary but important is simplifying the selective-focus activation path.

### 3. How will this change help the project achieve its SMART objectives more effectively?
Reliable digitization (GO1) is the necessary precondition for every subsequent module. More accurate, trustworthy pitch feedback (GO4) directly supports the experimental claim of $\ge 30\%$ improvement in independent note accuracy. Clearer, more reliable selective focus (GO2) ensures the cognitive-load benefit is experienced by the majority of users, raising the probability that the overall system meets its effectiveness and SUS targets.

### 4. What requirements and/or design documents will need to be updated as a result?
* `[x]` **SRS** — update functional requirements for OMR supported score types, interactive editor capabilities, pitch-calibration / mic-check flow, and selective-focus interaction model; adjust related NFRs if accuracy or latency targets are refined.
* `[x]` **SDD** — reflect revised OMR post-processing, expanded SATB/variant classification, updated PitchDetection Engine parameters, and simplified focus state machine / UI component behavior.
* `[x]` **SPMP** — adjust sprint backlog and risk register to accommodate the high-priority refactoring work.
* `[ ]` **Other:** User evaluation protocol may be lightly updated for the post-refactor validation round.

---

## Final Declaration

We certify that the findings and proposed refactoring priorities submitted in this form are based on actual MVP validation feedback gathered from our identified stakeholders/users. We understand that the proposed refactoring must be justified in relation to our project's SMART objectives and measurable outcomes.

**Team Lead:** _________________________  
**Date:** _________________________

### Quantitative Summary Snapshot (for reference)
$N = 35$ | Mean $\text{SUS} = 74.6$ (target $\ge 70$) | Learn-faster $= 4.23/5$ | Dimming helpful $= 3.97/5$ | More-effective practice $= 4.31/5$ | Upload ease $= 4.14/5$ | Isolate ease $= 4.20/5$ | Would use regularly $= 4.11/5$ | Would recommend $= 4.23/5$