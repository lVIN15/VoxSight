package com.cit.kaido.voxsight.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.viewmodel.PracticeUiState

@Composable
fun PracticeSummaryScreen(
    uiState: PracticeUiState,
    onRepeatFlagged: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Text(
                    text = "✕",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = "Practice Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            // spacer to balance the row
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Accuracy ring
        AccuracyRing(accuracy = uiState.overallAccuracy)

        Spacer(modifier = Modifier.height(32.dp))

        // Measures to review
        if (uiState.flaggedMeasures.isNotEmpty()) {
            Text(
                text = "Measures to Review",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.flaggedMeasures) { measure ->
                    FlaggedMeasureCard(measureNumber = measure, uiState = uiState)
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎉 Great job! No measures to review.",
                    fontSize = 15.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Repeat flagged button
        if (uiState.flaggedMeasures.isNotEmpty()) {
            Button(
                onClick = onRepeatFlagged,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "↺ Repeat Flagged Measures",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AccuracyRing(accuracy: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(160.dp)
    ) {
        CircularProgressIndicator(
            progress = { accuracy / 100f },
            modifier = Modifier.fillMaxSize(),
            color = PurplePrimary,
            trackColor = Color(0xFFE0E0E0),
            strokeWidth = 12.dp,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${"%.0f".format(accuracy)}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = PurplePrimary
            )
            Text(
                text = "Pitch Accuracy",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun FlaggedMeasureCard(
    measureNumber: Int,
    uiState: PracticeUiState
) {
    val attempts = uiState.attempts.filter {
        it.measureNumber == measureNumber && !it.isMatch
    }

    val direction = if (attempts.isNotEmpty()) {
        if (attempts.last().deviationCents > 0) "SHARP" else "FLAT"
    } else ""

    val directionColor = if (direction == "SHARP") Color(0xFFFF9800) else Color(0xFFF44336)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Measure $measureNumber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.Black
                )
                Text(
                    text = direction,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = directionColor
                )
            }
            Text(
                text = if (direction == "SHARP") "↑" else "↓",
                fontSize = 24.sp,
                color = directionColor
            )
        }
    }
}