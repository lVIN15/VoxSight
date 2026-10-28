package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.ui.graphics.Color
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.model.normalizeLegacyVoiceToParts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class VoicePart(val label: String, val shortLabel: String, val color: Color) {
    Soprano("Soprano", "S.", Color(0xFFE11D48)),
    Soprano1("Soprano 1", "S1", Color(0xFFFB7185)),
    Soprano2("Soprano 2", "S2", Color(0xFF881337)),
    Alto("Alto", "A.", Color(0xFF8E24AA)),
    Alto1("Alto 1", "A1", Color(0xFFC084FC)),
    Alto2("Alto 2", "A2", Color(0xFF4C1D95)),
    Tenor("Tenor", "T.", Color(0xFF2563EB)),
    Tenor1("Tenor 1", "T1", Color(0xFF0284C7)),
    Tenor2("Tenor 2", "T2", Color(0xFF1E3A8A)),
    Bass("Bass", "B.", Color(0xFF16A34A)),
    Bass1("Bass 1", "B1", Color(0xFF65A30D)),
    Bass2("Bass 2", "B2", Color(0xFF064E3B)),
    Solo("Solo", "Solo", Color(0xFFFF6D00)),
    BaritoneSolo("Baritone Solo", "Bar. Solo", Color(0xFFFF6D00)),
    Others("Others", "Other", Color(0xFF546E7A));

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
            BaritoneSolo -> "BARITONE SOLO"
            Others -> "OTHERS"
        }
}

fun VoicePart.toSATBVoice(): SATBVoice = when (this) {
    VoicePart.Soprano, VoicePart.Soprano1, VoicePart.Soprano2 -> SATBVoice.SOPRANO
    VoicePart.Alto, VoicePart.Alto1, VoicePart.Alto2 -> SATBVoice.ALTO
    VoicePart.Tenor, VoicePart.Tenor1, VoicePart.Tenor2 -> SATBVoice.TENOR
    VoicePart.Bass, VoicePart.Bass1, VoicePart.Bass2 -> SATBVoice.BASS
    VoicePart.Solo, VoicePart.BaritoneSolo -> SATBVoice.SOLO
    VoicePart.Others -> SATBVoice.OTHERS
}

val VoicePart.isSoloPart: Boolean
    get() = this == VoicePart.Solo || this == VoicePart.BaritoneSolo

val VoicePart.isSpecialOrSolo: Boolean
    get() = this.isSoloPart || this == VoicePart.Others

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

fun VoicePart.getDisplayLabel(availableParts: Collection<VoicePart> = emptyList()): String {
    return when (this) {
        VoicePart.Bass -> if (availableParts.any { it == VoicePart.Bass1 || it == VoicePart.Bass2 }) "Bass (All)" else "Bass"
        VoicePart.Tenor -> if (availableParts.any { it == VoicePart.Tenor1 || it == VoicePart.Tenor2 }) "Tenor (All)" else "Tenor"
        VoicePart.Alto -> if (availableParts.any { it == VoicePart.Alto1 || it == VoicePart.Alto2 }) "Alto (All)" else "Alto"
        VoicePart.Soprano -> if (availableParts.any { it == VoicePart.Soprano1 || it == VoicePart.Soprano2 }) "Soprano (All)" else "Soprano"
        else -> this.label
    }
}

fun VoicePart.getDisplayShortLabel(availableParts: Collection<VoicePart> = emptyList()): String {
    return when (this) {
        VoicePart.Bass -> if (availableParts.any { it == VoicePart.Bass1 || it == VoicePart.Bass2 }) "B. (All)" else "B."
        VoicePart.Tenor -> if (availableParts.any { it == VoicePart.Tenor1 || it == VoicePart.Tenor2 }) "T. (All)" else "T."
        VoicePart.Alto -> if (availableParts.any { it == VoicePart.Alto1 || it == VoicePart.Alto2 }) "A. (All)" else "A."
        VoicePart.Soprano -> if (availableParts.any { it == VoicePart.Soprano1 || it == VoicePart.Soprano2 }) "S. (All)" else "S."
        else -> this.shortLabel
    }
}

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
    if (selectedPart == VoicePart.Solo && parts.contains("BARITONE SOLO")) return true
    if (selectedPart == VoicePart.BaritoneSolo && (parts.contains("BARITONE SOLO") || parts.contains("SOLO"))) return true

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

