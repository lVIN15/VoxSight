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
import xml.etree.ElementTree as ET
from pathlib import Path

CREDIT_KEYWORDS = [
    'prepared', 'arranged by', 'arr.', 'arranger', 'composed by', 'composer', 
    'music by', 'words by', 'lyrics by', 'copyright', 'all rights reserved', 
    'freely distributed', 'duplicated', 'performed', 'recorded', 'edition', 
    'published by', 'printed in', 'jedscott', '.com', '.org', '.net', 'http', 
    'www.', 'page ', 'sheet music', 'transcribed by', 'edited by', 'score',
    'international copyright', 'ccli', 'public domain'
]

NAME_MAP = {
    's': 'Soprano', 'sop': 'Soprano', 'sopr': 'Soprano', 'soprano': 'Soprano', 'soprano 1': 'Soprano 1', 's1': 'Soprano 1', 'soprano 2': 'Soprano 2', 's2': 'Soprano 2',
    'a': 'Alto', 'alt': 'Alto', 'alto': 'Alto', 'alto 1': 'Alto 1', 'a1': 'Alto 1', 'alto 2': 'Alto 2', 'a2': 'Alto 2',
    't': 'Tenor', 'ten': 'Tenor', 'tenor': 'Tenor', 'tenor 1': 'Tenor 1', 't1': 'Tenor 1', 'tenor 2': 'Tenor 2', 't2': 'Tenor 2',
    'b': 'Bass', 'bas': 'Bass', 'bass': 'Bass', 'bass 1': 'Bass 1', 'b1': 'Bass 1', 'bass 2': 'Bass 2', 'b2': 'Bass 2'
}

