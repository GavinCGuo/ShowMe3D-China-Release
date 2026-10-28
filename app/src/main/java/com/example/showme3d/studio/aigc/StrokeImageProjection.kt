package com.example.showme3d.studio.aigc

import com.example.showme3d.studio.model.StrokeData
import com.example.showme3d.studio.model.StrokePoint
import kotlin.math.abs
import kotlin.math.min

data class StrokeImageLayout(
    val imageSizePx: Int,
    val strokeWidthPx: Float,
    val paths: List<StrokeImagePathLayout>,
)

data class StrokeImagePathLayout(
    val color: Long,
    val points: List<StrokeImagePoint>,
)

data class StrokeImagePoint(
    val x: Float,
    val y: Float,
)

enum class StrokeFrontFace(
    val normalX: Float,
    val normalZ: Float,
) {
    POSITIVE_Z(0f, 1f),
    NEGATIVE_Z(0f, -1f),
    POSITIVE_X(1f, 0f),
    NEGATIVE_X(-1f, 0f);

    val rightX: Float get() = normalZ
    val rightZ: Float get() = -normalX

    companion object {
        fun fromLocalViewerDirection(localX: Float, localZ: Float): StrokeFrontFace {
            if (!localX.isFinite() || !localZ.isFinite()) return POSITIVE_Z
            if (abs(localX) < MIN_DIRECTION && abs(localZ) < MIN_DIRECTION) return POSITIVE_Z
            return if (abs(localX) > abs(localZ)) {
                if (localX >= 0f) POSITIVE_X else NEGATIVE_X
            } else {
                if (localZ >= 0f) POSITIVE_Z else NEGATIVE_Z
            }
        }

        private const val MIN_DIRECTION = 0.0001f
    }
}

object StrokeImageProjection {
    fun project(
        data: StrokeData,
        imageSizePx: Int = DEFAULT_IMAGE_SIZE_PX,
        paddingPx: Float = DEFAULT_PADDING_PX,
    ): StrokeImageLayout {
        return projectMainView(
            data = data,
            frontFace = StrokeFrontFace.POSITIVE_Z,
            imageSizePx = imageSizePx,
            paddingPx = paddingPx,
        )
    }

    fun projectMainView(
        data: StrokeData,
        frontFace: StrokeFrontFace = StrokeFrontFace.POSITIVE_Z,
        imageSizePx: Int = DEFAULT_IMAGE_SIZE_PX,
        paddingPx: Float = DEFAULT_PADDING_PX,
    ): StrokeImageLayout {
        require(imageSizePx > paddingPx * 2f) { "线稿图片尺寸过小" }
        val projected = projectPaths(
            data = data,
            viewportLeftPx = 0f,
            viewportTopPx = 0f,
            viewportWidthPx = imageSizePx.toFloat(),
            viewportHeightPx = imageSizePx.toFloat(),
            paddingPx = paddingPx,
            horizontal = { it.x * frontFace.rightX + it.z * frontFace.rightZ },
            vertical = { it.y },
        )

        return StrokeImageLayout(
            imageSizePx = imageSizePx,
            strokeWidthPx = projected.strokeWidthPx,
            paths = projected.paths,
        )
    }

    private fun projectPaths(
        data: StrokeData,
        viewportLeftPx: Float,
        viewportTopPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        paddingPx: Float,
        horizontal: (StrokePoint) -> Float,
        vertical: (StrokePoint) -> Float,
    ): ProjectedPaths {
        require(viewportWidthPx > paddingPx * 2f) { "线稿视图宽度过小" }
        require(viewportHeightPx > paddingPx * 2f) { "线稿视图高度过小" }
        val paths = data.strokes
            .map { path -> path.color to path.points.map { it.copy() } }
            .filter { (_, points) -> points.size >= 2 }
        require(paths.isNotEmpty()) { "笔画没有可转换的线条" }

        val projectedSourcePaths = paths.map { (color, points) ->
            color to points.map { point -> StrokeImagePoint(horizontal(point), vertical(point)) }
        }
        val allPoints = projectedSourcePaths.flatMap { (_, points) -> points }
        var minX = allPoints.minOf { it.x }
        var maxX = allPoints.maxOf { it.x }
        var minY = allPoints.minOf { it.y }
        var maxY = allPoints.maxOf { it.y }
        if (maxX - minX < MIN_CONTENT_METERS) {
            val center = (minX + maxX) * 0.5f
            minX = center - MIN_CONTENT_METERS * 0.5f
            maxX = center + MIN_CONTENT_METERS * 0.5f
        }
        if (maxY - minY < MIN_CONTENT_METERS) {
            val center = (minY + maxY) * 0.5f
            minY = center - MIN_CONTENT_METERS * 0.5f
            maxY = center + MIN_CONTENT_METERS * 0.5f
        }

        val sourceWidth = maxX - minX
        val sourceHeight = maxY - minY
        val contentWidth = viewportWidthPx - paddingPx * 2f
        val contentHeight = viewportHeightPx - paddingPx * 2f
        val scale = min(contentWidth / sourceWidth, contentHeight / sourceHeight)
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        val originX = viewportLeftPx + (viewportWidthPx - drawnWidth) * 0.5f - minX * scale
        val originY = viewportTopPx + (viewportHeightPx - drawnHeight) * 0.5f + maxY * scale

        return ProjectedPaths(
            strokeWidthPx = (STROKE_DIAMETER_METERS * scale).coerceIn(MIN_STROKE_WIDTH_PX, MAX_STROKE_WIDTH_PX),
            paths = projectedSourcePaths.map { (color, points) ->
                StrokeImagePathLayout(
                    color = color,
                    points = points.map { point ->
                        StrokeImagePoint(
                            x = originX + point.x * scale,
                            y = originY - point.y * scale,
                        )
                    },
                )
            },
        )
    }

    private data class ProjectedPaths(
        val strokeWidthPx: Float,
        val paths: List<StrokeImagePathLayout>,
    )

    private const val DEFAULT_IMAGE_SIZE_PX = 1024
    private const val DEFAULT_PADDING_PX = 96f
    private const val MIN_CONTENT_METERS = 0.08f
    private const val STROKE_DIAMETER_METERS = 0.024f
    private const val MIN_STROKE_WIDTH_PX = 5f
    private const val MAX_STROKE_WIDTH_PX = 22f
}
