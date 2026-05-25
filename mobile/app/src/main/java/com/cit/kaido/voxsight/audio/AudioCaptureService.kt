package com.cit.kaido.voxsight.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * AudioCaptureService
 *
 * Responsible for verifying that the app holds the RECORD_AUDIO runtime
 * permission before the pitch-detection pipeline is allowed to open the
 * microphone.  This maps to the SDD class diagram component:
 *
 *   AudioCaptureService
 *     -hasMicrophonePermission : boolean
 *     +checkPermissions()      : boolean
 *
 * The actual AudioRecord / AudioDispatcher lifecycle is managed inside
 * PitchDetectionEngine, which calls this service before starting the
 * microphone stream.
 */
class AudioCaptureService(private val context: Context) {

    /**
     * Returns true if the app currently holds the RECORD_AUDIO permission.
     * This must be called on any thread (it does not touch the microphone).
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
