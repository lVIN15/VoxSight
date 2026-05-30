package com.cit.kaido.voxsight.pitch

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import be.tarsos.dsp.pitch.Yin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PitchDetectionEngine {

    private val _detectedPitchHz = MutableStateFlow(0f)
    val detectedPitchHz: StateFlow<Float> = _detectedPitchHz.asStateFlow()

    private val _pitchConfidence = MutableStateFlow(0f)
    val pitchConfidence: StateFlow<Float> = _pitchConfidence.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Noise threshold (probability/confidence threshold for YIN)
    // Lowered to 0.55f for natural choir singing/vibrato (Fix #5)
    var confidenceThreshold: Float = 0.55f 

    @SuppressLint("MissingPermission")
    fun start() {
        if (_isListening.value) return

        try {
            val sampleRate = 22050
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            // TarsosDSP Yin works well with 1024 or 2048 buffer sizes for 22050Hz
            val frameSize = 1024
            
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, frameSize * 2)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("PitchDetectionEngine", "AudioRecord initialization failed")
                return
            }

            val yin = Yin(sampleRate.toFloat(), frameSize)
            val shortBuffer = ShortArray(frameSize)
            val floatBuffer = FloatArray(frameSize)

            audioRecord?.startRecording()
            _isListening.value = true

            recordingJob = scope.launch {
                while (isActive && _isListening.value) {
                    val readResult = audioRecord?.read(shortBuffer, 0, frameSize) ?: -1
                    if (readResult > 0) {
                        // Convert PCM short to float (-1.0 to 1.0)
                        for (i in 0 until readResult) {
                            floatBuffer[i] = shortBuffer[i].toFloat() / 32768.0f
                        }

                        val result = yin.getPitch(floatBuffer)
                        val pitch = result.pitch
                        val prob = result.probability

                        if (pitch != -1f && prob >= confidenceThreshold) {
                            _detectedPitchHz.value = pitch
                            _pitchConfidence.value = prob
                        } else {
                            // Signal no valid pitch detected
                            _detectedPitchHz.value = -1f
                            _pitchConfidence.value = prob
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PitchDetectionEngine", "Exception starting PitchDetectionEngine", e)
            stop()
        }
    }

    fun stop() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("PitchDetectionEngine", "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }

        _detectedPitchHz.value = 0f
        _pitchConfidence.value = 0f
    }
}
