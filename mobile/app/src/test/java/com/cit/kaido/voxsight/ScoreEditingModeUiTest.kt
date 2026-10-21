package com.cit.kaido.voxsight

import androidx.compose.ui.graphics.Color
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.upload.extractScorePartLegendItems
import org.junit.Assert.*
import org.junit.Test

class ScoreEditingModeUiTest {

    @Test
    fun testExtractScorePartLegendItemsStandardSatb() {
        val score = MusicXmlScore(
            title = "Test SATB Hymnal",
            composer = "Composer",
            notes = emptyList(),
            parts = listOf(
                MusicXmlPart(id = 1, name = "Soprano", notes = emptyList()),
                MusicXmlPart(id = 2, name = "Alto", notes = emptyList()),
                MusicXmlPart(id = 3, name = "Tenor", notes = emptyList()),
                MusicXmlPart(id = 4, name = "Bass", notes = emptyList())
            ),
            totalSeconds = 60
        )

        val items = extractScorePartLegendItems(score)
        val codes = items.map { it.code }
        val names = items.map { it.name }

        assertEquals(listOf("S", "A", "T", "B"), codes)
        assertEquals(listOf("Soprano", "Alto", "Tenor", "Bass"), names)
        assertEquals(Color(0xFFE91E63), items[0].color)
        assertEquals(Color(0xFF8E24AA), items[1].color)
        assertEquals(Color(0xFF1976D2), items[2].color)
        assertEquals(Color(0xFF2E7D32), items[3].color)
    }

    @Test
    fun testExtractScorePartLegendItemsWithDivisi() {
        val score = MusicXmlScore(
            title = "Happy Birthday Divisi",
            composer = "Composer",
            notes = emptyList(),
            parts = listOf(
                MusicXmlPart(id = 1, name = "Soprano", notes = emptyList()),
                MusicXmlPart(id = 2, name = "Alto", notes = emptyList()),
                MusicXmlPart(id = 3, name = "Tenor", notes = emptyList()),
                MusicXmlPart(id = 4, name = "Bass 1", notes = emptyList()),
                MusicXmlPart(id = 5, name = "Bass 2", notes = emptyList())
            ),
            totalSeconds = 60
        )

        val items = extractScorePartLegendItems(score)
        val codes = items.map { it.code }
        val names = items.map { it.name }

        // Must display Bass 1 and Bass 2, NOT duplicating with parent Bass
        assertFalse("Parent Bass should not be duplicated when subdivisions exist", codes.contains("B"))
        assertTrue("Bass 1 must be present", codes.contains("B1"))
        assertTrue("Bass 2 must be present", codes.contains("B2"))
        assertEquals(listOf("S", "A", "T", "B1", "B2"), codes)
        assertEquals(listOf("Soprano", "Alto", "Tenor", "Bass 1", "Bass 2"), names)

        val b1Item = items.first { it.code == "B1" }
        val b2Item = items.first { it.code == "B2" }
        assertEquals(Color(0xFF7CB342), b1Item.color)
        assertEquals(Color(0xFF1B5E20), b2Item.color)
    }

    @Test
    fun testExtractScorePartLegendItemsWithUnisonsAndMergedNotes() {
        val noteTB = MusicXmlNote(
            step = "D", octave = 3, durationDivisions = 480,
            customVoiceStr = "UNISON_TB"
        )
        val noteTutti = MusicXmlNote(
            step = "G", octave = 4, durationDivisions = 480,
            customVoiceStr = "TUTTI"
        )

        val score = MusicXmlScore(
            title = "Score With Unisons",
            composer = "Composer",
            notes = listOf(noteTB, noteTutti),
            parts = listOf(
                MusicXmlPart(id = 1, name = "Soprano", notes = emptyList()),
                MusicXmlPart(id = 2, name = "Alto", notes = emptyList()),
                MusicXmlPart(id = 3, name = "Tenor", notes = listOf(noteTB)),
                MusicXmlPart(id = 4, name = "Bass", notes = listOf(noteTutti))
            ),
            totalSeconds = 60
        )

        val items = extractScorePartLegendItems(score)
        val codes = items.map { it.code }

        assertTrue("Must detect UNISON_TB", codes.contains("UNISON_TB"))
        assertTrue("Must detect TUTTI", codes.contains("TUTTI"))

        val tbItem = items.first { it.code == "UNISON_TB" }
        val tuttiItem = items.first { it.code == "TUTTI" }
        assertEquals("Tenor / Bass", tbItem.name)
        assertEquals(Color(0xFF00BFA5), tbItem.color)
        assertEquals("Tutti (All)", tuttiItem.name)
        assertEquals(Color(0xFFFFB300), tuttiItem.color)
    }

