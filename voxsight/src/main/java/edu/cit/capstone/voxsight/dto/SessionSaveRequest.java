package edu.cit.capstone.voxsight.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * SessionSaveRequest
 *
 * JSON payload sent by the Android client to POST /api/session/save.
 *
 * Mirrors the SDD-specified SessionMetricsController payload:
 *   "overall accuracy percentage, elapsed duration, and an array of
 *    flagged missed notes once the user pauses or completes a song."
 *
 * All UUID fields are optional strings in the prototype (no auth system yet).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSaveRequest {

    /** Nullable — absent for guest/anonymous sessions. */
    private String userId;

    /** Nullable — absent if the score was not fetched from the backend. */
    private String scoreId;

    /** Human-readable score title for denormalized storage. */
    private String scoreTitle;

    /** "Listening" or "Test Pitch". */
    private String mode;

    /** ISO-8601 UTC string, e.g. "2026-05-25T10:30:00Z". */
    private String startedAt;

    /** How many seconds of the score were played. */
    private int elapsedSeconds;

    /**
     * Pre-computed accuracy from the client.
     * Formula: (matchCount / totalAttempts) × 100.
     * Range: 0.0–100.0.
     */
    private float performanceScore;

    /**
     * Individual pitch attempt records for full granularity storage.
     * May be empty when the client only sends the aggregated score.
     */
    private List<AttemptDto> attempts;

    // ── Nested DTO ─────────────────────────────────────────────────────────

    /**
     * One-to-one mirror of the SDD PitchAttempt entity, flattened for JSON.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptDto {
        private String noteLabel;       // e.g. "A4"
        private float  detectedHz;
        private float  deviationCents;
        private boolean match;
        private long   timestampMs;
    }
}
