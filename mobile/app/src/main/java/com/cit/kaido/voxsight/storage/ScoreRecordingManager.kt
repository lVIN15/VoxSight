package com.cit.kaido.voxsight.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed performance summary and feedback metrics saved per vocal take.
 */
data class TakeSummaryData(
    val accuracyPercentage: Float = 0f,
    val totalNotesAttempted: Int = 0,
    val correctNotes: Int = 0,
    val averageDeviationCents: Float = 0f,
    val satbVoice: String? = null,
    val vocalHighlightHighest: String? = null,
    val vocalHighlightLowest: String? = null,
    val problematicNotes: List<ProblematicNoteData> = emptyList(),
    val topProblematicMeasure: ProblematicMeasureData? = null
)

data class ProblematicNoteData(
    val noteName: String,
    val averageDeviation: Float,
    val isSharp: Boolean
)

data class ProblematicMeasureData(
    val measureNumber: Int,
    val mistakeCount: Int,
    val totalNotes: Int,
    val isSharp: Boolean,
    val averageDeviation: Float
)

/**
 * Model representing a single recorded vocal practice take for a score.
 */
data class ScoreRecordingItem(
    val file: File,
    val scoreId: String? = null,
    val scoreTitle: String,
    val timestamp: Long,
    val formattedDate: String,
    val accuracyPercentage: Int?,
    val durationMs: Int,
    val summary: TakeSummaryData? = null,
    val accuracyDelta: Int? = null
)

/**
 * ScoreRecordingManager — Manages persistent storage, retrieval, and tagging
 * of recorded vocal takes associated with specific converted music sheets.
 *
 * Each music sheet has a completely isolated storage directory keyed primarily by its
 * unique score ID (or score title as fallback), ensuring that duplicate music sheets or
 * sheets with identical titles maintain separate, isolated playback takes.
 */
object ScoreRecordingManager {
    private const val DIRECTORY_NAME = "score_recordings"
    private val gson = Gson()

