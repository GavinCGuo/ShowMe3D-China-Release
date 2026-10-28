package com.example.showme3d.studio.imports

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.showme3d.studio.model.ObjectType
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportedAsset(
    val displayName: String,
    val type: ObjectType,
    val source: String,
)

data class ImportCandidate(
    val displayName: String,
    val directoryLabel: String,
    val path: String,
    val sizeLabel: String,
    val modifiedAt: Long,
    val isDirectory: Boolean = false,
    val importable: Boolean = true,
)

data class ImportBrowserPage(
    val title: String,
    val currentPath: String?,
    val parentPath: String?,
    val entries: List<ImportCandidate>,
    val message: String,
)

object LocalAssetImporter {
    val rootDirectoryLabels: List<String> = listOf(
        "全部文件",
        "ShowMe3DInbox",
        "App files",
        "Download",
        "Documents",
        "Pictures",
        "DCIM",
        "Movies",
    )

    fun inboxDir(context: Context): File =
        File(context.getExternalFilesDir(null), "ShowMe3DInbox").apply { mkdirs() }

    fun imageIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "image/jpeg", "image/webp"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun modelIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "model/*"
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            supportedModelMimeTypes.toTypedArray() + arrayOf("application/octet-stream", "application/json", "application/zip"),
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    fun import(
        context: Context,
        uri: Uri,
        flags: Int = 0,
        expectedType: ObjectType? = null,
    ): Result<ImportedAsset> = runCatching {
        persistReadPermission(context, uri, flags)
        val originalName = context.displayName(uri) ?: "imported_asset"
        val safeName = originalName.sanitizeFileName()
        val target = newImportTarget(context, safeName)

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open selected file" }
            target.outputStream().use { output -> input.copyToWithLimit(output, MAX_IMPORT_BYTES) }
        }
        val mimeType = context.contentResolver.getType(uri)
        buildImportedAsset(originalName, target, mimeType, expectedType)
    }

    fun listCandidates(context: Context, expectedType: ObjectType): Result<List<ImportCandidate>> = runCatching {
        candidateDirectories(context)
            .flatMap { (label, dir) -> dir.safeListFiles().map { label to it } }
            .filter { (_, file) -> file.isFile && file.canRead() && file.matchesExpectedType(expectedType) }
            .distinctBy { (_, file) -> file.absolutePath }
            .sortedByDescending { (_, file) -> file.lastModified() }
            .map { (label, file) ->
                ImportCandidate(
                    displayName = file.name,
                    directoryLabel = label,
                    path = file.absolutePath,
                    sizeLabel = file.length().toSizeLabel(),
                    modifiedAt = file.lastModified(),
                )
            }
    }

    fun listBrowserPage(
        context: Context,
        expectedType: ObjectType?,
        directoryPath: String?,
    ): Result<ImportBrowserPage> = runCatching {
        if (directoryPath == null) {
            val roots = candidateDirectories(context)
                .distinctBy { (_, dir) -> dir.absolutePath }
                .map { (label, dir) ->
                    ImportCandidate(
                        displayName = label,
                        directoryLabel = "位置",
                        path = dir.absolutePath,
                        sizeLabel = dir.absolutePath,
                        modifiedAt = dir.lastModified(),
                        isDirectory = true,
                        importable = false,
                    )
                }
            return@runCatching ImportBrowserPage(
                title = "导入资源",
                currentPath = null,
                parentPath = null,
                entries = roots,
                message = "选择一个位置浏览文件。",
            )
        }

        val dir = File(directoryPath)
        require(dir.isDirectory) { "Not a directory: $directoryPath" }
        require(dir.isAllowedImportLocation(context)) { "Cannot browse outside shared storage" }

        val entries = dir.safeListFiles()
            .filter { file ->
                when {
                    file.isDirectory -> file.canRead()
                    file.isFile -> file.canRead() && file.matchesExpectedType(expectedType)
                    else -> false
                }
            }
            .sortedWith(
                compareByDescending<File> { it.isDirectory }
                    .thenByDescending { it.lastModified() }
                    .thenBy { it.name.lowercase(Locale.US) },
            )
            .map { file ->
                ImportCandidate(
                    displayName = file.name,
                    directoryLabel = if (file.isDirectory) "文件夹" else dir.importLocationLabel(context),
                    path = file.absolutePath,
                    sizeLabel = if (file.isDirectory) "打开" else file.length().toSizeLabel(),
                    modifiedAt = file.lastModified(),
                    isDirectory = file.isDirectory,
                    importable = file.isFile,
                )
            }

        ImportBrowserPage(
            title = dir.importLocationTitle(context),
            currentPath = dir.absolutePath,
            parentPath = dir.importParentPath(context),
            entries = entries,
            message = if (entries.isEmpty()) {
                "这个目录没有可导入的资源文件。"
            } else {
                "找到 ${entries.count { it.importable }} 个可导入文件，${entries.count { it.isDirectory }} 个文件夹。"
            },
        )
    }

    fun lastBrowserDirectory(context: Context): String? {
        val path = context.importBrowserPreferences().getString(KEY_LAST_BROWSER_DIR, null) ?: return null
        val dir = File(path)
        return path.takeIf { dir.isDirectory && dir.canRead() && dir.isAllowedImportLocation(context) }
    }

    fun rememberBrowserDirectory(context: Context, path: String?) {
        if (path == null) return
        val dir = File(path)
        if (!dir.isDirectory || !dir.isAllowedImportLocation(context)) return
        context.importBrowserPreferences()
            .edit()
            .putString(KEY_LAST_BROWSER_DIR, dir.absolutePath)
            .apply()
    }

    fun importLatestFromInbox(context: Context, expectedType: ObjectType): Result<ImportedAsset> = runCatching {
        val inbox = inboxDir(context)
        val files = inbox.listFiles()
            ?.filter { it.isFile && it.canRead() }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        require(files.isNotEmpty()) {
            "Inbox is empty: ${inbox.absolutePath}"
        }
        var lastError: Throwable? = null
        files.forEach { file ->
            val result = runCatching { importFile(context, file, expectedType) }
            result.onSuccess { return@runCatching it }
            lastError = result.exceptionOrNull()
        }
        throw IllegalArgumentException(lastError?.message ?: "No supported file in ${inbox.absolutePath}")
    }

    fun importFromPath(context: Context, path: String, expectedType: ObjectType? = null): Result<ImportedAsset> = runCatching {
        val sourceFile = File(path)
        require(sourceFile.isFile && sourceFile.canRead()) {
            "Cannot read selected file: $path"
        }
        importFile(context, sourceFile, expectedType)
    }

    private fun importFile(context: Context, sourceFile: File, expectedType: ObjectType?): ImportedAsset {
        val originalName = sourceFile.name
        val safeName = originalName.sanitizeFileName()
        val target = newImportTarget(context, safeName)
        sourceFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyToWithLimit(output, MAX_IMPORT_BYTES) }
        }
        return buildImportedAsset(originalName, target, mimeType = null, expectedType = expectedType)
    }

    private fun Context.displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun candidateDirectories(context: Context): List<Pair<String, File>> {
        val externalRoot = android.os.Environment.getExternalStorageDirectory()
        return browseRootDirectories(context).filterNot { (_, dir) ->
            runCatching { dir.canonicalFile == externalRoot.canonicalFile }.getOrDefault(false)
        }
    }

    private fun browseRootDirectories(context: Context): List<Pair<String, File>> {
        val externalRoot = android.os.Environment.getExternalStorageDirectory()
        val directories = listOf(
            externalRoot,
            inboxDir(context),
            context.getExternalFilesDir(null),
            File(externalRoot, "Download"),
            File(externalRoot, "Documents"),
            File(externalRoot, "Pictures"),
            File(externalRoot, "DCIM"),
            File(externalRoot, "Movies"),
        )
        return rootDirectoryLabels.zip(directories).mapNotNull { (label, dir) ->
            dir?.let { label to it }
        }.distinctBy { (_, dir) -> runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath) }
    }

    private fun File.safeListFiles(): List<File> =
        runCatching { listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun File.isAllowedImportLocation(context: Context): Boolean {
        val target = runCatching { canonicalFile }.getOrDefault(absoluteFile)
        return browseBoundaryRoots(context).any { root ->
            val allowedRoot = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
            target.path == allowedRoot.path || target.path.startsWith(allowedRoot.path + File.separator)
        }
    }

    private fun File.importParentPath(context: Context): String? {
        val current = runCatching { canonicalFile }.getOrDefault(absoluteFile)
        val roots = browseBoundaryRoots(context).map { root ->
            runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
        }
        if (roots.any { it.path == current.path }) return null
        val parent = current.parentFile ?: return null
        return parent.takeIf { it.isAllowedImportLocation(context) }?.absolutePath
    }

    private fun File.importLocationLabel(context: Context): String =
        browseRootDirectories(context)
            .sortedByDescending { (_, root) ->
                runCatching { root.canonicalPath.length }.getOrDefault(root.absolutePath.length)
            }
            .firstOrNull { (_, root) ->
                val current = runCatching { canonicalFile }.getOrDefault(absoluteFile)
                val candidate = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
                current.path == candidate.path || current.path.startsWith(candidate.path + File.separator)
            }
            ?.first
            ?: "文件"

    private fun File.importLocationTitle(context: Context): String {
        val rootLabel = importLocationLabel(context)
        return "$rootLabel / ${name.ifBlank { absolutePath }}"
    }

    private fun browseBoundaryRoots(context: Context): List<File> {
        val externalRoot = android.os.Environment.getExternalStorageDirectory()
        return listOf(externalRoot, context.getExternalFilesDir(null))
            .filterNotNull()
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    }

    private fun Context.importBrowserPreferences() =
        getSharedPreferences(PREFS_IMPORT_BROWSER, Context.MODE_PRIVATE)

    private fun newImportTarget(context: Context, safeName: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val targetDir = File(context.filesDir, "imports").apply { mkdirs() }
        return File(targetDir, "${stamp}_$safeName")
    }

    private fun buildImportedAsset(
        originalName: String,
        target: File,
        mimeType: String?,
        expectedType: ObjectType?,
    ): ImportedAsset {
        val type = target.toObjectType(originalName, mimeType)
        if (expectedType != null && type != expectedType) {
            target.delete()
            error("Selected file is ${type.name.lowercase(Locale.US)}, expected ${expectedType.name.lowercase(Locale.US)}")
        }
        if (type == ObjectType.MODEL && !target.looksLikeSupportedModel(originalName, mimeType)) {
            target.delete()
            error("Unsupported model file. Use GLB, GLTF, USD, or USDZ")
        }
        if (type == ObjectType.IMAGE && target.imageSize() == null) {
            target.delete()
            error("Cannot decode selected image")
        }

        return ImportedAsset(
            displayName = originalName.substringBeforeLast('.').ifBlank { "Imported Asset" },
            type = type,
            source = "file://${target.absolutePath}",
        )
    }

    private fun File.toObjectType(originalName: String, mimeType: String?): ObjectType {
        if (mimeType?.startsWith("image/") == true) return ObjectType.IMAGE
        val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (ext) {
            "png", "jpg", "jpeg", "webp" -> ObjectType.IMAGE
            "" -> if (looksLikeImage()) ObjectType.IMAGE else ObjectType.MODEL
            else -> ObjectType.MODEL
        }
    }

    private fun File.looksLikeSupportedModel(originalName: String, mimeType: String?): Boolean {
        val ext = originalName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext in supportedModelExtensions) return true
        return mimeType in supportedModelMimeTypes
    }

    private fun File.looksLikeImage(): Boolean {
        return imageSize() != null
    }

    private fun File.matchesExpectedType(expectedType: ObjectType?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when (expectedType) {
            ObjectType.IMAGE -> ext in supportedImageExtensions
            ObjectType.MODEL -> ext in supportedModelExtensions
            null -> ext in supportedImageExtensions || ext in supportedModelExtensions
            else -> false
        }
    }

    private fun File.imageSize(): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "asset" }

    private fun persistReadPermission(context: Context, uri: Uri, flags: Int) {
        val readFlag = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readFlag == 0) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, readFlag)
        }
    }

    private fun Long.toSizeLabel(): String {
        if (this < 1024L) return "$this B"
        val units = arrayOf("KB", "MB", "GB")
        var value = this / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return "${DecimalFormat("0.#").format(value)} ${units[unit]}"
    }

    private fun java.io.InputStream.copyToWithLimit(output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesCopied = 0L
        while (true) {
            val bytes = read(buffer)
            if (bytes < 0) break
            bytesCopied += bytes
            if (bytesCopied > maxBytes) {
                throw IOException("File is larger than ${maxBytes / 1024 / 1024} MB")
            }
            output.write(buffer, 0, bytes)
        }
        return bytesCopied
    }

    private const val MAX_IMPORT_BYTES = 200L * 1024L * 1024L
    private const val PREFS_IMPORT_BROWSER = "showme3d_import_browser"
    private const val KEY_LAST_BROWSER_DIR = "lastBrowserDirectory"
    private val supportedImageExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val supportedModelExtensions = setOf("glb", "gltf", "usd", "usda", "usdc", "usdz")
    private val supportedModelMimeTypes = setOf(
        "model/usd",
        "model/vnd.usd",
        "model/vnd.pixar.usd",
        "model/vnd.usdz+zip",
        "model/gltf-binary",
        "model/gltf+json",
        "application/vnd.usdz+zip",
        "application/x-usd",
    )
}
