package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.pitch.ColorBand
import com.cit.kaido.voxsight.pitch.PitchComparator
import com.cit.kaido.voxsight.pitch.PitchDirection
import com.cit.kaido.voxsight.pitch.FeedbackResult
import com.cit.kaido.voxsight.pitch.PitchAttempt
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.ui.components.NotePitchStatus
import com.cit.kaido.voxsight.ui.components.evaluateMeasureNoteDetails
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure
import com.cit.kaido.voxsight.ui.screens.practice.SessionSummary
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit tests verifying pitch accuracy calculation, acceptable cents tolerance,
 * classification rules (In Tune, Sharp, Flat), and summary consistency.
 */
class PitchAccuracyInterpretationTest {

    @Test
    fun testAcceptableCentsTolerance_symmetric10CentsWindow() {
        val targetHz = 440.0f // A4

        // 1. Exactly on target: 0 cents -> In Tune
        val dev0 = PitchComparator.calculateCentDeviation(targetHz, targetHz)
        assertEquals(0.0f, dev0, 0.01f)
        assertTrue("0 cents must be a match", PitchComparator.isMatch(dev0))

        // 2. +5 cents sharp: within 10 cents tolerance -> In Tune
        val hzPlus5 = (440.0 * 2.0.pow(5.0 / 1200.0)).toFloat()
        val devPlus5 = PitchComparator.calculateCentDeviation(hzPlus5, targetHz)
        assertEquals(5.0f, devPlus5, 0.1f)
        assertTrue("+5 cents must be classified as in tune", PitchComparator.isMatch(devPlus5))

        // 3. -5 cents flat: within 10 cents tolerance -> In Tune
        val hzMinus5 = (440.0 * 2.0.pow(-5.0 / 1200.0)).toFloat()
        val devMinus5 = PitchComparator.calculateCentDeviation(hzMinus5, targetHz)
        assertEquals(-5.0f, devMinus5, 0.1f)
        assertTrue("-5 cents must be classified as in tune", PitchComparator.isMatch(devMinus5))

        // 4. Exactly at +10 cents: on positive boundary -> In Tune
        assertTrue("+10.0 cents must be classified as in tune", PitchComparator.isMatch(10.0f))
        assertTrue("-10.0 cents must be classified as in tune", PitchComparator.isMatch(-10.0f))

        val hzPlus9 = (440.0 * 2.0.pow(9.5 / 1200.0)).toFloat()
        val devPlus9 = PitchComparator.calculateCentDeviation(hzPlus9, targetHz)
        assertTrue("+9.5 cents must be classified as in tune", PitchComparator.isMatch(devPlus9))

        val hzMinus9 = (440.0 * 2.0.pow(-9.5 / 1200.0)).toFloat()
        val devMinus9 = PitchComparator.calculateCentDeviation(hzMinus9, targetHz)
        assertTrue("-9.5 cents must be classified as in tune", PitchComparator.isMatch(devMinus9))

        // 6. +15 cents sharp: exceeds 10 cents tolerance -> Not in tune (Sharp)
        val hzPlus15 = (440.0 * 2.0.pow(15.0 / 1200.0)).toFloat()
        val devPlus15 = PitchComparator.calculateCentDeviation(hzPlus15, targetHz)
        assertEquals(15.0f, devPlus15, 0.1f)
        assertFalse("+15 cents exceeds tolerance", PitchComparator.isMatch(devPlus15))

        // 7. -15 cents flat: exceeds 10 cents tolerance -> Not in tune (Flat)
        val hzMinus15 = (440.0 * 2.0.pow(-15.0 / 1200.0)).toFloat()
        val devMinus15 = PitchComparator.calculateCentDeviation(hzMinus15, targetHz)
        assertEquals(-15.0f, devMinus15, 0.1f)
        assertFalse("-15 cents exceeds tolerance", PitchComparator.isMatch(devMinus15))

        // 8. +20 cents sharp: exceeds tolerance -> Not in tune (Sharp)
        val hzPlus20 = (440.0 * 2.0.pow(20.0 / 1200.0)).toFloat()
        val devPlus20 = PitchComparator.calculateCentDeviation(hzPlus20, targetHz)
        assertEquals(20.0f, devPlus20, 0.1f)
        assertFalse("+20 cents exceeds tolerance", PitchComparator.isMatch(devPlus20))
    }

