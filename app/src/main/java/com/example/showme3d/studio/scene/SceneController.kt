package com.example.showme3d.studio.scene

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.PageAnimation
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.PageAnnotation
import com.example.showme3d.studio.model.PageAnnotationType
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.StrokeData
import com.example.showme3d.studio.model.StrokePath
import com.example.showme3d.studio.model.StrokePoint
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.interaction.PresentationResetMotion
import com.example.showme3d.studio.state.AnimationEndpointDraft
import com.example.showme3d.studio.state.AnimationEndpointRole
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.PresentAnnotationTool
import com.example.showme3d.studio.state.SceneEnvironmentMode
import com.example.showme3d.studio.state.StudioViewModel
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.LookAtComponent
import com.pico.spatial.core.ecs.LookAtForwardDirection
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data-to-ECS synchronizer for the M1 rebuild.
 */
class SceneController(
    private val content: SpatialViewContent,
    private val vm: StudioViewModel,
) {
    private companion object {
        const val TAG = "ShowMe3D.Scene"
        const val MAX_ENTITY_PARENT_SEARCH_DEPTH = 32
        const val HIGHLIGHT_PADDING = 1.16f
    }

    private val workspaceRoot = Entity().apply { setName("showme3d_workspace_root") }
    private val environmentRoot = Entity().apply { setName("showme3d_environment_root") }
    private val skyboxRoot = Entity().apply { setName("showme3d_user_skybox_root") }
    private val entities = mutableMapOf<String, Entity>()
    private val animationEndpointEntities = mutableMapOf<String, Entity>()
    private val animationEndpointLabels = mutableMapOf<String, Entity>()
    private val animationPathSegments = mutableListOf<Entity>()
    private val renderKeys = mutableMapOf<String, String>()
    private val interactionSizes = mutableMapOf<String, Vector3>()
    private val lastTransforms = mutableMapOf<String, TransformData>()
    private val lastVisibility = mutableMapOf<String, Boolean>()
    private val lastPageAnimations = mutableMapOf<String, PageAnimation>()
    private val importedAnimationControllers = mutableMapOf<String, MutableList<AnimationPlaybackController>>()
    private val importedModelRoots = mutableMapOf<String, Entity>()
    private val lastPageObjectIds = mutableSetOf<String>()
    private var highlight: Entity? = null
    private var highlightSegments: List<HighlightSegment> = emptyList()
    private var highlightLayoutKey: HighlightLayoutKey? = null
    private var highlightLocked: Boolean? = null
    private val strokePreviews = mutableMapOf<String, StrokePreview>()
    private val annotationPreviews = mutableMapOf<String, StrokePreview>()
    private val commentPreviews = mutableMapOf<String, CommentPreview>()
    private val animationClockStartSeconds = mutableMapOf<String, Float>()
    private val autoRotationClockStartSeconds = mutableMapOf<String, Float>()
    private val pageTransitionLockedObjectIds = mutableSetOf<String>()
    private val currentAnimationTransforms = mutableMapOf<String, TransformData>()
    private var presentationInspectionObjectId: String? = null
    private var lastPresentationInspectionObjectId: String? = null
    private val presentationInspectionTransforms = mutableMapOf<String, TransformData>()
    private val presentationResetMotions = mutableMapOf<String, PresentationResetMotion>()
    private var lastAnimationPathStart: TransformData? = null
    private var lastAnimationPathEnd: TransformData? = null
    private var animationEndpointChromeDirty = false
    private var virtualEnvironmentCreated = false
    private val virtualEnvironmentCreationMutex = Mutex()
    private var environmentMode = SceneEnvironmentMode.REAL
    private var environmentVisible = false
    private var skyboxVisible = false
    private var currentSkybox: SkyboxRender? = null
    private var cachedSkybox: SkyboxRender? = null
    private var skyboxTransition: SkyboxTransition? = null
    private var viewpointTargetEye = ViewpointPoint(
        VIEWPOINT_TARGET_X,
        VIEWPOINT_EYE_HEIGHT_METERS,
        VIEWPOINT_TARGET_Z,
    )
    private var viewpointTargetForward = ViewpointPoint(0f, 0f, -1f)
    private var viewpointInteractionsSuppressed = false
    var sceneVersion by mutableIntStateOf(0)
        private set

    init {
        environmentRoot.enabled = false
        skyboxRoot.enabled = false
        content.addEntity(environmentRoot)
        content.addEntity(skyboxRoot)
        content.addEntity(workspaceRoot)
    }

    suspend fun sync(animate: Boolean = false) {
        if (animate || vm.mode != AppMode.PRESENT) {
            clearPresentationInspections()
        }
        syncPageSkybox(animate)
        withContext(Dispatchers.Main.immediate) {
            removeStaleEntities()
        }
        syncCurrentPageEntities(animate)
        if (animate) {
            animateToCurrentPage()
        } else {
            withContext(Dispatchers.Main.immediate) {
                disableEntitiesNotOnCurrentPage()
                syncAnimationEndpoints()
                syncHighlight()
                syncStrokeDraft()
                syncPageAnnotations()
                if (vm.strokeDraft == null) {
                    clearStrokePreviews()
                }
                syncViewpointMode()
                rememberCurrentPageState()
            }
        }
    }

    private fun removeStaleEntities() {
        val liveObjectIds = vm.document.objects.keys
        entities.keys
            .filter { it !in liveObjectIds }
            .forEach { removeEntity(it) }
    }

    private suspend fun syncCurrentPageEntities(animate: Boolean) {
        vm.currentPage.states.forEach { (objectId, state) ->
            val obj = vm.renderObject(objectId) ?: return@forEach
            if (obj.isViewpointCalibrationDebugObject()) return@forEach
            val expectedRenderKey = obj.renderKey()
            if (entities[objectId] != null && renderKeys[objectId] != expectedRenderKey) {
                withContext(Dispatchers.Main.immediate) { removeEntity(objectId) }
            }
            val existingEntity = entities[objectId]
            val entity = existingEntity ?: createEntity(obj) ?: return@forEach
            withContext(Dispatchers.Main.immediate) {
                syncFaceViewer(entity, obj)
                val hiddenForAnimationDraft = vm.animationDraft?.objectId == objectId
                val unchangedPageAnimation = animate &&
                    state.visible &&
                    lastVisibility[objectId] == true &&
                    state.pageAnimation != null &&
                    lastPageAnimations[objectId] == state.pageAnimation
                val appearingForTransition = animate && state.visible && lastVisibility[objectId] != true
                val hiddenForViewpointMode = vm.viewpointModeObjectId != null && vm.isViewpointRelatedObject(objectId)
                entity.enabled = if (hiddenForAnimationDraft || hiddenForViewpointMode) {
                    false
                } else if (appearingForTransition) {
                    false
                } else if (animate) {
                    (lastVisibility[objectId] ?: state.visible) || state.visible
                } else {
                    state.visible
                }
                // An appearing page object is deliberately disabled until its
                // transition begins. Starting a skeletal clip while any parent
                // is disabled produces an invalid controller in Spatial SDK.
                syncEmbeddedAnimationPlayback(obj, state.visible && entity.enabled)
                val from = lastTransforms[objectId]?.takeIf { animate }
                if (unchangedPageAnimation) {
                    // Let the per-object animation clock continue without a one-frame page-sync overwrite.
                    lastVisibility[objectId] = state.visible
                } else if (from != null) {
                    applyRawTransform(entity, from)
                } else if (animate) {
                    val target = pageTransitionTargetTransform(objectId, state)
                    val start = if (lastVisibility[objectId] == true) {
                        target
                    } else {
                        target.copy(scale = target.scale * PAGE_APPEAR_SCALE)
                    }
                    applyRawTransform(entity, start)
                } else if (objectId in presentationInspectionTransforms) {
                    applyRawTransform(entity, presentationInspectionTransforms.getValue(objectId))
                } else if (
                    !animate &&
                    objectId != vm.animationFrozenObjectId &&
                    (
                        state.pageAnimation != null ||
                            state.autoRotateY
                        )
                ) {
                    // UI-only revisions (for example starting or finishing a presentation
                    // stroke) also call sync(). Keep the runtime pose so a continuously
                    // rotating object is not reset to its authored transform for one frame.
                    val current = currentAnimationTransforms[objectId]
                        ?: state.pageAnimation?.start
                        ?: state.transform
                    if (existingEntity == null) {
                        applyRawTransform(entity, current)
                    }
                    lastTransforms[objectId] = current
                } else if (objectId == vm.animationFrozenObjectId && vm.animationFrozenTransform != null) {
                    applyRawTransform(entity, vm.animationFrozenTransform ?: state.transform)
                } else {
                    currentAnimationTransforms.remove(objectId)
                    animationClockStartSeconds.remove(objectId)
                    applyTransform(entity, state.transform)
                }
                if (!animate) {
                    lastVisibility[objectId] = state.visible
                }
            }
        }
    }

    suspend fun prewarmAdjacentPages() {
        if (!vm.isPresentation) return
        val pageIndex = vm.currentPageIndex
        val pageIndexes = listOf(pageIndex + 1, pageIndex - 1)
            .filter { it in vm.document.pages.indices }
        val objectIds = pageIndexes
            .flatMap { vm.document.pages[it].states.keys }
            .distinct()
        for (objectId in objectIds) {
            if (vm.currentPageIndex != pageIndex) return
            if (entities.containsKey(objectId)) continue
            val obj = vm.renderObject(objectId) ?: continue
            createEntity(obj, reportStatus = false)?.enabled = false
        }
    }

    private fun disableEntitiesNotOnCurrentPage() {
        entities.forEach { (objectId, entity) ->
            if (
                !vm.currentPage.states.containsKey(objectId) ||
                vm.animationDraft?.objectId == objectId ||
                (vm.viewpointModeObjectId != null && vm.isViewpointRelatedObject(objectId))
            ) {
                entity.enabled = false
                lastVisibility[objectId] = false
            }
        }
    }

    fun entityFor(objectId: String): Entity? = entities[objectId]
        ?: animationEndpointEntities[objectId]

    fun objectFor(objectId: String): SceneObject? =
        vm.animationDraftEndpoints.firstOrNull { it.id == objectId }?.sourceObject
            ?: vm.renderObject(objectId)

    fun visibleTransformForSelection(objectId: String): TransformData? =
        presentationInspectionTransforms[objectId]
            ?: vm.animationDraftEndpoints.firstOrNull { it.id == objectId }?.transform
            ?: vm.animationFrozenTransform?.takeIf { vm.animationFrozenObjectId == objectId }
            ?: currentAnimationTransforms[objectId]
            ?: vm.currentPage.states[objectId]?.transform?.let { visibleTransformFor(objectId, it) }

    fun beginPresentationInspection(objectId: String, transform: TransformData) {
        if (!vm.canInspectModelInPresent(objectId)) return
        presentationResetMotions.remove(objectId)
        presentationInspectionObjectId = objectId
        lastPresentationInspectionObjectId = objectId
        presentationInspectionTransforms.putIfAbsent(objectId, transform)
    }

    fun updatePresentationInspection(objectId: String, transform: TransformData) {
        if (presentationInspectionObjectId != objectId) return
        presentationInspectionTransforms[objectId] = transform
        lastTransforms[objectId] = transform
        entities[objectId]?.let { applyRawTransform(it, transform) }
    }

    fun endPresentationInspection(objectId: String) {
        if (presentationInspectionObjectId != objectId) return
        presentationInspectionObjectId = null
        sceneVersion++
    }

    fun hasPresentationInspectionTransform(objectId: String): Boolean =
        objectId in presentationInspectionTransforms

    fun presentationInspectionTargetId(): String? =
        lastPresentationInspectionObjectId
            ?.takeIf { it in presentationInspectionTransforms && it !in presentationResetMotions }
            ?: presentationInspectionTransforms.keys.lastOrNull { it !in presentationResetMotions }

    fun resetPresentationInspection(objectId: String): Boolean {
        val from = presentationInspectionTransforms[objectId] ?: return false
        val to = vm.currentPage.states[objectId]?.transform ?: return false
        presentationInspectionObjectId = presentationInspectionObjectId.takeUnless { it == objectId }
        presentationResetMotions[objectId] = PresentationResetMotion(
            from = from,
            to = to,
            startedAtNanos = System.nanoTime(),
        )
        return true
    }

    private fun clearPresentationInspections() {
        presentationInspectionObjectId = null
        lastPresentationInspectionObjectId = null
        presentationInspectionTransforms.clear()
        presentationResetMotions.clear()
    }

    fun selectionToolbarPosition(objectId: String): Vector3? {
        val endpoint = vm.animationDraftEndpoints.firstOrNull { it.id == objectId }
        val anchorObjectId = endpoint?.objectId ?: objectId
        val state = vm.currentPage.states[anchorObjectId]?.takeIf { it.visible }
        val transform = vm.animationEditorAnchorTransform
            ?: endpoint?.let { toolbarAnchorTransform(it.objectId) }
            ?: vm.animationFrozenTransform?.takeIf { vm.animationFrozenObjectId == anchorObjectId }
            ?: currentAnimationTransforms[anchorObjectId]
            ?: state?.transform?.let { visibleTransformFor(anchorObjectId, it) }
            ?: return null
        val size = interactionSizes[anchorObjectId] ?: endpoint?.sourceObject?.let(ObjectEntityFactory::interactionSize)
            ?: Vector3(ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE)
        val toolbarScale = selectionToolbarScaleFor(transform)
        val yOffset = ((size.y * transform.scale) / 2f) +
            SELECTION_TOOLBAR_MARGIN_Y +
            ((toolbarScale - 1f).coerceAtLeast(0f) * SELECTION_TOOLBAR_SCALE_MARGIN_Y)
        return Vector3(transform.px, transform.py - yOffset, transform.pz + SELECTION_TOOLBAR_Z_OFFSET)
    }

    fun selectionToolbarScale(objectId: String): Float? {
        val endpoint = vm.animationDraftEndpoints.firstOrNull { it.id == objectId }
        val anchorObjectId = endpoint?.objectId ?: objectId
        val state = vm.currentPage.states[anchorObjectId]?.takeIf { it.visible }
        return selectionToolbarScaleFor(
            vm.animationEditorAnchorTransform
                ?: endpoint?.let { toolbarAnchorTransform(it.objectId) }
                ?: vm.animationFrozenTransform?.takeIf { vm.animationFrozenObjectId == anchorObjectId }
                ?: currentAnimationTransforms[anchorObjectId]
                ?: state?.transform?.let { visibleTransformFor(anchorObjectId, it) }
                ?: return null
        )
    }

    fun animationEndpointControlsPosition(start: Boolean): Vector3? {
        val endpoint = vm.animationDraftEndpoints.firstOrNull {
            it.role == if (start) AnimationEndpointRole.START else AnimationEndpointRole.END
        } ?: return null
        val size = interactionSizes[endpoint.id] ?: ObjectEntityFactory.interactionSize(endpoint.sourceObject)
        val controlScale = animationEndpointControlsScale(start) ?: 1f
        val yOffset = ((size.y * endpoint.transform.scale) / 2f) +
            ANIMATION_ENDPOINT_BADGE_GAP +
            ANIMATION_ENDPOINT_CONTROLS_MARGIN_Y +
            ((controlScale - 1f).coerceAtLeast(0f) * ANIMATION_ENDPOINT_CONTROLS_SCALE_MARGIN_Y)
        return Vector3(
            endpoint.transform.px,
            endpoint.transform.py - yOffset,
            endpoint.transform.pz + ANIMATION_ENDPOINT_BADGE_Z_OFFSET,
        )
    }

    fun animationEndpointControlsScale(start: Boolean): Float? {
        val endpoint = vm.animationDraftEndpoints.firstOrNull {
            it.role == if (start) AnimationEndpointRole.START else AnimationEndpointRole.END
        } ?: return null
        return selectionToolbarScaleFor(endpoint.transform)
    }

    private fun toolbarAnchorTransform(objectId: String): TransformData? {
        if (vm.groupMemberIds(objectId).size > 1) {
            selectedGroupBounds()?.center?.let { center ->
                return TransformData(px = center.x, py = center.y, pz = center.z)
            }
        }
        return vm.animationFrozenTransform?.takeIf { vm.animationFrozenObjectId == objectId }
            ?: currentAnimationTransforms[objectId]
            ?: vm.currentPage.states[objectId]?.transform?.let { visibleTransformFor(objectId, it) }
    }

    fun objectIdFor(entity: Entity?): String? {
        var current = entity
        repeat(MAX_ENTITY_PARENT_SEARCH_DEPTH) {
            val name = current?.getName()
            if (name != null && animationEndpointEntities.containsKey(name)) {
                return name
            }
            val endpointIdFromLabel = name?.removeSuffix("_label")
            if (endpointIdFromLabel != null && endpointIdFromLabel != name && animationEndpointLabels.containsKey(endpointIdFromLabel)) {
                return endpointIdFromLabel
            }
            if (name != null && vm.document.objects.containsKey(name)) {
                return vm.groupRepresentativeId(name)
            }
            current = runCatching { current?.getParent() }.getOrNull()
        }
        return null
    }

    fun annotationIdFor(entity: Entity?): String? {
        var current = entity
        repeat(MAX_ENTITY_PARENT_SEARCH_DEPTH) {
            val name = current?.getName()
            if (name != null) {
                annotationPreviews.keys.firstOrNull { id ->
                    name == id ||
                        name.startsWith("${id}_") ||
                        id.startsWith("$name")
                }?.let { previewId -> return previewId.removePrefix("${ANNOTATION_PREVIEW_PREFIX}_${vm.currentPage.id}_") }
            }
            current = runCatching { current?.getParent() }.getOrNull()
        }
        return null
    }

    fun commentIdFor(entity: Entity?): String? {
        var current = entity
        repeat(MAX_ENTITY_PARENT_SEARCH_DEPTH) {
            val name = current?.getName()
            commentPreviews.keys.firstOrNull { id -> name == id || name?.startsWith("${id}_") == true }
                ?.let { previewId -> return previewId.removePrefix("${ANNOTATION_PREVIEW_PREFIX}_${vm.currentPage.id}_") }
            current = runCatching { current?.getParent() }.getOrNull()
        }
        return null
    }

    fun pushTransform(objectId: String, transform: TransformData, updateAnimationEndpointChrome: Boolean = true) {
        animationEndpointEntities[objectId]?.let {
            applyRawTransform(it, transform)
            if (vm.selectedAnimationEndpoint?.id == objectId) {
                val box = highlight ?: createHighlight()?.also { highlight = it }
                box?.enabled = true
                applyEndpointHighlightTransform(objectId, transform)
            }
            if (updateAnimationEndpointChrome) {
                syncAnimationEndpointChrome(force = true)
            } else {
                animationEndpointChromeDirty = true
            }
            return
        }
        val groupedTransforms = vm.groupTransformsFor(objectId)
        if (groupedTransforms.size > 1) {
            groupedTransforms.forEach { (memberId, memberTransform) ->
                entities[memberId]?.let { entity ->
                    applyTransform(entity, memberTransform)
                    if (vm.currentPage.states[memberId]?.pageAnimation != null) {
                        currentAnimationTransforms[memberId] = memberTransform
                        lastTransforms[memberId] = memberTransform
                    }
                }
            }
            if (objectId == vm.selectedObjectId) applyGroupHighlightTransform()
        } else {
            entities[objectId]?.let {
                applyTransform(it, transform)
                if (vm.currentPage.states[objectId]?.pageAnimation != null) {
                    currentAnimationTransforms[objectId] = transform
                    lastTransforms[objectId] = transform
                }
            }
            if (objectId == vm.selectedObjectId) applyHighlightTransform(transform)
        }
    }

    fun strokePointFromTrackingPosition(position: Vector3): StrokePoint? =
        workspaceRoot.convertPositionFrom(position, null).toStrokePoint()

    private fun Vector3.toStrokePoint(): StrokePoint? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        if (abs(x) < STROKE_ZERO_EPSILON && abs(y) < STROKE_ZERO_EPSILON && abs(z) < STROKE_ZERO_EPSILON) return null
        return StrokePoint(
            x = x.coerceIn(STROKE_MIN_X, STROKE_MAX_X),
            y = y.coerceIn(STROKE_MIN_Y, STROKE_MAX_Y),
            z = z.coerceIn(STROKE_MIN_Z, STROKE_MAX_Z),
        )
    }

    fun beginStrokeInput(point: StrokePoint, color: Long) {
        val preview = strokePreviews[STROKE_LIVE_PREVIEW_ID] ?: StrokePreview(Entity().apply { setName(STROKE_LIVE_PREVIEW_ID) }).also {
            strokePreviews[STROKE_LIVE_PREVIEW_ID] = it
            workspaceRoot.addChild(it.root)
            sceneVersion++
        }
        preview.visible = true
        preview.root.enabled = true
        preview.activePathColor = color
        preview.lastPoint = point
        clearStrokeMeshGeometry(preview)
        preview.livePoints.clear()
        preview.livePoints += point
        preview.lastMeshUpdateNanos = 0L
    }

    fun appendStrokeInput(point: StrokePoint, color: Long) {
        val preview = strokePreviews[STROKE_LIVE_PREVIEW_ID] ?: run {
            beginStrokeInput(point, color)
            return
        }
        val last = preview.lastPoint ?: run {
            beginStrokeInput(point, color)
            return
        }
        preview.activePathColor = color
        preview.lastPoint = point
        preview.visible = true
        preview.root.enabled = true
        if (preview.livePoints.isEmpty()) preview.livePoints += last
        if (preview.livePoints.last().distanceTo(point) >= STROKE_LIVE_INPUT_MIN_DISTANCE) {
            preview.livePoints += point
            if (preview.livePoints.size >= STROKE_LIVE_CHUNK_POINT_LIMIT) {
                freezeStrokeLiveChunk(preview)
            }
        }
        updateStrokeLiveChunk(preview, force = false)
    }

    fun endStrokeInput() {
        val preview = strokePreviews[STROKE_LIVE_PREVIEW_ID] ?: return
        preview.livePoints.clear()
        preview.lastPoint = null
        preview.visible = false
        preview.root.enabled = false
        clearStrokeMeshGeometry(preview)
        preview.lastMeshUpdateNanos = 0L
    }

    fun beginAnnotationStrokeInput(point: StrokePoint, color: Long) {
        val preview = annotationPreviews[ANNOTATION_LIVE_PREVIEW_ID] ?: StrokePreview(Entity().apply { setName(ANNOTATION_LIVE_PREVIEW_ID) }).also {
            annotationPreviews[ANNOTATION_LIVE_PREVIEW_ID] = it
            workspaceRoot.addChild(it.root)
            sceneVersion++
        }
        preview.visible = true
        preview.root.enabled = vm.annotationsVisible
        preview.activePathColor = color
        preview.lastPoint = point
        clearStrokeMeshGeometry(preview)
        preview.livePoints.clear()
        preview.livePoints += point
        preview.lastMeshUpdateNanos = 0L
    }

    fun appendAnnotationStrokeInput(point: StrokePoint, color: Long) {
        val preview = annotationPreviews[ANNOTATION_LIVE_PREVIEW_ID] ?: run {
            beginAnnotationStrokeInput(point, color)
            return
        }
        val last = preview.lastPoint ?: run {
            beginAnnotationStrokeInput(point, color)
            return
        }
        preview.activePathColor = color
        preview.lastPoint = point
        preview.visible = true
        preview.root.enabled = vm.annotationsVisible
        if (preview.livePoints.isEmpty()) preview.livePoints += last
        if (preview.livePoints.last().distanceTo(point) >= STROKE_LIVE_INPUT_MIN_DISTANCE) {
            preview.livePoints += point
            if (preview.livePoints.size >= STROKE_LIVE_CHUNK_POINT_LIMIT) {
                freezeStrokeLiveChunk(preview)
            }
        }
        updateStrokeLiveChunk(preview, force = false)
    }

    fun endAnnotationStrokeInput() {
        val preview = annotationPreviews[ANNOTATION_LIVE_PREVIEW_ID] ?: return
        preview.livePoints.clear()
        preview.lastPoint = null
        preview.visible = false
        preview.root.enabled = false
        clearStrokeMeshGeometry(preview)
        preview.lastMeshUpdateNanos = 0L
    }

    fun flushAnimationEndpointChrome(force: Boolean = false) {
        if (!force && !animationEndpointChromeDirty) return
        syncAnimationEndpointChrome(force = force)
    }

    fun syncAnimationEndpoints() {
        val drafts = vm.animationDraftEndpoints.associateBy { it.id }
        animationEndpointEntities.keys
            .filter { it !in drafts }
            .forEach { removeAnimationEndpoint(it) }
        if (drafts.isEmpty()) {
            animationPathSegments.forEach { it.enabled = false }
            lastAnimationPathStart = null
            lastAnimationPathEnd = null
        }
        drafts.values.forEach { endpoint ->
            val entity = animationEndpointEntities[endpoint.id] ?: createAnimationEndpoint(endpoint).also {
                animationEndpointEntities[endpoint.id] = it
                interactionSizes[endpoint.id] = ObjectEntityFactory.interactionSize(endpoint.sourceObject)
                workspaceRoot.addChild(it)
                sceneVersion++
            }
            entity.enabled = true
            applyEndpointTransform(entity, endpoint)
            createOrUpdateAnimationEndpointLabel(endpoint)
        }
        syncAnimationEndpointLabels()
        syncAnimationPath(drafts.values.toList(), force = true)
        animationEndpointChromeDirty = false
    }

    fun tickPageAnimations(
        frameTimeSeconds: Float,
        paused: Boolean,
        frozenObjectId: String? = null,
    ) {
        tickSkyboxTransition(System.nanoTime())
        if (paused) {
            animationClockStartSeconds.clear()
            autoRotationClockStartSeconds.clear()
            return
        }
        var animatedSelectedTransform: TransformData? = null
        val nowNanos = System.nanoTime()
        vm.currentPage.states.forEach { (objectId, state) ->
            if (vm.animationDraft?.objectId == objectId) {
                entities[objectId]?.enabled = false
                return@forEach
            }
            if (vm.viewpointModeObjectId != null && vm.isViewpointRelatedObject(objectId)) {
                entities[objectId]?.enabled = false
                return@forEach
            }
            presentationResetMotions[objectId]?.let { motion ->
                val current = motion.transformAt(nowNanos)
                entities[objectId]?.let { applyRawTransform(it, current) }
                lastTransforms[objectId] = current
                if (motion.isComplete(nowNanos)) {
                    presentationResetMotions.remove(objectId)
                    presentationInspectionTransforms.remove(objectId)
                    animationClockStartSeconds.remove(objectId)
                    autoRotationClockStartSeconds.remove(objectId)
                } else {
                    presentationInspectionTransforms[objectId] = current
                }
                return@forEach
            }
            presentationInspectionTransforms[objectId]?.let { held ->
                entities[objectId]?.let { applyRawTransform(it, held) }
                lastTransforms[objectId] = held
                return@forEach
            }
            val animation = state.pageAnimation
            val autoRotateY = state.autoRotateY &&
                vm.document.objects[objectId]?.type in setOf(ObjectType.MODEL, ObjectType.STROKE)
            if (animation == null && !autoRotateY) {
                animationClockStartSeconds.remove(objectId)
                autoRotationClockStartSeconds.remove(objectId)
                return@forEach
            }
            if (objectId in pageTransitionLockedObjectIds) return@forEach
            if (objectId == frozenObjectId) {
                val frozen = vm.animationFrozenTransform?.takeIf { objectId == vm.animationFrozenObjectId }
                    ?: currentAnimationTransforms[objectId]
                frozen?.let {
                    entities[objectId]?.let { applyRawTransform(it, frozen) }
                    if (objectId == vm.selectedObjectId) animatedSelectedTransform = frozen
                }
                return@forEach
            }
            if (!state.visible) return@forEach
            val entity = entities[objectId] ?: return@forEach
            val base = if (animation != null) {
                val startSeconds = animationClockStartSeconds.getOrPut(objectId) { frameTimeSeconds }
                animation.transformAt(frameTimeSeconds - startSeconds)
            } else {
                animationClockStartSeconds.remove(objectId)
                state.transform
            }
            val current = if (autoRotateY) {
                val startSeconds = autoRotationClockStartSeconds.getOrPut(objectId) { frameTimeSeconds }
                base.copy(
                    rotationY = base.rotationY +
                        ((frameTimeSeconds - startSeconds) * AUTO_ROTATION_RADIANS_PER_SECOND),
                )
            } else {
                autoRotationClockStartSeconds.remove(objectId)
                base
            }
            applyRawTransform(entity, current)
            currentAnimationTransforms[objectId] = current
            lastTransforms[objectId] = current
            if (objectId == vm.selectedObjectId) {
                animatedSelectedTransform = current
            }
        }
        flushAnimationEndpointChrome()
        animatedSelectedTransform?.let(::applyHighlightTransform)
        syncViewpointMode()
    }

    fun clear() {
        entities.values.forEach { runCatching { it.destroy(true) } }
        entities.clear()
        animationEndpointEntities.values.forEach { runCatching { it.destroy(true) } }
        animationEndpointEntities.clear()
        animationEndpointLabels.values.forEach { runCatching { it.destroy(true) } }
        animationEndpointLabels.clear()
        animationPathSegments.forEach { runCatching { it.destroy(true) } }
        animationPathSegments.clear()
        importedAnimationControllers.keys.toList().forEach(::stopImportedAnimations)
        importedModelRoots.clear()
        animationEndpointChromeDirty = false
        animationClockStartSeconds.clear()
        autoRotationClockStartSeconds.clear()
        skyboxTransition?.to?.takeIf { it !== currentSkybox }?.destroy()
        skyboxTransition = null
        currentSkybox?.destroy()
        currentSkybox = null
        cachedSkybox?.destroy()
        cachedSkybox = null
        updateEnvironmentVisibility()
        pageTransitionLockedObjectIds.clear()
        currentAnimationTransforms.clear()
        clearPresentationInspections()
        clearStrokePreviews()
        clearAnnotationPreviews()
        clearCommentPreviews()
        highlight?.let { runCatching { it.destroy(true) } }
        highlight = null
        highlightSegments = emptyList()
        syncViewpointMode()
    }

    fun setWorkspaceVisible(visible: Boolean) {
        entities.values.forEach { it.enabled = visible }
        animationEndpointEntities.values.forEach { it.enabled = visible }
        animationEndpointLabels.values.forEach { it.enabled = visible }
        animationPathSegments.forEach { it.enabled = visible && vm.animationDraft != null }
        strokePreviews.values.forEach { it.root.enabled = visible && vm.strokeDraft != null && it.visible }
        annotationPreviews.values.forEach { it.root.enabled = visible && vm.annotationsVisible && it.visible }
        commentPreviews.values.forEach { it.root.enabled = visible && vm.annotationsVisible && it.visible }
        highlight?.enabled = visible && vm.selectedObjectId != null
    }

    suspend fun syncEnvironmentMode(
        mode: SceneEnvironmentMode,
        visible: Boolean,
        userSkyboxVisible: Boolean = visible,
    ) {
        environmentMode = mode
        environmentVisible = visible
        skyboxVisible = userSkyboxVisible
        val shouldShow = visible &&
            mode == SceneEnvironmentMode.VIRTUAL &&
            !(userSkyboxVisible && currentSkybox != null)
        if (shouldShow && !virtualEnvironmentCreated) {
            ensureVirtualEnvironmentCreated()
        }
        updateEnvironmentVisibility()
        sceneVersion++
    }

    suspend fun prewarmVirtualEnvironment() {
        ensureVirtualEnvironmentCreated()
    }

    private suspend fun syncPageSkybox(animate: Boolean) {
        withContext(Dispatchers.Main.immediate) {
            settleInterruptedSkyboxTransition()
        }
        val target = vm.currentPageSkybox
        val active = currentSkybox
        if (target == null && active == null) {
            updateEnvironmentVisibility()
            return
        }
        if (target != null && target.source == active?.skybox?.source) {
            active.applyRotation(target.rotationY)
            currentSkybox = active.copy(skybox = target)
            updateEnvironmentVisibility()
            return
        }

        val prepared = if (target != null && target.source == cachedSkybox?.skybox?.source) {
            cachedSkybox?.also {
                cachedSkybox = null
                it.applyRotation(target.rotationY)
            }?.copy(skybox = target)
        } else target?.let { skybox ->
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(
                    skybox.source.removePrefix(FILE_PREFIX),
                    BitmapFactory.Options().apply {
                        inMutable = true
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    },
                )
                    ?.withSeamlessHorizontalWrap()
            }
            if (bitmap == null) {
                vm.reportSceneError("天空背景读取失败：${skybox.displayName}")
                null
            } else {
                withContext(Dispatchers.Main.immediate) {
                    val texture = try {
                        TextureResource.create(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    val material = UnlitMaterial.create(
                        if (animate) BlendingMode.FADE else BlendingMode.OPAQUE
                    ).apply {
                        setBaseColor(Color4(1f, 1f, 1f, 1f))
                        setBaseColorTexture(texture)
                        setCullingMode(MaterialCullingMode.FRONT)
                        setDepthWrite(false)
                        setOpacity(if (animate) 0f else 1f)
                    }
                    val entity = ModelEntity(
                        buildEquirectangularSkyboxMesh(SKYBOX_RADIUS_METERS).createResource(),
                        material,
                    ).apply {
                        setName(skyboxEntityName(skybox))
                        components.get(TransformComponent::class.java)?.setEulerAngles(
                            EulerAngles(0f, Math.toDegrees(skybox.rotationY.toDouble()).toFloat(), 0f)
                        )
                        enabled = !animate
                    }
                    skyboxRoot.addChild(entity)
                    SkyboxRender(skybox, entity, material)
                }
            }
        }

        withContext(Dispatchers.Main.immediate) {
            skyboxTransition?.to?.takeIf { it !== currentSkybox }?.destroy()
            skyboxTransition = null
            if (!animate) {
                cacheSkybox(currentSkybox)
                currentSkybox = prepared
                currentSkybox?.makeOpaque()
                currentSkybox?.entity?.enabled = skyboxVisible
                skyboxRoot.enabled = skyboxVisible && prepared != null
                updateEnvironmentVisibility()
                sceneVersion++
                return@withContext
            }
            skyboxRoot.enabled = skyboxVisible && (active != null || prepared != null)
            active?.prepareForFade()
            prepared?.prepareForFadeIn()
            skyboxTransition = SkyboxTransition(
                from = active,
                to = prepared,
                startedAtNanos = System.nanoTime(),
            )
            if (active == null && prepared == null) updateEnvironmentVisibility()
            sceneVersion++
        }
    }

    private fun settleInterruptedSkyboxTransition() {
        val interrupted = skyboxTransition ?: return
        val settled = interrupted.to ?: interrupted.from
        cacheSkybox(interrupted.from?.takeIf { it !== settled })
        cacheSkybox(interrupted.to?.takeIf { it !== settled })
        settled?.let {
            it.entity.enabled = skyboxVisible
            it.makeOpaque()
        }
        currentSkybox = settled
        skyboxTransition = null
        updateEnvironmentVisibility()
    }

    private fun tickSkyboxTransition(nowNanos: Long) {
        val transition = skyboxTransition ?: return
        val progress = ((nowNanos - transition.startedAtNanos).toFloat() / SKYBOX_TRANSITION_NANOS)
            .coerceIn(0f, 1f)
        if (progress < 0.5f) {
            transition.from?.material?.setOpacity(1f - progress * 2f)
        } else {
            transition.from?.entity?.enabled = false
            transition.to?.let {
                it.entity.enabled = skyboxVisible
                it.material.setOpacity((progress - 0.5f) * 2f)
            } ?: run {
                environmentRoot.enabled =
                    environmentVisible && environmentMode == SceneEnvironmentMode.VIRTUAL
            }
        }
        if (progress < 1f) return
        cacheSkybox(transition.from?.takeIf { it !== transition.to })
        currentSkybox = transition.to
        currentSkybox?.makeOpaque()
        skyboxTransition = null
        updateEnvironmentVisibility()
        sceneVersion++
    }

    private fun cacheSkybox(render: SkyboxRender?) {
        if (render == null) return
        if (cachedSkybox !== render) {
            cachedSkybox?.destroy()
        }
        render.entity.enabled = false
        render.makeOpaque()
        cachedSkybox = render
    }

    private fun updateEnvironmentVisibility() {
        val hasSkybox = currentSkybox != null || skyboxTransition?.to != null
        skyboxRoot.enabled = skyboxVisible && hasSkybox
        environmentRoot.enabled =
            environmentVisible &&
                environmentMode == SceneEnvironmentMode.VIRTUAL &&
                !(skyboxVisible && hasSkybox)
    }

    private suspend fun ensureVirtualEnvironmentCreated() {
        if (virtualEnvironmentCreated) return
        virtualEnvironmentCreationMutex.withLock {
            if (!virtualEnvironmentCreated) createVirtualEnvironment()
        }
    }

    fun cloneVirtualEnvironmentForPortal(): Entity? =
        if (!virtualEnvironmentCreated) {
            null
        } else {
            runCatching {
                requireNotNull(environmentRoot.clone(
                    Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true),
                )).apply {
                    setName("showme3d_environment_portal_clone")
                    enabled = true
                }
            }.onFailure { Log.w(TAG, "Virtual environment portal clone failed", it) }.getOrNull()
        }

    fun syncViewpointMode(viewerPosition: Vector3? = null, viewerForward: Vector3? = null) {
        val viewpoint = vm.activeViewpointTransform
        syncViewpointInteractionState(viewpoint != null)
        workspaceRoot.components.get(TransformComponent::class.java)?.apply {
            if (viewpoint == null) {
                setPosition(Vector3(0f, 0f, 0f))
                setEulerAngles(EulerAngles(0f, 0f, 0f))
                setScaleVector(Vector3(1f))
                viewpointTargetEye = ViewpointPoint(
                    VIEWPOINT_TARGET_X,
                    VIEWPOINT_EYE_HEIGHT_METERS,
                    VIEWPOINT_TARGET_Z,
                )
                viewpointTargetForward = ViewpointPoint(0f, 0f, -1f)
                return
            }
            viewerPosition?.takeIf { it.isFiniteVector() }?.let {
                viewpointTargetEye = ViewpointPoint(it.x, it.y, it.z)
            }
            viewerForward?.takeIf { it.isFiniteVector() }?.let {
                viewpointTargetForward = ViewpointPoint(it.x, it.y, it.z)
            }
            val scale = viewpointSceneScale(viewpoint)
            val eye = calibratedViewpointEye(viewpoint)
            val rootPose = calculateViewpointRootPose(
                sourceEye = ViewpointPoint(eye.x, eye.y, eye.z),
                sourceYawRadians = viewpoint.rotationY,
                scale = scale,
                targetEye = viewpointTargetEye,
                targetForward = viewpointTargetForward,
            )
            setPosition(Vector3(rootPose.position.x, rootPose.position.y, rootPose.position.z))
            setEulerAngles(EulerAngles(0f, Math.toDegrees(rootPose.yawRadians.toDouble()).toFloat(), 0f))
            setScaleVector(Vector3(rootPose.scale))
        }
    }

    private fun syncViewpointInteractionState(suppress: Boolean) {
        if (viewpointInteractionsSuppressed == suppress) return
        viewpointInteractionsSuppressed = suppress
        workspaceRoot.visitRecursively { entity ->
            val hasCollision = entity.components.get(CollisionComponent::class.java) != null
            val hasInteraction = entity.components.get(InteractableComponent::class.java) != null
            when {
                suppress && hasInteraction ->
                    entity.components.remove(InteractableComponent::class.java)
                !suppress && hasCollision && !hasInteraction ->
                    entity.components.set(InteractableComponent())
            }
        }
    }

    private fun calibratedViewpointEye(viewpoint: TransformData): Vector3 {
        val cosYaw = cos(viewpoint.rotationY)
        val sinYaw = sin(viewpoint.rotationY)
        val localX = 0f
        val localY = VIEWPOINT_MODEL_TARGET_HEIGHT * (VIEWPOINT_EYE_HEIGHT_RATIO - 0.5f) * viewpoint.scale
        val localZ = 0f
        return Vector3(
            viewpoint.px + localX * cosYaw - localZ * sinYaw,
            viewpoint.py + localY,
            viewpoint.pz + localX * sinYaw + localZ * cosYaw,
        )
    }

    private fun viewpointSceneScale(viewpoint: TransformData): Float {
        val scaledEyeHeight = VIEWPOINT_MODEL_TARGET_HEIGHT * VIEWPOINT_EYE_HEIGHT_RATIO *
            viewpoint.scale.coerceIn(VIEWPOINT_MIN_SCALE, VIEWPOINT_MAX_SCALE)
        return (VIEWPOINT_EYE_HEIGHT_METERS / scaledEyeHeight.coerceAtLeast(0.001f))
            .coerceIn(VIEWPOINT_ROOT_MIN_SCALE, VIEWPOINT_ROOT_MAX_SCALE)
    }

    private suspend fun createEntity(obj: SceneObject, reportStatus: Boolean = true): Entity? {
        var usedFallback = false
        val entity = try {
            if (obj.type == ObjectType.STROKE) {
                withContext(Dispatchers.Main.immediate) { createStrokeEntity(obj) }
            } else if (obj.type == ObjectType.VIEWPOINT) {
                createViewpointModelEntity(obj)
            } else if (obj.type == ObjectType.MODEL && obj.source.isLoadableModelSource()) {
                createImportedModelEntity(obj)
            } else {
                withContext(Dispatchers.Main.immediate) { ObjectEntityFactory.create(obj) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create failed for ${obj.displayName} (${obj.type})", e)
            vm.reportSceneError("${obj.displayName} fallback: ${e.javaClass.simpleName}")
            usedFallback = true
            createFallbackEntity(obj) ?: return null
        }
        withContext(Dispatchers.Main.immediate) {
            entities[obj.id] = entity
            renderKeys[obj.id] = obj.renderKey()
            interactionSizes[obj.id] = if (usedFallback) {
                Vector3(ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE)
            } else if (obj.type == ObjectType.VIEWPOINT) {
                visualInteractionSize(entity, VIEWPOINT_MODEL_FALLBACK_INTERACTION_SIZE)
            } else if (obj.type == ObjectType.STROKE) {
                ObjectEntityFactory.interactionSize(obj)
            } else if (obj.type == ObjectType.MODEL && obj.source.isLoadableModelSource()) {
                visualInteractionSize(entity, importedModelFallbackInteractionSize())
            } else {
                ObjectEntityFactory.interactionSize(obj)
            }
            entity.enabled = false
            vm.currentPage.states[obj.id]?.let { state ->
                applyRawTransform(entity, visibleTransformFor(obj.id, state.transform))
            }
            workspaceRoot.addChild(entity)
            val message = if (usedFallback) {
                "Fallback visible: ${obj.displayName}. Total: ${entities.size}"
            } else {
                "Entity visible: ${obj.displayName}. Total: ${entities.size}"
            }
            if (reportStatus) {
                vm.reportSceneError(message)
            }
            sceneVersion++
        }
        return entity
    }

    private suspend fun createViewpointModelEntity(obj: SceneObject): Entity {
        val wrapper = Entity()
        wrapper.setName(obj.id)
        val modelRoot = Entity.loadSuspend(VIEWPOINT_MODEL_ASSET)
        modelRoot.setName("${obj.id}_standing_man")
        wrapper.addChild(modelRoot)

        val bounds = modelRoot.getVisualBounds(modelRoot, recursive = true, enabledOnly = false)
        val normalization = viewpointModelNormalization(bounds)
        val modelPosition = normalization.position
        modelRoot.components.get(TransformComponent::class.java)?.apply {
            setPosition(modelPosition)
            setEulerAngles(normalization.eulerAngles)
            setScaleVector(Vector3(normalization.scale))
        }
        val actualBounds = wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
        val interactionSize = normalization.interactionSize.takeIf { it.hasUsableSize() }
            ?: actualBounds.size.takeIf { it.hasUsableSize() }
            ?: VIEWPOINT_MODEL_FALLBACK_INTERACTION_SIZE
        val meshColliderCount = addMeshInteractions(modelRoot, wrapper, interactionSize)
        Log.i(
            TAG,
            "Viewpoint normalized: rawCenter=${bounds.center}, rawSize=${bounds.size}, " +
                "axis=${normalization.dominantAxis}, scale=${normalization.scale}, " +
                "position=${normalization.position}, actualCenter=${actualBounds.center}, " +
                "size=$interactionSize, " +
                "meshColliders=$meshColliderCount",
        )
        return wrapper
    }

    private fun viewpointModelNormalization(bounds: com.pico.spatial.core.ecs.BoundingBox): ModelNormalization {
        return normalizeModelToCenteredOrigin(
            bounds = bounds,
            targetDominantSide = VIEWPOINT_MODEL_TARGET_HEIGHT,
            fallbackSize = VIEWPOINT_MODEL_FALLBACK_INTERACTION_SIZE,
            alignDominantAxisToWorldY = true,
        )
    }

    private suspend fun createImportedModelEntity(obj: SceneObject): Entity {
        val wrapper = Entity()
        wrapper.setName(obj.id)
        val modelRoot = Entity.loadSuspend(obj.source)
        modelRoot.setName("${obj.id}model")
        wrapper.addChild(modelRoot)

        val bounds = modelRoot.getVisualBounds(modelRoot, recursive = true, enabledOnly = false)
        val size = bounds.size
        val correctedBounds = if (obj.modelOriginMode == ModelOriginMode.SHARED_ORIGIN) {
            // Assembly assets are authored in one common, already normalized frame.
            // Moving or fitting each file independently would break their exact pose.
            wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
        } else {
            val scale = ObjectEntityFactory.importedAssetScaleFor(size.maxSide())
            val modelPosition = Vector3(
                -bounds.center.x * scale,
                -bounds.center.y * scale,
                -bounds.center.z * scale,
            )
            modelRoot.components.get(TransformComponent::class.java)?.apply {
                setPosition(modelPosition)
                setScaleVector(Vector3(scale))
            }
            recenterModelRootToActualBounds(modelRoot, wrapper, modelPosition)
        }
        val fallbackSize = correctedBounds.size.takeIf { it.hasUsableSize() }
            ?: size.takeIf { it.hasUsableSize() }
            ?: importedModelFallbackInteractionSize()
        addMeshInteractions(
            wrapper = wrapper,
            modelRoot = modelRoot,
            fallbackSize = fallbackSize,
            hoverEffectEnabled = false,
        )
        importedModelRoots[obj.id] = modelRoot
        return wrapper
    }

    private fun recenterModelRootToActualBounds(
        modelRoot: Entity,
        wrapper: Entity,
        currentPosition: Vector3,
    ): com.pico.spatial.core.ecs.BoundingBox {
        val actualBounds = wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
        val actualCenter = actualBounds.center
        if (actualBounds.size.hasUsableSize()) {
            modelRoot.components.get(TransformComponent::class.java)?.apply {
                setPosition(
                    Vector3(
                        currentPosition.x - actualCenter.x,
                        currentPosition.y - actualCenter.y,
                        currentPosition.z - actualCenter.z,
                    )
                )
            }
        }
        return wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
    }

    private fun visualInteractionSize(entity: Entity, fallback: Vector3): Vector3 {
        val size = entity.getVisualBounds(entity, recursive = true, enabledOnly = false).size
        return size.takeIf { it.hasUsableSize() }
            ?: fallback
    }

    private fun normalizedSize(size: Vector3, scale: Float, fallback: Vector3): Vector3 =
        if (size.hasUsableSize()) {
            Vector3(size.x * scale, size.y * scale, size.z * scale)
        } else {
            fallback
        }

    private fun addMeshInteractions(
        modelRoot: Entity,
        wrapper: Entity,
        fallbackSize: Vector3,
        hoverEffectEnabled: Boolean = true,
    ): Int {
        val meshEntities = modelRoot.meshEntitiesRecursive()
        var addedCount = 0
        meshEntities.forEach { meshEntity ->
            val model = meshEntity.components.get(ModelComponent::class.java) ?: return@forEach
            val shape = runCatching { ShapeResource.createConvexMesh(model.mesh) }
                .onFailure { Log.w(TAG, "Mesh collider failed for ${meshEntity.getName()}", it) }
                .getOrNull()
                ?: return@forEach
            meshEntity.components.set(
                CollisionComponent(
                    collisionShape = listOf(shape),
                    physicsMaterial = PhysicsMaterialResource(),
                )
            )
            if (!viewpointInteractionsSuppressed) {
                meshEntity.components.set(InteractableComponent())
            }
            if (hoverEffectEnabled) {
                meshEntity.components.set(HoverEffectComponent())
            }
            addedCount++
        }
        if (addedCount == 0) {
            ObjectEntityFactory.addInteraction(wrapper, fallbackSize, hoverEffectEnabled)
            if (viewpointInteractionsSuppressed) {
                wrapper.components.remove(InteractableComponent::class.java)
            }
            Log.w(TAG, "Mesh collider unavailable for ${wrapper.getName()}, using centered fallback box $fallbackSize")
        }
        return addedCount
    }

    private fun startImportedAnimations(obj: SceneObject, modelRoot: Entity) {
        stopImportedAnimations(obj.id)
        val controllers = mutableListOf<AnimationPlaybackController>()
        // Adjacent-page prewarming keeps the wrapper disabled until the page
        // transition starts. Include inactive descendants so a prewarmed GLB
        // can still expose its skinned mesh and animation resources here.
        val skinnedMeshEntities = runCatching {
            modelRoot.findSkinnedMeshEntity(true)
        }
            .onFailure { Log.w(TAG, "Skinned mesh lookup failed for ${obj.displayName}", it) }
            .getOrDefault(emptyArray())
        if (skinnedMeshEntities.isEmpty()) {
            Log.w(TAG, "No skinned mesh found for embedded animation: ${obj.displayName}")
            return
        }
        Log.i(TAG, "Found ${skinnedMeshEntities.size} skinned meshes for ${obj.displayName}")
        skinnedMeshEntities.forEach { skinnedMeshEntity ->
            runCatching {
                val resources = skinnedMeshEntity.getAnimationResources()
                Log.i(
                    TAG,
                    "Found ${resources.size} animation resources on ${skinnedMeshEntity.getName()}",
                )
                val first = resources.firstOrNull() ?: return@runCatching
                first.use { animation ->
                    // Imported skeletal clips loop by default. Playing the clip directly keeps
                    // the final reconstruction moving until the page is left or playback stops.
                    val controller = skinnedMeshEntity.playAnimation(animation)
                    if (controller.valid) {
                        controllers += controller
                    } else {
                        controller.close()
                        Log.w(TAG, "Animation controller is invalid for ${obj.displayName}")
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Embedded animation failed for ${obj.displayName}", error)
            }
        }
        if (controllers.isNotEmpty()) {
            importedAnimationControllers[obj.id] = controllers
            Log.i(TAG, "Started ${controllers.size} embedded animations for ${obj.displayName}")
        }
    }

    private fun syncEmbeddedAnimationPlayback(obj: SceneObject, visible: Boolean) {
        if (!obj.playEmbeddedAnimation || !visible) {
            stopImportedAnimations(obj.id)
            return
        }
        if (obj.id in importedAnimationControllers) return
        importedModelRoots[obj.id]?.let { modelRoot -> startImportedAnimations(obj, modelRoot) }
    }

    private fun stopImportedAnimations(objectId: String) {
        importedAnimationControllers.remove(objectId)?.forEach { controller ->
            runCatching { controller.close() }
        }
    }

    private fun normalizeModelToCenteredOrigin(
        bounds: com.pico.spatial.core.ecs.BoundingBox,
        targetDominantSide: Float,
        fallbackSize: Vector3,
        alignDominantAxisToWorldY: Boolean,
    ): ModelNormalization {
        val rawSize = bounds.size
        if (!rawSize.hasUsableSize()) {
            return ModelNormalization(
                scale = 1f,
                position = Vector3(0f, 0f, 0f),
                eulerAngles = EulerAngles(0f, 0f, 0f),
                interactionSize = fallbackSize,
                dominantAxis = ModelAxis.Y,
            )
        }
        val dominantAxis = rawSize.dominantAxis()
        val dominantSide = rawSize.component(dominantAxis)
        val scale = if (dominantSide.isFinite() && dominantSide > 0.0001f) {
            targetDominantSide / dominantSide
        } else {
            1f
        }
        val center = if (alignDominantAxisToWorldY) {
            bounds.center.rotatedToPutAxisOnY(dominantAxis)
        } else {
            bounds.center
        }
        val size = if (alignDominantAxisToWorldY) {
            rawSize.sizeAfterAxisToY(dominantAxis)
        } else {
            rawSize
        }
        return ModelNormalization(
            scale = scale,
            position = Vector3(-center.x * scale, -center.y * scale, -center.z * scale),
            eulerAngles = if (alignDominantAxisToWorldY) dominantAxis.eulerAnglesToY() else EulerAngles(0f, 0f, 0f),
            interactionSize = Vector3(size.x * scale, size.y * scale, size.z * scale),
            dominantAxis = dominantAxis,
        )
    }

    private fun importedModelFallbackInteractionSize(): Vector3 =
        Vector3(
            ObjectEntityFactory.IMPORTED_ASSET_MAX_SIDE,
            ObjectEntityFactory.IMPORTED_ASSET_MAX_SIDE,
            ObjectEntityFactory.IMPORTED_ASSET_MAX_SIDE,
        )

    private fun Vector3.maxSide(): Float = max(max(x, y), z)

    private fun Vector3.hasUsableSize(): Boolean =
        x.isFinite() && y.isFinite() && z.isFinite() && maxSide() > 0.0001f

    private fun Entity.meshEntitiesRecursive(): List<Entity> {
        val result = mutableListOf<Entity>()
        visitRecursively { entity ->
            if (entity.components.get(ModelComponent::class.java) != null) {
                result += entity
            }
        }
        return result
    }

    private fun Entity.visitRecursively(action: (Entity) -> Unit) {
        action(this)
        getChildren().forEach { child -> child.visitRecursively(action) }
    }

    private fun Vector3.dominantAxis(): ModelAxis =
        when {
            x >= y && x >= z -> ModelAxis.X
            y >= z -> ModelAxis.Y
            else -> ModelAxis.Z
        }

    private fun Vector3.component(axis: ModelAxis): Float =
        when (axis) {
            ModelAxis.X -> x
            ModelAxis.Y -> y
            ModelAxis.Z -> z
        }

    private fun Vector3.rotatedToPutAxisOnY(axis: ModelAxis): Vector3 =
        when (axis) {
            ModelAxis.X -> Vector3(-y, x, z)
            ModelAxis.Y -> this
            ModelAxis.Z -> Vector3(x, z, -y)
        }

    private fun Vector3.sizeAfterAxisToY(axis: ModelAxis): Vector3 =
        when (axis) {
            ModelAxis.X -> Vector3(y, x, z)
            ModelAxis.Y -> this
            ModelAxis.Z -> Vector3(x, z, y)
        }

    private fun ModelAxis.eulerAnglesToY(): EulerAngles =
        when (this) {
            ModelAxis.X -> EulerAngles(0f, 0f, 90f)
            ModelAxis.Y -> EulerAngles(0f, 0f, 0f)
            ModelAxis.Z -> EulerAngles(-90f, 0f, 0f)
        }

    private suspend fun createFallbackEntity(obj: SceneObject): Entity? =
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                if (obj.type == ObjectType.VIEWPOINT) {
                    ObjectEntityFactory.create(obj)
                } else {
                    ObjectEntityFactory.createPlaceholder(obj)
                }
            }
                .onFailure { Log.e(TAG, "Fallback creation failed for ${obj.displayName}", it) }
                .getOrNull()
        }

    private fun removeEntity(objectId: String) {
        stopImportedAnimations(objectId)
        importedModelRoots.remove(objectId)
        entities.remove(objectId)?.let { runCatching { it.destroy(true) } }
        renderKeys.remove(objectId)
        interactionSizes.remove(objectId)
        lastTransforms.remove(objectId)
        lastVisibility.remove(objectId)
        lastPageAnimations.remove(objectId)
        animationClockStartSeconds.remove(objectId)
        autoRotationClockStartSeconds.remove(objectId)
        pageTransitionLockedObjectIds.remove(objectId)
        currentAnimationTransforms.remove(objectId)
        lastPageObjectIds.remove(objectId)
        strokePreviews.remove(objectId)?.let {
            clearStrokeMeshGeometry(it)
            runCatching { it.root.destroy(true) }
        }
        sceneVersion++
    }

    private fun removeAnimationEndpoint(endpointId: String) {
        animationEndpointEntities.remove(endpointId)?.let { runCatching { it.destroy(true) } }
        animationEndpointLabels.remove(endpointId)?.let { runCatching { it.destroy(true) } }
        interactionSizes.remove(endpointId)
        lastAnimationPathStart = null
        lastAnimationPathEnd = null
        sceneVersion++
    }

    private fun createAnimationEndpoint(endpoint: AnimationEndpointDraft): Entity {
        val entity = try {
            ObjectEntityFactory.create(endpoint.sourceObject.copy(id = endpoint.id))
        } catch (_: Exception) {
            ObjectEntityFactory.createPlaceholder(endpoint.sourceObject.copy(id = endpoint.id))
        }
        entity.setName(endpoint.id)
        return entity
    }

    private fun applyEndpointTransform(entity: Entity, endpoint: AnimationEndpointDraft) {
        applyRawTransform(entity, endpoint.transform)
        lastTransforms[endpoint.id] = endpoint.transform
    }

    private fun createOrUpdateAnimationEndpointLabel(endpoint: AnimationEndpointDraft) {
        val label = animationEndpointLabels[endpoint.id] ?: createAnimationEndpointLabel(endpoint).also {
            animationEndpointLabels[endpoint.id] = it
            workspaceRoot.addChild(it)
            sceneVersion++
        }
        label.enabled = true
        val endpointSize = interactionSizes[endpoint.id]
            ?: ObjectEntityFactory.interactionSize(endpoint.sourceObject)
        val belowOffset = ((endpointSize.y * endpoint.transform.scale) / 2f) + ANIMATION_ENDPOINT_BADGE_GAP
        val transform = endpoint.transform.copy(
            py = endpoint.transform.py - belowOffset,
            pz = endpoint.transform.pz + ANIMATION_ENDPOINT_BADGE_Z_OFFSET,
            scale = ANIMATION_ENDPOINT_BADGE_SCALE,
            rotationY = 0f,
        )
        applyRawTransform(label, transform)
    }

    private fun createAnimationEndpointLabel(endpoint: AnimationEndpointDraft): Entity =
        createEndpointBadge(endpoint).apply {
            setName("${endpoint.id}_label")
        }

    private fun createEndpointBadge(endpoint: AnimationEndpointDraft): Entity {
        val root = Entity()
        val badgeColor = when (endpoint.role) {
            AnimationEndpointRole.START -> Color4(0.1f, 0.23f, 0.72f, 0.92f)
            AnimationEndpointRole.END -> Color4(0.76f, 0.08f, 0.2f, 0.92f)
        }
        val background = createEndpointRoundedBadgeBackground(endpoint.id, badgeColor)
        val text = ObjectEntityFactory.create(
            SceneObject(
                id = "${endpoint.id}_badge_text",
                type = ObjectType.TEXT,
                source = if (endpoint.role == AnimationEndpointRole.START) "Start" else "End",
                displayName = if (endpoint.role == AnimationEndpointRole.START) "Start" else "End",
                textColor = 0xFFFFFFFF,
            )
        ).apply {
            setName("${endpoint.id}_badge_text")
            components.get(TransformComponent::class.java)?.apply {
                setPosition(Vector3(0f, -0.006f, ANIMATION_ENDPOINT_BADGE_TEXT_Z))
                setScaleVector(Vector3(ANIMATION_ENDPOINT_BADGE_TEXT_SCALE))
            }
        }
        root.addChild(background)
        root.addChild(text)
        root.components.set(
            LookAtComponent().apply {
                setViewerAsTarget()
                alignLocalUpToWorldUp = false
                lookAtForwardDirection = LookAtForwardDirection.POSITIVE_Z
            }
        )
        return root
    }

    private fun createEndpointRoundedBadgeBackground(endpointId: String, badgeColor: Color4): Entity {
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(badgeColor)
        }
        val root = Entity().apply { setName("${endpointId}_badge_bg") }
        val centerWidth = ANIMATION_ENDPOINT_BADGE_WIDTH - ANIMATION_ENDPOINT_BADGE_HEIGHT
        val center = ModelEntity(
            MeshResource.createBox(Vector3(centerWidth, ANIMATION_ENDPOINT_BADGE_HEIGHT, ANIMATION_ENDPOINT_BADGE_DEPTH)),
            material,
        ).apply {
            setName("${endpointId}_badge_center")
        }
        val leftCap = ModelEntity(
            MeshResource.createSphere(radius = ANIMATION_ENDPOINT_BADGE_HEIGHT * 0.5f),
            material,
        ).apply {
            setName("${endpointId}_badge_left_cap")
            components.get(TransformComponent::class.java)?.apply {
                setPosition(Vector3(-centerWidth * 0.5f, 0f, 0f))
                setScaleVector(Vector3(1f, 1f, ANIMATION_ENDPOINT_BADGE_DEPTH / ANIMATION_ENDPOINT_BADGE_HEIGHT))
            }
        }
        val rightCap = ModelEntity(
            MeshResource.createSphere(radius = ANIMATION_ENDPOINT_BADGE_HEIGHT * 0.5f),
            material,
        ).apply {
            setName("${endpointId}_badge_right_cap")
            components.get(TransformComponent::class.java)?.apply {
                setPosition(Vector3(centerWidth * 0.5f, 0f, 0f))
                setScaleVector(Vector3(1f, 1f, ANIMATION_ENDPOINT_BADGE_DEPTH / ANIMATION_ENDPOINT_BADGE_HEIGHT))
            }
        }
        root.addChild(center)
        root.addChild(leftCap)
        root.addChild(rightCap)
        return root
    }

    private fun syncAnimationEndpointLabels() {
        vm.animationDraftEndpoints.forEach(::createOrUpdateAnimationEndpointLabel)
    }

    private fun syncAnimationEndpointChrome(force: Boolean = false) {
        val endpoints = vm.animationDraftEndpoints
        endpoints.forEach { endpoint ->
            updateAnimationEndpointLabel(endpoint.id, endpoint.transform)
        }
        syncAnimationPath(endpoints, force = force)
        vm.selectedAnimationEndpoint?.let { endpoint ->
            val box = highlight ?: createHighlight()?.also { highlight = it }
            box?.enabled = true
            applyEndpointHighlightTransform(endpoint.id, endpoint.transform)
        }
        animationEndpointChromeDirty = false
    }

    private fun updateAnimationEndpointLabel(endpointId: String, transform: TransformData) {
        val endpoint = vm.animationDraftEndpoints.firstOrNull { it.id == endpointId } ?: return
        val label = animationEndpointLabels[endpoint.id] ?: return
        label.enabled = true
        val endpointSize = interactionSizes[endpoint.id]
            ?: ObjectEntityFactory.interactionSize(endpoint.sourceObject)
        val belowOffset = ((endpointSize.y * transform.scale) / 2f) + ANIMATION_ENDPOINT_BADGE_GAP
        applyRawTransform(
            label,
            transform.copy(
                py = transform.py - belowOffset,
                pz = transform.pz + ANIMATION_ENDPOINT_BADGE_Z_OFFSET,
                scale = ANIMATION_ENDPOINT_BADGE_SCALE,
                rotationY = 0f,
            ),
        )
    }

    private fun syncAnimationPath(endpoints: List<AnimationEndpointDraft>, force: Boolean = false) {
        val start = endpoints.firstOrNull { it.role == AnimationEndpointRole.START }?.transform
        val end = endpoints.firstOrNull { it.role == AnimationEndpointRole.END }?.transform
        if (start == null || end == null) {
            animationPathSegments.forEach { it.enabled = false }
            lastAnimationPathStart = null
            lastAnimationPathEnd = null
            return
        }
        if (
            !force &&
            lastAnimationPathStart?.approximatelyEquals(start, ANIMATION_PATH_UPDATE_EPSILON) == true &&
            lastAnimationPathEnd?.approximatelyEquals(end, ANIMATION_PATH_UPDATE_EPSILON) == true
        ) {
            return
        }
        lastAnimationPathStart = start
        lastAnimationPathEnd = end
        ensureAnimationPathSegments()
        val dx = end.px - start.px
        val dy = end.py - start.py
        val dz = end.pz - start.pz
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (length <= 0.001f) {
            animationPathSegments.forEach { it.enabled = false }
            return
        }
        val yaw = kotlin.math.atan2(dx, dz)
        val pitch = -kotlin.math.asin((dy / length).coerceIn(-1f, 1f))
        val activeSegments = minOf(ANIMATION_PATH_DASH_COUNT, maxOf(1, (length / ANIMATION_PATH_DASH_SPACING).toInt()))
        animationPathSegments.forEachIndexed { index, segment ->
            segment.enabled = index < activeSegments
            if (!segment.enabled) return@forEachIndexed
            val t = (index + 0.5f) / activeSegments
            segment.components.get(TransformComponent::class.java)?.apply {
                setPosition(
                    Vector3(
                        start.px + dx * t,
                        start.py + dy * t,
                        start.pz + dz * t,
                    )
                )
                setEulerAngles(
                    EulerAngles(
                        Math.toDegrees(pitch.toDouble()).toFloat(),
                        Math.toDegrees(yaw.toDouble()).toFloat(),
                        0f,
                    )
                )
                val dashLength = (length / activeSegments * ANIMATION_PATH_DASH_FILL).coerceIn(
                    ANIMATION_PATH_DASH_MIN_LENGTH,
                    ANIMATION_PATH_DASH_MAX_LENGTH,
                )
                setScaleVector(Vector3(ANIMATION_PATH_DASH_THICKNESS, ANIMATION_PATH_DASH_THICKNESS, dashLength))
            }
        }
    }

    private fun ensureAnimationPathSegments() {
        if (animationPathSegments.isNotEmpty()) return
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4(0.42f, 0.45f, 0.5f, 0.72f))
        }
        repeat(ANIMATION_PATH_DASH_COUNT) { index ->
            val segment = ModelEntity(MeshResource.createBox(Vector3(1f, 1f, 1f)), material).apply {
                setName("animation_path_dash_$index")
            }
            animationPathSegments.add(segment)
            workspaceRoot.addChild(segment)
        }
        sceneVersion++
    }

    private fun syncHighlight() {
        val selectedId = vm.selectedObjectId
        vm.selectedAnimationEndpoint?.let { endpoint ->
            val box = highlight ?: createHighlight()?.also { highlight = it } ?: return
            box.enabled = true
            applyEndpointHighlightTransform(endpoint.id, endpoint.transform)
            return
        }
        val state = selectedId?.let { vm.currentPage.states[it] }
        if (selectedId == null || state == null || !state.visible) {
            highlight?.enabled = false
            return
        }
        if (vm.animationDraft?.objectId == selectedId) {
            highlight?.enabled = false
            return
        }
        val box = highlight ?: createHighlight()?.also { highlight = it } ?: return
        box.enabled = true
        if (vm.selectedGroupMemberIds.size > 1) {
            applyGroupHighlightTransform()
        } else {
            applyHighlightTransform(state.transform)
        }
    }

    private fun createHighlight(): Entity? = try {
        Entity().apply {
            setName("selectionHighlight")
            highlightSegments = createHighlightSegments(this)
            highlightLayoutKey = null
            highlightLocked = null
            workspaceRoot.addChild(this)
        }
    } catch (_: Exception) {
        highlightSegments = emptyList()
        highlightLayoutKey = null
        highlightLocked = null
        null
    }

    private fun createHighlightSegments(root: Entity): List<HighlightSegment> {
        val segments = mutableListOf<HighlightSegment>()
        listOf(-1f, 1f).forEach { a ->
            listOf(-1f, 1f).forEach { b ->
                segments.addHighlightEdge(root, HighlightAxis.X, a, b)
                segments.addHighlightEdge(root, HighlightAxis.Y, a, b)
                segments.addHighlightEdge(root, HighlightAxis.Z, a, b)
            }
        }
        return segments
    }

    private fun MutableList<HighlightSegment>.addHighlightEdge(
        root: Entity,
        axis: HighlightAxis,
        signA: Float,
        signB: Float,
    ) {
        repeat(HIGHLIGHT_DASH_SEGMENT_COUNT) { segmentIndex ->
            add(root.addHighlightSegment(axis, signA, signB, segmentIndex))
        }
    }

    private fun Entity.addHighlightSegment(
        axis: HighlightAxis,
        signA: Float,
        signB: Float,
        segmentIndex: Int,
    ): HighlightSegment {
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(HIGHLIGHT_COLOR)
        }
        val segment = ModelEntity(MeshResource.createBox(Vector3(1f, 1f, 1f)), material).apply {
            setName("selectionHighlightDash")
        }
        addChild(segment)
        return HighlightSegment(segment, axis, signA, signB, segmentIndex, material)
    }

    private fun applyHighlightTransform(transform: TransformData) {
        if (vm.selectedGroupMemberIds.size > 1) {
            applyGroupHighlightTransform()
            return
        }
        val selectedId = vm.selectedObjectId
        val selectedObject = selectedId?.let { vm.document.objects[it] }
        syncViewerFacing(
            entity = highlight,
            enabled = selectedObject
                ?.takeIf { it.type == ObjectType.TEXT || it.type == ObjectType.IMAGE }
                ?.faceViewer == true,
        )
        updateHighlightColor(selectedId?.let { vm.document.objects[it]?.isLocked } == true)
        val baseSize = selectedId?.let { interactionSizes[it] }
            ?: Vector3(ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE)
        highlight?.components?.get(TransformComponent::class.java)?.apply {
            val isFlatText = selectedId
                ?.let { vm.document.objects[it]?.type }
                ?.let { it == ObjectType.IMAGE || it == ObjectType.TEXT } == true
            setPosition(
                Vector3(
                    transform.px,
                    transform.py,
                    if (isFlatText && selectedObject?.faceViewer != true) {
                        transform.pz + HIGHLIGHT_FLAT_Z_OFFSET
                    } else {
                        transform.pz
                    },
                )
            )
            setEulerAngles(EulerAngles(0f, Math.toDegrees(transform.rotationY.toDouble()).toFloat(), 0f))
            setScaleVector(Vector3(1f))
            updateHighlightSegmentsIfNeeded(
                size = Vector3(
                    (baseSize.x * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    (baseSize.y * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    if (isFlatText) {
                        HIGHLIGHT_FLAT_DEPTH
                    } else {
                        (baseSize.z * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE)
                    },
                ),
                flat = isFlatText,
            )
        }
    }

    private fun applyGroupHighlightTransform() {
        val bounds = selectedGroupBounds() ?: run {
            highlight?.enabled = false
            return
        }
        syncViewerFacing(highlight, enabled = false)
        val locked = vm.selectedGroupMemberIds.any { vm.document.objects[it]?.isLocked == true }
        updateHighlightColor(locked)
        highlight?.components?.get(TransformComponent::class.java)?.apply {
            setPosition(bounds.center)
            setEulerAngles(EulerAngles(0f, 0f, 0f))
            setScaleVector(Vector3(1f))
            updateHighlightSegmentsIfNeeded(
                size = Vector3(
                    (bounds.size.x * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    (bounds.size.y * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    (bounds.size.z * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                ),
                flat = false,
            )
        }
    }

    private fun selectedGroupBounds(): GroupSelectionBounds? {
        val selectedId = vm.selectedObjectId ?: return null
        val transforms = vm.groupTransformsFor(selectedId)
        if (transforms.size < 2) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        transforms.forEach { (memberId, transform) ->
            val baseSize = interactionSizes[memberId]
                ?: Vector3(ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE)
            val width = baseSize.x * transform.scale
            val height = baseSize.y * transform.scale
            val depth = baseSize.z * transform.scale
            val cosine = abs(kotlin.math.cos(transform.rotationY))
            val sine = abs(kotlin.math.sin(transform.rotationY))
            val halfX = (width * cosine + depth * sine) * 0.5f
            val halfZ = (width * sine + depth * cosine) * 0.5f
            val halfY = height * 0.5f
            minX = min(minX, transform.px - halfX)
            maxX = max(maxX, transform.px + halfX)
            minY = min(minY, transform.py - halfY)
            maxY = max(maxY, transform.py + halfY)
            minZ = min(minZ, transform.pz - halfZ)
            maxZ = max(maxZ, transform.pz + halfZ)
        }
        if (!minX.isFinite() || !maxX.isFinite()) return null
        return GroupSelectionBounds(
            center = Vector3((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f),
            size = Vector3(maxX - minX, maxY - minY, maxZ - minZ),
        )
    }

    private fun applyEndpointHighlightTransform(endpointId: String, transform: TransformData) {
        syncViewerFacing(highlight, enabled = false)
        updateHighlightColor(locked = false)
        val baseSize = interactionSizes[endpointId]
            ?: Vector3(ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE, ObjectEntityFactory.BOX_SIZE)
        highlight?.components?.get(TransformComponent::class.java)?.apply {
            setPosition(Vector3(transform.px, transform.py, transform.pz))
            setEulerAngles(EulerAngles(0f, Math.toDegrees(transform.rotationY.toDouble()).toFloat(), 0f))
            setScaleVector(Vector3(1f))
            updateHighlightSegmentsIfNeeded(
                size = Vector3(
                    (baseSize.x * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    (baseSize.y * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                    (baseSize.z * transform.scale * HIGHLIGHT_PADDING).coerceAtLeast(HIGHLIGHT_MIN_SIDE),
                ),
                flat = false,
            )
        }
    }

    private fun updateHighlightSegmentsIfNeeded(size: Vector3, flat: Boolean) {
        val key = HighlightLayoutKey(
            width = size.x,
            height = size.y,
            depth = size.z,
            flat = flat,
        )
        if (highlightLayoutKey == key) return
        highlightLayoutKey = key
        updateHighlightSegments(size, flat)
    }

    private fun updateHighlightSegments(size: Vector3, flat: Boolean) {
        val halfX = size.x / 2f
        val halfY = size.y / 2f
        val halfZ = size.z / 2f
        val thickness = minOf(
            HIGHLIGHT_EDGE_MAX_THICKNESS,
            maxOf(HIGHLIGHT_EDGE_MIN_THICKNESS, minOf(size.x, size.y, size.z.coerceAtLeast(HIGHLIGHT_FLAT_DEPTH)) * 0.035f),
        )
        highlightSegments.forEach { segment ->
            val useForFlat = when (segment.axis) {
                HighlightAxis.X,
                HighlightAxis.Y,
                -> segment.signB > 0f
                HighlightAxis.Z -> false
            }
            segment.entity.enabled = !flat || useForFlat
            if (!segment.entity.enabled) return@forEach
            segment.entity.components.get(TransformComponent::class.java)?.apply {
                when (segment.axis) {
                    HighlightAxis.X -> {
                        val length = dashLength(size.x)
                        setPosition(Vector3(dashCenter(size.x, segment.segmentIndex), segment.signA * halfY, if (flat) 0f else segment.signB * halfZ))
                        setScaleVector(Vector3(length, thickness, thickness))
                    }
                    HighlightAxis.Y -> {
                        val length = dashLength(size.y)
                        setPosition(Vector3(segment.signA * halfX, dashCenter(size.y, segment.segmentIndex), if (flat) 0f else segment.signB * halfZ))
                        setScaleVector(Vector3(thickness, length, thickness))
                    }
                    HighlightAxis.Z -> {
                        val length = dashLength(size.z)
                        setPosition(Vector3(segment.signA * halfX, segment.signB * halfY, dashCenter(size.z, segment.segmentIndex)))
                        setScaleVector(Vector3(thickness, thickness, length))
                    }
                }
                setEulerAngles(EulerAngles(0f, 0f, 0f))
            }
        }
    }

    private fun updateHighlightColor(locked: Boolean) {
        if (highlightLocked == locked) return
        highlightLocked = locked
        val color = if (locked) HIGHLIGHT_LOCKED_COLOR else HIGHLIGHT_COLOR
        highlightSegments.forEach { it.material.setBaseColor(color) }
    }

    private fun dashLength(edgeLength: Float): Float =
        (edgeLength / HIGHLIGHT_DASH_SEGMENT_COUNT) * HIGHLIGHT_DASH_FILL_RATIO

    private fun dashCenter(edgeLength: Float, segmentIndex: Int): Float {
        val segmentStride = edgeLength / HIGHLIGHT_DASH_SEGMENT_COUNT
        return (-edgeLength / 2f) + segmentStride * (segmentIndex + 0.5f)
    }

    private fun visibleTransformFor(objectId: String, transform: TransformData): TransformData {
        return transform
    }

    private fun applyTransform(entity: Entity, transform: TransformData) {
        val objectId = entity.getName()
        applyRawTransform(entity, transform, previous = lastTransforms[objectId])
        lastTransforms[objectId] = transform
    }

    fun syncStrokeDraft() {
        val draft = vm.strokeDraft
        if (draft == null) {
            clearStrokePreviews()
            return
        }
        createOrUpdateStrokePreview(
            id = STROKE_DRAFT_PREVIEW_ID,
            data = draft.toCommittedStrokeData(),
            enabled = true,
        )
    }

    fun syncPageAnnotations() {
        val visibleStrokeAnnotations = if (vm.annotationsVisible) {
            vm.currentPage.annotations.filter { it.type == PageAnnotationType.STROKE }
        } else {
            emptyList()
        }
        val visibleCommentAnnotations = if (vm.annotationsVisible) {
            vm.currentPage.annotations.filter { it.type == PageAnnotationType.COMMENT }
        } else {
            emptyList()
        }
        val liveIds = visibleStrokeAnnotations.map { it.renderId(vm.currentPage.id) }.toSet()
        annotationPreviews.keys
            .filter { it !in liveIds && it != ANNOTATION_LIVE_PREVIEW_ID }
            .forEach(::removeAnnotationPreview)
        visibleStrokeAnnotations.forEach { annotation ->
            val strokeData = annotation.strokeData ?: return@forEach
            createOrUpdateAnnotationPreview(
                id = annotation.renderId(vm.currentPage.id),
                data = strokeData,
                enabled = true,
            )
        }
        val liveCommentIds = visibleCommentAnnotations.map { it.renderId(vm.currentPage.id) }.toSet()
        commentPreviews.keys
            .filter { it !in liveCommentIds && it != COMMENT_LIVE_PREVIEW_ID }
            .forEach(::removeCommentPreview)
        visibleCommentAnnotations.forEach { annotation ->
            createOrUpdateCommentPreview(
                id = annotation.renderId(vm.currentPage.id),
                annotation = annotation,
                enabled = true,
                showCard = vm.editingAnnotationComment?.id != annotation.id,
            )
        }
        if (!vm.annotationsVisible) {
            annotationPreviews.values.forEach { preview ->
                preview.visible = false
                preview.root.enabled = false
            }
            commentPreviews.values.forEach { preview ->
                preview.visible = false
                preview.root.enabled = false
            }
        }
    }

    fun syncAnnotationDraft() {
        val strokeDraft = vm.annotationStrokeDraft
        if (strokeDraft?.activePath == null) {
            removeAnnotationPreview(ANNOTATION_LIVE_PREVIEW_ID)
        } else {
            createOrUpdateAnnotationPreview(
                id = ANNOTATION_LIVE_PREVIEW_ID,
                data = StrokeData(mutableListOf(strokeDraft.activePath.copyPath())),
                enabled = vm.annotationsVisible,
            )
        }
        val commentDraft = vm.annotationCommentDraft
        if (commentDraft == null) {
            removeCommentPreview(COMMENT_LIVE_PREVIEW_ID)
        } else {
            createOrUpdateCommentPreview(
                id = COMMENT_LIVE_PREVIEW_ID,
                annotation = PageAnnotation(
                    id = COMMENT_LIVE_PREVIEW_ID,
                    type = PageAnnotationType.COMMENT,
                    commentText = COMMENT_PLACEHOLDER_TEXT,
                    anchorPoint = commentDraft.anchorPoint,
                    panelPoint = commentDraft.currentPoint,
                ),
                enabled = vm.annotationsVisible,
                showCard = false,
            )
        }
    }

    private fun createStrokeEntity(obj: SceneObject): Entity {
        val root = ObjectEntityFactory.create(obj)
        obj.strokeData?.let { strokeData ->
            addStrokeMeshChunks(
                root = root,
                data = strokeData,
                namePrefix = "${obj.id}_stroke_mesh",
                alpha = 1f,
                radius = STROKE_RADIUS,
                quality = StrokeMeshQuality.FINAL,
            )
        }
        return root
    }

    private fun createOrUpdateStrokePreview(id: String, data: StrokeData, enabled: Boolean) {
        val preview = strokePreviews[id] ?: StrokePreview(Entity().apply { setName(id) }).also {
            strokePreviews[id] = it
            workspaceRoot.addChild(it.root)
            sceneVersion++
        }
        preview.livePoints.clear()
        preview.activePathColor = data.strokes.lastOrNull()?.color
            ?: preview.activePathColor.takeIf { it != 0L }
            ?: vm.strokeDraft?.selectedColor
            ?: 0xFFFFFFFF
        updateStrokePreviewChunks(preview, data, force = true)
        preview.visible = enabled && data.strokes.isNotEmpty()
        preview.root.enabled = preview.visible
        preview.lastPoint = data.strokes.lastOrNull()?.points?.lastOrNull() ?: preview.lastPoint
    }

    private fun createOrUpdateAnnotationPreview(id: String, data: StrokeData, enabled: Boolean) {
        val preview = annotationPreviews[id] ?: StrokePreview(Entity().apply { setName(id) }).also {
            annotationPreviews[id] = it
            workspaceRoot.addChild(it.root)
            sceneVersion++
        }
        preview.livePoints.clear()
        preview.activePathColor = data.strokes.lastOrNull()?.color
            ?: preview.activePathColor.takeIf { it != 0L }
            ?: vm.annotationStrokeDraft?.selectedColor
            ?: 0xFFFFFFFF
        updateStrokePreviewChunks(preview, data, force = true, interactive = true)
        val hoverEnabled = vm.mode == AppMode.PRESENT &&
            vm.presentAnnotationTool == PresentAnnotationTool.ERASER &&
            id != ANNOTATION_LIVE_PREVIEW_ID
        preview.meshChunks.forEach { chunk -> setHoverEnabled(chunk.entity, hoverEnabled) }
        preview.visible = enabled && data.strokes.isNotEmpty()
        preview.root.enabled = preview.visible
        preview.lastPoint = data.strokes.lastOrNull()?.points?.lastOrNull() ?: preview.lastPoint
    }

    private fun createOrUpdateCommentPreview(
        id: String,
        annotation: PageAnnotation,
        enabled: Boolean,
        showCard: Boolean = true,
    ) {
        val anchor = annotation.anchorPoint ?: return
        val panel = annotation.panelPoint ?: return
        val text = annotation.commentText?.takeIf { it.isNotEmpty() } ?: COMMENT_PLACEHOLDER_TEXT
        val preview = commentPreviews[id] ?: createCommentPreview(id).also {
            commentPreviews[id] = it
            workspaceRoot.addChild(it.root)
            sceneVersion++
        }
        preview.visible = enabled
        preview.root.enabled = enabled
        if (preview.textKey != text) {
            runCatching { preview.card.destroy(true) }
            preview.card = createCommentCardEntity(id, text)
            preview.root.addChild(preview.card)
            preview.textKey = text
        }
        setHoverEnabled(
            preview.card,
            vm.mode == AppMode.EDIT && id != COMMENT_LIVE_PREVIEW_ID,
        )
        applyCommentCardTransform(preview.card, panel)
        preview.card.enabled = showCard
        updateCommentLeader(preview, anchor, panel, text)
    }

    private fun createCommentPreview(id: String): CommentPreview {
        val root = Entity().apply { setName(id) }
        val card = createCommentCardEntity(id, COMMENT_PLACEHOLDER_TEXT)
        val leaderDots = createCommentLeaderDots(id)
        root.addChild(card)
        leaderDots.forEach(root::addChild)
        return CommentPreview(
            root = root,
            card = card,
            leaderDots = leaderDots,
            textKey = COMMENT_PLACEHOLDER_TEXT,
        )
    }

    private fun createCommentCardEntity(id: String, text: String): Entity =
        ObjectEntityFactory.createCommentCard("${id}_card", text).apply {
            setName("${id}_card")
            components.get(TransformComponent::class.java)?.setScaleVector(Vector3(COMMENT_CARD_SCALE))
        }

    private fun applyCommentCardTransform(card: Entity, panel: StrokePoint) {
        card.components.get(TransformComponent::class.java)?.apply {
            setPosition(panel.toVector3())
            setScaleVector(Vector3(COMMENT_CARD_SCALE))
        }
    }

    private fun updateCommentLeader(preview: CommentPreview, anchor: StrokePoint, panel: StrokePoint, text: String) {
        val anchorVector = anchor.toVector3()
        val cardEdge = commentCardEdgePoint(anchorVector, panel.toVector3(), ObjectEntityFactory.commentCardSize(text))
        val line = cardEdge - anchorVector
        val length = line.length()
        if (length <= COMMENT_LEADER_MIN_LENGTH) {
            preview.leaderDots.forEach { it.enabled = false }
            return
        }
        val yaw = kotlin.math.atan2(line.x, line.z)
        val pitch = -kotlin.math.asin((line.y / length).coerceIn(-1f, 1f))
        val segmentCount = minOf(COMMENT_LEADER_POINT_COUNT, maxOf(1, (length / COMMENT_LEADER_SEGMENT_SPACING).toInt()))
        preview.leaderDots.forEachIndexed { index, segment ->
            segment.enabled = index < segmentCount
            if (!segment.enabled) return@forEachIndexed
            val t = (index + 0.5f) / segmentCount
            segment.components.get(TransformComponent::class.java)?.apply {
                setPosition(
                    Vector3(
                        anchorVector.x + line.x * t,
                        anchorVector.y + line.y * t,
                        anchorVector.z + line.z * t,
                    )
                )
                setEulerAngles(
                    EulerAngles(
                        Math.toDegrees(pitch.toDouble()).toFloat(),
                        Math.toDegrees(yaw.toDouble()).toFloat(),
                        0f,
                    )
                )
                val segmentLength = (length / segmentCount * COMMENT_LEADER_SEGMENT_FILL).coerceIn(
                    COMMENT_LEADER_SEGMENT_MIN_LENGTH,
                    COMMENT_LEADER_SEGMENT_MAX_LENGTH,
                )
                setScaleVector(Vector3(1f, 1f, segmentLength))
            }
        }
    }

    private fun createCommentLeaderDots(id: String): List<Entity> {
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(COMMENT_LEADER_COLOR.toColor4(COMMENT_LEADER_ALPHA))
        }
        return List(COMMENT_LEADER_POINT_COUNT) { index ->
            val segment = StrokePath(
                color = COMMENT_LEADER_COLOR,
                points = mutableListOf(
                    StrokePoint(0f, 0f, -0.5f),
                    StrokePoint(0f, 0f, 0.5f),
                ),
            )
            val meshBuild = buildStrokeMesh(
                chunk = StrokeMeshPathChunk(path = segment, capStart = true, capEnd = true),
                alpha = COMMENT_LEADER_ALPHA,
                radius = COMMENT_LEADER_SEGMENT_RADIUS,
                quality = StrokeMeshQuality.LIVE_PREVIEW,
            )
            if (meshBuild != null) {
                ModelEntity(meshBuild.createResource("${id}_leader_segment_$index"), material).apply {
                    setName("${id}_leader_segment_$index")
                }
            } else {
                ModelEntity(MeshResource.createBox(Vector3(1f, 1f, 1f)), material).apply {
                    setName("${id}_leader_segment_$index")
                }
            }
        }
    }

    private fun commentCardEdgePoint(anchor: Vector3, panel: Vector3, cardSize: Vector3): Vector3 {
        val dx = anchor.x - panel.x
        val dy = anchor.y - panel.y
        val halfWidth = cardSize.x * COMMENT_CARD_SCALE * 0.5f
        val halfHeight = cardSize.y * COMMENT_CARD_SCALE * 0.5f
        if (abs(dx) < STROKE_VECTOR_EPSILON && abs(dy) < STROKE_VECTOR_EPSILON) {
            return Vector3(panel.x, panel.y - halfHeight, panel.z)
        }
        val scaleToVerticalEdge = if (abs(dx) > STROKE_VECTOR_EPSILON) halfWidth / abs(dx) else Float.POSITIVE_INFINITY
        val scaleToHorizontalEdge = if (abs(dy) > STROKE_VECTOR_EPSILON) halfHeight / abs(dy) else Float.POSITIVE_INFINITY
        val scale = minOf(scaleToVerticalEdge, scaleToHorizontalEdge)
        return Vector3(
            panel.x + dx * scale,
            panel.y + dy * scale,
            panel.z,
        )
    }

    private fun clearStrokePreviews() {
        strokePreviews.values.forEach { preview ->
            clearStrokeMeshGeometry(preview)
            runCatching { preview.root.destroy(true) }
        }
        strokePreviews.clear()
    }

    private fun removeAnnotationPreview(id: String) {
        annotationPreviews.remove(id)?.let { preview ->
            clearStrokeMeshGeometry(preview)
            runCatching { preview.root.destroy(true) }
        }
    }

    private fun clearAnnotationPreviews() {
        annotationPreviews.keys.toList().forEach(::removeAnnotationPreview)
    }

    private fun removeCommentPreview(id: String) {
        commentPreviews.remove(id)?.let { preview ->
            runCatching { preview.root.destroy(true) }
        }
    }

    private fun clearCommentPreviews() {
        commentPreviews.keys.toList().forEach(::removeCommentPreview)
    }

    private fun addStrokeMeshChunks(
        root: Entity,
        data: StrokeData,
        namePrefix: String,
        alpha: Float,
        radius: Float,
        quality: StrokeMeshQuality,
    ) {
        var chunkIndex = 0
        data.chunkedForMesh(quality).forEach { chunk ->
            val meshBuild = buildStrokeMesh(
                chunk = chunk,
                alpha = alpha,
                radius = radius,
                quality = quality,
            ) ?: return@forEach
            val material = strokeMaterial(chunk.path.color, alpha)
            val chunkName = "${namePrefix}_${chunkIndex++}"
            root.addChild(
                ModelEntity(meshBuild.createResource(chunkName), material).apply {
                    setName(chunkName)
                }
            )
        }
    }

    private fun updateStrokePreviewChunks(
        preview: StrokePreview,
        data: StrokeData,
        force: Boolean = true,
        interactive: Boolean = false,
    ) {
        val now = System.nanoTime()
        if (!force && now - preview.lastMeshUpdateNanos < STROKE_PREVIEW_UPDATE_INTERVAL_NANOS) return
        val chunks = data.chunkedForMesh(StrokeMeshQuality.FINAL)
        preview.meshChunks.forEachIndexed { index, chunk ->
            val enabled = index < chunks.size
            chunk.entity.enabled = enabled
        }
        chunks.forEachIndexed { index, chunkData ->
            val chunkKey = chunkData.renderKey()
            val existing = preview.meshChunks.getOrNull(index)
            if (existing != null && existing.renderKey == chunkKey) {
                existing.entity.enabled = true
                return@forEachIndexed
            }
            val meshBuild = buildStrokeMesh(
                chunk = chunkData,
                alpha = STROKE_PREVIEW_ALPHA,
                radius = STROKE_LIVE_RADIUS,
                quality = StrokeMeshQuality.FINAL,
            ) ?: return@forEachIndexed
            updateStrokeMeshChunk(preview, index, meshBuild, chunkData.path.color, chunkKey, interactive)
        }
        preview.lastMeshUpdateNanos = now
    }

    private fun updateStrokeLiveChunk(preview: StrokePreview, force: Boolean = true) {
        val now = System.nanoTime()
        if (!force && now - preview.lastMeshUpdateNanos < STROKE_PREVIEW_UPDATE_INTERVAL_NANOS) return
        val source = preview.toLiveStrokeData() ?: return
        val meshBuild = buildStrokeMesh(
            chunk = source,
            alpha = STROKE_PREVIEW_ALPHA,
            radius = STROKE_LIVE_RADIUS,
            quality = StrokeMeshQuality.LIVE_PREVIEW,
        ) ?: return
        updateStrokeMeshChunk(preview, preview.frozenLiveChunkCount, meshBuild, source.path.color, source.renderKey())
        preview.lastMeshUpdateNanos = now
    }

    private fun updateStrokeMeshChunk(
        preview: StrokePreview,
        index: Int,
        meshBuild: StrokeMeshBuild,
        color: Long,
        renderKey: String,
        interactive: Boolean = false,
    ): Entity {
        val existing = preview.meshChunks.getOrNull(index)
        if (existing != null) {
            existing.entity.enabled = true
            val currentMesh = existing.resource
            existing.material.setBaseColor(color.toColor4(STROKE_PREVIEW_ALPHA))
            runCatching {
                currentMesh.replaceWithMeshModel(meshBuild.model, meshBuild.bounds)
            }.onFailure { error ->
                Log.w(TAG, "Stroke chunk mesh replace failed; recreating mesh", error)
                val model = existing.entity.components.get(ModelComponent::class.java) ?: return@onFailure
                val mesh = meshBuild.createResource("${preview.root.getName()}_chunk_${index}_${preview.meshVersion++}")
                model.mesh = mesh
                runCatching { currentMesh.close() }
                preview.meshChunks[index] = existing.copy(resource = mesh, renderKey = renderKey)
            }
            if (preview.meshChunks.getOrNull(index) === existing) {
                preview.meshChunks[index] = existing.copy(renderKey = renderKey)
            }
            return existing.entity
        }
        val mesh = meshBuild.createResource("${preview.root.getName()}_chunk_$index")
        val material = strokeMaterial(color, STROKE_PREVIEW_ALPHA)
        val entity = ModelEntity(mesh, material).apply {
            setName("${preview.root.getName()}_chunk_$index")
        }
        preview.root.addChild(entity)
        if (interactive) {
            applyStrokeMeshInteraction(entity, mesh)
        }
        preview.meshChunks += StrokeMeshChunk(entity, mesh, material, renderKey)
        return entity
    }

    private fun applyStrokeMeshInteraction(entity: Entity, mesh: MeshResource) {
        val shape = runCatching { ShapeResource.createStaticMesh(mesh) }
            .onFailure { Log.w(TAG, "Stroke mesh collider failed for ${entity.getName()}", it) }
            .getOrNull()
            ?: return
        entity.components.set(
            CollisionComponent(
                collisionShape = listOf(shape),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
        entity.components.set(InteractableComponent())
    }

    private fun setHoverEnabled(entity: Entity, enabled: Boolean) {
        val hasHover = entity.components.has(HoverEffectComponent::class.java)
        when {
            enabled && !hasHover -> entity.components.set(HoverEffectComponent())
            !enabled && hasHover -> entity.components.remove(HoverEffectComponent::class.java)
        }
    }

    private fun syncFaceViewer(entity: Entity, obj: SceneObject) {
        syncViewerFacing(
            entity = entity,
            enabled = (obj.type == ObjectType.TEXT || obj.type == ObjectType.IMAGE) && obj.faceViewer,
        )
    }

    private fun syncViewerFacing(entity: Entity?, enabled: Boolean) {
        entity ?: return
        val hasLookAt = entity.components.has(LookAtComponent::class.java)
        when {
            enabled && !hasLookAt -> {
                entity.components.set(
                    LookAtComponent().apply {
                        setViewerAsTarget()
                        // Full viewer-facing orientation: yaw + pitch. Keeping local Y
                        // parallel to world Y would intentionally reduce this to yaw only.
                        alignLocalUpToWorldUp = false
                        lookAtForwardDirection = LookAtForwardDirection.POSITIVE_Z
                    }
                )
            }
            !enabled && hasLookAt -> entity.components.remove(LookAtComponent::class.java)
        }
    }

    private fun freezeStrokeLiveChunk(preview: StrokePreview) {
        val source = preview.toLiveStrokeData()?.copy(capEnd = false) ?: return
        val meshBuild = buildStrokeMesh(
            chunk = source,
            alpha = STROKE_PREVIEW_ALPHA,
            radius = STROKE_LIVE_RADIUS,
            quality = StrokeMeshQuality.LIVE_PREVIEW,
        ) ?: return
        val chunkIndex = preview.frozenLiveChunkCount
        updateStrokeMeshChunk(preview, chunkIndex, meshBuild, source.path.color, source.renderKey())
        preview.frozenLiveChunkCount++
        val tail = preview.livePoints.takeLast(STROKE_LIVE_CHUNK_OVERLAP_POINTS)
        preview.livePoints.clear()
        preview.livePoints += tail
        preview.lastMeshUpdateNanos = 0L
    }

    private fun clearStrokeMeshGeometry(preview: StrokePreview) {
        preview.meshChunks.forEach { chunk ->
            runCatching { chunk.resource.close() }
            runCatching { chunk.entity.destroy(true) }
        }
        preview.meshChunks.clear()
        preview.frozenLiveChunkCount = 0
    }

    private fun List<StrokePoint>.smoothedForStroke(): List<StrokePoint> {
        if (size < 3) return this
        return chaikinSmooth(STROKE_FINAL_CHAIKIN_ITERATIONS)
            .resampleByDistance(STROKE_FINAL_CURVE_SAMPLE_SPACING)
    }

    private fun List<StrokePoint>.smoothedForLivePreview(): List<StrokePoint> {
        if (size < 3) return this
        return chaikinSmooth(STROKE_LIVE_CHAIKIN_ITERATIONS)
            .resampleByDistance(STROKE_LIVE_CURVE_SAMPLE_SPACING)
            .limitPointCountPreservingTail(
                maxPoints = STROKE_MAX_LIVE_MESH_POINTS,
                tailPoints = STROKE_LIVE_TAIL_MESH_POINTS,
            )
    }

    private fun StrokePoint.distanceTo(other: StrokePoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun StrokePoint.lerp(other: StrokePoint, t: Float): StrokePoint =
        StrokePoint(
            x = x + (other.x - x) * t,
            y = y + (other.y - y) * t,
            z = z + (other.z - z) * t,
        )

    private fun StrokePoint.toVector3(): Vector3 = Vector3(x, y, z)

    private fun Vector3.normalizeOrNull(): Vector3? =
        takeIf { it.length() > STROKE_VECTOR_EPSILON }?.normalize()

    private fun List<StrokePoint>.dedupe(minDistance: Float): List<StrokePoint> {
        if (size < 2) return this
        val result = ArrayList<StrokePoint>(size)
        forEach { point ->
            if (result.lastOrNull()?.distanceTo(point) ?: Float.MAX_VALUE >= minDistance) {
                result += point
            }
        }
        return result
    }

    private fun List<StrokePoint>.chaikinSmooth(iterations: Int): List<StrokePoint> {
        var current = dedupe(STROKE_RENDER_MIN_SEGMENT_LENGTH)
        repeat(iterations) {
            if (current.size < 3) return@repeat
            val next = ArrayList<StrokePoint>(current.size * 2)
            next += current.first()
            for (index in 0 until current.lastIndex) {
                val a = current[index]
                val b = current[index + 1]
                next += a.lerp(b, 0.25f)
                next += a.lerp(b, 0.75f)
            }
            next += current.last()
            current = next
        }
        return current
    }

    private fun List<StrokePoint>.resampleByDistance(spacing: Float): List<StrokePoint> {
        if (size < 2) return this
        val result = ArrayList<StrokePoint>(size)
        result += first()
        var segmentStart = first()
        var accumulated = 0f
        for (index in 1 until size) {
            var segmentEnd = this[index]
            var distance = segmentStart.distanceTo(segmentEnd)
            if (distance < STROKE_VECTOR_EPSILON) continue
            while (accumulated + distance >= spacing) {
                val t = (spacing - accumulated) / distance
                val inserted = segmentStart.lerp(segmentEnd, t)
                result += inserted
                segmentStart = inserted
                distance = segmentStart.distanceTo(segmentEnd)
                accumulated = 0f
                if (distance < STROKE_VECTOR_EPSILON) break
            }
            accumulated += distance
            segmentStart = segmentEnd
        }
        if (result.lastOrNull() != last()) result += last()
        return result
    }

    private fun List<StrokePoint>.limitPointCountPreservingTail(maxPoints: Int, tailPoints: Int): List<StrokePoint> {
        if (size <= maxPoints) return this
        if (maxPoints <= 2) return listOf(first(), last())
        val tailCount = tailPoints.coerceIn(2, maxPoints - 1).coerceAtMost(size - 1)
        val tailStart = size - tailCount
        val headBudget = (maxPoints - tailCount).coerceAtLeast(1)
        val result = ArrayList<StrokePoint>(maxPoints)
        val head = subList(0, tailStart)
        if (headBudget == 1 || head.size == 1) {
            result += head.first()
        } else {
            val step = head.lastIndex.toFloat() / (headBudget - 1).toFloat()
            repeat(headBudget) { index ->
                result += head[(index * step).toInt().coerceIn(0, head.lastIndex)]
            }
        }
        result += subList(tailStart, size)
        return result.take(maxPoints)
    }

    private fun buildStrokeMesh(
        chunk: StrokeMeshPathChunk,
        alpha: Float,
        radius: Float,
        quality: StrokeMeshQuality,
    ): StrokeMeshBuild? {
        val builder = StrokeTubeMeshBuilder(alpha = alpha, radius = radius, quality = quality)
        builder.addPath(chunk.path, capStart = chunk.capStart, capEnd = chunk.capEnd)
        return builder.build()
    }

    private fun StrokeData.chunkedForMesh(quality: StrokeMeshQuality): List<StrokeMeshPathChunk> {
        val maxPoints = when (quality) {
            StrokeMeshQuality.LIVE_PREVIEW -> STROKE_LIVE_CHUNK_POINT_LIMIT
            StrokeMeshQuality.FINAL -> STROKE_FINAL_CHUNK_POINT_LIMIT
        }
        val result = mutableListOf<StrokeMeshPathChunk>()
        strokes.forEach { path ->
            val points = path.points
                .dedupe(STROKE_RENDER_MIN_SEGMENT_LENGTH)
            val overlapPoints = when (quality) {
                StrokeMeshQuality.LIVE_PREVIEW -> STROKE_LIVE_CHUNK_OVERLAP_POINTS
                StrokeMeshQuality.FINAL -> STROKE_FINAL_CHUNK_OVERLAP_POINTS
            }
            val chunks = points.windowedChunks(maxPoints, overlapPoints)
            chunks.forEachIndexed { index, chunkPoints ->
                if (chunkPoints.size >= 2) {
                    result += StrokeMeshPathChunk(
                        path = StrokePath(path.color, chunkPoints.toMutableList()),
                        capStart = index == 0,
                        capEnd = index == chunks.lastIndex,
                    )
                }
            }
        }
        return result
    }

    private fun List<StrokePoint>.windowedChunks(maxPoints: Int, overlapPoints: Int): List<List<StrokePoint>> {
        if (size <= maxPoints) return listOf(this)
        val chunks = mutableListOf<List<StrokePoint>>()
        val overlap = overlapPoints.coerceIn(1, maxPoints - 1)
        var start = 0
        while (start < size - 1) {
            val endExclusive = (start + maxPoints).coerceAtMost(size)
            chunks += subList(start, endExclusive)
            if (endExclusive == size) break
            start = (endExclusive - overlap).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private enum class StrokeMeshQuality {
        LIVE_PREVIEW,
        FINAL,
    }

    private inner class StrokeTubeMeshBuilder(
        private val alpha: Float,
        private val radius: Float,
        private val quality: StrokeMeshQuality,
    ) {
        private val positions = mutableListOf<Vector3>()
        private val normals = mutableListOf<Vector3>()
        private val colors = mutableListOf<Color4>()
        private val indices = mutableListOf<Int>()
        private var minX = Float.POSITIVE_INFINITY
        private var minY = Float.POSITIVE_INFINITY
        private var minZ = Float.POSITIVE_INFINITY
        private var maxX = Float.NEGATIVE_INFINITY
        private var maxY = Float.NEGATIVE_INFINITY
        private var maxZ = Float.NEGATIVE_INFINITY

        fun addPath(path: StrokePath, capStart: Boolean, capEnd: Boolean) {
            val pathVertexStart = positions.size
            val points = when (quality) {
                StrokeMeshQuality.LIVE_PREVIEW -> path.points.smoothedForLivePreview()
                StrokeMeshQuality.FINAL -> path.points.smoothedForStroke()
            }.dedupe(STROKE_RENDER_MIN_SEGMENT_LENGTH)
            if (points.isEmpty()) return
            val color = path.color.toColor4(alpha)
            if (points.size == 1) {
                addSphere(points.first().toVector3(), color, radius, quality.capSegments)
                return
            }
            val frames = buildFrames(points.map { it.toVector3() })
            frames.forEachIndexed { index, frame ->
                addRing(frame.center, frame.normal, frame.binormal, color, radius)
                if (index > 0) addTubeSegment(pathVertexStart, index - 1, index)
            }
            if (capStart) {
                addHemisphereCap(
                    frame = frames.first(),
                    outwardTangent = frames.first().tangent * -1f,
                    baseRingStart = pathVertexStart,
                    color = color,
                    r = radius,
                    reverseWinding = true,
                )
            }
            if (capEnd) {
                addHemisphereCap(
                    frame = frames.last(),
                    outwardTangent = frames.last().tangent,
                    baseRingStart = pathVertexStart + frames.lastIndex * quality.radialSegments,
                    color = color,
                    r = radius,
                )
            }
        }

        fun build(): StrokeMeshBuild? {
            if (positions.isEmpty() || indices.isEmpty()) return null
            val center = Vector3(
                (minX + maxX) * 0.5f,
                (minY + maxY) * 0.5f,
                (minZ + maxZ) * 0.5f,
            )
            val halfExtent = Vector3(
                ((maxX - minX) * 0.5f).coerceAtLeast(radius),
                ((maxY - minY) * 0.5f).coerceAtLeast(radius),
                ((maxZ - minZ) * 0.5f).coerceAtLeast(radius),
            )
            return StrokeMeshBuild(
                model = MeshModel(
                    positions = positions,
                    triangleIndices = indices,
                    normals = normals,
                    colors = colors,
                ),
                bounds = BoundingBox(center, halfExtent),
            )
        }

        private fun buildFrames(points: List<Vector3>): List<StrokeFrame> {
            var lastNormal: Vector3? = null
            return points.mapIndexed { index, point ->
                val tangent = tangentAt(points, index)
                val normal = stableNormal(tangent, lastNormal)
                val binormal = Vector3.cross(tangent, normal).normalize()
                lastNormal = normal
                StrokeFrame(point, tangent, normal, binormal)
            }
        }

        private fun tangentAt(points: List<Vector3>, index: Int): Vector3 {
            val previous = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(points.lastIndex)]
            val tangent = next - previous
            return if (tangent.length() > STROKE_VECTOR_EPSILON) tangent.normalize() else Vector3.UP
        }

        private fun stableNormal(tangent: Vector3, previous: Vector3?): Vector3 {
            previous?.let {
                val projected = it - tangent * Vector3.dot(it, tangent)
                if (projected.length() > STROKE_VECTOR_EPSILON) return projected.normalize()
            }
            val helper = if (abs(Vector3.dot(tangent, Vector3.UP)) < STROKE_PARALLEL_DOT) Vector3.UP else Vector3.RIGHT
            return Vector3.cross(helper, tangent).normalize()
        }

        private fun addRing(center: Vector3, normal: Vector3, binormal: Vector3, color: Color4, r: Float): Int {
            val startIndex = positions.size
            repeat(quality.radialSegments) { side ->
                val angle = (side.toFloat() / quality.radialSegments) * (Math.PI.toFloat() * 2f)
                val radial = normal * cos(angle) + binormal * sin(angle)
                addVertex(center + radial * r, radial, color)
            }
            return startIndex
        }

        private fun addTubeSegment(pathVertexStart: Int, previousRing: Int, currentRing: Int) {
            val previousStart = pathVertexStart + previousRing * quality.radialSegments
            val currentStart = pathVertexStart + currentRing * quality.radialSegments
            repeat(quality.radialSegments) { side ->
                val nextSide = (side + 1) % quality.radialSegments
                addQuad(
                    previousStart + side,
                    previousStart + nextSide,
                    currentStart + nextSide,
                    currentStart + side,
                )
            }
        }

        private fun addSphere(center: Vector3, color: Color4, r: Float, segments: Int) {
            val startIndex = positions.size
            for (lat in 0..segments) {
                val v = lat.toFloat() / segments
                val theta = v * Math.PI.toFloat()
                val y = cos(theta)
                val ringRadius = sin(theta)
                for (lon in 0 until segments) {
                    val u = lon.toFloat() / segments
                    val phi = u * Math.PI.toFloat() * 2f
                    val normal = Vector3(cos(phi) * ringRadius, y, sin(phi) * ringRadius).normalize()
                    addVertex(center + normal * r, normal, color)
                }
            }
            for (lat in 0 until segments) {
                for (lon in 0 until segments) {
                    val nextLon = (lon + 1) % segments
                    val a = startIndex + lat * segments + lon
                    val b = startIndex + lat * segments + nextLon
                    val c = startIndex + (lat + 1) * segments + nextLon
                    val d = startIndex + (lat + 1) * segments + lon
                    addQuad(a, b, c, d)
                }
            }
        }

        private fun addHemisphereCap(
            frame: StrokeFrame,
            outwardTangent: Vector3,
            baseRingStart: Int,
            color: Color4,
            r: Float,
            reverseWinding: Boolean = false,
        ) {
            val segmentCount = quality.radialSegments
            val latitudeCount = quality.capSegments.coerceAtLeast(2)
            val tangent = outwardTangent.normalize()
            val poleIndex = addVertex(frame.center + tangent * r, tangent, color)
            val ringStarts = mutableListOf<Int>()
            for (latitude in 1 until latitudeCount) {
                val theta = (latitude.toFloat() / latitudeCount) * (Math.PI.toFloat() * 0.5f)
                val axial = cos(theta)
                val ringRadius = sin(theta)
                val ringStart = positions.size
                repeat(segmentCount) { side ->
                    val angle = (side.toFloat() / segmentCount) * (Math.PI.toFloat() * 2f)
                    val radial = frame.normal * cos(angle) + frame.binormal * sin(angle)
                    val normal = (tangent * axial + radial * ringRadius).normalize()
                    addVertex(frame.center + normal * r, normal, color)
                }
                ringStarts += ringStart
            }
            val firstRingStart = ringStarts.firstOrNull() ?: baseRingStart
            repeat(segmentCount) { side ->
                val nextSide = (side + 1) % segmentCount
                addTriangle(
                    poleIndex,
                    firstRingStart + side,
                    firstRingStart + nextSide,
                    reverseWinding = reverseWinding,
                )
            }
            ringStarts.zipWithNext().forEach { (currentStart, nextStart) ->
                connectCapRings(currentStart, nextStart, segmentCount, reverseWinding)
            }
            connectCapRings(ringStarts.lastOrNull() ?: poleIndex, baseRingStart, segmentCount, reverseWinding)
        }

        private fun connectCapRings(
            innerRingStart: Int,
            outerRingStart: Int,
            segmentCount: Int,
            reverseWinding: Boolean = false,
        ) {
            repeat(segmentCount) { side ->
                val nextSide = (side + 1) % segmentCount
                addQuad(
                    innerRingStart + side,
                    outerRingStart + side,
                    outerRingStart + nextSide,
                    innerRingStart + nextSide,
                    reverseWinding = reverseWinding,
                )
            }
        }

        private fun addQuad(a: Int, b: Int, c: Int, d: Int, reverseWinding: Boolean = false) {
            addTriangle(a, b, c, reverseWinding)
            addTriangle(a, c, d, reverseWinding)
        }

        private fun addTriangle(a: Int, b: Int, c: Int, reverseWinding: Boolean = false) {
            indices += a
            indices += if (reverseWinding) c else b
            indices += if (reverseWinding) b else c
        }

        private fun addVertex(position: Vector3, normal: Vector3, color: Color4): Int {
            positions += position
            normals += normal.normalize()
            colors += color
            minX = minOf(minX, position.x)
            minY = minOf(minY, position.y)
            minZ = minOf(minZ, position.z)
            maxX = maxOf(maxX, position.x)
            maxY = maxOf(maxY, position.y)
            maxZ = maxOf(maxZ, position.z)
            return positions.lastIndex
        }
    }

    private data class StrokeFrame(
        val center: Vector3,
        val tangent: Vector3,
        val normal: Vector3,
        val binormal: Vector3,
    )

    private data class StrokeMeshBuild(
        val model: MeshModel,
        val bounds: BoundingBox,
    ) {
        fun createResource(name: String): MeshResource =
            MeshResource.createWithMeshModel(model = model, bounds = bounds, name = name)
    }

    private val StrokeMeshQuality.radialSegments: Int
        get() = when (this) {
            StrokeMeshQuality.LIVE_PREVIEW -> STROKE_LIVE_TUBE_RADIAL_SEGMENTS
            StrokeMeshQuality.FINAL -> STROKE_FINAL_TUBE_RADIAL_SEGMENTS
        }

    private val StrokeMeshQuality.capSegments: Int
        get() = when (this) {
            StrokeMeshQuality.LIVE_PREVIEW -> STROKE_LIVE_CAP_SEGMENTS
            StrokeMeshQuality.FINAL -> STROKE_FINAL_CAP_SEGMENTS
        }

    private fun StrokePreview.toLiveStrokeData(): StrokeMeshPathChunk? {
        val activePoints = livePointsForMesh()
        val active = activePoints
            .takeIf { it.size >= 2 }
            ?.let { StrokePath(color = activePathColor, points = it.toMutableList()) }
            ?: return null
        return StrokeMeshPathChunk(
            path = active.copyPath(),
            capStart = frozenLiveChunkCount == 0,
            capEnd = true,
        )
    }

    private fun StrokePreview.livePointsForMesh(): List<StrokePoint> {
        if (livePoints.isEmpty()) return lastPoint?.let { listOf(it) } ?: emptyList()
        val points = ArrayList<StrokePoint>(livePoints.size + 1)
        points.addAll(livePoints)
        lastPoint?.let { tip ->
            if (points.last().distanceTo(tip) >= STROKE_RENDER_MIN_SEGMENT_LENGTH) {
                points += tip
            }
        }
        return points
    }

    private fun com.example.showme3d.studio.state.StrokeDraft.toCommittedStrokeData(): StrokeData {
        return StrokeData(
            paths
                .filter { it.isDrawableStrokePathForPreview() }
                .map { it.copyPath() }
                .toMutableList()
        )
    }

    private fun StrokePath.isDrawableStrokePathForPreview(): Boolean {
        if (points.size < com.example.showme3d.studio.state.StrokeDraft.MIN_STROKE_POINTS) return false
        var length = 0f
        for (index in 1 until points.size) {
            length += points[index - 1].distanceTo(points[index])
            if (length >= com.example.showme3d.studio.state.StrokeDraft.MIN_STROKE_LENGTH_METERS) return true
        }
        return false
    }

    private fun Long.toColor4(alphaMultiplier: Float): Color4 {
        val resolvedAlpha = (((this shr 24) and 0xFF).toInt() / 255f) * alphaMultiplier
        return Color4(
            ((this shr 16) and 0xFF).toInt() / 255f,
            ((this shr 8) and 0xFF).toInt() / 255f,
            (this and 0xFF).toInt() / 255f,
            resolvedAlpha.coerceIn(0f, 1f),
        )
    }

    private fun strokeMaterial(color: Long, alpha: Float): UnlitMaterial {
        val resolvedAlpha = (((color shr 24) and 0xFF).toInt() / 255f) * alpha
        val blendingMode = if (resolvedAlpha >= 0.999f) BlendingMode.OPAQUE else BlendingMode.TRANSPARENT
        return UnlitMaterial.create(blendingMode).apply {
            setBaseColor(color.toColor4(alpha))
        }
    }

    private suspend fun createVirtualEnvironment() {
        runCatching {
            Entity.loadSuspend(VIRTUAL_SKY_SPHERE_ASSET)
        }.onSuccess { skySphere ->
            skySphere.setName("virtual_sky_sphere")
            skySphere.components.get(TransformComponent::class.java)?.apply {
                setPosition(Vector3(0f, VIRTUAL_SKY_CENTER_Y, VIRTUAL_ROOM_CENTER_Z))
                setScaleVector(Vector3(VIRTUAL_SKY_SCALE))
            }
            environmentRoot.addChild(skySphere)
        }.onFailure { error ->
            Log.w(TAG, "Virtual sky sphere load failed", error)
            vm.reportSceneError("Virtual sky fallback: ${error.javaClass.simpleName}")
        }
        withContext(Dispatchers.Main.immediate) {
            environmentRoot.addChild(
                createEnvironmentBox(
                    name = "virtual_ground",
                    size = Vector3(VIRTUAL_GROUND_WIDTH, VIRTUAL_GROUND_THICKNESS, VIRTUAL_GROUND_DEPTH),
                    color = Color4(0.18f, 0.19f, 0.20f, 1f),
                    position = Vector3(0f, VIRTUAL_GROUND_Y, VIRTUAL_ROOM_CENTER_Z),
                )
            )
            repeat(VIRTUAL_GRID_LINE_COUNT) { index ->
                val offset = (index - (VIRTUAL_GRID_LINE_COUNT - 1) / 2f) * VIRTUAL_GRID_SPACING
                environmentRoot.addChild(
                    createEnvironmentBox(
                        name = "virtual_grid_x_$index",
                        size = Vector3(0.01f, 0.006f, VIRTUAL_GROUND_DEPTH),
                        color = Color4(0.31f, 0.32f, 0.33f, 1f),
                        position = Vector3(offset, VIRTUAL_GROUND_Y + 0.018f, VIRTUAL_ROOM_CENTER_Z),
                    )
                )
                environmentRoot.addChild(
                    createEnvironmentBox(
                        name = "virtual_grid_z_$index",
                        size = Vector3(VIRTUAL_GROUND_WIDTH, 0.006f, 0.01f),
                        color = Color4(0.31f, 0.32f, 0.33f, 1f),
                        position = Vector3(0f, VIRTUAL_GROUND_Y + 0.019f, VIRTUAL_ROOM_CENTER_Z + offset),
                    )
                )
            }
            sceneVersion++
            virtualEnvironmentCreated = true
        }
    }

    private fun createEnvironmentBox(
        name: String,
        size: Vector3,
        color: Color4,
        position: Vector3,
    ): Entity {
        val material = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(color)
        }
        return ModelEntity(MeshResource.createBox(size), material).apply {
            setName(name)
            components.get(TransformComponent::class.java)?.setPosition(position)
        }
    }

    private fun quadraticBezier(start: Vector3, control: Vector3, end: Vector3, t: Float): Vector3 {
        val oneMinusT = 1f - t
        val startWeight = oneMinusT * oneMinusT
        val controlWeight = 2f * oneMinusT * t
        val endWeight = t * t
        return Vector3(
            start.x * startWeight + control.x * controlWeight + end.x * endWeight,
            start.y * startWeight + control.y * controlWeight + end.y * endWeight,
            start.z * startWeight + control.z * controlWeight + end.z * endWeight,
        )
    }

    private suspend fun animateToCurrentPage() {
        val objectIds = lastPageObjectIds + vm.currentPage.states.keys
        val appearingObjectIds = vm.currentPage.states
            .filter { (objectId, state) -> state.visible && lastVisibility[objectId] != true }
            .keys
            .toList()
        val specs = objectIds.associateWith { objectId ->
            val state = vm.currentPage.states[objectId]
            val currentAnimationTransform = currentAnimationTransforms[objectId]
            val nextAnimation = state?.pageAnimation
            val target = state
                ?.let { pageTransitionTargetTransform(objectId, it) }
                ?: lastTransforms[objectId]
                ?: TransformData()
            val isVisible = state?.visible == true
            val wasVisible = lastVisibility[objectId] ?: false
            val unchangedLoopingAnimation = isVisible &&
                wasVisible &&
                nextAnimation != null &&
                lastPageAnimations[objectId] == nextAnimation
            val kind = when {
                isVisible && !wasVisible -> PageAnimationKind.APPEAR
                !isVisible && wasVisible -> PageAnimationKind.DISAPPEAR
                else -> PageAnimationKind.MOVE
            }
            PageAnimationSpec(
                from = if (wasVisible) {
                    currentAnimationTransform ?: lastTransforms[objectId] ?: target
                } else {
                    target.copy(scale = target.scale * PAGE_APPEAR_SCALE)
                },
                to = if (isVisible) {
                    target
                } else {
                    target.copy(scale = target.scale * PAGE_APPEAR_SCALE)
                },
                shouldAnimate = (wasVisible || isVisible) && !unchangedLoopingAnimation,
                kind = kind,
                delaySteps = if (kind == PageAnimationKind.APPEAR) {
                    appearingObjectIds.indexOf(objectId).coerceAtLeast(0) * PAGE_APPEAR_STAGGER_STEPS
                } else {
                    0
                },
            )
        }
        val transitioningObjectIds = specs
            .filter { (_, spec) -> spec.shouldAnimate }
            .keys
        pageTransitionLockedObjectIds.addAll(transitioningObjectIds)
        try {
            val steps = PAGE_TRANSITION_STEPS +
                (specs.values.maxOfOrNull { if (it.shouldAnimate) it.delaySteps else 0 } ?: 0)
            for (step in 1..steps) {
                withContext(Dispatchers.Main.immediate) {
                    specs.forEach { (objectId, spec) ->
                        if (!spec.shouldAnimate) return@forEach
                        val entity = entities[objectId] ?: return@forEach
                        val localStep = step - spec.delaySteps
                        if (spec.kind == PageAnimationKind.APPEAR && localStep <= 0) {
                            entity.enabled = false
                            return@forEach
                        }
                        val rawT = (localStep / PAGE_TRANSITION_STEPS.toFloat()).coerceIn(0f, 1f)
                        entity.enabled = true
                        val current = interpolatePageTransform(spec, rawT)
                        applyRawTransform(entity, current)
                        lastTransforms[objectId] = current
                    }
                    syncHighlight()
                }
                delay(16L)
            }
            withContext(Dispatchers.Main.immediate) {
                specs.forEach { (objectId, spec) ->
                    if (!spec.shouldAnimate) return@forEach
                    val state = vm.currentPage.states[objectId]
                    entities[objectId]?.let {
                        val visible = state?.visible == true
                        it.enabled = visible
                        if (visible) {
                            applyRawTransform(it, spec.to)
                            lastTransforms[objectId] = spec.to
                            if (state.pageAnimation != null) {
                                currentAnimationTransforms[objectId] = spec.to
                                animationClockStartSeconds.remove(objectId)
                            } else {
                                currentAnimationTransforms.remove(objectId)
                                animationClockStartSeconds.remove(objectId)
                            }
                        } else {
                            currentAnimationTransforms.remove(objectId)
                            animationClockStartSeconds.remove(objectId)
                        }
                        lastVisibility[objectId] = visible
                    }
                }
                vm.currentPage.states.forEach { (objectId, state) ->
                    if (objectId in transitioningObjectIds) return@forEach
                    entities[objectId]?.let {
                        it.enabled = state.visible
                        if (state.visible) {
                            if (state.pageAnimation != null) {
                                lastTransforms[objectId] = currentAnimationTransforms[objectId]
                                    ?: lastTransforms[objectId]
                                    ?: pageTransitionTargetTransform(objectId, state)
                            } else {
                                currentAnimationTransforms.remove(objectId)
                                animationClockStartSeconds.remove(objectId)
                                val target = visibleTransformFor(objectId, state.transform)
                                lastTransforms[objectId] = target
                                applyTransform(it, state.transform)
                            }
                        } else {
                            currentAnimationTransforms.remove(objectId)
                            animationClockStartSeconds.remove(objectId)
                        }
                        lastVisibility[objectId] = state.visible
                    }
                }
                disableEntitiesNotOnCurrentPage()
                vm.currentPage.states.forEach { (objectId, state) ->
                    val obj = vm.renderObject(objectId) ?: return@forEach
                    val enabled = entities[objectId]?.enabled == true
                    syncEmbeddedAnimationPlayback(obj, state.visible && enabled)
                }
                syncHighlight()
                rememberCurrentPageState()
            }
        } finally {
            pageTransitionLockedObjectIds.removeAll(transitioningObjectIds)
        }
    }

    private fun pageTransitionTargetTransform(objectId: String, state: ObjectState): TransformData =
        state.pageAnimation?.start ?: visibleTransformFor(objectId, state.transform)

    private fun applyRawTransform(entity: Entity, transform: TransformData, previous: TransformData? = null) {
        entity.components.get(TransformComponent::class.java)?.apply {
            if (
                previous == null ||
                previous.px != transform.px ||
                previous.py != transform.py ||
                previous.pz != transform.pz
            ) {
                setPosition(Vector3(transform.px, transform.py, transform.pz))
            }
            if (previous == null || previous.rotationY != transform.rotationY) {
                setEulerAngles(EulerAngles(0f, Math.toDegrees(transform.rotationY.toDouble()).toFloat(), 0f))
            }
            if (previous == null || previous.scale != transform.scale) {
                setScaleVector(Vector3(transform.scale))
            }
        }
    }

    private fun lerp(from: TransformData, to: TransformData, t: Float): TransformData =
        TransformData(
            px = from.px + (to.px - from.px) * t,
            py = from.py + (to.py - from.py) * t,
            pz = from.pz + (to.pz - from.pz) * t,
            rotationY = from.rotationY + (to.rotationY - from.rotationY) * t,
            scale = from.scale + (to.scale - from.scale) * t,
        )

    private fun PageAnimation.transformAt(elapsedSeconds: Float): TransformData {
        val duration = durationSeconds.coerceAtLeast(PageAnimation.MIN_DURATION_SECONDS)
        val cycle = (elapsedSeconds / duration).coerceAtLeast(0f)
        val wholeCycles = kotlin.math.floor(cycle).toInt()
        val localT = cycle - wholeCycles
        val directedT = when (loopMode) {
            AnimationLoopMode.NONE -> cycle.coerceIn(0f, 1f)
            AnimationLoopMode.RESTART -> localT
            AnimationLoopMode.PING_PONG -> if (wholeCycles % 2 == 0) localT else 1f - localT
        }
        return lerp(start, end, easing.apply(directedT))
    }

    private fun AnimationEasing.apply(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (this) {
            AnimationEasing.LINEAR -> x
            AnimationEasing.EASE_IN -> x * x
            AnimationEasing.EASE_OUT -> 1f - ((1f - x) * (1f - x))
            AnimationEasing.EASE_IN_OUT -> cubicBezier(x, 0.32f, 0f, 0.18f, 1f)
        }
    }

    private fun interpolatePageTransform(spec: PageAnimationSpec, rawT: Float): TransformData {
        val motionT = pageMotionEase(rawT)
        val base = lerp(spec.from, spec.to, motionT)
        val scale = when (spec.kind) {
            PageAnimationKind.APPEAR -> appearScale(spec.to.scale, rawT)
            PageAnimationKind.DISAPPEAR -> disappearScale(spec.from.scale, spec.to.scale, rawT)
            PageAnimationKind.MOVE -> base.scale
        }
        return base.copy(scale = scale)
    }

    private fun pageMotionEase(t: Float): Float =
        cubicBezier(t, 0.32f, 0f, 0.18f, 1f)

    private fun appearScale(targetScale: Float, t: Float): Float {
        val growT = cubicBezier((t / APPEAR_OVERSHOOT_AT).coerceIn(0f, 1f), 0.18f, 0f, 0.12f, 1f)
        val settleT = cubicBezier(((t - APPEAR_OVERSHOOT_AT) / (1f - APPEAR_OVERSHOOT_AT)).coerceIn(0f, 1f), 0.2f, 0f, 0f, 1f)
        val overshootScale = targetScale * APPEAR_OVERSHOOT_SCALE
        return if (t < APPEAR_OVERSHOOT_AT) {
            lerpFloat(targetScale * PAGE_APPEAR_SCALE, overshootScale, growT)
        } else {
            lerpFloat(overshootScale, targetScale, settleT)
        }
    }

    private fun disappearScale(fromScale: Float, toScale: Float, t: Float): Float {
        val nudgeT = cubicBezier((t / DISAPPEAR_NUDGE_AT).coerceIn(0f, 1f), 0.3f, 0f, 0.4f, 1f)
        val collapseT = cubicBezier(((t - DISAPPEAR_NUDGE_AT) / (1f - DISAPPEAR_NUDGE_AT)).coerceIn(0f, 1f), 0.55f, 0f, 0.9f, 0.45f)
        val nudgeScale = fromScale * DISAPPEAR_NUDGE_SCALE
        return if (t < DISAPPEAR_NUDGE_AT) {
            lerpFloat(fromScale, nudgeScale, nudgeT)
        } else {
            lerpFloat(nudgeScale, toScale, collapseT)
        }
    }

    private fun cubicBezier(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val x = t.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(8) {
            val mid = (low + high) * 0.5f
            if (bezier(mid, x1, x2) < x) {
                low = mid
            } else {
                high = mid
            }
        }
        return bezier((low + high) * 0.5f, y1, y2).coerceIn(0f, 1f)
    }

    private fun bezier(t: Float, p1: Float, p2: Float): Float {
        val oneMinusT = 1f - t
        return 3f * oneMinusT * oneMinusT * t * p1 +
            3f * oneMinusT * t * t * p2 +
            t * t * t
    }

    private fun lerpFloat(from: Float, to: Float, t: Float): Float =
        from + (to - from) * t

    private fun selectionToolbarScaleFor(transform: TransformData): Float {
        val distance = kotlin.math.sqrt(transform.px * transform.px + transform.pz * transform.pz)
        val normalized = ((distance - SELECTION_TOOLBAR_NEAR_DISTANCE) /
            (SELECTION_TOOLBAR_FAR_DISTANCE - SELECTION_TOOLBAR_NEAR_DISTANCE)).coerceIn(0f, 1f)
        return lerpFloat(SELECTION_TOOLBAR_MIN_SCALE, SELECTION_TOOLBAR_MAX_SCALE, normalized)
    }

    private fun SceneObject.renderKey(): String =
        listOf(
            type,
            source,
            displayName,
            textColor,
            textBackgroundEnabled,
            strokeData?.renderKey(),
            modelOriginMode,
            playEmbeddedAnimation,
        ).joinToString("|")

    private fun StrokeData.renderKey(): String =
        strokes.joinToString(";") { path ->
            "${path.color}:${path.points.joinToString(",") { point -> "${point.x}/${point.y}/${point.z}" }}"
        }

    private fun StrokeMeshPathChunk.renderKey(): String {
        val points = path.points
        val sampledPoints = points.filterIndexed { index, _ ->
            index == 0 || index == points.lastIndex || index % STROKE_RENDER_KEY_POINT_STRIDE == 0
        }
        return listOf(
            path.color,
            capStart,
            capEnd,
            points.size,
            sampledPoints.joinToString(",") { it.quantizedRenderKey() },
        ).joinToString("|")
    }

    private fun StrokePoint.quantizedRenderKey(): String =
        "${(x * STROKE_RENDER_KEY_SCALE).toInt()}/${(y * STROKE_RENDER_KEY_SCALE).toInt()}/${(z * STROKE_RENDER_KEY_SCALE).toInt()}"

    private fun PageAnnotation.renderId(pageId: String): String =
        "${ANNOTATION_PREVIEW_PREFIX}_${pageId}_$id"

    private fun SceneObject.isViewpointCalibrationDebugObject(): Boolean =
        type == ObjectType.MODEL &&
            (source.startsWith("viewpoint-calibration-cone://") || source == "calibration://view_cone")

    private fun rememberCurrentPageState() {
        lastPageObjectIds.clear()
        lastPageObjectIds.addAll(vm.currentPage.states.keys)
        lastPageAnimations.clear()
        vm.currentPage.states.forEach { (objectId, state) ->
            state.pageAnimation?.let { lastPageAnimations[objectId] = it }
        }
    }

    private data class PageAnimationSpec(
        val from: TransformData,
        val to: TransformData,
        val shouldAnimate: Boolean,
        val kind: PageAnimationKind,
        val delaySteps: Int,
    )

    private enum class PageAnimationKind {
        MOVE,
        APPEAR,
        DISAPPEAR,
    }

    private data class HighlightSegment(
        val entity: Entity,
        val axis: HighlightAxis,
        val signA: Float,
        val signB: Float,
        val segmentIndex: Int,
        val material: UnlitMaterial,
    )

    private data class HighlightLayoutKey(
        val width: Float,
        val height: Float,
        val depth: Float,
        val flat: Boolean,
    )

    private data class GroupSelectionBounds(
        val center: Vector3,
        val size: Vector3,
    )

    private data class StrokePreview(
        val root: Entity,
        var lastPoint: StrokePoint? = null,
        var activePathColor: Long = 0L,
        val livePoints: MutableList<StrokePoint> = mutableListOf(),
        var visible: Boolean = false,
        val meshChunks: MutableList<StrokeMeshChunk> = mutableListOf(),
        var frozenLiveChunkCount: Int = 0,
        var meshVersion: Int = 0,
        var lastMeshUpdateNanos: Long = 0L,
    )

    private data class CommentPreview(
        val root: Entity,
        var card: Entity,
        val leaderDots: List<Entity>,
        var textKey: String,
        var visible: Boolean = false,
    )

    private data class StrokeMeshChunk(
        val entity: Entity,
        val resource: MeshResource,
        val material: UnlitMaterial,
        val renderKey: String,
    )

    private data class StrokeMeshPathChunk(
        val path: StrokePath,
        val capStart: Boolean,
        val capEnd: Boolean,
    )

    private enum class HighlightAxis {
        X,
        Y,
        Z,
    }

    private enum class ModelAxis {
        X,
        Y,
        Z,
    }

    private data class ModelNormalization(
        val scale: Float,
        val position: Vector3,
        val eulerAngles: EulerAngles,
        val interactionSize: Vector3,
        val dominantAxis: ModelAxis,
    )

    private data class SkyboxRender(
        val skybox: PageSkybox,
        val entity: ModelEntity,
        val material: UnlitMaterial,
    ) {
        fun applyRotation(rotationY: Float) {
            entity.components.get(TransformComponent::class.java)?.setEulerAngles(
                EulerAngles(0f, Math.toDegrees(rotationY.toDouble()).toFloat(), 0f)
            )
        }

        fun prepareForFade() {
            material.setBlendingMode(BlendingMode.FADE)
            material.setOpacity(1f)
        }

        fun prepareForFadeIn() {
            material.setBlendingMode(BlendingMode.FADE)
            material.setOpacity(0f)
            entity.enabled = false
        }

        fun makeOpaque() {
            material.setOpacity(1f)
            material.setBlendingMode(BlendingMode.OPAQUE)
        }

        fun destroy() {
            runCatching { entity.destroy(true) }
        }
    }

    private data class SkyboxTransition(
        val from: SkyboxRender?,
        val to: SkyboxRender?,
        val startedAtNanos: Long,
    )

}

private const val COMMENT_PLACEHOLDER_TEXT = "待评论"
private const val COMMENT_CARD_SCALE = 1.0f
private const val COMMENT_LEADER_POINT_COUNT = 14
private const val COMMENT_LEADER_COLOR = 0xFFE5A260
private const val COMMENT_LEADER_ALPHA = 0.88f
private const val COMMENT_LEADER_SEGMENT_RADIUS = 0.0026f
private const val COMMENT_LEADER_SEGMENT_SPACING = 0.052f
private const val COMMENT_LEADER_SEGMENT_FILL = 0.64f
private const val COMMENT_LEADER_SEGMENT_MIN_LENGTH = 0.014f
private const val COMMENT_LEADER_SEGMENT_MAX_LENGTH = 0.046f
private const val COMMENT_LEADER_MIN_LENGTH = 0.02f
private const val ANIMATION_ENDPOINT_BADGE_GAP = 0.11f
private const val ANIMATION_ENDPOINT_BADGE_Z_OFFSET = 0.055f
private const val ANIMATION_ENDPOINT_BADGE_SCALE = 1.0f
private const val ANIMATION_ENDPOINT_BADGE_WIDTH = 0.24f
private const val ANIMATION_ENDPOINT_BADGE_HEIGHT = 0.082f
private const val ANIMATION_ENDPOINT_BADGE_DEPTH = 0.018f
private const val ANIMATION_ENDPOINT_BADGE_TEXT_SCALE = 0.36f
private const val ANIMATION_ENDPOINT_BADGE_TEXT_Z = 0.014f
private const val ANIMATION_ENDPOINT_CONTROLS_MARGIN_Y = 0.06f
private const val ANIMATION_ENDPOINT_CONTROLS_SCALE_MARGIN_Y = 0.08f
private const val ANIMATION_PATH_DASH_COUNT = 18
private const val ANIMATION_PATH_DASH_SPACING = 0.08f
private const val ANIMATION_PATH_DASH_FILL = 0.54f
private const val ANIMATION_PATH_DASH_THICKNESS = 0.007f
private const val ANIMATION_PATH_DASH_MIN_LENGTH = 0.018f
private const val ANIMATION_PATH_DASH_MAX_LENGTH = 0.055f
private const val ANIMATION_PATH_UPDATE_EPSILON = 0.0025f
private const val SELECTION_TOOLBAR_MARGIN_Y = 0.18f
private const val SELECTION_TOOLBAR_SCALE_MARGIN_Y = 0.18f
private const val SELECTION_TOOLBAR_Z_OFFSET = 0.04f
private const val SELECTION_TOOLBAR_NEAR_DISTANCE = 0.75f
private const val SELECTION_TOOLBAR_FAR_DISTANCE = 3.6f
private const val SELECTION_TOOLBAR_MIN_SCALE = 1.1f
private const val SELECTION_TOOLBAR_MAX_SCALE = 2.1f
private const val HIGHLIGHT_FLAT_DEPTH = 0.012f
private const val HIGHLIGHT_FLAT_Z_OFFSET = -0.01f
private const val HIGHLIGHT_EDGE_MIN_THICKNESS = 0.0015f
private const val HIGHLIGHT_EDGE_MAX_THICKNESS = 0.0045f
private const val HIGHLIGHT_MIN_SIDE = 0.035f
private const val HIGHLIGHT_DASH_SEGMENT_COUNT = 7
private const val HIGHLIGHT_DASH_FILL_RATIO = 0.52f
private val HIGHLIGHT_COLOR = Color4(0.84f, 0.84f, 0.82f, 0.9f)
private val HIGHLIGHT_LOCKED_COLOR = Color4(1f, 0.1f, 0.08f, 0.96f)
private const val VIEWPOINT_EYE_HEIGHT_METERS = 1.62f
private const val VIEWPOINT_TARGET_X = 0f
private const val VIEWPOINT_TARGET_Z = 0f
private const val VIEWPOINT_MIN_SCALE = 0.1f
private const val VIEWPOINT_MAX_SCALE = 10f
private const val VIEWPOINT_ROOT_MIN_SCALE = 0.1f
private const val VIEWPOINT_ROOT_MAX_SCALE = 10f
private const val VIEWPOINT_MODEL_ASSET = "asset://viewpoint/standing_man.glb"
private const val VIEWPOINT_MODEL_TARGET_HEIGHT = 0.30f
private const val VIEWPOINT_EYE_HEIGHT_RATIO = 6f / 7f
private const val AUTO_ROTATION_SECONDS_PER_TURN = 8f
private const val AUTO_ROTATION_RADIANS_PER_SECOND =
    (2f * 3.1415927f) / AUTO_ROTATION_SECONDS_PER_TURN
private const val FILE_PREFIX = "file://"
private const val ASSET_PREFIX = "asset://"
private const val SKYBOX_RADIUS_METERS = 48f
private const val SKYBOX_TRANSITION_NANOS = 500_000_000f
private val VIEWPOINT_MODEL_FALLBACK_INTERACTION_SIZE = Vector3(0.10f, 0.30f, 0.07f)
private const val VIRTUAL_SKY_SPHERE_ASSET = "asset://environment/gray_studio_sky.glb"

/**
 * Spatial SDK entity names accept a restricted identifier alphabet. User-facing
 * skybox names may contain spaces, Chinese text, punctuation, or symbols such as
 * "°", so derive the internal ECS name from the stable source instead.
 */
internal fun skyboxEntityName(skybox: PageSkybox): String =
    "user_skybox_${skybox.source.hashCode().toUInt().toString(16)}"

private fun String.isLoadableModelSource(): Boolean =
    startsWith(FILE_PREFIX) || startsWith(ASSET_PREFIX)
private const val VIRTUAL_SKY_SCALE = 64f
private const val VIRTUAL_SKY_CENTER_Y = 1.2f
private const val VIRTUAL_GROUND_WIDTH = 80f
private const val VIRTUAL_GROUND_DEPTH = 80f
private const val VIRTUAL_GROUND_THICKNESS = 0.025f
private const val VIRTUAL_GROUND_Y = -0.72f
private const val VIRTUAL_ROOM_CENTER_Z = -6f
private const val VIRTUAL_GRID_LINE_COUNT = 41
private const val VIRTUAL_GRID_SPACING = 2f
private const val STROKE_DRAFT_PREVIEW_ID = "showme3d_stroke_preview"
private const val STROKE_LIVE_PREVIEW_ID = "showme3d_stroke_live_preview"
private const val ANNOTATION_PREVIEW_PREFIX = "showme3d_annotation_preview"
private const val ANNOTATION_LIVE_PREVIEW_ID = "showme3d_annotation_live_preview"
private const val COMMENT_LIVE_PREVIEW_ID = "showme3d_comment_live_preview"
private const val STROKE_MIN_X = -2.4f
private const val STROKE_MAX_X = 2.4f
private const val STROKE_MIN_Y = 0.25f
private const val STROKE_MAX_Y = 2.25f
private const val STROKE_MIN_Z = -2.8f
private const val STROKE_MAX_Z = 0.6f
private const val STROKE_RADIUS = 0.012f
private const val STROKE_LIVE_RADIUS = 0.0095f
private const val STROKE_PREVIEW_ALPHA = 1f
private const val STROKE_ZERO_EPSILON = 0.0001f
private const val STROKE_RENDER_MIN_SEGMENT_LENGTH = 0.006f
private const val STROKE_LIVE_INPUT_MIN_DISTANCE = 0.018f
private const val STROKE_LIVE_CHAIKIN_ITERATIONS = 1
private const val STROKE_FINAL_CHAIKIN_ITERATIONS = 2
private const val STROKE_LIVE_CURVE_SAMPLE_SPACING = 0.012f
private const val STROKE_FINAL_CURVE_SAMPLE_SPACING = 0.012f
private const val STROKE_LIVE_TUBE_RADIAL_SEGMENTS = 6
private const val STROKE_FINAL_TUBE_RADIAL_SEGMENTS = 8
private const val STROKE_LIVE_CAP_SEGMENTS = 5
private const val STROKE_FINAL_CAP_SEGMENTS = 10
private const val STROKE_VECTOR_EPSILON = 0.00001f
private const val STROKE_PARALLEL_DOT = 0.92f
private const val STROKE_LIVE_CHUNK_POINT_LIMIT = 24
private const val STROKE_LIVE_CHUNK_OVERLAP_POINTS = 5
private const val STROKE_FINAL_CHUNK_POINT_LIMIT = 260
private const val STROKE_FINAL_CHUNK_OVERLAP_POINTS = 5
private const val STROKE_MAX_LIVE_MESH_POINTS = 80
private const val STROKE_LIVE_TAIL_MESH_POINTS = 36
private const val STROKE_PREVIEW_UPDATE_INTERVAL_NANOS = 24_000_000L
private const val STROKE_RENDER_KEY_SCALE = 2000f
private const val STROKE_RENDER_KEY_POINT_STRIDE = 8
private const val PAGE_TRANSITION_STEPS = 22
private const val PAGE_APPEAR_STAGGER_STEPS = 4
private const val PAGE_APPEAR_SCALE = 0.05f
private const val APPEAR_OVERSHOOT_AT = 0.72f
private const val APPEAR_OVERSHOOT_SCALE = 1.08f
private const val DISAPPEAR_NUDGE_AT = 0.18f
private const val DISAPPEAR_NUDGE_SCALE = 1.025f

private fun TransformData.approximatelyEquals(other: TransformData, epsilon: Float): Boolean =
    kotlin.math.abs(px - other.px) < epsilon &&
        kotlin.math.abs(py - other.py) < epsilon &&
        kotlin.math.abs(pz - other.pz) < epsilon &&
        kotlin.math.abs(rotationY - other.rotationY) < epsilon &&
        kotlin.math.abs(scale - other.scale) < epsilon

private fun Vector3.isFiniteVector(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()
