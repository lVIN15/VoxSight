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

def is_stray_voice_name(name: str) -> bool:
    s = re.sub(r'[.\s_-]+', '', (name or '').lower())
    if re.match(r'^(soprano|sopr|sop|s)[1-9]?$', s): return False
    if re.match(r'^(alto|alt|a|contralto)[1-9]?$', s): return False
    if re.match(r'^(tenor|ten|t)[1-9]?$', s): return False
    if re.match(r'^(bass|bas|b)[1-9]?$', s): return False
    if re.match(r'^(baritone|bar)[1-9]?$', s): return False
    if re.match(r'^(mezzosoprano|mezzo)[1-9]?$', s): return False
    if re.match(r'^(voice|part|vocal|lead|solo)[0-9]*$', s): return True
    if s == '' or s == 'musicxmlpart' or s == 'staff': return True
    return False


def get_non_rest_note_count(measure: ET.Element) -> int:
    if measure is None: return 0
    cnt = 0
    for n in measure.findall('note'):
        if n.find('rest') is None:
            cnt += 1
    return cnt


def reconcile_choral_parts(root: ET.Element) -> bool:
    """Preserves all vocal and instrumental parts from Audiveris without destructive deletion."""
    return False


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


def fix_bass_cadence_whole_notes(root: ET.Element) -> bool:
    """Neutralized: Notes and pitches must never be hardcoded based on measure numbers or octave assumptions."""
    return False


