package com.cit.kaido.voxsight.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PracticeViewModel : ViewModel() {
    private val _isMicrophoneEnabled = MutableStateFlow(false)
    val isMicrophoneEnabled: StateFlow<Boolean> = _isMicrophoneEnabled.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _showPauseModal = MutableStateFlow(false)
    val showPauseModal: StateFlow<Boolean> = _showPauseModal.asStateFlow()

    // Map<Int, Boolean> (Note Index -> WasCorrect)
    private val _pitchAttempts = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val pitchAttempts: StateFlow<Map<Int, Boolean>> = _pitchAttempts.asStateFlow()

    fun setMicrophoneEnabled(enabled: Boolean) {
        _isMicrophoneEnabled.value = enabled
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setShowPauseModal(show: Boolean) {
        _showPauseModal.value = show
    }

    fun recordPitchAttempt(noteIndex: Int, wasCorrect: Boolean) {
        val currentMap = _pitchAttempts.value.toMutableMap()
        currentMap[noteIndex] = wasCorrect
        _pitchAttempts.value = currentMap
    }

    fun calculateAccuracy(): Int {
        val attempts = _pitchAttempts.value
        if (attempts.isEmpty()) return 0
        val correctCount = attempts.values.count { it }
        return ((correctCount.toFloat() / attempts.size) * 100).toInt()
    }
}
