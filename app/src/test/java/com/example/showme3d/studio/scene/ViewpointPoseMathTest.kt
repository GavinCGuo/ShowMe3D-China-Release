package com.example.showme3d.studio.scene

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ViewpointPoseMathTest {
    @Test
    fun turnedViewpointEyeStillLandsAtViewerPose() {
        val sourceEye = ViewpointPoint(0.7f, 1.3f, -2.1f)
        val targetEye = ViewpointPoint(-0.25f, 1.68f, 0.4f)
        val pose = calculateViewpointRootPose(
            sourceEye = sourceEye,
            sourceYawRadians = (PI / 2.0).toFloat(),
            scale = 2.4f,
            targetEye = targetEye,
            targetForward = ViewpointPoint(0f, 0f, -1f),
        )

        val transformedEye = transform(sourceEye, pose)

        assertPointEquals(targetEye, transformedEye)
    }

    @Test
    fun viewpointFacingIsAlignedWithCurrentHmdFacing() {
        val sourceYaw = (-PI / 3.0).toFloat()
        val targetForward = normalizedHorizontal(ViewpointPoint(0.8f, 0f, -0.6f))
        val pose = calculateViewpointRootPose(
            sourceEye = ViewpointPoint(0f, 1f, -1f),
            sourceYawRadians = sourceYaw,
            scale = 1f,
            targetEye = ViewpointPoint(0.2f, 1.72f, -0.1f),
            targetForward = targetForward,
        )

        val sourceForward = rotatePositiveZ(sourceYaw)
        val transformedForward = rotate(sourceForward, pose.yawRadians)

        assertEquals(targetForward.x, transformedForward.x, EPSILON)
        assertEquals(targetForward.z, transformedForward.z, EPSILON)
    }

    @Test
    fun invalidHmdFacingFallsBackToStageForward() {
        val pose = calculateViewpointRootPose(
            sourceEye = ViewpointPoint(0f, 0f, 0f),
            sourceYawRadians = 0f,
            scale = 1f,
            targetEye = ViewpointPoint(0f, 1.62f, 0f),
            targetForward = ViewpointPoint(0f, 0f, 0f),
        )

        val transformedForward = rotatePositiveZ(pose.yawRadians)

        assertEquals(0f, transformedForward.x, EPSILON)
        assertEquals(-1f, transformedForward.z, EPSILON)
    }

    private fun transform(point: ViewpointPoint, pose: ViewpointRootPose): ViewpointPoint {
        val scaled = ViewpointPoint(point.x * pose.scale, point.y * pose.scale, point.z * pose.scale)
        val rotated = rotate(scaled, pose.yawRadians)
        return ViewpointPoint(
            pose.position.x + rotated.x,
            pose.position.y + rotated.y,
            pose.position.z + rotated.z,
        )
    }

    private fun rotatePositiveZ(yaw: Float): ViewpointPoint =
        rotate(ViewpointPoint(0f, 0f, 1f), yaw)

    private fun rotate(point: ViewpointPoint, yaw: Float): ViewpointPoint {
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        return ViewpointPoint(
            x = point.x * cosYaw + point.z * sinYaw,
            y = point.y,
            z = -point.x * sinYaw + point.z * cosYaw,
        )
    }

    private fun normalizedHorizontal(point: ViewpointPoint): ViewpointPoint {
        val length = kotlin.math.sqrt(point.x * point.x + point.z * point.z)
        return ViewpointPoint(point.x / length, 0f, point.z / length)
    }

    private fun assertPointEquals(expected: ViewpointPoint, actual: ViewpointPoint) {
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
        assertEquals(expected.z, actual.z, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
