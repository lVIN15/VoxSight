package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.ui.screens.practice.VoicePart
import com.cit.kaido.voxsight.ui.screens.practice.isEventTargetForSinger
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
}
