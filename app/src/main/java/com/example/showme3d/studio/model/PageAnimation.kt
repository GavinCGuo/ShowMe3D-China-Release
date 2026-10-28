package com.example.showme3d.studio.model

data class PageAnimation(
    val start: TransformData,
    val end: TransformData,
    val durationSeconds: Float = DEFAULT_DURATION_SECONDS,
    val easing: AnimationEasing = AnimationEasing.EASE_IN_OUT,
    val loopMode: AnimationLoopMode = AnimationLoopMode.PING_PONG,
) {
    companion object {
        const val DEFAULT_DURATION_SECONDS = 2f
        const val MIN_DURATION_SECONDS = 0.5f
        const val MAX_DURATION_SECONDS = 12f
    }
}

enum class AnimationEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
}

enum class AnimationLoopMode {
    NONE,
    PING_PONG,
    RESTART,
}
