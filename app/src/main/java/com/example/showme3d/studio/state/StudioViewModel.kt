package com.example.showme3d.studio.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.showme3d.library.PresetAsset
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.examples.CuriosityExampleAssets
import com.example.showme3d.studio.examples.DinosaurDetectiveExampleAssets
import com.example.showme3d.studio.examples.HomeExamplePreset
import com.example.showme3d.studio.examples.ImmersiveInteriorExampleAssets
import com.example.showme3d.studio.examples.createDinosaurDetectiveExampleDocument
import com.example.showme3d.studio.examples.createCuriosityExampleDocument
import com.example.showme3d.studio.examples.createImmersiveInteriorExampleDocument
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ObjectVersion
import com.example.showme3d.studio.model.ObjectVersionHistory
import com.example.showme3d.studio.model.ObjectVersionOrigin
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
import com.example.showme3d.studio.model.hasNoElements
import com.example.showme3d.studio.persistence.SavedStudioState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class StudioPanel {
    NONE,
    ASSETS,
    IMPORT_BROWSER,
    FILES,
    TEXT,
    ANIMATION,
    STROKE,
}

enum class AppScreen {
    HOME,
    EDITOR,
}

enum class SceneEnvironmentMode {
    REAL,
    VIRTUAL,
}

enum class PresentAnnotationTool {
    NONE,
    COMMENT,
    STROKE,
    ERASER,
}

data class SceneGroupingResult(
    val groupId: String,
    val objectIds: List<String>,
)

data class SceneUngroupingResult(
    val objectCount: Int,
    val groupCount: Int,
)

enum class WhiteboardTool {
    NONE,
    COMMENT,
}

enum class AnimationEndpointRole {
    START,
    END,
}

data class AnimationEndpointDraft(
    val id: String,
    val role: AnimationEndpointRole,
    val objectId: String,
    val sourceObject: SceneObject,
    val transform: TransformData,
) {
    val label: String get() = if (role == AnimationEndpointRole.START) "Start" else "End"
}

data class AnimationDraft(
    val objectId: String,
    val sourceObject: SceneObject,
    val start: TransformData,
    val end: TransformData,
    val durationSeconds: Float,
    val easing: AnimationEasing,
    val loopMode: AnimationLoopMode,
) {
    val startEndpointId: String = endpointId(objectId, AnimationEndpointRole.START)
    val endEndpointId: String = endpointId(objectId, AnimationEndpointRole.END)

    fun toPageAnimation(): PageAnimation =
        PageAnimation(
            start = start,
            end = end,
            durationSeconds = durationSeconds,
            easing = easing,
            loopMode = loopMode,
        )

    fun endpoints(): List<AnimationEndpointDraft> =
        listOf(
            AnimationEndpointDraft(startEndpointId, AnimationEndpointRole.START, objectId, sourceObject, start),
            AnimationEndpointDraft(endEndpointId, AnimationEndpointRole.END, objectId, sourceObject, end),
        )

    companion object {
        fun endpointId(objectId: String, role: AnimationEndpointRole): String =
            "${objectId}__anim_${role.name.lowercase(Locale.US)}"
    }
}

data class StrokeDraft(
    val paths: List<StrokePath> = emptyList(),
    val redoPaths: List<StrokePath> = emptyList(),
    val activePath: StrokePath? = null,
    val selectedColor: Long = ObjectColors.STROKE_PALETTE.first(),
) {
    val committedStrokeCount: Int get() = paths.size
    val totalPointCount: Int get() = paths.sumOf { it.points.size } + (activePath?.points?.size ?: 0)
    val hasDrawableContent: Boolean get() =
        paths.any { it.isDrawableStrokePath() } || activePath?.isDrawableStrokePath() == true

    fun toStrokeData(): StrokeData =
        StrokeData(
            (paths + listOfNotNull(activePath))
                .filter { it.isDrawableStrokePath() }
                .map { it.optimizedForStrokeStorage(MAX_STROKE_POINTS_PER_PATH) }
                .toMutableList(),
        )

    companion object {
        const val MIN_STROKE_POINTS = 2
        const val MIN_STROKE_LENGTH_METERS = 0.035f
        const val MAX_STROKE_POINTS_PER_PATH = 180
    }
}

data class AnnotationStrokeDraft(
    val activePath: StrokePath? = null,
    val selectedColor: Long = ObjectColors.STROKE_PALETTE.first(),
) {
    val hasDrawableContent: Boolean get() = activePath?.isDrawableStrokePath() == true
}

data class AnnotationCommentDraft(
    val anchorPoint: StrokePoint,
    val currentPoint: StrokePoint,
)

data class ImageSubjectPreview(
    val objectId: String,
    val originalSource: String,
    val previewSource: String,
    val preprocessMode: AigcImagePreprocessMode? = null,
)

data class EditingAnnotationComment(
    val id: String,
    val text: String,
    val panelPoint: StrokePoint? = null,
)

data class ObjectVersionUiItem(
    val versionId: String,
    val title: String,
    val detail: String,
    val origin: ObjectVersionOrigin,
    val type: ObjectType,
    val active: Boolean,
)

/**
 * Small, deterministic editor state for the M1 rebuild.
 *
 * Keep this layer boring: one in-memory Show, stable object ids, page snapshots,
 * and one revision counter that drives SceneController.sync().
 */
class StudioViewModel {
    private val shows = mutableListOf(newDocument(1))
    private val undoStack = mutableListOf<EditorSnapshot>()
    private val redoStack = mutableListOf<EditorSnapshot>()
    private var activeEditorBatch: EditorBatch? = null
    private var builtInExamplePreset: HomeExamplePreset? = null
    private var builtInExampleReturnPageIndex = 0

    var activeShowIndex by mutableStateOf(0)
        private set
    var document by mutableStateOf(shows[activeShowIndex])
        private set

    var currentPageIndex by mutableStateOf(0)
        private set
    var selectedObjectId by mutableStateOf<String?>(null)
        private set
    var mode by mutableStateOf(AppMode.EDIT)
        private set
    var panel by mutableStateOf(StudioPanel.NONE)
        private set
    var revision by mutableStateOf(0)
        private set
    var previewRevision by mutableStateOf(0)
        private set
    var pageRevision by mutableStateOf(0)
        private set
    var statusMessage by mutableStateOf("Objects: 0")
        private set
    var objectManipulationInProgress by mutableStateOf(false)
        private set
    var animationsPaused by mutableStateOf(false)
        private set
    var viewpointModeObjectId by mutableStateOf<String?>(null)
        private set
    var sceneEnvironmentMode by mutableStateOf(SceneEnvironmentMode.REAL)
        private set
    var saveRevision by mutableStateOf(0)
        private set
    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var animationDraft by mutableStateOf<AnimationDraft?>(null)
        private set
    var strokeDraft by mutableStateOf<StrokeDraft?>(null)
        private set
    var annotationStrokeDraft by mutableStateOf<AnnotationStrokeDraft?>(null)
        private set
    var annotationCommentDraft by mutableStateOf<AnnotationCommentDraft?>(null)
        private set
    var editingAnnotationComment by mutableStateOf<EditingAnnotationComment?>(null)
        private set
    var presentAnnotationTool by mutableStateOf(PresentAnnotationTool.NONE)
        private set
    var whiteboardTool by mutableStateOf(WhiteboardTool.NONE)
        private set
    var annotationsVisible by mutableStateOf(true)
        private set
    var animationDraftHadExistingAnimation by mutableStateOf(false)
        private set
    private var frozenAnimationObjectId: String? = null
    private var frozenAnimationTransform: TransformData? = null
    private var activeTransformObjectId: String? = null
    private var activeTransformBase: TransformData? = null
    private var activeTransformLatest: TransformData? = null
    private val activeGroupTransformBases = mutableMapOf<String, TransformData>()
    private val activeGroupPreviewTransforms = mutableMapOf<String, TransformData>()
    private val endpointPreviewTransforms = mutableMapOf<String, TransformData>()
    private var activeTextEditObjectId: String? = null
    private var activeRotationObjectId: String? = null
    private var activeRotationLatest: TransformData? = null
    private val imageSubjectPreviews = mutableMapOf<String, ImageSubjectPreview>()
    private val imageSubjectCutoutCache = mutableMapOf<String, ImageSubjectPreview>()

    val currentPage: Page get() = document.pages[currentPageIndex]
    val pageCount: Int get() = document.pages.size
    val objectCount: Int get() = document.objects.size
    val isWhiteboard: Boolean get() = document.documentType == DocumentType.WHITEBOARD
    val isPresentation: Boolean get() = document.documentType == DocumentType.PRESENTATION
    val isBuiltInExampleSession: Boolean get() = builtInExamplePreset != null
    val selectedObject: SceneObject? get() = selectedObjectId?.let { document.objects[it] }
    val selectedGroupMemberIds: List<String>
        get() = selectedObjectId?.let(::groupMemberIds).orEmpty()
    val selectedObjectLocked: Boolean get() = selectedObject?.isLocked == true
    val selectedObjectAutoRotateY: Boolean
        get() = selectedObjectId?.let { currentPage.states[it]?.autoRotateY } == true
    val selectedObjectFacesViewer: Boolean
        get() = selectedObject
            ?.takeIf { it.type == ObjectType.TEXT || it.type == ObjectType.IMAGE }
            ?.faceViewer == true
    val selectedImageSubjectPreviewActive: Boolean
        get() = selectedObjectId?.let { imageSubjectPreviews.containsKey(it) } == true
    val selectedImageCanUseGeneratedModel: Boolean
        get() = selectedObjectId?.let { objectHasVersionOfType(it, ObjectType.MODEL) } == true
    val selectedGeneratedModelCanRestoreImage: Boolean
        get() = selectedObjectId?.let { objectHasVersionOfType(it, ObjectType.IMAGE) } == true
    val selectedObjectVersions: List<ObjectVersionUiItem>
        get() = selectedObjectId?.let(::objectVersionItems).orEmpty()
    val showSummaries: List<ShowSummary> get() = shows.mapIndexedNotNull { index, show ->
        show.takeUnless { (screen == AppScreen.HOME || builtInExamplePreset != null) && it.hasNoElements() }?.let {
            ShowSummary(
                index = index,
                name = show.name,
                documentType = show.documentType,
                objectCount = show.objects.size,
                pageCount = show.pages.size,
                selected = index == activeShowIndex,
            )
        }
    }
    val canAddPage: Boolean get() = isPresentation && pageCount < ShowDocument.MAX_PAGES
    val canDeletePage: Boolean get() = isPresentation && pageCount > 1
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val animationDraftEndpoints: List<AnimationEndpointDraft> get() =
        animationDraft?.endpoints()
            ?.map { endpoint ->
                endpointPreviewTransforms[endpoint.id]?.let { endpoint.copy(transform = it) } ?: endpoint
            }
            .orEmpty()
    val selectedAnimationEndpoint: AnimationEndpointDraft? get() =
        selectedObjectId?.let { selectedId ->
            animationDraftEndpoints.firstOrNull { it.id == selectedId }
        }
    val animationFrozenObjectId: String? get() =
        animationDraft?.objectId ?: frozenAnimationObjectId
    val animationFrozenTransform: TransformData? get() =
        frozenAnimationTransform
    val selectedPageAnimation: PageAnimation? get() =
        currentAnimationDraftWithPreviews()?.toPageAnimation()
            ?: selectedObjectId?.let { currentPage.states[it]?.pageAnimation }
    val animationEditorAnchorTransform: TransformData? get() =
        animationDraftEndpoints
            .takeIf { it.isNotEmpty() }
            ?.let { endpoints ->
                val start = endpoints.firstOrNull { it.role == AnimationEndpointRole.START }?.transform
                val end = endpoints.firstOrNull { it.role == AnimationEndpointRole.END }?.transform
                if (start != null && end != null) start.midpoint(end) else null
            }
    val currentPageHasAnimations: Boolean get() =
        currentPage.states.values.any {
            it.visible && (it.pageAnimation != null || it.autoRotateY)
        }
    val currentPageHasAnnotations: Boolean get() =
        currentPage.annotations.isNotEmpty()
    val currentPageHasStrokeAnnotations: Boolean get() =
        currentPage.annotations.any { it.type == PageAnnotationType.STROKE }
    val currentPageCommentAnnotations: List<PageAnnotation> get() =
        currentPage.annotations.filter { it.type == PageAnnotationType.COMMENT }
    val currentPageSkybox: PageSkybox? get() = currentPage.skybox
    val activeViewpointTransform: TransformData? get() =
        viewpointModeObjectId
            ?.let { objectId -> currentPage.states[objectId]?.takeIf { it.visible }?.transform }
            ?.takeIf { viewpointModeObjectId?.let { document.objects[it]?.type == ObjectType.VIEWPOINT } == true }

    fun isViewpointRelatedObject(objectId: String): Boolean {
        val obj = document.objects[objectId] ?: return false
        return obj.type == ObjectType.VIEWPOINT
    }

    fun isObjectLocked(objectId: String): Boolean =
        document.objects[objectId]?.isLocked == true ||
            animationDraftEndpoint(objectId)?.objectId?.let { document.objects[it]?.isLocked == true } == true

    fun toggleAssets() {
        closeWhiteboardTool()
        panel = if (panel == StudioPanel.ASSETS) StudioPanel.NONE else StudioPanel.ASSETS
    }

    fun toggleFiles() {
        closeWhiteboardTool()
        panel = if (panel == StudioPanel.FILES) StudioPanel.NONE else StudioPanel.FILES
    }

    fun openImportBrowser() {
        closeWhiteboardTool()
        panel = StudioPanel.IMPORT_BROWSER
    }

    fun closePanel() {
        if (panel == StudioPanel.ANIMATION) {
            cancelAnimationDraft()
            panel = StudioPanel.NONE
            notifyUiChanged()
            return
        }
        if (panel == StudioPanel.STROKE) {
            finishStrokeDraft()
            return
        }
        panel = StudioPanel.NONE
        activeTextEditObjectId = null
    }

