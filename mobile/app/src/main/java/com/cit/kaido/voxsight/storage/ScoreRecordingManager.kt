package com.cit.kaido.voxsight.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val durationMs: Int
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
     * Tags and moves a finalized WAV recording with the achieved pitch accuracy percentage.
     * Filename: take_<timestamp>_acc<accuracy>.wav
     */
    fun tagRecordingFileWithAccuracy(
        file: File,
        accuracyPercentage: Int?
    ): File {
        if (!file.exists()) return file
        return try {
            val parent = file.parentFile ?: return file
            val timestamp = System.currentTimeMillis()
            val accPart = if (accuracyPercentage != null) "_acc$accuracyPercentage" else ""
            val destFile = File(parent, "take_${timestamp}${accPart}.wav")
            if (file.renameTo(destFile)) {
                destFile
            } else {
                file.copyTo(destFile, overwrite = true)
                try { file.delete() } catch (_: Exception) {}
                destFile
            }
        } catch (e: Exception) {
            Log.e("ScoreRecordingManager", "Failed to tag recording file", e)
            file
        }
    }

    /**
     * Lists all recorded vocal takes for the given score.
     * If scoreId or scoreTitle is provided, returns ONLY the takes recorded for that specific score.
     * Never falls back to general/ or other score folders, guaranteeing separate storage.
     */
    fun getRecordings(
        context: Context,
        scoreId: String? = null,
        scoreTitle: String? = null
    ): List<ScoreRecordingItem> {
        val rootDir = File(context.filesDir, DIRECTORY_NAME)
        if (!rootDir.exists()) return emptyList()

        // When neither scoreId nor scoreTitle is provided, return all recordings across the library
        if (scoreId.isNullOrBlank() && scoreTitle.isNullOrBlank()) {
            val allFiles = mutableListOf<File>()
            rootDir.listFiles()?.forEach { sub ->
                if (sub.isDirectory) {
                    sub.listFiles { _, name -> name.endsWith(".wav") }?.let { allFiles.addAll(it) }
                } else if (sub.name.endsWith(".wav")) {
                    allFiles.add(sub)
                }
            }
            return allFiles.mapNotNull { file ->
                val parentName = file.parentFile?.name
                    ?.removePrefix("score_")
                    ?.replace("_", " ") ?: "Music Score"
                parseRecordingItem(file, scoreTitle = parentName, scoreId = null)
            }.sortedByDescending { it.timestamp }
        }

        // Query the dedicated folder for this specific score
        val dir = getRecordingsDir(context, scoreId = scoreId, scoreTitle = scoreTitle)
        val files = dir.listFiles { _, name -> name.endsWith(".wav") }?.toMutableList() ?: mutableListOf()

        val list = files.mapNotNull { file ->
            parseRecordingItem(file, scoreTitle = scoreTitle ?: "Music Score", scoreId = scoreId)
        }
        return list.sortedByDescending { it.timestamp }
    }

    /**
     * Overload for callers supplying only scoreTitle.
     */
    fun getRecordings(context: Context, scoreTitle: String?): List<ScoreRecordingItem> {
        return getRecordings(context, scoreId = null, scoreTitle = scoreTitle)
    }

    /**
     * Deletes a recording take file.
     */
    fun deleteRecording(file: File): Boolean {
        return try {
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

        return ScoreRecordingItem(
            file = file,
            scoreId = scoreId,
            scoreTitle = scoreTitle,
            timestamp = timestamp,
            formattedDate = formattedDate,
            accuracyPercentage = accuracy,
            durationMs = durationMs
        )
    }
}
