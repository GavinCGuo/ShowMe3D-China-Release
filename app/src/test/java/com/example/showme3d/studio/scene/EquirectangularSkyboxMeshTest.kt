package com.example.showme3d.studio.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquirectangularSkyboxMeshTest {
    @Test
    fun duplicatesTheLongitudeSeamWithoutConnectingUvAcrossTheTexture() {
        val longitudeSegments = 16
        val latitudeSegments = 8
        val mesh = buildEquirectangularSkyboxMesh(
            radius = 10f,
            longitudeSegments = longitudeSegments,
            latitudeSegments = latitudeSegments,
        ).model
        val rowSize = longitudeSegments + 1
        val uv0 = requireNotNull(mesh.uv0)

        assertEquals(rowSize * (latitudeSegments + 1), mesh.positions.size)
        assertEquals(mesh.positions.size, uv0.size)
        assertEquals(1f, uv0.first().y, EPSILON)
        assertEquals(0f, uv0.last().y, EPSILON)
        for (latitude in 0..latitudeSegments) {
            val first = latitude * rowSize
            val last = first + longitudeSegments
            assertVectorEquals(mesh.positions[first], mesh.positions[last])
            assertEquals(0f, uv0[first].x, EPSILON)
            assertEquals(1f, uv0[last].x, EPSILON)
        }
    }

    @Test
    fun placesPanoramaCenterInFrontAndTheSeamBehindTheViewer() {
        val longitudeSegments = 16
        val latitudeSegments = 8
        val radius = 10f
        val mesh = buildEquirectangularSkyboxMesh(
            radius = radius,
            longitudeSegments = longitudeSegments,
            latitudeSegments = latitudeSegments,
        ).model
        val rowSize = longitudeSegments + 1
        val uv0 = requireNotNull(mesh.uv0)
        val equatorRow = (latitudeSegments / 2) * rowSize
        val front = mesh.positions[equatorRow + longitudeSegments / 2]
        val seam = mesh.positions[equatorRow]

        assertEquals(0f, front.x, EPSILON)
        assertEquals(0f, front.y, EPSILON)
        assertEquals(-radius, front.z, EPSILON)
        assertEquals(radius, seam.z, EPSILON)
        assertEquals(0.5f, uv0[equatorRow + longitudeSegments / 2].x, EPSILON)
        assertTrue(mesh.triangleIndices.all { it in mesh.positions.indices })
    }

    private fun assertVectorEquals(
        expected: com.pico.spatial.core.math.Vector3,
        actual: com.pico.spatial.core.math.Vector3,
    ) {
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
        assertEquals(expected.z, actual.z, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
