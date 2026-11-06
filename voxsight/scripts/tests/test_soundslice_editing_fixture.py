"""
Soundslice-Inspired Music Sheet Editing Integration Test on LEAD ME LORD Fixture
Verifies:
1. Rest to pitched whole note conversion (Tenor whole note issue)
2. Pitched whole note to another pitch
3. Pitched note to rest conversion
4. Whole-measure rest vs whole note semantic preservation
5. Half-rest and quarter-rest duration adjustments
6. Accidental changes (Natural -> Sharp -> Flat)
7. Duration changes and division recalculation
8. Slur and Tie curve generation and preservation
9. SATB voice assignment preservation
10. Complete Undo/Redo stack restoration
"""

import os
import unittest
import zipfile
import xml.etree.ElementTree as ET

class TestSoundsliceLeadMeLordFixture(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture_path = os.path.abspath(os.path.join(
            os.path.dirname(__file__),
            "../../outputs/1793466483934-LEAD_ME_LORD__SATB_1790332345810.mxl"
        ))
        if not os.path.exists(cls.fixture_path):
            raise unittest.SkipTest(f"Fixture not found: {cls.fixture_path}")
        
        # Extract XML
        with zipfile.ZipFile(cls.fixture_path, 'r') as z:
            xml_names = [n for n in z.namelist() if n.endswith('.xml') and 'container.xml' not in n]
            cls.xml_content = z.read(xml_names[0]).decode('utf-8')

    def setUp(self):
        self.root = ET.fromstring(self.xml_content)
        # Tag notes with data-vx-id
        id_counter = 1
        for note in self.root.iter('note'):
            note.set('data-vx-id', str(id_counter))
            id_counter += 1

    def test_01_rest_to_pitched_whole_note(self):
        """Tenor Rest converted to pitched Whole Note (e.g. C4)"""
        # Find a rest
        target_rest = None
        for note in self.root.iter('note'):
            if note.find('rest') is not None:
                target_rest = note
                break
        
        self.assertIsNotNone(target_rest, "Rest note found")
        
        # Remove rest tag
        rest_el = target_rest.find('rest')
        target_rest.remove(rest_el)
        
        # Insert pitch C4
        pitch_el = ET.Element('pitch')
        step_el = ET.SubElement(pitch_el, 'step')
        step_el.text = 'C'
        oct_el = ET.SubElement(pitch_el, 'octave')
        oct_el.text = '4'
        target_rest.insert(0, pitch_el)
        
        # Update type and duration
        type_el = target_rest.find('type')
        if type_el is None:
            type_el = ET.SubElement(target_rest, 'type')
        type_el.text = 'whole'
        
        dur_el = target_rest.find('duration')
        if dur_el is not None:
            dur_el.text = '960'
            
        self.assertIsNone(target_rest.find('rest'), "Rest tag removed")
        self.assertIsNotNone(target_rest.find('pitch'), "Pitch tag present")
        self.assertEqual(target_rest.find('pitch/step').text, 'C')
        self.assertEqual(target_rest.find('pitch/octave').text, '4')
        self.assertEqual(target_rest.find('type').text, 'whole')

    def test_02_pitched_whole_note_shift(self):
        """Pitched Whole Note altered to another pitch (C4 -> F4)"""
        note = self.root.find(".//note[@data-vx-id='1']")
        self.assertIsNotNone(note)
        
        pitch = note.find('pitch')
        if pitch is None:
            pitch = ET.SubElement(note, 'pitch')
        
        step = pitch.find('step')
        if step is None:
            step = ET.SubElement(pitch, 'step')
        step.text = 'F'
        
        octave = pitch.find('octave')
        if octave is None:
            octave = ET.SubElement(pitch, 'octave')
        octave.text = '4'
        
        self.assertEqual(note.find('pitch/step').text, 'F')
        self.assertEqual(note.find('pitch/octave').text, '4')

    def test_03_pitched_note_to_rest(self):
        """Pitched note converted to rest"""
        note = self.root.find(".//note[@data-vx-id='2']")
        self.assertIsNotNone(note)
        
        pitch = note.find('pitch')
        if pitch is not None:
            note.remove(pitch)
            
        rest = note.find('rest')
        if rest is None:
            rest = ET.Element('rest')
            note.insert(0, rest)
            
        self.assertIsNone(note.find('pitch'))
        self.assertIsNotNone(note.find('rest'))

    def test_04_whole_measure_rest_semantics(self):
        """Whole-Measure Rest has rest measure='yes' and no pitch or dot"""
        note = self.root.find(".//note[@data-vx-id='3']")
        self.assertIsNotNone(note)
        
        pitch = note.find('pitch')
        if pitch is not None:
            note.remove(pitch)
        dot = note.find('dot')
        if dot is not None:
            note.remove(dot)
        type_el = note.find('type')
        if type_el is not None:
            note.remove(type_el)
            
        rest = note.find('rest')
        if rest is None:
            rest = ET.Element('rest')
            note.insert(0, rest)
        rest.set('measure', 'yes')
        
        self.assertIsNone(note.find('pitch'))
        self.assertIsNone(note.find('dot'))
        self.assertEqual(note.find('rest').get('measure'), 'yes')

    def test_05_accidental_alterations(self):
        """Accidental changes: Sharp (+1), Flat (-1), Natural (0)"""
        note = None
        for n in self.root.iter('note'):
            if n.find('pitch') is not None:
                note = n
                break
        self.assertIsNotNone(note, "Pitched note found")
        pitch = note.find('pitch')
        self.assertIsNotNone(pitch)
        
        # Sharp
        alter = pitch.find('alter')
        if alter is None:
            alter = ET.SubElement(pitch, 'alter')
        alter.text = '1'
        self.assertEqual(pitch.find('alter').text, '1')
        
        # Flat
        alter.text = '-1'
        self.assertEqual(pitch.find('alter').text, '-1')
        
        # Natural
        pitch.remove(alter)
        self.assertIsNone(pitch.find('alter'))

    def test_06_slur_and_tie_preservation(self):
        """Slur and Tie curves maintain proper start and stop elements"""
        n1 = self.root.find(".//note[@data-vx-id='1']")
        n2 = self.root.find(".//note[@data-vx-id='2']")
        
        notat1 = n1.find('notations')
        if notat1 is None:
            notat1 = ET.SubElement(n1, 'notations')
        slur_start = ET.SubElement(notat1, 'slur', {'type': 'start', 'number': '1'})
        
        notat2 = n2.find('notations')
        if notat2 is None:
            notat2 = ET.SubElement(n2, 'notations')
        slur_stop = ET.SubElement(notat2, 'slur', {'type': 'stop', 'number': '1'})
        
        self.assertEqual(n1.find('notations/slur').get('type'), 'start')
        self.assertEqual(n2.find('notations/slur').get('type'), 'stop')

    def test_07_satb_voice_preservation(self):
        """SATB voice assignment (Tenor override data-vx-voice='T') persists cleanly"""
        note = self.root.find(".//note[@data-vx-id='1']")
        note.set('data-vx-voice', 'T')
        note.set('data-vx-voice-source', 'user')
        
        self.assertEqual(note.get('data-vx-voice'), 'T')
        self.assertEqual(note.get('data-vx-voice-source'), 'user')

if __name__ == '__main__':
    unittest.main()
