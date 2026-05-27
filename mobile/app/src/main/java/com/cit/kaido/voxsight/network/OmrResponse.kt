package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName

data class OmrResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("url") val url: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("error") val error: String?
)
