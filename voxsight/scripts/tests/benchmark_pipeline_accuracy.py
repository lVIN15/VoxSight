#!/usr/bin/env python3
"""
VoxSight Phase 4 Objective Downstream Accuracy & Cross-Layer Parity Benchmark
=============================================================================
Measures objective accuracy across the complete post-OMR pipeline:
  1. A_score : Downstream score note, pitch, onset, duration & stave-order fidelity
  2. A_sync  : 1:1 source_note_id <-> XML data-vx-id binding & split-measure timeline sync
  3. A_voice : Vocal-part & divisi identification accuracy (satb_analyzer.py)
  4. A_color : End-to-end hex color accuracy & 100% parity across renderer.html + interactive_editor.html

Hard Acceptance Gates:
  - Aggregate & per-fixture A_score >= 99.0%
  - Aggregate & per-fixture A_sync  >= 95.0%
  - Aggregate & per-fixture A_voice >= 90.0%
  - Aggregate & per-fixture A_color >= 90.0%
"""

import json
import os
import pathlib
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

TESTS_DIR = pathlib.Path(__file__).resolve().parent
SCRIPTS_DIR = TESTS_DIR.parent
REPO_ROOT = SCRIPTS_DIR.parent.parent

sys.path.insert(0, str(SCRIPTS_DIR))
import satb_analyzer  # noqa: E402

CANONICAL_HEX = {
    "S": "#E11D48",
    "SOPRANO": "#E11D48",
    "S1": "#FB7185",
    "SOPRANO 1": "#FB7185",
    "S2": "#881337",
    "SOPRANO 2": "#881337",
    "A": "#8E24AA",
    "ALTO": "#8E24AA",
    "A1": "#C084FC",
    "ALTO 1": "#C084FC",
    "A2": "#4C1D95",
    "ALTO 2": "#4C1D95",
    "T": "#2563EB",
    "TENOR": "#2563EB",
    "T1": "#0284C7",
    "TENOR 1": "#0284C7",
    "T2": "#1E3A8A",
    "TENOR 2": "#1E3A8A",
    "B": "#16A34A",
    "BASS": "#16A34A",
    "B1": "#65A30D",
    "BASS 1": "#65A30D",
    "B2": "#064E3B",
    "BASS 2": "#064E3B",
    "UNISON_SA": "#4F46E5",
    "UNISON_TB": "#0D9488",
    "UNISON_S1S2": "#E11D48",
    "UNISON_A1A2": "#8E24AA",
    "UNISON_T1T2": "#2563EB",
    "UNISON_B1B2": "#16A34A",
    "TUTTI": "#CA8A04",
    "UNISON_ALL": "#CA8A04",
    "SOLO": "#FF6D00",
    "OTHERS": "#546E7A",
    "OTHER": "#546E7A",
}

STEP_OFFSETS = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note_xml(step, octave, duration=4, voice=1, ntype="quarter", alter=0, stem=None, is_chord=False, extra_attrs=""):
    chord_tag = "<chord/>" if is_chord else ""
    alter_tag = f"<alter>{alter}</alter>" if alter else ""
    stem_tag = f"<stem>{stem}</stem>" if stem else ""
    attr_str = f" {extra_attrs.strip()}" if extra_attrs.strip() else ""
    return (
        f"<note{attr_str}>{chord_tag}"
        f"<pitch><step>{step}</step>{alter_tag}<octave>{octave}</octave></pitch>"
        f"<duration>{duration}</duration><voice>{voice}</voice><type>{ntype}</type>{stem_tag}"
        f"</note>"
    )


def rest_xml(duration=4, voice=1, ntype="quarter"):
    return f"<note><rest/><duration>{duration}</duration><voice>{voice}</voice><type>{ntype}</type></note>"


def clef_xml(sign="G", line=2, oct_change=None):
    oct_tag = f"<clef-octave-change>{oct_change}</clef-octave-change>" if oct_change is not None else ""
    return f"<clef><sign>{sign}</sign><line>{line}</line>{oct_tag}</clef>"


