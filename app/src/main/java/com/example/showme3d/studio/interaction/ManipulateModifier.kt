package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.scene.SceneController
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioViewModel
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.manipulatable(context: Context, vm: StudioViewModel, controller: SceneController?): Modifier {
    if (controller == null || vm.screen != AppScreen.EDITOR || vm.viewpointModeObjectId != null ||
        vm.strokeDraft != null) return this
    val page = vm.currentPage
    val mode = vm.mode
    return composed {
        fun eligible(id: String) = when (mode) {
            AppMode.EDIT -> !vm.isTransformLocked(id)
            AppMode.PRESENT -> vm.canInspectModelInPresent(id)
        }
        val session = remember(controller, vm, page, vm.document.id, mode, vm.presentAnnotationTool) {
            var pivot = Vector3.ZERO
            var start = TransformData()
            ModelGrabSession(
                read = read@{ id ->
                    if (!eligible(id)) return@read null
                    val visible = controller.visibleTransformForSelection(id) ?: vm.currentTransform(id) ?: return@read null
                    start = visible
                    pivot = controller.entityFor(id)?.let { it.getVisualBounds(it, recursive = true, enabledOnly = false).center }
                        ?.takeIf { it.length().isFinite() } ?: Vector3.ZERO
                    if (mode == AppMode.PRESENT) controller.beginPresentationInspection(id, visible)
                    else {
                        vm.select(id, visible)
                        vm.beginTransformPreview(id, visible)
                    }
                    visible
                },
                scaleOf = TransformData::scale,
                transform = { pose, delta, scale, yaw ->
                    val moved = pose.copy(px = pose.px + delta.x, py = pose.py + delta.y, pz = pose.pz + delta.z)
                    scaleTransformAroundLocalPivot(moved, scale, pivot.x, pivot.y, pivot.z, moved.rotationY + yaw)
                },
                preview = { id, pose ->
                    if (eligible(id)) {
                        if (mode == AppMode.PRESENT) controller.updatePresentationInspection(id, pose)
                        else {
                            vm.previewTransform(id, pose)
                            controller.pushTransform(id, pose, updateAnimationEndpointChrome = false)
                        }
                    }
                },
                release = { id, pose, cancelled ->
                    if (vm.currentPage === page && vm.mode == mode) {
                        if (mode == AppMode.PRESENT) {
                            if (cancelled) controller.updatePresentationInspection(id, start)
                            controller.endPresentationInspection(id, released = !cancelled)
                        } else if (cancelled || !eligible(id)) {
                            vm.cancelTransformPreview(id, start)
                            controller.pushTransform(id, start)
                        } else {
                            vm.commitTransform(id, pose)
                            controller.pushTransform(id, pose)
                        }
                    }
                },
                activeChanged = vm::updateObjectManipulationInProgress,
            )
        }
        modelGrabInput(context, session, { controller.objectIdFor(it)?.takeIf(::eligible) }, controller::viewPointInWorkspace)
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
    nextYaw: Float = transform.rotationY,
): TransformData {
    val scaleDelta = transform.scale - nextScale
    if (scaleDelta == 0f && nextYaw == transform.rotationY) return transform
    val cosine = cos(transform.rotationY)
    val sine = sin(transform.rotationY)
    val rotatedPivotX = cosine * localPivotX + sine * localPivotZ
    val rotatedPivotZ = -sine * localPivotX + cosine * localPivotZ
    return transform.copy(
        px = transform.px + rotatedPivotX * transform.scale - (cos(nextYaw) * localPivotX + sin(nextYaw) * localPivotZ) * nextScale,
        py = transform.py + localPivotY * scaleDelta,
        pz = transform.pz + rotatedPivotZ * transform.scale - (-sin(nextYaw) * localPivotX + cos(nextYaw) * localPivotZ) * nextScale,
        rotationY = nextYaw,
        scale = nextScale,
    )
}
