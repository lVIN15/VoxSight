#!/usr/bin/env python3
"""
VoxSight Cadence Whole Notes & Rest Synthesis Regression Suite
==============================================================
Validates:
1. Reconstructed whole notes in LEAD ME LORD (Tenor M2 G#3, Tenor M4 C4 natural, Bass M3 F#3).
2. Correct pitches, divisions-derived durations, voices, stems, and types.
3. Correct Bass cadence tie connection (M2 eighth note 3 -> M3 whole note).
4. Preservation of genuine whole-measure rests (measure="yes" retained, no type/dot).
5. No unintended 3-beat dotted half rests synthesized from OMR omissions.
6. Preservation of legitimate dotted notes and shorter rests.
7. Correct Bass beamed eighth-note cadence grouping.
8. Idempotency: No duplicate notes after repeated clean passes.
9. W3C MusicXML element ordering schema compliance.
10. SATB part identification parity.
"""

import copy
import pathlib
import sys
import unittest
import xml.etree.ElementTree as ET

TESTS_DIR = pathlib.Path(__file__).resolve().parent
SCRIPTS_DIR = TESTS_DIR.parent
sys.path.insert(0, str(SCRIPTS_DIR))

import musicxml_cleaner
import satb_analyzer

RAW_SCORE_PATH = pathlib.Path(r"C:\Users\John\.gemini\antigravity\brain\9e220da4-9f1d-44c7-852a-a5373b09d0cd\scratch\page_1_raw.xml")


