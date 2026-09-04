package com.cit.kaido.voxsight.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary

data class ProblematicNote(
    val noteName: String,
    val averageDeviation: Float,
    val isSharp: Boolean
)

data class ProblematicMeasure(
    val measureNumber: Int,
    val mistakeCount: Int,
    val totalNotes: Int = 0,
    val isSharp: Boolean = false,
    val averageDeviation: Float = 0f
)

data class VocalHighlight(
    val highestNote: String,
    val lowestNote: String
)

data class SessionSummary(
    val totalNotesAttempted: Int,
    val correctNotes: Int,
    val averageDeviationCents: Float,
    val problematicNotes: List<ProblematicNote> = emptyList(),
    val vocalHighlight: VocalHighlight? = null,
    val topProblematicMeasure: ProblematicMeasure? = null
) {
    val accuracyPercentage: Float
        get() = if (totalNotesAttempted > 0) {
            (correctNotes.toFloat() / totalNotesAttempted.toFloat()) * 100f
        } else {
            0f
        }
}

@Composable
fun SessionSummaryDialog(
    summary: SessionSummary,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Session Summary",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = VoxPurplePrimary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Big Accuracy Number
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = VoxPurplePrimary.copy(alpha = 0.1f), 
                            shape = RoundedCornerShape(60.dp)
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${summary.accuracyPercentage.toInt()}%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxPurplePrimary
                        )
                        Text(
                            text = "Accuracy",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notes Attempted:", color = Color.Gray)
                    Text("${summary.totalNotesAttempted}", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Correct Hits:", color = Color.Gray)
                    Text("${summary.correctNotes}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avg Deviation:", color = Color.Gray)
                    Text(String.format("%.1f cents", summary.averageDeviationCents), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VoxPurplePrimary)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
