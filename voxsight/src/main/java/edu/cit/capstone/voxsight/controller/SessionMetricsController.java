package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.SessionSaveRequest;
import edu.cit.capstone.voxsight.dto.SessionSaveResponse;
import edu.cit.capstone.voxsight.model.PitchAttempt;
import edu.cit.capstone.voxsight.model.PracticeSession;
import edu.cit.capstone.voxsight.repository.PitchAttemptRepository;
import edu.cit.capstone.voxsight.repository.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SessionMetricsController
 *
 * Implements the SDD-specified back-end component:
 *
 *   "Receives the final aggregated practice session payload (overall
 *    accuracy percentage, elapsed duration, and an array of flagged
 *    missed notes) once the user pauses or completes a song, persisting
 *    the historical data to the database for progress tracking."
 *
 * Endpoint: POST /api/session/save
 */
@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = "*")
public class SessionMetricsController {

    private static final Logger log = LoggerFactory.getLogger(SessionMetricsController.class);

    private final SessionRepository sessionRepository;
    private final PitchAttemptRepository attemptRepository;

    public SessionMetricsController(
            SessionRepository sessionRepository,
            PitchAttemptRepository attemptRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.attemptRepository = attemptRepository;
    }

    // ── POST /api/session/save ────────────────────────────────────────────────

    /**
     * Persists a completed (or paused) practice session.
     *
     * Workflow:
     *  1. Parse and validate the request payload.
     *  2. Persist the {@link PracticeSession} header row.
     *  3. Persist each {@link PitchAttempt} record (bulk insert).
     *  4. Optionally re-compute the performance score server-side for
     *     consistency (the client's pre-computed value is also stored).
     *  5. Return the assigned sessionId and final accuracy.
     */
    @PostMapping("/save")
    public ResponseEntity<SessionSaveResponse> saveSession(
            @RequestBody SessionSaveRequest request
    ) {
        log.info("[Session] Saving session for score='{}', mode='{}'",
                request.getScoreTitle(), request.getMode());

        // ── 1. Parse optional UUID fields ────────────────────────────────────
        UUID userId  = parseUuidSafely(request.getUserId());
        UUID scoreId = parseUuidSafely(request.getScoreId());

        // ── 2. Persist PracticeSession ────────────────────────────────────────
        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setScoreId(scoreId);
        session.setScoreTitle(sanitize(request.getScoreTitle(), "Untitled Score"));
        session.setMode(sanitize(request.getMode(), "Listening"));
        session.setStartedAt(parseInstantSafely(request.getStartedAt()));
        session.setElapsedSeconds(request.getElapsedSeconds());
        session.setPerformanceScore(request.getPerformanceScore());

        PracticeSession saved = sessionRepository.save(session);
        UUID sessionId = saved.getSessionId();

        log.info("[Session] Saved PracticeSession id={}", sessionId);

        // ── 3. Persist individual PitchAttempts ──────────────────────────────
        List<SessionSaveRequest.AttemptDto> dtos = request.getAttempts();
        if (dtos != null && !dtos.isEmpty()) {
            List<PitchAttempt> attempts = new ArrayList<>(dtos.size());
            for (SessionSaveRequest.AttemptDto dto : dtos) {
                PitchAttempt attempt = new PitchAttempt();
                attempt.setSessionId(sessionId);
                attempt.setNoteLabel(dto.getNoteLabel());
                attempt.setDetectedHz(dto.getDetectedHz());
                attempt.setDeviationCents(dto.getDeviationCents());
                attempt.setMatch(dto.isMatch());
                attempt.setTimestampMs(dto.getTimestampMs());
                attempts.add(attempt);
            }
            attemptRepository.saveAll(attempts);
            log.info("[Session] Saved {} PitchAttempt rows for session={}",
                    attempts.size(), sessionId);

            // ── 4. Re-compute server-side accuracy ────────────────────────────
            // Use the persisted rows as the source of truth so the score is
            // always consistent with the individual attempt records.
            long total = attempts.size();
            long matches = attempts.stream().filter(PitchAttempt::isMatch).count();
            float serverScore = (total > 0) ? (matches * 100f / total) : 0f;

            if (Math.abs(serverScore - saved.getPerformanceScore()) > 0.5f) {
                // Correct any client/server discrepancy
                saved.setPerformanceScore(serverScore);
                saved = sessionRepository.save(saved);
                log.info("[Session] Re-computed performance_score={:.1f}% (client sent {:.1f}%)",
                        serverScore, request.getPerformanceScore());
            }
        }

        // ── 5. Return response ────────────────────────────────────────────────
        SessionSaveResponse response = new SessionSaveResponse(
                sessionId,
                saved.getPerformanceScore(),
                "Session saved successfully."
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID parseUuidSafely(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant parseInstantSafely(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String sanitize(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
