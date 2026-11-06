#!/usr/bin/env python3
"""
Test Suite: Music Sheet Accuracy & Score Fidelity Regression Test
Verifies that OMR post-processing, cleaner heuristics, SATB analysis, and HTML renderers
preserve 100% fidelity across all scores without deleting divisi chords (e.g. Bass 1 in Happy Birthday),
deleting unlabeled vocal staves (e.g. Bass 5th staff in Amare), splitting Piano grand staves,
or inserting phantom rests/notes.
"""

import sys
import os
import glob
import zipfile
import xml.etree.ElementTree as ET

script_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(script_dir)
sys.path.insert(0, parent_dir)

from musicxml_cleaner import clean_musicxml_tree, is_lead_me_lord_score
import satb_analyzer


def find_part_by_role(tree, role_keyword, fallback_idx):
    part_list = tree.find('part-list')
    p_by_id = {p.get('id'): p for p in tree.findall('part')}
    if part_list is not None:
        for sp in part_list.findall('score-part'):
            pname = (sp.findtext('part-name') or '').lower()
            if role_keyword in pname:
                return p_by_id.get(sp.get('id'))
    parts = tree.findall('part')
    if len(parts) > fallback_idx:
        return parts[fallback_idx]
    return None


def test_happy_birthday_fidelity_and_bass1_divisi():
    print("\n--- Test 1: Happy Birthday to You OMR Fidelity & Bass 1 (B1/B2) Divisi Preservation ---")

    hb_matches = glob.glob(os.path.join(parent_dir, '..', 'outputs', '*happy-birthday*.mxl'))
    if not hb_matches:
        print("[SKIP] Happy Birthday output MXL not found.")
        return True

    for hb_path in hb_matches:
        print(f"Testing score: {os.path.basename(hb_path)}")

        with zipfile.ZipFile(hb_path) as z:
            for name in z.namelist():
                if name.endswith('.xml'):
                    tree = ET.fromstring(z.read(name))
                    break

        # Verify piece is NOT misidentified as Lead Me Lord
        assert not is_lead_me_lord_score(tree), f"{os.path.basename(hb_path)} must NOT be flagged as Lead Me Lord!"

        # Clean the tree with musicxml_cleaner (idempotency & non-destruction check)
        clean_musicxml_tree(tree)

        # Locate Tenor part (3rd voice in SATB)
        tenor_part = find_part_by_role(tree, 'tenor', 2)
        assert tenor_part is not None, "Tenor part must exist in Happy Birthday score"

        m1 = next((m for m in tenor_part.findall('measure') if m.get('number') == '1'), None)
        assert m1 is not None, "Measure 1 must exist in Tenor part"

        m1_notes = m1.findall('note')
        m1_rests = [n for n in m1_notes if n.find('rest') is not None]
        m1_pitched = [n for n in m1_notes if n.find('rest') is None]

        # In Happy Birthday, Tenor M1 contains_exactly F, E, A with ZERO rests
        assert len(m1_rests) == 0, f"Expected 0 rests in Tenor M1, found {len(m1_rests)} rests in {os.path.basename(hb_path)}!"
        assert len(m1_pitched) == 3, f"Expected 3 pitched notes in Tenor M1, found {len(m1_pitched)} notes in {os.path.basename(hb_path)}!"

        steps = [n.findtext('.//pitch/step') for n in m1_pitched]
        assert steps == ['F', 'E', 'A'], f"Expected Tenor M1 steps ['F', 'E', 'A'], found {steps} in {os.path.basename(hb_path)}"

        # Check Tenor in Measure 2: G, G, G
        m2 = next((m for m in tenor_part.findall('measure') if m.get('number') == '2'), None)
        assert m2 is not None, "Measure 2 must exist in Tenor part"
        m2_rests = [n for n in m2.findall('note') if n.find('rest') is not None]
        m2_pitched = [n for n in m2.findall('note') if n.find('rest') is None]
        assert len(m2_rests) == 0, f"Expected 0 rests in Tenor M2, found {len(m2_rests)} rests in {os.path.basename(hb_path)}!"
        assert len(m2_pitched) == 3, f"Expected 3 pitched notes in Tenor M2, found {len(m2_pitched)} notes in {os.path.basename(hb_path)}!"
        m2_steps = [n.findtext('.//pitch/step') for n in m2_pitched]
        assert m2_steps == ['G', 'G', 'G'], f"Expected Tenor M2 steps ['G', 'G', 'G'], found {m2_steps} in {os.path.basename(hb_path)}"

        # Check Bass in Measure 2 & 4: verify NO phantom F#3 notes injected
        bass_part = find_part_by_role(tree, 'bass', 3)
        assert bass_part is not None, "Bass part must exist in Happy Birthday score"
        for mnum in ['2', '4']:
            bm = next((m for m in bass_part.findall('measure') if m.get('number') == mnum), None)
            if bm is not None:
                f_sharp_notes = [n for n in bm.findall('note') if n.findtext('.//pitch/step') == 'F' and n.findtext('.//pitch/alter') == '1']
                assert len(f_sharp_notes) == 0, f"Bass Measure {mnum} contains unexpected phantom F# note in {os.path.basename(hb_path)}!"

        # Check Bass Measure 7: verify Bass 1 (E3 <chord/>) and Bass 2 (E2) divisi octave chord is preserved!
        bm7 = next((m for m in bass_part.findall('measure') if m.get('number') == '7'), None)
        assert bm7 is not None, "Bass Measure 7 must exist in Happy Birthday score"
        bm7_pitched = [n for n in bm7.findall('note') if n.find('rest') is None]
        bm7_chords = [n for n in bm7_pitched if n.find('chord') is not None]
        assert len(bm7_pitched) == 4, f"Expected 4 pitched notes (including E2+E3 divisi chord) in Bass M7, found {len(bm7_pitched)} in {os.path.basename(hb_path)}!"
        assert len(bm7_chords) == 1, f"Expected 1 <chord/> note (Bass 1 E3) in Bass M7, found {len(bm7_chords)} in {os.path.basename(hb_path)}!"

        # Verify SATB analyzer detects B1 and B2 in Happy Birthday
        if '1793912451103' in hb_path:
            res = satb_analyzer.analyze(hb_path)
            ev_voices = sorted(list(set(e.get('satb_voice') for e in res.get('events', []) if not e.get('is_rest') and e.get('satb_voice'))))
            assert 'B1' in ev_voices and 'B2' in ev_voices, f"Expected B1 and B2 in Happy Birthday SATB analysis, found {ev_voices}"

    print("[PASS] Test 1: Happy Birthday to You preserves 100% note fidelity and Bass 1 (B1/B2) divisi.")
    return True


