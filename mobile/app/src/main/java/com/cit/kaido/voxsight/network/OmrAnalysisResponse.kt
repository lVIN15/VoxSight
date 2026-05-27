package com.cit.kaido.voxsight.network

import com.cit.kaido.voxsight.model.MusicalEvent
import com.google.gson.annotations.SerializedName

/**
 * Enhanced response from the /api/analyze endpoint.
 * Contains raw MusicXML + SATB analysis metadata + normalized event timeline.
 *
 * Architecture Contract (v3.7):
 *   - musicxml is the RAW, UNTOUCHED MusicXML string
 *   - events list is ORDER-FROZEN (Fix #40) — never re-sort downstream
 *   - Schema version is always present
 */
data class OmrAnalysisResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("error") val error: String? = null,
    @SerializedName("musicxml") val musicXml: String? = null,
    @SerializedName("score_metadata") val scoreMetadata: ScoreMetadata? = null,
    @SerializedName("events") val events: List<MusicalEvent>? = null,
    @SerializedName("schema_version") val schemaVersion: String? = null
)

/**
 * Score-level metadata from SATB analysis.
 */
data class ScoreMetadata(
    @SerializedName("structure_type") val structureType: String = "UNCERTAIN",
    @SerializedName("satb_confidence") val satbConfidence: Float = 0f,
    @SerializedName("satb_confidence_smoothed") val satbConfidenceSmoothed: Float = 0f,
    @SerializedName("part_count") val partCount: Int = 0,
    @SerializedName("staff_count") val staffCount: Int = 0,
    @SerializedName("time_signatures") val timeSignatures: List<String> = emptyList(),
    @SerializedName("ticks_per_quarter") val ticksPerQuarter: Int = 960,
    @SerializedName("total_measures") val totalMeasures: Int = 0,
    @SerializedName("tempo_events") val tempoEvents: List<TempoEvent> = emptyList(),
    @SerializedName("corrupt_measures") val corruptMeasures: List<Int> = emptyList(),
    @SerializedName("alignment_drift") val alignmentDrift: Boolean = false,
    @SerializedName("offset_segments") val offsetSegments: List<Map<String, Any>> = emptyList(),
    @SerializedName("validation_passed") val validationPassed: Boolean = false,
    @SerializedName("playback_tracks") val playbackTracks: List<PlaybackTrack> = emptyList()
)

/**
 * Tempo marking extracted from MusicXML (Fix #34: tempo-aware drift).
 */
data class TempoEvent(
    @SerializedName("tick") val tick: Int = 0,
    @SerializedName("bpm") val bpm: Float = 120f
)

/**
 * Structural playback track with dual-layer labeling (Fix #25).
 */
data class PlaybackTrack(
    @SerializedName("track_id") val trackId: String = "",
    @SerializedName("structural_label") val structuralLabel: String = "",
    @SerializedName("satb_label") val satbLabel: String = "",
    @SerializedName("satb_confidence_applied") val satbConfidenceApplied: Boolean = false
)
