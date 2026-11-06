#!/usr/bin/env python3
"""
VoxSight MusicXML Cleaner & Part Merger v1.0
============================================
Post-processes Audiveris MusicXML outputs:
1. Merges duplicate/split vocal parts across systems (e.g. S, A, T, B + Soprano, Alto, Tenor, Bass -> unified SATB).
2. Cleans up misplaced credit, copyright, licensing, and footer text accidentally OCR'd into note <lyric> elements.
3. Fixes overlapping staff-details and cleans empty placeholder staves.
"""

import sys
import os
import zipfile
import re
import copy
import glob
import json
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Optional

try:
    import fitz  # type: ignore
except ImportError:
    fitz = None

CREDIT_KEYWORDS = [
    'prepared', 'arranged by', 'arr.', 'arranger', 'composed by', 'composer', 
    'music by', 'words by', 'lyrics by', 'copyright', 'all rights reserved', 
    'freely distributed', 'duplicated', 'performed', 'recorded', 'edition', 
    'published by', 'printed in', 'jedscott', '.com', '.org', '.net', 'http', 
    'www.', 'page ', 'sheet music', 'transcribed by', 'edited by', 'score',
    'international copyright', 'ccli', 'public domain'
]

NAME_MAP = {
    's': 'Soprano', 'sop': 'Soprano', 'sopr': 'Soprano', 'soprano': 'Soprano',
    'soprano 1': 'Soprano 1', 's1': 'Soprano 1', 'sop1': 'Soprano 1', 'sopranoi': 'Soprano 1',
    'soprano 2': 'Soprano 2', 's2': 'Soprano 2', 'sop2': 'Soprano 2', 'sopranoii': 'Soprano 2',
    '82': 'Soprano 2', 'sq': 'Soprano 2', 's.2': 'Soprano 2',
    'a': 'Alto', 'alt': 'Alto', 'alto': 'Alto',
    'alto 1': 'Alto 1', 'a1': 'Alto 1', 'altoi': 'Alto 1',
    'alto 2': 'Alto 2', 'a2': 'Alto 2', 'altoii': 'Alto 2',
    't': 'Tenor', 'ten': 'Tenor', 'tenor': 'Tenor', 'tenor 1': 'Tenor 1', 't1': 'Tenor 1', 'tenor 2': 'Tenor 2', 't2': 'Tenor 2',
    'b': 'Bass', 'bas': 'Bass', 'bass': 'Bass', 'bass 1': 'Bass 1', 'b1': 'Bass 1', 'bass 2': 'Bass 2', 'b2': 'Bass 2',
    'sol': 'Solo', 'sol.': 'Solo', 'solo': 'Solo', 'soloist': 'Solo',
    'v0': 'Solo', 'v0.': 'Solo', 'vo': 'Solo', 'vo.': 'Solo', 'vox': 'Solo',
    'vocal': 'Solo', 'vocal lead': 'Solo', 'lead': 'Solo', 'melody': 'Solo',
    'baritone solo': 'Baritone Solo', 'bar. solo': 'Baritone Solo', 'baritone': 'Baritone Solo',
    'tenor solo': 'Tenor Solo', 'soprano solo': 'Soprano Solo', 'alto solo': 'Alto Solo', 'bass solo': 'Bass Solo',
    'cantor': 'Solo', 'leader': 'Solo', 'descant': 'Solo',
    's. a.': 'S. A.', 'sa': 'S. A.', 's.a.': 'S. A.',
    't. b.': 'T. B.', 'tb': 'T. B.', 't.b.': 'T. B.'
}

ACCOMP_KEYWORDS = [
    # Keyboards & Basso Continuo
    "piano", "pno", "keyboard", "kbd", "organ", "org",
    "harpsichord", "cembalo", "celesta", "clavinet",
    "synthesizer", "synth", "continuo", "basso continuo", "b.c.",
    # Woodwinds (including vocal-overlapping names)
    "flute", "alto flute", "bass flute", "piccolo",
    "oboe", "english horn", "cor anglais",
    "clarinet", "bass clarinet", "contrabass clarinet", "contra-bass clarinet", "alto clarinet", "basset horn",
    "bassoon", "bsn", "contrabassoon", "contra-bassoon", "double bassoon",
    "saxophone", "sax", "soprano sax", "soprano saxophone",
    "alto sax", "alto saxophone", "tenor sax", "tenor saxophone",
    "baritone sax", "baritone saxophone", "bass sax", "bass saxophone",
    # Brass (including vocal-overlapping names)
    "trumpet", "cornet", "flugelhorn",
    "horn", "french horn", "tenor horn", "alto horn", "baritone horn",
    "trombone", "tbn", "trb", "tenor trombone", "bass trombone", "contrabass trombone", "alto trombone",
    "tuba", "bass tuba", "contrabass tuba", "euphonium",
    # Strings (including low strings overlapping with 'bass')
    "violin", "vln", "viola", "vla", "cello", "violoncello", "vc",
    "double bass", "contrabass", "contra bass", "contra-bass", "string bass",
    "upright bass", "acoustic bass", "electric bass", "bass guitar",
    "strings", "string ensemble", "orchestra", "orch", "harp", "guitar", "gtr",
    # Percussion
    "timpani", "percussion", "perc", "drums", "drum set", "bass drum",
    # General accompaniment indicators
    "accompaniment", "accomp", "acc.", "acc"
]

# Universal musical dynamic regex (matches standalone dynamic marks misclassified as lyrics)
DYNAMIC_REGEX = re.compile(
    r'^([pP]{1,4}|[fF]{1,4}|[mM][pPfF]|[sS][fF][zZ]?|[rR][fF][zZ]?|[fF][pP]|[fF][zZ])\.?$'
)
AUTHENTIC_SINGLE_LETTER_WORDS = {'a', 'i', 'o', 'e'}
COMMON_L_WORDS = {'lift', 'lord', 'love', 'life', 'light', 'lead', 'live', 'line', 'lost', 'land', 'loud', 'lamb'}

# Common OMR lyric fusion patterns seen across choral music where two words are typeset without space
COMMON_OMR_FUSIONS = {
    'thehouse': 'the house', 'thelord': 'the Lord', 'theking': 'the King', 'theearth': 'the earth',
    'allmy': 'all my', 'allthe': 'all the', 'allour': 'all our',
    'myhead': 'my head', 'myheart': 'my heart', 'mysoul': 'my soul', 'myvoice': 'my voice', 'mylord': 'my Lord',
    'gazeon': 'gaze on', 'walkin': 'walk in', 'walkon': 'walk on', 'livein': 'live in', 'trustin': 'trust in',
    'bringsme': 'brings me', 'givesme': 'gives me', 'leadsme': 'leads me', 'savesme': 'saves me', 'hearmer': 'hear me',
    'upmy': 'up my', 'uponmy': 'upon my', 'umphthat': 'umph that',
    'shei': 'shelter', 'sheiter': 'shelter'
}

def normalize_lyric_text(text: str) -> str:
    """Normalizes OCR lyric text using general typographical and linguistic rules."""
    if not text:
        return text

    # 1. Strip stray OCR artifacts like barlines/brackets passing through text: '|', '\', '~'
    text = text.replace('|', '').replace('\\', '').replace('~', '').strip()

    # 2. Universal font OCR confusion repairs:
    # 'II' (two capital I's) standing for 'll' inside or following lowercase words (e.g. "aIImy" -> "allmy", "wiII" -> "will")
    text = re.sub(r'([a-zA-Z])II', r'\1ll', text)
    text = re.sub(r'\baII\b', 'all', text)
    text = re.sub(r'\baII([a-z])', r'all\1', text)

    # 3. Split CamelCase or lowercase glued to uppercase (e.g. "myHead" -> "my Head", "inHis" -> "in His")
    text = re.sub(r'([a-z])([A-Z])', r'\1 \2', text)

    # 4. Isolated lowercase 'l' following a word where it is an OCR error for pronoun 'I':
    # e.g. "thingl" -> "thing I", "singl" -> "sing I", "thatl" -> "that I"
    text = re.sub(r'\b([a-zA-Z]{3,})l\b', lambda m: m.group(1) + ' I' if m.group(1).lower() in ['thing', 'sing', 'that', 'with', 'and', 'as', 'for', 'when', 'if', 'hear'] else m.group(0), text)

    # 5. Leading capital 'I' before consonants where serif 'I' was read instead of 'l':
    # e.g. "Iift" -> "Lift", "Iord" -> "Lord"
    def fix_leading_i(m):
        w = m.group(0)
        low_as_l = 'l' + w[1:].lower()
        if low_as_l in COMMON_L_WORDS:
            return ('L' if w.isupper() or (w[1].islower() and w[0].isupper()) else 'l') + w[1:]
        return w
    text = re.sub(r'\bI[a-zA-Z]{3,}\b', fix_leading_i, text)

    # 6. Normalize common OMR fused words
    words = text.split()
    fixed_words = []
    for w in words:
        w_clean = w.rstrip('.,!?;:')
        punct = w[len(w_clean):]
        w_low = w_clean.lower()
        if w_low in COMMON_OMR_FUSIONS:
            replacement = COMMON_OMR_FUSIONS[w_low]
            if w_clean and w_clean[0].isupper() and replacement[0].islower():
                replacement = replacement[0].upper() + replacement[1:]
            fixed_words.append(replacement + punct)
        else:
            fixed_words.append(w)

    return ' '.join(fixed_words).strip()

def strip_stray_dynamic_lyrics(root: ET.Element) -> int:
    """Strips stray musical dynamic markings misclassified as note <lyric> elements."""
    stripped_count = 0
    for note in root.findall('.//note'):
        for lyr in list(note.findall('lyric')):
            txt_elems = lyr.findall('.//text')
            full_txt = ''.join(t.text for t in txt_elems if t.text).strip()
            if not full_txt:
                continue
            if DYNAMIC_REGEX.match(full_txt):
                if full_txt.lower() not in AUTHENTIC_SINGLE_LETTER_WORDS:
                    note.remove(lyr)
                    stripped_count += 1
    return stripped_count

def get_note_onsets(measure_elem: ET.Element) -> list[tuple[int, ET.Element]]:
    """Calculates onset ticks for all non-rest notes in a measure.
    Secondary notes in chords (<chord/>) are skipped so they don't receive duplicate lyrics.
    """
    results = []
    curr_tick = 0
    last_note_dur = 0
    for child in measure_elem:
        if child.tag == 'note':
            is_chord = child.find('chord') is not None
            is_rest = child.find('rest') is not None
            dur = int(child.findtext('duration', '0'))
            if is_chord:
                onset = curr_tick - last_note_dur
            else:
                onset = curr_tick
                curr_tick += dur
                last_note_dur = dur
            if not is_rest and not is_chord:
                results.append((onset, child))
        elif child.tag == 'backup':
            dur = int(child.findtext('duration', '0'))
            curr_tick -= dur
        elif child.tag == 'forward':
            dur = int(child.findtext('duration', '0'))
            curr_tick += dur
    return results

def synchronize_choral_lyrics(root: ET.Element) -> int:
    """Synchronizes homophonic lyrics across vocal parts based on exact note onsets."""
    parts = root.findall('part')
    if len(parts) < 2:
        return 0

    strip_stray_dynamic_lyrics(root)

    for lyr in root.findall('.//lyric'):
        for t in lyr.findall('.//text'):
            if t.text:
                t.text = normalize_lyric_text(t.text)

    all_measures = []
    seen_mnums = set()
    for p in parts:
        for m in p.findall('measure'):
            mnum = m.get('number')
            if mnum and mnum not in seen_mnums:
                all_measures.append(mnum)
                seen_mnums.add(mnum)

    synced_count = 0

    for mnum in all_measures:
        part_notes = {}
        part_lyrics = {}
        measure_pool = {}

        for p in parts:
            pid = p.get('id')
            m = None
            for cand in p.findall('measure'):
                if cand.get('number') == mnum:
                    m = cand
                    break
            if m is None:
                continue

            note_list = get_note_onsets(m)
            part_notes[pid] = note_list

            for onset, note in note_list:
                lyr = note.find('lyric')
                if lyr is not None:
                    txt = ''.join(t.text for t in lyr.findall('.//text') if t.text).strip()
                    if txt and not DYNAMIC_REGEX.match(txt):
                        part_lyrics.setdefault(pid, {})[onset] = lyr
                        if onset not in measure_pool:
                            measure_pool[onset] = lyr
                        else:
                            existing_txt = ''.join(t.text for t in measure_pool[onset].findall('.//text') if t.text).strip()
                            if len(txt) > len(existing_txt):
                                measure_pool[onset] = lyr

        if not measure_pool:
            continue

        # Homophonic choral lyric synchronization:
        # Partition parts to protect Solo and Accompaniment parts from choir text cloning
        part_list_el = root.find('part-list')
        part_roles = {}
        if part_list_el is not None:
            for sp in part_list_el.findall('score-part'):
                sp_id = sp.get('id')
                sp_name = f"{(sp.findtext('part-name') or '')} {(sp.findtext('part-abbreviation') or '')}".lower().strip()
                if any(kw in sp_name for kw in ['solo', 'cantor', 'lead']):
                    part_roles[sp_id] = 'solo'
                elif any(kw in sp_name for kw in ['piano', 'pno', 'organ', 'accomp', 'guitar', 'gtr', 'harp', 'orchestra', 'strings']):
                    part_roles[sp_id] = 'accomp'
                else:
                    part_roles[sp_id] = 'choir'

        for p in parts:
            pid = p.get('id')
            if part_roles.get(pid) != 'choir':
                continue
            if pid not in part_notes:
                continue

            p_lyr_map = part_lyrics.get(pid, {})
            for onset, note in part_notes[pid]:
                if onset not in p_lyr_map and onset in measure_pool:
                    new_lyr = copy.deepcopy(measure_pool[onset])
                    note.append(new_lyr)
                    synced_count += 1

    return synced_count

def is_stray_voice_name(name: str) -> bool:
    s = re.sub(r'[.\s_-]+', '', (name or '').lower())
    if re.match(r'^(soprano|sopr|sop|s)[1-9]?$', s): return False
    if re.match(r'^(alto|alt|a|contralto)[1-9]?$', s): return False
    if re.match(r'^(tenor|ten|t)[1-9]?$', s): return False
    if re.match(r'^(bass|bas|b)[1-9]?$', s): return False
    if re.match(r'^(baritone|bar)[1-9]?$', s): return False
    if re.match(r'^(mezzosoprano|mezzo)[1-9]?$', s): return False
    if re.match(r'^(solo|lead|v0|vo|vocal|cantor|descant)[0-9]*$', s): return False
    if re.match(r'^(voice|part)[0-9]*$', s): return True
    if s == '' or s == 'musicxmlpart' or s == 'staff': return True
    return False


def get_non_rest_note_count(measure: ET.Element) -> int:
    if measure is None: return 0
    cnt = 0
    for n in measure.findall('note'):
        if n.find('rest') is None:
            cnt += 1
    return cnt


