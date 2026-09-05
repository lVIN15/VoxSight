package com.cit.kaido.voxsight.ui.screens.practice

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.cit.kaido.voxsight.model.MusicalEvent
import com.google.gson.Gson
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_BPM = 96f
const val STANDARD_TPQ = 480

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
    val isChord: Boolean = false,
    val measureNumber: Int = 1,
    val measureIndex: Int = 0,
    val customVoice: Int? = null
)

fun parseMusicXmlScore(
    context: Context,
    uri: Uri,
    fallbackTitle: String
): MusicXmlScore? {
    val rawText = readMusicXmlText(context, uri) ?: return null
    return parseMusicXmlScoreFromText(rawText, fallbackTitle)
}

fun parseMusicXmlFromString(
    rawXml: String,
    fallbackTitle: String = "Untitled Score"
): MusicXmlScore? {
    return parseMusicXmlScoreFromText(rawXml, fallbackTitle)
}

/**
 * Holds pre-computed measure timing to ensure ALL parts and voices stay in exact unison.
 */
data class MeasureTimelineInfo(
    val measureNominalTicks: List<Int>,
    val measureStartTicks: List<Int>,
    val totalScoreTicks: Int
)

/**
 * Pre-scans MusicXML to determine the global measure timeline based on time signatures (<time>).
 * Guarantees that every voice, staff, and part crosses each measure barline at the exact same musical tick.
 */
fun computeScoreMeasureTimeline(rawXml: String): MeasureTimelineInfo {
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(sanitizeXmlEntities(rawXml)))

    val measureTimeSigs = mutableMapOf<Int, Pair<Int, Int>>()
    val measureRawMaxDurs = mutableMapOf<Int, Int>()
    var maxMeasuresFound = 0
    var currentPartMeasureCount = 0
    var inAttributes = false
    var inTime = false
    var curDivisions = 1
    var inMeasure = false
    var currentMeasureIdx = 0
    var intraMeasureDur = 0

    try {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "part" -> {
                            currentPartMeasureCount = 0
                        }
                        "measure" -> {
                            inMeasure = true
                            currentMeasureIdx = currentPartMeasureCount
                            currentPartMeasureCount++
                            if (currentPartMeasureCount > maxMeasuresFound) {
                                maxMeasuresFound = currentPartMeasureCount
                            }
                            intraMeasureDur = 0
                        }
                        "attributes" -> inAttributes = true
                        "divisions" -> {
                            val divVal = parser.nextText().trim().toIntOrNull()
                            if (divVal != null && divVal > 0) curDivisions = divVal
                        }
                        "time" -> if (inAttributes) inTime = true
                        "beats" -> if (inTime) {
                            val b = parser.nextText().trim().toIntOrNull()
                            if (b != null && b > 0) {
                                val prev = measureTimeSigs[currentMeasureIdx] ?: Pair(4, 4)
                                measureTimeSigs[currentMeasureIdx] = Pair(b, prev.second)
                            }
                        }
                        "beat-type" -> if (inTime) {
                            val bt = parser.nextText().trim().toIntOrNull()
                            if (bt != null && bt > 0) {
                                val prev = measureTimeSigs[currentMeasureIdx] ?: Pair(4, 4)
                                measureTimeSigs[currentMeasureIdx] = Pair(prev.first, bt)
                            }
                        }
                        "duration" -> {
                            val durVal = parser.nextText().trim().toIntOrNull() ?: 0
                            if (inMeasure && curDivisions > 0) {
                                val ticks = (durVal.toDouble() / curDivisions * STANDARD_TPQ).roundToInt()
                                intraMeasureDur += ticks
                                val existingMax = measureRawMaxDurs[currentMeasureIdx] ?: 0
                                if (intraMeasureDur > existingMax) {
                                    measureRawMaxDurs[currentMeasureIdx] = intraMeasureDur
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "attributes" -> inAttributes = false
                        "time" -> inTime = false
                        "measure" -> inMeasure = false
                    }
                }
            }
            event = parser.next()
        }
    } catch (_: Exception) {}

    val totalMeasures = maxOf(1, maxMeasuresFound)
    var prevailingBeats = 4
    var prevailingBeatType = 4
    val nominalTicksList = mutableListOf<Int>()

    for (m in 0 until totalMeasures) {
        measureTimeSigs[m]?.let { (b, bt) ->
            if (b > 0 && bt > 0) {
                prevailingBeats = b
                prevailingBeatType = bt
            }
        }
        val nomQuarters = prevailingBeats.toDouble() * (4.0 / prevailingBeatType.toDouble())
        var nomTicks = (nomQuarters * STANDARD_TPQ).roundToInt().coerceAtLeast(STANDARD_TPQ)

        // Pickup / anacrusis detection for measure 0:
        // If measure 0 has notes across parts that are shorter than a full measure, use the pickup length
        if (m == 0) {
            val measuredDur = measureRawMaxDurs[0] ?: 0
            if (measuredDur in 1 until nomTicks) {
                nomTicks = measuredDur
            }
        }
        nominalTicksList.add(nomTicks)
    }

    val startTicksList = mutableListOf<Int>()
    var accumulated = 0
    for (nom in nominalTicksList) {
        startTicksList.add(accumulated)
        accumulated += nom
    }

    return MeasureTimelineInfo(
        measureNominalTicks = nominalTicksList,
        measureStartTicks = startTicksList,
        totalScoreTicks = accumulated
    )
}

