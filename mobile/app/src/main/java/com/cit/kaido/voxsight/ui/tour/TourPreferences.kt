package com.cit.kaido.voxsight.ui.tour

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent state for interactive guided tours to ensure they only appear
 * during a fresh install (or until the user skips / completes them).
 */
object TourPreferences {
    private const val PREFS_NAME = "voxsight_tour_prefs"

    const val KEY_LAUNCH_USER_GUIDE = "tour_launch_user_guide_completed"
    const val KEY_PRACTICE_TOUR = "tour_practice_completed"
    const val KEY_UPLOAD_TOUR = "tour_upload_completed"
    const val KEY_REVIEW_TOUR = "tour_review_completed"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Returns true if the user has completed or skipped the specified tour.
     * Defaults to false on fresh installs.
     */
    fun isTourCompleted(context: Context, tourKey: String): Boolean {
        return getPrefs(context).getBoolean(tourKey, false)
    }

    /**
     * Marks the specified tour as completed/skipped so it will not appear again.
     */
    fun setTourCompleted(context: Context, tourKey: String, completed: Boolean = true) {
        getPrefs(context).edit().putBoolean(tourKey, completed).apply()
    }

    /**
     * Resets the tour completion status (e.g. from help menu or during testing).
     */
    fun resetTour(context: Context, tourKey: String) {
        getPrefs(context).edit().remove(tourKey).apply()
    }
}
