package com.cit.kaido.voxsight.ui.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.ui.theme.VoxPurpleAccent
import com.cit.kaido.voxsight.ui.theme.VoxPurpleLight
import com.cit.kaido.voxsight.ui.theme.VoxPurplePrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextPrimary
import com.cit.kaido.voxsight.ui.theme.VoxTextSecondary
import kotlin.math.roundToInt

/**
 * Flexible fullscreen spotlight overlay with:
 * - Pixel-perfect target coordinate translation (subtracts overlay root position to prevent status bar/inset misalignment)
 * - Dynamic shape support (perfect circles for circular icons, rounded rectangles for cards)
 * - Intelligent anti-overlap positioning (places tooltip strictly above or below the target with a clean gap)
 */
@Composable
fun SpotlightTourOverlay(
    tourState: TourState,
    onTourFinished: () -> Unit,
    onTourSkipped: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!tourState.isVisible || tourState.currentStep == null) return

    val currentStep = tourState.currentStep ?: return
    val rawTargetRect = tourState.targetRects[currentStep.id]

    val density = LocalDensity.current
    var overlayBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }

    // Translate target rect from Root to local coordinates of this overlay
    val targetRect = if (rawTargetRect != null && overlayBoundsInRoot != null) {
        val root = overlayBoundsInRoot!!
        Rect(
            left = rawTargetRect.left - root.left,
            top = rawTargetRect.top - root.top,
            right = rawTargetRect.right - root.left,
            bottom = rawTargetRect.bottom - root.top
        )
    } else {
        rawTargetRect
    }

    AnimatedVisibility(
        visible = tourState.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    overlayBoundsInRoot = coordinates.boundsInRoot()
                    overlayHeightPx = coordinates.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* consume background touches */ }
                }
        ) {
            // ── 1. Scrim with Spotlight Cutout ───────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                // Dimmed background (78% alpha black)
                drawRect(color = Color(0xC7000000))

                if (targetRect != null && targetRect.width > 0 && targetRect.height > 0) {
                    when (currentStep.shape) {
                        SpotlightShapeType.Circle -> {
                            val padPx = 6.dp.toPx()
                            val center = Offset(targetRect.center.x, targetRect.center.y)
                            val radius = maxOf(targetRect.width, targetRect.height) / 2f + padPx

                            // Cut clear circle hole
                            drawCircle(
                                color = Color.Transparent,
                                center = center,
                                radius = radius,
                                blendMode = BlendMode.Clear
                            )
                            // Glowing accent circle outline
                            drawCircle(
                                color = VoxPurpleAccent,
                                center = center,
                                radius = radius,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                        SpotlightShapeType.RoundedRect -> {
                            val padPx = 8.dp.toPx()
                            val cutoutLeft = (targetRect.left - padPx).coerceAtLeast(0f)
                            val cutoutTop = (targetRect.top - padPx).coerceAtLeast(0f)
                            val cutoutWidth = (targetRect.width + padPx * 2).coerceAtMost(size.width - cutoutLeft)
                            val cutoutHeight = (targetRect.height + padPx * 2).coerceAtMost(size.height - cutoutTop)
                            val cornerRadius = CornerRadius(currentStep.cornerRadiusDp.dp.toPx(), currentStep.cornerRadiusDp.dp.toPx())

                            // Cut clear rounded rectangle hole
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(cutoutLeft, cutoutTop),
                                size = Size(cutoutWidth, cutoutHeight),
                                cornerRadius = cornerRadius,
                                blendMode = BlendMode.Clear
                            )
                            // Glowing accent outline
                            drawRoundRect(
                                color = VoxPurpleAccent,
                                topLeft = Offset(cutoutLeft, cutoutTop),
                                size = Size(cutoutWidth, cutoutHeight),
                                cornerRadius = cornerRadius,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            // ── 2. Dynamic, Non-Overlapping Tooltip Card ─────────────
            val padPx = with(density) { 8.dp.toPx() }
            val gapPx = with(density) { 16.dp.toPx() }
            val minSafeMarginPx = with(density) { 16.dp.toPx() }
            val estimatedCardHeight = if (cardHeightPx > 0f) cardHeightPx else with(density) { 220.dp.toPx() }

            val targetCutoutTop = if (targetRect != null) {
                if (currentStep.shape == SpotlightShapeType.Circle) {
                    val radius = maxOf(targetRect.width, targetRect.height) / 2f + with(density) { 6.dp.toPx() }
                    targetRect.center.y - radius
                } else {
                    targetRect.top - padPx
                }
            } else 0f

            val targetCutoutBottom = if (targetRect != null) {
                if (currentStep.shape == SpotlightShapeType.Circle) {
                    val radius = maxOf(targetRect.width, targetRect.height) / 2f + with(density) { 6.dp.toPx() }
                    targetRect.center.y + radius
                } else {
                    targetRect.bottom + padPx
                }
            } else 0f

            val totalHeight = if (overlayHeightPx > 0f) overlayHeightPx else with(density) { 700.dp.toPx() }
            val spaceAbove = (targetCutoutTop).coerceAtLeast(0f)
            val spaceBelow = (totalHeight - targetCutoutBottom).coerceAtLeast(0f)

            // Flexible decision: never place on top of the target
            val shouldPlaceBelow = when {
                targetRect == null -> false
                // If target is in the top 45% of the screen, place below if there is room
                targetCutoutTop < totalHeight * 0.45f && spaceBelow >= estimatedCardHeight -> true
                // If there's enough space above, prefer above to avoid covering lower cards
                spaceAbove >= estimatedCardHeight + gapPx -> false
                // Otherwise choose the side with the most available space
                else -> spaceBelow >= spaceAbove
            }

            val targetYPx = if (targetRect == null) {
                (totalHeight - estimatedCardHeight) / 2f
            } else if (shouldPlaceBelow) {
                targetCutoutBottom + gapPx
            } else {
                targetCutoutTop - estimatedCardHeight - gapPx
            }

            val maxSafeYPx = (totalHeight - estimatedCardHeight - minSafeMarginPx).coerceAtLeast(minSafeMarginPx)
            val clampedYPx = targetYPx.coerceIn(minSafeMarginPx, maxSafeYPx)

            val animatedYPx by animateFloatAsState(
                targetValue = clampedYPx,
                animationSpec = tween(durationMillis = 280),
                label = "tooltipY"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, animatedYPx.roundToInt()) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                TooltipCard(
                    step = currentStep,
                    isLastStep = tourState.isLastStep,
                    onNext = { tourState.nextStep(onTourFinished) },
                    onSkip = { tourState.skipTour(onTourSkipped) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        cardHeightPx = coordinates.size.height.toFloat()
                    }
                )
            }
        }
    }
}

@Composable
private fun TooltipCard(
    step: TourStep,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Step Badge Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = VoxPurpleLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = VoxPurplePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "STEP ${step.stepIndex} OF ${step.totalSteps}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoxPurplePrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Title
            Text(
                text = step.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = VoxTextPrimary
            )

            // Description
            Text(
                text = step.description,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = VoxTextSecondary
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Skip Button
                TextButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Skip Tour",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VoxTextSecondary
                    )
                }

                // Next / Got It Button
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoxPurplePrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = if (isLastStep) "Got It! ✓" else "Next ➔",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
