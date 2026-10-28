package com.example.showme3d.studio.model

data class StrokeData(
    val strokes: MutableList<StrokePath> = mutableListOf(),
) {
    fun copyData(): StrokeData =
        StrokeData(strokes.map { it.copyPath() }.toMutableList())

    val pointCount: Int get() = strokes.sumOf { it.points.size }
}

data class StrokePath(
    val color: Long,
    val points: MutableList<StrokePoint> = mutableListOf(),
) {
    fun copyPath(): StrokePath =
        StrokePath(color = color, points = points.map { it.copy() }.toMutableList())
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val z: Float,
)
