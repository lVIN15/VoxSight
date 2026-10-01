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
    val scoreTitle: String,
    val timestamp: Long,
    val formattedDate: String,
    val accuracyPercentage: Int?,
    val durationMs: Int
)

/**
 * ScoreRecordingManager — Manages persistent storage, retrieval, and tagging
 * of recorded vocal takes associated with specific converted music sheets.
 */
object ScoreRecordingManager {
    private const val DIRECTORY_NAME = "score_recordings"

    fun getRecordingsDir(context: Context, scoreTitle: String?): File {
        val safeTitle = sanitizeTitle(scoreTitle)
        val dir = File(context.filesDir, "$DIRECTORY_NAME/$safeTitle")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun sanitizeTitle(title: String?): String {
        return (title ?: "general")
            .trim()
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .take(64)
            .ifEmpty { "score" }
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
     * Lists all recorded vocal takes for the given score title, ordered from newest to oldest.
     * If scoreTitle is null or empty, lists all recorded vocal takes across all scores.
     */
    fun getRecordings(context: Context, scoreTitle: String?): List<ScoreRecordingItem> {
        val rootDir = File(context.filesDir, DIRECTORY_NAME)
        if (!rootDir.exists()) return emptyList()

        if (scoreTitle.isNullOrBlank()) {
            val allFiles = mutableListOf<File>()
            rootDir.listFiles()?.forEach { sub ->
                if (sub.isDirectory) {
                    sub.listFiles { _, name -> name.endsWith(".wav") }?.let { allFiles.addAll(it) }
                } else if (sub.name.endsWith(".wav")) {
                    allFiles.add(sub)
                }
            }
            return allFiles.mapNotNull { file ->
                val parentName = file.parentFile?.name?.replace("_", " ") ?: "Music Score"
                parseRecordingItem(file, parentName)
            }.sortedByDescending { it.timestamp }
        }

        val dir = getRecordingsDir(context, scoreTitle)
        val files = dir.listFiles { _, name -> name.endsWith(".wav") }?.toMutableList() ?: mutableListOf()

        // Also check if recordings were saved under general/fallback
        if (files.isEmpty()) {
            val generalDir = File(rootDir, "general")
            generalDir.listFiles { _, name -> name.endsWith(".wav") }?.let { files.addAll(it) }
        }

        val list = files.mapNotNull { file ->
            parseRecordingItem(file, scoreTitle)
        }
        return list.sortedByDescending { it.timestamp }
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

    private fun parseRecordingItem(file: File, scoreTitle: String): ScoreRecordingItem? {
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
            scoreTitle = scoreTitle,
            timestamp = timestamp,
            formattedDate = formattedDate,
            accuracyPercentage = accuracy,
            durationMs = durationMs
        )
    }
}