/**
 * Determines whether a MusicalEvent matches a target voice part or visual focus part.
 * Used for audio playback gating and visual playback highlight filtering.
 *
 * - When targetPart is null: all active vocal notes match (no filtering).
 * - When targetPart is a divisi subdivision (e.g. "B1"):
 *   - Matches notes assigned specifically to "B1".
 *   - Matches undivided parent section notes ("B") that both subdivisions sing.
 *   - Rejects sibling subdivisions ("B2") to maintain strict isolation.
 *   - Matches unisons (UNISON_B1B2, UNISON_TB, TUTTI/ALL).
 * - When targetPart is a parent section (e.g. "B"):
 *   - Matches "B", "B1", "B2", UNISON_TB, and TUTTI/ALL.
 */
fun isEventMatchingFocusOrPart(targetPart: String?, event: MusicalEvent): Boolean {
    if (targetPart.isNullOrBlank()) return true
    val focus = targetPart.trim().uppercase()
    val eventVoices = event.participatingParts.ifEmpty { normalizeLegacyVoiceToParts(event.satbVoice) }
        .map { it.trim().uppercase() }

    if (eventVoices.isEmpty()) return true

    // 1. Tutti / Universal Choral Unison: All vocal singers participate
    if (eventVoices.any { it == "ALL" || it == "TUTTI" || it == "UNISON_ALL" }) {
        return focus !in setOf("OTHER", "OTHERS")
    }

    // 2. Direct exact match (e.g. "B1" == "B1", "SOLO" == "SOLO", or unison ["B1", "B2"])
    if (eventVoices.contains(focus)) {
        return true
    }

    // 3. Parent section selection (e.g. Focus is "B" -> intentionally includes "B", "B1", "B2")
    val allowedDivisi = SECTION_DIVISI_MAP[focus]
    if (allowedDivisi != null && eventVoices.any { it in allowedDivisi }) {
        return true
    }

    // 4. Divisi focus matching undivided parent notes (e.g. Focus is "B1" or "B2", note is parent "B")
    val parent = DIVISI_TO_PARENT_MAP[focus]
    if (parent != null && eventVoices.contains(parent)) {
        return true
    }

    // 5. Cross-section unisons (e.g. UNISON_TB has ["T", "B"])
    if (eventVoices.any { it == "UNISON_TB" || it == "TB" }) {
        val effective = parent ?: focus
        if (effective == "T" || effective == "B") return true
    }
    if (eventVoices.any { it == "UNISON_SA" || it == "SA" }) {
        val effective = parent ?: focus
        if (effective == "S" || effective == "A") return true
    }

    return false
}

object VoicePalette {
    const val SOPRANO = "#E11D48"
    const val ALTO = "#8E24AA"
    const val TENOR = "#2563EB"
    const val BASS = "#16A34A"
    const val SOLO = "#FF6D00"
    const val OTHER = "#546E7A"
    const val UNKNOWN = "#808080"

    fun asJson(): String = """{"S":"$SOPRANO","A":"$ALTO","T":"$TENOR","B":"$BASS","SOLO":"$SOLO","OTHER":"$OTHER","UNKNOWN":"$UNKNOWN"}"""
}

fun getVoiceHexColor(partCode: String): String = when (partCode.trim().uppercase()) {
    "S", "SOPRANO" -> "#E11D48"
    "S1", "SOPRANO 1", "SOPRANO1" -> "#FB7185"
    "S2", "SOPRANO 2", "SOPRANO2" -> "#881337"
    "A", "ALTO" -> "#8E24AA"
    "A1", "ALTO 1", "ALTO1" -> "#C084FC"
    "A2", "ALTO 2", "ALTO2" -> "#4C1D95"
    "T", "TENOR" -> "#2563EB"
    "T1", "TENOR 1", "TENOR1" -> "#0284C7"
    "T2", "TENOR 2", "TENOR2" -> "#1E3A8A"
    "B", "BASS" -> "#16A34A"
    "B1", "BASS 1", "BASS1" -> "#65A30D"
    "B2", "BASS 2", "BASS2" -> "#064E3B"
    "UNISON_SA", "SA" -> "#4F46E5"
    "UNISON_TB", "TB" -> "#0D9488"
    "UNISON_S1S2", "S1S2" -> "#E11D48"
    "UNISON_A1A2", "A1A2" -> "#8E24AA"
    "UNISON_T1T2", "T1T2" -> "#2563EB"
    "UNISON_B1B2", "B1B2" -> "#16A34A"
    "TUTTI", "UNISON_ALL", "ALL" -> "#CA8A04"
    "SOLO", "BARITONE SOLO", "BARITONE_SOLO", "BARITONE", "TENOR SOLO", "SOPRANO SOLO", "ALTO SOLO", "BASS SOLO" -> "#FF6D00"
    "OTHER", "OTHERS" -> "#546E7A"
    else -> "#6366f1"
}