def build_score_xml(parts_spec, measures_per_part):
    """
    parts_spec: list of (part_id, part_name)
    measures_per_part: dict[part_id -> list of (measure_num_str, implicit_bool, attrs_xml, body_xml)]
    """
    plist = ["<part-list>"]
    for pid, pname in parts_spec:
        plist.append(f'<score-part id="{pid}"><part-name>{pname}</part-name></score-part>')
    plist.append("</part-list>")

    pblocks = []
    for pid, _ in parts_spec:
        pblocks.append(f'<part id="{pid}">')
        for m_num, implicit, attrs_xml, body_xml in measures_per_part[pid]:
            imp_attr = ' implicit="yes"' if implicit else ""
            attr_block = f"<attributes>{attrs_xml}</attributes>" if attrs_xml else ""
            pblocks.append(f'<measure number="{m_num}"{imp_attr}>{attr_block}{body_xml}</measure>')
        pblocks.append("</part>")

    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<score-partwise version="3.1">\n'
        + "\n".join(plist)
        + "\n"
        + "\n".join(pblocks)
        + "\n</score-partwise>\n"
    )


def extract_xml_pitched_notes(xml_text):
    """Extracts deterministic ordered pitched note tuples and data-vx-* attributes from MusicXML."""
    root = ET.fromstring(xml_text)
    part_order = [sp.attrib.get("id", "") for sp in root.findall("./part-list/score-part")]
    notes = []
    by_vx_id = {}

    for p_idx, part_el in enumerate(root.findall("./part")):
        pid = part_el.attrib.get("id", f"P{p_idx + 1}")
        divisions = 1
        for m_idx, meas_el in enumerate(part_el.findall("./measure")):
            div_el = meas_el.find("./attributes/divisions")
            if div_el is not None and (div_el.text or "").strip().isdigit():
                d_val = int(div_el.text.strip())
                if d_val > 0:
                    divisions = d_val

            cursor = 0
            prev_onset = 0
            for child in list(meas_el):
                tag = child.tag.split("}")[-1]
                if tag == "backup":
                    dur = int((child.findtext("duration") or "0").strip() or "0")
                    cursor = max(0, cursor - dur)
                elif tag == "forward":
                    dur = int((child.findtext("duration") or "0").strip() or "0")
                    cursor += dur
                elif tag == "note":
                    if child.attrib.get("print-object") == "no":
                        continue
                    is_chord = child.find("chord") is not None
                    dur = int((child.findtext("duration") or "0").strip() or "0")
                    onset = prev_onset if is_chord else cursor
                    if not is_chord:
                        prev_onset = cursor
                        cursor += dur
                    if child.find("rest") is not None:
                        continue
                    pitch_el = child.find("pitch")
                    if pitch_el is None:
                        continue
                    step = (pitch_el.findtext("step") or "C").strip().upper()
                    octv = int((pitch_el.findtext("octave") or "4").strip())
                    alt = int((pitch_el.findtext("alter") or "0").strip() or "0")
                    midi = (octv + 1) * 12 + STEP_OFFSETS.get(step, 0) + alt
                    voice = int((child.findtext("voice") or "1").strip() or "1")
                    staff = int((child.findtext("staff") or "1").strip() or "1")
                    vx_id_raw = child.attrib.get("data-vx-id")
                    vx_id = int(vx_id_raw) if (vx_id_raw and vx_id_raw.strip().isdigit()) else None
                    vx_voice = (child.attrib.get("data-vx-voice") or "").strip() or None

                    rec = {
                        "part_idx": p_idx,
                        "part_id": pid,
                        "m_idx": m_idx,
                        "m_num": meas_el.attrib.get("number", str(m_idx + 1)),
                        "onset_q": round(onset / float(divisions), 4),
                        "dur_q": round(dur / float(divisions), 4),
                        "midi": midi,
                        "voice": voice,
                        "staff": staff,
                        "vx_id": vx_id,
                        "vx_voice": vx_voice,
                    }
                    notes.append(rec)
                    if vx_id is not None:
                        by_vx_id[vx_id] = rec

    return part_order, notes, by_vx_id


