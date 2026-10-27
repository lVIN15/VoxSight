package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName

/**
 * Data model for asynchronous OMR job polling.
 */
data class OmrJobStatusResponse(
    @SerializedName("jobId") val jobId: String? = null,
    @SerializedName("status") val status: String? = null, // "PROCESSING", "COMPLETED", "FAILED"
    @SerializedName("progressPercent") val progressPercent: Int = 0,
    @SerializedName("phaseText") val phaseText: String? = null,
    @SerializedName("result") val result: OmrAnalysisResponse? = null,
    @SerializedName("error") val error: String? = null
)
