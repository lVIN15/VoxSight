package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName

data class OmrResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("fileName") val fileName: String?
)
