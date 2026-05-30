package edu.cit.capstone.voxsight.service;

import edu.cit.capstone.voxsight.dto.ProcessedMusicXmlDto;
import edu.cit.capstone.voxsight.model.NoteModel;
import edu.cit.capstone.voxsight.model.SatbModel;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MusicXmlProcessingService {

    /**
     * Parse a MusicXML file into a flat list of NoteModel objects.
     * This implementation uses the standard DOM parser to avoid extra dependencies.
     */
    public List<NoteModel> parseMusicXml(File xmlFile) throws Exception {
        List<NoteModel> notes = new ArrayList<>();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();

        // Get divisions (ticks per quarter note) – default to 1 if missing
        int divisions = 1;
        NodeList divisionNodes = doc.getElementsByTagName("divisions");
        if (divisionNodes.getLength() > 0) {
            divisions = Integer.parseInt(divisionNodes.item(0).getTextContent().trim());
        }

        // Track cumulative time per voice/staff to compute startTime
        Map<String, Double> timeByKey = new HashMap<>();
        Map<String, Double> lastStartByKey = new HashMap<>();
        NodeList noteNodes = doc.getElementsByTagName("note");
        for (int i = 0; i < noteNodes.getLength(); i++) {
            Element noteElem = (Element) noteNodes.item(i);

            // Duration (in divisions)
            int durDivisions = getIntValue(noteElem, "duration", divisions);
            double durationBeats = (double) durDivisions / divisions;

            // Staff (optional)
            int staff = getIntValue(noteElem, "staff", -1);
            // Voice (optional)
            int voice = getIntValue(noteElem, "voice", -1);
            String timeKey = buildTimeKey(voice, staff);
            double voiceTime = timeByKey.getOrDefault(timeKey, 0.0);

            // Skip rests
            if (noteElem.getElementsByTagName("rest").getLength() > 0) {
                // advance time by duration of rest
                timeByKey.put(timeKey, voiceTime + durationBeats);
                continue;
            }

            // Pitch extraction
            Element pitchElem = (Element) noteElem.getElementsByTagName("pitch").item(0);
            String step = getTextValue(pitchElem, "step");
            int alter = getIntValue(pitchElem, "alter", 0);
            int octave = Integer.parseInt(getTextValue(pitchElem, "octave"));
            String pitch = step;
            if (alter == 1) pitch += "#";
            else if (alter == -1) pitch += "b";
            pitch += octave;

            // Convert duration to Tone.js notation (simple mapping)
            String toneDuration = mapDurationToTone(durationBeats);

            boolean isChord = noteElem.getElementsByTagName("chord").getLength() > 0;
            double startTime = isChord
                    ? lastStartByKey.getOrDefault(timeKey, voiceTime)
                    : voiceTime;

            NoteModel note = new NoteModel(pitch, toneDuration, startTime, staff, voice);
            note.setDurationBeats(durationBeats);
            notes.add(note);

            // Advance time for this voice unless it's a chord tone
            if (!isChord) {
                lastStartByKey.put(timeKey, startTime);
                timeByKey.put(timeKey, voiceTime + durationBeats);
            }
        }
        return notes;
    }

    /**
     * Classify notes into SATB parts based on priority rules.
     */
    public SatbModel classifySatb(List<NoteModel> notes) {
        SatbModel satb = new SatbModel();
        for (NoteModel note : notes) {
            String part = classifyNote(note);
            satb.addNote(part, note);
        }
        return satb;
    }

    /**
     * Extract tempo from MusicXML (look for <sound tempo="..."/>). Default 120.
     */
    public int extractTempo(File xmlFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        NodeList soundNodes = doc.getElementsByTagName("sound");
        for (int i = 0; i < soundNodes.getLength(); i++) {
            Element soundElem = (Element) soundNodes.item(i);
            if (soundElem.hasAttribute("tempo")) {
                try {
                    return Integer.parseInt(soundElem.getAttribute("tempo"));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 120; // default BPM
    }

    /** Helper to classify a single note */
    private String classifyNote(NoteModel note) {
        // 1) Staff based mapping (1=S,2=A,3=T,4=B)
        if (note.getStaff() >= 1 && note.getStaff() <= 4) {
            switch (note.getStaff()) {
                case 1: return "S";
                case 2: return "A";
                case 3: return "T";
                case 4: return "B";
            }
        }
        // 2) Voice based mapping (1=S,2=A,3=T,4=B)
        if (note.getVoice() >= 1 && note.getVoice() <= 4) {
            switch (note.getVoice()) {
                case 1: return "S";
                case 2: return "A";
                case 3: return "T";
                case 4: return "B";
            }
        }
        // 3) Pitch range fallback using MIDI conversion
        int midi = midiFromPitch(note.getPitch());
        if (midi >= 72) return "S"; // C5+ (MIDI 72)
        if (midi >= 60) return "A"; // C4‑B4
        if (midi >= 48) return "T"; // C3‑B3
        return "B"; // below C3
    }

    /** Convert pitch string (e.g., C4, D#5) to MIDI number */
    private int midiFromPitch(String pitch) {
        // Extract step, accidental, octave
        String step = pitch.substring(0, 1);
        int accidental = 0;
        int idx = 1;
        if (pitch.length() > 2 && (pitch.charAt(1) == '#' || pitch.charAt(1) == 'b')) {
            accidental = pitch.charAt(1) == '#' ? 1 : -1;
            idx = 2;
        }
        int octave = Integer.parseInt(pitch.substring(idx));
        int base = switch (step) {
            case "C" -> 0;
            case "D" -> 2;
            case "E" -> 4;
            case "F" -> 5;
            case "G" -> 7;
            case "A" -> 9;
            case "B" -> 11;
            default -> 0;
        };
        int midi = (octave + 1) * 12 + base + accidental; // MIDI note numbers start at C‑1 = 0
        return midi;
    }

    /** Map duration in beats to Tone.js notation, including dotted values */
    private String mapDurationToTone(double beats) {
        // Dotted durations first (more specific)
        if (beats >= 6.0) return "1n.";  // dotted whole
        if (beats >= 4.0) return "1n";
        if (beats >= 3.0) return "2n.";  // dotted half
        if (beats >= 2.0) return "2n";
        if (beats >= 1.5) return "4n.";  // dotted quarter
        if (beats >= 1.0) return "4n";
        if (beats >= 0.75) return "8n."; // dotted eighth
        if (beats >= 0.5) return "8n";
        if (beats >= 0.375) return "16n."; // dotted 16th
        if (beats >= 0.25) return "16n";
        return "32n";
    }

    /** Utility helpers */
    private String getTextValue(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return "";
        return list.item(0).getTextContent().trim();
    }

    private int getIntValue(Element parent, String tag, int defaultVal) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return defaultVal;
        try {
            return Integer.parseInt(list.item(0).getTextContent().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String buildTimeKey(int voice, int staff) {
        if (voice >= 1) {
            return "v:" + voice + ":s:" + staff;
        }
        if (staff >= 1) {
            return "s:" + staff;
        }
        return "g";
    }
}
