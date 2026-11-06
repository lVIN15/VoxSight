package com.cit.kaido.voxsight.ui.screens.upload

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.cit.kaido.voxsight.MainActivity
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.OmrAnalysisResponse
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

enum class UploadStatus { IDLE, PROCESSING, READY }

/**
 * Fine-grained centralized state machine for all file-import and photo-capture operations.
 * Prevents duplicate file-picker dialogs, concurrent camera/picker launches, and inconsistent UI states.
 */
enum class ImportOperationState {
    IDLE,
    PICKER_OPENING,
    PICKER_OPEN,
    CAPTURING,
    IMPORTING,
    PROCESSING,
    READY,
    ERROR
}

object UploadManager {
    private val stateLock = Any()

    val uploadStatus = MutableStateFlow(UploadStatus.IDLE)
    val operationState = MutableStateFlow(ImportOperationState.IDLE)
    val uploadProgress = MutableStateFlow(0f)
    val uploadPhaseText = MutableStateFlow("Import in progress...")
    var pendingMusicXml: String? = null
    var pendingScoreTitle: String? = null
    var pendingFileName: String? = null
    var pendingOriginalUri: String? = null
    var activeError: String? = null
    val activeErrorFlow = MutableStateFlow<String?>(null)
    val navigateToReview = MutableStateFlow(false)

    private var pickerOpenedAtMs: Long = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeProgressJob: Job? = null

    /**
     * Returns true if an import, picker launch, photo capture, or OMR processing operation is currently active.
     */
    fun isBusy(): Boolean = synchronized(stateLock) {
        when (operationState.value) {
            ImportOperationState.PICKER_OPENING,
            ImportOperationState.PICKER_OPEN,
            ImportOperationState.CAPTURING,
            ImportOperationState.IMPORTING,
            ImportOperationState.PROCESSING -> true
            ImportOperationState.IDLE,
            ImportOperationState.READY,
            ImportOperationState.ERROR -> uploadStatus.value == UploadStatus.PROCESSING
        }
    }

    /**
     * Atomically attempts to transition into [ImportOperationState.PICKER_OPENING].
     * Immediately rejects duplicate taps on Import File or taps while Take Photo / Processing is active.
     */
    fun tryBeginPickerOpening(): Boolean = synchronized(stateLock) {
        val current = operationState.value
        if (current != ImportOperationState.IDLE && current != ImportOperationState.ERROR) {
            return false
        }
        if (uploadStatus.value == UploadStatus.PROCESSING) {
            return false
        }
        activeError = null
        activeErrorFlow.value = null
        operationState.value = ImportOperationState.PICKER_OPENING
        uploadPhaseText.value = "Opening file manager..."
        return true
    }

    /**
     * Transitions from [ImportOperationState.PICKER_OPENING] to [ImportOperationState.PICKER_OPEN]
     * right before launching the native system file picker Intent.
     */
    fun markPickerOpen(): Boolean = synchronized(stateLock) {
        if (operationState.value != ImportOperationState.PICKER_OPENING) {
            return false
        }
        pickerOpenedAtMs = System.currentTimeMillis()
        operationState.value = ImportOperationState.PICKER_OPEN
        uploadPhaseText.value = "Waiting for file selection..."
        return true
    }

    /**
     * Resets state when the file picker is cancelled by the user or fails to launch.
     * Never overrides an active [ImportOperationState.IMPORTING] or [ImportOperationState.PROCESSING] state.
     */
    fun onPickerCancelledOrFailed(errorMessage: String? = null) = synchronized(stateLock) {
        val current = operationState.value
        if (current == ImportOperationState.IMPORTING || current == ImportOperationState.PROCESSING) {
            return
        }
        pickerOpenedAtMs = 0L
        uploadProgress.value = 0f
        if (!errorMessage.isNullOrBlank()) {
            activeError = errorMessage
            activeErrorFlow.value = errorMessage
            uploadPhaseText.value = ""
            uploadStatus.value = UploadStatus.IDLE
            operationState.value = ImportOperationState.ERROR
        } else {
            uploadPhaseText.value = "Import in progress..."
            uploadStatus.value = UploadStatus.IDLE
            operationState.value = ImportOperationState.IDLE
        }
    }

