package edu.cit.capstone.voxsight.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * PracticeSession
 *
 * Persists one complete practice run for a user on a given score.
 *
 * SDD ERD (§4.1):
 *   PracticeSession
 *     o session_id    : UUID  <<PK>>
 *     o user_id       : UUID  <<FK>>
 *     o score_id      : UUID  <<FK>>
 *     mode            : VARCHAR  -- 'Listening' | 'Test Pitch'
 *     started_at      : TIMESTAMP
 *     performance_score : FLOAT  -- aggregated accuracy (0.0–100.0)
 */
@Entity
@Table(name = "practice_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID sessionId;

    /** Foreign key — nullable for anonymous/guest users in the prototype. */
    @Column(name = "user_id")
    private UUID userId;

    /** Logical reference to the digitized score; not a hard FK in the prototype. */
    @Column(name = "score_id")
    private UUID scoreId;

    /** Human-readable title of the score (denormalized for easy display). */
    @Column(name = "score_title", length = 255)
    private String scoreTitle;

    /** "Listening" or "Test Pitch" as defined in the SRS. */
    @Column(name = "mode", length = 32)
    private String mode;

    /** Wall-clock UTC instant when the session was started. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Elapsed playback time in seconds. */
    @Column(name = "elapsed_seconds")
    private int elapsedSeconds;

    /**
     * Aggregated pitch accuracy as a percentage (0.0–100.0).
     * Formula: (matchCount / totalAttempts) * 100.
     * Persisted by [SessionMetricsController] once the session ends.
     */
    @Column(name = "performance_score")
    private float performanceScore;
}
