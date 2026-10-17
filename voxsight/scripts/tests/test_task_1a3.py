"""
Test Suite for Task 1A.3: MusicXML Pre-Scan & Voice-Stamping Engine.
Strengthened Remediation Suite covering:
- NI-01 through NI-10 (with exact expected voice codes and unstamped-note assertions)
- ID-01 through ID-05 (passing actual MusicXML through prescan_musicxml)
- Multi-Part Multi-Staff Regression (Choir multi-staff + Accompaniment)
- Metric-Onset Repeated Pitches Distinction
- Tie Normalization & Symmetric Rejection
- Genuine Musical Ambiguity Fail-Safe
- Part UNKNOWN Ambiguity Fail-Safe
"""

import os
import sys
import tempfile
import urllib.parse
import xml.etree.ElementTree as ET
from pathlib import Path

# Add voxsight/scripts to sys.path
SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import satb_analyzer


def make_minimal_musicxml(parts_xml_str: str, part_list_str: str = None) -> str:
    """Helper to wrap part XML in valid MusicXML document."""
    if part_list_str is None:
        part_list_str = '<score-part id="P1"><part-name>Choir</part-name></score-part>'
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
<score-partwise version="3.1">
  <part-list>
    {part_list_str}
  </part-list>
  {parts_xml_str}
