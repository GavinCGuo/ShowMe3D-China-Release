package com.example.showme3d.studio.aigc

import com.example.showme3d.release.ReleasePolicy
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.model.ObjectType
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class SeedreamImageOptions(
    val size: String = "2K",
    val watermark: Boolean = false,
)

object SeedreamImageClient {
    fun generateTextToImage(
        context: Context,
        apiKey: String,
        prompt: String,
        options: SeedreamImageOptions = SeedreamImageOptions(),
    ): Result<ImportedAsset> = runCatching {
        require(prompt.isNotBlank()) { "请输入图片生成描述" }
        val file = requestAndDownloadImage(
            apiKey = apiKey,
            prompt = prompt.trim(),
            imageInputs = emptyList(),
            targetDirectory = AigcLocalLibrary.imageDirectory(context),
            displayNameSeed = prompt,
            options = options,
        )
        ImportedAsset(
            displayName = prompt.take(24).ifBlank { "Seedream Image" },
            type = ObjectType.IMAGE,
            source = "file://${file.absolutePath}",
        )
    }

    fun generateImageToImage(
        context: Context,
        apiKey: String,
        imageFiles: List<File>,
        prompt: String,
        options: SeedreamImageOptions = SeedreamImageOptions(),
    ): Result<ImportedAsset> = runCatching {
        require(imageFiles.isNotEmpty()) { "请先选择参考图片" }
        require(prompt.isNotBlank()) { "请输入图片生成描述" }
        val inputs = imageFiles.map { it.toDataUrl() }
        val file = requestAndDownloadImage(
            apiKey = apiKey,
            prompt = prompt.trim(),
            imageInputs = inputs,
            targetDirectory = AigcLocalLibrary.imageDirectory(context),
            displayNameSeed = prompt,
            options = options,
        )
        ImportedAsset(
            displayName = prompt.take(24).ifBlank { "Seedream Image" },
            type = ObjectType.IMAGE,
            source = "file://${file.absolutePath}",
        )
    }

    fun cleanImageFor3d(
        context: Context,
        apiKey: String,
        imageFile: File,
        extraPrompt: String = "",
        options: SeedreamImageOptions = SeedreamImageOptions(),
    ): Result<File> = runCatching {
        require(imageFile.isFile && imageFile.canRead()) { "无法读取待清理图片：${imageFile.name}" }
        requestAndDownloadImage(
            apiKey = apiKey,
            prompt = cleanImagePrompt(extraPrompt),
            imageInputs = listOf(imageFile.toDataUrl()),
            targetDirectory = AigcLocalLibrary.preprocessedImageDirectory(context),
            displayNameSeed = "${imageFile.nameWithoutExtension}_seedream_clean",
            options = options,
        )
    }

    internal fun cleanImagePrompt(extraPrompt: String): String {
        val extra = extraPrompt.trim().ifBlank { "无" }
        return """
            这是图生 3D 前处理任务，不是原图复刻任务。输入图只是主体参考，请不要原样返回输入图。
            请保留输入图中最主要的一个主体，保持主体身份、整体轮廓、姿态、主要颜色和材质特征；去除或弱化背景、杂物、阴影、文字、边框、水印和多余物体。
            如果主体被手、工具、前景物、遮挡物或其他杂物遮挡，请移除遮挡物，并根据主体已有结构、材质和透视关系合理补全被遮挡的主体部分。
            输出一张全新的单主体干净图：主体完整、居中、边缘清晰、背景纯净，适合后续 AIGC 3D。不要裁切主体、不要新增主体、不要改变主体类别、不要复杂背景、不要高光、不要强反光。
            额外要求为：$extra
        """.trimIndent()
    }

    internal fun strokeMainViewPrompt(extraPrompt: String): String {
        val extra = extraPrompt.trim().ifBlank { "无" }
        return """
            参考图是用户从当前观察方向画出的主视图草图。请把它发展成一张完成度高、具有清晰体积感的图片，供后续图生 3D 使用。
            尊重草图的主体数量、主轮廓、核心构成、朝向和最有辨识度的特征，但不需要逐线复刻。可以主动理解用户意图，自由修正抖动、断线、比例和透视，补全合理结构，并在不改变核心形态的前提下大胆丰富材质、色彩、细节与风格，让结果自然、完整且有设计感。线条颜色可作为灵感，不是硬性限制。
            如果草图语义含糊，请选择视觉上最合理、最有表现力的具体形态继续创作，不要把局部线条机械识别成文字或符号。额外提示若存在，优先用于理解主体和创作方向；没有额外提示时，直接依据草图发挥，不要向用户追问。
            最终只输出一张完整主体图片，构图清楚、背景简洁，不出现说明文字、标注、边框或水印。
            额外创作方向：$extra
        """.trimIndent()
    }

