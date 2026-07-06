package com.cit.kaido.voxsight.ui.screens.practice

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_BPM = 96f

/**
 * Minimal MusicXML parser for prototype playback and score visualization.
 */
data class MusicXmlScore(
    val title: String,
    val composer: String,
    val notes: List<MusicXmlNote>,
    val parts: List<MusicXmlPart>,
    val totalSeconds: Int,
    val divisions: Int = 1,
    val rawXml: String = "",
    val eventsJson: String? = null,
    val metadataJson: String? = null
)

data class MusicXmlMeasure(
    val number: Int,
    val notes: List<MusicXmlNote>
)

// Represents a single part (e.g., Soprano, Alto, Tenor, Bass)
data class MusicXmlPart(
    val id: Int,
    val name: String,
    val notes: List<MusicXmlNote>,
    val measures: List<MusicXmlMeasure> = emptyList()
)

data class MusicXmlNote(
    val step: String,
    val octave: Int,
    val durationDivisions: Int,
    val startTimeDivisions: Int = 0,
    /** MusicXML <voice> value (1-based). Defaults to 1 if missing. */
    val voice: Int = 1,
    /** MusicXML <staff> value (1-based). Defaults to 1 if missing. */
    val staff: Int = 1,
    /** MusicXML <type> value: whole, half, quarter, eighth, 16th, etc. */
    val type: String = "quarter",
    val isRest: Boolean = false,
    val alter: Int = 0,
    val isDotted: Boolean = false,
    val originalVoice: Int = 1,
    val isChord: Boolean = false
)

