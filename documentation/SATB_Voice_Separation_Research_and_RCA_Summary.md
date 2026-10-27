# VoxSight: SATB Voice Separation & Unison Notes Analysis
## Research Findings, Root Cause Analysis (RCA), and System Evaluation

> **Document Purpose**: A plain-English evaluation summary for review and team feedback.  
> **Status**: Review & Evaluation Only (No code changes implemented).  
> **File Location**: `documentation/SATB_Voice_Separation_Research_and_RCA_Summary.md`  

---

## 1. Quick Answers to Your Core Questions

### Q1: Does our current `satb_analyzer.py` already assign colors to vocal parts?
**No.** `satb_analyzer.py` has zero awareness of colors. It only classifies notes into letters (`"S"`, `"A"`, `"T"`, `"B"`, `"Solo"`, `"Others"`).  
All colors (Pink for Soprano, Purple for Alto, Blue for Tenor, Green for Bass) are assigned separately on the mobile frontend inside `renderer.html` (the web sheet music renderer) and `VoicePart.kt`.

### Q2: Does `satb_analyzer.py` use an actual AI engine/model, or is it mainly hard-coded logic?
**It is 100% hard-coded logic and manual heuristics.**  
It does **not** use a machine learning model, a neural network, or a specialized voice separation engine.  
Although it imports `music21` (a Python music toolkit), it only uses `music21` to read the MusicXML file into memory. The actual classification is done through:
- Hard-coded MIDI pitch ranges (e.g., is the note between MIDI 48 and 69?).
- Hard-coded staff positions (Staff 1 = Soprano/Alto, Staff 2 = Tenor/Bass).
- Hard-coded `if/else` thresholds (e.g., if a note on the bass staff is $\ge$ F3, give 80% weight to Tenor; otherwise give 80% to Bass).
- A "winner-take-all" formula (`max()`) that forces every note to pick exactly **one** voice part.

### Q3: Why did I mention Audiveris, and should it be replaced?
**Audiveris should NOT be replaced.**  
My previous remark was clarifying that replacing Audiveris with AI vision models is unnecessary and would not solve this problem anyway.  
- **What Audiveris does:** Audiveris is an Optical Music Recognition (OMR) tool. Its job is computer vision: scanning an image/PDF and recognizing where noteheads, staves, stems, and clefs are located. It transcribes pixels into notes.
- **What Audiveris does NOT do:** It does not know who is singing what. It does not separate SATB voices, nor does it know if a note is a unison between Tenor and Bass.
- **Conclusion:** Audiveris is doing its job well. The unison and vocal separation issues happen **after Audiveris**, in the symbolic classification step (`satb_analyzer.py`) and in the frontend display (`renderer.html`).

---

## 2. Root Cause Analysis (RCA): Why Unison Notes Fail

### What is a Unison Note in Choral Music?
A unison note occurs when two voice parts sing the **exact same pitch at the same time** (e.g., both Tenor and Bass sing D3, or both Soprano and Alto sing G4).  
In standard 2-staff choral sheet music (where Soprano & Alto share the top treble staff, and Tenor & Bass share the bottom bass staff), a unison note is typically written as:
1. Two noteheads side-by-side on the same line.
2. A single notehead with two stems (one stem pointing UP for Tenor, one stem pointing DOWN for Bass).
3. A single notehead during a shared passage (sometimes marked *"unis."* or *"a2"*).

---

### The 4 Breakpoints in Our Current System

| Breakpoint | Where It Happens | What Currently Happens | Why It Causes an Issue |
| :--- | :--- | :--- | :--- |
| **1. The "Winner-Take-All" Classifier** | `satb_analyzer.py` (line 825) & `MusicXmlParser.kt` | The code calculates scores for S, A, T, and B, and runs `max()` to pick **one single winner**. | If a note on the bass staff is a unison between Tenor and Bass, the math picks either Tenor OR Bass. **The other voice part is completely discarded.** |
| **2. The Mutually Exclusive Data Model** | `MusicalEvent.kt` & `SATBVoice.kt` | Every note event only has a single string field: `satbVoice: String`. | There is no way for the system to store that a note belongs to *both* Tenor and Bass (`UNISON_TB`). |
| **3. Strict String Matching in Visual Focus** | `renderer.html` (line 1026) | The visual focus check does: `if (normFocus !== normPart) { dimNote(); }` | If the note was labeled Tenor, and the user selects **Bass**, the system sees `"B" !== "T"` and **dims the unison note to 15% opacity**, making the Bass singer think they don't sing. |
| **4. Highlight & Pitch Filter Drop** | `MidiPlaybackEngine.kt` & `PracticeViewModel.kt` | Playback highlighting and pitch targets only evaluate events matching the single selected part. | The glowing highlight aura is skipped for the second voice, and the pitch comparator does not evaluate the second singer on that note. |

---

## 3. Research: Existing Engines & Approaches for SATB Voice Separation

We researched industry and academic technologies specifically designed to separate vocal parts from symbolic sheet music (MusicXML/MIDI):

### Approach A: Rule-Based Symbolic Voice Combiners (Industry Standard)
* **How it works:** Mimics the engine used by **LilyPond** (called `\partCombine`) and **MuseScore**.
* **How it detects unisons:**
  1. It checks if two notes in the same measure share the exact same start tick and pitch.
  2. It checks stem direction (e.g., Stem Up = Tenor, Stem Down = Bass on the same staff).
  3. When two notes collide on the same pitch and staff, it identifies them as a **Unison (`unisono` / `a2`)** instead of forcing one to overwrite the other.
* **Pros:** Extremely fast, deterministic, 100% reliable, runs locally in milliseconds with zero extra dependencies.
* **Cons:** Requires clear rules for edge cases (e.g., single-stem passages).

