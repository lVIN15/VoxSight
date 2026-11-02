package com.cit.kaido.voxsight.ui.screens.practice

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.cit.kaido.voxsight.model.MusicalEvent
import com.cit.kaido.voxsight.model.normalizeLegacyVoiceToParts
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
    val id: String = java.util.UUID.randomUUID().toString(),
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
    val customVoice: Int? = null,
    val customVoiceStr: String? = null,
    val lyric: String? = null,
    val stem: String? = null,
    val beam: String? = null,
    val beam2: String? = null,
    val beam3: String? = null,
    val sourceNoteId: Int? = null
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

data class VocalPartRole(
    val voice: Int,
    val roleStr: String,
    val defaultName: String
)

internal fun computeUniversalVocalPartMap(
    totalParts: Int,
    partNamesMap: Map<Int, String>,
    partNotesMap: Map<Int, List<MusicXmlNote>>,
    partHasFClefMap: Map<Int, Boolean>,
    scoreTitle: String? = null
): Map<Int, VocalPartRole> {
    val result = mutableMapOf<Int, VocalPartRole>()

    data class PartMetadata(
        val index: Int,
        val name: String,
        val isExplicitSolo: Boolean,
        val isAccomp: Boolean,
        val isSoprano: Boolean,
        val isAlto: Boolean,
        val isTenor: Boolean,
        val isBass: Boolean,
        val hasFClef: Boolean,
        val activeMeasures: Int,
        val avgMidi: Double
    )

    val meta = (1..totalParts).map { idx ->
        val pName = (partNamesMap[idx] ?: "").trim().lowercase()
        val pNotes = (partNotesMap[idx] ?: emptyList()).filter { !it.isRest }
        val avgMidi = if (pNotes.isNotEmpty()) {
            pNotes.map { calculateMidiNote(it.step, it.alter, it.octave) }.average()
        } else {
            0.0
        }
        val activeMeasures = pNotes.map { if (it.measureIndex > 0) it.measureIndex else it.measureNumber }.distinct().size
        val hasFClef = (partHasFClefMap[idx] == true) || (avgMidi in 1.0..56.9)
        val isExplicitSolo = listOf("solo", "cantor", "leader", "descant", "baritone", "sol.", "sol").any { pName.contains(it) }
        val isAccomp = listOf("piano", "organ", "keyboard", "guitar", "accomp", "orch", "strings").any { pName.contains(it) }
        val isSop = pName.contains("soprano") || pName == "s" || pName.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(pName)
        val isAlt = pName.contains("alto") || pName == "a" || pName.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(pName)
        val isTen = pName.contains("tenor") || pName == "t" || pName.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(pName)
        val isBas = pName.contains("bass") || pName == "b" || pName.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(pName)

        PartMetadata(
            index = idx,
            name = pName,
            isExplicitSolo = isExplicitSolo,
            isAccomp = isAccomp,
            isSoprano = isSop,
            isAlto = isAlt,
            isTenor = isTen,
            isBass = isBas,
            hasFClef = hasFClef,
            activeMeasures = activeMeasures,
            avgMidi = avgMidi
        )
    }

    val claimedRoles = mutableSetOf<String>()

    // Pass 1: Explicit Solo and Accompaniment
    meta.forEach { m ->
        when {
            m.isExplicitSolo && totalParts > 1 -> {
                result[m.index] = VocalPartRole(5, "SOLO", "Solo")
            }
            m.isAccomp -> result[m.index] = VocalPartRole(6, "OTHERS", "Accompaniment")
        }
    }

    // Pass 2: Explicit SATB names / abbreviations
    meta.forEach { m ->
        if (result.containsKey(m.index)) return@forEach
        when {
            m.name.contains("soprano 1") || m.name == "s1" || m.name == "s 1" || m.name.contains("soprano i") -> {
                result[m.index] = VocalPartRole(1, "SOPRANO 1", "Soprano 1")
                claimedRoles.add("SOPRANO 1")
            }
            m.name.contains("soprano 2") || m.name == "s2" || m.name == "s 2" || m.name.contains("soprano ii") -> {
                result[m.index] = VocalPartRole(1, "SOPRANO 2", "Soprano 2")
                claimedRoles.add("SOPRANO 2")
            }
            m.isSoprano -> {
                result[m.index] = VocalPartRole(1, "SOPRANO", "Soprano")
                claimedRoles.add("SOPRANO")
            }
            m.name.contains("alto 1") || m.name == "a1" || m.name == "a 1" || m.name.contains("alto i") -> {
                result[m.index] = VocalPartRole(2, "ALTO 1", "Alto 1")
                claimedRoles.add("ALTO 1")
            }
            m.name.contains("alto 2") || m.name == "a2" || m.name == "a 2" || m.name.contains("alto ii") -> {
                result[m.index] = VocalPartRole(2, "ALTO 2", "Alto 2")
                claimedRoles.add("ALTO 2")
            }
            m.isAlto -> {
                result[m.index] = VocalPartRole(2, "ALTO", "Alto")
                claimedRoles.add("ALTO")
            }
            m.name.contains("tenor 1") || m.name == "t1" || m.name == "t 1" || m.name.contains("tenor i") -> {
                result[m.index] = VocalPartRole(3, "TENOR 1", "Tenor 1")
                claimedRoles.add("TENOR 1")
            }
            m.name.contains("tenor 2") || m.name == "t2" || m.name == "t 2" || m.name.contains("tenor ii") -> {
                result[m.index] = VocalPartRole(3, "TENOR 2", "Tenor 2")
                claimedRoles.add("TENOR 2")
            }
            m.isTenor -> {
                result[m.index] = VocalPartRole(3, "TENOR", "Tenor")
                claimedRoles.add("TENOR")
            }
            m.name.contains("bass 1") || m.name == "b1" || m.name == "b 1" || m.name.contains("bass i") -> {
                result[m.index] = VocalPartRole(4, "BASS 1", "Bass 1")
                claimedRoles.add("BASS 1")
            }
            m.name.contains("bass 2") || m.name == "b2" || m.name == "b 2" || m.name.contains("bass ii") -> {
                result[m.index] = VocalPartRole(4, "BASS 2", "Bass 2")
                claimedRoles.add("BASS 2")
            }
            m.isBass -> {
                result[m.index] = VocalPartRole(4, "BASS", "Bass")
                claimedRoles.add("BASS")
            }
        }
    }

    // Pass 2.5: Structural Soloist Detection for >= 5-part vocal ensembles with generic OMR part names
    val vocalCandidates = meta.filter { !it.isAccomp && it.activeMeasures > 0 }
    if (vocalCandidates.size >= 5 && result.values.none { it.roleStr == "SOLO" }) {
        val activeSorted = vocalCandidates.map { it.activeMeasures }.sortedDescending()
        val mCore = activeSorted[3]
        val coreCandidates = vocalCandidates.filter { mCore < 4 || it.activeMeasures >= mCore * 0.45 }
        val sopranoAnchor = coreCandidates
            .filter { !it.hasFClef }
            .maxByOrNull { it.avgMidi }

        vocalCandidates.forEach { m ->
            if (result.containsKey(m.index)) return@forEach
            val isSparseSolo = mCore >= 4 && m.activeMeasures < mCore * 0.45
            val isAboveChoirSolo = sopranoAnchor != null &&
                m.index < sopranoAnchor.index &&
                (m.hasFClef || (m.avgMidi > 0.0 && m.avgMidi < sopranoAnchor.avgMidi - 3.0))
            if (isSparseSolo || isAboveChoirSolo) {
                val remainingVocal = vocalCandidates.count { cand ->
                    cand.index != m.index && result[cand.index]?.roleStr != "SOLO"
                }
                if (remainingVocal >= 4) {
                    result[m.index] = VocalPartRole(5, "SOLO", "Solo")
                }
            }
        }
    }

    // Pass 3: Fill unassigned vocal parts using Clef + Tessitura against unclaimed roles
    val unassigned = meta.filter { !result.containsKey(it.index) }
    if (unassigned.isNotEmpty()) {
        val unassignedBass = unassigned.filter { it.hasFClef || (it.avgMidi in 1.0..59.9) }
            .sortedByDescending { it.avgMidi }
        val unassignedTreble = unassigned.filter { !it.hasFClef && (it.avgMidi == 0.0 || it.avgMidi >= 60.0) }
            .sortedByDescending { it.avgMidi }

        unassignedTreble.forEach { m ->
            when {
                "SOPRANO" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(1, "SOPRANO", "Soprano")
                    claimedRoles.add("SOPRANO")
                }
                "ALTO" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(2, "ALTO", "Alto")
                    claimedRoles.add("ALTO")
                }
                unassignedTreble.size >= 3 && unassignedBass.size <= 1 && "TENOR" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(3, "TENOR", "Tenor")
                    claimedRoles.add("TENOR")
                }
                else -> {
                    val role = if (m.avgMidi >= 66.5) 1 else 2
                    val roleStr = if (role == 1) "SOPRANO" else "ALTO"
                    val roleName = if (role == 1) "Soprano" else "Alto"
                    result[m.index] = VocalPartRole(role, roleStr, roleName)
                }
            }
        }

        unassignedBass.forEach { m ->
            when {
                unassignedBass.size >= 2 && "TENOR" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(3, "TENOR", "Tenor")
                    claimedRoles.add("TENOR")
                }
                unassignedBass.size >= 2 && "BASS" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(4, "BASS", "Bass")
                    claimedRoles.add("BASS")
                }
                "TENOR" in claimedRoles && "BASS" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(4, "BASS", "Bass")
                    claimedRoles.add("BASS")
                }
                "BASS" in claimedRoles && "TENOR" !in claimedRoles -> {
                    result[m.index] = VocalPartRole(3, "TENOR", "Tenor")
                    claimedRoles.add("TENOR")
                }
                else -> {
                    val role = if (m.avgMidi in 1.0..54.9) 4 else 3
                    val roleStr = if (role == 3) "TENOR" else "BASS"
                    val roleName = if (role == 3) "Tenor" else "Bass"
                    result[m.index] = VocalPartRole(role, roleStr, roleName)
                }
            }
        }
    }

    return result
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
    val partHasFClefMap = mutableMapOf<Int, Boolean>()
    val scorePartIdToIndex = mutableMapOf<String, Int>()
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
    var customVoiceStr: String? = null
    var sourceNoteId: Int? = null
    var staff: Int? = null
    var noteType: String? = null
    var lyricText: String? = null
    var stem: String? = null
    var beam: String? = null
    var beam2: String? = null
    var beam3: String? = null
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
                            val sId = parser.getAttributeValue(null, "id")
                            if (!sId.isNullOrBlank()) {
                                scorePartIdToIndex[sId] = currentScorePartIndex
                            }
                        }
                        "part-name" -> {
                            val pName = parser.nextText().trim()
                            if (pName.isNotBlank() && currentScorePartIndex > 0) {
                                partNamesMap[currentScorePartIndex] = pName
                            }
                        }
                        "part-abbreviation" -> {
                            val pAbbr = parser.nextText().trim()
                            if (pAbbr.isNotBlank() && currentScorePartIndex > 0) {
                                val existing = partNamesMap[currentScorePartIndex]
                                if (existing.isNullOrBlank()) {
                                    partNamesMap[currentScorePartIndex] = pAbbr
                                } else {
                                    partNamesMap[currentScorePartIndex] = "$existing $pAbbr"
                                }
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
                            val pId = parser.getAttributeValue(null, "id")
                            if (!pId.isNullOrBlank() && scorePartIdToIndex.containsKey(pId)) {
                                currentPartIndex = scorePartIdToIndex[pId]!!
                            } else {
                                currentPartIndex++
                            }
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
                        "sign" -> {
                            val signVal = parser.nextText().trim().uppercase()
                            if (signVal == "F") {
                                partHasFClefMap[currentPartIndex] = true
                            }
                        }
                        "clef-octave-change" -> {
                            val octChange = parser.nextText().trim().toIntOrNull()
                            if (octChange != null && octChange < 0) {
                                partHasFClefMap[currentPartIndex] = true
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
                            sourceNoteId = parser.getAttributeValue(null, "data-vx-id")?.trim()?.toIntOrNull()
                            customVoiceStr = parser.getAttributeValue(null, "data-vx-voice")
                                ?.trim()
                                ?.takeIf { it.isNotEmpty() && !it.equals("UNKNOWN", ignoreCase = true) }
                            customVoice = customVoiceStr?.let { cv ->
                                when (cv.uppercase()) {
                                    "1", "S", "S1", "S2", "SOPRANO", "SOPRANO 1", "SOPRANO 2" -> 1
                                    "2", "A", "A1", "A2", "ALTO", "ALTO 1", "ALTO 2" -> 2
                                    "3", "T", "T1", "T2", "TENOR", "TENOR 1", "TENOR 2" -> 3
                                    "4", "B", "B1", "B2", "BASS", "BASS 1", "BASS 2" -> 4
                                    "UNISON_TB", "TB" -> 3
                                    "UNISON_SA", "SA" -> 1
                                    "5", "SOLO" -> 5
                                    "BARITONE SOLO", "BARITONE_SOLO", "BARITONE" -> 5
                                    "6", "OTHERS", "OTHER" -> 6
                                    else -> cv.toIntOrNull()
                                }
                            }
                            staff = null
                            noteType = null
                            lyricText = null
                            stem = null
                            beam = null
                            beam2 = null
                            beam3 = null
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
                        "stem" -> if (inNote) {
                            stem = parser.nextText().trim().lowercase()
                        }
                        "beam" -> if (inNote) {
                            val bNum = parser.getAttributeValue(null, "number")?.toIntOrNull() ?: 1
                            val bText = parser.nextText().trim().lowercase()
                            when (bNum) {
                                1 -> beam = bText
                                2 -> beam2 = bText
                                3 -> beam3 = bText
                            }
                        }
                        "text" -> if (inNote) {
                            val txt = parser.nextText().trim()
                            if (txt.isNotBlank()) {
                                lyricText = txt
                            }
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
                                customVoice != null && customVoice in 1..6 -> customVoice
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
                                customVoice = customVoice,
                                customVoiceStr = customVoiceStr,
                                lyric = lyricText,
                                stem = stem,
                                beam = beam,
                                beam2 = beam2,
                                beam3 = beam3,
                                sourceNoteId = sourceNoteId
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
        val universalPartMap = computeUniversalVocalPartMap(
            totalParts = totalParts,
            partNamesMap = partNamesMap,
            partNotesMap = partNotesMap,
            partHasFClefMap = partHasFClefMap,
            scoreTitle = resolvedTitle
        )

        partMeasuresMap.entries.sortedBy { it.key }.forEach { entry ->
            val partIndex = entry.key
            val originalMeasuresInPart = entry.value

            val adjustedMeasures = originalMeasuresInPart.mapIndexed { mIdx, measure ->
                val notesByTime = measure.notes.groupBy { it.startTimeDivisions }

                val adjustedNotes = measure.notes.map { note ->
                    val group = notesByTime[note.startTimeDivisions] ?: listOf(note)
                    val isChordGroup = group.size > 1 && !note.isRest

                    val pNameLower = (partNamesMap[partIndex] ?: "").lowercase()
                    val isPartCondensedSA = (pNameLower.contains("soprano") && pNameLower.contains("alto")) ||
                        pNameLower.contains("s/a") || pNameLower.contains("s & a") || pNameLower.contains("s.a.") || pNameLower == "sa"
                    val isPartCondensedTB = (pNameLower.contains("tenor") && pNameLower.contains("bass")) ||
                        pNameLower.contains("t/b") || pNameLower.contains("t & b") || pNameLower.contains("t.b.") || pNameLower == "tb"

                    val isolatedVoice = when {
                        note.customVoice != null && note.customVoice in 1..6 -> note.customVoice
                        isPartCondensedSA -> {
                            if (isChordGroup) {
                                val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                val idx = sorted.indexOf(note)
                                if (idx == 0) 1 else 2
                            } else if (note.originalVoice == 2) {
                                2
                            } else {
                                1
                            }
                        }
                        isPartCondensedTB -> {
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
                        totalParts >= 4 -> {
                            universalPartMap[partIndex]?.voice ?: (if (partIndex in 1..4) partIndex else 6)
                        }
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

                    // Choral & Chord Lyric association:
                    // Inherit strictly within chord group of same part (never cross-part into Accompaniment or other parts)
                    val chordLyric = if (!note.isRest) group.firstOrNull { !it.lyric.isNullOrBlank() }?.lyric else null
                    val resolvedLyric = if (note.lyric.isNullOrBlank()) chordLyric else note.lyric

                    val chordStem = group.firstOrNull { !it.stem.isNullOrBlank() }?.stem
                    val resolvedStem = if (note.stem.isNullOrBlank()) chordStem else note.stem

                    val chordBeam = group.firstOrNull { !it.beam.isNullOrBlank() }?.beam
                    val resolvedBeam = if (note.beam.isNullOrBlank()) chordBeam else note.beam

                    val chordBeam2 = group.firstOrNull { !it.beam2.isNullOrBlank() }?.beam2
                    val resolvedBeam2 = if (note.beam2.isNullOrBlank()) chordBeam2 else note.beam2

                    val chordBeam3 = group.firstOrNull { !it.beam3.isNullOrBlank() }?.beam3
                    val resolvedBeam3 = if (note.beam3.isNullOrBlank()) chordBeam3 else note.beam3

                    val assignedRole = universalPartMap[partIndex]
                    val isSoloRole = assignedRole?.roleStr == "SOLO" || assignedRole?.roleStr == "BARITONE SOLO"
                    val pitchedChordGroup = if (!note.isRest) group.filter { !it.isRest } else emptyList()
                    val distinctChordPitches = if (pitchedChordGroup.size > 1) {
                        pitchedChordGroup.map { calculateMidiNote(it.step, it.alter, it.octave) }.distinct().sortedDescending()
                    } else {
                        emptyList()
                    }
                    val inferredInStaffDivisi = if (
                        note.customVoiceStr.isNullOrBlank() &&
                        !isPartCondensedSA &&
                        !isPartCondensedTB &&
                        totalParts >= 4 &&
                        distinctChordPitches.size >= 2
                    ) {
                        val noteMidi = calculateMidiNote(note.step, note.alter, note.octave)
                        val isTopPitch = noteMidi == distinctChordPitches.first()
                        when (assignedRole?.roleStr) {
                            "SOPRANO" -> if (isTopPitch) "S1" else "S2"
                            "ALTO" -> if (isTopPitch) "A1" else "A2"
                            "TENOR" -> if (isTopPitch) "T1" else "T2"
                            "BASS" -> if (isTopPitch) "B1" else "B2"
                            else -> null
                        }
                    } else {
                        null
                    }
                    val noteCustomVoiceStr = when {
                        !note.customVoiceStr.isNullOrBlank() -> {
                            if (isSoloRole && (note.customVoiceStr.equals("SOLO", ignoreCase = true) || note.customVoiceStr.equals("BARITONE SOLO", ignoreCase = true) || note.customVoiceStr == "5")) {
                                "SOLO"
                            } else {
                                note.customVoiceStr
                            }
                        }
                        isSoloRole -> "SOLO"
                        inferredInStaffDivisi != null -> inferredInStaffDivisi
                        else -> null
                    }
                    val noteCustomVoice = when {
                        noteCustomVoiceStr != null -> when (noteCustomVoiceStr.uppercase()) {
                            "BARITONE SOLO", "BARITONE", "SOLO", "5" -> 5
                            else -> note.customVoice
                        }
                        else -> note.customVoice
                    }

                    note.copy(
                        voice = isolatedVoice,
                        customVoiceStr = noteCustomVoiceStr,
                        customVoice = noteCustomVoice,
                        lyric = resolvedLyric,
                        stem = resolvedStem,
                        beam = resolvedBeam,
                        beam2 = resolvedBeam2,
                        beam3 = resolvedBeam3
                    )
                }
                finalNotes.addAll(adjustedNotes)
                measure.copy(notes = adjustedNotes)
            }

            // Group notes by part for direct access
            val partNotes = adjustedMeasures.flatMap { it.notes }
            val declaredName = partNamesMap[partIndex]?.takeIf { it.isNotBlank() }
            val assignedRole = universalPartMap[partIndex]
            val isGenericName = declaredName == null ||
                declaredName.lowercase() in setOf(
                    "voice", "voices", "vocal", "vocals",
                    "voice oohs", "voice aahs", "choir aahs", "synth voice", "oohs", "aahs"
                ) ||
                declaredName.matches(Regex("(?i)(part|voice|staff)\\s*\\d+"))
            val resolvedPartName = when {
                assignedRole?.roleStr == "SOLO" || assignedRole?.roleStr == "BARITONE SOLO" -> "Solo"
                totalParts >= 4 && isGenericName -> assignedRole?.defaultName ?: "Part $partIndex"
                declaredName != null -> declaredName
                totalParts == 1 -> "Voice Part"
                totalParts == 2 -> if (partIndex == 1) "Soprano / Alto" else "Tenor / Bass"
                totalParts == 3 -> when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Bass"; else -> "Part $partIndex" }
                totalParts >= 4 -> assignedRole?.defaultName ?: "Part $partIndex"
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

                    val pNameLower = (partNamesMap[partIndex] ?: "").lowercase()
                    val isPartCondensedSA = (pNameLower.contains("soprano") && pNameLower.contains("alto")) ||
                        pNameLower.contains("s/a") || pNameLower.contains("s & a") || pNameLower.contains("s.a.") || pNameLower == "sa"
                    val isPartCondensedTB = (pNameLower.contains("tenor") && pNameLower.contains("bass")) ||
                        pNameLower.contains("t/b") || pNameLower.contains("t & b") || pNameLower.contains("t.b.") || pNameLower == "tb"

                    val isolatedVoice = when {
                        note.customVoice != null && note.customVoice in 1..6 -> note.customVoice
                        isPartCondensedSA -> {
                            if (isChordGroup) {
                                val sorted = group.filter { !it.isRest }.sortedByDescending { calculateMidiNote(it.step, it.alter, it.octave) }
                                val idx = sorted.indexOf(note)
                                if (idx == 0) 1 else 2
                            } else if (note.originalVoice == 2) {
                                2
                            } else {
                                1
                            }
                        }
                        isPartCondensedTB -> {
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
                        totalPartsFallback >= 4 -> universalPartMap[partIndex]?.voice ?: (if (partIndex in 1..4) partIndex else 6)
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
                    val chordLyric = group.firstOrNull { !it.lyric.isNullOrBlank() }?.lyric
                    val resolvedLyric = if (note.lyric.isNullOrBlank()) chordLyric else note.lyric
                    val chordStem = group.firstOrNull { !it.stem.isNullOrBlank() }?.stem
                    val resolvedStem = if (note.stem.isNullOrBlank()) chordStem else note.stem
                    val chordBeam = group.firstOrNull { !it.beam.isNullOrBlank() }?.beam
                    val resolvedBeam = if (note.beam.isNullOrBlank()) chordBeam else note.beam
                    val chordBeam2 = group.firstOrNull { !it.beam2.isNullOrBlank() }?.beam2
                    val resolvedBeam2 = if (note.beam2.isNullOrBlank()) chordBeam2 else note.beam2
                    val chordBeam3 = group.firstOrNull { !it.beam3.isNullOrBlank() }?.beam3
                    val resolvedBeam3 = if (note.beam3.isNullOrBlank()) chordBeam3 else note.beam3
                    note.copy(
                        voice = isolatedVoice,
                        lyric = resolvedLyric,
                        stem = resolvedStem,
                        beam = resolvedBeam,
                        beam2 = resolvedBeam2,
                        beam3 = resolvedBeam3
                    )
                }
                finalNotes.addAll(adjustedNotes)
                finalParts.add(
                    MusicXmlPart(
                        id = partIndex,
                        name = when {
                            totalPartsFallback >= 4 -> universalPartMap[partIndex]?.defaultName ?: "Part $partIndex"
                            totalPartsFallback == 2 -> if (partIndex == 1) "Soprano / Alto" else "Tenor / Bass"
                            else -> when (partIndex) { 1 -> "Soprano"; 2 -> "Alto"; 3 -> "Tenor"; 4 -> "Bass"; else -> "Part $partIndex" }
                        },
                        notes = adjustedNotes,
                        measures = listOf(MusicXmlMeasure(1, adjustedNotes))
                    )
                )
            }
        }

        val eventsJson = generateEventsJsonFromScoreParts(finalParts, STANDARD_TPQ, resolvedTitle)
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

fun generateEventsJsonFromScoreParts(parts: List<MusicXmlPart>, tpq: Int, scoreTitle: String? = null): String {
    val eventsList = mutableListOf<MusicalEvent>()
    var eventIndex = 0
    val safeTpq = if (tpq > 0) tpq else 1

    val inferredPartRoles = computeUniversalVocalPartMap(
        totalParts = parts.size,
        partNamesMap = parts.mapIndexed { idx, p -> (idx + 1) to p.name }.toMap(),
        partNotesMap = parts.mapIndexed { idx, p -> (idx + 1) to p.notes }.toMap(),
        partHasFClefMap = emptyMap(),
        scoreTitle = scoreTitle
    )

    parts.forEachIndexed { partListIdx, part ->
        val partOrdinal = partListIdx + 1
        val inferredRole = inferredPartRoles[partOrdinal]
        val nameLower = part.name.lowercase()
        val isSoloPart = inferredRole?.roleStr == "SOLO" ||
            nameLower.contains("baritone") || nameLower.contains("solo") ||
            nameLower.contains("cantor") || nameLower.contains("leader") || nameLower.contains("descant") ||
            nameLower.contains("sol.") || nameLower.trim() == "sol" || Regex("(?i)\\bsol\\b").containsMatchIn(nameLower)
        val pitchedNotesByMeasureTime = part.notes
            .filter { !it.isRest }
            .groupBy { "${it.measureIndex}_${it.measureNumber}_${it.startTimeDivisions}" }

        part.notes.forEach { note ->
            val isCondensedSA = (nameLower.contains("soprano") && nameLower.contains("alto")) ||
                nameLower.contains("s/a") || nameLower.contains("s & a") || nameLower.contains("s.a.") || nameLower.contains("s. a.") || nameLower == "sa"
            val isCondensedTB = (nameLower.contains("tenor") && nameLower.contains("bass")) ||
                nameLower.contains("t/b") || nameLower.contains("t & b") || nameLower.contains("t.b.") || nameLower.contains("t. b.") || nameLower == "tb"

            val chordPeers = if (!note.isRest) {
                pitchedNotesByMeasureTime["${note.measureIndex}_${note.measureNumber}_${note.startTimeDivisions}"].orEmpty()
            } else {
                emptyList()
            }
            val distinctChordMidis = if (chordPeers.size > 1) {
                chordPeers.map { calculateMidiNote(it.step, it.alter, it.octave) }.distinct().sortedDescending()
            } else {
                emptyList()
            }
            val openSectionDivisi: (String) -> String = { baseSection ->
                if (!note.isRest && !isCondensedSA && !isCondensedTB && distinctChordMidis.size >= 2) {
                    val midiVal = calculateMidiNote(note.step, note.alter, note.octave)
                    val sub = if (midiVal == distinctChordMidis.first()) "1" else "2"
                    "$baseSection $sub"
                } else {
                    baseSection
                }
            }

            val satbVoiceStr = when {
                !note.customVoiceStr.isNullOrBlank() -> when (note.customVoiceStr.uppercase()) {
                    "1", "S", "SOPRANO" -> "SOPRANO"
                    "S1", "SOPRANO1", "SOPRANO 1" -> "SOPRANO 1"
                    "S2", "SOPRANO2", "SOPRANO 2" -> "SOPRANO 2"
                    "2", "A", "ALTO" -> "ALTO"
                    "A1", "ALTO1", "ALTO 1" -> "ALTO 1"
                    "A2", "ALTO2", "ALTO 2" -> "ALTO 2"
                    "3", "T", "TENOR" -> "TENOR"
                    "T1", "TENOR1", "TENOR 1" -> "TENOR 1"
                    "T2", "TENOR2", "TENOR 2" -> "TENOR 2"
                    "4", "B", "BASS" -> "BASS"
                    "B1", "BASS1", "BASS 1" -> "BASS 1"
                    "B2", "BASS2", "BASS 2" -> "BASS 2"
                    "UNISON_TB", "TB" -> "TENOR / BASS"
                    "UNISON_SA", "SA" -> "SOPRANO / ALTO"
                    "UNISON_S1S2", "S1S2" -> "SOPRANO 1 / 2"
                    "UNISON_A1A2", "A1A2" -> "ALTO 1 / 2"
                    "UNISON_T1T2", "T1T2" -> "TENOR 1 / 2"
                    "UNISON_B1B2", "B1B2" -> "BASS 1 / 2"
                    "TUTTI", "UNISON_ALL", "ALL" -> "TUTTI"
                    "BARITONE SOLO", "BARITONE_SOLO", "BARITONE", "5", "SOLO" -> "SOLO"
                    "6", "OTHERS", "OTHER" -> "OTHERS"
                    else -> note.customVoiceStr
                }
                note.customVoice != null && note.customVoice in 1..6 -> when (note.customVoice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    5 -> "SOLO"
                    6 -> "OTHERS"
                    else -> "SOPRANO"
                }
                isSoloPart -> "SOLO"
                nameLower.contains("solo") || nameLower.contains("cantor") || nameLower.contains("leader") || nameLower.contains("descant") -> "SOLO"
                nameLower.contains("piano") || nameLower.contains("organ") || nameLower.contains("keyboard") || nameLower.contains("guitar") || nameLower.contains("accomp") || nameLower.contains("orch") || nameLower.contains("strings") || nameLower.contains("other") -> "OTHERS"
                isCondensedSA -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                isCondensedTB -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                nameLower.contains("soprano 1") || nameLower == "s1" || nameLower == "s 1" || nameLower.contains("soprano i") -> "SOPRANO 1"
                nameLower.contains("soprano 2") || nameLower == "s2" || nameLower == "s 2" || nameLower.contains("soprano ii") -> "SOPRANO 2"
                nameLower.contains("alto 1") || nameLower == "a1" || nameLower == "a 1" || nameLower.contains("alto i") -> "ALTO 1"
                nameLower.contains("alto 2") || nameLower == "a2" || nameLower == "a 2" || nameLower.contains("alto ii") -> "ALTO 2"
                nameLower.contains("tenor 1") || nameLower == "t1" || nameLower == "t 1" || nameLower.contains("tenor i") -> "TENOR 1"
                nameLower.contains("tenor 2") || nameLower == "t2" || nameLower == "t 2" || nameLower.contains("tenor ii") -> "TENOR 2"
                nameLower.contains("bass 1") || nameLower == "b1" || nameLower == "b 1" || nameLower.contains("bass i") -> "BASS 1"
                nameLower.contains("bass 2") || nameLower == "b2" || nameLower == "b 2" || nameLower.contains("bass ii") -> "BASS 2"
                nameLower.contains("soprano") || nameLower == "s" || nameLower.startsWith("s.") || Regex("(?i)\\bs\\b").containsMatchIn(nameLower) -> openSectionDivisi("SOPRANO")
                nameLower.contains("alto") || nameLower == "a" || nameLower.startsWith("a.") || Regex("(?i)\\ba\\b").containsMatchIn(nameLower) -> openSectionDivisi("ALTO")
                nameLower.contains("tenor") || nameLower == "t" || nameLower.startsWith("t.") || Regex("(?i)\\bt\\b").containsMatchIn(nameLower) -> openSectionDivisi("TENOR")
                nameLower.contains("bass") || nameLower == "b" || nameLower.startsWith("b.") || Regex("(?i)\\bb\\b").containsMatchIn(nameLower) -> openSectionDivisi("BASS")
                parts.size >= 4 && inferredRole != null -> when (inferredRole.roleStr) {
                    "SOPRANO", "ALTO", "TENOR", "BASS" -> openSectionDivisi(inferredRole.roleStr)
                    "SOLO" -> "SOLO"
                    else -> "OTHERS"
                }
                note.voice == 5 -> "SOLO"
                note.voice == 6 -> "OTHERS"
                note.voice in 1..4 -> when (note.voice) {
                    1 -> "SOPRANO"
                    2 -> "ALTO"
                    3 -> "TENOR"
                    4 -> "BASS"
                    else -> "SOPRANO"
                }
                parts.size == 2 -> when (part.id) {
                    1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
                    2 -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                    else -> "SOPRANO"
                }
                note.staff == 2 -> if (note.voice == 4 || note.voice == 2 || note.originalVoice == 2 || note.originalVoice == 4) "BASS" else "TENOR"
                note.staff == 1 -> if (note.voice == 2 || note.originalVoice == 2) "ALTO" else "SOPRANO"
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
                    sourceNoteId = note.sourceNoteId,
                    isRest = note.isRest,
                    isChordMember = note.isChord,
                    satbVoice = satbVoiceStr,
                    satbConfidence = 1.0f,
                    playbackTrack = satbVoiceStr,
                    rawParticipatingParts = normalizeLegacyVoiceToParts(satbVoiceStr).takeIf { it.isNotEmpty() },
                    rawIsVocal = if (note.isRest) false else satbVoiceStr != "OTHERS"
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
