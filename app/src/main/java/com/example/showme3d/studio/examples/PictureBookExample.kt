package com.example.showme3d.studio.examples

import android.content.Context
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.persistence.ShowFileRepository
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

internal enum class PictureBookExample(val preset: HomeExamplePreset, val title: String, private val folder: String) {
    ALICE(HomeExamplePreset.ALICE_IN_WONDERLAND, "爱丽丝梦游仙境", "alice"),
    THREE_PIGS(HomeExamplePreset.THREE_LITTLE_PIGS, "三只小猪", "three_pigs");

    fun load(context: Context, repository: ShowFileRepository): Result<ShowDocument> = runCatching {
        read(repository, File(context.filesDir, "showme3d/builtin/$folder/v1")) { name ->
            context.assets.open("examples/$folder/$name")
        }
    }

    internal fun read(
        repository: ShowFileRepository,
        directory: File,
        openAsset: (String) -> InputStream,
    ): ShowDocument {
        val manifest = openAsset("book.json").bufferedReader().use { JSONObject(it.readText()) }
        val book = repository.parseShow(manifest.put("id", UUID.randomUUID().toString()))
        check(directory.isDirectory || directory.mkdirs() || directory.isDirectory)

        fun installedSource(name: String): String {
            require(name.isNotBlank() && File(name).name == name && name != "." && name != "..")
            val file = File(directory, name)
            if (!file.isFile || file.length() == 0L) {
                val temporary = File.createTempFile("story-", ".tmp", directory)
                try {
                    openAsset(name).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    check(temporary.length() > 0 && temporary.renameTo(file)) { "无法安装 $name" }
                } finally {
                    temporary.delete()
                }
            }
            return "file://${file.absolutePath}"
        }

        for (obj in book.objects.values) {
            if (obj.type == ObjectType.MODEL) obj.source = installedSource(obj.source)
        }
        for (page in book.pages) {
            page.skybox = page.skybox?.let { it.copy(source = installedSource(it.source)) }
        }
        return book
    }
}
