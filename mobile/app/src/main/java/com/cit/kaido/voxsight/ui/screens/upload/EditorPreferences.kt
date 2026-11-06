package com.cit.kaido.voxsight.ui.screens.upload

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson

enum class PerformanceProfile(val id: String, val label: String) {
    AUTOMATIC("automatic", "Automatic"),
    PERFORMANCE("performance", "Performance"),
    QUALITY("quality", "Quality")
}

enum class ToolbarDensity(val id: String, val label: String) {
    COMPACT("compact", "Compact"),
    COMFORTABLE("comfortable", "Comfortable")
}

enum class ScoreScaling(val id: String, val label: String) {
    FIT_WIDTH("fit_width", "Fit to Width"),
    FIT_PAGE("fit_page", "Fit to Page")
}

data class EditorSettings(
    val performanceProfile: PerformanceProfile = PerformanceProfile.AUTOMATIC,
    val toolbarDensity: ToolbarDensity = ToolbarDensity.COMPACT,
    val scoreScaling: ScoreScaling = ScoreScaling.FIT_WIDTH,
    val zoomLevel: Float = 1.0f,
    val showLyrics: Boolean = true,
    val showVoiceLabels: Boolean = true,
    val showMeasureNumbers: Boolean = true,
    val showPartsLegend: Boolean = true,
    val showToolLabels: Boolean = true,
    val autoCollapseDeck: Boolean = true,
    val autoScrollToNote: Boolean = true,
    val audioPreview: Boolean = true,
    val hapticFeedback: Boolean = true,
    // Visible tool groups
    val showAddGroup: Boolean = true,
    val showNotesGroup: Boolean = true,
    val showRestsGroup: Boolean = true,
    val showModGroup: Boolean = true,
    val showCurvesGroup: Boolean = true,
    val showVoiceGroup: Boolean = true,
    val showActionsGroup: Boolean = true,
    val lastSelectedToolGroup: String = "notes"
) {
    fun toJson(): String {
        return Gson().toJson(
            mapOf(
                "performanceProfile" to performanceProfile.id,
                "toolbarDensity" to toolbarDensity.id,
                "scoreScaling" to scoreScaling.id,
                "zoomLevel" to zoomLevel,
                "showLyrics" to showLyrics,
                "showVoiceLabels" to showVoiceLabels,
                "showMeasureNumbers" to showMeasureNumbers,
                "showPartsLegend" to showPartsLegend,
                "showToolLabels" to showToolLabels,
                "autoCollapseDeck" to autoCollapseDeck,
                "autoScrollToNote" to autoScrollToNote,
                "audioPreview" to audioPreview,
                "hapticFeedback" to hapticFeedback,
                "showAddGroup" to showAddGroup,
                "showNotesGroup" to showNotesGroup,
                "showRestsGroup" to showRestsGroup,
                "showModGroup" to showModGroup,
                "showCurvesGroup" to showCurvesGroup,
                "showVoiceGroup" to showVoiceGroup,
                "showActionsGroup" to showActionsGroup,
                "lastSelectedToolGroup" to lastSelectedToolGroup
            )
        )
    }
}

/**
 * Manages persistent state for VoxSight music editor settings,
 * device capability detection, and profile resolution.
 */
object EditorPreferences {
    private const val PREFS_NAME = "voxsight_editor_preferences"