    fun toggleAnimationPlayback() {
        animationsPaused = !animationsPaused
        notifyUiChanged()
    }

    fun toggleSceneEnvironment() {
        sceneEnvironmentMode = when (sceneEnvironmentMode) {
            SceneEnvironmentMode.REAL -> SceneEnvironmentMode.VIRTUAL
            SceneEnvironmentMode.VIRTUAL -> SceneEnvironmentMode.REAL
        }
        statusMessage = when (sceneEnvironmentMode) {
            SceneEnvironmentMode.REAL -> "Scene: real passthrough"
            SceneEnvironmentMode.VIRTUAL -> "Scene: virtual studio"
        }
        notifyUiChanged()
    }

    fun addViewpointFromToolbar() {
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        val objectId = newId()
        val obj = SceneObject(
            id = objectId,
            type = ObjectType.VIEWPOINT,
            source = "viewpoint",
            displayName = "视点",
        )
        addObjectToCurrentPage(
            obj,
            ObjectState(
                transform = spawnTransform(document.objects.size).copy(
                    py = 1.15f,
                    scale = 1f,
                ),
            ),
        )
        panel = StudioPanel.NONE
        selectedObjectId = objectId
        statusMessage = "Added viewpoint"
        notifyChanged()
    }

    fun enterSelectedViewpoint() {
        val objectId = selectedObjectId ?: return
        if (isObjectLocked(objectId)) return
        enterViewpoint(objectId)
    }

    fun enterViewpoint(objectId: String) {
        val obj = document.objects[objectId] ?: return
        val state = currentPage.states[objectId] ?: return
        if (obj.type != ObjectType.VIEWPOINT || !state.visible) return
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        viewpointModeObjectId = objectId
        panel = StudioPanel.NONE
        clearSelection()
        statusMessage = "Entered viewpoint"
        notifyUiChanged()
    }

    fun exitViewpoint() {
        if (viewpointModeObjectId == null) return
        viewpointModeObjectId = null
        statusMessage = "Exited viewpoint"
        notifyUiChanged()
    }

    fun enterPresentMode() {
        if (isWhiteboard) return
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        clearSelection()
        panel = StudioPanel.NONE
        mode = AppMode.PRESENT
        presentAnnotationTool = PresentAnnotationTool.NONE
        annotationStrokeDraft = null
        annotationCommentDraft = null
        editingAnnotationComment = null
        notifyPageChanged(documentChanged = false)
    }

    fun exitPresentMode() {
        finishAnnotationStrokePath()
        presentAnnotationTool = PresentAnnotationTool.NONE
        annotationStrokeDraft = null
        annotationCommentDraft = null
        editingAnnotationComment = null
        if (isBuiltInExampleSession) {
            returnHome()
            return
        }
        mode = AppMode.EDIT
        notifyUiChanged()
    }

    fun toggleWhiteboardCommentTool() {
        if (!isWhiteboard || mode != AppMode.EDIT || viewpointModeObjectId != null) return
        cancelAnimationDraft()
        cancelStrokeDraft()
        clearSelection()
        annotationStrokeDraft = null
        annotationCommentDraft = null
        editingAnnotationComment = null
        whiteboardTool = if (whiteboardTool == WhiteboardTool.COMMENT) {
            WhiteboardTool.NONE
        } else {
            WhiteboardTool.COMMENT
        }
        statusMessage = if (whiteboardTool == WhiteboardTool.COMMENT) {
            "Whiteboard comment: pinch and drag to place"
        } else {
            "Whiteboard comment closed"
        }
        notifyUiChanged()
    }

    fun closeWhiteboardTool() {
        if (whiteboardTool == WhiteboardTool.NONE && annotationCommentDraft == null) return
        annotationCommentDraft = null
        if (isWhiteboard) {
            whiteboardTool = WhiteboardTool.NONE
        }
        notifyUiChanged()
    }

    fun select(objectId: String?, visibleTransform: TransformData? = null) {
        if (viewpointModeObjectId != null) return
        if (mode != AppMode.EDIT) return
        if (strokeDraft != null) return
        val previousSelectedObjectId = selectedObjectId
        val endpoint = objectId?.let(::animationDraftEndpoint)
        if (endpoint != null) {
            selectedObjectId = endpoint.id
            statusMessage = "Editing animation ${endpoint.label}"
            notifyUiChanged()
            return
        }
        val groupedObjectId = objectId?.let(::groupRepresentativeId)
        val target = groupedObjectId?.let { document.objects[it] }
        val resolvedObjectId = when {
            target == null -> null
            else -> groupedObjectId
        }?.takeIf { document.objects.containsKey(it) }
        if (panel == StudioPanel.ANIMATION && resolvedObjectId != animationDraft?.objectId) {
            saveAnimationDraftAndClose(captureHistory = true, message = "Animation applied")
        }
        if (panel == StudioPanel.TEXT && resolvedObjectId != previousSelectedObjectId) {
            panel = StudioPanel.NONE
            activeTextEditObjectId = null
        }
        if (resolvedObjectId != frozenAnimationObjectId) {
            frozenAnimationObjectId = null
            frozenAnimationTransform = null
        }
        if (resolvedObjectId != null && currentPage.states[resolvedObjectId]?.pageAnimation != null) {
            frozenAnimationObjectId = resolvedObjectId
            frozenAnimationTransform = visibleTransform ?: frozenAnimationTransform ?: currentPage.states[resolvedObjectId]?.transform
        }
        selectedObjectId = resolvedObjectId
        notifyUiChanged()
    }

    fun handleSceneTap(objectId: String?, visibleTransform: TransformData? = null) {
        if (viewpointModeObjectId != null) return
        if (editingAnnotationComment != null) {
            closeEditingAnnotationComment()
        }
        if (isWhiteboard && whiteboardTool != WhiteboardTool.NONE) {
            closeWhiteboardTool()
            return
        }
        if (mode == AppMode.PRESENT) {
            if (presentAnnotationTool != PresentAnnotationTool.NONE) {
                closePresentAnnotationTool()
                return
            }
            objectId
                ?.takeIf { document.objects[it]?.type == ObjectType.VIEWPOINT }
                ?.let(::enterViewpoint)
            return
        }
        val groupedObjectId = objectId?.let(::groupRepresentativeId)
        if (groupedObjectId != null && groupedObjectId == selectedObjectId && document.objects.containsKey(groupedObjectId)) {
            if (panel == StudioPanel.ANIMATION && objectId == animationDraft?.objectId) {
                saveAnimationDraftAndClose(captureHistory = true, message = "Animation applied")
            }
            panel = StudioPanel.NONE
            activeTextEditObjectId = null
            clearSelection()
            statusMessage = "Selection cleared"
            notifyUiChanged()
            return
        }
        select(groupedObjectId, visibleTransform)
    }

    fun groupRepresentativeId(objectId: String): String {
        val groupId = currentPage.states[objectId]?.groupId ?: return objectId
        return currentPage.states.entries.firstOrNull { (_, state) -> state.groupId == groupId }?.key ?: objectId
    }

    fun groupMemberIds(objectId: String): List<String> {
        val groupId = currentPage.states[objectId]?.groupId ?: return listOf(objectId)
        return currentPage.states.entries.filter { (_, state) -> state.groupId == groupId }.map { it.key }
    }

    fun groupTransformsFor(objectId: String): Map<String, TransformData> {
        val representativeId = groupRepresentativeId(objectId)
        if (activeTransformObjectId == representativeId && activeGroupPreviewTransforms.isNotEmpty()) {
            return activeGroupPreviewTransforms.toMap()
        }
        return groupMemberIds(representativeId).mapNotNull { memberId ->
            currentPage.states[memberId]?.transform?.let { memberId to it }
        }.toMap()
    }

    fun togglePresentStrokeTool() {
        if (mode != AppMode.PRESENT || viewpointModeObjectId != null) return
        editingAnnotationComment = null
        presentAnnotationTool = when (presentAnnotationTool) {
            PresentAnnotationTool.STROKE,
            PresentAnnotationTool.ERASER,
            -> PresentAnnotationTool.NONE
            PresentAnnotationTool.NONE,
            PresentAnnotationTool.COMMENT,
            -> PresentAnnotationTool.STROKE
        }
        annotationStrokeDraft = null
        annotationCommentDraft = null
        statusMessage = if (presentAnnotationTool == PresentAnnotationTool.STROKE) {
            "Presentation brush: pinch to annotate"
        } else {
            "Presentation brush closed"
        }
        notifyUiChanged()
    }

    fun selectPresentStrokeTool() {
        if (mode != AppMode.PRESENT || viewpointModeObjectId != null) return
        editingAnnotationComment = null
        presentAnnotationTool = PresentAnnotationTool.STROKE
        annotationStrokeDraft = null
        annotationCommentDraft = null
        statusMessage = "Presentation brush: pinch to annotate"
        notifyUiChanged()
    }

    fun togglePresentEraserTool() {
        if (mode != AppMode.PRESENT || viewpointModeObjectId != null) return
        editingAnnotationComment = null
        presentAnnotationTool = if (presentAnnotationTool == PresentAnnotationTool.ERASER) {
            PresentAnnotationTool.NONE
        } else if (presentAnnotationTool == PresentAnnotationTool.STROKE) {
            PresentAnnotationTool.ERASER
        } else {
            PresentAnnotationTool.NONE
        }
        annotationStrokeDraft = null
        annotationCommentDraft = null
        statusMessage = if (presentAnnotationTool == PresentAnnotationTool.ERASER) {
            "Presentation eraser: pinch near a line"
        } else if (presentAnnotationTool == PresentAnnotationTool.STROKE) {
            "Presentation brush: pinch to annotate"
        } else {
            "Presentation brush closed"
        }
        notifyUiChanged()
    }

    fun togglePresentCommentTool() {
        if (mode != AppMode.PRESENT || viewpointModeObjectId != null) return
        presentAnnotationTool = if (presentAnnotationTool == PresentAnnotationTool.COMMENT) {
            PresentAnnotationTool.NONE
        } else {
            PresentAnnotationTool.COMMENT
        }
        annotationStrokeDraft = null
        annotationCommentDraft = null
        editingAnnotationComment = null
        statusMessage = if (presentAnnotationTool == PresentAnnotationTool.COMMENT) {
            "Presentation comment: pinch and drag to place"
        } else {
            "Presentation comment closed"
        }
        notifyUiChanged()
    }

    fun closePresentAnnotationTool() {
        if (presentAnnotationTool == PresentAnnotationTool.NONE) return
        finishAnnotationStrokePath()
        annotationStrokeDraft = null
        annotationCommentDraft = null
        presentAnnotationTool = PresentAnnotationTool.NONE
        statusMessage = "Presentation annotation tools closed"
        notifyUiChanged()
    }

    fun setAnnotationStrokeColor(color: Long) {
        val current = annotationStrokeDraft ?: AnnotationStrokeDraft()
        annotationStrokeDraft = current.copy(selectedColor = color)
        notifyUiChanged()
    }

    fun beginAnnotationStrokePath(point: StrokePoint) {
        if (mode != AppMode.PRESENT || presentAnnotationTool != PresentAnnotationTool.STROKE) return
        val current = annotationStrokeDraft ?: AnnotationStrokeDraft()
        annotationStrokeDraft = current.copy(
            activePath = StrokePath(
                color = current.selectedColor,
                points = mutableListOf(point),
            ),
        )
        notifyUiChanged()
    }

    fun appendAnnotationStrokePoint(point: StrokePoint, notify: Boolean = true): Boolean {
        val current = annotationStrokeDraft ?: return false
        val active = current.activePath ?: return false
        val last = active.points.lastOrNull()
        if (last != null && last.distanceTo(point) < STROKE_POINT_MIN_DISTANCE) return false
        active.points.add(point)
        if (!notify) return true
        annotationStrokeDraft = current.copy(activePath = active.copyPath())
        notifyUiChanged()
        return true
    }

    fun finishAnnotationStrokePath() {
        val current = annotationStrokeDraft ?: return
        val active = current.activePath
        annotationStrokeDraft = current.copy(activePath = null)
        if (active == null || !active.isDrawableStrokePath()) {
            notifyUiChanged()
            return
        }
        captureUndo()
        currentPage.annotations += PageAnnotation(
            id = newId(),
            type = PageAnnotationType.STROKE,
            strokeData = StrokeData(mutableListOf(active.optimizedForStrokeStorage(StrokeDraft.MAX_STROKE_POINTS_PER_PATH))),
        )
        statusMessage = "Presentation annotation added"
        notifyChanged()
    }

    fun cancelAnnotationStrokePath() {
        val current = annotationStrokeDraft ?: return
        if (current.activePath == null) return
        annotationStrokeDraft = current.copy(activePath = null)
        notifyUiChanged()
    }

    fun beginAnnotationComment(anchorPoint: StrokePoint) {
        if (mode != AppMode.PRESENT || presentAnnotationTool != PresentAnnotationTool.COMMENT) return
        val point = anchorPoint.copy()
        annotationCommentDraft = AnnotationCommentDraft(
            anchorPoint = point,
            currentPoint = point,
        )
        editingAnnotationComment = null
        statusMessage = "Presentation comment: drag to panel position"
        notifyUiChanged()
    }

    fun beginWhiteboardComment(anchorPoint: StrokePoint) {
        if (!isWhiteboard || mode != AppMode.EDIT || whiteboardTool != WhiteboardTool.COMMENT) return
        val point = anchorPoint.copy()
        annotationCommentDraft = AnnotationCommentDraft(
            anchorPoint = point,
            currentPoint = point,
        )
        editingAnnotationComment = null
        statusMessage = "Whiteboard comment: drag to panel position"
        notifyUiChanged()
    }

    fun updateAnnotationComment(point: StrokePoint, notify: Boolean = true): Boolean {
        val current = annotationCommentDraft ?: return false
        if (current.currentPoint.distanceTo(point) < COMMENT_DRAFT_POINT_MIN_DISTANCE) return false
        annotationCommentDraft = current.copy(currentPoint = point.copy())
        if (notify) {
            notifyUiChanged()
        }
        return true
    }

