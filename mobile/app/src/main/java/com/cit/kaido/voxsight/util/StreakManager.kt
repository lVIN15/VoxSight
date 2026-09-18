package com.cit.kaido.voxsight.util

import android.content.Context
import java.time.LocalDate

object StreakManager {
    private const val PREFS_NAME = "voxsight_streaks"
    private const val KEY_STREAK_DATES = "streak_dates"

    fun addStreakToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dates = prefs.getStringSet(KEY_STREAK_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        dates.add(LocalDate.now().toString())
        prefs.edit().putStringSet(KEY_STREAK_DATES, dates).apply()
    }

    fun getStreakDates(context: Context): Set<LocalDate> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val datesStr = prefs.getStringSet(KEY_STREAK_DATES, emptySet()) ?: emptySet()
        return datesStr.mapNotNull {
            try {
                LocalDate.parse(it)
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }
}

