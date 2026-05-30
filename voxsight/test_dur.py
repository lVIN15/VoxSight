import xml.etree.ElementTree as ET

tree = ET.parse('scratch_music.xml')
root = tree.getroot()
divisions_elem = root.find('.//divisions')
divisions = int(divisions_elem.text) if divisions_elem is not None else 1

part_durs = {}
for p in root.findall('.//part'):
    pid = p.get('id')
    dur = 0
    for m in p.findall('.//measure'):
        for n in m:
            if n.tag == 'backup':
                dur -= int(n.find('duration').text)
            elif n.tag == 'forward':
                dur += int(n.find('duration').text)
            elif n.tag == 'note':
                chord = n.find('chord')
                duration_elem = n.find('duration')
                if duration_elem is not None:
                    # In MusicXmlParser, chord notes don't advance the cursor!
                    if chord is None:
                        dur += int(duration_elem.text)
    part_durs[pid] = max(part_durs.get(pid, 0), dur)

max_dur = max(part_durs.values()) if part_durs else 0
print(f"divisions: {divisions}, max_dur: {max_dur}")
beats = max_dur / float(divisions)
print(f"beats: {beats}")
totalSeconds = max(1, round(beats * 60.0 / 96.0))
print(f"totalSeconds: {totalSeconds}")
