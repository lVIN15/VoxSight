package com.cit.kaido.voxsight.model

import com.google.gson.annotations.SerializedName

enum class SATBVoice {
    SOPRANO, ALTO, TENOR, BASS, UNKNOWN;

    companion object {
        fun fromString(value: String?): SATBVoice {
            if (value == null) return UNKNOWN
            val upper = value.uppercase()
            if (upper.startsWith("S")) return SOPRANO
            if (upper.startsWith("A")) return ALTO
            if (upper.startsWith("T")) return TENOR
            if (upper.startsWith("B")) return BASS
            return UNKNOWN
        }
    }
}

/**
 * Normalized musical event from the SATB analysis pipeline.
 *
 * event_id = t{tick}-p{part}-c{chord_index}
 *
 * Architecture Contract (v3.7):
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
    @SerializedName("is_rest") val isRest: Boolean = false,
    @SerializedName("is_chord_member") val isChordMember: Boolean = false,
    @SerializedName("tie_type") val tieType: String? = null,
    @SerializedName("playback_track") val playbackTrack: String = "",
    @SerializedName("satb_voice") val satbVoice: String = "UNKNOWN",
    @SerializedName("satb_confidence") val satbConfidence: Float = 0f,
    @SerializedName("schema_version") val schemaVersion: String = "1.0"
) {
    val satbEnum: SATBVoice
        get() = SATBVoice.fromString(satbVoice)
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
