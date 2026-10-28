package com.example.showme3d.studio.aigc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class SubjectCutoutMode {
    LOCAL_U2NET_SUBJECT,
    EXISTING_ALPHA,
}

data class SubjectCutoutOptions(
    val maxLongEdge: Int = 1536,
    val backgroundTolerance: Int = 34,
    val cropPaddingPx: Int = 24,
    val minBackgroundCoverage: Float = 0.05f,
    val minForegroundCoverage: Float = 0.03f,
    val maxForegroundCoverage: Float = 0.96f,
)

data class SubjectCutoutResult(
    val sourceFile: File,
    val outputFile: File,
    val mode: SubjectCutoutMode,
    val foregroundCoverage: Float,
    val elapsedMs: Long,
) {
    val usedPreprocessedImage: Boolean
        get() = outputFile != sourceFile

    val summary: String
        get() = when (mode) {
            SubjectCutoutMode.LOCAL_U2NET_SUBJECT ->
                "已用包内 U²-Net 主体模型生成透明 PNG（主体占比 ${(foregroundCoverage * 100).toInt()}%，${elapsedMs}ms）"
            SubjectCutoutMode.EXISTING_ALPHA ->
                "参考图已有透明通道，已规范为 PNG（主体占比 ${(foregroundCoverage * 100).toInt()}%，${elapsedMs}ms）"
        }
}

object SubjectCutoutProcessor {
    fun reusableTransparentSubjectImage(context: Context, inputFile: File): File? {
        if (!inputFile.isFile || !inputFile.canRead()) return null
        return listOf("alpha", "u2net")
            .asSequence()
            .map { suffix -> cutoutTarget(context, inputFile, suffix) }
            .firstOrNull { target -> target.isFile && target.canRead() && target.length() > 0L }
    }

    fun createTransparentSubjectImage(
        context: Context,
        inputFile: File,
        options: SubjectCutoutOptions = SubjectCutoutOptions(),
    ): Result<SubjectCutoutResult> = runCatching {
        require(inputFile.isFile && inputFile.canRead()) { "无法读取图片：${inputFile.name}" }
        val started = System.nanoTime()
        val bitmap = decodeBitmap(inputFile, options.maxLongEdge)
        try {
            val width = bitmap.width
            val height = bitmap.height
            require(width > 0 && height > 0) { "图片尺寸非法" }

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val alphaBounds = SubjectCutoutAlgorithm.alphaForegroundBounds(pixels, width, height)
            if (alphaBounds != null && SubjectCutoutAlgorithm.hasMeaningfulTransparency(pixels)) {
                val target = cutoutTarget(context, inputFile, "alpha")
                writeCroppedPng(
                    pixels = pixels,
                    width = width,
                    height = height,
                    bounds = alphaBounds.padded(options.cropPaddingPx, width, height),
                    target = target,
                )
                return@runCatching SubjectCutoutResult(
                    sourceFile = inputFile,
                    outputFile = target,
                    mode = SubjectCutoutMode.EXISTING_ALPHA,
                    foregroundCoverage = alphaBounds.areaCoverage(width, height),
                    elapsedMs = elapsedMillis(started),
                )
            }

            runLocalU2NetSubjectSegmentation(context, inputFile, bitmap, options, started)
        } finally {
            bitmap.recycle()
        }
    }

