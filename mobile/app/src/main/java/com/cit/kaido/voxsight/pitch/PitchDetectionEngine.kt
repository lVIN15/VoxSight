package com.cit.kaido.voxsight.pitch

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm

class PitchDetectionEngine(
    private val onPitchDetected: (hz: Float, confidence: Float) -> Unit,
    private val onNoiseDetected: () -> Unit
) {

    private var dispatcher: AudioDispatcher? = null
    private var dispatcherThread: Thread? = null

    private val sampleRate = 22050
    private val bufferSize = 1024
    private val minConfidence = 0.85f

    fun start() {
        stop()

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, 0)

        val handler = PitchDetectionHandler { result, _ ->
            val hz = result.pitch
            val confidence = result.probability

            when {
                hz < 0 -> onNoiseDetected()
                confidence < minConfidence -> onNoiseDetected()
                else -> onPitchDetected(hz, confidence)
            }
        }

        val pitchProcessor = PitchProcessor(
            PitchEstimationAlgorithm.YIN,
            sampleRate.toFloat(),
            bufferSize,
            handler
        )

        dispatcher?.addAudioProcessor(pitchProcessor)

        dispatcherThread = Thread(dispatcher, "PitchDetectionThread")
        dispatcherThread?.start()
    }

    fun stop() {
        dispatcher?.stop()
        dispatcher = null
        dispatcherThread = null
    }

    fun isRunning(): Boolean {
        return dispatcherThread?.isAlive == true
    }
}