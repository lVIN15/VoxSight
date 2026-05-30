package edu.cit.capstone.voxsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Enhanced OMR response that carries both the raw MusicXML string
 * and the SATB analysis metadata + normalized event timeline.
 *
 * Architecture Contract (v3.7):
 *   - musicxml field contains the RAW, UNTOUCHED MusicXML string
 *   - events list is ORDER-FROZEN (Fix #40) — never re-sort downstream
 *   - Schema version is always present
 */
public record OmrAnalysisResponse(
    boolean success,
    String error,

    /** Raw, untouched MusicXML string from Audiveris */
    @JsonProperty("musicxml")
    String musicXml,

    /** Score-level metadata including SATB confidence and validation results */
    @JsonProperty("score_metadata")
    Map<String, Object> scoreMetadata,

    /**
     * ORDER-FROZEN normalized event timeline.
     * Each entry is a Map with fields defined by schema_version 1.0.
     * event_id = t{tick}-p{part}-c{chord_index}
     */
    @JsonProperty("events")
    List<Map<String, Object>> events,

    /** Schema version for forward compatibility */
    @JsonProperty("schema_version")
    String schemaVersion
) {
    public static OmrAnalysisResponse ofSuccess(
            String musicXml,
            Map<String, Object> scoreMetadata,
            List<Map<String, Object>> events,
            String schemaVersion) {
        return new OmrAnalysisResponse(true, null, musicXml, scoreMetadata, events, schemaVersion);
    }

    public static OmrAnalysisResponse ofError(String error) {
        return new OmrAnalysisResponse(false, error, null, null, null, null);
    }

    /** Fallback: wrap a legacy OmrResponse (URL-based) for backward compat */
    public static OmrAnalysisResponse ofLegacy(OmrResponse legacy) {
        if (legacy.success()) {
            return new OmrAnalysisResponse(true, null, null, null, null, null);
        }
        return ofError(legacy.error());
    }
}
