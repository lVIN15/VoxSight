package com.cit.kaido.voxsight.ui.screens.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cit.kaido.voxsight.MainActivity
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.OmrAnalysisResponse
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

enum class UploadStatus { IDLE, PROCESSING, READY }

object UploadManager {
    val uploadStatus = MutableStateFlow(UploadStatus.IDLE)
    val uploadProgress = MutableStateFlow(0f)
    var pendingMusicXml: String? = null
    var pendingScoreTitle: String? = null
    var pendingFileName: String? = null
    var activeError: String? = null
    val navigateToReview = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CHANNEL_ID = "upload_channel"
    private const val NOTIFICATION_ID = 1001

    fun clearReadyState() {
        uploadStatus.value = UploadStatus.IDLE
        pendingMusicXml = null
        pendingScoreTitle = null
        pendingFileName = null
        activeError = null
    }

    fun uploadScore(
        context: Context,
        imageUri: Uri,
        bypassCache: Boolean,
        onProgress: (Float) -> Unit,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (uploadStatus.value == UploadStatus.PROCESSING) return // Block multiple uploads
        
        uploadStatus.value = UploadStatus.PROCESSING
        uploadProgress.value = 0f
        activeError = null
        pendingMusicXml = null
        pendingScoreTitle = null
        
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Score Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for uploading sheet music"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Uploading Score...")
            .setContentText("Processing your sheet music")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, false)
            
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        
        scope.launch {
            try {
                // Extract actual file name and MIME type
                val originalFileName = getFileName(appContext, imageUri)
                pendingFileName = originalFileName
                val mimeType = appContext.contentResolver.getType(imageUri) ?: run {
                    val lower = originalFileName.lowercase()
                    when {
                        lower.endsWith(".pdf") -> "application/pdf"
                        lower.endsWith(".png") -> "image/png"
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                        lower.endsWith(".musicxml") || lower.endsWith(".xml") -> "application/xml"
                        lower.endsWith(".mxl") -> "application/vnd.recordare.musicxml"
                        else -> "image/jpeg"
                    }
                }
                
                // Copy URI content to a temp file
                val tempFile = File(appContext.cacheDir, "upload_$originalFileName")
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("musicFile", originalFileName, requestFile)
                
                // Smooth natural progression over ~30 seconds
                val progressJob = launch {
                    var currentP = 0f
                    var lastNotifyTime = 0L
                    while (currentP < 1f) {
                        currentP += 0.0033f // Approx 30 seconds to reach 100%
                        if (currentP > 1f) currentP = 1f
                        uploadProgress.value = currentP
                        withContext(Dispatchers.Main) { onProgress(currentP) }
                        
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > 1000) {
                            builder.setProgress(100, (currentP * 100).toInt(), false)
                            notificationManager.notify(NOTIFICATION_ID, builder.build())
                            lastNotifyTime = now
                        }
                        if (currentP >= 1f) break
                        delay(100)
                    }
                }
                
                val response = ApiClient.omrService.analyzeScore(body, bypassCache)
                
                // If it finishes early, smoothly fast-forward to 100%
                if (progressJob.isActive) {
                    progressJob.cancel()
                    var currentP = uploadProgress.value
                    while (currentP < 1f) {
                        currentP += 0.05f // Speed up significantly
                        if (currentP > 1f) currentP = 1f
                        uploadProgress.value = currentP
                        withContext(Dispatchers.Main) { onProgress(currentP) }
                        // Skip notifying Android during fast-forward to avoid rate limits
                        if (currentP >= 1f) break
                        delay(30)
                    }
                }
                
                if (response.success && response.musicXml != null) {
                    val scoreTitle = deriveTitleFromFileName(originalFileName)
                    uploadProgress.value = 1f
                    withContext(Dispatchers.Main) { onProgress(1f) }
                    
                    pendingMusicXml = response.musicXml
                    pendingScoreTitle = scoreTitle
                    
                    // Create PendingIntent to launch MainActivity and handle review
                    val intent = Intent(appContext, MainActivity::class.java).apply {
                        action = "ACTION_REVIEW_SCORE"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        appContext, 
                        0, 
                        intent, 
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    builder.setContentTitle("Upload Complete")
                        .setContentText("Tap to review the score.")
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                    
                    pendingMusicXml = response.musicXml
                    pendingScoreTitle = scoreTitle
                    uploadStatus.value = UploadStatus.READY
                    withContext(Dispatchers.Main) {
                        onSuccess(response.musicXml, scoreTitle)
                    }
                } else {
                    val err = response.error ?: "Conversion/Analysis failed."
                    activeError = err
                    builder.setContentTitle("Upload Failed")
                        .setContentText(err)
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true)
                    notificationManager.notify(NOTIFICATION_ID, builder.build())
                    
                    uploadStatus.value = UploadStatus.IDLE
                    withContext(Dispatchers.Main) {
                        onError(err)
                    }
                }
            } catch (e: HttpException) {
                e.printStackTrace()
                var errorMessage = "Network error: ${e.code()}"
                val errorBody = e.response()?.errorBody()?.string()
                if (errorBody != null) {
                    try {
                        val errorResponse = Gson().fromJson(errorBody, OmrAnalysisResponse::class.java)
                        errorMessage = errorResponse.error ?: errorMessage
                    } catch (e2: Exception) {
                        // ignore
                    }
                }
                activeError = errorMessage
                builder.setContentTitle("Upload Failed")
                    .setContentText(errorMessage)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                
                uploadStatus.value = UploadStatus.IDLE
                withContext(Dispatchers.Main) {
                    onError(errorMessage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val err = "Upload Error: ${e.message}"
                activeError = err
                builder.setContentTitle("Upload Failed")
                    .setContentText("An unexpected error occurred.")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
                
                uploadStatus.value = UploadStatus.IDLE
                withContext(Dispatchers.Main) {
                    onError(err)
                }
            }
        }
    }
}
