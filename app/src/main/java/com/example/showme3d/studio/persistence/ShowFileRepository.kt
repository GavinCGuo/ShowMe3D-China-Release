package com.example.showme3d.studio.persistence

import android.content.Context
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.ObjectVersion
import com.example.showme3d.studio.model.ObjectVersionHistory
import com.example.showme3d.studio.model.ObjectVersionOrigin
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.Page
import com.example.showme3d.studio.model.PageAnnotation
import com.example.showme3d.studio.model.PageAnnotationType
import com.example.showme3d.studio.model.PageAnimation
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.StrokeData
import com.example.showme3d.studio.model.StrokePath
import com.example.showme3d.studio.model.StrokePoint
import com.example.showme3d.studio.model.TransformData
import java.io.File
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

data class SavedStudioState(
    val shows: List<ShowDocument>,
    val activeShowId: String?,
    val skippedFileCount: Int = 0,
    val restoredFromStorage: Boolean = false,
)

class ShowFileRepository(context: Context) {
    private val rootDir = File(context.filesDir, ROOT_DIR)
    private val showDir = File(rootDir, SHOW_DIR)
    private val indexFile = File(rootDir, INDEX_FILE)

    fun load(): Result<SavedStudioState> = runCatching {
        if (!showDir.exists()) {
            return@runCatching SavedStudioState(emptyList(), null)
        }
        var skippedFileCount = 0
        val indexJson = runCatching { JSONObject(indexFile.readText()) }
            .onFailure { if (indexFile.exists()) skippedFileCount++ }
            .getOrNull()
        val showFiles = indexJson
            ?.optJSONArray(KEY_SHOWS)
            ?.indexedObjects()
            ?.mapNotNull { entry ->
                entry.optionalString(KEY_FILE)?.let { fileName -> File(showDir, fileName) }
            }
            ?: showDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.sortedBy { it.name }
                .orEmpty()
        val shows = mutableListOf<ShowDocument>()
        for (showFile in showFiles) {
            if (!showFile.exists()) continue
            runCatching { parseShow(JSONObject(showFile.readText())) }
                .onSuccess { shows.add(it) }
                .onFailure {
                    skippedFileCount++
                    showFile.quarantineBrokenFile()
                }
        }
        SavedStudioState(
            shows = shows.filter { it.pages.isNotEmpty() && it.objects.isNotEmpty() },
            activeShowId = indexJson?.optionalString(KEY_ACTIVE_SHOW_ID),
            skippedFileCount = skippedFileCount,
            restoredFromStorage = indexFile.exists(),
        )
    }

    fun save(state: SavedStudioState): Result<Unit> = runCatching {
        rootDir.mkdirs()
        showDir.mkdirs()
        val validShows = state.shows.filter { it.pages.isNotEmpty() && it.objects.isNotEmpty() }
        val validIds = validShows.map { it.id }.toSet()
        validShows.forEach { show ->
            File(showDir, "${show.id}.json").writeAtomically(show.toJson().toString(2))
        }
        showDir.listFiles()
            ?.filter { it.extension == "json" && it.nameWithoutExtension !in validIds }
            ?.forEach { it.delete() }
        indexFile.writeAtomically(
            JSONObject()
                .put(KEY_SCHEMA_VERSION, ShowDocument.CURRENT_SCHEMA_VERSION)
                .put(KEY_ACTIVE_SHOW_ID, state.activeShowId)
                .put(
                    KEY_SHOWS,
                    JSONArray().also { array ->
                        validShows.forEach { show ->
                            array.put(
                                JSONObject()
                                    .put(KEY_ID, show.id)
                                    .put(KEY_NAME, show.name)
                                    .put(KEY_DOCUMENT_TYPE, show.documentType.name)
                                    .put(KEY_FILE, "${show.id}.json")
                            )
                        }
                    },
                )
                .toString(2)
        )
    }

