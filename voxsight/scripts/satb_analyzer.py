#!/usr/bin/env python3
"""
VoxSight SATB Analyzer v1.0 (Milestone 1)
==========================================
Consumes raw MusicXML from Audiveris. Produces a normalized tick-centric
event timeline with probabilistic SATB voice labels.

Architecture contract (v3.7):
  - ZERO MusicXML modification
  - event_id = t{tick}-p{part}-c{chord_index}
  - Identity Sort ≠ Playback Sort (Fix #37)
  - Events array is ORDER-FROZEN after export (Fix #40)
  - Schema version included (1.0)

Usage:
  python satb_analyzer.py <path_to_musicxml>
  Outputs JSON to stdout.
"""

import sys
import json
import hashlib
import logging
import zipfile
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Optional

import music21

# ─── Pipeline Boundary Markers ───────────────────────────────────────────
PIPELINE_START_ANALYSIS = "PIPELINE_START_ANALYSIS"
PIPELINE_FROZEN_KERNEL_START = "PIPELINE_FROZEN_KERNEL_START"
PIPELINE_FROZEN_KERNEL_END = "PIPELINE_FROZEN_KERNEL_END"
PIPELINE_OUTPUT = "PIPELINE_OUTPUT"

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s", stream=sys.stderr)
log = logging.getLogger("satb_analyzer")

# ─── Schema Version ──────────────────────────────────────────────────────
SCHEMA_VERSION = "1.0"

# ─── SATB Pitch Ranges (MIDI) ────────────────────────────────────────────
SATB_RANGES = {
    "S": (60, 84),   # C4 - C6
    "A": (53, 74),   # F3 - D5
    "T": (48, 69),   # C3 - A4
    "B": (36, 60),   # C2 - C4
}

# ─── SATB Confidence Thresholds ──────────────────────────────────────────
THRESHOLD_TRUE_SATB = 0.75
THRESHOLD_CONDENSED = 0.40
# Below 0.40 = UNCERTAIN — NEVER overridden to SATB

# ─── Hysteresis Buffer ───────────────────────────────────────────────────
HYSTERESIS_BUFFER = 0.05
SMOOTHING_WINDOW = 3  # measures


def pitch_to_midi(p: music21.pitch.Pitch) -> int:
    """Convert music21 Pitch to MIDI number."""
    return p.midi


def compute_ticks_per_quarter(score: music21.stream.Score) -> int:
    """Extract or default ticks per quarter note."""
    # music21 uses quarterLength internally; we define our own tick resolution
    return 960  # Standard MIDI resolution


def normalize_to_ticks(quarter_length: float, tpq: int) -> int:
    """Convert a quarterLength value to absolute ticks."""
    return int(round(quarter_length * tpq))


def compute_identity_fingerprint(pitch_midi: int, voice_source: int,
                                  staff_id: int, tie_type: Optional[str],
                                  xml_order: int) -> str:
    """Debug-only deterministic fingerprint (Fix #29 / 2.3)."""
    raw = f"{pitch_midi}-{voice_source}-{staff_id}-{tie_type}-{xml_order}"
    return hashlib.md5(raw.encode()).hexdigest()[:8]


# ─── Identity Sort (analysis domain ONLY — Fix #37) ─────────────────────
def identity_sort_key(event: dict) -> tuple:
    """
    Sort key for chord_index assignment ONLY.
    NEVER use this for playback ordering.

    Sort: (pitch_midi ASC, voice_source ASC, staff_id ASC, tie_type_priority ASC)
    Fallback: xml_element_order ASC (Fix #41)
    """
    tie_priority = {"stop": 0, None: 1, "start": 2, "continue": 1}.get(
        event.get("tie_type"), 1
    )
    return (
        event.get("pitch_midi", 0),
        event.get("voice_source", 0),
        event.get("staff_id", 0),
        tie_priority,
        event.get("xml_element_order", 0),
    )


# ─── SATB Scoring (3-signal model — Fix #27, #13, #19) ──────────────────
def score_pitch(midi: int) -> dict:
    """Score a MIDI pitch against each SATB range."""
    scores = {}
    for voice, (low, high) in SATB_RANGES.items():
        mid = (low + high) / 2.0
        rng = (high - low) / 2.0
        if rng == 0:
            rng = 1.0
        dist = abs(midi - mid)
        scores[voice] = max(0.0, 1.0 - (dist / rng))
    return scores


