package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class AppVersionResponse(
    @SerializedName("minVersionCode") val minVersionCode: Int = 1,
    @SerializedName("minVersionName") val minVersionName: String = "1.0.0",
    @SerializedName("latestVersionName") val latestVersionName: String = "1.1.0",
    @SerializedName("downloadUrl") val downloadUrl: String = "",
    @SerializedName("releaseNotesUrl") val releaseNotesUrl: String = "",
    @SerializedName("forceUpdate") val forceUpdate: Boolean = true
)

interface AppVersionApi {
    @GET("api/app/version")
    suspend fun getAppVersion(): AppVersionResponse
}