fun parseMusicXmlScoreFromText(
    rawText: String,
    fallbackTitle: String = "Untitled Score"
): MusicXmlScore? {
    val timeline = computeScoreMeasureTimeline(rawText)
    val measureStartTicks = timeline.measureStartTicks
    val measureNominalTicks = timeline.measureNominalTicks

    val notes = mutableListOf<MusicXmlNote>()
    val partNotesMap = mutableMapOf<Int, MutableList<MusicXmlNote>>()
    val partMeasuresMap = mutableMapOf<Int, MutableList<MusicXmlMeasure>>()
    var currentMeasureNumber = 1
    var currentMeasureIndex = 0
    var currentMeasureNotes = mutableListOf<MusicXmlNote>()
    var title: String? = null
    var composer: String? = null
    var currentDivisions = 1
    var parsedTempo: Float? = null

    // Intra-measure tick cursor (resets at each measure boundary)
    var intraMeasureTick = 0

    // We will track total duration per part to find the actual max duration
    val partDurations = mutableMapOf<Int, Int>()
    val partNamesMap = mutableMapOf<Int, String>()
    var currentScorePartIndex = 0
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
    var customVoice: Int? = null
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
                        "score-part" -> {
                            currentScorePartIndex++
                        }
                        "part-name" -> {
                            val pName = parser.nextText().trim()
                            if (pName.isNotBlank() && currentScorePartIndex > 0) {
                                partNamesMap[currentScorePartIndex] = pName
                            }
                        }
                        "sound" -> {
                            val tAttr = parser.getAttributeValue(null, "tempo")?.toFloatOrNull()
                            if (tAttr != null && parsedTempo == null) parsedTempo = tAttr
                        }
                        "per-minute" -> {
                            val pm = parser.nextText().trim().toFloatOrNull()
                            if (pm != null && parsedTempo == null) parsedTempo = pm
                        }
                        "part" -> {
                            currentPartIndex++
                        }
                        "measure" -> {
                            currentMeasureNumber = parser.getAttributeValue(null, "number")?.toIntOrNull() ?: 1
                            currentMeasureIndex = partMeasuresMap.getOrDefault(currentPartIndex, emptyList()).size
                            currentMeasureNotes = mutableListOf<MusicXmlNote>()
                            intraMeasureTick = 0
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
                            val parsedDiv = parser.nextText().toIntOrNull()
                            if (parsedDiv != null && parsedDiv > 0) {
                                currentDivisions = parsedDiv
                            }
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
                            customVoice = parser.getAttributeValue(null, "data-vx-voice")?.toIntOrNull()
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
                        val backupTicks = (backupDuration.toDouble() / currentDivisions * STANDARD_TPQ).roundToInt()
                        intraMeasureTick = max(0, intraMeasureTick - backupTicks)
                        inBackup = false
                    } else if (parser.name == "forward") {
                        val forwardTicks = (forwardDuration.toDouble() / currentDivisions * STANDARD_TPQ).roundToInt()
                        intraMeasureTick += forwardTicks
                        inForward = false
                    } else if (parser.name == "note" && inNote) {
                        val rawDur = if (isChord) lastDuration else duration ?: 0
                        val effectiveDuration = (rawDur.toDouble() / currentDivisions * STANDARD_TPQ).roundToInt()

                        val noteOffset = if (isChord) {
                            max(0, intraMeasureTick - effectiveDuration)
                        } else {
                            intraMeasureTick
                        }

                        if (!isChord) {
                            intraMeasureTick = noteOffset + effectiveDuration
                        }

                        val measureStartTick = measureStartTicks.getOrElse(currentMeasureIndex) {
                            if (measureStartTicks.isNotEmpty()) {
                                measureStartTicks.last() + (currentMeasureIndex - measureStartTicks.size + 1) * STANDARD_TPQ * 4
                            } else {
                                0
                            }
                        }
                        val measureNominalTick = measureNominalTicks.getOrElse(currentMeasureIndex) { STANDARD_TPQ * 4 }

                        // Clamp intra-measure offset to prevent overshooting nominal measure length
                        val clampedOffset = if (measureNominalTick > 0) {
                            min(noteOffset, measureNominalTick - 1)
                        } else {
                            noteOffset
                        }

                        // Clamp duration so the note does not bleed beyond the measure boundary into next measure
                        val clampedDuration = if (measureNominalTick > 0 && clampedOffset + effectiveDuration > measureNominalTick) {
                            max(STANDARD_TPQ / 4, measureNominalTick - clampedOffset)
                        } else {
                            effectiveDuration
                        }

                        val noteStartTime = measureStartTick + clampedOffset
                        partDurations[currentPartIndex] = max(partDurations.getOrDefault(currentPartIndex, 0), noteStartTime + clampedDuration)

                        if (isRest || (step != null && octave != null)) {
                            val resolvedStep = if (isRest) "R" else {
                                when (alter) {
                                    1 -> "${step}#"
                                    -1 -> "${step}b"
                                    else -> step!!
                                }
                            }
                            val resolvedOctave = if (isRest) 0 else octave!!

                            val actualVoice = when {
                                customVoice != null && customVoice in 1..4 -> customVoice
                                voice != null && voice in 1..4 -> voice!!
                                staff != null && staff in 1..4 -> staff!!
                                currentPartIndex > 0 -> currentPartIndex
                                else -> 1
                            }

                            val note = MusicXmlNote(
                                step = resolvedStep,
                                octave = resolvedOctave,
                                durationDivisions = clampedDuration,
                                startTimeDivisions = noteStartTime,
                                voice = actualVoice,
                                staff = staff ?: 1,
                                type = noteType ?: "quarter",
                                isRest = isRest,
                                alter = alter ?: 0,
                                isDotted = isDotted,
                                originalVoice = voice ?: 1,
                                isChord = isChord,
                                measureNumber = currentMeasureNumber,
                                measureIndex = currentMeasureIndex,
                                customVoice = customVoice
                            )
                            notes.add(note)
                            currentMeasureNotes.add(note)
                            val partList = partNotesMap.getOrPut(currentPartIndex) { mutableListOf() }
                            partList.add(note)
                        }
                        inNote = false
                        isChord = false
                    } else if (parser.name == "measure") {
                        val measureNotesList = currentMeasureNotes.toList()
                        val measure = MusicXmlMeasure(currentMeasureNumber, measureNotesList)
                        partMeasuresMap.getOrPut(currentPartIndex) { mutableListOf() }.add(measure)
                        intraMeasureTick = 0
                    }
                }
            }
            event = parser.next()
        }

        android.util.Log.d("MusicXmlParser", "Parsed ${notes.size} notes, distinct voices: ${notes.map { it.voice }.distinct()}")
        val maxTotalDuration = max(timeline.totalScoreTicks, partDurations.values.maxOrNull() ?: 0)

        val resolvedTempo = parsedTempo ?: 120f
        val beats = maxTotalDuration.toFloat() / STANDARD_TPQ
        val totalSeconds = max(1, (beats * 60f / resolvedTempo).roundToInt())
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

            val adjustedMeasures = originalMeasuresInPart.map { measure ->
                val notesByTime = measure.notes.groupBy { it.startTimeDivisions }

                val adjustedNotes = measure.notes.map { note ->
                    val group = notesByTime[note.startTimeDivisions] ?: listOf(note)
                    val isChordGroup = group.size > 1 && !note.isRest

                    val isolatedVoice = when {
                        note.customVoice != null && note.customVoice in 1..4 -> note.customVoice
                        totalParts >= 4 -> partIndex
                        totalParts == 2 || totalParts == 3 -> {
                            if (partIndex == 1) {
                                if (isChordGroup) {
                                    val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                    val idx = sorted.indexOf(note)
                                    if (idx == 0) 1 else 2
                                } else if (note.originalVoice == 2) {
                                    2
                                } else {
                                    1
                                }
                            } else {
                                if (isChordGroup) {
                                    val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                    val idx = sorted.indexOf(note)
                                    if (idx == 0) 3 else 4
                                } else if (note.originalVoice == 2 || note.originalVoice == 4) {
                                    4
                                } else {
                                    3
                                }
                            }
                        }
                        else -> note.originalVoice
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                measure.copy(notes = adjustedNotes)
            }

            // Group notes by part for direct access
            val partNotes = adjustedMeasures.flatMap { it.notes }
            val declaredName = partNamesMap[partIndex]?.takeIf { it.isNotBlank() }
            val resolvedPartName = when {
                declaredName != null -> declaredName
                totalParts == 1 -> "Voice Part"
                totalParts == 2 -> if (partIndex == 1) "Soprano / Alto" else "Tenor / Bass"
                totalParts == 3 -> when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Bass"; else -> "Part $partIndex" }
                totalParts == 4 -> when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Tenor"; 4 -> "Bass"; else -> "Part $partIndex" }
                else -> "Part $partIndex"
            }

            finalParts.add(
                MusicXmlPart(
                    id = partIndex,
                    name = resolvedPartName,
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
                val notesByTime = originalNotesInPart.groupBy { it.startTimeDivisions }

                val adjustedNotes = originalNotesInPart.map { note ->
                    val group = notesByTime[note.startTimeDivisions] ?: listOf(note)
                    val isChordGroup = group.size > 1 && !note.isRest

                    val isolatedVoice = when {
                        note.customVoice != null && note.customVoice in 1..4 -> note.customVoice
                        totalPartsFallback >= 4 -> partIndex
                        totalPartsFallback == 2 || totalPartsFallback == 3 -> {
                            if (partIndex == 1) {
                                if (isChordGroup) {
                                    val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                    val idx = sorted.indexOf(note)
                                    if (idx == 0) 1 else 2
                                } else if (note.originalVoice == 2) {
                                    2
                                } else {
                                    1
                                }
                            } else {
                                if (isChordGroup) {
                                    val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                    val idx = sorted.indexOf(note)
                                    if (idx == 0) 3 else 4
                                } else if (note.originalVoice == 2 || note.originalVoice == 4) {
                                    4
                                } else {
                                    3
                                }
                            }
                        }
                        else -> note.originalVoice
                    }
                    note.copy(voice = isolatedVoice)
                }
                finalNotes.addAll(adjustedNotes)
                finalParts.add(
                    MusicXmlPart(
                        id = partIndex,
                        name = when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Tenor"; 4 -> "Bass"; else -> "Part $partIndex" },
                        notes = adjustedNotes,
                        measures = listOf(MusicXmlMeasure(1, adjustedNotes))
                    )
                )
            }
        }

        val eventsJson = generateEventsJsonFromScoreParts(finalParts, STANDARD_TPQ)
        val metadataJson = "{\"ticks_per_quarter\":$STANDARD_TPQ,\"tempo_events\":[{\"tick\":0,\"bpm\":$resolvedTempo}]}"

        MusicXmlScore(
            title = resolvedTitle,
            composer = resolvedComposer,
            notes = finalNotes,
            parts = finalParts,
            totalSeconds = totalSeconds,
            divisions = STANDARD_TPQ,
            rawXml = rawText,
            eventsJson = eventsJson,
            metadataJson = metadataJson
        )
    } catch (_: Exception) {
        null
    }
}

