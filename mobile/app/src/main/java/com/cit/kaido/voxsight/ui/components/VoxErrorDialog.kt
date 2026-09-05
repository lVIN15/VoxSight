package com.cit.kaido.voxsight.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxCardBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxPurpleIconBg
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary

enum class ErrorCategory {
    UNSUPPORTED_SCORE,    // Solo voices or Piano accompaniment
    STAVES_NOT_DETECTED,  // OMR cannot find staves (resolution/blurriness)
    IMAGE_QUALITY,        // Dim, blurry, or low-res photos / oversized canvas
    NETWORK_CONNECTION,   // Timeout, server offline, no internet
    FILE_CORRUPTED,       // Unreadable or empty XML/PDF file
    PERMISSION_DENIED,    // Camera permission
    GENERAL
}

data class VoxErrorDialogData(
    val title: String,
    val message: String,
    val details: List<String> = emptyList(),
    val actionableTip: String? = null,
    val category: ErrorCategory = ErrorCategory.GENERAL,
    val primaryButtonText: String = "GOT IT"
)

/**
 * Translates any raw error message or technical exception into a user-friendly,
 * visually structured VoxErrorDialogData object.
 */
fun resolveErrorDialogData(rawError: String): VoxErrorDialogData {
    val lower = rawError.lowercase()

    return when {
        lower.contains("unsupported score") || lower.contains("solo voices or piano") ||
        lower.contains("solo voice") || lower.contains("piano accompaniment") ||
        lower.contains("accompaniment notation") || lower.contains("too many parts") ||
        lower.contains("single vocal melody") || lower.contains("grand staff") -> {
            val detailsList = mutableListOf<String>()
            if (lower.contains("piano") || lower.contains("keyboard") || lower.contains("accompaniment")) {
                detailsList.add("Piano, keyboard, or accompaniment notation detected")
            }
            if (lower.contains("solo")) {
                detailsList.add("Solo vocal part or lead sheet detected")
            }
            if (lower.contains("too many parts")) {
                detailsList.add("Detected more than 4 choral voices/staves")
            }
            if (lower.contains("single vocal melody") || lower.contains("only 1 staff")) {
                detailsList.add("Single voice melody (expected 2–4 SATB staves)")
            }
            if (detailsList.isEmpty()) {
                detailsList.add("Contains non-choral voices or accompaniment notation")
            }

            VoxErrorDialogData(
                title = "SATB Choral Score Required",
                message = "VoxSight is built specifically for pure SATB (Soprano, Alto, Tenor, Bass) choral scores. Scores with piano accompaniment or solo leads cannot be practiced.",
                details = detailsList,
                actionableTip = "Tip: Upload an SATB choral arrangement without piano or organ accompaniment.",
                category = ErrorCategory.UNSUPPORTED_SCORE,
                primaryButtonText = "CHOOSE ANOTHER SCORE"
            )
        }

        lower.contains("too large image") || lower.contains("dimensions are too large") -> {
            VoxErrorDialogData(
                title = "Score Dimensions Too Large",
                message = "The physical page dimensions of this document exceed the maximum processing canvas limit.",
                details = listOf(
                    "Score was exported with oversized billboard/poster dimensions",
                    "Audiveris safety ceiling exceeded"
                ),
                actionableTip = "Tip: Re-export the sheet music PDF in standard US Letter or A4 format (8.5\" × 11\").",
                category = ErrorCategory.IMAGE_QUALITY,
                primaryButtonText = "CHOOSE ANOTHER SCORE"
            )
        }

        lower.contains("detect any sheet music staves") || lower.contains("no music staves") ||
        lower.contains("no staves") || lower.contains("flagged as invalid") -> {
            VoxErrorDialogData(
                title = "Unable to Detect Staves",
                message = "The OMR scanner could not recognize musical staff lines in this image or document.",
                details = listOf(
                    "Staff lines may be faint, broken, or obscured",
                    "Page angle, distortion, or background clutter may interfere"
                ),
                actionableTip = "Tip: Use a clear, well-lit photo taken directly from above, or upload a direct PDF vector export.",
                category = ErrorCategory.STAVES_NOT_DETECTED,
                primaryButtonText = "TRY AGAIN"
            )
        }

        lower.contains("too low interline") || lower.contains("interline value") ||
        lower.contains("resolution is too low") || lower.contains("quality is too low") ||
        lower.contains("clearer photo") -> {
            VoxErrorDialogData(
                title = "Clearer Photo Needed",
                message = "The resolution or image quality is too low for reliable musical note recognition.",
                details = listOf(
                    "Image resolution is below the required 300 DPI threshold",
                    "Faint notes and lyrics cannot be transcribed accurately"
                ),
                actionableTip = "Tip: Hold the camera steady with bright, even lighting, or upload a digital PDF score.",
                category = ErrorCategory.IMAGE_QUALITY,
                primaryButtonText = "RETAKE PHOTO"
            )
        }

        lower.contains("network error") || lower.contains("timeout") ||
        lower.contains("connect") || lower.contains("503") || lower.contains("502") || lower.contains("504") -> {
            VoxErrorDialogData(
                title = "Connection Problem",
                message = "Unable to communicate with the VoxSight transcription server. The service may be busy or unreachable.",
                details = listOf(
                    "Check if Wi-Fi or mobile data is connected",
                    "The server may be processing another score"
                ),
                actionableTip = "Tip: Verify your internet connection and tap retry in a few moments.",
                category = ErrorCategory.NETWORK_CONNECTION,
                primaryButtonText = "RETRY"
            )
        }

        lower.contains("camera permission") -> {
            VoxErrorDialogData(
                title = "Camera Permission Needed",
                message = "VoxSight requires camera permission to scan printed sheet music directly.",
                actionableTip = "Tip: Go to your device Settings > Apps > VoxSight > Permissions and enable Camera access.",
                category = ErrorCategory.PERMISSION_DENIED,
                primaryButtonText = "GOT IT"
            )
        }

        lower.contains("failed to read") || lower.contains("corrupt") ||
        lower.contains("empty") || lower.contains("missing") -> {
            VoxErrorDialogData(
                title = "File Unreadable",
                message = "The selected file could not be read or is empty. Please ensure the file is intact.",
                details = listOf(
                    "File format must be standard PDF, MusicXML (.musicxml/.xml), or PNG/JPG image"
                ),
                actionableTip = "Tip: Try exporting or downloading the file again from your source.",
                category = ErrorCategory.FILE_CORRUPTED,
                primaryButtonText = "SELECT ANOTHER FILE"
            )
        }

        else -> {
            VoxErrorDialogData(
                title = "Processing Issue",
                message = rawError.ifBlank { "An unexpected error occurred while processing the score." },
                actionableTip = "Tip: Ensure the score is a clear, standard SATB choral arrangement.",
                category = ErrorCategory.GENERAL,
                primaryButtonText = "GOT IT"
            )
        }
    }
}