def evaluate_js_colors_batch(notes):
    """
    Runs the ACTUAL getVocalPartInfo (renderer.html) and getVocalPartColor (interactive_editor.html)
    via Node.js on all stamped note records to verify 100% end-to-end color parity.
    """
    payload = [
        {
            "voice": n["voice"],
            "staffIdx": n["part_idx"] + (n["staff"] - 1),
            "partIdx": n["part_idx"],
            "totalParts": max((x["part_idx"] for x in notes), default=0) + 1,
            "customVoice": n["vx_voice"],
        }
        for n in notes
    ]

    js_harness = r"""
const fs = require('fs');
const vm = require('vm');

const rendererHtml = fs.readFileSync(process.argv[2], 'utf8');
const editorHtml = fs.readFileSync(process.argv[3], 'utf8');
const items = JSON.parse(fs.readFileSync(process.argv[4], 'utf8'));

const rMatch = rendererHtml.match(/function getVocalPartInfo\([\s\S]*?\n {8}\}/);
const eMatch = editorHtml.match(/function getVocalPartColor\([\s\S]*?\n {8}\}/);
if (!rMatch || !eMatch) {
    console.error('Failed to extract getVocalPartInfo or getVocalPartColor');
    process.exit(2);
}

const ctx = { xmlDoc: null, console, VOICE_PALETTE: { S: '#E11D48', A: '#8E24AA', T: '#2563EB', B: '#16A34A', SOLO: '#FF6D00', OTHER: '#546E7A', UNKNOWN: '#808080' } };
vm.createContext(ctx);
vm.runInContext(rMatch[0], ctx);
vm.runInContext(eMatch[0], ctx);

const out = items.map(it => {
    const rInfo = ctx.getVocalPartInfo(it.voice, it.staffIdx, it.partIdx, it.totalParts, 'single', it.customVoice, false);
    const eColor = ctx.getVocalPartColor(it.voice, it.staffIdx, it.partIdx, it.totalParts, 'single', it.customVoice, false);
    return { rColor: String(rInfo.color).toUpperCase(), eColor: String(eColor).toUpperCase(), rPart: rInfo.part };
});
process.stdout.write(JSON.stringify(out));
"""
    renderer_path = REPO_ROOT / "mobile/app/src/main/assets/renderer.html"
    editor_path = REPO_ROOT / "mobile/app/src/main/assets/interactive_editor.html"

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tf_in, \
         tempfile.NamedTemporaryFile("w", suffix=".js", delete=False, encoding="utf-8") as tf_js:
        json.dump(payload, tf_in)
        in_path = tf_in.name
        tf_js.write(js_harness)
        js_path = tf_js.name

    try:
        proc = subprocess.run(
            ["node", js_path, str(renderer_path), str(editor_path), in_path],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0:
            raise RuntimeError(f"Node JS harness failed ({proc.returncode}): {proc.stderr}")
        return json.loads(proc.stdout)
    finally:
        for p in (in_path, js_path):
            if os.path.exists(p):
                os.remove(p)


# ---------------------------------------------------------------------------
# Representative Score Fixtures & Ground-Truth Oracles
# ---------------------------------------------------------------------------
def fixture_1_five_part_generic_solo_satb_split_divisi():
    """5-stave generic 'Voice Oohs' Solo + SATB + page-break split measure + in-staff divisi."""
    parts = [(f"P{i}", "Voice Oohs") for i in range(1, 6)]
    m_map = {pid: [] for pid, _ in parts}

    # 8 logical measures including a split measure ("4" + implicit "4") -> 9 XML <measure> elements
    measure_headers = [
        ("1", False, True),
        ("2", False, False),
        ("3", False, False),
        ("4", False, False),  # First half of split measure 4 (2 beats)
        ("4", True, False),   # Second half of split measure 4 after page break (2 beats)
        ("5", False, False),
        ("6", False, False),
        ("7", False, False),
        ("8", False, False),
    ]
    clefs = {
        "P1": clef_xml("G", 2),
        "P2": clef_xml("G", 2),
        "P3": clef_xml("G", 2),
        "P4": clef_xml("F", 4),
        "P5": clef_xml("F", 4),
    }

    for idx, (m_num, is_imp, is_first) in enumerate(measure_headers):
        dur = 8 if m_num == "4" else 16
        ntype = "half" if m_num == "4" else "whole"
        for pid, _ in parts:
            attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs[pid]}" if is_first else ""
            if pid == "P1":
                # Sparse soloist: sings ONLY in measure index 1 and 7 (2 / 9 active measures < 45%)
                body = note_xml("D", 4, dur, 1, ntype) if idx in (1, 7) else rest_xml(dur, 1, ntype)
            elif pid == "P2":
                # Soprano: with S1/S2 divisi chord in measure index 5
                if idx == 5:
                    body = note_xml("G", 4, dur, 1, ntype) + note_xml("E", 4, dur, 1, ntype, is_chord=True)
                else:
                    body = note_xml("F", 4, dur, 1, ntype)
            elif pid == "P3":
                # Alto: with A1/A2 divisi chord in measure index 4 (the implicit split measure!)
                if idx == 4:
                    body = note_xml("D", 4, dur, 1, ntype) + note_xml("B", 3, dur, 1, ntype, is_chord=True)
                else:
                    body = note_xml("C", 4, dur, 1, ntype)
            elif pid == "P4":
                # Tenor (higher F-clef)
                body = note_xml("G", 3, dur, 1, ntype)
            else:
                # Bass (lower F-clef)
                body = note_xml("C", 3, dur, 1, ntype)
            m_map[pid].append((m_num, is_imp, attrs, body))

    xml_str = build_score_xml(parts, m_map)

    def oracle(n, group_by_part_measure_onset):
        p = n["part_idx"]
        peers = group_by_part_measure_onset[(p, n["m_idx"], n["onset_q"])]
        distinct_midis = sorted({x["midi"] for x in peers}, reverse=True)
        if p == 0:
            return "Solo"
        base = {1: "S", 2: "A", 3: "T", 4: "B"}[p]
        if len(distinct_midis) >= 2:
            return f"{base}1" if n["midi"] == distinct_midis[0] else f"{base}2"
        return base

    return "5-Part Generic 'Voice Oohs' Solo+SATB+Split+Divisi", xml_str, oracle


