package com.cit.kaido.voxsight.model

import com.google.gson.annotations.SerializedName

/**
 * Strict whitelist sets for vocal and non-vocal parts.
 * Used for fail-safe validation of participating parts.
 */
val KNOWN_VOCAL_PARTS: Set<String> = setOf(
    "S", "S1", "S2",
    "A", "A1", "A2",
    "T", "T1", "T2",
    "B", "B1", "B2",
    "SOLO", "ALL"
)

val KNOWN_NON_VOCAL_PARTS: Set<String> = setOf(
    "OTHER", "OTHERS", "PIANO", "ACCOMP", "ORGAN", "VIOLIN", "STRINGS", "INSTRUMENT"
)

/**
 * Normalizes legacy v1.0 satb_voice strings to a list of standard part keys.
 * Handles single parts, legacy numeric codes ('1'-'6'), and compound unisons.
 */
fun normalizeLegacyVoiceToParts(voice: String?): List<String> {
    if (voice.isNullOrBlank()) return emptyList()
    val upper = voice.uppercase().trim()
    return when (upper) {
        "S", "SOPRANO", "1" -> listOf("S")
        "A", "ALTO", "2" -> listOf("A")
        "T", "TENOR", "3" -> listOf("T")
        "B", "BASS", "4" -> listOf("B")
        "S1", "SOPRANO1", "SOPRANO 1" -> listOf("S1")
        "S2", "SOPRANO2", "SOPRANO 2" -> listOf("S2")
        "A1", "ALTO1", "ALTO 1" -> listOf("A1")
        "A2", "ALTO2", "ALTO 2" -> listOf("A2")
        "T1", "TENOR1", "TENOR 1" -> listOf("T1")
        "T2", "TENOR2", "TENOR 2" -> listOf("T2")
        "B1", "BASS1", "BASS 1" -> listOf("B1")
        "B2", "BASS2", "BASS 2" -> listOf("B2")
        "SOLO", "5" -> listOf("SOLO")
        "UNISON_TB", "TB" -> listOf("T", "B")
        "UNISON_SA", "SA" -> listOf("S", "A")
        "UNISON_S1S2", "S1S2" -> listOf("S1", "S2")
        "UNISON_A1A2", "A1A2" -> listOf("A1", "A2")
        "UNISON_T1T2", "T1T2" -> listOf("T1", "T2")
        "UNISON_B1B2", "B1B2" -> listOf("B1", "B2")
        "UNISON_ALL", "TUTTI", "ALL" -> listOf("ALL") // Universal Choral Unison token
        "6", "OTHER", "OTHERS", "PIANO", "ACCOMP", "ORGAN", "VIOLIN", "STRINGS" -> listOf("OTHERS")
        else -> emptyList() // All unrecognized or contradictory tokens fail safely
    }
}

enum class SATBVoice {
    SOPRANO, ALTO, TENOR, BASS, SOLO, OTHERS, UNKNOWN;

    companion object {
        fun fromString(value: String?): SATBVoice {
            if (value == null) return UNKNOWN
            val upper = value.uppercase().trim()
            if (upper in setOf("SOLO", "5")) return SOLO
            if (upper in setOf("OTHER", "OTHERS", "PIANO", "ACCOMP", "ORGAN", "VIOLIN", "STRINGS", "INSTRUMENT", "6")) return OTHERS
            if (upper in setOf("S", "S1", "S2", "SOPRANO", "SOPRANO 1", "SOPRANO 2", "1")) return SOPRANO
            if (upper in setOf("A", "A1", "A2", "ALTO", "ALTO 1", "ALTO 2", "2")) return ALTO
            if (upper in setOf("T", "T1", "T2", "TENOR", "TENOR 1", "TENOR 2", "3")) return TENOR
            if (upper in setOf("B", "B1", "B2", "BASS", "BASS 1", "BASS 2", "4")) return BASS
            // Compound or Tutti tokens
            if (upper.startsWith("UNISON_") || upper == "TUTTI" || upper == "ALL") return UNKNOWN
            // Fallback for legacy prefixes (if not an instrument)
            if (upper.startsWith("SOLO")) return SOLO
            if (upper.startsWith("OTHER")) return OTHERS
            if (upper.startsWith("SOPRANO")) return SOPRANO
            if (upper.startsWith("ALTO")) return ALTO
            if (upper.startsWith("TENOR")) return TENOR
            if (upper.startsWith("BASS")) return BASS
            return UNKNOWN
        }
    }
}