def clean_musicxml_tree(root: ET.Element) -> bool:
    """Cleans the XML tree in-place. Returns True if modifications were made."""
    modified = False

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
    # 2c. Preserve original vocal staves without deleting lead or resting parts
    # ─────────────────────────────────────────────────────────────────────────

    # 3. Detect & merge split / complementary parts (e.g. S/A + Alto, T/B + Bass)
    # ─────────────────────────────────────────────────────────────────────────
    part_list = root.find('part-list')
    parts = root.findall('part')
    if part_list is not None and len(parts) >= 2:
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
        import copy as _copy
        for p in list(root.findall('part')):
            pid = p.get('id')
            sp = part_list.find(f"score-part[@id='{pid}']")
            pname = (sp.findtext('part-name') or '') if sp is not None else ''
            
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
                    m_clone = _copy.deepcopy(m)
                    if m in f_measures:
                        new_p.append(m_clone)
                        for note in list(m.findall('note')):
                            m.remove(note)
                        rest_note = ET.SubElement(m, 'note', {'print-object': 'no'})
                        ET.SubElement(rest_note, 'rest', {'measure': 'yes'})
                        dur = ET.SubElement(rest_note, 'duration')
                        dur.text = '24'
                        v = ET.SubElement(rest_note, 'voice')
                        v.text = '1'
                        # In p, this was an F-clef measure moved to bass, so p is cutaway here!
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
                        ET.SubElement(rest_note, 'rest', {'measure': 'yes'})
                        dur = ET.SubElement(rest_note, 'duration')
                        dur.text = '24'
                        v = ET.SubElement(rest_note, 'voice')
                        v.text = '1'
                        # In new_p (bass), this was a G-clef measure belonging to treble, so new_p is cutaway here!
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
                        
                # Vocal Clef Invariant: Sanitize clefs in remaining treble/solo part p
                # Remove any stray F-clef from p so it does not infect subsequent measures
                for m in p.findall('measure'):
                    for attrs in m.findall('attributes'):
                        for c in list(attrs.findall('clef')):
                            if (c.findtext('sign') or '').strip().upper() == 'F':
                                attrs.remove(c)
                # Ensure measure 1 of p has explicit G2 clef
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

                # Ensure measure 1 of new_p (Bass) has explicit F4 clef
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
            pid = p.get('id')
            sp = root.find(f".//score-part[@id='{pid}']")
            raw_name = sp.find('part-name').text if (sp is not None and sp.find('part-name') is not None and sp.find('part-name').text) else pid
            raw_abbr = sp.find('part-abbreviation').text if (sp is not None and sp.find('part-abbreviation') is not None and sp.find('part-abbreviation').text) else raw_name
            norm_name = NAME_MAP.get(raw_name.strip().lower(), raw_name.strip())
            tier = get_tier(raw_name)

            instr_el = sp.find('.//instrument-name') if sp is not None else None
            raw_instr = instr_el.text.strip().lower() if (instr_el is not None and instr_el.text) else ""

            combined_ident = f"{raw_name} {raw_abbr} {raw_instr}".lower()
            is_accomp = any(re.search(rf"\b{re.escape(kw)}\b", combined_ident) for kw in ACCOMP_KEYWORDS)
            solo_indicators = ['solo', 'cantor', 'leader', 'descant', 'baritone solo', 'tenor solo', 'soprano solo', 'alto solo', 'bass solo']
            is_solo = (
                any(re.search(rf"\b{re.escape(kw)}\b", combined_ident) for kw in solo_indicators)
                or bool(re.search(r'\bsol\b|\bsol\.', combined_ident))
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
        # ─── Solo Detection & Assembly (Two-Strategy) ──────────────────────
        # Strategy A: Name-based — if any part is explicitly labeled "SOLO"
        # Strategy B: Position-based — detect solo by musical structure
        # Then: Multi-fragment assembly — merge all Solo parts into one

        # Strategy A: Solo continuation harmonization (if explicit SOLO label exists)
        has_explicit_solo = any(info['is_solo'] for info in part_info.values())
        if has_explicit_solo:
            choir_measures = set()
            for pid, info in part_info.items():
                if info['is_solo'] or info['is_accomp']:
                    continue
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
        has_choral_parts = any(info['tier'] in (1, 2, 3, 4) for info in part_info.values())
        if not has_choral_parts and not any(info['is_solo'] for info in part_info.values()):
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

                # Solo condition: choir enters later than score starting point,
                # AND all measures before the choir entrance are monophonic/duet (<= 2 parts)
                if choir_entrance_m > earliest_m:
                    intro_measures = [m for m in measure_active_parts if m < choir_entrance_m]
                    is_solo_intro = intro_measures and all(len(measure_active_parts[m]) <= 2 for m in intro_measures)

                    if is_solo_intro:
                        # All non-accomp parts active in the intro are Solo fragments!
                        for pid in non_accomp_pids:
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
            # Pick anchor = prefer part with measure 1 notes (so Baritone Solo stays on Stave #1), then note count
            solo_pids.sort(key=lambda pid: (
                1 if (part_info[pid]['element'].find("measure[@number='1']") is not None and 
                     any(n.find('rest') is None for n in part_info[pid]['element'].find("measure[@number='1']").findall('note'))) else 0,
                sum(part_info[pid]['measures'].get(m, 0) for m in part_info[pid]['measures'])
            ), reverse=True)
            anchor_pid = solo_pids[0]
            anchor_p = part_info[anchor_pid]['element']
            anchor_sp = part_info[anchor_pid]['score_part']

            # Rename anchor to "Solo" if generic, but preserve specific solo names (e.g. Baritone Solo)
            if anchor_sp is not None:
                norm_anchor = part_info[anchor_pid].get('norm_name', '')
                pn = anchor_sp.find('part-name')
                if pn is None:
                    pn = ET.SubElement(anchor_sp, 'part-name')
                current_pname = (pn.text or "").strip().lower()
                if norm_anchor == 'Baritone Solo':
                    pn.text = 'Baritone Solo'
                elif not current_pname or current_pname in ['voice', 's', 's.', 'sop']:
                    pn.text = 'Solo'
                pa = anchor_sp.find('part-abbreviation')
                if pa is None:
                    pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                current_pabbr = (pa.text or "").strip().lower()
                if norm_anchor == 'Baritone Solo':
                    pa.text = 'Bar. Solo'
                elif not current_pabbr or current_pabbr in ['voice', 's', 's.', 'sop']:
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

                # Preserve authentic cutaway / hidden staff metadata from Audiveris

                # Remove fragment from score
                if frag_p in list(root):
                    root.remove(frag_p)
                if frag_sp is not None and frag_sp in list(part_list):
                    part_list.remove(frag_sp)
                merged_solo_count += 1

            if merged_solo_count > 0:
                # Ensure Solo anchor part never retains F-clef
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
                # Preserve specific vocal solo names (e.g. Soprano Solo, Cantor); only normalize if empty or generic
                current_pname = (pn.text or "").strip().lower()
                if norm_solo == 'Baritone Solo':
                    pn.text = 'Baritone Solo'
                    pa = anchor_sp.find('part-abbreviation')
                    if pa is None:
                        pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                    pa.text = 'Bar. Solo'
                    modified = True
                elif not current_pname or current_pname in ['voice', 's', 's.', 'sop']:
                    pn.text = 'Solo'
                    pa = anchor_sp.find('part-abbreviation')
                    if pa is None:
                        pa = ET.SubElement(anchor_sp, 'part-abbreviation')
                    pa.text = 'Solo'
                    modified = True


        # Step 3a: Classify paired generic 'Voice' parts directly above 'Soprano 2' fragments
        pids_in_order = [p.get('id') for p in parts if p.get('id') in part_info]
        for idx in range(len(pids_in_order) - 1):
            pid_curr = pids_in_order[idx]
            pid_next = pids_in_order[idx + 1]
            info_c = part_info[pid_curr]
            info_n = part_info[pid_next]
            if not info_c.get('is_accomp') and not info_n.get('is_accomp'):
                if info_c['norm_name'].lower() in ['voice', 'part'] and info_n['norm_name'] in ['Soprano 2', 'S2']:
                    info_c['norm_name'] = 'Soprano'
                    info_c['tier'] = 1

        # Step 3b: Multi-fragment Voice Reassembly (Order-Preserving)
        # Iteratively merge all non-overlapping fragments for each vocal voice
        name_groups = {}
        for pid in pids_in_order:
            info = part_info[pid]
            if info.get('is_accomp') or info.get('is_solo'):
                continue
            norm = info['norm_name']
            if norm in ['Soprano 1', 'Soprano']:
                canon = 'Soprano'
            elif norm in ['Soprano 2']:
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
            else:
                canon = norm
            name_groups.setdefault(canon, []).append(pid)

        for canon, g_pids in name_groups.items():
            if len(g_pids) > 1:
                # Check overlap among fragments in group
                has_overlap = False
                for i in range(len(g_pids)):
                    for j in range(i + 1, len(g_pids)):
                        m_i = part_info[g_pids[i]]['measures']
                        m_j = part_info[g_pids[j]]['measures']
                        if any(mnum in m_j and m_i[mnum] > 0 and m_j[mnum] > 0 for mnum in m_i):
                            has_overlap = True
                            break
                    if has_overlap:
                        break

                if not has_overlap:
                    # Pick anchor: preferred is part containing measure 1 or earliest active measure
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

                            m1_candidates = [m for m in anchor_p.findall(f"measure[@number='{mnum}']")]
                            if m1_candidates:
                                target_m1 = m1_candidates[0]
                                t_non_rest = [n for n in target_m1.findall('note') if n.find('rest') is None]
                                if not t_non_rest:
                                    import copy
                                    m2_clone = copy.deepcopy(m2)
                                    m2_attrs = m2_clone.find('attributes')
                                    t_attrs = target_m1.find('attributes')
                                    if t_attrs is not None and m2_attrs is None:
                                        m2_clone.insert(0, copy.deepcopy(t_attrs))
                                    elif t_attrs is not None and m2_attrs is not None:
                                        for child in list(t_attrs):
                                            if child.tag != 'measure-style' and m2_attrs.find(child.tag) is None:
                                                m2_attrs.append(copy.deepcopy(child))
                                    idx = list(anchor_p).index(target_m1)
                                    anchor_p.remove(target_m1)
                                    anchor_p.insert(idx, m2_clone)
                            else:
                                import copy
                                anchor_p.append(copy.deepcopy(m2))

                        # Preserve authentic cutaway / hidden staff metadata from Audiveris

                        if frag_p in list(root):
                            root.remove(frag_p)
                        if frag_sp is not None and frag_sp in list(part_list):
                            part_list.remove(frag_sp)
                        modified = True

    # ─────────────────────────────────────────────────────────────────────────
    # 3b-post. Canonical Choral Stave Ordering (AGENTS.md Stave & Part Ordering Rule)
    # ─────────────────────────────────────────────────────────────────────────
    # Vertical stave order in the score MUST match standard choral system layout:
    # Soloist staves (e.g. Solo, Baritone Solo, Cantor) MUST appear on top of choir,
    # NEVER below Bass or collapsed into adjacent parts.
    # Order: Solo -> Soprano (S1, S2, SA) -> Alto (A1, A2) -> Tenor (T1, T2, TB) -> Bass (B1, B2) -> Accompaniment
    part_list = root.find('part-list')
    if part_list is not None:
        curr_score_parts = part_list.findall('score-part')
        if len(curr_score_parts) > 1:
            # 1. Divisi Sibling Promotion:
            # If a score contains "Soprano 2" and an unnumbered "Soprano", promote "Soprano" to "Soprano 1"
            # Apply universally for Alto, Tenor, Bass
            divisi_pairs = [
                ('soprano', ['soprano 2', 's2', 's 2', 'sop 2', 'sop. 2', 'soprano ii'], 'Soprano 1', 'S1'),
                ('alto', ['alto 2', 'a2', 'a 2', 'alt 2', 'alt. 2', 'alto ii'], 'Alto 1', 'A1'),
                ('tenor', ['tenor 2', 't2', 't 2', 'ten 2', 'ten. 2', 'tenor ii'], 'Tenor 1', 'T1'),
                ('bass', ['bass 2', 'b2', 'b 2', 'bas 2', 'bas. 2', 'bass ii'], 'Bass 1', 'B1'),
            ]
            for voice_base, second_indicators, target_name, target_abbr in divisi_pairs:
                has_second = any(
                    any(ind in ((sp.findtext('part-name') or '') + ' ' + (sp.findtext('part-abbreviation') or '')).lower() for ind in second_indicators)
                    for sp in curr_score_parts
                )
                if has_second:
                    for sp in curr_score_parts:
                        pn = (sp.findtext('part-name') or '').strip().lower()
                        pa = (sp.findtext('part-abbreviation') or '').strip().lower()
                        comb = f"{pn} {pa}"
                        if voice_base in comb and not any(ind in comb for ind in second_indicators) and not any(cond in comb for cond in ['s. a.', 's.a.', 'sa', 's/a', 't. b.', 't.b.', 'tb', 't/b']):
                            pn_elem = sp.find('part-name')
                            if pn_elem is None:
                                pn_elem = ET.SubElement(sp, 'part-name')
                            pn_elem.text = target_name
                            pa_elem = sp.find('part-abbreviation')
                            if pa_elem is None:
                                pa_elem = ET.SubElement(sp, 'part-abbreviation')
                            pa_elem.text = target_abbr
                            modified = True

            def get_stave_order_rank(sp_elem):
                pn = (sp_elem.findtext('part-name') or '').strip().lower()
                pa = (sp_elem.findtext('part-abbreviation') or '').strip().lower()
                comb = f"{pn} {pa}"
                if any(kw in comb for kw in ['solo', 'sol.', 'sol', 'cantor', 'leader', 'descant']):
                    return 0
                if any(kw in comb for kw in ['soprano 1', 's1', 's 1', 'sop 1', 'sop. 1', 'soprano i']):
                    return 10
                if any(kw in comb for kw in ['soprano 2', 's2', 's 2', 'sop 2', 'sop. 2', 'soprano ii']):
                    return 11
                if any(kw in comb for kw in ['s. a.', 's.a.', 'sa', 's/a', 's & a']):
                    return 12
                if any(kw in comb for kw in ['soprano', 'sop']):
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
                if any(kw in comb for kw in ['piano', 'pno', 'organ', 'keyboard', 'guitar', 'accomp', 'orch', 'strings']):
                    return 100
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
                                            n1.find('duration').text = str(eighth_dur)
                                            n2.find('duration').text = str(eighth_dur)
                                            if n1.find('type') is not None:
                                                n1.find('type').text = 'eighth'
                                            if n2.find('type') is not None:
                                                n2.find('type').text = 'eighth'
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
                    if len(m.findall('note')) == 0:
                        note_el = ET.SubElement(m, 'note')
                        ET.SubElement(note_el, 'rest', {'measure': 'yes'})
                        dur_el = ET.SubElement(note_el, 'duration')
                        dur_el.text = str(target_dur)
                        voice_el = ET.SubElement(note_el, 'voice')
                        voice_el.text = '1'
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
