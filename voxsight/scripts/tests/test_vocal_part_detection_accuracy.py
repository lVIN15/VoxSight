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


def test_condensed_2staff_four_parts_no_false_divisi():
    """Condensed 2-staff SATB (Staff 1: S=A4, A=F4; Staff 2: T=D3, B=G2).
    Must assign S, A, T, B. ZERO false divisi (no S1/S2 or B1/B2).
    """
    print("\n--- Test 4: Condensed 2-Staff 4-Part SATB (No False Divisi) ---")
    xml_cond = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <note><pitch><step>A</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice><staff>1</staff></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>F</step><octave>4</octave></pitch><duration>1</duration><voice>2</voice><staff>1</staff></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>D</step><octave>3</octave></pitch><duration>1</duration><voice>3</voice><staff>2</staff></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>G</step><octave>2</octave></pitch><duration>1</duration><voice>4</voice><staff>2</staff></note>
    </measure>
  </part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_cond)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        voices = set(e["satb_voice"] for e in res["events"])
        print(f"  Assigned voices: {voices}")
        assert voices == {"S", "A", "T", "B"}, f"Expected {{'S', 'A', 'T', 'B'}}, got {voices}"
        assert not any(v in voices for v in ("B1", "B2", "S1", "S2")), f"False divisi detected in {voices}"
        print("  [PASS] Test 4: Condensed SATB cleanly separated into S, A, T, B without false divisi.")
    finally:
        if os.path.exists(p):
            os.remove(p)


def test_open_satb_bass_divisi_isolation():
    """Open 4-part SATB with Part 4 (Bass) Divisi into B1 and B2 (Happy Birthday m.8 case).
    Measures 1-7 Bass are B; Measure 8 Bass divides into B1 and B2.
    """
    print("\n--- Test 5: Open 4-Part SATB with Part 4 Divisi (B1 and B2) ---")
    xml_b_div = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Soprano</part-name></score-part>
    <score-part id="P2"><part-name>Alto</part-name></score-part>
    <score-part id="P3"><part-name>Tenor</part-name></score-part>
    <score-part id="P4"><part-name>Bass</part-name></score-part>
  </part-list>
  <part id="P1"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>G</step><octave>4</octave></pitch><duration>1</duration><lyric><text>to</text></lyric></note></measure></part>
  <part id="P2"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><lyric><text>to</text></lyric></note></measure></part>
  <part id="P3"><measure number="1"><attributes><divisions>1</divisions></attributes><note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><lyric><text>to</text></lyric></note></measure></part>
  <part id="P4">
    <measure number="1">
      <attributes><divisions>1</divisions></attributes>
      <note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>1</voice><lyric><text>to</text></lyric></note>
      <backup><duration>1</duration></backup>
      <note><pitch><step>C</step><octave>2</octave></pitch><duration>1</duration><voice>2</voice></note>
    </measure>
  </part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_b_div)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        part_voices = {}
        for ev in res["events"]:
            part_voices.setdefault(ev["part_id"], []).append(ev["satb_voice"])
        print(f"  Part voices: {part_voices}")
        assert set(part_voices[4]) == {"B1", "B2"}, f"Expected B1 and B2 in Part 4, got {part_voices[4]}"
        assert part_voices[1] == ["S"], f"Expected ['S'] in Part 1, got {part_voices[1]}"
        assert part_voices[2] == ["A"], f"Expected ['A'] in Part 2, got {part_voices[2]}"
        assert part_voices[3] == ["T"], f"Expected ['T'] in Part 3, got {part_voices[3]}"
        print("  [PASS] Test 5: Bass Divisi B1/B2 cleanly separated in Part 4; other parts undisturbed.")
    finally:
        if os.path.exists(p):
            os.remove(p)


def test_stem_aware_condensed_voices():
    """Verify that stem directions (stem up/down) take precedence over pitch cutoffs:
    1. Low Soprano (F4 < 69) with stem up stays Soprano 'S' instead of flipping to Alto.
    2. Low Tenor (Eb3 < 53) with stem up stays Tenor 'T' instead of flipping to Bass.
    3. Single note on Staff 2 with stem down stays Bass 'B' instead of becoming UNISON_TB.
    """
    print("\n--- Test 6: Stem-Aware Condensed 2-Staff Classification ---")
    xml_stem = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1">
  <part-list>
    <score-part id="P1"><part-name>Choir</part-name></score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes><divisions>1</divisions><staves>2</staves></attributes>
      <!-- Staff 1: Low Soprano F4 (MIDI 65 < 69) with stem UP -->
      <note>
        <pitch><step>F</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <stem>up</stem>
        <staff>1</staff>
        <lyric><text>Glo-</text></lyric>
      </note>
      <backup><duration>1</duration></backup>
      <!-- Staff 2: Low Tenor Eb3 (MIDI 51 < 53) with stem UP -->
      <note>
        <pitch><step>E</step><alter>-1</alter><octave>3</octave></pitch>
        <duration>1</duration>
        <voice>3</voice>
        <stem>up</stem>
        <staff>2</staff>
      </note>
    </measure>
    <measure number="2">
      <attributes><divisions>1</divisions></attributes>
      <!-- Staff 1 polyphony: S (stem up) and A (stem down) -->
      <note>
        <pitch><step>G</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>1</voice>
        <stem>up</stem>
        <staff>1</staff>
      </note>
      <backup><duration>1</duration></backup>
      <note>
        <pitch><step>E</step><octave>4</octave></pitch>
        <duration>1</duration>
        <voice>2</voice>
        <stem>down</stem>
        <staff>1</staff>
      </note>
      <backup><duration>1</duration></backup>
      <!-- Staff 2: Single note with explicit stem DOWN (Bass). Must NOT become UNISON_TB! -->
      <note>
        <pitch><step>C</step><octave>3</octave></pitch>
        <duration>1</duration>
        <voice>4</voice>
        <stem>down</stem>
        <staff>2</staff>
      </note>
    </measure>
  </part>
</score-partwise>"""

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as f:
        f.write(xml_stem)
        p = f.name
    try:
        res = satb_analyzer.analyze(p)
        events_m1 = [e for e in res["events"] if e["measure_number"] == 1]
        events_m2 = [e for e in res["events"] if e["measure_number"] == 2]

        m1_voices = {e["staff_id"]: e["satb_voice"] for e in events_m1}
        print(f"  Measure 1 voices: {m1_voices}")
        assert m1_voices[1] == "S", f"Expected Low Soprano F4 to be 'S' due to stem UP, got {m1_voices[1]}"
        assert m1_voices[2] == "T", f"Expected Low Tenor Eb3 to be 'T' due to stem UP, got {m1_voices[2]}"

        m2_s2_voices = [e["satb_voice"] for e in events_m2 if e["staff_id"] == 2]
        print(f"  Measure 2 Staff 2 voices: {m2_s2_voices}")
        assert m2_s2_voices == ["B"], f"Expected Staff 2 stem-down note to be 'B' (not UNISON_TB), got {m2_s2_voices}"
        print("  [PASS] Test 6: Stem-aware SATB correctly classified low Soprano, low Tenor, and stem-down Bass.")
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
    test_condensed_2staff_four_parts_no_false_divisi()
    test_open_satb_bass_divisi_isolation()
    test_stem_aware_condensed_voices()
    print("\n==================================================================")
    print("ALL VOCAL-PART DETECTION TESTS PASSED (100% SUCCESS RATE)")
    print("==================================================================")


if __name__ == "__main__":
    main()

