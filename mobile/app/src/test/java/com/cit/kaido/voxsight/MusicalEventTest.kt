package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.model.normalizeLegacyVoiceToParts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class MusicalEventTest {

    private val gson = Gson()

    @Test
    fun testLegacyV10Deserialization() {
        val json = """[{"event_id":"t100-p1-c0","measure_number":1,"tick_position":100,"pitch_midi":60,"pitch_name":"C4","duration_ticks":960,"satb_voice":"S","schema_version":"1.0"}]"""
        val type = object : TypeToken<List<MusicalEvent>>() {}.type
        val events: List<MusicalEvent> = gson.fromJson(json, type)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals("t100-p1-c0", event.eventId)
        assertEquals("S", event.satbVoice)
        assertEquals(listOf("S"), event.participatingParts)
        assertTrue(event.isVocal)
        assertFalse(event.isUnison)
        assertEquals(SATBVoice.SOPRANO, event.satbEnum)
        assertEquals("1.0", event.schemaVersion)
    }

    @Test
    fun testAdditiveV11Deserialization() {
        val json = """[{"event_id":"t200-p1-c0","measure_number":2,"tick_position":200,"pitch_midi":64,"pitch_name":"E4","duration_ticks":960,"satb_voice":"S1","participating_parts":["S1","S2"],"is_vocal":true,"is_unison":true,"unison_type":"SECTION_DIVISI","xml_element_order":42,"schema_version":"1.1"}]"""
        val type = object : TypeToken<List<MusicalEvent>>() {}.type
        val events: List<MusicalEvent> = gson.fromJson(json, type)

        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(listOf("S1", "S2"), event.participatingParts)
        assertTrue(event.isVocal)
        assertTrue(event.isUnison)
        assertEquals("SECTION_DIVISI", event.unisonType)
        assertEquals(42, event.xmlElementOrder)
        assertEquals("1.1", event.schemaVersion)
    }

    @Test
    fun testStrictWhitelistPureVocal() {
        val event = MusicalEvent(
            eventId = "t1", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("S1", "s2", " ALL "),
            rawIsVocal = true
        )
        assertEquals(listOf("S1", "S2", "ALL"), event.participatingParts)
        assertTrue(event.isVocal)
    }

    @Test
    fun testStrictWhitelistPureInstrumental() {
        val event = MusicalEvent(
            eventId = "t2", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("PIANO", "STRINGS"),
            rawIsVocal = false
        )
        assertEquals(listOf("OTHERS"), event.participatingParts)
        assertFalse(event.isVocal)
    }

    @Test
    fun testStrictWhitelistContaminatedListFailsSafe() {
        val event = MusicalEvent(
            eventId = "t3", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("S", "GUITAR"),
            rawIsVocal = true
        )
        // Contaminated with non-whitelisted token -> fails safe to empty list and non-vocal
        assertTrue(event.participatingParts.isEmpty())
        assertFalse(event.isVocal)
    }

    @Test
    fun testFailSafeVocalGatingRawIsVocalFalseOverrides() {
        val event = MusicalEvent(
            eventId = "t4", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("S"),
            rawIsVocal = false
        )
        // Explicit rawIsVocal = false MUST NOT be overridden by vocal part name
        assertFalse(event.isVocal)
    }

    @Test
    fun testFailSafeVocalGatingBlindTrustDefense() {
        val event = MusicalEvent(
            eventId = "t5", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("PIANO"),
            rawIsVocal = true
        )
        // rawIsVocal = true MUST NOT be trusted blindly over non-vocal token
        assertFalse(event.isVocal)
    }

    @Test
    fun testUniversalUnisonSemantics() {
        val event = MusicalEvent(
            eventId = "t6", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            rawParticipatingParts = listOf("ALL")
        )
        assertTrue(event.isVocal)
        assertTrue(event.isUnison)
    }

    @Test
    fun testCompoundUnisonLegacyFallback() {
        val event = MusicalEvent(
            eventId = "t7", measureNumber = 1, tickPosition = 0, pitchMidi = 60, pitchName = "C4", durationTicks = 480,
            satbVoice = "UNISON_TB"
        )
        assertEquals(listOf("T", "B"), event.participatingParts)
        assertTrue(event.isVocal)
        assertTrue(event.isUnison)
    }

    @Test
    fun testNormalizeLegacyVoiceToPartsAllSections() {
        assertEquals(listOf("S"), normalizeLegacyVoiceToParts("S"))
        assertEquals(listOf("S"), normalizeLegacyVoiceToParts("Soprano"))
        assertEquals(listOf("S"), normalizeLegacyVoiceToParts("1"))
        assertEquals(listOf("A"), normalizeLegacyVoiceToParts("A"))
        assertEquals(listOf("A"), normalizeLegacyVoiceToParts("2"))
        assertEquals(listOf("T"), normalizeLegacyVoiceToParts("T"))
        assertEquals(listOf("T"), normalizeLegacyVoiceToParts("3"))
        assertEquals(listOf("B"), normalizeLegacyVoiceToParts("B"))
        assertEquals(listOf("B"), normalizeLegacyVoiceToParts("4"))
        assertEquals(listOf("SOLO"), normalizeLegacyVoiceToParts("Solo"))
        assertEquals(listOf("SOLO"), normalizeLegacyVoiceToParts("5"))
        assertEquals(listOf("OTHERS"), normalizeLegacyVoiceToParts("Others"))
        assertEquals(listOf("OTHERS"), normalizeLegacyVoiceToParts("6"))
        assertEquals(listOf("OTHERS"), normalizeLegacyVoiceToParts("Piano"))
        assertEquals(listOf("S1"), normalizeLegacyVoiceToParts("S1"))
        assertEquals(listOf("S2"), normalizeLegacyVoiceToParts("S2"))
        assertEquals(listOf("T", "B"), normalizeLegacyVoiceToParts("UNISON_TB"))
        assertEquals(listOf("S", "A"), normalizeLegacyVoiceToParts("UNISON_SA"))
        assertEquals(listOf("ALL"), normalizeLegacyVoiceToParts("TUTTI"))
        assertEquals(listOf("ALL"), normalizeLegacyVoiceToParts("UNISON_ALL"))
        assertEquals(emptyList<String>(), normalizeLegacyVoiceToParts("XYZ_INVALID"))
        assertEquals(emptyList<String>(), normalizeLegacyVoiceToParts(""))
        assertEquals(emptyList<String>(), normalizeLegacyVoiceToParts(null))
    }

    @Test
    fun testSatbVoiceFromStringSafe() {
        assertEquals(SATBVoice.SOPRANO, SATBVoice.fromString("S"))
        assertEquals(SATBVoice.SOPRANO, SATBVoice.fromString("S1"))
        assertEquals(SATBVoice.SOPRANO, SATBVoice.fromString("S2"))
        assertEquals(SATBVoice.SOPRANO, SATBVoice.fromString("Soprano"))
        assertEquals(SATBVoice.SOPRANO, SATBVoice.fromString("1"))
        assertEquals(SATBVoice.ALTO, SATBVoice.fromString("A"))
        assertEquals(SATBVoice.ALTO, SATBVoice.fromString("2"))
        assertEquals(SATBVoice.TENOR, SATBVoice.fromString("T"))
        assertEquals(SATBVoice.TENOR, SATBVoice.fromString("3"))
        assertEquals(SATBVoice.BASS, SATBVoice.fromString("B"))
        assertEquals(SATBVoice.BASS, SATBVoice.fromString("4"))
        assertEquals(SATBVoice.SOLO, SATBVoice.fromString("SOLO"))
        assertEquals(SATBVoice.SOLO, SATBVoice.fromString("5"))
        assertEquals(SATBVoice.OTHERS, SATBVoice.fromString("Others"))
        assertEquals(SATBVoice.OTHERS, SATBVoice.fromString("6"))
        assertEquals(SATBVoice.OTHERS, SATBVoice.fromString("Piano"))
        assertEquals(SATBVoice.OTHERS, SATBVoice.fromString("Strings")) // Never mistaken for Soprano!
        assertEquals(SATBVoice.UNKNOWN, SATBVoice.fromString("UNISON_TB"))
        assertEquals(SATBVoice.UNKNOWN, SATBVoice.fromString(null))
        assertEquals(SATBVoice.UNKNOWN, SATBVoice.fromString("UNKNOWN"))
    }
}
