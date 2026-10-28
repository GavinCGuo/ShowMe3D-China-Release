package com.example.showme3d.studio.interaction

import com.example.showme3d.studio.model.TransformData

internal class PresentationDoubleTapTracker(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private var lastObjectId: String? = null
    private var lastTapMillis: Long = Long.MIN_VALUE

    fun register(objectId: String, nowMillis: Long): Boolean {
        val elapsed = nowMillis - lastTapMillis
        val matched = objectId == lastObjectId && elapsed in 0..windowMillis
        if (matched) {
            reset()
            return true
        }
        lastObjectId = objectId
        lastTapMillis = nowMillis
        return false
    }

    fun reset() {
        lastObjectId = null
        lastTapMillis = Long.MIN_VALUE
    }

    private companion object {
        const val DEFAULT_WINDOW_MILLIS = 450L
    }
}

internal data class PresentationResetMotion(
    val from: TransformData,
    val to: TransformData,
    val startedAtNanos: Long,
    val durationNanos: Long = DEFAULT_DURATION_NANOS,
) {
    fun transformAt(nowNanos: Long): TransformData {
        val raw = ((nowNanos - startedAtNanos).toDouble() / durationNanos.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        val eased = 1f - (1f - raw) * (1f - raw)
        return lerpTransform(from, to, eased)
    }

    fun isComplete(nowNanos: Long): Boolean = nowNanos - startedAtNanos >= durationNanos

    private companion object {
        const val DEFAULT_DURATION_NANOS = 220_000_000L
    }
}

private fun lerpTransform(from: TransformData, to: TransformData, t: Float): TransformData =
    TransformData(
        px = from.px + (to.px - from.px) * t,
        py = from.py + (to.py - from.py) * t,
        pz = from.pz + (to.pz - from.pz) * t,
        rotationY = from.rotationY + (to.rotationY - from.rotationY) * t,
        scale = from.scale + (to.scale - from.scale) * t,
    )