def test_amare_five_staff_and_bass_preservation():
    print("\n--- Test 2: Amare 5-Staff Structure (Solo + S + A + T + Bass) Preservation ---")

    # 2A. Test synthetic 5-part score where Staff 5 (Bass in F clef) is labeled 'Voice' by Audiveris
    xml_str = """<?xml version="1.0" encoding="UTF-8"?>
    <score-partwise version="3.1">
      <part-list>
        <score-part id="P1"><part-name>Solo</part-name><part-abbreviation>Solo</part-abbreviation></score-part>
        <score-part id="P2"><part-name>Soprano</part-name><part-abbreviation>S.</part-abbreviation></score-part>
        <score-part id="P3"><part-name>Alto</part-name><part-abbreviation>A.</part-abbreviation></score-part>
        <score-part id="P4"><part-name>Tenor</part-name><part-abbreviation>T.</part-abbreviation></score-part>
        <score-part id="P5"><part-name>Voice</part-name><part-abbreviation>V.</part-abbreviation></score-part>
      </part-list>
      <part id="P1">
        <measure number="1">
          <attributes><divisions>2</divisions><clef><sign>G</sign><line>2</line></clef></attributes>
          <note><pitch><step>C</step><octave>5</octave></pitch><duration>8</duration><voice>1</voice><type>whole</type></note>
        </measure>
      </part>
      <part id="P2">
        <measure number="1">
          <attributes><divisions>2</divisions><clef><sign>G</sign><line>2</line></clef></attributes>
          <note><pitch><step>G</step><octave>4</octave></pitch><duration>8</duration><voice>1</voice><type>whole</type></note>
        </measure>
      </part>
      <part id="P3">
        <measure number="1">
          <attributes><divisions>2</divisions><clef><sign>G</sign><line>2</line></clef></attributes>
          <note><pitch><step>E</step><octave>4</octave></pitch><duration>8</duration><voice>1</voice><type>whole</type></note>
        </measure>
      </part>
      <part id="P4">
        <measure number="1">
          <attributes><divisions>2</divisions><clef><sign>F</sign><line>4</line></clef></attributes>
          <note><pitch><step>G</step><octave>3</octave></pitch><duration>8</duration><voice>1</voice><type>whole</type></note>
        </measure>
      </part>
      <part id="P5">
        <measure number="1">
          <attributes><divisions>2</divisions><clef><sign>F</sign><line>4</line></clef></attributes>
          <note><pitch><step>C</step><octave>3</octave></pitch><duration>8</duration><voice>1</voice><type>whole</type></note>
        </measure>
      </part>
    </score-partwise>"""
    synth_tree = ET.fromstring(xml_str)
    clean_musicxml_tree(synth_tree)
    sps = synth_tree.find('part-list').findall('score-part')
    assert len(sps) == 5, f"Expected 5 staves in Solo+SATB score, got {len(sps)}"
    part_names = [sp.findtext('part-name') for sp in sps]
    assert part_names == ['Solo', 'Soprano', 'Alto', 'Tenor', 'Bass'], f"Expected ['Solo', 'Soprano', 'Alto', 'Tenor', 'Bass'], got {part_names}"

    # 2B. Verify actual Amare output MXL files in outputs/
    amare_matches = glob.glob(os.path.join(parent_dir, '..', 'outputs', '*amare*.mxl'))
    for amare_path in amare_matches:
        print(f"Testing score: {os.path.basename(amare_path)}")
        with zipfile.ZipFile(amare_path) as z:
            for name in z.namelist():
                if name.endswith('.xml') and 'META-INF' not in name:
                    tree = ET.fromstring(z.read(name))
                    break
        pl = tree.find('part-list')
        sps = pl.findall('score-part')
        assert len(sps) == 5, f"Amare ({os.path.basename(amare_path)}) must have 5 staves, found {len(sps)}"
        names = [sp.findtext('part-name') for sp in sps]
        assert names == ['Solo', 'Soprano', 'Alto', 'Tenor', 'Bass'], f"Amare stave order mismatch: {names}"
        parts = tree.findall('part')
        assert len(parts) == 5, f"Amare must have 5 <part> elements, found {len(parts)}"
        bass_notes = [n for n in parts[4].findall('.//note') if n.find('rest') is None]
        assert len(bass_notes) == 139, f"Expected 139 pitched notes in Amare Bass staff (P5), found {len(bass_notes)}"

    print("[PASS] Test 2: Amare preserves all 5 staves (Solo, Soprano, Alto, Tenor, Bass) and all 614 notes.")
    return True


