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
 * accidentals (♯, ♭), stems, lyrics, and pitch mistake highlights for the specified measure.
 */
@Composable
fun RealMeasureNotationView(
    measureNumber: Int,
    score: MusicXmlScore?,
    voice: SATBVoice = SATBVoice.SOPRANO,
    pitchAttempts: List<PitchAttempt> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 1. Resolve notes for this measure & voice part
    val voiceIndex = when (voice) {
        SATBVoice.SOPRANO -> 1
        SATBVoice.ALTO -> 2
        SATBVoice.TENOR -> 3
        SATBVoice.BASS -> 4
        SATBVoice.UNKNOWN -> 1
    }

    val measureNotes = score?.notes?.filter { note ->
        note.measureNumber == measureNumber && !note.isRest
    } ?: emptyList()

    val filteredNotes = measureNotes.filter { note ->
        note.voice == voiceIndex || note.originalVoice == voiceIndex || note.customVoice == voiceIndex
    }.ifEmpty {
        measureNotes
    }

    // Determine Clef based on SATB voice
    val isBassClef = voice == SATBVoice.TENOR || voice == SATBVoice.BASS
    val clefSymbol = if (isBassClef) "𝄢" else "𝄞"
    val clefLabel = if (isBassClef) "Bass Clef (F)" else "Treble Clef (G)"

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
            // Header: Measure badge + Clef info
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

                Text(
                    text = "${filteredNotes.size} ${if (filteredNotes.size == 1) "Note" else "Notes"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8D909F)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sheet Music Staff Canvas
            val canvasHeight = 110.dp
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
            ) {
                val width = size.width
                val height = size.height

                val staffTopY = 32.dp.toPx()
                val lineSpacing = 9.dp.toPx()
                val staffBottomY = staffTopY + (4 * lineSpacing)

                val lineColor = Color(0xFF757885)
                val barlineColor = Color(0xFF333644)

                // Draw Left and Right Barlines
                val leftMargin = 12.dp.toPx()
                val rightMargin = width - 12.dp.toPx()

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
                    textSize = if (isBassClef) 30.sp.toPx() else 42.sp.toPx()
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
                val clefY = if (isBassClef) staffTopY + 24.dp.toPx() else staffTopY + 28.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(clefSymbol, leftMargin + 6.dp.toPx(), clefY, clefPaint)

                // Pitch calculation helper:
                // Maps a diatonic note step & octave to vertical Y position relative to Clef bottom line
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

                // Baseline reference:
                // Treble Bottom Line (Line 1) is E4 (diatonic: 4 * 7 + 2 = 30)
                // Bass Bottom Line (Line 1) is G2 (diatonic: 2 * 7 + 4 = 18)
                val baselineDiatonic = if (isBassClef) 18 else 30

                val notesStartOffset = leftMargin + 44.dp.toPx()
                val notesAvailableWidth = rightMargin - notesStartOffset - 16.dp.toPx()
                val noteCount = filteredNotes.size

                val stepSpacing = if (noteCount > 1) {
                    notesAvailableWidth / (noteCount + 0.2f)
                } else {
                    notesAvailableWidth / 2f
                }

                val lyricPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#1F222E")
                    textSize = 12.sp.toPx()
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                }

                val accidentalPaint = Paint().apply {
                    textSize = 14.sp.toPx()
                    isAntiAlias = true
                    textAlign = Paint.Align.RIGHT
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }

                // Draw each note in the measure
                filteredNotes.forEachIndexed { index, note ->
                    val noteX = notesStartOffset + (index + 0.6f) * stepSpacing
                    val diatonic = (note.octave * 7) + stepIndex(note.step)
                    val stepDiff = diatonic - baselineDiatonic

                    // Each step = half a line spacing (between line and space)
                    val noteY = staffBottomY - (stepDiff * (lineSpacing / 2f))

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

                    // Highlight colors:
                    // Amber for Sharp (+ cents), Red for Flat (- cents), Dark indigo for clean/correct
                    val noteColor = when {
                        !isAttempted -> Color(0xFF2C3040)
                        hasMatch -> Color(0xFF1B5E20) // Deep green match
                        avgDev > 0f -> Color(0xFFE65100) // Sharp amber
                        else -> Color(0xFFC62828) // Flat crimson
                    }

                    // 1. Draw Ledger lines if note extends beyond the 5 staff lines
                    val noteRadiusX = 5.5.dp.toPx()
                    val noteRadiusY = 4.2.dp.toPx()

                    if (stepDiff < 0) { // Below staff
                        var ledgerDiff = -2
                        while (ledgerDiff >= stepDiff) {
                            val ledgerY = staffBottomY - (ledgerDiff * (lineSpacing / 2f))
                            drawLine(
                                color = lineColor,
                                start = Offset(noteX - 10.dp.toPx(), ledgerY),
                                end = Offset(noteX + 10.dp.toPx(), ledgerY),
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
                                start = Offset(noteX - 10.dp.toPx(), ledgerY),
                                end = Offset(noteX + 10.dp.toPx(), ledgerY),
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
                            noteX - noteRadiusX - 3.dp.toPx(),
                            noteY + 4.dp.toPx(),
                            accidentalPaint
                        )
                    }

                    // 3. Draw Notehead (slightly rotated ellipse for authentic engraving look)
                    val isHalfOrWhole = note.type.equals("half", ignoreCase = true) || note.type.equals("whole", ignoreCase = true)

                    rotate(degrees = -18f, pivot = Offset(noteX, noteY)) {
                        drawOval(
                            color = noteColor,
                            topLeft = Offset(noteX - noteRadiusX, noteY - noteRadiusY),
                            size = Size(noteRadiusX * 2f, noteRadiusY * 2f),
                            style = if (isHalfOrWhole) Stroke(width = 1.8.dp.toPx()) else Fill
                        )
                    }

                    // 4. Draw Stem (pointing up if below middle line, down if above)
                    if (!note.type.equals("whole", ignoreCase = true)) {
                        val stemLength = 26.dp.toPx()
                        val isStemUp = stepDiff <= 4 // Below or at middle line (B4 for Treble, D3 for Bass)

                        if (isStemUp) {
                            drawLine(
                                color = noteColor,
                                start = Offset(noteX + noteRadiusX - 0.8.dp.toPx(), noteY),
                                end = Offset(noteX + noteRadiusX - 0.8.dp.toPx(), noteY - stemLength),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        } else {
                            drawLine(
                                color = noteColor,
                                start = Offset(noteX - noteRadiusX + 0.8.dp.toPx(), noteY),
                                end = Offset(noteX - noteRadiusX + 0.8.dp.toPx(), noteY + stemLength),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        }
                    }

                    // 5. Draw Lyrics below the staff
                    val lyricText = note.lyric ?: note.step.replace("#", "♯").replace("b", "♭")
                    val lyricY = staffBottomY + 22.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(
                        lyricText,
                        noteX,
                        lyricY,
                        lyricPaint
                    )
                }
            }

            // Legend / Tip indicator below the staff
            val hasMistakes = filteredNotes.any { note ->
                val cleanPitch = "${note.step}${note.octave}"
                pitchAttempts.any { it.measureNumber == measureNumber && it.noteName.equals(cleanPitch, true) && !it.isMatch }
            }

            if (hasMistakes) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFFF3E0))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFE65100))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Colored notes indicate pitch errors detected during your singing practice.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Color(0xFFE65100)
                    )
                }
            }
        }
    }
}
