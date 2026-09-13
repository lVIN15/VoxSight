package com.cit.kaido.voxsight.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String
)

data class UserProfileDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("username") val username: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("defaultVoicePart") val defaultVoicePart: String? = "SOPRANO",
    @SerializedName("soundfontTone") val soundfontTone: String? = "AAH",
    @SerializedName("staffDimmingOpacity") val staffDimmingOpacity: Float? = 0.20f,
    @SerializedName("pitchToleranceCents") val pitchToleranceCents: Int? = 25,
    @SerializedName("memberStatus") val memberStatus: String? = "FREE"
)

data class AuthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("token") val token: String?,
    @SerializedName("user") val user: UserProfileDto?
)

interface AuthService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
}
