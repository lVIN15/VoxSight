package com.cit.kaido.voxsight.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.model.SATBVoice
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlNote
import com.cit.kaido.voxsight.ui.screens.practice.MusicXmlScore
import com.cit.kaido.voxsight.pitch.PitchAttempt
import kotlin.math.abs
import kotlin.math.min

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
    modifier: Modifier = Modifier
) {
    fun stepIndex(step: String): Int {
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

    val voiceIndex = when (voice) {
        SATBVoice.SOPRANO -> 1
        SATBVoice.ALTO -> 2
        SATBVoice.TENOR -> 3
        SATBVoice.BASS -> 4
        SATBVoice.UNKNOWN -> 1
    }

    val isLowerVoice = voice == SATBVoice.ALTO || voice == SATBVoice.BASS

    // 1. Isolate notes strictly for the user's selected vocal line to prevent multi-part overlap
    val voicePartNotes = if (!score?.parts.isNullOrEmpty()) {
        val targetPart = score?.parts?.firstOrNull { part ->
            when (voice) {
                SATBVoice.SOPRANO -> part.name.contains("soprano", true) || part.id == 1
                SATBVoice.ALTO -> part.name.contains("alto", true) || part.id == 2
                SATBVoice.TENOR -> part.name.contains("tenor", true) || part.id == 3
                SATBVoice.BASS -> part.name.contains("bass", true) || part.id == 4
                else -> part.id == 1
            }
        } ?: score?.parts?.getOrNull(voiceIndex - 1) ?: score?.parts?.firstOrNull()
        targetPart?.notes?.filter { it.measureNumber == measureNumber } ?: emptyList()
    } else {
        emptyList()
    }

    val baseNotes = if (voicePartNotes.isNotEmpty()) {
        val matchingVoice = voicePartNotes.filter { it.voice == voiceIndex || it.customVoice == voiceIndex }
        if (matchingVoice.isNotEmpty()) matchingVoice else voicePartNotes
    } else {
        val allMeasureNotes = score?.notes?.filter { it.measureNumber == measureNumber } ?: emptyList()
        val voiceSpecific = allMeasureNotes.filter { it.voice == voiceIndex || it.customVoice == voiceIndex }
        if (voiceSpecific.isNotEmpty()) voiceSpecific else allMeasureNotes
    }

    // Measure-level shared lyric fallback across all parts in the score
    val measureLyricsByTick = score?.notes
        ?.filter { it.measureNumber == measureNumber && !it.lyric.isNullOrBlank() }
        ?.associate { it.startTimeDivisions to it.lyric!!.trim() }
        ?: emptyMap()

    // Deduplicate chord/simultaneous notes at the exact same startTimeDivisions for the selected line.
    // For Soprano/Tenor, pick the upper melody note; for Alto/Bass, pick the lower harmony note.
    // If a tick contains both pitched notes and rests, prioritize pitched notes.
    // Inherit the shared lyric from the beat group if the individual note lacks one.
    val filteredNotes = baseNotes
        .groupBy { it.startTimeDivisions }
        .values
        .map { notesAtTick ->
            val nonRests = notesAtTick.filter { !it.isRest }
            val pool = if (nonRests.isNotEmpty()) nonRests else notesAtTick
            val selected = if (isLowerVoice) {
                pool.minByOrNull { (it.octave * 7) + stepIndex(it.step) } ?: pool.first()
            } else {
                pool.maxByOrNull { (it.octave * 7) + stepIndex(it.step) } ?: pool.first()
            }
            val sharedLyric = notesAtTick.firstOrNull { !it.lyric.isNullOrBlank() }?.lyric?.trim()
                ?: measureLyricsByTick[selected.startTimeDivisions]

            if (selected.lyric.isNullOrBlank() && sharedLyric != null) {
                selected.copy(lyric = sharedLyric)
            } else {
                selected
            }
        }
        .sortedBy { it.startTimeDivisions }

    // Determine Clef based on SATB voice
    val isBassClef = voice == SATBVoice.TENOR || voice == SATBVoice.BASS
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
            // Header: Measure badge + Clef info + Mistake indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEDE7F6))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$clefSymbol $clefLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6E717E)
                    )
                }

                if (mistakeCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFEBEE))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "$mistakeCount ${if (mistakeCount == 1) "MISTAKE" else "MISTAKES"}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFC62828)
                        )
                    }
                } else {
                    val pitchNotesCount = filteredNotes.count { !it.isRest }
                    Text(
                        text = "$pitchNotesCount ${if (pitchNotesCount == 1) "Note" else "Notes"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8D909F)
                    )
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

                    // Determine note attempt result / error state
                    val cleanPitchName = "${note.step}${note.octave}"
                    val noteAttempts = pitchAttempts.filter { attempt ->
                        attempt.measureNumber == measureNumber &&
                        (attempt.noteName.equals(cleanPitchName, ignoreCase = true) ||
                         attempt.noteName.startsWith(note.step.take(1), ignoreCase = true))
                    }

                    val isAttempted = noteAttempts.isNotEmpty()
                    val hasMatch = noteAttempts.any { it.isMatch }
                    val avgDev = if (isAttempted) {
                        noteAttempts.map { it.deviationCents }.average().toFloat()
                    } else 0f

                    val isError = isAttempted && !hasMatch

                    // Highlight colors:
                    // Amber for Sharp (+ cents), Red for Flat (- cents), Dark slate for unattempted, Deep green for match
                    val noteColor = when {
                        !isAttempted -> Color(0xFF2C3040)
                        hasMatch -> Color(0xFF1B5E20) // Deep green match
                        avgDev > 0f -> Color(0xFFE65100) // Sharp amber
                        else -> Color(0xFFC62828) // Flat crimson
                    }

                    val noteRadiusX = 5.5.dp.toPx()
                    val noteRadiusY = 4.2.dp.toPx()

                    // Subtle highlight ring behind error notes for clear feedback
                    if (isError) {
                        drawCircle(
                            color = (if (avgDev > 0f) Color(0xFFFFE0B2) else Color(0xFFFFCDD2)).copy(alpha = 0.55f),
                            radius = 12.dp.toPx(),
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
                        text = "Sharp (+pitch)",
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
                        text = "Flat (-pitch)",
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
