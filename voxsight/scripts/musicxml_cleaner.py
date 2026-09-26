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

        # NOTE: Forced lyric cloning disabled to protect polyphony & staggered entrances.
        # In pieces like "Be Not Afraid", the Solo sings while SATB rests — copying Solo lyrics
        # onto resting choir parts corrupts the score. Lyrics are now only retained where
        # Audiveris originally recognized them.
        # for p in parts:
        #     pid = p.get('id')
        #     if pid not in part_notes:
        #         continue
        #
        #     p_lyr_map = part_lyrics.get(pid, {})
        #     for onset, note in part_notes[pid]:
        #         if onset not in p_lyr_map and onset in measure_pool:
        #             new_lyr = copy.deepcopy(measure_pool[onset])
        #             note.append(new_lyr)
        #             synced_count += 1

    return synced_count

def clean_musicxml_tree(root: ET.Element) -> bool:
    """Cleans the XML tree in-place. Returns True if modifications were made."""
    modified = False

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
            
            name_lower = raw_name.lower()
            abbr_lower = raw_abbr.lower()
            is_solo = any(kw in name_lower or kw in abbr_lower for kw in ['solo', 'cantor', 'leader', 'descant'])
            is_accomp = any(kw in name_lower or kw in abbr_lower for kw in ['piano', 'organ', 'keyboard', 'guitar', 'accomp', 'orch', 'strings'])

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
        # ─── Solo Detection & Assembly (Two-Strategy) ──────────────────────
        # Strategy A: Name-based — if any part is explicitly labeled "SOLO"
        # Strategy B: Position-based — detect solo by musical structure
        # Then: Multi-fragment assembly — merge all Solo parts into one

        # Strategy A: Solo continuation harmonization (if explicit SOLO label exists)
        has_explicit_solo = any(info['is_solo'] for info in part_info.values())
        if has_explicit_solo:
            choir_measures = set()
            for pid, info in part_info.items():
                # Detect choir staves by Alto, Tenor, Bass names OR Bass Clef
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

        # Strategy B: Position-based Solo detection (when Audiveris labels everything "Voice")
        # Detects staggered choral entrances by musical structure, not part names.
        if not any(info['is_solo'] for info in part_info.values()):
            non_accomp_pids = [pid for pid, info in part_info.items() if not info['is_accomp']]

            # Count how many non-accomp parts have notes at each measure
            measure_active_parts = {}
            for pid in non_accomp_pids:
                for m, cnt in part_info[pid]['measures'].items():
                    if cnt > 0 and m.isdigit():
                        mi = int(m)
                        measure_active_parts.setdefault(mi, set()).add(pid)

            # Detect choir entrance:
            # 1. First measure with 3+ parts active simultaneously (full choir chord)
            # 2. First measure where bass-clef (F-clef) parts enter (choir Tenor/Bass)
            # IMPORTANT: For bass clef, only check the FIRST measure's clef (initial clef).
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
                        first_bass_m = min(first_bass_m, int(m))

            choir_entrance_m = min(first_poly_m, first_bass_m)

            if measure_active_parts and choir_entrance_m < 9999:
                earliest_m = min(measure_active_parts.keys())

                # Solo condition: choir enters later than score starting point,
                # AND all measures before the choir entrance are monophonic/duet (<= 2 parts)
                if choir_entrance_m > earliest_m:
                    intro_measures = [m for m in measure_active_parts if m < choir_entrance_m]
                    is_solo_intro = intro_measures and all(len(measure_active_parts[m]) <= 2 for m in intro_measures)

                    if is_solo_intro:
                        # All non-accomp treble parts active in the intro are Solo fragments!
                        for pid in non_accomp_pids:
                            if pid in bass_clef_pids:
                                continue
                            if part_info[pid]['tier'] in (1, 2, 3, 4):
                                continue
                            active_m = [int(m) for m, cnt in part_info[pid]['measures'].items() if cnt > 0 and m.isdigit()]
                            if active_m and any(m < choir_entrance_m for m in active_m):
                                part_info[pid]['is_solo'] = True
                                part_info[pid]['norm_name'] = 'Solo'
                                part_info[pid]['tier'] = 0

        # Multi-fragment Solo assembly: merge ALL Solo parts into one anchor.
        # Audiveris often shatters a single Solo vocal line across 3–5+ parts
        # (one per system/page), sometimes with OCR-garbled names ('E', 'a').
        # This reassembles them into a single continuous Solo part.
        solo_pids = [pid for pid, info in part_info.items() if info['is_solo']]
        if len(solo_pids) > 1:
            import copy as _copy
            # Pick anchor = the Solo part with the most total notes
            solo_pids.sort(key=lambda pid: sum(part_info[pid]['measures'].get(m, 0) for m in part_info[pid]['measures']), reverse=True)
            anchor_pid = solo_pids[0]
            anchor_p = part_info[anchor_pid]['element']
            anchor_sp = part_info[anchor_pid]['score_part']

            # Rename anchor to "Solo"
            if anchor_sp is not None:
                pn = anchor_sp.find('part-name')
                if pn is None:
                    pn = ET.SubElement(anchor_sp, 'part-name')
                pn.text = 'Solo'
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                pa.text = 'Solo'

            merged_solo_count = 0
            for frag_pid in solo_pids[1:]:
                frag_p = part_info[frag_pid]['element']
                frag_sp = part_info[frag_pid]['score_part']

                # Merge fragment's note-containing measures into anchor
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
                            # Anchor has rest at this measure → replace with fragment's notes
                            m2_clone = _copy.deepcopy(m2)
                            m2_attrs = m2_clone.find('attributes')
                            t_attrs = target_m1.find('attributes')
                            if t_attrs is not None and m2_attrs is None:
                                m2_clone.insert(0, _copy.deepcopy(t_attrs))
                            elif t_attrs is not None and m2_attrs is not None:
                                for child in list(t_attrs):
                                    if child.tag != 'measure-style' and m2_attrs.find(child.tag) is None:
                                        m2_attrs.append(_copy.deepcopy(child))
                            idx = list(anchor_p).index(target_m1)
                            anchor_p.remove(target_m1)
                            anchor_p.insert(idx, m2_clone)
                    else:
                        anchor_p.append(_copy.deepcopy(m2))

                # Ensure staff-details are visible
                for sd in anchor_p.findall('.//staff-details'):
                    if sd.get('print-object') == 'no':
                        sd.set('print-object', 'yes')

                # Remove fragment from score
                if frag_p in list(root):
                    root.remove(frag_p)
                if frag_sp is not None and frag_sp in list(part_list):
                    part_list.remove(frag_sp)
                merged_solo_count += 1

            if merged_solo_count > 0:
                modified = True
        elif len(solo_pids) == 1:
            anchor_sp = part_info[solo_pids[0]]['score_part']
            if anchor_sp is not None:
                pn = anchor_sp.find('part-name')
                if pn is None:
                    pn = ET.SubElement(anchor_sp, 'part-name')
                pn.text = 'Solo'
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                pa.text = 'Solo'
                modified = True


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

            is_solo1 = info1.get('is_solo', False)
            is_accomp1 = info1.get('is_accomp', False)

            for j in range(i + 1, len(pids)):
                pid2 = pids[j]
                if pid2 in merged_pids:
                    continue
                info2 = part_info[pid2]
                norm2 = info2['norm_name'].lower()
                tier2 = info2['tier']
                is_solo2 = info2.get('is_solo', False)
                is_accomp2 = info2.get('is_accomp', False)

                # Never merge a Solo part with a non-Solo part, or an Accompaniment with a Vocal part
                if is_solo1 != is_solo2 or is_accomp1 != is_accomp2:
                    continue

                tier_match = (tier1 != 0 and tier1 == tier2)
                name_match = (norm1 == norm2) or tier_match or (len(parts) == 8 and j == i + 4)

                if name_match:
                    # Guard: If both parts have generic names ('voice'/'part'), never merge across different clefs!
                    if norm1 in ['voice', 'part'] and norm2 in ['voice', 'part']:
                        f1 = any(m.find('.//clef/sign') is not None and m.find('.//clef/sign').text == 'F' for m in part_info[pid1]['element'].findall('measure'))
                        f2 = any(m.find('.//clef/sign') is not None and m.find('.//clef/sign').text == 'F' for m in part_info[pid2]['element'].findall('measure'))
                        if f1 != f2:
                            continue

                    m1 = info1['measures']
                    m2 = info2['measures']
                    overlap = any(mnum in m2 and m1[mnum] > 0 and m2[mnum] > 0 for mnum in m1)
                    
                    if not overlap:
                        canon_name, canon_abbr = ('Solo', 'Solo') if is_solo1 else get_canonical_name_and_abbr(info1['raw_name'], info2['raw_name'])
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
                    new_m = ET.Element('measure', {'number': mnum})
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
                    new_m.append(new_attrs)

                    note_el = ET.SubElement(new_m, 'note')
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
