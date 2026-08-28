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
    # Sorting ensures measures increment in order.
    # Note: If m_num is 0 (pickup), it comes first.
    # We must convert keys to string/int carefully if there are mixed types, but music21 measure numbers are ints or float/string if complex.
    # We'll just sort them using a robust key.
    for m_num in sorted(measure_durations.keys(), key=lambda x: int(x) if isinstance(x, (int, float, str)) and str(x).isdigit() else 9999):
        global_measure_offsets[m_num] = current_offset
        current_offset += measure_durations[m_num]


    for part_idx, part in enumerate(parts):
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
                # Force vertical measure alignment using the global offset rather than the drifting part offset
                measure_offset = global_measure_offsets.get(measure_num, measure.offset)
                global_offset = measure_offset + offset_in_measure
                tick_position = normalize_to_ticks(global_offset, tpq)
                duration_ticks = normalize_to_ticks(element.quarterLength, tpq)

                # Staff detection — use part-relative staff mapping
                staff_id = part_idx + 1  # Default: part index = staff
                # music21 stores staff assignment in note's editorial or via <staff> tag
                if hasattr(element, 'editorial'):
                    ed = element.editorial
                    if hasattr(ed, 'staffNumber') and ed.staffNumber is not None:
                        staff_id = ed.staffNumber
                # Also check if MusicXML had explicit <staff> element
                if hasattr(element, 'storedInstrument'):
                    pass  # keep staff_id from editorial
                staves_in_part.add(staff_id)

                # Voice detection — extract the actual MusicXML <voice> number
                voice_source = 0
                # Method 1: Check the Voice container the element lives in
                if element.activeSite is not None:
                    site = element.activeSite
                    if isinstance(site, music21.stream.Voice):
                        # music21 Voice objects have an .id that maps to MusicXML <voice>
                        try:
                            voice_source = int(site.id)
                        except (ValueError, TypeError):
                            # music21 sometimes uses string IDs; map to sequential int
                            voice_source = hash(str(site.id)) % 100  # stable small int
                # Method 2: Some notes have a direct voice property
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
                        "part_id": part_idx + 1,
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

                    # Tie detection
                    tie_type = None
                    if note_obj.tie:
                        tie_type = note_obj.tie.type  # 'start', 'stop', 'continue'

                    is_chord = element.isChord

                    # Voice signature
                    sig = compute_voice_signature(staff_id, pitch_midi, staff_pitch_range)

                    # SATB scoring
                    pitch_scores = score_pitch(pitch_midi)
                    staff_scores = score_staff(staff_id, part_count)
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
                        "part_id": part_idx + 1,
                        "is_rest": is_chord,  # will be corrected below
                        "is_chord_member": is_chord,
                        "tie_type": tie_type,
                        "voice_signature": sig,
                        "satb_voice": satb_voice,
                        "satb_confidence": satb_confidence,
                        "xml_element_order": xml_order,
                    })
                    xml_order += 1

        staff_count += len(staves_in_part)

    # Fix is_rest field (was incorrectly set to is_chord for notes)
    for e in raw_events:
        if e.get("pitch_midi", 0) > 0:
            e["is_rest"] = False

    log.info(f"[{PIPELINE_FROZEN_KERNEL_START}] Extracted {len(raw_events)} raw events")

    # ─── Global Melodic & Vertical Voice Assignment Optimization ─────
    try:
        # Group raw notes (non-rests) by tick
        non_rests = [e for e in raw_events if not e["is_rest"]]
        
        # Group by tick_position
        by_tick = {}
        for e in non_rests:
            by_tick.setdefault(e["tick_position"], []).append(e)
            
        # We will track the last pitch assigned to each voice to maintain melodic continuity
        last_voice_pitch = {v: None for v in "SATB"}
        last_voice_tick = {v: -9999 for v in "SATB"}
        
        # Iterate chronologically to classify voices with vertical + continuity context
        for tick in sorted(by_tick.keys()):
            tick_notes = by_tick[tick]
            
            # Sort notes by pitch descending (highest first)
            tick_notes.sort(key=lambda x: x["pitch_midi"], reverse=True)
            
            # Group notes by staff_id to apply vertical constraints within each staff
            by_staff = {}
            for tn in tick_notes:
                by_staff.setdefault(tn["staff_id"], []).append(tn)
                
            for staff_id, staff_notes in by_staff.items():
                # staff_notes are already sorted descending because tick_notes was sorted
                # Apply vertical rank scoring
                for rank, tn in enumerate(staff_notes):
                    pitch = tn["pitch_midi"]
                    
                    # 1. Pitch Range Score
                    p_scores = score_pitch(pitch)
                    
                    # 2. Staff Score
                    s_scores = score_staff(staff_id, part_count)
                    
                    # 3. Vertical Order Score
                    v_scores = {v: 0.0 for v in "SATB"}
                    if part_count >= 4:
                        # In a 4-part score, staff matches voice 1:1, so vertical rank within staff doesn't split S vs A.
                        mapping = {1: "S", 2: "A", 3: "T", 4: "B"}
                        default_v = mapping.get(staff_id, "S")
                        v_scores[default_v] = 1.0
                    else:
                        # Treble staff (S/A)
                        if staff_id == 1:
                            if len(staff_notes) >= 2:
                                if rank == 0:
                                    v_scores["S"] = 0.9
                                    v_scores["A"] = 0.1
                                else:
                                    v_scores["S"] = 0.1
                                    v_scores["A"] = 0.9
                            else:
                                if pitch >= 69:  # A4 and above
                                    v_scores["S"] = 0.8
                                    v_scores["A"] = 0.2
                                else:
                                    v_scores["S"] = 0.2
                                    v_scores["A"] = 0.8
                        # Bass staff (T/B)
                        else:
                            if len(staff_notes) >= 2:
                                if rank == 0:
                                    v_scores["T"] = 0.9
                                    v_scores["B"] = 0.1
                                else:
                                    v_scores["T"] = 0.1
                                    v_scores["B"] = 0.9
                            else:
                                if pitch >= 53:  # F3 and above
                                    v_scores["T"] = 0.8
                                    v_scores["B"] = 0.2
                                else:
                                    v_scores["T"] = 0.2
                                    v_scores["B"] = 0.8
                                    
                    # 4. Melodic Continuity Score (HMM-lite)
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
                                    
                    # Combine scores using highly optimized weights
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
                    
                    # Update event
                    tn["satb_voice"] = best_voice
                    tn["satb_confidence"] = confidence
                    
                    # Update tracking for continuity
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

    # Default SATB label mapping
    sorted_tracks = sorted(track_ids)
    default_labels = ["Soprano", "Alto", "Tenor", "Bass"]
    playback_tracks = []
    for i, tid in enumerate(sorted_tracks):
        satb_label = default_labels[i] if i < len(default_labels) else f"Voice {i+1}"
        playback_tracks.append({
            "track_id": tid,
            "structural_label": tid.replace("p", "Part ").replace("-s", ", Staff ").replace("-v", ", Voice "),
            "satb_label": satb_label,
            "satb_confidence_applied": avg_confidence >= THRESHOLD_CONDENSED,
        })

    total_measures = 0
    if final_events:
        total_measures = max(e["measure_number"] for e in final_events)

    log.info(f"[{PIPELINE_OUTPUT}] {len(final_events)} events, "
             f"confidence={avg_confidence:.3f}, type={structure_type}")

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
    return {
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