    /**
     * Resolves the isolated recording directory for a specific score.
     * Prefers scoreId for unique per-score isolation so duplicates never share storage.
     */
    fun getRecordingsDir(context: Context, scoreId: String?, scoreTitle: String? = null): File {
        val folderName = when {
            !scoreId.isNullOrBlank() -> "score_${sanitizeIdentifier(scoreId)}"
            !scoreTitle.isNullOrBlank() -> "score_${sanitizeIdentifier(scoreTitle)}"
            else -> "general"
        }
        val dir = File(context.filesDir, "$DIRECTORY_NAME/$folderName")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Overload for callers supplying only scoreTitle.
     */
    fun getRecordingsDir(context: Context, scoreTitle: String?): File {
        return getRecordingsDir(context, scoreId = null, scoreTitle = scoreTitle)
    }

    fun sanitizeIdentifier(identifier: String?): String {
        return (identifier ?: "general")
            .trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(64)
            .ifEmpty { "score" }
    }

    fun sanitizeTitle(title: String?): String {
        return sanitizeIdentifier(title)
    }

    /**
     * Saves or updates detailed performance summary JSON for a specific take.
     */
    fun saveTakeSummary(wavFile: File, summary: TakeSummaryData) {
        try {
            val jsonFile = File(wavFile.parentFile, "${wavFile.nameWithoutExtension}.json")
            jsonFile.writeText(gson.toJson(summary))
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to save take summary for ${wavFile.name}", e)
        }
    }

    /**
     * Tags and moves a finalized WAV recording with the achieved pitch accuracy percentage,
     * and persists the companion summary feedback JSON.
     * Filename: take_<timestamp>_acc<accuracy>.wav & take_<timestamp>_acc<accuracy>.json
     */
    fun tagRecordingFileWithAccuracy(
        file: File,
        accuracyPercentage: Int?,
        summary: TakeSummaryData? = null
    ): File {
        if (!file.exists()) return file
        return try {
            val parent = file.parentFile ?: return file
            val timestamp = System.currentTimeMillis()
            val accPart = if (accuracyPercentage != null) "_acc$accuracyPercentage" else ""
            val destFile = File(parent, "take_${timestamp}${accPart}.wav")
            val renamed = if (file.renameTo(destFile)) {
                destFile
            } else {
                file.copyTo(destFile, overwrite = true)
                try { file.delete() } catch (_: Exception) {}
                destFile
            }
            if (summary != null) {
                saveTakeSummary(renamed, summary)
            }
            renamed
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to tag recording file", e)
            file
        }
    }

    /**
     * Lists all recorded vocal takes for the given score.
     * Computes accuracyDelta (+/- %) across chronological attempts.
     */
    fun getRecordings(
        context: Context,
        scoreId: String? = null,
        scoreTitle: String? = null
    ): List<ScoreRecordingItem> {
        val rootDir = File(context.filesDir, DIRECTORY_NAME)
        if (!rootDir.exists()) return emptyList()

        val rawList = if (scoreId.isNullOrBlank() && scoreTitle.isNullOrBlank()) {
            val allFiles = mutableListOf<File>()
            rootDir.listFiles()?.forEach { sub ->
                if (sub.isDirectory) {
                    sub.listFiles { _, name -> name.endsWith(".wav") }?.let { allFiles.addAll(it) }
                } else if (sub.name.endsWith(".wav")) {
                    allFiles.add(sub)
                }
            }
            allFiles.mapNotNull { file ->
                val parentName = file.parentFile?.name
                    ?.removePrefix("score_")
                    ?.replace("_", " ") ?: "Music Score"
                parseRecordingItem(file, scoreTitle = parentName, scoreId = null)
            }
        } else {
            val dir = getRecordingsDir(context, scoreId = scoreId, scoreTitle = scoreTitle)
            val files = dir.listFiles { _, name -> name.endsWith(".wav") }?.toMutableList() ?: mutableListOf()
            files.mapNotNull { file ->
                parseRecordingItem(file, scoreTitle = scoreTitle ?: "Music Score", scoreId = scoreId)
            }
        }

        // Group by score (or process as a list) to compute accuracy deltas chronologically
        val chronological = rawList.sortedBy { it.timestamp }
        val withDeltas = chronological.mapIndexed { idx, item ->
            val delta = if (idx > 0 && item.accuracyPercentage != null && chronological[idx - 1].accuracyPercentage != null) {
                item.accuracyPercentage - chronological[idx - 1].accuracyPercentage!!
            } else null
            item.copy(accuracyDelta = delta)
        }

        return withDeltas.sortedByDescending { it.timestamp }
    }

    /**
     * Overload for callers supplying only scoreTitle.
     */
    fun getRecordings(context: Context, scoreTitle: String?): List<ScoreRecordingItem> {
        return getRecordings(context, scoreId = null, scoreTitle = scoreTitle)
    }

    /**
     * Deletes a recording take file and its companion summary metadata JSON.
     */
    fun deleteRecording(file: File): Boolean {
        return try {
            val jsonFile = File(file.parentFile, "${file.nameWithoutExtension}.json")
            if (jsonFile.exists()) {
                jsonFile.delete()
            }
            file.delete()
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to delete recording: ${file.name}", e)
            false
        }
    }

    /**
     * Deletes the entire isolated recording directory for a specific score ID.
     */
    fun deleteScoreRecordingsDir(context: Context, scoreId: String?): Boolean {
        if (scoreId.isNullOrBlank()) return false
        val dir = File(context.filesDir, "$DIRECTORY_NAME/score_${sanitizeIdentifier(scoreId)}")
        return try {
            if (dir.exists()) {
                dir.deleteRecursively()
            } else false
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to delete score recordings dir: ${dir.name}", e)
            false
        }
    }

    /**
     * Clears all score recordings on the device.
     */
    fun clearAllScoreRecordings(context: Context): Boolean {
        val rootDir = File(context.filesDir, DIRECTORY_NAME)
        return try {
            if (rootDir.exists()) {
                rootDir.deleteRecursively()
            } else false
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to clear all score recordings", e)
            false
        }
    }

    private fun parseRecordingItem(
        file: File,
        scoreTitle: String,
        scoreId: String? = null
    ): ScoreRecordingItem? {
        if (!file.exists()) return null
        val name = file.nameWithoutExtension
        var timestamp = file.lastModified()
        var accuracy: Int? = null

        try {
            if (name.contains("_acc")) {
                val parts = name.split("_acc")
                if (parts.size >= 2) {
                    accuracy = parts[1].toIntOrNull()
                }
                val tsPart = parts[0].removePrefix("take_").removePrefix("practice_take_")
                tsPart.toLongOrNull()?.let { timestamp = it }
            } else {
                val tsPart = name.removePrefix("take_").removePrefix("practice_take_")
                tsPart.toLongOrNull()?.let { timestamp = it }
            }
        } catch (_: Exception) {}

        val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(timestamp))

        var durationMs = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationMs = time?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            val dataBytes = (file.length() - 44).coerceAtLeast(0)
            durationMs = ((dataBytes * 1000) / 44100).toInt()
        }

        var summaryData: TakeSummaryData? = null
        try {
            val jsonFile = File(file.parentFile, "${file.nameWithoutExtension}.json")
            if (jsonFile.exists()) {
                summaryData = gson.fromJson(jsonFile.readText(), TakeSummaryData::class.java)
            }
        } catch (e: Exception) {
            Log.w("ScoreRecordingManager", "Failed to parse summary json for ${file.name}", e)
        }

        if (summaryData == null && accuracy != null) {
            summaryData = TakeSummaryData(accuracyPercentage = accuracy.toFloat())
        }

        return ScoreRecordingItem(
            file = file,
            scoreId = scoreId,
            scoreTitle = scoreTitle,
            timestamp = timestamp,
            formattedDate = formattedDate,
            accuracyPercentage = accuracy,
            durationMs = durationMs,
            summary = summaryData
        )
    }
}
