package com.cit.kaido.voxsight.ui.screens.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.cit.kaido.voxsight.R

/**
 * Foreground Service that keeps VoxSight's process and TCP socket alive
 * while sheet music is being uploaded and digitized on the backend,
 * even when the user switches apps or returns to the Home screen.
 */
class ScoreUploadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel(this)

        // Always satisfy Android's startForeground contract immediately
        val initialNotification = buildProgressNotification(
            context = this,
            progressPercent = (UploadManager.uploadProgress.value * 100).toInt(),
            phaseText = UploadManager.uploadPhaseText.value.ifBlank { "Processing your sheet music" }
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    PROGRESS_NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(PROGRESS_NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {
            // Fallback if OS restricts foreground promotion in an edge state
        }

        if (intent?.action == ACTION_STOP || !isUploadActive) {
            releaseWakeLock()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PROGRESS_NOTIFICATION_ID)
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        activeInstance = this
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VoxSight::ScoreUploadWakeLock"
            ).apply {
                setReferenceCounted(false)
                // 15-minute max timeout for heavy multi-page scores running in background
                acquire(15 * 60 * 1000L)
            }
        } catch (_: Exception) {
            // Ignore if WakeLock cannot be acquired
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
            // Ignore if already released
        } finally {
            wakeLock = null
        }
    }

    private fun stopForegroundNow() {
        releaseWakeLock()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignore
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PROGRESS_NOTIFICATION_ID)
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID = "upload_channel"
        const val COMPLETION_CHANNEL_ID = "upload_completion_channel"
        const val PROGRESS_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002

        private const val ACTION_START = "com.cit.kaido.voxsight.action.START_SCORE_UPLOAD"
        private const val ACTION_STOP = "com.cit.kaido.voxsight.action.STOP_SCORE_UPLOAD"

        @Volatile
        private var isUploadActive = false

        @Volatile
        private var activeInstance: ScoreUploadService? = null

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // Channel 1: Progress Notification (silent, ongoing during upload/conversion)
                val uploadChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Score Uploads & Digitization",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows real-time progress while digitizing sheet music"
                }
                nm.createNotificationChannel(uploadChannel)

                // Channel 2: Completion Notification (high importance with chime & vibration)
                val completionChannel = NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    "Score Import Results",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts you when sheet music digitization is complete"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(completionChannel)
            }
        }

        fun buildProgressNotification(
            context: Context,
            progressPercent: Int,
            phaseText: String
        ): Notification {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Digitizing Sheet Music...")
                .setContentText(phaseText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progressPercent.coerceIn(0, 100), false)
                .build()
        }

        fun start(context: Context) {
            val appContext = context.applicationContext
            isUploadActive = true
            ensureNotificationChannel(appContext)
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(RESULT_NOTIFICATION_ID)

            val intent = Intent(appContext, ScoreUploadService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (_: Exception) {
                // Fallback if started while restricted: still post progress notification
                nm.notify(
                    PROGRESS_NOTIFICATION_ID,
                    buildProgressNotification(appContext, 0, "Uploading score...")
                )
            }
        }

        fun updateProgress(context: Context, progressPercent: Int, phaseText: String) {
            if (!isUploadActive) return
            val appContext = context.applicationContext
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                PROGRESS_NOTIFICATION_ID,
                buildProgressNotification(appContext, progressPercent, phaseText)
            )
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            isUploadActive = false
            val current = activeInstance
            if (current != null) {
                current.stopForegroundNow()
            } else {
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(PROGRESS_NOTIFICATION_ID)
                try {
                    appContext.stopService(Intent(appContext, ScoreUploadService::class.java))
                } catch (_: Exception) {
                    // Ignore
                }
            }
        }
    }
}
