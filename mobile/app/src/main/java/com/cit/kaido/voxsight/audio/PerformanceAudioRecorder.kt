package com.cit.kaido.voxsight.audio

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PerformanceAudioRecorder — Streams raw PCM buffers from AudioRecord into a WAV audio file.
 * Records the singer's actual performance during practice for playback, review, and progress evaluation.
 */
class PerformanceAudioRecorder {

    private var rawPcmFile: File? = null
    private var pcmOutputStream: FileOutputStream? = null
    private var isRecording = false
    private var totalPcmBytes: Long = 0
    private var sampleRate: Int = 22050
    private val channels: Int = 1
    private val bitsPerSample: Int = 16

    val activeRecordingFile: File?
        get() = rawPcmFile

    fun start(outputDir: File, sampleRate: Int = 22050): File? {
        return try {
            this.sampleRate = sampleRate
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // Create temporary raw PCM file
            val file = File(outputDir, "perf_raw_${System.currentTimeMillis()}.pcm")
            rawPcmFile = file
            pcmOutputStream = FileOutputStream(file)
            totalPcmBytes = 0
            isRecording = true
            Log.d("PerformanceAudioRecorder", "Recording started: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e("PerformanceAudioRecorder", "Failed to start audio recording", e)
            isRecording = false
            null
        }
    }

    fun writeChunk(buffer: ShortArray, readCount: Int) {
        if (!isRecording || pcmOutputStream == null || readCount <= 0) return
        try {
            val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until readCount) {
                byteBuffer.putShort(buffer[i])
            }
            val bytes = byteBuffer.array()
            pcmOutputStream?.write(bytes)
            totalPcmBytes += bytes.size
        } catch (e: Exception) {
            Log.e("PerformanceAudioRecorder", "Error writing audio chunk", e)
        }
    }

    fun stop(outputDir: File): File? {
        if (!isRecording) return null
        isRecording = false

        try {
            pcmOutputStream?.flush()
            pcmOutputStream?.close()
        } catch (e: Exception) {
            Log.e("PerformanceAudioRecorder", "Error closing PCM stream", e)
        } finally {
            pcmOutputStream = null
        }

        val rawFile = rawPcmFile ?: return null
        if (!rawFile.exists() || totalPcmBytes <= 0) {
            try { rawFile.delete() } catch (_: Exception) {}
            rawPcmFile = null
            return null
        }

        val wavFile = File(outputDir, "practice_take_${System.currentTimeMillis()}.wav")
        return try {
            rawToWav(rawFile, wavFile, totalPcmBytes, sampleRate, channels, bitsPerSample)
            try { rawFile.delete() } catch (_: Exception) {} // clean up raw PCM
            rawPcmFile = null
            Log.d("PerformanceAudioRecorder", "WAV finalized: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
            wavFile
        } catch (e: Exception) {
            Log.e("PerformanceAudioRecorder", "Failed to create WAV file", e)
            rawPcmFile = null
            null
        }
    }

    private fun rawToWav(
        rawFile: File,
        wavFile: File,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
        val blockAlign = (channels * bitsPerSample / 8)

        FileInputStream(rawFile).use { input ->
            FileOutputStream(wavFile).use { output ->
                val header = ByteArray(44)
                // RIFF chunk descriptor
                header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
                // fmt sub-chunk
                header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
                header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size = 16
                header[20] = 1; header[21] = 0 // AudioFormat 1 = PCM
                header[22] = channels.toByte(); header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = blockAlign.toByte(); header[33] = 0
                header[34] = bitsPerSample.toByte(); header[35] = 0
                // data sub-chunk
                header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
                header[40] = (totalAudioLen and 0xff).toByte()
                header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
                header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
                header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

                output.write(header, 0, 44)
                input.copyTo(output)
            }
        }
    }
}