    private fun parseShow(json: JSONObject): ShowDocument {
        val document = ShowDocument(
            id = json.getString(KEY_ID),
            name = json.optString(KEY_NAME, "Untitled Show"),
            schemaVersion = ShowDocument.CURRENT_SCHEMA_VERSION,
            documentType = parseDocumentType(json.optString(KEY_DOCUMENT_TYPE, DocumentType.PRESENTATION.name)),
            anchorEnabled = json.optBoolean(KEY_ANCHOR_ENABLED, false),
        )
        val objects = json.optJSONArray(KEY_OBJECTS) ?: JSONArray()
        for (i in 0 until objects.length()) {
            val obj = objects.optJSONObject(i) ?: continue
            val type = parseObjectType(obj.optString(KEY_TYPE, ObjectType.MODEL.name)) ?: continue
            val sceneObject = SceneObject(
                id = obj.getString(KEY_ID),
                type = type,
                source = obj.optString(KEY_SOURCE, ""),
                displayName = obj.optString(KEY_DISPLAY_NAME, "Object"),
                textColor = obj.optionalLong(KEY_TEXT_COLOR),
                strokeData = obj.optJSONObject(KEY_STROKE_DATA)?.let(::parseStrokeData),
                isLocked = obj.optBoolean(KEY_LOCKED, false),
                faceViewer = obj.optBoolean(KEY_FACE_VIEWER, false),
                textBackgroundEnabled = obj.optBoolean(KEY_TEXT_BACKGROUND_ENABLED, false),
                modelOriginMode = parseModelOriginMode(obj.optString(KEY_MODEL_ORIGIN_MODE, "")),
                playEmbeddedAnimation = obj.optBoolean(KEY_PLAY_EMBEDDED_ANIMATION, false),
                versionHistory = obj.optJSONObject(KEY_VERSION_HISTORY)?.let(::parseObjectVersionHistory),
            )
            document.objects[sceneObject.id] = sceneObject
        }
        val pages = json.optJSONArray(KEY_PAGES) ?: JSONArray()
        for (i in 0 until pages.length()) {
            val pageJson = pages.optJSONObject(i) ?: continue
            document.pages.add(parsePage(pageJson))
        }
        if (document.pages.isEmpty()) {
            document.pages.add(Page("restored_page"))
        }
        document.pages.forEach { page ->
            page.states.keys.removeAll { objectId -> objectId !in document.objects }
        }
        return document
    }

    private fun parsePage(json: JSONObject): Page {
        val page = Page(id = json.getString(KEY_ID))
        val states = json.optJSONArray(KEY_STATES) ?: JSONArray()
        for (i in 0 until states.length()) {
            val entry = states.optJSONObject(i) ?: continue
            val objectId = entry.optionalString(KEY_OBJECT_ID) ?: continue
            val transform = entry.optJSONObject(KEY_TRANSFORM)?.let(::parseTransform) ?: TransformData()
            page.states[objectId] = ObjectState(
                transform = transform,
                visible = entry.optBoolean(KEY_VISIBLE, true),
                pageAnimation = entry.optJSONObject(KEY_PAGE_ANIMATION)?.let(::parsePageAnimation),
                autoRotateY = entry.optBoolean(KEY_AUTO_ROTATE_Y, false),
                groupId = entry.optionalString(KEY_GROUP_ID),
            )
        }
        val annotations = json.optJSONArray(KEY_ANNOTATIONS) ?: JSONArray()
        annotations.indexedObjects()
            .mapNotNull(::parsePageAnnotation)
            .forEach { page.annotations += it }
        page.skybox = json.optJSONObject(KEY_SKYBOX)?.let(::parsePageSkybox)
        return page
    }

    private fun parsePageSkybox(json: JSONObject): PageSkybox? {
        val source = json.optionalString(KEY_SOURCE)?.takeIf { it.isNotBlank() } ?: return null
        return PageSkybox(
            source = source,
            displayName = json.optString(KEY_DISPLAY_NAME, "天空背景"),
            rotationY = json.optDouble(KEY_ROTATION_Y, 0.0).toFloat(),
        )
    }