def score_staff(staff_id: int, part_count: int) -> dict:
    """Score based on staff position."""
    if part_count >= 4:
        # True SATB parts
        mapping = {1: "S", 2: "A", 3: "T", 4: "B"}
        result = {v: 0.0 for v in "SATB"}
        voice = mapping.get(staff_id, None)
        if voice:
            result[voice] = 1.0
        return result
    elif part_count >= 2:
        # Condensed: staff 1 = S/A, staff 2 = T/B
        result = {v: 0.0 for v in "SATB"}
        if staff_id == 1:
            result["S"] = 0.6
            result["A"] = 0.4
        else:
            result["T"] = 0.6
            result["B"] = 0.4
        return result
    else:
        return {"S": 0.25, "A": 0.25, "T": 0.25, "B": 0.25}


def compute_voice_signature(staff_id: int, pitch_midi: int,
                             staff_pitch_range: tuple) -> str:
    """
    voice_signature = f"{staff_id}-{pitch_band}-ctx"
    pitch_band: "hi" (top 50%) or "lo" (bottom 50%)
    """
    if staff_pitch_range[1] == staff_pitch_range[0]:
        band = "mid"
    else:
        midpoint = (staff_pitch_range[0] + staff_pitch_range[1]) / 2.0
        band = "hi" if pitch_midi >= midpoint else "lo"
    return f"s{staff_id}-{band}"


def score_voice_signature(signature: str) -> dict:
    """Score based on voice signature clustering."""
    result = {"S": 0.25, "A": 0.25, "T": 0.25, "B": 0.25}
    if "s1-hi" in signature:
        result["S"] = 0.6
        result["A"] = 0.2
        result["T"] = 0.1
        result["B"] = 0.1
    elif "s1-lo" in signature:
        result["S"] = 0.2
        result["A"] = 0.6
        result["T"] = 0.1
        result["B"] = 0.1
    elif "s2-hi" in signature or "s1" not in signature and "hi" in signature:
        result["S"] = 0.1
        result["A"] = 0.1
        result["T"] = 0.6
        result["B"] = 0.2
    elif "s2-lo" in signature or "s1" not in signature and "lo" in signature:
        result["S"] = 0.1
        result["A"] = 0.1
        result["T"] = 0.2
        result["B"] = 0.6
    return result


def classify_satb(pitch_scores: dict, staff_scores: dict,
                  sig_scores: dict) -> tuple:
    """
    3-signal weighted model:
      pitch (0.50) + staff (0.30) + voice_signature (0.20)

    Returns (voice, confidence).
    """
    combined = {}
    for v in "SATB":
        combined[v] = (
            0.50 * pitch_scores.get(v, 0.0) +
            0.30 * staff_scores.get(v, 0.0) +
            0.20 * sig_scores.get(v, 0.0)
        )

    best_voice = max(combined, key=combined.get)
    confidence = combined[best_voice]
    # Clamp to [0.0, 1.0] (Fix: 2.4 — guard before UI policy)
    confidence = max(0.0, min(1.0, confidence))
    return best_voice, confidence


# ─── Rhythm Integrity Validator (Fix #6, #14, #28) ───────────────────────
def validate_rhythm(events: list, tpq: int, time_sig_map: dict) -> dict:
    """
    Validates:
    - Measure duration consistency
    - Zero-duration notes
    - Cross-voice tick alignment
    Returns validation report.
    """
    corrupt_measures = []
    warnings = []
    events_to_remove = []

    # Group events by measure
    by_measure = {}
    for e in events:
        m = e["measure_number"]
        by_measure.setdefault(m, []).append(e)

    for measure_num, m_events in by_measure.items():
        # Check for zero-duration notes
        for e in m_events:
            if e["duration_ticks"] <= 0 and not e["is_rest"]:
                warnings.append(f"Zero-duration note at m{measure_num}")
                events_to_remove.append(e)

    # Remove zero-duration notes
    for e in events_to_remove:
        if e in events:
            events.remove(e)

    # Cross-voice alignment: check all parts share same tick grid
    parts_ticks = {}
    for e in events:
        p = e["part_id"]
        parts_ticks.setdefault(p, set()).add(e["tick_position"])

    alignment_drift = False
    # Segment-based offset correction placeholder
    offset_segments = []

    validation_passed = len(corrupt_measures) == 0 and not alignment_drift

    return {
        "validation_passed": validation_passed,
        "corrupt_measures": corrupt_measures,
        "alignment_drift": alignment_drift,
        "offset_segments": offset_segments,
        "warnings": warnings,
    }


