package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.ui.graphics.Color
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBVoice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class VoicePart(val label: String, val shortLabel: String, val color: Color) {
    Soprano("Soprano", "S.", Color(0xFFE91E63)),
    Soprano1("Soprano 1", "S1", Color(0xFFFF4081)),
    Soprano2("Soprano 2", "S2", Color(0xFFC2185B)),
    Alto("Alto", "A.", Color(0xFF9C27B0)),
    Alto1("Alto 1", "A1", Color(0xFFBA68C8)),
    Alto2("Alto 2", "A2", Color(0xFF6A1B9A)),
    Tenor("Tenor", "T.", Color(0xFF2196F3)),
    Tenor1("Tenor 1", "T1", Color(0xFF00B0FF)),
    Tenor2("Tenor 2", "T2", Color(0xFF1565C0)),
    Bass("Bass", "B.", Color(0xFF4CAF50)),
    Bass1("Bass 1", "B1", Color(0xFF8BC34A)),
    Bass2("Bass 2", "B2", Color(0xFF2E7D32)),
    Solo("Solo", "Solo", Color(0xFFFF9800)),
    Others("Others", "Other", Color(0xFF607D8B));

    val apiCode: String
        get() = when (this) {
            Soprano -> "S"
            Soprano1 -> "S1"
            Soprano2 -> "S2"
            Alto -> "A"
            Alto1 -> "A1"
            Alto2 -> "A2"
            Tenor -> "T"
            Tenor1 -> "T1"
            Tenor2 -> "T2"
            Bass -> "B"
            Bass1 -> "B1"
            Bass2 -> "B2"
            Solo -> "SOLO"
            Others -> "OTHERS"
        }
}

fun VoicePart.toSATBVoice(): SATBVoice = when (this) {
    VoicePart.Soprano, VoicePart.Soprano1, VoicePart.Soprano2 -> SATBVoice.SOPRANO
    VoicePart.Alto, VoicePart.Alto1, VoicePart.Alto2 -> SATBVoice.ALTO
    VoicePart.Tenor, VoicePart.Tenor1, VoicePart.Tenor2 -> SATBVoice.TENOR
    VoicePart.Bass, VoicePart.Bass1, VoicePart.Bass2 -> SATBVoice.BASS
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
 * Deterministic Pitch Target Matcher for Singer Practice (Fix #Phase2.3 & Phase 3 Divisi/Unison).
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

    val selectedKey = selectedPart.apiCode
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
    var hasSoprano1 = false
    var hasSoprano2 = false
    var hasAlto = false
    var hasAlto1 = false
    var hasAlto2 = false
    var hasTenor = false
    var hasTenor1 = false
    var hasTenor2 = false
    var hasBass = false
    var hasBass1 = false
    var hasBass2 = false
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
                if (parts.contains("S")) hasSoprano = true
                if (parts.contains("S1")) hasSoprano1 = true
                if (parts.contains("S2")) hasSoprano2 = true
                if (parts.contains("A")) hasAlto = true
                if (parts.contains("A1")) hasAlto1 = true
                if (parts.contains("A2")) hasAlto2 = true
                if (parts.contains("T")) hasTenor = true
                if (parts.contains("T1")) hasTenor1 = true
                if (parts.contains("T2")) hasTenor2 = true
                if (parts.contains("B")) hasBass = true
                if (parts.contains("B1")) hasBass1 = true
                if (parts.contains("B2")) hasBass2 = true
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
        if (nameLower.contains("soprano 1") || nameLower == "s1" || nameLower == "s 1") hasSoprano1 = true
        if (nameLower.contains("soprano 2") || nameLower == "s2" || nameLower == "s 2") hasSoprano2 = true
        if (nameLower.contains("alto 1") || nameLower == "a1" || nameLower == "a 1") hasAlto1 = true
        if (nameLower.contains("alto 2") || nameLower == "a2" || nameLower == "a 2") hasAlto2 = true
        if (nameLower.contains("tenor 1") || nameLower == "t1" || nameLower == "t 1") hasTenor1 = true
        if (nameLower.contains("tenor 2") || nameLower == "t2" || nameLower == "t 2") hasTenor2 = true
        if (nameLower.contains("bass 1") || nameLower == "b1" || nameLower == "b 1") hasBass1 = true
        if (nameLower.contains("bass 2") || nameLower == "b2" || nameLower == "b 2") hasBass2 = true

        if (!hasSoprano1 && !hasSoprano2 && (nameLower.contains("soprano") || nameLower == "s" || nameLower.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(nameLower))) hasSoprano = true
        if (!hasAlto1 && !hasAlto2 && (nameLower.contains("alto") || nameLower == "a" || nameLower.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(nameLower))) hasAlto = true
        if (!hasTenor1 && !hasTenor2 && (nameLower.contains("tenor") || nameLower == "t" || nameLower.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(nameLower))) hasTenor = true
        if (!hasBass1 && !hasBass2 && (nameLower.contains("bass") || nameLower == "b" || nameLower.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(nameLower))) hasBass = true

        if (listOf("solo", "cantor", "leader", "descant").any { nameLower.contains(it) }) hasSolo = true
        if (listOf("piano", "organ", "keyboard", "guitar", "accomp", "orch", "strings", "other", "instrument").any { nameLower.contains(it) }) hasOthers = true
    }

    val result = mutableListOf<VoicePart>()
    if (hasSoprano1) result.add(VoicePart.Soprano1)
    if (hasSoprano2) result.add(VoicePart.Soprano2)
    if (hasSoprano || (!hasSoprano1 && !hasSoprano2 && hasSoprano)) {
        if (!result.contains(VoicePart.Soprano)) result.add(VoicePart.Soprano)
    }

    if (hasAlto1) result.add(VoicePart.Alto1)
    if (hasAlto2) result.add(VoicePart.Alto2)
    if (hasAlto || (!hasAlto1 && !hasAlto2 && hasAlto)) {
        if (!result.contains(VoicePart.Alto)) result.add(VoicePart.Alto)
    }

    if (hasTenor1) result.add(VoicePart.Tenor1)
    if (hasTenor2) result.add(VoicePart.Tenor2)
    if (hasTenor || (!hasTenor1 && !hasTenor2 && hasTenor)) {
        if (!result.contains(VoicePart.Tenor)) result.add(VoicePart.Tenor)
    }

    if (hasBass1) result.add(VoicePart.Bass1)
    if (hasBass2) result.add(VoicePart.Bass2)
    if (hasBass || (!hasBass1 && !hasBass2 && hasBass)) {
        if (!result.contains(VoicePart.Bass)) result.add(VoicePart.Bass)
    }

    if (hasSolo) result.add(VoicePart.Solo)
    if (hasOthers) result.add(VoicePart.Others)

    // Fallback: Default to standard SATB
    return if (result.isNotEmpty()) result else listOf(VoicePart.Soprano, VoicePart.Alto, VoicePart.Tenor, VoicePart.Bass)
}
