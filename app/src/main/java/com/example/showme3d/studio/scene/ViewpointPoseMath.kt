package com.example.showme3d.studio.scene

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small SDK-independent representation used to keep viewpoint inverse-transform math testable.
 */
internal data class ViewpointPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

internal data class ViewpointRootPose(
    val position: ViewpointPoint,
    val yawRadians: Float,
    val scale: Float,
)

/**
 * Builds the workspace transform that places [sourceEye] at [targetEye] and aligns the
 * viewpoint's local +Z facing direction with [targetForward].
 *
 * PICO's spatial entity space is right-handed. For a positive Y rotation:
 *   x' = cos(yaw) * x + sin(yaw) * z
 *   z' = -sin(yaw) * x + cos(yaw) * z
 *
 * Keeping that same rotation convention for both orientation and translation is important:
 * using the inverse signs for the translated eye makes the viewpoint drift whenever it is turned.
 */
internal fun calculateViewpointRootPose(
    sourceEye: ViewpointPoint,
    sourceYawRadians: Float,
    scale: Float,
    targetEye: ViewpointPoint,
    targetForward: ViewpointPoint,
): ViewpointRootPose {
    val targetYaw = if (targetForward.hasUsableHorizontalDirection()) {
        atan2(targetForward.x, targetForward.z)
    } else {
        DEFAULT_VIEWER_FORWARD_YAW_RADIANS
    }
    val rootYaw = targetYaw - sourceYawRadians
    val cosYaw = cos(rootYaw)
    val sinYaw = sin(rootYaw)
    val scaledX = sourceEye.x * scale
    val scaledY = sourceEye.y * scale
    val scaledZ = sourceEye.z * scale
    return ViewpointRootPose(
        position = ViewpointPoint(
            x = targetEye.x - (scaledX * cosYaw + scaledZ * sinYaw),
            y = targetEye.y - scaledY,
            z = targetEye.z - (-scaledX * sinYaw + scaledZ * cosYaw),
        ),
        yawRadians = rootYaw,
        scale = scale,
    )
}

private fun ViewpointPoint.hasUsableHorizontalDirection(): Boolean =
    x.isFinite() && z.isFinite() && x * x + z * z > MIN_DIRECTION_LENGTH_SQUARED

private const val MIN_DIRECTION_LENGTH_SQUARED = 0.000001f
private const val DEFAULT_VIEWER_FORWARD_YAW_RADIANS = 3.1415927f