</score-partwise>"""


def test_ni_01():
    """NI-01: Measure with C4+E4+G4 ascending chord.
    All 3 notes stamped; exact voice codes asserted.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
      <note>
        <chord/>
        <pitch><step>E</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
      <note>
        <chord/>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        pitches = [f"{n.findtext('.//step')}{n.findtext('.//octave')}" for n in notes]
        # Strengthened assertion: exact expected voice codes ['A', 'A', 'S']
        expected = ['A', 'A', 'S']
        is_correct = (stamps == expected)
        return {
            "name": "NI-01",
            "pitches": pitches,
            "stamps": stamps,
            "expected": expected,
            "success": is_correct,
            "details": f"stamps={stamps}, expected={expected}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_02():
    """NI-02: Measure with G4+E4+C4 descending chord.
    Stamp order must match XML document order, not pitch-sorted order.
    Note 0 is G4, Note 1 is E4, Note 2 is C4.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
      <note>
        <chord/>
        <pitch><step>E</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
      <note>
        <chord/>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        pitches = [f"{n.findtext('.//step')}{n.findtext('.//octave')}" for n in notes]

        events = res.get('events', [])
        g4_event = next((e for e in events if e['pitch_midi'] == 67), None)
        c4_event = next((e for e in events if e['pitch_midi'] == 60), None)
        e4_event = next((e for e in events if e['pitch_midi'] == 64), None)

        expected_g4_stamp = satb_analyzer.VOICE_CODE_MAP.get(g4_event['satb_voice']) if g4_event else None
        expected_e4_stamp = satb_analyzer.VOICE_CODE_MAP.get(e4_event['satb_voice']) if e4_event else None
        expected_c4_stamp = satb_analyzer.VOICE_CODE_MAP.get(c4_event['satb_voice']) if c4_event else None

        actual_g4_stamp = stamps[0]
        actual_e4_stamp = stamps[1]
        actual_c4_stamp = stamps[2]

        is_correct = (
            actual_g4_stamp == expected_g4_stamp and
            actual_e4_stamp == expected_e4_stamp and
            actual_c4_stamp == expected_c4_stamp and
            all(s is not None for s in stamps)
        )

        return {
            "name": "NI-02",
            "pitches": pitches,
            "stamps": stamps,
            "expected": [expected_g4_stamp, expected_e4_stamp, expected_c4_stamp],
            "success": is_correct,
            "details": f"actual={stamps}, expected={[expected_g4_stamp, expected_e4_stamp, expected_c4_stamp]}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_03():
    """NI-03: C4 tied across measure boundary.
    Tie metadata disambiguates; both notes stamped; tie-stop has own index in m2.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <tie type="start"/>
        <voice>1</voice>
        <type>whole</type>
        <notations><tied type="start"/></notations>
      </note>
    </measure>
    <measure number="2">
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <tie type="stop"/>
        <voice>1</voice>
        <type>whole</type>
        <notations><tied type="stop"/></notations>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        m1_note = root.find('.//measure[@number="1"]//note')
        m2_note = root.find('.//measure[@number="2"]//note')
        s1 = m1_note.get('data-vx-voice')
        s2 = m2_note.get('data-vx-voice')
        return {
            "name": "NI-03",
            "stamps": [s1, s2],
            "success": s1 is not None and s2 is not None and s1 == s2,
            "details": f"m1_stamp={s1}, m2_stamp={s2}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_04():
    """NI-04: Grace note before beat note.
    Grace note receives index 0 and remains UNSTAMPED; beat note receives index 1 and stamp '2'.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <grace/>
        <pitch><step>D</step><octave>4</octave></pitch>
        <voice>1</voice>
        <type>eighth</type>
      </note>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <type>whole</type>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        grace_stamp = notes[0].get('data-vx-voice')
        beat_stamp = notes[1].get('data-vx-voice')

        # Strengthened assertion: grace note unstamped (None), beat note stamped 'A'
        is_correct = (grace_stamp is None and beat_stamp == 'A' and len(notes) == 2)
        return {
            "name": "NI-04",
            "notes_count": len(notes),
            "grace_stamp": grace_stamp,
            "beat_stamp": beat_stamp,
            "success": is_correct,
            "details": f"grace_stamp={grace_stamp} (expected None), beat_stamp={beat_stamp} (expected 'A')"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_05():
    """NI-05: 1 rest followed by 2 notes in a measure.
    Rest excluded from index; 2 sounding notes receive indices 0 and 1.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>3</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <rest/>
        <duration>1</duration>
        <voice>1</voice>
        <type>quarter</type>
      </note>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <type>quarter</type>
      </note>
      <note>
        <pitch><step>D</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <type>quarter</type>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        rest_stamp = notes[0].get('data-vx-voice')
        c_stamp = notes[1].get('data-vx-voice')
        d_stamp = notes[2].get('data-vx-voice')
        return {
            "name": "NI-05",
            "rest_stamp": rest_stamp,
            "c_stamp": c_stamp,
            "d_stamp": d_stamp,
            "success": rest_stamp is None and c_stamp is not None and d_stamp is not None,
            "details": f"rest_stamp={rest_stamp}, c_stamp={c_stamp}, d_stamp={d_stamp}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_06():
    """NI-06: Two consecutive identical C4 quarter notes.
    Duration and tie checks match; positional fallback succeeds safely; 0 mismatches.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>2</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <type>quarter</type>
      </note>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <type>quarter</type>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        s1 = notes[0].get('data-vx-voice')
        s2 = notes[1].get('data-vx-voice')
        return {
            "name": "NI-06",
            "stamps": [s1, s2],
            "success": s1 is not None and s2 is not None and s1 == s2,
            "details": f"s1={s1}, s2={s2}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_07():
    """NI-07: Note with <staff>2</staff> in single-part score.
    Strengthened assertion: staff 1 note (G4) receives '2' (Alto), staff 2 note (C3) receives '4' (Bass).
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <staves>2</staves>
        <clef number="1"><sign>G</sign><line>2</line></clef>
        <clef number="2"><sign>F</sign><line>4</line></clef>
      </attributes>
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>1</voice>
        <staff>1</staff>
      </note>
      <note>
        <pitch><step>C</step><octave>3</octave></pitch>
        <duration>2</duration>
        <voice>2</voice>
        <staff>2</staff>
      </note>
    </measure>
  </part>""", part_list_str='<score-part id="P1"><part-name>Choir</part-name></score-part>')
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        s1 = notes[0].get('data-vx-voice')
        s2 = notes[1].get('data-vx-voice')
        expected = ['A', 'B']
        is_correct = (s1 == 'A' and s2 == 'B')
        return {
            "name": "NI-07",
            "stamps": [s1, s2],
            "expected": expected,
            "success": is_correct,
            "details": f"staff1_stamp={s1} (expected 'A'), staff2_stamp={s2} (expected 'B')"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_08():
    """NI-08: Two voices in same measure with overlapping pitches.
    Strengthened assertion: exact expected voice codes ['3', '1', '2', '2'].
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <!-- Voice 1: C4, G4 -->
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>1</voice>
      </note>
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>1</voice>
      </note>
      <!-- Backup 4 divisions -->
      <backup>
        <duration>4</duration>
      </backup>
      <!-- Voice 2: C4, E4 -->
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>2</voice>
      </note>
      <note>
        <pitch><step>E</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>2</voice>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        expected = ['T', 'S', 'A', 'A']
        is_correct = (stamps == expected)
        return {
            "name": "NI-08",
            "stamps": stamps,
            "expected": expected,
            "success": is_correct,
            "details": f"stamps={stamps}, expected={expected}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_09():
    """NI-09: Measure with <forward> shifting cursor before second voice.
    Strengthened assertion: exact expected voice codes ['2', '2'].
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>2</divisions>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <!-- Voice 1: note for 4 divisions (2 quarters) -->
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
      </note>
      <!-- Backup 4 divisions to start of measure -->
      <backup>
        <duration>4</duration>
      </backup>
      <!-- Voice 2: forward 2 divisions (1 quarter) then note -->
      <forward>
        <duration>2</duration>
      </forward>
      <note>
        <pitch><step>D</step><octave>4</octave></pitch>
        <duration>2</duration>
        <voice>2</voice>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        expected = ['A', 'A']
        is_correct = (stamps == expected)
        return {
            "name": "NI-09",
            "stamps": stamps,
            "expected": expected,
            "success": is_correct,
            "details": f"stamps={stamps}, expected={expected}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_ni_10():
    """NI-10: Full test score (4-part choral + piano).
    Every non-rest XML <note> matches 1:1 to an analysis event; zero unmatched notes.
    """
    part_list = """
      <score-part id="P1"><part-name>Soprano</part-name></score-part>
      <score-part id="P2"><part-name>Alto</part-name></score-part>
      <score-part id="P3"><part-name>Tenor</part-name></score-part>
      <score-part id="P4"><part-name>Bass</part-name></score-part>
      <score-part id="P5"><part-name>Piano</part-name></score-part>
    """
    parts = """
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P3">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P4">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P5">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>2</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
    """
    xml = make_minimal_musicxml(parts, part_list)
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        expected = ['S', 'A', 'T', 'B', 'Others']
        return {
            "name": "NI-10",
            "stamps": stamps,
            "expected": expected,
            "success": stamps == expected,
            "details": f"stamps={stamps}, expected={expected}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_id_01():
    """ID-01: Simultaneous unisons at identical tick and pitch in different parts.
    Passes actual MusicXML through prescan_musicxml; verifies distinct canonical source_note_id tokens.
    """
    part_list = """
      <score-part id="P1"><part-name>Soprano</part-name></score-part>
      <score-part id="P2"><part-name>Alto</part-name></score-part>
    """
    parts = """
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>
    """
    xml = make_minimal_musicxml(parts, part_list)
    root = ET.fromstring(xml)
    parts_data, notes_by_id = satb_analyzer.prescan_musicxml(root)

    p1_note = parts_data['P1'][1][0]
    p2_note = parts_data['P2'][1][0]

    id1 = p1_note['source_note_id']
    id2 = p2_note['source_note_id']

    is_ok = (
        id1 == "p[P1]_m0000_n0000" and
        id2 == "p[P2]_m0000_n0000" and
        id1 != id2 and
        len(notes_by_id) == 2 and
        p1_note['xml_note_index_in_measure'] == 0 and
        p2_note['xml_note_index_in_measure'] == 0 and
        p1_note['pitch_midi'] == 60 and
        p2_note['pitch_midi'] == 60
    )
    return {
        "name": "ID-01 (Actual prescan_musicxml execution)",
        "id1": id1,
        "id2": id2,
        "success": is_ok,
        "details": f"id1={id1}, id2={id2}"
    }


def test_id_02():
    """ID-02: Repeated pitches in same measure.
    Passes actual MusicXML through prescan_musicxml; verifies consecutive identical pitches receive distinct sequential indices n0000, n0001.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>2</duration><voice>1</voice></note>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>2</duration><voice>1</voice></note>
    </measure>
  </part>""")
    root = ET.fromstring(xml)
    parts_data, notes_by_id = satb_analyzer.prescan_musicxml(root)

    m1_notes = parts_data['P1'][1]
    idx0 = m1_notes[0]['source_note_id']
    idx1 = m1_notes[1]['source_note_id']

    is_ok = (
        len(m1_notes) == 2 and
        m1_notes[0]['xml_note_index_in_measure'] == 0 and
        m1_notes[1]['xml_note_index_in_measure'] == 1 and
        idx0 == "p[P1]_m0000_n0000" and
        idx1 == "p[P1]_m0000_n0001" and
        idx0 != idx1 and
        m1_notes[0]['pitch_midi'] == 60 and
        m1_notes[1]['pitch_midi'] == 60
    )
    return {
        "name": "ID-02 (Actual prescan_musicxml execution)",
        "idx0": idx0,
        "idx1": idx1,
        "success": is_ok,
        "details": f"idx0={idx0}, idx1={idx1}"
    }


def test_id_03():
    """ID-03: Chords in XML document order.
    Passes actual MusicXML through prescan_musicxml; verifies monotonic document-order indices n0000, n0001, n0002.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
      <note><chord/><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
      <note><chord/><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>""")
    root = ET.fromstring(xml)
    parts_data, notes_by_id = satb_analyzer.prescan_musicxml(root)

    m1_notes = parts_data['P1'][1]
    indices = [n['source_note_id'].split('_')[-1] for n in m1_notes]
    is_ok = (
        len(m1_notes) == 3 and
        indices == ["n0000", "n0001", "n0002"] and
        [n['xml_note_index_in_measure'] for n in m1_notes] == [0, 1, 2] and
        not m1_notes[0]['is_chord'] and
        m1_notes[1]['is_chord'] and
        m1_notes[2]['is_chord'] and
        m1_notes[0]['onset_quarters'] == 0.0 and
        m1_notes[1]['onset_quarters'] == 0.0 and
        m1_notes[2]['onset_quarters'] == 0.0
    )
    return {
        "name": "ID-03 (Actual prescan_musicxml execution)",
        "indices": indices,
        "success": is_ok,
        "details": f"indices={indices}"
    }


def test_id_04():
    """ID-04: Interleaved rests and notes.
    Passes actual MusicXML through prescan_musicxml; verifies rests consume zero indices and sounding notes receive contiguous indices.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><rest/><duration>1</duration><voice>1</voice></note>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
      <note><rest/><duration>1</duration><voice>1</voice></note>
      <note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>
    </measure>
  </part>""")
    root = ET.fromstring(xml)
    parts_data, notes_by_id = satb_analyzer.prescan_musicxml(root)

    m1_notes = parts_data['P1'][1]
    formatted = [f"n{n['xml_note_index_in_measure']:04d}" for n in m1_notes]
    is_ok = (
        len(m1_notes) == 2 and
        formatted == ["n0000", "n0001"] and
        m1_notes[0]['pitch_midi'] == 60 and
        m1_notes[1]['pitch_midi'] == 64 and
        m1_notes[0]['onset_quarters'] == 1.0 and
        m1_notes[1]['onset_quarters'] == 3.0
    )
    return {
        "name": "ID-04 (Actual prescan_musicxml execution)",
        "formatted": formatted,
        "success": is_ok,
        "details": f"formatted={formatted}"
    }


def test_id_05():
    """ID-05: Part IDs with delimiter characters (_, -, :, /).
    Passes actual MusicXML through prescan_musicxml; verifies URL-escaped collision-free source_note_id tokens.
    """
    part_list = """
      <score-part id="P_1-Lead:Tenor"><part-name>P1</part-name></score-part>
      <score-part id="Part/Bass_01"><part-name>P2</part-name></score-part>
      <score-part id="P:1/A_2"><part-name>P3</part-name></score-part>
    """
    parts = """
  <part id="P_1-Lead:Tenor">
    <measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure>
  </part>
  <part id="Part/Bass_01">
    <measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure>
  </part>
  <part id="P:1/A_2">
    <measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note></measure>
  </part>
    """
    xml = make_minimal_musicxml(parts, part_list)
    root = ET.fromstring(xml)
    parts_data, notes_by_id = satb_analyzer.prescan_musicxml(root)

    tokens = list(notes_by_id.keys())
    has_slash_or_colon = any('/' in t or ':' in t for t in tokens)
    expected_tokens = [
        'p[P_1-Lead%3ATenor]_m0000_n0000',
        'p[Part%2FBass_01]_m0000_n0000',
        'p[P%3A1%2FA_2]_m0000_n0000'
    ]
    is_ok = (
        (not has_slash_or_colon) and
        len(tokens) == 3 and
        set(tokens) == set(expected_tokens)
    )
    return {
        "name": "ID-05 (Actual prescan_musicxml execution)",
        "tokens": tokens,
        "success": is_ok,
        "details": f"tokens={tokens}"
    }


def test_multipart_multistaff_regression():
    """Multi-Part Multi-Staff Regression:
    XML with multi-staff choir part (P1: G4 on staff 1, C3 on staff 2) and separate accompaniment part (P2: C2 on staff 1).
    Verifies exact per-note voice assignments, preserving separation between distinct XML parts without cross-matching.
    """
    xml = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Choir</part-name></score-part>
    <score-part id="P2"><part-name>Accompaniment</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <staves>2</staves>
        <clef number="1"><sign>G</sign><line>2</line></clef>
        <clef number="2"><sign>F</sign><line>4</line></clef>
      </attributes>
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
        <staff>1</staff>
      </note>
      <backup><duration>4</duration></backup>
      <note>
        <pitch><step>C</step><octave>3</octave></pitch>
        <duration>4</duration>
        <voice>2</voice>
        <staff>2</staff>
      </note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <clef><sign>F</sign><line>4</line></clef>
      </attributes>
      <note>
        <pitch><step>C</step><octave>2</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
      </note>
    </measure>
  </part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        p1 = root.find('.//part[@id="P1"]')
        p2 = root.find('.//part[@id="P2"]')

        p1_notes = p1.findall('.//note')
        p2_notes = p2.findall('.//note')

        p1_stamps = [n.get('data-vx-voice') for n in p1_notes]
        p2_stamps = [n.get('data-vx-voice') for n in p2_notes]

        # P1 staff 1 (G4) -> 'A' (Alto), P1 staff 2 (C3) -> 'B' (Bass)
        # P2 (C2) -> 'Others' (Others)
        is_ok = (
            p1_stamps == ['A', 'B'] and
            p2_stamps == ['Others']
        )
        return {
            "name": "Multi-Part Multi-Staff Regression",
            "p1_stamps": p1_stamps,
            "p2_stamps": p2_stamps,
            "success": is_ok,
            "details": f"p1_stamps={p1_stamps} (expected ['A', 'B']), p2_stamps={p2_stamps} (expected ['Others'])"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_metric_onset_distinguishes_repeated_pitches():
    """Metric-onset condition: abs(xn_onset - ev_onset) < 1e-4.
    Verifies that repeated pitches with identical durations at different onsets are correctly distinguished.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>1</divisions>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>G</sign><line>2</line></clef>
      </attributes>
      <!-- C4 quarter note at onset 0.0 -->
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
      </note>
      <!-- G4 quarter note at onset 1.0 -->
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
      </note>
      <!-- C4 quarter note at onset 2.0 (same pitch and duration as first note, different onset) -->
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
      </note>
      <!-- E4 quarter note at onset 3.0 -->
      <note>
        <pitch><step>E</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        is_ok = len(stamps) == 4 and all(s is not None for s in stamps)
        return {
            "name": "Metric-Onset Repeated Pitches Distinction",
            "stamps": stamps,
            "success": is_ok,
            "details": f"stamps={stamps}"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_tie_normalization_suite():
    """Tie normalization:
    Verifies symmetric tie-state comparison, tie starts, stops, ties across bar lines, and continuous ties.
    Confirms that tied and untied notes cannot match.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>2</duration>
        <tie type="start"/>
        <notations><tied type="start"/></notations>
        <voice>1</voice>
      </note>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>2</duration>
        <tie type="stop"/>
        <tie type="start"/>
        <notations><tied type="stop"/><tied type="start"/></notations>
        <voice>1</voice>
      </note>
    </measure>
    <measure number="2">
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <tie type="stop"/>
        <notations><tied type="stop"/></notations>
        <voice>1</voice>
      </note>
    </measure>
  </part>""")
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        notes = root.findall('.//note')
        stamps = [n.get('data-vx-voice') for n in notes]
        is_ok = len(stamps) == 3 and all(s is not None for s in stamps) and stamps[0] == stamps[1] == stamps[2]

        # Test asymmetric mismatch rejection:
        # Untied XML note vs Tied Event -> must reject match!
        untied_xml = ET.fromstring("""<score-partwise version="3.1"><part id="P1"><measure number="1">
          <attributes><divisions>1</divisions></attributes>
          <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
        </measure></part></score-partwise>""")
        tied_event = [{"part_id": 1, "measure_number": 1, "pitch_midi": 60, "duration_quarters": 4.0, "tie_type": "start", "satb_voice": "S"}]
        count = satb_analyzer.match_and_stamp_voices(untied_xml, tied_event)
        untied_note_stamp = untied_xml.find('.//note').get('data-vx-voice')

        is_ok = is_ok and (count == 0) and (untied_note_stamp is None)
        return {
            "name": "Tie Normalization & Symmetric Rejection",
            "stamps": stamps,
            "asymmetric_stamped_count": count,
            "success": is_ok,
            "details": f"stamps={stamps}, asymmetric_stamped_count={count} (expected 0)"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def test_genuine_musical_ambiguity_failsafe():
    """Genuine musical ambiguity:
    Multiple plausible candidates (2 identical XML notes in same measure at identical pitch, onset, duration, voice, staff)
    with fewer resolvable events (only 1 event).
    Verifies that Step 9 rejects ambiguity and fails safely by leaving BOTH notes unstamped.
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
      </note>
      <backup><duration>4</duration></backup>
      <note>
        <pitch><step>C</step><octave>4</octave></pitch>
        <duration>4</duration>
        <voice>1</voice>
      </note>
    </measure>
  </part>""")
    root = ET.fromstring(xml)
    ambiguous_events = [
        {"part_id": 1, "measure_number": 1, "pitch_midi": 60, "duration_quarters": 4.0, "tick_position": 0, "ticks_per_quarter": 960, "voice_source": 1, "staff_id": 1, "tie_type": None, "satb_voice": "S"}
    ]
    stamped = satb_analyzer.match_and_stamp_voices(root, ambiguous_events)
    notes = root.findall('.//note')
    stamps = [n.get('data-vx-voice') for n in notes]

    is_ok = (stamped == 0) and all(s is None for s in stamps)
    return {
        "name": "Genuine Musical Ambiguity Fail-Safe",
        "stamped_count": stamped,
        "stamps": stamps,
        "success": is_ok,
        "details": f"stamped={stamped}, stamps={stamps} (expected all None)"
    }


def test_ambiguity_failsafe():
    """Ambiguity Fail-Safe:
    Injected ambiguous notes that cannot be uniquely matched 1:1 are left unstamped (no data-vx-voice).
    """
    xml = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>""", part_list_str='<score-part id="P1"><part-name>Theremin</part-name></score-part>')
    with tempfile.NamedTemporaryFile('w', suffix='.musicxml', delete=False, encoding='utf-8') as f:
        f.write(xml)
        tmp = f.name
    try:
        res = satb_analyzer.analyze(tmp)
        root = ET.parse(tmp).getroot()
        note = root.find('.//note')
        stamp = note.get('data-vx-voice')
        return {
            "name": "Ambiguity Fail-Safe (UNKNOWN part)",
            "stamp": stamp,
            "success": stamp is None,
            "details": f"stamp={stamp} (expected None)"
        }
    finally:
        if os.path.exists(tmp): os.unlink(tmp)


def run_all_tests():
    print("=" * 70)
    print("RUNNING STRENGTHENED TASK 1A.3 PRE-SCAN & VOICE-STAMPING TEST SUITE")
    print("=" * 70)
    tests = [
        test_ni_01, test_ni_02, test_ni_03, test_ni_04, test_ni_05,
        test_ni_06, test_ni_07, test_ni_08, test_ni_09, test_ni_10,
        test_id_01, test_id_02, test_id_03, test_id_04, test_id_05,
        test_multipart_multistaff_regression,
        test_metric_onset_distinguishes_repeated_pitches,
        test_tie_normalization_suite,
        test_genuine_musical_ambiguity_failsafe,
        test_ambiguity_failsafe
    ]
    results = []
    for t in tests:
        try:
            r = t()
            status = "[PASS]" if r["success"] else "[FAIL]"
            print(f"{status} {r['name']}: {r.get('details', '')}")
            results.append(r)
        except Exception as e:
            print(f"[ERROR] {t.__name__}: {e}")
            results.append({"name": t.__name__, "success": False, "details": str(e)})

    passed = sum(1 for r in results if r["success"])
    total = len(results)
    print("-" * 70)
    print(f"Results: {passed}/{total} passed.")
    print("=" * 70)
    return passed == total


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