    @Test
    fun testColorBandClassificationThresholds() {
        // <= 50 cents -> GREEN
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(0f))
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(10f))
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(-10f))
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(30f))
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(-49f))
        assertEquals(ColorBand.GREEN, ColorBand.fromCents(50f))

        // 51 to 100 cents -> YELLOW
        assertEquals(ColorBand.YELLOW, ColorBand.fromCents(51f))
        assertEquals(ColorBand.YELLOW, ColorBand.fromCents(-75f))
        assertEquals(ColorBand.YELLOW, ColorBand.fromCents(100f))

        // > 100 cents -> RED
        assertEquals(ColorBand.RED, ColorBand.fromCents(101f))
        assertEquals(ColorBand.RED, ColorBand.fromCents(-150f))
    }

    @Test
    fun testFeedbackResultDirectionClassification() {
        // Within tolerance -> ON_PITCH
        val matchResult = FeedbackResult(isMatch = true, deviationCents = 8f)
        assertEquals(PitchDirection.ON_PITCH, matchResult.direction)

        // Above tolerance -> SHARP
        val sharpResult = FeedbackResult(isMatch = false, deviationCents = 15f)
        assertEquals(PitchDirection.SHARP, sharpResult.direction)

        // Below tolerance -> FLAT
        val flatResult = FeedbackResult(isMatch = false, deviationCents = -15f)
        assertEquals(PitchDirection.FLAT, flatResult.direction)
    }

    @Test
    fun testPitchAccuracy100PercentWithIntonationTendencyExplanation() {
        // Given a session where user sang 5 notes, and all 5 achieved at least one match
        val totalAttempted = 5
        val correctNotes = 5
        val summary = SessionSummary(
            totalNotesAttempted = totalAttempted,
            correctNotes = correctNotes,
            averageDeviationCents = 12f,
            problematicMeasures = listOf(
                ProblematicMeasure(
                    measureNumber = 12,
                    mistakeCount = 0, // 0 mistakes! Every note was hit!
                    totalNotes = 4,
                    isSharp = true,
                    averageDeviation = 26.5f // Leaned slightly sharp on sustain
                )
            )
        )

        // Accuracy is 100%
        assertEquals(100f, summary.accuracyPercentage, 0.01f)

        // Flagged measures have 0 mistakes
        val flagged = summary.problematicMeasures
        assertEquals(1, flagged.size)
        assertEquals(0, flagged[0].mistakeCount)
        assertTrue("Measure has sharp intonation drift", flagged[0].isSharp)

        // UI distinction logic:
        val allMeasuresFlawless = flagged.all { it.mistakeCount == 0 } && summary.accuracyPercentage >= 100f
        assertTrue("Summary must detect flawless discrete accuracy with intonation drift", allMeasuresFlawless)

        val sectionTitle = if (allMeasuresFlawless) "Intonation Focus Areas" else "Top Area to Practice"
        assertEquals("Intonation Focus Areas", sectionTitle)
    }

    @Test
    fun testPitchAccuracyLessThan100PercentShowsTopAreaToPractice() {
        val summary = SessionSummary(
            totalNotesAttempted = 10,
            correctNotes = 8,
            averageDeviationCents = 35f,
            problematicMeasures = listOf(
                ProblematicMeasure(
                    measureNumber = 4,
                    mistakeCount = 2, // 2 notes were never matched
                    totalNotes = 4,
                    isSharp = false,
                    averageDeviation = -38f
                )
            )
        )

        assertEquals(80f, summary.accuracyPercentage, 0.01f)
        val flagged = summary.problematicMeasures
        val allMeasuresFlawless = flagged.all { it.mistakeCount == 0 } && summary.accuracyPercentage >= 100f
        assertFalse("Cannot be flawless when mistakes exist", allMeasuresFlawless)

        val sectionTitle = if (allMeasuresFlawless) "Intonation Focus Areas" else "Top Area to Practice"
        assertEquals("Top Area to Practice", sectionTitle)
    }

    @Test
    fun testEvaluateMeasureNoteDetails_noteSungSharp() {
        val note = MusicXmlNote(
            step = "C",
            octave = 4,
            durationDivisions = 480,
            startTimeDivisions = 0,
            measureNumber = 8
        )
        val attempt = PitchAttempt(
            eventId = "t0-p1-c0",
            targetHz = 261.63f,
            detectedHz = 267.0f,
            deviationCents = 35.0f,
            isMatch = false,
            timestampMs = 1000L,
            noteName = "C4",
            measureNumber = 8,
            satbVoice = SATBVoice.SOPRANO
        )

        val details = evaluateMeasureNoteDetails(
            filteredNotes = listOf(note),
            measureNumber = 8,
            pitchAttempts = listOf(attempt)
        )

        assertEquals(1, details.size)
        val d = details[0]
        assertEquals(1, d.noteIndex)
        assertEquals("C4", d.noteName)
        assertEquals(NotePitchStatus.SUNG_SHARP, d.status)
        assertEquals("Sung Sharp", d.statusLabel)
        assertTrue("Note sung sharp must be flagged", d.isFlagged)
        assertNotNull(d.deviationCents)
        assertEquals(35.0f, d.deviationCents!!, 0.1f)
    }

    @Test
    fun testEvaluateMeasureNoteDetails_noteSungFlat() {
        val note = MusicXmlNote(
            step = "D",
            octave = 4,
            durationDivisions = 480,
            startTimeDivisions = 480,
            measureNumber = 8
        )
        val attempt = PitchAttempt(
            eventId = "t480-p1-c0",
            targetHz = 293.66f,
            detectedHz = 286.5f,
            deviationCents = -42.0f,
            isMatch = false,
            timestampMs = 1500L,
            noteName = "D4",
            measureNumber = 8,
            satbVoice = SATBVoice.SOPRANO
        )

        val details = evaluateMeasureNoteDetails(
            filteredNotes = listOf(note),
            measureNumber = 8,
            pitchAttempts = listOf(attempt)
        )

        assertEquals(1, details.size)
        val d = details[0]
        assertEquals(1, d.noteIndex)
        assertEquals("D4", d.noteName)
        assertEquals(NotePitchStatus.SUNG_FLAT, d.status)
        assertEquals("Sung Flat", d.statusLabel)
        assertTrue("Note sung flat must be flagged", d.isFlagged)
        assertNotNull(d.deviationCents)
        assertEquals(-42.0f, d.deviationCents!!, 0.1f)
    }

    @Test
    fun testEvaluateMeasureNoteDetails_noteNotSung() {
        val note = MusicXmlNote(
            step = "E",
            octave = 4,
            durationDivisions = 480,
            startTimeDivisions = 960,
            measureNumber = 8
        )

        // No pitch attempts recorded for this note/measure
        val details = evaluateMeasureNoteDetails(
            filteredNotes = listOf(note),
            measureNumber = 8,
            pitchAttempts = emptyList()
        )

        assertEquals(1, details.size)
        val d = details[0]
        assertEquals(1, d.noteIndex)
        assertEquals("E4", d.noteName)
        assertEquals(NotePitchStatus.NOT_SUNG, d.status)
        assertEquals("Not Sung", d.statusLabel)
        assertTrue("Unattempted note must be flagged as Not Sung", d.isFlagged)
        assertNull("Not Sung note has no cents value", d.deviationCents)
    }

    @Test
    fun testEvaluateMeasureNoteDetails_noteInTune() {
        val note = MusicXmlNote(
            step = "F",
            octave = 4,
            durationDivisions = 480,
            startTimeDivisions = 1440,
            measureNumber = 8
        )
        val attempt = PitchAttempt(
            eventId = "t1440-p1-c0",
            targetHz = 349.23f,
            detectedHz = 350.0f,
            deviationCents = 3.8f,
            isMatch = true,
            timestampMs = 2000L,
            noteName = "F4",
            measureNumber = 8,
            satbVoice = SATBVoice.SOPRANO
        )

        val details = evaluateMeasureNoteDetails(
            filteredNotes = listOf(note),
            measureNumber = 8,
            pitchAttempts = listOf(attempt)
        )

        assertEquals(1, details.size)
        val d = details[0]
        assertEquals(1, d.noteIndex)
        assertEquals("F4", d.noteName)
        assertEquals(NotePitchStatus.IN_TUNE, d.status)
        assertEquals("In Tune", d.statusLabel)
        assertFalse("In Tune note is not flagged for correction", d.isFlagged)
        assertNotNull(d.deviationCents)
        assertEquals(3.8f, d.deviationCents!!, 0.1f)
    }

    @Test
    fun testEvaluateMeasureNoteDetails_mixedMeasureWithMultipleFlaggedNotes() {
        // Measure 10 with 4 pitched notes and 1 rest:
        // Note 1: G4 - Sung Flat (-38¢)
        // Note 2: A4 - Sung Sharp (+32¢)
        // Note 3: B4 - Not Sung
        // Rest: whole rest (must be excluded from pitched note index numbering)
        // Note 4: C5 - In Tune (+2¢)
        val notes = listOf(
            MusicXmlNote(step = "G", octave = 4, durationDivisions = 240, startTimeDivisions = 0, measureNumber = 10, lyric = "Pu-"),
            MusicXmlNote(step = "A", octave = 4, durationDivisions = 240, startTimeDivisions = 240, measureNumber = 10, lyric = "ri-"),
            MusicXmlNote(step = "B", octave = 4, durationDivisions = 240, startTimeDivisions = 480, measureNumber = 10, lyric = "hin"),
            MusicXmlNote(step = "C", octave = 4, durationDivisions = 240, startTimeDivisions = 720, measureNumber = 10, isRest = true),
            MusicXmlNote(step = "C", octave = 5, durationDivisions = 240, startTimeDivisions = 960, measureNumber = 10, lyric = "Diyos")
        )

        val attempts = listOf(
            // G4 attempt (flat)
            PitchAttempt(eventId = "t0-p1-c0", targetHz = 392.0f, detectedHz = 383.5f, deviationCents = -38.0f, isMatch = false, timestampMs = 1000L, noteName = "G4", measureNumber = 10),
            // A4 attempt (sharp)
            PitchAttempt(eventId = "t240-p1-c1", targetHz = 440.0f, detectedHz = 448.2f, deviationCents = 32.0f, isMatch = false, timestampMs = 1250L, noteName = "A4", measureNumber = 10),
            // B4: no attempt (not sung)
            // C5 attempt (in tune)
            PitchAttempt(eventId = "t960-p1-c3", targetHz = 523.25f, detectedHz = 523.8f, deviationCents = 2.0f, isMatch = true, timestampMs = 2000L, noteName = "C5", measureNumber = 10)
        )

        val details = evaluateMeasureNoteDetails(
            filteredNotes = notes,
            measureNumber = 10,
            pitchAttempts = attempts
        )

        // Rests must be ignored in pitched note details
        assertEquals("Must evaluate exactly 4 pitched notes", 4, details.size)

        // Note 1: G4 -> Sung Flat
        val n1 = details[0]
        assertEquals(1, n1.noteIndex)
        assertEquals("G4", n1.noteName)
        assertEquals("Pu-", n1.lyric)
        assertEquals(NotePitchStatus.SUNG_FLAT, n1.status)
        assertEquals("Sung Flat", n1.statusLabel)
        assertTrue(n1.isFlagged)
        assertEquals(-38.0f, n1.deviationCents!!, 0.1f)

        // Note 2: A4 -> Sung Sharp
        val n2 = details[1]
        assertEquals(2, n2.noteIndex)
        assertEquals("A4", n2.noteName)
        assertEquals("ri-", n2.lyric)
        assertEquals(NotePitchStatus.SUNG_SHARP, n2.status)
        assertEquals("Sung Sharp", n2.statusLabel)
        assertTrue(n2.isFlagged)
        assertEquals(32.0f, n2.deviationCents!!, 0.1f)

        // Note 3: B4 -> Not Sung
        val n3 = details[2]
        assertEquals(3, n3.noteIndex)
        assertEquals("B4", n3.noteName)
        assertEquals("hin", n3.lyric)
        assertEquals(NotePitchStatus.NOT_SUNG, n3.status)
        assertEquals("Not Sung", n3.statusLabel)
        assertTrue(n3.isFlagged)
        assertNull(n3.deviationCents)

        // Note 4: C5 -> In Tune
        val n4 = details[3]
        assertEquals(4, n4.noteIndex)
        assertEquals("C5", n4.noteName)
        assertEquals("Diyos", n4.lyric)
        assertEquals(NotePitchStatus.IN_TUNE, n4.status)
        assertEquals("In Tune", n4.statusLabel)
        assertFalse(n4.isFlagged)
        assertEquals(2.0f, n4.deviationCents!!, 0.1f)

        // Exactly 3 flagged notes out of 4 (zero double counting)
        val flagged = details.filter { it.isFlagged }
        assertEquals(3, flagged.size)
        assertEquals(listOf(1, 2, 3), flagged.map { it.noteIndex })
    }

    @Test
    fun testEvaluateMeasureNoteDetails_measureIsolation() {
        val noteM8 = MusicXmlNote(step = "C", octave = 4, durationDivisions = 480, startTimeDivisions = 0, measureNumber = 8)
        val attemptM5 = PitchAttempt(eventId = "t0-p1-c0", targetHz = 261.63f, detectedHz = 250.0f, deviationCents = -78.0f, isMatch = false, timestampMs = 500L, noteName = "C4", measureNumber = 5)

        val details = evaluateMeasureNoteDetails(
            filteredNotes = listOf(noteM8),
            measureNumber = 8,
            pitchAttempts = listOf(attemptM5)
        )

        assertEquals(1, details.size)
        // Measure 5 attempt must NOT apply to Measure 8 note; note 8 is Not Sung
        assertEquals(NotePitchStatus.NOT_SUNG, details[0].status)
    }
}
