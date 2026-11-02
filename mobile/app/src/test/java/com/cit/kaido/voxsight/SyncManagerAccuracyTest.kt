package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.playback.SyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncManagerAccuracyTest {

    @Test
    fun testUnisonNotesMapToSingleSharedOsmdNoteHead() {
        val syncManager = SyncManager()

        // Real OSMD rendering merges a Tenor/Bass unison onto ONE shared graphical notehead (id = 101)
        val tenorEvent = MusicalEvent(
            eventId = "t0-p1-c0",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            pitchMidi = 60,
            pitchName = "C4",
            durationTicks = 480,
            satbVoice = "TENOR",
            partId = 1,
            staffId = 1,
            sourceNoteId = 101
        )
        val bassEvent = MusicalEvent(
            eventId = "t0-p2-c1",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            pitchMidi = 60,
            pitchName = "C4",
            durationTicks = 480,
            satbVoice = "BASS",
            partId = 2,
            staffId = 1,
            sourceNoteId = 101
        )

        val osmdElements = listOf(
            SyncManager.OsmdNoteElement(
                id = 101,
                tick = 0,
                midiNote = 60,
                measureNumber = 1,
                measureIndex = 0,
                voice = 1,
                staffIdx = 0,
                part = "UNISON_TB",
                x = 100f,
                y = 150f,
                width = 16f,
                height = 12f
            )
        )

        syncManager.buildMapping(EventStream.freeze(listOf(tenorEvent, bassEvent)), osmdElements, "layout_unison_shared")

        val tenorCoord = syncManager.getCoordinateForEvent("t0-p1-c0")
        val bassCoord = syncManager.getCoordinateForEvent("t0-p2-c1")

        assertNotNull("Tenor unison event must have a coordinate", tenorCoord)
        assertNotNull("Bass unison event must have a coordinate", bassCoord)
        assertEquals(tenorCoord?.x, bassCoord?.x)
        assertEquals(101, tenorCoord?.id)
        assertEquals(101, bassCoord?.id)
        assertEquals(1.0f, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
    }

    @Test
    fun testRepeatedPitchesMaintainHighConfidence() {
        val syncManager = SyncManager()

        val note1 = MusicalEvent(
            eventId = "t0-p1-c0",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            pitchMidi = 60,
            pitchName = "C4",
            durationTicks = 240,
            satbVoice = "SOPRANO",
            partId = 1,
            staffId = 1,
            sourceNoteId = 1
        )
        val note2 = MusicalEvent(
            eventId = "t240-p1-c1",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 240,
            pitchMidi = 60,
            pitchName = "C4",
            durationTicks = 240,
            satbVoice = "SOPRANO",
            partId = 1,
            staffId = 1,
            sourceNoteId = 2
        )

        val osmdElements = listOf(
            SyncManager.OsmdNoteElement(
                id = 1,
                tick = 0,
                midiNote = 60,
                measureNumber = 1,
                measureIndex = 0,
                voice = 1,
                staffIdx = 0,
                part = "S",
                x = 50f,
                y = 100f,
                width = 16f,
                height = 12f
            ),
            SyncManager.OsmdNoteElement(
                id = 2,
                tick = 240,
                midiNote = 60,
                measureNumber = 1,
                measureIndex = 0,
                voice = 1,
                staffIdx = 0,
                part = "S",
                x = 100f,
                y = 100f,
                width = 16f,
                height = 12f
            )
        )

        syncManager.buildMapping(EventStream.freeze(listOf(note1, note2)), osmdElements, "layout_test_2")

        assertEquals(1.0f, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
        assertEquals(50f, syncManager.getCoordinateForEvent("t0-p1-c0")?.x ?: -1f, 0.01f)
        assertEquals(100f, syncManager.getCoordinateForEvent("t240-p1-c1")?.x ?: -1f, 0.01f)
    }

    @Test
    fun testContinuousPlaybackDoesNotDegradeConfidence() {
        val syncManager = SyncManager()

        val note = MusicalEvent(
            eventId = "t0-p1-c0",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            pitchMidi = 64,
            pitchName = "E4",
            durationTicks = 480,
            satbVoice = "ALTO",
            partId = 1,
            staffId = 1,
            sourceNoteId = 10
        )

        val osmdElements = listOf(
            SyncManager.OsmdNoteElement(
                id = 10,
                tick = 0,
                midiNote = 64,
                measureNumber = 1,
                measureIndex = 0,
                voice = 1,
                staffIdx = 0,
                part = "A",
                x = 80f,
                y = 120f,
                width = 16f,
                height = 12f
            )
        )

        syncManager.buildMapping(EventStream.freeze(listOf(note)), osmdElements, "layout_test_3")
        val initialConfidence = syncManager.getSyncConfidence()

        val highlightedEvents = mutableListOf<String>()
        syncManager.setListener(object : SyncManager.SyncListener {
            override fun onHighlightNotes(highlights: List<SyncManager.NoteHighlightData>) {
                highlightedEvents.addAll(highlights.map { it.eventId })
            }
            override fun onClearHighlights() {}
            override fun onSyncConfidenceChanged(confidence: Float) {}
            override fun onHighlightModeChanged(mode: SyncManager.HighlightMode) {}
        })

        for (i in 0 until 50) {
            syncManager.onPlaybackProgress(listOf("t0-p1-c0", "unmatched_phantom_event"))
        }

        assertEquals(initialConfidence, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
        assertTrue("Should have emitted highlights for matched note", highlightedEvents.contains("t0-p1-c0"))
    }

    @Test
    fun testLegacyUnstampedCloudOrSavedEventsFallbackToNoteLevelSync() {
        val syncManager = SyncManager()

        // Simulates Railway cloud / saved local score events with sourceNoteId == null,
        // TPQ = 960, and default measureIndex = 0 across multiple measures.
        val m1Sop = MusicalEvent(
            eventId = "t0-p1-c0",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            ticksPerQuarter = 960,
            pitchMidi = 67,
            pitchName = "G4",
            durationTicks = 960,
            satbVoice = "S",
            partId = 1,
            staffId = 1,
            sourceNoteId = null
        )
        val m2Bass = MusicalEvent(
            eventId = "t3840-p4-c0",
            measureNumber = 2,
            measureIndex = 0,
            tickPosition = 3840,
            ticksPerQuarter = 960,
            pitchMidi = 48,
            pitchName = "C3",
            durationTicks = 960,
            satbVoice = "B",
            partId = 4,
            staffId = 4,
            sourceNoteId = null
        )

        val osmdElements = listOf(
            SyncManager.OsmdNoteElement(
                id = 0,
                tick = 0,
                midiNote = 67,
                measureNumber = 1,
                measureIndex = 0,
                staffIdx = 0,
                part = "S",
                x = 120f,
                y = 90f,
                width = 14f,
                height = 12f
            ),
            SyncManager.OsmdNoteElement(
                id = 1,
                tick = 1920,
                midiNote = 48,
                measureNumber = 2,
                measureIndex = 1,
                staffIdx = 3,
                part = "B",
                x = 340f,
                y = 260f,
                width = 14f,
                height = 12f
            )
        )

        syncManager.buildMapping(EventStream.freeze(listOf(m1Sop, m2Bass)), osmdElements, "legacy_cloud_layout")

        assertEquals(1.0f, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
        assertEquals(120f, syncManager.getCoordinateForEvent("t0-p1-c0")?.x ?: -1f, 0.01f)
        assertEquals(340f, syncManager.getCoordinateForEvent("t3840-p4-c0")?.x ?: -1f, 0.01f)
    }

    @Test
    fun testTiedMultiHeadOsmdElementsResolveCleanlyByOnset() {
        val syncManager = SyncManager()
        val event = MusicalEvent(
            eventId = "tied-note",
            measureNumber = 14,
            measureIndex = 13,
            tickPosition = 960,
            ticksPerQuarter = 960,
            pitchMidi = 62,
            pitchName = "D4",
            durationTicks = 960,
            satbVoice = "A",
            partId = 3,
            staffId = 1,
            sourceNoteId = 77
        )

        // OSMD splits a tied/system-wrapped note into two graphical heads sharing data-vx-id = 77
        val head1 = SyncManager.OsmdNoteElement(
            id = 77,
            tick = 480,
            midiNote = 62,
            measureNumber = 14,
            measureIndex = 13,
            staffIdx = 2,
            part = "A",
            x = 210f,
            y = 140f,
            width = 15f,
            height = 11f
        )
        val head2 = SyncManager.OsmdNoteElement(
            id = 77,
            tick = 960,
            midiNote = 62,
            measureNumber = 14,
            measureIndex = 14,
            staffIdx = 2,
            part = "A",
            x = 45f,
            y = 340f,
            width = 15f,
            height = 11f
        )

        syncManager.buildMapping(EventStream.freeze(listOf(event)), listOf(head1, head2), "tied_multihead")

        val coord = syncManager.getCoordinateForEvent("tied-note")
        assertNotNull("Tied multi-head note must resolve instead of dropping", coord)
        assertEquals(210f, coord?.x ?: -1f, 0.01f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
    }

    @Test
    fun staleExplicitSourceNoteIdDoesNotCrossMatchWrongNote() {
        val syncManager = SyncManager()
        val event = MusicalEvent(
            eventId = "deleted-note-999",
            measureNumber = 1,
            measureIndex = 0,
            tickPosition = 0,
            pitchMidi = 60,
            pitchName = "C4",
            durationTicks = 480,
            satbVoice = "SOPRANO",
            sourceNoteId = 999
        )
        val renderedNote = SyncManager.OsmdNoteElement(
            id = 44,
            tick = 0,
            midiNote = 60,
            measureNumber = 1,
            measureIndex = 0,
            x = 50f,
            y = 80f
        )

        syncManager.buildMapping(EventStream.freeze(listOf(event)), listOf(renderedNote), "stale_id")

        assertNull(syncManager.getCoordinateForEvent("deleted-note-999"))
        assertEquals(0f, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.DISABLED, syncManager.getHighlightMode())
    }
}
