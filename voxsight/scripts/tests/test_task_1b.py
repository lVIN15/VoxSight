"""
Task 1B Verification Suite: Backend String-Token Migration
Tests the string-token contract in voxsight/scripts/satb_analyzer.py:
1. Exact canonical VOICE_CODE_MAP values and unknown-key behavior.
2. SATB string-token stamping ("S", "A", "T", "B").
3. Solo and Others stamping ("Solo", "Others").
4. Unknown instrumental parts remaining unstamped (data-vx-voice is None).
5. Ambiguous notes remaining unstamped (data-vx-voice is None).
6. MusicXML parsing and round-trip validity.
7. Preservation of the v1.0 production JSON schema.
"""

import sys
import os
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
import json

# Ensure satb_analyzer can be imported
SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))
import satb_analyzer

def make_musicxml_wrapper(part_xml_snippets: str) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
<score-partwise version="3.1">
  <part-list>
{part_xml_snippets[0]}
  </part-list>
{part_xml_snippets[1]}
</score-partwise>"""

def test_1_voice_code_map_contract():
    print("\n--- Test 1: Canonical VOICE_CODE_MAP & Unknown-Key Behavior ---")
    vmap = satb_analyzer.VOICE_CODE_MAP

    assert vmap.get('S') == 'S', f"Expected 'S', got {vmap.get('S')}"
    assert vmap.get('A') == 'A', f"Expected 'A', got {vmap.get('A')}"
    assert vmap.get('T') == 'T', f"Expected 'T', got {vmap.get('T')}"
    assert vmap.get('B') == 'B', f"Expected 'B', got {vmap.get('B')}"
    assert vmap.get('Solo') == 'Solo', f"Expected 'Solo', got {vmap.get('Solo')}"
    assert vmap.get('Others') == 'Others', f"Expected 'Others', got {vmap.get('Others')}"

    # Assert unknown keys return None (never default to Soprano or '1')
    assert vmap.get('UNKNOWN') is None, "UNKNOWN must map to None"
    assert vmap.get('UNISON_TB') == 'UNISON_TB', "Unison tokens supported in VOICE_CODE_MAP"
    assert vmap.get('GUITAR') is None, "Unrecognized instruments must return None"
    assert vmap.get(None) is None, "None must return None"
    assert vmap.get('') is None, "Empty string must return None"

    print("[PASS] Test 1: Exact canonical VOICE_CODE_MAP contract and unknown key rejection verified.")

def test_2_satb_string_stamping():
    print("\n--- Test 2: SATB String-Token Stamping into MusicXML ---")
    part_list = """    <score-part id="P1"><part-name>Soprano</part-name></score-part>
    <score-part id="P2"><part-name>Alto</part-name></score-part>
    <score-part id="P3"><part-name>Tenor</part-name></score-part>
    <score-part id="P4"><part-name>Bass</part-name></score-part>"""

    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P3">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P4">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        events = [
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 72, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "S"},
            {"part_id": "P2", "measure_number": 1, "pitch_midi": 67, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "A"},
            {"part_id": "P3", "measure_number": 1, "pitch_midi": 64, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "T"},
            {"part_id": "P4", "measure_number": 1, "pitch_midi": 48, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "B"},
        ]

        stamped = satb_analyzer.stamp_voices_into_xml(tmp_path, events)
        assert stamped, "stamp_voices_into_xml returned False"

        tree = ET.parse(tmp_path)
        root = tree.getroot()

        p1_note = root.find(".//part[@id='P1']//note")
        p2_note = root.find(".//part[@id='P2']//note")
        p3_note = root.find(".//part[@id='P3']//note")
        p4_note = root.find(".//part[@id='P4']//note")

        assert p1_note.get("data-vx-voice") == "S", f"P1 expected 'S', got {p1_note.get('data-vx-voice')}"
        assert p2_note.get("data-vx-voice") == "A", f"P2 expected 'A', got {p2_note.get('data-vx-voice')}"
        assert p3_note.get("data-vx-voice") == "T", f"P3 expected 'T', got {p3_note.get('data-vx-voice')}"
        assert p4_note.get("data-vx-voice") == "B", f"P4 expected 'B', got {p4_note.get('data-vx-voice')}"

        print("[PASS] Test 2: S, A, T, B stamped cleanly with canonical string tokens.")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

def test_3_solo_and_others_stamping():
    print("\n--- Test 3: Solo and Others String-Token Stamping ---")
    part_list = """    <score-part id="P1"><part-name>Solo Voice</part-name></score-part>
    <score-part id="P2"><part-name>Piano Accompaniment</part-name></score-part>"""

    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>G</step><octave>3</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        events = [
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 62, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "Solo"},
            {"part_id": "P2", "measure_number": 1, "pitch_midi": 55, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "Others"},
        ]

        stamped = satb_analyzer.stamp_voices_into_xml(tmp_path, events)
        assert stamped, "stamp_voices_into_xml returned False"

        tree = ET.parse(tmp_path)
        root = tree.getroot()

        p1_note = root.find(".//part[@id='P1']//note")
        p2_note = root.find(".//part[@id='P2']//note")

        assert p1_note.get("data-vx-voice") == "Solo", f"P1 expected 'Solo', got {p1_note.get('data-vx-voice')}"
        assert p2_note.get("data-vx-voice") == "Others", f"P2 expected 'Others', got {p2_note.get('data-vx-voice')}"

        print("[PASS] Test 3: Solo and Others stamped cleanly with canonical string tokens.")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

def test_4_unknown_part_unstamped():
    print("\n--- Test 4: Unknown Instrumental Part Remains Unstamped ---")
    part_list = """    <score-part id="P1"><part-name>Theremin</part-name></score-part>"""

    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>A</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        events = [
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 69, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "UNKNOWN"},
        ]

        stamped = satb_analyzer.stamp_voices_into_xml(tmp_path, events)
        assert stamped, "stamp_voices_into_xml returned False"

        tree = ET.parse(tmp_path)
        root = tree.getroot()

        p1_note = root.find(".//part[@id='P1']//note")
        assert p1_note.get("data-vx-voice") is None, f"Expected unstamped note, got: {p1_note.get('data-vx-voice')}"

        print("[PASS] Test 4: Unknown instrumental parts remain 100% unstamped (data-vx-voice is None).")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

def test_5_ambiguous_notes_unstamped():
    print("\n--- Test 5: Ambiguous Notes Remain Unstamped (Fail-Open Non-Stamping) ---")
    part_list = """    <score-part id="P1"><part-name>Choir</part-name></score-part>"""

    # 1 XML note, but events list has 2 conflicting events at same pitch/measure
    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        events = [
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 60, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "S"},
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 60, "duration_quarters": 1.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "A"},
        ]

        satb_analyzer.stamp_voices_into_xml(tmp_path, events)

        tree = ET.parse(tmp_path)
        root = tree.getroot()

        p1_note = root.find(".//part[@id='P1']//note")
        assert p1_note.get("data-vx-voice") is None, f"Ambiguous candidate must remain unstamped, got: {p1_note.get('data-vx-voice')}"

        print("[PASS] Test 5: Ambiguous candidate note was correctly rejected and left unstamped.")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

def test_6_musicxml_roundtrip():
    print("\n--- Test 6: Stamped MusicXML Parsing & Well-Formedness Round-Trip ---")
    import music21

    part_list = """    <score-part id="P1"><part-name>Soprano</part-name></score-part>"""
    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch><duration>2</duration><voice>1</voice></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        events = [
            {"part_id": "P1", "measure_number": 1, "pitch_midi": 66, "duration_quarters": 2.0, "tie_type": None, "staff_id": 1, "voice_source": 1, "satb_voice": "S"}
        ]

        stamped = satb_analyzer.stamp_voices_into_xml(tmp_path, events)
        assert stamped

        # Verify standard ElementTree parsing
        tree = ET.parse(tmp_path)
        note = tree.find(".//note")
        assert note.get("data-vx-voice") == "S"

        # Verify music21 parsing of the stamped file
        score = music21.converter.parse(tmp_path)
        notes = list(score.recurse().notes)
        assert len(notes) == 1
        assert notes[0].pitch.midi == 66

        print("[PASS] Test 6: Stamped MusicXML is 100% well-formed and parses cleanly in music21.")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

def test_7_schema_v1_1_compliance():
    print("\n--- Test 7: Public JSON Schema v1.1 Formalization ---")
    assert satb_analyzer.SCHEMA_VERSION == "1.1", f"SCHEMA_VERSION must be '1.1', got {satb_analyzer.SCHEMA_VERSION}"

    # Verify that analyzing a sample score in standard mode emits schema_version == "1.1" and additive v1.1 fields
    part_list = """    <score-part id="P1"><part-name>Soprano</part-name></score-part>"""
    parts = """  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice><lyric><text>Glo-</text></lyric></note>
    </measure>
  </part>"""

    xml_content = make_musicxml_wrapper((part_list, parts))
    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf:
        tf.write(xml_content)
        tmp_path = tf.name

    try:
        result = satb_analyzer.analyze(tmp_path)
        assert result.get("schema_version") == "1.1", f"Output schema must be '1.1', got {result.get('schema_version')}"

        # Verify additive v1.1 fields in production payload
        events = result.get("events", [])
        assert len(events) > 0, "Expected at least 1 event"
        for ev in events:
            assert ev.get("schema_version") == "1.1", f"Event schema must be '1.1', got {ev.get('schema_version')}"
            assert "is_vocal" in ev, "is_vocal must be present in Schema v1.1 events"
            assert isinstance(ev["is_vocal"], bool), "is_vocal must be boolean"
            assert ev["is_vocal"] is True, "Soprano event must have is_vocal == True"
            assert "participating_parts" in ev, "participating_parts must be present in Schema v1.1 events"
            assert isinstance(ev["participating_parts"], list), "participating_parts must be a list"
            assert ev["participating_parts"] == [ev["satb_voice"]], f"Expected [{ev['satb_voice']}], got {ev['participating_parts']}"
            assert "is_unison" in ev, "is_unison must be present in Schema v1.1 events"
            assert isinstance(ev["is_unison"], bool), "is_unison must be boolean"
            assert ev["is_unison"] is False, "Single-voice Soprano note must have is_unison == False"
            assert "unison_type" in ev, "unison_type must be present in Schema v1.1 events"
            assert ev["unison_type"] is None, "Single-voice note must have unison_type == None"
            assert "xml_element_order" in ev, "xml_element_order must be present in Schema v1.1 events"
            assert isinstance(ev["xml_element_order"], int), "xml_element_order must be int"
            assert ev["xml_element_order"] >= 0, "xml_element_order must be >= 0"

        # Section 7: Verify playback_tracks metadata enrichment
        tracks = result.get("score_metadata", {}).get("playback_tracks", [])
        assert len(tracks) > 0, "Expected at least 1 playback track"
        for trk in tracks:
            assert "part_key" in trk, "part_key must be present in playback track"
            assert "part_role" in trk, "part_role must be present in playback track"
            assert trk["part_role"] in ("VOCAL", "ACCOMPANIMENT", "UNKNOWN")
            assert "parent_section" in trk, "parent_section must be present in playback track"
            assert "is_accompaniment" in trk, "is_accompaniment must be present in playback track"
            assert isinstance(trk["is_accompaniment"], bool)

        print("[PASS] Test 7: Production output strictly complies with Schema v1.1 & Section 7 contract.")
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)

if __name__ == "__main__":
    print("=" * 70)
    print("RUNNING TASK 1B DEDICATED VERIFICATION SUITE")
    print("=" * 70)

    test_1_voice_code_map_contract()
    test_2_satb_string_stamping()
    test_3_solo_and_others_stamping()
    test_4_unknown_part_unstamped()
    test_5_ambiguous_notes_unstamped()
    test_6_musicxml_roundtrip()
    test_7_schema_v1_1_compliance()

    print("\n" + "=" * 70)
    print("ALL 7/7 TASK 1B TESTS PASSED SUCCESSFULLY!")
    print("=" * 70)
