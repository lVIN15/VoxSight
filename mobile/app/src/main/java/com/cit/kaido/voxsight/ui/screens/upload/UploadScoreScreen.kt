package com.cit.kaido.voxsight.ui.screens.upload

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ripple
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.ui.theme.VoxBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardBackground
import com.cit.kaido.voxsight.ui.theme.VoxCardStroke
import com.cit.kaido.voxsight.ui.theme.VoxProcessingBg
import com.cit.kaido.voxsight.ui.theme.VoxProgressIndicator
import com.cit.kaido.voxsight.ui.theme.VoxProgressText
import com.cit.kaido.voxsight.ui.theme.VoxProgressTrack
import com.cit.kaido.voxsight.ui.theme.VoxPurpleIconBg
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary
import com.cit.kaido.voxsight.ui.theme.VoxTextSubtitle
import java.io.File

/**
 * UploadScoreScreen — Module 1, Transaction 1.1
 *
 * Provides the UI for "Take Photo" and "Import from Device",
 * handles Android device permission requests, and triggers the
 * ImageCaptureService flow on successful capture.
 *
 * SDD Components mapped here:
 *  - UploadScoreScreen.openCamera()
 *  - UploadScoreScreen.openGallery()
 *  - ImageCaptureService.captureImage() / validateQuality()
 *  - ProcessingLoadingState.showLoadingDialog() / updateProgress()
 */
@Composable
fun UploadScoreScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ── Processing state ────────────────────────────────────────
    var isProcessing by remember { mutableStateOf(false) }
    var processingFileName by remember { mutableStateOf("") }
    var processingProgress by remember { mutableFloatStateOf(0f) }

    // ── Camera URI for captured image ───────────────────────────
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // ── Activity Result Launchers ───────────────────────────────

    /** Camera capture result — maps to ImageCaptureService.captureImage() */
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            onImageCaptured(
                context = context,
                imageUri = pendingCameraUri!!,
                onProcessing = { fileName ->
                    processingFileName = fileName
                    processingProgress = 0f
                    isProcessing = true
                }
            )
        }
    }

    /** File picker result — maps to UploadScoreScreen.openGallery() */
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileName = getFileName(context, it)
            processingFileName = fileName
            processingProgress = 0f
            isProcessing = true
            // TODO: Send file to ScoreUploadController via Retrofit
        }
    }

    /** Camera permission request */
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraUri = createCameraUri(context)
            pendingCameraUri?.let { cameraLauncher.launch(it) }
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to scan sheet music.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── UI ──────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoxBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // ===== Top Bar: Logo + Profile Avatar =====
        TopBar()

        Spacer(modifier = Modifier.height(24.dp))

        // ===== Title Section =====
        Text(
            text = stringResource(R.string.digitize_score_title),
            style = MaterialTheme.typography.headlineLarge,
            color = VoxTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.digitize_score_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = VoxTextSecondary,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ===== Take Photo Card =====
        ActionCard(
            icon = Icons.Outlined.CameraAlt,
            title = stringResource(R.string.take_photo_title),
            subtitle = stringResource(R.string.take_photo_subtitle),
            onClick = {
                // openCamera() — check permission first
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    pendingCameraUri = createCameraUri(context)
                    pendingCameraUri?.let { cameraLauncher.launch(it) }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== Import File Card =====
        ActionCard(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.import_file_title),
            subtitle = stringResource(R.string.import_file_subtitle),
            onClick = {
                // openGallery() — launch file picker for images
                filePickerLauncher.launch("image/*")
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ===== Processing Status Card (animated entry) =====
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + slideInVertically { it / 2 }
        ) {
            ProcessingCard(
                fileName = processingFileName,
                progress = processingProgress
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Composable Sub-components
// ═══════════════════════════════════════════════════════════════════

/**
 * Top bar with VoxSight logo and profile avatar.
 */
@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Music note icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VoxPurplePrimary),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = stringResource(R.string.cd_music_note),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = stringResource(R.string.voxsight_logo_text),
            style = MaterialTheme.typography.titleLarge,
            color = VoxPurplePrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        // Profile Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(VoxPurpleIconBg),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = stringResource(R.string.cd_profile_avatar),
                tint = VoxPurplePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Reusable action card for "Take Photo" and "Import File".
 * Center-aligned icon circle + title + subtitle.
 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoxCardBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = VoxPurplePrimary),
                onClick = onClick
            )
            .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(VoxPurpleIconBg),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VoxPurplePrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = VoxTextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = VoxTextSubtitle
        )
    }
}

/**
 * Processing status card shown at the bottom while the OMR engine works.
 * Maps to SDD: ProcessingLoadingState
 */
@Composable
private fun ProcessingCard(
    fileName: String,
    progress: Float
) {
    val percent = (progress * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoxProcessingBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VoxPurpleIconBg),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = stringResource(R.string.cd_file_icon),
                tint = VoxPurplePrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text + progress bar
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.processing_title) + " '$fileName'",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                color = VoxTextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = VoxProgressIndicator,
                trackColor = VoxProgressTrack,
                strokeCap = StrokeCap.Round,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.processing_status),
                style = MaterialTheme.typography.labelSmall,
                color = VoxTextSubtitle
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Percentage
        Text(
            text = stringResource(R.string.processing_percent, percent),
            style = MaterialTheme.typography.labelLarge,
            color = VoxProgressText
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════

/**
 * Creates a temporary file URI for the camera to write to.
 * Uses FileProvider for Scoped Storage compliance.
 * Maps to SDD: ImageCaptureService.captureImage()
 */
private fun createCameraUri(context: Context): Uri {
    val photoFile = File.createTempFile(
        "score_capture_",
        ".jpg",
        context.cacheDir
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

/**
 * Called when the camera successfully captures an image.
 * Validates quality before proceeding to upload.
 * Maps to SDD: ImageCaptureService.validateQuality()
 */
private fun onImageCaptured(
    context: Context,
    imageUri: Uri,
    onProcessing: (String) -> Unit
) {
    if (validateImageQuality(imageUri)) {
        val fileName = getFileName(context, imageUri)
        onProcessing(fileName)
        // TODO: Send image to ScoreUploadController via Retrofit
        //       ScoreUploadController.uploadImage(image)
    } else {
        Toast.makeText(
            context,
            "Image quality is too low. Please capture a clearer photo.",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * Performs basic resolution and clarity validation on the captured image.
 * Maps to SDD: ImageCaptureService.validateQuality(image: File): Boolean
 *
 * @return true if the image meets minimum quality thresholds.
 */
private fun validateImageQuality(imageUri: Uri): Boolean {
    // TODO: Implement resolution/clarity checks per SDD spec
    //       - Minimum resolution threshold
    //       - Contrast / sharpness analysis
    return true // Placeholder — accept all images for now
}

/**
 * Extracts a human-readable file name from a content URI.
 */
private fun getFileName(context: Context, uri: Uri): String {
    var name = "score_image"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}