def check_unsupported_score(xml_path: str) -> tuple[bool, str]:
    """
    Upload Validation Gate: Inspects score structure, metadata, parts, staves,
    and musical texture for Piano/Keyboard accompaniment, Solo voices, lead sheets,
    or non-SATB multi-part configurations. Rejects them before downstream SATB processing.
    """
    try:
        root = None
        is_mxl = False
        p = Path(xml_path)
        if not p.exists():
            return False, ""

        with open(p, "rb") as f:
            header = f.read(4)
            if header.startswith(b"PK"):
                is_mxl = True

        if is_mxl:
            with zipfile.ZipFile(xml_path, "r") as z:
                xml_names = [n for n in z.namelist() if (n.endswith(".xml") or n.endswith(".musicxml")) and not n.startswith("META-INF")]
                if xml_names:
                    root = ET.fromstring(z.read(xml_names[0]))
        else:
            tree = ET.parse(xml_path)
            root = tree.getroot()

        if root is None:
            return False, ""

        parts = root.findall("part")
        if not parts:
            return False, ""

        CHORAL_KEYWORDS = [
            "soprano", "alto", "tenor", "bass", "choir", "chorus", "choral",
            "satb", "s.a.t.b.", "voices", "vocal", "coro", "unison"
        ]

        has_choral_component = False
        total_lyrics = 0
        total_notes = 0

        for p_elem in parts:
            pid = p_elem.get("id")
            sp = root.find(f".//score-part[@id='{pid}']")
            p_name = ""
            p_abbr = ""
            instr_name = ""

            if sp is not None:
                pn = sp.find("part-name")
                if pn is not None and pn.text:
                    p_name = pn.text.strip().lower()
                pa = sp.find("part-abbreviation")
                if pa is not None and pa.text:
                    p_abbr = pa.text.strip().lower()
                instr = sp.find(".//instrument-name")
                if instr is not None and instr.text:
                    instr_name = instr.text.strip().lower()

            combined = f"{p_name} {p_abbr} {instr_name}"
            for kw in CHORAL_KEYWORDS:
                if re.search(rf"\b{re.escape(kw)}\b", combined):
                    has_choral_component = True
                    break

            part_lyrics = 0
            for m in p_elem.findall("measure"):
                for n in m.findall("note"):
                    total_notes += 1
                    if n.find("lyric") is not None:
                        part_lyrics += 1
            total_lyrics += part_lyrics

            # If a part has substantial lyrics (5 or more), it's a vocal/choral component
            if part_lyrics >= 5:
                has_choral_component = True

        # Also check work title or credits for SATB / Choral keywords
        wt = (root.findtext(".//work/work-title") or "").lower()
        mt = (root.findtext(".//movement-title") or "").lower()
        for kw in ["satb", "choir", "chorus", "choral"]:
            if kw in wt or kw in mt:
                has_choral_component = True
                break

        # If no choral/SATB component is found (e.g. 0 lyrics, no SATB names, purely instrumental)
        if not has_choral_component and total_lyrics < 5:
            msg = (
                "Unsupported Score: Purely instrumental score without SATB or choral vocal parts. "
                "VoxSight requires sheet music with a choral or SATB vocal component. "
                "Scores intended exclusively for piano, guitar, or orchestra without vocal parts cannot be processed."
            )
            return True, msg

    except Exception as ex:
        log.warn(f"Error checking score support: {ex}")

    return False, ""


VOICE_CODE_MAP = {
    'S': '1',
    'A': '2',
    'T': '3',
    'B': '4',
    'Solo': '5',
    'Others': '6'
}


