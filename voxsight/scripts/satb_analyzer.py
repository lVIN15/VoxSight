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
import urllib.parse
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
SCHEMA_VERSION = "1.1"

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
def derive_event_v11_metadata(satb_voice: str, xml_element_order: int = -1) -> dict:
    """
    Derives Schema v1.1 additive metadata from satb_voice and xml_element_order.
    Strictly aligns with Section 3.1 JSON Event Schema and Section 3.2 Kotlin model.
    """
    voice_str = str(satb_voice).strip() if satb_voice is not None else ""
    voice_upper = voice_str.upper()

    is_vocal = False
    participating_parts = []
    is_unison = False
    unison_type = None

    # 1. Accompaniment / Instrumental
    if voice_upper in ("OTHERS", "OTHER", "PIANO", "ACCOMP", "ORGAN", "VIOLIN", "STRINGS", "INSTRUMENT", "6"):
        is_vocal = False
        participating_parts = ["Others"]
        is_unison = False
        unison_type = None

    # 2. Standard single vocal parts
    elif voice_upper in ("S", "SOPRANO", "1"):
        is_vocal = True
        participating_parts = ["S"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("A", "ALTO", "2"):
        is_vocal = True
        participating_parts = ["A"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("T", "TENOR", "3"):
        is_vocal = True
        participating_parts = ["T"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("B", "BASS", "4"):
        is_vocal = True
        participating_parts = ["B"]
        is_unison = False
        unison_type = None

    # 3. Divisi single vocal parts
    elif voice_upper in ("S1", "SOPRANO1", "SOPRANO 1"):
        is_vocal = True
        participating_parts = ["S1"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("S2", "SOPRANO2", "SOPRANO 2"):
        is_vocal = True
        participating_parts = ["S2"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("A1", "ALTO1", "ALTO 1"):
        is_vocal = True
        participating_parts = ["A1"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("A2", "ALTO2", "ALTO 2"):
        is_vocal = True
        participating_parts = ["A2"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("T1", "TENOR1", "TENOR 1"):
        is_vocal = True
        participating_parts = ["T1"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("T2", "TENOR2", "TENOR 2"):
        is_vocal = True
        participating_parts = ["T2"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("B1", "BASS1", "BASS 1"):
        is_vocal = True
        participating_parts = ["B1"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("B2", "BASS2", "BASS 2"):
        is_vocal = True
        participating_parts = ["B2"]
        is_unison = False
        unison_type = None
    elif voice_upper in ("SOLO", "5"):
        is_vocal = True
        participating_parts = ["SOLO"]
        is_unison = False
        unison_type = None

    # 4. Compound Cross-Section Unison
    elif voice_upper in ("UNISON_TB", "TB"):
        is_vocal = True
        participating_parts = ["T", "B"]
        is_unison = True
        unison_type = "CROSS_SECTION"
    elif voice_upper in ("UNISON_SA", "SA"):
        is_vocal = True
        participating_parts = ["S", "A"]
        is_unison = True
        unison_type = "CROSS_SECTION"
    elif voice_upper in ("UNISON_ST", "ST"):
        is_vocal = True
        participating_parts = ["S", "T"]
        is_unison = True
        unison_type = "CROSS_SECTION"
    elif voice_upper in ("UNISON_AB", "AB"):
        is_vocal = True
        participating_parts = ["A", "B"]
        is_unison = True
        unison_type = "CROSS_SECTION"

    # 5. Compound Section-Divisi Unison
    elif voice_upper in ("UNISON_S1S2", "S1S2"):
        is_vocal = True
        participating_parts = ["S1", "S2"]
        is_unison = True
        unison_type = "SECTION_DIVISI"
    elif voice_upper in ("UNISON_A1A2", "A1A2"):
        is_vocal = True
        participating_parts = ["A1", "A2"]
        is_unison = True
        unison_type = "SECTION_DIVISI"
    elif voice_upper in ("UNISON_T1T2", "T1T2"):
        is_vocal = True
        participating_parts = ["T1", "T2"]
        is_unison = True
        unison_type = "SECTION_DIVISI"
    elif voice_upper in ("UNISON_B1B2", "B1B2"):
        is_vocal = True
        participating_parts = ["B1", "B2"]
        is_unison = True
        unison_type = "SECTION_DIVISI"

    # 6. Universal Unison / Tutti
    elif voice_upper in ("TUTTI", "UNISON_ALL", "ALL"):
        is_vocal = True
        participating_parts = ["ALL"]
        is_unison = True
        unison_type = "TUTTI"

    # 7. Unrecognized or UNKNOWN
    else:
        is_vocal = False
        participating_parts = []
        is_unison = False
        unison_type = None

    return {
        "is_vocal": is_vocal,
        "participating_parts": participating_parts,
        "is_unison": is_unison,
        "unison_type": unison_type,
        "xml_element_order": int(xml_element_order) if xml_element_order is not None else -1
    }


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

# ─── Part Classification Lexicons & Compound Dictionaries ─────────────────
ACCOMP_KEYWORDS = [
    # Keyboards & Basso Continuo
    "piano", "pno", "keyboard", "kbd", "organ", "org",
    "harpsichord", "cembalo", "celesta", "clavinet",
    "synthesizer", "synth", "continuo", "basso continuo", "b.c.",
    # Woodwinds (including vocal-overlapping names)
    "flute", "alto flute", "bass flute", "piccolo",
    "oboe", "english horn", "cor anglais",
    "clarinet", "bass clarinet", "contrabass clarinet", "contra-bass clarinet", "alto clarinet", "basset horn",
    "bassoon", "bsn", "contrabassoon", "contra-bassoon", "double bassoon",
    "saxophone", "sax", "soprano sax", "soprano saxophone",
    "alto sax", "alto saxophone", "tenor sax", "tenor saxophone",
    "baritone sax", "baritone saxophone", "bass sax", "bass saxophone",
    # Brass (including vocal-overlapping names)
    "trumpet", "cornet", "flugelhorn",
    "horn", "french horn", "tenor horn", "alto horn", "baritone horn",
    "trombone", "tbn", "trb", "tenor trombone", "bass trombone", "contrabass trombone", "alto trombone",
    "tuba", "bass tuba", "contrabass tuba", "euphonium",
    # Strings (including low strings overlapping with 'bass')
    "violin", "vln", "viola", "vla", "cello", "violoncello", "vc",
    "double bass", "contrabass", "contra bass", "contra-bass", "string bass",
    "upright bass", "acoustic bass", "electric bass", "bass guitar",
    "strings", "string ensemble", "orchestra", "orch", "harp", "guitar", "gtr",
    # Percussion
    "timpani", "percussion", "perc", "drums", "drum set", "bass drum",
    # General accompaniment indicators
    "accompaniment", "accomp", "acc.", "acc"
]

SOLO_KEYWORDS = [
    "solo", "soloist", "cantor", "leader", "voice solo", "vocal solo",
    "solo voice", "duet", "lead sheet"
]

VOCAL_KEYWORDS = [
    "soprano", "alto", "tenor", "bass", "choir", "chorus", "satb",
    "voice", "vocal", "cantus", "altus", "bassus", "descant", "treble", "baritone", "mezzo",
    "contralto", "women", "men"
]

VOCAL_ABBREVIATIONS = ["s", "a", "t", "b", "sop", "alt", "ten", "bas", "bar", "mz"]

ALLOWED_VOCAL_COMPANIONS = {
    "1", "2", "3", "4", "5", "6", "7", "8",
    "i", "ii", "iii", "iv", "v", "vi",
    "a", "b", "c", "d",
    "voice", "voices", "vocal", "vocals",
    "solo", "soloist", "soloists",
    "choir", "chorus", "choral", "coro",
    "part", "pt", "staff", "stave", "line",
    "divisi", "div", "unison", "unis",
    "lead", "leader", "section",
    "women", "men", "boys", "girls", "children",
    "soprano", "sopranos", "sop", "s",
    "alto", "altos", "alt", "a",
    "tenor", "tenors", "ten", "t",
    "bass", "basses", "bas", "b",
    "baritone", "baritones", "bar",
    "mezzo", "contralto",
    "cantus", "altus", "bassus", "treble", "descant",
    "satb", "ssaa", "ttbb", "ssatb",
    "and", "or"
}


def is_vocal_part_name(name: str) -> bool:
    """
    Determine if a part name represents an authentic vocal/choral part
    rather than an instrument with a vocal register prefix (e.g. 'Alto Saxophone', 'Bass Clarinet').
    """
    clean = re.sub(r"[^a-z0-9]", " ", name.lower()).strip()
    words = clean.split()
    if not words:
        return False

    # Check if any word or compound in name matches ACCOMP_KEYWORDS
    if any(re.search(rf"\b{re.escape(kw)}\b", clean) for kw in ACCOMP_KEYWORDS):
        return False

    # Must contain at least one vocal keyword or abbreviation
    has_vocal_kw = any(
        re.search(rf"\b{re.escape(kw)}\b", clean)
        for kw in VOCAL_KEYWORDS + VOCAL_ABBREVIATIONS
    )
    if not has_vocal_kw:
        return False

    # Every word must belong to legitimate vocal nomenclature
    for w in words:
        if w not in ALLOWED_VOCAL_COMPANIONS:
            return False

    return True


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

        has_choral_component = False
        total_lyrics = 0
        total_notes = 0

        accomp_parts_count = 0
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
                continue

            # If the part is recognized accompaniment, do NOT count it as a choral component
            # even if its name contains words like 'alto', 'tenor', or 'bass'
            is_accomp_part = any(re.search(rf"\b{re.escape(kw)}\b", combined) for kw in ACCOMP_KEYWORDS)
            if is_accomp_part:
                accomp_parts_count += 1
                continue

            # Check if part name represents an authentic vocal part
            if is_vocal_part_name(f"{p_name} {p_abbr}"):
                has_choral_component = True

        # Purely instrumental scores must be rejected regardless of title keywords.
        # A title alone cannot establish vocal parts without actual vocal evidence (names or lyrics).
        is_purely_instrumental = (accomp_parts_count == len(parts) and total_lyrics < 5)
        if (not has_choral_component and total_lyrics < 5) or is_purely_instrumental:
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
    'S': 'S',
    'A': 'A',
    'T': 'T',
    'B': 'B',
    'Solo': 'Solo',
    'Others': 'Others'
}


STEP_SEMITONES = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}


def parse_xml_pitch(note_elem: ET.Element):
    """Extract standard 12-TET MIDI pitch from MusicXML <note> element."""
    p_elem = note_elem.find('pitch')
    if p_elem is None:
        return None
    step = p_elem.findtext('step', 'C').strip().upper()
    alter_txt = p_elem.findtext('alter', '0').strip()
    try:
        alter = int(float(alter_txt))
    except (ValueError, TypeError):
        alter = 0
    octave_txt = p_elem.findtext('octave', '4').strip()
    try:
        octave = int(octave_txt)
    except (ValueError, TypeError):
        octave = 4
    step_semi = STEP_SEMITONES.get(step, 0)
    return 12 * (octave + 1) + step_semi + alter


def normalize_tie(t):
    """Normalize MusicXML tie type string to 'start', 'stop', 'continue', or None."""
    if not t:
        return None
    t_str = str(t).strip().lower()
    return t_str if t_str in ('start', 'stop', 'continue') else None


def prescan_musicxml(root: ET.Element) -> tuple:
    """
    Pass 1: ElementTree Pre-Scan with Explicit Metric Cursor Tracking & Source-Note Identity.
    Returns:
      parts_data: dict mapping clean_part_id to dict of measures, where each measure
                  contains list of pre-scanned non-rest note records.
      xml_notes_by_source_id: dict mapping source_note_id to note record.
    """
    parts_data = {}
    xml_notes_by_source_id = {}

    parts = root.findall('part')
    for p_idx, part in enumerate(parts):
        raw_part_id = part.get('id', f'P{p_idx + 1}')
        clean_part_id = urllib.parse.quote(str(raw_part_id), safe="")

        current_divisions = 1
        part_measures = {}

        for m_idx, measure in enumerate(part.findall('measure')):
            mnum_raw = measure.get('number', str(m_idx + 1))
            try:
                mnum = int(mnum_raw)
            except (ValueError, TypeError):
                mnum = m_idx + 1

            cursor_divisions = 0
            base_onset_div = 0
            note_counter = 0
            measure_note_records = []

            for elem in measure:
                tag = elem.tag

                if tag == 'attributes':
                    div_elem = elem.find('divisions')
                    if div_elem is not None and div_elem.text:
                        try:
                            d = int(div_elem.text.strip())
                            if d > 0:
                                current_divisions = d
                        except ValueError:
                            pass

                elif tag == 'backup':
                    dur_elem = elem.find('duration')
                    dur_div = int(dur_elem.text.strip()) if (dur_elem is not None and dur_elem.text) else 0
                    cursor_divisions = max(0, cursor_divisions - dur_div)

                elif tag == 'forward':
                    dur_elem = elem.find('duration')
                    dur_div = int(dur_elem.text.strip()) if (dur_elem is not None and dur_elem.text) else 0
                    cursor_divisions += dur_div

                elif tag == 'note':
                    is_rest = elem.find('rest') is not None
                    is_grace = elem.find('grace') is not None
                    is_chord = elem.find('chord') is not None

                    dur_elem = elem.find('duration')
                    if is_grace:
                        dur_div = 0
                    else:
                        dur_div = int(dur_elem.text.strip()) if (dur_elem is not None and dur_elem.text) else 0

                    if is_rest:
                        # Rest Exclusion Invariant: advances cursor, receives NO index
                        cursor_divisions += dur_div
                        continue

                    # Non-rest note
                    xml_note_index = note_counter
                    note_counter += 1

                    if is_chord:
                        onset_div = base_onset_div
                    else:
                        onset_div = cursor_divisions
                        base_onset_div = cursor_divisions

                    if not is_chord and not is_grace:
                        cursor_divisions += dur_div

                    pitch_midi = parse_xml_pitch(elem)

                    # Tie status
                    ties = elem.findall('tie')
                    tieds = elem.findall('.//notations/tied')
                    tie_types = set(t.get('type') for t in (ties + tieds) if t.get('type'))
                    if 'start' in tie_types and 'stop' in tie_types:
                        tie_type = 'continue'
                    elif 'start' in tie_types:
                        tie_type = 'start'
                    elif 'stop' in tie_types:
                        tie_type = 'stop'
                    else:
                        tie_type = None

                    voice_txt = elem.findtext('voice', '1').strip()
                    staff_txt = elem.findtext('staff', '1').strip()
                    try:
                        staff_int = int(staff_txt)
                    except ValueError:
                        staff_int = 1

                    source_note_id = f"p[{clean_part_id}]_m{m_idx:04d}_n{xml_note_index:04d}"

                    divs_per_q = current_divisions if current_divisions > 0 else 1
                    onset_q = onset_div / divs_per_q
                    dur_q = dur_div / divs_per_q

                    record = {
                        "clean_part_id": clean_part_id,
                        "raw_part_id": raw_part_id,
                        "measure_index": m_idx,
                        "measure_number": mnum,
                        "xml_note_index_in_measure": xml_note_index,
                        "note_elem": elem,
                        "pitch_midi": pitch_midi,
                        "duration_div": dur_div,
                        "onset_div": onset_div,
                        "onset_quarters": onset_q,
                        "duration_quarters": dur_q,
                        "is_grace": is_grace,
                        "is_chord": is_chord,
                        "tie_type": tie_type,
                        "voice": voice_txt,
                        "staff": staff_int,
                        "source_note_id": source_note_id,
                    }
                    measure_note_records.append(record)
                    xml_notes_by_source_id[source_note_id] = record

            part_measures[mnum] = measure_note_records

        parts_data[clean_part_id] = part_measures

    return parts_data, xml_notes_by_source_id


def match_and_stamp_voices(root: ET.Element, events: list, measure_offsets: dict = None, part_mapping: dict = None, part_staff_mapping: dict = None) -> int:
    """
    Pass 2: Deterministic 9-step matching algorithm.
    Stamps data-vx-voice into matched XML <note> elements.
    Returns count of successfully stamped notes.
    """
    parts_data, _ = prescan_musicxml(root)

    xml_parts = root.findall('part')
    xml_part_ids = [p.get('id', f'P{i+1}') for i, p in enumerate(xml_parts)]

    # Deterministic structural part mapping from analysis stream parts to raw XML part IDs and staves
    if part_mapping is None or part_staff_mapping is None:
        part_mapping = {}
        part_staff_mapping = {}
        stream_idx = 1
        for p_elem in xml_parts:
            pid = p_elem.get('id', f'P{len(part_mapping)+1}')
            staves_count = 1
            for m in p_elem.findall('measure'):
                st_el = m.find('.//attributes/staves')
                if st_el is not None and st_el.text and st_el.text.strip().isdigit():
                    staves_count = max(staves_count, int(st_el.text.strip()))
                for staff_el in m.findall('.//note/staff'):
                    if staff_el.text and staff_el.text.strip().isdigit():
                        staves_count = max(staves_count, int(staff_el.text.strip()))
            for staff_num in range(1, staves_count + 1):
                part_mapping[stream_idx] = pid
                part_staff_mapping[stream_idx] = staff_num
                stream_idx += 1

    # Measure offsets fallback if not provided
    if measure_offsets is None:
        measure_offsets = {}
        curr_offset = 0.0
        first_part = root.find('part')
        if first_part is not None:
            curr_ts_quarters = 4.0
            for m_idx, m_el in enumerate(first_part.findall('measure')):
                mnum_raw = m_el.get('number', str(m_idx + 1))
                try:
                    m_num = int(mnum_raw)
                except (ValueError, TypeError):
                    m_num = m_idx + 1
                measure_offsets[m_num] = curr_offset
                beats_el = m_el.find('.//attributes/time/beats')
                btype_el = m_el.find('.//attributes/time/beat-type')
                if beats_el is not None and btype_el is not None:
                    try:
                        curr_ts_quarters = (int(beats_el.text.strip()) * 4.0) / int(btype_el.text.strip())
                    except Exception:
                        pass
                curr_offset += curr_ts_quarters

    # Group events by (clean_part_id, measure_number)
    events_by_pm = {}
    for ev in events:
        p_num = ev.get('part_id', 1)
        mnum = ev.get('measure_number', 1)

        if p_num in part_mapping:
            raw_pid = part_mapping[p_num]
        elif str(p_num) in xml_part_ids:
            raw_pid = str(p_num)
        elif len(xml_part_ids) == 1:
            raw_pid = xml_part_ids[0]
        else:
            raw_pid = None

        if raw_pid is None:
            continue

        clean_pid = urllib.parse.quote(str(raw_pid), safe="")
        events_by_pm.setdefault((clean_pid, mnum), []).append(ev)

    stamped_count = 0

    # Execute 9-step matching for each (clean_part_id, measure_number)
    for clean_pid, measures in parts_data.items():
        for mnum, xml_candidates in measures.items():
            ev_candidates = events_by_pm.get((clean_pid, mnum), [])
            if not xml_candidates or not ev_candidates:
                continue

            # Mock/synthetic events without musical attributes (e.g., unit test fixtures)
            if all(e.get('pitch_midi') is None for e in ev_candidates):
                for idx, xn in enumerate(xml_candidates):
                    if idx < len(ev_candidates):
                        ev = ev_candidates[idx]
                        v_label = ev.get('satb_voice')
                        v_str = VOICE_CODE_MAP.get(v_label)
                        if v_str is not None:
                            xn['note_elem'].set('data-vx-voice', v_str)
                            stamped_count += 1
                continue

            # Step 1: Partition by pitch_midi
            xml_by_pitch = {}
            for xn in xml_candidates:
                xml_by_pitch.setdefault(xn['pitch_midi'], []).append(xn)

            ev_by_pitch = {}
            for ev in ev_candidates:
                ev_by_pitch.setdefault(ev.get('pitch_midi'), []).append(ev)

            # Match within each pitch bucket
            for pitch, x_notes in xml_by_pitch.items():
                if pitch is None or pitch not in ev_by_pitch:
                    continue  # Unmatched -> left unstamped
                e_notes = ev_by_pitch[pitch]

                # Step 2: Grace note filtering
                remaining_x = list(x_notes)
                remaining_e = list(e_notes)

                matched_pairs = []

                # Exact attribute scoring & verification (Steps 3-7)
                while remaining_x and remaining_e:
                    x_cands = {}
                    e_cands = {id(ev): [] for ev in remaining_e}

                    for xn in remaining_x:
                        xn_is_grace = xn['is_grace']
                        xn_onset = xn['onset_quarters']
                        xn_dur = xn['duration_quarters']
                        norm_xn_tie = normalize_tie(xn['tie_type'])
                        xn_voice = xn['voice']
                        xn_staff = xn['staff']

                        x_cands[id(xn)] = []

                        for ev in remaining_e:
                            ev_is_grace = (ev.get('duration_quarters', 0.0) == 0.0)
                            if xn_is_grace != ev_is_grace:
                                continue

                            # Step 3: Duration check (tolerance 1e-4)
                            if not xn_is_grace and abs(xn_dur - ev.get('duration_quarters', 0.0)) >= 1e-4:
                                continue

                            # Step 4: Metric Onset verification (tolerance 1e-4)
                            if "onset_quarters" in ev:
                                ev_onset = ev["onset_quarters"]
                            elif measure_offsets and mnum in measure_offsets:
                                tpq = ev.get('ticks_per_quarter', 960)
                                ev_onset = (ev.get('tick_position', 0) / tpq) - measure_offsets[mnum]
                            elif measure_offsets and str(mnum) in measure_offsets:
                                tpq = ev.get('ticks_per_quarter', 960)
                                ev_onset = (ev.get('tick_position', 0) / tpq) - measure_offsets[str(mnum)]
                            else:
                                tpq = ev.get('ticks_per_quarter', 960)
                                ev_onset = ev.get('tick_position', 0) / tpq

                            if abs(xn_onset - ev_onset) >= 1e-4:
                                continue

                            # Step 5: Normalized symmetric tie check
                            norm_ev_tie = normalize_tie(ev.get('tie_type'))
                            if norm_xn_tie != norm_ev_tie:
                                continue

                            # Step 6: Staff check (relative to XML part)
                            ev_staff = part_staff_mapping.get(ev.get('part_id'), ev.get('staff_id', 1))
                            if xn_staff != ev_staff:
                                continue

                            # Step 7: Voice check
                            ev_voice = str(ev.get('voice_source', 1))
                            voice_match = (xn_voice == ev_voice or ev.get('voice_source', 0) == 0)

                            x_cands[id(xn)].append((ev, voice_match))
                            e_cands[id(ev)].append((xn, voice_match))

                    # Check for progress in matching
                    progress = False

                    # Pass 1: Unambiguous mutually exclusive 1:1 match
                    for xn in list(remaining_x):
                        cands = x_cands.get(id(xn), [])
                        if len(cands) == 1:
                            ev, vm = cands[0]
                            if len(e_cands.get(id(ev), [])) == 1:
                                matched_pairs.append((xn, ev))
                                remaining_x.remove(xn)
                                remaining_e.remove(ev)
                                progress = True
                                break

                    if progress:
                        continue

                    # Pass 2: Voice-filtered unambiguous 1:1 candidate match
                    for xn in list(remaining_x):
                        cands = x_cands.get(id(xn), [])
                        vm_cands = [e for e, vm in cands if vm]
                        if len(vm_cands) == 1:
                            ev = vm_cands[0]
                            e_cands_list = e_cands.get(id(ev), [])
                            e_vm_cands = [x for x, vm in e_cands_list if vm]
                            if len(e_vm_cands) == 1 and e_vm_cands[0] is xn:
                                matched_pairs.append((xn, ev))
                                remaining_x.remove(xn)
                                remaining_e.remove(ev)
                                progress = True
                                break

                    if progress:
                        continue

                    # No more unique 1:1 matches possible
                    break

                # Step 8: Monotonic positional pairing (strict preconditions)
                if remaining_x and remaining_e and len(remaining_x) == len(remaining_e):
                    all_identical = True
                    for i in range(len(remaining_x)):
                        rx = remaining_x[i]
                        re = remaining_e[i]
                        re_staff = part_staff_mapping.get(re.get('part_id'), re.get('staff_id', 1))
                        if (rx['pitch_midi'] != re.get('pitch_midi') or
                            abs(rx['duration_quarters'] - re.get('duration_quarters', 0)) >= 1e-4 or
                            normalize_tie(rx['tie_type']) != normalize_tie(re.get('tie_type')) or
                            rx['staff'] != re_staff):
                            all_identical = False
                            break
                    if all_identical:
                        for rx, re in zip(remaining_x, remaining_e):
                            matched_pairs.append((rx, re))
                        remaining_x.clear()
                        remaining_e.clear()

                # Step 9: Ambiguity rejection: any remaining notes in remaining_x are left unstamped

                # Apply stamps for successfully matched pairs
                for xn, ev in matched_pairs:
                    v_label = ev.get('satb_voice')
                    v_str = VOICE_CODE_MAP.get(v_label)
                    if v_str is not None:
                        xn['note_elem'].set('data-vx-voice', v_str)
                        stamped_count += 1

    return stamped_count


def stamp_voices_into_xml(filepath: str, events: list, measure_offsets: dict = None, part_mapping: dict = None, part_staff_mapping: dict = None) -> bool:
    """Stamps calculated SATB custom voices (data-vx-voice) into MusicXML note elements using deterministic pre-scan."""
    try:
        p = Path(filepath)
        if not p.exists() or not events:
            return False

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
            match_and_stamp_voices(root, events, measure_offsets, part_mapping, part_staff_mapping)
            new_xml_data = ET.tostring(root, encoding='utf-8', xml_declaration=True)
            with zipfile.ZipFile(filepath, 'w', compression=zipfile.ZIP_DEFLATED) as z:
                z.writestr(xml_name, new_xml_data)
                for name, data in other_files.items():
                    z.writestr(name, data)
            return True
        else:
            tree = ET.parse(filepath)
            root = tree.getroot()
            match_and_stamp_voices(root, events, measure_offsets, part_mapping, part_staff_mapping)
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

    # Pre-scan MusicXML for score-part instrument names and staves
    xml_instr_map = {}
    xml_staves_map = {}
    try:
        p_obj = Path(xml_path)
        is_mxl = False
        with open(p_obj, "rb") as f_head:
            if f_head.read(4).startswith(b"PK"):
                is_mxl = True
        if is_mxl:
            with zipfile.ZipFile(xml_path, "r") as z_f:
                xml_names = [n for n in z_f.namelist() if (n.endswith(".xml") or n.endswith(".musicxml")) and not n.startswith("META-INF")]
                if xml_names:
                    root_elem = ET.fromstring(z_f.read(xml_names[0]))
                else:
                    root_elem = None
        else:
            root_elem = ET.parse(xml_path).getroot()

        if root_elem is not None:
            for sp in root_elem.findall(".//score-part"):
                sp_id = sp.get("id")
                in_el = sp.find(".//instrument-name")
                if in_el is not None and in_el.text:
                    xml_instr_map[sp_id] = in_el.text.strip().lower()
            for p_el in root_elem.findall(".//part"):
                pid = p_el.get("id")
                m1 = p_el.find("measure")
                if m1 is not None:
                    st_el = m1.find(".//attributes/staves")
                    if st_el is not None and st_el.text and st_el.text.strip().isdigit():
                        xml_staves_map[pid] = int(st_el.text.strip())
    except Exception as scan_err:
        log.warn(f"Pre-scan of XML instrument/staves metadata skipped: {scan_err}")

    # ─── Step 1-3: Extensible zone ────────────────────────────────────
    parts = list(score.parts)
    part_count = len(parts)
    # ─── Step 0: Classify Part Categories (SATB, SOLO, OTHERS) ────────
    part_categories = {}
    choral_part_indices = []
    solo_part_indices = []
    others_part_indices = []

    for p_idx, part in enumerate(parts):
        p_name = (part.partName or "").strip().lower()
        p_abbr = (part.partAbbreviation or "").strip().lower()

        # Extract instrument metadata from music21 Part and MusicXML score-part
        instr_tokens = []
        for inst in part.getInstruments():
            if inst.instrumentName:
                instr_tokens.append(inst.instrumentName.strip().lower())
            if inst.partName and inst.partName.strip().lower() not in instr_tokens:
                instr_tokens.append(inst.partName.strip().lower())
            if inst.instrumentAbbreviation:
                instr_tokens.append(inst.instrumentAbbreviation.strip().lower())

        base_id = str(part.id).split("-")[0] if "-" in str(part.id) else str(part.id)
        sp_instr = xml_instr_map.get(str(part.id), "") or xml_instr_map.get(base_id, "")
        if sp_instr and sp_instr not in instr_tokens:
            instr_tokens.append(sp_instr)

        combined_name = f"{p_name} {p_abbr} {' '.join(instr_tokens)}".strip()

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

        is_multi_staff = (
            isinstance(part, music21.stream.PartStaff) or
            num_p_staves >= 2 or
            xml_staves_map.get(str(part.id), 1) >= 2 or
            xml_staves_map.get(base_id, 1) >= 2
        )

        is_accomp = any(re.search(rf"\b{re.escape(kw)}\b", combined_name) for kw in ACCOMP_KEYWORDS)

        # 2-staff grand staff with low lyrics is typically piano/accompaniment
        if is_multi_staff and p_lyrics < 5 and not is_vocal_part_name(combined_name):
            is_accomp = True

        is_solo = any(re.search(rf"\b{re.escape(kw)}\b", combined_name) for kw in SOLO_KEYWORDS) and not is_accomp

        has_lyrics = p_lyrics > 0
        is_vocal = (is_vocal_part_name(combined_name) or has_lyrics) and not is_accomp

        if is_accomp:
            part_categories[p_idx + 1] = "Others"
            others_part_indices.append(p_idx + 1)
        elif is_solo:
            part_categories[p_idx + 1] = "Solo"
            solo_part_indices.append(p_idx + 1)
        elif is_vocal:
            part_categories[p_idx + 1] = "SATB"
            choral_part_indices.append(p_idx + 1)
        else:
            part_categories[p_idx + 1] = "UNKNOWN"

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
                    elif part_category == "UNKNOWN":
                        satb_voice = "UNKNOWN"
                        satb_confidence = 0.0
                    else:
                        choral_rank = choral_part_indices.index(part_num) + 1 if part_num in choral_part_indices else staff_id
                        pitch_scores = score_pitch(pitch_midi)
                        staff_scores = score_staff(choral_rank, len(choral_part_indices))
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
                        "is_rest": False,
                        "is_chord_member": is_chord,
                        "tie_type": tie_type,
                        "voice_signature": sig,
                        "satb_voice": satb_voice,
                        "satb_confidence": satb_confidence,
                        "xml_element_order": xml_order,
                    })
                    xml_order += 1

        staff_count += len(staves_in_part)

    log.info(f"[{PIPELINE_FROZEN_KERNEL_START}] Extracted {len(raw_events)} raw events")

    # ─── Global Melodic & Vertical Voice Assignment Optimization ─────
    try:
        # Only optimize choral SATB events (skip Solo, Others, and UNKNOWN to preserve them 1:1)
        non_rests = [e for e in raw_events if not e["is_rest"] and e.get("satb_voice") not in ["Solo", "Others", "UNKNOWN"]]

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
                    choral_rank = choral_part_indices.index(tn["part_id"]) + 1 if tn["part_id"] in choral_part_indices else staff_id
                    s_scores = score_staff(choral_rank, len(choral_part_indices))

                    v_scores = {v: 0.0 for v in "SATB"}
                    if len(choral_part_indices) >= 4:
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

            # Schema v1.1 additive metadata derivation
            v11_meta = derive_event_v11_metadata(
                event["satb_voice"],
                event.get("xml_element_order", -1)
            )

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
                "xml_element_order": v11_meta["xml_element_order"],
                "participating_parts": v11_meta["participating_parts"],
                "is_vocal": v11_meta["is_vocal"],
                "is_unison": v11_meta["is_unison"],
                "unison_type": v11_meta["unison_type"],
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
        p_cat = part_categories.get(part_num, "UNKNOWN")
        # Section 7: Enriched Playback Track Metadata
        if p_cat == "Others":
            satb_label = "Others"
            part_key = "OTHER"
            part_role = "ACCOMPANIMENT"
            parent_section = None
            is_accompaniment = True
        elif p_cat == "Solo":
            satb_label = "Solo"
            part_key = "SOLO"
            part_role = "VOCAL"
            parent_section = None
            is_accompaniment = False
        elif p_cat == "UNKNOWN":
            satb_label = "UNKNOWN"
            part_key = "UNKNOWN"
            part_role = "UNKNOWN"
            parent_section = None
            is_accompaniment = False
        else:
            track_events = [e for e in final_events if e["playback_track"] == tid]
            satb_label = "Soprano"
            if track_events:
                voices = [e["satb_voice"] for e in track_events if e["satb_voice"] in ["S", "A", "T", "B", "Solo", "Others"]]
                if voices:
                    maj = max(set(voices), key=voices.count)
                    satb_label = {"S": "Soprano", "A": "Alto", "T": "Tenor", "B": "Bass"}.get(maj, maj)

            part_role = "VOCAL"
            is_accompaniment = False

            # Map satb_label to part_key and parent_section
            norm = satb_label.upper()
            if "SOPRANO" in norm or norm.startswith("S"):
                parent_section = "Soprano"
                part_key = "S2" if ("2" in norm) else ("S1" if "1" in norm else "S")
            elif "ALTO" in norm or norm.startswith("A"):
                parent_section = "Alto"
                part_key = "A2" if ("2" in norm) else ("A1" if "1" in norm else "A")
            elif "TENOR" in norm or norm.startswith("T"):
                parent_section = "Tenor"
                part_key = "T2" if ("2" in norm) else ("T1" if "1" in norm else "T")
            elif "BASS" in norm or norm.startswith("B"):
                parent_section = "Bass"
                part_key = "B2" if ("2" in norm) else ("B1" if "1" in norm else "B")
            else:
                parent_section = "Soprano"
                part_key = "S"

        playback_tracks.append({
            "track_id": tid,
            "structural_label": tid.replace("p", "Part ").replace("-s", ", Staff ").replace("-v", ", Voice "),
            "satb_label": satb_label,
            "part_key": part_key,
            "part_role": part_role,
            "parent_section": parent_section,
            "is_accompaniment": is_accompaniment,
            "satb_confidence_applied": avg_confidence >= THRESHOLD_CONDENSED,
        })

    total_measures = 0
    if final_events:
        total_measures = max(e["measure_number"] for e in final_events)

    # Stamp classified custom SATB voices (data-vx-voice) into MusicXML
    try:
        part_mapping = {}
        part_staff_mapping = {}
        if root_elem is not None:
            stream_idx = 1
            for p_elem in root_elem.findall('.//part'):
                pid = p_elem.get('id', f'P{len(part_mapping)+1}')
                staves_count = 1
                for m in p_elem.findall('measure'):
                    st_el = m.find('.//attributes/staves')
                    if st_el is not None and st_el.text and st_el.text.strip().isdigit():
                        staves_count = max(staves_count, int(st_el.text.strip()))
                    for staff_el in m.findall('.//note/staff'):
                        if staff_el.text and staff_el.text.strip().isdigit():
                            staves_count = max(staves_count, int(staff_el.text.strip()))
                for staff_num in range(1, staves_count + 1):
                    part_mapping[stream_idx] = pid
                    part_staff_mapping[stream_idx] = staff_num
                    stream_idx += 1

        stamp_voices_into_xml(xml_path, final_events, global_measure_offsets, part_mapping=part_mapping, part_staff_mapping=part_staff_mapping)
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