    internal fun buildRequestJson(
        prompt: String,
        imageInputs: List<String>,
        options: SeedreamImageOptions = SeedreamImageOptions(),
    ): JSONObject =
        JSONObject()
            .put("model", MODEL)
            .put("prompt", prompt)
            .put("sequential_image_generation", "disabled")
            .put("response_format", "url")
            .put("size", options.size)
            .put("stream", false)
            .put("watermark", options.watermark)
            .also { body ->
                if (imageInputs.size == 1) {
                    body.put("image", imageInputs.single())
                } else if (imageInputs.size > 1) {
                    body.put("image", JSONArray(imageInputs))
                }
            }

    private fun requestAndDownloadImage(
        apiKey: String,
        prompt: String,
        imageInputs: List<String>,
        targetDirectory: File,
        displayNameSeed: String,
        options: SeedreamImageOptions,
    ): File {
        ReleasePolicy.requireCloudAi()
        require(apiKey.isNotBlank()) { "请输入图生图 Key" }
        logSeedream("request start imageInputs=${imageInputs.size} promptLength=${prompt.length} size=${options.size}")
        val response = requestJson(
            apiKey = apiKey.trim(),
            body = buildRequestJson(
                prompt = prompt,
                imageInputs = imageInputs,
                options = options,
            ),
        )
        val imagePayload = response.findImagePayload()
            ?: error("Seedream 没有返回可下载图片")
        logSeedream("request completed payload=${imagePayload.logName}")
        return downloadImage(
            payload = imagePayload,
            targetDirectory = targetDirectory,
            displayNameSeed = displayNameSeed,
        ).also { file ->
            logSeedream("download completed bytes=${file.length()} extension=${file.extension}")
        }
    }

    private fun requestJson(apiKey: String, body: JSONObject): JSONObject {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            return connection.readJsonResponse()
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readJsonResponse(): JSONObject {
        val responseCode = responseCode
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            error("Seedream HTTP $responseCode: ${text.take(180)}")
        }
        if (text.isBlank()) error("Seedream 返回空响应")
        val json = JSONObject(text)
        val errorMessage = json.optString("error")
            .ifBlank { json.optString("message") }
            .ifBlank { json.optString("detail") }
        if (errorMessage.isNotBlank() && json.optJSONArray("data") == null) {
            error("Seedream 返回错误：$errorMessage")
        }
        return json
    }

