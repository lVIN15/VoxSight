package com.cit.kaido.voxsight.ui.screens.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cit.kaido.voxsight.audio.AudioCaptureService
import com.cit.kaido.voxsight.audio.DetectedPitch
import com.cit.kaido.voxsight.audio.FeedbackResult
import com.cit.kaido.voxsight.audio.PitchComparator
import com.cit.kaido.voxsight.audio.PitchDetectionEngine
import com.cit.kaido.voxsight.audio.ScoreAlignmentManager
import com.cit.kaido.voxsight.network.ApiClient
import com.cit.kaido.voxsight.network.SessionPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Collections

/**
 * PitchVisualizerController
 *
 * The orchestrator for Module 4 (SDD §4.1 Class Diagram).
 * Owns the audio pipeline lifecycle and translates raw pitch data into
 * Compose-observable [PitchUiState] updates.
 *
 * SDD Class:
 *   PitchVisualizerController
 *     -isTracking          : boolean
 *     +startPitchTracking(): void
 *     +stopPitchTracking() : void
 *     -executionLoop()     : void
 *
 * Threading model
 * ───────────────
 * [PitchDetectionEngine] calls its callbacks from a background daemon thread.
 * This ViewModel posts state updates to the main dispatcher via
 * [viewModelScope] + [Dispatchers.Main] so Compose state is always mutated
 * on the correct thread.
 */
class PitchVisualizerController(application: Application) : AndroidViewModel(application) {

    // ── Exposed Compose state ────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<PitchUiState>(PitchUiState.Idle)

    /**
     * Observed by [Module2PracticeScreen] to drive [PitchFeedbackCard].
     * Always emits on the main thread.
     */
    val uiState: StateFlow<PitchUiState> = _uiState.asStateFlow()

    // ── Internal services ────────────────────────────────────────────────────

    private val captureService  = AudioCaptureService(application)
    private val sessionService  = ApiClient.sessionService

    private var engine: PitchDetectionEngine? = null
    private var alignmentManager: ScoreAlignmentManager? = null

    // ── Tracking flag ─────────────────────────────────────────────────────────
    var isTracking: Boolean = false
        private set

    // ── Aggregation buffer ────────────────────────────────────────────────────
    /**
     * Thread-safe list — populated from the TarsosDSP background thread by
     * [executionLoop] and drained by [flushSession] on the IO dispatcher.
     */
    private val attemptBuffer: MutableList<SessionPayload.AttemptPayload> =
        Collections.synchronizedList(mutableListOf())

    private var sessionStartedAt: Instant = Instant.now()
    private var currentScoreTitle: String = ""

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the pitch-tracking pipeline.
     *
     * 1. Checks the runtime RECORD_AUDIO permission via [AudioCaptureService].
     *    If denied, emits [PitchUiState.Idle] and returns (the caller must
     *    handle the permission request — it cannot be done from a ViewModel).
     * 2. Starts [PitchDetectionEngine] on its own daemon thread.
     * 3. On every detected pitch, queries [ScoreAlignmentManager] for the
     *    current target note and emits a [PitchUiState.Active] result.
     *
     * Safe to call from the Compose UI thread.
     */
    fun startPitchTracking(score: MusicXmlScore, playheadProgressProvider: () -> Float) {
        if (isTracking) return

        if (!captureService.hasPermission()) {
            // Permission not yet granted — UI layer must call
            // ActivityResultLauncher to request it, then call this method again.
            _uiState.value = PitchUiState.Idle
            return
        }

        // Record session start metadata.
        sessionStartedAt = Instant.now()
        currentScoreTitle = score.title
        attemptBuffer.clear()

        // Build the alignment manager for this score (voice 1 = selected part by default).
        alignmentManager = ScoreAlignmentManager(score, voiceFilter = 1)

        _uiState.value = PitchUiState.Listening

        val newEngine = PitchDetectionEngine(
            minConfidenceLevel = 0.80f,
            onPitchDetected = { hz, confidence ->
                executionLoop(
                    detected = DetectedPitch(hz, confidence),
                    playheadProgressProvider = playheadProgressProvider
                )
            },
            onNoPitchDetected = {
                postState(PitchUiState.Listening)
            }
        )

        engine = newEngine
        newEngine.start()
        isTracking = true
    }