    fun finishAnnotationComment(): String? {
        val current = annotationCommentDraft ?: return null
        annotationCommentDraft = null
        if (mode != AppMode.PRESENT || presentAnnotationTool != PresentAnnotationTool.COMMENT) {
            notifyUiChanged()
            return null
        }
        captureUndo()
        val annotation = PageAnnotation(
            id = newId(),
            type = PageAnnotationType.COMMENT,
            commentText = "",
            anchorPoint = current.anchorPoint.copy(),
            panelPoint = current.currentPoint.copy(),
        )
        currentPage.annotations += annotation
        editingAnnotationComment = EditingAnnotationComment(annotation.id, "", annotation.panelPoint?.copy())
        presentAnnotationTool = PresentAnnotationTool.NONE
        statusMessage = "Comment annotation ready"
        notifyChanged()
        return annotation.id
    }

    fun finishWhiteboardComment(): String? {
        val current = annotationCommentDraft ?: return null
        annotationCommentDraft = null
        if (!isWhiteboard || mode != AppMode.EDIT || whiteboardTool != WhiteboardTool.COMMENT) {
            notifyUiChanged()
            return null
        }
        captureUndo()
        val annotation = PageAnnotation(
            id = newId(),
            type = PageAnnotationType.COMMENT,
            commentText = "",
            anchorPoint = current.anchorPoint.copy(),
            panelPoint = current.currentPoint.copy(),
        )
        currentPage.annotations += annotation
        editingAnnotationComment = EditingAnnotationComment(annotation.id, "", annotation.panelPoint?.copy())
        whiteboardTool = WhiteboardTool.NONE
        statusMessage = "Whiteboard comment ready"
        notifyChanged()
        return annotation.id
    }

    fun cancelAnnotationComment() {
        if (annotationCommentDraft == null) return
        annotationCommentDraft = null
        notifyUiChanged()
    }

    fun eraseStrokeAnnotation(annotationId: String): Boolean {
        if (mode != AppMode.PRESENT || presentAnnotationTool != PresentAnnotationTool.ERASER) return false
        val target = currentPage.annotations.firstOrNull {
            it.id == annotationId && it.type == PageAnnotationType.STROKE
        } ?: return false
        captureUndo()
        currentPage.annotations.remove(target)
        statusMessage = "Presentation annotation erased"
        notifyChanged()
        return true
    }

    fun editAnnotationComment(annotationId: String) {
        if (mode != AppMode.EDIT) return
        val annotation = currentPage.annotations.firstOrNull {
            it.id == annotationId && it.type == PageAnnotationType.COMMENT
        } ?: return
        editingAnnotationComment = EditingAnnotationComment(
            id = annotation.id,
            text = annotation.commentText.orEmpty(),
            panelPoint = annotation.panelPoint?.copy(),
        )
        panel = StudioPanel.NONE
        statusMessage = "Editing comment"
        notifyUiChanged()
    }

    fun updateEditingAnnotationComment(text: String) {
        val editing = editingAnnotationComment ?: return
        val annotation = currentPage.annotations.firstOrNull {
            it.id == editing.id && it.type == PageAnnotationType.COMMENT
        } ?: return
        annotation.commentText = text
        editingAnnotationComment = editing.copy(text = text)
        statusMessage = "Comment updated"
        notifyChanged()
    }

    fun closeEditingAnnotationComment() {
        editingAnnotationComment = null
        notifyUiChanged()
    }

    fun deleteAnnotationComment(annotationId: String) {
        val target = currentPage.annotations.firstOrNull {
            it.id == annotationId && it.type == PageAnnotationType.COMMENT
        } ?: return
        captureUndo()
        currentPage.annotations.remove(target)
        if (editingAnnotationComment?.id == annotationId) {
            editingAnnotationComment = null
        }
        statusMessage = "Comment deleted"
        notifyChanged()
    }

    fun toggleAnnotationsVisible() {
        annotationsVisible = !annotationsVisible
        statusMessage = if (annotationsVisible) "Annotations shown" else "Annotations hidden"
        notifyUiChanged()
    }

    fun deleteCurrentPageStrokeAnnotations() {
        if (!currentPageHasStrokeAnnotations) return
        captureUndo()
        currentPage.annotations.removeAll { it.type == PageAnnotationType.STROKE }
        statusMessage = "Page brush annotations deleted"
        notifyChanged()
    }

    fun addPresetObject(preset: PresetAsset) {
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        val objectId = newId()
        val obj = SceneObject(
            id = objectId,
            type = preset.type,
            source = preset.source,
            displayName = preset.displayName,
        )
        addObjectToCurrentPage(obj, ObjectState(transform = spawnTransform(document.objects.size).copy(scale = 1f)))
        panel = StudioPanel.NONE
        selectedObjectId = objectId
        statusMessage = "Added ${obj.displayName}. Objects: $objectCount"
        notifyChanged()
    }

    fun addImportedAsset(asset: ImportedAsset) {
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        val objectId = newId()
        val obj = SceneObject(
            id = objectId,
            type = asset.type,
            source = asset.source,
            displayName = asset.displayName.take(24),
        )
        addObjectToCurrentPage(obj, ObjectState(transform = spawnTransform(document.objects.size).copy(scale = 1f)))
        panel = StudioPanel.NONE
        selectedObjectId = objectId
        statusMessage = "Imported ${obj.displayName}. Objects: $objectCount"
        notifyChanged()
    }