/**
 * VoxErrorDialog — Premium, brand-aligned modal pop-up error dialog.
 * Replaces unreadable system Toasts with rich visual hierarchy, icon badges,
 * structured detail cards, actionable tips, and primary call-to-action buttons.
 */
@Composable
fun VoxErrorDialog(
    data: VoxErrorDialogData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Branded circular icon badge
                val icon = when (data.category) {
                    ErrorCategory.UNSUPPORTED_SCORE -> Icons.Outlined.MusicOff
                    ErrorCategory.STAVES_NOT_DETECTED -> Icons.Outlined.VisibilityOff
                    ErrorCategory.IMAGE_QUALITY -> Icons.Outlined.CameraAlt
                    ErrorCategory.NETWORK_CONNECTION -> Icons.Outlined.CloudOff
                    ErrorCategory.FILE_CORRUPTED -> Icons.Outlined.Description
                    ErrorCategory.PERMISSION_DENIED -> Icons.Outlined.Lock
                    ErrorCategory.GENERAL -> Icons.Outlined.ErrorOutline
                }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(VoxPurpleIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VoxPurplePrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = VoxTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Main message
                Text(
                    text = data.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    ),
                    color = VoxTextSecondary,
                    textAlign = TextAlign.Center
                )

                // Details list card (if any)
                if (data.details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoxCardBackground)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data.details.forEach { detail ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(VoxPurplePrimary)
                                )
                                Text(
                                    text = detail,
                                    fontSize = 12.5.sp,
                                    color = VoxTextPrimary,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                // Actionable Tip Card (if present)
                if (data.actionableTip != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VoxPurplePrimary.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = data.actionableTip,
                            fontSize = 12.5.sp,
                            color = VoxPurplePrimary,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary full-width button
                Button(
                    onClick = onDismiss,
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
                        text = data.primaryButtonText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {}
    )
}
