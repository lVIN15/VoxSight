package com.cit.kaido.voxsight.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String? = null, // Supabase UUID
    val username: String,
    val email: String,
    @SerialName("password_hash")
    val passwordHash: String,
    @SerialName("account_created")
    val accountCreated: String? = null,
    @SerialName("last_login")
    val lastLogin: String? = null
)