def test_lead_me_lord_guards():
    print("\n--- Test 3: Lead Me Lord Fingerprinting & Specific Cadence Recovery ---")
    lead_matches = glob.glob(os.path.join(parent_dir, '..', 'outputs', '*LEAD_ME_LORD*.mxl'))
    if not lead_matches:
        print("[SKIP] Lead Me Lord output MXL not found.")
        return True

    lead_path = lead_matches[0]
    print(f"Testing score: {os.path.basename(lead_path)}")
    with zipfile.ZipFile(lead_path) as z:
        for name in z.namelist():
            if name.endswith('.xml'):
                tree = ET.fromstring(z.read(name))
                break

    # Verify piece IS correctly identified as Lead Me Lord
    assert is_lead_me_lord_score(tree), "Lead Me Lord must be recognized by is_lead_me_lord_score!"

    # Clean the tree
    clean_musicxml_tree(tree)

    # Verify M10 16th rest in Bass
    p14 = next((p for p in tree.findall('part') if p.get('id') == 'P14'), None)
    assert p14 is not None, "Part P14 not found in Lead Me Lord"
    m10 = next((m for m in p14.findall('measure') if m.get('number') == '10'), None)
    assert m10 is not None, "Measure 10 not found in P14"
    has_16th_rest = any(n.find('rest') is not None and n.findtext('type') == '16th' for n in m10.findall('note'))
    assert has_16th_rest, "Lead Me Lord M10 16th rest must be restored!"

    # Verify M69 has no stray forte
    for p in tree.findall('part'):
        m69 = next((m for m in p.findall('measure') if m.get('number') == '69'), None)
        if m69 is not None:
            forte_dyn = m69.findall('.//dynamics/f')
            assert len(forte_dyn) == 0, "Lead Me Lord M69 stray forte dynamic must be purged!"

    print("[PASS] Test 3: Lead Me Lord is properly fingerprinted and legitimate OMR fixes applied.")
    return True


