package com.cit.kaido.voxsight.ui.screens.upload

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiRestEditorBottomSheet(
    measureNumber: Int,
    count: Int,
    onCountChanged: (delta: Int) -> Unit,
    onSetCount: (newCount: Int) -> Unit = {},
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VoxCardStroke) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Section
            Text(
                text = "Edit Multimeasure Rest",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = VoxTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = VoxPurpleIconBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ALL VOICES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoxPurplePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "Measure $measureNumber • Shared Rest",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VoxTextSubtitle
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Description Box: Full Context & Explanation
            Surface(
                color = VoxPurplePrimary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VoxPurplePrimary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VoxPurpleIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = "All Voices",
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Synchronized Across All Voices",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = VoxPurplePrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "This rest spans across all choral parts (Soprano, Alto, Tenor, Bass). Adjusting the count updates all parts in lockstep to keep timing synchronized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VoxTextSubtitle,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rest Duration Stepper Control
            Text(
                text = "REST DURATION (MEASURES)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = VoxTextSubtitle
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                // Decrement Button
                FilledIconButton(
                    onClick = { if (count > 1) onCountChanged(-1) },
                    enabled = count > 1,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = VoxPurpleIconBg,
                        contentColor = VoxPurplePrimary,
                        disabledContainerColor = Color(0xFFF2F2F6),
                        disabledContentColor = Color(0xFFC4C4D0)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Decrease Measures",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Number Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(min = 80.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VoxTextPrimary,
                        lineHeight = 46.sp
                    )
                    Text(
                        text = if (count == 1) "measure" else "measures",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VoxTextSubtitle
                    )
                }

                // Increment Button
                FilledIconButton(
                    onClick = { onCountChanged(1) },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = VoxPurplePrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase Measures",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Preset Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(2, 3, 4, 8).forEach { preset ->
                    val isSelected = count == preset
                    Surface(
                        color = if (isSelected) VoxPurplePrimary else VoxCardBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) VoxPurplePrimary else VoxCardStroke
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetCount(preset) }
                    ) {
                        Text(
                            text = "$preset bars",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else VoxTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Done Button
            Button(
                onClick = onDismissRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoxPurplePrimary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "DONE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
