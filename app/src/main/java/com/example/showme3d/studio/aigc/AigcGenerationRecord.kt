package com.example.showme3d.studio.aigc

enum class AigcGenerationRecordType(
    val displayName: String,
) {
    TEXT_TO_IMAGE("文生图"),
    TEXT_TO_MODEL("文生 3D"),
    IMAGE_TO_IMAGE("图生图"),
    IMAGE_TO_MODEL("图生 3D"),
    IMAGE_PREPROCESS("图片前处理"),
    STROKE_PREVIEW("三视图预览"),
    STROKE_TO_IMAGE("笔画成图"),
    STROKE_TO_MODEL("笔画生成 3D"),
}

enum class AigcGenerationRecordStatus(
    val displayName: String,
) {
    RUNNING("生成中"),
    SUCCEEDED("成功"),
    FAILED("失败"),
}

data class AigcGenerationRecord(
    val id: String,
    val type: AigcGenerationRecordType,
    val title: String,
    val status: AigcGenerationRecordStatus,
    val stage: String,
    val sourceName: String = "",
    val prompt: String = "",
    val inputSources: List<String> = emptyList(),
    val outputImageSource: String? = null,
    val outputModelSource: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