    fun addImageGeneratedFromImageObject(
        sourceObjectId: String,
        asset: ImportedAsset,
        selectNewObject: Boolean = true,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val sourceObj = document.objects[sourceObjectId] ?: return false
        if (sourceObj.isLocked || sourceObj.type != ObjectType.IMAGE || asset.type != ObjectType.IMAGE) return false
        return replaceObjectWithGeneratedVersion(
            objectId = sourceObjectId,
            nextObject = generatedImageObject(sourceObjectId, asset),
            origin = ObjectVersionOrigin.IMAGE_TO_IMAGE,
            selectReplacedObject = selectNewObject,
            message = "Generated image replaced source: ${asset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun addImageGeneratedFromStrokeObject(
        sourceObjectId: String,
        asset: ImportedAsset,
        selectNewObject: Boolean = true,
        origin: ObjectVersionOrigin = ObjectVersionOrigin.STROKE_TO_IMAGE,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val sourceObj = document.objects[sourceObjectId] ?: return false
        if (sourceObj.isLocked || sourceObj.type != ObjectType.STROKE || asset.type != ObjectType.IMAGE) return false
        return replaceObjectWithGeneratedVersion(
            objectId = sourceObjectId,
            nextObject = generatedImageObject(sourceObjectId, asset),
            origin = origin,
            selectReplacedObject = selectNewObject,
            message = "Stroke converted to image: ${asset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun addModelGeneratedFromStrokeImage(
        sourceObjectId: String,
        imageAsset: ImportedAsset,
        modelAsset: ImportedAsset,
        selectNewObject: Boolean = true,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val sourceObj = document.objects[sourceObjectId] ?: return false
        if (
            sourceObj.isLocked ||
            sourceObj.type != ObjectType.STROKE ||
            imageAsset.type != ObjectType.IMAGE ||
            modelAsset.type != ObjectType.MODEL
        ) return false
        return replaceObjectWithGeneratedVersions(
            objectId = sourceObjectId,
            nextObjects = listOf(
                generatedImageObject(sourceObjectId, imageAsset) to ObjectVersionOrigin.STROKE_TO_IMAGE,
                generatedModelObject(sourceObjectId, modelAsset) to ObjectVersionOrigin.STROKE_TO_MODEL,
            ),
            selectReplacedObject = selectNewObject,
            message = "Stroke generated 3D model replaced stroke: ${modelAsset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun replaceSelectedImageWithGeneratedModel(asset: ImportedAsset): Boolean {
        val objectId = selectedObjectId ?: return false
        return replaceImageWithGeneratedModel(objectId, asset)
    }

    fun replaceTextWithGeneratedImage(
        objectId: String,
        asset: ImportedAsset,
        selectReplacedObject: Boolean = true,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val obj = document.objects[objectId] ?: return false
        if (obj.isLocked || obj.type != ObjectType.TEXT || asset.type != ObjectType.IMAGE) return false
        return replaceObjectWithGeneratedVersion(
            objectId = objectId,
            nextObject = generatedImageObject(objectId, asset),
            origin = ObjectVersionOrigin.TEXT_TO_IMAGE,
            selectReplacedObject = selectReplacedObject,
            message = "Generated image replaced text: ${asset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun replaceImageWithGeneratedModel(
        objectId: String,
        asset: ImportedAsset,
        selectReplacedObject: Boolean = true,
        preprocessingAsset: ImportedAsset? = null,
        preprocessingMode: AigcImagePreprocessMode? = null,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val obj = document.objects[objectId] ?: return false
        if (obj.isLocked || obj.type != ObjectType.IMAGE || asset.type != ObjectType.MODEL) return false
        val nextObjects = mutableListOf<Pair<SceneObject, ObjectVersionOrigin>>()
        val preprocessOrigin = preprocessingMode?.takeIf { it != AigcImagePreprocessMode.NONE }?.toPreprocessOrigin()
        if (preprocessOrigin != null && preprocessingAsset != null) {
            if (preprocessingAsset.type != ObjectType.IMAGE) return false
            if (preprocessingAsset.source != obj.source) {
                nextObjects += generatedImageObject(objectId, preprocessingAsset) to preprocessOrigin
            }
        }
        nextObjects += generatedModelObject(objectId, asset) to ObjectVersionOrigin.IMAGE_TO_MODEL
        return replaceObjectWithGeneratedVersions(
            objectId = objectId,
            nextObjects = nextObjects,
            selectReplacedObject = selectReplacedObject,
            message = "Generated 3D model replaced image: ${asset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun replaceImageWithPreprocessedVersion(
        objectId: String,
        asset: ImportedAsset,
        mode: AigcImagePreprocessMode,
        selectReplacedObject: Boolean = true,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        val obj = document.objects[objectId] ?: return false
        if (obj.isLocked || obj.type != ObjectType.IMAGE || asset.type != ObjectType.IMAGE) return false
        return replaceObjectWithGeneratedVersion(
            objectId = objectId,
            nextObject = generatedImageObject(objectId, asset),
            origin = mode.toPreprocessOrigin(),
            selectReplacedObject = selectReplacedObject,
            message = "Image preprocessed: ${asset.displayName.take(24)}",
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
    }

    fun switchSelectedImageToGeneratedModel(): Boolean {
        val objectId = selectedObjectId ?: return false
        return switchImageToGeneratedModel(objectId)
    }

    fun switchImageToGeneratedModel(objectId: String): Boolean {
        return switchObjectToLatestVersionOfType(objectId, ObjectType.MODEL)
    }

    fun restoreSelectedGeneratedModelToImage(): Boolean {
        val objectId = selectedObjectId ?: return false
        return restoreGeneratedModelToImage(objectId)
    }

    fun restoreGeneratedModelToImage(objectId: String): Boolean {
        return switchObjectToLatestVersionOfType(objectId, ObjectType.IMAGE)
    }

    fun switchSelectedObjectVersion(versionId: String): Boolean {
        val objectId = selectedObjectId ?: return false
        return switchObjectVersion(objectId, versionId)
    }

    fun branchSelectedObjectVersion(versionId: String? = null): Boolean {
        val sourceObjectId = selectedObjectId ?: return false
        val source = document.objects[sourceObjectId] ?: return false
        if (source.isLocked) return false
        val state = currentPage.states[sourceObjectId]?.copyState() ?: return false
        val history = source.versionHistory ?: return false
        val sourceVersion = (versionId?.let { id -> history.versions.firstOrNull { it.versionId == id } }
            ?: history.versions.firstOrNull { it.versionId == history.activeVersionId }
            ?: source.toObjectVersion(
                versionId = newVersionId(),
                origin = ObjectVersionOrigin.ORIGINAL,
                transform = state.transform,
            )).copyVersion()
        val newObjectId = newId()
        val newVersionId = newVersionId()
        val branchTransform = state.transform.copy(px = state.transform.px + GENERATED_IMAGE_OFFSET_X)
        val branchVersion = sourceVersion.copy(
            versionId = newVersionId,
            createdAt = System.currentTimeMillis(),
            preferredTransform = null,
        )
        val branchObject = branchVersion.toSceneObject(
            objectId = newObjectId,
            isLocked = false,
            faceViewer = source.faceViewer,
            versionHistory = ObjectVersionHistory(
                activeVersionId = newVersionId,
                versions = mutableListOf(branchVersion.copyVersion()),
            ),
        )
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        addObjectToCurrentPage(
            branchObject,
            ObjectState(transform = branchTransform, visible = state.visible),
        )
        panel = StudioPanel.NONE
        selectedObjectId = newObjectId
        statusMessage = "Version branched: ${branchObject.displayName}"
        notifyChanged()
        return true
    }

    fun currentImageVersionPreprocessMode(objectId: String): AigcImagePreprocessMode? {
        val current = document.objects[objectId] ?: return null
        if (current.type != ObjectType.IMAGE) return null
        return when (current.activeVersion()?.origin) {
            ObjectVersionOrigin.IMAGE_PREPROCESS_LOCAL_SUBJECT -> AigcImagePreprocessMode.LOCAL_SUBJECT
            ObjectVersionOrigin.IMAGE_PREPROCESS_SEEDREAM_CLEAN -> AigcImagePreprocessMode.SEEDREAM_CLEAN
            else -> null
        }
    }

    private fun replaceObjectWithGeneratedVersion(
        objectId: String,
        nextObject: SceneObject,
        origin: ObjectVersionOrigin,
        selectReplacedObject: Boolean,
        message: String,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean =
        replaceObjectWithGeneratedVersions(
            objectId = objectId,
            nextObjects = listOf(nextObject to origin),
            selectReplacedObject = selectReplacedObject,
            message = message,
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )

    private fun replaceObjectWithGeneratedVersions(
        objectId: String,
        nextObjects: List<Pair<SceneObject, ObjectVersionOrigin>>,
        selectReplacedObject: Boolean,
        message: String,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): Boolean {
        if (nextObjects.isEmpty()) return false
        val current = document.objects[objectId] ?: return false
        if (current.isLocked) return false
        val state = currentPage.states[objectId] ?: return false
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        val history = current.versionHistory?.copyHistory()
            ?: ObjectVersionHistory(
                activeVersionId = newVersionId(),
                versions = mutableListOf(),
            ).also { initial ->
                initial.versions += current.toObjectVersion(
                    versionId = initial.activeVersionId,
                    origin = ObjectVersionOrigin.ORIGINAL,
                    transform = state.transform,
                )
            }
        syncActiveVersionSnapshot(history, current, state.transform)
        val now = System.currentTimeMillis()
        val newVersions = nextObjects.map { (nextObject, origin) ->
            nextObject.toObjectVersion(
                versionId = newVersionId(),
                origin = origin,
                transform = state.transform,
                recordId = recordId,
                prompt = prompt,
                inputSources = inputSources,
                createdAt = now,
            )
        }
        history.versions += newVersions.map { it.copyVersion() }
        history.activeVersionId = newVersions.last().versionId
        val activeVersion = newVersions.last()
        document.objects[objectId] = activeVersion.toSceneObject(
            objectId = objectId,
            isLocked = current.isLocked,
            faceViewer = current.faceViewer,
            versionHistory = history,
        )
        imageSubjectPreviews.remove(objectId)
        imageSubjectCutoutCache.remove(objectId)
        if (selectReplacedObject) {
            panel = StudioPanel.NONE
            selectedObjectId = objectId
        }
        statusMessage = message
        notifyChanged()
        return true
    }

    private fun switchObjectToLatestVersionOfType(objectId: String, type: ObjectType): Boolean {
        val current = document.objects[objectId] ?: return false
        val history = current.versionHistory ?: return false
        val target = history.versions.lastOrNull {
            it.type == type && it.versionId != history.activeVersionId
        } ?: return false
        return switchObjectVersion(objectId, target.versionId)
    }

    private fun switchObjectVersion(objectId: String, versionId: String): Boolean {
        val current = document.objects[objectId] ?: return false
        if (current.isLocked) return false
        val state = currentPage.states[objectId] ?: return false
        val history = current.versionHistory?.copyHistory() ?: return false
        if (history.activeVersionId == versionId) return false
        val target = history.versions.firstOrNull { it.versionId == versionId } ?: return false
        syncActiveVersionSnapshot(history, current, state.transform)
        cancelAnimationDraft()
        captureUndo()
        history.activeVersionId = target.versionId
        document.objects[objectId] = target.toSceneObject(
            objectId = objectId,
            isLocked = current.isLocked,
            faceViewer = current.faceViewer,
            versionHistory = history,
        )
        imageSubjectPreviews.remove(objectId)
        imageSubjectCutoutCache.remove(objectId)
        if (panel != StudioPanel.STROKE) {
            panel = StudioPanel.NONE
        }
        selectedObjectId = objectId
        statusMessage = "Switched version: ${target.displayName}"
        notifyChanged()
        return true
    }

    private fun objectHasVersionOfType(objectId: String, type: ObjectType): Boolean {
        val current = document.objects[objectId] ?: return false
        return !current.isLocked &&
            current.type != type &&
            currentPage.states.containsKey(objectId) &&
            current.versionHistory?.versions?.any { it.type == type } == true
    }

    private fun objectVersionItems(objectId: String): List<ObjectVersionUiItem> {
        val history = document.objects[objectId]?.versionHistory ?: return emptyList()
        if (history.versions.size <= 1) return emptyList()
        return history.versions.map { version ->
            ObjectVersionUiItem(
                versionId = version.versionId,
                title = version.origin.displayName,
                detail = version.displayName.take(18),
                origin = version.origin,
                type = version.type,
                active = version.versionId == history.activeVersionId,
            )
        }
    }

    private fun syncActiveVersionSnapshot(
        history: ObjectVersionHistory,
        current: SceneObject,
        transform: TransformData?,
    ) {
        val index = history.versions.indexOfFirst { it.versionId == history.activeVersionId }
        if (index < 0) return
        val existing = history.versions[index]
        history.versions[index] = current.toObjectVersion(
            versionId = existing.versionId,
            origin = existing.origin,
            transform = transform,
            recordId = existing.recordId,
            prompt = existing.prompt,
            inputSources = existing.inputSources,
            createdAt = existing.createdAt,
        )
    }

    private fun generatedImageObject(objectId: String, asset: ImportedAsset): SceneObject =
        SceneObject(
            id = objectId,
            type = ObjectType.IMAGE,
            source = asset.source,
            displayName = asset.displayName.take(24),
        )

    private fun generatedModelObject(objectId: String, asset: ImportedAsset): SceneObject =
        SceneObject(
            id = objectId,
            type = ObjectType.MODEL,
            source = asset.source,
            displayName = asset.displayName.take(24),
            textColor = ObjectColors.DEFAULT_GEOMETRY_COLOR,
        )

    fun renderObject(objectId: String): SceneObject? {
        val obj = document.objects[objectId] ?: return null
        val preview = imageSubjectPreviews[objectId] ?: return obj
        if (obj.type != ObjectType.IMAGE) return obj
        return obj.copy(source = preview.previewSource)
    }

    fun showImageSubjectPreview(
        objectId: String,
        previewSource: String,
        message: String,
        cachePreview: Boolean = true,
        preprocessMode: AigcImagePreprocessMode? = null,
    ) {
        val obj = document.objects[objectId] ?: return
        if (obj.type != ObjectType.IMAGE || obj.isLocked) return
        val originalSource = imageSubjectPreviews[objectId]?.originalSource ?: obj.source
        val preview = ImageSubjectPreview(
            objectId = objectId,
            originalSource = originalSource,
            previewSource = previewSource,
            preprocessMode = preprocessMode,
        )
        imageSubjectPreviews[objectId] = preview
        if (cachePreview) {
            imageSubjectCutoutCache[objectId] = preview
        }
        statusMessage = message
        previewRevision++
        notifyUiChanged()
    }

    fun cachedImageSubjectPreviewSource(objectId: String): String? {
        val obj = document.objects[objectId] ?: return null
        if (obj.type != ObjectType.IMAGE || obj.isLocked) return null
        val cached = imageSubjectCutoutCache[objectId] ?: return null
        if (cached.originalSource != obj.source) return null
        return cached.previewSource
    }

    fun activeImageSubjectPreviewMode(objectId: String): AigcImagePreprocessMode? =
        imageSubjectPreviews[objectId]?.preprocessMode

    fun clearImageSubjectPreviewCache(objectId: String) {
        imageSubjectCutoutCache.remove(objectId)
    }

    fun restoreImageSubjectPreview(objectId: String) {
        val preview = imageSubjectPreviews.remove(objectId) ?: return
        statusMessage = "已恢复原图：${document.objects[preview.objectId]?.displayName ?: "图片"}"
        previewRevision++
        notifyUiChanged()
    }

    fun reportImageSubjectPreviewStarted(message: String = "正在使用包内 U²-Net 主体模型生成透明图...") {
        statusMessage = message
        notifyUiChanged()
    }

    fun reportImageSubjectPreviewNoChange(message: String) {
        statusMessage = "保留主体未改变：$message"
        notifyUiChanged()
    }

    fun reportImageSubjectPreviewError(message: String) {
        statusMessage = "保留主体失败：$message"
        notifyUiChanged()
    }

    fun reportImportError(message: String) {
        statusMessage = "Import failed: $message"
        notifyUiChanged()
    }

    fun reportImportStarted(kind: ObjectType?) {
        statusMessage = when (kind) {
            ObjectType.IMAGE -> "Importing image..."
            ObjectType.MODEL -> "Importing model..."
            else -> "Importing asset..."
        }
        notifyUiChanged()
    }

    fun updateObjectManipulationInProgress(inProgress: Boolean) {
        if (objectManipulationInProgress == inProgress) return
        objectManipulationInProgress = inProgress
    }

    fun canInspectModelInPresent(objectId: String): Boolean =
        mode == AppMode.PRESENT &&
            viewpointModeObjectId == null &&
            presentAnnotationTool == PresentAnnotationTool.NONE &&
            document.objects[objectId]?.let { it.type == ObjectType.MODEL && !it.isLocked } == true &&
            currentPage.states[objectId]?.visible == true

    fun addTextObject() {
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        val objectId = newId()
        val obj = SceneObject(
            id = objectId,
            type = ObjectType.TEXT,
            source = "新增文本",
            displayName = "新增文本",
            textColor = 0xFF111111,
        )
        addObjectToCurrentPage(
            obj,
            ObjectState(transform = spawnTransform(document.objects.size).copy(scale = 0.7f)),
        )
        selectedObjectId = objectId
        panel = StudioPanel.TEXT
        activeTextEditObjectId = objectId
        statusMessage = "Added text. Objects: $objectCount"
        notifyChanged()
    }

    fun addTextFromToolbar() {
        addTextObject()
    }

    fun openSelectedTextEditor() {
        val selected = selectedObject ?: return
        if (selected.isLocked) return
        if (selected.type != ObjectType.TEXT) return
        if (panel == StudioPanel.TEXT && activeTextEditObjectId == selected.id) {
            panel = StudioPanel.NONE
            activeTextEditObjectId = null
            notifyUiChanged()
            return
        }
        panel = StudioPanel.TEXT
        activeTextEditObjectId = selected.id
        notifyUiChanged()
    }

    fun openSelectedAnimationEditor() {
        if (strokeDraft != null) return
        val objectId = selectedObjectId ?: return
        val state = currentPage.states[objectId] ?: return
        val sourceObject = document.objects[objectId] ?: return
        if (sourceObject.isLocked) return
        if (sourceObject.type == ObjectType.VIEWPOINT) return
        val animation = state.pageAnimation
        val defaultStart = frozenAnimationTransform?.takeIf { objectId == frozenAnimationObjectId } ?: state.transform
        animationDraftHadExistingAnimation = animation != null
        animationDraft = AnimationDraft(
            objectId = objectId,
            sourceObject = sourceObject.copy(),
            start = animation?.start ?: defaultStart,
            end = animation?.end ?: defaultStart.copy(px = defaultStart.px + DEFAULT_ANIMATION_END_OFFSET_X),
            durationSeconds = animation?.durationSeconds ?: PageAnimation.DEFAULT_DURATION_SECONDS,
            easing = animation?.easing ?: AnimationEasing.EASE_IN_OUT,
            loopMode = animation?.loopMode ?: AnimationLoopMode.PING_PONG,
        )
        panel = StudioPanel.ANIMATION
        activeTextEditObjectId = null
        statusMessage = "Animation endpoints ready"
        notifyUiChanged()
    }

    fun toggleSelectedAutoRotateY() {
        val objectId = selectedObjectId ?: return
        val obj = document.objects[objectId] ?: return
        if (obj.isLocked || obj.type !in setOf(ObjectType.MODEL, ObjectType.STROKE)) return
        val state = currentPage.states[objectId] ?: return
        captureUndo()
        state.autoRotateY = !state.autoRotateY
        statusMessage = if (state.autoRotateY) "Y rotation enabled" else "Y rotation disabled"
        notifyChanged()
    }

    fun toggleSelectedFaceViewer() {
        val obj = selectedObject ?: return
        if (obj.isLocked || obj.type !in setOf(ObjectType.TEXT, ObjectType.IMAGE)) return
        captureUndo()
        obj.faceViewer = !obj.faceViewer
        statusMessage = if (obj.faceViewer) "Object faces viewer" else "Object rotation restored"
        notifyChanged()
    }

    fun setCurrentPageSkybox(source: String, displayName: String) {
        val next = PageSkybox(source = source, displayName = displayName)
        if (currentPage.skybox == next) return
        captureUndo()
        currentPage.skybox = next
        statusMessage = "Skybox: $displayName"
        notifyChanged()
    }

    fun clearCurrentPageSkybox() {
        if (currentPage.skybox == null) return
        captureUndo()
        currentPage.skybox = null
        statusMessage = "Skybox removed"
        notifyChanged()
    }

    fun rotateCurrentPageSkybox(deltaRadians: Float) {
        val current = currentPage.skybox ?: return
        captureUndo()
        currentPage.skybox = current.copy(rotationY = current.rotationY + deltaRadians)
        notifyChanged()
    }

    fun resetCurrentPageSkyboxRotation() {
        val current = currentPage.skybox ?: return
        if (current.rotationY == 0f) return
        captureUndo()
        currentPage.skybox = current.copy(rotationY = 0f)
        notifyChanged()
    }

    fun updateSelectedAnimationDuration(seconds: Float) {
        updateAnimationDraftOrSelected { draft ->
            draft.copy(durationSeconds = seconds.coerceIn(PageAnimation.MIN_DURATION_SECONDS, PageAnimation.MAX_DURATION_SECONDS))
        }
    }

    fun updateSelectedAnimationEasing(easing: AnimationEasing) {
        updateAnimationDraftOrSelected { it.copy(easing = easing) }
    }

    fun updateSelectedAnimationLoopMode(loopMode: AnimationLoopMode) {
        updateAnimationDraftOrSelected { it.copy(loopMode = loopMode) }
    }

    fun confirmSelectedAnimation() {
        saveAnimationDraftAndClose(captureHistory = true, message = "Animation applied")
    }

    private fun saveAnimationDraftAndClose(captureHistory: Boolean, message: String) {
        val draft = currentAnimationDraftWithPreviews() ?: return
        if (isObjectLocked(draft.objectId)) return
        val state = currentPage.states[draft.objectId] ?: return
        if (captureHistory) {
            captureUndo()
        }
        state.pageAnimation = draft.toPageAnimation()
        state.transform = draft.start.midpoint(draft.end)
        selectedObjectId = draft.objectId
        frozenAnimationObjectId = draft.objectId
        frozenAnimationTransform = state.transform
        animationDraft = null
        endpointPreviewTransforms.clear()
        clearActiveTransform(draft.objectId)
        clearActiveTransform(draft.startEndpointId)
        clearActiveTransform(draft.endEndpointId)
        animationDraftHadExistingAnimation = true
        panel = StudioPanel.NONE
        statusMessage = message
        notifyChanged()
    }

    fun deleteSelectedAnimation() {
        val objectId = animationDraft?.objectId ?: selectedObjectId ?: return
        if (isObjectLocked(objectId)) return
        val state = currentPage.states[objectId] ?: return
        if (state.pageAnimation == null) {
            cancelAnimationDraft()
            panel = StudioPanel.NONE
            selectedObjectId = objectId
            notifyUiChanged()
            return
        }
        captureUndo()
        state.pageAnimation = null
        animationDraft = null
        animationDraftHadExistingAnimation = false
        selectedObjectId = objectId
        panel = StudioPanel.NONE
        statusMessage = "Animation deleted"
        notifyChanged()
    }

    private fun updateAnimationDraftOrSelected(update: (AnimationDraft) -> AnimationDraft) {
        val current = animationDraft
        if (current != null) {
            if (isObjectLocked(current.objectId)) return
            val next = update(current)
            if (next == current) return
            animationDraft = next
            statusMessage = "Animation draft updated"
            notifyUiChanged()
            return
        }
        val objectId = selectedObjectId ?: return
        if (isObjectLocked(objectId)) return
        val state = currentPage.states[objectId] ?: return
        val sourceObject = document.objects[objectId] ?: return
        val animation = state.pageAnimation ?: return
        val draft = AnimationDraft(
            objectId = objectId,
            sourceObject = sourceObject.copy(),
            start = animation.start,
            end = animation.end,
            durationSeconds = animation.durationSeconds,
            easing = animation.easing,
            loopMode = animation.loopMode,
        )
        val next = update(draft)
        if (next == draft) return
        captureUndo()
        state.pageAnimation = next.toPageAnimation()
        statusMessage = "Animation updated"
        notifyChanged()
    }

    fun updateSelectedText(text: String) {
        val objectId = activeTextEditObjectId ?: selectedObjectId ?: return
        updateTextObject(objectId, text, captureHistory = false, closeTextPanel = false)
    }

    fun setTextForAgent(objectId: String, text: String, captureHistory: Boolean = true): Boolean =
        updateTextObject(objectId, text, captureHistory = captureHistory, closeTextPanel = true)

    private fun updateTextObject(
        objectId: String,
        text: String,
        captureHistory: Boolean,
        closeTextPanel: Boolean,
    ): Boolean {
        val obj = document.objects[objectId] ?: return false
        if (obj.isLocked) return false
        if (obj.type != ObjectType.TEXT) return false
        val nextText = text.ifBlank { "新增文本" }
        val changed = obj.source != nextText || obj.displayName != nextText.take(12)
        if (changed && captureHistory) {
            captureUndo()
        }
        if (changed) {
            obj.source = nextText
            obj.displayName = obj.source.take(12)
        }
        selectedObjectId = objectId
        if (closeTextPanel) {
            panel = StudioPanel.NONE
            activeTextEditObjectId = null
        }
        statusMessage = "Updated text"
        if (changed) {
            notifyChanged()
        } else {
            notifyUiChanged()
        }
        return true
    }

    fun openStrokeTool() {
        if (mode != AppMode.EDIT || viewpointModeObjectId != null) return
        closeWhiteboardTool()
        cancelAnimationDraft()
        clearSelection()
        strokeDraft = strokeDraft ?: StrokeDraft()
        panel = StudioPanel.STROKE
        statusMessage = if (isWhiteboard) {
            "Whiteboard brush: pinch in space; finish to create one grouped object"
        } else {
            "Draw mode: pinch in space to add strokes"
        }
        notifyUiChanged()
    }

    fun setStrokeColor(color: Long) {
        val current = strokeDraft ?: StrokeDraft()
        strokeDraft = current.copy(selectedColor = color)
        statusMessage = "Stroke color selected"
        notifyUiChanged()
    }

    fun beginStrokePath(point: StrokePoint) {
        val current = strokeDraft ?: StrokeDraft()
        strokeDraft = current.copy(
            activePath = StrokePath(
                color = current.selectedColor,
                points = mutableListOf(point),
            ),
            redoPaths = emptyList(),
        )
        notifyUiChanged()
    }

    fun appendStrokePoint(point: StrokePoint, notify: Boolean = true): Boolean {
        val current = strokeDraft ?: return false
        val active = current.activePath ?: return false
        val last = active.points.lastOrNull()
        if (last != null && last.distanceTo(point) < STROKE_POINT_MIN_DISTANCE) return false
        active.points.add(point)
        if (!notify) return true
        strokeDraft = current.copy(activePath = active.copyPath())
        notifyUiChanged()
        return true
    }

    fun endStrokePath() {
        val current = strokeDraft ?: return
        val active = current.activePath
        strokeDraft = if (active != null && active.isDrawableStrokePath()) {
            current.copy(paths = current.paths + active.copyPath(), activePath = null)
        } else {
            current.copy(activePath = null)
        }
        statusMessage = "Stroke paths: ${strokeDraft?.committedStrokeCount ?: 0}"
        notifyUiChanged()
    }

    fun cancelActiveStrokePath() {
        val current = strokeDraft ?: return
        if (current.activePath == null) return
        strokeDraft = current.copy(activePath = null)
        notifyUiChanged()
    }

    fun undoLastStrokePath() {
        val current = strokeDraft ?: return
        strokeDraft = if (current.activePath != null) {
            current.copy(activePath = null)
        } else {
            val removed = current.paths.lastOrNull()
            current.copy(
                paths = current.paths.dropLast(1),
                redoPaths = if (removed == null) current.redoPaths else current.redoPaths + removed.copyPath(),
            )
        }
        statusMessage = "Stroke paths: ${strokeDraft?.committedStrokeCount ?: 0}"
        notifyUiChanged()
    }

    fun redoLastStrokePath() {
        val current = strokeDraft ?: return
        val restored = current.redoPaths.lastOrNull() ?: return
        strokeDraft = current.copy(
            paths = current.paths + restored.copyPath(),
            redoPaths = current.redoPaths.dropLast(1),
            activePath = null,
        )
        statusMessage = "Stroke paths: ${strokeDraft?.committedStrokeCount ?: 0}"
        notifyUiChanged()
    }

    fun finishStrokeDraft() {
        val draft = strokeDraft ?: run {
            panel = StudioPanel.NONE
            notifyUiChanged()
            return
        }
        val data = draft.toStrokeData()
        strokeDraft = null
        panel = StudioPanel.NONE
        if (data.strokes.isEmpty()) {
            statusMessage = "Stroke discarded"
            notifyUiChanged()
            return
        }
        captureUndo()
        val bounds = data.bounds()
        val objectId = newId()
        val centered = data.centered(bounds.center)
        val obj = SceneObject(
            id = objectId,
            type = ObjectType.STROKE,
            source = "stroke://${data.strokes.size}",
            displayName = "画笔",
            textColor = data.strokes.firstOrNull()?.color ?: ObjectColors.STROKE_PALETTE.first(),
            strokeData = centered,
        )
        addObjectToCurrentPage(
            obj,
            ObjectState(
                transform = TransformData(
                    px = bounds.center.x,
                    py = bounds.center.y,
                    pz = bounds.center.z,
                    scale = 1f,
                ),
            ),
        )
        selectedObjectId = objectId
        statusMessage = "Stroke object added: ${data.pointCount} points"
        notifyChanged()
    }

    fun updateSelectedTextColor(color: Long) {
        val selected = selectedObject ?: return
        if (selected.isLocked) return
        val targets = when (selected.type) {
            ObjectType.TEXT,
            ObjectType.STROKE,
            -> listOf(selected)
            ObjectType.IMAGE,
            ObjectType.VIEWPOINT,
            -> emptyList()
            ObjectType.MODEL -> if (selected.source.startsWith("preset://geometry/")) listOf(selected) else emptyList()
        }
        if (targets.isEmpty()) return
        captureUndo()
        targets.forEach { it.textColor = color }
        targets
            .filter { it.type == ObjectType.STROKE }
            .forEach { stroke ->
                stroke.strokeData = stroke.strokeData?.copyData()?.also { data ->
                    data.strokes.forEachIndexed { index, path ->
                        data.strokes[index] = path.copy(color = color)
                    }
                }
            }
        statusMessage = if (selected.type == ObjectType.MODEL) {
            "Updated object color"
        } else {
            "Updated text color"
        }
        notifyChanged()
    }

    fun lockSelectedObject() {
        val selected = selectedObject ?: return
        val memberIds = selectedObjectId?.let(::groupMemberIds).orEmpty()
        val members = memberIds.mapNotNull(document.objects::get)
        if (members.isEmpty() || members.all { it.isLocked }) return
        cancelAnimationDraft()
        captureUndo()
        members.forEach { it.isLocked = true }
        panel = StudioPanel.NONE
        activeTextEditObjectId = null
        activeRotationObjectId = null
        activeRotationLatest = null
        activeTransformObjectId = null
        activeTransformBase = null
        activeTransformLatest = null
        statusMessage = if (members.size > 1) "Locked model group" else "Locked ${selected.displayName}"
        notifyChanged()
    }

    fun unlockSelectedObject() {
        val selected = selectedObject ?: return
        val memberIds = selectedObjectId?.let(::groupMemberIds).orEmpty()
        val members = memberIds.mapNotNull(document.objects::get)
        if (members.none { it.isLocked }) return
        captureUndo()
        members.forEach { it.isLocked = false }
        panel = StudioPanel.NONE
        activeTextEditObjectId = null
        statusMessage = if (members.size > 1) "Unlocked model group" else "Unlocked ${selected.displayName}"
        notifyChanged()
    }

    fun deleteSelectedObject() {
        val objectId = selectedObjectId ?: return
        val objectIds = groupMemberIds(objectId)
        if (objectIds.any(::isObjectLocked)) return
        if (viewpointModeObjectId in objectIds) {
            exitViewpoint()
        }
        captureUndo()
        objectIds.forEach { memberId ->
            currentPage.states.remove(memberId)
            imageSubjectPreviews.remove(memberId)
            imageSubjectCutoutCache.remove(memberId)
        }
        objectIds.forEach(::removeObjectDefinitionIfUnused)
        clearSelection()
        if (activeTextEditObjectId == objectId) {
            panel = StudioPanel.NONE
            activeTextEditObjectId = null
        }
        statusMessage = if (objectIds.size > 1) {
            "Deleted model group (${objectIds.size} objects). Objects: $objectCount"
        } else {
            "Deleted object on this page. Objects: $objectCount"
        }
        notifyPageChanged()
    }

    fun clearCurrentPageObjects(): Int {
        if (currentPage.states.isEmpty()) return 0
        val objectIds = currentPage.states.keys.toList()
        if (viewpointModeObjectId in objectIds) {
            exitViewpoint()
        }
        cancelAnimationDraft()
        cancelStrokeDraft()
        closeWhiteboardTool()
        captureUndo()
        objectIds.forEach { objectId ->
            currentPage.states.remove(objectId)
            imageSubjectPreviews.remove(objectId)
            imageSubjectCutoutCache.remove(objectId)
        }
        objectIds.forEach(::removeObjectDefinitionIfUnused)
        clearSelection()
        panel = StudioPanel.NONE
        activeTextEditObjectId = null
        statusMessage = "Cleared ${objectIds.size} objects on this page. Objects: $objectCount"
        notifyPageChanged()
        return objectIds.size
    }

    fun groupAllVisibleModels(): SceneGroupingResult? {
        val modelIds = currentPage.states.entries
            .filter { (_, state) -> state.visible }
            .mapNotNull { (objectId, _) ->
                objectId.takeIf { document.objects[objectId]?.type == ObjectType.MODEL }
            }
        if (modelIds.size < 2) return null
        captureUndo()
        val groupId = "g${UUID.randomUUID().toString().replace("-", "")}"
        modelIds.forEach { objectId -> currentPage.states[objectId]?.groupId = groupId }
        selectedObjectId = modelIds.first()
        statusMessage = "Grouped ${modelIds.size} models"
        notifyChanged()
        return SceneGroupingResult(groupId = groupId, objectIds = modelIds)
    }

    fun ungroupAllObjects(): SceneUngroupingResult {
        val groupedStates = currentPage.states.values.filter { it.groupId != null }
        if (groupedStates.isEmpty()) return SceneUngroupingResult(objectCount = 0, groupCount = 0)
        val groupCount = groupedStates.mapNotNull { it.groupId }.distinct().size
        captureUndo()
        groupedStates.forEach { it.groupId = null }
        statusMessage = "Ungrouped ${groupedStates.size} objects"
        notifyChanged()
        return SceneUngroupingResult(
            objectCount = groupedStates.size,
            groupCount = groupCount,
        )
    }

    fun duplicateSelectedObject() {
        val sourceId = selectedObjectId ?: return
        val source = document.objects[sourceId] ?: return
        if (source.isLocked) return
        if (source.type == ObjectType.VIEWPOINT && viewpointModeObjectId == sourceId) {
            exitViewpoint()
        }
        captureUndo()
        val duplicateId = newId()
        val duplicate = source.copy(
            id = duplicateId,
            displayName = "${source.displayName} Copy",
            strokeData = source.strokeData?.copyData(),
            versionHistory = source.versionHistory?.copyHistory(),
        )
        document.objects[duplicateId] = duplicate
        document.pages.forEach { page ->
            page.states[sourceId]?.copyState()?.let { copied ->
                copied.transform = copied.transform.copy(
                    px = copied.transform.px + 0.55f,
                    pz = copied.transform.pz - 0.08f,
                )
                page.states[duplicateId] = copied
            }
        }
        imageSubjectCutoutCache[sourceId]
            ?.takeIf { it.originalSource == source.source }
            ?.let { imageSubjectCutoutCache[duplicateId] = it.copy(objectId = duplicateId) }
        selectedObjectId = duplicateId
        statusMessage = "Duplicated ${source.displayName}. Objects: $objectCount"
        notifyChanged()
    }

    fun createPresetShow(type: DocumentType) {
        cancelStrokeDraft()
        captureUndo()
        val next = newPresetDocument(shows.size + 1, type)
        shows.add(next)
        activeShowIndex = shows.lastIndex
        document = next
        currentPageIndex = 0
        clearSelection()
        mode = AppMode.EDIT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Created ${next.name}"
        notifyChanged()
    }

    fun createCuriosityExampleShow(assets: CuriosityExampleAssets) {
        cancelStrokeDraft()
        captureUndo()
        val next = createCuriosityExampleDocument(shows.size + 1, assets)
        shows.add(next)
        activeShowIndex = shows.lastIndex
        document = next
        currentPageIndex = 0
        clearSelection()
        mode = AppMode.EDIT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Created ${next.name}"
        notifyChanged()
    }

    fun presentCuriosityExample(assets: CuriosityExampleAssets) {
        presentBuiltInExample(
            preset = HomeExamplePreset.CURIOSITY_ASSEMBLY,
            next = createCuriosityExampleDocument(shows.size + 1, assets),
        )
    }

    fun presentImmersiveInteriorExample(assets: ImmersiveInteriorExampleAssets) {
        presentBuiltInExample(
            preset = HomeExamplePreset.IMMERSIVE_INTERIOR,
            next = createImmersiveInteriorExampleDocument(shows.size + 1, assets),
        )
    }

    fun presentDinosaurDetectiveExample(assets: DinosaurDetectiveExampleAssets) {
        presentBuiltInExample(
            preset = HomeExamplePreset.DINOSAUR_DETECTIVE,
            next = createDinosaurDetectiveExampleDocument(shows.size + 1, assets),
        )
    }

    fun presentExamplePlaceholder(preset: HomeExamplePreset) {
        val next = newPresetDocument(shows.size + 1, DocumentType.PRESENTATION).apply {
            name = when (preset) {
                HomeExamplePreset.CURIOSITY_ASSEMBLY -> "把好奇号装起来"
                HomeExamplePreset.FOUR_STROKE_ENGINE -> "发动机为什么会转"
                HomeExamplePreset.IMMERSIVE_INTERIOR -> "走进尚未建成的家"
                HomeExamplePreset.DINOSAUR_DETECTIVE -> "恐龙侦探：化石里藏着什么？"
            }
        }
        presentBuiltInExample(preset, next)
    }

    private fun presentBuiltInExample(preset: HomeExamplePreset, next: ShowDocument) {
        cancelStrokeDraft()
        clearSelection()
        builtInExampleReturnPageIndex = currentPageIndex
        builtInExamplePreset = preset
        document = next
        currentPageIndex = 0
        mode = AppMode.PRESENT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        presentAnnotationTool = PresentAnnotationTool.NONE
        annotationStrokeDraft = null
        annotationCommentDraft = null
        editingAnnotationComment = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Playing ${next.name}"
        notifyPageChanged(documentChanged = false)
    }

    /**
     * Replaces an older built-in Curiosity sample exactly once. Besides the v6
     * asset marker, the staged suspension spacing distinguishes the corrected
     * layout from the first v6 document written to storage.
     */
    fun upgradeLegacyCuriosityExample(assets: CuriosityExampleAssets): Boolean {
        var upgraded = false
        shows.indices.forEach { index ->
            val current = shows[index]
            val isCuriosityExample = current.name == "把好奇号装起来" ||
                current.name.startsWith("把好奇号装起来_")
            val stagedLeftX = current.pages.getOrNull(2)
                ?.states
                ?.get("suspension_left")
                ?.transform
                ?.px
            val stagedRightX = current.pages.getOrNull(2)
                ?.states
                ?.get("suspension_right")
                ?.transform
                ?.px
            val hasCurrentLayout = "rover_animated_clean" in current.objects &&
                current.name == "把好奇号装起来" &&
                stagedLeftX != null &&
                stagedRightX != null &&
                stagedLeftX >= 0.5f &&
                stagedRightX <= -0.5f &&
                current.objects["curiosity_title_1"]?.source == "写在前面" &&
                current.objects["curiosity_title_1"]?.textBackgroundEnabled == true &&
                current.pages.getOrNull(5)?.annotations?.any { it.id == "p5_right" } == true
            if (
                !isCuriosityExample ||
                hasCurrentLayout
            ) {
                return@forEach
            }
            shows[index] = createCuriosityExampleDocument(
                index = index + 1,
                assets = assets,
                documentId = current.id,
                documentName = "把好奇号装起来",
            )
            upgraded = true
        }
        if (!upgraded) return false
        document = shows[activeShowIndex]
        currentPageIndex = 0
        clearSelection()
        mode = AppMode.EDIT
        panel = StudioPanel.NONE
        statusMessage = "Restored and upgraded Curiosity example"
        notifyChanged()
        return true
    }

    fun newShow(type: DocumentType = DocumentType.PRESENTATION) {
        cancelStrokeDraft()
        captureUndo()
        discardActiveShowIfEmpty()
        val next = newDocument(shows.size + 1, type)
        shows.add(next)
        activeShowIndex = shows.lastIndex
        document = next
        currentPageIndex = 0
        clearSelection()
        mode = AppMode.EDIT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Created ${next.name}"
        notifyChanged()
    }

    fun duplicateShow(index: Int) {
        if (index !in shows.indices) return
        captureUndo()
        val source = shows[index]
        val duplicate = source.deepCopyAsNew(
            id = newId(),
            name = uniqueCopyName(source.name),
        )
        shows.add(index + 1, duplicate)
        activeShowIndex = activeShowIndex.let { current ->
            if (current > index) current + 1 else current
        }
        document = shows[activeShowIndex]
        clearSelection()
        panel = StudioPanel.NONE
        statusMessage = "Copied ${source.name}"
        notifyChanged()
    }

    fun renameShow(index: Int, nextName: String) {
        if (index !in shows.indices) return
        val cleaned = nextName.trim().ifBlank { shows[index].name }
        if (cleaned == shows[index].name) return
        captureUndo()
        shows[index].name = cleaned.take(MAX_SHOW_NAME_LENGTH)
        statusMessage = "Renamed show"
        notifyChanged()
    }

    fun deleteShow(index: Int) {
        if (index !in shows.indices) return
        if (shows.size == 1 && screen != AppScreen.HOME) return
        captureUndo()
        val removedName = shows[index].name
        if (shows.size == 1) {
            shows.clear()
            activeShowIndex = 0
            document = newDocument(1)
            currentPageIndex = 0
            clearSelection()
            panel = StudioPanel.NONE
            statusMessage = "Deleted $removedName"
            notifyPageChanged()
            return
        } else {
            shows.removeAt(index)
            activeShowIndex = when {
                activeShowIndex > index -> activeShowIndex - 1
                activeShowIndex >= shows.size -> shows.lastIndex
                else -> activeShowIndex
            }
        }
        document = shows[activeShowIndex]
        currentPageIndex = 0
        clearSelection()
        panel = if (screen == AppScreen.HOME) StudioPanel.NONE else StudioPanel.FILES
        statusMessage = "Deleted $removedName"
        notifyPageChanged()
    }

    fun switchShow(index: Int) {
        if (index !in shows.indices || index == activeShowIndex) return
        val targetId = shows[index].id
        discardActiveShowIfEmpty()
        val targetIndex = shows.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return
        activeShowIndex = targetIndex
        document = shows[targetIndex]
        currentPageIndex = currentPageIndex.coerceAtMost(document.pages.lastIndex)
        clearSelection()
        cancelStrokeDraft()
        mode = AppMode.EDIT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Opened ${document.name}"
        notifyChanged()
    }

    fun openShowFromHome(index: Int) {
        if (index !in shows.indices) return
        builtInExamplePreset = null
        if (index != activeShowIndex) {
            activeShowIndex = index
            document = shows[index]
            currentPageIndex = currentPageIndex.coerceAtMost(document.pages.lastIndex)
        }
        clearSelection()
        cancelStrokeDraft()
        mode = AppMode.EDIT
        animationsPaused = false
        viewpointModeObjectId = null
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        panel = StudioPanel.NONE
        screen = AppScreen.EDITOR
        statusMessage = "Opened ${document.name}"
        notifyChanged()
    }

    fun returnHome() {
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        clearSelection()
        if (builtInExamplePreset != null) {
            builtInExamplePreset = null
            document = shows.getOrNull(activeShowIndex) ?: newDocument(1)
            currentPageIndex = builtInExampleReturnPageIndex.coerceIn(0, document.pages.lastIndex)
            builtInExampleReturnPageIndex = 0
        } else {
            discardActiveShowIfEmpty()
        }
        mode = AppMode.EDIT
        panel = StudioPanel.NONE
        whiteboardTool = WhiteboardTool.NONE
        annotationCommentDraft = null
        activeTextEditObjectId = null
        screen = AppScreen.HOME
        notifyUiChanged()
    }

    fun undo() {
        if (activeEditorBatch != null) return
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.add(snapshot())
        viewpointModeObjectId = null
        restore(snapshot)
        statusMessage = "Undo"
        notifyChanged()
    }

    fun redo() {
        if (activeEditorBatch != null) return
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.add(snapshot())
        viewpointModeObjectId = null
        restore(snapshot)
        statusMessage = "Redo"
        notifyChanged()
    }

    fun reportSceneError(message: String) {
        statusMessage = message
    }

    fun restoreSavedState(state: SavedStudioState): Boolean {
        if (state.shows.isEmpty() && !state.restoredFromStorage) return false
        activeEditorBatch = null
        builtInExamplePreset = null
        builtInExampleReturnPageIndex = 0
        shows.clear()
        shows.addAll(state.shows.map { it.deepCopy() })
        if (shows.isEmpty()) {
            activeShowIndex = 0
            document = newDocument(1)
            currentPageIndex = 0
            clearSelection()
            mode = AppMode.EDIT
            panel = StudioPanel.NONE
            screen = AppScreen.HOME
            activeTextEditObjectId = null
            undoStack.clear()
            redoStack.clear()
            statusMessage = "No saved files"
            notifyPageChanged(documentChanged = false)
            return true
        }
        activeShowIndex = state.activeShowId
            ?.let { id -> shows.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        document = shows[activeShowIndex]
        currentPageIndex = 0
        clearSelection()
        mode = AppMode.EDIT
        panel = StudioPanel.NONE
        screen = AppScreen.HOME
        activeTextEditObjectId = null
        undoStack.clear()
        redoStack.clear()
        statusMessage = buildString {
            append("Loaded ${shows.size} saved show${if (shows.size == 1) "" else "s"}")
            if (state.skippedFileCount > 0) {
                append("; skipped ${state.skippedFileCount} broken file${if (state.skippedFileCount == 1) "" else "s"}")
            }
        }
        notifyPageChanged(documentChanged = false)
        return true
    }

    fun reportStorageError(message: String) {
        statusMessage = "Save error: $message"
        notifyUiChanged()
    }

    fun beginAgentEditBatch() {
        if (activeEditorBatch != null) return
        activeEditorBatch = EditorBatch(
            startSnapshot = snapshot(),
            undoStackBefore = undoStack.map { it.deepCopy() },
            redoStackBefore = redoStack.map { it.deepCopy() },
        )
    }

    fun commitAgentEditBatch(): Boolean {
        val batch = activeEditorBatch ?: return false
        activeEditorBatch = null
        if (!batch.hasCapturedMutation) return false
        if (snapshot() == batch.startSnapshot) return false
        pushUndoSnapshot(batch.startSnapshot)
        return true
    }

    fun rollbackAgentEditBatch() {
        val batch = activeEditorBatch ?: return
        activeEditorBatch = null
        undoStack.clear()
        undoStack.addAll(batch.undoStackBefore.map { it.deepCopy() })
        redoStack.clear()
        redoStack.addAll(batch.redoStackBefore.map { it.deepCopy() })
        viewpointModeObjectId = null
        restore(batch.startSnapshot)
        statusMessage = "Agent task rolled back"
        notifyChanged()
    }

    fun savedState(): SavedStudioState =
        SavedStudioState(
            shows = shows.filterNot { it.hasNoElements() }.map { it.deepCopy() },
            activeShowId = shows.getOrNull(activeShowIndex)?.takeUnless { it.hasNoElements() }?.id,
        )

    private fun discardActiveShowIfEmpty() {
        val current = shows.getOrNull(activeShowIndex) ?: return
        if (!current.hasNoElements()) return
        shows.removeAt(activeShowIndex)
        if (shows.isEmpty()) {
            activeShowIndex = 0
            document = newDocument(1)
        } else {
            activeShowIndex = activeShowIndex.coerceAtMost(shows.lastIndex)
            document = shows[activeShowIndex]
        }
        currentPageIndex = 0
        clearSelection()
    }

    fun addPage() {
        if (isWhiteboard) return
        if (!canAddPage) return
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        captureUndo()
        val next = currentPage.deepCopy(newId())
        next.annotations.clear()
        document.pages.add(currentPageIndex + 1, next)
        currentPageIndex += 1
        clearSelection()
        notifyPageChanged(documentChanged = true)
    }

    fun duplicateCurrentPage() {
        if (isWhiteboard) return
        if (!canAddPage) return
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        captureUndo()
        val duplicate = currentPage.deepCopy(newId())
        document.pages.add(currentPageIndex + 1, duplicate)
        currentPageIndex += 1
        clearSelection()
        notifyPageChanged(documentChanged = true)
    }

    fun deleteCurrentPage() {
        if (isWhiteboard) return
        if (!canDeletePage) return
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        captureUndo()
        document.pages.removeAt(currentPageIndex)
        currentPageIndex = currentPageIndex.coerceAtMost(document.pages.lastIndex)
        clearSelection()
        notifyPageChanged(documentChanged = true)
    }

    fun goToPage(index: Int) {
        if (isWhiteboard) return
        if (index !in document.pages.indices || index == currentPageIndex) return
        exitViewpoint()
        cancelAnimationDraft()
        cancelStrokeDraft()
        annotationStrokeDraft = null
        annotationCommentDraft = null
        currentPageIndex = index
        clearSelection()
        notifyPageChanged(documentChanged = false)
    }

    fun nextPage() {
        goToPage((currentPageIndex + 1).coerceAtMost(document.pages.lastIndex))
    }

    fun previousPage() {
        goToPage((currentPageIndex - 1).coerceAtLeast(0))
    }

    fun currentTransform(objectId: String): TransformData? =
        endpointPreviewTransforms[objectId] ?:
        animationDraftEndpoint(objectId)?.transform ?:
        frozenAnimationTransform?.takeIf { frozenAnimationObjectId == objectId } ?:
        currentPage.states[objectId]?.transform

    fun activeManipulationTransform(objectId: String): TransformData? =
        activeTransformLatest.takeIf { activeTransformObjectId == objectId }

    fun activeManipulationBaseTransform(objectId: String): TransformData? =
        activeTransformBase.takeIf { activeTransformObjectId == objectId }

    fun beginTransformPreview(objectId: String, visibleTransform: TransformData? = null) {
        if (viewpointModeObjectId != null) return
        if (isTransformLocked(objectId)) return
        val existingBase = activeTransformBase.takeIf { activeTransformObjectId == objectId }
        animationDraftEndpoint(objectId)?.let {
            val current = visibleTransform ?: currentTransform(objectId) ?: return
            activeTransformObjectId = objectId
            activeTransformBase = existingBase ?: current
            activeTransformLatest = current
            endpointPreviewTransforms[objectId] = current
            previewRevision++
            return
        }
        val current = currentPage.states[objectId] ?: return
        val previewStart = visibleTransform
            ?: activeTransformLatest.takeIf { activeTransformObjectId == objectId }
            ?: frozenAnimationTransform?.takeIf { frozenAnimationObjectId == objectId }
            ?: current.transform
        activeTransformObjectId = objectId
        activeTransformBase = existingBase ?: previewStart
        activeTransformLatest = previewStart
        if (existingBase == null) {
            activeGroupTransformBases.clear()
            activeGroupPreviewTransforms.clear()
            groupMemberIds(objectId).forEach { memberId ->
                currentPage.states[memberId]?.transform?.let { memberTransform ->
                    activeGroupTransformBases[memberId] = if (memberId == objectId) previewStart else memberTransform
                }
            }
            activeGroupPreviewTransforms.putAll(activeGroupTransformBases)
        }
        if (current.pageAnimation != null) {
            frozenAnimationObjectId = objectId
            frozenAnimationTransform = previewStart
        }
    }

    fun previewTransform(objectId: String, next: TransformData) {
        if (viewpointModeObjectId != null) return
        if (isTransformLocked(objectId)) return
        val clamped = clampTransform(next)
        if (activeTransformObjectId == objectId) {
            activeTransformLatest = clamped
            val anchorBase = activeTransformBase ?: currentPage.states[objectId]?.transform
            if (anchorBase != null && activeGroupTransformBases.size > 1) {
                activeGroupPreviewTransforms.clear()
                activeGroupPreviewTransforms.putAll(
                    transformGroupFromAnchor(
                        anchorId = objectId,
                        anchorBase = anchorBase,
                        anchorNext = clamped,
                        memberBases = activeGroupTransformBases,
                    )
                )
            }
        }
        if (previewAnimationEndpointTransform(objectId, clamped)) return
        if (frozenAnimationObjectId == objectId) {
            frozenAnimationTransform = clamped
        }
    }

    fun cancelTransformPreview(objectId: String, original: TransformData) {
        if (activeTransformObjectId == objectId) {
            activeTransformObjectId = null
            activeTransformBase = null
            activeTransformLatest = null
            activeGroupTransformBases.clear()
            activeGroupPreviewTransforms.clear()
        }
        if (frozenAnimationObjectId == objectId) {
            frozenAnimationTransform = original
        }
        if (endpointPreviewTransforms.remove(objectId) != null) {
            previewRevision++
        }
    }

    fun commitTransform(objectId: String, next: TransformData) {
        if (viewpointModeObjectId != null) return
        if (isTransformLocked(objectId)) return
        if (commitAnimationEndpointTransform(objectId, next)) {
            clearActiveTransform(objectId)
            return
        }
        val current = currentPage.states[objectId] ?: return
        val clamped = clampTransform(next)
        val baseTransform = activeTransformBase.takeIf { activeTransformObjectId == objectId }
            ?: frozenAnimationTransform?.takeIf { frozenAnimationObjectId == objectId }
            ?: current.transform
        val memberBases = activeGroupTransformBases.takeIf { it.isNotEmpty() }?.toMap()
            ?: groupMemberIds(objectId).mapNotNull { memberId ->
                currentPage.states[memberId]?.transform?.let { memberId to it }
            }.toMap()
        val transformedMembers = transformGroupFromAnchor(
            anchorId = objectId,
            anchorBase = baseTransform,
            anchorNext = clamped,
            memberBases = memberBases,
        )
        clearActiveTransform(objectId)
        if (baseTransform.approximatelyEquals(clamped)) return
        captureUndo()
        transformedMembers.forEach { (memberId, memberNext) ->
            val memberState = currentPage.states[memberId] ?: return@forEach
            val memberBase = if (memberId == objectId) baseTransform else memberBases[memberId] ?: memberState.transform
            memberState.pageAnimation = memberState.pageAnimation?.shiftedBy(memberBase, memberNext)
            memberState.transform = memberNext
        }
        if (frozenAnimationObjectId == objectId) {
            frozenAnimationTransform = clamped
        }
        notifyChanged()
    }

    private fun clearActiveTransform(objectId: String) {
        if (activeTransformObjectId != objectId) return
        activeTransformObjectId = null
        activeTransformBase = null
        activeTransformLatest = null
        activeGroupTransformBases.clear()
        activeGroupPreviewTransforms.clear()
    }

    private fun transformGroupFromAnchor(
        anchorId: String,
        anchorBase: TransformData,
        anchorNext: TransformData,
        memberBases: Map<String, TransformData>,
    ): Map<String, TransformData> {
        if (memberBases.size <= 1) return mapOf(anchorId to anchorNext)
        val rotationDelta = anchorNext.rotationY - anchorBase.rotationY
        val scaleFactor = if (anchorBase.scale == 0f) 1f else anchorNext.scale / anchorBase.scale
        val cosine = kotlin.math.cos(rotationDelta)
        val sine = kotlin.math.sin(rotationDelta)
        return memberBases.mapValues { (memberId, memberBase) ->
            if (memberId == anchorId) {
                anchorNext
            } else {
                val relativeX = (memberBase.px - anchorBase.px) * scaleFactor
                val relativeZ = (memberBase.pz - anchorBase.pz) * scaleFactor
                clampTransform(
                    memberBase.copy(
                        px = anchorNext.px + relativeX * cosine + relativeZ * sine,
                        py = anchorNext.py + (memberBase.py - anchorBase.py) * scaleFactor,
                        pz = anchorNext.pz - relativeX * sine + relativeZ * cosine,
                        rotationY = memberBase.rotationY + rotationDelta,
                        scale = memberBase.scale * scaleFactor,
                    )
                )
            }
        }
    }

    fun beginSelectedRotation() {
        val objectId = selectedObjectId ?: return
        if (isObjectLocked(objectId)) return
        activeRotationObjectId = objectId
        activeRotationLatest = currentTransform(objectId)
        beginTransformPreview(objectId, activeRotationLatest)
    }

    fun previewSelectedRotation(deltaRadians: Float): Pair<String, TransformData>? {
        val objectId = activeRotationObjectId ?: selectedObjectId ?: return null
        if (isObjectLocked(objectId)) return null
        val base = activeRotationLatest ?: currentTransform(objectId) ?: return null
        val next = base.copy(rotationY = base.rotationY + deltaRadians)
        activeRotationObjectId = objectId
        activeRotationLatest = next
        previewTransform(objectId, next)
        return objectId to next
    }

    fun commitSelectedRotation(): Pair<String, TransformData>? {
        val objectId = activeRotationObjectId ?: return null
        if (isObjectLocked(objectId)) {
            activeRotationObjectId = null
            activeRotationLatest = null
            return null
        }
        val latest = activeRotationLatest ?: return null
        activeRotationObjectId = null
        activeRotationLatest = null
        commitTransform(objectId, latest)
        return objectId to latest
    }

    private fun animationDraftEndpoint(endpointId: String): AnimationEndpointDraft? =
        animationDraft?.endpoints()?.firstOrNull { it.id == endpointId }

    private fun isTransformLocked(objectId: String): Boolean {
        if (isObjectLocked(objectId)) return true
        val groupId = currentPage.states[objectId]?.groupId ?: return false
        return currentPage.states.any { (memberId, state) ->
            state.groupId == groupId && isObjectLocked(memberId)
        }
    }

    private fun currentAnimationDraftWithPreviews(): AnimationDraft? {
        val draft = animationDraft ?: return null
        val start = endpointPreviewTransforms[draft.startEndpointId] ?: draft.start
        val end = endpointPreviewTransforms[draft.endEndpointId] ?: draft.end
        return draft.copy(start = start, end = end)
    }

    private fun previewAnimationEndpointTransform(endpointId: String, next: TransformData): Boolean {
        animationDraftEndpoint(endpointId) ?: return false
        endpointPreviewTransforms[endpointId] = clampTransform(next)
        return true
    }

    private fun commitAnimationEndpointTransform(endpointId: String, next: TransformData): Boolean {
        val draft = animationDraft ?: return false
        val endpoint = animationDraftEndpoint(endpointId) ?: return false
        val clamped = clampTransform(next)
        endpointPreviewTransforms.remove(endpointId)
        animationDraft = when (endpoint.role) {
            AnimationEndpointRole.START -> draft.copy(start = clamped)
            AnimationEndpointRole.END -> draft.copy(end = clamped)
        }
        statusMessage = "Animation ${endpoint.label} updated"
        previewRevision++
        notifyUiChanged()
        return true
    }

    private fun PageAnimation.shiftedBy(from: TransformData, to: TransformData): PageAnimation {
        val dx = to.px - from.px
        val dy = to.py - from.py
        val dz = to.pz - from.pz
        val dRotation = to.rotationY - from.rotationY
        val scaleFactor = if (from.scale != 0f) to.scale / from.scale else 1f
        fun shift(transform: TransformData): TransformData =
            clampTransform(
                transform.copy(
                    px = transform.px + dx,
                    py = transform.py + dy,
                    pz = transform.pz + dz,
                    rotationY = transform.rotationY + dRotation,
                    scale = transform.scale * scaleFactor,
                )
            )
        return copy(start = shift(start), end = shift(end))
    }

    private fun cancelAnimationDraft() {
        val draft = animationDraft ?: return
        if (selectedObjectId in listOf(draft.startEndpointId, draft.endEndpointId)) {
            selectedObjectId = draft.objectId
        }
        animationDraft = null
        animationDraftHadExistingAnimation = false
        frozenAnimationObjectId = draft.objectId
        endpointPreviewTransforms.clear()
        clearActiveTransform(draft.startEndpointId)
        clearActiveTransform(draft.endEndpointId)
        statusMessage = "Animation draft cancelled"
        notifyUiChanged()
    }

    private fun cancelStrokeDraft() {
        if (strokeDraft == null) return
        strokeDraft = null
        if (panel == StudioPanel.STROKE) {
            panel = StudioPanel.NONE
        }
        statusMessage = "Stroke draft cancelled"
        notifyUiChanged()
    }

    private fun addObjectToCurrentPage(obj: SceneObject, initial: ObjectState) {
        document.objects[obj.id] = obj
        currentPage.states[obj.id] = initial.copyState()
    }

    private fun removeObjectDefinitionIfUnused(objectId: String) {
        if (document.pages.any { it.states.containsKey(objectId) }) return
        document.objects.remove(objectId)
    }

    private fun notifyChanged() {
        revision++
        saveRevision++
    }

    private fun notifyUiChanged() {
        revision++
    }

    private fun notifyPageChanged(documentChanged: Boolean = true) {
        pageRevision++
        if (documentChanged) {
            notifyChanged()
        } else {
            notifyUiChanged()
        }
    }

    private fun captureUndo() {
        activeEditorBatch?.let { batch ->
            batch.hasCapturedMutation = true
            return
        }
        pushUndoSnapshot(snapshot())
    }

    private fun pushUndoSnapshot(snapshot: EditorSnapshot) {
        undoStack.add(snapshot.deepCopy())
        if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        redoStack.clear()
    }

    private fun snapshot(): EditorSnapshot = EditorSnapshot(
        shows = shows.map { it.deepCopy() },
        activeShowIndex = activeShowIndex,
        currentPageIndex = currentPageIndex,
        selectedObjectId = selectedObjectId,
        mode = mode,
        panel = panel,
        screen = screen,
        animationsPaused = animationsPaused,
        viewpointModeObjectId = viewpointModeObjectId,
        whiteboardTool = whiteboardTool,
        annotationsVisible = annotationsVisible,
    )

    private fun restore(snapshot: EditorSnapshot) {
        shows.clear()
        shows.addAll(snapshot.shows.map { it.deepCopy() })
        if (shows.isEmpty()) {
            activeShowIndex = 0
            document = newDocument(1)
            currentPageIndex = 0
            clearSelection()
            mode = AppMode.EDIT
            panel = StudioPanel.NONE
            screen = AppScreen.HOME
            activeTextEditObjectId = null
            return
        }
        activeShowIndex = snapshot.activeShowIndex.coerceIn(0, shows.lastIndex)
        document = shows[activeShowIndex]
        currentPageIndex = snapshot.currentPageIndex.coerceIn(0, document.pages.lastIndex)
        selectedObjectId = snapshot.selectedObjectId?.takeIf { document.objects.containsKey(it) }
        mode = if (document.documentType == DocumentType.WHITEBOARD) AppMode.EDIT else snapshot.mode
        screen = snapshot.screen
        animationsPaused = if (document.documentType == DocumentType.WHITEBOARD) false else snapshot.animationsPaused
        viewpointModeObjectId = snapshot.viewpointModeObjectId
            ?.takeIf { id ->
                document.objects[id]?.type == ObjectType.VIEWPOINT &&
                    currentPage.states[id]?.visible == true
            }
        panel = if (document.documentType == DocumentType.WHITEBOARD && snapshot.panel == StudioPanel.ANIMATION) {
            StudioPanel.NONE
        } else {
            snapshot.panel
        }
        whiteboardTool = if (document.documentType == DocumentType.WHITEBOARD) {
            snapshot.whiteboardTool
        } else {
            WhiteboardTool.NONE
        }
        annotationsVisible = snapshot.annotationsVisible
        if (document.documentType == DocumentType.WHITEBOARD) {
            animationDraft = null
            endpointPreviewTransforms.clear()
            animationDraftHadExistingAnimation = false
        }
        presentAnnotationTool = PresentAnnotationTool.NONE
        annotationStrokeDraft = null
        annotationCommentDraft = null
        activeTransformObjectId = null
        activeTransformBase = null
        activeTransformLatest = null
        activeGroupTransformBases.clear()
        activeGroupPreviewTransforms.clear()
        activeTextEditObjectId = selectedObjectId?.takeIf { document.objects[it]?.type == ObjectType.TEXT && panel == StudioPanel.TEXT }
        if (selectedObjectId != frozenAnimationObjectId) {
            frozenAnimationObjectId = null
            frozenAnimationTransform = null
        }
    }

    private fun clearSelection() {
        selectedObjectId = null
        frozenAnimationObjectId = null
        frozenAnimationTransform = null
        activeTransformObjectId = null
        activeTransformBase = null
        activeTransformLatest = null
        activeGroupTransformBases.clear()
        activeGroupPreviewTransforms.clear()
        activeRotationObjectId = null
        activeRotationLatest = null
        endpointPreviewTransforms.clear()
        imageSubjectPreviews.keys.removeAll { document.objects[it]?.type != ObjectType.IMAGE }
        imageSubjectCutoutCache.keys.removeAll { document.objects[it]?.type != ObjectType.IMAGE }
    }

    private companion object {
        const val MAX_HISTORY = 30
        const val MAX_SHOW_NAME_LENGTH = 48
        const val DEFAULT_ANIMATION_END_OFFSET_X = 0.45f

        fun newId(): String = "s" + UUID.randomUUID().toString().replace("-", "")

        fun newVersionId(): String = "v" + UUID.randomUUID().toString().replace("-", "")

        fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        fun newDocument(index: Int, type: DocumentType = DocumentType.PRESENTATION): ShowDocument = ShowDocument(
            id = newId(),
            name = when (type) {
                DocumentType.PRESENTATION -> "ShowMe3D_${today()}_$index"
                DocumentType.WHITEBOARD -> "Whiteboard_${today()}_$index"
            },
            documentType = type,
        ).apply {
            pages.add(Page(newId()))
        }

        fun newPresetDocument(index: Int, type: DocumentType): ShowDocument {
            val document = newDocument(index, type).apply {
                name = when (type) {
                    DocumentType.PRESENTATION -> "产品展示空间_$index"
                    DocumentType.WHITEBOARD -> "自由白板草稿_$index"
                }
            }
            return when (type) {
                DocumentType.PRESENTATION -> document.applyPresentationPreset()
                DocumentType.WHITEBOARD -> document.applyWhiteboardPreset()
            }
        }

        fun uniqueCopyName(sourceName: String): String =
            "${sourceName}_副本".take(MAX_SHOW_NAME_LENGTH)

        fun spawnTransform(existingObjects: Int): TransformData {
            val column = existingObjects % 3
            val row = existingObjects / 3
            return TransformData(
                px = (column - 1) * 0.42f,
                py = 1.15f + row * 0.08f,
                pz = -1.45f - row * 0.16f,
                scale = 1.8f,
            )
        }

        fun clampTransform(t: TransformData): TransformData =
            t.copy(scale = t.scale.coerceIn(0.08f, 10f))
    }
}

data class ShowSummary(
    val index: Int,
    val name: String,
    val documentType: DocumentType,
    val objectCount: Int,
    val pageCount: Int,
    val selected: Boolean,
)

private data class EditorSnapshot(
    val shows: List<ShowDocument>,
    val activeShowIndex: Int,
    val currentPageIndex: Int,
    val selectedObjectId: String?,
    val mode: AppMode,
    val panel: StudioPanel,
    val screen: AppScreen,
    val animationsPaused: Boolean,
    val viewpointModeObjectId: String?,
    val whiteboardTool: WhiteboardTool,
    val annotationsVisible: Boolean,
)

private data class EditorBatch(
    val startSnapshot: EditorSnapshot,
    val undoStackBefore: List<EditorSnapshot>,
    val redoStackBefore: List<EditorSnapshot>,
    var hasCapturedMutation: Boolean = false,
)

private fun EditorSnapshot.deepCopy(): EditorSnapshot =
    copy(shows = shows.map { it.deepCopy() })

private fun ShowDocument.deepCopy(): ShowDocument =
    ShowDocument(
        id = id,
        name = name,
        schemaVersion = schemaVersion,
        documentType = documentType,
        objects = objects.mapValues {
            it.value.copy(
                strokeData = it.value.strokeData?.copyData(),
                versionHistory = it.value.versionHistory?.copyHistory(),
            )
        }.toMutableMap(),
        pages = pages.map { page -> page.deepCopy(page.id) }.toMutableList(),
        anchorEnabled = anchorEnabled,
    )

private fun ShowDocument.deepCopyAsNew(id: String, name: String): ShowDocument {
    val objectIdMap = objects.keys.associateWith { "s" + UUID.randomUUID().toString().replace("-", "") }
    val copiedObjects = objects.map { (oldId, obj) ->
        val newObjectId = objectIdMap.getValue(oldId)
        newObjectId to obj.copy(
            id = newObjectId,
            strokeData = obj.strokeData?.copyData(),
            versionHistory = obj.versionHistory?.copyHistory(),
        )
    }.toMap().toMutableMap()
    val copiedPages = pages.map { page ->
        Page("s" + UUID.randomUUID().toString().replace("-", "")).also { copiedPage ->
            page.states.forEach { (oldObjectId, state) ->
                val newObjectId = objectIdMap[oldObjectId] ?: return@forEach
                copiedPage.states[newObjectId] = state.copyState()
            }
            copiedPage.annotations += page.annotations.map { it.copyAnnotation() }
            copiedPage.skybox = page.skybox?.copy()
        }
    }.toMutableList()
    return ShowDocument(
        id = id,
        name = name,
        schemaVersion = ShowDocument.CURRENT_SCHEMA_VERSION,
        documentType = documentType,
        objects = copiedObjects,
        pages = copiedPages,
        anchorEnabled = anchorEnabled,
    )
}

private fun ShowDocument.applyPresentationPreset(): ShowDocument {
    val heroId = "s" + UUID.randomUUID().toString().replace("-", "")
    objects[heroId] = SceneObject(
        id = heroId,
        type = ObjectType.MODEL,
        source = "MyScene",
        displayName = "产品模型",
        textColor = ObjectColors.DEFAULT_GEOMETRY_COLOR,
    )
    pages.firstOrNull()?.states?.apply {
        this[heroId] = ObjectState(
            transform = TransformData(px = 0f, py = 1.18f, pz = -1.55f, scale = 1.8f),
        )
    }
    return this
}

private fun ShowDocument.applyWhiteboardPreset(): ShowDocument {
    val titleId = "s" + UUID.randomUUID().toString().replace("-", "")
    objects[titleId] = SceneObject(
        id = titleId,
        type = ObjectType.TEXT,
        source = "在空间里记录想法",
        displayName = "在空间里记录想法",
        textColor = 0xFF111111,
    )
    pages.firstOrNull()?.states?.set(
        titleId,
        ObjectState(
            transform = TransformData(px = 0f, py = 1.25f, pz = -1.45f, scale = 0.8f),
        ),
    )
    return this
}

private fun SceneObject.toObjectVersion(
    versionId: String,
    origin: ObjectVersionOrigin,
    transform: TransformData?,
    recordId: String? = null,
    prompt: String = "",
    inputSources: List<String> = emptyList(),
    createdAt: Long = System.currentTimeMillis(),
): ObjectVersion =
    ObjectVersion(
        versionId = versionId,
        type = type,
        source = source,
        displayName = displayName,
        textColor = textColor,
        strokeData = strokeData?.copyData(),
        origin = origin,
        recordId = recordId,
        prompt = prompt,
        inputSources = inputSources,
        createdAt = createdAt,
        preferredTransform = null,
    )

private fun ObjectVersion.toSceneObject(
    objectId: String,
    isLocked: Boolean,
    faceViewer: Boolean,
    versionHistory: ObjectVersionHistory,
): SceneObject =
    SceneObject(
        id = objectId,
        type = type,
        source = source,
        displayName = displayName,
        textColor = textColor,
        strokeData = strokeData?.copyData(),
        isLocked = isLocked,
        faceViewer = faceViewer,
        versionHistory = versionHistory,
    )

private fun SceneObject.activeVersion(): ObjectVersion? =
    versionHistory?.let { history ->
        history.versions.firstOrNull { it.versionId == history.activeVersionId }
    }

private fun AigcImagePreprocessMode.toPreprocessOrigin(): ObjectVersionOrigin =
    when (this) {
        AigcImagePreprocessMode.LOCAL_SUBJECT -> ObjectVersionOrigin.IMAGE_PREPROCESS_LOCAL_SUBJECT
        AigcImagePreprocessMode.SEEDREAM_CLEAN -> ObjectVersionOrigin.IMAGE_PREPROCESS_SEEDREAM_CLEAN
        AigcImagePreprocessMode.NONE -> ObjectVersionOrigin.IMAGE_TO_IMAGE
    }

private fun ObjectVersion.copyVersion(): ObjectVersion =
    copy(
        strokeData = strokeData?.copyData(),
        inputSources = inputSources.toList(),
        preferredTransform = preferredTransform?.copy(),
    )

private fun ObjectVersionHistory.copyHistory(): ObjectVersionHistory =
    ObjectVersionHistory(
        activeVersionId = activeVersionId,
        versions = versions.map { it.copyVersion() }.toMutableList(),
    )

private fun TransformData.approximatelyEquals(other: TransformData): Boolean =
    kotlin.math.abs(px - other.px) < 0.0001f &&
        kotlin.math.abs(py - other.py) < 0.0001f &&
        kotlin.math.abs(pz - other.pz) < 0.0001f &&
        kotlin.math.abs(rotationY - other.rotationY) < 0.0001f &&
        kotlin.math.abs(scale - other.scale) < 0.0001f

private fun TransformData.midpoint(other: TransformData): TransformData =
    TransformData(
        px = (px + other.px) * 0.5f,
        py = (py + other.py) * 0.5f,
        pz = (pz + other.pz) * 0.5f,
        rotationY = (rotationY + other.rotationY) * 0.5f,
        scale = (scale + other.scale) * 0.5f,
    )

private data class StrokeBounds(
    val center: StrokePoint,
)

private fun StrokeData.bounds(): StrokeBounds {
    val points = strokes.flatMap { it.points }
    if (points.isEmpty()) return StrokeBounds(StrokePoint(0f, 1.15f, -1.45f))
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val minZ = points.minOf { it.z }
    val maxZ = points.maxOf { it.z }
    return StrokeBounds(
        center = StrokePoint(
            x = (minX + maxX) * 0.5f,
            y = (minY + maxY) * 0.5f,
            z = (minZ + maxZ) * 0.5f,
        ),
    )
}

private fun StrokeData.centered(center: StrokePoint): StrokeData =
    StrokeData(
        strokes = strokes.map { path ->
            StrokePath(
                color = path.color,
                points = path.points.map { point ->
                    StrokePoint(
                        x = point.x - center.x,
                        y = point.y - center.y,
                        z = point.z - center.z,
                    )
                }.toMutableList(),
            )
        }.toMutableList(),
    )

private fun StrokePath.optimizedForStrokeStorage(maxPoints: Int): StrokePath {
    if (points.size <= maxPoints) return copyPath()
    if (maxPoints <= 2) return StrokePath(color = color, points = mutableListOf(points.first().copy(), points.last().copy()))
    val result = ArrayList<StrokePoint>(maxPoints)
    val step = (points.lastIndex).toFloat() / (maxPoints - 1).toFloat()
    repeat(maxPoints) { index ->
        result += points[(index * step).toInt().coerceIn(0, points.lastIndex)].copy()
    }
    if (result.last() != points.last()) {
        result[result.lastIndex] = points.last().copy()
    }
    return StrokePath(color = color, points = result.toMutableList())
}

private fun StrokePath.isDrawableStrokePath(): Boolean {
    if (points.size < StrokeDraft.MIN_STROKE_POINTS) return false
    var length = 0f
    for (index in 1 until points.size) {
        length += points[index - 1].distanceTo(points[index])
        if (length >= StrokeDraft.MIN_STROKE_LENGTH_METERS) return true
    }
    return false
}

private fun StrokePoint.distanceTo(other: StrokePoint): Float {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
}

private const val STROKE_POINT_MIN_DISTANCE = 0.018f
private const val COMMENT_DRAFT_POINT_MIN_DISTANCE = 0.01f
private const val GENERATED_IMAGE_OFFSET_X = 0.38f
