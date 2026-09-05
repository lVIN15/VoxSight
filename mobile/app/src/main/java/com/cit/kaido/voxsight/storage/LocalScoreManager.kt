package com.cit.kaido.voxsight.storage

import android.content.Context
import android.net.Uri
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.parseMusicXmlScore
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cit.kaido.voxsight.util.ScoreValidator
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class LocalScoreMetadata(
    val id: String,
    val title: String,
    val composer: String,
    val xmlFileName: String,
    val eventsJson: String?,
    val metadataJson: String?,
    val timestamp: Long
)

object LocalScoreManager {
    private const val DIRECTORY_NAME = "saved_scores"

    private fun getScoresDir(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun saveScore(context: Context, score: MusicXmlScore): LocalScoreMetadata = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val id = UUID.randomUUID().toString()
        val xmlFileName = "$id.musicxml"
        val xmlFile = File(scoresDir, xmlFileName)

        // Save raw XML
        FileOutputStream(xmlFile).use { output ->
            output.write(score.rawXml.toByteArray(Charsets.UTF_8))
        }

        // Save metadata
        val metadata = LocalScoreMetadata(
            id = id,
            title = score.title,
            composer = score.composer,
            xmlFileName = xmlFileName,
            eventsJson = score.eventsJson,
            metadataJson = score.metadataJson,
            timestamp = System.currentTimeMillis()
        )
        val metadataFile = File(scoresDir, "$id.json")
        val gson = Gson()
        FileOutputStream(metadataFile).use { output ->
            output.write(gson.toJson(metadata).toByteArray(Charsets.UTF_8))
        }

        metadata
    }

    suspend fun loadSavedScores(context: Context): List<LocalScoreMetadata> = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val files = scoresDir.listFiles() ?: return@withContext emptyList()
        val gson = Gson()
        val list = mutableListOf<LocalScoreMetadata>()
        
        for (file in files) {
            if (file.name.endsWith(".json")) {
                try {
                    val jsonStr = file.readText(Charsets.UTF_8)
                    val meta = gson.fromJson(jsonStr, LocalScoreMetadata::class.java)
                    val xmlFile = File(scoresDir, meta.xmlFileName)
                    if (xmlFile.exists()) {
                        // Strict validation gate: purge any saved scores that contain solo or piano parts
                        val xmlContent = xmlFile.readText(Charsets.UTF_8)
                        val unsupportedReason = ScoreValidator.checkUnsupportedMusicXml(xmlContent)
                        if (unsupportedReason != null) {
                            xmlFile.delete()
                            file.delete()
                        } else {
                            list.add(meta)
                        }
                    } else {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        list.sortedByDescending { it.timestamp }
    }

    suspend fun loadFullScore(context: Context, metadata: LocalScoreMetadata): MusicXmlScore? = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val xmlFile = File(scoresDir, metadata.xmlFileName)
        if (!xmlFile.exists()) return@withContext null

        val score = parseMusicXmlScore(context, Uri.fromFile(xmlFile), metadata.title) ?: return@withContext null
        val unsupported = ScoreValidator.checkUnsupportedMusicXml(score.rawXml)
        if (unsupported != null) {
            deleteScore(context, metadata.id)
            return@withContext null
        }

        score.copy(
            eventsJson = metadata.eventsJson,
            metadataJson = metadata.metadataJson
        )
    }

    suspend fun deleteScore(context: Context, id: String) = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val xmlFile = File(scoresDir, "$id.musicxml")
        val jsonFile = File(scoresDir, "$id.json")
        if (xmlFile.exists()) xmlFile.delete()
        if (jsonFile.exists()) jsonFile.delete()
    }

    suspend fun clearAllScores(context: Context) = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        scoresDir.listFiles()?.forEach { it.delete() }
    }
}