/**
 * Normalized musical event from the SATB analysis pipeline.
 *
 * event_id = t{tick}-p{part}-c{chord_index}
 *
 * Architecture Contract (v3.7 / Schema v1.1):
 *   - event_id is deterministic via Identity Sort (Fix #37)
 *   - event_id is NEVER used for sorting (Fix #33)
 *   - This class is immutable by design
 */
data class MusicalEvent(
    @SerializedName("event_id") val eventId: String,
    @SerializedName("debug_identity") val debugIdentity: DebugIdentity? = null,
    @SerializedName("measure_number") val measureNumber: Int,
    @SerializedName("measure_index") val measureIndex: Int = 0,
    @SerializedName("tick_position") val tickPosition: Int,
    @SerializedName("ticks_per_quarter") val ticksPerQuarter: Int = 960,
    @SerializedName("pitch_midi") val pitchMidi: Int,
    @SerializedName("pitch_name") val pitchName: String,
    @SerializedName("duration_ticks") val durationTicks: Int,
    @SerializedName("duration_quarters") val durationQuarters: Float = 0f,
    @SerializedName("voice_source") val voiceSource: Int = 0,
    @SerializedName("staff_id") val staffId: Int = 1,
    @SerializedName("part_id") val partId: Int = 1,
    @SerializedName("source_part_id") val sourcePartId: String? = null,
    @SerializedName("source_note_id") val sourceNoteId: Int? = null,
    @SerializedName("assignment_source") val assignmentSource: String = "backend",
    @SerializedName("is_rest") val isRest: Boolean = false,
    @SerializedName("is_chord_member") val isChordMember: Boolean = false,
    @SerializedName("tie_type") val tieType: String? = null,
    @SerializedName("playback_track") val playbackTrack: String = "",
    @SerializedName("satb_voice") val satbVoice: String = "UNKNOWN",
    @SerializedName("satb_confidence") val satbConfidence: Float = 0f,
    @SerializedName("schema_version") val schemaVersion: String = "1.0",

    // Schema v1.1 Additive Fields
    @SerializedName("xml_element_order") val xmlElementOrder: Int = -1,
    @SerializedName("participating_parts") val rawParticipatingParts: List<String>? = null,
    @SerializedName("is_vocal") val rawIsVocal: Boolean? = null,
    @SerializedName("is_unison") val rawIsUnison: Boolean? = null,
    @SerializedName("unison_type") val unisonType: String? = null
) {
    val satbEnum: SATBVoice
        get() = SATBVoice.fromString(satbVoice)

    /**
     * Normalized participating parts with strict whitelist validation.
     * Uses property getter instead of delegated property to guarantee 100% safety
     * with Gson reflection deserialization (which bypasses constructors).
     *
     * - Pure vocal list: Accepted if ALL tokens belong to KNOWN_VOCAL_PARTS.
     * - Pure instrumental list: Normalizes to ["OTHERS"] if ALL tokens belong to KNOWN_NON_VOCAL_PARTS.
     * - Mixed/contaminated list (e.g. ["S", "GUITAR"]): FAILS SAFELY to emptyList().
     * - Legacy v1.0 fallback: Evaluated through normalizeLegacyVoiceToParts(satbVoice).
     */
    val participatingParts: List<String>
        get() {
            if (!rawParticipatingParts.isNullOrEmpty()) {
                val cleaned = rawParticipatingParts.map { it.uppercase().trim() }
                return when {
                    cleaned.all { it in KNOWN_VOCAL_PARTS } -> cleaned
                    cleaned.all { it in KNOWN_NON_VOCAL_PARTS } -> listOf("OTHERS")
                    else -> emptyList() // Contaminated or unrecognized -> Fail safely
                }
            }
            return normalizeLegacyVoiceToParts(satbVoice)
        }

    /**
     * Fail-Safe Vocal Classification:
     * A note is vocal IF AND ONLY IF:
     * 1. rawIsVocal is not explicitly false.
     * 2. participatingParts is non-empty.
     * 3. ALL parts belong to KNOWN_VOCAL_PARTS.
     * 4. ZERO parts belong to KNOWN_NON_VOCAL_PARTS.
     * Guarantees rawIsVocal is never trusted blindly over contaminated parts.
     */
    val isVocal: Boolean
        get() {
            if (rawIsVocal == false) return false
            val parts = participatingParts
            if (parts.isEmpty()) return false
            if (parts.any { it in KNOWN_NON_VOCAL_PARTS }) return false
            return parts.all { it in KNOWN_VOCAL_PARTS }
        }

    /**
     * Correct Unison Derivation (handling TUTTI and ALL semantics):
     * True if participant count > 1, OR if marked with universal unison token "ALL",
     * OR if satb_voice indicates a compound unison.
     */
    val isUnison: Boolean
        get() = rawIsUnison ?: (
            participatingParts.size > 1 ||
            participatingParts.contains("ALL") ||
            (satbVoice?.uppercase()?.startsWith("UNISON_") == true) ||
            (satbVoice?.uppercase() == "TUTTI")
        )
}