### Approach B: Algorithmic Contrapuntal Separation (e.g., Partitura / VoSA)
* **What it is:** **Partitura** is an open-source Python musicology library developed by CPJKU. It implements the **Chew & Wu (2004) Voice Separation Algorithm (VoSA)**.
* **How it works:** It uses an optimization algorithm ("contig-mapping") to trace horizontal melodic streams through polyphonic textures based on pitch proximity and voice continuity.
* **Pros:** Highly respected in academic musicology; excellent at following complex interwoven melodic lines.
* **Cons:** Adds an external Python dependency (`partitura`), and can occasionally over-segment simple hymn-like homophonic choral music where voices jump octaves.

### Approach C: Deep Learning / AI Source Separation
* **Audio-based separation (Demucs, Spleeter):** Splits recorded *audio* (.mp3/.wav) into vocal tracks. **Not applicable here** because VoxSight works with sheet music files (MusicXML), not recorded audio.
* **Symbolic Graph Neural Networks (MusicGNN):** AI models that treat notes as nodes in a graph.
* **Pros:** Can handle complex, messy polyphony.
* **Cons:** Overkill for standard SATB sheet music; slow; requires GPU servers; prone to "hallucinating" voice parts that do not exist in the printed score.

---

## 4. How the Enhanced Solution Fits With Audiveris

You **do not need to change Audiveris**. The entire workflow operates smoothly in sequence:

```
[Sheet Music Image / PDF]
          │
          ▼
1. Audiveris OMR Engine (Unchanged)
   • Converts scanned image pixels into raw MusicXML (staves, measures, noteheads).
          │
          ▼
2. musicxml_cleaner.py (Unchanged)
   • Cleans formatting, resolves XML syntax, standardizes staves.
          │
          ▼
3. Enhanced satb_analyzer.py (THE UPGRADE POINT)
   • Reads the clean MusicXML using music21.
   • Detects individual voice lines (S, A, T, B).
   • Detects UNISON / MERGED notes on shared staves (e.g., Tenor & Bass co-located on Staff 2).
   • Stamps the notes with multi-voice tags (e.g., UNISON_TB, UNISON_SA).
          │
          ▼
4. VoxSight Mobile Application (THE DISPLAY & FOCUS UPGRADE)
   • Renders unison notes with a distinctive blended color (e.g., Teal for T+B, Orchid for S+A).
   • Visual Focus logic: If Bass is focused, UNISON_TB notes stay 100% visible (not dimmed).
   • If Tenor is focused, UNISON_TB notes also stay 100% visible (not dimmed).
   • Pitch Tracking & Audio: Both Tenor and Bass singers receive evaluation on unison notes.
```

---

## 5. Preliminary Recommendations for Your Review

### Recommendation 1: Introduce Unison Voice Types in the Data Model
Expand our voice classification to recognize shared parts:
- `SOPRANO` (`"S"`)
- `ALTO` (`"A"`)
- `TENOR` (`"T"`)
- `BASS` (`"B"`)
- **`UNISON_TB`** (Tenor & Bass shared note)
- **`UNISON_SA`** (Soprano & Alto shared note)
- **`UNISON_ALL`** (Full choir unison)

### Recommendation 2: Distinct Visual Palette for Unison Notes
Instead of forcing a unison note to look purely like Tenor (Blue) or Bass (Green), give it a distinct blended visual identity:
- **Tenor + Bass Unison (`UNISON_TB`)**: **Teal / Emerald (`#00897B`)** (the natural visual blend of Tenor Blue `#2196F3` and Bass Green `#4CAF50`).
- **Soprano + Alto Unison (`UNISON_SA`)**: **Radiant Orchid / Magenta (`#C2185B`)** (the natural visual blend of Soprano Pink `#E91E63` and Alto Purple `#9C27B0`).
- **Full Choir Unison (`UNISON_ALL`)**: **Amber Gold (`#FFB300`)**.

### Recommendation 3: Smart Visual Focus Filtering
Change the filter check in `renderer.html` from `if (focus !== notePart)` to a **multi-part check**:
- If user selects **Bass**: Show Bass notes (`"B"`) AND Unison Tenor/Bass notes (`"UNISON_TB"` / `"TB"`) at 100% opacity.
- If user selects **Tenor**: Show Tenor notes (`"T"`) AND Unison Tenor/Bass notes (`"UNISON_TB"` / `"TB"`) at 100% opacity.
- Dim only the unrelated parts (e.g., Soprano and Alto).

### Recommendation 4: Two-Layer Detection (Rule-Based + Music21 Context)
In `satb_analyzer.py`, implement a **LilyPond-style co-location check**:
1. If two notes in the same measure share the exact same start tick and pitch on the lower staff $\rightarrow$ tag as `UNISON_TB`.
2. If notes have opposite stems on the same notehead (Stem Up + Stem Down) $\rightarrow$ tag as `UNISON_TB` (or `UNISON_SA`).
3. If a note is marked with text *"unis."* or *"a2"* $\rightarrow$ tag as Unison.

---

## 6. Your Feedback & Comments Area

Please use this section (or respond in chat) with your thoughts on the following points:

1. **Unison Colors:** Do you like the blended colors (Teal for Tenor+Bass, Orchid for Soprano+Alto), or would you prefer a different visual treatment (such as a dual-color border or dual-color glowing aura)?
2. **Audiveris Confirmation:** Are you aligned with keeping Audiveris as our image-to-MusicXML scanner and focusing our upgrades on `satb_analyzer.py` and the mobile frontend?
3. **Detection Scope:** In your sheet music, are the unison notes mostly written with double stems (one up, one down), two noteheads at the same pitch, or single notes marked "unis."?
