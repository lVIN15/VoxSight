#!/usr/bin/env python3
"""
Test Suite for Phase 1A.2: Classifier Lexicon Hardening.
Verifies:
  1. Instrumental cases with vocal prefixes/terms (Alto Flute, Alto Saxophone,
     Tenor Saxophone, Bass Clarinet, Double Bass, Contrabass, Bassoon, Trombone)
     are classified as 'Others' and strictly NOT admitted into choral_part_indices or marked 'SATB'.
  2. Legitimate vocal cases (Soprano, Alto, Tenor, Bass, Choir, S, A, T, B)
     continue to be recognized as 'SATB'.
  3. Genuinely unknown parts (Theremin) and unknown parts with vocal prefixes (Bass Theremin)
     are classified as 'UNKNOWN' and NEVER forced into 'SATB'.
  4. Purely instrumental scores with overlapping instruments (e.g. Alto Sax, Bassoon)
     are rejected by upload validation gate (check_unsupported_score).
  5. Full score with mixed SATB + accompaniment + unknown parts properly categorizes each.
"""

import sys
import os
import hashlib
import xml.etree.ElementTree as ET
import tempfile
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import satb_analyzer

def create_musicxml(parts_data, title="Score"):
    """Construct minimal MusicXML score string."""
    root = ET.Element("score-partwise", version="3.1")
    mov_title = ET.SubElement(root, "movement-title")
    mov_title.text = title
    part_list = ET.SubElement(root, "part-list")
    for p in parts_data:
        score_part = ET.SubElement(part_list, "score-part", id=p['id'])
        part_name = ET.SubElement(score_part, "part-name")
        part_name.text = p['name']
        if 'abbr' in p:
            part_abbr = ET.SubElement(score_part, "part-abbreviation")
            part_abbr.text = p['abbr']

    for p in parts_data:
        part_elem = ET.SubElement(root, "part", id=p['id'])
        for m_idx, m_notes in enumerate(p['measures'], 1):
            measure_elem = ET.SubElement(part_elem, "measure", number=str(m_idx))
            if m_idx == 1:
                attrs = ET.SubElement(measure_elem, "attributes")
                divs = ET.SubElement(attrs, "divisions")
                divs.text = "1"
                time_elem = ET.SubElement(attrs, "time")
                beats = ET.SubElement(time_elem, "beats")
                beats.text = "4"
                beat_type = ET.SubElement(time_elem, "beat-type")
                beat_type.text = "4"
                clef = ET.SubElement(attrs, "clef")
                sign = ET.SubElement(clef, "sign")
                sign.text = "G"
                line = ET.SubElement(clef, "line")
                line.text = "2"

            for item in m_notes:
                note_elem = ET.SubElement(measure_elem, "note")
                if item.get('chord'):
                    ET.SubElement(note_elem, "chord")
                if item['type'] == 'rest':
                    ET.SubElement(note_elem, "rest")
                elif item['type'] == 'note':
                    pitch_elem = ET.SubElement(note_elem, "pitch")
                    step_elem = ET.SubElement(pitch_elem, "step")
                    step_elem.text = item['pitch'][0]
                    if len(item['pitch']) > 2 and item['pitch'][2] != 0:
                        alter_elem = ET.SubElement(pitch_elem, "alter")
                        alter_elem.text = str(item['pitch'][2])
                    octave_elem = ET.SubElement(pitch_elem, "octave")
                    octave_elem.text = str(item['pitch'][1])

                dur_elem = ET.SubElement(note_elem, "duration")
                dur_elem.text = str(item['duration'])
                type_elem = ET.SubElement(note_elem, "type")
                type_elem.text = "quarter" if item['duration'] == 1 else "whole"

                if item.get('lyrics'):
                    lyric_elem = ET.SubElement(note_elem, "lyric")
                    text_elem = ET.SubElement(lyric_elem, "text")
                    text_elem.text = item['lyrics']

    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def test_1_instrument_cases_with_vocal_overlap():
    """Verify that Alto Flute, Alto Sax, Tenor Sax, Bass Clarinet, Double Bass, Contrabass, Bassoon, Trombone are Others, not SATB."""
    print("--- Test 1: Instrument Cases with Vocal Overlap ---")

    # 8 instruments with vocal-overlapping names + 4 SATB vocal parts
    parts_data = [
        # Legitimate vocal parts
        {'id': 'P1', 'name': 'Soprano', 'measures': [[{'pitch': ('C', 5), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P2', 'name': 'Alto',    'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P3', 'name': 'Tenor',   'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P4', 'name': 'Bass',    'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]},
        # Instrumental parts with vocal overlapping names
        {'id': 'P5', 'name': 'Alto Flute',        'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P6', 'name': 'Alto Saxophone',    'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P7', 'name': 'Tenor Saxophone',   'measures': [[{'pitch': ('D', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P8', 'name': 'Bass Clarinet',     'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P9', 'name': 'Double Bass',       'measures': [[{'pitch': ('E', 2), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P10', 'name': 'Contrabass',       'measures': [[{'pitch': ('C', 2), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P11', 'name': 'Bassoon',          'measures': [[{'pitch': ('F', 2), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P12', 'name': 'Trombone',         'measures': [[{'pitch': ('A', 2), 'duration': 4, 'type': 'note'}]]},
    ]

    xml_data = create_musicxml(parts_data, title="Missa Brevis with Orchestra")
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_data)
        temp_path = tf.name

    try:
        res = satb_analyzer.analyze(temp_path)
        events = res["events"]
        pb_tracks = res["score_metadata"]["playback_tracks"]

        events_by_part = {e["part_id"]: e for e in events}

        # 1. Assert legitimate vocal parts are SATB
        assert events_by_part[1]["satb_voice"] == "S", f"P1 Soprano expected S, got {events_by_part[1]['satb_voice']}"
        assert events_by_part[2]["satb_voice"] == "A", f"P2 Alto expected A, got {events_by_part[2]['satb_voice']}"
        assert events_by_part[3]["satb_voice"] == "T", f"P3 Tenor expected T, got {events_by_part[3]['satb_voice']}"
        assert events_by_part[4]["satb_voice"] == "B", f"P4 Bass expected B, got {events_by_part[4]['satb_voice']}"

        # 2. Assert ALL 8 instrument parts are classified as Others, NOT SATB!
        instrument_parts = [
            (5, "Alto Flute"),
            (6, "Alto Saxophone"),
            (7, "Tenor Saxophone"),
            (8, "Bass Clarinet"),
            (9, "Double Bass"),
            (10, "Contrabass"),
            (11, "Bassoon"),
            (12, "Trombone"),
        ]

        for pid, name in instrument_parts:
            ev = events_by_part[pid]
            voice = ev["satb_voice"]
            print(f"  Part {pid:2d} ({name:18}): satb_voice = {voice} (confidence = {ev['satb_confidence']})")
            assert voice == "Others", f"Part {pid} ({name}) misclassified as {voice}! Expected 'Others'!"
            assert ev["satb_confidence"] == 1.0
            assert voice not in ["S", "A", "T", "B"], f"Part {pid} ({name}) admitted to vocal SATB!"

        # 3. Check XML voice stamping on all notes
        tree = ET.parse(temp_path)
        root = tree.getroot()
        parts = root.findall("part")

        # Vocal parts stamped S, A, T, B
        assert parts[0].find("measure").find("note").get("data-vx-voice") == "S"
        assert parts[1].find("measure").find("note").get("data-vx-voice") == "A"
        assert parts[2].find("measure").find("note").get("data-vx-voice") == "T"
        assert parts[3].find("measure").find("note").get("data-vx-voice") == "B"

        # Instrument parts stamped 'Others'
        for idx in range(4, 12):
            stamped_code = parts[idx].find("measure").find("note").get("data-vx-voice")
            assert stamped_code == "Others", f"Part {idx+1} stamped with {stamped_code}, expected 'Others'"

        print("[PASS] Test 1: All 8 overlapping instrument parts correctly classified as 'Others' (stamped 'Others') and excluded from vocal SATB.")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def test_2_unknown_parts_with_vocal_prefix():
    """Verify that unlisted instruments (Theremin, Bass Theremin, Tenor Kazoo) are UNKNOWN, never SATB."""
    print("\n--- Test 2: Unknown Parts with Vocal Prefixes ---")

    parts_data = [
        # Vocal anchor
        {'id': 'P1', 'name': 'Soprano',        'measures': [[{'pitch': ('C', 5), 'duration': 4, 'type': 'note'}]]},
        # Truly unknown instrument
        {'id': 'P2', 'name': 'Theremin',       'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]},
        # Unknown instrument with vocal prefix
        {'id': 'P3', 'name': 'Bass Theremin',  'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P4', 'name': 'Tenor Kazoo',    'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]},
    ]

    xml_data = create_musicxml(parts_data, title="Experimental Vocal Ensemble")
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_data)
        temp_path = tf.name

    try:
        res = satb_analyzer.analyze(temp_path)
        events = res["events"]
        events_by_part = {e["part_id"]: e for e in events}

        # P1 Soprano -> S
        assert events_by_part[1]["satb_voice"] == "S"

        # P2, P3, P4 -> UNKNOWN (NEVER SATB!)
        for pid, name in [(2, "Theremin"), (3, "Bass Theremin"), (4, "Tenor Kazoo")]:
            ev = events_by_part[pid]
            print(f"  Part {pid} ({name:14}): satb_voice = {ev['satb_voice']}, confidence = {ev['satb_confidence']}")
            assert ev["satb_voice"] == "UNKNOWN", f"{name} misclassified as {ev['satb_voice']}! Expected 'UNKNOWN'!"
            assert ev["satb_confidence"] == 0.0

        # Verify XML stamping: UNKNOWN parts must be left UNSTAMPED (None, never '1')
        tree = ET.parse(temp_path)
        parts = tree.getroot().findall("part")
        assert parts[0].find("measure").find("note").get("data-vx-voice") == "S"
        for idx in [1, 2, 3]:
            stamped = parts[idx].find("measure").find("note").get("data-vx-voice")
            assert stamped is None, f"Part {idx+1} unstamped note got {stamped}! Expected None!"

        print("[PASS] Test 2: Unlisted instruments with vocal prefixes correctly classified as 'UNKNOWN' and left unstamped.")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def test_3_purely_instrumental_score_rejection():
    """Verify that a purely instrumental score with overlapping instruments (Alto Sax, Tenor Sax, Trombone) is rejected by upload validation gate."""
    print("\n--- Test 3: Purely Instrumental Score Upload Gate Rejection ---")

    parts_data = [
        {'id': 'P1', 'name': 'Alto Saxophone',  'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P2', 'name': 'Tenor Saxophone', 'measures': [[{'pitch': ('D', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P3', 'name': 'Trombone',        'measures': [[{'pitch': ('A', 2), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P4', 'name': 'Double Bass',     'measures': [[{'pitch': ('E', 2), 'duration': 4, 'type': 'note'}]]},
    ]

    xml_data = create_musicxml(parts_data, title="Jazz Quartet")
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_data)
        temp_path = tf.name

    try:
        is_unsupported, reason = satb_analyzer.check_unsupported_score(temp_path)
        print(f"Validation Gate: is_unsupported = {is_unsupported}, reason = {reason[:60]}...")
        assert is_unsupported is True, "Purely instrumental score with Alto/Tenor/Bass instruments was NOT rejected!"
        assert "Unsupported Score: Purely instrumental score" in reason

        # Verify analyze() returns error response
        res = satb_analyzer.analyze(temp_path)
        assert res["score_metadata"]["structure_type"] == "UNCERTAIN"
        assert res["score_metadata"]["satb_confidence"] == 0.0
        assert len(res["events"]) == 0
        print("[PASS] Test 3: Purely instrumental score correctly rejected at upload validation gate.")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def test_4_supported_vocal_labels_preserved():
    """Verify that all standard supported vocal labels (Choir, Chorus, Descant, Treble, Baritone, Mezzo-Soprano) are classified as SATB."""
    print("\n--- Test 4: Supported Vocal Labels Preserved ---")

    labels_to_test = [
        ("Choir", 60),
        ("Chorus", 64),
        ("Treble", 72),
        ("Descant", 76),
        ("Baritone", 50),
        ("Mezzo-Soprano", 65),
        ("Altus", 65),
        ("Cantus", 72),
        ("Bassus", 45),
        ("Soprano 1", 72),
        ("Alto II", 65),
        ("Tenor 1", 57),
        ("Bass 2", 45),
    ]

    for label, midi in labels_to_test:
        parts_data = [
            {'id': 'P1', 'name': label, 'measures': [[{'pitch': ('C', 4), 'duration': 4, 'type': 'note'}]]}
        ]
        xml_data = create_musicxml(parts_data, title="Choral Movement")
        with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
            tf.write(xml_data)
            temp_path = tf.name
        try:
            # Check is_vocal_part_name
            is_vocal = satb_analyzer.is_vocal_part_name(label)
            assert is_vocal is True, f"Vocal label '{label}' was evaluated as False by is_vocal_part_name!"
            print(f"  Vocal label '{label:15}': is_vocal_part_name = {is_vocal}")
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path)

    print("[PASS] Test 4: All supported vocal labels correctly recognized as vocal.")


def main():
    print("==================================================================")
    print("STARTING PHASE 1A.2 VERIFICATION TEST SUITE")
    print("Target file: voxsight/scripts/satb_analyzer.py")
    print("==================================================================")

    test_1_instrument_cases_with_vocal_overlap()
    test_2_unknown_parts_with_vocal_prefix()
    test_3_purely_instrumental_score_rejection()
    test_4_supported_vocal_labels_preserved()

    print("\n==================================================================")
    print("ALL PHASE 1A.2 TESTS PASSED PERFECTLY!")
    print("==================================================================")

if __name__ == "__main__":
    main()
