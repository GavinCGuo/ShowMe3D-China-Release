package com.example.showme3d.studio.aigc

import com.example.showme3d.release.ReleasePolicy
import android.content.Context
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.model.ObjectType
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class AigcGeneratedAsset(
    val id: String,
    val displayName: String,
    val source: String,
    val createdAt: Long,
    val type: ObjectType = ObjectType.MODEL,
)

class AigcSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun rodinApiKey(): String =
        if (ReleasePolicy.CLOUD_AI_ENABLED) prefs.getString(KEY_RODIN_API_KEY, "").orEmpty() else ""

    fun saveRodinApiKey(value: String) {
        ReleasePolicy.requireCloudAi()
        prefs.edit().putString(KEY_RODIN_API_KEY, value.trim()).apply()
    }

    fun imageToImageApiKey(): String =
        if (ReleasePolicy.CLOUD_AI_ENABLED) prefs.getString(KEY_IMAGE_TO_IMAGE_API_KEY, "").orEmpty() else ""

    fun saveImageToImageApiKey(value: String) {
        ReleasePolicy.requireCloudAi()
        prefs.edit().putString(KEY_IMAGE_TO_IMAGE_API_KEY, value.trim()).apply()
    }

    fun doubaoApiKey(): String =
        if (ReleasePolicy.CLOUD_AI_ENABLED) prefs.getString(KEY_DOUBAO_API_KEY, "").orEmpty() else ""

    fun saveDoubaoApiKey(value: String) {
        ReleasePolicy.requireCloudAi()
        prefs.edit().putString(KEY_DOUBAO_API_KEY, value.trim()).apply()
    }

    fun generatedAssets(): List<AigcGeneratedAsset> {
        val prefAssets = parseGeneratedAssets(
            raw = prefs.getString(KEY_GENERATED_ASSETS, "[]"),
            defaultType = ObjectType.MODEL,
        ).filter { it.type == ObjectType.MODEL }
        val manifestAssets = parseGeneratedAssets(
            raw = AigcLocalLibrary.readManifest(appContext),
            defaultType = ObjectType.MODEL,
        ).filter { it.type == ObjectType.MODEL }
        val fileAssets = AigcLocalLibrary.modelFiles(appContext).map { it.toGeneratedAsset(ObjectType.MODEL) }
        val assets = (prefAssets + manifestAssets + fileAssets)
            .filter { it.hasReadableLocalFile() }
            .distinctBy { it.source }
            .sortedByDescending { it.createdAt }
        if (assets != prefAssets) {
            persistGeneratedAssets(assets)
        }
        return assets
    }

    fun generatedImages(): List<AigcGeneratedAsset> {
        val prefAssets = parseGeneratedAssets(
            raw = prefs.getString(KEY_GENERATED_IMAGES, "[]"),
            defaultType = ObjectType.IMAGE,
        ).filter { it.type == ObjectType.IMAGE }
        val manifestAssets = parseGeneratedAssets(
            raw = AigcLocalLibrary.readImageManifest(appContext),
            defaultType = ObjectType.IMAGE,
        ).filter { it.type == ObjectType.IMAGE }
        val fileAssets = AigcLocalLibrary.imageFiles(appContext).map { it.toGeneratedAsset(ObjectType.IMAGE) }
        val assets = (prefAssets + manifestAssets + fileAssets)
            .filter { it.hasReadableLocalFile() }
            .distinctBy { it.source }
            .sortedByDescending { it.createdAt }
        if (assets != prefAssets) {
            persistGeneratedImages(assets)
        }
        return assets
    }

    fun generationRecords(): List<AigcGenerationRecord> {
        val prefRecords = parseGenerationRecords(
            raw = prefs.getString(KEY_GENERATION_RECORDS, "[]"),
        )
        val manifestRecords = parseGenerationRecords(
            raw = AigcLocalLibrary.readRecordManifest(appContext),
        )
        val records = (prefRecords + manifestRecords)
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
            .take(MAX_GENERATION_RECORDS)
        if (records != prefRecords) {
            persistGenerationRecords(records)
        }
        return records
    }

    fun recordGeneratedAsset(asset: ImportedAsset): List<AigcGeneratedAsset> {
        require(asset.type == ObjectType.MODEL) { "AIGC generated asset must be a model" }
        val next = AigcGeneratedAsset(
            id = "aigc_${System.currentTimeMillis()}",
            displayName = asset.displayName.take(28),
            source = asset.source,
            createdAt = System.currentTimeMillis(),
            type = ObjectType.MODEL,
        )
        val assets = (listOf(next) + generatedAssets())
            .distinctBy { it.source }
            .sortedByDescending { it.createdAt }
        persistGeneratedAssets(assets)
        return assets
    }

    fun recordGeneratedImage(asset: ImportedAsset): List<AigcGeneratedAsset> {
        require(asset.type == ObjectType.IMAGE) { "AIGC generated image must be an image" }
        val next = AigcGeneratedAsset(
            id = "aigc_image_${System.currentTimeMillis()}",
            displayName = asset.displayName.take(28),
            source = asset.source,
            createdAt = System.currentTimeMillis(),
            type = ObjectType.IMAGE,
        )
        val assets = (listOf(next) + generatedImages())
            .distinctBy { it.source }
            .sortedByDescending { it.createdAt }
        persistGeneratedImages(assets)
        return assets
    }

    fun upsertGenerationRecord(record: AigcGenerationRecord): List<AigcGenerationRecord> {
        val records = (listOf(record) + generationRecords().filterNot { it.id == record.id })
            .sortedByDescending { it.updatedAt }
            .take(MAX_GENERATION_RECORDS)
        persistGenerationRecords(records)
        return records
    }

    private fun File.toGeneratedAsset(type: ObjectType): AigcGeneratedAsset =
        AigcGeneratedAsset(
            id = "${if (type == ObjectType.IMAGE) "aigc_image_file" else "aigc_file"}_${absolutePath.hashCode()}",
            displayName = nameWithoutExtension
                .replace(Regex("^\\d{8}_\\d{6}_"), "")
                .replace('_', ' ')
                .ifBlank { if (type == ObjectType.IMAGE) "生成图片" else "生成模型" }
                .take(28),
            source = "$FILE_SOURCE_PREFIX$absolutePath",
            createdAt = lastModified().takeIf { it > 0L } ?: 0L,
            type = type,
        )

    private fun parseGeneratedAssets(raw: String?, defaultType: ObjectType): List<AigcGeneratedAsset> =
        runCatching {
            val text = raw.orEmpty().trim()
            if (text.isBlank()) return@runCatching emptyList()
            val array = if (text.startsWith("{")) {
                JSONObject(text).optJSONArray(KEY_ASSETS) ?: JSONArray()
            } else {
                JSONArray(text)
            }
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(KEY_ID)
                    val source = obj.optString(KEY_SOURCE)
                    if (id.isBlank() || source.isBlank()) continue
                    val type = obj.optString(KEY_TYPE)
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { ObjectType.valueOf(it) }.getOrNull() }
                        ?: defaultType
                    add(
                        AigcGeneratedAsset(
                            id = id,
                            displayName = obj.optString(
                                KEY_DISPLAY_NAME,
                                if (type == ObjectType.IMAGE) "生成图片" else "生成模型",
                            ),
                            source = source,
                            createdAt = obj.optLong(KEY_CREATED_AT, 0L),
                            type = type,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun parseGenerationRecords(raw: String?): List<AigcGenerationRecord> =
        runCatching {
            val text = raw.orEmpty().trim()
            if (text.isBlank()) return@runCatching emptyList()
            val array = if (text.startsWith("{")) {
                JSONObject(text).optJSONArray(KEY_RECORDS) ?: JSONArray()
            } else {
                JSONArray(text)
            }
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val id = obj.optString(KEY_ID)
                    if (id.isBlank()) continue
                    val type = obj.optString(KEY_RECORD_TYPE)
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { AigcGenerationRecordType.valueOf(it) }.getOrNull() }
                        ?: AigcGenerationRecordType.TEXT_TO_MODEL
                    val status = obj.optString(KEY_STATUS)
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { AigcGenerationRecordStatus.valueOf(it) }.getOrNull() }
                        ?: AigcGenerationRecordStatus.FAILED
                    add(
                        AigcGenerationRecord(
                            id = id,
                            type = type,
                            title = obj.optString(KEY_TITLE, type.displayName),
                            status = status,
                            stage = obj.optString(KEY_STAGE, status.displayName),
                            sourceName = obj.optString(KEY_SOURCE_NAME, ""),
                            prompt = obj.optString(KEY_PROMPT, ""),
                            inputSources = obj.optJSONArray(KEY_INPUT_SOURCES).toStringList(),
                            outputImageSource = obj.optionalString(KEY_OUTPUT_IMAGE_SOURCE),
                            outputModelSource = obj.optionalString(KEY_OUTPUT_MODEL_SOURCE),
                            errorMessage = obj.optionalString(KEY_ERROR_MESSAGE),
                            createdAt = obj.optLong(KEY_CREATED_AT, 0L),
                            updatedAt = obj.optLong(KEY_UPDATED_AT, obj.optLong(KEY_CREATED_AT, 0L)),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun persistGeneratedAssets(assets: List<AigcGeneratedAsset>) {
        val array = assets.toAssetJson()
        prefs.edit()
            .putString(KEY_GENERATED_ASSETS, array.toString())
            .apply()
        val manifest = JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_ASSETS, array)
            .toString(2)
        AigcLocalLibrary.writeManifest(appContext, manifest)
    }

    private fun persistGeneratedImages(assets: List<AigcGeneratedAsset>) {
        val array = assets.toAssetJson()
        prefs.edit()
            .putString(KEY_GENERATED_IMAGES, array.toString())
            .apply()
        val manifest = JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_ASSETS, array)
            .toString(2)
        AigcLocalLibrary.writeImageManifest(appContext, manifest)
    }

    private fun persistGenerationRecords(records: List<AigcGenerationRecord>) {
        val array = records.toRecordJson()
        prefs.edit()
            .putString(KEY_GENERATION_RECORDS, array.toString())
            .apply()
        val manifest = JSONObject()
            .put(KEY_VERSION, 1)
            .put(KEY_RECORDS, array)
            .toString(2)
        AigcLocalLibrary.writeRecordManifest(appContext, manifest)
    }

    private fun AigcGeneratedAsset.hasReadableLocalFile(): Boolean {
        if (!source.startsWith(FILE_SOURCE_PREFIX)) return true
        return File(source.removePrefix(FILE_SOURCE_PREFIX)).isFile
    }

    private fun List<AigcGeneratedAsset>.toAssetJson(): JSONArray =
        JSONArray().also { array ->
            forEach { asset ->
                array.put(
                    JSONObject()
                        .put(KEY_ID, asset.id)
                        .put(KEY_DISPLAY_NAME, asset.displayName)
                        .put(KEY_SOURCE, asset.source)
                        .put(KEY_CREATED_AT, asset.createdAt)
                        .put(KEY_TYPE, asset.type.name)
                )
            }
        }

    private fun List<AigcGenerationRecord>.toRecordJson(): JSONArray =
        JSONArray().also { array ->
            forEach { record ->
                array.put(
                    JSONObject()
                        .put(KEY_ID, record.id)
                        .put(KEY_RECORD_TYPE, record.type.name)
                        .put(KEY_TITLE, record.title)
                        .put(KEY_STATUS, record.status.name)
                        .put(KEY_STAGE, record.stage)
                        .put(KEY_SOURCE_NAME, record.sourceName)
                        .put(KEY_PROMPT, record.prompt)
                        .put(KEY_INPUT_SOURCES, JSONArray(record.inputSources))
                        .putNullable(KEY_OUTPUT_IMAGE_SOURCE, record.outputImageSource)
                        .putNullable(KEY_OUTPUT_MODEL_SOURCE, record.outputModelSource)
                        .putNullable(KEY_ERROR_MESSAGE, record.errorMessage)
                        .put(KEY_CREATED_AT, record.createdAt)
                        .put(KEY_UPDATED_AT, record.updatedAt)
                )
            }
        }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value)

    private companion object {
        const val PREFS_NAME = "showme3d_aigc"
        const val KEY_RODIN_API_KEY = "rodinApiKey"
        const val KEY_IMAGE_TO_IMAGE_API_KEY = "imageToImageApiKey"
        const val KEY_DOUBAO_API_KEY = "doubaoApiKey"
        const val KEY_GENERATED_ASSETS = "generatedAssets"
        const val KEY_GENERATED_IMAGES = "generatedImages"
        const val KEY_GENERATION_RECORDS = "generationRecords"
        const val KEY_VERSION = "version"
        const val KEY_ASSETS = "assets"
        const val KEY_RECORDS = "records"
        const val KEY_ID = "id"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_SOURCE = "source"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_TYPE = "type"
        const val KEY_RECORD_TYPE = "recordType"
        const val KEY_TITLE = "title"
        const val KEY_STATUS = "status"
        const val KEY_STAGE = "stage"
        const val KEY_SOURCE_NAME = "sourceName"
        const val KEY_PROMPT = "prompt"
        const val KEY_INPUT_SOURCES = "inputSources"
        const val KEY_OUTPUT_IMAGE_SOURCE = "outputImageSource"
        const val KEY_OUTPUT_MODEL_SOURCE = "outputModelSource"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        const val FILE_SOURCE_PREFIX = "file://"
        const val MAX_GENERATION_RECORDS = 80
    }
}