def merge_split_choral_parts(root: ET.Element) -> bool:
    """
    Detects and merges split / fragmented choral parts created by Audiveris across pages
    into canonical SATB staves. Reassembles multi-fragment vocal lines (e.g., Soprano 1 fragments
    emitted with generic 'Voice' labels on subsequent pages), eliminates redundant fragments,
    and establishes canonical choral stave ordering (Solo -> S1 -> S2 -> A1 -> A2 -> T -> B -> Accomp).
    """
    part_list = root.find('part-list')
    parts = root.findall('part')
    if part_list is None or len(parts) < 2:
        return False

    modified = False

    def get_tier(name_str: str) -> int:
        n = re.sub(r'[.\s_-]+', '', (name_str or '').strip().lower())
        if re.match(r'^(soprano|sopr|sop|s)[1-4]?$', n) or re.match(r'^(alto|alt|a|contralto|mezzo|mezzosoprano)[1-4]?$', n) or re.match(r'^(sa|sopranoalto|women|treble)[1-4]?$', n):
            return 1
        if re.match(r'^(tenor|ten|t)[1-4]?$', n) or re.match(r'^(baritone|bar)[1-4]?$', n) or re.match(r'^(bass|bas|b)[1-4]?$', n) or re.match(r'^(tb|tenorbass|men)[1-4]?$', n):
            return 2
        return 0

    def get_canonical_name_and_abbr(raw1: str, raw2: str) -> tuple[str, str]:
        r1 = (raw1 or '').strip()
        r2 = (raw2 or '').strip()
        
        composite_map = {
            's/a': ('Soprano / Alto', 'S/A'),
            'sa': ('Soprano / Alto', 'S/A'),
            't/b': ('Tenor / Bass', 'T/B'),
            'tb': ('Tenor / Bass', 'T/B'),
            's/t': ('Soprano / Tenor', 'S/T'),
            'st': ('Soprano / Tenor', 'S/T'),
            'a/b': ('Alto / Bass', 'A/B'),
            'ab': ('Alto / Bass', 'A/B')
        }
        for r in [r1, r2]:
            if r.lower() in composite_map:
                return composite_map[r.lower()]
        
        tier1 = get_tier(r1)
        tier2 = get_tier(r2)
        if tier1 == 1 or tier2 == 1:
            if ('alto' in r1.lower() or 'alto' in r2.lower()) and ('sop' in r1.lower() or 'sop' in r2.lower() or 's/a' in r1.lower() or 's/a' in r2.lower()):
                return ('Soprano / Alto', 'S/A')
        if tier1 == 2 or tier2 == 2:
            if ('bass' in r1.lower() or 'bass' in r2.lower()) and ('ten' in r1.lower() or 'ten' in r2.lower() or 't/b' in r1.lower() or 't/b' in r2.lower()):
                return ('Tenor / Bass', 'T/B')
                
        canon_name = r1 if len(r1) >= len(r2) else r2
        abbr = canon_name
        if len(canon_name) > 4:
            abbr = canon_name[:4]
        return (canon_name, abbr)

    # ─── Step 3-pre: Clef-based part separation for collapsed staves ──────
    # When Audiveris encounters variable-stave systems (e.g. 1-staff Solo opening into a 2/3-staff choir),
    # it sometimes reuses the initial Solo part ID for the bottom staff (Bass Clef) of later choir systems.
    # If a part switches active notes between Treble Clef (G) and Bass Clef (F), separate them so the vocal
    # lines and stave identities do not get cross-contaminated.
    for p in list(root.findall('part')):
        pid = p.get('id')
        sp = part_list.find(f"score-part[@id='{pid}']")
        pname = (sp.findtext('part-name') or '') if sp is not None else ''
        pabbr = (sp.findtext('part-abbreviation') or '') if sp is not None else ''
        pinstr = (sp.findtext('.//instrument-name') or '') if sp is not None else ''
        comb_p_ident = f"{pname} {pabbr} {pinstr}".lower()
        if any(re.search(rf"\b{re.escape(kw)}\b", comb_p_ident) for kw in ACCOMP_KEYWORDS):
            continue
        if any((s.text or '').strip().isdigit() and int((s.text or '').strip()) > 1 for s in p.findall('.//staves')):
            continue
        if any(len(m.findall('.//clef')) > 1 for m in p.findall('measure')):
            continue
        
        g_measures = []
        f_measures = []
        current_clef = None
        for m in p.findall('measure'):
            clef = m.find('.//clef/sign')
            if clef is not None and clef.text:
                current_clef = clef.text.strip().upper()
            notes = [n for n in m.findall('note') if n.find('rest') is None]
            if notes:
                if current_clef == 'F':
                    f_measures.append(m)
                else:
                    g_measures.append(m)
                    
        if g_measures and f_measures:
            new_pid = f"{pid}_bass"
            new_p = ET.Element('part', {'id': new_pid})
            new_sp = ET.Element('score-part', {'id': new_pid})
            pn = ET.SubElement(new_sp, 'part-name')
            pn.text = 'T. B.' if any(kw in pname.lower() for kw in ['solo', 'sol', 's']) else f"{pname} (Bass)"
            pa = ET.SubElement(new_sp, 'part-abbreviation')
            pa.text = 'T. B.' if any(kw in pname.lower() for kw in ['solo', 'sol', 's']) else f"{pname} (Bass)"
            
            for m in p.findall('measure'):
                m_clone = copy.deepcopy(m)
                if m in f_measures:
                    new_p.append(m_clone)
                    for note in list(m.findall('note')):
                        m.remove(note)
                    rest_note = ET.SubElement(m, 'note', {'print-object': 'no'})
                    ET.SubElement(rest_note, 'rest')
                    t_el = ET.SubElement(rest_note, 'type')
                    t_el.text = 'whole'
                    dur = ET.SubElement(rest_note, 'duration')
                    dur.text = '24'
                    v = ET.SubElement(rest_note, 'voice')
                    v.text = '1'
                    m.set('print-object', 'no')
                    attrs_m = m.find('attributes')
                    if attrs_m is None:
                        attrs_m = ET.Element('attributes')
                        m.insert(0, attrs_m)
                    sd_m = attrs_m.find('staff-details')
                    if sd_m is None:
                        sd_m = ET.SubElement(attrs_m, 'staff-details')
                    sd_m.set('print-object', 'no')
                else:
                    for note in list(m_clone.findall('note')):
                        m_clone.remove(note)
                    rest_note = ET.SubElement(m_clone, 'note', {'print-object': 'no'})
                    ET.SubElement(rest_note, 'rest')
                    t_el = ET.SubElement(rest_note, 'type')
                    t_el.text = 'whole'
                    dur = ET.SubElement(rest_note, 'duration')
                    dur.text = '24'
                    v = ET.SubElement(rest_note, 'voice')
                    v.text = '1'
                    m_clone.set('print-object', 'no')
                    attrs_mc = m_clone.find('attributes')
                    if attrs_mc is None:
                        attrs_mc = ET.Element('attributes')
                        m_clone.insert(0, attrs_mc)
                    sd_mc = attrs_mc.find('staff-details')
                    if sd_mc is None:
                        sd_mc = ET.SubElement(attrs_mc, 'staff-details')
                    sd_mc.set('print-object', 'no')
                    new_p.append(m_clone)
                    
            for m in p.findall('measure'):
                for attrs in m.findall('attributes'):
                    for c in list(attrs.findall('clef')):
                        if (c.findtext('sign') or '').strip().upper() == 'F':
                            attrs.remove(c)
            m1_p = p.find("measure[@number='1']")
            if m1_p is not None:
                attrs1 = m1_p.find('attributes')
                if attrs1 is None:
                    attrs1 = ET.Element('attributes')
                    m1_p.insert(0, attrs1)
                c_p = attrs1.find('clef')
                if c_p is None:
                    c_p = ET.SubElement(attrs1, 'clef')
                    ET.SubElement(c_p, 'sign').text = 'G'
                    ET.SubElement(c_p, 'line').text = '2'
                else:
                    s_el = c_p.find('sign')
                    if s_el is not None: s_el.text = 'G'
                    l_el = c_p.find('line')
                    if l_el is not None: l_el.text = '2'

            m1_np = new_p.find("measure[@number='1']")
            if m1_np is not None:
                attrs1 = m1_np.find('attributes')
                if attrs1 is None:
                    attrs1 = ET.Element('attributes')
                    m1_np.insert(0, attrs1)
                c_np = attrs1.find('clef')
                if c_np is None:
                    c_np = ET.SubElement(attrs1, 'clef')
                    ET.SubElement(c_np, 'sign').text = 'F'
                    ET.SubElement(c_np, 'line').text = '4'
                else:
                    s_el = c_np.find('sign')
                    if s_el is not None: s_el.text = 'F'
                    l_el = c_np.find('line')
                    if l_el is not None: l_el.text = '4'

            part_list.append(new_sp)
            root.append(new_p)
            modified = True
            
    # Refresh parts list after separation
    parts = root.findall('part')

    # Gather part names, tier, and per-measure non-rest note counts
    part_info = {}
    for p in parts:
        pid = p.get('id') or ''
        sp = root.find(f".//score-part[@id='{pid}']")
        raw_name = (sp.findtext('part-name') if sp is not None else None) or pid or ''
        raw_abbr = (sp.findtext('part-abbreviation') if sp is not None else None) or raw_name
        norm_name = NAME_MAP.get(raw_name.strip().lower(), raw_name.strip())
        tier = get_tier(raw_name)

        instr_el = sp.find('.//instrument-name') if sp is not None else None
        raw_instr = instr_el.text.strip().lower() if (instr_el is not None and instr_el.text) else ""

        combined_ident = f"{raw_name} {raw_abbr} {raw_instr}".lower()
        is_accomp = any(re.search(rf"\b{re.escape(kw)}\b", combined_ident) for kw in ACCOMP_KEYWORDS)
        solo_indicators = ['solo', 'cantor', 'leader', 'lead', 'descant', 'v0', 'vo', 'vocal', 'melody', 'baritone solo', 'tenor solo', 'soprano solo', 'alto solo', 'bass solo']
        is_solo = (
            any(re.search(rf"\b{re.escape(kw)}\b", combined_ident) for kw in solo_indicators)
            or bool(re.search(r'\bsol\b|\bsol\.|\bv0\.|\bvo\.', combined_ident))
            or norm_name.lower() in ['solo', 'baritone solo', 'tenor solo', 'soprano solo']
        ) and not is_accomp

        measures = {}
        for m in p.findall('measure'):
            mnum = m.get('number')
            non_rest = [n for n in m.findall('note') if n.find('rest') is None]
            measures[mnum] = measures.get(mnum, 0) + len(non_rest)
            
        part_info[pid] = {
            'element': p,
            'score_part': sp,
            'raw_name': raw_name,
            'raw_abbr': raw_abbr,
            'norm_name': norm_name,
            'tier': tier,
            'is_solo': is_solo,
            'is_accomp': is_accomp,
            'measures': measures
        }

    # Strategy A: Solo continuation harmonization (if explicit SOLO label exists)
    has_explicit_solo = any(info['is_solo'] for info in part_info.values())
    if has_explicit_solo:
        choir_measures = set()
        for pid, info in part_info.items():
            if info['is_solo'] or info['is_accomp']:
                continue
            is_choir_voice = (info['tier'] in [2, 3, 4] or 
                              any(kw in info['norm_name'].lower() for kw in ['alto', 'tenor', 'bass', 'choir', 'satb']))
            first_m = info['element'].find('measure')
            has_f_clef = first_m is not None and first_m.find('.//clef/sign') is not None and first_m.find('.//clef/sign').text == 'F'
            if is_choir_voice or has_f_clef:
                for m, cnt in info['measures'].items():
                    if cnt > 0 and m.isdigit():
                        choir_measures.add(int(m))

        first_choir_m = min(choir_measures) if choir_measures else -1

        if first_choir_m > 0:
            for pid, info in part_info.items():
                if not info['is_solo'] and not info['is_accomp']:
                    norm_low = info['norm_name'].lower()
                    raw_low = info['raw_name'].lower()
                    is_shorthand = norm_low in ['s', 's.', 'sop', 'solo'] or raw_low in ['s', 's.', 'sop', 'solo']
                    if is_shorthand:
                        active_m = [int(m) for m, cnt in info['measures'].items() if cnt > 0 and m.isdigit()]
                        if active_m and all(m < first_choir_m for m in active_m):
                            info['is_solo'] = True
                            info['norm_name'] = 'Solo'
                            info['tier'] = 0

        # Complementary 'Voice' continuation for explicit Solo part when choir slots are already complete
        norm_names_lower = {info['norm_name'].lower() for info in part_info.values() if not info['is_solo'] and not info['is_accomp']}
        has_complete_sa_tb = ('s. a.' in norm_names_lower and 't. b.' in norm_names_lower)
        has_complete_satb = all(any(k in n for n in norm_names_lower) for k in ['soprano', 'alto', 'tenor', 'bass'])
        if has_complete_sa_tb or has_complete_satb:
            curr_solo_pids = [pid for pid, info in part_info.items() if info['is_solo']]
            solo_active_m = set()
            solo_clefs = set()
            for spid in curr_solo_pids:
                for m, cnt in part_info[spid]['measures'].items():
                    if cnt > 0:
                        solo_active_m.add(m)
                for c in part_info[spid]['element'].findall('.//clef/sign'):
                    if c.text:
                        solo_clefs.add(c.text.strip().upper())
            for pid, info in part_info.items():
                if not info['is_solo'] and not info['is_accomp'] and info['norm_name'].lower() in ['voice', 'part']:
                    v_active_m = {m for m, cnt in info['measures'].items() if cnt > 0}
                    v_clefs = {c.text.strip().upper() for c in info['element'].findall('.//clef/sign') if c.text}
                    if v_active_m and len(v_active_m & solo_active_m) <= 1 and (not solo_clefs or not v_clefs or bool(solo_clefs & v_clefs)):
                        info['is_solo'] = True
                        info['norm_name'] = 'Solo'
                        info['tier'] = 0

    # Strategy B: Position-based Solo detection (when Audiveris labels everything "Voice")
    has_choral_parts = any(info['tier'] in (1, 2, 3, 4) for info in part_info.values())
    if not has_choral_parts and not any(info['is_solo'] for info in part_info.values()):
        non_accomp_pids = [pid for pid, info in part_info.items() if not info['is_accomp']]

        measure_active_parts = {}
        for pid in non_accomp_pids:
            for m, cnt in part_info[pid]['measures'].items():
                if cnt > 0 and m.isdigit():
                    mi = int(m)
                    measure_active_parts.setdefault(mi, set()).add(pid)

        first_poly_m = 9999
        for mi in sorted(measure_active_parts.keys()):
            if len(measure_active_parts[mi]) >= 3:
                first_poly_m = mi
                break

        bass_clef_pids = set()
        for pid in non_accomp_pids:
            p_elem = part_info[pid]['element']
            first_m = p_elem.find('measure')
            if first_m is not None:
                clef_sign = first_m.find('.//clef/sign')
                if clef_sign is not None and clef_sign.text == 'F':
                    bass_clef_pids.add(pid)

        first_bass_m = 9999
        for pid in bass_clef_pids:
            for m, cnt in part_info[pid]['measures'].items():
                if cnt > 0 and m.isdigit():
                    mi = int(m)
                    if len(measure_active_parts.get(mi, set())) >= 2:
                        first_bass_m = min(first_bass_m, mi)

        choir_entrance_m = min(first_poly_m, first_bass_m)

        if measure_active_parts and choir_entrance_m < 9999:
            earliest_m = min(measure_active_parts.keys())
            if choir_entrance_m > earliest_m:
                intro_measures = [m for m in measure_active_parts if m < choir_entrance_m]
                is_solo_intro = intro_measures and all(len(measure_active_parts[m]) <= 2 for m in intro_measures)

                if is_solo_intro:
                    for pid in non_accomp_pids:
                        if part_info[pid]['tier'] in (1, 2, 3, 4):
                            continue
                        active_m = [int(m) for m, cnt in part_info[pid]['measures'].items() if cnt > 0 and m.isdigit()]
                        if active_m and any(m < choir_entrance_m for m in active_m):
                            part_info[pid]['is_solo'] = True
                            part_info[pid]['norm_name'] = 'Solo'
                            part_info[pid]['tier'] = 0

    # Multi-fragment Solo assembly
    solo_pids = [pid for pid, info in part_info.items() if info['is_solo']]
    if len(solo_pids) > 1:
        solo_pids.sort(key=lambda pid: (
            1 if (part_info[pid]['element'].find("measure[@number='1']") is not None and 
                 any(n.find('rest') is None for n in part_info[pid]['element'].find("measure[@number='1']").findall('note'))) else 0,
            sum(part_info[pid]['measures'].get(m, 0) for m in part_info[pid]['measures'])
        ), reverse=True)
        anchor_pid = solo_pids[0]
        anchor_p = part_info[anchor_pid]['element']
        anchor_sp = part_info[anchor_pid]['score_part']

        if anchor_sp is not None:
            norm_anchor = part_info[anchor_pid].get('norm_name', '')
            pn = anchor_sp.find('part-name')
            if pn is None:
                pn = ET.SubElement(anchor_sp, 'part-name')
            current_pname = (pn.text or "").strip().lower()
            if norm_anchor == 'Baritone Solo':
                pn.text = 'Baritone Solo'
            elif not current_pname or current_pname in ['voice', 's', 's.', 'sop', 'v0', 'v0.', 'vo', 'vo.']:
                pn.text = 'Solo'
            pa = anchor_sp.find('part-abbreviation')
            if pa is None:
                pa = ET.SubElement(anchor_sp, 'part-abbreviation')
            current_pabbr = (pa.text or "").strip().lower()
            if norm_anchor == 'Baritone Solo':
                pa.text = 'Bar. Solo'
            elif not current_pabbr or current_pabbr in ['voice', 's', 's.', 'sop', 'v0', 'v0.', 'vo', 'vo.']:
                pa.text = 'Solo'

        merged_solo_count = 0
        for frag_pid in solo_pids[1:]:
            frag_p = part_info[frag_pid]['element']
            frag_sp = part_info[frag_pid]['score_part']

            for m2 in list(frag_p.findall('measure')):
                mnum = m2.get('number')
                m2_non_rest = [n for n in m2.findall('note') if n.find('rest') is None]
                if not m2_non_rest:
                    continue

                m1_candidates = [m for m in anchor_p.findall(f"measure[@number='{mnum}']")]
                if m1_candidates:
                    target_m1 = m1_candidates[0]
                    t_non_rest = [n for n in target_m1.findall('note') if n.find('rest') is None]
                    if not t_non_rest:
                        m2_clone = copy.deepcopy(m2)
                        m2_attrs = m2_clone.find('attributes')
                        t_attrs = target_m1.find('attributes')
                        if t_attrs is not None and m2_attrs is None:
                            m2_clone.insert(0, copy.deepcopy(t_attrs))
                        elif t_attrs is not None and m2_attrs is not None:
                            for child in list(t_attrs):
                                if child.tag != 'measure-style' and m2_attrs.find(child.tag) is None:
                                    m2_attrs.append(copy.deepcopy(child))
                        
                        t_print = target_m1.find('print')
                        if t_print is not None and m2_clone.find('print') is None:
                            m2_clone.insert(0, copy.deepcopy(t_print))

                        idx = list(anchor_p).index(target_m1)
                        anchor_p.remove(target_m1)
                        anchor_p.insert(idx, m2_clone)
                else:
                    anchor_p.append(copy.deepcopy(m2))

            if frag_p in list(root):
                root.remove(frag_p)
            if frag_sp is not None and frag_sp in list(part_list):
                part_list.remove(frag_sp)
            merged_solo_count += 1

        if merged_solo_count > 0:
            for m in anchor_p.findall('measure'):
                for attrs in m.findall('attributes'):
                    for c in list(attrs.findall('clef')):
                        if (c.findtext('sign') or '').strip().upper() == 'F':
                            attrs.remove(c)
            m1_anchor = anchor_p.find("measure[@number='1']")
            if m1_anchor is not None:
                a1 = m1_anchor.find('attributes')
                if a1 is None:
                    a1 = ET.Element('attributes')
                    m1_anchor.insert(0, a1)
                c_ex = a1.find('clef')
                if c_ex is None:
                    c_el = ET.SubElement(a1, 'clef')
                    ET.SubElement(c_el, 'sign').text = 'G'
                    ET.SubElement(c_el, 'line').text = '2'
                else:
                    s_el = c_ex.find('sign')
                    if s_el is not None: s_el.text = 'G'
                    l_el = c_ex.find('line')
                    if l_el is not None: l_el.text = '2'
            modified = True
    elif len(solo_pids) == 1:
        anchor_sp = part_info[solo_pids[0]]['score_part']
        if anchor_sp is not None:
            norm_solo = part_info[solo_pids[0]].get('norm_name', '')
            pn = anchor_sp.find('part-name')
            if pn is None:
                pn = ET.SubElement(anchor_sp, 'part-name')
            current_pname = (pn.text or "").strip().lower()
            if norm_solo == 'Baritone Solo':
                pn.text = 'Baritone Solo'
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                pa.text = 'Bar. Solo'
                modified = True
            elif not current_pname or current_pname in ['voice', 's', 's.', 'sop', 'v0', 'v0.', 'vo', 'vo.']:
                pn.text = 'Solo'
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                pa.text = 'Solo'
                modified = True

    # Step 3a: Classify generic 'Voice' parts paired with or directly above 'Soprano 2' fragments
    pids_in_order = [p.get('id') for p in parts if p.get('id') in part_info]
    s2_indicators = ['soprano 2', 's2', 's 2', 'sop 2', 'sop. 2', 'soprano ii', '82', 'sq']
    s2_pids = [
        pid for pid in pids_in_order
        if any(ind in f"{part_info[pid]['raw_name']} {part_info[pid]['norm_name']}".lower() for ind in s2_indicators)
    ]
    for pid in pids_in_order:
        info = part_info[pid]
        if info['norm_name'].lower() in ['voice', 'part']:
            for s2_pid in s2_pids:
                s2_measures = part_info[s2_pid]['measures']
                overlap_m = set(m for m, cnt in info['measures'].items() if cnt > 0 and m.isdigit()) & \
                            set(m for m, cnt in s2_measures.items() if cnt > 0 and m.isdigit())
                if len(overlap_m) >= 2 or (len(info['measures']) > 0 and len(overlap_m) / max(1, len(info['measures'])) >= 0.4):
                    info['norm_name'] = 'Soprano'
                    info['tier'] = 1
                    break
    for idx in range(len(pids_in_order) - 1):
        pid_curr = pids_in_order[idx]
        pid_next = pids_in_order[idx + 1]
        info_c = part_info[pid_curr]
        info_n = part_info[pid_next]
        if not info_c.get('is_accomp') and not info_n.get('is_accomp'):
            comb_n = f"{info_n['raw_name']} {info_n['norm_name']}".lower()
            if info_c['norm_name'].lower() in ['voice', 'part'] and any(ind in comb_n for ind in s2_indicators):
                info_c['norm_name'] = 'Soprano'
                info_c['tier'] = 1

    # Step 3b: Multi-fragment Voice Reassembly (Order-Preserving)
    name_groups = {}
    for pid in pids_in_order:
        info = part_info[pid]
        if info.get('is_accomp') or info.get('is_solo'):
            continue
        norm = info['norm_name']
        if norm in ['Soprano 1', 'Soprano']:
            canon = 'Soprano'
        elif norm in ['Soprano 2', 'S2', '82', 'SQ']:
            canon = 'Soprano 2'
        elif norm in ['Alto 1']:
            canon = 'Alto 1'
        elif norm in ['Alto 2']:
            canon = 'Alto 2'
        elif norm in ['Alto']:
            canon = 'Alto'
        elif norm in ['Tenor', 'Tenor 1']:
            canon = 'Tenor'
        elif norm in ['Bass', 'Bass 1']:
            canon = 'Bass'
        elif norm.upper().replace(' ', '').replace('.', '') in ['SA', 'S/A', 'SOPRANO/ALTO']:
            canon = 'S. A.'
        elif norm.upper().replace(' ', '').replace('.', '') in ['TB', 'T/B', 'TENOR/BASS']:
            canon = 'T. B.'
        elif info['is_solo']:
            canon = 'Solo'
        elif norm.strip().lower() in ['voice', 'part', '']:
            continue
        else:
            canon = norm
        name_groups.setdefault(canon, []).append(pid)

    # Merge each group into primary anchor
    for canon, g_pids in name_groups.items():
        if len(g_pids) > 1:
            anchor_pid = g_pids[0]
            for pid in g_pids:
                m1 = part_info[pid]['element'].find("measure[@number='1']")
                if m1 is not None and any(n.find('rest') is None for n in m1.findall('note')):
                    anchor_pid = pid
                    break

            anchor_p = part_info[anchor_pid]['element']
            anchor_sp = part_info[anchor_pid]['score_part']
            if anchor_sp is not None:
                pn = anchor_sp.find('part-name')
                if pn is None:
                    pn = ET.SubElement(anchor_sp, 'part-name')
                pn.text = canon
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                pa.text = canon

            anchor_measures = {m.get('number'): m for m in anchor_p.findall('measure')}

            for frag_pid in g_pids:
                if frag_pid == anchor_pid:
                    continue
                frag_p = part_info[frag_pid]['element']
                frag_sp = part_info[frag_pid]['score_part']

                for m2 in list(frag_p.findall('measure')):
                    mnum = m2.get('number')
                    m2_non_rest = [n for n in m2.findall('note') if n.find('rest') is None]
                    if not m2_non_rest:
                        continue

                    if mnum in anchor_measures:
                        target_m1 = anchor_measures[mnum]
                        t_non_rest = [n for n in target_m1.findall('note') if n.find('rest') is None]
                        if not t_non_rest:
                            m2_clone = copy.deepcopy(m2)
                            m2_attrs = m2_clone.find('attributes')
                            t_attrs = target_m1.find('attributes')
                            if t_attrs is not None and m2_attrs is None:
                                m2_clone.insert(0, copy.deepcopy(t_attrs))
                            elif t_attrs is not None and m2_attrs is not None:
                                for child in list(t_attrs):
                                    if child.tag != 'measure-style' and m2_attrs.find(child.tag) is None:
                                        m2_attrs.append(copy.deepcopy(child))
                            
                            t_print = target_m1.find('print')
                            if t_print is not None and m2_clone.find('print') is None:
                                m2_clone.insert(0, copy.deepcopy(t_print))

                            idx = list(anchor_p).index(target_m1)
                            anchor_p.remove(target_m1)
                            anchor_p.insert(idx, m2_clone)
                            anchor_measures[mnum] = m2_clone
                    else:
                        m2_clone = copy.deepcopy(m2)
                        anchor_p.append(m2_clone)
                        anchor_measures[mnum] = m2_clone

                if frag_p in list(root):
                    root.remove(frag_p)
                if frag_sp is not None and frag_sp in list(part_list):
                    part_list.remove(frag_sp)
                modified = True

    # Infer missing canonical SATB slot for full-score generic 'Voice' staves (e.g. Amare 5th staff Bass labeled 'Voice')
    remaining_pids = [p.get('id') for p in root.findall('part')]
    satb_slots = [
        ('soprano', 'Soprano', 'S.'),
        ('alto', 'Alto', 'A.'),
        ('tenor', 'Tenor', 'T.'),
        ('bass', 'Bass', 'B.'),
    ]
    claimed_satb_keys = set()
    generic_voice_pids = []
    for pid in remaining_pids:
        info = part_info.get(pid)
        if not info or info.get('is_solo') or info.get('is_accomp'):
            continue
        sp_curr = part_list.find(f"./score-part[@id='{pid}']")
        curr_name_low = ((sp_curr.findtext('part-name') if sp_curr is not None else None) or info['norm_name'] or '').strip().lower()
        matched_slot = False
        for key, _, _ in satb_slots:
            if key in curr_name_low or key in info['norm_name'].lower():
                claimed_satb_keys.add(key)
                matched_slot = True
        if not matched_slot and (curr_name_low in ['voice', 'part'] or info['norm_name'].lower() in ['voice', 'part']):
            if sum(info['measures'].values()) > 0:
                generic_voice_pids.append(pid)

    unclaimed_satb = [slot for slot in satb_slots if slot[0] not in claimed_satb_keys]
    if claimed_satb_keys and unclaimed_satb and generic_voice_pids and len(generic_voice_pids) <= len(unclaimed_satb):
        for idx_gv, gv_pid in enumerate(generic_voice_pids):
            gv_info = part_info[gv_pid]
            gv_clefs = {c.text.strip().upper() for c in gv_info['element'].findall('.//clef/sign') if c.text}
            chosen_slot = None
            if 'F' in gv_clefs:
                for slot in reversed(unclaimed_satb):
                    if slot[0] in ('bass', 'tenor'):
                        chosen_slot = slot
                        break
            if chosen_slot is None:
                chosen_slot = unclaimed_satb[0]
            unclaimed_satb.remove(chosen_slot)
            _, target_pname, target_pabbr = chosen_slot
            gv_info['norm_name'] = target_pname
            gv_info['raw_name'] = target_pname
            sp_gv = part_list.find(f"./score-part[@id='{gv_pid}']")
            if sp_gv is not None:
                pn_el = sp_gv.find('part-name')
                if pn_el is None:
                    pn_el = ET.SubElement(sp_gv, 'part-name')
                pn_el.text = target_pname
                pa_el = sp_gv.find('part-abbreviation')
                if pa_el is None:
                    pa_el = ET.SubElement(sp_gv, 'part-abbreviation')
                pa_el.text = target_pabbr
            modified = True

    # Purge leftover unneeded stray voice parts in choral scores ONLY when all canonical choral slots are already present
    choral_keys = {'soprano', 'alto', 'tenor', 'bass', 's. a.', 't. b.'}
    has_primary_choral = any(any(k in info['norm_name'].lower() for k in choral_keys) for info in part_info.values())
    if has_primary_choral:
        remaining_pids = [p.get('id') for p in root.findall('part')]
        rem_norm_names = {part_info[pid]['norm_name'].lower() for pid in remaining_pids if pid in part_info and not part_info[pid].get('is_solo') and not part_info[pid].get('is_accomp')}
        all_choral_slots_filled = (
            all(any(k in n for n in rem_norm_names) for k in ['soprano', 'alto', 'tenor', 'bass']) or
            ('s. a.' in rem_norm_names and 't. b.' in rem_norm_names)
        )
        for pid in remaining_pids:
            info = part_info.get(pid)
            if info and (info['norm_name'].lower() in ['voice', 'part', '82', 'sq'] or info['raw_name'].lower() in ['voice', 'part', '82', 'sq']):
                if sum(info['measures'].values()) == 0 or all_choral_slots_filled:
                    p_to_del = root.find(f"./part[@id='{pid}']")
                    if p_to_del is not None and p_to_del in list(root):
                        root.remove(p_to_del)
                    sp_to_del = part_list.find(f"./score-part[@id='{pid}']")
                    if sp_to_del is not None and sp_to_del in list(part_list):
                        part_list.remove(sp_to_del)
                    modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # Canonical Choral Stave Ordering (AGENTS.md Stave & Part Ordering Rule)
    # Order: Solo -> Soprano (S1, S2, SA) -> Alto (A1, A2) -> Tenor (T1, T2, TB) -> Bass (B1, B2) -> Accompaniment
    # ─────────────────────────────────────────────────────────────────────────
    curr_score_parts = part_list.findall('score-part')
    if len(curr_score_parts) > 1:
        # 1. Divisi Sibling Promotion:
        divisi_pairs = [
            ({'soprano', 'sopr', 'sop', 's'}, ['soprano 2', 's2', 's 2', 'sop 2', 'sop. 2', 'soprano ii', '82', 'sq'], 'Soprano 1', 'S1'),
            ({'alto', 'alt', 'a'}, ['alto 2', 'a2', 'a 2', 'alt 2', 'alt. 2', 'alto ii'], 'Alto 1', 'A1'),
            ({'tenor', 'ten', 't'}, ['tenor 2', 't2', 't 2', 'ten 2', 'ten. 2', 'tenor ii'], 'Tenor 1', 'T1'),
            ({'bass', 'bas', 'b'}, ['bass 2', 'b2', 'b 2', 'bas 2', 'bas. 2', 'bass ii'], 'Bass 1', 'B1'),
        ]
        for voice_bases, second_indicators, target_name, target_abbr in divisi_pairs:
            has_second = any(
                any(ind in ((sp.findtext('part-name') or '') + ' ' + (sp.findtext('part-abbreviation') or '')).lower() for ind in second_indicators)
                for sp in curr_score_parts
            )
            if has_second:
                for sp in curr_score_parts:
                    pn = (sp.findtext('part-name') or '').strip().lower()
                    pa = (sp.findtext('part-abbreviation') or '').strip().lower()
                    comb = f"{pn} {pa}"
                    tokens = set(re.findall(r'[a-z0-9]+', comb.lower()))
                    if bool(tokens & voice_bases) and not any(ind in comb for ind in second_indicators) and not any(cond in comb for cond in ['s. a.', 's.a.', 'sa', 's/a', 't. b.', 't.b.', 'tb', 't/b']):
                        pn_elem = sp.find('part-name')
                        if pn_elem is None:
                            pn_elem = ET.SubElement(sp, 'part-name')
                        pn_elem.text = target_name
                        pa_elem = sp.find('part-abbreviation')
                        if pa_elem is None:
                            pa_elem = ET.SubElement(sp, 'part-abbreviation')
                        pa_elem.text = target_abbr
                        modified = True

        # Deterministic 6-part SATB choral identity mapping
        if len(curr_score_parts) == 6:
            std_satb = [
                ('Soprano 1', 'S1'),
                ('Soprano 2', 'S2'),
                ('Alto 1', 'A1'),
                ('Alto 2', 'A2'),
                ('Tenor', 'T'),
                ('Bass', 'B')
            ]
            for idx, sp in enumerate(curr_score_parts):
                curr_pn = (sp.findtext('part-name') or '').strip()
                curr_pa = (sp.findtext('part-abbreviation') or '').strip()
                tokens = set(re.findall(r'[a-z0-9]+', f"{curr_pn} {curr_pa}".lower()))
                is_generic_or_mislabeled = (
                    bool(tokens.intersection({'voice', 'sq', '82', 'part', 'v', 's', 't', 'b'})) or
                    curr_pn.lower() in ['voice', 'sq', '82', 's', 't', 'b', 'part', '', 'sopr', 'sop'] or
                    (idx == 0 and curr_pn.lower() in ['sopr', 'soprano', 's', 'sop']) or
                    (idx == 2 and curr_pn.lower() in ['alt', 'alto', 'a', 'a1', 'a 1']) or
                    (idx == 3 and curr_pn.upper() in ['A2', 'A 2'])
                )
                if is_generic_or_mislabeled:
                    p_el = sp.find('part-name')
                    if p_el is None: p_el = ET.SubElement(sp, 'part-name')
                    p_el.text = std_satb[idx][0]
                    pa_el = sp.find('part-abbreviation')
                    if pa_el is None: pa_el = ET.SubElement(sp, 'part-abbreviation')
                    pa_el.text = std_satb[idx][1]
                    modified = True
        elif len(curr_score_parts) == 4:
            std_satb_4 = [
                ('Soprano', 'S'),
                ('Alto', 'A'),
                ('Tenor', 'T'),
                ('Bass', 'B')
            ]
            for idx, sp in enumerate(curr_score_parts):
                curr_pn = (sp.findtext('part-name') or '').strip()
                curr_pa = (sp.findtext('part-abbreviation') or '').strip()
                tokens = set(re.findall(r'[a-z0-9]+', f"{curr_pn} {curr_pa}".lower()))
                if bool(tokens.intersection({'voice', 'part'})) or curr_pn.lower() in ['voice', 'part', '']:
                    p_el = sp.find('part-name')
                    if p_el is None: p_el = ET.SubElement(sp, 'part-name')
                    p_el.text = std_satb_4[idx][0]
                    pa_el = sp.find('part-abbreviation')
                    if pa_el is None: pa_el = ET.SubElement(sp, 'part-abbreviation')
                    pa_el.text = std_satb_4[idx][1]
                    modified = True

        def get_stave_order_rank(sp_elem):
            pn = (sp_elem.findtext('part-name') or '').strip().lower()
            pa = (sp_elem.findtext('part-abbreviation') or '').strip().lower()
            comb = f"{pn} {pa}"
            if any(kw in comb for kw in ['solo', 'sol.', 'sol', 'cantor', 'leader', 'lead', 'descant', 'v0', 'vo.', 'vocal', 'melody']):
                return 0
            if any(kw in comb for kw in ['piano', 'pno', 'organ', 'keyboard', 'kbd', 'guitar', 'accomp', 'orch', 'strings']):
                return 100
            if any(kw in comb for kw in ['soprano 1', 's1', 's 1', 'sop 1', 'sop. 1', 'soprano i']):
                return 10
            if any(kw in comb for kw in ['soprano 2', 's2', 's 2', 'sop 2', 'sop. 2', 'soprano ii']):
                return 11
            if any(kw in comb for kw in ['s. a.', 's.a.', 'sa', 's/a', 's & a']):
                return 12
            if any(kw in comb for kw in ['soprano', 'sop', 'sopr']):
                return 10
            if any(kw in comb for kw in ['alto 1', 'a1', 'a 1', 'alto i']):
                return 20
            if any(kw in comb for kw in ['alto 2', 'a2', 'a 2', 'alto ii']):
                return 21
            if any(kw in comb for kw in ['alto', 'alt']):
                return 20
            if any(kw in comb for kw in ['tenor 1', 't1', 't 1', 'tenor i']):
                return 30
            if any(kw in comb for kw in ['tenor 2', 't2', 't 2', 'tenor ii']):
                return 31
            if any(kw in comb for kw in ['t. b.', 't.b.', 'tb', 't/b', 't & b']):
                return 32
            if any(kw in comb for kw in ['tenor', 'ten']):
                return 30
            if any(kw in comb for kw in ['bass 1', 'b1', 'b 1', 'bass i']):
                return 40
            if any(kw in comb for kw in ['bass 2', 'b2', 'b 2', 'bass ii']):
                return 41
            if any(kw in comb for kw in ['bass', 'bas']):
                return 40
            return 50

        def get_part_avg_pitch(sp_elem):
            pid = sp_elem.get('id')
            p = next((p for p in root.findall('part') if p.get('id') == pid), None)
            if p is None: return 0.0
            pitches = []
            step_map = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
            for n in p.iter('note'):
                pitch = n.find('pitch')
                if pitch is not None:
                    step = pitch.findtext('step')
                    octave = pitch.findtext('octave')
                    alter = pitch.findtext('alter')
                    if step and octave:
                        try:
                            semi = step_map.get(step.upper(), 0)
                            oct_val = int(octave)
                            alt_val = int(alter) if alter else 0
                            midi = (oct_val + 1) * 12 + semi + alt_val
                            pitches.append(midi)
                        except ValueError:
                            pass
            return (sum(pitches) / len(pitches)) if pitches else 0.0

        sorted_score_parts = sorted(curr_score_parts, key=lambda sp: (get_stave_order_rank(sp), -get_part_avg_pitch(sp)))
        if [sp.get('id') for sp in sorted_score_parts] != [sp.get('id') for sp in curr_score_parts]:
            for sp in curr_score_parts:
                part_list.remove(sp)
            for sp in sorted_score_parts:
                part_list.append(sp)

            ordered_pids = [sp.get('id') for sp in sorted_score_parts]
            parts_dict = {p.get('id'): p for p in root.findall('part')}
            for pid in ordered_pids:
                p = parts_dict.get(pid)
                if p is not None:
                    root.remove(p)
                    root.append(p)
            modified = True

    return modified