    /**
     * Defensive recovery if the activity resumes while still in [ImportOperationState.PICKER_OPEN]
     * and the system picker dismissed without delivering a callback.
     */
    fun releaseStalePickerIfStillOpen(
        minElapsedMs: Long = 500L,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(stateLock) {
        if (operationState.value == ImportOperationState.PICKER_OPEN &&
            pickerOpenedAtMs > 0L &&
            (nowMs - pickerOpenedAtMs) >= minElapsedMs
        ) {
            pickerOpenedAtMs = 0L
            uploadStatus.value = UploadStatus.IDLE
            operationState.value = ImportOperationState.IDLE
            uploadPhaseText.value = "Import in progress..."
            return true
        }
        return false
    }

    /**
     * Atomically attempts to transition into [ImportOperationState.CAPTURING] when Take Photo is pressed.
     * Immediately rejects duplicate taps or taps while Import File / Processing is active.
     */
    fun tryBeginCapturing(): Boolean = synchronized(stateLock) {
        val current = operationState.value
        if (current != ImportOperationState.IDLE && current != ImportOperationState.ERROR) {
            return false
        }
        if (uploadStatus.value == UploadStatus.PROCESSING) {
            return false
        }
        activeError = null
        activeErrorFlow.value = null
        operationState.value = ImportOperationState.CAPTURING
        uploadPhaseText.value = "Capturing sheet music..."
        return true
    }

    /**
     * Resets state when photo capture or camera permission is cancelled or fails.
     */
    fun onCaptureCancelledOrFailed(errorMessage: String? = null) = synchronized(stateLock) {
        val current = operationState.value
        if (current == ImportOperationState.IMPORTING || current == ImportOperationState.PROCESSING) {
            return
        }
        uploadProgress.value = 0f
        if (!errorMessage.isNullOrBlank()) {
            activeError = errorMessage
            activeErrorFlow.value = errorMessage
            uploadPhaseText.value = ""
            uploadStatus.value = UploadStatus.IDLE
            operationState.value = ImportOperationState.ERROR
        } else {
            uploadPhaseText.value = "Import in progress..."
            uploadStatus.value = UploadStatus.IDLE
            operationState.value = ImportOperationState.IDLE
        }
    }

    /**
     * Atomically transitions into [ImportOperationState.IMPORTING] once a file URI or captured image URI is received.
     * Rejects duplicate submissions if an import or processing job is already running.
     */
    fun tryBeginImporting(fileName: String? = null): Boolean = synchronized(stateLock) {
        val current = operationState.value
        if (current == ImportOperationState.IMPORTING ||
            current == ImportOperationState.PROCESSING ||
            uploadStatus.value == UploadStatus.PROCESSING
        ) {
            return false
        }
        pickerOpenedAtMs = 0L
        if (!fileName.isNullOrBlank()) {
            pendingFileName = fileName
        }
        activeError = null
        activeErrorFlow.value = null
        pendingMusicXml = null
        pendingScoreTitle = null
        navigateToReview.value = false
        uploadProgress.value = 0f
        uploadPhaseText.value = "Reading selected file..."
        uploadStatus.value = UploadStatus.PROCESSING
        operationState.value = ImportOperationState.IMPORTING
        return true
    }

    /**
     * Transitions from [ImportOperationState.IMPORTING] to [ImportOperationState.PROCESSING]
     * once the local file stream is validated and OMR/backend analysis begins.
     */
    fun markProcessing(phaseText: String = "Uploading score...") = synchronized(stateLock) {
        uploadStatus.value = UploadStatus.PROCESSING
        operationState.value = ImportOperationState.PROCESSING
        uploadPhaseText.value = phaseText
    }

    /**
     * Transitions to [ImportOperationState.READY] upon successful score analysis.
     */
    fun markReady(musicXml: String, scoreTitle: String) = synchronized(stateLock) {
        pendingMusicXml = musicXml
        pendingScoreTitle = scoreTitle
        uploadProgress.value = 1f
        uploadPhaseText.value = "Score ready!"
        uploadStatus.value = UploadStatus.READY
        operationState.value = ImportOperationState.READY
    }

    /**
     * Transitions to [ImportOperationState.ERROR] and resets [uploadStatus] to [UploadStatus.IDLE]
     * so the user can retry immediately.
     */
    fun markFailed(errorMessage: String) = synchronized(stateLock) {
        pickerOpenedAtMs = 0L
        activeError = errorMessage
        activeErrorFlow.value = errorMessage
        uploadProgress.value = 0f
        uploadPhaseText.value = ""
        uploadStatus.value = UploadStatus.IDLE
        operationState.value = ImportOperationState.ERROR
    }

    /**
     * Clears the active error and returns the state machine to [ImportOperationState.IDLE].
     */
    fun acknowledgeError() = synchronized(stateLock) {
        activeError = null
        activeErrorFlow.value = null
        if (operationState.value == ImportOperationState.ERROR) {
            operationState.value = ImportOperationState.IDLE
            uploadPhaseText.value = "Import in progress..."
        }
    }

    fun clearReadyState() = synchronized(stateLock) {
        pickerOpenedAtMs = 0L
        uploadStatus.value = UploadStatus.IDLE
        operationState.value = ImportOperationState.IDLE
        uploadProgress.value = 0f
        uploadPhaseText.value = "Import in progress..."
        pendingMusicXml = null
        pendingScoreTitle = null
        pendingFileName = null
        activeError = null
        activeErrorFlow.value = null
        navigateToReview.value = false
    }

    private fun postResultNotification(
        appContext: Context,
        title: String,
        message: String,
        contentIntent: PendingIntent? = null
    ) {
        ScoreUploadService.ensureNotificationChannel(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(appContext, ScoreUploadService.COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        notificationManager.notify(ScoreUploadService.RESULT_NOTIFICATION_ID, builder.build())
    }

    fun uploadScore(
        context: Context,
        imageUri: Uri,
        bypassCache: Boolean,
        onProgress: (Float) -> Unit,
        onSuccess: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        pendingOriginalUri = imageUri.toString()
        if (!tryBeginImporting()) return // Atomic gate: block duplicate/concurrent uploads

        android.widget.Toast.makeText(context, "Import in progress", android.widget.Toast.LENGTH_SHORT).show()

        val appContext = context.applicationContext

        // Start ForegroundService so Android keeps the app & network socket alive on the Home screen
        ScoreUploadService.start(appContext)

        scope.launch {
            // Ensure any previous progress simulator is completely stopped before starting a new one
            activeProgressJob?.cancelAndJoin()
            var progressJob: Job? = null

            try {
                // Extract actual file name and MIME type while in IMPORTING stage
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

                // Copy URI content to a temp file immediately while URI grant is active
                val tempFile = File(appContext.cacheDir, "upload_$originalFileName")
                val copiedBytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: -1L
                }
                if (copiedBytes <= 0L || !tempFile.exists() || tempFile.length() <= 0L) {
                    throw IllegalArgumentException("The selected file could not be read or is empty.")
                }

                // Transition from IMPORTING -> PROCESSING now that file ingestion is complete
                markProcessing("Uploading score...")

                val requestFile = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("musicFile", originalFileName, requestFile)

                var response: OmrAnalysisResponse? = null
                var asyncHandled = false

                // Step 1: Attempt asynchronous background job submission (eliminates socket timeouts)
                try {
                    val submitResp = ApiClient.omrService.submitJob(body, bypassCache)
                    if (submitResp.status == "COMPLETED" && submitResp.result != null) {
                        // Cache hit: immediately ready
                        response = submitResp.result
                        asyncHandled = true
                    } else if (!submitResp.jobId.isNullOrBlank()) {
                        val jobId = submitResp.jobId
                        asyncHandled = true
                        uploadPhaseText.value = submitResp.phaseText ?: "Digitizing sheet music in background..."

                        // Poll status with accurate real-time progress from Audiveris
                        while (isActive && uploadStatus.value == UploadStatus.PROCESSING) {
                            delay(1500)
                            val statusResp = ApiClient.omrService.getJobStatus(jobId)
                            val pct = (statusResp.progressPercent / 100f).coerceIn(0f, 0.95f)
                            val phase = statusResp.phaseText ?: "Digitizing sheet music..."

                            uploadProgress.value = pct
                            uploadPhaseText.value = phase
                            withContext(Dispatchers.Main) {
                                if (isActive) onProgress(pct)
                            }
                            ScoreUploadService.updateProgress(
                                context = appContext,
                                progressPercent = statusResp.progressPercent,
                                phaseText = phase
                            )

                            if (statusResp.status == "COMPLETED" && statusResp.result != null) {
                                response = statusResp.result
                                break
                            } else if (statusResp.status == "FAILED") {
                                throw Exception(statusResp.error ?: "Digitization failed on server.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (asyncHandled) {
                        throw e // Propagate real conversion error
                    }
                    // Fallback to synchronous analyzeScore if endpoint not found or initial submission failed
                    android.util.Log.w("UploadManager", "Async job not available, falling back to sync: ${e.message}")
                }

                // Step 2: Fallback to synchronous analyzeScore if async was not supported
                if (!asyncHandled || response == null) {
                    progressJob = launch {
                        var currentP = uploadProgress.value
                        var lastNotifyTime = 0L
                        while (isActive && currentP < 0.90f && uploadStatus.value == UploadStatus.PROCESSING) {
                            val increment = when {
                                currentP < 0.40f -> 0.008f
                                currentP < 0.70f -> 0.0035f
                                currentP < 0.85f -> 0.0015f
                                else -> 0.0003f
                            }
                            currentP = (currentP + increment).coerceAtMost(0.90f)
                            uploadProgress.value = currentP

                            val phase = when {
                                currentP < 0.35f -> "Uploading score..."
                                currentP < 0.70f -> "Digitizing staves & notes..."
                                currentP < 0.88f -> "Analyzing vocal parts (SATB)..."
                                else -> "Finalizing musical analysis..."
                            }
                            uploadPhaseText.value = phase

                            withContext(Dispatchers.Main) {
                                if (isActive) onProgress(currentP)
                            }

                            val now = System.currentTimeMillis()
                            if (isActive && now - lastNotifyTime > 1000) {
                                ScoreUploadService.updateProgress(
                                    context = appContext,
                                    progressPercent = (currentP * 100).toInt(),
                                    phaseText = phase
                                )
                                lastNotifyTime = now
                            }

                            if (currentP >= 0.90f) {
                                delay(200)
                            } else {
                                delay(100)
                            }
                        }
                    }
                    activeProgressJob = progressJob

                    response = ApiClient.omrService.analyzeScore(body, bypassCache)

                    progressJob.cancelAndJoin()
                    activeProgressJob = null
                }

                if (response.success && response.musicXml != null) {
                    val scoreTitle = deriveTitleFromFileName(originalFileName)

                    // Smooth fast-forward to 100%
                    uploadPhaseText.value = "Score ready!"
                    var currentP = uploadProgress.value
                    while (currentP < 1f) {
                        currentP = (currentP + 0.05f).coerceAtMost(1f)
                        uploadProgress.value = currentP
                        withContext(Dispatchers.Main) { onProgress(currentP) }
                        delay(25)
                    }
                    uploadProgress.value = 1f
                    withContext(Dispatchers.Main) { onProgress(1f) }

                    // Stop ongoing foreground progress service & notification
                    ScoreUploadService.stop(appContext)

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

                    postResultNotification(
                        appContext = appContext,
                        title = "Upload Complete",
                        message = "Tap to review the score.",
                        contentIntent = pendingIntent
                    )

                    markReady(response.musicXml, scoreTitle)
                    navigateToReview.value = true
                    withContext(Dispatchers.Main) {
                        onSuccess(response.musicXml, scoreTitle)
                    }
                } else {
                    val err = response.error ?: "Conversion/Analysis failed."
                    ScoreUploadService.stop(appContext)
                    markFailed(err)

                    postResultNotification(
                        appContext = appContext,
                        title = "Upload Failed",
                        message = err
                    )

                    withContext(Dispatchers.Main) {
                        onError(err)
                    }
                }
            } catch (e: HttpException) {
                e.printStackTrace()
                progressJob?.cancelAndJoin()
                activeProgressJob = null
                ScoreUploadService.stop(appContext)

                var errorMessage = "Network error: ${e.code()}"
                val errorBody = e.response()?.errorBody()?.string()
                if (errorBody != null) {
                    try {
                        val errorResponse = Gson().fromJson(errorBody, OmrAnalysisResponse::class.java)
                        errorMessage = errorResponse.error ?: errorMessage
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                markFailed(errorMessage)

                postResultNotification(
                    appContext = appContext,
                    title = "Upload Failed",
                    message = errorMessage
                )

                withContext(Dispatchers.Main) {
                    onError(errorMessage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                progressJob?.cancelAndJoin()
                activeProgressJob = null
                ScoreUploadService.stop(appContext)

                val err = when {
                    e is java.net.UnknownHostException || e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> {
                        "No internet connection. Please check your Wi-Fi or mobile data and try again."
                    }
                    e is java.io.InterruptedIOException || e.message?.contains("timeout", ignoreCase = true) == true -> {
                        "Connection timed out. Please verify your connection and try again."
                    }
                    else -> {
                        "Upload Error: ${e.message ?: "An unexpected error occurred."}"
                    }
                }
                markFailed(err)

                postResultNotification(
                    appContext = appContext,
                    title = "Upload Failed",
                    message = "An unexpected error occurred."
                )

                withContext(Dispatchers.Main) {
                    onError(err)
                }
            }
        }
    }
}
