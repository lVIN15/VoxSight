package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.model.EventStream
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.playback.SyncManager
import org.junit.Assert.*
import org.junit.Test

class SyncManagerAccuracyTest {

    @Test
    fun testUnisonNotesBothGetMatched() {
        val syncManager = SyncManager()

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
            staffId = 1
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
            staffId = 1
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

        syncManager.buildMapping(EventStream.freeze(listOf(tenorEvent, bassEvent)), osmdElements, "layout_test_1")

        val tenorCoord = syncManager.getCoordinateForEvent("t0-p1-c0")
        val bassCoord = syncManager.getCoordinateForEvent("t0-p2-c1")

        assertNotNull("Tenor unison event must have a coordinate", tenorCoord)
        assertNotNull("Bass unison event must have a coordinate", bassCoord)
        assertEquals(tenorCoord?.x, bassCoord?.x)
        assertEquals(101, tenorCoord?.id)
        assertEquals(101, bassCoord?.id)
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
            staffId = 1
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
            staffId = 1
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
            staffId = 1
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

        // Simulate 50 audio frames with active event + unmatched event (e.g. phantom rest event)
        for (i in 0 until 50) {
            syncManager.onPlaybackProgress(listOf("t0-p1-c0", "unmatched_phantom_event"))
        }

        // Confidence should NOT have plummeted to 0, and highlights should have been emitted
        assertEquals(initialConfidence, syncManager.getSyncConfidence(), 0.001f)
        assertEquals(SyncManager.HighlightMode.NOTE_LEVEL, syncManager.getHighlightMode())
        assertTrue("Should have emitted highlights for matched note", highlightedEvents.contains("t0-p1-c0"))
    }
}