fun generateEventsJsonFromScoreParts(parts: List<MusicXmlPart>, tpq: Int): String {
    val eventsList = mutableListOf<MusicalEvent>()
    var eventIndex = 0
    val safeTpq = if (tpq > 0) tpq else 1

    parts.forEach { part ->
        part.notes.forEach { note ->
            val satbVoiceStr = when {
                note.customVoice != null && note.customVoice in 1..4 -> when (note.customVoice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                parts.size >= 4 -> when (part.id) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                parts.size == 2 -> when (part.id) {
                    1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                    2 -> if (note.voice == 2 || note.originalVoice == 2 || note.voice == 4) "BASS" else "TENOR"
                    else -> "SOPRANO"
                }
                note.staff == 2 -> if (note.voice == 2 || note.originalVoice == 2 || note.voice == 4) "BASS" else "TENOR"
                note.staff == 1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                note.voice in 1..4 -> when (note.voice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                else -> "SOPRANO"
            }

            val midi = if (note.isRest) 0 else calculateMidiNote(note.step, note.alter, note.octave)
            val pitchName = if (note.isRest) "Rest" else formatPitchName(note.step, note.alter, note.octave)
            val eventId = "t${note.startTimeDivisions}-p${part.id}-c${eventIndex++}"

            eventsList.add(
                MusicalEvent(
                    eventId = eventId,
                    measureNumber = note.measureNumber,
                    measureIndex = note.measureIndex,
                    tickPosition = note.startTimeDivisions,
                    ticksPerQuarter = safeTpq,
                    pitchMidi = midi,
                    pitchName = pitchName,
                    durationTicks = note.durationDivisions,
                    durationQuarters = note.durationDivisions.toFloat() / safeTpq,
                    voiceSource = note.voice,
                    staffId = note.staff,
                    partId = part.id,
                    isRest = note.isRest,
                    isChordMember = note.isChord,
                    satbVoice = satbVoiceStr,
                    satbConfidence = 1.0f,
                    playbackTrack = satbVoiceStr
                )
            )
        }
    }
    return Gson().toJson(eventsList)
}

private fun calculateMidiNote(step: String, alter: Int, octave: Int): Int {
    val baseLetter = step.take(1).uppercase()
    val base = when (baseLetter) {
        "C" -> 0
        "D" -> 2
        "E" -> 4
        "F" -> 5
        "G" -> 7
        "A" -> 9
        "B" -> 11
        else -> 0
    }
    return base + alter + (octave + 1) * 12
}

private fun formatPitchName(step: String, alter: Int, octave: Int): String {
    val alterStr = when (alter) {
        -1 -> "b"
        1 -> "#"
        else -> ""
    }
    return "$step$alterStr$octave"
}

private val xmlEntityPattern =
    Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)")

private fun sanitizeXmlEntities(xml: String): String {
    // Guard against unescaped '&' in metadata fields that crash the XML parser.
    return xmlEntityPattern.replace(xml, "&amp;")
}

internal fun readMusicXmlText(context: Context, uri: Uri): String? {
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
