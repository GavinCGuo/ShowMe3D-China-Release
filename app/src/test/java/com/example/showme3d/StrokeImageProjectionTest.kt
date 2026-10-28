package com.example.showme3d

import com.example.showme3d.studio.aigc.StrokeImageProjection
import com.example.showme3d.studio.aigc.StrokeFrontFace
import com.example.showme3d.studio.model.StrokeData
import com.example.showme3d.studio.model.StrokePath
import com.example.showme3d.studio.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeImageProjectionTest {
    @Test
    fun projectUsesObjectLocalXYAndCentersLineArt() {
        val data = StrokeData(
            mutableListOf(
                StrokePath(
                    color = 0xFF000000,
                    points = mutableListOf(
                        StrokePoint(-0.5f, -0.25f, -2f),
                        StrokePoint(0.5f, 0.25f, 2f),
                    ),
                ),
            ),
        )

        val layout = StrokeImageProjection.project(data, imageSizePx = 1000, paddingPx = 100f)
        val points = layout.paths.single().points

        assertEquals(100f, points.first().x, 0.001f)
        assertEquals(700f, points.first().y, 0.001f)
        assertEquals(900f, points.last().x, 0.001f)
        assertEquals(300f, points.last().y, 0.001f)
        assertEquals(19.2f, layout.strokeWidthPx, 0.001f)
    }

    @Test
    fun projectMainViewUsesTheViewerFacingAxis() {
        val data = StrokeData(
            mutableListOf(
                StrokePath(
                    color = 0xFF000000,
                    points = mutableListOf(
                        StrokePoint(-0.5f, -0.25f, -0.4f),
                        StrokePoint(0.5f, 0.25f, 0.4f),
                    ),
                ),
            ),
        )

        val positiveZ = StrokeImageProjection.projectMainView(
            data = data,
            frontFace = StrokeFrontFace.POSITIVE_Z,
            imageSizePx = 900,
            paddingPx = 90f,
        )
        val positiveX = StrokeImageProjection.projectMainView(
            data = data,
            frontFace = StrokeFrontFace.POSITIVE_X,
            imageSizePx = 900,
            paddingPx = 90f,
        )
        val frontPoints = positiveZ.paths.single().points
        val sidePoints = positiveX.paths.single().points

        assertTrue(frontPoints.first().x < frontPoints.last().x)
        assertTrue(frontPoints.first().y > frontPoints.last().y)
        assertTrue(sidePoints.first().x > sidePoints.last().x)
    }

    @Test
    fun projectMainViewFlipsWhenPlayerFacesNegativeZSide() {
        val data = StrokeData(
            mutableListOf(
                StrokePath(
                    color = 0xFF000000,
                    points = mutableListOf(
                        StrokePoint(-0.5f, 0f, 0f),
                        StrokePoint(0.5f, 0f, 0f),
                    ),
                ),
            ),
        )

        val layout = StrokeImageProjection.projectMainView(
            data = data,
            frontFace = StrokeFrontFace.NEGATIVE_Z,
            imageSizePx = 900,
            paddingPx = 90f,
        )
        val frontPoints = layout.paths.single().points

        assertTrue(frontPoints.first().x > frontPoints.last().x)
    }
}
