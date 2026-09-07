package com.cit.kaido.voxsight.ui.tour

enum class SpotlightShapeType {
    RoundedRect,
    Circle
}

/**
 * Represents a single step in an in-app interactive guided walkthrough.
 */
data class TourStep(
    val id: String,
    val title: String,
    val description: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val shape: SpotlightShapeType = SpotlightShapeType.RoundedRect,
    val cornerRadiusDp: Float = 16f
)
