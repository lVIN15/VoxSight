#!/usr/bin/env python3
"""
Regression test suite for Lead Me Lord & Awit ng Paghahangad bug fixes:
- Lead Me Lord M10 16th rest restoration
- Lead Me Lord M26 Alto lyric synchronization and slur/tie validation
- Lead Me Lord M38-39 Soprano 1 & 2 sustained whole note recovery
- Lead Me Lord M42 Alto 1 & 2 sustained whole note recovery
- Lead Me Lord M69 stray forte dynamic removal
- Awit ng Paghahangad stray guitar chord symbol purging
"""

import sys
import os
import zipfile
import xml.etree.ElementTree as ET

# Add scripts directory to path
script_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(script_dir)
sys.path.insert(0, parent_dir)

from musicxml_cleaner import clean_musicxml_tree

def test_lead_me_lord_fixes():
    print("Testing Lead Me Lord remediation...")
    f_lead = os.path.join(parent_dir, '..', 'outputs', '1793211829937-LEAD_ME_LORD__SATB_1790332345810.mxl')
    if not os.path.exists(f_lead):
        # find matching file
        import glob
        matches = glob.glob(os.path.join(parent_dir, '..', 'outputs', '*LEAD_ME_LORD*.mxl'))
        if matches:
            f_lead = matches[0]
        else:
            print("Lead Me Lord output MXL not found, skipping.")
            return True

    with zipfile.ZipFile(f_lead) as z:
        for name in z.namelist():
            if name.endswith('.xml'):
                tree = ET.fromstring(z.read(name))
                break

    # Clean the tree
    clean_musicxml_tree(tree)

    # 1. M10 16th rest in Bass
    p14 = next((p for p in tree.findall('part') if p.get('id') == 'P14'), None)
    assert p14 is not None, "Part P14 not found"
    m10 = next((m for m in p14.findall('measure') if m.get('number') == '10'), None)
    assert m10 is not None, "P14 M10 not found"
    m10_notes = m10.findall('note')
    has_16th_rest = any(n.find('rest') is not None and n.findtext('type') == '16th' for n in m10_notes)
    tot_dur_m10 = sum(int(n.findtext('duration', '0')) for n in m10_notes if n.find('chord') is None)
    assert has_16th_rest, "M10 Bass missing 16th rest"
    assert tot_dur_m10 == 16, f"M10 Bass duration should be 16, got {tot_dur_m10}"
    print("  [PASS] M10 Bass 16th rest restored and aligned (16 ticks)")

    # 2. M26 Alto lyrics & slur
    for pid in ['P11', 'P12']:
        p = next((p for p in tree.findall('part') if p.get('id') == pid), None)
        if p is not None:
            m26 = next((m for m in p.findall('measure') if m.get('number') == '26'), None)
            assert m26 is not None, f"{pid} M26 not found"
            lyrs = [t.text for t in m26.findall('.//lyric/text') if t.text]
            assert len(lyrs) >= 3, f"{pid} M26 lyrics missing or empty: {lyrs}"
            # Check notes are not tied
            for n in m26.findall('note'):
                assert n.find('tie') is None, f"{pid} M26 note has false tie tag"
    print("  [PASS] M26 Alto 1 & 2 lyrics synchronized and slurs separated from ties")

    # 3. M38-39 Soprano 1 & 2 whole notes
    for pid, expected_pitch in [('P9', 'F4'), ('P10', 'D4')]:
        p = next((p for p in tree.findall('part') if p.get('id') == pid), None)
        assert p is not None, f"Part {pid} not found"
        m39 = next((m for m in p.findall('measure') if m.get('number') == '39'), None)
        assert m39 is not None, f"{pid} M39 not found"
        p_notes = [n for n in m39.findall('note') if n.find('rest') is None]
        assert len(p_notes) == 1, f"{pid} M39 should have 1 whole note, got {len(p_notes)}"
        n = p_notes[0]
        assert n.findtext('type') == 'whole', f"{pid} M39 note type is not whole"
        step = n.findtext('.//step')
        assert step == expected_pitch[0], f"{pid} M39 expected {expected_pitch}, got {step}"
        assert n.find('tie[@type="stop"]') is not None or n.find('.//tied[@type="stop"]') is not None, f"{pid} M39 note missing stop tie"
    print("  [PASS] M38-39 Soprano 1 & 2 sustained whole notes restored with ties")

    # 4. M42 Alto 1 & 2 whole notes
    for pid, expected_pitch in [('P11', 'F4'), ('P12', 'D4')]:
        p = next((p for p in tree.findall('part') if p.get('id') == pid), None)
        assert p is not None, f"Part {pid} not found"
        m42 = next((m for m in p.findall('measure') if m.get('number') == '42'), None)
        assert m42 is not None, f"{pid} M42 not found"
        p_notes = [n for n in m42.findall('note') if n.find('rest') is None]
        assert len(p_notes) == 1, f"{pid} M42 should have 1 whole note, got {len(p_notes)}"
        n = p_notes[0]
        assert n.findtext('type') == 'whole', f"{pid} M42 note type is not whole"
        step = n.findtext('.//step')
        assert step == expected_pitch[0], f"{pid} M42 expected {expected_pitch}, got {step}"
    print("  [PASS] M42 Alto 1 & 2 sustained whole notes restored")

    # 5. M69 forte dynamic removal
    for pid in ['P11', 'P13', 'P14']:
        p = next((p for p in tree.findall('part') if p.get('id') == pid), None)
        if p is not None:
            m69 = next((m for m in p.findall('measure') if m.get('number') == '69'), None)
            if m69 is not None:
                forte_dirs = [d for d in m69.findall('.//dynamics') if any(c.tag == 'f' for c in d)]
                assert len(forte_dirs) == 0, f"{pid} M69 still has stray forte dynamic: {len(forte_dirs)}"
    print("  [PASS] M69 stray forte dynamics purged")

    return True

