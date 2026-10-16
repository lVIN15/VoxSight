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
    val isPremium: Boolean? = null
)

@Serializable
data class UserPremiumUpdate(
    @SerialName("is_premium")
    val isPremium: Boolean
)

object PremiumManager {
    private const val PREFS_NAME = "voxsight_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_LAST_SESSION_ID = "paymongo_last_session_id"

    private fun userScopedKey(identifier: String): String {
        return "is_premium_${identifier.trim().lowercase()}"
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

        // Persist to Supabase User table if user is logged in
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

                val remotePremium = rows.firstOrNull()?.isPremium == true
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
            .remove("logged_in_email")
            .apply()
    }
}

