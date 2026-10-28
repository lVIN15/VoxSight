package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.util.ScoreValidator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreValidatorTest {

    @Test
    fun acceptsAudiverisGenericVoicePartsWithoutLyrics() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1">
                  <part-name>Voice</part-name>
                  <score-instrument id="P1-I1"><instrument-name>Voice Oohs</instrument-name></score-instrument>
                </score-part>
                <score-part id="P2"><part-name>Voice</part-name></score-part>
                <score-part id="P3"><part-name>Voice</part-name></score-instrument></score-part>
                <score-part id="P4"><part-name>Voice</part-name></score-part>
              </part-list>
              <part id="P1"><measure number="1"><note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure></part>
            </score-partwise>
        """.trimIndent()
        assertNull(ScoreValidator.checkUnsupportedMusicXml(xml))
    }

    @Test
    fun acceptsBackendStampedVocalVoices() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1"><part-name>Piano</part-name></score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <note data-vx-id="note_1" data-vx-voice="S">
                    <pitch><step>G</step><octave>4</octave></pitch><duration>4</duration>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        assertNull(ScoreValidator.checkUnsupportedMusicXml(xml))
    }

    @Test
    fun acceptsAbbreviatedSatbAndSoloistScores() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1"><part-name>Baritone Solo</part-name></score-part>
                <score-part id="P2"><part-name>S.</part-name></score-part>
                <score-part id="P3"><part-name>A.</part-name></score-part>
                <score-part id="P4"><part-name>T.</part-name></score-part>
                <score-part id="P5"><part-name>B.</part-name></score-part>
              </part-list>
              <part id="P1"><measure number="1"/></part>
            </score-partwise>
        """.trimIndent()
        assertNull(ScoreValidator.checkUnsupportedMusicXml(xml))
    }

    @Test
    fun rejectsPurelyInstrumentalPianoSolo() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1">
                  <part-name>Piano</part-name>
                  <part-abbreviation>Pno.</part-abbreviation>
                  <score-instrument id="P1-I1">
                    <instrument-name>Acoustic Grand Piano</instrument-name>
                  </score-instrument>
                </score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <note data-vx-id="note_1" data-vx-voice="Accompaniment">
                    <pitch><step>C</step><octave>4</octave></pitch><duration>4</duration>
                  </note>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()
        assertNotNull(ScoreValidator.checkUnsupportedMusicXml(xml))
    }

    @Test
    fun rejectsPurelyInstrumentalStringEnsembleWithDoubleBass() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1"><part-name>Violin I</part-name></score-part>
                <score-part id="P2"><part-name>Viola</part-name></score-part>
                <score-part id="P3"><part-name>Violoncello</part-name></score-part>
                <score-part id="P4"><part-name>Double Bass</part-name></score-part>
              </part-list>
              <part id="P1"><measure number="1"/></part>
            </score-partwise>
        """.trimIndent()
        assertNotNull(ScoreValidator.checkUnsupportedMusicXml(xml))
    }
}