    private fun runLocalU2NetSubjectSegmentation(
        context: Context,
        inputFile: File,
        bitmap: Bitmap,
        options: SubjectCutoutOptions,
        started: Long,
    ): SubjectCutoutResult {
        Log.i(TAG, "Start local U2Net subject segmentation for ${inputFile.name}")
        val input = createU2NetInput(bitmap)
        val output = Array(1) { Array(1) { Array(LOCAL_MODEL_INPUT_SIZE) { FloatArray(LOCAL_MODEL_INPUT_SIZE) } } }
        synchronized(u2NetLock) {
            getU2NetInterpreter(context.applicationContext).run(input, output)
        }

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val boundsAndCoverage = applyU2NetMask(
            pixels = pixels,
            width = bitmap.width,
            height = bitmap.height,
            mask = output[0][0],
        ) ?: error("包内 U²-Net 未识别到稳定主体")
        val foregroundCoverage = boundsAndCoverage.foregroundCoverage
        if (
            foregroundCoverage < options.minForegroundCoverage ||
            foregroundCoverage > options.maxForegroundCoverage
        ) {
            error("包内 U²-Net 主体占比异常 ${(foregroundCoverage * 100).toInt()}%")
        }

        val target = cutoutTarget(context, inputFile, "u2net")
        writeCroppedPng(
            pixels = pixels,
            width = bitmap.width,
            height = bitmap.height,
            bounds = boundsAndCoverage.bounds.padded(options.cropPaddingPx, bitmap.width, bitmap.height),
            target = target,
        )
        val cutout = SubjectCutoutResult(
            sourceFile = inputFile,
            outputFile = target,
            mode = SubjectCutoutMode.LOCAL_U2NET_SUBJECT,
            foregroundCoverage = foregroundCoverage,
            elapsedMs = elapsedMillis(started),
        )
        Log.i(TAG, "Local U2Net subject segmentation succeeded for ${inputFile.name} in ${cutout.elapsedMs}ms")
        return cutout
    }

