package com.example.showme3d.studio.imports

import android.content.Context
import android.graphics.BitmapFactory
import com.example.showme3d.studio.model.ObjectType
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class UserSkyboxAsset(
    val id: String,
    val displayName: String,
    val source: String,
    val width: Int,
    val height: Int,
    val lastUsedAt: Long,
)

object UserSkyboxLibrary {
    fun load(context: Context): List<UserSkyboxAsset> =
        runCatching {
            val index = indexFile(context)
            if (!index.exists()) return emptyList()
            val entries = JSONObject(index.readText()).optJSONArray(KEY_ITEMS) ?: JSONArray()
            buildList {
                for (i in 0 until entries.length()) {
                    val json = entries.optJSONObject(i) ?: continue
                    val source = json.optString(KEY_SOURCE)
                    val file = source.removePrefix(FILE_PREFIX).takeIf { source.startsWith(FILE_PREFIX) }?.let(::File)
                    if (file?.isFile != true) continue
                    add(
                        UserSkyboxAsset(
                            id = json.optString(KEY_ID),
                            displayName = json.optString(KEY_DISPLAY_NAME, "天空背景"),
                            source = source,
                            width = json.optInt(KEY_WIDTH, 0),
                            height = json.optInt(KEY_HEIGHT, 0),
                            lastUsedAt = json.optLong(KEY_LAST_USED_AT, 0L),
                        )
                    )
                }
            }.sortedByDescending { it.lastUsedAt }
        }.getOrDefault(emptyList())

    fun importFromPath(context: Context, path: String): Result<UserSkyboxAsset> = runCatching {
        val imported = LocalAssetImporter.importFromPath(
            context = context,
            path = path,
            expectedType = ObjectType.IMAGE,
        ).getOrThrow()
        val file = File(imported.source.removePrefix(FILE_PREFIX))
        val size = file.imageSize() ?: error("无法读取天空背景尺寸")
        val ratio = size.first.toFloat() / size.second.toFloat()
        if (ratio !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) {
            file.delete()
            error("天空背景需要接近 2:1，当前为 ${size.first} × ${size.second}")
        }
        val id = file.sha256()
        val existing = load(context).firstOrNull { it.id == id }
        if (existing != null) {
            file.delete()
            touch(context, existing)
            return@runCatching existing.copy(lastUsedAt = System.currentTimeMillis())
        }
        val asset = UserSkyboxAsset(
            id = id,
            displayName = imported.displayName,
            source = imported.source,
            width = size.first,
            height = size.second,
            lastUsedAt = System.currentTimeMillis(),
        )
        save(context, listOf(asset) + load(context))
        asset
    }

    fun touch(context: Context, asset: UserSkyboxAsset) {
        val now = System.currentTimeMillis()
        val updated = load(context)
            .filterNot { it.id == asset.id }
            .toMutableList()
            .apply { add(0, asset.copy(lastUsedAt = now)) }
        save(context, updated)
    }

    private fun save(context: Context, assets: List<UserSkyboxAsset>) {
        val index = indexFile(context)
        index.parentFile?.mkdirs()
        val json = JSONObject().put(
            KEY_ITEMS,
            JSONArray().also { array ->
                assets.distinctBy { it.id }.forEach { asset ->
                    array.put(
                        JSONObject()
                            .put(KEY_ID, asset.id)
                            .put(KEY_DISPLAY_NAME, asset.displayName)
                            .put(KEY_SOURCE, asset.source)
                            .put(KEY_WIDTH, asset.width)
                            .put(KEY_HEIGHT, asset.height)
                            .put(KEY_LAST_USED_AT, asset.lastUsedAt)
                    )
                }
            },
        )
        val temp = File(index.parentFile, "${index.name}.tmp")
        temp.writeText(json.toString(2))
        if (index.exists()) index.delete()
        check(temp.renameTo(index)) { "无法保存天空背景资源库" }
    }

    private fun indexFile(context: Context): File =
        File(context.filesDir, "$LIBRARY_DIR/$INDEX_FILE")

    private fun File.imageSize(): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val FILE_PREFIX = "file://"
    private const val LIBRARY_DIR = "skyboxes"
    private const val INDEX_FILE = "index.json"
    private const val KEY_ITEMS = "items"
    private const val KEY_ID = "id"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_SOURCE = "source"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_LAST_USED_AT = "lastUsedAt"
    private const val MIN_ASPECT_RATIO = 1.9f
    private const val MAX_ASPECT_RATIO = 2.1f
}