fun parseMusicXmlScore(
    context: Context,
    uri: Uri,
    fallbackTitle: String
): MusicXmlScore? {
    val rawText = readMusicXmlText(context, uri) ?: return null

    val notes = mutableListOf<MusicXmlNote>()
    val partNotesMap = mutableMapOf<Int, MutableList<MusicXmlNote>>()
    val partMeasuresMap = mutableMapOf<Int, MutableList<MusicXmlMeasure>>()
    var currentMeasureNumber = 1
    var currentMeasureNotes = mutableListOf<MusicXmlNote>()
    var title: String? = null
    var composer: String? = null
    var divisions = 1
    
    // We will track total duration per part to find the actual max duration
    val partDurations = mutableMapOf<Int, Int>()
    var currentPartIndex = 0

    var inNote = false
    var isRest = false
    var isChord = false
    var isDotted = false
    var step: String? = null
    // Backup/forward handling
    var inBackup = false
    var backupDuration = 0
    var inForward = false
    var forwardDuration = 0
    var alter: Int? = null
    var octave: Int? = null
    var duration: Int? = null
    var voice: Int? = null
    var staff: Int? = null
    var noteType: String? = null
    // Track the most recent duration for chord inheritance
    var lastDuration = 0

    val parser = Xml.newPullParser()
    parser.setInput(StringReader(sanitizeXmlEntities(rawText)))

    return try {
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "part" -> {
                            // Start of a new <part> element. Increment the part counter.
                            // This counter is later used to assign a default voice for notes
                            // that do not specify a <staff> or <voice> element.
                            currentPartIndex++
                        }
                        "measure" -> {
                            currentMeasureNumber = parser.getAttributeValue(null, "number")?.toIntOrNull() ?: 1
                            currentMeasureNotes = mutableListOf<MusicXmlNote>()
                        }
                        "work-title",
                        "movement-title",
                        "credit-words" -> {
                            if (title.isNullOrBlank()) {
                                title = parser.nextText().trim()
                            }
                        }
                        "creator" -> {
                            val type = parser.getAttributeValue(null, "type")
                            if (composer.isNullOrBlank() && type == "composer") {
                                composer = parser.nextText().trim()
                            }
                        }
                        "divisions" -> {
                            divisions = parser.nextText().toIntOrNull() ?: divisions
                        }
                        "note" -> {
                            inNote = true
                            isRest = false
                            isChord = false
                            isDotted = false
                            step = null
                            alter = null
                            octave = null
                            duration = null
                            voice = null
                            staff = null
                            noteType = null
                        }
                        "chord" -> if (inNote) {
                            isChord = true
                        }
                        "rest" -> if (inNote) {
                            isRest = true
                        }
                        "dot" -> if (inNote) {
                            isDotted = true
                        }
                        "backup" -> {
                            inBackup = true
                            backupDuration = 0
                        }
                        "forward" -> {
                            inForward = true
                            forwardDuration = 0
                        }
                        "step" -> if (inNote) {
                            step = parser.nextText().trim()
                        }
                        "alter" -> if (inNote) {
                            alter = parser.nextText().toIntOrNull()
                        }
                        "octave" -> if (inNote) {
                            octave = parser.nextText().toIntOrNull()
                        }
                        "duration" -> {
                            val durVal = parser.nextText().toIntOrNull() ?: 0
                            if (inNote) {
                                duration = durVal
                                lastDuration = durVal
                            } else if (inBackup) {
                                backupDuration = durVal
                            } else if (inForward) {
                                forwardDuration = durVal
                            }
                        }
                        "voice" -> if (inNote) {
                            voice = parser.nextText().toIntOrNull()
                        }
                        "staff" -> if (inNote) {
                            staff = parser.nextText().toIntOrNull()
                        }
                        "type" -> if (inNote) {
                            noteType = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "backup") {
                        val currentPartDuration = partDurations.getOrDefault(currentPartIndex, 0)
                        partDurations[currentPartIndex] = max(0, currentPartDuration - backupDuration)
                        inBackup = false
                    } else if (parser.name == "forward") {
                        val currentPartDuration = partDurations.getOrDefault(currentPartIndex, 0)
                        partDurations[currentPartIndex] = currentPartDuration + forwardDuration
                        inForward = false
                    } else if (parser.name == "note" && inNote) {
                        // Determine the duration to use (inherit for chords)
                        val effectiveDuration = if (isChord) lastDuration else duration ?: 0

                        val currentCursor = partDurations.getOrDefault(currentPartIndex, 0)
                        val noteStartTime = if (isChord) {
                            max(0, currentCursor - effectiveDuration)
                        } else {
                            currentCursor
                        }
                        
                        // Accumulate duration for the current part
                        partDurations[currentPartIndex] = noteStartTime + effectiveDuration

                        if (isRest || (step != null && octave != null)) {
                            // Resolve pitch step with accidental
                            val resolvedStep = if (isRest) "R" else {
                                when (alter) {
                                    1 -> "${step}#"
                                    -1 -> "${step}b"
                                    else -> step!!
                                }
                            }
                            val resolvedOctave = if (isRest) 0 else octave!!

                            // Voice assignment: prioritize staff number, then part index, then voice attribute
                            val actualVoice = when {
                                staff != null && staff in 1..4 -> staff!!
                                currentPartIndex > 0 -> currentPartIndex
                                voice != null -> voice!!
                                else -> 1
                            }

                            // Add note to the overall list
                            val note = MusicXmlNote(
                                step = resolvedStep,
                                octave = resolvedOctave,
                                durationDivisions = effectiveDuration,
                                startTimeDivisions = noteStartTime,
                                voice = actualVoice,
                                staff = staff ?: 1,
                                type = noteType ?: "quarter",
                                isRest = isRest,
                                alter = alter ?: 0,
                                isDotted = isDotted,
                                originalVoice = voice ?: 1,
                                isChord = isChord
                            )
                            notes.add(note)
                            currentMeasureNotes.add(note)
                            // Also add to part‑specific collection
                            val partList = partNotesMap.getOrPut(currentPartIndex) { mutableListOf() }
                            partList.add(note)
                        }
                        // Reset state for next note
                        inNote = false
                        isChord = false
                    } else if (parser.name == "measure") {
                        val measureNotesList = currentMeasureNotes.toList()
                        val measure = MusicXmlMeasure(currentMeasureNumber, measureNotesList)
                        partMeasuresMap.getOrPut(currentPartIndex) { mutableListOf() }.add(measure)
                    }
                }
            }
            event = parser.next()
        }

        // Log parsing summary for debugging
        android.util.Log.d("MusicXmlParser", "Parsed ${notes.size} notes, distinct voices: ${notes.map { it.voice }.distinct()}")
        // The overall length of the piece should be based on the longest part.
        // Using the maximum duration across all parts prevents the total time from
        // being inflated by summing every part together.
        val maxTotalDuration = partDurations.values.maxOrNull() ?: 0

        val beats = if (divisions > 0) {
            maxTotalDuration.toFloat() / divisions
        } else {
            // Fallback: approximate using note count and number of parts.
            notes.size.toFloat() / max(1, currentPartIndex)
        }
        val totalSeconds = max(1, (beats * 60f / DEFAULT_BPM).roundToInt())
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: fallbackTitle.ifBlank { "Untitled Score" }
        val resolvedComposer = composer?.takeIf { it.isNotBlank() } ?: "Unknown Composer"

        // Reconstruct parts, measures, and final notes with corrected voice numbers
        val finalNotes = mutableListOf<MusicXmlNote>()
        val finalParts = mutableListOf<MusicXmlPart>()
        val totalParts = partMeasuresMap.size.coerceAtLeast(partNotesMap.size)

        partMeasuresMap.entries.sortedBy { it.key }.forEach { entry ->
            val partIndex = entry.key
            val originalMeasuresInPart = entry.value

            // Analyze distinct original voices inside this part to perform highly-accurate sub-staff/sub-part isolation
            val allNotesInPart = originalMeasuresInPart.flatMap { it.notes }
            val distinctOriginalVoices = allNotesInPart.map { it.originalVoice }.distinct().sorted()

            val adjustedMeasures = originalMeasuresInPart.map { measure ->
                val adjustedNotes = measure.notes.map { note ->
                    val isolatedVoice = when {
                        totalParts >= 4 -> {
                            // Case A: 4 or more separate parts (one part per voice)
                            partIndex
                        }
                        totalParts == 2 || totalParts == 3 -> {
                            // Case B: 2 or 3 parts (Treble part has Soprano/Alto, Bass part has Tenor/Bass)
                            if (partIndex == 1) {
                                // Part 1 contains Soprano & Alto
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) {
                                    2 // Alto
                                } else {
                                    1 // Soprano
                                }
                            } else {
                                // Part 2 contains Tenor & Bass
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) {
                                    4 // Bass
                                } else {
                                    3 // Tenor
                                }
                            }
                        }
                        else -> {
                            // Case C: Single part piano-style reduction
                            note.originalVoice
                        }
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                measure.copy(notes = adjustedNotes)
            }

            // Group notes by part for direct access
            val partNotes = adjustedMeasures.flatMap { it.notes }

            finalParts.add(
                MusicXmlPart(
                    id = partIndex,
                    name = when (partIndex) {
                        1 -> "Soprano"
                        2 -> "Alto"
                        3 -> "Tenor"
                        4 -> "Bass"
                        else -> "Part $partIndex"
                    },
                    notes = partNotes,
                    measures = adjustedMeasures
                )
            )
        }

        // Fallback for notes/parts if partMeasuresMap was empty (e.g. no <measure> elements in XML)
        if (finalParts.isEmpty()) {
            val totalPartsFallback = partNotesMap.size
            partNotesMap.entries.sortedBy { it.key }.forEach { entry ->
                val partIndex = entry.key
                val originalNotesInPart = entry.value

                val distinctOriginalVoices = originalNotesInPart.map { it.originalVoice }.distinct().sorted()

                val adjustedNotes = originalNotesInPart.map { note ->
                    val isolatedVoice = when {
                        totalPartsFallback >= 4 -> {
                            partIndex
                        }
                        totalPartsFallback == 2 || totalPartsFallback == 3 -> {
                            if (partIndex == 1) {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) {
                                    2
                                } else {
                                    1
                                }
                            } else {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) {
                                    4
                                } else {
                                    3
                                }
                            }
                        }
                        else -> {
                            note.originalVoice
                        }
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                finalParts.add(
                    MusicXmlPart(
                        id = partIndex,
                        name = when (partIndex) {
                            1 -> "Soprano"
                            2 -> "Alto"
                            3 -> "Tenor"
                            4 -> "Bass"
                            else -> "Part $partIndex"
                        },
                        notes = adjustedNotes,
                        measures = listOf(MusicXmlMeasure(1, adjustedNotes))
                    )
                )
            }
        }

        MusicXmlScore(
            title = resolvedTitle,
            composer = resolvedComposer,
            notes = finalNotes,
            parts = finalParts,
            totalSeconds = totalSeconds,
            divisions = divisions,
            rawXml = rawText
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Parses a MusicXML score from a raw XML string (e.g. returned by the OMR API).
 * Delegates to [parseMusicXmlScore] by writing the string to a temporary file.
 */
fun parseMusicXmlFromString(
    rawXml: String,
    fallbackTitle: String
): MusicXmlScore? {
    // Write the XML string to a temporary in-memory–style parse using the same
    // logic as the URI-based parser. We reuse the internal parsing pipeline by
    // calling the shared helper directly.
    val notes = mutableListOf<MusicXmlNote>()
    val partNotesMap = mutableMapOf<Int, MutableList<MusicXmlNote>>()
    val partMeasuresMap = mutableMapOf<Int, MutableList<MusicXmlMeasure>>()
    var currentMeasureNumber = 1
    var currentMeasureNotes = mutableListOf<MusicXmlNote>()
    var title: String? = null
    var composer: String? = null
    var divisions = 1

    val partDurations = mutableMapOf<Int, Int>()
    var currentPartIndex = 0

    var inNote = false
    var isRest = false
    var isChord = false
    var isDotted = false
    var step: String? = null
    var inBackup = false
    var backupDuration = 0
    var inForward = false
    var forwardDuration = 0
    var alter: Int? = null
    var octave: Int? = null
    var duration: Int? = null
    var voice: Int? = null
    var staff: Int? = null
    var noteType: String? = null
    var lastDuration = 0

    val parser = Xml.newPullParser()
    parser.setInput(StringReader(sanitizeXmlEntities(rawXml)))

    return try {
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "part" -> { currentPartIndex++ }
                        "measure" -> {
                            currentMeasureNumber = parser.getAttributeValue(null, "number")?.toIntOrNull() ?: 1
                            currentMeasureNotes = mutableListOf<MusicXmlNote>()
                        }
                        "work-title",
                        "movement-title",
                        "credit-words" -> {
                            if (title.isNullOrBlank()) {
                                title = parser.nextText().trim()
                            }
                        }
                        "creator" -> {
                            val type = parser.getAttributeValue(null, "type")
                            if (composer.isNullOrBlank() && type == "composer") {
                                composer = parser.nextText().trim()
                            }
                        }
                        "divisions" -> {
                            divisions = parser.nextText().toIntOrNull() ?: divisions
                        }
                        "note" -> {
                            inNote = true; isRest = false; isChord = false; isDotted = false
                            step = null; alter = null; octave = null; duration = null
                            voice = null; staff = null; noteType = null
                        }
                        "chord" -> if (inNote) { isChord = true }
                        "rest" -> if (inNote) { isRest = true }
                        "dot" -> if (inNote) { isDotted = true }
                        "backup" -> { inBackup = true; backupDuration = 0 }
                        "forward" -> { inForward = true; forwardDuration = 0 }
                        "step" -> if (inNote) { step = parser.nextText().trim() }
                        "alter" -> if (inNote) { alter = parser.nextText().toIntOrNull() }
                        "octave" -> if (inNote) { octave = parser.nextText().toIntOrNull() }
                        "duration" -> {
                            val durVal = parser.nextText().toIntOrNull() ?: 0
                            if (inNote) { duration = durVal; lastDuration = durVal }
                            else if (inBackup) { backupDuration = durVal }
                            else if (inForward) { forwardDuration = durVal }
                        }
                        "voice" -> if (inNote) { voice = parser.nextText().toIntOrNull() }
                        "staff" -> if (inNote) { staff = parser.nextText().toIntOrNull() }
                        "type" -> if (inNote) { noteType = parser.nextText().trim() }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "backup") {
                        val cur = partDurations.getOrDefault(currentPartIndex, 0)
                        partDurations[currentPartIndex] = max(0, cur - backupDuration)
                        inBackup = false
                    } else if (parser.name == "forward") {
                        val cur = partDurations.getOrDefault(currentPartIndex, 0)
                        partDurations[currentPartIndex] = cur + forwardDuration
                        inForward = false
                    } else if (parser.name == "note" && inNote) {
                        val effectiveDuration = if (isChord) lastDuration else duration ?: 0
                        val currentCursor = partDurations.getOrDefault(currentPartIndex, 0)
                        val noteStartTime = if (isChord) max(0, currentCursor - effectiveDuration) else currentCursor
                        partDurations[currentPartIndex] = noteStartTime + effectiveDuration

                        if (isRest || (step != null && octave != null)) {
                            val resolvedStep = if (isRest) "R" else {
                                when (alter) { 1 -> "${step}#"; -1 -> "${step}b"; else -> step!! }
                            }
                            val resolvedOctave = if (isRest) 0 else octave!!
                            val actualVoice = when {
                                staff != null && staff in 1..4 -> staff!!
                                currentPartIndex > 0 -> currentPartIndex
                                voice != null -> voice!!
                                else -> 1
                            }
                            val note = MusicXmlNote(
                                step = resolvedStep, octave = resolvedOctave,
                                durationDivisions = effectiveDuration, startTimeDivisions = noteStartTime,
                                voice = actualVoice, staff = staff ?: 1,
                                type = noteType ?: "quarter", isRest = isRest,
                                alter = alter ?: 0, isDotted = isDotted,
                                originalVoice = voice ?: 1, isChord = isChord
                            )
                            notes.add(note); currentMeasureNotes.add(note)
                            partNotesMap.getOrPut(currentPartIndex) { mutableListOf() }.add(note)
                        }
                        inNote = false; isChord = false
                    } else if (parser.name == "measure") {
                        val measure = MusicXmlMeasure(currentMeasureNumber, currentMeasureNotes.toList())
                        partMeasuresMap.getOrPut(currentPartIndex) { mutableListOf() }.add(measure)
                    }
                }
            }
            event = parser.next()
        }

        val maxTotalDuration = partDurations.values.maxOrNull() ?: 0
        val beats = if (divisions > 0) maxTotalDuration.toFloat() / divisions
                    else notes.size.toFloat() / max(1, currentPartIndex)
        val totalSeconds = max(1, (beats * 60f / DEFAULT_BPM).roundToInt())
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { "Untitled Score" }
        val resolvedComposer = composer?.takeIf { it.isNotBlank() } ?: "Unknown Composer"

        val finalNotes = mutableListOf<MusicXmlNote>()
        val finalParts = mutableListOf<MusicXmlPart>()
        val totalParts = partMeasuresMap.size.coerceAtLeast(partNotesMap.size)

        partMeasuresMap.entries.sortedBy { it.key }.forEach { entry ->
            val partIndex = entry.key
            val originalMeasuresInPart = entry.value
            val allNotesInPart = originalMeasuresInPart.flatMap { it.notes }
            val distinctOriginalVoices = allNotesInPart.map { it.originalVoice }.distinct().sorted()

            val adjustedMeasures = originalMeasuresInPart.map { measure ->
                val adjustedNotes = measure.notes.map { note ->
                    val isolatedVoice = when {
                        totalParts >= 4 -> partIndex
                        totalParts == 2 || totalParts == 3 -> {
                            if (partIndex == 1) {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) 2 else 1
                            } else {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) 4 else 3
                            }
                        }
                        else -> note.originalVoice
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                measure.copy(notes = adjustedNotes)
            }

            finalParts.add(MusicXmlPart(
                id = partIndex,
                name = when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Tenor"; 4 -> "Bass"; else -> "Part $partIndex" },
                notes = adjustedMeasures.flatMap { it.notes },
                measures = adjustedMeasures
            ))
        }

        if (finalParts.isEmpty()) {
            partNotesMap.entries.sortedBy { it.key }.forEach { entry ->
                val partIndex = entry.key
                val originalNotesInPart = entry.value
                val distinctOriginalVoices = originalNotesInPart.map { it.originalVoice }.distinct().sorted()
                val totalPartsFallback = partNotesMap.size

                val adjustedNotes = originalNotesInPart.map { note ->
                    val isolatedVoice = when {
                        totalPartsFallback >= 4 -> partIndex
                        totalPartsFallback == 2 || totalPartsFallback == 3 -> {
                            if (partIndex == 1) {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) 2 else 1
                            } else {
                                if (distinctOriginalVoices.size > 1 && note.originalVoice != distinctOriginalVoices.first()) 4 else 3
                            }
                        }
                        else -> note.originalVoice
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                finalParts.add(MusicXmlPart(
                    id = partIndex,
                    name = when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Tenor"; 4 -> "Bass"; else -> "Part $partIndex" },
                    notes = adjustedNotes,
                    measures = listOf(MusicXmlMeasure(1, adjustedNotes))
                ))
            }
        }

        MusicXmlScore(
            title = resolvedTitle, composer = resolvedComposer,
            notes = finalNotes, parts = finalParts,
            totalSeconds = totalSeconds, divisions = divisions,
            rawXml = rawXml
        )
    } catch (_: Exception) {
        null
    }
}

private val xmlEntityPattern =
    Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)")

private fun sanitizeXmlEntities(xml: String): String {
    // Guard against unescaped '&' in metadata fields that crash the XML parser.
    return xmlEntityPattern.replace(xml, "&amp;")
}

private fun readMusicXmlText(context: Context, uri: Uri): String? {
    val bytes = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    } ?: return null

    return if (looksLikeZip(bytes)) {
        extractXmlFromZip(bytes)
    } else {
        decodeXmlBytes(bytes)
    }
}

private fun looksLikeZip(bytes: ByteArray): Boolean {
    return bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}

private fun extractXmlFromZip(bytes: ByteArray): String? {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val name = entry.name.lowercase()
            if (!entry.isDirectory && (name.endsWith(".xml") || name.endsWith(".musicxml"))) {
                val data = zis.readBytes()
                return decodeXmlBytes(data)
            }
            entry = zis.nextEntry
        }
    }
    return null
}

private fun decodeXmlBytes(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
        return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16LE"))
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
        return String(bytes.copyOfRange(2, bytes.size), Charset.forName("UTF-16BE"))
    }
    return String(bytes, Charsets.UTF_8)
}
