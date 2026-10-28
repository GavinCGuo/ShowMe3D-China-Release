package com.example.showme3d.studio.model

/**
 * Stable object definition for a Show. Per-page differences live in [ObjectState],
 * not here, so the same object can tween from page to page.
 */
data class SceneObject(
    val id: String,
    val type: ObjectType,
    var source: String,
    var displayName: String,
    var textColor: Long? = null,
    var strokeData: StrokeData? = null,
    var isLocked: Boolean = false,
    var faceViewer: Boolean = false,
    /** Renders ordinary spatial text on the high-contrast presentation card. */
    var textBackgroundEnabled: Boolean = false,
    /** Controls whether imported geometry is fitted independently or keeps an authored shared frame. */
    var modelOriginMode: ModelOriginMode = ModelOriginMode.CENTER_AND_FIT,
    /** Starts the first embedded skinned-mesh clip whenever this object becomes visible. */
    var playEmbeddedAnimation: Boolean = false,
    var versionHistory: ObjectVersionHistory? = null,
)

enum class ModelOriginMode {
    CENTER_AND_FIT,
    SHARED_ORIGIN,
}

data class ObjectVersionHistory(
    var activeVersionId: String,
    val versions: MutableList<ObjectVersion> = mutableListOf(),
)

data class ObjectVersion(
    val versionId: String,
    val type: ObjectType,
    val source: String,
    val displayName: String,
    val textColor: Long? = null,
    val strokeData: StrokeData? = null,
    val origin: ObjectVersionOrigin = ObjectVersionOrigin.ORIGINAL,
    val recordId: String? = null,
    val prompt: String = "",
    val inputSources: List<String> = emptyList(),
    val createdAt: Long = 0L,
    var preferredTransform: TransformData? = null,
)

enum class ObjectVersionOrigin(
    val displayName: String,
) {
    ORIGINAL("原始版本"),
    TEXT_TO_IMAGE("文本生成图"),
    IMAGE_PREPROCESS_LOCAL_SUBJECT("本地抠图"),
    IMAGE_PREPROCESS_SEEDREAM_CLEAN("AI 抠图"),
    IMAGE_TO_IMAGE("AI 生成图"),
    IMAGE_TO_MODEL("图生 3D"),
    STROKE_PREVIEW("三视图"),
    STROKE_TO_IMAGE("笔画生成图"),
    STROKE_TO_MODEL("笔画生成 3D"),
    UNKNOWN("生成版本"),
}