    private fun parseTransform(json: JSONObject): TransformData =
        TransformData(
            px = json.optDouble(KEY_PX, 0.0).toFloat(),
            py = json.optDouble(KEY_PY, 0.0).toFloat(),
            pz = json.optDouble(KEY_PZ, 0.0).toFloat(),
            rotationY = json.optDouble(KEY_ROTATION_Y, 0.0).toFloat(),
            scale = json.optDouble(KEY_SCALE, 1.0).toFloat(),
        )

    private fun parsePageAnimation(json: JSONObject): PageAnimation? {
        val start = json.optJSONObject(KEY_START)?.let(::parseTransform) ?: return null
        val end = json.optJSONObject(KEY_END)?.let(::parseTransform) ?: return null
        return PageAnimation(
            start = start,
            end = end,
            durationSeconds = json.optDouble(KEY_DURATION_SECONDS, PageAnimation.DEFAULT_DURATION_SECONDS.toDouble())
                .toFloat()
                .coerceIn(PageAnimation.MIN_DURATION_SECONDS, PageAnimation.MAX_DURATION_SECONDS),
            easing = parseAnimationEasing(json.optString(KEY_EASING, AnimationEasing.EASE_IN_OUT.name)),
            loopMode = parseAnimationLoopMode(json.optString(KEY_LOOP_MODE, AnimationLoopMode.PING_PONG.name)),
        )
    }

    private fun ShowDocument.toJson(): JSONObject =
        JSONObject()
            .put(KEY_SCHEMA_VERSION, schemaVersion)
            .put(KEY_ID, id)
            .put(KEY_NAME, name)
            .put(KEY_DOCUMENT_TYPE, documentType.name)
            .put(KEY_ANCHOR_ENABLED, anchorEnabled)
            .put(
                KEY_OBJECTS,
                JSONArray().also { array ->
                    objects.values.forEach { obj -> array.put(obj.toJson()) }
                },
            )
            .put(
                KEY_PAGES,
                JSONArray().also { array ->
                    pages.forEach { page -> array.put(page.toJson()) }
                },
            )

