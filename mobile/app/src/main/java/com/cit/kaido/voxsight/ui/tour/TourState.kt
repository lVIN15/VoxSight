package com.cit.kaido.voxsight.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Observable state holder for the in-app interactive walkthrough.
 */
class TourState(
    val steps: List<TourStep>,
    isInitiallyVisible: Boolean = true
) {
    var isVisible by mutableStateOf(isInitiallyVisible && steps.isNotEmpty())
    var currentStepIndex by mutableIntStateOf(0)
    
    // Holds measured screen bounds for each tour step target
    val targetRects = mutableStateMapOf<String, Rect>()

    val currentStep: TourStep?
        get() = steps.getOrNull(currentStepIndex)

    val isLastStep: Boolean
        get() = currentStepIndex >= steps.size - 1

    fun updateTargetRect(stepId: String, rect: Rect) {
        targetRects[stepId] = rect
    }

    fun nextStep(onFinished: () -> Unit) {
        if (isLastStep) {
            isVisible = false
            onFinished()
        } else {
            currentStepIndex++
        }
    }

    fun skipTour(onSkipped: () -> Unit) {
        isVisible = false
        onSkipped()
    }

    fun reset() {
        currentStepIndex = 0
        isVisible = steps.isNotEmpty()
    }
}

@Composable
fun rememberTourState(
    steps: List<TourStep>,
    isInitiallyVisible: Boolean = true
): TourState {
    return remember(steps, isInitiallyVisible) {
        TourState(steps, isInitiallyVisible)
    }
}
