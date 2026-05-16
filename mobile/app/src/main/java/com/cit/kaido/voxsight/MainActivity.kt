package com.cit.kaido.voxsight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cit.kaido.voxsight.pitch.PitchComparator
import com.cit.kaido.voxsight.pitch.PitchDetectionEngine
import com.cit.kaido.voxsight.ui.ActivePracticeScreen
import com.cit.kaido.voxsight.ui.PracticeModeScreen
import com.cit.kaido.voxsight.ui.PracticeSummaryScreen
import com.cit.kaido.voxsight.viewmodel.NoteAttempt
import com.cit.kaido.voxsight.viewmodel.PracticeMode
import com.cit.kaido.voxsight.viewmodel.PracticeViewModel

data class ReferenceNote(
    val label: String,
    val hz: Float,
    val measure: Int,
    val durationMs: Long
)

class MainActivity : ComponentActivity() {

    private val viewModel: PracticeViewModel by viewModels()

    // Notes extracted from twinkle_twinkle.xml
    // Each note has label, Hz, measure number, duration in ms (quarter=500ms at 120bpm)
    private val songNotes = listOf(
        ReferenceNote("C4", 261.63f, 1, 500),
        ReferenceNote("C4", 261.63f, 1, 500),
        ReferenceNote("G4", 392.00f, 1, 500),
        ReferenceNote("G4", 392.00f, 1, 500),
        ReferenceNote("A4", 440.00f, 2, 500),
        ReferenceNote("A4", 440.00f, 2, 500),
        ReferenceNote("G4", 392.00f, 2, 1000),
        ReferenceNote("F4", 349.23f, 3, 500),
        ReferenceNote("F4", 349.23f, 3, 500),
        ReferenceNote("E4", 329.63f, 3, 500),
        ReferenceNote("E4", 329.63f, 3, 500),
        ReferenceNote("D4", 293.66f, 4, 500),
        ReferenceNote("D4", 293.66f, 4, 500),
        ReferenceNote("C4", 261.63f, 4, 1000),
        ReferenceNote("G4", 392.00f, 5, 500),
        ReferenceNote("G4", 392.00f, 5, 500),
        ReferenceNote("F4", 349.23f, 5, 500),
        ReferenceNote("F4", 349.23f, 5, 500),
        ReferenceNote("E4", 329.63f, 6, 500),
        ReferenceNote("E4", 329.63f, 6, 500),
        ReferenceNote("D4", 293.66f, 6, 1000),
        ReferenceNote("G4", 392.00f, 7, 500),
        ReferenceNote("G4", 392.00f, 7, 500),
        ReferenceNote("F4", 349.23f, 7, 500),
        ReferenceNote("F4", 349.23f, 7, 500),
        ReferenceNote("E4", 329.63f, 8, 500),
        ReferenceNote("E4", 329.63f, 8, 500),
        ReferenceNote("D4", 293.66f, 8, 1000),
        ReferenceNote("C4", 261.63f, 9, 500),
        ReferenceNote("C4", 261.63f, 9, 500),
        ReferenceNote("G4", 392.00f, 9, 500),
        ReferenceNote("G4", 392.00f, 9, 500),
        ReferenceNote("A4", 440.00f, 10, 500),
        ReferenceNote("A4", 440.00f, 10, 500),
        ReferenceNote("G4", 392.00f, 10, 1000),
        ReferenceNote("F4", 349.23f, 11, 500),
        ReferenceNote("F4", 349.23f, 11, 500),
        ReferenceNote("E4", 329.63f, 11, 500),
        ReferenceNote("E4", 329.63f, 11, 500),
        ReferenceNote("D4", 293.66f, 12, 500),
        ReferenceNote("D4", 293.66f, 12, 500),
        ReferenceNote("C4", 261.63f, 12, 1000)
    )

    private var currentNoteIndex = 0
    private var noteStartTime = 0L
    private var pitchEngine: PitchDetectionEngine? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setMicPermission(granted)
        if (granted) startPitchDetection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            when {
                uiState.selectedMode == PracticeMode.NONE -> {
                    PracticeModeScreen(
                        onModeSelected = { mode ->
                            viewModel.selectMode(mode)
                            viewModel.startSession()
                            currentNoteIndex = 0
                            if (mode == PracticeMode.TEST_PITCH) {
                                checkAndRequestMicPermission()
                            }
                        },
                        onCancel = { finish() }
                    )
                }

                !uiState.isSessionActive &&
                        uiState.selectedMode != PracticeMode.NONE -> {
                    PracticeSummaryScreen(
                        uiState = uiState,
                        onRepeatFlagged = {
                            stopPitchDetection()
                            viewModel.resetSession()
                            currentNoteIndex = 0
                        },
                        onClose = {
                            stopPitchDetection()
                            viewModel.resetSession()
                            currentNoteIndex = 0
                        }
                    )
                }

                else -> {
                    ActivePracticeScreen(
                        uiState = uiState,
                        onStop = {
                            stopPitchDetection()
                            viewModel.endSession()
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestMicPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.setMicPermission(true)
                startPitchDetection()
            }
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun getCurrentNote(): ReferenceNote {
        return if (currentNoteIndex < songNotes.size)
            songNotes[currentNoteIndex]
        else
            songNotes.last()
    }

    private fun advanceNote(detectedHz: Float, deviation: Float, isMatch: Boolean) {
        val current = getCurrentNote()
        val now = System.currentTimeMillis()

        if (noteStartTime == 0L) noteStartTime = now

        viewModel.logAttempt(
            NoteAttempt(
                noteLabel = current.label,
                detectedHz = detectedHz,
                deviationCents = deviation,
                isMatch = isMatch,
                measureNumber = current.measure
            )
        )

        if (now - noteStartTime >= current.durationMs) {
            currentNoteIndex = (currentNoteIndex + 1).coerceAtMost(songNotes.size - 1)
            noteStartTime = now
        }
    }

    private fun startPitchDetection() {
        pitchEngine = PitchDetectionEngine(
            onPitchDetected = { hz, _ ->
                val current = getCurrentNote()
                val deviation = PitchComparator.calculateDeviation(hz, current.hz)
                val feedback = PitchComparator.evaluate(deviation)
                val isMatch = PitchComparator.isMatch(deviation)

                viewModel.updatePitchFeedback(hz, deviation, feedback)
                viewModel.setNoiseWarning(false)
                advanceNote(hz, deviation, isMatch)
            },
            onNoiseDetected = {
                viewModel.setNoiseWarning(true)
            }
        )
        pitchEngine?.start()
    }

    private fun stopPitchDetection() {
        pitchEngine?.stop()
        pitchEngine = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPitchDetection()
    }
}