    private fun JSONObject.findImagePayload(): ImagePayload? {
        val data = optJSONArray("data")
        if (data != null) {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                item.optString("url")
                    .takeIf { it.isNotBlank() }
                    ?.let { return ImagePayload.Url(it) }
                item.optString("image_url")
                    .takeIf { it.isNotBlank() }
                    ?.let { return ImagePayload.Url(it) }
                item.optString("b64_json")
                    .takeIf { it.isNotBlank() }
                    ?.let { return ImagePayload.Base64Png(it) }
            }
        }
        optString("url")
            .takeIf { it.isNotBlank() }
            ?.let { return ImagePayload.Url(it) }
        optString("b64_json")
            .takeIf { it.isNotBlank() }
            ?.let { return ImagePayload.Base64Png(it) }
        return null
    }

    private fun downloadImage(
        payload: ImagePayload,
        targetDirectory: File,
        displayNameSeed: String,
    ): File {
        targetDirectory.mkdirs()
        return when (payload) {
            is ImagePayload.Base64Png -> {
                val bytes = java.util.Base64.getDecoder().decode(payload.value)
                require(bytes.size <= MAX_IMAGE_BYTES) { "生成图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024} MB" }
                val target = newImageTarget(targetDirectory, displayNameSeed, "png")
                target.writeBytes(bytes)
                target.ensureDecodableImage()
                target
            }
            is ImagePayload.Url -> {
                if (payload.value.startsWith("data:image/")) {
                    return saveDataUrlImage(payload.value, targetDirectory, displayNameSeed)
                }
                downloadUrlImageWithRetry(payload.value, targetDirectory, displayNameSeed)
            }
        }
    }

    private fun downloadUrlImageWithRetry(
        imageUrl: String,
        targetDirectory: File,
        displayNameSeed: String,
    ): File {
        var lastNetworkError: IOException? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { zeroBasedAttempt ->
            val attempt = zeroBasedAttempt + 1
            val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_TIMEOUT_MS
                setRequestProperty("Accept", "image/*,*/*")
            }
            var target: File? = null
            try {
                if (connection.responseCode !in 200..299) {
                    error("图片下载失败：HTTP ${connection.responseCode}")
                }
                val extension = connection.contentType?.imageExtension()
                    ?: imageUrl.imageExtensionFromUrl()
                    ?: "png"
                target = newImageTarget(targetDirectory, displayNameSeed, extension)
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        input.copyToWithLimit(output, MAX_IMAGE_BYTES)
                    }
                }
                target.ensureDecodableImage()
                return target
            } catch (error: IOException) {
                target?.delete()
                lastNetworkError = error
                if (attempt >= MAX_DOWNLOAD_ATTEMPTS) throw error
                logSeedream("download network failure; retrying attempt=${attempt + 1}/$MAX_DOWNLOAD_ATTEMPTS")
            } finally {
                connection.disconnect()
            }
        }
        throw requireNotNull(lastNetworkError)
    }

    private fun saveDataUrlImage(dataUrl: String, targetDirectory: File, displayNameSeed: String): File {
        val header = dataUrl.substringBefore(',', missingDelimiterValue = "")
        val payload = dataUrl.substringAfter(',', missingDelimiterValue = "")
        require(payload.isNotBlank()) { "Seedream 返回的 data URL 无图片内容" }
        val extension = header.substringAfter("data:image/", "png")
            .substringBefore(';')
            .lowercase(Locale.US)
            .let { if (it == "jpeg") "jpg" else it }
            .takeIf { it in SUPPORTED_IMAGE_EXTENSIONS }
            ?: "png"
        val bytes = java.util.Base64.getDecoder().decode(payload)
        require(bytes.size <= MAX_IMAGE_BYTES) { "生成图片超过 ${MAX_IMAGE_BYTES / 1024 / 1024} MB" }
        val target = newImageTarget(targetDirectory, displayNameSeed, extension)
        target.writeBytes(bytes)
        target.ensureDecodableImage()
        return target
    }

    private fun File.toDataUrl(): String {
        require(isFile && canRead()) { "无法读取参考图片：$name" }
        require(length() <= MAX_INPUT_IMAGE_BYTES) { "参考图片超过 ${MAX_INPUT_IMAGE_BYTES / 1024 / 1024} MB" }
        val extension = extension.lowercase(Locale.US)
        require(extension in SUPPORTED_IMAGE_EXTENSIONS) { "仅支持 PNG / JPG / WebP 参考图" }
        val bytes = readBytes()
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        return "data:${imageContentType()};base64,$encoded"
    }

    private fun newImageTarget(targetDirectory: File, displayNameSeed: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = displayNameSeed
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(32)
            .ifBlank { "seedream" }
        return File(targetDirectory, "${stamp}_${safeName}.${extension.lowercase(Locale.US)}")
    }

    private fun File.ensureDecodableImage() {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Seedream 返回的图片无法解码" }
    }

    private fun File.imageContentType(): String =
        when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }

    private fun String.imageExtension(): String? =
        substringBefore(';')
            .substringAfter("image/", "")
            .lowercase(Locale.US)
            .let { if (it == "jpeg") "jpg" else it }
            .takeIf { it in SUPPORTED_IMAGE_EXTENSIONS }

    private fun String.imageExtensionFromUrl(): String? =
        substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase(Locale.US)
            .let { if (it == "jpeg") "jpg" else it }
            .takeIf { it in SUPPORTED_IMAGE_EXTENSIONS }

    private fun java.io.InputStream.copyToWithLimit(output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            copied += count
            if (copied > maxBytes) {
                throw IllegalArgumentException("生成图片超过 ${maxBytes / 1024 / 1024} MB")
            }
            output.write(buffer, 0, count)
        }
        return copied
    }

    private sealed class ImagePayload {
        data class Url(val value: String) : ImagePayload()
        data class Base64Png(val value: String) : ImagePayload()
    }

    private val ImagePayload.logName: String
        get() = when (this) {
            is ImagePayload.Url -> if (value.startsWith("data:image/")) "data_url" else "url"
            is ImagePayload.Base64Png -> "base64"
        }

    private const val API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations"
    private const val MODEL = "doubao-seedream-5-0-260128"
    private const val CLEAN_IMAGE_PROMPT_PREFIX = "生成产物用于后续 3DAIGC，现在将该图转化为底色纯净，主体清晰的图片，额外要求为"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val DOWNLOAD_TIMEOUT_MS = 120_000
    private const val MAX_DOWNLOAD_ATTEMPTS = 2
    private const val MAX_INPUT_IMAGE_BYTES = 25L * 1024L * 1024L
    private const val MAX_IMAGE_BYTES = 80L * 1024L * 1024L
    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
}

private const val SEEDREAM_TAG = "ShowMe3D.Seedream"

private fun logSeedream(message: String) {
    runCatching { Log.w(SEEDREAM_TAG, message) }
}
