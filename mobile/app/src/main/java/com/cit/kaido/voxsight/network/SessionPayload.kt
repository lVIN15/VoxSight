package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName

/**
 * SessionPayload
 *
 * JSON body sent to POST /api/session/save.
 *
 * Mirrors [SessionSaveRequest] on the Spring Boot server.
 * Built by [PitchVisualizerController.buildSessionPayload] once the user
 * pauses or the score ends.
 */
data class SessionPayload(
    @SerializedName("userId")          val userId: String? = null,
    @SerializedName("scoreId")         val scoreId: String? = null,
    @SerializedName("scoreTitle")      val scoreTitle: String,
    @SerializedName("mode")            val mode: String = "Test Pitch",
    @SerializedName("startedAt")       val startedAt: String,         // ISO-8601 UTC
    @SerializedName("elapsedSeconds")  val elapsedSeconds: Int,
    @SerializedName("performanceScore") val performanceScore: Float,
    @SerializedName("attempts")        val attempts: List<AttemptPayload>
) {
    /**
     * One pitch-comparison event, mirroring [SessionSaveRequest.AttemptDto].
     */
    data class AttemptPayload(
        @SerializedName("noteLabel")      val noteLabel: String,
        @SerializedName("detectedHz")     val detectedHz: Float,
        @SerializedName("deviationCents") val deviationCents: Float,
        @SerializedName("match")          val match: Boolean,
        @SerializedName("timestampMs")    val timestampMs: Long
    )
}

/**
 * SessionSaveResponse
 *
 * JSON body returned by POST /api/session/save.
 */
data class SessionSaveResponse(
    @SerializedName("sessionId")        val sessionId: String,
    @SerializedName("performanceScore") val performanceScore: Float,
    @SerializedName("message")          val message: String
)