/**
 * Debug-only identity info for each event (Fix #29).
 * NOT used for identity — only for debugging and validation.
 */
data class DebugIdentity(
    @SerializedName("tick") val tick: Int,
    @SerializedName("part") val part: Int,
    @SerializedName("chord_index") val chordIndex: Int,
    @SerializedName("content_hash") val contentHash: String? = null
)

/**
 * ORDER-FROZEN event stream (Fix #40 — type-level enforcement).
 *
 * This wrapper ensures the events list CANNOT be re-sorted, modified,
 * or reordered after construction. All downstream consumers (SyncManager,
 * renderer, debug tools, UI) receive events through this wrapper.
 *
 * Exposes ONLY: get(index), forEach, size, iterator.
 * Does NOT expose: sorted(), add(), remove(), any mutation.
 */
class EventStream private constructor(
    private val data: List<MusicalEvent>
) : Iterable<MusicalEvent> {

    /** Number of events in the stream */
    val size: Int get() = data.size

    /** Get event at index */
    operator fun get(index: Int): MusicalEvent = data[index]

    /** Iterate over events in ORDER-FROZEN order */
    override fun iterator(): Iterator<MusicalEvent> = data.iterator()

    /** Check if stream is empty */
    fun isEmpty(): Boolean = data.isEmpty()

    /** Find events by predicate (returns new list, does NOT modify order) */
    fun filter(predicate: (MusicalEvent) -> Boolean): List<MusicalEvent> =
        data.filter(predicate)

    /** Find single event by event_id */
    fun findById(eventId: String): MusicalEvent? =
        data.find { it.eventId == eventId }

    /** Get all unique playback track IDs */
    fun playbackTrackIds(): Set<String> =
        data.map { it.playbackTrack }.toSet()

    companion object {
        /**
         * Create an EventStream from a list. The list is copied and frozen.
         * After this point, the order is permanently locked.
         */
        fun freeze(events: List<MusicalEvent>): EventStream =
            EventStream(events.toList()) // defensive copy

        /**
         * Create an empty EventStream.
         */
        fun empty(): EventStream = EventStream(emptyList())
    }
}

/**
 * SATB classification result (decoupled from UI — Fix #39).
 * UI consumes this through SATBDisplayPolicy, never directly.
 */
data class SATBClassification(
    val satbVoice: String,       // "S", "A", "T", "B", "UNKNOWN"
    val confidence: Float,        // 0.0 - 1.0 (clamped)
    val uncertaintyBand: Float    // std dev across measures
)

/**
 * Display policy for SATB labels (Fix #39 — policy abstraction).
 * UI NEVER reads confidence directly — always goes through this policy.
 * Policy can be swapped (e.g., per-genre thresholds) without touching classifier.
 */
object SATBDisplayPolicy {

    enum class LabelStyle { CONFIRMED, TENTATIVE, HIDDEN }

    /** Whether to show the SATB label at all */
    fun shouldShowSATBLabel(classification: SATBClassification): Boolean =
        classification.confidence >= 0.40f

    /** Determine the visual style of the SATB label */
    fun labelStyle(classification: SATBClassification): LabelStyle = when {
        classification.confidence >= 0.75f -> LabelStyle.CONFIRMED
        classification.confidence >= 0.40f -> LabelStyle.TENTATIVE
        else -> LabelStyle.HIDDEN
    }
}
