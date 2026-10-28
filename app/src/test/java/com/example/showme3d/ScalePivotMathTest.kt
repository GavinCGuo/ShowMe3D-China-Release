package com.example.showme3d

import com.example.showme3d.studio.interaction.scaleTransformAroundLocalPivot
import com.example.showme3d.studio.model.TransformData
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class ScalePivotMathTest {

    @Test
    fun sharedOriginVisualCenterStaysFixedWhileScaling() {
        val start = TransformData(
            px = 0.10604139f,
            py = 0.71439004f,
            pz = -2.5826592f,
            rotationY = -1.3380868f,
            scale = 2.7302701f,
        )
        val pivot = floatArrayOf(0.72f, 0.31f, -0.48f)
        val next = scaleTransformAroundLocalPivot(
            transform = start,
            nextScale = 4.15f,
            localPivotX = pivot[0],
            localPivotY = pivot[1],
            localPivotZ = pivot[2],
        )

        assertPointEquals(worldPivot(start, pivot), worldPivot(next, pivot))
    }

    @Test
    fun centeredModelOnlyChangesScale() {
        val start = TransformData(px = 0.4f, py = 1.2f, pz = -2.1f, rotationY = 0.7f, scale = 1f)
        val next = scaleTransformAroundLocalPivot(start, 1.8f, 0f, 0f, 0f)

        assertEquals(start.px, next.px, EPSILON)
        assertEquals(start.py, next.py, EPSILON)
        assertEquals(start.pz, next.pz, EPSILON)
        assertEquals(1.8f, next.scale, EPSILON)
    }

    @Test
    fun repeatedScaleEventsKeepTheSameWorldPivot() {
        val pivot = floatArrayOf(-0.35f, 0.82f, 0.64f)
        val start = TransformData(px = -0.2f, py = 0.9f, pz = -1.7f, rotationY = 1.1f, scale = 2.2f)
        val middle = scaleTransformAroundLocalPivot(start, 2.6f, pivot[0], pivot[1], pivot[2])
        val end = scaleTransformAroundLocalPivot(middle, 1.4f, pivot[0], pivot[1], pivot[2])

        assertPointEquals(worldPivot(start, pivot), worldPivot(end, pivot))
    }

    private fun worldPivot(transform: TransformData, pivot: FloatArray): FloatArray {
        val cosine = cos(transform.rotationY)
        val sine = sin(transform.rotationY)
        return floatArrayOf(
            transform.px + (cosine * pivot[0] + sine * pivot[2]) * transform.scale,
            transform.py + pivot[1] * transform.scale,
            transform.pz + (-sine * pivot[0] + cosine * pivot[2]) * transform.scale,
        )
    }

    private fun assertPointEquals(expected: FloatArray, actual: FloatArray) {
        expected.indices.forEach { index -> assertEquals(expected[index], actual[index], EPSILON) }
    }

    private companion object {
        const val EPSILON = 0.00001f
    }
}