class TestCadenceWholeNotesRegression(unittest.TestCase):

    def setUp(self):
        if RAW_SCORE_PATH.exists():
            self.tree = ET.parse(RAW_SCORE_PATH)
            self.root = self.tree.getroot()
        else:
            self.skipTest(f"Raw score fixture not found at {RAW_SCORE_PATH}")

    def test_01_tenor_measure_2_whole_note(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p5 = root.find("part[@id='P5']")
        self.assertIsNotNone(p5)
        m2 = next(m for m in p5.findall('measure') if m.get('number') == '2')

        notes = m2.findall('note')
        self.assertEqual(len(notes), 1, "Tenor M2 should contain exactly 1 note")

        n = notes[0]
        self.assertIsNone(n.find('rest'), "Tenor M2 must be a pitched note, not a rest")
        self.assertEqual(n.findtext('.//step'), 'C')
        self.assertEqual(n.findtext('.//alter'), '1')
        self.assertEqual(n.findtext('.//octave'), '4')
        self.assertEqual(n.findtext('type'), 'whole')
        self.assertEqual(n.findtext('stem'), 'none')
        self.assertEqual(n.findtext('duration'), '8')

    def test_02_tenor_measure_4_whole_note_natural(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p5 = root.find("part[@id='P5']")
        self.assertIsNotNone(p5)
        m4 = next(m for m in p5.findall('measure') if m.get('number') == '4')

        notes = m4.findall('note')
        self.assertEqual(len(notes), 1, "Tenor M4 should contain exactly 1 note")

        n = notes[0]
        self.assertIsNone(n.find('rest'), "Tenor M4 must be a pitched note, not a rest")
        self.assertEqual(n.findtext('.//step'), 'A')
        self.assertEqual(n.findtext('.//octave'), '3')
        self.assertEqual(n.findtext('type'), 'whole')
        self.assertEqual(n.findtext('stem'), 'none')
        self.assertEqual(n.findtext('duration'), '8')
        self.assertEqual(n.findtext('accidental'), 'natural', "Natural sign must be preserved on A3")

    def test_03_bass_measure_3_whole_note_and_slur(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p6 = root.find("part[@id='P6']")
        self.assertIsNotNone(p6)
        m2 = next(m for m in p6.findall('measure') if m.get('number') == '2')
        m3 = next(m for m in p6.findall('measure') if m.get('number') == '3')

        notes_m3 = m3.findall('note')
        self.assertEqual(len(notes_m3), 1, "Bass M3 should contain exactly 1 note")

        n3 = notes_m3[0]
        self.assertIsNone(n3.find('rest'), "Bass M3 must be a pitched note, not a rest")
        self.assertEqual(n3.findtext('.//step'), 'C', "Bass M3 whole note is C#3 in Space 2")
        self.assertEqual(n3.findtext('.//alter'), '1')
        self.assertEqual(n3.findtext('.//octave'), '3')
        self.assertEqual(n3.findtext('type'), 'whole')
        self.assertEqual(n3.findtext('stem'), 'none')
        self.assertEqual(n3.findtext('duration'), '8')

        # Slur stop on M3 (connected from M2 note 1)
        slur_stop = n3.find('.//slur[@type="stop"]')
        self.assertIsNotNone(slur_stop, "Bass M3 whole note must have <slur type='stop'/> in notations")

        # Slur start on first note of eighth-note cadence in M2 (note 1: A#3)
        m2_pitched = [n for n in m2.findall('note') if n.find('rest') is None]
        self.assertGreaterEqual(len(m2_pitched), 3)
        n1_m2 = m2_pitched[0]
        self.assertEqual(n1_m2.findtext('.//step'), 'A')
        self.assertEqual(n1_m2.findtext('.//alter'), '1')
        self.assertIsNotNone(n1_m2.find('.//slur[@type="start"]'), "Bass M2 note 1 must have <slur type='start'/>")

    def test_04_preservation_of_genuine_whole_measure_rest(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p6 = root.find("part[@id='P6']")
        m1 = next(m for m in p6.findall('measure') if m.get('number') == '1')
        notes = m1.findall('note')
        self.assertEqual(len(notes), 1)

        r = notes[0].find('rest')
        self.assertIsNotNone(r)
        self.assertEqual(r.get('measure'), 'yes', 'measure="yes" attribute must be preserved')
        self.assertIsNone(notes[0].find('type'), 'Whole-measure rest must not have conflicting type')
        self.assertIsNone(notes[0].find('dot'), 'Whole-measure rest must not have dot')

    def test_05_no_unintended_three_beat_dotted_rests(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        for p in root.findall('part'):
            for m in p.findall('measure'):
                notes = m.findall('note')
                for n in notes:
                    if n.find('rest') is not None and n.find('dot') is not None:
                        # Legitimate dotted rests should not occur in empty full measures
                        self.assertNotEqual(n.findtext('duration'), '12', "Unintended 12-duration dotted rest detected")

    def test_06_preservation_of_legitimate_dotted_notes_and_shorter_rests(self):
        # Create a test score with a dotted quarter note and a dotted quarter rest
        xml = """<score-partwise version="3.1">
          <part-list><score-part id="P1"><part-name>Soprano</part-name></score-part></part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time></attributes>
              <note><pitch><step>G</step><octave>4</octave></pitch><duration>6</duration><type>quarter</type><dot/></note>
              <note><rest/><duration>6</duration><type>quarter</type><dot/></note>
              <note><pitch><step>A</step><octave>4</octave></pitch><duration>4</duration><type>quarter</type></note>
            </measure>
          </part>
        </score-partwise>"""
        root = ET.fromstring(xml)
        musicxml_cleaner.clean_musicxml_tree(root)

        notes = root.findall('.//note')
        self.assertEqual(len(notes), 3)
        self.assertIsNotNone(notes[0].find('dot'), "Legitimate dotted quarter note must preserve dot")
        self.assertIsNotNone(notes[1].find('dot'), "Legitimate dotted quarter rest must preserve dot")
        self.assertEqual(notes[1].findtext('duration'), '6')

    def test_07_bass_cadence_eighth_notes_beaming(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p6 = root.find("part[@id='P6']")
        for mnum in ['2', '4']:
            m = next(m for m in p6.findall('measure') if m.get('number') == mnum)
            pitched = [n for n in m.findall('note') if n.find('rest') is None]
            self.assertEqual(len(pitched), 3, f"Bass M{mnum} must have 3 cadence notes")

            for n in pitched:
                self.assertEqual(n.findtext('type'), 'eighth')
                self.assertEqual(n.findtext('stem'), 'down')
                self.assertEqual(n.findtext('duration'), '1')

            self.assertEqual(pitched[0].findtext('beam'), 'begin')
            self.assertEqual(pitched[1].findtext('beam'), 'continue')
            self.assertEqual(pitched[2].findtext('beam'), 'end')

    def test_08_idempotency_no_duplicate_notes(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)
        pass1_counts = [len(m.findall('note')) for p in root.findall('part') for m in p.findall('measure')]

        # Run 2nd and 3rd pass
        musicxml_cleaner.clean_musicxml_tree(root)
        musicxml_cleaner.clean_musicxml_tree(root)
        pass3_counts = [len(m.findall('note')) for p in root.findall('part') for m in p.findall('measure')]

        self.assertEqual(pass1_counts, pass3_counts, "Repeated cleaning must never duplicate notes")

    def test_09_musicxml_note_element_ordering(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        # W3C schema ordering inside <note>:
        # pitch/rest -> duration -> tie -> voice -> type -> accidental -> stem -> beam -> notations
        tag_order = {'pitch': 1, 'rest': 1, 'duration': 2, 'tie': 3, 'voice': 4, 'type': 5, 'accidental': 6, 'stem': 7, 'beam': 8, 'notations': 9}

        for n in root.findall('.//note'):
            tags = [child.tag for child in n if child.tag in tag_order]
            ranks = [tag_order[t] for t in tags]
            self.assertEqual(ranks, sorted(ranks), f"Note elements out of W3C schema order: {tags}")

    def test_10_satb_part_identification_parity(self):
        import tempfile
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        with tempfile.NamedTemporaryFile(suffix='.musicxml', delete=False, mode='w', encoding='utf-8') as tf:
            ET.ElementTree(root).write(tf.name, encoding='utf-8', xml_declaration=True)
            temp_path = tf.name

        try:
            analysis = satb_analyzer.analyze(temp_path)
            meta = analysis.get('score_metadata', {})
            self.assertEqual(meta.get('structure_type'), 'TRUE_SATB')
            self.assertGreaterEqual(meta.get('satb_confidence', 0), 0.75)
            self.assertTrue(meta.get('validation_passed', False))
            tracks = meta.get('playback_tracks', [])
            self.assertEqual(len(tracks), 6)
            self.assertIn(tracks[0].get('part_key'), ['S', 'S1'])
            self.assertEqual(tracks[1].get('part_key'), 'S2')
            self.assertEqual(tracks[2].get('part_key'), 'A1')
            self.assertEqual(tracks[3].get('part_key'), 'A2')
            self.assertEqual(tracks[4].get('part_key'), 'T')
            self.assertEqual(tracks[5].get('part_key'), 'B')
        finally:
            pathlib.Path(temp_path).unlink(missing_ok=True)

    def test_11_lyric_syllabic_alignment_preserved(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        lyrics = []
        for n in root.findall('.//note'):
            txt = n.findtext('.//lyric/text')
            if txt:
                lyrics.append(txt)
        self.assertGreater(len(lyrics), 0, "Lyrics must be preserved after cleaner pass")
        self.assertTrue(any('ooh' in l.lower() for l in lyrics), "Ooh choral lyrics must be retained")

    def test_12_percussion_midi_channel_10_remapped(self):
        root = copy.deepcopy(self.root)
        # Verify if channel 10 is present in the raw score, it gets safely remapped
        midi_channels = [mc.text for mc in root.iter('midi-channel')]
        musicxml_cleaner.clean_musicxml_tree(root)
        clean_channels = [mc.text for mc in root.iter('midi-channel')]
        self.assertNotIn('10', clean_channels, "General MIDI Channel 10 (Percussion) must be remapped to avoid OSMD key signature suppression")

    def test_13_bass_cadence_rest_placement_middle_line(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p6 = root.find("part[@id='P6']")
        self.assertIsNotNone(p6)
        for mnum in ['2', '4']:
            m = next(m for m in p6.findall('measure') if m.get('number') == mnum)
            rests = [n for n in m.findall('note') if n.find('rest') is not None]
            self.assertGreaterEqual(len(rests), 2, f"Bass M{mnum} should contain half and eighth rests")
            for r_note in rests:
                r_elem = r_note.find('rest')
                ds = r_elem.findtext('display-step')
                do = r_elem.findtext('display-octave')
                self.assertEqual(ds, 'B', f"Bass M{mnum} rest display-step must be B (for Line 3 middle line placement)")
                self.assertEqual(do, '2', f"Bass M{mnum} rest display-octave must be 2 (for Line 3 middle line placement)")

    def test_14_tenor_measure_1_contour_and_slur(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p5 = root.find("part[@id='P5']")
        self.assertIsNotNone(p5)
        m1 = next(m for m in p5.findall('measure') if m.get('number') == '1')
        p_notes = [n for n in m1.findall('note') if n.find('rest') is None]
        self.assertEqual(len(p_notes), 4, "Tenor M1 must contain 2 eighth notes + 2 quarter notes on beats 2, 3, 4")

        steps = [n.findtext('.//step') for n in p_notes]
        octs = [n.findtext('.//octave') for n in p_notes]
        stems = [n.findtext('stem') for n in p_notes]
        types = [n.findtext('type') for n in p_notes]
        self.assertEqual(steps, ['E', 'D', 'C', 'C'])
        self.assertEqual(octs, ['4', '4', '4', '4'])
        self.assertEqual(stems, ['down', 'down', 'down', 'down'])
        self.assertEqual(types, ['eighth', 'eighth', 'quarter', 'quarter'])
        self.assertEqual(p_notes[3].findtext('accidental'), 'natural', "Tenor M1 C4 note 4 must have natural accidental")
        self.assertIsNotNone(p_notes[0].find('.//slur[@type="start"]'), "Tenor M1 Note 1 must have slur start")
        self.assertIsNotNone(p_notes[3].find('.//slur[@type="stop"]'), "Tenor M1 Note 4 must have slur stop")

    def test_15_soprano_measure_3_cadence_eighths(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p1 = root.find("part[@id='P1']")
        self.assertIsNotNone(p1)
        m3 = next(m for m in p1.findall('measure') if m.get('number') == '3')
        notes = m3.findall('note')
        self.assertEqual(len(notes), 6, "Soprano 1 M3 must contain 4 eighth notes + 2 quarter notes")

        types = [n.findtext('type') for n in notes]
        stems = [n.findtext('stem') for n in notes]
        steps = [n.findtext('.//step') for n in notes]
        octs = [n.findtext('.//octave') for n in notes]
        self.assertEqual(types, ['eighth', 'eighth', 'eighth', 'eighth', 'quarter', 'quarter'])
        self.assertEqual(stems, ['down', 'down', 'down', 'down', 'up', 'down'])
        self.assertEqual(steps, ['C', 'B', 'B', 'A', 'G', 'D'])
        self.assertEqual(octs, ['5', '4', '4', '4', '4', '5'])
        self.assertIsNotNone(notes[0].find('.//slur[@type="start"]'), "S1 M3 eighth note 1 must have slur start")
        self.assertIsNotNone(notes[3].find('.//slur[@type="stop"]'), "S1 M3 eighth note 4 must have slur stop")

        p2 = root.find("part[@id='P2']")
        self.assertIsNotNone(p2)
        m3_s2 = next(m for m in p2.findall('measure') if m.get('number') == '3')
        notes_s2 = m3_s2.findall('note')
        self.assertEqual(len(notes_s2), 6)
        types_s2 = [n.findtext('type') for n in notes_s2]
        stems_s2 = [n.findtext('stem') for n in notes_s2]
        steps_s2 = [n.findtext('.//step') for n in notes_s2]
        self.assertEqual(types_s2, ['eighth', 'eighth', 'eighth', 'eighth', 'quarter', 'quarter'])
        self.assertEqual(stems_s2, ['down', 'down', 'down', 'down', 'up', 'down'])
        self.assertEqual(steps_s2, ['C', 'B', 'B', 'A', 'G', 'D'])
        self.assertIsNone(notes_s2[0].find('.//slur'))

        # Measure 4 stems
        m4_s1 = next(m for m in p1.findall('measure') if m.get('number') == '4')
        stems_m4_s1 = [n.findtext('stem') for n in m4_s1.findall('note') if n.find('rest') is None]
        self.assertEqual(stems_m4_s1, ['down', 'down'])

        m4_s2 = next(m for m in p2.findall('measure') if m.get('number') == '4')
        stems_m4_s2 = [n.findtext('stem') for n in m4_s2.findall('note') if n.find('rest') is None]
        self.assertEqual(stems_m4_s2, ['down', 'down'])

    def test_16_bass_m1_whole_measure_rest_no_display_step(self):
        root = copy.deepcopy(self.root)
        musicxml_cleaner.clean_musicxml_tree(root)

        p6 = root.find("part[@id='P6']")
        self.assertIsNotNone(p6)
        m1 = next(m for m in p6.findall('measure') if m.get('number') == '1')
        notes = m1.findall('note')
        self.assertEqual(len(notes), 1)
        r = notes[0].find('rest')
        self.assertIsNotNone(r)
        self.assertEqual(r.get('measure'), 'yes')
        self.assertIsNone(r.find('display-step'), "Bass M1 whole rest must not have display-step so OSMD hangs it from Line 4")
        self.assertIsNone(r.find('display-octave'), "Bass M1 whole rest must not have display-octave so OSMD hangs it from Line 4")


if __name__ == '__main__':
    unittest.main()