    private fun SceneObject.toJson(): JSONObject =
        JSONObject()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type.name)
            .put(KEY_SOURCE, source)
            .put(KEY_DISPLAY_NAME, displayName)
            .putNullable(KEY_TEXT_COLOR, textColor)
            .putNullable(KEY_STROKE_DATA, strokeData?.toJson())
            .put(KEY_LOCKED, isLocked)
            .put(KEY_FACE_VIEWER, faceViewer)
            .put(KEY_TEXT_BACKGROUND_ENABLED, textBackgroundEnabled)
            .put(KEY_MODEL_ORIGIN_MODE, modelOriginMode.name)
            .put(KEY_PLAY_EMBEDDED_ANIMATION, playEmbeddedAnimation)
            .putNullable(KEY_VERSION_HISTORY, versionHistory?.toJson())

    private fun parseObjectVersionHistory(json: JSONObject): ObjectVersionHistory? {
        val versions = json.optJSONArray(KEY_VERSIONS)
            ?.indexedObjects()
            ?.mapNotNull(::parseObjectVersion)
            ?.toMutableList()
            ?: return null
        if (versions.isEmpty()) return null
        val activeVersionId = json.optionalString(KEY_ACTIVE_VERSION_ID)
            ?.takeIf { activeId -> versions.any { it.versionId == activeId } }
            ?: versions.last().versionId
        return ObjectVersionHistory(
            activeVersionId = activeVersionId,
            versions = versions,
        )
    }

    private fun parseObjectVersion(json: JSONObject): ObjectVersion? {
        val versionId = json.optionalString(KEY_VERSION_ID) ?: return null
        val type = parseObjectType(json.optString(KEY_TYPE, ObjectType.IMAGE.name)) ?: return null
        return ObjectVersion(
            versionId = versionId,
            type = type,
            source = json.optString(KEY_SOURCE, ""),
            displayName = json.optString(KEY_DISPLAY_NAME, type.name),
            textColor = json.optionalLong(KEY_TEXT_COLOR),
            strokeData = json.optJSONObject(KEY_STROKE_DATA)?.let(::parseStrokeData),
            origin = parseObjectVersionOrigin(json.optString(KEY_ORIGIN, ObjectVersionOrigin.UNKNOWN.name)),
            recordId = json.optionalString(KEY_RECORD_ID),
            prompt = json.optString(KEY_PROMPT, ""),
            inputSources = json.optJSONArray(KEY_INPUT_SOURCES).toStringList(),
            createdAt = json.optLong(KEY_CREATED_AT, 0L),
            preferredTransform = json.optJSONObject(KEY_PREFERRED_TRANSFORM)?.let(::parseTransform),
        )
    }

    private fun ObjectVersionHistory.toJson(): JSONObject =
        JSONObject()
            .put(KEY_ACTIVE_VERSION_ID, activeVersionId)
            .put(
                KEY_VERSIONS,
                JSONArray().also { array ->
                    versions.forEach { version -> array.put(version.toJson()) }
                },
            )

    private fun ObjectVersion.toJson(): JSONObject =
        JSONObject()
            .put(KEY_VERSION_ID, versionId)
            .put(KEY_TYPE, type.name)
            .put(KEY_SOURCE, source)
            .put(KEY_DISPLAY_NAME, displayName)
            .putNullable(KEY_TEXT_COLOR, textColor)
            .putNullable(KEY_STROKE_DATA, strokeData?.toJson())
            .put(KEY_ORIGIN, origin.name)
            .putNullable(KEY_RECORD_ID, recordId)
            .put(KEY_PROMPT, prompt)
            .put(KEY_INPUT_SOURCES, JSONArray(inputSources))
            .put(KEY_CREATED_AT, createdAt)
            .putNullable(KEY_PREFERRED_TRANSFORM, preferredTransform?.toJson())

    private fun Page.toJson(): JSONObject =
        JSONObject()
            .put(KEY_ID, id)
            .put(
                KEY_STATES,
                JSONArray().also { array ->
                    states.forEach { (objectId, state) ->
                        array.put(
                            JSONObject()
                                .put(KEY_OBJECT_ID, objectId)
                                .put(KEY_VISIBLE, state.visible)
                                .put(KEY_TRANSFORM, state.transform.toJson())
                                .putNullable(KEY_PAGE_ANIMATION, state.pageAnimation?.toJson())
                                .put(KEY_AUTO_ROTATE_Y, state.autoRotateY)
                                .putNullable(KEY_GROUP_ID, state.groupId)
                        )
                    }
                },
            )
            .put(
                KEY_ANNOTATIONS,
                JSONArray().also { array ->
                    annotations.forEach { annotation -> array.put(annotation.toJson()) }
                },
            )
            .putNullable(KEY_SKYBOX, skybox?.toJson())

    private fun PageSkybox.toJson(): JSONObject =
        JSONObject()
            .put(KEY_SOURCE, source)
            .put(KEY_DISPLAY_NAME, displayName)
            .put(KEY_ROTATION_Y, rotationY)

    private fun PageAnimation.toJson(): JSONObject =
        JSONObject()
            .put(KEY_START, start.toJson())
            .put(KEY_END, end.toJson())
            .put(KEY_DURATION_SECONDS, durationSeconds)
            .put(KEY_EASING, easing.name)
            .put(KEY_LOOP_MODE, loopMode.name)

    private fun TransformData.toJson(): JSONObject =
        JSONObject()
            .put(KEY_PX, px)
            .put(KEY_PY, py)
            .put(KEY_PZ, pz)
            .put(KEY_ROTATION_Y, rotationY)
            .put(KEY_SCALE, scale)

    private fun parseStrokeData(json: JSONObject): StrokeData {
        val paths = json.optJSONArray(KEY_STROKES) ?: JSONArray()
        return StrokeData(
            strokes = paths.indexedObjects()
                .map { pathJson ->
                    StrokePath(
                        color = pathJson.optionalLong(KEY_COLOR) ?: DEFAULT_STROKE_COLOR,
                        points = (pathJson.optJSONArray(KEY_POINTS) ?: JSONArray())
                            .indexedObjects()
                            .map { pointJson ->
                                StrokePoint(
                                    x = pointJson.optDouble(KEY_X, 0.0).toFloat(),
                                    y = pointJson.optDouble(KEY_Y, 0.0).toFloat(),
                                    z = pointJson.optDouble(KEY_Z, 0.0).toFloat(),
                                )
                            }
                            .toMutableList(),
                    )
                }
                .filter { it.points.size >= MIN_STROKE_POINTS }
                .toMutableList(),
        )
    }

    private fun StrokeData.toJson(): JSONObject =
        JSONObject()
            .put(
                KEY_STROKES,
                JSONArray().also { array ->
                    strokes.forEach { path -> array.put(path.toJson()) }
                },
            )

    private fun StrokePath.toJson(): JSONObject =
        JSONObject()
            .put(KEY_COLOR, color)
            .put(
                KEY_POINTS,
                JSONArray().also { array ->
                    points.forEach { point -> array.put(point.toJson()) }
                },
            )

    private fun StrokePoint.toJson(): JSONObject =
        JSONObject()
            .put(KEY_X, x)
            .put(KEY_Y, y)
            .put(KEY_Z, z)

    private fun parseStrokePoint(json: JSONObject): StrokePoint =
        StrokePoint(
            x = json.optDouble(KEY_X, 0.0).toFloat(),
            y = json.optDouble(KEY_Y, 0.0).toFloat(),
            z = json.optDouble(KEY_Z, 0.0).toFloat(),
        )

    private fun parsePageAnnotation(json: JSONObject): PageAnnotation? {
        val id = json.optionalString(KEY_ID) ?: return null
        val type = parsePageAnnotationType(json.optString(KEY_TYPE, PageAnnotationType.STROKE.name))
        return PageAnnotation(
            id = id,
            type = type,
            strokeData = json.optJSONObject(KEY_STROKE_DATA)?.let(::parseStrokeData),
            commentText = json.optionalString(KEY_COMMENT_TEXT),
            anchorPoint = json.optJSONObject(KEY_ANCHOR_POINT)?.let(::parseStrokePoint),
            panelPoint = json.optJSONObject(KEY_PANEL_POINT)?.let(::parseStrokePoint),
        ).takeIf { annotation ->
            when (annotation.type) {
                PageAnnotationType.STROKE -> annotation.strokeData?.strokes?.isNotEmpty() == true
                PageAnnotationType.COMMENT -> annotation.anchorPoint != null && annotation.panelPoint != null
            }
        }
    }

    private fun PageAnnotation.toJson(): JSONObject =
        JSONObject()
            .put(KEY_ID, id)
            .put(KEY_TYPE, type.name)
            .putNullable(KEY_STROKE_DATA, strokeData?.toJson())
            .putNullable(KEY_COMMENT_TEXT, commentText)
            .putNullable(KEY_ANCHOR_POINT, anchorPoint?.toJson())
            .putNullable(KEY_PANEL_POINT, panelPoint?.toJson())

    private fun parseObjectType(raw: String): ObjectType? =
        when (raw) {
            "ANCHOR" -> ObjectType.MODEL
            else -> runCatching { ObjectType.valueOf(raw) }.getOrNull()
        }

    private fun parseDocumentType(raw: String): DocumentType =
        runCatching { DocumentType.valueOf(raw) }.getOrDefault(DocumentType.PRESENTATION)

    private fun parseModelOriginMode(raw: String): ModelOriginMode =
        runCatching { ModelOriginMode.valueOf(raw) }.getOrDefault(ModelOriginMode.CENTER_AND_FIT)

    private fun parseAnimationEasing(raw: String): AnimationEasing =
        runCatching { AnimationEasing.valueOf(raw) }.getOrDefault(AnimationEasing.EASE_IN_OUT)

    private fun parseAnimationLoopMode(raw: String): AnimationLoopMode =
        runCatching { AnimationLoopMode.valueOf(raw) }.getOrDefault(AnimationLoopMode.PING_PONG)

    private fun parsePageAnnotationType(raw: String): PageAnnotationType =
        runCatching { PageAnnotationType.valueOf(raw) }.getOrDefault(PageAnnotationType.STROKE)

    private fun parseObjectVersionOrigin(raw: String): ObjectVersionOrigin =
        runCatching { ObjectVersionOrigin.valueOf(raw) }.getOrDefault(ObjectVersionOrigin.UNKNOWN)

    private fun File.writeAtomically(text: String) {
        parentFile?.mkdirs()
        val temp = File(parentFile, "$name.tmp")
        temp.writeText(text)
        if (exists() && !delete()) {
            temp.delete()
            throw IOException("Cannot replace $absolutePath")
        }
        if (!temp.renameTo(this)) {
            temp.delete()
            throw IOException("Cannot write $absolutePath")
        }
    }

    private fun File.quarantineBrokenFile() {
        val target = File(parentFile, "$name.broken")
        if (target.exists()) target.delete()
        renameTo(target)
    }

    private fun JSONArray.indexedObjects(): List<JSONObject> =
        buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(it) }
            }
        }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optionalString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optionalLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private companion object {
        const val ROOT_DIR = "shows"
        const val SHOW_DIR = "documents"
        const val INDEX_FILE = "index.json"
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_ACTIVE_SHOW_ID = "activeShowId"
        const val KEY_SHOWS = "shows"
        const val KEY_FILE = "file"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_DOCUMENT_TYPE = "documentType"
        const val KEY_ANCHOR_ENABLED = "anchorEnabled"
        const val KEY_OBJECTS = "objects"
        const val KEY_OBJECT_ID = "objectId"
        const val KEY_TYPE = "type"
        const val KEY_SOURCE = "source"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_TEXT_COLOR = "textColor"
        const val KEY_STROKE_DATA = "strokeData"
        const val KEY_LOCKED = "locked"
        const val KEY_FACE_VIEWER = "faceViewer"
        const val KEY_TEXT_BACKGROUND_ENABLED = "textBackgroundEnabled"
        const val KEY_MODEL_ORIGIN_MODE = "modelOriginMode"
        const val KEY_PLAY_EMBEDDED_ANIMATION = "playEmbeddedAnimation"
        const val KEY_VERSION_HISTORY = "versionHistory"
        const val KEY_ACTIVE_VERSION_ID = "activeVersionId"
        const val KEY_VERSION_ID = "versionId"
        const val KEY_VERSIONS = "versions"
        const val KEY_ORIGIN = "origin"
        const val KEY_RECORD_ID = "recordId"
        const val KEY_PROMPT = "prompt"
        const val KEY_INPUT_SOURCES = "inputSources"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_PREFERRED_TRANSFORM = "preferredTransform"
        const val KEY_STROKES = "strokes"
        const val KEY_COLOR = "color"
        const val KEY_POINTS = "points"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_Z = "z"
        const val KEY_PAGES = "pages"
        const val KEY_STATES = "states"
        const val KEY_ANNOTATIONS = "annotations"
        const val KEY_VISIBLE = "visible"
        const val KEY_TRANSFORM = "transform"
        const val KEY_PAGE_ANIMATION = "pageAnimation"
        const val KEY_AUTO_ROTATE_Y = "autoRotateY"
        const val KEY_GROUP_ID = "groupId"
        const val KEY_SKYBOX = "skybox"
        const val KEY_START = "start"
        const val KEY_END = "end"
        const val KEY_DURATION_SECONDS = "durationSeconds"
        const val KEY_EASING = "easing"
        const val KEY_LOOP_MODE = "loopMode"
        const val KEY_COMMENT_TEXT = "commentText"
        const val KEY_ANCHOR_POINT = "anchorPoint"
        const val KEY_PANEL_POINT = "panelPoint"
        const val KEY_PX = "px"
        const val KEY_PY = "py"
        const val KEY_PZ = "pz"
        const val KEY_ROTATION_Y = "rotationY"
        const val KEY_SCALE = "scale"
        const val DEFAULT_STROKE_COLOR = 0xFFE11D48
        const val MIN_STROKE_POINTS = 2
    }
}
