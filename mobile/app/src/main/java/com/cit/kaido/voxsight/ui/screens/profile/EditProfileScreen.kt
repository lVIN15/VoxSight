package com.cit.kaido.voxsight.ui.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun EditProfileScreen(
    currentName: String,
    currentProfilePictureBase64: String? = null,
    onBackClicked: () -> Unit,
    onSaveClicked: (newName: String, newProfilePictureBase64: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(currentName) }
    var profilePictureBase64 by remember(currentProfilePictureBase64) {
        mutableStateOf(currentProfilePictureBase64)
    }

    val avatarBitmap = remember(profilePictureBase64) {
        decodeBase64ToImageBitmap(profilePictureBase64)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    encodeUriToBase64(context, uri)
                }
                if (encoded != null) {
                    profilePictureBase64 = encoded
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Could not load the selected image",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val topPurple = Color(0xFF38036B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // --- Top App Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Edit your Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(48.dp)) // To balance the back button
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

        Spacer(modifier = Modifier.height(48.dp))

        // --- Profile Picture Edit ---
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    // Default placeholder when there is no active profile picture
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile Picture Placeholder",
                        tint = Color.LightGray,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            // Camera Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = 50.dp, y = (-8).dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF4A5568))
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Change Photo",
                    tint = Color(0xFF4FD1C5),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // --- Name Input ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            com.cit.kaido.voxsight.ui.screens.auth.VoxOutlinedField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = "YOUR NAME",
                placeholder = "e.g. John Doe"
            )

            Spacer(modifier = Modifier.height(64.dp))

            // --- Save Button ---
            Button(
                onClick = { onSaveClicked(name, profilePictureBase64) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp), spotColor = topPurple),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = topPurple)
            ) {
                Text(
                    text = "SAVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * Reads an image from [uri], fixes EXIF rotation, center-crops to a square,
 * scales to 256x256, and returns a compact Base64-encoded JPEG string.
 */
fun encodeUriToBase64(context: Context, uri: Uri): String? {
    return try {
        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val rotatedBitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        val minDim = minOf(rotatedBitmap.width, rotatedBitmap.height)
        val startX = (rotatedBitmap.width - minDim) / 2
        val startY = (rotatedBitmap.height - minDim) / 2
        val squareBitmap = Bitmap.createBitmap(rotatedBitmap, startX, startY, minDim, minDim)
        val scaledBitmap = Bitmap.createScaledBitmap(squareBitmap, 256, 256, true)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}

/**
 * Decodes a Base64-encoded image string into a Compose [ImageBitmap], or null if empty/invalid.
 */
fun decodeBase64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str.isNullOrBlank()) return null
    return try {
        val cleanBase64 = base64Str.substringAfter("base64,", base64Str).trim()
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
