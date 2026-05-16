package com.cit.kaido.voxsight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.viewmodel.PitchFeedback
import com.cit.kaido.voxsight.viewmodel.PracticeMode
import com.cit.kaido.voxsight.viewmodel.PracticeUiState

val GreenCorrect = Color(0xFF4CAF50)
val YellowClose = Color(0xFFFFC107)
val RedIncorrect = Color(0xFFF44336)
val NeutralGray = Color(0xFFBDBDBD)

@Composable
fun ActivePracticeScreen(
    uiState: PracticeUiState,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(16.dp))

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kyrie Eleison",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PurplePrimary
            )
            Text(
                text = if (uiState.selectedMode == PracticeMode.TEST_PITCH)
                    "Test Pitch Mode" else "Listen Mode",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Noise warning banner
        if (uiState.showNoiseWarning) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ Noise Warning — Too much background noise detected.",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Staff placeholder
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "♩ ♩ ♩ ♩",
                        fontSize = 32.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Score display area",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Pitch feedback indicator
        if (uiState.selectedMode == PracticeMode.TEST_PITCH) {
            PitchFeedbackIndicator(
                feedback = uiState.currentFeedback,
                detectedHz = uiState.detectedHz,
                deviationCents = uiState.deviationCents
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Stop button
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            shape = RoundedCornerShape(50.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "⏹ Stop & View Summary",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PitchFeedbackIndicator(
    feedback: PitchFeedback,
    detectedHz: Float,
    deviationCents: Float
) {
    val indicatorColor = when (feedback) {
        PitchFeedback.CORRECT -> GreenCorrect
        PitchFeedback.CLOSE -> YellowClose
        PitchFeedback.INCORRECT -> RedIncorrect
        PitchFeedback.INACTIVE -> NeutralGray
    }

    val feedbackLabel = when (feedback) {
        PitchFeedback.CORRECT -> "✓ In Tune"
        PitchFeedback.CLOSE -> "~ Almost"
        PitchFeedback.INCORRECT -> "✗ Off Pitch"
        PitchFeedback.INACTIVE -> "Listening..."
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color indicator circle
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(indicatorColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = feedbackLabel.take(1),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = feedbackLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = indicatorColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (detectedHz > 0f) {
                Text(
                    text = "Detected: ${"%.1f".format(detectedHz)} Hz",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Deviation: ${"%.1f".format(deviationCents)} cents",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = "Sing to receive feedback",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
    }
}