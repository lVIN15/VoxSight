package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

data class UpdateProfileRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("defaultVoicePart") val defaultVoicePart: String? = null
)

data class UserSettingsDto(
    @SerializedName("defaultVoicePart") val defaultVoicePart: String = "SOPRANO",
    @SerializedName("soundfontTone") val soundfontTone: String = "AAH",
    @SerializedName("staffDimmingOpacity") val staffDimmingOpacity: Float = 0.20f,
    @SerializedName("pitchToleranceCents") val pitchToleranceCents: Int = 25
)

data class UpdateSettingsRequest(
    @SerializedName("defaultVoicePart") val defaultVoicePart: String? = null,
    @SerializedName("soundfontTone") val soundfontTone: String? = null,
    @SerializedName("staffDimmingOpacity") val staffDimmingOpacity: Float? = null,
    @SerializedName("pitchToleranceCents") val pitchToleranceCents: Int? = null
)

data class ChangePasswordRequest(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String
)

data class SimpleMessageResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?
)

interface UserService {

    @GET("api/user/profile")
    suspend fun getProfile(@Query("userId") userId: Long): Response<UserProfileDto>

    @PUT("api/user/profile")
    suspend fun updateProfile(
        @Query("userId") userId: Long,
        @Body request: UpdateProfileRequest
    ): Response<UserProfileDto>

    @GET("api/user/settings")
    suspend fun getSettings(@Query("userId") userId: Long): Response<UserSettingsDto>

    @PUT("api/user/settings")
    suspend fun updateSettings(
        @Query("userId") userId: Long,
        @Body request: UpdateSettingsRequest
    ): Response<UserSettingsDto>

    @POST("api/user/change-password")
    suspend fun changePassword(
        @Query("userId") userId: Long,
        @Body request: ChangePasswordRequest
    ): Response<SimpleMessageResponse>
}
