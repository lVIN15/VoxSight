package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.ui.graphics.Color
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBVoice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class VoicePart(val label: String, val shortLabel: String, val color: Color) {
    Soprano("Soprano", "S.", Color(0xFFE91E63)),
    Alto("Alto", "A.", Color(0xFF9C27B0)),
    Tenor("Tenor", "T.", Color(0xFF2196F3)),
    Bass("Bass", "B.", Color(0xFF4CAF50)),
    Solo("Solo", "Solo", Color(0xFFFF9800)),
    Others("Others", "Other", Color(0xFF607D8B))
}

fun VoicePart.toSATBVoice(): SATBVoice = when (this) {
    VoicePart.Soprano -> SATBVoice.SOPRANO
    VoicePart.Alto -> SATBVoice.ALTO
    VoicePart.Tenor -> SATBVoice.TENOR
    VoicePart.Bass -> SATBVoice.BASS
    VoicePart.Solo -> SATBVoice.SOLO
    VoicePart.Others -> SATBVoice.OTHERS
}

fun detectAvailableParts(score: MusicXmlScore): List<VoicePart> {
    var hasSoprano = false
    var hasAlto = false
    var hasTenor = false
    var hasBass = false
    var hasSolo = false
    var hasOthers = false

    // 1. Check parsed events if present
    if (!score.eventsJson.isNullOrBlank() && score.eventsJson != "[]") {
        try {
            val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
            val events: List<MusicalEvent>? = Gson().fromJson(score.eventsJson, eventType)
            events?.forEach { ev ->
                val v = ev.satbVoice.uppercase()
                when {
                    v.startsWith("SOLO") -> hasSolo = true
                    v.startsWith("OTHER") || v.startsWith("PIANO") || v.startsWith("ACCOMP") -> hasOthers = true
                    v.startsWith("S") -> hasSoprano = true
                    v.startsWith("A") -> hasAlto = true
                    v.startsWith("T") -> hasTenor = true
                    v.startsWith("B") -> hasBass = true
                }
            }
        } catch (_: Exception) {}
    }

    // 2. Check direct notes voices (1=Soprano, 2=Alto, 3=Tenor, 4=Bass, 5=Solo, 6=Others)
    score.notes.forEach { note ->
        when (note.voice) {
            1 -> hasSoprano = true
            2 -> hasAlto = true
            3 -> hasTenor = true
            4 -> hasBass = true
            5 -> hasSolo = true
            6 -> hasOthers = true
        }
    }

    // 3. Check parts metadata and notes in parts
    score.parts.forEach { part ->
        val nameLower = part.name.lowercase()
        if (nameLower.contains("soprano") || nameLower == "s" || nameLower.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(nameLower)) hasSoprano = true
        if (nameLower.contains("alto") || nameLower == "a" || nameLower.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(nameLower)) hasAlto = true
        if (nameLower.contains("tenor") || nameLower == "t" || nameLower.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(nameLower)) hasTenor = true
        if (nameLower.contains("bass") || nameLower == "b" || nameLower.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(nameLower)) hasBass = true
        if (listOf("solo", "cantor", "leader", "descant").any { nameLower.contains(it) }) hasSolo = true
        if (listOf("piano", "organ", "keyboard", "guitar", "accomp", "orch", "strings", "other", "instrument").any { nameLower.contains(it) }) hasOthers = true

        part.notes.forEach { note ->
            when (note.voice) {
                1 -> hasSoprano = true
                2 -> hasAlto = true
                3 -> hasTenor = true
                4 -> hasBass = true
                5 -> hasSolo = true
                6 -> hasOthers = true
            }
        }
    }

    val result = mutableListOf<VoicePart>()
    if (hasSoprano) result.add(VoicePart.Soprano)
    if (hasAlto) result.add(VoicePart.Alto)
    if (hasTenor) result.add(VoicePart.Tenor)
    if (hasBass) result.add(VoicePart.Bass)
    if (hasSolo) result.add(VoicePart.Solo)
    if (hasOthers) result.add(VoicePart.Others)

    // Fallback: If no specific parts detected, default to standard SATB
    return if (result.isNotEmpty()) result else listOf(VoicePart.Soprano, VoicePart.Alto, VoicePart.Tenor, VoicePart.Bass)
}
