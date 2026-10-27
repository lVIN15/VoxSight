package com.cit.kaido.voxsight.storage

import android.content.Context
import android.net.Uri
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.ui.screens.practice.parseMusicXmlScore
import com.cit.kaido.voxsight.util.ScoreValidator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class LocalScoreMetadata(
    val id: String,
    val title: String,
    val composer: String,
    val xmlFileName: String,
    val eventsJson: String? = null,
    val metadataJson: String? = null,
    val timestamp: Long,
    val keySignature: String? = null
)

object LocalScoreManager {
    private const val DIRECTORY_NAME = "saved_scores"
    private const val EVENTS_SUFFIX = ".events.json"
    private val gson = Gson()
    private val cacheMutex = Mutex()
    private val FIFTHS_REGEX = Regex("<fifths>\\s*(-?\\d+)\\s*</fifths>")

    @Volatile
    private var cachedScores: List<LocalScoreMetadata>? = null

    /**
     * Returns the in-memory score metadata list immediately if already loaded,
     * allowing UI screens to render on their very first frame with zero delay.
     */
    fun peekCachedScores(): List<LocalScoreMetadata>? = cachedScores

    private fun getScoresDir(context: Context): File {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun eventsFileFor(scoresDir: File, id: String): File {
        return File(scoresDir, "$id$EVENTS_SUFFIX")
    }

    fun extractKeySignatureFromXml(xml: String): String {
        val fifthsMatch = FIFTHS_REGEX.find(xml)
        val fifths = fifthsMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return fifthsToKeyLabel(fifths)
    }

    private fun extractKeySignatureFromFile(xmlFile: File): String {
        if (!xmlFile.exists()) return "C MAJ"
        return try {
            // <fifths> appears near the top of measure 1; read a small prefix first to avoid multi-MB I/O
            val headText = xmlFile.bufferedReader(Charsets.UTF_8).use { reader ->
                val buf = CharArray(32 * 1024)
                val read = reader.read(buf)
                if (read > 0) String(buf, 0, read) else ""
            }
            val headMatch = FIFTHS_REGEX.find(headText)
            if (headMatch != null) {
                val fifths = headMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                fifthsToKeyLabel(fifths)
            } else {
                extractKeySignatureFromXml(xmlFile.readText(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            "C MAJ"
        }
    }

    private fun fifthsToKeyLabel(fifths: Int): String = when (fifths) {
        -7 -> "Cb MAJ"
        -6 -> "Gb MAJ"
        -5 -> "Db MAJ"
        -4 -> "Ab MAJ"
        -3 -> "Eb MAJ"
        -2 -> "Bb MAJ"
        -1 -> "F MAJ"
        0 -> "C MAJ"
        1 -> "G MAJ"
        2 -> "D MAJ"
        3 -> "A MAJ"
        4 -> "E MAJ"
        5 -> "B MAJ"
        6 -> "F# MAJ"
        7 -> "C# MAJ"
        else -> "C MAJ"
    }

    suspend fun saveScore(context: Context, score: MusicXmlScore): LocalScoreMetadata = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)

        // Use the score's assigned unique ID to preserve identity
        val id = score.id.ifBlank { UUID.randomUUID().toString() }
        val xmlFileName = "$id.musicxml"
        val xmlFile = File(scoresDir, xmlFileName)

        // Save raw XML
        FileOutputStream(xmlFile).use { output ->
            output.write(score.rawXml.toByteArray(Charsets.UTF_8))
        }

        // Save heavy playback events to a separate sidecar file so list metadata stays tiny
        val eventsFile = eventsFileFor(scoresDir, id)
        if (!score.eventsJson.isNullOrBlank()) {
            FileOutputStream(eventsFile).use { output ->
                output.write(score.eventsJson.toByteArray(Charsets.UTF_8))
            }
        } else if (eventsFile.exists()) {
            eventsFile.delete()
        }

        val keySig = extractKeySignatureFromXml(score.rawXml)

        // Save lightweight metadata JSON (without eventsJson payload)
        val metadata = LocalScoreMetadata(
            id = id,
            title = score.title,
            composer = score.composer,
            xmlFileName = xmlFileName,
            eventsJson = null,
            metadataJson = score.metadataJson,
            timestamp = System.currentTimeMillis(),
            keySignature = keySig
        )
        val metadataFile = File(scoresDir, "$id.json")
        FileOutputStream(metadataFile).use { output ->
            output.write(gson.toJson(metadata).toByteArray(Charsets.UTF_8))
        }

        cacheMutex.withLock {
            val current = cachedScores
            if (current != null) {
                cachedScores = (listOf(metadata) + current.filterNot { it.id == id })
                    .sortedByDescending { it.timestamp }
            }
        }

        metadata
    }

    /**
     * Creates a duplicate of an existing score in the library.
     * The duplicated score receives a unique ID, new storage files, and its own isolated
     * recording folder with 0 initial takes, ensuring separated playback storage.
     */
    suspend fun duplicateScore(context: Context, sourceId: String): LocalScoreMetadata? = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val sourceJson = File(scoresDir, "$sourceId.json")
        val sourceXml = File(scoresDir, "$sourceId.musicxml")
        if (!sourceJson.exists() || !sourceXml.exists()) return@withContext null

        val sourceMeta = try {
            gson.fromJson(sourceJson.readText(Charsets.UTF_8), LocalScoreMetadata::class.java)
        } catch (_: Exception) {
            return@withContext null
        }

        val newId = UUID.randomUUID().toString()
        val newXmlName = "$newId.musicxml"
        val newXmlFile = File(scoresDir, newXmlName)
        sourceXml.copyTo(newXmlFile, overwrite = true)

        val sourceEventsFile = eventsFileFor(scoresDir, sourceId)
        val newEventsFile = eventsFileFor(scoresDir, newId)
        if (sourceEventsFile.exists()) {
            sourceEventsFile.copyTo(newEventsFile, overwrite = true)
        } else if (!sourceMeta.eventsJson.isNullOrBlank()) {
            FileOutputStream(newEventsFile).use { output ->
                output.write(sourceMeta.eventsJson.toByteArray(Charsets.UTF_8))
            }
        }

        val resolvedKeySig = sourceMeta.keySignature?.takeIf { it.isNotBlank() }
            ?: extractKeySignatureFromFile(sourceXml)

        val newMeta = LocalScoreMetadata(
            id = newId,
            title = "${sourceMeta.title} (Copy)",
            composer = sourceMeta.composer,
            xmlFileName = newXmlName,
            eventsJson = null,
            metadataJson = sourceMeta.metadataJson,
            timestamp = System.currentTimeMillis(),
            keySignature = resolvedKeySig
        )
        val newJsonFile = File(scoresDir, "$newId.json")
        FileOutputStream(newJsonFile).use { output ->
            output.write(gson.toJson(newMeta).toByteArray(Charsets.UTF_8))
        }

        cacheMutex.withLock {
            val current = cachedScores
            if (current != null) {
                cachedScores = (listOf(newMeta) + current).sortedByDescending { it.timestamp }
            }
        }

        newMeta
    }

    suspend fun loadSavedScores(context: Context): List<LocalScoreMetadata> {
        cachedScores?.let { return it }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                cachedScores?.let { return@withLock it }

                val scoresDir = getScoresDir(context)
                val files = scoresDir.listFiles() ?: return@withLock emptyList<LocalScoreMetadata>().also {
                    cachedScores = it
                }
                val list = mutableListOf<LocalScoreMetadata>()

                for (file in files) {
                    val name = file.name
                    if (name.endsWith(".json") && !name.endsWith(EVENTS_SUFFIX)) {
                        try {
                            val jsonStr = file.readText(Charsets.UTF_8)
                            val meta = gson.fromJson(jsonStr, LocalScoreMetadata::class.java)
                            val xmlFile = File(scoresDir, meta.xmlFileName)
                            if (!xmlFile.exists()) {
                                file.delete()
                                eventsFileFor(scoresDir, meta.id).delete()
                                continue
                            }

                            // One-time lazy migration for older saved scores:
                            // extract keySignature once and move embedded eventsJson to sidecar file
                            val needsKeySig = meta.keySignature.isNullOrBlank()
                            val hasEmbeddedEvents = !meta.eventsJson.isNullOrBlank()
                            val finalMeta = if (needsKeySig || hasEmbeddedEvents) {
                                val resolvedKeySig = meta.keySignature?.takeIf { it.isNotBlank() }
                                    ?: extractKeySignatureFromFile(xmlFile)
                                if (hasEmbeddedEvents) {
                                    val eventsFile = eventsFileFor(scoresDir, meta.id)
                                    if (!eventsFile.exists()) {
                                        FileOutputStream(eventsFile).use { output ->
                                            output.write(meta.eventsJson!!.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                }
                                val migrated = meta.copy(
                                    eventsJson = null,
                                    keySignature = resolvedKeySig
                                )
                                FileOutputStream(file).use { output ->
                                    output.write(gson.toJson(migrated).toByteArray(Charsets.UTF_8))
                                }
                                migrated
                            } else {
                                meta
                            }

                            list.add(finalMeta)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                list.sortedByDescending { it.timestamp }.also { sorted ->
                    cachedScores = sorted
                }
            }
        }
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

        val eventsFile = eventsFileFor(scoresDir, metadata.id)
        val resolvedEventsJson = if (eventsFile.exists()) {
            try {
                eventsFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                metadata.eventsJson
            }
        } else {
            metadata.eventsJson
        }

        score.copy(
            id = metadata.id,
            eventsJson = resolvedEventsJson,
            metadataJson = metadata.metadataJson
        )
    }

    suspend fun deleteScore(context: Context, id: String) = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        val xmlFile = File(scoresDir, "$id.musicxml")
        val jsonFile = File(scoresDir, "$id.json")
        val eventsFile = eventsFileFor(scoresDir, id)
        if (xmlFile.exists()) xmlFile.delete()
        if (jsonFile.exists()) jsonFile.delete()
        if (eventsFile.exists()) eventsFile.delete()

        cacheMutex.withLock {
            cachedScores = cachedScores?.filterNot { it.id == id }
        }

        // Delete this score's isolated recordings directory
        ScoreRecordingManager.deleteScoreRecordingsDir(context, id)
    }

    suspend fun clearAllScores(context: Context) = withContext(Dispatchers.IO) {
        val scoresDir = getScoresDir(context)
        scoresDir.listFiles()?.forEach { it.delete() }
        cacheMutex.withLock {
            cachedScores = emptyList()
        }
        ScoreRecordingManager.clearAllScoreRecordings(context)
    }
}
