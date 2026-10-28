package com.example.showme3d.studio.aigc

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.Locale

object AigcLocalLibrary {
    private const val ROOT_DIRECTORY = "ShowMe3D/AIGC"
    private const val MODELS_DIRECTORY = "models"
    private const val IMAGES_DIRECTORY = "images"
    private const val PREPROCESSED_DIRECTORY = "preprocessed"
    private const val MANIFEST_FILE = "generated_assets.json"
    private const val IMAGE_MANIFEST_FILE = "generated_images.json"
    private const val RECORD_MANIFEST_FILE = "generation_records.json"
    private val SUPPORTED_MODEL_EXTENSIONS = setOf("glb", "gltf", "usd", "usda", "usdc", "usdz")
    private val SUPPORTED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun rootDirectory(context: Context): File =
        publicRootDirectory().takeIf { it.ensureWritableDirectory() }
            ?: fallbackRootDirectory(context)

    fun modelDirectory(context: Context): File =
        File(rootDirectory(context), MODELS_DIRECTORY).apply { mkdirs() }

    fun imageDirectory(context: Context): File =
        File(rootDirectory(context), IMAGES_DIRECTORY).apply { mkdirs() }

    fun preprocessedImageDirectory(context: Context): File =
        File(rootDirectory(context), PREPROCESSED_DIRECTORY).apply { mkdirs() }

    fun manifestFile(context: Context): File =
        File(rootDirectory(context), MANIFEST_FILE)

    fun imageManifestFile(context: Context): File =
        File(rootDirectory(context), IMAGE_MANIFEST_FILE)

    fun recordManifestFile(context: Context): File =
        File(rootDirectory(context), RECORD_MANIFEST_FILE)

    fun modelFiles(context: Context): List<File> =
        modelDirectory(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) in SUPPORTED_MODEL_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun imageFiles(context: Context): List<File> =
        imageDirectory(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) in SUPPORTED_IMAGE_EXTENSIONS }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun readManifest(context: Context): String? {
        val file = manifestFile(context)
        if (!file.isFile || !file.canRead()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun readImageManifest(context: Context): String? {
        val file = imageManifestFile(context)
        if (!file.isFile || !file.canRead()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun readRecordManifest(context: Context): String? {
        val file = recordManifestFile(context)
        if (!file.isFile || !file.canRead()) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun writeManifest(context: Context, content: String) {
        val file = manifestFile(context)
        file.parentFile?.mkdirs()
        runCatching { file.writeText(content, Charsets.UTF_8) }
    }

    fun writeImageManifest(context: Context, content: String) {
        val file = imageManifestFile(context)
        file.parentFile?.mkdirs()
        runCatching { file.writeText(content, Charsets.UTF_8) }
    }

    fun writeRecordManifest(context: Context, content: String) {
        val file = recordManifestFile(context)
        file.parentFile?.mkdirs()
        runCatching { file.writeText(content, Charsets.UTF_8) }
    }

    private fun publicRootDirectory(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            ROOT_DIRECTORY,
        )

    private fun fallbackRootDirectory(context: Context): File =
        File(context.filesDir, "aigc").apply { mkdirs() }

    private fun File.ensureWritableDirectory(): Boolean =
        runCatching {
            if (!exists()) mkdirs()
            isDirectory && canWrite()
        }.getOrDefault(false)
}
