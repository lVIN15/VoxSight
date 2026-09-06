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
        targetPart?.notes?.filter { it.measureNumber == measureNumber && !it.isRest } ?: emptyList()
    } else {
        emptyList()
    }

    val baseNotes = if (voicePartNotes.isNotEmpty()) {
        voicePartNotes
    } else {
        val allMeasureNotes = score?.notes?.filter { it.measureNumber == measureNumber && !it.isRest } ?: emptyList()
        val voiceSpecific = allMeasureNotes.filter { it.voice == voiceIndex || it.customVoice == voiceIndex }
        if (voiceSpecific.isNotEmpty()) voiceSpecific else allMeasureNotes
    }

    // Deduplicate chord/simultaneous notes at the exact same startTimeDivisions
    // For choral vocal sight-reading, pick the melody/top note if there are multiple notes at the same tick
    val filteredNotes = baseNotes
        .groupBy { it.startTimeDivisions }
        .values
        .map { notesAtTick ->
            notesAtTick.maxByOrNull { (it.octave * 7) + stepIndex(it.step) } ?: notesAtTick.first()
        }
        .sortedBy { it.startTimeDivisions }

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
                    Text(
                        text = "${filteredNotes.size} ${if (filteredNotes.size == 1) "Note" else "Notes"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8D909F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sheet Music Staff Canvas with ample vertical clearance
            val canvasHeight = 135.dp
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
            ) {
                val width = size.width
                val height = size.height

                val staffTopY = 30.dp.toPx()
                val lineSpacing = 10.dp.toPx()
                val staffBottomY = staffTopY + (4 * lineSpacing) // 70dp

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

                // Baseline reference:
                // Treble Bottom Line (Line 1) is E4 (diatonic: 4 * 7 + 2 = 30)
                // Bass Bottom Line (Line 1) is G2 (diatonic: 2 * 7 + 4 = 18)
                val baselineDiatonic = if (isBassClef) 18 else 30

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

                    val isError = isAttempted && !hasMatch

                    // Highlight colors:
                    // Amber for Sharp (+ cents), Red for Flat (- cents), Dark indigo for clean/correct
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

                    // 4. Draw Stem (pointing up if below middle line, down if above)
                    // Downward stems are capped above the lyrics line to avoid overlap
                    if (!note.type.equals("whole", ignoreCase = true)) {
                        val stemLength = 24.dp.toPx()
                        val isStemUp = stepDiff <= 4 // Below or at middle line (B4 for Treble, D3 for Bass)

                        if (isStemUp) {
                            drawLine(
                                color = noteColor,
                                start = Offset(noteX + noteRadiusX - 0.8.dp.toPx(), noteY),
                                end = Offset(noteX + noteRadiusX - 0.8.dp.toPx(), noteY - stemLength),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        } else {
                            val maxDownY = staffBottomY + 14.dp.toPx()
                            val endY = min(noteY + stemLength, maxDownY)
                            drawLine(
                                color = noteColor,
                                start = Offset(noteX - noteRadiusX + 0.8.dp.toPx(), noteY),
                                end = Offset(noteX - noteRadiusX + 0.8.dp.toPx(), endY),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        }
                    }

                    // 5. Draw Lyrics safely below the staff
                    val lyricText = note.lyric ?: note.step.replace("#", "♯").replace("b", "♭")
                    val lyricY = staffBottomY + 28.dp.toPx()
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
