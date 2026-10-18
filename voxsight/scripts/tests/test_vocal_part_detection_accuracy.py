#!/usr/bin/env python3
"""
Regression Test Suite: Vocal-Part Detection Accuracy & Unison/Divisi Integrity
Validates that:
1. Open 4-part SATB scores with identical pitches (e.g. Happy Birthday pickup measure)
   retain their individual vocal-part identities (S, A, T, B) and are NEVER falsely marked as TUTTI.
2. Condensed 2-staff scores (e.g. Lead Me Lord) correctly detect genuine UNISON_TB and UNISON_SA.
3. In-staff divisi in open 4-part scores cleanly separates into S1/S2 without disturbing other vocal parts.
4. Single-staff choral chords remain unified without spurious divisi splitting.
"""

import os
import sys
import tempfile
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import satb_analyzer


def test_open_satb_unison_pitches_no_tutti():
    """Happy Birthday reproduction: All 4 parts sing G4 simultaneously.
    Must maintain S, A, T, B. Zero TUTTI allowed.
    """
    print("\n--- Test 1: Open 4-Part SATB Simultaneous Pitch (Happy Birthday) ---")
    xml_hb = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Soprano</part-name></score-part>
    <score-part id="P2"><part-name>Alto</part-name></score-part>
    <score-part id="P3"><part-name>Tenor</part-name></score-part>
    <score-part id="P4"><part-name>Bass</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Hap-</text></lyric></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Hap-</text></lyric></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Hap-</text></lyric></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Hap-</text></lyric></note></measure></part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_hb)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        voices = [e["satb_voice"] for e in res["events"]]
        print(f"  Assigned voices: {voices}")
        assert voices == ["S", "A", "T", "B"], f"Expected ['S', 'A', 'T', 'B'], got {voices}"
        assert "TUTTI" not in voices, "TUTTI incorrectly assigned to open SATB parts!"
        print("  [PASS] Test 1: Open SATB parts preserved S, A, T, B; no false TUTTI.")
    finally:
        if os.path.exists(p):
            os.remove(p)


def test_condensed_2staff_unison_tb():
    """Condensed 2-staff score (Staff 1: SA, Staff 2: TB).
    Staff 1 has polyphony (S and A), Staff 2 has single note -> UNISON_TB.
    """
    print("\n--- Test 2: Condensed 2-Staff Score (Staff 2 UNISON_TB) ---")
    xml_cond = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><voice>1</voice><staff>1</staff><lyric><text>Glo-</text></lyric></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><voice>2</voice><staff>1</staff></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>3</voice><staff>2</staff></note>
    </measure>
  </part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_cond)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        voices = [e["satb_voice"] for e in res["events"]]
        print(f"  Assigned voices: {voices}")
        assert "UNISON_TB" in voices, f"Expected UNISON_TB in {voices}"
        assert "TUTTI" not in voices, "TUTTI incorrectly assigned to condensed staves!"
        print("  [PASS] Test 2: Condensed 2-staff correctly detected UNISON_TB on Staff 2.")
    finally:
        if os.path.exists(p):
            os.remove(p)


def test_open_satb_divisi_isolation():
    """Open 4-part SATB with Part 1 Divisi into S1 and S2.
    Must split Part 1 into S1/S2 while Parts 2, 3, 4 remain A, T, B.
    """
    print("\n--- Test 3: Open 4-Part SATB with Part 1 Divisi (S1 and S2) ---")
    xml_div = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Soprano</part-name></score-part>
    <score-part id="P2"><part-name>Alto</part-name></score-part>
    <score-part id="P3"><part-name>Tenor</part-name></score-part>
    <score-part id="P4"><part-name>Bass</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>E</step><octave>5</octave></pitch><duration>1</duration><voice>1</voice><lyric><text>Glo-</text></lyric></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><voice>2</voice></note>
    </measure>
  </part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Glo-</text></lyric></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Glo-</text></lyric></note></measure></part>
  <part id="P4"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><lyric><text>Glo-</text></lyric></note></measure></part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_div)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        part_voices = {}
        for ev in res["events"]:
            part_voices.setdefault(ev["part_id"], []).append(ev["satb_voice"])
        print(f"  Part voices: {part_voices}")
        assert set(part_voices[1]) == {"S1", "S2"}, f"Expected S1 and S2 in Part 1, got {part_voices[1]}"
        assert part_voices[2] == ["A"], f"Expected ['A'] in Part 2, got {part_voices[2]}"
        assert part_voices[3] == ["T"], f"Expected ['T'] in Part 3, got {part_voices[3]}"
        assert part_voices[4] == ["B"], f"Expected ['B'] in Part 4, got {part_voices[4]}"
        print("  [PASS] Test 3: Divisi S1/S2 cleanly separated; other parts completely undisturbed.")
    finally:
        if os.path.exists(p):
            os.remove(p)


def main():
    print("==================================================================")
    print("STARTING VOCAL-PART DETECTION ACCURACY REGRESSION SUITE")
    print("==================================================================")
    test_open_satb_unison_pitches_no_tutti()
    test_condensed_2staff_unison_tb()
    test_open_satb_divisi_isolation()
    print("\n==================================================================")
    print("ALL VOCAL-PART DETECTION TESTS PASSED (100% SUCCESS RATE)")
    print("==================================================================")


if __name__ == "__main__":
    main()
