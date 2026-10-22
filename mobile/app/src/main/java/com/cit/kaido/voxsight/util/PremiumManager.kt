package com.cit.kaido.voxsight.util

import android.content.Context
import com.cit.kaido.voxsight.network.Supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPremiumRow(
    val username: String? = null,
    val email: String? = null,
    @SerialName("is_premium")
    val isPremium: Boolean? = null,
    @SerialName("upload_credits")
    val uploadCredits: Int? = null,
    @SerialName("profile_picture")
    val profilePicture: String? = null
)

@Serializable
data class UserPremiumUpdate(
    @SerialName("is_premium")
    val isPremium: Boolean
)

@Serializable
data class UserCreditsUpdate(
    @SerialName("upload_credits")
    val uploadCredits: Int
)

object PremiumManager {
    private const val PREFS_NAME = "voxsight_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_UPLOAD_CREDITS = "upload_credits"
    private const val KEY_LAST_SESSION_ID = "paymongo_last_session_id"
    const val DEFAULT_FREE_CREDITS = 3

    private fun userScopedKey(identifier: String): String {
        return "is_premium_${identifier.trim().lowercase()}"
    }

    private fun userScopedCreditsKey(identifier: String): String {
        return "upload_credits_${identifier.trim().lowercase()}"
    }

    fun isUserPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString("logged_in_username", null)
        val email = prefs.getString("logged_in_email", null)

        if (!email.isNullOrBlank() && prefs.getBoolean(userScopedKey(email), false)) {
            return true
        }
        if (!username.isNullOrBlank() && prefs.getBoolean(userScopedKey(username), false)) {
            return true
        }
        return prefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun getUploadCredits(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString("logged_in_username", null)
        val email = prefs.getString("logged_in_email", null)

        if (!email.isNullOrBlank() && prefs.contains(userScopedCreditsKey(email))) {
            return prefs.getInt(userScopedCreditsKey(email), DEFAULT_FREE_CREDITS)
        }
        if (!username.isNullOrBlank() && prefs.contains(userScopedCreditsKey(username))) {
            return prefs.getInt(userScopedCreditsKey(username), DEFAULT_FREE_CREDITS)
        }
        if (prefs.contains(KEY_UPLOAD_CREDITS)) {
            return prefs.getInt(KEY_UPLOAD_CREDITS, DEFAULT_FREE_CREDITS)
        }
        // Initialize default 3 free uploads if not yet set for this user
        saveLocalCredits(context, DEFAULT_FREE_CREDITS, username, email)
        return DEFAULT_FREE_CREDITS
    }

    private fun saveLocalCredits(
        context: Context,
        credits: Int,
        username: String? = null,
        email: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val resolvedUsername = username ?: prefs.getString("logged_in_username", null)
        val resolvedEmail = email ?: prefs.getString("logged_in_email", null)
        val clamped = credits.coerceAtLeast(0)

        prefs.edit().apply {
            putInt(KEY_UPLOAD_CREDITS, clamped)
            if (!resolvedUsername.isNullOrBlank()) {
                putInt(userScopedCreditsKey(resolvedUsername), clamped)
            }
            if (!resolvedEmail.isNullOrBlank()) {
                putInt(userScopedCreditsKey(resolvedEmail), clamped)
            }
            apply()
        }
    }

    suspend fun initializeNewUserCredits(
        context: Context,
        username: String? = null,
        email: String? = null
    ) {
        saveLocalCredits(context, DEFAULT_FREE_CREDITS, username, email)
        syncCreditsToSupabase(context, DEFAULT_FREE_CREDITS, username, email)
    }

    suspend fun addUploadCredits(context: Context, addedCredits: Int): Int {
        val current = getUploadCredits(context)
        val updated = (current + addedCredits).coerceAtLeast(0)
        saveLocalCredits(context, updated)
        syncCreditsToSupabase(context, updated)
        return updated
    }

    suspend fun consumeUploadCredit(context: Context): Boolean {
        val current = getUploadCredits(context)
        if (current <= 0) return false
        val updated = (current - 1).coerceAtLeast(0)
        saveLocalCredits(context, updated)
        syncCreditsToSupabase(context, updated)
        return true
    }

