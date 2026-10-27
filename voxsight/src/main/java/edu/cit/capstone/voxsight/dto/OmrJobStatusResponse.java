package edu.cit.capstone.voxsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Asynchronous job status response for long-running multi-page OMR conversions.
 * Allows client to poll progress, update notifications, and retrieve the final OmrAnalysisResponse.
 */
public record OmrJobStatusResponse(
    String jobId,
    String status, // "PROCESSING", "COMPLETED", "FAILED"
    int progressPercent,
    String phaseText,
    @JsonProperty("result")
    OmrAnalysisResponse result,
    String error
) {
    public static OmrJobStatusResponse processing(String jobId, int progressPercent, String phaseText) {
        return new OmrJobStatusResponse(jobId, "PROCESSING", progressPercent, phaseText, null, null);
    }

    public static OmrJobStatusResponse completed(String jobId, OmrAnalysisResponse result) {
        return new OmrJobStatusResponse(jobId, "COMPLETED", 100, "Score ready!", result, null);
    }

    public static OmrJobStatusResponse failed(String jobId, String error) {
        return new OmrJobStatusResponse(jobId, "FAILED", 0, "Processing failed", null, error);
    }
}
