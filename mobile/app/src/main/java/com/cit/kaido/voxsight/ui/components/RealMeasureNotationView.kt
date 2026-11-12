package com.cit.kaido.voxsight.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlPart
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.pitch.PitchAttempt
import kotlin.math.abs
import kotlin.math.min

/**
 * High-level pitch classification categories for individual notes.
 */
enum class NotePitchStatus {
    IN_TUNE,
    SUNG_SHARP,
    SUNG_FLAT,
    NOT_SUNG
}

/**
 * Detailed pitch evaluation record for a single note within a practice measure.
 */
data class FlaggedNoteDetail(
    val noteIndex: Int,
    val noteName: String,
    val lyric: String?,
    val status: NotePitchStatus,
    val statusLabel: String,
    val deviationCents: Float?,
    val isFlagged: Boolean,
    val badgeColor: Color,
    val badgeBg: Color
)

private data class NoteClassificationColors(
    val status: NotePitchStatus,
    val statusLabel: String,
    val color: Color,
    val bg: Color
)

/**
 * Pure evaluator mapping notes of a measure to their exact pitch evaluation status.
 * Ensures strict 1:1 classification without duplicate counting.
 */
fun evaluateMeasureNoteDetails(
    filteredNotes: List<MusicXmlNote>,
    measureNumber: Int,
    pitchAttempts: List<PitchAttempt>
): List<FlaggedNoteDetail> {
    val pitchedNotes = filteredNotes.filter { !it.isRest }
    return pitchedNotes.mapIndexed { index, note ->
        val noteAttempts = matchAttemptsForMeasureNote(
            note = note,
            measureNumber = measureNumber,
            pitchAttempts = pitchAttempts
        )
        val isAttempted = noteAttempts.isNotEmpty()
        val hasMatch = noteAttempts.any { it.isMatch }
        val validDeviations = noteAttempts.map { it.deviationCents }.filter { abs(it) < 500f }
        val avgDev = if (validDeviations.isNotEmpty()) {
            validDeviations.average().toFloat()
        } else if (isAttempted) {
            noteAttempts.map { it.deviationCents }.average().toFloat()
        } else 0f

        val (status, statusLabel, color, bg) = when {
            !isAttempted -> {
                NoteClassificationColors(NotePitchStatus.NOT_SUNG, "Not Sung", Color(0xFF6E717E), Color(0xFFF1F2F6))
            }
            !hasMatch -> {
                if (avgDev > 0f) {
                    NoteClassificationColors(NotePitchStatus.SUNG_SHARP, "Sung Sharp", Color(0xFFE65100), Color(0xFFFFF3E0))
                } else {
                    NoteClassificationColors(NotePitchStatus.SUNG_FLAT, "Sung Flat", Color(0xFFC62828), Color(0xFFFFEBEE))
                }
            }
            avgDev > 25f -> {
                NoteClassificationColors(NotePitchStatus.SUNG_SHARP, "Sung Sharp", Color(0xFFE65100), Color(0xFFFFF3E0))
            }
            avgDev < -25f -> {
                NoteClassificationColors(NotePitchStatus.SUNG_FLAT, "Sung Flat", Color(0xFFC62828), Color(0xFFFFEBEE))
            }
            else -> {
                NoteClassificationColors(NotePitchStatus.IN_TUNE, "In Tune", Color(0xFF1B5E20), Color(0xFFE8F5E9))
            }
        }

        val stepFormatted = note.step.replace("#", "♯").replace("b", "♭")
        val noteName = "$stepFormatted${note.octave}"
        val directLyric = note.lyric?.trim()?.takeIf { it.isNotBlank() }

        FlaggedNoteDetail(
            noteIndex = index + 1,
            noteName = noteName,
            lyric = directLyric,
            status = status,
            statusLabel = statusLabel,
            deviationCents = if (isAttempted) avgDev else null,
            isFlagged = (status != NotePitchStatus.IN_TUNE),
            badgeColor = color,
            badgeBg = bg
        )
    }
}

private val SUMMARY_EVENT_ID_REGEX = Regex("^t(\\d+)-p(\\d+)-c(\\d+)$")

private fun diatonicStepIndex(step: String): Int {
    val cleanStep = step.take(1).uppercase()
    return when (cleanStep) {
        "C" -> 0
        "D" -> 1
        "E" -> 2
        "F" -> 3
        "G" -> 4
        "A" -> 5
        "B" -> 6
        else -> 0
    }
}

private fun isSoloPartSummaryCandidate(part: MusicXmlPart): Boolean {
    val pName = part.name.trim().lowercase()
    return listOf("solo", "baritone", "cantor", "leader", "descant", "obbligato", "sol.").any { pName.contains(it) } ||
        part.notes.any { !it.isRest && (it.customVoice == 5 || it.voice == 5) }
}

private fun isAccompanimentPartSummaryCandidate(part: MusicXmlPart): Boolean {
    val pName = part.name.trim().lowercase()
    return listOf("other", "accomp", "piano", "organ", "keyboard", "guitar", "strings", "orch").any { pName.contains(it) }
}

private fun doesPartMatchSelectedVoice(part: MusicXmlPart, voice: SATBVoice): Boolean {
    val pName = part.name.trim().lowercase()
    val isSolo = isSoloPartSummaryCandidate(part)
    val isAccomp = isAccompanimentPartSummaryCandidate(part)
    val isCondensedSA = (pName.contains("soprano") && pName.contains("alto")) ||
        pName.contains("s/a") || pName.contains("s & a") || pName.contains("s.a.") ||
        pName.contains("s. a.") || pName == "sa" || pName.contains("women") || pName.contains("treble")
    val isCondensedTB = (pName.contains("tenor") && pName.contains("bass")) ||
        pName.contains("t/b") || pName.contains("t & b") || pName.contains("t.b.") ||
        pName.contains("t. b.") || pName == "tb" || pName.contains("men")

    return when (voice) {
        SATBVoice.SOLO -> isSolo
        SATBVoice.OTHERS -> isAccomp
        SATBVoice.SOPRANO -> !isSolo && !isAccomp && (
            pName.contains("soprano") || pName == "s" || pName.startsWith("s.") ||
            Regex("(?i)\\bs\\b").containsMatchIn(pName) || isCondensedSA
        )
        SATBVoice.ALTO -> !isSolo && !isAccomp && (
            pName.contains("alto") || pName == "a" || pName.startsWith("a.") ||
            Regex("(?i)\\ba\\b").containsMatchIn(pName) || isCondensedSA
        )
        SATBVoice.TENOR -> !isSolo && !isAccomp && (
            pName.contains("tenor") || pName == "t" || pName.startsWith("t.") ||
            Regex("(?i)\\bt\\b").containsMatchIn(pName) || isCondensedTB
        )
        SATBVoice.BASS -> !isSolo && !isAccomp && (
            pName.contains("bass") || pName == "b" || pName.startsWith("b.") ||
            Regex("(?i)\\bb\\b").containsMatchIn(pName) || isCondensedTB
        )
        SATBVoice.UNKNOWN -> !isAccomp
    }
}

/**
 * Resolves the exact single-measure musical content for the user's selected vocal part in Practice Summary.
 *
 * Guarantees:
 * 1. Never misidentifies Part 1 ("Baritone Solo" / "Solo") as Soprano in 5-part Solo+SATB scores.
 * 2. Handles split-system multi-part scores (e.g., 6-page Awit ng Paghahangad where Soprano notes are in P4
 *    for M1-M7 and P1 for M8-M24) by preferring parts with active pitched notes in [measureNumber].
 * 3. Disambiguates duplicate XML measure numbers across system breaks via [MusicXmlNote.measureIndex].
 * 4. Preserves authentic leading/interleaved rests while stripping polyphonic backup filler rests that
 *    overlap active sustained pitched notes in the same measure.
 */
