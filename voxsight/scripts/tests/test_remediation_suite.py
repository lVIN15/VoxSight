#!/usr/bin/env python3
"""
VoxSight Phase 1A.2 — Remediation Verification Test Suite
Tests Findings 1, 2, 3, and 4 against the active codebase.
"""

import sys
import os
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import satb_analyzer
import musicxml_cleaner


def test_finding_1_instrument_metadata():
    print("\n--- Running Test Finding 1: Instrument Metadata Consideration ---")
    test_cases = [
        ("Alto", "Alto Saxophone", "Others", "Others"),
        ("Tenor", "Tenor Trombone", "Others", "Others"),
        ("Bass", "Acoustic Bass", "Others", "Others"),
        ("Soprano", "Soprano Voice", "S", "S"),
    ]

    for p_name, instr_name, expected_voice, expected_code in test_cases:
        xml_str = f"""<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1">
      <part-name>{p_name}</part-name>
      <score-instrument id="P1-I1">
        <instrument-name>{instr_name}</instrument-name>
      </score-instrument>
    </score-part>
    <score-part id="P2">
      <part-name>Choir</part-name>
    </score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><type>whole</type></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note>
    </measure>
  </part>
</score-partwise>"""

        with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
            tf.write(xml_str)
            t_path = tf.name

        try:
            res = satb_analyzer.analyze(t_path)
            p1_ev = [e for e in res["events"] if e["part_id"] == 1][0]
            actual_voice = p1_ev["satb_voice"]

            # Read stamped voice code from modified XML on disk
            tree = ET.parse(t_path)
            p1_elem = tree.find(".//part[@id='P1']")
            stamped_code = None
            for n in p1_elem.findall(".//note"):
                stamped_code = n.get("data-vx-voice")
                if stamped_code:
                    break

            print(f"  Part '{p_name}' + Instrument '{instr_name}': actual_voice='{actual_voice}' (expected '{expected_voice}'), stamped='{stamped_code}' (expected '{expected_code}')")
            assert actual_voice == expected_voice, f"Expected {expected_voice}, got {actual_voice}"
            assert stamped_code == expected_code, f"Expected stamp {expected_code}, got {stamped_code}"
        finally:
            if os.path.exists(t_path):
                os.remove(t_path)

    print("[PASS] Finding 1: All instrument metadata test cases passed successfully.")


def test_finding_2_upload_validation():
    print("\n--- Running Test Finding 2: Upload-Validation Gate Compatibility ---")

    # 2a: Wind Quartet titled Choral Fantasy (purely instrumental, 0 lyrics) -> must be rejected
    xml_wind = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <movement-title>Choral Fantasy for Wind Quartet</movement-title>
  <part-list>
    <score-part id="P1"><part-name>Flute</part-name></score-part>
    <score-part id="P2"><part-name>Oboe</part-name></score-part>
    <score-part id="P3"><part-name>Clarinet</part-name></score-part>
    <score-part id="P4"><part-name>Bassoon</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration></note></measure></part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_wind)
        t_path = tf.name
    try:
        is_unsup, msg = satb_analyzer.check_unsupported_score(t_path)
        print(f"  Wind Quartet with 'Choral' in title: is_unsupported={is_unsup} (expected True)")
        assert is_unsup is True, "Purely instrumental score with choral title was not rejected!"
    finally:
        os.remove(t_path)

    # 2b: Lyricless Soprano, Alto, Tenor, Bass -> must be accepted
    xml_satb_full = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Soprano</part-name></score-part>
    <score-part id="P2"><part-name>Alto</part-name></score-part>
    <score-part id="P3"><part-name>Tenor</part-name></score-part>
    <score-part id="P4"><part-name>Bass</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration></note></measure></part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_satb_full)
        t_path = tf.name
    try:
        is_unsup, msg = satb_analyzer.check_unsupported_score(t_path)
        print(f"  Lyricless full SATB: is_unsupported={is_unsup} (expected False)")
        assert is_unsup is False, "Lyricless full SATB score was incorrectly rejected!"
    finally:
        os.remove(t_path)

    # 2c: Lyricless S, A, T, B -> must be accepted
    xml_satb_short = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>S</part-name></score-part>
    <score-part id="P2"><part-name>A</part-name></score-part>
    <score-part id="P3"><part-name>T</part-name></score-part>
    <score-part id="P4"><part-name>B</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration></note></measure></part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_satb_short)
        t_path = tf.name
    try:
        is_unsup, msg = satb_analyzer.check_unsupported_score(t_path)
        print(f"  Lyricless S, A, T, B: is_unsupported={is_unsup} (expected False)")
        assert is_unsup is False, "Lyricless S, A, T, B score was incorrectly rejected!"
    finally:
        os.remove(t_path)

    # 2d: Lyricless Sop., Alt., Ten., Bas. -> must be accepted
    xml_satb_abbr = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Sop.</part-name></score-part>
    <score-part id="P2"><part-name>Alt.</part-name></score-part>
    <score-part id="P3"><part-name>Ten.</part-name></score-part>
    <score-part id="P4"><part-name>Bas.</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration></note></measure></part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_satb_abbr)
        t_path = tf.name
    try:
        is_unsup, msg = satb_analyzer.check_unsupported_score(t_path)
        print(f"  Lyricless Sop., Alt., Ten., Bas.: is_unsupported={is_unsup} (expected False)")
        assert is_unsup is False, "Lyricless standard abbreviated SATB score was incorrectly rejected!"
    finally:
        os.remove(t_path)

    # 2e: Generic part names with a choral title and no lyrics -> must be rejected
    xml_generic = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <work><work-title>Choral Hymn for Choir</work-title></work>
  <part-list>
    <score-part id="P1"><part-name>Part 1</part-name></score-part>
    <score-part id="P2"><part-name>Part 2</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note></measure></part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_generic)
        t_path = tf.name
    try:
        is_unsup, msg = satb_analyzer.check_unsupported_score(t_path)
        print(f"  Generic Part 1/2 with Choral title (0 lyrics): is_unsupported={is_unsup} (expected True)")
        assert is_unsup is True, "Generic part names with choral title and 0 lyrics was not rejected!"
    finally:
        os.remove(t_path)

    print("[PASS] Finding 2: Upload-validation gate tests passed successfully.")