def clean_musicxml_tree(root: ET.Element) -> bool:
    """Cleans the XML tree in-place. Returns True if modifications were made."""
    modified = False

    # ─────────────────────────────────────────────────────────────────────────
    # 1. Clean misplaced credit / copyright / annotation lyrics
    # ─────────────────────────────────────────────────────────────────────────
    for note in root.findall('.//note'):
        lyrics_to_remove = []
        for lyr in note.findall('lyric'):
            num = lyr.get('number', '1')
            text_elems = lyr.findall('.//text')
            full_text = ' '.join(t.text.strip() for t in text_elems if t.text).strip().lower()

            # Remove secondary orphan lyric layers (e.g. number != 1 which catches stray words like "Scott", "in", "optional")
            if num != '1':
                lyrics_to_remove.append(lyr)
                continue

            # Check credit keywords, performance annotation words, or overly long non-lyric text
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
        'solo', 'soloist', 'cantor', 'leader', 'duet', 'descant', 'obbligato'
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

        # Identify choir vocal parts
        choral_vocal_parts = [
            m for m in part_info
            if not m['is_explicit_solo'] and not m['is_explicit_accomp'] and (m['is_vocal_name'] or m['valid_lyrics'] >= 5)
        ]

        if len(choral_vocal_parts) >= 2 or (len(choral_vocal_parts) >= 1 and len(parts) >= 3):
            to_prune = []
            for idx, m in enumerate(part_info):
                if m['is_explicit_accomp']:
                    to_prune.append(m)
                    continue
                if m['is_explicit_solo'] and len(choral_vocal_parts) >= 2:
                    to_prune.append(m)
                    continue
                if m['staves'] >= 2 and m['valid_lyrics'] < 5:
                    to_prune.append(m)
                    continue
                # Non-vocal parts with negligible/no lyrics (e.g. instrument accompaniment or stray OCR staff)
                if m not in choral_vocal_parts and not m['is_vocal_name']:
                    lyric_ratio = (m['valid_lyrics'] / m['note_count']) if m['note_count'] > 0 else 0
                    if lyric_ratio < 0.08 and (m['chords'] > 0 or m['valid_lyrics'] <= 2):
                        to_prune.append(m)
                        continue

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
    # 2d. Strip Dynamics (direction elements containing <dynamics>)
    # ─────────────────────────────────────────────────────────────────────────
    for part in root.findall('part'):
        for measure in part.findall('measure'):
            for direction in list(measure.findall('direction')):
                has_dynamics = direction.find('.//dynamics') is not None
                if has_dynamics:
                    measure.remove(direction)
                    modified = True

            # Also strip <sound dynamics="..."/> attributes
            for sound in measure.findall('.//sound'):
                if sound.get('dynamics') is not None:
                    del sound.attrib['dynamics']
                    modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 2e. Strip Ornaments / Trills / Turns / Mordents from <notations>
    # ─────────────────────────────────────────────────────────────────────────
    ORNAMENT_TAGS = {'ornaments', 'trill-mark', 'turn', 'inverted-turn',
                     'delayed-turn', 'mordent', 'inverted-mordent', 'shake',
                     'wavy-line', 'tremolo', 'schleifer'}
    for note in root.findall('.//note'):
        for notations in note.findall('notations'):
            for child in list(notations):
                if child.tag in ORNAMENT_TAGS:
                    notations.remove(child)
                    modified = True
            # Remove empty <notations> elements
            if len(notations) == 0:
                note.remove(notations)
                modified = True

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
                    start_num = int(m_elem.get('number', '1')) if (m_elem.get('number') and m_elem.get('number').isdigit()) else 1

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
                            ET.SubElement(n_elem, 'rest', {'measure': 'yes'})
                            dur_elem = ET.SubElement(n_elem, 'duration')
                            dur_elem.text = dur_text
                            p.insert(insert_pos, placeholder_m)
                            insert_pos += 1

                        all_m_after = list(p.findall('measure'))[m_idx + N:]
                        for sm in all_m_after:
                            sm_num = sm.get('number')
                            if sm_num and sm_num.isdigit():
                                sm.set('number', str(int(sm_num) + offset))

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
    # 3. Detect & merge split / complementary parts (e.g. S/A + Alto, T/B + Bass)
    # ─────────────────────────────────────────────────────────────────────────
    part_list = root.find('part-list')
    parts = root.findall('part')
    if part_list is not None and len(parts) >= 2:
        TIER_1 = {'s', 'sop', 'sopr', 'soprano', 'a', 'alt', 'alto', 's/a', 'sa', 's.a.', 'soprano/alto', 'soprano / alto', 'women', 'treble', 's1', 's2', 'a1', 'a2'}
        TIER_2 = {'t', 'ten', 'tenor', 'b', 'bas', 'bass', 't/b', 'tb', 't.b.', 'tenor/bass', 'tenor / bass', 'men', 't1', 't2', 'b1', 'b2'}

        def get_tier(name_str: str) -> int:
            n = (name_str or '').strip().lower()
            if n in TIER_1:
                return 1
            if n in TIER_2:
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

        # Gather part names, tier, and per-measure non-rest note counts
        part_info = {}
        for p in parts:
            pid = p.get('id')
            sp = root.find(f".//score-part[@id='{pid}']")
            raw_name = sp.find('part-name').text if (sp is not None and sp.find('part-name') is not None and sp.find('part-name').text) else pid
            raw_abbr = sp.find('part-abbreviation').text if (sp is not None and sp.find('part-abbreviation') is not None and sp.find('part-abbreviation').text) else raw_name
            norm_name = NAME_MAP.get(raw_name.strip().lower(), raw_name.strip())
            tier = get_tier(raw_name)
            
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
                'measures': measures
            }

        # Find complementary pairs
        pids = [p.get('id') for p in parts]
        merged_pids = set()
        merge_pairs = []

        for i in range(len(pids)):
            pid1 = pids[i]
            if pid1 in merged_pids:
                continue
            info1 = part_info[pid1]
            norm1 = info1['norm_name'].lower()
            tier1 = info1['tier']

            for j in range(i + 1, len(pids)):
                pid2 = pids[j]
                if pid2 in merged_pids:
                    continue
                info2 = part_info[pid2]
                norm2 = info2['norm_name'].lower()
                tier2 = info2['tier']

                tier_match = (tier1 != 0 and tier1 == tier2)
                name_match = (norm1 == norm2) or tier_match or (len(parts) == 8 and j == i + 4)

                if name_match:
                    m1 = info1['measures']
                    m2 = info2['measures']
                    overlap = any(mnum in m2 and m1[mnum] > 0 and m2[mnum] > 0 for mnum in m1)
                    
                    if not overlap:
                        canon_name, canon_abbr = get_canonical_name_and_abbr(info1['raw_name'], info2['raw_name'])
                        merge_pairs.append((pid1, pid2, canon_name, canon_abbr))
                        merged_pids.add(pid1)
                        merged_pids.add(pid2)
                        break

        if merge_pairs:
            for pid1, pid2, canon_name, canon_abbr in merge_pairs:
                p1 = part_info[pid1]['element']
                p2 = part_info[pid2]['element']
                sp1 = part_info[pid1]['score_part']
                sp2 = part_info[pid2]['score_part']

                if sp1 is not None:
                    pn = sp1.find('part-name')
                    if pn is None:
                        pn = ET.SubElement(sp1, 'part-name')
                    pn.text = canon_name
                    pa = sp1.find('part-abbreviation')
                    if pa is None:
                        pa = ET.SubElement(sp1, 'part-abbreviation')
                    pa.text = canon_abbr

                seen_m2 = {}
                p2_measures_list = list(p2.findall('measure'))

                for m2 in p2_measures_list:
                    mnum = m2.get('number')
                    k = seen_m2.get(mnum, 0)
                    seen_m2[mnum] = k + 1

                    m2_non_rest = [n for n in m2.findall('note') if n.find('rest') is None]
                    if not m2_non_rest:
                        continue

                    m1_candidates = [m for m in p1.findall(f"measure[@number='{mnum}']")]
                    if m1_candidates:
                        target_m1 = m1_candidates[k] if k < len(m1_candidates) else m1_candidates[0]
                        t_non_rest = [n for n in target_m1.findall('note') if n.find('rest') is None]

                        if not t_non_rest:
                            import copy
                            m2_clone = copy.deepcopy(m2)
                            m2_attrs = m2_clone.find('attributes')

                            # Preserve <measure-style> containing <multiple-rest> from target
                            t_ms_list = target_m1.findall('.//measure-style')
                            if t_ms_list:
                                if m2_attrs is None:
                                    m2_attrs = ET.Element('attributes')
                                    m2_clone.insert(0, m2_attrs)
                                for ms in t_ms_list:
                                    m2_attrs.append(copy.deepcopy(ms))

                            t_attrs = target_m1.find('attributes')
                            if t_attrs is not None and m2_attrs is None:
                                m2_clone.insert(0, copy.deepcopy(t_attrs))
                            elif t_attrs is not None and m2_attrs is not None:
                                for child in list(t_attrs):
                                    if child.tag != 'measure-style' and m2_attrs.find(child.tag) is None:
                                        m2_attrs.append(copy.deepcopy(child))

                            idx = list(p1).index(target_m1)
                            p1.remove(target_m1)
                            p1.insert(idx, m2_clone)
                    else:
                        import copy
                        p1.append(copy.deepcopy(m2))

                for sd in p1.findall('.//staff-details'):
                    if sd.get('print-object') == 'no':
                        sd.set('print-object', 'yes')

                if p2 in list(root):
                    root.remove(p2)
                if sp2 is not None and sp2 in list(part_list):
                    part_list.remove(sp2)

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
                        s = p_elem.findtext('step')
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
    # 5. Ensure empty measures have explicit full-measure rests
    # ─────────────────────────────────────────────────────────────────────────
    for p in root.findall('part'):
        divisions = '4'
        for m in p.findall('measure'):
            d_el = m.find('.//divisions')
            if d_el is not None and d_el.text:
                divisions = d_el.text

            notes = m.findall('note')
            if len(notes) == 0:
                note_el = ET.SubElement(m, 'note')
                ET.SubElement(note_el, 'rest', {'measure': 'yes'})
                dur_el = ET.SubElement(note_el, 'duration')
                try:
                    dur_val = int(divisions) * 3
                except ValueError:
                    dur_val = 12
                dur_el.text = str(dur_val)
                voice_el = ET.SubElement(note_el, 'voice')
                voice_el.text = '1'
                modified = True

    return modified


def clean_musicxml_file(filepath: str) -> bool:
    """Cleans a MusicXML file (.xml or .mxl) in-place."""
    p = Path(filepath)
    if not p.exists():
        return False

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
        modified = clean_musicxml_tree(root)

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
        modified = clean_musicxml_tree(root)
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
