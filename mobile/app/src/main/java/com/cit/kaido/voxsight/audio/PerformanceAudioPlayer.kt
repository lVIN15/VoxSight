package com.cit.kaido.voxsight.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * PerformanceAudioPlayer — Manages playback of the user's recorded vocal performance.
 * Supports play/pause, position seeking, speed adjustments, and progress tracking.
 */
class PerformanceAudioPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun load(file: File) {
        release()
        if (!file.exists()) return

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    _durationMs.value = mp.duration
                    _currentPositionMs.value = 0
                    _isReady.value = true
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0
                    stopProgressTracking()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("PerformanceAudioPlayer", "Error loading audio file: ${file.name}", e)
        }
    }

    fun play() {
        val mp = mediaPlayer ?: return
        try {
            applySpeed(_playbackSpeed.value)
            mp.start()
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            Log.e("PerformanceAudioPlayer", "Error starting playback", e)
        }
    }

    fun pause() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
            }
            _isPlaying.value = false
            stopProgressTracking()
        } catch (e: Exception) {
            Log.e("PerformanceAudioPlayer", "Error pausing playback", e)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Int) {
        val mp = mediaPlayer ?: return
        try {
            val clamped = positionMs.coerceIn(0, _durationMs.value)
            mp.seekTo(clamped)
            _currentPositionMs.value = clamped
        } catch (e: Exception) {
            Log.e("PerformanceAudioPlayer", "Error seeking playback", e)
        }
    }

    fun seekRelative(deltaMs: Int) {
        val target = (_currentPositionMs.value + deltaMs).coerceIn(0, _durationMs.value)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                }
            } catch (e: Exception) {
                Log.w("PerformanceAudioPlayer", "Failed to apply speed params: ${e.message}")
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _currentPositionMs.value = mp.currentPosition
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _currentPositionMs.value = 0
            _durationMs.value = 0
            _isReady.value = false
        }
    }
}