def test_finding_3_instrumental_solo_identity():
    print("\n--- Running Test Finding 3: Instrumental Solo Identity & Preservation ---")
    test_cases = [
        ("Vocal Solo", "Solo", "Solo"),
        ("Soprano Solo", "Solo", "Solo"),
        ("Tenor Solo", "Solo", "Solo"),
        ("Cantor", "Solo", "Solo"),
        ("Violin Solo", "Others", "Others"),
        ("Flute Solo", "Others", "Others"),
        ("Piano Solo", "Others", "Others"),
        ("Alto Saxophone Solo", "Others", "Others"),
    ]

    for name, expected_voice, expected_code in test_cases:
        xml_str = f"""<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>{name}</part-name></score-part>
    <score-part id="P2"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></measure></part>
</score-partwise>"""

        with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
            tf.write(xml_str)
            t_path = tf.name

        try:
            # 1. Test musicxml_cleaner directly
            musicxml_cleaner.clean_musicxml_file(t_path)

            # Verify that instrument name was not destructively overwritten to 'Solo'
            tree = ET.parse(t_path)
            sp1 = tree.find(".//score-part[@id='P1']")
            cleaned_name = sp1.findtext("part-name")

            if expected_voice == "Others":
                assert cleaned_name != "Solo", f"Cleaner destructively overwrote {name} with 'Solo'!"

            # 2. Run satb_analyzer
            res = satb_analyzer.analyze(t_path)
            p1_ev = [e for e in res["events"] if e["part_id"] == 1][0]
            actual_voice = p1_ev["satb_voice"]

            # Check stamped voice code
            tree = ET.parse(t_path)
            p1_elem = tree.find(".//part[@id='P1']")
            stamped_code = None
            for n in p1_elem.findall(".//note"):
                stamped_code = n.get("data-vx-voice")
                if stamped_code:
                    break

            print(f"  Part '{name:22}': after_clean='{cleaned_name:12}', satb_voice='{actual_voice:6}', stamped='{stamped_code}'")
            assert actual_voice == expected_voice, f"Expected {expected_voice}, got {actual_voice}"
            assert stamped_code == expected_code, f"Expected stamp {expected_code}, got {stamped_code}"
        finally:
            if os.path.exists(t_path):
                os.remove(t_path)

    print("[PASS] Finding 3: All solo classification and identity preservation tests passed.")


