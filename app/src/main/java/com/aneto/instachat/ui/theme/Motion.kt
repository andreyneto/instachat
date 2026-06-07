package com.aneto.instachat.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

object MotionTokens {
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val EmphasizedDurationMs = 500
    const val EmphasizedDecelerateDurationMs = 400
    const val EmphasizedAccelerateDurationMs = 200
}

fun <T> emphasizedSpec() = tween<T>(
    durationMillis = MotionTokens.EmphasizedDurationMs,
    easing = MotionTokens.EmphasizedEasing,
)

fun <T> emphasizedDecelerateSpec() = tween<T>(
    durationMillis = MotionTokens.EmphasizedDecelerateDurationMs,
    easing = MotionTokens.EmphasizedDecelerateEasing,
)

fun <T> emphasizedAccelerateSpec() = tween<T>(
    durationMillis = MotionTokens.EmphasizedAccelerateDurationMs,
    easing = MotionTokens.EmphasizedAccelerateEasing,
)