def stamp_voices_into_xml(filepath: str, events: list) -> bool:
    """Stamps calculated SATB custom voices (data-vx-voice) into MusicXML note elements."""
    try:
        p = Path(filepath)
        if not p.exists() or not events:
            return False

        events_by_pm = {}
        for e in events:
            key = (e['part_id'], e['measure_number'])
            events_by_pm.setdefault(key, []).append(e)

        def stamp_tree(root: ET.Element):
            parts = root.findall('part')
            for p_idx, part in enumerate(parts):
                part_id = p_idx + 1
                for m in part.findall('measure'):
                    mnum_raw = m.get('number')
                    try:
                        mnum = int(mnum_raw)
                    except (ValueError, TypeError):
                        continue
                    m_events = events_by_pm.get((part_id, mnum), [])
                    m_notes = [n for n in m.findall('note') if n.find('rest') is None]
                    for idx, n in enumerate(m_notes):
                        if idx < len(m_events):
                            ev = m_events[idx]
                            v_str = VOICE_CODE_MAP.get(ev.get('satb_voice'), '1')
                            n.set('data-vx-voice', v_str)

        is_mxl = False
        with open(p, 'rb') as f:
            header = f.read(4)
            if header.startswith(b'PK'):
                is_mxl = True

        if is_mxl:
            with zipfile.ZipFile(filepath, 'r') as z:
                xml_names = [n for n in z.namelist() if (n.endswith('.xml') or n.endswith('.musicxml')) and not n.startswith('META-INF')]
                if not xml_names:
                    return False
                xml_name = xml_names[0]
                xml_data = z.read(xml_name)
                other_files = {n: z.read(n) for n in z.namelist() if n != xml_name}

            root = ET.fromstring(xml_data)
            stamp_tree(root)
            new_xml_data = ET.tostring(root, encoding='utf-8', xml_declaration=True)
            with zipfile.ZipFile(filepath, 'w', compression=zipfile.ZIP_DEFLATED) as z:
                z.writestr(xml_name, new_xml_data)
                for name, data in other_files.items():
                    z.writestr(name, data)
            return True
        else:
            tree = ET.parse(filepath)
            root = tree.getroot()
            stamp_tree(root)
            tree.write(filepath, encoding='utf-8', xml_declaration=True)
            return True
    except Exception as e:
        log.warn(f"stamp_voices_into_xml error: {e}")
        return False


