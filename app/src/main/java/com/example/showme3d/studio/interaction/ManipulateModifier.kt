package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.scene.SceneController
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.StudioViewModel
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 选中物体的 Transform 手势：移动 / 缩放。
 *
 * 关键约束（见开发文档 §2 / §6.4）：
 * - **每个手势各占一个独立 [pointerInput]**，不可堆叠在同一块（否则互相阻塞）。
 * - 拖拽命中任意可编辑实体后，以命中实体作为本次移动对象；编辑态缩放作用于当前选中实体，
 *   播放态缩放作用于最近一次拿取并保持临时变换的模型。
 * - 过程中实时 [SceneController.pushTransform] + [StudioViewModel.previewTransform] 做视觉跟手；
 *   手势结束时一次性 [StudioViewModel.commitTransform] 写回数据并入撤销栈。
 * - Spatial SDK exposes raw gesture deltas. We damp and clamp them here because raw pointer
 *   coordinates can be much larger than scene meters and may spike when tracking switches/lifts.
 */
fun Modifier.manipulatable(
    context: Context,
    vm: StudioViewModel,
    scene: () -> SceneController?,
    selectedEntity: () -> Entity?,
): Modifier =
    composed {
        val handoff = remember { ManipulationHandoffState() }
        this
            .pointerInput(scene()?.sceneVersion, vm.mode) { dragGesture(context, vm, scene, handoff) }
            .pointerInput(vm.selectedObjectId, scene()?.sceneVersion, vm.mode) {
                scaleGesture(context, vm, scene, selectedEntity, handoff)
            }
    }

private enum class GestureOwner {
    DRAG,
    SCALE,
}

private class ManipulationHandoffState {
    private var owner: GestureOwner? = null
    private var objectId: String? = null
    private var latest: TransformData? = null

    fun latestFor(id: String): TransformData? =
        latest.takeIf { objectId == id }

    fun begin(owner: GestureOwner, id: String, transform: TransformData) {
        this.owner = owner
        objectId = id
        latest = transform
    }

    fun update(owner: GestureOwner, id: String, transform: TransformData) {
        if (objectId != null && objectId != id) return
        begin(owner, id, transform)
    }

    fun clear(owner: GestureOwner, id: String) {
        if (this.owner != owner || objectId != id) return
        this.owner = null
        objectId = null
        latest = null
    }

    fun isSuperseded(owner: GestureOwner, id: String): Boolean =
        objectId == id && this.owner != null && this.owner != owner
}

/** 一次手势的临时状态：起点基准 + 最新计算结果。 */
private class GestureSession {
    var objectId: String? = null
    var base: TransformData? = null
    var rollback: TransformData? = null
    var latest: TransformData? = null
    var presentationInspection = false

    fun begin(
        vm: StudioViewModel,
        objectId: String,
        controller: SceneController?,
        sceneTransform: TransformData? = null,
    ): Boolean {
        val editing = vm.mode == AppMode.EDIT
        val inspecting = vm.canInspectModelInPresent(objectId)
        if (!editing && !inspecting) return false
        if (editing && vm.isObjectLocked(objectId)) return false
        val t = sceneTransform ?: vm.currentTransform(objectId) ?: return false
        val id = objectId
        this.objectId = id
        base = t
        rollback = if (editing) vm.activeManipulationBaseTransform(id) ?: t else t
        latest = t
        presentationInspection = inspecting
        vm.updateObjectManipulationInProgress(true)
        if (editing) {
            vm.select(id, sceneTransform)
            vm.beginTransformPreview(id, t)
        } else {
            controller?.beginPresentationInspection(id, t)
        }
        return true
    }

    fun beginSelected(
        vm: StudioViewModel,
        controller: SceneController?,
        sceneTransform: TransformData? = null,
    ): Boolean {
        val id = vm.selectedObjectId ?: return false
        return begin(vm, id, controller, sceneTransform)
    }

    fun end(vm: StudioViewModel, scene: () -> SceneController?) {
        val id = objectId ?: return
        val result = latest ?: return
        if (presentationInspection) {
            scene()?.endPresentationInspection(id)
        } else {
            vm.commitTransform(id, result)
            scene()?.pushTransform(id, result, updateAnimationEndpointChrome = true)
        }
        vm.updateObjectManipulationInProgress(false)
        clear()
    }

