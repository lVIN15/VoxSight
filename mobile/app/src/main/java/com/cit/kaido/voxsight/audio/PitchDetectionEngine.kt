package com.cit.kaido.voxsight.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm

// ── Private TarsosDSP Stream Adapter ─────────────────────────────────────────

/**
 * Wraps Android's native AudioRecord into a stream that TarsosDSP can consume.
 * This removes the need for the unmaintained TarsosDSP-Android dependency.
 */
private class AndroidAudioInputStream(
    private val audioRecord: AudioRecord,
    private val format: TarsosDSPAudioFormat
) : TarsosDSPAudioInputStream {
    
    override fun skip(bytesToSkip: Long): Long = 0
    
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return audioRecord.read(b, off, len)
    }
    
    override fun close() {
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: Exception) {
            // Ignore stop errors on closed streams
        }
    }
    
    override fun getFormat(): TarsosDSPAudioFormat = format
    override fun getFrameLength(): Long = -1
}

/**
 * PitchDetectionEngine
 *
 * Opens the device microphone through TarsosDSP and continuously emits the
 * detected fundamental frequency (Hz) to the caller via [onPitchDetected].
 *
 * SDD Class Diagram mapping (Module 4):
 *   PitchDetectionEngine
 *     -noiseThresholdLimit  : float
 *     -minConfidenceLevel   : float
 *     +analyzeFrequency()   → DetectedPitch
 *     +isNoiseLevelTooHigh(): boolean
 *
 * Threading model
 * ---------------
 * [start] spawns a dedicated background [Thread] so the audio processing loop
 * never blocks the Android main/UI thread (prevents ANR).
 * [onPitchDetected] is invoked from that background thread — callers must
 * post to the main thread themselves if they need to update Compose state
 * (e.g., via a MutableStateFlow collected from a ViewModel).
 *
 * Performance targets (SRS §3.3)
 * --------------------------------
 * • Pitch feedback latency  ≤ 0.5 s
 * • Pitch detection accuracy ≥ 85 % vs. standard tuner
 *
 * These are satisfied by:
 *   - SAMPLE_RATE  22 050 Hz  (good coverage of all SATB ranges)
 *   - BUFFER_SIZE  1 024 samples ≈ 46 ms per frame at 22 050 Hz
 *   - Algorithm    YIN  (accurate monophonic pitch tracker)
 */
class PitchDetectionEngine(
    /**
     * Minimum TarsosDSP confidence score (0.0–1.0) for a result to be
     * forwarded.  Readings below this value are treated as noise/silence.
     * Default: 0.8 (80 % confidence).
     */
    private val minConfidenceLevel: Float = 0.8f,

    /**
     * Called on every audio frame where a valid pitch is detected.
     * [frequencyHz] — detected fundamental frequency in Hz (> 0).
     * [confidence]  — TarsosDSP confidence score in range 0.0–1.0.
     */
    private val onPitchDetected: (frequencyHz: Float, confidence: Float) -> Unit,

    /**
     * Called when a frame does not yield a confident pitch (noise / silence /
     * environmental interference).  The UI uses this to show the neutral
     * indicator described in the SRS activity diagram.
     */
    private val onNoPitchDetected: () -> Unit = {}
) {
    // ── Constants ────────────────────────────────────────────────────────────
    companion object {
        /** Audio sample rate in Hz.  22 050 Hz covers all SATB vocal ranges. */
        private const val SAMPLE_RATE = 22_050

        /**
         * TarsosDSP audio buffer size in samples.
         * 1 024 samples @ 22 050 Hz ≈ 46 ms per analysis frame.
         * Keeping this small ensures we stay well under the ≤ 0.5 s latency
         * target even after aggregating multiple frames.
         */
        private const val BUFFER_SIZE = 1024

        /** Number of samples that overlap between consecutive buffers. */
        private const val BUFFER_OVERLAP = 0
    }

    // ── Internal state ───────────────────────────────────────────────────────
    private var dispatcher: AudioDispatcher? = null
    private var detectionThread: Thread? = null

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Opens the default device microphone and starts the YIN pitch-estimation
     * loop on a new background thread.
     *
     * Safe to call from any thread.  Does nothing if already running.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (dispatcher != null) return           // already running

        // 1. Initialize native Android AudioRecord (requires RECORD_AUDIO permission,
        // which the UI layer has already requested and validated).
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, BUFFER_SIZE * 2)
        )
        audioRecord.startRecording()

        // 2. Wrap into TarsosDSP formats
        val format = TarsosDSPAudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val audioStream = AndroidAudioInputStream(audioRecord, format)
        
        val newDispatcher = AudioDispatcher(audioStream, BUFFER_SIZE, BUFFER_OVERLAP)

        // 3. Set up the YIN algorithm pitch tracker
        val pitchHandler = PitchDetectionHandler { pitchDetectionResult, _ ->
            val hz = pitchDetectionResult.pitch
            val confidence = pitchDetectionResult.probability

            when {
                hz > 0f && confidence >= minConfidenceLevel -> {
                    // Valid, confident pitch detected.
                    onPitchDetected(hz, confidence)
                }
                else -> {
                    // No reliable pitch — silence, noise, or low confidence.
                    onNoPitchDetected()
                }
            }
        }

        val pitchProcessor = PitchProcessor(
            PitchEstimationAlgorithm.YIN,
            SAMPLE_RATE.toFloat(),
            BUFFER_SIZE,
            pitchHandler
        )

        newDispatcher.addAudioProcessor(pitchProcessor)
        dispatcher = newDispatcher

        // 4. Run on a dedicated daemon thread so it does not block the UI thread.
        detectionThread = Thread(newDispatcher, "VoxSight-PitchDetection")
        detectionThread?.isDaemon = true
        detectionThread?.start()
    }

    /**
     * Stops the microphone capture and releases all TarsosDSP resources.
     *
     * Safe to call from any thread, including from the UI thread.
     * After [stop] returns, [start] may be called again.
     */
    fun stop() {
        dispatcher?.stop()
        dispatcher = null
        detectionThread = null
    }

    /**
     * Convenience: returns true while the engine is actively recording.
     */
    val isRunning: Boolean
        get() = dispatcher != null
}