def fixture_2_open_satb_dual_f_clef_with_register_dips():
    """4-part unnamed 'Voice' Open SATB where Soprano dips to A3 and Tenor/Bass both use F-clefs."""
    parts = [(f"P{i}", "Voice") for i in range(1, 5)]
    m_map = {pid: [] for pid, _ in parts}
    clefs = {"P1": clef_xml("G", 2), "P2": clef_xml("G", 2), "P3": clef_xml("F", 4), "P4": clef_xml("F", 4)}
    sop_pitches = [("G", 4), ("A", 3), ("F", 4), ("A", 4)]  # Dip to A3 in m.2 must stay Soprano!
    alt_pitches = [("D", 4), ("F", 3), ("C", 4), ("D", 4)]
    ten_pitches = [("A", 3), ("D", 4), ("G", 3), ("A", 3)]
    bas_pitches = [("D", 3), ("D", 3), ("C", 3), ("F", 2)]

    for m in range(4):
        for pid, seq in [("P1", sop_pitches), ("P2", alt_pitches), ("P3", ten_pitches), ("P4", bas_pitches)]:
            attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs[pid]}" if m == 0 else ""
            step, octv = seq[m]
            m_map[pid].append((str(m + 1), False, attrs, note_xml(step, octv, 16, 1, "whole")))

    xml_str = build_score_xml(parts, m_map)
    return "4-Part Generic Open SATB (Dual F-Clef + Register Dips)", xml_str, lambda n, _: {0: "S", 1: "A", 2: "T", 3: "B"}[n["part_idx"]]


