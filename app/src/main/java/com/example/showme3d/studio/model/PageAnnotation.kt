package com.example.showme3d.studio.model

data class PageAnnotation(
    val id: String,
    val type: PageAnnotationType,
    var strokeData: StrokeData? = null,
    var commentText: String? = null,
    var anchorPoint: StrokePoint? = null,
    var panelPoint: StrokePoint? = null,
) {
    fun copyAnnotation(): PageAnnotation =
        copy(
            strokeData = strokeData?.copyData(),
            anchorPoint = anchorPoint?.copy(),
            panelPoint = panelPoint?.copy(),
        )
}

enum class PageAnnotationType {
    STROKE,
    COMMENT,
}