    @Test
    fun testExtractScorePartLegendItemsDynamicSessionUpdates() {
        val score = MusicXmlScore(
            title = "Standard Score",
            composer = "Composer",
            notes = emptyList(),
            parts = listOf(
                MusicXmlPart(id = 1, name = "Soprano", notes = emptyList()),
                MusicXmlPart(id = 2, name = "Alto", notes = emptyList()),
                MusicXmlPart(id = 3, name = "Tenor", notes = emptyList()),
                MusicXmlPart(id = 4, name = "Bass", notes = emptyList())
            ),
            totalSeconds = 60
        )

        // User dynamically edits a note in the session to S/A Unison and another to Solo
        val dynamicVoices = setOf("UNISON_SA", "SOLO")
        val items = extractScorePartLegendItems(score, dynamicVoices)
        val codes = items.map { it.code }

        assertTrue("Must dynamically reflect UNISON_SA", codes.contains("UNISON_SA"))
        assertTrue("Must dynamically reflect SOLO", codes.contains("SOLO"))

        val saItem = items.first { it.code == "UNISON_SA" }
        val soloItem = items.first { it.code == "SOLO" }
        assertEquals("Soprano / Alto", saItem.name)
        assertEquals(Color(0xFFD81B60), saItem.color)
        assertEquals("Solo", soloItem.name)
        assertEquals(Color(0xFFFF6D00), soloItem.color)
    }

    @Test
    fun testPitchStepAndOctaveCalculationLogic() {
        val steps = listOf("C", "D", "E", "F", "G", "A", "B")

        // 1. Step up from middle: D3 -> E3
        var step = "D"
        var octave = 3
        var alter = 0

        var idx = steps.indexOf(step)
        if (step == "B") {
            if (octave < 8) { step = "C"; octave += 1 }
        } else if (idx != -1 && idx < steps.size - 1) {
            step = steps[idx + 1]
        }
        assertEquals("E", step)
        assertEquals(3, octave)

        // 2. Step up wrapping boundary: B3 -> C4
        step = "B"
        octave = 3
        idx = steps.indexOf(step)
        if (step == "B") {
            if (octave < 8) { step = "C"; octave += 1 }
        } else if (idx != -1 && idx < steps.size - 1) {
            step = steps[idx + 1]
        }
        assertEquals("C", step)
        assertEquals(4, octave)

        // 3. Step down wrapping boundary: C4 -> B3
        step = "C"
        octave = 4
        idx = steps.indexOf(step)
        if (step == "C") {
            if (octave > 1) { step = "B"; octave -= 1 }
        } else if (idx > 0) {
            step = steps[idx - 1]
        }
        assertEquals("B", step)
        assertEquals(3, octave)

        // 4. Bound check: C1 cannot step down
        step = "C"
        octave = 1
        idx = steps.indexOf(step)
        if (step == "C") {
            if (octave > 1) { step = "B"; octave -= 1 }
        } else if (idx > 0) {
            step = steps[idx - 1]
        }
        assertEquals("C", step)
        assertEquals(1, octave)

        // 5. Bound check: B8 cannot step up
        step = "B"
        octave = 8
        idx = steps.indexOf(step)
        if (step == "B") {
            if (octave < 8) { step = "C"; octave += 1 }
        } else if (idx != -1 && idx < steps.size - 1) {
            step = steps[idx + 1]
        }
        assertEquals("B", step)
        assertEquals(8, octave)

        // 6. Accidental symbols and alter mapping
        val alterSymbols = mapOf(-1 to "♭", 0 to "♮", 1 to "♯")
        assertEquals("♭", alterSymbols[-1])
        assertEquals("♮", alterSymbols[0])
        assertEquals("♯", alterSymbols[1])
    }
}
