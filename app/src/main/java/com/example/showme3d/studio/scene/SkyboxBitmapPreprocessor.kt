package com.example.showme3d.studio.scene

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Softens only the wrap boundary of panoramas whose first and last columns do
 * not match. Properly authored seamless panoramas are returned unchanged.
 */
internal fun Bitmap.withSeamlessHorizontalWrap(): Bitmap {
    if (width < MIN_WIDTH || height < MIN_HEIGHT || horizontalEdgeDifference() <= EDGE_DIFFERENCE_THRESHOLD) {
        return this
    }
    val result = if (isMutable && config == Bitmap.Config.ARGB_8888) {
        this
    } else {
        copy(Bitmap.Config.ARGB_8888, true) ?: return this
    }
    val row = IntArray(width)
    val featherWidth = (width / FEATHER_WIDTH_DIVISOR).coerceIn(MIN_FEATHER_WIDTH, width / 4)

    for (y in 0 until height) {
        result.getPixels(row, 0, width, 0, y, width, 1)
        for (offset in 0 until featherWidth) {
            val leftIndex = offset
            val rightIndex = width - 1 - offset
            val left = row[leftIndex]
            val right = row[rightIndex]
            val edgeColor = blendArgb(left, right, 0.5f)
            val progress = if (featherWidth == 1) 1f else offset.toFloat() / (featherWidth - 1)
            val eased = progress * progress * (3f - 2f * progress)
            row[leftIndex] = blendArgb(edgeColor, left, eased)
            row[rightIndex] = blendArgb(edgeColor, right, eased)
        }
        result.setPixels(row, 0, width, 0, y, width, 1)
    }
    return result
}

private fun Bitmap.horizontalEdgeDifference(): Float {
    val stride = (height / EDGE_SAMPLE_COUNT).coerceAtLeast(1)
    var total = 0L
    var samples = 0
    var y = 0
    while (y < height) {
        val left = getPixel(0, y)
        val right = getPixel(width - 1, y)
        total += abs(channel(left, 16) - channel(right, 16))
        total += abs(channel(left, 8) - channel(right, 8))
        total += abs(channel(left, 0) - channel(right, 0))
        samples += 3
        y += stride
    }
    return if (samples == 0) 0f else total.toFloat() / samples
}

private fun blendArgb(from: Int, to: Int, progress: Float): Int {
    val t = progress.coerceIn(0f, 1f)
    fun blendChannel(shift: Int): Int =
        (channel(from, shift) + (channel(to, shift) - channel(from, shift)) * t)
            .toInt()
            .coerceIn(0, 255)
    return (blendChannel(24) shl 24) or
        (blendChannel(16) shl 16) or
        (blendChannel(8) shl 8) or
        blendChannel(0)
}

private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

private const val MIN_WIDTH = 64
private const val MIN_HEIGHT = 32
private const val EDGE_SAMPLE_COUNT = 64
private const val EDGE_DIFFERENCE_THRESHOLD = 5f
private const val FEATHER_WIDTH_DIVISOR = 24
private const val MIN_FEATHER_WIDTH = 8