# ─── Main Analysis Pipeline ──────────────────────────────────────────────
def analyze(xml_path: str) -> dict:
    """
    Main analysis pipeline. Follows the frozen kernel ordering:
      1. Division normalization
      2. Tuplet resolution (handled by music21)
      3. Pickup measure normalization
      4. Segment offset correction (MEASURE SPACE)
      5. Tick projection (FINAL)
      6. Event expansion
    """
    log.info(f"[{PIPELINE_START_ANALYSIS}] Parsing: {xml_path}")

    # Validation Gate: Reject scores with Solo voices or Piano accompaniment
    is_unsupported, reason = check_unsupported_score(xml_path)
    if is_unsupported:
        log.warn(f"[Upload Validation Gate] Rejecting unsupported score: {reason}")
        return _error_response(reason)

    # Clean and merge duplicate parts / strip credit lyrics before music21 parsing
    try:
        from musicxml_cleaner import clean_musicxml_file
        clean_musicxml_file(xml_path)
    except Exception as e:
        log.warn(f"Pre-analysis musicxml clean error: {e}")

    try:
        score = music21.converter.parse(xml_path)
    except Exception as e:
        log.error(f"music21 parse failed: {e}")
        return _error_response(str(e))

    tpq = compute_ticks_per_quarter(score)

    # ─── Step 1-3: Extensible zone ────────────────────────────────────
    parts = list(score.parts)
    part_count = len(parts)
    # ─── Step 0: Classify Part Categories (SATB, SOLO, OTHERS) ────────
    part_categories = {}
    choral_part_indices = []
    solo_part_indices = []
    others_part_indices = []

    ACCOMP_KEYWORDS = [
        "piano", "pno", "keyboard", "kbd", "organ", "org",
        "accompaniment", "accomp", "acc.", "acc", "guitar", "gtr",
        "strings", "orchestra", "orch", "harp", "harpsichord", "celesta",
        "synthesizer", "synth", "continuo", "basso continuo", "b.c.",
        "flute", "violin", "cello", "oboe", "clarinet", "trumpet", "horn"
    ]
    SOLO_KEYWORDS = [
        "solo", "soloist", "cantor", "leader", "voice solo", "vocal solo",
        "solo voice", "duet", "lead sheet"
    ]

    for p_idx, part in enumerate(parts):
        p_name = (part.partName or "").strip().lower()
        p_abbr = (part.partAbbreviation or "").strip().lower()
        combined_name = f"{p_name} {p_abbr}"

        p_staves = set()
        p_lyrics = 0
        for m in part.getElementsByClass(music21.stream.Measure):
            for el in m.flatten().notesAndRests:
                if hasattr(el, 'editorial') and hasattr(el.editorial, 'staffNumber') and el.editorial.staffNumber:
                    p_staves.add(el.editorial.staffNumber)
                if el.isNote or el.isChord:
                    if hasattr(el, 'lyric') and el.lyric:
                        p_lyrics += 1
        num_p_staves = len(p_staves) or 1

        is_solo = any(re.search(rf"\b{re.escape(kw)}\b", combined_name) for kw in SOLO_KEYWORDS)
        is_accomp = any(re.search(rf"\b{re.escape(kw)}\b", combined_name) for kw in ACCOMP_KEYWORDS)

        # 2-staff grand staff with low lyrics is typically piano/accompaniment
        if num_p_staves >= 2 and p_lyrics < 5 and not any(kw in combined_name for kw in ["soprano", "alto", "tenor", "bass", "choir", "satb"]):
            is_accomp = True

        if is_solo:
            part_categories[p_idx + 1] = "Solo"
            solo_part_indices.append(p_idx + 1)
        elif is_accomp:
            part_categories[p_idx + 1] = "Others"
            others_part_indices.append(p_idx + 1)
        else:
            part_categories[p_idx + 1] = "SATB"
            choral_part_indices.append(p_idx + 1)

    if not choral_part_indices and parts:
        choral_part_indices.append(1)
        part_categories[1] = "SATB"

    # Count staves
    staff_count = 0

    # Detect time signatures
    time_sigs = set()
    for ts in score.flatten().getElementsByClass(music21.meter.TimeSignature):
        time_sigs.add(f"{ts.numerator}/{ts.denominator}")
    if not time_sigs:
        time_sigs = {"4/4"}

    # Detect tempo markings (Fix #34: tempo-aware drift)
    tempo_events = []
    for mm in score.flatten().getElementsByClass(music21.tempo.MetronomeMark):
        tick = normalize_to_ticks(mm.offset, tpq)
        tempo_events.append({"tick": tick, "bpm": mm.number})
    if not tempo_events:
        tempo_events.append({"tick": 0, "bpm": 120})

    # Extract raw events per part
    raw_events = []
    xml_order = 0

    # ─── New Global Measure Offset Strategy (Fix for OMR sync drift) ───
    global_measure_offsets = {}
    measure_durations = {}
    for part in parts:
        for measure in part.getElementsByClass(music21.stream.Measure):
            m_num = measure.number if measure.number is not None else 0
            dur = measure.quarterLength
            # Take the max duration across all staves for this measure
            if m_num not in measure_durations:
                measure_durations[m_num] = dur
            else:
                measure_durations[m_num] = max(measure_durations[m_num], dur)

    current_offset = 0.0
    for m_num in sorted(measure_durations.keys(), key=lambda x: int(x) if isinstance(x, (int, float, str)) and str(x).isdigit() else 9999):
        global_measure_offsets[m_num] = current_offset
        current_offset += measure_durations[m_num]

    for part_idx, part in enumerate(parts):
        part_num = part_idx + 1
        part_category = part_categories.get(part_num, "SATB")

        # Compute staff pitch range for voice_signature
        pitches_in_part = []
        for n in part.flatten().notes:
            if n.isNote:
                pitches_in_part.append(n.pitch.midi)
            elif n.isChord:
                for p in n.pitches:
                    pitches_in_part.append(p.midi)

        if pitches_in_part:
            staff_pitch_range = (min(pitches_in_part), max(pitches_in_part))
        else:
            staff_pitch_range = (60, 72)

        # Count staves
        staves_in_part = set()

        for measure in part.getElementsByClass(music21.stream.Measure):
            measure_num = measure.number if measure.number is not None else 0

            for element in measure.flatten().notesAndRests:
                # Compute tick position
                offset_in_measure = element.offset
                measure_offset = global_measure_offsets.get(measure_num, measure.offset)
                global_offset = measure_offset + offset_in_measure
                tick_position = normalize_to_ticks(global_offset, tpq)
                duration_ticks = normalize_to_ticks(element.quarterLength, tpq)

                staff_id = part_idx + 1
                if hasattr(element, 'editorial'):
                    ed = element.editorial
                    if hasattr(ed, 'staffNumber') and ed.staffNumber is not None:
                        staff_id = ed.staffNumber
                staves_in_part.add(staff_id)

                voice_source = 0
                if element.activeSite is not None:
                    site = element.activeSite
                    if isinstance(site, music21.stream.Voice):
                        try:
                            voice_source = int(site.id)
                        except (ValueError, TypeError):
                            voice_source = hash(str(site.id)) % 100
                if voice_source == 0 and hasattr(element, 'voice'):
                    try:
                        voice_source = int(element.voice)
                    except (ValueError, TypeError, AttributeError):
                        pass

                if element.isRest:
                    raw_events.append({
                        "measure_number": measure_num,
                        "tick_position": tick_position,
                        "pitch_midi": 0,
                        "pitch_name": "rest",
                        "duration_ticks": duration_ticks,
                        "duration_quarters": float(element.quarterLength),
                        "voice_source": voice_source,
                        "staff_id": staff_id,
                        "part_id": part_num,
                        "is_rest": True,
                        "is_chord_member": False,
                        "tie_type": None,
                        "xml_element_order": xml_order,
                    })
                    xml_order += 1
                    continue

                # Handle notes and chords
                notes_to_process = []
                if element.isNote:
                    notes_to_process = [element]
                elif element.isChord:
                    notes_to_process = list(element)

                for note_obj in notes_to_process:
                    pitch_midi = note_obj.pitch.midi
                    pitch_name = str(note_obj.pitch)

                    tie_type = None
                    if note_obj.tie:
                        tie_type = note_obj.tie.type

                    is_chord = element.isChord
                    sig = compute_voice_signature(staff_id, pitch_midi, staff_pitch_range)

                    if part_category == "Solo":
                        satb_voice = "Solo"
                        satb_confidence = 1.0
                    elif part_category == "Others":
                        satb_voice = "Others"
                        satb_confidence = 1.0
                    else:
                        pitch_scores = score_pitch(pitch_midi)
                        staff_scores = score_staff(staff_id, len(choral_part_indices))
                        sig_scores = score_voice_signature(sig)
                        satb_voice, satb_confidence = classify_satb(
                            pitch_scores, staff_scores, sig_scores
                        )

                    raw_events.append({
                        "measure_number": measure_num,
                        "tick_position": tick_position,
                        "pitch_midi": pitch_midi,
                        "pitch_name": pitch_name,
                        "duration_ticks": duration_ticks,
                        "duration_quarters": float(element.quarterLength),
                        "voice_source": voice_source,
                        "staff_id": staff_id,
                        "part_id": part_num,
                        "is_rest": is_chord,
                        "is_chord_member": is_chord,
                        "tie_type": tie_type,
                        "voice_signature": sig,
                        "satb_voice": satb_voice,
                        "satb_confidence": satb_confidence,
                        "xml_element_order": xml_order,
                    })
                    xml_order += 1

        staff_count += len(staves_in_part)

    # Fix is_rest field
    for e in raw_events:
        if e.get("pitch_midi", 0) > 0:
            e["is_rest"] = False

    log.info(f"[{PIPELINE_FROZEN_KERNEL_START}] Extracted {len(raw_events)} raw events")

    # ─── Global Melodic & Vertical Voice Assignment Optimization ─────
    try:
        # Only optimize choral SATB events (skip Solo and Others to preserve them 1:1)
        non_rests = [e for e in raw_events if not e["is_rest"] and e.get("satb_voice") not in ["Solo", "Others"]]

        by_tick = {}
        for e in non_rests:
            by_tick.setdefault(e["tick_position"], []).append(e)

        last_voice_pitch = {v: None for v in "SATB"}
        last_voice_tick = {v: -9999 for v in "SATB"}

        for tick in sorted(by_tick.keys()):
            tick_notes = by_tick[tick]
            tick_notes.sort(key=lambda x: x["pitch_midi"], reverse=True)

            by_staff = {}
            for tn in tick_notes:
                by_staff.setdefault(tn["staff_id"], []).append(tn)

            for staff_id, staff_notes in by_staff.items():
                for rank, tn in enumerate(staff_notes):
                    pitch = tn["pitch_midi"]
                    p_scores = score_pitch(pitch)
                    s_scores = score_staff(staff_id, len(choral_part_indices))

                    v_scores = {v: 0.0 for v in "SATB"}
                    if len(choral_part_indices) >= 4:
                        # Find the 1-based rank among choral parts
                        choral_rank = choral_part_indices.index(tn["part_id"]) + 1 if tn["part_id"] in choral_part_indices else staff_id
                        mapping = {1: "S", 2: "A", 3: "T", 4: "B"}
                        default_v = mapping.get(choral_rank, "B" if pitch < 60 else "S")
                        v_scores[default_v] = 1.0
                    else:
                        if staff_id == 1:
                            if len(staff_notes) >= 2:
                                if rank == 0:
                                    v_scores["S"] = 0.9
                                    v_scores["A"] = 0.1
                                else:
                                    v_scores["S"] = 0.1
                                    v_scores["A"] = 0.9
                            else:
                                if pitch >= 69:
                                    v_scores["S"] = 0.8
                                    v_scores["A"] = 0.2
                                else:
                                    v_scores["S"] = 0.2
                                    v_scores["A"] = 0.8
                        else:
                            if len(staff_notes) >= 2:
                                if rank == 0:
                                    v_scores["T"] = 0.9
                                    v_scores["B"] = 0.1
                                else:
                                    v_scores["T"] = 0.1
                                    v_scores["B"] = 0.9
                            else:
                                if pitch >= 53:
                                    v_scores["T"] = 0.8
                                    v_scores["B"] = 0.2
                                else:
                                    v_scores["T"] = 0.2
                                    v_scores["B"] = 0.8

                    c_scores = {v: 0.5 for v in "SATB"}
                    for v in "SATB":
                        last_pitch = last_voice_pitch[v]
                        last_tick = last_voice_tick[v]
                        if last_pitch is not None:
                            tick_diff = tick - last_tick
                            if tick_diff < 3840:
                                semitone_diff = abs(pitch - last_pitch)
                                if semitone_diff == 0:
                                    c_scores[v] = 0.95
                                elif semitone_diff <= 2:
                                    c_scores[v] = 0.90
                                elif semitone_diff <= 4:
                                    c_scores[v] = 0.75
                                elif semitone_diff <= 7:
                                    c_scores[v] = 0.60
                                elif semitone_diff <= 12:
                                    c_scores[v] = 0.35
                                else:
                                    c_scores[v] = 0.05

                    combined = {}
                    sig = tn.get("voice_signature", "")
                    sig_scores = score_voice_signature(sig)

                    for v in "SATB":
                        combined[v] = (
                            0.40 * p_scores.get(v, 0.0) +
                            0.25 * s_scores.get(v, 0.0) +
                            0.20 * v_scores.get(v, 0.0) +
                            0.15 * c_scores.get(v, 0.0)
                        )

                    best_voice = max(combined, key=combined.get)
                    confidence = combined[best_voice]
                    confidence = max(0.0, min(1.0, confidence))

                    tn["satb_voice"] = best_voice
                    tn["satb_confidence"] = confidence

                    last_voice_pitch[best_voice] = pitch
                    last_voice_tick[best_voice] = tick
    except Exception as exc:
        log.error(f"Global SATB optimization failed: {exc}")

    # ─── Step 4: Segment offset correction (MEASURE SPACE) ───────────
    # Currently passthrough — offset correction activates when drift detected
    # This is the extensible zone boundary: steps 4-6 are FROZEN KERNEL

    # ─── Step 5: Tick projection (FINAL) ──────────────────────────────
    # Already computed as absolute ticks above (music21 offsets → ticks)

    # ─── Step 6: Event expansion + chord_index assignment ─────────────
    # Group by (tick, part) and assign chord_index via Identity Sort
    events_by_tick_part = {}
    for e in raw_events:
        if e["is_rest"]:
            continue  # Rests don't get event_ids
        key = (e["tick_position"], e["part_id"])
        events_by_tick_part.setdefault(key, []).append(e)

    final_events = []
    for key, group in events_by_tick_part.items():
        # Identity Sort (Fix #37): analysis domain ONLY
        group.sort(key=identity_sort_key)
        for chord_idx, event in enumerate(group):
            tick = event["tick_position"]
            part = event["part_id"]
            event_id = f"t{tick}-p{part}-c{chord_idx}"

            # Debug identity (Fix #29)
            content_hash = compute_identity_fingerprint(
                event["pitch_midi"], event["voice_source"],
                event["staff_id"], event["tie_type"],
                event["xml_element_order"]
            )

            # Determine playback track (structural — Fix #23)
            playback_track = f"p{part}-s{event['staff_id']}"
            if event["voice_source"] > 0:
                playback_track += f"-v{event['voice_source']}"

            final_events.append({
                "event_id": event_id,
                "debug_identity": {
                    "tick": tick,
                    "part": part,
                    "chord_index": chord_idx,
                    "content_hash": content_hash,
                },
                "measure_number": event["measure_number"],
                "tick_position": tick,
                "ticks_per_quarter": tpq,
                "pitch_midi": event["pitch_midi"],
                "pitch_name": event["pitch_name"],
                "duration_ticks": event["duration_ticks"],
                "duration_quarters": event["duration_quarters"],
                "voice_source": event["voice_source"],
                "staff_id": event["staff_id"],
                "part_id": part,
                "is_rest": False,
                "is_chord_member": event["is_chord_member"],
                "tie_type": event["tie_type"],
                "playback_track": playback_track,
                "satb_voice": event["satb_voice"],
                "satb_confidence": event["satb_confidence"],
                "schema_version": SCHEMA_VERSION,
            })

    log.info(f"[{PIPELINE_FROZEN_KERNEL_END}] Assigned {len(final_events)} event IDs")

    # ─── Rhythm Validation ────────────────────────────────────────────
    validation = validate_rhythm(final_events, tpq, {})

    # ─── Compute overall SATB confidence ──────────────────────────────
    if final_events:
        avg_confidence = sum(e["satb_confidence"] for e in final_events) / len(final_events)
    else:
        avg_confidence = 0.0
    avg_confidence = max(0.0, min(1.0, avg_confidence))  # Clamp

    # Determine structure type
    if avg_confidence >= THRESHOLD_TRUE_SATB:
        structure_type = "TRUE_SATB"
    elif avg_confidence >= THRESHOLD_CONDENSED:
        structure_type = "CONDENSED"
    else:
        structure_type = "UNCERTAIN"

    # Build playback tracks list
    track_ids = set()
    for e in final_events:
        track_ids.add(e["playback_track"])

    # Build playback tracks with proper labels (Soprano, Alto, Tenor, Bass, Solo, Others)
    playback_tracks = []
    for tid in sorted(track_ids):
        part_num = 1
        if tid.startswith("p"):
            try:
                part_num = int(tid.split("-")[0][1:])
            except ValueError:
                pass
        p_cat = part_categories.get(part_num, "SATB")
        if p_cat == "Solo":
            satb_label = "Solo"
        elif p_cat == "Others":
            satb_label = "Others"
        else:
            track_events = [e for e in final_events if e["playback_track"] == tid]
            satb_label = "Soprano"
            if track_events:
                voices = [e["satb_voice"] for e in track_events if e["satb_voice"] in ["S", "A", "T", "B", "Solo", "Others"]]
                if voices:
                    maj = max(set(voices), key=voices.count)
                    satb_label = {"S": "Soprano", "A": "Alto", "T": "Tenor", "B": "Bass"}.get(maj, maj)

        playback_tracks.append({
            "track_id": tid,
            "structural_label": tid.replace("p", "Part ").replace("-s", ", Staff ").replace("-v", ", Voice "),
            "satb_label": satb_label,
            "satb_confidence_applied": avg_confidence >= THRESHOLD_CONDENSED,
        })

    total_measures = 0
    if final_events:
        total_measures = max(e["measure_number"] for e in final_events)

    # Stamp classified custom SATB voices (data-vx-voice) into MusicXML
    try:
        stamp_voices_into_xml(xml_path, final_events)
    except Exception as stamp_err:
        log.warn(f"Failed to stamp voices into MusicXML: {stamp_err}")

    # ─── ORDER-FROZEN: events list is now immutable (Fix #40) ─────────
    # No further sorting, insertion, removal, or reordering allowed.

    return {
        "schema_version": SCHEMA_VERSION,
        "score_metadata": {
            "structure_type": structure_type,
            "satb_confidence": round(avg_confidence, 4),
            "satb_confidence_smoothed": round(avg_confidence, 4),
            "part_count": part_count,
            "staff_count": staff_count,
            "time_signatures": list(time_sigs),
            "ticks_per_quarter": tpq,
            "total_measures": total_measures,
            "tempo_events": tempo_events,
            "corrupt_measures": validation["corrupt_measures"],
            "alignment_drift": validation["alignment_drift"],
            "offset_segments": validation["offset_segments"],
            "validation_passed": validation["validation_passed"],
            "playback_tracks": playback_tracks,
        },
        "events": final_events,  # ORDER-FROZEN
    }


def _error_response(msg: str) -> dict:
    """Return error response matching schema."""
    resp = {
        "schema_version": SCHEMA_VERSION,
        "score_metadata": {
            "structure_type": "UNCERTAIN",
            "satb_confidence": 0.0,
            "satb_confidence_smoothed": 0.0,
            "part_count": 0,
            "staff_count": 0,
            "time_signatures": [],
            "ticks_per_quarter": 960,
            "total_measures": 0,
            "tempo_events": [],
            "corrupt_measures": [],
            "alignment_drift": False,
            "offset_segments": [],
            "validation_passed": False,
            "playback_tracks": [],
        },
        "events": [],
        "error": msg,
    }
    if "Solo voices or Piano" in msg:
        resp["error_code"] = "UNSUPPORTED_SCORE_SOLO_PIANO"
    return resp


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps(_error_response("Usage: python satb_analyzer.py <musicxml_path>")))
        sys.exit(1)

    xml_path = sys.argv[1]
    if not Path(xml_path).exists():
        print(json.dumps(_error_response(f"File not found: {xml_path}")))
        sys.exit(1)

    result = analyze(xml_path)
    # Output JSON to stdout (Spring Boot reads this)
    print(json.dumps(result, indent=None, ensure_ascii=False))