    /**
     * Stops the microphone capture and resets the state to [PitchUiState.Idle].
     *
     * Safe to call from any thread.  Called automatically by [onCleared].
     */
    fun stopPitchTracking() {
        engine?.stop()
        engine = null
        alignmentManager = null
        isTracking = false
        _uiState.value = PitchUiState.Idle
    }

    /**
     * Aggregates the buffered [attemptBuffer] into a [SessionPayload] and
     * posts it to POST /api/session/save via Retrofit.
     *
     * Call this when the user pauses or the score ends (from the UI layer).
     * The coroutine runs on [Dispatchers.IO] so it never blocks the UI thread.
     *
     * [elapsedSeconds] — how many seconds of the score were actually played,
     *                    derived from the playhead progress × totalSeconds.
     */
    fun flushSession(elapsedSeconds: Int) {
        val snapshot: List<SessionPayload.AttemptPayload> = synchronized(attemptBuffer) {
            attemptBuffer.toList().also { attemptBuffer.clear() }
        }

        if (snapshot.isEmpty()) return   // Nothing to persist — mic was off or no notes hit

        val total   = snapshot.size
        val matches = snapshot.count { it.match }
        val accuracy = if (total > 0) (matches * 100f / total) else 0f

        val payload = SessionPayload(
            scoreTitle       = currentScoreTitle,
            mode             = "Test Pitch",
            startedAt        = sessionStartedAt.toString(),
            elapsedSeconds   = elapsedSeconds,
            performanceScore = accuracy,
            attempts         = snapshot
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = sessionService.saveSession(payload)
                android.util.Log.i(
                    "PitchVC",
                    "Session saved: id=${response.sessionId}, score=${response.performanceScore}%"
                )
            } catch (e: Exception) {
                android.util.Log.e("PitchVC", "Failed to save session", e)
                // Non-fatal: local practice still worked; server sync failed.
            }
        }
    }

    /**
     * Updates [voiceFilter] on the active [ScoreAlignmentManager] when the
     * user switches SATB part mid-session.
     *
     * [voiceNumber] follows MusicXML convention: 1 = Soprano, 2 = Alto,
     * 3 = Tenor, 4 = Bass.
     */
    fun updateVoicePart(score: MusicXmlScore, voiceNumber: Int) {
        alignmentManager = ScoreAlignmentManager(score, voiceFilter = voiceNumber)
    }

    // ── ViewModel lifecycle ────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        stopPitchTracking()
    }

    // ── Private: execution loop (SDD: executionLoop) ───────────────────────

    /**
     * Implements the SDD "executionLoop" — compares a [DetectedPitch] against
     * the current [TargetNote] and posts a [PitchUiState.Active] verdict.
     *
     * Called from the TarsosDSP background thread; state is posted to
     * [Dispatchers.Main] via [viewModelScope].
     */
    private fun executionLoop(
        detected: DetectedPitch,
        playheadProgressProvider: () -> Float
    ) {
        val manager = alignmentManager ?: run {
            postState(PitchUiState.Listening)
            return
        }

        val progress = playheadProgressProvider()
        val targetNote = manager.getCurrentTargetNote(progress)

        if (targetNote == null || targetNote.pitchFrequencyHz <= 0f) {
            // No note at this playhead position (rest, intro, gap).
            postState(PitchUiState.Listening)
            return
        }

        val feedbackResult = PitchComparator.compare(detected, targetNote)

        if (feedbackResult == null) {
            // NaN deviation — noise or unpitched sound.
            postState(PitchUiState.NoiseWarning)
            return
        }

        // Accumulate the attempt for end-of-session persistence.
        attemptBuffer.add(
            SessionPayload.AttemptPayload(
                noteLabel      = targetNote.noteName,
                detectedHz     = detected.frequencyHz,
                deviationCents = feedbackResult.deviationCents,
                match          = feedbackResult.isMatch,
                timestampMs    = System.currentTimeMillis()
            )
        )

        postState(
            PitchUiState.Active(
                result = feedbackResult,
                targetNoteName = targetNote.noteName
            )
        )
    }

    private fun postState(state: PitchUiState) {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = state
        }
    }
}