def fixture_3_open_satb_treble_8vb_tenor():
    """4-part generic Open SATB where Tenor uses transposing G2-8vb clef."""
    parts = [(f"P{i}", "Voice") for i in range(1, 5)]
    m_map = {pid: [] for pid, _ in parts}
    clefs = {
        "P1": clef_xml("G", 2),
        "P2": clef_xml("G", 2),
        "P3": clef_xml("G", 2, oct_change=-1),
        "P4": clef_xml("F", 4),
    }
    pitches = {"P1": ("G", 4), "P2": ("C", 4), "P3": ("A", 3), "P4": ("D", 3)}
    for m in range(4):
        for pid in ("P1", "P2", "P3", "P4"):
            attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs[pid]}" if m == 0 else ""
            st, oc = pitches[pid]
            m_map[pid].append((str(m + 1), False, attrs, note_xml(st, oc, 16, 1, "whole")))

    xml_str = build_score_xml(parts, m_map)
    return "4-Part Open SATB (Transposing G2-8vb Tenor Clef)", xml_str, lambda n, _: {0: "S", 1: "A", 2: "T", 3: "B"}[n["part_idx"]]


def fixture_4_condensed_two_staff_satb():
    """2-part condensed SATB (S/A + T/B) with 4-voice polyphony, partner-rest solo, and stemless unison."""
    parts = [("P1", "Soprano / Alto"), ("P2", "Tenor / Bass")]
    m_map = {"P1": [], "P2": []}

    # Measure 1: Standard 2-voice polyphony per staff via <backup>
    m1_p1 = note_xml("G", 4, 16, 1, "whole", stem="up") + "<backup><duration>16</duration></backup>" + note_xml("C", 4, 16, 2, "whole", stem="down")
    m1_p2 = note_xml("G", 3, 16, 1, "whole", stem="up") + "<backup><duration>16</duration></backup>" + note_xml("C", 3, 16, 2, "whole", stem="down")
    m_map["P1"].append(("1", False, f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clef_xml('G', 2)}", m1_p1))
    m_map["P2"].append(("1", False, f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clef_xml('F', 4)}", m1_p2))

    # Measure 2: Soprano sings stem-up while Alto has explicit Voice 2 rest; Tenor sings stem-up while Bass rests
    m2_p1 = note_xml("F", 4, 8, 1, "half", stem="up") + note_xml("E", 4, 8, 1, "half", stem="up") + "<backup><duration>16</duration></backup>" + rest_xml(16, 2, "whole")
    m2_p2 = note_xml("F", 3, 8, 1, "half", stem="up") + note_xml("G", 3, 8, 1, "half", stem="up") + "<backup><duration>16</duration></backup>" + rest_xml(16, 2, "whole")
    m_map["P1"].append(("2", False, "", m2_p1))
    m_map["P2"].append(("2", False, "", m2_p2))

    # Measure 3: Exact same-pitch dual-voice unison on both staves (UNISON_SA and UNISON_TB)
    m3_p1 = note_xml("D", 4, 16, 1, "whole", stem="up") + "<backup><duration>16</duration></backup>" + note_xml("D", 4, 16, 2, "whole", stem="down")
    m3_p2 = note_xml("D", 3, 16, 1, "whole", stem="up") + "<backup><duration>16</duration></backup>" + note_xml("D", 3, 16, 2, "whole", stem="down")
    m_map["P1"].append(("3", False, "", m3_p1))
    m_map["P2"].append(("3", False, "", m3_p2))

    xml_str = build_score_xml(parts, m_map)

    def oracle(n, _):
        if n["m_idx"] == 2:
            return "UNISON_SA" if n["part_idx"] == 0 else "UNISON_TB"
        if n["part_idx"] == 0:
            return "S" if n["voice"] == 1 else "A"
        return "T" if n["voice"] == 1 else "B"

    return "Condensed 2-Staff SATB (Polyphony + Partner Rest + Unison)", xml_str, oracle


def fixture_5_accompanied_satb_plus_piano():
    """5-part score: Open SATB + Piano Accompaniment (must isolate Piano as Others, is_vocal=False)."""
    parts = [("P1", "Soprano"), ("P2", "Alto"), ("P3", "Tenor"), ("P4", "Bass"), ("P5", "Piano")]
    m_map = {pid: [] for pid, _ in parts}
    clefs = {"P1": clef_xml("G", 2), "P2": clef_xml("G", 2), "P3": clef_xml("F", 4), "P4": clef_xml("F", 4), "P5": clef_xml("G", 2)}
    pitches = {"P1": ("G", 4), "P2": ("D", 4), "P3": ("A", 3), "P4": ("D", 3)}

    for m in range(4):
        for pid in ("P1", "P2", "P3", "P4"):
            attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs[pid]}" if m == 0 else ""
            st, oc = pitches[pid]
            m_map[pid].append((str(m + 1), False, attrs, note_xml(st, oc, 16, 1, "whole")))
        p_attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs['P5']}" if m == 0 else ""
        piano_body = note_xml("G", 4, 16, 1, "whole") + note_xml("B", 4, 16, 1, "whole", is_chord=True) + note_xml("D", 5, 16, 1, "whole", is_chord=True)
        m_map["P5"].append((str(m + 1), False, p_attrs, piano_body))

    xml_str = build_score_xml(parts, m_map)
    return "Accompanied 4-Part SATB + Piano (Instrumental Isolation)", xml_str, lambda n, _: {0: "S", 1: "A", 2: "T", 3: "B", 4: "Others"}[n["part_idx"]]


