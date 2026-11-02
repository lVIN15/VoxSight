import sys, os, tempfile, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))
TEST_DIR = Path(__file__).resolve().parent
if str(TEST_DIR) not in sys.path:
    sys.path.insert(0, str(TEST_DIR))
import satb_analyzer
from test_task_1a3 import make_minimal_musicxml

# Test MXL round-trip
xml_text = make_minimal_musicxml("""
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>1</voice></note>
    </measure>
  </part>""")

with tempfile.NamedTemporaryFile(suffix='.mxl', delete=False) as tf:
    mxl_path = tf.name

try:
    with zipfile.ZipFile(mxl_path, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr('score.xml', xml_text.encode('utf-8'))
        zf.writestr('META-INF/container.xml', b'<container/>')

    res = satb_analyzer.analyze(mxl_path)
    assert res['score_metadata']['validation_passed'], "Validation failed on MXL"

    # Read back stamped MXL
    with zipfile.ZipFile(mxl_path, 'r') as zf:
        stamped_xml = zf.read('score.xml')
        container = zf.read('META-INF/container.xml')
        assert container == b'<container/>', "Container file corrupted in MXL"

    root = ET.fromstring(stamped_xml)
    note = root.find('.//note')
    stamp = note.get('data-vx-voice')
    print("MXL note stamped voice:", stamp)
    assert stamp in ['S', 'A', 'T', 'B', 'Solo', 'Others'], f"Unexpected stamp {stamp}"
    assert note.get('data-vx-id') is not None, "Stable note identity was not stamped"
    assert res['events'][0]['source_note_id'] == int(note.get('data-vx-id'))
    assert res['events'][0]['source_part_id'] == 'P1'
    print("[PASS] MXL test passed cleanly!")
finally:
    if os.path.exists(mxl_path):
        os.remove(mxl_path)


# Explicit editor assignments survive re-analysis; unmarked legacy labels are
# recalculated as backend output rather than being mistaken for user decisions.
override_xml = ET.fromstring("""<score-partwise version="3.1">
  <part-list><score-part id="P1"><part-name>Choir</part-name></score-part></part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes>
    <note data-vx-id="7" data-vx-voice="B" data-vx-voice-source="user"><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration></note>
    <note data-vx-voice="B"><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration></note>
  </measure></part>
</score-partwise>""")
override_events = [
    {"part_id": 1, "measure_number": 1, "pitch_midi": None, "satb_voice": "S"},
    {"part_id": 1, "measure_number": 1, "pitch_midi": None, "satb_voice": "T"},
]
stamped = satb_analyzer.match_and_stamp_voices(override_xml, override_events)
override_notes = override_xml.findall('.//note')
assert stamped == 2
assert override_notes[0].get('data-vx-voice') == 'B'
assert override_notes[0].get('data-vx-voice-source') == 'user_override'
assert override_events[0]['satb_voice'] == 'B'
assert override_notes[1].get('data-vx-voice') == 'T'
assert override_notes[1].get('data-vx-voice-source') == 'backend'
assert override_events[1]['source_note_id'] == int(override_notes[1].get('data-vx-id'))
print("[PASS] User voice overrides and source note IDs survive backend re-analysis.")
