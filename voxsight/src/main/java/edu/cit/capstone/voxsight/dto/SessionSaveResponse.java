package edu.cit.capstone.voxsight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * Response body returned by POST /api/session/save.
 *
 * The client uses the returned [sessionId] to correlate local state
 * with the server record (e.g. for a future "view detail" screen).
 */
@Data
@AllArgsConstructor
public class SessionSaveResponse {

    /** UUID assigned to the newly persisted PracticeSession. */
    private UUID sessionId;

    /** Echo of the persisted accuracy percentage. */
    private float performanceScore;

    /** Human-readable status message. */
    private String message;
}