def fixture_6_user_override_persistence():
    """Open SATB score containing explicit user vocal-part overrides (data-vx-voice-source='user')."""
    parts = [("P1", "Soprano"), ("P2", "Alto"), ("P3", "Tenor"), ("P4", "Bass")]
    m_map = {pid: [] for pid, _ in parts}
    clefs = {"P1": clef_xml("G", 2), "P2": clef_xml("G", 2), "P3": clef_xml("F", 4), "P4": clef_xml("F", 4)}
    for m in range(2):
        for pid, (st, oc) in [("P1", ("G", 4)), ("P2", ("C", 4)), ("P3", ("G", 3)), ("P4", ("C", 3))]:
            attrs = f"<divisions>4</divisions><time><beats>4</beats><beat-type>4</beat-type></time>{clefs[pid]}" if m == 0 else ""
            extra = 'data-vx-voice="Solo" data-vx-voice-source="user"' if (pid == "P1" and m == 1) else ""
            m_map[pid].append((str(m + 1), False, attrs, note_xml(st, oc, 16, 1, "whole", extra_attrs=extra)))

    xml_str = build_score_xml(parts, m_map)

    def oracle(n, _):
        if n["part_idx"] == 0 and n["m_idx"] == 1:
            return "Solo"
        return {0: "S", 1: "A", 2: "T", 3: "B"}[n["part_idx"]]

    return "User Manual Override Persistence (data-vx-voice-source='user')", xml_str, oracle