fun getHighlightColorForEvent(event: MusicalEvent, targetFocusPart: String?): String {
    val voice = event.satbVoice.uppercase().trim()
    val parts = (if (event.participatingParts.isNotEmpty()) event.participatingParts else listOf(voice))
        .map { it.uppercase().trim() }

    // If focused on a specific line (e.g. B1) and note is for B1 or undivided B, use the focused line color
    if (targetFocusPart != null) {
        val upperFocus = targetFocusPart.trim().uppercase()
        if (parts.contains(upperFocus)) {
            return getVoiceHexColor(upperFocus)
        }
        val parent = DIVISI_TO_PARENT_MAP[upperFocus]
        if (parent != null && parts.contains(parent)) {
            return getVoiceHexColor(upperFocus)
        }
    }

    // Otherwise use note's own primary voice color
    val primaryPart = when {
        parts.contains("UNISON_SA") || parts.contains("SA") -> "UNISON_SA"
        parts.contains("UNISON_TB") || parts.contains("TB") -> "UNISON_TB"
        parts.contains("UNISON_S1S2") || parts.contains("S1S2") -> "UNISON_S1S2"
        parts.contains("UNISON_A1A2") || parts.contains("A1A2") -> "UNISON_A1A2"
        parts.contains("UNISON_T1T2") || parts.contains("T1T2") -> "UNISON_T1T2"
        parts.contains("UNISON_B1B2") || parts.contains("B1B2") -> "UNISON_B1B2"
        parts.contains("TUTTI") || parts.contains("ALL") -> "TUTTI"
        parts.contains("B2") -> "B2"
        parts.contains("B1") -> "B1"
        parts.contains("B") -> "B"
        parts.contains("T2") -> "T2"
        parts.contains("T1") -> "T1"
        parts.contains("T") -> "T"
        parts.contains("A2") -> "A2"
        parts.contains("A1") -> "A1"
        parts.contains("A") -> "A"
        parts.contains("S2") -> "S2"
        parts.contains("S1") -> "S1"
        parts.contains("S") -> "S"
        parts.contains("BARITONE SOLO") -> "BARITONE SOLO"
        parts.contains("SOLO") -> "SOLO"
        parts.contains("OTHERS") || parts.contains("OTHER") -> "OTHER"
        else -> voice
    }
    return getVoiceHexColor(primaryPart)
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
                if (parts.contains("SOLO") || parts.contains("BARITONE SOLO")) hasSolo = true
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

    // 2. Check direct notes voices (including string tokens like S1, S2, UNISON_TB)
    score.notes.forEach { note ->
        if (!note.customVoiceStr.isNullOrBlank()) {
            when (note.customVoiceStr.uppercase()) {
                "S", "SOPRANO" -> hasSoprano = true
                "S1" -> hasSoprano1 = true
                "S2" -> hasSoprano2 = true
                "A", "ALTO" -> hasAlto = true
                "A1" -> hasAlto1 = true
                "A2" -> hasAlto2 = true
                "T", "TENOR" -> hasTenor = true
                "T1" -> hasTenor1 = true
                "T2" -> hasTenor2 = true
                "B", "BASS" -> hasBass = true
                "B1" -> hasBass1 = true
                "B2" -> hasBass2 = true
                "UNISON_TB", "TB" -> { hasTenor = true; hasBass = true }
                "UNISON_SA", "SA" -> { hasSoprano = true; hasAlto = true }
                "UNISON_S1S2", "S1S2" -> { hasSoprano1 = true; hasSoprano2 = true }
                "TUTTI", "UNISON_ALL", "ALL" -> { hasSoprano = true; hasAlto = true; hasTenor = true; hasBass = true }
                "SOLO", "BARITONE SOLO", "BARITONE" -> hasSolo = true
                "OTHERS", "OTHER" -> hasOthers = true
            }
        }
        if (note.customVoiceStr.isNullOrBlank() && score.parts.size >= 4) {
            when (note.voice) {
                1 -> hasSoprano = true
                2 -> hasAlto = true
                3 -> hasTenor = true
                4 -> hasBass = true
                5 -> hasSolo = true
                6 -> hasOthers = true
            }
        } else if (note.customVoiceStr.isNullOrBlank()) {
            when (note.voice) {
                5 -> hasSolo = true
                6 -> hasOthers = true
            }
        }
    }

    // 3. Check parts metadata and notes in parts
    score.parts.forEach { part ->
        val nameLower = part.name.lowercase().trim()
        val isInstrumental = listOf(
            "piano", "organ", "keyboard", "guitar", "accomp", "orch",
            "strings", "instrument", "violin", "flute", "trumpet", "drum", "percussion", "clarinet", "saxophone"
        ).any { nameLower.contains(it) }

        if (isInstrumental) {
            hasOthers = true
            return@forEach
        }

        if (nameLower.contains("soprano 1") || nameLower == "s1" || nameLower == "s 1") hasSoprano1 = true
        if (nameLower.contains("soprano 2") || nameLower == "s2" || nameLower == "s 2") hasSoprano2 = true
        if (nameLower.contains("alto 1") || nameLower == "a1" || nameLower == "a 1") hasAlto1 = true
        if (nameLower.contains("alto 2") || nameLower == "a2" || nameLower == "a 2") hasAlto2 = true
        if (nameLower.contains("tenor 1") || nameLower == "t1" || nameLower == "t 1") hasTenor1 = true
        if (nameLower.contains("tenor 2") || nameLower == "t2" || nameLower == "t 2") hasTenor2 = true
        if (nameLower.contains("bass 1") || nameLower == "b1" || nameLower == "b 1") hasBass1 = true
        if (nameLower.contains("bass 2") || nameLower == "b2" || nameLower == "b 2") hasBass2 = true

        val isSA = (nameLower.contains("soprano") && nameLower.contains("alto")) ||
            nameLower.contains("s/a") || nameLower.contains("s & a") || nameLower.contains("s.a.") || nameLower.contains("s. a.") || nameLower == "sa"
        val isTB = (nameLower.contains("tenor") && nameLower.contains("bass")) ||
            nameLower.contains("t/b") || nameLower.contains("t & b") || nameLower.contains("t.b.") || nameLower.contains("t. b.") || nameLower == "tb"

        if (isSA) {
            hasSoprano = true
            hasAlto = true
        } else {
            if (!hasSoprano1 && !hasSoprano2 && (nameLower.contains("soprano") || nameLower == "s" || nameLower.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(nameLower))) hasSoprano = true
            if (!hasAlto1 && !hasAlto2 && (nameLower.contains("alto") || nameLower == "a" || nameLower.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(nameLower))) hasAlto = true
        }

        if (isTB) {
            hasTenor = true
            hasBass = true
        } else {
            if (!hasTenor1 && !hasTenor2 && (nameLower.contains("tenor") || nameLower == "t" || nameLower.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(nameLower))) hasTenor = true
            if (!hasBass1 && !hasBass2 && (nameLower.contains("bass") || nameLower == "b" || nameLower.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(nameLower))) hasBass = true
        }

        if (listOf("solo", "cantor", "leader", "descant", "baritone", "sol.", "sol").any { nameLower.contains(it) }) hasSolo = true
        if (nameLower.contains("other")) hasOthers = true
    }

    val result = mutableListOf<VoicePart>()

    // Sibling divisi promotion: If sub-voice 2 is present along with generic voice, promote generic voice to Voice 1
    if (hasSoprano2 && hasSoprano) hasSoprano1 = true
    if (hasAlto2 && hasAlto) hasAlto1 = true
    if (hasTenor2 && hasTenor) hasTenor1 = true
    if (hasBass2 && hasBass) hasBass1 = true

    // Soprano Section
    if (hasSoprano1 && hasSoprano2) {
        result.add(VoicePart.Soprano1)
        result.add(VoicePart.Soprano2)
    } else if (hasSoprano || hasSoprano1 || hasSoprano2) {
        result.add(VoicePart.Soprano)
    }

    // Alto Section
    if (hasAlto1 && hasAlto2) {
        result.add(VoicePart.Alto1)
        result.add(VoicePart.Alto2)
    } else if (hasAlto || hasAlto1 || hasAlto2) {
        result.add(VoicePart.Alto)
    }

    // Tenor Section
    if (hasTenor1 && hasTenor2) {
        result.add(VoicePart.Tenor1)
        result.add(VoicePart.Tenor2)
    } else if (hasTenor || hasTenor1 || hasTenor2) {
        result.add(VoicePart.Tenor)
    }

    // Bass Section
    if (hasBass1 && hasBass2) {
        result.add(VoicePart.Bass1)
        result.add(VoicePart.Bass2)
    } else if (hasBass || hasBass1 || hasBass2) {
        result.add(VoicePart.Bass)
    }

    val hasBaritoneSolo = score.parts.any { it.name.contains("baritone", ignoreCase = true) } ||
        score.notes.any { it.customVoiceStr?.contains("BARITONE", ignoreCase = true) == true }

    if (hasBaritoneSolo) {
        result.add(VoicePart.BaritoneSolo)
    } else if (hasSolo) {
        result.add(VoicePart.Solo)
    }
    if (hasOthers) result.add(VoicePart.Others)

    // Fallback: Default to standard SATB
    return if (result.isNotEmpty()) result else listOf(VoicePart.Soprano, VoicePart.Alto, VoicePart.Tenor, VoicePart.Bass)
}
