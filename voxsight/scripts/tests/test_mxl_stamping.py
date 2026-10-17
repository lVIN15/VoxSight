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
    print("[PASS] MXL test passed cleanly!")
finally:
    if os.path.exists(mxl_path):
        os.remove(mxl_path)