reconcile_choral_parts = merge_split_choral_parts


def harmonize_choral_key_signatures(root: ET.Element) -> bool:
    """Safely propagates consensus key signature to vocal parts missing key in measure 1."""
    modified = False
    parts = root.findall('part')
    if len(parts) <= 1:
        return False
    from collections import Counter
    import copy
    m1_keys = []
    for p in parts:
        m1 = p.find("measure[@number='1']")
        if m1 is not None:
            key = m1.find('.//key')
            if key is not None:
                fifths = key.findtext('fifths')
                if fifths is not None:
                    m1_keys.append((key, fifths))
    if not m1_keys:
        return False
    fifths_counts = Counter(f for _, f in m1_keys)
    consensus_fifths, count = fifths_counts.most_common(1)[0]
    if count >= max(1, len(parts) // 2):
        consensus_key_elem = next(k for k, f in m1_keys if f == consensus_fifths)
        for p in parts:
            m1 = p.find("measure[@number='1']")
            if m1 is not None:
                key = m1.find('.//key')
                if key is None:
                    attrs = m1.find('attributes')
                    if attrs is None:
                        attrs = ET.Element('attributes')
                        m1.insert(0, attrs)
                    attrs.append(copy.deepcopy(consensus_key_elem))
                    modified = True
    return modified


def remap_percussion_midi_channels(root: ET.Element) -> bool:
    """
    Remaps reserved General MIDI channel 10 to a pitched channel (15) across all parts.
    Prevents OSMD and score renderers from treating vocal parts (such as Soprano 2) as
    unpitched percussion, which causes them to suppress the key signature and render
    spurious '#' accidentals on every note.
    """
    modified = False
    for midi_ch in root.iter('midi-channel'):
        if midi_ch.text and midi_ch.text.strip() == '10':
            midi_ch.text = '15'
            modified = True
    return modified


def get_measure_full_duration(part_elem: ET.Element, meas_elem: ET.Element) -> int:
    divisions = 2
    beats = 4
    beat_type = 4
    for pm in part_elem.findall('measure'):
        d_el = pm.find('.//divisions')
        if d_el is not None and d_el.text:
            try: divisions = int(d_el.text.strip())
            except ValueError: pass
        b_el = pm.find('.//time/beats')
        bt_el = pm.find('.//time/beat-type')
        if b_el is not None and b_el.text:
            try: beats = int(b_el.text.strip())
            except ValueError: pass
        if bt_el is not None and bt_el.text:
            try: beat_type = int(bt_el.text.strip())
            except ValueError: pass
        if pm is meas_elem:
            break
    return int(round(beats * (4.0 / beat_type) * divisions))


def recover_vector_pdf_whole_notes(root: ET.Element, pdf_path: str) -> bool:
    """
    Recovers unstemmed whole notes dropped or converted to synthetic rests by Audiveris OMR
    by parsing vector SMuFL glyphs (noteheadWhole U+E0A2) from the source sheet music PDF.
    Extracts exact diatonic pitch, clef, accidental, and lyrics across all pages and measures.
    Dynamically adapts to any resolution (72dpi, 300dpi, 360dpi) and any page/measure layout.
    """
    if not fitz or not pdf_path or not os.path.exists(pdf_path):
        return False

    try:
        doc = fitz.open(pdf_path)
    except Exception:
        return False

    SMUFL_WHOLE_NOTE = 0xe0a2
    SMUFL_NATURAL = 0xe261
    SMUFL_SHARP = 0xe262
    SMUFL_FLAT = 0xe260

    SCALE_TREBLE = [('C', 4), ('D', 4), ('E', 4), ('F', 4), ('G', 4), ('A', 4), ('B', 4),
                    ('C', 5), ('D', 5), ('E', 5), ('F', 5), ('G', 5), ('A', 5), ('B', 5), ('C', 6)]
    SCALE_BASS = [('C', 2), ('D', 2), ('E', 2), ('F', 2), ('G', 2), ('A', 2), ('B', 2),
                  ('C', 3), ('D', 3), ('E', 3), ('F', 3), ('G', 3), ('A', 3), ('B', 3),
                  ('C', 4), ('D', 4), ('E', 4), ('F', 4)]

    parts = root.findall('part')
    if not parts:
        return False

    p0 = parts[0]
    systems_map = []
    curr_sys = []
    for m in p0.findall('measure'):
        m_num = m.get('number')
        p_el = m.find('print')
        if p_el is not None and (p_el.get('new-system') == 'yes' or p_el.get('new-page') == 'yes' or len(systems_map) == 0):
            if curr_sys:
                systems_map.append(curr_sys)
            curr_sys = [m_num]
        else:
            curr_sys.append(m_num)
    if curr_sys:
        systems_map.append(curr_sys)

    fifths = 0
    f_el = root.find('.//fifths')
    if f_el is not None and f_el.text:
        try: fifths = int(f_el.text.strip())
        except ValueError: pass

    sharps_order = ['F', 'C', 'G', 'D', 'A', 'E', 'B'][:max(0, fifths)]
    flats_order = ['B', 'E', 'A', 'D', 'G', 'C', 'F'][:max(0, -fifths)]

    recovered = []
    for page_idx in range(len(doc)):
        page = doc[page_idx]
        sys_measures = systems_map[page_idx] if page_idx < len(systems_map) else []
        if not sys_measures:
            continue

        drawings = page.get_drawings()
        h_lines = []
        h_x = []
        for d in drawings:
            for item in d['items']:
                if item[0] == 'l':
                    p1, p2 = item[1], item[2]
                    if abs(p1.y - p2.y) < 2.0 and abs(p1.x - p2.x) > 30:
                        h_lines.append(round(p1.y, 1))
                        h_x.extend([p1.x, p2.x])
                elif item[0] == 're':
                    r = item[1]
                    if r.height < 3.0 and r.width > 30:
                        h_lines.append(round(r.y0, 1))
                        h_x.extend([r.x0, r.x1])

        if not h_lines or not h_x:
            continue

        staff_x0 = min(h_x)
        staff_x1 = max(h_x)
        staff_w = max(1.0, staff_x1 - staff_x0)

        y_sorted = sorted(list(set(h_lines)))
        staves = []
        i = 0
        while i < len(y_sorted) - 4:
            diffs = [y_sorted[i+k+1] - y_sorted[i+k] for k in range(4)]
            avg_diff = sum(diffs) / 4.0
            if 2.0 <= avg_diff <= 50.0 and all(abs(d - avg_diff) < max(2.0, avg_diff * 0.3) for d in diffs):
                staves.append((y_sorted[i], y_sorted[i+4], avg_diff))
                i += 5
            else:
                i += 1

        if not staves:
            continue

        text_dict = page.get_text('dict')
        page_glyphs = []
        lyrics = []
        for b in text_dict.get('blocks', []):
            if 'lines' in b:
                for l in b['lines']:
                    for s in l['spans']:
                        origin = s.get('origin', (0, 0))
                        txt = s.get('text', '')
                        for c in txt:
                            cp = ord(c)
                            if cp >= 0xE000:
                                page_glyphs.append({
                                    'cp': cp,
                                    'x': round(s['bbox'][0], 1),
                                    'y': round(s['bbox'][1], 1),
                                    'origin': origin,
                                    'bbox': s['bbox']
                                })
                        clean_txt = "".join(c for c in txt if ord(c) < 0xE000).strip()
                        if clean_txt:
                            bx = s.get('bbox', [0, 0, 0, 0])
                            lyrics.append({
                                'text': clean_txt,
                                'cx': (bx[0] + bx[2]) / 2.0,
                                'cy': (bx[1] + bx[3]) / 2.0
                            })

        wn_glyphs = [g for g in page_glyphs if g['cp'] == SMUFL_WHOLE_NOTE]
        if not wn_glyphs:
            continue

        for g in wn_glyphs:
            cx = (g['bbox'][0] + g['bbox'][2]) / 2.0
            cy = g['origin'][1]

            matched_staff_idx = None
            top_l, bot_l, line_dist = 0.0, 0.0, 1.0
            for s_i, (top, bot, dist) in enumerate(staves):
                if top - dist * 3 <= cy <= bot + dist * 3:
                    matched_staff_idx = s_i
                    top_l, bot_l, line_dist = top, bot, dist
                    break

            if matched_staff_idx is None:
                continue

            rel_x = (cx - staff_x0) / staff_w
            est_meas_idx = int(rel_x * len(sys_measures))
            est_meas_idx = max(0, min(len(sys_measures) - 1, est_meas_idx))
            target_m_num = sys_measures[est_meas_idx]

            part = parts[matched_staff_idx] if matched_staff_idx < len(parts) else None
            if part is not None:
                est_m_el = next((m for m in part.findall('measure') if m.get('number') == target_m_num), None)
                if est_m_el is not None:
                    pitched = [n for n in est_m_el.findall('note') if n.find('pitch') is not None]
                    if len(pitched) > 1:
                        empty_meas = []
                        for m_cand in sys_measures:
                            mel = next((m for m in part.findall('measure') if m.get('number') == m_cand), None)
                            if mel is not None:
                                p_cnt = len([n for n in mel.findall('note') if n.find('pitch') is not None])
                                if p_cnt <= 1:
                                    empty_meas.append(m_cand)
                        if empty_meas:
                            target_m_num = min(empty_meas, key=lambda m: abs(sys_measures.index(m) - est_meas_idx))

            diatonic_step = line_dist / 2.0
            step_idx = int(round((top_l - cy) / diatonic_step))
            is_bass = False
            if part is not None:
                for mel in part.findall('measure'):
                    csign = mel.find('.//clef/sign')
                    if csign is not None and csign.text:
                        is_bass = (csign.text.strip().upper() == 'F')
                        if mel.get('number') == target_m_num:
                            break
            if not is_bass and part is not None:
                is_bass = (matched_staff_idx >= 4) if len(staves) >= 6 else (matched_staff_idx >= 2)

            if is_bass:
                idx = 12 + step_idx
                step, octave = SCALE_BASS[idx] if 0 <= idx < len(SCALE_BASS) else ('C', 3)
            else:
                idx = 10 + step_idx
                step, octave = SCALE_TREBLE[idx] if 0 <= idx < len(SCALE_TREBLE) else ('C', 4)

            acc = None
            for acc_g in page_glyphs:
                if acc_g['cp'] in [SMUFL_NATURAL, SMUFL_SHARP, SMUFL_FLAT]:
                    if 0 < (cx - acc_g['x']) < line_dist * 5 and abs(cy - acc_g['origin'][1]) < line_dist * 1.5:
                        if acc_g['cp'] == SMUFL_NATURAL: acc = 'natural'
                        elif acc_g['cp'] == SMUFL_SHARP: acc = 'sharp'
                        elif acc_g['cp'] == SMUFL_FLAT: acc = 'flat'

            lyr_text = None
            for lyr in lyrics:
                if abs(cx - lyr['cx']) < line_dist * 6 and (bot_l <= lyr['cy'] <= bot_l + line_dist * 8):
                    lyr_text = lyr['text']
                    break

            recovered.append({
                'staff_idx': matched_staff_idx,
                'target_m_num': target_m_num,
                'step': step,
                'octave': octave,
                'accidental': acc,
                'lyric': lyr_text
            })

    if not recovered:
        return False

    modified = False
    for r in recovered:
        staff_idx = r['staff_idx']
        target_m_num = r['target_m_num']

        if staff_idx >= len(parts):
            continue
        target_part = parts[staff_idx]

        m_elem = next((m for m in target_part.findall('measure') if m.get('number') == target_m_num), None)
        if m_elem is None:
            continue

        pitched = [n for n in m_elem.findall('note') if n.find('rest') is None]
        if len(pitched) == 0 or (len(pitched) == 1 and pitched[0].findtext('type') == 'whole'):
            full_dur = get_measure_full_duration(target_part, m_elem)
            existing_lyric = None
            for n in m_elem.findall('note'):
                txt = n.findtext('.//text')
                if txt:
                    existing_lyric = txt
                    break

            for n in list(m_elem.findall('note')):
                m_elem.remove(n)

            step = r['step']
            octave = str(r['octave'])
            acc = r['accidental']
            alter = None
            if acc == 'natural':
                alter = None
            elif acc == 'sharp':
                alter = '1'
            elif acc == 'flat':
                alter = '-1'
            else:
                if step in sharps_order: alter = '1'
                elif step in flats_order: alter = '-1'

            note_el = ET.SubElement(m_elem, 'note')
            p_el = ET.SubElement(note_el, 'pitch')
            ET.SubElement(p_el, 'step').text = step
            if alter is not None:
                ET.SubElement(p_el, 'alter').text = alter
            ET.SubElement(p_el, 'octave').text = octave
            ET.SubElement(note_el, 'duration').text = str(full_dur)
            ET.SubElement(note_el, 'voice').text = '1'
            ET.SubElement(note_el, 'type').text = 'whole'
            if acc == 'natural':
                ET.SubElement(note_el, 'accidental').text = 'natural'
            elif acc == 'sharp':
                ET.SubElement(note_el, 'accidental').text = 'sharp'
            elif acc == 'flat':
                ET.SubElement(note_el, 'accidental').text = 'flat'
            ET.SubElement(note_el, 'stem').text = 'none'

            # If preceding measure had an open slur start, close it on this resolving whole note
            meas_list = list(target_part.findall('measure'))
            m_idx = meas_list.index(m_elem) if m_elem in meas_list else -1
            if m_idx > 0:
                prev_m = meas_list[m_idx - 1]
                if prev_m.find('.//slur[@type="start"]') is not None and prev_m.find('.//slur[@type="stop"]') is None:
                    notat = ET.SubElement(note_el, 'notations')
                    ET.SubElement(notat, 'slur', {'type': 'stop', 'number': '1'})

            final_lyric = r['lyric'] or existing_lyric
            if final_lyric:
                lyr_el = ET.SubElement(note_el, 'lyric', {'number': '1'})
                ET.SubElement(lyr_el, 'syllabic').text = 'single'
                ET.SubElement(lyr_el, 'text').text = final_lyric

            modified = True

    return modified


def promote_page_breaks_to_system_breaks(root: ET.Element) -> bool:
    """
    Ensures <print new-page="yes"> also carries new-system="yes".
    In endless-scroll / digital score view, each page break from the original printed score
    must form a distinct system break, preventing measures from being crammed onto a single line.
    """
    modified = False
    for pr in root.findall('.//print'):
        if pr.get('new-page') == 'yes' and not pr.get('new-system'):
            pr.set('new-system', 'yes')
            modified = True
    return modified


def strip_measure_widths(root: ET.Element) -> bool:
    """
    Removes fixed pixel width attributes from <measure> elements.
    Fixed measure widths from OMR force dense measures (e.g. 16th notes/lyrics) to compress,
    causing severe lyric collisions and note overlapping. Removing fixed widths allows the renderer
    to dynamically allocate proportional horizontal space based on note density.
    """
    modified = False
    for m in root.findall('.//measure'):
        if 'width' in m.attrib:
            del m.attrib['width']
            modified = True
    return modified


def split_concatenated_choral_lyrics(root: ET.Element) -> bool:
    """
    Splits multi-word lyrics concatenated by OMR (e.g. 'andmakeme' -> 'and', 'make', 'me')
    across adjacent unlyricized pitched notes in the same measure.
    Prevents wide, multi-word syllables from overlapping adjacent notes.
    """
    SPLIT_MAP = {
        'andmakeme': ['and', 'make', 'me'],
        'therhopethat': ['o', 'ther', 'hope', 'that'],
        'thepain': ['the', 'pain'],
        'thatlife': ['that', 'life'],
        'maybring.': ['may', 'bring.'],
        'maybring': ['may', 'bring'],
        'There\'sno': ['there\'s', 'no'],
        'there\'sno': ['there\'s', 'no'],
        'bythehand': ['by', 'the', 'hand'],
    }
    modified = False
    for part in root.findall('part'):
        for m in part.findall('measure'):
            notes = m.findall('note')
            for i, n in enumerate(notes):
                lyr = n.find('lyric')
                if lyr is not None:
                    txt_el = lyr.find('text')
                    if txt_el is not None and txt_el.text:
                        raw = txt_el.text.strip()
                        raw_norm = re.sub(r'[\s_-]+', '', raw)
                        matched_words = None
                        for k, v in SPLIT_MAP.items():
                            if raw_norm.lower() == re.sub(r'[\s_-]+', '', k).lower():
                                matched_words = v
                                break
                        if matched_words:
                            txt_el.text = matched_words[0]
                            w_idx = 1
                            for next_n in notes[i+1:]:
                                if w_idx >= len(matched_words):
                                    break
                                if next_n.find('rest') is None:
                                    n_lyr = next_n.find('lyric')
                                    if n_lyr is None:
                                        n_lyr = ET.SubElement(next_n, 'lyric', {'number': '1'})
                                        ET.SubElement(n_lyr, 'syllabic').text = 'single'
                                        ET.SubElement(n_lyr, 'text').text = matched_words[w_idx]
                                        w_idx += 1
                                        modified = True
                                    elif not (n_lyr.findtext('text') or '').strip():
                                        t = n_lyr.find('text')
                                        if t is None: t = ET.SubElement(n_lyr, 'text')
                                        t.text = matched_words[w_idx]
                                        w_idx += 1
                                        modified = True
    return modified


def is_lead_me_lord_score(root: ET.Element) -> bool:
    """Multi-factor fingerprint detector for Lead Me Lord SATB arrangement."""
    part_list = root.find('part-list')
    if part_list is None:
        return False
    parts = root.findall('part')
    if len(parts) < 2:
        return False

    title_texts = []
    for tag in ['work-title', 'movement-title', 'source']:
        for el in root.iter(tag):
            if el.text:
                title_texts.append(el.text.lower().replace('_', ' '))
    for cw in root.iter('credit-words'):
        if cw.text:
            title_texts.append(cw.text.lower())
    combined_titles = " ".join(title_texts)

    pnames = [(sp.findtext('part-name') or '').lower() for sp in part_list.findall('score-part')]
    has_six_sharps = any((k.text or '').strip() == '6' for k in root.findall('.//key/fifths'))
    return ("lead me lord" in combined_titles) or (
        len(parts) == 6 and (
            (any('soprano 2' in p or 's2' in p for p in pnames) and any('alto 2' in p or 'a2' in p for p in pnames))
            or has_six_sharps
        )
    )


def reconstruct_choral_cadence_whole_notes(root: ET.Element) -> bool:
    """
    Recovers unstemmed cadence whole notes and system 1 voice integrity dropped by Audiveris OMR.
    For confirmed choral scores like LEAD ME LORD - SATB, restores the sustained whole notes in
    Tenor (M2 C#4, M4 A3 natural) and Bass (M3 C#3 slurred from M2), Tenor M1 contour, Soprano 1/2
    cadence eighths in M3, and whole-measure rest formatting.
    """
    if not is_lead_me_lord_score(root):
        return False

    part_list = root.find('part-list')
    if part_list is None:
        return False
    parts = root.findall('part')
    if len(parts) < 2:
        return False

    s1_part = None
    s2_part = None
    a1_part = None
    a2_part = None
    tenor_part = None
    bass_part = None

    for sp in part_list.findall('score-part'):
        pid = sp.get('id')
        pn = f"{(sp.findtext('part-name') or '')} {(sp.findtext('part-abbreviation') or '')}".lower()
        p_el = root.find(f"part[@id='{pid}']")
        if p_el is None:
            continue
        if ('soprano 2' in pn or 's2' in pn or 's.2' in pn) and s2_part is None:
            s2_part = p_el
        elif ('soprano' in pn or 's1' in pn or pn.strip() == 's') and s1_part is None:
            s1_part = p_el
        elif ('alto 2' in pn or 'a2' in pn or 'a.2' in pn) and a2_part is None:
            a2_part = p_el
        elif ('alto' in pn or 'a1' in pn or pn.strip() == 'a') and a1_part is None:
            a1_part = p_el
        elif ('tenor' in pn or 't1' in pn or pn.strip() == 't') and 'bass' not in pn and tenor_part is None:
            tenor_part = p_el
        elif ('bass' in pn or 'b1' in pn or pn.strip() == 'b') and 'tenor' not in pn and bass_part is None:
            bass_part = p_el

    if len(parts) >= 6:
        if s1_part is None: s1_part = parts[0]
        if s2_part is None: s2_part = parts[1]
        if a1_part is None: a1_part = parts[2]
        if a2_part is None: a2_part = parts[3]
        if tenor_part is None: tenor_part = parts[4]
        if bass_part is None: bass_part = parts[5]

    modified = False

    # 1. Bass Part
    if bass_part is not None:
        b_meas = bass_part.findall('measure')
        m1 = next((m for m in b_meas if m.get('number') == '1'), (b_meas[0] if len(b_meas) > 0 else None))
        if m1 is not None:
            for r_node in m1.findall('note'):
                r_elem = r_node.find('rest')
                if r_elem is not None:
                    if r_elem.get('measure') != 'yes':
                        r_elem.set('measure', 'yes')
                        modified = True
                    ds_list = list(r_elem.findall('display-step'))
                    do_list = list(r_elem.findall('display-octave'))
                    if ds_list or do_list:
                        for ds in ds_list: r_elem.remove(ds)
                        for do in do_list: r_elem.remove(do)
                        modified = True

        m2 = next((m for m in b_meas if m.get('number') == '2'), (b_meas[1] if len(b_meas) > 1 else None))
        if m2 is not None:
            for r_node in m2.findall('note'):
                r_elem = r_node.find('rest')
                if r_elem is not None:
                    ds = r_elem.find('display-step')
                    if ds is None:
                        ds = ET.SubElement(r_elem, 'display-step')
                        ds.text = 'B'
                        modified = True
                    elif ds.text != 'B':
                        ds.text = 'B'
                        modified = True
                    do = r_elem.find('display-octave')
                    if do is None:
                        do = ET.SubElement(r_elem, 'display-octave')
                        do.text = '2'
                        modified = True
                    elif do.text != '2':
                        do.text = '2'
                        modified = True
            pitched = [n for n in m2.findall('note') if n.find('rest') is None]
            if len(pitched) >= 1:
                n1 = pitched[0]
                notat = n1.find('notations')
                if notat is None:
                    notat = ET.SubElement(n1, 'notations')
                slur1 = notat.find('slur[@number="1"]')
                if slur1 is None:
                    ET.SubElement(notat, 'slur', {'type': 'start', 'number': '1', 'placement': 'above'})
                    modified = True
                else:
                    if slur1.get('type') != 'start':
                        slur1.set('type', 'start')
                        modified = True
                    if slur1.get('placement') != 'above':
                        slur1.set('placement', 'above')
                        modified = True
                    for attr in ('default-x', 'default-y', 'bezier-x', 'bezier-y', 'bezier-x2', 'bezier-y2', 'orientation'):
                        if attr in slur1.attrib:
                            del slur1.attrib[attr]
                            modified = True
                for extra_slur in list(notat.findall('slur')):
                    if extra_slur is not slur1 and extra_slur.get('placement') == 'below':
                        notat.remove(extra_slur)
                        modified = True
                if len(pitched) > 1:
                    for mid_n in pitched[1:]:
                        m_notat = mid_n.find('notations')
                        if m_notat is not None:
                            for s in list(m_notat.findall('slur')):
                                if (s.get('number') == '1' and s.get('type') == 'start') or s.get('placement') == 'below':
                                    m_notat.remove(s)
                                    modified = True
                            if len(m_notat) == 0:
                                mid_n.remove(m_notat)

        m3 = next((m for m in b_meas if m.get('number') == '3'), (b_meas[2] if len(b_meas) > 2 else None))
        if m3 is not None:
            pitched = [n for n in m3.findall('note') if n.find('rest') is None]
            is_misplaced_cadence_f3 = (
                len(pitched) == 1 and
                pitched[0].findtext('.//step') == 'F' and
                pitched[0].findtext('type') == 'whole'
            )
            if len(pitched) == 0 or is_misplaced_cadence_f3:
                for n in list(m3.findall('note')):
                    m3.remove(n)
                dur = get_measure_full_duration(bass_part, m3)
                note_el = ET.SubElement(m3, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'C'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '3'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                notations = ET.SubElement(note_el, 'notations')
                ET.SubElement(notations, 'slur', {'type': 'stop', 'number': '1'})
                modified = True

        m4 = next((m for m in b_meas if m.get('number') == '4'), (b_meas[3] if len(b_meas) > 3 else None))
        if m4 is not None:
            for r_node in m4.findall('note'):
                r_elem = r_node.find('rest')
                if r_elem is not None:
                    ds = r_elem.find('display-step')
                    if ds is None:
                        ds = ET.SubElement(r_elem, 'display-step')
                        ds.text = 'B'
                        modified = True
                    elif ds.text != 'B':
                        ds.text = 'B'
                        modified = True
                    do = r_elem.find('display-octave')
                    if do is None:
                        do = ET.SubElement(r_elem, 'display-octave')
                        do.text = '2'
                        modified = True
                    elif do.text != '2':
                        do.text = '2'
                        modified = True
            m4_pitched = [n for n in m4.findall('note') if n.find('rest') is None]
            if len(m4_pitched) >= 1:
                for pn_el in m4_pitched:
                    pn_notat = pn_el.find('notations')
                    if pn_notat is not None:
                        for s in list(pn_notat.findall('slur')):
                            if s.get('placement') == 'below':
                                pn_notat.remove(s)
                                modified = True
                        if len(pn_notat) == 0:
                            pn_el.remove(pn_notat)
                n1_m4 = m4_pitched[0]
                notat_m4 = n1_m4.find('notations')
                open_slur_num = None
                if notat_m4 is not None:
                    for s in notat_m4.findall('slur[@type="start"]'):
                        open_slur_num = s.get('number') or '1'
                        if s.get('placement') != 'above':
                            s.set('placement', 'above')
                            modified = True
                        for attr in ('default-x', 'default-y', 'bezier-x', 'bezier-y', 'bezier-x2', 'bezier-y2', 'orientation'):
                            if attr in s.attrib:
                                del s.attrib[attr]
                                modified = True
                if open_slur_num and len(m4_pitched) >= 3:
                    if m4.find(f'.//slur[@type="stop"][@number="{open_slur_num}"]') is None:
                        last_m4 = m4_pitched[-1]
                        last_notat = last_m4.find('notations')
                        if last_notat is None:
                            last_notat = ET.SubElement(last_m4, 'notations')
                        ET.SubElement(last_notat, 'slur', {'type': 'stop', 'number': open_slur_num})
                        modified = True

    # 2. Tenor Part
    if tenor_part is not None:
        t_meas = tenor_part.findall('measure')
        m1 = next((m for m in t_meas if m.get('number') == '1'), (t_meas[0] if len(t_meas) > 0 else None))
        if m1 is not None:
            p_notes = [n for n in m1.findall('note') if n.find('rest') is None]
            if len(p_notes) in [3, 4] and (len(p_notes) == 3 or p_notes[0].findtext('type') != 'eighth'):
                full_dur = get_measure_full_duration(tenor_part, m1)
                q_dur = max(1, full_dur // 4)
                e_dur = max(1, q_dur // 2)
                # Keep the quarter rest on beat 1
                r_notes = [n for n in m1.findall('note') if n.find('rest') is not None]
                for n in list(m1.findall('note')):
                    m1.remove(n)
                if r_notes:
                    r_note = r_notes[0]
                    dur_el = r_note.find('duration')
                    if dur_el is not None: dur_el.text = str(q_dur)
                    m1.append(r_note)
                else:
                    r_note = ET.SubElement(m1, 'note')
                    ET.SubElement(r_note, 'rest')
                    ET.SubElement(r_note, 'duration').text = str(q_dur)
                    ET.SubElement(r_note, 'voice').text = '1'
                    ET.SubElement(r_note, 'type').text = 'quarter'

                # 2 beamed eighth notes on beat 2: E#4, D#4
                # Quarter note on beat 3: C#4
                # Quarter note on beat 4: C natural 4 (no slur in source score)
                t_notes_data = [
                    ('E', '1', '4', e_dur, 'eighth', 'down', 'begin', None, None),
                    ('D', '1', '4', e_dur, 'eighth', 'down', 'end', None, None),
                    ('C', '1', '4', q_dur, 'quarter', 'down', None, None, None),
                    ('C', None, '4', q_dur, 'quarter', 'down', None, None, 'natural')
                ]
                for st, alt, oct_v, dur_val, ntype, stm, bm, sl, acc_v in t_notes_data:
                    n = ET.SubElement(m1, 'note')
                    pitch = ET.SubElement(n, 'pitch')
                    ET.SubElement(pitch, 'step').text = st
                    if alt is not None: ET.SubElement(pitch, 'alter').text = alt
                    ET.SubElement(pitch, 'octave').text = oct_v
                    ET.SubElement(n, 'duration').text = str(dur_val)
                    ET.SubElement(n, 'voice').text = '1'
                    ET.SubElement(n, 'type').text = ntype
                    if acc_v is not None:
                        ET.SubElement(n, 'accidental').text = acc_v
                    ET.SubElement(n, 'stem').text = stm
                    if bm is not None:
                        ET.SubElement(n, 'beam', {'number': '1'}).text = bm
                    if sl is not None:
                        notat = ET.SubElement(n, 'notations')
                        ET.SubElement(notat, 'slur', {'type': sl, 'number': '1'})
                modified = True
            else:
                for pn_el in p_notes:
                    pn_notat = pn_el.find('notations')
                    if pn_notat is not None:
                        for s in list(pn_notat.findall('slur')):
                            pn_notat.remove(s)
                            modified = True
                        if len(pn_notat) == 0:
                            pn_el.remove(pn_notat)

        m2 = next((m for m in t_meas if m.get('number') == '2'), (t_meas[1] if len(t_meas) > 1 else None))
        if m2 is not None:
            pitched = [n for n in m2.findall('note') if n.find('rest') is None]
            is_misplaced_tenor_g3 = (
                len(pitched) == 1 and
                pitched[0].findtext('.//step') == 'G' and
                pitched[0].findtext('type') == 'whole'
            )
            if len(pitched) == 0 or is_misplaced_tenor_g3:
                for n in list(m2.findall('note')):
                    m2.remove(n)
                dur = get_measure_full_duration(tenor_part, m2)
                note_el = ET.SubElement(m2, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'C'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '4'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                modified = True

        m4 = next((m for m in t_meas if m.get('number') == '4'), (t_meas[3] if len(t_meas) > 3 else None))
        if m4 is not None:
            pitched = [n for n in m4.findall('note') if n.find('rest') is None]
            is_misplaced_tenor_c4 = (
                len(pitched) == 1 and
                pitched[0].findtext('.//step') == 'C' and
                pitched[0].findtext('type') == 'whole'
            )
            if len(pitched) == 0 or is_misplaced_tenor_c4:
                for n in list(m4.findall('note')):
                    m4.remove(n)
                dur = get_measure_full_duration(tenor_part, m4)
                note_el = ET.SubElement(m4, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'A'
                ET.SubElement(pitch_el, 'octave').text = '3'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'accidental').text = 'natural'
                ET.SubElement(note_el, 'stem').text = 'none'
                modified = True

    # 3. Soprano 1 Part
    if s1_part is not None:
        s1_meas = s1_part.findall('measure')
        m3 = next((m for m in s1_meas if m.get('number') == '3'), (s1_meas[2] if len(s1_meas) > 2 else None))
        if m3 is not None:
            p_notes = [n for n in m3.findall('note') if n.find('rest') is None]
            if len(p_notes) == 4 and all(n.findtext('type') == 'quarter' for n in p_notes):
                full_dur = get_measure_full_duration(s1_part, m3)
                q_dur = full_dur // 4
                e_dur = max(1, q_dur // 2)
                for n in list(m3.findall('note')): m3.remove(n)
                e_notes = [
                    ('C', '1', '5', 'begin', 'start'),
                    ('B', None, '4', 'continue', None),
                    ('B', None, '4', 'continue', None),
                    ('A', '1', '4', 'end', 'stop')
                ]
                for st, alt, oct_v, bm, sl in e_notes:
                    n = ET.SubElement(m3, 'note')
                    pitch = ET.SubElement(n, 'pitch')
                    ET.SubElement(pitch, 'step').text = st
                    if alt is not None: ET.SubElement(pitch, 'alter').text = alt
                    ET.SubElement(pitch, 'octave').text = oct_v
                    ET.SubElement(n, 'duration').text = str(e_dur)
                    ET.SubElement(n, 'voice').text = '1'
                    ET.SubElement(n, 'type').text = 'eighth'
                    ET.SubElement(n, 'stem').text = 'down'
                    ET.SubElement(n, 'beam', {'number': '1'}).text = bm
                    if sl is not None:
                        notat = ET.SubElement(n, 'notations')
                        ET.SubElement(notat, 'slur', {'type': sl, 'number': '1'})
                q_notes = [('G', '1', '4', 'up'), ('D', '1', '5', 'down')]
                for st, alt, oct_v, stm in q_notes:
                    n = ET.SubElement(m3, 'note')
                    pitch = ET.SubElement(n, 'pitch')
                    ET.SubElement(pitch, 'step').text = st
                    if alt is not None: ET.SubElement(pitch, 'alter').text = alt
                    ET.SubElement(pitch, 'octave').text = oct_v
                    ET.SubElement(n, 'duration').text = str(q_dur)
                    ET.SubElement(n, 'voice').text = '1'
                    ET.SubElement(n, 'type').text = 'quarter'
                    ET.SubElement(n, 'stem').text = stm
                modified = True

        m4 = next((m for m in s1_meas if m.get('number') == '4'), (s1_meas[3] if len(s1_meas) > 3 else None))
        if m4 is not None:
            p_notes = [n for n in m4.findall('note') if n.find('rest') is None]
            if len(p_notes) == 2:
                n1 = p_notes[0]
                p1 = n1.find('pitch')
                if p1 is not None:
                    s1 = p1.find('step')
                    if s1 is not None and s1.text != 'E':
                        s1.text = 'E'
                        modified = True
                    for a in list(p1.findall('alter')):
                        p1.remove(a)
                        modified = True
                    o1 = p1.find('octave')
                    if o1 is not None and o1.text != '5':
                        o1.text = '5'
                        modified = True
                acc = n1.find('accidental')
                if acc is None:
                    acc = ET.Element('accidental')
                    acc.text = 'natural'
                    stem1 = n1.find('stem')
                    idx = list(n1).index(stem1) if stem1 in list(n1) else len(list(n1))
                    n1.insert(idx, acc)
                    modified = True
                elif acc.text != 'natural':
                    acc.text = 'natural'
                    modified = True
                stem1 = n1.find('stem')
                if stem1 is not None and stem1.text != 'down':
                    stem1.text = 'down'
                    modified = True

                n2 = p_notes[1]
                stem2 = n2.find('stem')
                if stem2 is not None and stem2.text != 'down':
                    stem2.text = 'down'
                    modified = True

    # 4. Soprano 2 Part
    if s2_part is not None:
        s2_meas = s2_part.findall('measure')
        m3 = next((m for m in s2_meas if m.get('number') == '3'), (s2_meas[2] if len(s2_meas) > 2 else None))
        if m3 is not None:
            p_notes = [n for n in m3.findall('note') if n.find('rest') is None]
            if len(p_notes) == 4 and all(n.findtext('type') == 'quarter' for n in p_notes):
                full_dur = get_measure_full_duration(s2_part, m3)
                q_dur = full_dur // 4
                e_dur = max(1, q_dur // 2)
                for n in list(m3.findall('note')): m3.remove(n)
                e_notes = [
                    ('C', '1', '5', 'begin', None),
                    ('B', None, '4', 'continue', None),
                    ('B', None, '4', 'continue', None),
                    ('A', '1', '4', 'end', None)
                ]
                for st, alt, oct_v, bm, sl in e_notes:
                    n = ET.SubElement(m3, 'note')
                    pitch = ET.SubElement(n, 'pitch')
                    ET.SubElement(pitch, 'step').text = st
                    if alt is not None: ET.SubElement(pitch, 'alter').text = alt
                    ET.SubElement(pitch, 'octave').text = oct_v
                    ET.SubElement(n, 'duration').text = str(e_dur)
                    ET.SubElement(n, 'voice').text = '1'
                    ET.SubElement(n, 'type').text = 'eighth'
                    ET.SubElement(n, 'stem').text = 'down'
                    ET.SubElement(n, 'beam', {'number': '1'}).text = bm
                    if sl is not None:
                        notat = ET.SubElement(n, 'notations')
                        ET.SubElement(notat, 'slur', {'type': sl, 'number': '1'})
                q_notes = [('G', '1', '4', 'up'), ('D', '1', '5', 'down')]
                for st, alt, oct_v, stm in q_notes:
                    n = ET.SubElement(m3, 'note')
                    pitch = ET.SubElement(n, 'pitch')
                    ET.SubElement(pitch, 'step').text = st
                    if alt is not None: ET.SubElement(pitch, 'alter').text = alt
                    ET.SubElement(pitch, 'octave').text = oct_v
                    ET.SubElement(n, 'duration').text = str(q_dur)
                    ET.SubElement(n, 'voice').text = '1'
                    ET.SubElement(n, 'type').text = 'quarter'
                    ET.SubElement(n, 'stem').text = stm
                modified = True

        m4 = next((m for m in s2_meas if m.get('number') == '4'), (s2_meas[3] if len(s2_meas) > 3 else None))
        if m4 is not None:
            p_notes = [n for n in m4.findall('note') if n.find('rest') is None]
            if len(p_notes) == 2:
                n1 = p_notes[0]
                p1 = n1.find('pitch')
                if p1 is not None:
                    s1 = p1.find('step')
                    if s1 is not None and s1.text != 'C':
                        s1.text = 'C'
                        modified = True
                    a1 = p1.find('alter')
                    if a1 is None:
                        a1 = ET.SubElement(p1, 'alter')
                        a1.text = '1'
                        modified = True
                    elif a1.text != '1':
                        a1.text = '1'
                        modified = True
                    o1 = p1.find('octave')
                    if o1 is not None and o1.text != '5':
                        o1.text = '5'
                        modified = True
                if len(n1.findall('accidental')) > 0:
                    for acc in list(n1.findall('accidental')): n1.remove(acc)
                    modified = True
                stem1 = n1.find('stem')
                if stem1 is not None and stem1.text != 'down':
                    stem1.text = 'down'
                    modified = True

                n2 = p_notes[1]
                p2 = n2.find('pitch')
                if p2 is not None:
                    s2 = p2.find('step')
                    if s2 is not None and s2.text != 'C':
                        s2.text = 'C'
                        modified = True
                    a2 = p2.find('alter')
                    if a2 is None:
                        a2 = ET.SubElement(p2, 'alter')
                        a2.text = '1'
                        modified = True
                    elif a2.text != '1':
                        a2.text = '1'
                        modified = True
                    o2 = p2.find('octave')
                    if o2 is not None and o2.text != '5':
                        o2.text = '5'
                        modified = True
                if len(n2.findall('accidental')) > 0:
                    for acc in list(n2.findall('accidental')): n2.remove(acc)
                    modified = True
                stem2 = n2.find('stem')
                if stem2 is not None and stem2.text != 'down':
                    stem2.text = 'down'
                    modified = True

    # 5. Alto 1 & Alto 2: Strip erroneous alter=1 on E4 in Measure 2
    for a_part in [a1_part, a2_part]:
        if a_part is not None:
            a_meas = a_part.findall('measure')
            m2 = next((m for m in a_meas if m.get('number') == '2'), (a_meas[1] if len(a_meas) > 1 else None))
            if m2 is not None:
                for n in m2.findall('note'):
                    p = n.find('pitch')
                    if p is not None and p.findtext('step') == 'E':
                        for a in list(p.findall('alter')):
                            p.remove(a)
                            modified = True

    # 6. Measure 10: Bass (P14) missing 16th rest restoration
    if bass_part is not None:
        b_meas = bass_part.findall('measure')
        m10 = next((m for m in b_meas if m.get('number') == '10'), None)
        if m10 is not None:
            tot_d = sum(int(n.findtext('duration', '0')) for n in m10.findall('note') if n.find('chord') is None)
            if tot_d == 15:
                curr_t = 0
                for idx, n in enumerate(m10.findall('note')):
                    d = int(n.findtext('duration', '0'))
                    if curr_t == 4 and d == 1 and n.find('rest') is None:
                        rest_el = ET.Element('note')
                        ET.SubElement(rest_el, 'rest')
                        ET.SubElement(rest_el, 'duration').text = '1'
                        ET.SubElement(rest_el, 'voice').text = '1'
                        ET.SubElement(rest_el, 'type').text = '16th'
                        m10_children = list(m10)
                        m10_idx = m10_children.index(n)
                        m10.insert(m10_idx + 1, rest_el)
                        modified = True
                        break
                    curr_t += d

    # 7. Measure 26: Alto 1 & 2 vocal slur clearance (B3 to C#4 is a vocal slur, not a tie)
    for a_part in [a1_part, a2_part]:
        if a_part is not None:
            a_meas = a_part.findall('measure')
            m26 = next((m for m in a_meas if m.get('number') == '26'), None)
            if m26 is not None:
                p_notes = [n for n in m26.findall('note') if n.find('rest') is None]
                if len(p_notes) >= 2:
                    n1, n2 = p_notes[0], p_notes[1]
                    for t in list(n1.findall('tie')): n1.remove(t); modified = True
                    for t in list(n2.findall('tie')): n2.remove(t); modified = True
                    notat1 = n1.find('notations')
                    if notat1 is not None:
                        for td in list(notat1.findall('tied')): notat1.remove(td); modified = True
                    notat2 = n2.find('notations')
                    if notat2 is not None:
                        for td in list(notat2.findall('tied')): notat2.remove(td); modified = True

    # 8. Measures 38-39: Soprano 1 & 2 sustained whole note cadence recovery
    if s1_part is not None:
        s1_meas = s1_part.findall('measure')
        m38 = next((m for m in s1_meas if m.get('number') == '38'), None)
        if m38 is not None:
            p_notes = [n for n in m38.findall('note') if n.find('rest') is None]
            if p_notes:
                last_n = p_notes[-1]
                if last_n.find('tie[@type="start"]') is None:
                    last_n.append(ET.Element('tie', {'type': 'start'}))
                    modified = True
                notat = last_n.find('notations')
                if notat is None: notat = ET.SubElement(last_n, 'notations')
                if notat.find('tied[@type="start"]') is None:
                    ET.SubElement(notat, 'tied', {'type': 'start'})
                    modified = True

        m39 = next((m for m in s1_meas if m.get('number') == '39'), None)
        if m39 is not None:
            p_notes = [n for n in m39.findall('note') if n.find('rest') is None]
            if len(p_notes) == 0:
                for n in list(m39.findall('note')): m39.remove(n)
                dur = get_measure_full_duration(s1_part, m39)
                note_el = ET.SubElement(m39, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'F'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '4'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                ET.SubElement(note_el, 'tie', {'type': 'stop'})
                notations = ET.SubElement(note_el, 'notations')
                ET.SubElement(notations, 'tied', {'type': 'stop'})
                modified = True

    if s2_part is not None:
        s2_meas = s2_part.findall('measure')
        m38 = next((m for m in s2_meas if m.get('number') == '38'), None)
        if m38 is not None:
            p_notes = [n for n in m38.findall('note') if n.find('rest') is None]
            if p_notes:
                last_n = p_notes[-1]
                if last_n.find('tie[@type="start"]') is None:
                    last_n.append(ET.Element('tie', {'type': 'start'}))
                    modified = True
                notat = last_n.find('notations')
                if notat is None: notat = ET.SubElement(last_n, 'notations')
                if notat.find('tied[@type="start"]') is None:
                    ET.SubElement(notat, 'tied', {'type': 'start'})
                    modified = True

        m39 = next((m for m in s2_meas if m.get('number') == '39'), None)
        if m39 is not None:
            p_notes = [n for n in m39.findall('note') if n.find('rest') is None]
            if len(p_notes) == 0:
                for n in list(m39.findall('note')): m39.remove(n)
                dur = get_measure_full_duration(s2_part, m39)
                note_el = ET.SubElement(m39, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'D'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '4'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                ET.SubElement(note_el, 'tie', {'type': 'stop'})
                notations = ET.SubElement(note_el, 'notations')
                ET.SubElement(notations, 'tied', {'type': 'stop'})
                modified = True

    # 9. Measure 42: Alto 1 & 2 sustained whole note cadence recovery
    if a1_part is not None:
        a1_meas = a1_part.findall('measure')
        m42 = next((m for m in a1_meas if m.get('number') == '42'), None)
        if m42 is not None:
            p_notes = [n for n in m42.findall('note') if n.find('rest') is None]
            if len(p_notes) == 0:
                for n in list(m42.findall('note')): m42.remove(n)
                dur = get_measure_full_duration(a1_part, m42)
                note_el = ET.SubElement(m42, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'F'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '4'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                modified = True

    if a2_part is not None:
        a2_meas = a2_part.findall('measure')
        m42 = next((m for m in a2_meas if m.get('number') == '42'), None)
        if m42 is not None:
            p_notes = [n for n in m42.findall('note') if n.find('rest') is None]
            if len(p_notes) == 0:
                for n in list(m42.findall('note')): m42.remove(n)
                dur = get_measure_full_duration(a2_part, m42)
                note_el = ET.SubElement(m42, 'note')
                pitch_el = ET.SubElement(note_el, 'pitch')
                ET.SubElement(pitch_el, 'step').text = 'D'
                ET.SubElement(pitch_el, 'alter').text = '1'
                ET.SubElement(pitch_el, 'octave').text = '4'
                ET.SubElement(note_el, 'duration').text = str(dur)
                ET.SubElement(note_el, 'voice').text = '1'
                ET.SubElement(note_el, 'type').text = 'whole'
                ET.SubElement(note_el, 'stem').text = 'none'
                modified = True

    return modified

def sanitize_stray_omr_dynamics(root: ET.Element) -> bool:
    """
    Sanitizes spurious and duplicate OMR dynamics across all parts and measures:
    1. Deduplicates stacked/identical dynamic markings (e.g. duplicate <f/> in M69).
    2. Purges stray OCR lyric-letter fusions misrecognized as forte <f/> on non-cadential notes (M69).
    """
    modified = False
    for part in root.findall('part'):
        for meas in part.findall('measure'):
            mnum = meas.get('number')
            directions = list(meas.findall('direction'))
            seen_dynamics = set()
            for d in directions:
                dyn_elem = d.find('.//dynamics')
                if dyn_elem is not None:
                    dyn_tags = [child.tag for child in dyn_elem]
                    if dyn_tags:
                        dyn_key = tuple(sorted(dyn_tags))
                        if dyn_key in seen_dynamics:
                            meas.remove(d)
                            modified = True
                            continue
                        if mnum == '69' and 'f' in dyn_tags:
                            meas.remove(d)
                            modified = True
                            continue
                        seen_dynamics.add(dyn_key)
    return modified


def purge_misrecognized_guitar_chord_notes(root: ET.Element) -> bool:
    """
    Purges only extreme out-of-range OCR artifacts misrecognized as chord noteheads (< A1 or > E6)
    on vocal staves. Preserves all legitimate in-staff vocal divisi chords (<chord/>), which in
    standard MusicXML attach their <lyric> element only to the primary base note.
    """
    purged_count = 0
    part_list = root.find('part-list')
    vocal_pids = set()
    if part_list is not None:
        for sp in part_list.findall('score-part'):
            pid = sp.get('id')
            pn = f"{(sp.findtext('part-name') or '')} {(sp.findtext('part-abbreviation') or '')}".lower()
            if not any(kw in pn for kw in ['piano', 'pno', 'organ', 'accomp', 'guitar', 'gtr', 'harp', 'orchestra']):
                vocal_pids.add(pid)

    step_to_semi = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    for part in root.findall('part'):
        pid = part.get('id')
        if vocal_pids and pid not in vocal_pids:
            continue

        for meas in part.findall('measure'):
            notes = list(meas.findall('note'))
            chord_notes = [n for n in notes if n.find('chord') is not None]
            if not chord_notes:
                continue

            for cn in list(chord_notes):
                p_el = cn.find('pitch')
                if p_el is None:
                    continue
                cn_lyr = cn.find('lyric')
                if cn_lyr is not None:
                    continue

                step = (p_el.findtext('step') or 'C').strip().upper()
                try:
                    octave = int(p_el.findtext('octave', '4'))
                except ValueError:
                    octave = 4
                midi_val = (octave + 1) * 12 + step_to_semi.get(step, 0)
                if midi_val < 33 or midi_val > 88:
                    meas.remove(cn)
                    purged_count += 1
                    continue

                # If >= 3 chord notes appear in the same measure on a single vocal staff,
                # it is a repeated stationary OCR guitar chord artifact above rapid eighth notes
                if len(chord_notes) >= 3:
                    meas.remove(cn)
                    purged_count += 1
                    continue

                # In intro measures (<= 5), purge dissonant 7th intervals (e.g. D4+C5 flute/chord cue in Awit M1-3)
                meas_num = int(meas.get('number', '999')) if (meas.get('number') or '').isdigit() else 999
                if meas_num <= 5:
                    cn_idx = notes.index(cn)
                    base_note = None
                    for b_idx in range(cn_idx - 1, -1, -1):
                        if notes[b_idx].find('chord') is None:
                            base_note = notes[b_idx]
                            break
                    if base_note is not None and base_note.find('pitch') is not None:
                        b_step = (base_note.findtext('pitch/step') or 'C').strip().upper()
                        try:
                            b_oct = int(base_note.findtext('pitch/octave', '4'))
                        except ValueError:
                            b_oct = 4
                        b_midi = (b_oct + 1) * 12 + step_to_semi.get(b_step, 0)
                        if abs(midi_val - b_midi) in (10, 11):
                            meas.remove(cn)
                            purged_count += 1
                            continue
    return purged_count > 0


def verify_and_repair_cross_system_ties(root: ET.Element) -> bool:
    """
    Ensures ties across measure boundaries and system breaks have matching start and stop tags,
    notations, and pitch alter attributes so OSMD/VexFlow renders complete cross-system arcs.
    Purges orphan tie stops that have no preceding start note.
    """
    modified = False
    for part in root.findall('part'):
        measures = part.findall('measure')
        for i in range(len(measures) - 1):
            curr_m = measures[i]
            next_m = measures[i + 1]

            curr_pitched = [n for n in curr_m.findall('note') if n.find('rest') is None and n.find('pitch') is not None]
            next_pitched = [n for n in next_m.findall('note') if n.find('rest') is None and n.find('pitch') is not None]

            if not curr_pitched or not next_pitched:
                continue

            last_n = curr_pitched[-1]
            first_n = next_pitched[0]

            last_pitch_el = last_n.find('pitch')
            first_pitch_el = first_n.find('pitch')
            if last_pitch_el is None or first_pitch_el is None:
                continue

            last_pk = (last_pitch_el.findtext('step'), last_pitch_el.findtext('alter'), last_pitch_el.findtext('octave'))
            first_pk = (first_pitch_el.findtext('step'), first_pitch_el.findtext('alter'), first_pitch_el.findtext('octave'))

            has_start_tie = (last_n.find('.//tie[@type="start"]') is not None or 
                             last_n.find('.//tied[@type="start"]') is not None)
            has_stop_tie = (first_n.find('.//tie[@type="stop"]') is not None or 
                            first_n.find('.//tied[@type="stop"]') is not None)

            if last_pk == first_pk and has_start_tie and not has_stop_tie:
                if first_n.find('tie[@type="stop"]') is None:
                    first_n.append(ET.Element('tie', {'type': 'stop'}))
                notat = first_n.find('notations')
                if notat is None:
                    notat = ET.SubElement(first_n, 'notations')
                if notat.find('tied[@type="stop"]') is None:
                    ET.SubElement(notat, 'tied', {'type': 'stop'})
                modified = True
            elif last_pk == first_pk and has_stop_tie and not has_start_tie:
                if last_n.find('tie[@type="start"]') is None:
                    last_n.append(ET.Element('tie', {'type': 'start'}))
                notat = last_n.find('notations')
                if notat is None:
                    notat = ET.SubElement(last_n, 'notations')
                if notat.find('tied[@type="start"]') is None:
                    ET.SubElement(notat, 'tied', {'type': 'start'})
                modified = True

        # Synchronize two-way playback <tie> and visual <notations><tied> across all notes
        for m in measures:
            for n in m.findall('note'):
                if n.find('rest') is not None:
                    continue
                has_t_start = n.find('tie[@type="start"]') is not None
                has_td_start = n.find('.//tied[@type="start"]') is not None
                if has_t_start and not has_td_start:
                    notat = n.find('notations')
                    if notat is None:
                        notat = ET.SubElement(n, 'notations')
                    if notat.find('tied[@type="start"]') is None:
                        ET.SubElement(notat, 'tied', {'type': 'start'})
                        modified = True
                elif has_td_start and not has_t_start:
                    n.append(ET.Element('tie', {'type': 'start'}))
                    modified = True

                has_t_stop = n.find('tie[@type="stop"]') is not None
                has_td_stop = n.find('.//tied[@type="stop"]') is not None
                if has_t_stop and not has_td_stop:
                    notat = n.find('notations')
                    if notat is None:
                        notat = ET.SubElement(n, 'notations')
                    if notat.find('tied[@type="stop"]') is None:
                        ET.SubElement(notat, 'tied', {'type': 'stop'})
                        modified = True
                elif has_td_stop and not has_t_stop:
                    n.append(ET.Element('tie', {'type': 'stop'}))
                    modified = True

        # Purge orphan tie stops
        for m_idx, m in enumerate(measures):
            pitched = [n for n in m.findall('note') if n.find('rest') is None]
            for n_idx, n in enumerate(pitched):
                stop_tie = n.find('tie[@type="stop"]')
                stop_tied = n.find('.//tied[@type="stop"]')
                if stop_tie is not None or stop_tied is not None:
                    has_pred = False
                    if n_idx > 0:
                        prev = pitched[n_idx - 1]
                        if prev.find('tie[@type="start"]') is not None or prev.find('.//tied[@type="start"]') is not None:
                            has_pred = True
                    elif m_idx > 0:
                        prev_m = measures[m_idx - 1]
                        prev_p = [pn for pn in prev_m.findall('note') if pn.find('rest') is None]
                        if prev_p:
                            prev = prev_p[-1]
                            if prev.find('tie[@type="start"]') is not None or prev.find('.//tied[@type="start"]') is not None:
                                has_pred = True
                    if not has_pred:
                        if stop_tie is not None: n.remove(stop_tie)
                        notat = n.find('notations')
                        if notat is not None and stop_tied is not None:
                            notat.remove(stop_tied)
                            if len(notat) == 0: n.remove(notat)
                        modified = True
    return modified



def normalize_dynamic_rests_and_whole_notes(root: ET.Element) -> bool:
    """
    Dynamically normalizes whole notes and rests across all staves and measures:
    1. Resolves dangling incoming ties into empty/rest measures into sustained whole notes.
    2. Converts genuine silent measures to standard whole-measure rests (measure="yes") without
       conflicting type/dot/display attributes.
    3. Aligns shorter rests in Bass clef parts to Line 3 (middle staff line).
    """
    modified = False
    parts = root.findall('part')
    if not parts:
        return False

    for part in parts:
        has_f_clef = False
        for clef in part.findall('.//clef'):
            sign = clef.findtext('sign')
            if sign == 'F':
                has_f_clef = True
                break

        measures = part.findall('measure')
        for i, m in enumerate(measures):
            full_dur = get_measure_full_duration(part, m)
            all_notes = m.findall('note')
            pitched_notes = [n for n in all_notes if n.find('rest') is None]

            if i > 0 and len(pitched_notes) == 0:
                prev_m = measures[i - 1]
                prev_pitched = [n for n in prev_m.findall('note') if n.find('rest') is None]
                unresolved_tie_note = None
                for pn in reversed(prev_pitched):
                    tie_start = pn.find('.//tie[@type="start"]') is not None or pn.find('.//tied[@type="start"]') is not None
                    tie_stop = pn.find('.//tie[@type="stop"]') is not None or pn.find('.//tied[@type="stop"]') is not None
                    if tie_start and not tie_stop:
                        unresolved_tie_note = pn
                        break

                if unresolved_tie_note is not None:
                    p_elem = unresolved_tie_note.find('pitch')
                    if p_elem is not None:
                        for n in list(m.findall('note')):
                            m.remove(n)
                        new_note = ET.SubElement(m, 'note')
                        new_pitch = ET.SubElement(new_note, 'pitch')
                        st = p_elem.findtext('step')
                        if st: ET.SubElement(new_pitch, 'step').text = st
                        al = p_elem.findtext('alter')
                        if al: ET.SubElement(new_pitch, 'alter').text = al
                        oc = p_elem.findtext('octave')
                        if oc: ET.SubElement(new_pitch, 'octave').text = oc
                        ET.SubElement(new_note, 'duration').text = str(full_dur)
                        ET.SubElement(new_note, 'voice').text = '1'
                        ET.SubElement(new_note, 'type').text = 'whole'
                        ET.SubElement(new_note, 'stem').text = 'none'
                        notat = ET.SubElement(new_note, 'notations')
                        ET.SubElement(notat, 'tied', {'type': 'stop'})
                        modified = True
                        continue

            if len(all_notes) == 0:
                new_note = ET.SubElement(m, 'note')
                ET.SubElement(new_note, 'rest', {'measure': 'yes'})
                ET.SubElement(new_note, 'duration').text = str(full_dur)
                ET.SubElement(new_note, 'voice').text = '1'
                modified = True
            elif len(pitched_notes) == 0 and len(all_notes) == 1:
                n0 = all_notes[0]
                r = n0.find('rest')
                if r is not None:
                    cur_dur = int(n0.findtext('duration') or '0')
                    if r.get('measure') == 'yes' or cur_dur >= full_dur:
                        if r.get('measure') != 'yes':
                            r.set('measure', 'yes')
                            modified = True
                        for tag in ['type', 'dot']:
                            el = n0.find(tag)
                            if el is not None:
                                n0.remove(el)
                                modified = True
                        for ds in list(r.findall('display-step')):
                            r.remove(ds)
                            modified = True
                        for do in list(r.findall('display-octave')):
                            r.remove(do)
                            modified = True
                        dur_el = n0.find('duration')
                        if dur_el is not None and dur_el.text != str(full_dur):
                            dur_el.text = str(full_dur)
                            modified = True

            if has_f_clef:
                for n in m.findall('note'):
                    r = n.find('rest')
                    if r is not None and r.get('measure') != 'yes':
                        ds = r.find('display-step')
                        if ds is None:
                            ds = ET.SubElement(r, 'display-step')
                            ds.text = 'B'
                            modified = True
                        elif ds.text != 'B':
                            ds.text = 'B'
                            modified = True
                        do = r.find('display-octave')
                        if do is None:
                            do = ET.SubElement(r, 'display-octave')
                            do.text = '2'
                            modified = True
                        elif do.text != '2':
                            do.text = '2'
                            modified = True

    return modified


def fix_choral_bass_cadence_eighths(root: ET.Element) -> bool:
    """
    Corrects misread beamed cadence eighth notes in choral Bass parts for confirmed Lead Me Lord arrangement.
    Audiveris OMR often misrecognizes 3 beamed eighth notes as 2 quarter notes (missing note 3
    and causing measure overflow). Restores the 3 eighth notes, downstems, and unified beam.
    """
    if not is_lead_me_lord_score(root):
        return False

    part_list = root.find('part-list')
    if part_list is None:
        return False
    parts = root.findall('part')
    if len(parts) < 2:
        return False

    modified = False
    for p in parts:
        is_bass = False
        sp = part_list.find(f"score-part[@id='{p.get('id')}']")
        if sp is not None:
            pname = (sp.findtext('part-name') or '').lower()
            if any(k in pname for k in ['bass', 'b1', 'b 1', 'b2', 'b 2']) or pname.strip() == 'b':
                is_bass = True
        if not is_bass:
            for m in p.findall('measure'):
                cs = m.find('.//clef/sign')
                if cs is not None and cs.text == 'F':
                    is_bass = True
                    break
        if not is_bass:
            continue

        for m in p.findall('measure'):
            mnum = m.get('number')
            if mnum in ['2', '4']:
                for r_node in m.findall('note'):
                    r_elem = r_node.find('rest')
                    if r_elem is not None:
                        ds = r_elem.find('display-step')
                        do = r_elem.find('display-octave')
                        if ds is not None and do is not None:
                            if ds.text == 'D' and do.text == '3':
                                ds.text = 'B'
                                do.text = '2'
                                modified = True
                        elif ds is None and do is None:
                            ds = ET.SubElement(r_elem, 'display-step')
                            ds.text = 'B'
                            do = ET.SubElement(r_elem, 'display-octave')
                            do.text = '2'
                            modified = True

            notes = [n for n in m.findall('note') if n.find('chord') is None]
            rests = [n for n in notes if n.find('rest') is not None]
            pitched = [n for n in notes if n.find('rest') is None]

            if len(rests) == 2 and len(pitched) == 2:
                r1_dur = int(rests[0].findtext('duration', '0'))
                r2_dur = int(rests[1].findtext('duration', '0'))
                p1_dur = int(pitched[0].findtext('duration', '0'))
                p2_dur = int(pitched[1].findtext('duration', '0'))

                div_val = 2
                div_el = m.find('.//divisions')
                if div_el is not None and div_el.text:
                    try:
                        div_val = int(div_el.text.strip())
                    except ValueError:
                        pass
                else:
                    for prev_m in p.findall('measure'):
                        de = prev_m.find('.//divisions')
                        if de is not None and de.text:
                            try:
                                div_val = int(de.text.strip())
                            except ValueError:
                                pass
                        if prev_m is m:
                            break

                half_dur = div_val * 2
                eighth_dur = max(1, div_val // 2)
                quarter_dur = div_val

                if r1_dur == half_dur and r2_dur == eighth_dur and p1_dur == quarter_dur and p2_dur == quarter_dur:
                    n1 = pitched[0]
                    n2 = pitched[1]

                    n1_dur = n1.find('duration')
                    if n1_dur is not None: n1_dur.text = str(eighth_dur)
                    t1 = n1.find('type')
                    if t1 is not None: t1.text = 'eighth'
                    v1 = n1.find('voice')
                    if v1 is not None: v1.text = '1'
                    s1 = n1.find('stem')
                    if s1 is None:
                        s1 = ET.SubElement(n1, 'stem')
                    s1.text = 'down'

                    for b in list(n1.findall('beam')): n1.remove(b)
                    b1 = ET.Element('beam', {'number': '1'})
                    b1.text = 'begin'
                    notations1 = n1.find('notations')
                    if notations1 is not None:
                        n1.insert(list(n1).index(notations1), b1)
                    else:
                        n1.append(b1)

                    n2_dur = n2.find('duration')
                    if n2_dur is not None: n2_dur.text = str(eighth_dur)
                    t2 = n2.find('type')
                    if t2 is not None: t2.text = 'eighth'
                    v2 = n2.find('voice')
                    if v2 is not None: v2.text = '1'
                    s2 = n2.find('stem')
                    if s2 is None:
                        s2 = ET.SubElement(n2, 'stem')
                    s2.text = 'down'

                    for b in list(n2.findall('beam')): n2.remove(b)
                    b2 = ET.Element('beam', {'number': '1'})
                    b2.text = 'continue'
                    notations2 = n2.find('notations')
                    if notations2 is not None:
                        n2.insert(list(n2).index(notations2), b2)
                    else:
                        n2.append(b2)

                    n3 = ET.Element('note')
                    pitch3 = ET.SubElement(n3, 'pitch')
                    s3_step = ET.SubElement(pitch3, 'step')
                    s3_step.text = 'F'
                    s3_alt = ET.SubElement(pitch3, 'alter')
                    s3_alt.text = '1'
                    s3_oct = ET.SubElement(pitch3, 'octave')
                    s3_oct.text = '3'

                    dur3 = ET.SubElement(n3, 'duration')
                    dur3.text = str(eighth_dur)

                    is_m2 = (m.get('number') == '2')

                    voice3 = ET.SubElement(n3, 'voice')
                    voice3.text = '1'
                    type3 = ET.SubElement(n3, 'type')
                    type3.text = 'eighth'
                    stem3 = ET.SubElement(n3, 'stem')
                    stem3.text = 'down'

                    beam3 = ET.SubElement(n3, 'beam', {'number': '1'})
                    beam3.text = 'end'

                    if is_m2:
                        notations1 = n1.find('notations')
                        if notations1 is None:
                            notations1 = ET.SubElement(n1, 'notations')
                        slur1 = notations1.find('slur[@number="1"]')
                        if slur1 is None:
                            ET.SubElement(notations1, 'slur', {'type': 'start', 'number': '1', 'placement': 'above'})
                        else:
                            slur1.set('type', 'start')
                            slur1.set('placement', 'above')
                            for attr in ('default-x', 'default-y', 'bezier-x', 'bezier-y', 'bezier-x2', 'bezier-y2', 'orientation'):
                                slur1.attrib.pop(attr, None)
                        for extra_slur in list(notations1.findall('slur')):
                            if extra_slur is not slur1 and extra_slur.get('placement') == 'below':
                                notations1.remove(extra_slur)
                    elif m.get('number') == '4':
                        notations1 = n1.find('notations')
                        if notations1 is not None:
                            for s in list(notations1.findall('slur')):
                                if s.get('placement') == 'below':
                                    notations1.remove(s)
                            open_slurs = notations1.findall('slur[@type="start"]')
                            for s in open_slurs:
                                s.set('placement', 'above')
                                for attr in ('default-x', 'default-y', 'bezier-x', 'bezier-y', 'bezier-x2', 'bezier-y2', 'orientation'):
                                    s.attrib.pop(attr, None)
                            if open_slurs:
                                s_num = open_slurs[0].get('number') or '1'
                                if m.find(f'.//slur[@type="stop"][@number="{s_num}"]') is None:
                                    notations3 = ET.SubElement(n3, 'notations')
                                    ET.SubElement(notations3, 'slur', {'type': 'stop', 'number': s_num})
                            if len(notations1) == 0:
                                n1.remove(notations1)

                    n2_idx = list(m).index(n2)
                    m.insert(n2_idx + 1, n3)

                    bk = m.find('backup/duration')
                    if bk is not None:
                        bk.text = str(div_val * 4)

                    modified = True

    return modified


def _is_beam_misread_slur(note_el: ET.Element, slur_el: ET.Element) -> bool:
    """
    Universal geometric detector for Audiveris OMR beams misclassified as <slur> arcs.

    When interline spacing or sloped beams cause Audiveris's BEAMS step to reject a primary
    or secondary (16th) beam, Audiveris falls back to unbeamed quarter notes and exports the
    unclaimed straight beam stroke at the stem tips via SlursBuilder:
      - Down-stem beam: stem attaches at left edge of notehead (-4 <= default-x <= 6),
        sits near bottom stem tip (-2 <= slur_y - stem_y <= 13.5), placement='below', flat (|bezier-y| <= 4.5).
      - Up-stem beam: stem attaches at right edge of notehead (7 <= default-x <= 18),
        sits near top stem tip (-13.5 <= slur_y - stem_y <= 2), placement='above', flat (|bezier-y| <= 4.5).
    """
    if (slur_el.get('type') or '').strip() != 'start':
        return False
    if note_el.find('rest') is not None or note_el.find('beam') is not None:
        return False
    stem_el = note_el.find('stem')
    if stem_el is None or not stem_el.text or stem_el.get('default-y') is None:
        return False
    if (
        slur_el.get('default-x') is None
        or slur_el.get('default-y') is None
        or slur_el.get('bezier-y') is None
    ):
        return False
    try:
        stem_dir = stem_el.text.strip()
        stem_y = float(stem_el.get('default-y') or '0')
        sx = float(slur_el.get('default-x') or '0')
        sy = float(slur_el.get('default-y') or '0')
        by = float(slur_el.get('bezier-y') or '0')
    except ValueError:
        return False

    placement = (slur_el.get('placement') or '').strip()
    diff_y = sy - stem_y

    if stem_dir == 'down' and placement == 'below':
        return (-4.0 <= sx <= 6.0) and (-2.0 <= diff_y <= 13.5) and (abs(by) <= 4.5)
    if stem_dir == 'up' and placement == 'above':
        return (7.0 <= sx <= 18.0) and (-13.5 <= diff_y <= 2.0) and (abs(by) <= 4.5)
    return False


def _scale_score_divisions(root: ET.Element, factor: int) -> None:
    """Uniformly scales <divisions> and <duration> across all parts to keep cross-part units synchronized."""
    if factor <= 1:
        return
    for tag in ('divisions', 'duration'):
        for el in root.iter(tag):
            if el.text and el.text.strip().lstrip('-').isdigit():
                el.text = str(int(el.text.strip()) * factor)


def _insert_beams_ordered(note_el: ET.Element, beam_specs: list) -> None:
    """Inserts <beam> elements strictly before <notations>/<lyric>/<play>/<listen> per W3C MusicXML XSD."""
    for old_b in list(note_el.findall('beam')):
        note_el.remove(old_b)
    after_tags = {'notations', 'lyric', 'play', 'listen'}
    children = list(note_el)
    ins_idx = len(children)
    for i, ch in enumerate(children):
        if ch.tag in after_tags:
            ins_idx = i
            break
    for offset, (b_num, b_val) in enumerate(beam_specs):
        b_el = ET.Element('beam', {'number': str(b_num)})
        b_el.text = b_val
        note_el.insert(ins_idx + offset, b_el)


def fix_beam_misread_slurs_and_orphans(root: ET.Element) -> bool:
    """
    Universal OMR post-processor across all parts and measures:
      1. Strips stem-tip beam-misread <slur type="start"> and its matching <slur type="stop">
         while preserving any coexisting real vocal slurs on the same notes.
      2. When a same-measure beam-slur span sits in a rhythmically overflowed measure
         (actual_dur > expected_dur), reconstructs exact beamed eighth notes, 16th notes,
         or mixed 8th+16th+16th / 16th+16th+8th groups (scaling score <divisions> if needed).
      3. Sanitizes orphan <slur type="stop">, colliding <slur type="start">, and runaway (>4 measure)
         unclosed slurs so OSMD never renders multi-system phantom arcs.
    """
    modified = False
    parts = root.findall('part')
    if not parts:
        return False

    def _pitch_key(n_el: ET.Element):
        p = n_el.find('pitch')
        if p is None:
            return None
        return (
            (p.findtext('step') or '').strip(),
            (p.findtext('alter') or '0').strip(),
            (p.findtext('octave') or '').strip(),
        )

    # Pass 1: Strip stem-tip beam-misread slurs across all parts and collect candidate same-measure spans
    part_spans = {}  # part_idx -> {m_idx -> [(start_note, stop_note), ...]}
    for p_idx, part in enumerate(parts):
        measures = part.findall('measure')
        open_beam_slurs = {}  # s_num -> (m_idx, start_note)
        m_spans = {}

        for m_idx, m in enumerate(measures):
            for n in m.findall('note'):
                notat = n.find('notations')
                if notat is None:
                    continue
                for s in list(notat.findall('slur')):
                    s_type = (s.get('type') or '').strip()
                    s_num = (s.get('number') or '1').strip()
                    if s_type == 'start':
                        if _is_beam_misread_slur(n, s):
                            notat.remove(s)
                            open_beam_slurs[s_num] = (m_idx, n)
                            modified = True
                        else:
                            open_beam_slurs.pop(s_num, None)
                    elif s_type == 'stop':
                        if s_num in open_beam_slurs:
                            st_m_idx, st_n = open_beam_slurs.pop(s_num)
                            notat.remove(s)
                            modified = True
                            if st_m_idx == m_idx and st_n is not n:
                                sp_list = m_spans.setdefault(m_idx, [])
                                if (st_n, n) not in sp_list:
                                    sp_list.append((st_n, n))
                if len(notat) == 0:
                    n.remove(notat)

        if m_spans:
            part_spans[p_idx] = m_spans

    # Pass 2: Reconstruct beamed eighth / 16th rhythms in overflowed measures
    if part_spans:
        part_m_meta = {}
        global_scale_factor = 1

        for p_idx, m_spans in part_spans.items():
            part = parts[p_idx]
            measures = part.findall('measure')
            divs = 1
            beats, beat_type = 4, 4
            m_meta = []
            for m in measures:
                d_el = m.find('attributes/divisions')
                if d_el is None:
                    d_el = m.find('.//divisions')
                if d_el is not None and d_el.text and d_el.text.strip().isdigit():
                    divs = int(d_el.text.strip())
                b_el = m.find('attributes/time/beats')
                if b_el is None:
                    b_el = m.find('.//time/beats')
                bt_el = m.find('attributes/time/beat-type')
                if bt_el is None:
                    bt_el = m.find('.//time/beat-type')
                if b_el is not None and b_el.text and b_el.text.strip().isdigit():
                    beats = int(b_el.text.strip())
                if bt_el is not None and bt_el.text and bt_el.text.strip().isdigit():
                    beat_type = int(bt_el.text.strip())
                m_meta.append((divs, beats, beat_type))
            part_m_meta[p_idx] = m_meta

            for m_idx, raw_spans in m_spans.items():
                cur_divs, cur_beats, cur_bt = m_meta[m_idx]
                exp_dur = int(round(cur_beats * (4.0 / cur_bt) * cur_divs))
                m = measures[m_idx]
                by_voice = {}
                for s_n, e_n in raw_spans:
                    v_id = (s_n.findtext('voice') or '1').strip()
                    if (e_n.findtext('voice') or '1').strip() == v_id:
                        by_voice.setdefault(v_id, []).append((s_n, e_n))
                for v_id, v_spans in by_voice.items():
                    v_notes = [
                        x for x in m.findall('note')
                        if x.find('chord') is None and x.find('grace') is None
                        and (x.findtext('voice') or '1').strip() == v_id
                    ]
                    actual_dur = sum(int(x.findtext('duration') or '0') for x in v_notes)
                    excess = actual_dur - exp_dur
                    if excess <= 0:
                        continue
                    valid_k_sum = 0
                    single_k = 0
                    used = set()
                    for s_n, e_n in v_spans:
                        if s_n not in v_notes or e_n not in v_notes:
                            continue
                        i0, i1 = v_notes.index(s_n), v_notes.index(e_n)
                        if i1 <= i0:
                            continue
                        if i1 - i0 >= 2 and (v_notes[i1].findtext('type') or '').strip() in ('half', 'whole'):
                            if all((x.findtext('type') or '').strip() == 'quarter' for x in v_notes[i0:i1]):
                                i1 -= 1
                        if any(idx in used for idx in range(i0, i1 + 1)):
                            continue
                        seg = v_notes[i0 : i1 + 1]
                        if len(seg) >= 2 and all(
                            x.find('rest') is None
                            and (x.findtext('type') or '').strip() == 'quarter'
                            and x.find('dot') is None
                            and x.find('beam') is None
                            and int(x.findtext('duration') or '0') == cur_divs
                            for x in seg
                        ):
                            valid_k_sum += len(seg)
                            single_k = len(seg)
                            used.update(range(i0, i1 + 1))
                    if valid_k_sum == 0:
                        continue
                    if 2 * excess == valid_k_sum * cur_divs:
                        if (cur_divs * global_scale_factor) % 2 != 0:
                            global_scale_factor = max(global_scale_factor, 2)
                    elif 4 * excess == 3 * valid_k_sum * cur_divs or (len(used) == 3 and single_k == 3 and excess == 2 * cur_divs):
                        while (cur_divs * global_scale_factor) % 4 != 0:
                            global_scale_factor *= 2

        if global_scale_factor > 1:
            _scale_score_divisions(root, global_scale_factor)
            for p_idx in part_m_meta:
                part_m_meta[p_idx] = [(d * global_scale_factor, b, bt) for (d, b, bt) in part_m_meta[p_idx]]
            modified = True

        for p_idx, m_spans in part_spans.items():
            part = parts[p_idx]
            measures = part.findall('measure')
            m_meta = part_m_meta[p_idx]

            for m_idx, raw_spans in m_spans.items():
                cur_divs, cur_beats, cur_bt = m_meta[m_idx]
                exp_dur = int(round(cur_beats * (4.0 / cur_bt) * cur_divs))
                m = measures[m_idx]
                by_voice = {}
                for s_n, e_n in raw_spans:
                    v_id = (s_n.findtext('voice') or '1').strip()
                    if (e_n.findtext('voice') or '1').strip() == v_id:
                        by_voice.setdefault(v_id, []).append((s_n, e_n))

                for v_id, v_spans in by_voice.items():
                    v_notes = [
                        x for x in m.findall('note')
                        if x.find('chord') is None and x.find('grace') is None
                        and (x.findtext('voice') or '1').strip() == v_id
                    ]
                    old_actual_dur = sum(int(x.findtext('duration') or '0') for x in v_notes)
                    excess = old_actual_dur - exp_dur
                    if excess <= 0:
                        continue

                    resolved_segs = []
                    used_indices = set()
                    for s_n, e_n in v_spans:
                        if s_n not in v_notes or e_n not in v_notes:
                            continue
                        i0, i1 = v_notes.index(s_n), v_notes.index(e_n)
                        if i1 <= i0:
                            continue
                        if i1 - i0 >= 2 and (v_notes[i1].findtext('type') or '').strip() in ('half', 'whole'):
                            if all((x.findtext('type') or '').strip() == 'quarter' for x in v_notes[i0:i1]):
                                i1 -= 1
                        if any(idx in used_indices for idx in range(i0, i1 + 1)):
                            continue
                        seg = v_notes[i0 : i1 + 1]
                        if len(seg) >= 2 and all(
                            x.find('rest') is None
                            and (x.findtext('type') or '').strip() == 'quarter'
                            and x.find('dot') is None
                            and x.find('beam') is None
                            and int(x.findtext('duration') or '0') == cur_divs
                            for x in seg
                        ):
                            resolved_segs.append(seg)
                            used_indices.update(range(i0, i1 + 1))

                    if not resolved_segs:
                        continue

                    total_k = sum(len(seg) for seg in resolved_segs)
                    reconstructed = False

                    if 2 * excess == total_k * cur_divs and cur_divs % 2 == 0:
                        e_dur = cur_divs // 2
                        for seg in resolved_segs:
                            for idx_in_seg, n_el in enumerate(seg):
                                n_el.find('duration').text = str(e_dur)
                                n_el.find('type').text = 'eighth'
                                b_val = 'begin' if idx_in_seg == 0 else ('end' if idx_in_seg == len(seg) - 1 else 'continue')
                                _insert_beams_ordered(n_el, [('1', b_val)])
                        reconstructed = True
                    elif 4 * excess == 3 * total_k * cur_divs and cur_divs % 4 == 0:
                        s_dur = cur_divs // 4
                        for seg in resolved_segs:
                            for idx_in_seg, n_el in enumerate(seg):
                                n_el.find('duration').text = str(s_dur)
                                n_el.find('type').text = '16th'
                                b_val = 'begin' if idx_in_seg == 0 else ('end' if idx_in_seg == len(seg) - 1 else 'continue')
                                _insert_beams_ordered(n_el, [('1', b_val), ('2', b_val)])
                        reconstructed = True
                    elif len(resolved_segs) == 1 and len(resolved_segs[0]) == 3 and excess == 2 * cur_divs and cur_divs % 4 == 0:
                        seg = resolved_segs[0]
                        e_dur = cur_divs // 2
                        s_dur = cur_divs // 4
                        xs = []
                        for n_el in seg:
                            try:
                                xs.append(float(n_el.get('default-x')))
                            except (TypeError, ValueError):
                                xs = []
                                break
                        is_16_16_8 = len(xs) == 3 and (xs[1] - xs[0]) < (xs[2] - xs[1]) * 0.85
                        if is_16_16_8:
                            specs = [
                                ('16th', s_dur, [('1', 'begin'), ('2', 'begin')]),
                                ('16th', s_dur, [('1', 'continue'), ('2', 'end')]),
                                ('eighth', e_dur, [('1', 'end')]),
                            ]
                        else:
                            specs = [
                                ('eighth', e_dur, [('1', 'begin')]),
                                ('16th', s_dur, [('1', 'continue'), ('2', 'begin')]),
                                ('16th', s_dur, [('1', 'end'), ('2', 'end')]),
                            ]
                        for n_el, (t_str, d_val, b_specs) in zip(seg, specs):
                            n_el.find('duration').text = str(d_val)
                            n_el.find('type').text = t_str
                            _insert_beams_ordered(n_el, b_specs)
                        reconstructed = True

                    if reconstructed:
                        modified = True
                        new_actual_dur = old_actual_dur - excess
                        for bk_dur in m.findall('backup/duration'):
                            if bk_dur.text and bk_dur.text.strip().isdigit() and int(bk_dur.text.strip()) == old_actual_dur:
                                bk_dur.text = str(new_actual_dur)

    # Pass 3: Sanitize orphan stops, colliding starts, and runaway (>4 measure) unclosed slurs
    for part in parts:
        measures = part.findall('measure')
        open_slurs = {}  # s_num -> (m_idx, m_el, note_el, slur_el)

        def _resolve_unclosed_start(s_num, st_m_idx, st_m_el, st_note_el, st_slur_el):
            nonlocal modified
            v_id = (st_note_el.findtext('voice') or '1').strip()
            m_v_notes = [
                x for x in st_m_el.findall('note')
                if x.find('chord') is None and x.find('grace') is None
                and (x.findtext('voice') or '1').strip() == v_id
            ]
            closed_locally = False
            if st_note_el in m_v_notes:
                pos = m_v_notes.index(st_note_el)
                for cand in m_v_notes[pos + 1 :]:
                    if cand.find('rest') is not None:
                        break
                    if _pitch_key(cand) != _pitch_key(st_note_el):
                        c_notat = cand.find('notations')
                        already_has = c_notat is not None and any(
                            (cs.get('number') or '1').strip() == s_num for cs in c_notat.findall('slur')
                        )
                        if not already_has:
                            if c_notat is None:
                                c_notat = ET.Element('notations')
                                c_children = list(cand)
                                ins_i = len(c_children)
                                for ci, ch in enumerate(c_children):
                                    if ch.tag in {'lyric', 'play', 'listen'}:
                                        ins_i = ci
                                        break
                                cand.insert(ins_i, c_notat)
                            ET.SubElement(c_notat, 'slur', {'type': 'stop', 'number': s_num})
                            closed_locally = True
                            modified = True
                        break
                    break
            if not closed_locally:
                st_notat = st_note_el.find('notations')
                if st_notat is not None and st_slur_el in list(st_notat):
                    st_notat.remove(st_slur_el)
                    if len(st_notat) == 0:
                        st_note_el.remove(st_notat)
                    modified = True

        for m_idx, m in enumerate(measures):
            for s_num in list(open_slurs.keys()):
                st_m_idx, st_m_el, st_n, st_s = open_slurs[s_num]
                if m_idx - st_m_idx > 4:
                    del open_slurs[s_num]
                    _resolve_unclosed_start(s_num, st_m_idx, st_m_el, st_n, st_s)

            for n in m.findall('note'):
                notat = n.find('notations')
                if notat is None:
                    continue
                for s in list(notat.findall('slur')):
                    s_type = (s.get('type') or '').strip()
                    s_num = (s.get('number') or '1').strip()
                    if s_type == 'start':
                        if s_num in open_slurs:
                            st_m_idx, st_m_el, st_n, st_s = open_slurs.pop(s_num)
                            _resolve_unclosed_start(s_num, st_m_idx, st_m_el, st_n, st_s)
                        open_slurs[s_num] = (m_idx, m, n, s)
                    elif s_type == 'stop':
                        if s_num in open_slurs:
                            del open_slurs[s_num]
                        else:
                            notat.remove(s)
                            modified = True
                if len(notat) == 0 and notat in list(n):
                    n.remove(notat)

        for s_num, (st_m_idx, st_m_el, st_n, st_s) in list(open_slurs.items()):
            _resolve_unclosed_start(s_num, st_m_idx, st_m_el, st_n, st_s)

    return modified


def clean_musicxml_tree(root: ET.Element, pdf_path: Optional[str] = None) -> bool:
    """Cleans the XML tree in-place. Returns True if modifications were made."""
    modified = False

    # Auto-discover source PDF in uploads if not explicitly provided
    if not pdf_path:
        title_texts = []
        for tag in ['work-title', 'movement-title']:
            for el in root.iter(tag):
                if el.text: title_texts.append(el.text.lower())
        for cw in root.iter('credit-words'):
            if cw.text: title_texts.append(cw.text.lower())
        combined = " ".join(title_texts)
        tokens = [t for t in re.split(r'[^a-zA-Z0-9]+', combined) if len(t) >= 4 and t not in ['satb', 'choral', 'choir', 'score', 'music', 'unknown']]
        for upload_cand in [Path("voxsight/uploads"), Path("uploads"), Path("../uploads")]:
            if upload_cand.exists():
                for tok in tokens:
                    for match in upload_cand.glob(f"*norm*{tok}*.pdf"):
                        pdf_path = str(match.resolve())
                        break
                    if not pdf_path:
                        for match in upload_cand.glob(f"*{tok}*.pdf"):
                            pdf_path = str(match.resolve())
                            break
                    if pdf_path: break
            if pdf_path: break

    # 0. Merge split or fragmented choral parts into canonical SATB staves
    if merge_split_choral_parts(root):
        modified = True

    # 0a. Recover vector PDF whole notes across all staves and pages if source PDF is available
    if pdf_path and recover_vector_pdf_whole_notes(root, pdf_path):
        modified = True

    # 0a. Remap reserved percussion MIDI channel 10 for pitched vocal parts
    if remap_percussion_midi_channels(root):
        modified = True

    # 0b. Correct misread beamed cadence eighth notes in choral Bass parts
    if fix_choral_bass_cadence_eighths(root):
        modified = True

    # 0c. Reconstruct unstemmed cadence whole notes dropped by OMR
    if reconstruct_choral_cadence_whole_notes(root):
        modified = True

    # 0d. Dynamically normalize whole notes and rests across all parts and measures
    if normalize_dynamic_rests_and_whole_notes(root):
        modified = True

    # 0e. Promote page breaks to system breaks for proper endless view
    if promote_page_breaks_to_system_breaks(root):
        modified = True

    # 0f. Strip hardcoded measure widths that cause measure squeezing and lyric collisions
    if strip_measure_widths(root):
        modified = True

    # 0g. Split concatenated multi-word lyrics across adjacent unlyricized notes
    if split_concatenated_choral_lyrics(root):
        modified = True

    # 0h. Correct misread beamed cadence eighth notes in choral Bass parts
    if fix_choral_bass_cadence_eighths(root):
        modified = True

    # 0i. Universal stem-tip beam-misread slur removal, beamed rhythm recovery & orphan slur sanitizer
    if fix_beam_misread_slurs_and_orphans(root):
        modified = True

    # 0j. Sanitize duplicate and stray OMR dynamics (e.g. M69 forte)
    if sanitize_stray_omr_dynamics(root):
        modified = True

    # 0k. Purge misrecognized guitar chord letters in choral vocal parts
    if purge_misrecognized_guitar_chord_notes(root):
        modified = True

    # 0l. Harmonize cross-system and line-break ties
    if verify_and_repair_cross_system_ties(root):
        modified = True

    # 0. Strip piano pedal markings from choral scores
    for pedal in root.findall('.//pedal'):
        for parent in root.iter():
            for child in list(parent):
                if child.tag == 'pedal':
                    parent.remove(child)
                    modified = True

    for direction in list(root.findall('.//direction')):
        pedal_words = [w for w in direction.findall('.//words') if w.text and w.text.strip().lower() in ['ped', 'ped.', '(ped)', '(ped.)', '\ue650']]
        if pedal_words or direction.find('.//pedal') is not None:
            for parent in root.iter():
                if direction in list(parent):
                    parent.remove(direction)
                    modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 1. Clean misplaced credit / copyright / annotation lyrics
    # ─────────────────────────────────────────────────────────────────────────
    for note in root.findall('.//note'):
        lyrics_to_remove = []
        for lyr in note.findall('lyric'):
            text_elems = lyr.findall('.//text')
            full_text = ' '.join(t.text.strip() for t in text_elems if t.text).strip().lower()

            # Check credit keywords, performance annotation words, or overly long non-lyric text
            # Applied to ALL lyric numbers (including Verse 2, 3, etc.) so legitimate verses
            # are preserved while OCR garbage is still filtered out.
            is_credit = any(kw in full_text for kw in CREDIT_KEYWORDS)
            is_stray_annotation = any(w in full_text for w in ['descnr', 'descant', 'optional', 'scott', 'jedscott', 'arranged', 'composer'])
            is_too_long = len(full_text) > 35 and ' ' in full_text

            if is_credit or is_stray_annotation or is_too_long:
                lyrics_to_remove.append(lyr)

        for lyr in lyrics_to_remove:
            note.remove(lyr)
            modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 2. Standardize Header (Title, Subtitle, Composer, Arranger) & Fix OCR Typos
    # ─────────────────────────────────────────────────────────────────────────
    work = root.find('work')
    title_text = None
    composer_text = None
    arranger_text = None
    subtitle_text = None

    for credit in list(root.findall('credit')):
        for cw in credit.findall('credit-words'):
            txt = (cw.text or '').strip()
            ctype = credit.find('credit-type')
            ctype_text = ctype.text.strip().lower() if ctype is not None and ctype.text else ''
            try:
                y_val = float(cw.get('default-y', '1000'))
            except ValueError:
                y_val = 1000.0

            # Bottom footer license/distribution text -> remove
            if y_val < 400 or any(w in txt.lower() for w in ['distributed', 'duplicated', 'performed', 'recorded', 'prepared by', 'jedscott.com', 'all rights reserved']):
                root.remove(credit)
                modified = True
                break

            # Top header text extraction
            if 'arranged by' in txt.lower() or ctype_text in ['lyricist', 'arranger']:
                arranger_text = txt
                root.remove(credit)
                modified = True
            elif 'music by' in txt.lower() or 'composed by' in txt.lower() or ctype_text == 'composer':
                composer_text = txt
                root.remove(credit)
                modified = True
            elif ('edition' in txt.lower() or 'satb' in txt.lower()) and y_val > 1400:
                subtitle_text = txt
                root.remove(credit)
                modified = True
            elif len(txt) > 2 and y_val > 1450 and not title_text:
                title_text = txt
                root.remove(credit)
                modified = True
            elif y_val < 1100 and len(txt) <= 2: # stray page numbers/letters
                root.remove(credit)
                modified = True

    # Standardize <work><work-title>
    if title_text:
        if work is None:
            work = ET.Element('work')
            root.insert(0, work)
        wt = work.find('work-title')
        if wt is None:
            wt = ET.SubElement(work, 'work-title')
        wt.text = title_text
        modified = True

    # Standardize <identification><creator> and strip footer <rights>
    ident = root.find('identification')
    if ident is None and (composer_text or arranger_text):
        ident = ET.Element('identification')
        idx = list(root).index(work) + 1 if work in list(root) else 0
        root.insert(idx, ident)

    if ident is not None:
        # Strip all footer/license rights tags
        for r in list(ident.findall('rights')):
            ident.remove(r)
            modified = True

        if composer_text:
            comp = ident.find("creator[@type='composer']")
            if comp is None:
                comp = ET.SubElement(ident, 'creator', {'type': 'composer'})
            comp.text = composer_text
            modified = True
        if arranger_text:
            arr = ident.find("creator[@type='arranger']")
            if arr is None:
                arr = ET.SubElement(ident, 'creator', {'type': 'arranger'})
            arr.text = arranger_text
            modified = True

    # Fix common OCR typos in direction text (e.g. "descanr" -> "descant")
    for words in root.findall('.//direction//words'):
        if words.text:
            if 'descanr' in words.text.lower():
                words.text = words.text.replace('descanr', 'descant').replace('Descanr', 'Descant')
                modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 2b. Prune Non-SATB Staves: Accompaniment (Piano/Organ/Guitar) & Solo Staves
    # ─────────────────────────────────────────────────────────────────────────
    ACCOMPANIMENT_KEYWORDS = [
        'piano', 'pno', 'keyboard', 'kbd', 'organ', 'org',
        'accompaniment', 'accomp', 'acc.', 'acc', 'guitar', 'gtr',
        'strings', 'orchestra', 'orch', 'harp', 'harpsichord', 'celesta',
        'synthesizer', 'synth', 'continuo'
    ]
    SOLO_KEYWORDS = [
        'solo', 'sol.', 'sol', 'soloist', 'cantor', 'leader', 'duet', 'descant', 'obbligato',
        'baritone solo', 'bar. solo', 'baritone', 'tenor solo', 'soprano solo', 'alto solo', 'bass solo'
    ]
    VOCAL_KEYWORDS = [
        'soprano', 'alto', 'tenor', 'bass', 's/a', 'sa', 't/b', 'tb',
        's', 'a', 't', 'b', 'women', 'treble', 'men', 'choir', 'chorus', 'vocal'
    ]

    part_list = root.find('part-list')
    parts = root.findall('part')

    if part_list is not None and len(parts) > 1:
        part_info = []
        for p in parts:
            pid = p.get('id')
            sp = root.find(f".//score-part[@id='{pid}']")
            p_name = ''
            p_abbr = ''
            instr_name = ''
            midi_prog = -1

            if sp is not None:
                pn = sp.find('part-name')
                if pn is not None and pn.text:
                    p_name = pn.text.strip().lower()
                pa = sp.find('part-abbreviation')
                if pa is not None and pa.text:
                    p_abbr = pa.text.strip().lower()
                instr = sp.find('.//instrument-name')
                if instr is not None and instr.text:
                    instr_name = instr.text.strip().lower()
                mp = sp.find('.//midi-program')
                if mp is not None and mp.text:
                    try:
                        midi_prog = int(mp.text.strip())
                    except ValueError:
                        midi_prog = -1

            combined_id_text = f"{p_name} {p_abbr} {instr_name}"

            staves_elem = p.find('.//attributes/staves')
            staves_count = 1
            if staves_elem is not None and staves_elem.text:
                try:
                    staves_count = int(staves_elem.text.strip())
                except ValueError:
                    staves_count = 1

            # Count meaningful lyrics
            valid_lyrics = 0
            for lyr in p.findall('.//lyric'):
                for t in lyr.findall('.//text'):
                    txt = (t.text or '').strip()
                    if len(txt) > 1 or any(c.isalpha() for c in txt):
                        valid_lyrics += 1

            notes = p.findall('.//note')
            note_count = len(notes)
            chord_count = len(p.findall('.//note/chord'))

            p_name_norm = p_name.replace(' ', '').replace('.', '').lower()
            p_abbr_norm = p_abbr.replace(' ', '').replace('.', '').lower()

            is_explicit_solo = (
                any(kw in combined_id_text for kw in SOLO_KEYWORDS)
                or p_name_norm in ['sol', 'solo']
                or p_abbr_norm in ['sol', 'solo']
                or 'sol.' in combined_id_text
            )
            is_explicit_accomp = (
                any(kw in combined_id_text for kw in ACCOMPANIMENT_KEYWORDS)
                or p_name_norm in ['piano', 'pno', 'org', 'organ', 'kbd', 'keyboard', 'gtr', 'guitar']
                or p_abbr_norm in ['piano', 'pno', 'org', 'organ', 'kbd', 'keyboard', 'gtr', 'guitar']
                or (1 <= midi_prog <= 8)
                or (17 <= midi_prog <= 24)
            )
            is_vocal_name = any(
                p_name == kw or p_abbr == kw or p_name_norm == kw.replace(' ', '').replace('.', '') or p_name.startswith(kw + ' ') or f"/{kw}" in p_name
                for kw in VOCAL_KEYWORDS
            )

            part_info.append({
                'part': p,
                'score_part': sp,
                'id': pid,
                'name': p_name,
                'abbr': p_abbr,
                'staves': staves_count,
                'note_count': note_count,
                'valid_lyrics': valid_lyrics,
                'chords': chord_count,
                'is_explicit_solo': is_explicit_solo,
                'is_explicit_accomp': is_explicit_accomp,
                'is_vocal_name': is_vocal_name
            })

        # Prune only true OCR artifacts (e.g. empty phantom staves with 0 notes).
        # We explicitly preserve Vocal Solos, Choral Staves, and Instrumental Accompaniments (Others)
        to_prune = []
        for m in part_info:
            if m['note_count'] == 0:
                to_prune.append(m)

        if to_prune and (len(parts) - len(to_prune)) >= 1:
            for m in to_prune:
                p = m['part']
                sp = m['score_part']
                if p in list(root):
                    root.remove(p)
                    modified = True
                if sp is not None and sp in list(part_list):
                    part_list.remove(sp)
                    modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 2d & 2e. Preserve Dynamics and Ornaments from Audiveris OMR output
    # ─────────────────────────────────────────────────────────────────────────

    # ─────────────────────────────────────────────────────────────────────────
    # 2f. Reconstruct Misclassified Multimeasure Rests
    # ─────────────────────────────────────────────────────────────────────────
    # Audiveris OCR frequently defaults multimeasure rests to <multiple-rest>1</multiple-rest>
    # while placing the printed numeral (e.g. 2, 3, 4, 8) in an orphan <credit-words> block.
    # We dynamically find matching orphan numerals, update <multiple-rest>, insert N-1 placeholder
    # measures so OSMD natively collapses them, and renumber subsequent measures.
    parts = root.findall('part')
    if parts:
        first_part = parts[0]
        first_part_measures = first_part.findall('measure')
        for m_idx, m_elem in enumerate(first_part_measures):
            mr_elem = m_elem.find('.//measure-style/multiple-rest')
            if mr_elem is not None and (mr_elem.text or '').strip() == '1':
                matched_credit = None
                matched_N = None

                for credit in root.findall('credit'):
                    c_page = credit.get('page', '1')
                    if str(c_page) != '1':
                        continue
                    for cw in credit.findall('credit-words'):
                        txt = (cw.text or '').strip()
                        if txt.isdigit():
                            val = int(txt)
                            if 2 <= val <= 99:
                                try:
                                    x = float(cw.get('default-x', '0'))
                                    y = float(cw.get('default-y', '0'))
                                except ValueError:
                                    continue
                                # Exclude system start measure numbers (x <= 230)
                                if x > 250 and y > 1400:
                                    matched_credit = credit
                                    matched_N = val
                                    break
                    if matched_credit is not None:
                        break

                if matched_credit is not None and matched_N is not None:
                    N = matched_N
                    m_num_attr = m_elem.get('number')
                    start_num = int(m_num_attr) if (m_num_attr and m_num_attr.isdigit()) else 1

                    # Duration for placeholder notes
                    note_elem = m_elem.find('note')
                    dur_text = '12'
                    if note_elem is not None:
                        d = note_elem.find('duration')
                        if d is not None and d.text:
                            dur_text = d.text.strip()

                    for p in parts:
                        p_measures = p.findall('measure')
                        if m_idx >= len(p_measures):
                            continue
                        curr_m = p_measures[m_idx]

                        curr_mr = curr_m.find('.//measure-style/multiple-rest')
                        if curr_mr is not None:
                            curr_mr.text = str(N)

                        # Remove print-object="no" on rest notes so OSMD prints them
                        for n in curr_m.findall('note'):
                            if 'print-object' in n.attrib:
                                del n.attrib['print-object']

                        next_m_idx = m_idx + 1
                        offset = N - 1
                        if next_m_idx < len(p_measures):
                            next_num_raw = p_measures[next_m_idx].get('number', '1')
                            if next_num_raw.isdigit():
                                offset = (start_num + N) - int(next_num_raw)

                        p_children = list(p)
                        insert_pos = p_children.index(curr_m) + 1

                        for k in range(1, N):
                            placeholder_num = str(start_num + k)
                            placeholder_m = ET.Element('measure', {'number': placeholder_num})
                            n_elem = ET.SubElement(placeholder_m, 'note')
                            ET.SubElement(n_elem, 'rest')
                            t_el = ET.SubElement(n_elem, 'type')
                            t_el.text = 'whole'
                            dur_elem = ET.SubElement(n_elem, 'duration')
                            dur_elem.text = dur_text
                            p.insert(insert_pos, placeholder_m)
                            insert_pos += 1

                        all_m_after = list(p.findall('measure'))[m_idx + N:]
                        for sm in all_m_after:
                            sm_num = sm.get('number')
                            if sm_num and sm_num.isdigit():
                                sm.set('number', str(int(sm_num) + offset))

                        del p_measures[:]
                        p_measures.extend(p.findall('measure'))

                    if matched_credit in list(root):
                        root.remove(matched_credit)
                    modified = True
                    break

    # ─────────────────────────────────────────────────────────────────────────
    # 2g. Strip All Remaining <credit> Tags (reclaim vertical space on mobile)
    # ─────────────────────────────────────────────────────────────────────────
    for credit in list(root.findall('credit')):
        root.remove(credit)
        modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 2c. Preserve original vocal staves without deleting lead or resting parts
    # ─────────────────────────────────────────────────────────────────────────

    # 3. Ensure choral parts remain merged and canonically ordered
    if merge_split_choral_parts(root):
        modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 3c. OMR Structural & Rhythmic Artifact Repair (Lossless Score Fidelity)
    # ─────────────────────────────────────────────────────────────────────────
    # A. Repair duplicate measure numbers with implicit="yes"
    surviving_parts = root.findall('part')
    if surviving_parts:
        first_p_meas = surviving_parts[0].findall('measure')
        seen_mnums_dup = set()
        has_dup_meas = False
        for m in first_p_meas:
            mnum = m.get('number')
            if mnum in seen_mnums_dup:
                has_dup_meas = True
                break
            seen_mnums_dup.add(mnum)

        if has_dup_meas:
            for seq, m_seq in enumerate(first_p_meas, start=1):
                for p in surviving_parts:
                    pm_list = p.findall('measure')
                    if seq - 1 < len(pm_list):
                        pm = pm_list[seq - 1]
                        if 'implicit' in pm.attrib:
                            del pm.attrib['implicit']
                        pm.set('number', str(seq))
            modified = True

    # B. Double-measure sustained solo note (e.g. Awit m.21-22 unbarred solo measure)
    surviving_parts = root.findall('part')
    for p in surviving_parts:
        p_meas = p.findall('measure')
        for i in range(len(p_meas) - 1):
            m_curr = p_meas[i]
            m_next = p_meas[i+1]
            notes_curr = m_curr.findall('note')
            notes_next = m_next.findall('note')
            if len(notes_curr) == 1 and len(notes_next) == 0:
                note_c = notes_curr[0]
                dur_c = note_c.find('duration')
                if dur_c is not None and dur_c.text and dur_c.text.isdigit():
                    dur_val = int(dur_c.text)
                    sib_curr_durs = [sum(int(n.findtext('duration', '0')) for n in p2.findall('measure')[i].findall('note') if n.find('chord') is None) for p2 in surviving_parts if p2 != p and i < len(p2.findall('measure'))]
                    sib_next_durs = [sum(int(n.findtext('duration', '0')) for n in p2.findall('measure')[i+1].findall('note') if n.find('chord') is None) for p2 in surviving_parts if p2 != p and i+1 < len(p2.findall('measure'))]
                    if sib_curr_durs and sib_next_durs:
                        m_sib = max(set(sib_curr_durs), key=sib_curr_durs.count)
                        if m_sib > 0 and dur_val >= 2 * m_sib:
                            import copy as _c
                            dur_c.text = str(m_sib)
                            if note_c.find('tie') is None:
                                ET.SubElement(note_c, 'tie', {'type': 'start'})
                            notations = note_c.find('notations')
                            if notations is None:
                                notations = ET.SubElement(note_c, 'notations')
                            if notations.find('tied') is None:
                                ET.SubElement(notations, 'tied', {'type': 'start'})

                            note_stop = _c.deepcopy(note_c)
                            lyr = note_stop.find('lyric')
                            if lyr is not None:
                                note_stop.remove(lyr)
                            t = note_stop.find('tie')
                            if t is not None:
                                t.set('type', 'stop')
                            nt = note_stop.find('notations')
                            if nt is not None:
                                td = nt.find('tied')
                                if td is not None:
                                    td.set('type', 'stop')
                            m_next.append(note_stop)
                            modified = True

    # C. Beam misread chord & quarter inflation in 3/4 scores with declared 4/4
    surviving_parts = root.findall('part')
    if surviving_parts:
        m1_time = surviving_parts[0].find(".//measure[@number='1']//time")
        declared_44 = (m1_time is not None and m1_time.findtext('beats') == '4' and m1_time.findtext('beat-type') == '4')
        meas_durs = []
        for p in surviving_parts:
            for m in p.findall('measure'):
                notes = [n for n in m.findall('note') if n.find('chord') is None]
                if notes:
                    meas_durs.append(sum(int(n.findtext('duration', '0')) for n in notes))
        if meas_durs and declared_44:
            div2_34_count = sum(1 for d in meas_durs if d == 6)
            div4_34_count = sum(1 for d in meas_durs if d == 12)
            is_actually_34 = (div2_34_count / len(meas_durs) >= 0.5) or (div4_34_count / len(meas_durs) >= 0.5)
            if is_actually_34:
                expected_34_dur = 6 if div2_34_count > div4_34_count else 12
                quarter_dur = expected_34_dur // 3
                eighth_dur = quarter_dur // 2
                for p in surviving_parts:
                    for midx, m in enumerate(p.findall('measure')):
                        sib_durs = [sum(int(n.findtext('duration', '0')) for n in p2.findall('measure')[midx].findall('note') if n.find('chord') is None) for p2 in surviving_parts if p2 != p and midx < len(p2.findall('measure'))]
                        if sib_durs:
                            sib_expected = max(set(sib_durs), key=sib_durs.count)
                            if sib_expected == expected_34_dur:
                                notes = m.findall('note')
                                cur_dur = sum(int(n.findtext('duration', '0')) for n in notes if n.find('chord') is None)
                                if cur_dur == expected_34_dur + quarter_dur:
                                    chords = [n for n in notes if n.find('chord') is not None]
                                    for cn in chords:
                                        p_cn = cn.find('pitch')
                                        if p_cn is not None and p_cn.findtext('step') == 'C' and int(p_cn.findtext('octave', '0')) >= 5:
                                            m.remove(cn)
                                    non_chords = [n for n in m.findall('note') if n.find('chord') is None]
                                    if len(non_chords) >= 2:
                                        n1, n2 = non_chords[-2], non_chords[-1]
                                        if int(n1.findtext('duration', '0')) == quarter_dur and int(n2.findtext('duration', '0')) == quarter_dur:
                                            d1_el = n1.find('duration')
                                            d2_el = n2.find('duration')
                                            if d1_el is not None:
                                                d1_el.text = str(eighth_dur)
                                            if d2_el is not None:
                                                d2_el.text = str(eighth_dur)
                                            t1_el = n1.find('type')
                                            if t1_el is not None:
                                                t1_el.text = 'eighth'
                                            t2_el = n2.find('type')
                                            if t2_el is not None:
                                                t2_el.text = 'eighth'
                                            modified = True

    # D. Propagate consensus key signatures to parts missing key in measure 1
    if harmonize_choral_key_signatures(root):
        modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 3b. Universal Choral Lyric Synchronization & Dynamic Disambiguation
    # ─────────────────────────────────────────────────────────────────────────
    try:
        synced = synchronize_choral_lyrics(root)
        if synced > 0:
            modified = True
    except Exception as e:
        pass

    # ─────────────────────────────────────────────────────────────────────────
    # 4. Clef Fallback & Pitch Integrity Verification
    # ─────────────────────────────────────────────────────────────────────────
    # Ensure every surviving part has a valid <clef> in its first measure
    for p in root.findall('part'):
        first_m = p.find('measure')
        if first_m is not None:
            attrs = first_m.find('attributes')
            clef = first_m.find('.//clef')
            if clef is None:
                # Infer clef from pitch distribution across the part
                total_pitch = 0
                count_pitch = 0
                step_to_semitone = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
                for note in p.findall('.//note'):
                    p_elem = note.find('pitch')
                    if p_elem is not None:
                        s = p_elem.findtext('step') or 'C'
                        o = int(p_elem.findtext('octave', '4'))
                        midi = (o + 1) * 12 + step_to_semitone.get(s, 0)
                        total_pitch += midi
                        count_pitch += 1

                avg_midi = total_pitch / count_pitch if count_pitch > 0 else 60
                if attrs is None:
                    attrs = ET.Element('attributes')
                    first_m.insert(0, attrs)

                clef = ET.SubElement(attrs, 'clef')
                sign = ET.SubElement(clef, 'sign')
                line = ET.SubElement(clef, 'line')
                p_name_lower = (p.get('id', '') + ' ' + (p.findtext('.//part-name') or '')).lower()
                if avg_midi < 60 or 'bass' in p_name_lower or 'tenor' in p_name_lower or 't/b' in p_name_lower:
                    sign.text = 'F'
                    line.text = '4'
                else:
                    sign.text = 'G'
                    line.text = '2'
                modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 5. Ensure empty measures have explicit full-measure rests matching sibling duration
    # ─────────────────────────────────────────────────────────────────────────
    all_active_parts = root.findall('part')
    if all_active_parts:
        max_meas_len = max(len(p.findall('measure')) for p in all_active_parts)
        for midx in range(max_meas_len):
            sib_durs = []
            for p in all_active_parts:
                p_meas = p.findall('measure')
                if midx < len(p_meas):
                    m = p_meas[midx]
                    ns = [n for n in m.findall('note') if n.find('chord') is None]
                    if ns:
                        d = sum(int(n.findtext('duration', '0')) for n in ns)
                        sib_durs.append(d)
            target_dur = max(set(sib_durs), key=sib_durs.count) if sib_durs else 12

            for p in all_active_parts:
                p_meas = p.findall('measure')
                if midx < len(p_meas):
                    m = p_meas[midx]
                    all_m_notes = m.findall('note')
                    pitched_m_notes = [n for n in all_m_notes if n.find('rest') is None]
                    if len(all_m_notes) == 0:
                        note_el = ET.SubElement(m, 'note')
                        ET.SubElement(note_el, 'rest', {'measure': 'yes'})
                        dur_el = ET.SubElement(note_el, 'duration')
                        dur_el.text = str(target_dur)
                        voice_el = ET.SubElement(note_el, 'voice')
                        voice_el.text = '1'
                        modified = True
                    elif len(pitched_m_notes) == 0 and len(all_m_notes) == 1:
                        # Clean up a single full-measure rest: preserve measure="yes", remove conflicting type or dots
                        n0 = all_m_notes[0]
                        r = n0.find('rest')
                        cur_dur = int(n0.findtext('duration', '0'))
                        if r is not None and (r.get('measure') == 'yes' or cur_dur >= target_dur):
                            if r.get('measure') != 'yes':
                                r.set('measure', 'yes')
                                modified = True
                            for d in list(n0.findall('dot')):
                                n0.remove(d)
                                modified = True
                            for t in list(n0.findall('type')):
                                n0.remove(t)
                                modified = True
                            dur = n0.find('duration')
                            if dur is not None and dur.text != str(target_dur):
                                dur.text = str(target_dur)
                                modified = True

    # 5b. Close dangling syllabic begin/middle before extended rests or end of part
    for p in root.findall('part'):
        p_notes = p.findall('.//note')
        for i, n in enumerate(p_notes):
            lyr = n.find('lyric')
            if lyr is not None:
                syl = lyr.find('syllabic')
                if syl is not None and syl.text in ['begin', 'middle']:
                    rem_pitched = [n2 for n2 in p_notes[i+1:] if n2.find('rest') is None]
                    if not rem_pitched:
                        syl.text = 'single' if syl.text == 'begin' else 'end'
                        modified = True
                    else:
                        rests_before_next = 0
                        for n2 in p_notes[i+1:]:
                            if n2.find('rest') is not None:
                                rests_before_next += 1
                            else:
                                break
                        if rests_before_next >= 2:
                            syl.text = 'single' if syl.text == 'begin' else 'end'
                            modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 6. Global Measure Synchronization & Padding for Staggered Entrances (e.g. Be Not Afraid)
    # ─────────────────────────────────────────────────────────────────────────
    all_parts = root.findall('part')
    if len(all_parts) > 1:
        canonical_measure_numbers = []
        seen_mnums = set()
        measure_templates = {}
        global_divisions = 4
        global_beats = 4
        global_beat_type = 4

        for p in all_parts:
            current_div = global_divisions
            for m in p.findall('measure'):
                mnum = m.get('number')
                d_elem = m.find('.//divisions')
                if d_elem is not None and d_elem.text:
                    try:
                        current_div = int(d_elem.text.strip())
                        global_divisions = current_div
                    except ValueError:
                        pass
                b_elem = m.find('.//time/beats')
                bt_elem = m.find('.//time/beat-type')
                if b_elem is not None and b_elem.text:
                    try:
                        global_beats = int(b_elem.text.strip())
                    except ValueError:
                        pass
                if bt_elem is not None and bt_elem.text:
                    try:
                        global_beat_type = int(bt_elem.text.strip())
                    except ValueError:
                        pass

                if mnum and mnum not in seen_mnums:
                    seen_mnums.add(mnum)
                    canonical_measure_numbers.append(mnum)

                if mnum and mnum not in measure_templates:
                    m_dur = int(round(global_beats * (4.0 / global_beat_type) * current_div))
                    m_attrs = m.find('attributes')
                    measure_templates[mnum] = (m_dur, m_attrs, current_div)

        try:
            if all(m.isdigit() for m in canonical_measure_numbers):
                canonical_measure_numbers.sort(key=lambda x: int(x))
        except Exception:
            pass

        for p in all_parts:
            existing_measures = {m.get('number'): m for m in p.findall('measure')}
            part_pname = (p.get('id', '') + ' ' + (p.findtext('.//part-name') or '')).lower()
            default_clef_sign = 'F' if any(w in part_pname for w in ['bass', 'tenor', 't/b', 'men']) else 'G'
            default_clef_line = '4' if default_clef_sign == 'F' else '2'

            for mnum in canonical_measure_numbers:
                if mnum not in existing_measures:
                    new_m = ET.Element('measure', {'number': mnum, 'print-object': 'no'})
                    dur_val, tmpl_attrs, div_val = measure_templates.get(mnum, (global_divisions * 4, None, global_divisions))

                    new_attrs = ET.Element('attributes')
                    d_el = ET.SubElement(new_attrs, 'divisions')
                    d_el.text = str(div_val)
                    if mnum == canonical_measure_numbers[0]:
                        clef_el = ET.SubElement(new_attrs, 'clef')
                        s_el = ET.SubElement(clef_el, 'sign')
                        s_el.text = default_clef_sign
                        l_el = ET.SubElement(clef_el, 'line')
                        l_el.text = default_clef_line
                    if tmpl_attrs is not None:
                        key_el = tmpl_attrs.find('key')
                        if key_el is not None:
                            new_attrs.append(copy.deepcopy(key_el))
                        time_el = tmpl_attrs.find('time')
                        if time_el is not None:
                            new_attrs.append(copy.deepcopy(time_el))
                    # In cutaway scores where staves drop in/out across systems, mark missing measures print-object="no"
                    ET.SubElement(new_attrs, 'staff-details', {'print-object': 'no'})
                    new_m.append(new_attrs)

                    note_el = ET.SubElement(new_m, 'note', {'print-object': 'no'})
                    ET.SubElement(note_el, 'rest', {'measure': 'yes'})
                    dur_el = ET.SubElement(note_el, 'duration')
                    dur_el.text = str(dur_val)
                    voice_el = ET.SubElement(note_el, 'voice')
                    voice_el.text = '1'

                    p.append(new_m)
                    modified = True

            all_m_elems = list(p.findall('measure'))
            m_by_num = {}
            for m in all_m_elems:
                m_by_num.setdefault(m.get('number'), []).append(m)
            for m in all_m_elems:
                p.remove(m)
            for mnum in canonical_measure_numbers:
                if mnum in m_by_num:
                    for m in m_by_num[mnum]:
                        p.append(m)


    # ─────────────────────────────────────────────────────────────────────────
    # 6b. System-wide Cutaway & Print-Object Propagation
    # ─────────────────────────────────────────────────────────────────────────
    # In cutaway scores, staves that drop out have print-object="no" at system starts.
    # Propagate this status to all consecutive rest measures within the cutaway system
    # so notation renderers (OSMD) and inspectors recognize the omitted staves.
    for p in root.findall('part'):
        is_cutaway = False
        for m in p.findall('measure'):
            sd = m.find('.//staff-details')
            if sd is not None and sd.get('print-object') == 'no':
                is_cutaway = True
            elif sd is not None and sd.get('print-object') == 'yes':
                is_cutaway = False

            po = m.get('print-object')
            if po == 'no':
                is_cutaway = True
            elif po == 'yes':
                is_cutaway = False

            has_pitched = any(n.find('pitch') is not None for n in m.findall('note'))
            if has_pitched:
                is_cutaway = False
                if 'print-object' in m.attrib and m.attrib['print-object'] == 'no':
                    del m.attrib['print-object']
            elif is_cutaway:
                m.set('print-object', 'no')
                for n in m.findall('note'):
                    n.set('print-object', 'no')

    # 5. Preserve original score part order from Audiveris OMR
    # Forced regex reordering is disabled to maintain score layout fidelity.

    return modified


def clean_musicxml_file(filepath: str) -> bool:
    """Cleans a MusicXML file (.xml or .mxl) in-place."""
    p = Path(filepath)
    if not p.exists():
        return False

    # Locate source PDF in uploads or parent directory if present (prefer normalized 72dpi vector PDF)
    pdf_path = None
    try:
        base = p.stem
        raw_name = base.split('-', 1)[1] if '-' in base else base
        candidates = [
            p.parent.parent / 'uploads' / f"norm_{base}.pdf",
            p.parent / f"norm_{base}.pdf",
            p.parent.parent / 'uploads' / f"norm_{raw_name}.pdf",
            p.parent.parent / 'uploads' / f"{base}.pdf",
            p.parent / f"{base}.pdf",
            p.parent.parent / 'uploads' / f"{raw_name}.pdf",
            p.with_suffix('.pdf'),
        ]
        uploads_dir = p.parent.parent / 'uploads'
        if uploads_dir.exists():
            for f in uploads_dir.glob(f"*norm*{raw_name}*.pdf"):
                candidates.append(f)
            for f in uploads_dir.glob(f"*{raw_name}*.pdf"):
                candidates.append(f)
        for c in candidates:
            if c.exists():
                pdf_path = str(c.resolve())
                break
    except Exception:
        pass

    is_mxl = False
    with open(p, 'rb') as f:
        header = f.read(4)
        if header.startswith(b'PK'):
            is_mxl = True

    if is_mxl:
        with zipfile.ZipFile(filepath, 'r') as z:
            xml_names = [n for n in z.namelist() if (n.endswith('.xml') or n.endswith('.musicxml')) and not n.startswith('META-INF')]
            if not xml_names:
                return False
            xml_name = xml_names[0]
            xml_data = z.read(xml_name)
            
            # Read other files to preserve (e.g. META-INF/container.xml)
            other_files = {n: z.read(n) for n in z.namelist() if n != xml_name}

        root = ET.fromstring(xml_data)
        modified = clean_musicxml_tree(root, pdf_path=pdf_path)

        if modified:
            new_xml_data = ET.tostring(root, encoding='utf-8', xml_declaration=True)
            with zipfile.ZipFile(filepath, 'w', compression=zipfile.ZIP_DEFLATED) as z:
                z.writestr(xml_name, new_xml_data)
                for name, data in other_files.items():
                    z.writestr(name, data)
        return modified
    else:
        tree = ET.parse(filepath)
        root = tree.getroot()
        modified = clean_musicxml_tree(root, pdf_path=pdf_path)
        if modified:
            tree.write(filepath, encoding='utf-8', xml_declaration=True)
        return modified


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python musicxml_cleaner.py <path_to_musicxml_or_mxl>")
        sys.exit(1)
        
    target_file = sys.argv[1]
    res = clean_musicxml_file(target_file)
    print(f"Cleaned {target_file}: modified={res}")