fun resolveMeasureNotesForSummary(
    measureNumber: Int,
    score: MusicXmlScore?,
    voice: SATBVoice = SATBVoice.SOPRANO,
    pitchAttempts: List<PitchAttempt> = emptyList()
): List<MusicXmlNote> {
    if (score == null) return emptyList()

    val voiceIndex = when (voice) {
        SATBVoice.SOPRANO -> 1
        SATBVoice.ALTO -> 2
        SATBVoice.TENOR -> 3
        SATBVoice.BASS -> 4
        SATBVoice.SOLO -> 5
        SATBVoice.OTHERS -> 6
        SATBVoice.UNKNOWN -> 1
    }
    val isLowerVoice = voice == SATBVoice.ALTO || voice == SATBVoice.BASS

    val measureAttempts = pitchAttempts.filter { it.measureNumber == measureNumber }
    val attemptedPartIdsInMeasure = measureAttempts.mapNotNull {
        SUMMARY_EVENT_ID_REGEX.matchEntire(it.eventId.trim())?.groupValues?.getOrNull(2)?.toIntOrNull()
    }.toSet()
    val attemptedTicksInMeasure = measureAttempts.mapNotNull {
        SUMMARY_EVENT_ID_REGEX.matchEntire(it.eventId.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.toSet()
    val attemptedPartIdsInSession = pitchAttempts.mapNotNull {
        SUMMARY_EVENT_ID_REGEX.matchEntire(it.eventId.trim())?.groupValues?.getOrNull(2)?.toIntOrNull()
    }.toSet()

    val rawCandidateNotes: List<MusicXmlNote> = if (score.parts.isNotEmpty()) {
        val nonSoloChoralParts = score.parts.filter {
            !isSoloPartSummaryCandidate(it) && !isAccompanimentPartSummaryCandidate(it)
        }
        val positionalPart = when (voice) {
            SATBVoice.SOPRANO -> nonSoloChoralParts.getOrNull(0)
            SATBVoice.ALTO -> nonSoloChoralParts.getOrNull(1) ?: nonSoloChoralParts.getOrNull(0)
            SATBVoice.TENOR -> nonSoloChoralParts.getOrNull(2) ?: nonSoloChoralParts.lastOrNull()
            SATBVoice.BASS -> nonSoloChoralParts.getOrNull(3) ?: nonSoloChoralParts.lastOrNull()
            SATBVoice.SOLO -> score.parts.firstOrNull { isSoloPartSummaryCandidate(it) }
            SATBVoice.OTHERS -> score.parts.firstOrNull { isAccompanimentPartSummaryCandidate(it) }
            SATBVoice.UNKNOWN -> score.parts.firstOrNull()
        }

        val namedMatchingParts = score.parts.filter { doesPartMatchSelectedVoice(it, voice) }

        val selectedPart: MusicXmlPart? =
            // 1. Part explicitly referenced by PitchAttempt eventId in this measure that has pitched notes
            score.parts.firstOrNull { part ->
                part.id in attemptedPartIdsInMeasure &&
                    part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }
            // 2. Named matching part (also in session's attempted parts) with pitched notes in this measure
            ?: namedMatchingParts.firstOrNull { part ->
                part.id in attemptedPartIdsInSession &&
                    part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }
            // 3. Any named matching part with pitched notes in this measure (handles split-system parts like P1="S" vs P4="Soprano")
            ?: namedMatchingParts.firstOrNull { part ->
                part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }
            // 4. Any part containing pitched notes tagged with voiceIndex in this measure
            ?: score.parts.firstOrNull { part ->
                part.notes.any {
                    it.measureNumber == measureNumber && !it.isRest &&
                        (it.voice == voiceIndex || it.customVoice == voiceIndex)
                }
            }
            // 5. Positional choral fallback part if it has pitched notes in this measure
            ?: positionalPart?.takeIf { part ->
                part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }
            // 6. Session-attempted part or single active cutaway staff in this measure
            ?: score.parts.firstOrNull { part ->
                part.id in attemptedPartIdsInSession &&
                    part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }
            ?: score.parts.filter { part ->
                !isAccompanimentPartSummaryCandidate(part) &&
                    part.notes.any { it.measureNumber == measureNumber && !it.isRest }
            }.takeIf { it.size == 1 || measureAttempts.isNotEmpty() }?.firstOrNull()
            // 7. Fallback to named or positional part even if resting in this measure
            ?: namedMatchingParts.firstOrNull()
            ?: positionalPart
            ?: score.parts.firstOrNull()

        selectedPart?.notes?.filter { it.measureNumber == measureNumber } ?: emptyList()
    } else {
        val allMeasureNotes = score.notes.filter { it.measureNumber == measureNumber }
        val voicePitched = allMeasureNotes.filter { !it.isRest && (it.voice == voiceIndex || it.customVoice == voiceIndex) }
        if (voicePitched.isNotEmpty()) {
            allMeasureNotes.filter { it.isRest || it.voice == voiceIndex || it.customVoice == voiceIndex }
        } else {
            allMeasureNotes
        }
    }

    if (rawCandidateNotes.isEmpty()) return emptyList()

    // Disambiguate duplicate measure numbers (e.g., split systems sharing the same XML measure number)
    val byMeasureIndex = rawCandidateNotes.groupBy { it.measureIndex }
    val singleMeasureNotes = if (byMeasureIndex.size <= 1) {
        rawCandidateNotes
    } else {
        byMeasureIndex.values.maxByOrNull { notesInIdx ->
            val tickMatches = notesInIdx.count { !it.isRest && it.startTimeDivisions in attemptedTicksInMeasure }
            val pitchedCount = notesInIdx.count { !it.isRest }
            (tickMatches * 1000) + pitchedCount
        } ?: rawCandidateNotes
    }

    // Measure-level shared lyric fallback across vocal parts
    val measureLyricsByTick = score.notes
        .filter { it.measureNumber == measureNumber && !it.lyric.isNullOrBlank() && it.voice != 6 && it.customVoice != 6 }
        .associate { it.startTimeDivisions to it.lyric!!.trim() }

    val hasVoiceSpecificPitched = singleMeasureNotes.any {
        !it.isRest && (it.voice == voiceIndex || it.customVoice == voiceIndex)
    }

    val attemptsByTick = measureAttempts.mapNotNull { attempt ->
        val tick = SUMMARY_EVENT_ID_REGEX.matchEntire(attempt.eventId.trim())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (tick != null) tick to attempt else null
    }.groupBy({ it.first }, { it.second })

    val sortedTickGroups = singleMeasureNotes
        .groupBy { it.startTimeDivisions }
        .entries
        .sortedBy { it.key }

    // First pass: resolve active pitched notes for the selected vocal line across the measure
    val selectedPitchedByTick = linkedMapOf<Int, MusicXmlNote>()
    var activeVoiceEndTick = -1

    for ((tick, notesAtTick) in sortedTickGroups) {
        val nonRests = notesAtTick.filter { !it.isRest }
        if (nonRests.isEmpty()) continue

        val voiceMatched = if (hasVoiceSpecificPitched) {
            nonRests.filter { it.voice == voiceIndex || it.customVoice == voiceIndex }
        } else {
            nonRests
        }

        val pool = when {
            voiceMatched.isNotEmpty() -> voiceMatched
            // If another voice on a condensed staff has a note while the selected voice is still sustaining, skip
            hasVoiceSpecificPitched && tick < activeVoiceEndTick -> emptyList()
            else -> nonRests
        }
        if (pool.isEmpty()) continue

        val attemptedNamesAtTick = attemptsByTick[tick]?.map { it.noteName.uppercase() }?.toSet() ?: emptySet()
        val attemptMatchedPool = if (attemptedNamesAtTick.isNotEmpty()) {
            pool.filter { "${it.step}${it.octave}".uppercase() in attemptedNamesAtTick }
        } else {
            emptyList()
        }
        val effectivePool = if (attemptMatchedPool.isNotEmpty()) attemptMatchedPool else pool

        val selected = if (isLowerVoice) {
            effectivePool.minByOrNull { (it.octave * 7) + diatonicStepIndex(it.step) } ?: effectivePool.first()
        } else {
            effectivePool.maxByOrNull { (it.octave * 7) + diatonicStepIndex(it.step) } ?: effectivePool.first()
        }

        val sharedLyric = notesAtTick.firstOrNull { !it.lyric.isNullOrBlank() }?.lyric?.trim()
            ?: measureLyricsByTick[selected.startTimeDivisions]
        val finalNote = if (selected.lyric.isNullOrBlank() && sharedLyric != null) {
            selected.copy(lyric = sharedLyric)
        } else {
            selected
        }

        selectedPitchedByTick[tick] = finalNote
        activeVoiceEndTick = maxOf(activeVoiceEndTick, tick + finalNote.durationDivisions)
    }

    val pitchedNotesList = selectedPitchedByTick.values.toList()
    if (pitchedNotesList.isEmpty()) {
        // Measure genuinely contains only rests for this part; keep at most one rest (preferring whole/longest rest)
        val firstTickRests = sortedTickGroups.firstOrNull()?.value ?: return emptyList()
        val longestRest = firstTickRests.maxByOrNull { it.durationDivisions } ?: firstTickRests.first()
        return listOf(longestRest)
    }

    // Second pass: combine pitched notes with genuine non-overlapping rests (excluding polyphonic backup rests)
    val finalMeasureSequence = mutableListOf<MusicXmlNote>()
    val wholeMeasureThreshold = (score.divisions * 3).coerceAtLeast(1440)

    for ((tick, notesAtTick) in sortedTickGroups) {
        val pitchedAtTick = selectedPitchedByTick[tick]
        if (pitchedAtTick != null) {
            finalMeasureSequence.add(pitchedAtTick)
            continue
        }

        val restsAtTick = notesAtTick.filter { it.isRest }
        if (restsAtTick.isEmpty()) continue

        // Ignore rest if a pitched note in the selected voice is currently sustaining across this tick
        val isCoveredBySustainedNote = pitchedNotesList.any { p ->
            p.startTimeDivisions <= tick && tick < (p.startTimeDivisions + p.durationDivisions)
        }
        if (isCoveredBySustainedNote) continue

        val candidateRest = restsAtTick.firstOrNull { it.voice == voiceIndex || it.customVoice == voiceIndex }
            ?: restsAtTick.first()

        // Ignore whole-measure placeholder rests from inactive backup voices when pitched notes exist in the measure
        val isBackupWholeMeasureRest = candidateRest.type.equals("whole", ignoreCase = true) ||
            (candidateRest.durationDivisions >= wholeMeasureThreshold &&
                pitchedNotesList.any { p -> p.startTimeDivisions < candidateRest.startTimeDivisions + candidateRest.durationDivisions })
        if (isBackupWholeMeasureRest) continue

        finalMeasureSequence.add(candidateRest)
    }

    return finalMeasureSequence.sortedBy { it.startTimeDivisions }
}

/**
 * Matches a rendered [MusicXmlNote] in [measureNumber] to its corresponding [PitchAttempt] evaluations.
 * Prioritizes exact tick alignment from [PitchAttempt.eventId] (`t{tick}-p{part}-c{idx}`),
 * and falls back to exact pitch name (`step + alter + octave`), avoiding false partial-letter matches.
 */
fun matchAttemptsForMeasureNote(
    note: MusicXmlNote,
    measureNumber: Int,
    pitchAttempts: List<PitchAttempt>
): List<PitchAttempt> {
    if (note.isRest) return emptyList()
    val measureAttempts = pitchAttempts.filter { it.measureNumber == measureNumber }
    if (measureAttempts.isEmpty()) return emptyList()

    val cleanPitchName = "${note.step}${note.octave}"

    // 1. Match by exact tick position encoded in eventId (t{tick}-p{partId}-c{idx})
    // Supports 1:1, 1:2, and 2:1 division scaling (e.g. 480 vs 960 divisions) with proportional tolerance
    val tickMatched = measureAttempts.filter { attempt ->
        val tick = SUMMARY_EVENT_ID_REGEX.matchEntire(attempt.eventId.trim())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        tick != null && (
            abs(tick - note.startTimeDivisions) <= 15 ||
            abs(tick / 2 - note.startTimeDivisions) <= 15 ||
            abs(tick * 2 - note.startTimeDivisions) <= 15
        )
    }
    if (tickMatched.isNotEmpty()) {
        val exactPitchAtTick = tickMatched.filter { it.noteName.equals(cleanPitchName, ignoreCase = true) }
        if (exactPitchAtTick.isNotEmpty()) {
            return exactPitchAtTick
        }
    }

    // 2. Fallback for legacy/custom eventIds: match by exact full pitch name (e.g. "C#4", "D4"), never by bare first letter
    val pitchMatched = measureAttempts.filter { attempt ->
        attempt.noteName.equals(cleanPitchName, ignoreCase = true)
    }
    if (pitchMatched.isNotEmpty()) {
        return pitchMatched
    }

    // 3. Fallback: if tick matched and attempts have blank noteName
    return tickMatched.filter { it.noteName.isBlank() }
}

/**
 * Authentic Sheet Music Measure View.
 * Natively renders the 5-line musical staff, Treble/Bass clef, diatonic pitch placement,
 * accidentals (♯, ♭), stems, lyrics, and pitch mistake highlights for the single specified measure.
 * Prevents note, stem, and lyric overlaps by isolating the user's specific vocal part and deduplicating chord onsets.
 */
@Composable
fun RealMeasureNotationView(
    measureNumber: Int,
    score: MusicXmlScore?,
    voice: SATBVoice = SATBVoice.SOPRANO,
    pitchAttempts: List<PitchAttempt> = emptyList(),
    mistakeCount: Int = 0,
    isSharp: Boolean = false,
    averageDeviation: Float = 0f,
    modifier: Modifier = Modifier
) {
    fun stepIndex(step: String): Int = diatonicStepIndex(step)

    val filteredNotes = resolveMeasureNotesForSummary(
        measureNumber = measureNumber,
        score = score,
        voice = voice,
        pitchAttempts = pitchAttempts
    )

    // Determine Clef based on SATB voice (and tessitura for Solo parts like Baritone Solo)
    val pitchedNotesInMeasure = filteredNotes.filter { !it.isRest }
    val avgDiatonic = if (pitchedNotesInMeasure.isNotEmpty()) {
        pitchedNotesInMeasure.map { (it.octave * 7) + stepIndex(it.step) }.average()
    } else 30.0
    val isBassClef = voice == SATBVoice.TENOR ||
        voice == SATBVoice.BASS ||
        (voice == SATBVoice.SOLO && pitchedNotesInMeasure.isNotEmpty() && avgDiatonic < 27.5)
    val clefSymbol = if (isBassClef) "𝄢" else "𝄞"
    val clefLabel = if (isBassClef) "Bass Clef (F)" else "Treble Clef (G)"
    val baselineDiatonic = if (isBassClef) 18 else 30

    // Beam Group Data Structure
    data class NotationBeamGroup(
        val indices: List<Int>,
        val isStemUp: Boolean
    )

    fun resolveStemForGroup(groupIndices: List<Int>): Boolean {
        val notesInGroup = groupIndices.map { filteredNotes[it] }
        val explicitStem = notesInGroup.firstOrNull { !it.stem.isNullOrBlank() }?.stem
        if (explicitStem != null) {
            if (explicitStem.equals("up", ignoreCase = true)) return true
            if (explicitStem.equals("down", ignoreCase = true)) return false
        }
        return when (voice) {
            SATBVoice.SOPRANO, SATBVoice.TENOR -> true
            SATBVoice.ALTO, SATBVoice.BASS -> false
            else -> {
                val avgStep = notesInGroup.map { (it.octave * 7) + stepIndex(it.step) - baselineDiatonic }.average()
                avgStep <= 4
            }
        }
    }

    // Pre-calculate beam groups strictly following MusicXML <beam> directives
    val beamGroups = mutableListOf<NotationBeamGroup>()
    var currentGroup = mutableListOf<Int>()
    filteredNotes.forEachIndexed { idx, note ->
        val hasBeamDirectives = !note.beam.isNullOrBlank() || !note.beam2.isNullOrBlank() || !note.beam3.isNullOrBlank()
        if (note.isRest) {
            if (currentGroup.size >= 2) {
                beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
            }
            currentGroup.clear()
        } else if (note.beam.equals("begin", true)) {
            if (currentGroup.size >= 2) {
                beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
            }
            currentGroup = mutableListOf(idx)
        } else if (currentGroup.isNotEmpty() && hasBeamDirectives) {
            currentGroup.add(idx)
            val isPrimaryEnd = note.beam.equals("end", true)
            val isHookOnEnd = note.beam2.equals("forward hook", true) || note.beam3.equals("forward hook", true)
            if (isPrimaryEnd && !isHookOnEnd) {
                if (currentGroup.size >= 2) {
                    beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
                }
                currentGroup = mutableListOf()
            }
        } else if (note.beam.equals("forward hook", true) || note.beam2.equals("forward hook", true)) {
            if (currentGroup.size >= 2) {
                beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
            }
            currentGroup = mutableListOf(idx)
        } else {
            if (currentGroup.size >= 2) {
                beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
            }
            currentGroup = mutableListOf()
        }
    }
    if (currentGroup.size >= 2) {
        beamGroups.add(NotationBeamGroup(currentGroup.toList(), isStemUp = resolveStemForGroup(currentGroup)))
    }

    val noteToBeamGroup = mutableMapOf<Int, NotationBeamGroup>()
    beamGroups.forEach { bg -> bg.indices.forEach { idx -> noteToBeamGroup[idx] = bg } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = Color(0xFFFBFBFE),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E4ED)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            var isExpanded by remember { mutableStateOf(false) }

            val noteDetails = remember(filteredNotes, pitchAttempts, measureNumber) {
                evaluateMeasureNoteDetails(
                    filteredNotes = filteredNotes,
                    measureNumber = measureNumber,
                    pitchAttempts = pitchAttempts
                )
            }
            val flaggedNotes = noteDetails.filter { it.isFlagged }

            // Header: Measure badge + Clef info (left) & Interactive Flagged Button (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Measure Badge + Clef (weighted to prevent crowding and support clean truncation)
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEDE7F6))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = "MEASURE $measureNumber",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color(0xFF5E35B1)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$clefSymbol $clefLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6E717E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Interactive Flagged / Pitch Issue Button
                val count = flaggedNotes.size
                val hasFlagged = count > 0 || mistakeCount > 0
                val allSharp = flaggedNotes.isNotEmpty() && flaggedNotes.all { it.status == NotePitchStatus.SUNG_SHARP }
                val allNotSung = flaggedNotes.isNotEmpty() && flaggedNotes.all { it.status == NotePitchStatus.NOT_SUNG }
                val hasIntonation = !hasFlagged && (abs(averageDeviation) > 20f || isSharp)

                val buttonLabel = when {
                    count > 0 -> "$count Flagged"
                    mistakeCount > 0 -> "$mistakeCount Flagged"
                    hasIntonation -> if (isSharp) "Sung Sharp" else "Sung Flat"
                    else -> "In Tune"
                }

                val buttonColor = when {
                    allSharp -> Color(0xFFD97706)
                    allNotSung -> Color(0xFF4B5563)
                    hasFlagged -> Color(0xFFDC2626)
                    hasIntonation -> if (isSharp) Color(0xFFD97706) else Color(0xFFDC2626)
                    else -> Color(0xFF059669)
                }

                val buttonBg = when {
                    allSharp -> Color(0xFFFFFBEB)
                    allNotSung -> Color(0xFFF3F4F6)
                    hasFlagged -> Color(0xFFFEF2F2)
                    hasIntonation -> if (isSharp) Color(0xFFFFFBEB) else Color(0xFFFEF2F2)
                    else -> Color(0xFFECFDF5)
                }

                val buttonIcon = when {
                    allSharp -> Icons.Outlined.WarningAmber
                    allNotSung -> Icons.Outlined.Info
                    hasFlagged -> Icons.Outlined.ErrorOutline
                    hasIntonation -> Icons.Outlined.WarningAmber
                    else -> Icons.Outlined.CheckCircle
                }

                val canExpand = noteDetails.isNotEmpty()

                // Sleek, distinctly clickable button with clear visual boundary and tactile state feedback
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isExpanded) buttonColor.copy(alpha = 0.14f) else buttonBg,
                    border = BorderStroke(
                        width = if (isExpanded) 1.5.dp else 1.dp,
                        color = if (isExpanded) buttonColor else buttonColor.copy(alpha = 0.5f)
                    ),
                    shadowElevation = if (isExpanded) 0.dp else 1.dp,
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = canExpand) { isExpanded = !isExpanded }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = null,
                            tint = buttonColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = buttonColor
                        )
                        if (canExpand) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse details" else "Expand note details",
                                tint = buttonColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Expandable Dropdown Details: Note-by-note evaluation breakdown
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8F9FD),
                    border = BorderStroke(1.dp, Color(0xFFE2E4ED)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOTE-BY-NOTE PITCH BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF5E35B1)
                            )
                            Text(
                                text = "${flaggedNotes.size} of ${noteDetails.size} need attention",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF6E717E)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.8.dp)

                        noteDetails.forEach { detail ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (detail.isFlagged) detail.badgeBg.copy(alpha = 0.55f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Status dot + Note index & pitch name (+ lyric if available)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(detail.badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val lyricText = if (!detail.lyric.isNullOrBlank()) " • \"${detail.lyric}\"" else ""
                                    Text(
                                        text = "Note ${detail.noteIndex} (${detail.noteName}$lyricText)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (detail.isFlagged) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.5.sp
                                        ),
                                        color = if (detail.isFlagged) Color(0xFF191C20) else Color(0xFF4A4452),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Right: Reason label ("Sung Flat", "Sung Sharp", "Not Sung", "In Tune") + secondary cents
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = detail.statusLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = detail.badgeColor
                                    )
                                    if (detail.deviationCents != null && detail.status != NotePitchStatus.NOT_SUNG) {
                                        val sign = if (detail.deviationCents > 0) "+" else ""
                                        Text(
                                            text = " ($sign${detail.deviationCents.toInt()}¢)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = Color(0xFF6E717E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sheet Music Staff Canvas with ample vertical clearance
            val canvasHeight = 150.dp
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
            ) {
                val width = size.width
                val height = size.height

                val staffTopY = 32.dp.toPx()
                val lineSpacing = 10.dp.toPx()
                val staffBottomY = staffTopY + (4 * lineSpacing) // 72dp

                val lineColor = Color(0xFF757885)
                val barlineColor = Color(0xFF333644)

                val leftMargin = 12.dp.toPx()
                val rightMargin = width - 12.dp.toPx()

                // Draw Left and Right Barlines
                drawLine(
                    color = barlineColor,
                    start = Offset(leftMargin, staffTopY - 2.dp.toPx()),
                    end = Offset(leftMargin, staffBottomY + 2.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                drawLine(
                    color = barlineColor,
                    start = Offset(rightMargin, staffTopY - 2.dp.toPx()),
                    end = Offset(rightMargin, staffBottomY + 2.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )

                // Draw 5 Staff Lines
                for (i in 0..4) {
                    val y = staffTopY + (i * lineSpacing)
                    drawLine(
                        color = lineColor,
                        start = Offset(leftMargin, y),
                        end = Offset(rightMargin, y),
                        strokeWidth = 1.2f.dp.toPx()
                    )
                }

                // Draw Clef Glyph on the Left
                val clefPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#2E313D")
                    textSize = if (isBassClef) 28.sp.toPx() else 38.sp.toPx()
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                val clefY = if (isBassClef) staffTopY + 24.dp.toPx() else staffTopY + 28.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(clefSymbol, leftMargin + 4.dp.toPx(), clefY, clefPaint)

                val notesStartOffset = leftMargin + 46.dp.toPx()
                val notesAvailableWidth = rightMargin - notesStartOffset - 16.dp.toPx()
                val noteCount = filteredNotes.size

                val stepSpacing = if (noteCount > 1) {
                    notesAvailableWidth / (noteCount + 0.2f)
                } else {
                    notesAvailableWidth / 2f
                }

                val lyricPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#1F222E")
                    textSize = 11.sp.toPx()
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                }

                val accidentalPaint = Paint().apply {
                    textSize = 13.sp.toPx()
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }

                val drawnLyricOnsets = mutableSetOf<Int>()
                val hasAnyLyricsInScore = score?.notes?.any { !it.lyric.isNullOrBlank() } == true

                val noteRadiusX = 5.5.dp.toPx()
                val noteRadiusY = 4.2.dp.toPx()

                // Precalculate geometry for all beam groups outside the note loop
                // to eliminate in-loop mutation and guarantee stems attach seamlessly to the beam bar
                data class BeamGeometry(
                    val bg: NotationBeamGroup,
                    val isStemUp: Boolean,
                    val firstStemX: Float,
                    val lastStemX: Float,
                    val beamYStart: Float,
                    val beamYEnd: Float,
                    val noteBeamYs: Map<Int, Float>
                )

                val beamGeometryMap = mutableMapOf<NotationBeamGroup, BeamGeometry>()

                beamGroups.forEach { bg ->
                    val firstIdx = bg.indices.first()
                    val lastIdx = bg.indices.last()
                    val firstX = notesStartOffset + (firstIdx + 0.6f) * stepSpacing
                    val lastX = notesStartOffset + (lastIdx + 0.6f) * stepSpacing

                    val isStemUp = bg.isStemUp
                    val stemXOffset = if (isStemUp) (noteRadiusX - 0.8.dp.toPx()) else (-noteRadiusX + 0.8.dp.toPx())
                    val firstStemX = firstX + stemXOffset
                    val lastStemX = lastX + stemXOffset

                    val firstNote = filteredNotes[firstIdx]
                    val lastNote = filteredNotes[lastIdx]
                    val firstNoteY = staffBottomY - ((((firstNote.octave * 7) + stepIndex(firstNote.step)) - baselineDiatonic) * (lineSpacing / 2f))
                    val lastNoteY = staffBottomY - ((((lastNote.octave * 7) + stepIndex(lastNote.step)) - baselineDiatonic) * (lineSpacing / 2f))

                    val clampedDelta = ((lastNoteY - firstNoteY) * 0.35f).coerceIn(-8.dp.toPx(), 8.dp.toPx())

                    var beamYStart: Float
                    var beamYEnd: Float

                    if (isStemUp) {
                        val minNoteY = bg.indices.minOf { idx ->
                            val n = filteredNotes[idx]
                            staffBottomY - ((((n.octave * 7) + stepIndex(n.step)) - baselineDiatonic) * (lineSpacing / 2f))
                        }
                        val baseBeamY = minNoteY - 24.dp.toPx()
                        beamYStart = baseBeamY - (clampedDelta / 2f)
                        beamYEnd = baseBeamY + (clampedDelta / 2f)

                        // Clearance: ensure stem length is at least 22dp for all notes in group
                        bg.indices.forEach { idx ->
                            val nX = notesStartOffset + (idx + 0.6f) * stepSpacing
                            val t = if (lastX > firstX) (nX - firstX) / (lastX - firstX) else 0f
                            val bY = beamYStart + t * (beamYEnd - beamYStart)
                            val n = filteredNotes[idx]
                            val nY = staffBottomY - ((((n.octave * 7) + stepIndex(n.step)) - baselineDiatonic) * (lineSpacing / 2f))
                            if (nY - bY < 22.dp.toPx()) {
                                val diff = 22.dp.toPx() - (nY - bY)
                                beamYStart -= diff
                                beamYEnd -= diff
                            }
                        }
                    } else {
                        val maxNoteY = bg.indices.maxOf { idx ->
                            val n = filteredNotes[idx]
                            staffBottomY - ((((n.octave * 7) + stepIndex(n.step)) - baselineDiatonic) * (lineSpacing / 2f))
                        }
                        // Natural downward beam clearance without artificial clamping
                        val baseBeamY = maxNoteY + 24.dp.toPx()
                        beamYStart = baseBeamY - (clampedDelta / 2f)
                        beamYEnd = baseBeamY + (clampedDelta / 2f)

                        // Clearance: ensure stem length is at least 22dp for all notes in group
                        bg.indices.forEach { idx ->
                            val nX = notesStartOffset + (idx + 0.6f) * stepSpacing
                            val t = if (lastX > firstX) (nX - firstX) / (lastX - firstX) else 0f
                            val bY = beamYStart + t * (beamYEnd - beamYStart)
                            val n = filteredNotes[idx]
                            val nY = staffBottomY - ((((n.octave * 7) + stepIndex(n.step)) - baselineDiatonic) * (lineSpacing / 2f))
                            if (bY - nY < 22.dp.toPx()) {
                                val diff = 22.dp.toPx() - (bY - nY)
                                beamYStart += diff
                                beamYEnd += diff
                            }
                        }
                    }

                    val noteBeamYs = mutableMapOf<Int, Float>()
                    bg.indices.forEach { idx ->
                        val nX = notesStartOffset + (idx + 0.6f) * stepSpacing
                        val t = if (lastX > firstX) (nX - firstX) / (lastX - firstX) else 0f
                        noteBeamYs[idx] = beamYStart + t * (beamYEnd - beamYStart)
                    }

                    beamGeometryMap[bg] = BeamGeometry(
                        bg = bg,
                        isStemUp = isStemUp,
                        firstStemX = firstStemX,
                        lastStemX = lastStemX,
                        beamYStart = beamYStart,
                        beamYEnd = beamYEnd,
                        noteBeamYs = noteBeamYs
                    )
                }

                // Dynamic lyric baseline: guarantee lyrics are placed safely below all downward beams, stems, and noteheads
                val maxDownwardExtent = filteredNotes.mapIndexed { idx, note ->
                    val nY = staffBottomY - ((((note.octave * 7) + stepIndex(note.step)) - baselineDiatonic) * (lineSpacing / 2f))
                    val bg = noteToBeamGroup[idx]
                    if (bg != null) {
                        val geom = beamGeometryMap[bg]
                        if (geom != null && !geom.isStemUp) {
                            (geom.noteBeamYs[idx] ?: nY) + 4.dp.toPx()
                        } else {
                            nY + noteRadiusY
                        }
                    } else {
                        val isStemUp = if (!note.stem.isNullOrBlank()) {
                            note.stem.equals("up", ignoreCase = true)
                        } else {
                            when (voice) {
                                SATBVoice.SOPRANO, SATBVoice.TENOR -> true
                                SATBVoice.ALTO, SATBVoice.BASS -> false
                                else -> ((((note.octave * 7) + stepIndex(note.step)) - baselineDiatonic)) <= 4
                            }
                        }
                        if (!isStemUp) {
                            nY + 26.dp.toPx() + 4.dp.toPx()
                        } else {
                            nY + noteRadiusY
                        }
                    }
                }.maxOrNull() ?: (staffBottomY + 12.dp.toPx())

                val lyricBaselineY = maxOf(staffBottomY + 28.dp.toPx(), maxDownwardExtent + 14.dp.toPx())

                // Draw each note or rest in the measure
                filteredNotes.forEachIndexed { index, note ->
                    val noteX = notesStartOffset + (index + 0.6f) * stepSpacing
                    val diatonic = (note.octave * 7) + stepIndex(note.step)
                    val stepDiff = diatonic - baselineDiatonic
                    val noteY = staffBottomY - (stepDiff * (lineSpacing / 2f))

                    if (note.isRest) {
                        // Render Musical Rest
                        val midY = staffTopY + (2 * lineSpacing) // 3rd staff line (middle line)
                        val restColor = Color(0xFF333644)
                        when (note.type.lowercase()) {
                            "whole" -> {
                                val line4Y = staffTopY + lineSpacing
                                drawRect(
                                    color = restColor,
                                    topLeft = Offset(noteX - 5.dp.toPx(), line4Y),
                                    size = Size(10.dp.toPx(), 4.5.dp.toPx())
                                )
                            }
                            "half" -> {
                                drawRect(
                                    color = restColor,
                                    topLeft = Offset(noteX - 5.dp.toPx(), midY - 4.5.dp.toPx()),
                                    size = Size(10.dp.toPx(), 4.5.dp.toPx())
                                )
                            }
                            "eighth" -> {
                                drawCircle(color = restColor, radius = 2.2.dp.toPx(), center = Offset(noteX - 2.dp.toPx(), midY - 3.dp.toPx()))
                                drawLine(color = restColor, start = Offset(noteX - 2.dp.toPx(), midY - 3.dp.toPx()), end = Offset(noteX + 2.5.dp.toPx(), midY - 6.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                                drawLine(color = restColor, start = Offset(noteX + 2.5.dp.toPx(), midY - 6.dp.toPx()), end = Offset(noteX - 1.5.dp.toPx(), midY + 8.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                            }
                            "16th" -> {
                                drawCircle(color = restColor, radius = 2.0.dp.toPx(), center = Offset(noteX - 1.5.dp.toPx(), midY - 4.5.dp.toPx()))
                                drawLine(color = restColor, start = Offset(noteX - 1.5.dp.toPx(), midY - 4.5.dp.toPx()), end = Offset(noteX + 2.8.dp.toPx(), midY - 7.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                                drawCircle(color = restColor, radius = 2.0.dp.toPx(), center = Offset(noteX - 2.5.dp.toPx(), midY + 0.5.dp.toPx()))
                                drawLine(color = restColor, start = Offset(noteX - 2.5.dp.toPx(), midY + 0.5.dp.toPx()), end = Offset(noteX + 1.8.dp.toPx(), midY - 2.dp.toPx()), strokeWidth = 1.6.dp.toPx())
                                drawLine(color = restColor, start = Offset(noteX + 2.8.dp.toPx(), midY - 7.dp.toPx()), end = Offset(noteX - 1.8.dp.toPx(), midY + 9.dp.toPx()), strokeWidth = 1.8.dp.toPx())
                            }
                            else -> {
                                // Quarter rest (classic angular shape)
                                val qRestPath = Path().apply {
                                    moveTo(noteX + 1.dp.toPx(), midY - 11.dp.toPx())
                                    lineTo(noteX + 4.dp.toPx(), midY - 5.dp.toPx())
                                    lineTo(noteX - 3.dp.toPx(), midY + 1.dp.toPx())
                                    lineTo(noteX + 2.dp.toPx(), midY + 5.dp.toPx())
                                    cubicTo(
                                        noteX + 2.dp.toPx(), midY + 8.dp.toPx(),
                                        noteX - 1.5.dp.toPx(), midY + 10.5.dp.toPx(),
                                        noteX - 3.5.dp.toPx(), midY + 9.5.dp.toPx()
                                    )
                                }
                                drawPath(qRestPath, color = restColor, style = Stroke(width = 2.dp.toPx()))
                            }
                        }
                        if (note.isDotted) {
                            drawCircle(
                                color = restColor,
                                radius = 1.9.dp.toPx(),
                                center = Offset(noteX + 7.dp.toPx(), midY)
                            )
                        }
                        return@forEachIndexed
                    }

                    // Determine note attempt result / error state using tick + pitch matching
                    val noteAttempts = matchAttemptsForMeasureNote(
                        note = note,
                        measureNumber = measureNumber,
                        pitchAttempts = pitchAttempts
                    )

                    val isAttempted = noteAttempts.isNotEmpty()
                    val hasMatch = noteAttempts.any { it.isMatch }
                    val validDeviations = noteAttempts.map { it.deviationCents }.filter { abs(it) < 500f }
                    val avgDev = if (validDeviations.isNotEmpty()) {
                        validDeviations.average().toFloat()
                    } else if (isAttempted) {
                        noteAttempts.map { it.deviationCents }.average().toFloat()
                    } else 0f

                    val isError = isAttempted && !hasMatch
                    val isIntonationOff = isAttempted && hasMatch && abs(avgDev) > 25f

                    // Highlight colors:
                    // Amber for Sharp (+ cents), Red for Flat (- cents), Dark slate for unattempted, Deep green for match
                    val noteColor = when {
                        !isAttempted -> Color(0xFF2C3040)
                        isError -> if (avgDev > 0f) Color(0xFFE65100) else Color(0xFFC62828)
                        avgDev > 25f -> Color(0xFFE65100) // Sharp tendency
                        avgDev < -25f -> Color(0xFFC62828) // Flat tendency
                        else -> Color(0xFF1B5E20) // Deep green match
                    }

                    val noteRadiusX = 5.5.dp.toPx()
                    val noteRadiusY = 4.2.dp.toPx()

                    // Subtle highlight ring behind error or intonation-drift notes for clear feedback
                    if (isError) {
                        drawCircle(
                            color = (if (avgDev > 0f) Color(0xFFFFE0B2) else Color(0xFFFFCDD2)).copy(alpha = 0.65f),
                            radius = 12.dp.toPx(),
                            center = Offset(noteX, noteY)
                        )
                    } else if (isIntonationOff) {
                        drawCircle(
                            color = (if (avgDev > 0f) Color(0xFFFFE0B2) else Color(0xFFFFCDD2)).copy(alpha = 0.40f),
                            radius = 10.dp.toPx(),
                            center = Offset(noteX, noteY)
                        )
                    }

                    // 1. Draw Ledger lines if note extends beyond the 5 staff lines
                    if (stepDiff < 0) { // Below staff
                        var ledgerDiff = -2
                        while (ledgerDiff >= stepDiff) {
                            val ledgerY = staffBottomY - (ledgerDiff * (lineSpacing / 2f))
                            drawLine(
                                color = lineColor,
                                start = Offset(noteX - 9.dp.toPx(), ledgerY),
                                end = Offset(noteX + 9.dp.toPx(), ledgerY),
                                strokeWidth = 1.2f.dp.toPx()
                            )
                            ledgerDiff -= 2
                        }
                    } else if (stepDiff > 8) { // Above staff
                        var ledgerDiff = 10
                        while (ledgerDiff <= stepDiff) {
                            val ledgerY = staffBottomY - (ledgerDiff * (lineSpacing / 2f))
                            drawLine(
                                color = lineColor,
                                start = Offset(noteX - 9.dp.toPx(), ledgerY),
                                end = Offset(noteX + 9.dp.toPx(), ledgerY),
                                strokeWidth = 1.2f.dp.toPx()
                            )
                            ledgerDiff += 2
                        }
                    }

                    // 2. Draw Accidental Glyph (♯ or ♭) if present
                    if (note.alter != 0 || note.step.contains("#") || note.step.contains("b")) {
                        val accidentalStr = if (note.alter > 0 || note.step.contains("#")) "♯" else "♭"
                        accidentalPaint.color = noteColor.hashCode()
                        drawContext.canvas.nativeCanvas.drawText(
                            accidentalStr,
                            noteX - noteRadiusX - 2.dp.toPx(),
                            noteY + 4.dp.toPx(),
                            accidentalPaint
                        )
                    }

                    // 3. Draw Notehead
                    val isHalfOrWhole = note.type.equals("half", ignoreCase = true) || note.type.equals("whole", ignoreCase = true)
                    rotate(degrees = -18f, pivot = Offset(noteX, noteY)) {
                        drawOval(
                            color = noteColor,
                            topLeft = Offset(noteX - noteRadiusX, noteY - noteRadiusY),
                            size = Size(noteRadiusX * 2f, noteRadiusY * 2f),
                            style = if (isHalfOrWhole) Stroke(width = 1.8.dp.toPx()) else Fill
                        )
                    }

                    // 4. Draw Rhythm Dot (if dotted)
                    if (note.isDotted) {
                        val dotRadius = 1.9.dp.toPx()
                        val dotX = noteX + noteRadiusX + 4.5.dp.toPx()
                        val dotY = if (stepDiff % 2 == 0) noteY - (lineSpacing * 0.35f) else noteY
                        drawCircle(
                            color = noteColor,
                            radius = dotRadius,
                            center = Offset(dotX, dotY)
                        )
                    }

                    // 5. Draw Stem & Beams / Flags
                    if (!note.type.equals("whole", ignoreCase = true)) {
                        val beamGroup = noteToBeamGroup[index]
                        if (beamGroup != null) {
                            val geom = beamGeometryMap[beamGroup]!!
                            val stemX = if (geom.isStemUp) (noteX + noteRadiusX - 0.8.dp.toPx()) else (noteX - noteRadiusX + 0.8.dp.toPx())
                            val noteBeamY = geom.noteBeamYs[index] ?: geom.beamYStart

                            // Draw note stem connecting notehead seamlessly to beam
                            drawLine(
                                color = noteColor,
                                start = Offset(stemX, noteY),
                                end = Offset(stemX, noteBeamY),
                                strokeWidth = 1.3.dp.toPx()
                            )

                            // When on the last note of the group, draw the beam bar(s)
                            if (index == beamGroup.indices.last()) {
                                // 1. Primary Beam connecting the group
                                drawLine(
                                    color = Color(0xFF2C3040),
                                    start = Offset(geom.firstStemX, geom.beamYStart),
                                    end = Offset(geom.lastStemX, geom.beamYEnd),
                                    strokeWidth = 3.5.dp.toPx()
                                )

                                val beamSlope = if (geom.lastStemX > geom.firstStemX) {
                                    (geom.beamYEnd - geom.beamYStart) / (geom.lastStemX - geom.firstStemX)
                                } else 0f
                                val hookLength = min(11.dp.toPx(), stepSpacing * 0.38f)
                                val indices = beamGroup.indices

                                // Level 1 Primary Beam Hooks (if any)
                                for (i in 0 until indices.size) {
                                    val idx = indices[i]
                                    val n = filteredNotes[idx]
                                    val b1 = n.beam?.lowercase()
                                    if (b1 == "backward hook" || b1 == "forward hook") {
                                        val hDir = if (b1 == "forward hook") 1f else -1f
                                        val sX = if (geom.isStemUp) (notesStartOffset + (idx + 0.6f) * stepSpacing + noteRadiusX - 0.8.dp.toPx())
                                                 else (notesStartOffset + (idx + 0.6f) * stepSpacing - noteRadiusX + 0.8.dp.toPx())
                                        val startY = geom.noteBeamYs[idx]!!
                                        val hookDx = hookLength * hDir
                                        val hookDy = beamSlope * hookDx
                                        drawLine(
                                            color = Color(0xFF2C3040),
                                            start = Offset(sX, startY),
                                            end = Offset(sX + hookDx, startY + hookDy),
                                            strokeWidth = 3.5.dp.toPx()
                                        )
                                    }
                                }

                                // Helper function to render sub-beams & slope-parallel hooks for a specific beam level
                                fun renderSubBeamLevel(level: Int, offsetDp: Float) {
                                    val offsetPx = if (geom.isStemUp) offsetDp.dp.toPx() else -offsetDp.dp.toPx()
                                    val connectedToNext = BooleanArray(indices.size)
                                    val connectedToPrev = BooleanArray(indices.size)

                                    // 1. Draw full connecting segments between adjacent notes on this level
                                    for (i in 0 until indices.size - 1) {
                                        val idx1 = indices[i]
                                        val idx2 = indices[i + 1]
                                        val n1 = filteredNotes[idx1]
                                        val n2 = filteredNotes[idx2]

                                        val tag1 = when (level) {
                                            2 -> n1.beam2?.lowercase() ?: if (n1.type.equals("16th", true) || n1.type.equals("32nd", true)) "auto" else null
                                            3 -> n1.beam3?.lowercase() ?: if (n1.type.equals("32nd", true)) "auto" else null
                                            else -> null
                                        }
                                        val tag2 = when (level) {
                                            2 -> n2.beam2?.lowercase() ?: if (n2.type.equals("16th", true) || n2.type.equals("32nd", true)) "auto" else null
                                            3 -> n2.beam3?.lowercase() ?: if (n2.type.equals("32nd", true)) "auto" else null
                                            else -> null
                                        }

                                        val isEligible = tag1 != null && tag2 != null &&
                                                tag1 != "backward hook" && tag2 != "forward hook" &&
                                                !(tag1 == "forward hook" && tag2 == "backward hook")

                                        if (isEligible) {
                                            connectedToNext[i] = true
                                            connectedToPrev[i + 1] = true

                                            val sX1 = if (geom.isStemUp) (notesStartOffset + (idx1 + 0.6f) * stepSpacing + noteRadiusX - 0.8.dp.toPx())
                                                      else (notesStartOffset + (idx1 + 0.6f) * stepSpacing - noteRadiusX + 0.8.dp.toPx())
                                            val sX2 = if (geom.isStemUp) (notesStartOffset + (idx2 + 0.6f) * stepSpacing + noteRadiusX - 0.8.dp.toPx())
                                                      else (notesStartOffset + (idx2 + 0.6f) * stepSpacing - noteRadiusX + 0.8.dp.toPx())
                                            val bY1 = geom.noteBeamYs[idx1]!! + offsetPx
                                            val bY2 = geom.noteBeamYs[idx2]!! + offsetPx

                                            drawLine(
                                                color = Color(0xFF2C3040),
                                                start = Offset(sX1, bY1),
                                                end = Offset(sX2, bY2),
                                                strokeWidth = 2.5.dp.toPx()
                                            )
                                        }
                                    }

                                    // 2. Draw fractional beams (beam hooks) strictly parallel to the beam slope
                                    for (i in 0 until indices.size) {
                                        val idx = indices[i]
                                        val n = filteredNotes[idx]
                                        val tag = when (level) {
                                            2 -> n.beam2?.lowercase() ?: if (n.type.equals("16th", true) || n.type.equals("32nd", true)) "auto" else null
                                            3 -> n.beam3?.lowercase() ?: if (n.type.equals("32nd", true)) "auto" else null
                                            else -> null
                                        } ?: continue

                                        var hookDir: Float? = null
                                        if (tag == "backward hook") {
                                            hookDir = -1f
                                        } else if (tag == "forward hook") {
                                            hookDir = 1f
                                        } else if (!connectedToPrev[i] && !connectedToNext[i]) {
                                            // Fallback for isolated fractional notes (e.g. dotted 8th + 16th)
                                            hookDir = when {
                                                i > 0 && filteredNotes[indices[i - 1]].isDotted -> -1f
                                                i < indices.size - 1 && filteredNotes[indices[i + 1]].isDotted -> 1f
                                                i == indices.size - 1 -> -1f
                                                i == 0 -> 1f
                                                else -> -1f
                                            }
                                        }

                                        if (hookDir != null) {
                                            val sX = if (geom.isStemUp) (notesStartOffset + (idx + 0.6f) * stepSpacing + noteRadiusX - 0.8.dp.toPx())
                                                     else (notesStartOffset + (idx + 0.6f) * stepSpacing - noteRadiusX + 0.8.dp.toPx())
                                            val startY = geom.noteBeamYs[idx]!! + offsetPx
                                            val hookDx = hookLength * hookDir
                                            val hookDy = beamSlope * hookDx

                                            drawLine(
                                                color = Color(0xFF2C3040),
                                                start = Offset(sX, startY),
                                                end = Offset(sX + hookDx, startY + hookDy),
                                                strokeWidth = 2.5.dp.toPx()
                                            )
                                        }
                                    }
                                }

                                // Render Level 2 (16th notes: 4.5dp offset)
                                renderSubBeamLevel(level = 2, offsetDp = 4.5f)

                                // Render Level 3 (32nd notes: 9.0dp offset)
                                val hasLevel3 = indices.any {
                                    val n = filteredNotes[it]
                                    n.beam3 != null || n.type.equals("32nd", true)
                                }
                                if (hasLevel3) {
                                    renderSubBeamLevel(level = 3, offsetDp = 9.0f)
                                }
                            }
                        } else {
                            // Un-beamed individual note: follow note.stem, voice fallback, and natural stem length
                            val stemLength = 26.dp.toPx()
                            val isStemUp = if (!note.stem.isNullOrBlank()) {
                                note.stem.equals("up", ignoreCase = true)
                            } else {
                                when (voice) {
                                    SATBVoice.SOPRANO, SATBVoice.TENOR -> true
                                    SATBVoice.ALTO, SATBVoice.BASS -> false
                                    else -> stepDiff <= 4
                                }
                            }

                            if (isStemUp) {
                                val stemX = noteX + noteRadiusX - 0.8.dp.toPx()
                                val stemTopY = noteY - stemLength
                                drawLine(
                                    color = noteColor,
                                    start = Offset(stemX, noteY),
                                    end = Offset(stemX, stemTopY),
                                    strokeWidth = 1.3.dp.toPx()
                                )

                                // Draw flag for un-beamed 8th or 16th note
                                if (note.type.equals("eighth", true) || note.type.equals("16th", true)) {
                                    val flagPath = Path().apply {
                                        moveTo(stemX, stemTopY)
                                        cubicTo(
                                            stemX + 4.dp.toPx(), stemTopY + 2.dp.toPx(),
                                            stemX + 8.dp.toPx(), stemTopY + 7.dp.toPx(),
                                            stemX + 6.dp.toPx(), stemTopY + 14.dp.toPx()
                                        )
                                        cubicTo(
                                            stemX + 6.5f.dp.toPx(), stemTopY + 9.dp.toPx(),
                                            stemX + 3.dp.toPx(), stemTopY + 4.dp.toPx(),
                                            stemX, stemTopY + 4.dp.toPx()
                                        )
                                        close()
                                    }
                                    drawPath(flagPath, color = noteColor)

                                    if (note.type.equals("16th", true)) {
                                        val offset2 = 5.dp.toPx()
                                        val flag2Path = Path().apply {
                                            moveTo(stemX, stemTopY + offset2)
                                            cubicTo(
                                                stemX + 4.dp.toPx(), stemTopY + offset2 + 2.dp.toPx(),
                                                stemX + 8.dp.toPx(), stemTopY + offset2 + 7.dp.toPx(),
                                                stemX + 6.dp.toPx(), stemTopY + offset2 + 14.dp.toPx()
                                            )
                                            cubicTo(
                                                stemX + 6.5f.dp.toPx(), stemTopY + offset2 + 9.dp.toPx(),
                                                stemX + 3.dp.toPx(), stemTopY + offset2 + 4.dp.toPx(),
                                                stemX, stemTopY + offset2 + 4.dp.toPx()
                                            )
                                            close()
                                        }
                                        drawPath(flag2Path, color = noteColor)
                                    }
                                }
                            } else {
                                val stemX = noteX - noteRadiusX + 0.8.dp.toPx()
                                val stemBottomY = noteY + stemLength
                                drawLine(
                                    color = noteColor,
                                    start = Offset(stemX, noteY),
                                    end = Offset(stemX, stemBottomY),
                                    strokeWidth = 1.3.dp.toPx()
                                )

                                if (note.type.equals("eighth", true) || note.type.equals("16th", true)) {
                                    val flagPath = Path().apply {
                                        moveTo(stemX, stemBottomY)
                                        cubicTo(
                                            stemX + 4.dp.toPx(), stemBottomY - 2.dp.toPx(),
                                            stemX + 8.dp.toPx(), stemBottomY - 7.dp.toPx(),
                                            stemX + 6.dp.toPx(), stemBottomY - 14.dp.toPx()
                                        )
                                        cubicTo(
                                            stemX + 6.5f.dp.toPx(), stemBottomY - 9.dp.toPx(),
                                            stemX + 3.dp.toPx(), stemBottomY - 4.dp.toPx(),
                                            stemX, stemBottomY - 4.dp.toPx()
                                        )
                                        close()
                                    }
                                    drawPath(flagPath, color = noteColor)

                                    if (note.type.equals("16th", true)) {
                                        val offset2 = 5.dp.toPx()
                                        val flag2Path = Path().apply {
                                            moveTo(stemX, stemBottomY - offset2)
                                            cubicTo(
                                                stemX + 4.dp.toPx(), stemBottomY - offset2 - 2.dp.toPx(),
                                                stemX + 8.dp.toPx(), stemBottomY - offset2 - 7.dp.toPx(),
                                                stemX + 6.dp.toPx(), stemBottomY - offset2 - 14.dp.toPx()
                                            )
                                            cubicTo(
                                                stemX + 6.5f.dp.toPx(), stemBottomY - offset2 - 9.dp.toPx(),
                                                stemX + 3.dp.toPx(), stemBottomY - offset2 - 4.dp.toPx(),
                                                stemX, stemBottomY - offset2 - 4.dp.toPx()
                                            )
                                            close()
                                        }
                                        drawPath(flag2Path, color = noteColor)
                                    }
                                }
                            }
                        }
                    }

                    // 6. Draw Lyrics safely below the staff (Single-Rendering Policy)
                    val directLyric = note.lyric?.trim()?.takeIf { it.isNotBlank() }
                    val lyricText = directLyric ?: if (!hasAnyLyricsInScore) note.step.replace("#", "♯").replace("b", "♭") else null

                    if (lyricText != null && !drawnLyricOnsets.contains(note.startTimeDivisions)) {
                        drawnLyricOnsets.add(note.startTimeDivisions)
                        drawContext.canvas.nativeCanvas.drawText(
                            lyricText,
                            noteX,
                            lyricBaselineY,
                            lyricPaint
                        )
                    }
                }
            }

            // Pitch Accuracy Color Legend Bar
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F2F6))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // In Tune
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFF1B5E20))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "In Tune",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF1B5E20)
                    )
                }

                // Sharp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFE65100))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sung Sharp",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFE65100)
                    )
                }

                // Flat
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFC62828))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sung Flat",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFFC62828)
                    )
                }

                // Not Sung
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFF6E717E))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Not Sung",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF6E717E)
                    )
                }
            }
        }
    }
}
