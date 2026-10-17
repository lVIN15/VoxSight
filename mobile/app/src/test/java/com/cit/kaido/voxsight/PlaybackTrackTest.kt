package com.cit.kaido.voxsight

import com.cit.kaido.voxsight.network.PlaybackTrack
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class PlaybackTrackTest {

    private val gson = Gson()

    @Test
    fun testVocalPlaybackTrackDeserialization() {
        val json = """
            {
                "track_id": "p1-s1-v1",
                "structural_label": "Part 1, Staff 1, Voice 1",
                "satb_label": "Soprano",
                "satb_confidence_applied": true,
                "part_key": "S",
                "part_role": "VOCAL",
                "parent_section": "Soprano",
                "is_accompaniment": false
            }
        """.trimIndent()

        val track = gson.fromJson(json, PlaybackTrack::class.java)
        assertEquals("p1-s1-v1", track.trackId)
        assertEquals("Soprano", track.satbLabel)
        assertEquals("S", track.partKey)
        assertEquals("VOCAL", track.partRole)
        assertEquals("Soprano", track.parentSection)
        assertFalse(track.isAccompaniment)
        assertTrue(track.satbConfidenceApplied)
    }

    @Test
    fun testAccompanimentPlaybackTrackDeserialization() {
        val json = """
            {
                "track_id": "p5-s1-v1",
                "structural_label": "Part 5, Staff 1, Voice 1",
                "satb_label": "Others",
                "satb_confidence_applied": true,
                "part_key": "OTHER",
                "part_role": "ACCOMPANIMENT",
                "parent_section": null,
                "is_accompaniment": true
            }
        """.trimIndent()

        val track = gson.fromJson(json, PlaybackTrack::class.java)
        assertEquals("p5-s1-v1", track.trackId)
        assertEquals("Others", track.satbLabel)
        assertEquals("OTHER", track.partKey)
        assertEquals("ACCOMPANIMENT", track.partRole)
        assertNull(track.parentSection)
        assertTrue(track.isAccompaniment)
    }

    @Test
    fun testLegacyPlaybackTrackWithoutSection7Fields() {
        val json = """
            {
                "track_id": "p1-s1",
                "structural_label": "Part 1, Staff 1",
                "satb_label": "Tenor"
            }
        """.trimIndent()

        val track = gson.fromJson(json, PlaybackTrack::class.java)
        assertEquals("p1-s1", track.trackId)
        assertEquals("Tenor", track.satbLabel)
        assertEquals("", track.partKey)
        assertEquals("UNKNOWN", track.partRole)
        assertNull(track.parentSection)
        assertFalse(track.isAccompaniment)
    }
}