    private suspend fun syncCreditsToSupabase(
        context: Context,
        credits: Int,
        username: String? = null,
        email: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val resolvedUsername = username ?: prefs.getString("logged_in_username", null)
        val resolvedEmail = email ?: prefs.getString("logged_in_email", null)

        withContext(Dispatchers.IO) {
            try {
                val client = Supabase.client
                if (!resolvedEmail.isNullOrBlank()) {
                    client.postgrest["User"].update(UserCreditsUpdate(uploadCredits = credits)) {
                        filter { eq("email", resolvedEmail) }
                    }
                } else if (!resolvedUsername.isNullOrBlank()) {
                    client.postgrest["User"].update(UserCreditsUpdate(uploadCredits = credits)) {
                        filter { eq("username", resolvedUsername) }
                    }
                }
            } catch (_: Exception) {
                // Gracefully ignore if Supabase 'upload_credits' column is not added yet or offline
            }
        }
    }

    suspend fun markUserPremium(context: Context, sessionId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString("logged_in_username", null)
        val email = prefs.getString("logged_in_email", null)

        prefs.edit().apply {
            putBoolean(KEY_IS_PREMIUM, true)
            if (!username.isNullOrBlank()) {
                putBoolean(userScopedKey(username), true)
            }
            if (!email.isNullOrBlank()) {
                putBoolean(userScopedKey(email), true)
            }
            if (!sessionId.isNullOrBlank()) {
                putString(KEY_LAST_SESSION_ID, sessionId)
            }
            apply()
        }

        // VoxSight Premium also includes +50 Upload Credits
        addUploadCredits(context, 50)

        // Persist is_premium to Supabase User table if user is logged in
        withContext(Dispatchers.IO) {
            try {
                val client = Supabase.client
                if (!email.isNullOrBlank()) {
                    client.postgrest["User"].update(UserPremiumUpdate(isPremium = true)) {
                        filter {
                            eq("email", email)
                        }
                    }
                } else if (!username.isNullOrBlank()) {
                    client.postgrest["User"].update(UserPremiumUpdate(isPremium = true)) {
                        filter {
                            eq("username", username)
                        }
                    }
                }
            } catch (_: Exception) {
                // Gracefully ignore if Supabase 'is_premium' column is not migrated yet or offline
            }
        }
    }

    suspend fun syncFromSupabase(context: Context, username: String? = null, email: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val resolvedUsername = username ?: prefs.getString("logged_in_username", null)
            val resolvedEmail = email ?: prefs.getString("logged_in_email", null)

            try {
                val client = Supabase.client
                val rows = if (!resolvedEmail.isNullOrBlank()) {
                    client.postgrest["User"].select {
                        filter { eq("email", resolvedEmail) }
                    }.decodeList<UserPremiumRow>()
                } else if (!resolvedUsername.isNullOrBlank()) {
                    client.postgrest["User"].select {
                        filter { eq("username", resolvedUsername) }
                    }.decodeList<UserPremiumRow>()
                } else {
                    emptyList()
                }

                val row = rows.firstOrNull()
                if (row?.uploadCredits != null) {
                    saveLocalCredits(context, row.uploadCredits, resolvedUsername, resolvedEmail)
                }
                if (!row?.profilePicture.isNullOrBlank()) {
                    prefs.edit().putString("logged_in_profile_picture", row?.profilePicture).apply()
                }

                val remotePremium = row?.isPremium == true
                if (remotePremium) {
                    prefs.edit().apply {
                        putBoolean(KEY_IS_PREMIUM, true)
                        if (!resolvedUsername.isNullOrBlank()) {
                            putBoolean(userScopedKey(resolvedUsername), true)
                        }
                        if (!resolvedEmail.isNullOrBlank()) {
                            putBoolean(userScopedKey(resolvedEmail), true)
                        }
                        apply()
                    }
                    return@withContext true
                }
            } catch (_: Exception) {
                // Ignore if column doesn't exist yet or network unavailable
            }

            isUserPremium(context)
        }
    }

    fun clearActiveSessionPremium(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_IS_PREMIUM)
            .remove(KEY_UPLOAD_CREDITS)
            .remove("logged_in_email")
            .remove("logged_in_profile_picture")
            .apply()
    }
}