    private const val KEY_PROFILE = "pref_performance_profile"
    private const val KEY_DENSITY = "pref_toolbar_density"
    private const val KEY_SCALING = "pref_score_scaling"
    private const val KEY_ZOOM = "pref_zoom_level"
    private const val KEY_LYRICS = "pref_show_lyrics"
    private const val KEY_VOICE_LABELS = "pref_show_voice_labels"
    private const val KEY_MEASURE_NUMBERS = "pref_show_measure_numbers"
    private const val KEY_PARTS_LEGEND = "pref_show_parts_legend"
    private const val KEY_TOOL_LABELS = "pref_show_tool_labels"
    private const val KEY_AUTO_COLLAPSE = "pref_auto_collapse_deck"
    private const val KEY_AUTO_SCROLL = "pref_auto_scroll_to_note"
    private const val KEY_AUDIO_PREVIEW = "pref_audio_preview"
    private const val KEY_HAPTIC = "pref_haptic_feedback"
    private const val KEY_GROUP_ADD = "pref_group_add"
    private const val KEY_GROUP_NOTES = "pref_group_notes"
    private const val KEY_GROUP_RESTS = "pref_group_rests"
    private const val KEY_GROUP_MOD = "pref_group_mod"
    private const val KEY_GROUP_CURVES = "pref_group_curves"
    private const val KEY_GROUP_VOICE = "pref_group_voice"
    private const val KEY_GROUP_ACTIONS = "pref_group_actions"
    private const val KEY_LAST_TOOL_GROUP = "pref_last_tool_group"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Determines whether the current device is considered low-end / resource-constrained
     * based on ActivityManager.isLowRamDevice, available heap memory, and API level.
     */
    fun isLowEndDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val isOldApi = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        return isLowRam || maxMemoryMb < 192 || isOldApi
    }

    /**
     * Resolves the active performance profile, accounting for AUTOMATIC detection.
     * Returns true if performance mode (lightweight rendering) should be used.
     */
    fun isEffectivePerformanceMode(context: Context, settings: EditorSettings): Boolean {
        return when (settings.performanceProfile) {
            PerformanceProfile.PERFORMANCE -> true
            PerformanceProfile.QUALITY -> false
            PerformanceProfile.AUTOMATIC -> isLowEndDevice(context)
        }
    }

    /**
     * Loads the stored editor settings from SharedPreferences, falling back to sensible defaults.
     */
    fun loadSettings(context: Context): EditorSettings {
        val p = getPrefs(context)
        val profileStr = p.getString(KEY_PROFILE, PerformanceProfile.AUTOMATIC.id)
        val profile = PerformanceProfile.values().find { it.id == profileStr } ?: PerformanceProfile.AUTOMATIC

        val densityStr = p.getString(KEY_DENSITY, ToolbarDensity.COMPACT.id)
        val density = ToolbarDensity.values().find { it.id == densityStr } ?: ToolbarDensity.COMPACT

        val scalingStr = p.getString(KEY_SCALING, ScoreScaling.FIT_WIDTH.id)
        val scaling = ScoreScaling.values().find { it.id == scalingStr } ?: ScoreScaling.FIT_WIDTH

        return EditorSettings(
            performanceProfile = profile,
            toolbarDensity = density,
            scoreScaling = scaling,
            zoomLevel = p.getFloat(KEY_ZOOM, 1.0f),
            showLyrics = p.getBoolean(KEY_LYRICS, true),
            showVoiceLabels = p.getBoolean(KEY_VOICE_LABELS, true),
            showMeasureNumbers = p.getBoolean(KEY_MEASURE_NUMBERS, true),
            showPartsLegend = p.getBoolean(KEY_PARTS_LEGEND, true),
            showToolLabels = p.getBoolean(KEY_TOOL_LABELS, true),
            autoCollapseDeck = p.getBoolean(KEY_AUTO_COLLAPSE, true),
            autoScrollToNote = p.getBoolean(KEY_AUTO_SCROLL, true),
            audioPreview = p.getBoolean(KEY_AUDIO_PREVIEW, true),
            hapticFeedback = p.getBoolean(KEY_HAPTIC, true),
            showAddGroup = p.getBoolean(KEY_GROUP_ADD, true),
            showNotesGroup = p.getBoolean(KEY_GROUP_NOTES, true),
            showRestsGroup = p.getBoolean(KEY_GROUP_RESTS, true),
            showModGroup = p.getBoolean(KEY_GROUP_MOD, true),
            showCurvesGroup = p.getBoolean(KEY_GROUP_CURVES, true),
            showVoiceGroup = p.getBoolean(KEY_GROUP_VOICE, true),
            showActionsGroup = p.getBoolean(KEY_GROUP_ACTIONS, true),
            lastSelectedToolGroup = p.getString(KEY_LAST_TOOL_GROUP, "notes") ?: "notes"
        )
    }

    /**
     * Persists all editor settings into SharedPreferences.
     */
    fun saveSettings(context: Context, settings: EditorSettings) {
        getPrefs(context).edit().apply {
            putString(KEY_PROFILE, settings.performanceProfile.id)
            putString(KEY_DENSITY, settings.toolbarDensity.id)
            putString(KEY_SCALING, settings.scoreScaling.id)
            putFloat(KEY_ZOOM, settings.zoomLevel)
            putBoolean(KEY_LYRICS, settings.showLyrics)
            putBoolean(KEY_VOICE_LABELS, settings.showVoiceLabels)
            putBoolean(KEY_MEASURE_NUMBERS, settings.showMeasureNumbers)
            putBoolean(KEY_PARTS_LEGEND, settings.showPartsLegend)
            putBoolean(KEY_TOOL_LABELS, settings.showToolLabels)
            putBoolean(KEY_AUTO_COLLAPSE, settings.autoCollapseDeck)
            putBoolean(KEY_AUTO_SCROLL, settings.autoScrollToNote)
            putBoolean(KEY_AUDIO_PREVIEW, settings.audioPreview)
            putBoolean(KEY_HAPTIC, settings.hapticFeedback)
            putBoolean(KEY_GROUP_ADD, settings.showAddGroup)
            .putBoolean(KEY_GROUP_NOTES, settings.showNotesGroup)
            putBoolean(KEY_GROUP_RESTS, settings.showRestsGroup)
            putBoolean(KEY_GROUP_MOD, settings.showModGroup)
            putBoolean(KEY_GROUP_CURVES, settings.showCurvesGroup)
            putBoolean(KEY_GROUP_VOICE, settings.showVoiceGroup)
            putBoolean(KEY_GROUP_ACTIONS, settings.showActionsGroup)
            putString(KEY_LAST_TOOL_GROUP, settings.lastSelectedToolGroup)
            apply()
        }
    }

    /**
     * Updates the last selected tool group to remember user preference.
     */
    fun saveLastToolGroup(context: Context, groupName: String) {
        getPrefs(context).edit().putString(KEY_LAST_TOOL_GROUP, groupName).apply()
    }
}
