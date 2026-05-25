package edu.cit.capstone.voxsight.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * PitchAttempt
 *
 * Records the outcome of a single pitch comparison event during a
 * "Test Pitch" practice session.
 *
 * SDD ERD (§4.1):
 *   PitchAttempt
 *     o attempt_id      : UUID   <<PK>>
 *     o session_id      : UUID   <<FK>>  → PracticeSession
 *     o note_id         : UUID   <<FK>>  → NoteEvent (score note being tested)
 *     detected_hz       : FLOAT
 *     deviation_cents   : FLOAT
 *     is_match          : BOOLEAN
 *     timestamp_ms      : LONG
 */
@Entity
@Table(name = "pitch_attempt")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PitchAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id", updatable = false, nullable = false)
    private UUID attemptId;

    /**
     * Owning session — many attempts belong to one session.
     * Using a plain UUID FK (not a @ManyToOne) to avoid eager-loading the
     * full session graph on every attempt query.
     */
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /**
     * Identifies which note in the score was being tested.
     * Human-readable label (e.g. "A4", "C#5") stored for debugging;
     * full NoteEvent linking is done server-side when score data is available.
     */
    @Column(name = "note_label", length = 16)
    private String noteLabel;

    /** Frequency detected by TarsosDSP YIN in Hz. */
    @Column(name = "detected_hz")
    private float detectedHz;

    /**
     * Signed cent deviation: positive = sharp, negative = flat.
     * Formula: 1200 × log₂(detectedHz / targetHz).
     */
    @Column(name = "deviation_cents")
    private float deviationCents;

    /**
     * True when |deviationCents| ≤ PitchComparator.TOLERANCE_CENTS (5 ¢).
     * Denormalized for fast aggregation queries.
     */
    @Column(name = "is_match", nullable = false)
    private boolean match;

    /** System.currentTimeMillis() at the moment of detection on the client. */
    @Column(name = "timestamp_ms")
    private long timestampMs;
}
