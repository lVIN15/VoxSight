#!/usr/bin/env python3
"""
Test Suite for Task 1A.1: Safe Classifier & VOICE_CODE_MAP Fallback Fixes.
Verifies all required safeguards and acceptance criteria:
  1. Recoverable baseline checkpoint & checksum validation.
  2. Regression test: Valid lyricless SATB score preservation under positive-evidence rule.
  3. Classification test: Recognized accompaniment (Oboe, Piano, Violin) classified as 'Others' (code '6').
  4. Classification test: Genuinely unknown instrument (Theremin) classified as 'UNKNOWN' (no stamp, never '1').
  5. Voice stamping test: Legacy labels ('S','A','T','B','Solo','Others') stamped correctly;
     unknown/invalid/missing labels ('UNKNOWN', 'UNISON_TB', None, '', etc.) skipped, NEVER default to '1'.
  6. MIDI pitch 0 edge case & is_rest: Note with pitch_midi==0 preserved, chord notes is_rest==False, rests skipped.
"""

import sys
import os
import hashlib
import xml.etree.ElementTree as ET
import tempfile
from pathlib import Path

# Add scripts directory to sys.path
SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import satb_analyzer

def test_1_baseline_checkpoint():
    """Verify clean baseline backup file exists and has correct SHA-256 hash."""
    print("--- Test 1: Baseline Checkpoint Verification ---")
    bak_candidates = [
        SCRIPTS_DIR / "satb_analyzer.py.bak.pre_sync",
        SCRIPTS_DIR / "satb_analyzer.py.bak.phase1a1",
    ]
    bak_path = next((p for p in bak_candidates if p.exists()), None)
    assert bak_path is not None, f"Backup file does not exist in {SCRIPTS_DIR}. Looked for: {[p.name for p in bak_candidates]}"

    with open(bak_path, "rb") as f:
        actual_hash = hashlib.sha256(f.read()).hexdigest().lower()

    expected_hash = "ecb8ee250f3cd2528f86244ea62fdc44b73f65d8b48a321fd41a163c935a51d5"
    assert actual_hash == expected_hash, f"Hash mismatch! Expected {expected_hash}, got {actual_hash}"
    print(f"[PASS] Backup file exists with valid SHA-256: {actual_hash}")


def create_minimal_musicxml(parts_data, title="Choral Score"):
    """
    Helper to construct minimal MusicXML score string.
    """
    root = ET.Element("score-partwise", version="3.1")

    # Movement title
    mov_title = ET.SubElement(root, "movement-title")
    mov_title.text = title

    # Part list
    part_list = ET.SubElement(root, "part-list")
    for p in parts_data:
        score_part = ET.SubElement(part_list, "score-part", id=p['id'])
        part_name = ET.SubElement(score_part, "part-name")
        part_name.text = p['name']
        if 'abbr' in p:
            part_abbr = ET.SubElement(score_part, "part-abbreviation")
            part_abbr.text = p['abbr']

    # Parts
    for p in parts_data:
        part_elem = ET.SubElement(root, "part", id=p['id'])
        for m_idx, m_notes in enumerate(p['measures'], 1):
            measure_elem = ET.SubElement(part_elem, "measure", number=str(m_idx))

            # Add attributes in measure 1
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