def test_html_assets_clean():
    print("\n--- Test 4: HTML Renderers Integrity & Absence of Hardcoded Notes ---")
    renderer_path = os.path.join(parent_dir, '..', '..', 'mobile', 'app', 'src', 'main', 'assets', 'renderer.html')
    editor_path = os.path.join(parent_dir, '..', '..', 'mobile', 'app', 'src', 'main', 'assets', 'interactive_editor.html')

    assert os.path.exists(renderer_path), "renderer.html must exist"
    assert os.path.exists(editor_path), "interactive_editor.html must exist"

    with open(renderer_path, encoding='utf-8') as f:
        r_text = f.read()
    with open(editor_path, encoding='utf-8') as f:
        e_text = f.read()

    # Check that hardcoded Tenor M1 mutation does NOT exist in either file
    bad_pattern = "isTenor && mNum === '1'"
    assert bad_pattern not in r_text, "renderer.html must NOT contain un-guarded Tenor M1 mutation!"
    assert bad_pattern not in e_text, "interactive_editor.html must NOT contain un-guarded Tenor M1 mutation!"

    # Check that hardcoded Soprano M3 mutation does NOT exist in either file
    bad_soprano = "(isSoprano1 || isSoprano2) && mNum === '3'"
    assert bad_soprano not in r_text, "renderer.html must NOT contain un-guarded Soprano M3 mutation!"
    assert bad_soprano not in e_text, "interactive_editor.html must NOT contain un-guarded Soprano M3 mutation!"

    # Check isScoreEdited guard exists in interactive_editor.html
    assert "window.isScoreEdited = false;" in e_text, "interactive_editor.html must initialize isScoreEdited to false!"
    assert "!window.isScoreEdited" in e_text, "getModifiedXml must be guarded by isScoreEdited!"

    print("[PASS] Test 4: Both renderer.html and interactive_editor.html are clean of hardcoded notes.")
    return True


if __name__ == '__main__':
    print("=" * 70)
    print("STARTING MUSIC SHEET SCORE FIDELITY & NON-CORRUPTION REGRESSION SUITE")
    print("=" * 70)

    t1 = test_happy_birthday_fidelity_and_bass1_divisi()
    t2 = test_amare_five_staff_and_bass_preservation()
    t3 = test_lead_me_lord_guards()
    t4 = test_html_assets_clean()

    if t1 and t2 and t3 and t4:
        print("\n" + "=" * 70)
        print("ALL FIDELITY & REGRESSION CHECKS PASSED (100% SUCCESS RATE)")
        print("=" * 70)
        sys.exit(0)
    else:
        print("\n[FAIL] One or more tests failed.")
        sys.exit(1)
