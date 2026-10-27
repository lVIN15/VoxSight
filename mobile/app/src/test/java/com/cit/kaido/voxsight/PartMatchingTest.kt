package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.ui.screens.practice.VoicePart
import com.cit.kaido.voxsight.ui.screens.practice.isEventTargetForSinger
import com.cit.kaido.voxsight.ui.screens.practice.getDisplayLabel
import com.cit.kaido.voxsight.ui.screens.practice.getDisplayShortLabel
import com.cit.kaido.voxsight.ui.screens.practice.detectAvailableParts
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart
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
}
