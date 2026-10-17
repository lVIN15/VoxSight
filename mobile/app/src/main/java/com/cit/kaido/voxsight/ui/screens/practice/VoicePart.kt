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

val SECTION_DIVISI_MAP: Map<String, Set<String>> = mapOf(
    "S" to setOf("S", "S1", "S2"),
    "A" to setOf("A", "A1", "A2"),
    "T" to setOf("T", "T1", "T2"),
    "B" to setOf("B", "B1", "B2")
)

val DIVISI_TO_PARENT_MAP: Map<String, String> = mapOf(
    "S1" to "S", "S2" to "S",
    "A1" to "A", "A2" to "A",
    "T1" to "T", "T2" to "T",
    "B1" to "B", "B2" to "B"
)

/**
 * Deterministic Pitch Target Matcher for Singer Practice (Fix #Phase2.3).
 *
 * Strict Relational Mapping with ZERO startsWith usage:
 * 1. Non-vocal notes (isVocal = false or empty parts) NEVER receive pitch targets.
 * 2. Accompaniment (VoicePart.Others) NEVER receives pitch targets.
 * 3. Choral Tutti / ALL matches all active vocal singers.
 * 4. Exact part key match (or participant in cross-section unisons like ["T", "B"]).
 * 5. Parent section matches configured divisi parts (Soprano matches S, S1, S2).
 * 6. Divisi part matches undivided parent section (S1 matches S).
 */
fun isEventTargetForSinger(selectedPart: VoicePart, event: MusicalEvent): Boolean {
    // 1. Strict Fail-Safe Gating: Non-vocal notes NEVER receive pitch targets
    if (!event.isVocal || event.participatingParts.isEmpty()) {
        return false
    }

    // 2. Others (Accompaniment) is never evaluated by the singer mic
    if (selectedPart == VoicePart.Others) {
        return false
    }

    val selectedKey = when (selectedPart) {
        VoicePart.Solo -> "SOLO"
        VoicePart.Others -> "OTHERS"
        VoicePart.Soprano -> "S"
        VoicePart.Alto -> "A"
        VoicePart.Tenor -> "T"
        VoicePart.Bass -> "B"
    }
    val parts = event.participatingParts.map { it.uppercase().trim() }

    // 3. Tutti / Universal Choral Match (ALL singers sing Tutti)
    if (parts.contains("ALL")) {
        return true
    }

    // 4. Direct Match (Exact part match or participant in cross-section unison like ["T", "B"])
    if (parts.contains(selectedKey)) {
        return true
    }

    // 5. Parent Section Selection (User selected "Soprano" / "S")
    // Explicitly matches S, S1, or S2 via SECTION_DIVISI_MAP - ZERO startsWith usage!
    val allowedParts = SECTION_DIVISI_MAP[selectedKey]
    if (allowedParts != null && parts.any { it in allowedParts }) {
        return true
    }

    // 6. Divisi Sub-Part Match (if selectedPart is Divisi like S1 and event is parent "S")
    val parent = DIVISI_TO_PARENT_MAP[selectedKey]
    if (parent != null && parts.contains(parent)) {
        return true
    }

    return false
}

fun detectAvailableParts(score: MusicXmlScore): List<VoicePart> {
    var hasSoprano = false
    var hasAlto = false
    var hasTenor = false
    var hasBass = false
    var hasSolo = false
    var hasOthers = false

    // 1. Check parsed events if present (using safe participatingParts)
    if (!score.eventsJson.isNullOrBlank() && score.eventsJson != "[]") {
        try {
            val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
            val events: List<MusicalEvent>? = Gson().fromJson(score.eventsJson, eventType)
            events?.forEach { ev ->
                val parts = ev.participatingParts
                if (parts.contains("SOLO")) hasSolo = true
                if (parts.contains("OTHERS")) hasOthers = true
                if (parts.any { it in setOf("S", "S1", "S2") }) hasSoprano = true
                if (parts.any { it in setOf("A", "A1", "A2") }) hasAlto = true
                if (parts.any { it in setOf("T", "T1", "T2") }) hasTenor = true
                if (parts.any { it in setOf("B", "B1", "B2") }) hasBass = true
                if (parts.contains("ALL")) {
                    hasSoprano = true
                    hasAlto = true
                    hasTenor = true
                    hasBass = true
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