def test_2_regression_lyricless_satb():
    """Regression Test: Valid lyricless SATB score must NOT be classified as UNKNOWN."""
    print("\n--- Test 2: Regression Test for Valid Lyricless SATB Score ---")

    # 2a: Standard full part names (Soprano, Alto, Tenor, Bass) with 0 lyrics
    parts_data = [
        {
            'id': 'P1', 'name': 'Soprano',
            'measures': [[{'pitch': ('C', 5), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P2', 'name': 'Alto',
            'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P3', 'name': 'Tenor',
            'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P4', 'name': 'Bass',
            'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]
        },
    ]

    xml_content = create_minimal_musicxml(parts_data, title="Choral Hymn")

    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_content)
        temp_path = tf.name

    try:
        result = satb_analyzer.analyze(temp_path)
        meta = result["score_metadata"]
        events = result["events"]

        print(f"Structure Type: {meta['structure_type']}, SATB Confidence: {meta['satb_confidence']}")
        print(f"Number of events: {len(events)}")

        # All 4 parts must be vocal SATB
        assert meta["structure_type"] == "TRUE_SATB", f"Expected TRUE_SATB, got {meta['structure_type']}"
        assert meta["satb_confidence"] >= 0.75, f"Confidence too low: {meta['satb_confidence']}"
        assert len(events) == 4, f"Expected 4 events, got {len(events)}"

        voices = [e["satb_voice"] for e in events]
        print(f"Assigned voices: {voices}")
        assert voices == ['S', 'A', 'T', 'B'], f"Expected ['S', 'A', 'T', 'B'], got {voices}"

        # Check stamped XML
        tree = ET.parse(temp_path)
        root = tree.getroot()
        stamped_codes = []
        for p in root.findall("part"):
            for m in p.findall("measure"):
                for n in m.findall("note"):
                    if n.find("rest") is None:
                        stamped_codes.append(n.get("data-vx-voice"))
        print(f"Stamped voice codes in XML: {stamped_codes}")
        assert stamped_codes == ['S', 'A', 'T', 'B'], f"Expected ['S', 'A', 'T', 'B'], got {stamped_codes}"

        print("[PASS] Regression test 2a passed: Lyricless SATB parts correctly recognized and stamped as SATB S, A, T, B.")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)

    # 2b: Abbreviations S, A, T, B without lyrics
    print("\nTesting SATB abbreviations (S, A, T, B) without lyrics...")
    parts_data_abbr = [
        {'id': 'P1', 'name': 'S', 'measures': [[{'pitch': ('C', 5), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P2', 'name': 'A', 'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P3', 'name': 'T', 'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]},
        {'id': 'P4', 'name': 'B', 'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]},
    ]
    xml_abbr = create_minimal_musicxml(parts_data_abbr, title="Motet")
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_abbr)
        temp_path_abbr = tf.name
    try:
        res_abbr = satb_analyzer.analyze(temp_path_abbr)
        v_abbr = [e["satb_voice"] for e in res_abbr["events"]]
        print(f"Abbreviated assigned voices: {v_abbr}")
        assert v_abbr == ['S', 'A', 'T', 'B'], f"Expected ['S', 'A', 'T', 'B'], got {v_abbr}"

        # Check stamped XML
        tree = ET.parse(temp_path_abbr)
        root = tree.getroot()
        stamped_codes_abbr = [n.get("data-vx-voice") for p in root.findall("part") for m in p.findall("measure") for n in m.findall("note") if n.find("rest") is None]
        assert stamped_codes_abbr == ['S', 'A', 'T', 'B']
        print(f"[PASS] Regression test 2b passed: Abbreviated SATB without lyrics correctly recognized and stamped as S, A, T, B.")
    finally:
        if os.path.exists(temp_path_abbr):
            os.remove(temp_path_abbr)


def test_3_recognized_accomp_vs_unknown_instrument():
    """Verify recognized accompaniment (Oboe, Piano, Violin) -> Others (6), unknown (Theremin) -> UNKNOWN (no stamp)."""
    print("\n--- Test 3: Accompaniment vs Truly Unknown Instrument ---")

    parts_data = [
        {
            'id': 'P1', 'name': 'Soprano',
            'measures': [[{'pitch': ('C', 5), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P2', 'name': 'Alto',
            'measures': [[{'pitch': ('G', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P3', 'name': 'Tenor',
            'measures': [[{'pitch': ('E', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P4', 'name': 'Bass',
            'measures': [[{'pitch': ('C', 3), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P5', 'name': 'Oboe',    # Recognized accompaniment in ACCOMP_KEYWORDS
            'measures': [[{'pitch': ('A', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P6', 'name': 'Piano',   # Recognized accompaniment in ACCOMP_KEYWORDS
            'measures': [[{'pitch': ('C', 4), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P7', 'name': 'Violin',  # Recognized accompaniment in ACCOMP_KEYWORDS
            'measures': [[{'pitch': ('E', 5), 'duration': 4, 'type': 'note'}]]
        },
        {
            'id': 'P8', 'name': 'Theremin',# Genuinely UNKNOWN instrument
            'measures': [[{'pitch': ('F', 4), 'duration': 4, 'type': 'note'}]]
        },
    ]

    xml_content = create_minimal_musicxml(parts_data, title="Choral and Chamber Ensemble")

    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_content)
        temp_path = tf.name

    try:
        result = satb_analyzer.analyze(temp_path)
        events = result["events"]
        pb_tracks = result["score_metadata"]["playback_tracks"]

        # Check event voice assignments
        events_by_part = {e["part_id"]: e for e in events}

        # P1..P4: SATB
        assert events_by_part[1]["satb_voice"] == "S", f"P1 expected S, got {events_by_part[1]['satb_voice']}"
        assert events_by_part[2]["satb_voice"] == "A", f"P2 expected A, got {events_by_part[2]['satb_voice']}"
        assert events_by_part[3]["satb_voice"] == "T", f"P3 expected T, got {events_by_part[3]['satb_voice']}"
        assert events_by_part[4]["satb_voice"] == "B", f"P4 expected B, got {events_by_part[4]['satb_voice']}"

        # P5..P7: Recognized Accompaniment -> Others
        assert events_by_part[5]["satb_voice"] == "Others", f"P5 Oboe expected Others, got {events_by_part[5]['satb_voice']}"
        assert events_by_part[5]["satb_confidence"] == 1.0
        assert events_by_part[6]["satb_voice"] == "Others", f"P6 Piano expected Others, got {events_by_part[6]['satb_voice']}"
        assert events_by_part[6]["satb_confidence"] == 1.0
        assert events_by_part[7]["satb_voice"] == "Others", f"P7 Violin expected Others, got {events_by_part[7]['satb_voice']}"
        assert events_by_part[7]["satb_confidence"] == 1.0

        # P8: Theremin -> UNKNOWN
        assert events_by_part[8]["satb_voice"] == "UNKNOWN", f"P8 Theremin expected UNKNOWN, got {events_by_part[8]['satb_voice']}"
        assert events_by_part[8]["satb_confidence"] == 0.0

        print("[PASS] Event classifications:")
        print(f"  P1 (Soprano): {events_by_part[1]['satb_voice']}")
        print(f"  P2 (Alto):    {events_by_part[2]['satb_voice']}")
        print(f"  P3 (Tenor):   {events_by_part[3]['satb_voice']}")
        print(f"  P4 (Bass):    {events_by_part[4]['satb_voice']}")
        print(f"  P5 (Oboe):    {events_by_part[5]['satb_voice']}")
        print(f"  P6 (Piano):   {events_by_part[6]['satb_voice']}")
        print(f"  P7 (Violin):  {events_by_part[7]['satb_voice']}")
        print(f"  P8 (Theremin):{events_by_part[8]['satb_voice']}")

        # Check playback track labels
        labels_by_track = {t["track_id"]: t["satb_label"] for t in pb_tracks}
        print(f"Playback track labels: {labels_by_track}")
        assert labels_by_track["p5-s5"] == "Others"
        assert labels_by_track["p6-s6"] == "Others"
        assert labels_by_track["p7-s7"] == "Others"
        assert labels_by_track["p8-s8"] == "UNKNOWN"

        # Check stamped XML
        tree = ET.parse(temp_path)
        root = tree.getroot()
        parts = root.findall("part")

        p1_note = parts[0].find("measure").find("note")
        p2_note = parts[1].find("measure").find("note")
        p3_note = parts[2].find("measure").find("note")
        p4_note = parts[3].find("measure").find("note")
        p5_note = parts[4].find("measure").find("note")
        p6_note = parts[5].find("measure").find("note")
        p7_note = parts[6].find("measure").find("note")
        p8_note = parts[7].find("measure").find("note")

        assert p1_note.get("data-vx-voice") == "S", f"P1 Soprano stamped {p1_note.get('data-vx-voice')}, expected 'S'"
        assert p2_note.get("data-vx-voice") == "A", f"P2 Alto stamped {p2_note.get('data-vx-voice')}, expected 'A'"
        assert p3_note.get("data-vx-voice") == "T", f"P3 Tenor stamped {p3_note.get('data-vx-voice')}, expected 'T'"
        assert p4_note.get("data-vx-voice") == "B", f"P4 Bass stamped {p4_note.get('data-vx-voice')}, expected 'B'"
        assert p5_note.get("data-vx-voice") == "Others", f"P5 Oboe stamped {p5_note.get('data-vx-voice')}, expected 'Others'"
        assert p6_note.get("data-vx-voice") == "Others", f"P6 Piano stamped {p6_note.get('data-vx-voice')}, expected 'Others'"
        assert p7_note.get("data-vx-voice") == "Others", f"P7 Violin stamped {p7_note.get('data-vx-voice')}, expected 'Others'"

        # P8 (Theremin) MUST NOT HAVE data-vx-voice attribute! It must NEVER default to '1'!
        assert p8_note.get("data-vx-voice") is None, f"P8 Theremin was stamped with: {p8_note.get('data-vx-voice')}! Expected None!"
        print("[PASS] Stamping verification:")
        print(f"  P1 Soprano:  data-vx-voice = '{p1_note.get('data-vx-voice')}'")
        print(f"  P2 Alto:     data-vx-voice = '{p2_note.get('data-vx-voice')}'")
        print(f"  P3 Tenor:    data-vx-voice = '{p3_note.get('data-vx-voice')}'")
        print(f"  P4 Bass:     data-vx-voice = '{p4_note.get('data-vx-voice')}'")
        print(f"  P5 Oboe:     data-vx-voice = '{p5_note.get('data-vx-voice')}'")
        print(f"  P6 Piano:    data-vx-voice = '{p6_note.get('data-vx-voice')}'")
        print(f"  P7 Violin:   data-vx-voice = '{p7_note.get('data-vx-voice')}'")
        print(f"  P8 Theremin: data-vx-voice = {p8_note.get('data-vx-voice')} (SKIPPED, never defaulted to '1')")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def test_4_voice_code_map_fallbacks():
    """Verify all legacy voice labels and ensure unknown/invalid/missing labels are skipped and NEVER default to '1'."""
    print("\n--- Test 4: VOICE_CODE_MAP Legacy and Fallback Stamping ---")

    notes_to_test = [
        ("S", "S"),
        ("A", "A"),
        ("T", "T"),
        ("B", "B"),
        ("Solo", "Solo"),
        ("Others", "Others"),
        ("UNKNOWN", None),
        ("UNISON_TB", None),
        ("DIVISI_S1", None),
        (None, None),
        ("", None),
    ]

    measures_notes = []
    for _ in notes_to_test:
        measures_notes.append({'pitch': ('C', 4), 'duration': 1, 'type': 'note'})

    parts_data = [{
        'id': 'P1', 'name': 'TestPart',
        'measures': [measures_notes]
    }]

    xml_content = create_minimal_musicxml(parts_data)
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False) as tf:
        tf.write(xml_content)
        temp_path = tf.name

    try:
        events = []
        for idx, (label, _) in enumerate(notes_to_test):
            events.append({
                'part_id': 1,
                'measure_number': 1,
                'satb_voice': label
            })

        success = satb_analyzer.stamp_voices_into_xml(temp_path, events)
        assert success, "stamp_voices_into_xml returned False"

        tree = ET.parse(temp_path)
        notes = tree.getroot().find("part").find("measure").findall("note")

        for idx, (label, expected_code) in enumerate(notes_to_test):
            actual_code = notes[idx].get("data-vx-voice")
            print(f"  Input: {repr(label):12} -> Expected: {repr(expected_code):6} -> Actual: {repr(actual_code)}")
            if expected_code is None:
                assert actual_code is None, f"Expected unstamped None for {label}, but got {actual_code}!"
            else:
                assert actual_code == expected_code, f"Expected {expected_code} for {label}, but got {actual_code}!"

        print("[PASS] VOICE_CODE_MAP correctly mapped all legacy labels and skipped all invalid/unknown labels without defaulting to '1'.")
    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def test_5_midi_pitch_0_and_is_rest():
    """Verify that MIDI pitch 0 (C-1) has is_rest=False and is retained in final_events, and chord notes are not rests."""
    print("\n--- Test 5: MIDI Pitch 0 Edge Case & Chord is_rest Handling ---")

    # Measure contains:
    # 1. Pitch C-1 (MIDI pitch 0)
    # 2. A Rest
    # 3. Chord C4 + E4 + G4 (triad)
    # 4. Normal note C4

    xml_str = """<?xml version="1.0" encoding="UTF-8"?>
    <score-partwise version="3.1">
      <part-list>
        <score-part id="P1">
          <part-name>Bass</part-name>
        </score-part>
      </part-list>
      <part id="P1">
        <measure number="1">
          <attributes>
            <divisions>1</divisions>
            <time><beats>4</beats><beat-type>4</beat-type></time>
            <clef><sign>F</sign><line>4</line></clef>
          </attributes>
          <!-- 1. Pitch C-1 (MIDI 0) -->
          <note>
            <pitch><step>C</step><octave>-1</octave></pitch>
            <duration>1</duration>
            <type>quarter</type>
          </note>
          <!-- 2. Rest -->
          <note>
            <rest/>
            <duration>1</duration>
            <type>quarter</type>
          </note>
          <!-- 3. Chord C4 + E4 + G4 -->
          <note>
            <pitch><step>C</step><octave>4</octave></pitch>
            <duration>1</duration>
            <type>quarter</type>
          </note>
          <note>
            <chord/>
            <pitch><step>E</step><octave>4</octave></pitch>
            <duration>1</duration>
            <type>quarter</type>
          </note>
          <note>
            <chord/>
            <pitch><step>G</step><octave>4</octave></pitch>
            <duration>1</duration>
            <type>quarter</type>
          </note>
          <!-- 4. Normal Note C4 -->
          <note>
            <pitch><step>C</step><octave>4</octave></pitch>
            <duration>1</duration>
            <type>quarter</type>
          </note>
        </measure>
      </part>
    </score-partwise>
    """

    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_str)
        temp_path = tf.name

    try:
        result = satb_analyzer.analyze(temp_path)
        events = result["events"]

        print(f"Total final_events extracted: {len(events)}")
        for e in events:
            print(f"  Event: pitch={e['pitch_midi']} ({e['pitch_name']}), is_rest={e['is_rest']}, is_chord_member={e['is_chord_member']}")

        # Total non-rest sounding notes:
        # Note 1: C-1 (1 note)
        # Note 2: rest (0 notes)
        # Note 3: C4 + E4 + G4 chord (3 notes)
        # Note 4: C4 (1 note)
        # Total expected in final_events = 5
        assert len(events) == 5, f"Expected 5 sounding notes in final_events, got {len(events)}"

        # Verify note 1: C-1 (MIDI pitch 0)
        c_neg1 = events[0]
        assert c_neg1["pitch_midi"] == 0, f"Expected pitch_midi 0, got {c_neg1['pitch_midi']}"
        assert c_neg1["pitch_name"] == "C-1", f"Expected pitch_name C-1, got {c_neg1['pitch_name']}"
        assert c_neg1["is_rest"] is False, "MIDI pitch 0 must NOT be marked is_rest=True!"
        print("[PASS] Pitch C-1 (MIDI 0) preserved in final_events with is_rest=False.")

        # Verify rest: no rest element in final_events
        rests_in_final = [e for e in events if e.get("pitch_name") == "rest" or e["is_rest"]]
        assert len(rests_in_final) == 0, f"Rests must not be present in final_events: {rests_in_final}"
        print("[PASS] Rest correctly excluded from final_events.")

        # Verify chord notes: all 3 chord members must have is_rest=False and is_chord_member=True
        chord_events = [e for e in events if e["is_chord_member"]]
        assert len(chord_events) == 3, f"Expected 3 chord notes, got {len(chord_events)}"
        for ce in chord_events:
            assert ce["is_rest"] is False, f"Chord member {ce['pitch_name']} had is_rest=True!"
            assert ce["is_chord_member"] is True
        print("[PASS] All chord members have is_rest=False and is_chord_member=True.")

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)


def main():
    print("==================================================================")
    print("STARTING TASK 1A.1 VERIFICATION TEST SUITE")
    print("Target file: voxsight/scripts/satb_analyzer.py")
    print("==================================================================")

    test_1_baseline_checkpoint()
    test_2_regression_lyricless_satb()
    test_3_recognized_accomp_vs_unknown_instrument()
    test_4_voice_code_map_fallbacks()
    test_5_midi_pitch_0_and_is_rest()

    print("\n==================================================================")
    print("ALL 5 TEST SUITES PASSED PERFECTLY!")
    print("Task 1A.1 criteria and safeguards 100% satisfied.")
    print("==================================================================")

if __name__ == "__main__":
    main()
