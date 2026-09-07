package com.cit.kaido.voxsight.ui.tour

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Modifier to register the global screen coordinates of a composable into the TourState.
 * When this tour step is active, a spotlight cutout will be placed over this composable.
 */
fun Modifier.spotlightTarget(
    tourState: TourState,
    stepId: String
): Modifier = this.onGloballyPositioned { coordinates ->
    if (coordinates.isAttached) {
        val bounds = coordinates.boundsInRoot()
        tourState.updateTargetRect(stepId, bounds)
    }
}
