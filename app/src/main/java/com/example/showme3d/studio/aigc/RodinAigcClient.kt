package com.example.showme3d.studio.aigc

import com.example.showme3d.release.ReleasePolicy
import android.content.Context
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.model.ObjectType
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class RodinGenerationTier(
    val apiValue: String,
    val displayName: String,
) {
    MINIMUM("Gen-2.5-Minimum", "极速"),
    EXTREME_LOW("Gen-2.5-Extreme-Low", "极低"),
    LOW("Gen-2.5-Low", "低"),
    MEDIUM("Gen-2.5-Medium", "均衡"),
    HIGH("Gen-2.5-High", "高质"),
    EXTREME_HIGH("Gen-2.5-Extreme-High", "极高"),
}

val DEFAULT_RODIN_GENERATION_TIER = RodinGenerationTier.EXTREME_LOW
const val DEFAULT_RODIN_FACE_BUDGET = 20_000

data class RodinGenerationOptions(
    val tier: RodinGenerationTier = DEFAULT_RODIN_GENERATION_TIER,
    val seed: Int? = null,
    val qualityOverride: Int? = DEFAULT_RODIN_FACE_BUDGET,
    val meshMode: String = "Raw",
    val geometryFileFormat: String = "glb",
    val material: String = "Shaded",
    val textureMode: String? = null,
    val taPose: Boolean = false,
    val hdTexture: Boolean = false,
    val modelEarlyExport: Boolean = false,
    val isMicro: Boolean = false,
    val creative: Boolean = false,
    val bboxWidth: Int? = null,
    val bboxHeight: Int? = null,
    val bboxLength: Int? = null,
) {
    val geometryInstructMode: String get() = if (creative) "creative" else "faithful"
}

object RodinAigcClient {
    fun generateTextToModel(
        context: Context,
        apiKey: String,
        prompt: String,
        options: RodinGenerationOptions = RodinGenerationOptions(),
    ): Result<ImportedAsset> = runCatching {
        require(prompt.isNotBlank()) { "请输入生成描述" }
        generateModel(
            context = context,
            apiKey = apiKey,
            prompt = prompt.trim(),
            imageFiles = emptyList(),
            displayNameSeed = prompt,
            options = options,
        )
    }

    fun generateImageToModel(
        context: Context,
        apiKey: String,
        imageFiles: List<File>,
        prompt: String? = null,
        options: RodinGenerationOptions = RodinGenerationOptions(),
    ): Result<ImportedAsset> = runCatching {
        require(imageFiles.isNotEmpty()) { "请先选择参考图片" }
        imageFiles.forEach { file ->
            require(file.isFile && file.canRead()) { "无法读取参考图片：${file.name}" }
        }
        generateModel(
            context = context,
            apiKey = apiKey,
            prompt = prompt?.trim().orEmpty(),
            imageFiles = imageFiles,
            displayNameSeed = prompt?.takeIf { it.isNotBlank() } ?: imageFiles.first().nameWithoutExtension,
            options = options,
        )
    }

    private fun generateModel(
        context: Context,
        apiKey: String,
        prompt: String,
        imageFiles: List<File>,
        displayNameSeed: String,
        options: RodinGenerationOptions,
    ): ImportedAsset {
        val submitted = submitGenerateJob(
            apiKey = apiKey.trim(),
            prompt = prompt,
            imageFiles = imageFiles,
            options = options,
        )
        pollJob(apiKey.trim(), submitted)
        val modelUrl = requestDownloadUrl(
            apiKey = apiKey.trim(),
            taskUuid = submitted.jobUuid,
            preferredFormat = options.geometryFileFormat,
        )
        val target = downloadModel(
            context = context,
            displayNameSeed = displayNameSeed,
            modelUrl = modelUrl,
            preferredFormat = options.geometryFileFormat,
        )
        return ImportedAsset(
            displayName = displayNameSeed.take(24).ifBlank { "Rodin Model" },
            type = ObjectType.MODEL,
            source = "file://${target.absolutePath}",
        )
    }