def build_awit_real_omr_fixture_if_present():
    awit_path = REPO_ROOT / "tmp/awit_audiveris/audiveris_raw.musicxml"
    if not awit_path.exists():
        return None
    xml_str = awit_path.read_text(encoding="utf-8")

    def oracle(n, group_by_part_measure_onset):
        p = n["part_idx"]
        if p == 0:
            return "Solo"
        base = {1: "S", 2: "A", 3: "T", 4: "B"}[p]
        peers = group_by_part_measure_onset[(p, n["m_idx"], n["onset_q"])]
        distinct_midis = sorted({x["midi"] for x in peers}, reverse=True)
        if len(distinct_midis) >= 2:
            return f"{base}1" if n["midi"] == distinct_midis[0] else f"{base}2"
        return base

    return "Real Raw Audiveris OMR Scan: 'Awit ng Paghahangad' (107 mm, 991 notes)", xml_str, oracle


def evaluate_fixture(name, raw_xml, oracle_fn):
    in_parts, in_notes, _ = extract_xml_pitched_notes(raw_xml)

    with tempfile.NamedTemporaryFile("w", suffix=".musicxml", delete=False, encoding="utf-8") as tf_in:
        tf_in.write(raw_xml)
        in_path = tf_in.name
    try:
        result = satb_analyzer.analyze(in_path)
        assert "error" not in result, f"{name}: analyze() failed: {result.get('error')}"
        stamped_xml = pathlib.Path(in_path).read_text(encoding="utf-8")
    finally:
        if os.path.exists(in_path):
            os.remove(in_path)

    out_parts, out_notes, out_by_vx_id = extract_xml_pitched_notes(stamped_xml)

    # 1. A_score: Stave order + 1:1 note preservation (part_idx, m_idx, onset_q, dur_q, midi)
    stave_order_ok = (in_parts == out_parts)
    in_sigs = [(n["part_idx"], n["m_idx"], n["onset_q"], n["dur_q"], n["midi"]) for n in in_notes]
    out_sigs = [(n["part_idx"], n["m_idx"], n["onset_q"], n["dur_q"], n["midi"]) for n in out_notes]
    matched_score_notes = sum(1 for a, b in zip(in_sigs, out_sigs) if a == b) if (stave_order_ok and len(in_sigs) == len(out_sigs)) else 0
    total_notes = len(in_notes)

    # 2. A_sync: Non-rest playback events bound via valid integer source_note_id to exact XML data-vx-id & pitch
    pitched_events = [ev for ev in result.get("events", []) if not ev.get("is_rest", False)]
    synced_events = 0
    for ev in pitched_events:
        sid = ev.get("source_note_id")
        if isinstance(sid, int) and sid in out_by_vx_id:
            xn = out_by_vx_id[sid]
            if xn["midi"] == ev.get("pitch_midi") and xn["m_idx"] == ev.get("measure_index"):
                synced_events += 1

    # 3. A_voice & 4. A_color (with live renderer.html + interactive_editor.html JS execution)
    group_map = {}
    for n in out_notes:
        key = (n["part_idx"], n["m_idx"], n["onset_q"])
        group_map.setdefault(key, []).append(n)

    js_colors = evaluate_js_colors_batch(out_notes)
    voice_correct = 0
    color_correct = 0
    parity_matches = 0

    for n, js_res in zip(out_notes, js_colors):
        expected_token = oracle_fn(n, group_map)
        expected_hex = CANONICAL_HEX[expected_token.upper()].upper()
        actual_token = (n["vx_voice"] or "").strip()

        if actual_token.upper() == expected_token.upper():
            voice_correct += 1
        if js_res["rColor"] == js_res["eColor"]:
            parity_matches += 1
        if js_res["rColor"] == expected_hex and js_res["eColor"] == expected_hex:
            color_correct += 1

    a_score = 100.0 * matched_score_notes / max(1, total_notes)
    a_sync = 100.0 * synced_events / max(1, len(pitched_events))
    a_voice = 100.0 * voice_correct / max(1, total_notes)
    a_color = 100.0 * color_correct / max(1, total_notes)
    a_parity = 100.0 * parity_matches / max(1, total_notes)

    return {
        "name": name,
        "notes": total_notes,
        "events": len(pitched_events),
        "matched_score": matched_score_notes,
        "synced_events": synced_events,
        "voice_correct": voice_correct,
        "color_correct": color_correct,
        "a_score": a_score,
        "a_sync": a_sync,
        "a_voice": a_voice,
        "a_color": a_color,
        "a_parity": a_parity,
    }