    fun cancel(vm: StudioViewModel, scene: () -> SceneController?) {
        val id = objectId
        val original = rollback ?: base
        if (id != null && original != null) {
            if (presentationInspection) {
                scene()?.endPresentationInspection(id)
            } else {
                vm.cancelTransformPreview(id, original)
                scene()?.pushTransform(id, original, updateAnimationEndpointChrome = true)
            }
        }
        vm.updateObjectManipulationInProgress(false)
        clear()
    }

    fun clear() {
        objectId = null
        base = null
        rollback = null
        latest = null
        presentationInspection = false
    }
}

private const val DRAG_SENSITIVITY_XY = 0.00042f
private const val DRAG_SENSITIVITY_Z = 0.00028f
private const val MAX_DRAG_STEP_XY = 0.034f
private const val MAX_DRAG_STEP_Z = 0.032f
private const val MAX_RAW_DRAG_DELTA = 160f
private const val MIN_SCALE = 0.08f
private const val MAX_SCALE = 10f
private const val MAX_SCALE_FACTOR_PER_EVENT = 1.08f

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragGesture(
    context: Context,
    vm: StudioViewModel,
    scene: () -> SceneController?,
    handoff: ManipulationHandoffState,
) {
    val session = GestureSession()
    detectSpatialDragGesture(
        context,
        TargetEntity.any { entity ->
            val objectId = scene()?.objectIdFor(entity)
            objectId != null && (
                (
                    vm.mode == AppMode.EDIT &&
                        vm.viewpointModeObjectId == null &&
                        vm.strokeDraft == null &&
                        !vm.isObjectLocked(objectId)
                    ) ||
                    vm.canInspectModelInPresent(objectId)
                )
        },
        { /* Target entity is available on SpatialDragValue, not on drag start. */ },
        { session.endOrClear(vm, scene, handoff, GestureOwner.DRAG) },
        { session.cancelOrClear(vm, scene, handoff, GestureOwner.DRAG) },
    ) { value ->
        val controller = scene() ?: return@detectSpatialDragGesture
        val id = session.objectId
            ?: controller.objectIdFor(value.targetEntity)
                ?.takeUnless { handoff.isSuperseded(GestureOwner.DRAG, it) }
                ?.takeIf {
                    session.begin(
                        vm,
                        it,
                        controller,
                        handoff.latestFor(it)
                            ?: vm.activeManipulationTransform(it)
                            ?: controller.visibleTransformForSelection(it),
                    )
                }
                ?.also {
                    val start = session.latest ?: session.base ?: return@detectSpatialDragGesture
                    handoff.begin(GestureOwner.DRAG, it, start)
                    if (!session.presentationInspection) {
                        controller.pushTransform(it, start, updateAnimationEndpointChrome = false)
                    }
                }
            ?: return@detectSpatialDragGesture
        if (handoff.isSuperseded(GestureOwner.DRAG, id)) return@detectSpatialDragGesture
        val base = session.base ?: return@detectSpatialDragGesture
        val d = value.dragAmount
        if (abs(d.x) > MAX_RAW_DRAG_DELTA || abs(d.y) > MAX_RAW_DRAG_DELTA || abs(d.z) > MAX_RAW_DRAG_DELTA) {
            return@detectSpatialDragGesture
        }
        val current = session.latest ?: base
        val next = current.copy(
            px = current.px + (d.x * DRAG_SENSITIVITY_XY).coerceIn(-MAX_DRAG_STEP_XY, MAX_DRAG_STEP_XY),
            py = current.py + (-d.y * DRAG_SENSITIVITY_XY).coerceIn(-MAX_DRAG_STEP_XY, MAX_DRAG_STEP_XY),
            pz = current.pz + (d.z * DRAG_SENSITIVITY_Z).coerceIn(-MAX_DRAG_STEP_Z, MAX_DRAG_STEP_Z),
        )
        session.latest = next
        handoff.update(GestureOwner.DRAG, id, next)
        if (session.presentationInspection) {
            scene()?.updatePresentationInspection(id, next)
        } else {
            vm.previewTransform(id, next)
            scene()?.pushTransform(id, next, updateAnimationEndpointChrome = false)
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.scaleGesture(
    context: Context,
    vm: StudioViewModel,
    scene: () -> SceneController?,
    selectedEntity: () -> Entity?,
    handoff: ManipulationHandoffState,
) {
    val session = GestureSession()
    if (vm.viewpointModeObjectId != null) return
    if (vm.strokeDraft != null) return
    if (vm.selectedObjectId?.let { vm.isObjectLocked(it) } == true) return
    val controller = scene() ?: return
    val targetId = vm.selectedObjectId
        ?: controller.presentationInspectionTargetId()
        ?: return
    val targetRoot = if (vm.selectedObjectId == targetId) {
        selectedEntity()
    } else {
        controller.entityFor(targetId)
    } ?: return
    // Bounds expressed relative to the object root are invariant while the
    // root itself is moved/scaled. Shared-origin assembly assets deliberately
    // keep this center away from (0, 0, 0), so it must be used as the scale
    // pivot to prevent the visible part orbiting around the room origin.
    val localScalePivot = runCatching {
        targetRoot.getVisualBounds(targetRoot, recursive = true, enabledOnly = false).center
    }.getOrNull()?.takeIf { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
    detectSpatialScaleGesture(
        context,
        // EntityTreeTarget recursively accepts every visible mesh below the
        // object root while keeping both hands bound to one stable tree. A
        // generic condition matcher can resolve each hand to a different mesh
        // child and lose the scale gesture after the hierarchy moves/scales.
        TargetEntity.hit(targetRoot),
        {
            val start = handoff.latestFor(targetId)
                ?: vm.activeManipulationTransform(targetId)
                ?: scene()?.visibleTransformForSelection(targetId)
            if (session.begin(vm, targetId, scene(), start)) {
                val handoffStart = session.latest ?: session.base ?: return@detectSpatialScaleGesture
                handoff.begin(GestureOwner.SCALE, targetId, handoffStart)
                if (!session.presentationInspection) {
                    scene()?.pushTransform(targetId, handoffStart, updateAnimationEndpointChrome = false)
                }
            }
        },
        { session.endOrClear(vm, scene, handoff, GestureOwner.SCALE) },
        { session.cancelOrClear(vm, scene, handoff, GestureOwner.SCALE) },
    ) { value ->
        val base = session.base ?: return@detectSpatialScaleGesture
        val id = session.objectId ?: return@detectSpatialScaleGesture
        if (handoff.isSuperseded(GestureOwner.SCALE, id)) return@detectSpatialScaleGesture
        val current = session.latest ?: base
        val factor = value.scaleValue.coerceIn(
            1f / MAX_SCALE_FACTOR_PER_EVENT,
            MAX_SCALE_FACTOR_PER_EVENT,
        )
        val nextScale = (current.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val next = localScalePivot?.let { pivot ->
            scaleTransformAroundLocalPivot(
                transform = current,
                nextScale = nextScale,
                localPivotX = pivot.x,
                localPivotY = pivot.y,
                localPivotZ = pivot.z,
            )
        } ?: current.copy(scale = nextScale)
        session.latest = next
        handoff.update(GestureOwner.SCALE, id, next)
        if (session.presentationInspection) {
            scene()?.updatePresentationInspection(id, next)
        } else {
            vm.previewTransform(id, next)
            scene()?.pushTransform(id, next)
        }
    }
}

/**
 * Changes uniform scale while keeping a local-space pivot fixed in world space.
 *
 * PICO entity space uses a right-handed positive-Y rotation:
 * x' = cos(yaw) * x + sin(yaw) * z
 * z' = -sin(yaw) * x + cos(yaw) * z
 */
internal fun scaleTransformAroundLocalPivot(
    transform: TransformData,
    nextScale: Float,
    localPivotX: Float,
    localPivotY: Float,
    localPivotZ: Float,
): TransformData {
    val scaleDelta = transform.scale - nextScale
    if (scaleDelta == 0f) return transform
    val cosine = cos(transform.rotationY)
    val sine = sin(transform.rotationY)
    val rotatedPivotX = cosine * localPivotX + sine * localPivotZ
    val rotatedPivotZ = -sine * localPivotX + cosine * localPivotZ
    return transform.copy(
        px = transform.px + rotatedPivotX * scaleDelta,
        py = transform.py + localPivotY * scaleDelta,
        pz = transform.pz + rotatedPivotZ * scaleDelta,
        scale = nextScale,
    )
}

private fun GestureSession.endOrClear(
    vm: StudioViewModel,
    scene: () -> SceneController?,
    handoff: ManipulationHandoffState,
    owner: GestureOwner,
) {
    val id = objectId ?: return
    if (handoff.isSuperseded(owner, id)) {
        clear()
        return
    }
    end(vm, scene)
    handoff.clear(owner, id)
}

private fun GestureSession.cancelOrClear(
    vm: StudioViewModel,
    scene: () -> SceneController?,
    handoff: ManipulationHandoffState,
    owner: GestureOwner,
) {
    val id = objectId ?: return
    if (handoff.isSuperseded(owner, id)) {
        clear()
        return
    }
    cancel(vm, scene)
    handoff.clear(owner, id)
}