    private fun submitGenerateJob(
        apiKey: String,
        prompt: String,
        imageFiles: List<File>,
        options: RodinGenerationOptions,
    ): RodinSubmittedJob {
        ReleasePolicy.requireCloudAi()
        require(apiKey.isNotBlank()) { "请输入 Rodin API Key" }
        require(prompt.isNotBlank() || imageFiles.isNotEmpty()) { "请填写描述或选择参考图片" }

        val boundary = "ShowMe3DRodinBoundary-${UUID.randomUUID()}"
        val connection = (URL("$API_BASE/api/v2/rodin").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                imageFiles.forEach { file ->
                    output.writeFileField(boundary, name = "images", file = file)
                }
                if (prompt.isNotBlank()) output.writeFormField(boundary, "prompt", prompt)
                options.seed?.takeIf { it != 0 }?.let { output.writeFormField(boundary, "seed", it.toString()) }
                options.qualityOverride?.let { output.writeFormField(boundary, "quality_override", it.toString()) }
                output.writeFormField(boundary, "mesh_mode", options.meshMode)
                output.writeFormField(boundary, "geometry_file_format", options.geometryFileFormat)
                output.writeFormField(boundary, "material", options.material)
                options.textureMode
                    ?.takeIf { it.isNotBlank() && it != "Default" }
                    ?.let { output.writeFormField(boundary, "texture_mode", it) }
                output.writeFormField(boundary, "tier", options.tier.apiValue)
                output.writeFormField(boundary, "ta_pose", options.taPose.toString())
                output.writeFormField(boundary, "hd_texture", options.hdTexture.toString())
                output.writeFormField(boundary, "model_early_export", options.modelEarlyExport.toString())
                output.writeFormField(boundary, "is_micro", options.isMicro.toString())
                output.writeFormField(boundary, "geometry_instruct_mode", options.geometryInstructMode)
                if (options.bboxWidth != null && options.bboxHeight != null && options.bboxLength != null) {
                    output.writeFormField(
                        boundary,
                        "bbox_condition",
                        "[${options.bboxWidth},${options.bboxHeight},${options.bboxLength}]",
                    )
                }
                output.writeBytes("--$boundary--\r\n")
                output.flush()
            }

            val json = connection.readJsonResponse()
            val jobUuid = json.optString("uuid")
                .ifBlank { json.optString("task_uuid") }
                .ifBlank { error("Rodin 未返回 uuid") }
            val jobs = json.optJSONObject("jobs") ?: JSONObject()
            val subscriptionKey = jobs.optString("subscription_key")
                .ifBlank { json.optString("subscription_key") }
                .ifBlank { error("Rodin 未返回 subscription_key") }
            return RodinSubmittedJob(jobUuid = jobUuid, subscriptionKey = subscriptionKey)
        } finally {
            connection.disconnect()
        }
    }

    private fun pollJob(apiKey: String, submitted: RodinSubmittedJob) {
        repeat(MAX_POLL_ATTEMPTS) {
            Thread.sleep(POLL_INTERVAL_MS)
            val response = requestJson(
                method = "POST",
                url = "$API_BASE/api/v2/status",
                apiKey = apiKey,
                body = JSONObject().put("subscription_key", submitted.subscriptionKey),
            )
            val jobs = response.optJSONArray("jobs")
                ?: error("Rodin 状态响应缺少 jobs")
            if (jobs.length() == 0) error("Rodin 状态响应没有任务")

            val statuses = jobs.statuses()
            if (statuses.any { it.equals("Failed", ignoreCase = true) }) {
                error("Rodin 生成失败：${statuses.joinToString()}")
            }
            if (statuses.all { it.equals("Done", ignoreCase = true) }) {
                return
            }
        }
        error("Rodin 生成超时")
    }

    private fun requestDownloadUrl(apiKey: String, taskUuid: String, preferredFormat: String): String {
        val response = requestJson(
            method = "POST",
            url = "$API_BASE/api/v2/download",
            apiKey = apiKey,
            body = JSONObject().put("task_uuid", taskUuid),
        )
        val list = response.optJSONArray("list") ?: error("Rodin 下载响应缺少文件列表")
        if (list.length() == 0) error("Rodin 没有返回可下载文件")
        return list.findModelUrl(preferredFormat)
            ?: error("Rodin 没有返回 ${preferredFormat.uppercase(Locale.US)} 模型文件")
    }

    private fun requestJson(method: String, url: String, apiKey: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
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
            error("Rodin HTTP $responseCode: ${text.take(180)}")
        }
        if (text.isBlank()) error("Rodin 返回空响应")
        val json = JSONObject(text)
        val errorMessage = json.optString("error")
            .ifBlank { json.optString("message") }
            .ifBlank { json.optString("detail") }
        if (
            errorMessage.isNotBlank() &&
            json.optString("uuid").isBlank() &&
            json.optJSONArray("jobs") == null &&
            json.optJSONArray("list") == null
        ) {
            error("Rodin 返回错误：$errorMessage")
        }
        return json
    }

    private fun downloadModel(
        context: Context,
        displayNameSeed: String,
        modelUrl: String,
        preferredFormat: String,
    ): File {
        val extension = modelUrl.modelExtension() ?: preferredFormat.lowercase(Locale.US)
        require(extension in SUPPORTED_MODEL_EXTENSIONS) { "不支持的 Rodin 模型格式：$extension" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = displayNameSeed.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(32)
            .ifBlank { "rodin" }
        val targetDir = AigcLocalLibrary.modelDirectory(context)
        val target = File(targetDir, "${stamp}_${safeName}.$extension")
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_TIMEOUT_MS
            setRequestProperty("Accept", "*/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("模型下载失败：HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyToWithLimit(output, MAX_MODEL_BYTES)
                }
            }
        } finally {
            connection.disconnect()
        }
        return target
    }

    private fun JSONArray.statuses(): List<String> =
        buildList {
            for (index in 0 until length()) {
                val job = optJSONObject(index) ?: continue
                add(job.optString("status", "unknown"))
            }
        }

    private fun JSONArray.findModelUrl(preferredFormat: String): String? {
        val files = buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val url = item.optString("url")
                val name = item.optString("name")
                if (url.isNotBlank()) add(RodinFile(url = url, name = name))
            }
        }
        val preferred = preferredFormat.lowercase(Locale.US)
        return files.firstOrNull { it.matchesExtension(preferred) }?.url
            ?: files.firstOrNull { file ->
                SUPPORTED_MODEL_EXTENSIONS.any { extension -> file.matchesExtension(extension) }
            }?.url
    }

    private fun RodinFile.matchesExtension(extension: String): Boolean =
        name.substringBefore('?').lowercase(Locale.US).endsWith(".$extension") ||
            url.substringBefore('?').lowercase(Locale.US).endsWith(".$extension")

    private fun String.modelExtension(): String? =
        substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase(Locale.US)
            .takeIf { it in SUPPORTED_MODEL_EXTENSIONS }

    private fun DataOutputStream.writeFormField(boundary: String, name: String, value: String) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
        writeBytes("\r\n")
        write(value.toByteArray(Charsets.UTF_8))
        writeBytes("\r\n")
    }

    private fun DataOutputStream.writeFileField(boundary: String, name: String, file: File) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n")
        writeBytes("Content-Type: ${file.imageContentType()}\r\n")
        writeBytes("\r\n")
        file.inputStream().use { it.copyTo(this) }
        writeBytes("\r\n")
    }

    private fun File.imageContentType(): String =
        when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }

    private fun java.io.InputStream.copyToWithLimit(output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            copied += count
            if (copied > maxBytes) {
                throw IllegalArgumentException("生成模型超过 ${maxBytes / 1024 / 1024} MB")
            }
            output.write(buffer, 0, count)
        }
        return copied
    }

    private data class RodinSubmittedJob(
        val jobUuid: String,
        val subscriptionKey: String,
    )

    private data class RodinFile(
        val url: String,
        val name: String,
    )

    private const val API_BASE = "https://api.hyper3d.com"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val DOWNLOAD_TIMEOUT_MS = 120_000
    private const val POLL_INTERVAL_MS = 5_000L
    private const val MAX_POLL_ATTEMPTS = 360
    private const val MAX_MODEL_BYTES = 200L * 1024L * 1024L
    private val SUPPORTED_MODEL_EXTENSIONS = setOf("glb", "gltf", "usd", "usdz")
}
