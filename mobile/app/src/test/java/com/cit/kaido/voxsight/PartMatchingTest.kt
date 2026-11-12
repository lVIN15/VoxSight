package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.ui.screens.practice.VoicePart
import com.cit.kaido.voxsight.ui.screens.practice.isEventTargetForSinger
import com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart
import com.cit.kaido.voxsight.ui.screens.practice.getDisplayLabel
import com.cit.kaido.voxsight.ui.screens.practice.getDisplayShortLabel
import com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlMeasure
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.computeUniversalVocalPartMap
import com.cit.kaido.voxsight.ui.screens.practice.generateEventsJsonFromScoreParts
import com.cit.kaido.voxsight.ui.screens.practice.reconcileEventsWithScoreParts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class PartMatchingTest {

    @Test
    fun testParentSectionMatchS1AndS2() {
        val eventS1 = MusicalEvent(
            eventId = "e1", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("S1"), rawIsVocal = true
        )
        val eventS2 = MusicalEvent(
            eventId = "e2", measureNumber = 1, tickPosition = 0, pitchMidi = 64, pitchName = "E4", durationTicks = 480,
            rawParticipatingParts = listOf("S2"), rawIsVocal = true
        )
        // Soprano singer should target both S1 and S2 divisi lines
        assertTrue(isEventTargetForSinger(VoicePart.Soprano, eventS1))
        assertTrue(isEventTargetForSinger(VoicePart.Soprano, eventS2))

        // Alto singer should NOT target S1 or S2
        assertFalse(isEventTargetForSinger(VoicePart.Alto, eventS1))
        assertFalse(isEventTargetForSinger(VoicePart.Alto, eventS2))
    }

    @Test
    fun testCrossSectionUnisonBothSingersTarget() {
        val eventTB = MusicalEvent(
            eventId = "e3", measureNumber = 1, tickPosition = 0, pitchMidi = 50, pitchName = "D3", durationTicks = 480,
            rawParticipatingParts = listOf("T", "B"), rawIsVocal = true
        )
        // Both Tenor and Bass sing on cross-section unison TB
        assertTrue(isEventTargetForSinger(VoicePart.Tenor, eventTB))
        assertTrue(isEventTargetForSinger(VoicePart.Bass, eventTB))

        // Soprano and Alto should NOT target TB unison
        assertFalse(isEventTargetForSinger(VoicePart.Soprano, eventTB))
        assertFalse(isEventTargetForSinger(VoicePart.Alto, eventTB))
    }

    @Test
    fun testInstrumentalAccompanimentExcludedFromAllSingers() {
        val eventPiano = MusicalEvent(
            eventId = "e4", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("PIANO"), rawIsVocal = false
        )
        assertFalse(isEventTargetForSinger(VoicePart.Soprano, eventPiano))
        assertFalse(isEventTargetForSinger(VoicePart.Alto, eventPiano))
        assertFalse(isEventTargetForSinger(VoicePart.Tenor, eventPiano))
        assertFalse(isEventTargetForSinger(VoicePart.Bass, eventPiano))
        assertFalse(isEventTargetForSinger(VoicePart.Solo, eventPiano))
        assertFalse(isEventTargetForSinger(VoicePart.Others, eventPiano))
    }

    @Test
    fun testUniversalChoralUnisonAllSingersTarget() {
        val eventAll = MusicalEvent(
            eventId = "e5", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("ALL"), rawIsVocal = true
        )
        assertTrue(isEventTargetForSinger(VoicePart.Soprano, eventAll))
        assertTrue(isEventTargetForSinger(VoicePart.Alto, eventAll))
        assertTrue(isEventTargetForSinger(VoicePart.Tenor, eventAll))
        assertTrue(isEventTargetForSinger(VoicePart.Bass, eventAll))
        assertTrue(isEventTargetForSinger(VoicePart.Solo, eventAll))
        assertFalse(isEventTargetForSinger(VoicePart.Others, eventAll))
    }

    @Test
    fun testSoloMatchAndRejection() {
        val eventSolo = MusicalEvent(
            eventId = "e6", measureNumber = 1, tickPosition = 0, pitchMidi = 65, pitchName = "F4", durationTicks = 480,
            rawParticipatingParts = listOf("SOLO"), rawIsVocal = true
        )
        assertTrue(isEventTargetForSinger(VoicePart.Solo, eventSolo))
        assertFalse(isEventTargetForSinger(VoicePart.Soprano, eventSolo))
        assertFalse(isEventTargetForSinger(VoicePart.Alto, eventSolo))
        assertFalse(isEventTargetForSinger(VoicePart.Tenor, eventSolo))
        assertFalse(isEventTargetForSinger(VoicePart.Bass, eventSolo))
    }

    @Test
    fun testContaminatedListFailsSafe() {
        val eventContaminated = MusicalEvent(
            eventId = "e7", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("S", "GUITAR"), rawIsVocal = true
        )
        assertFalse(isEventTargetForSinger(VoicePart.Soprano, eventContaminated))
    }

    @Test
    fun testAudioFocusIsolationBetweenDivisiParts() {
        val engine = com.cit.kaido.voxsight.playback.NativePlaybackEngine(null)

        // 1. Audio Focus on Bass 1 ("B1")
        engine.setFocusedVoicePart("B1")
        assertTrue("Bass 1 notes must sound when B1 is focused", engine.isVocalEventActiveInAudio(listOf("B1")))
        assertFalse("Bass 2 notes MUST BE MUTED when B1 is focused", engine.isVocalEventActiveInAudio(listOf("B2")))
        assertTrue("Undivided Bass notes (mm. 1-7) must sound for B1", engine.isVocalEventActiveInAudio(listOf("B")))
        assertTrue("Unison B1B2 notes must sound for B1", engine.isVocalEventActiveInAudio(listOf("B1", "B2")))
        assertTrue("Universal Tutti must sound for B1", engine.isVocalEventActiveInAudio(listOf("ALL")))
        assertTrue("Cross-section unison TB must sound for B1", engine.isVocalEventActiveInAudio(listOf("T", "B")))
        assertFalse("Soprano notes must be muted for B1", engine.isVocalEventActiveInAudio(listOf("S")))
        assertFalse("Alto notes must be muted for B1", engine.isVocalEventActiveInAudio(listOf("A")))
        assertFalse("Tenor notes must be muted for B1", engine.isVocalEventActiveInAudio(listOf("T")))

        // 2. Audio Focus on Bass 2 ("B2")
        engine.setFocusedVoicePart("B2")
        assertFalse("Bass 1 notes MUST BE MUTED when B2 is focused", engine.isVocalEventActiveInAudio(listOf("B1")))
        assertTrue("Bass 2 notes must sound when B2 is focused", engine.isVocalEventActiveInAudio(listOf("B2")))
        assertTrue("Undivided Bass notes (mm. 1-7) must sound for B2", engine.isVocalEventActiveInAudio(listOf("B")))
        assertTrue("Unison B1B2 notes must sound for B2", engine.isVocalEventActiveInAudio(listOf("B1", "B2")))
        assertTrue("Universal Tutti must sound for B2", engine.isVocalEventActiveInAudio(listOf("ALL")))
        assertFalse("Soprano notes must be muted for B2", engine.isVocalEventActiveInAudio(listOf("S")))

        // 3. Audio Focus on Parent Bass Group ("B" / Bass All)
        engine.setFocusedVoicePart("B")
        assertTrue("Parent Bass must intentionally include Bass 1 notes", engine.isVocalEventActiveInAudio(listOf("B1")))
        assertTrue("Parent Bass must intentionally include Bass 2 notes", engine.isVocalEventActiveInAudio(listOf("B2")))
        assertTrue("Parent Bass must include undivided Bass notes", engine.isVocalEventActiveInAudio(listOf("B")))
        assertFalse("Parent Bass must mute Soprano notes", engine.isVocalEventActiveInAudio(listOf("S")))
        assertFalse("Parent Bass must mute Alto notes", engine.isVocalEventActiveInAudio(listOf("A")))

        // 4. Audio Focus Disabled (null)
        engine.setFocusedVoicePart(null)
        assertTrue("All vocal parts active when focus is null", engine.isVocalEventActiveInAudio(listOf("B1")))
        assertTrue("All vocal parts active when focus is null", engine.isVocalEventActiveInAudio(listOf("B2")))
        assertTrue("All vocal parts active when focus is null", engine.isVocalEventActiveInAudio(listOf("S")))
    }

    @Test
    fun testDetectAvailablePartsNoDivisiScore() {
        val scoreNoDivisi = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Ode to Joy",
            composer = "Beethoven",
            notes = emptyList(),
            parts = listOf(
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(1, "Soprano", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(2, "Alto", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(3, "Tenor", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(4, "Bass", emptyList())
            ),
            totalSeconds = 10
        )
        val parts = com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts(scoreNoDivisi)

        // Must display standard SATB
        assertEquals(listOf(VoicePart.Soprano, VoicePart.Alto, VoicePart.Tenor, VoicePart.Bass), parts)
        // Must NOT display any divisi buttons
        assertFalse(parts.contains(VoicePart.Bass1))
        assertFalse(parts.contains(VoicePart.Bass2))
        assertFalse(parts.contains(VoicePart.Soprano1))
        assertFalse(parts.contains(VoicePart.Soprano2))

        // Parent label must be standard "Bass" when undivided
        assertEquals("Bass", VoicePart.Bass.getDisplayLabel(parts))
        assertEquals("B.", VoicePart.Bass.getDisplayShortLabel(parts))
    }

    @Test
    fun testDetectAvailablePartsWithDivisiScore() {
        val scoreHbd = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Happy Birthday",
            composer = "Traditional",
            notes = emptyList(),
            parts = listOf(
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(1, "Soprano", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(2, "Alto", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(3, "Tenor", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(4, "Bass 1", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(5, "Bass 2", emptyList())
            ),
            totalSeconds = 10
        )
        val parts = com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts(scoreHbd)

        // Must contain Bass 1 and Bass 2, and omit the redundant parent Bass
        assertFalse(parts.contains(VoicePart.Bass))
        assertTrue(parts.contains(VoicePart.Bass1))
        assertTrue(parts.contains(VoicePart.Bass2))

        assertEquals("Bass 1", VoicePart.Bass1.getDisplayLabel(parts))
        assertEquals("Bass 2", VoicePart.Bass2.getDisplayLabel(parts))
    }

    @Test
    fun testDetectAvailablePartsSingleSubdivisionAnomalySafelyNormalizes() {
        // If a score has a solitary "Bass 1" part with no "Bass 2", it is not a genuine divisi section
        val scoreAnomaly = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Solo Bass Part",
            composer = "Composer",
            notes = emptyList(),
            parts = listOf(
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(1, "Bass 1", emptyList())
            ),
            totalSeconds = 10
        )
        val parts = com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts(scoreAnomaly)

        // Safely normalizes to single parent Bass without creating confusing single divisi button
        assertEquals(listOf(VoicePart.Bass), parts)
        assertFalse(parts.contains(VoicePart.Bass1))
        assertFalse(parts.contains(VoicePart.Bass2))
        assertEquals("Bass", VoicePart.Bass.getDisplayLabel(parts))
    }

    @Test
    fun testScoreSwitchingUpdatesInterfaceCleanly() {
        val scoreA = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Standard Choral",
            composer = "Composer A",
            notes = emptyList(),
            parts = listOf(
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(1, "Soprano", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(2, "Alto", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(3, "Tenor", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(4, "Bass", emptyList())
            ),
            totalSeconds = 10
        )
        val scoreB = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Divisi Choral",
            composer = "Composer B",
            notes = emptyList(),
            parts = listOf(
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(1, "Soprano 1", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(2, "Soprano 2", emptyList()),
                com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart(3, "Bass", emptyList())
            ),
            totalSeconds = 10
        )

        val partsA = com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts(scoreA)
        val partsB = com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts(scoreB)

        // Score A has no divisi
        assertFalse(partsA.contains(VoicePart.Soprano1))
        assertFalse(partsA.contains(VoicePart.Soprano2))

        // Score B has Soprano divisi: subdivisions replace redundant parent Soprano
        assertFalse(partsB.contains(VoicePart.Soprano))
        assertTrue(partsB.contains(VoicePart.Soprano1))
        assertTrue(partsB.contains(VoicePart.Soprano2))
        assertTrue(partsB.contains(VoicePart.Bass))
    }

    @Test
    fun testVisualFocusPlaybackHighlightDivisiIsolation() {
        val eventBassUndivided = MusicalEvent(
            eventId = "e_bass_m1", measureNumber = 1, tickPosition = 0, pitchMidi = 48, pitchName = "C3", durationTicks = 480,
            rawParticipatingParts = listOf("B"), rawIsVocal = true
        )
        val eventBass1 = MusicalEvent(
            eventId = "e_b1_m8", measureNumber = 8, tickPosition = 3360, pitchMidi = 50, pitchName = "D3", durationTicks = 480,
            rawParticipatingParts = listOf("B1"), rawIsVocal = true
        )
        val eventBass2 = MusicalEvent(
            eventId = "e_b2_m8", measureNumber = 8, tickPosition = 3360, pitchMidi = 43, pitchName = "G2", durationTicks = 480,
            rawParticipatingParts = listOf("B2"), rawIsVocal = true
        )
        val eventTenor = MusicalEvent(
            eventId = "e_tenor_m1", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("T"), rawIsVocal = true
        )

        // 1. When Visual Focus is on Bass 1 ("B1")
        // Undivided Bass (mm. 1-7) must highlight
        assertTrue("Undivided Bass notes must highlight when B1 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B1", eventBassUndivided))
        // Bass 1 note (m. 8) must highlight
        assertTrue("Bass 1 note must highlight when B1 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B1", eventBass1))
        // Bass 2 note (m. 8) MUST NOT highlight
        assertFalse("Bass 2 note MUST NOT highlight when B1 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B1", eventBass2))
        // Tenor note MUST NOT highlight
        assertFalse("Tenor note must not highlight when B1 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B1", eventTenor))

        // 2. When Visual Focus is on Bass 2 ("B2")
        // Undivided Bass (mm. 1-7) must highlight
        assertTrue("Undivided Bass notes must highlight when B2 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B2", eventBassUndivided))
        // Bass 2 note (m. 8) must highlight
        assertTrue("Bass 2 note must highlight when B2 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B2", eventBass2))
        // Bass 1 note (m. 8) MUST NOT highlight
        assertFalse("Bass 1 note MUST NOT highlight when B2 is visually focused",
            com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart("B2", eventBass1))

        // 3. When Visual Focus is null (all parts active)
        assertTrue(com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart(null, eventBassUndivided))
        assertTrue(com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart(null, eventBass1))
        assertTrue(com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart(null, eventBass2))
        assertTrue(com.cit.kaido.voxsight.ui.screens.practice.isEventMatchingFocusOrPart(null, eventTenor))
    }

    @Test
    fun testVisualFocusHighlightColorsForDivisi() {
        val eventBassUndivided = MusicalEvent(
            eventId = "e_bass_m1", measureNumber = 1, tickPosition = 0, pitchMidi = 48, pitchName = "C3", durationTicks = 480,
            rawParticipatingParts = listOf("B"), rawIsVocal = true
        )
        val eventBass1 = MusicalEvent(
            eventId = "e_b1_m8", measureNumber = 8, tickPosition = 3360, pitchMidi = 50, pitchName = "D3", durationTicks = 480,
            rawParticipatingParts = listOf("B1"), rawIsVocal = true
        )
        val eventBass2 = MusicalEvent(
            eventId = "e_b2_m8", measureNumber = 8, tickPosition = 3360, pitchMidi = 43, pitchName = "G2", durationTicks = 480,
            rawParticipatingParts = listOf("B2"), rawIsVocal = true
        )

        // Bass 1 color is #65A30D
        assertEquals("#65A30D", com.cit.kaido.voxsight.ui.screens.practice.getHighlightColorForEvent(eventBass1, "B1"))
        // Bass 2 color is #064E3B
        assertEquals("#064E3B", com.cit.kaido.voxsight.ui.screens.practice.getHighlightColorForEvent(eventBass2, "B2"))
        // Undivided Bass under B1 focus inherits B1 theme color for visual clarity
        assertEquals("#65A30D", com.cit.kaido.voxsight.ui.screens.practice.getHighlightColorForEvent(eventBassUndivided, "B1"))
        // Undivided Bass with no focus uses standard Bass green
        assertEquals("#16A34A", com.cit.kaido.voxsight.ui.screens.practice.getHighlightColorForEvent(eventBassUndivided, null))
    }

    private fun syntheticMeasureNotes(
        count: Int,
        step: String,
        octave: Int,
        startMeasure: Int = 0
    ): List<MusicXmlNote> {
        return (0 until count).map { idx ->
            MusicXmlNote(
                step = step,
                octave = octave,
                durationDivisions = 480,
                startTimeDivisions = (startMeasure + idx) * 1920,
                measureNumber = startMeasure + idx + 1,
                measureIndex = startMeasure + idx
            )
        }
    }

    @Test
    fun testUniversalVocalPartMapFivePartGenericVoiceSoloPlusSatb() {
        // Mirrors raw Audiveris OMR output for 5-stave Solo + SATB scores (e.g. Awit ng Paghahangad)
        // where all staves have generic "Voice Oohs" names and 0 lyrics.
        val partNames = mapOf(
            1 to "Voice Oohs",
            2 to "Voice Oohs",
            3 to "Voice Oohs",
            4 to "Voice Oohs",
            5 to "Voice Oohs"
        )
        val partNotes = mapOf(
            1 to syntheticMeasureNotes(count = 15, step = "D", octave = 4), // Sparse soloist above choir
            2 to syntheticMeasureNotes(count = 82, step = "F", octave = 4), // Soprano (G clef, ~65)
            3 to syntheticMeasureNotes(count = 84, step = "C", octave = 4), // Alto (G clef, ~60)
            4 to syntheticMeasureNotes(count = 81, step = "G", octave = 3), // Tenor (F clef, ~55)
            5 to syntheticMeasureNotes(count = 83, step = "C", octave = 3)  // Bass (F clef, ~48)
        )
        val hasFClef = mapOf(1 to false, 2 to false, 3 to false, 4 to true, 5 to true)

        val roles = computeUniversalVocalPartMap(
            totalParts = 5,
            partNamesMap = partNames,
            partNotesMap = partNotes,
            partHasFClefMap = hasFClef
        )

        assertEquals("SOLO", roles[1]?.roleStr)
        assertEquals("SOPRANO", roles[2]?.roleStr)
        assertEquals("ALTO", roles[3]?.roleStr)
        assertEquals("TENOR", roles[4]?.roleStr)
        assertEquals("BASS", roles[5]?.roleStr)
    }

    @Test
    fun testUniversalVocalPartMapDualFClefTenorAboveBass() {
        val partNames = mapOf(1 to "Voice", 2 to "Voice", 3 to "Voice", 4 to "Voice")
        val partNotes = mapOf(
            1 to syntheticMeasureNotes(count = 16, step = "G", octave = 4), // Soprano
            2 to syntheticMeasureNotes(count = 16, step = "D", octave = 4), // Alto
            3 to syntheticMeasureNotes(count = 16, step = "A", octave = 3), // Tenor (MIDI 57)
            4 to syntheticMeasureNotes(count = 16, step = "D", octave = 3)  // Bass (MIDI 50)
        )
        val hasFClef = mapOf(1 to false, 2 to false, 3 to true, 4 to true)

        val roles = computeUniversalVocalPartMap(
            totalParts = 4,
            partNamesMap = partNames,
            partNotesMap = partNotes,
            partHasFClefMap = hasFClef
        )

        assertEquals("SOPRANO", roles[1]?.roleStr)
        assertEquals("ALTO", roles[2]?.roleStr)
        assertEquals("TENOR", roles[3]?.roleStr)
        assertEquals("BASS", roles[4]?.roleStr)
    }

    @Test
    fun testUniversalVocalPartMapFivePartSsatbMotetNotMisclassifiedAsSolo() {
        val partNames = (1..5).associateWith { "Voice" }
        val partNotes = mapOf(
            1 to syntheticMeasureNotes(count = 40, step = "A", octave = 4), // S1
            2 to syntheticMeasureNotes(count = 40, step = "F", octave = 4), // S2
            3 to syntheticMeasureNotes(count = 40, step = "C", octave = 4), // A
            4 to syntheticMeasureNotes(count = 40, step = "G", octave = 3), // T
            5 to syntheticMeasureNotes(count = 40, step = "C", octave = 3)  // B
        )
        val hasFClef = mapOf(1 to false, 2 to false, 3 to false, 4 to true, 5 to true)

        val roles = computeUniversalVocalPartMap(
            totalParts = 5,
            partNamesMap = partNames,
            partNotesMap = partNotes,
            partHasFClefMap = hasFClef
        )

        assertTrue("No stave in full SSATB motet should be SOLO", roles.values.none { it.roleStr == "SOLO" })
        assertEquals("TENOR", roles[4]?.roleStr)
        assertEquals("BASS", roles[5]?.roleStr)
    }

    @Test
    fun testGenerateEventsJsonPreservesSourceNoteIdAndInStaffDivisi() {
        val altoDivTop = MusicXmlNote(
            step = "E", octave = 4, durationDivisions = 480, startTimeDivisions = 0,
            voice = 2, measureNumber = 1, measureIndex = 0, sourceNoteId = 201
        )
        val altoDivBottom = MusicXmlNote(
            step = "C", octave = 4, durationDivisions = 480, startTimeDivisions = 0,
            voice = 2, isChord = true, measureNumber = 1, measureIndex = 0, sourceNoteId = 202
        )
        val parts = listOf(
            MusicXmlPart(1, "Soprano", listOf(MusicXmlNote("G", 4, 480, 0, voice = 1, sourceNoteId = 101)), listOf(MusicXmlMeasure(1, emptyList()))),
            MusicXmlPart(2, "Alto", listOf(altoDivTop, altoDivBottom), listOf(MusicXmlMeasure(1, emptyList()))),
            MusicXmlPart(3, "Tenor", listOf(MusicXmlNote("G", 3, 480, 0, voice = 3, sourceNoteId = 301)), listOf(MusicXmlMeasure(1, emptyList()))),
            MusicXmlPart(4, "Bass", listOf(MusicXmlNote("C", 3, 480, 0, voice = 4, sourceNoteId = 401)), listOf(MusicXmlMeasure(1, emptyList())))
        )

        val json = generateEventsJsonFromScoreParts(parts, tpq = 480)
        val type = object : TypeToken<List<MusicalEvent>>() {}.type
        val events: List<MusicalEvent> = Gson().fromJson(json, type)

        val e201 = events.first { it.sourceNoteId == 201 }
        val e202 = events.first { it.sourceNoteId == 202 }
        assertEquals("ALTO 1", e201.satbVoice)
        assertEquals(listOf("A1"), e201.participatingParts)
        assertEquals("ALTO 2", e202.satbVoice)
        assertEquals(listOf("A2"), e202.participatingParts)
    }

    @Test
    fun testSoloAndBaritoneSoloPitchTestScoringAndExclusion() {
        val baritoneSoloEvent = MusicalEvent(
            eventId = "solo_1", measureNumber = 2, tickPosition = 480, pitchMidi = 55, pitchName = "G3", durationTicks = 480,
            satbVoice = "Baritone Solo", rawParticipatingParts = listOf("BARITONE SOLO"), rawIsVocal = true
        )
        val legacyBaritoneSoloEvent = MusicalEvent(
            eventId = "solo_2", measureNumber = 3, tickPosition = 960, pitchMidi = 57, pitchName = "A3", durationTicks = 480,
            satbVoice = "BARITONE SOLO"
        )
        val sopranoSoloEvent = MusicalEvent(
            eventId = "solo_3", measureNumber = 4, tickPosition = 1440, pitchMidi = 69, pitchName = "A4", durationTicks = 480,
            satbVoice = "Soprano Solo", rawParticipatingParts = listOf("SOPRANO SOLO"), rawIsVocal = true
        )
        val sopranoChoirEvent = MusicalEvent(
            eventId = "satb_s", measureNumber = 6, tickPosition = 2400, pitchMidi = 67, pitchName = "G4", durationTicks = 480,
            satbVoice = "SOPRANO", rawParticipatingParts = listOf("S"), rawIsVocal = true
        )
        val bassChoirEvent = MusicalEvent(
            eventId = "satb_b", measureNumber = 6, tickPosition = 2400, pitchMidi = 48, pitchName = "C3", durationTicks = 480,
            satbVoice = "BASS", rawParticipatingParts = listOf("B"), rawIsVocal = true
        )

        // 1. Verify Solo events are recognized as vocal and SATBVoice.SOLO
        assertTrue(baritoneSoloEvent.isVocal)
        assertTrue(legacyBaritoneSoloEvent.isVocal)
        assertTrue(sopranoSoloEvent.isVocal)
        assertEquals(SATBVoice.SOLO, baritoneSoloEvent.satbEnum)
        assertEquals(SATBVoice.SOLO, legacyBaritoneSoloEvent.satbEnum)
        assertEquals(SATBVoice.SOLO, sopranoSoloEvent.satbEnum)

        // 2. Pitch Test with Solo or BaritoneSolo selected targets all Solo events and rejects SATB choir events
        assertTrue(isEventTargetForSinger(VoicePart.Solo, baritoneSoloEvent))
        assertTrue(isEventTargetForSinger(VoicePart.Solo, legacyBaritoneSoloEvent))
        assertTrue(isEventTargetForSinger(VoicePart.Solo, sopranoSoloEvent))
        assertTrue(isEventTargetForSinger(VoicePart.BaritoneSolo, baritoneSoloEvent))
        assertTrue(isEventTargetForSinger(VoicePart.BaritoneSolo, legacyBaritoneSoloEvent))
        assertFalse(isEventTargetForSinger(VoicePart.Solo, sopranoChoirEvent))
        assertFalse(isEventTargetForSinger(VoicePart.Solo, bassChoirEvent))
        assertFalse(isEventTargetForSinger(VoicePart.BaritoneSolo, sopranoChoirEvent))
        assertFalse(isEventTargetForSinger(VoicePart.BaritoneSolo, bassChoirEvent))

        // 3. Pitch Test with Soprano, Alto, Tenor, or Bass selected rejects Solo events
        for (part in listOf(VoicePart.Soprano, VoicePart.Alto, VoicePart.Tenor, VoicePart.Bass)) {
            assertFalse("Solo must not be targeted when ${part.label} is selected", isEventTargetForSinger(part, baritoneSoloEvent))
            assertFalse("Legacy Baritone Solo must not be targeted when ${part.label} is selected", isEventTargetForSinger(part, legacyBaritoneSoloEvent))
            assertFalse("Soprano Solo must not be targeted when ${part.label} is selected", isEventTargetForSinger(part, sopranoSoloEvent))
        }
    }

    @Test
    fun testSoloAudioFocusVisualFocusAndListenOnlyIsolation() {
        val engine = com.cit.kaido.voxsight.playback.NativePlaybackEngine(null)
        val soloEvent = MusicalEvent(
            eventId = "solo_ev", measureNumber = 2, tickPosition = 480, pitchMidi = 55, pitchName = "G3", durationTicks = 480,
            playbackTrack = "Baritone Solo", satbVoice = "Baritone Solo", rawParticipatingParts = listOf("BARITONE SOLO"), rawIsVocal = true
        )
        val sopranoEvent = MusicalEvent(
            eventId = "sop_ev", measureNumber = 6, tickPosition = 2400, pitchMidi = 67, pitchName = "G4", durationTicks = 480,
            playbackTrack = "S", satbVoice = "S", rawParticipatingParts = listOf("S"), rawIsVocal = true
        )
        val bassEvent = MusicalEvent(
            eventId = "bass_ev", measureNumber = 6, tickPosition = 2400, pitchMidi = 48, pitchName = "C3", durationTicks = 480,
            playbackTrack = "B", satbVoice = "B", rawParticipatingParts = listOf("B"), rawIsVocal = true
        )

        // Normalize voice key must map Baritone Solo / Bass Solo / Soprano Solo to "SOLO", never "B" or "S"
        assertEquals("SOLO", com.cit.kaido.voxsight.playback.NativePlaybackEngine.normalizeVoiceKey("Baritone Solo"))
        assertEquals("SOLO", com.cit.kaido.voxsight.playback.NativePlaybackEngine.normalizeVoiceKey("BASS SOLO"))
        assertEquals("SOLO", com.cit.kaido.voxsight.playback.NativePlaybackEngine.normalizeVoiceKey("SOPRANO SOLO"))

        // When Soprano, Alto, Tenor, or Bass is focused (Regular Playback with Audio/Visual Focus or Listen Only Mode):
        for (focusCode in listOf("S", "A", "T", "B", "S1", "B1")) {
            engine.setFocusedVoicePart(focusCode)
            assertFalse("Solo must be muted in audio when $focusCode is focused", engine.isVocalEventActiveInAudio(soloEvent.participatingParts))
            assertFalse("Solo must be excluded from visual focus highlights when $focusCode is focused", isEventMatchingFocusOrPart(focusCode, soloEvent))
        }

        // When Solo or Baritone Solo is focused:
        for (soloFocus in listOf("SOLO", "BARITONE SOLO")) {
            engine.setFocusedVoicePart(soloFocus)
            assertTrue("Solo must be audible when $soloFocus is focused", engine.isVocalEventActiveInAudio(soloEvent.participatingParts))
            assertTrue("Solo must be highlighted when $soloFocus is focused", isEventMatchingFocusOrPart(soloFocus, soloEvent))
            assertFalse("Soprano must be muted when $soloFocus is focused", engine.isVocalEventActiveInAudio(sopranoEvent.participatingParts))
            assertFalse("Bass must be muted when $soloFocus is focused", engine.isVocalEventActiveInAudio(bassEvent.participatingParts))
            assertFalse("Soprano must not be highlighted when $soloFocus is focused", isEventMatchingFocusOrPart(soloFocus, sopranoEvent))
            assertFalse("Bass must not be highlighted when $soloFocus is focused", isEventMatchingFocusOrPart(soloFocus, bassEvent))
        }

        // When Focus is disabled (null): all vocal parts including Solo are audible and highlighted
        engine.setFocusedVoicePart(null)
        assertTrue(engine.isVocalEventActiveInAudio(soloEvent.participatingParts))
        assertTrue(engine.isVocalEventActiveInAudio(sopranoEvent.participatingParts))
        assertTrue(engine.isVocalEventActiveInAudio(bassEvent.participatingParts))
        assertTrue(isEventMatchingFocusOrPart(null, soloEvent))
        assertTrue(isEventMatchingFocusOrPart(null, sopranoEvent))
        assertTrue(isEventMatchingFocusOrPart(null, bassEvent))
    }

    @Test
    fun testReconcileEventsWithScorePartsFixesStaleSoloAndSopranoStaves() {
        val score = MusicXmlScore(
            title = "Be Not Afraid",
            composer = "Bob Dufford",
            notes = emptyList(),
            parts = listOf(
                MusicXmlPart(1, "Solo", listOf(MusicXmlNote("G", 3, 480, 0, voice = 5, measureNumber = 2))),
                MusicXmlPart(2, "Soprano", listOf(MusicXmlNote("C", 5, 480, 0, voice = 1, measureNumber = 18))),
                MusicXmlPart(3, "Alto", listOf(MusicXmlNote("E", 4, 480, 0, voice = 2, measureNumber = 18))),
                MusicXmlPart(4, "Tenor", listOf(MusicXmlNote("G", 3, 480, 0, voice = 3, measureNumber = 18))),
                MusicXmlPart(5, "Bass", listOf(MusicXmlNote("C", 3, 480, 0, voice = 4, measureNumber = 18)))
            ),
            totalSeconds = 60,
            divisions = 480
        )

        // Simulate stale backend events where Part 1 (Solo) was mislabeled as "S" and Part 2 (Soprano) as "A"
        val staleEvents = listOf(
            MusicalEvent(
                eventId = "t0-p1-c0", measureNumber = 2, tickPosition = 0, pitchMidi = 55, pitchName = "G3", durationTicks = 480,
                partId = 1, satbVoice = "S", playbackTrack = "S", rawParticipatingParts = listOf("S"), rawIsVocal = true
            ),
            MusicalEvent(
                eventId = "t0-p2-c1", measureNumber = 18, tickPosition = 7680, pitchMidi = 72, pitchName = "C5", durationTicks = 480,
                partId = 2, satbVoice = "A", playbackTrack = "A", rawParticipatingParts = listOf("A"), rawIsVocal = true
            )
        )

        val reconciled = reconcileEventsWithScoreParts(staleEvents, score)
        assertEquals("SOLO", reconciled[0].satbVoice)
        assertEquals(listOf("SOLO"), reconciled[0].participatingParts)
        assertTrue(isEventTargetForSinger(VoicePart.Solo, reconciled[0]))
        assertFalse(isEventTargetForSinger(VoicePart.Soprano, reconciled[0]))

        assertEquals("SOPRANO", reconciled[1].satbVoice)
        assertEquals(listOf("S"), reconciled[1].participatingParts)
        assertTrue(isEventTargetForSinger(VoicePart.Soprano, reconciled[1]))
        assertFalse(isEventTargetForSinger(VoicePart.Alto, reconciled[1]))
    }

    @Test
    fun testSyncManagerDoesNotCrossShareNoteheadsBetweenSoloAndTenorOnSamePitch() {
        val syncManager = com.cit.kaido.voxsight.playback.SyncManager()

        // Mirrors Measure 6 of Awit ng Paghahangad:
        // Part 1 (Baritone Solo, staffIdx = 0) and Part 4 (Tenor, staffIdx = 3) both sing A3 (MIDI 57) at tick 0
        val soloEvent = MusicalEvent(
            eventId = "m6_solo_a3", measureNumber = 6, measureIndex = 5, tickPosition = 9600, ticksPerQuarter = 480,
            pitchMidi = 57, pitchName = "A3", durationTicks = 480,
            partId = 1, staffId = 1, sourceNoteId = null,
            satbVoice = "SOLO", playbackTrack = "SOLO", rawParticipatingParts = listOf("SOLO"), rawIsVocal = true
        )
        val tenorEvent = MusicalEvent(
            eventId = "m6_tenor_a3", measureNumber = 6, measureIndex = 5, tickPosition = 9600, ticksPerQuarter = 480,
            pitchMidi = 57, pitchName = "A3", durationTicks = 480,
            partId = 4, staffId = 1, sourceNoteId = null,
            satbVoice = "TENOR", playbackTrack = "TENOR", rawParticipatingParts = listOf("T"), rawIsVocal = true
        )

        val osmdElements = listOf(
            com.cit.kaido.voxsight.playback.SyncManager.OsmdNoteElement(
                id = 1001, tick = 9600, midiNote = 57, measureNumber = 6, measureIndex = 5,
                voice = 1, staffIdx = 0, part = "SOLO", color = "#FF6D00",
                x = 120f, y = 80f, width = 14f, height = 14f
            ),
            com.cit.kaido.voxsight.playback.SyncManager.OsmdNoteElement(
                id = 4001, tick = 9600, midiNote = 57, measureNumber = 6, measureIndex = 5,
                voice = 1, staffIdx = 3, part = "T", color = "#2563EB",
                x = 120f, y = 320f, width = 14f, height = 14f
            )
        )

        val stream = com.cit.kaido.voxsight.model.EventStream.freeze(listOf(soloEvent, tenorEvent))
        syncManager.buildMapping(stream, osmdElements, "layout_hash_m6")

        val soloCoord = syncManager.getCoordinateForEvent("m6_solo_a3")
        val tenorCoord = syncManager.getCoordinateForEvent("m6_tenor_a3")

        assertNotNull(soloCoord)
        assertNotNull(tenorCoord)
        assertEquals("Solo event must map strictly to staffIdx 0 notehead (id 1001)", 1001, soloCoord?.id)
        assertEquals("Tenor event must map strictly to staffIdx 3 notehead (id 4001)", 4001, tenorCoord?.id)
        assertNotEquals("Solo and Tenor on different staves must never share the same OSMD notehead", soloCoord?.id, tenorCoord?.id)
    }

    @Test
    fun testReconcileEventsWithThreePartSoloPlusCondensedSaAndTb() {
        // Mirrors 3-part Be Not Afraid score: Part 1 = SOLO, Part 2 = S. A., Part 3 = T. B.
        val score = MusicXmlScore(
            title = "Be Not Afraid",
            composer = "Bob Dufford",
            notes = emptyList(),
            parts = listOf(
                MusicXmlPart(1, "SOLO", listOf(MusicXmlNote("G", 4, 480, 0, voice = 5, measureNumber = 2))),
                MusicXmlPart(2, "S. A.", listOf(
                    MusicXmlNote("C", 5, 480, 0, voice = 1, originalVoice = 1, measureNumber = 18),
                    MusicXmlNote("E", 4, 480, 0, voice = 2, originalVoice = 2, isChord = true, measureNumber = 18)
                )),
                MusicXmlPart(3, "T. B.", listOf(
                    MusicXmlNote("G", 3, 480, 0, voice = 3, originalVoice = 1, measureNumber = 18),
                    MusicXmlNote("C", 3, 480, 0, voice = 4, originalVoice = 2, isChord = true, measureNumber = 18)
                ))
            ),
            totalSeconds = 60,
            divisions = 480
        )

        // Simulate stale backend events where Part 2 (S. A.) was mislabeled as "T"
        val staleEvents = listOf(
            MusicalEvent(
                eventId = "t0-p1-c0", measureNumber = 2, tickPosition = 0, pitchMidi = 67, pitchName = "G4", durationTicks = 480,
                partId = 1, voiceSource = 1, satbVoice = "SOLO", playbackTrack = "SOLO", rawParticipatingParts = listOf("SOLO"), rawIsVocal = true
            ),
            MusicalEvent(
                eventId = "t0-p2-c1", measureNumber = 18, tickPosition = 7680, pitchMidi = 72, pitchName = "C5", durationTicks = 480,
                partId = 2, voiceSource = 1, satbVoice = "T", playbackTrack = "T", rawParticipatingParts = listOf("T"), rawIsVocal = true
            ),
            MusicalEvent(
                eventId = "t0-p3-c2", measureNumber = 18, tickPosition = 7680, pitchMidi = 55, pitchName = "G3", durationTicks = 480,
                partId = 3, voiceSource = 1, satbVoice = "T", playbackTrack = "T", rawParticipatingParts = listOf("T"), rawIsVocal = true
            )
        )

        val reconciled = reconcileEventsWithScoreParts(staleEvents, score)
        assertEquals("SOLO", reconciled[0].satbVoice)
        assertEquals(listOf("SOLO"), reconciled[0].participatingParts)
        assertEquals("SOPRANO", reconciled[1].satbVoice)
        assertEquals(listOf("S"), reconciled[1].participatingParts)
        assertEquals("T", reconciled[2].satbVoice)
        assertEquals(listOf("T"), reconciled[2].participatingParts)

        // When Tenor is selected in Test Pitch: only Part 3 (T. B. top voice) is targeted and highlighted
        assertFalse(isEventTargetForSinger(VoicePart.Tenor, reconciled[0]))
        assertFalse(isEventTargetForSinger(VoicePart.Tenor, reconciled[1]))
        assertTrue(isEventTargetForSinger(VoicePart.Tenor, reconciled[2]))
        assertFalse(isEventMatchingFocusOrPart("T", reconciled[0]))
        assertFalse(isEventMatchingFocusOrPart("T", reconciled[1]))
        assertTrue(isEventMatchingFocusOrPart("T", reconciled[2]))
    }

    @Test
    fun testConfirmPipelinePreservesUserEditsAndCachedUploadEventsWithoutNetworkRoundTrip() {
        val uploadManager = com.cit.kaido.voxsight.ui.screens.upload.UploadManager
        uploadManager.clearReadyState()
        uploadManager.markReady(
            musicXml = "<score-partwise/>",
            scoreTitle = "Cached Score",
            eventsJson = "[{\"event_id\":\"t0-p1-c0\",\"source_note_id\":101}]",
            metadataJson = "{\"title\":\"Cached Score\"}"
        )
        assertEquals("Cached Score", uploadManager.pendingScoreTitle)
        assertEquals("[{\"event_id\":\"t0-p1-c0\",\"source_note_id\":101}]", uploadManager.pendingEventsJson)
        assertEquals("{\"title\":\"Cached Score\"}", uploadManager.pendingMetadataJson)

        uploadManager.clearReadyState()
        assertEquals(null, uploadManager.pendingEventsJson)
        assertEquals(null, uploadManager.pendingMetadataJson)

        // Verify local event generation preserves user voice overrides and sourceNoteId without backend re-upload
        val editedParts = listOf(
            MusicXmlPart(
                id = 1,
                name = "Soprano",
                notes = listOf(
                    MusicXmlNote(
                        step = "D",
                        octave = 5,
                        durationDivisions = 480,
                        startTimeDivisions = 0,
                        voice = 2,
                        customVoice = 2,
                        customVoiceStr = "A",
                        isUserVoiceOverride = true,
                        measureNumber = 1,
                        sourceNoteId = 501
                    )
                )
            ),
            MusicXmlPart(
                id = 2,
                name = "Alto",
                notes = listOf(
                    MusicXmlNote(
                        step = "F",
                        octave = 4,
                        durationDivisions = 480,
                        startTimeDivisions = 0,
                        voice = 2,
                        measureNumber = 1,
                        sourceNoteId = 502
                    )
                )
            )
        )
        val eventsJson = com.cit.kaido.voxsight.ui.screens.practice.generateEventsJsonFromScoreParts(editedParts, 480)
        val gson = com.google.gson.Gson()
        val listType = object : com.google.gson.reflect.TypeToken<List<MusicalEvent>>() {}.type
        val generatedEvents: List<MusicalEvent> = gson.fromJson(eventsJson, listType)

        assertEquals(2, generatedEvents.size)
        val overriddenEvent = generatedEvents.first { it.sourceNoteId == 501 }
        assertEquals("user", overriddenEvent.assignmentSource)
        assertEquals("ALTO", overriddenEvent.satbVoice)
        assertEquals(listOf("A"), overriddenEvent.participatingParts)
    }

    @Test
    fun testPracticeSummaryMeasureResolutionFor5PartAwitNgPaghahangadNeverSelectsSoloRestsForSoprano() {
        // 5-part Awit ng Paghahangad structure:
        // P1 = Baritone Solo (resting in M1-M5, entering in M6)
        // P2 = Soprano (singing in M1: dotted-quarter rest, D4 eighth, C#4 quarter, D4 quarter)
        // P3 = Alto, P4 = Tenor, P5 = Bass
        val baritoneSoloPart = MusicXmlPart(
            id = 1,
            name = "Baritone Solo",
            notes = listOf(
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 1440, startTimeDivisions = 0,
                    voice = 5, customVoice = 5, customVoiceStr = "SOLO", type = "whole",
                    isRest = true, measureNumber = 1, measureIndex = 0
                ),
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 960, startTimeDivisions = 7200,
                    voice = 5, customVoice = 5, customVoiceStr = "SOLO", type = "half",
                    isRest = true, measureNumber = 6, measureIndex = 5
                ),
                MusicXmlNote(
                    step = "A", octave = 3, durationDivisions = 480, startTimeDivisions = 8160,
                    voice = 5, customVoice = 5, customVoiceStr = "SOLO", type = "quarter",
                    isRest = false, measureNumber = 6, measureIndex = 5
                )
            )
        )
        val sopranoPart = MusicXmlPart(
            id = 2,
            name = "Soprano",
            notes = listOf(
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 720, startTimeDivisions = 0,
                    voice = 1, type = "quarter", isDotted = true,
                    isRest = true, measureNumber = 1, measureIndex = 0
                ),
                MusicXmlNote(
                    step = "D", octave = 4, durationDivisions = 240, startTimeDivisions = 720,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "eighth",
                    isRest = false, measureNumber = 1, measureIndex = 0
                ),
                MusicXmlNote(
                    step = "C#", octave = 4, durationDivisions = 480, startTimeDivisions = 960,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "quarter", alter = 1,
                    isRest = false, measureNumber = 1, measureIndex = 0
                ),
                MusicXmlNote(
                    step = "D", octave = 4, durationDivisions = 480, startTimeDivisions = 1440,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "quarter",
                    isRest = false, measureNumber = 1, measureIndex = 0
                )
            )
        )
        val altoPart = MusicXmlPart(
            id = 3,
            name = "Alto",
            notes = listOf(
                MusicXmlNote(
                    step = "B", octave = 3, durationDivisions = 1440, startTimeDivisions = 1440,
                    voice = 2, customVoice = 2, customVoiceStr = "A2", type = "half", isDotted = true,
                    isRest = false, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "D", octave = 4, durationDivisions = 1440, startTimeDivisions = 1440,
                    voice = 2, customVoice = 2, customVoiceStr = "A1", type = "half", isDotted = true,
                    isChord = true, isRest = false, measureNumber = 2, measureIndex = 1
                )
            )
        )
        val tenorPart = MusicXmlPart(
            id = 4,
            name = "Tenor",
            notes = listOf(
                MusicXmlNote(
                    step = "A", octave = 3, durationDivisions = 1440, startTimeDivisions = 1440,
                    voice = 3, customVoice = 3, customVoiceStr = "T", type = "half", isDotted = true,
                    isRest = false, measureNumber = 2, measureIndex = 1
                )
            )
        )
        val bassPart = MusicXmlPart(
            id = 5,
            name = "Bass",
            notes = listOf(
                MusicXmlNote(
                    step = "G", octave = 3, durationDivisions = 1440, startTimeDivisions = 1440,
                    voice = 4, customVoice = 4, customVoiceStr = "B", type = "half", isDotted = true,
                    isRest = false, measureNumber = 2, measureIndex = 1
                )
            )
        )

        val allParts = listOf(baritoneSoloPart, sopranoPart, altoPart, tenorPart, bassPart)
        val score = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Awit ng Paghahangad",
            composer = "Charlie Cenzon, SJ",
            totalSeconds = 180,
            divisions = 480,
            notes = allParts.flatMap { it.notes },
            parts = allParts
        )

        // 1. Soprano in Measure 1 must resolve P2's [dotted-quarter rest, D4, C#4, D4], NEVER P1's whole rest!
        val sopM1 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 1,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
        )
        assertEquals(4, sopM1.size)
        assertTrue(sopM1[0].isRest && sopM1[0].isDotted && sopM1[0].type == "quarter")
        assertEquals(listOf("D", "C#", "D"), sopM1.drop(1).map { it.step })

        // 2. Alto, Tenor, Bass in Measure 2 must each resolve their own part's pitched notes
        val altoM2 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 2,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.ALTO
        )
        assertEquals(1, altoM2.size)
        assertFalse(altoM2[0].isRest)
        assertEquals("B", altoM2[0].step)

        val tenorM2 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 2,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.TENOR
        )
        assertEquals(1, tenorM2.size)
        assertEquals("A", tenorM2[0].step)

        val bassM2 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 2,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.BASS
        )
        assertEquals(1, bassM2.size)
        assertEquals("G", bassM2[0].step)

        // 3. Solo in Measure 6 must resolve P1's [half rest, A3 quarter]
        val soloM6 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 6,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.SOLO
        )
        assertEquals(2, soloM6.size)
        assertTrue(soloM6[0].isRest && soloM6[0].type == "half")
        assertEquals("A", soloM6[1].step)
        assertEquals(3, soloM6[1].octave)
    }

    @Test
    fun testPracticeSummarySplitSystemPartSelectionAndNoteAttemptTickMatching() {
        // 6-page Awit ng Paghahangad split-system structure:
        // P1 ("S") has whole-measure rests in M1-M7 and pitched notes in M8
        // P4 ("Soprano") has pitched notes in M2 and whole-measure rests in M8
        val p1SplitSoprano = MusicXmlPart(
            id = 1,
            name = "S",
            notes = listOf(
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 1440, startTimeDivisions = 1440,
                    voice = 1, type = "whole", isRest = true, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "F#", octave = 4, durationDivisions = 960, startTimeDivisions = 10080,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "half", alter = 1,
                    isRest = false, measureNumber = 8, measureIndex = 7
                ),
                MusicXmlNote(
                    step = "G", octave = 4, durationDivisions = 480, startTimeDivisions = 11040,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "quarter",
                    isRest = false, measureNumber = 8, measureIndex = 7
                )
            )
        )
        val p4MainSoprano = MusicXmlPart(
            id = 4,
            name = "Soprano",
            notes = listOf(
                MusicXmlNote(
                    step = "A", octave = 4, durationDivisions = 720, startTimeDivisions = 1440,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "quarter", isDotted = true,
                    isRest = false, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "D", octave = 4, durationDivisions = 240, startTimeDivisions = 2160,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "eighth",
                    isRest = false, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "C#", octave = 4, durationDivisions = 240, startTimeDivisions = 2400,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "eighth", alter = 1,
                    isRest = false, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "D", octave = 4, durationDivisions = 240, startTimeDivisions = 2640,
                    voice = 1, customVoice = 1, customVoiceStr = "S", type = "eighth",
                    isRest = false, measureNumber = 2, measureIndex = 1
                ),
                // Spurious polyphonic backup quarter rests overlapping pitched notes in M2
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 480, startTimeDivisions = 1920,
                    voice = 2, type = "quarter", isRest = true, measureNumber = 2, measureIndex = 1
                ),
                MusicXmlNote(
                    step = "R", octave = 0, durationDivisions = 1440, startTimeDivisions = 10080,
                    voice = 1, type = "whole", isRest = true, measureNumber = 8, measureIndex = 7
                )
            )
        )

        val score = com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore(
            title = "Awit ng Paghahangad (6-page)",
            composer = "Charlie Cenzon, SJ",
            totalSeconds = 180,
            divisions = 480,
            notes = p1SplitSoprano.notes + p4MainSoprano.notes,
            parts = listOf(p1SplitSoprano, p4MainSoprano)
        )

        // In Measure 2, Soprano must select P4 (which has pitched notes) and strip the overlapping backup quarter rest at tick 1920
        val resolvedM2 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 2,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
        )
        assertEquals(listOf("A", "D", "C#", "D"), resolvedM2.map { it.step })
        assertTrue(resolvedM2.none { it.isRest })

        // In Measure 8, Soprano must select P1 (which has pitched notes) and never P4's whole-measure rest
        val resolvedM8 = com.cit.kaido.voxsight.ui.components.resolveMeasureNotesForSummary(
            measureNumber = 8,
            score = score,
            voice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
        )
        assertEquals(listOf("F#", "G"), resolvedM8.map { it.step })
        assertTrue(resolvedM8.none { it.isRest })

        // Verify note-to-PitchAttempt matching distinguishes the two D4 notes in Measure 2 by tick!
        val firstD4 = resolvedM2[1]  // tick 2160
        val secondD4 = resolvedM2[3] // tick 2640
        val attempts = listOf(
            com.cit.kaido.voxsight.pitch.PitchAttempt(
                timestampMs = 1000L,
                eventId = "t2160-p4-c1",
                measureNumber = 2,
                noteName = "D4",
                targetHz = 293.66f,
                detectedHz = 294.0f,
                deviationCents = 2.0f,
                isMatch = true,
                satbVoice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
            ),
            com.cit.kaido.voxsight.pitch.PitchAttempt(
                timestampMs = 1500L,
                eventId = "t2640-p4-c3",
                measureNumber = 2,
                noteName = "D4",
                targetHz = 293.66f,
                detectedHz = 277.18f,
                deviationCents = -100.0f,
                isMatch = false,
                satbVoice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
            )
        )

        val firstMatches = com.cit.kaido.voxsight.ui.components.matchAttemptsForMeasureNote(firstD4, 2, attempts)
        val secondMatches = com.cit.kaido.voxsight.ui.components.matchAttemptsForMeasureNote(secondD4, 2, attempts)

        assertEquals(1, firstMatches.size)
        assertTrue(firstMatches.first().isMatch)
        assertEquals(1, secondMatches.size)
        assertFalse(secondMatches.first().isMatch)
    }

    @Test
    fun testProblematicMeasureIntonationOnlyZeroMistakes() {
        // Measure 12 with 2 notes, both matched (isMatch = true), but with an average sharp intonation of +28 cents
        val attemptsM12 = listOf(
            com.cit.kaido.voxsight.pitch.PitchAttempt(
                timestampMs = 1000L,
                eventId = "t480-p1-c0",
                measureNumber = 12,
                noteName = "G4",
                targetHz = 392.0f,
                detectedHz = 398.5f,
                deviationCents = 28.5f,
                isMatch = true,
                satbVoice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
            ),
            com.cit.kaido.voxsight.pitch.PitchAttempt(
                timestampMs = 1500L,
                eventId = "t960-p1-c1",
                measureNumber = 12,
                noteName = "A4",
                targetHz = 440.0f,
                detectedHz = 447.0f,
                deviationCents = 27.5f,
                isMatch = true,
                satbVoice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
            )
        )

        val eventsInMeasure = attemptsM12.groupBy { it.eventId }
        val failedAttemptedEvents = eventsInMeasure.values.count { attemptsForEvent ->
            !attemptsForEvent.any { it.isMatch }
        }
        val validDeviations = attemptsM12.map { it.deviationCents }.filter { kotlin.math.abs(it) < 500f }
        val avgDevMeasure = if (validDeviations.isNotEmpty()) validDeviations.average().toFloat() else 0f

        // All notes were matched within the 50 cent window, so failedAttemptedEvents MUST be 0
        assertEquals(0, failedAttemptedEvents)
        // Average intonation drift is ~28 cents
        assertTrue(avgDevMeasure > 25f)

        // With the fix, effectiveMistakes must be failedAttemptedEvents (0), NOT fabricated as 1
        val effectiveMistakes = failedAttemptedEvents
        assertEquals(0, effectiveMistakes)

        val problematicMeasure = com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(
            measureNumber = 12,
            mistakeCount = effectiveMistakes,
            totalNotes = eventsInMeasure.size,
            isSharp = avgDevMeasure > 0f,
            averageDeviation = avgDevMeasure
        )

        assertEquals(0, problematicMeasure.mistakeCount)
        assertTrue(problematicMeasure.isSharp)
        assertEquals(28.0f, problematicMeasure.averageDeviation, 0.5f)
    }

    @Test
    fun testProblematicMeasureRankingAndCapAtThree() {
        // Create 5 measures with varying mistake counts and intonation deviations
        // Measure 1: 2 mistakes, deviation 10¢
        // Measure 2: 1 mistake, deviation 40¢
        // Measure 3: 1 mistake, deviation 20¢
        // Measure 4: 0 mistakes, deviation 35¢ (sharp intonation drift)
        // Measure 5: 0 mistakes, deviation -28¢ (flat intonation drift)
        val measures = listOf(
            com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(1, mistakeCount = 2, totalNotes = 4, isSharp = true, averageDeviation = 10f),
            com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(2, mistakeCount = 1, totalNotes = 4, isSharp = true, averageDeviation = 40f),
            com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(3, mistakeCount = 1, totalNotes = 4, isSharp = true, averageDeviation = 20f),
            com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(4, mistakeCount = 0, totalNotes = 4, isSharp = true, averageDeviation = 35f),
            com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure(5, mistakeCount = 0, totalNotes = 4, isSharp = false, averageDeviation = -28f)
        )

        val sortedAndCapped = measures.sortedWith(
            compareByDescending<com.cit.kaido.voxsight.ui.screens.practice.ProblematicMeasure> { it.mistakeCount }
                .thenByDescending { kotlin.math.abs(it.averageDeviation) }
                .thenBy { it.measureNumber }
        ).take(3)

        // Must cap at exactly 3 measures
        assertEquals(3, sortedAndCapped.size)
        // Measure 1 ranks first (2 mistakes)
        assertEquals(1, sortedAndCapped[0].measureNumber)
        // Measure 2 ranks second (1 mistake, 40¢ dev beats 20¢ dev)
        assertEquals(2, sortedAndCapped[1].measureNumber)
        // Measure 3 ranks third (1 mistake, 20¢ dev)
        assertEquals(3, sortedAndCapped[2].measureNumber)
        // Measures 4 and 5 (0 mistakes) are excluded due to cap at 3
        assertFalse(sortedAndCapped.any { it.measureNumber == 4 || it.measureNumber == 5 })
    }

    @Test
    fun testMatchAttemptsWithProportionalTpqScaling() {
        val note480 = MusicXmlNote(
            step = "G",
            octave = 4,
            durationDivisions = 480,
            startTimeDivisions = 480
        )
        // Attempt recorded with backend divisions 960 (tick 960 is tick 480 * 2)
        val attempt960 = com.cit.kaido.voxsight.pitch.PitchAttempt(
            timestampMs = 1200L,
            eventId = "t960-p1-c0",
            measureNumber = 5,
            noteName = "G4",
            targetHz = 392.0f,
            detectedHz = 392.0f,
            deviationCents = 0.0f,
            isMatch = true,
            satbVoice = com.cit.kaido.voxsight.model.SATBVoice.SOPRANO
        )

        val matches = com.cit.kaido.voxsight.ui.components.matchAttemptsForMeasureNote(
            note = note480,
            measureNumber = 5,
            pitchAttempts = listOf(attempt960)
        )

        assertEquals(1, matches.size)
        assertEquals("G4", matches.first().noteName)
        assertTrue(matches.first().isMatch)
    }
}