def test_finding_4_multi_staff_heuristic():
    print("\n--- Running Test Finding 4: Multi-Staff Detection Heuristic ---")

    # 4a: Unnamed 2-staff part with 0 lyrics -> Others (code 6)
    xml_unnamed = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name></part-name></score-part>
    <score-part id="P2"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><type>whole</type><staff>1</staff></note>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><type>whole</type><staff>2</staff></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note>
    </measure>
  </part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_unnamed)
        t_path = tf.name
    try:
        res = satb_analyzer.analyze(t_path)
        # Part 1 events should be Others
        p1_voices = set(e["satb_voice"] for e in res["events"] if e["part_id"] == 1)
        print(f"  Case 4a (Unnamed 2-staff, 0 lyrics): voices={p1_voices} (expected {{'Others'}})")
        assert p1_voices == {"Others"}, f"Expected {{'Others'}}, got {p1_voices}"
    finally:
        os.remove(t_path)

    # 4b: Choir with 2 staves and 0 lyrics -> remains vocal SATB (never Others)
    xml_choir_2staff = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><type>whole</type><staff>1</staff></note>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><type>whole</type><staff>2</staff></note>
    </measure>
  </part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_choir_2staff)
        t_path = tf.name
    try:
        res = satb_analyzer.analyze(t_path)
        p1_voices = set(e["satb_voice"] for e in res["events"] if e["part_id"] == 1)
        print(f"  Case 4b (Choir 2-staff, 0 lyrics): voices={p1_voices} (expected vocal, not Others)")
        assert "Others" not in p1_voices, "2-staff Choir without lyrics was incorrectly demoted to Others!"
    finally:
        os.remove(t_path)

    # 4c: Vocal part with 2 staves and sufficient lyrics (>= 5) -> remains vocal
    xml_vocal_lyrics = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Voice</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><lyric><text>Glo</text></lyric></note>
      <note><pitch><step>D</step><octave>5</octave></pitch><duration>1</duration><lyric><text>ri</text></lyric></note>
      <note><pitch><step>E</step><octave>5</octave></pitch><duration>1</duration><lyric><text>a</text></lyric></note>
      <note><pitch><step>F</step><octave>5</octave></pitch><duration>1</duration><lyric><text>in</text></lyric></note>
      <note><pitch><step>G</step><octave>5</octave></pitch><duration>1</duration><lyric><text>ex</text></lyric></note>
    </measure>
  </part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_vocal_lyrics)
        t_path = tf.name
    try:
        res = satb_analyzer.analyze(t_path)
        p1_voices = set(e["satb_voice"] for e in res["events"] if e["part_id"] == 1)
        print(f"  Case 4c (Voice 2-staff with >= 5 lyrics): voices={p1_voices} (expected vocal)")
        assert "Others" not in p1_voices, "2-staff vocal part with lyrics was incorrectly classified as Others!"
    finally:
        os.remove(t_path)

    # 4d: Standard Piano grand-staff part -> Others
    xml_piano = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Piano</part-name></score-part>
    <score-part id="P2"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration><staff>1</staff></note>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>4</duration><staff>2</staff></note>
    </measure>
  </part>
  <part id="P2">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>G</step><octave>4</octave></pitch><duration>4</duration></note>
    </measure>
  </part>
</score-partwise>"""
    with tempfile.NamedTemporaryFile(suffix=".xml", delete=False, mode="w", encoding="utf-8") as tf:
        tf.write(xml_piano)
        t_path = tf.name
    try:
        res = satb_analyzer.analyze(t_path)
        p1_voices = set(e["satb_voice"] for e in res["events"] if e["part_id"] == 1)
        print(f"  Case 4d (Piano grand staff): voices={p1_voices} (expected {{'Others'}})")
        assert p1_voices == {"Others"}, f"Expected {{'Others'}}, got {p1_voices}"
    finally:
        os.remove(t_path)

    print("[PASS] Finding 4: Multi-staff detection heuristic tests passed.")


def main():
    print("==================================================================")
    print("STARTING REMEDIATION REGRESSION TEST SUITE (FINDINGS 1 - 4)")
    print("==================================================================")
    test_finding_1_instrument_metadata()
    test_finding_2_upload_validation()
    test_finding_3_instrumental_solo_identity()
    test_finding_4_multi_staff_heuristic()
    print("\n==================================================================")
    print("ALL REMEDIATION TESTS PASSED PERFECTLY (100%)")
    print("==================================================================")


if __name__ == "__main__":
    main()