def main():
    fixtures = [
        fixture_1_five_part_generic_solo_satb_split_divisi(),
        fixture_2_open_satb_dual_f_clef_with_register_dips(),
        fixture_3_open_satb_treble_8vb_tenor(),
        fixture_4_condensed_two_staff_satb(),
        fixture_5_accompanied_satb_plus_piano(),
        fixture_6_user_override_persistence(),
    ]
    awit_fixture = build_awit_real_omr_fixture_if_present()
    if awit_fixture is not None:
        fixtures.insert(0, awit_fixture)

    print("=" * 108)
    print("VOXSIGHT PHASE 4 DOWNSTREAM SCORE, SYNC, VOCAL-PART & COLOR ACCURACY BENCHMARK")
    print("=" * 108)
    print(f"{'Fixture':<60} | {'Notes':>5} | {'A_score':>7} | {'A_sync':>7} | {'A_voice':>7} | {'A_color':>7}")
    print("-" * 108)

    results = []
    failures = []
    for name, xml_str, oracle in fixtures:
        metrics = evaluate_fixture(name, xml_str, oracle)
        results.append(metrics)
        short_name = name if len(name) <= 60 else name[:57] + "..."
        print(
            f"{short_name:<60} | {metrics['notes']:>5d} | "
            f"{metrics['a_score']:>6.2f}% | {metrics['a_sync']:>6.2f}% | "
            f"{metrics['a_voice']:>6.2f}% | {metrics['a_color']:>6.2f}%"
        )
        if metrics["a_score"] < 99.0:
            failures.append(f"{name}: A_score {metrics['a_score']:.2f}% < 99.0%")
        if metrics["a_sync"] < 95.0:
            failures.append(f"{name}: A_sync {metrics['a_sync']:.2f}% < 95.0%")
        if metrics["a_voice"] < 90.0:
            failures.append(f"{name}: A_voice {metrics['a_voice']:.2f}% < 90.0%")
        if metrics["a_color"] < 90.0:
            failures.append(f"{name}: A_color {metrics['a_color']:.2f}% < 90.0%")
        if metrics["a_parity"] < 100.0:
            failures.append(f"{name}: Renderer/Editor parity {metrics['a_parity']:.2f}% < 100.0%")

    tot_notes = sum(r["notes"] for r in results)
    tot_events = sum(r["events"] for r in results)
    agg_score = 100.0 * sum(r["matched_score"] for r in results) / max(1, tot_notes)
    agg_sync = 100.0 * sum(r["synced_events"] for r in results) / max(1, tot_events)
    agg_voice = 100.0 * sum(r["voice_correct"] for r in results) / max(1, tot_notes)
    agg_color = 100.0 * sum(r["color_correct"] for r in results) / max(1, tot_notes)

    print("-" * 108)
    print(
        f"{'AGGREGATE ACROSS ALL FIXTURES':<60} | {tot_notes:>5d} | "
        f"{agg_score:>6.2f}% | {agg_sync:>6.2f}% | "
        f"{agg_voice:>6.2f}% | {agg_color:>6.2f}%"
    )
    print("=" * 108)

    if failures:
        print("\n[FAIL] Accuracy gate failures detected:")
        for f in failures:
            print("  -", f)
        sys.exit(1)

    print(
        "\n[PASS] All accuracy gates met: "
        f"A_score={agg_score:.2f}% (>=99%), "
        f"A_sync={agg_sync:.2f}% (>=95%), "
        f"A_voice={agg_voice:.2f}% (>=90%), "
        f"A_color={agg_color:.2f}% (>=90%), "
        "Renderer<->Editor Color Parity=100.00%."
    )


if __name__ == "__main__":
    main()
