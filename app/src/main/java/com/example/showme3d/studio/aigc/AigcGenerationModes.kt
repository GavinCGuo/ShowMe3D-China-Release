package com.example.showme3d.studio.aigc

enum class AigcOutputMode(
    val displayName: String,
) {
    MODEL("生成 3D"),
    IMAGE("生成图片"),
}

enum class AigcImagePreprocessMode(
    val displayName: String,
) {
    NONE("原图直送"),
    LOCAL_SUBJECT("本地抠图"),
    SEEDREAM_CLEAN("AI 清理"),
}