    private fun createU2NetInput(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val scaled = Bitmap.createScaledBitmap(bitmap, LOCAL_MODEL_INPUT_SIZE, LOCAL_MODEL_INPUT_SIZE, true)
        try {
            val scaledPixels = IntArray(LOCAL_MODEL_INPUT_SIZE * LOCAL_MODEL_INPUT_SIZE)
            scaled.getPixels(scaledPixels, 0, LOCAL_MODEL_INPUT_SIZE, 0, 0, LOCAL_MODEL_INPUT_SIZE, LOCAL_MODEL_INPUT_SIZE)
            val maxChannelValue = scaledPixels.maxOf {
                max(redOfArgb(it), max(greenOfArgb(it), blueOfArgb(it)))
            }.coerceAtLeast(1).toFloat()
            val input = Array(1) { Array(3) { Array(LOCAL_MODEL_INPUT_SIZE) { FloatArray(LOCAL_MODEL_INPUT_SIZE) } } }
            for (y in 0 until LOCAL_MODEL_INPUT_SIZE) {
                for (x in 0 until LOCAL_MODEL_INPUT_SIZE) {
                    val color = scaledPixels[y * LOCAL_MODEL_INPUT_SIZE + x]
                    input[0][0][y][x] = normalizeU2NetChannel(redOfArgb(color), maxChannelValue, U2NET_MEAN_R, U2NET_STD_R)
                    input[0][1][y][x] = normalizeU2NetChannel(greenOfArgb(color), maxChannelValue, U2NET_MEAN_G, U2NET_STD_G)
                    input[0][2][y][x] = normalizeU2NetChannel(blueOfArgb(color), maxChannelValue, U2NET_MEAN_B, U2NET_STD_B)
                }
            }
            return input
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun normalizeU2NetChannel(value: Int, maxChannelValue: Float, mean: Float, std: Float): Float =
        ((value.toFloat() / maxChannelValue) - mean) / std

    private fun applyU2NetMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        mask: Array<FloatArray>,
    ): LocalMaskBounds? {
        var maskMin = Float.POSITIVE_INFINITY
        var maskMax = Float.NEGATIVE_INFINITY
        for (row in mask) {
            for (value in row) {
                val clamped = value.coerceIn(0f, 1f)
                if (clamped < maskMin) maskMin = clamped
                if (clamped > maskMax) maskMax = clamped
            }
        }
        if (!maskMin.isFinite() || !maskMax.isFinite()) return null
        val maskRange = maskMax - maskMin

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var foregroundCount = 0
        val xScale = if (width <= 1) 0f else (LOCAL_MODEL_INPUT_SIZE - 1).toFloat() / (width - 1).toFloat()
        val yScale = if (height <= 1) 0f else (LOCAL_MODEL_INPUT_SIZE - 1).toFloat() / (height - 1).toFloat()
        for (y in 0 until height) {
            val sampleY = y * yScale
            for (x in 0 until width) {
                val sampleX = x * xScale
                val rawMask = sampleMaskBilinear(mask, sampleX, sampleY)
                val normalizedMask = if (maskRange > U2NET_MIN_MASK_RANGE) {
                    ((rawMask.coerceIn(0f, 1f) - maskMin) / maskRange).coerceIn(0f, 1f)
                } else {
                    rawMask.coerceIn(0f, 1f)
                }
                val alpha = alphaFromU2NetMask(normalizedMask)
                val index = y * width + x
                pixels[index] = (alpha shl 24) or (pixels[index] and 0x00FFFFFF)
                if (alpha > U2NET_FOREGROUND_ALPHA_THRESHOLD) {
                    foregroundCount += 1
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        return LocalMaskBounds(
            bounds = PixelBounds(left, top, right, bottom),
            foregroundCoverage = foregroundCount.toFloat() / pixels.size.toFloat(),
        )
    }

    private fun sampleMaskBilinear(mask: Array<FloatArray>, x: Float, y: Float): Float {
        val x0 = x.toInt().coerceIn(0, LOCAL_MODEL_INPUT_SIZE - 1)
        val y0 = y.toInt().coerceIn(0, LOCAL_MODEL_INPUT_SIZE - 1)
        val x1 = (x0 + 1).coerceAtMost(LOCAL_MODEL_INPUT_SIZE - 1)
        val y1 = (y0 + 1).coerceAtMost(LOCAL_MODEL_INPUT_SIZE - 1)
        val dx = x - x0
        val dy = y - y0
        val top = lerp(mask[y0][x0], mask[y0][x1], dx)
        val bottom = lerp(mask[y1][x0], mask[y1][x1], dx)
        return lerp(top, bottom, dy)
    }

    private fun alphaFromU2NetMask(maskValue: Float): Int {
        val normalized = ((maskValue - U2NET_ALPHA_LOW_CUT) / (U2NET_ALPHA_HIGH_CUT - U2NET_ALPHA_LOW_CUT))
            .coerceIn(0f, 1f)
        val smoothed = normalized * normalized * (3f - 2f * normalized)
        val alpha = (smoothed * 255f).roundToInt().coerceIn(0, 255)
        return if (alpha <= U2NET_ALPHA_ZERO_THRESHOLD) 0 else alpha
    }

    private fun lerp(from: Float, to: Float, t: Float): Float =
        from + ((to - from) * t.coerceIn(0f, 1f))

    private fun getU2NetInterpreter(context: Context): Interpreter {
        u2NetInterpreter?.let { return it }
        val model = loadAssetModel(context, LOCAL_MODEL_ASSET)
        u2NetModelBuffer = model
        return Interpreter(
            model,
            Interpreter.Options().apply {
                setNumThreads(LOCAL_MODEL_THREADS)
            },
        ).also {
            u2NetInterpreter = it
        }
    }

    private fun loadAssetModel(context: Context, assetPath: String): ByteBuffer {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }

    private fun redOfArgb(color: Int): Int = (color ushr 16) and 0xFF
    private fun greenOfArgb(color: Int): Int = (color ushr 8) and 0xFF
    private fun blueOfArgb(color: Int): Int = color and 0xFF

    private fun decodeBitmap(file: File, maxLongEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片" }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxLongEdge)
        }
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath, decodeOptions)) {
            "无法解码图片"
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxLongEdge: Int): Int {
        var sampleSize = 1
        val longEdge = max(width, height)
        while (longEdge / sampleSize > maxLongEdge) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun writeCroppedPng(
        pixels: IntArray,
        width: Int,
        height: Int,
        bounds: PixelBounds,
        target: File,
    ) {
        val cropWidth = bounds.right - bounds.left + 1
        val cropHeight = bounds.bottom - bounds.top + 1
        require(cropWidth > 0 && cropHeight > 0) { "主体裁剪区域非法" }

        val cropped = IntArray(cropWidth * cropHeight)
        for (row in 0 until cropHeight) {
            System.arraycopy(
                pixels,
                (bounds.top + row) * width + bounds.left,
                cropped,
                row * cropWidth,
                cropWidth,
            )
        }
        val output = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        try {
            output.setPixels(cropped, 0, cropWidth, 0, 0, cropWidth, cropHeight)
            target.parentFile?.mkdirs()
            target.outputStream().use { stream ->
                require(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 写入失败" }
            }
        } finally {
            output.recycle()
        }
    }

    private fun cutoutTarget(context: Context, inputFile: File, suffix: String): File {
        val safeName = inputFile.nameWithoutExtension
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(40)
            .ifBlank { "image" }
        return File(AigcLocalLibrary.preprocessedImageDirectory(context), "${safeName}_${stableInputKey(inputFile)}_$suffix.png")
    }

    private fun stableInputKey(inputFile: File): String {
        val path = runCatching { inputFile.canonicalPath }.getOrElse { inputFile.absolutePath }
        val identity = buildString {
            append(path)
            append('\n')
            append(inputFile.length())
            append('\n')
            append(inputFile.lastModified())
        }.toByteArray(Charsets.UTF_8)
        return MessageDigest
            .getInstance("SHA-256")
            .digest(identity)
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
            .take(16)
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L

    private data class LocalMaskBounds(
        val bounds: PixelBounds,
        val foregroundCoverage: Float,
    )

    private val u2NetLock = Any()
    private var u2NetInterpreter: Interpreter? = null
    private var u2NetModelBuffer: ByteBuffer? = null

    private const val TAG = "ShowMe3D.Cutout"
    private const val LOCAL_MODEL_ASSET = "ml/u2net_fp16.tflite"
    private const val LOCAL_MODEL_INPUT_SIZE = 320
    private const val LOCAL_MODEL_THREADS = 4
    private const val U2NET_MEAN_R = 0.485f
    private const val U2NET_MEAN_G = 0.456f
    private const val U2NET_MEAN_B = 0.406f
    private const val U2NET_STD_R = 0.229f
    private const val U2NET_STD_G = 0.224f
    private const val U2NET_STD_B = 0.225f
    private const val U2NET_MIN_MASK_RANGE = 0.04f
    private const val U2NET_ALPHA_LOW_CUT = 0.18f
    private const val U2NET_ALPHA_HIGH_CUT = 0.86f
    private const val U2NET_ALPHA_ZERO_THRESHOLD = 5
    private const val U2NET_FOREGROUND_ALPHA_THRESHOLD = 32
}

internal data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun padded(padding: Int, width: Int, height: Int): PixelBounds =
        PixelBounds(
            left = (left - padding).coerceAtLeast(0),
            top = (top - padding).coerceAtLeast(0),
            right = (right + padding).coerceAtMost(width - 1),
            bottom = (bottom + padding).coerceAtMost(height - 1),
        )

    fun areaCoverage(width: Int, height: Int): Float =
        ((right - left + 1) * (bottom - top + 1)).toFloat() / (width * height).toFloat()
}

internal data class SubjectMask(
    val background: BooleanArray,
    val foregroundBounds: PixelBounds?,
    val backgroundCoverage: Float,
    val foregroundCoverage: Float,
)

internal object SubjectCutoutAlgorithm {
    fun hasMeaningfulTransparency(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        val transparentCount = pixels.count { alphaOf(it) < EXISTING_ALPHA_THRESHOLD }
        return transparentCount.toFloat() / pixels.size.toFloat() >= MIN_EXISTING_TRANSPARENT_COVERAGE
    }

    fun alphaForegroundBounds(pixels: IntArray, width: Int, height: Int): PixelBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (alphaOf(color) <= FOREGROUND_ALPHA_THRESHOLD) return@forEachIndexed
            val x = index % width
            val y = index / width
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        return if (right >= left && bottom >= top) PixelBounds(left, top, right, bottom) else null
    }

    fun buildEdgeBackgroundMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        tolerance: Int,
    ): SubjectMask {
        require(width > 0 && height > 0) { "Image size must be positive" }
        require(pixels.size == width * height) { "Pixel count does not match image size" }
        val total = pixels.size
        val background = BooleanArray(total)
        val queue = IntArray(total)
        var tail = 0
        val backgroundRgb = estimateCornerBackgroundRgb(pixels, width, height)

        fun tryEnqueue(index: Int) {
            if (background[index]) return
            if (!looksLikeBackground(pixels[index], backgroundRgb, tolerance)) return
            background[index] = true
            queue[tail] = index
            tail += 1
        }

        for (x in 0 until width) {
            tryEnqueue(x)
            tryEnqueue((height - 1) * width + x)
        }
        for (y in 0 until height) {
            tryEnqueue(y * width)
            tryEnqueue(y * width + width - 1)
        }

        var head = 0
        while (head < tail) {
            val index = queue[head]
            head += 1
            val x = index % width
            val y = index / width
            if (x > 0) tryEnqueue(index - 1)
            if (x < width - 1) tryEnqueue(index + 1)
            if (y > 0) tryEnqueue(index - width)
            if (y < height - 1) tryEnqueue(index + width)
        }

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        var foregroundCount = 0
        background.forEachIndexed { index, isBackground ->
            if (isBackground) return@forEachIndexed
            foregroundCount += 1
            val x = index % width
            val y = index / width
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }

        val bounds = if (right >= left && bottom >= top) PixelBounds(left, top, right, bottom) else null
        val backgroundCoverage = tail.toFloat() / total.toFloat()
        val foregroundCoverage = foregroundCount.toFloat() / total.toFloat()
        return SubjectMask(
            background = background,
            foregroundBounds = bounds,
            backgroundCoverage = backgroundCoverage,
            foregroundCoverage = foregroundCoverage,
        )
    }

    private fun estimateCornerBackgroundRgb(pixels: IntArray, width: Int, height: Int): Rgb {
        val sampleSide = min(max(min(width, height) / 24, 2), 12)
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L

        fun sampleArea(startX: Int, startY: Int) {
            for (y in startY until (startY + sampleSide).coerceAtMost(height)) {
                for (x in startX until (startX + sampleSide).coerceAtMost(width)) {
                    val color = pixels[y * width + x]
                    r += redOf(color)
                    g += greenOf(color)
                    b += blueOf(color)
                    count += 1
                }
            }
        }

        sampleArea(0, 0)
        sampleArea((width - sampleSide).coerceAtLeast(0), 0)
        sampleArea(0, (height - sampleSide).coerceAtLeast(0))
        sampleArea((width - sampleSide).coerceAtLeast(0), (height - sampleSide).coerceAtLeast(0))

        return Rgb(
            r = (r / count).toInt(),
            g = (g / count).toInt(),
            b = (b / count).toInt(),
        )
    }

    private fun looksLikeBackground(color: Int, backgroundRgb: Rgb, tolerance: Int): Boolean {
        if (alphaOf(color) <= FOREGROUND_ALPHA_THRESHOLD) return true
        return rgbDistance(color, backgroundRgb) <= tolerance
    }

    private fun rgbDistance(color: Int, backgroundRgb: Rgb): Float {
        val dr = redOf(color) - backgroundRgb.r
        val dg = greenOf(color) - backgroundRgb.g
        val db = blueOf(color) - backgroundRgb.b
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF
    private fun redOf(color: Int): Int = (color ushr 16) and 0xFF
    private fun greenOf(color: Int): Int = (color ushr 8) and 0xFF
    private fun blueOf(color: Int): Int = color and 0xFF

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    private const val EXISTING_ALPHA_THRESHOLD = 250
    private const val FOREGROUND_ALPHA_THRESHOLD = 16
    private const val MIN_EXISTING_TRANSPARENT_COVERAGE = 0.005f
}
