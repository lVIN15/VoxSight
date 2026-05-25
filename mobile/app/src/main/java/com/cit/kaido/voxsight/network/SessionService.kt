package com.cit.kaido.voxsight.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the Session Metrics endpoint.
 *
 * SDD back-end component:
 *   SessionMetricsController  →  POST /api/session/save
 */
interface SessionService {

    /**
     * Persists a completed or paused practice session.
     *
     * Called by [PitchVisualizerController.flushSession] once the user
     * pauses playback or the score reaches its end.
     *
     * @param payload  Aggregated session data including all pitch attempts.
     * @return         [SessionSaveResponse] containing the assigned sessionId
     *                 and the server-reconciled performance score.
     */
    @POST("api/session/save")
    suspend fun saveSession(@Body payload: SessionPayload): SessionSaveResponse
}