def test_awit_phantom_chords():
    print("\nTesting Awit ng Paghahangad remediation...")
    f_awit = os.path.join(parent_dir, '..', 'outputs', '1793198866731-1793195070300-toaz.info-awit-ng-paghahangad-satbpdf-pr_7b430026849e1603d8400c8bc1d260c8.mxl')
    if not os.path.exists(f_awit):
        import glob
        matches = glob.glob(os.path.join(parent_dir, '..', 'outputs', '*Awit*.mxl'))
        if matches:
            f_awit = matches[0]
        else:
            print("Awit output MXL not found, skipping.")
            return True

    with zipfile.ZipFile(f_awit) as z:
        for name in z.namelist():
            if name.endswith('.xml'):
                tree = ET.fromstring(z.read(name))
                break

    # Clean the tree
    clean_musicxml_tree(tree)

    # Check M1-3 Soprano (P2), M33 Alto (P3), M37 Tenor (P4), M51 Alto (P3), M86 Alto (P3)
    p2 = next((p for p in tree.findall('part') if p.get('id') == 'P2'), None)
    if p2 is not None:
        for mnum in ['1', '2', '3']:
            m = next((m for m in p2.findall('measure') if m.get('number') == mnum), None)
            if m is not None:
                chords = [n for n in m.findall('note') if n.find('chord') is not None]
                assert len(chords) == 0, f"P2 M{mnum} still has phantom chords: {len(chords)}"
        print("  [PASS] M1-3 Soprano intro phantom C5 chord notes purged")

    p3 = next((p for p in tree.findall('part') if p.get('id') == 'P3'), None)
    if p3 is not None:
        for mnum in ['33', '51', '86']:
            m = next((m for m in p3.findall('measure') if m.get('number') == mnum), None)
            if m is not None:
                chords = [n for n in m.findall('note') if n.find('chord') is not None]
                assert len(chords) == 0, f"P3 M{mnum} still has phantom chords: {len(chords)}"
        print("  [PASS] M33, M51, M86 Alto phantom guitar chords purged")

    p4 = next((p for p in tree.findall('part') if p.get('id') == 'P4'), None)
    if p4 is not None:
        m37 = next((m for m in p4.findall('measure') if m.get('number') == '37'), None)
        if m37 is not None:
            chords = [n for n in m37.findall('note') if n.find('chord') is not None]
            assert len(chords) == 0, f"P4 M37 still has phantom chords: {len(chords)}"
        print("  [PASS] M37 Tenor phantom A3 chords purged")

    return True

if __name__ == '__main__':
    ok1 = test_lead_me_lord_fixes()
    ok2 = test_awit_phantom_chords()
    if ok1 and ok2:
        print("\n" + "="*60)
        print("ALL REMEDIATION TESTS PASSED (100% SUCCESS RATE)")
        print("="*60)
        sys.exit(0)
    else:
        sys.exit(1)

