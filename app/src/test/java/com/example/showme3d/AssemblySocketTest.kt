package com.example.showme3d

import com.example.showme3d.studio.examples.fitsSocket
import com.example.showme3d.studio.model.TransformData
import kotlin.math.PI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblySocketTest {
    private val socket = TransformData(px = 0.1f, py = 1.1f, pz = -2.4f, rotationY = 3.1f, scale = 1.4f)

    @Test
    fun allowsNearFitAcrossAngleWrap() {
        assertTrue(socket.copy(px = 0.15f, rotationY = socket.rotationY - (2 * PI).toFloat()).fitsSocket(socket))
    }

    @Test
    fun mismatchedPositionOrientationOrScaleDoesNotSnap() {
        assertFalse(socket.copy(px = 0.2f, py = 1.2f).fitsSocket(socket))
        assertFalse(socket.copy(rotationY = 0f).fitsSocket(socket))
        assertFalse(socket.copy(scale = 2f).fitsSocket(socket))
    }
}
