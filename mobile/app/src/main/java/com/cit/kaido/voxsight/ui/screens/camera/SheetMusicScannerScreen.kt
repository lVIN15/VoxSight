package com.cit.kaido.voxsight.ui.screens.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cit.kaido.voxsight.ui.theme.VoxAccentGreen
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.util.ImageOptimizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val TAG = "SheetMusicScanner"

@Composable
fun SheetMusicScannerScreen(
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isProcessingCapture by remember { mutableStateOf(false) }

    // Level / Tilt Guide State
    var pitchAngle by remember { mutableFloatStateOf(0f) }
    var rollAngle by remember { mutableFloatStateOf(0f) }
    var isLevel by remember { mutableStateOf(false) }

    // Accelerometer listener for tilt leveling
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]

                    // Calculate pitch and roll relative to facing down at a table (Z-axis dominant)
                    val pitch = (atan2(y.toDouble(), sqrt((x * x + z * z).toDouble())) * 180.0 / Math.PI).toFloat()
                    val roll = (atan2(-x.toDouble(), z.toDouble()) * 180.0 / Math.PI).toFloat()

                    pitchAngle = pitch
                    rollAngle = roll

                    // Parallel to desk: Z should be around -9.8 or +9.8 (depending on orientation), X and Y close to 0
                    val totalTilt = sqrt((x * x + y * y).toDouble()).toFloat()
                    // If phone is flat (tilt under ~1.8 m/s^2), it is level (within ~10 degrees of parallel)
                    isLevel = totalTilt < 2.0f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Toggle Torch
    LaunchedEffect(isTorchOn, camera) {
        camera?.cameraControl?.enableTorch(isTorchOn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        camera?.let { cam ->
                            val factory = PreviewView(context).meteringPointFactory
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            cam.cameraControl.startFocusAndMetering(action)
                        }
                    }
                },
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (exc: Exception) {
                        Log.e(TAG, "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Viewfinder Framing Guide Overlay
        val frameBorderColor by animateColorAsState(
            targetValue = if (isLevel) VoxAccentGreen else Color.White.copy(alpha = 0.7f),
            animationSpec = tween(300),
            label = "FrameBorder"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                // Tilt Indicator Badge
                Surface(
                    color = if (isLevel) VoxAccentGreen.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isLevel) VoxAccentGreen else Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isLevel) VoxAccentGreen else Color(0xFFFFA726), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLevel) "Level (Parallel)" else "Tilt: Hold Flat",
                            color = if (isLevel) VoxAccentGreen else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Torch Toggle Button
                IconButton(
                    onClick = { isTorchOn = !isTorchOn },
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isTorchOn) VoxPurplePrimary.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (isTorchOn) Color.Yellow else Color.White
                    )
                }
            }

            // Rectangular Sheet Music Guide Box (Aspect Ratio ~1:1.3 matching sheet music)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, frameBorderColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Subtle corner markers or guidance text
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isLevel) "Sheet music in frame" else "Hold phone flat over sheet",
                        color = if (isLevel) VoxAccentGreen else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Bottom Capture Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Tip: Use Torch in dim rooms for blur-free scans",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Shutter Button
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessingCapture) {
                        CircularProgressIndicator(
                            color = VoxPurplePrimary,
                            modifier = Modifier.size(76.dp)
                        )
                    }

                    IconButton(
                        enabled = !isProcessingCapture && imageCapture != null,
                        onClick = {
                            val capture = imageCapture ?: return@IconButton
                            isProcessingCapture = true

                            val cacheDir = context.cacheDir
                            val tempRawFile = File(cacheDir, "raw_capture_${System.currentTimeMillis()}.jpg")
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(tempRawFile).build()

                            capture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        // Run optimization asynchronously on IO
                                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                            val optimizedFile = File(
                                                cacheDir,
                                                "sheet_music_${System.currentTimeMillis()}.jpg"
                                            )
                                            val finalFile = ImageOptimizationHelper.optimizeSheetMusicImage(
                                                context,
                                                Uri.fromFile(tempRawFile),
                                                optimizedFile
                                            )
                                            // Delete raw large capture
                                            tempRawFile.delete()

                                            withContext(Dispatchers.Main) {
                                                isProcessingCapture = false
                                                onImageCaptured(Uri.fromFile(finalFile))
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                                        isProcessingCapture = false
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, CircleShape)
                            .border(4.dp, if (isLevel) VoxAccentGreen else VoxPurplePrimary, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(if (isLevel) VoxAccentGreen else VoxPurplePrimary, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
