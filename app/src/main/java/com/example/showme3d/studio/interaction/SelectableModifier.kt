package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.showme3d.studio.scene.SceneController
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.PresentAnnotationTool
import com.example.showme3d.studio.state.StudioViewModel
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture

/**
 * 点击选中：tap 命中任意可交互实体 → 用实体名（= objectId）写回 [StudioViewModel.select]。
 *
 * 配合 [ObjectEntityFactory] 给每个实体补的 Collision/Interactable/HoverEffect 组件，
 * hover 高亮由 SDK 自动处理；选中后的虚线框/操作条由 UI 层根据 selectedObjectId 渲染。
 *
 * 注意：必须独立占用一个 [pointerInput]，不要和拖拽/缩放手势堆在同一块。
 */
fun Modifier.selectable(
    context: Context,
    vm: StudioViewModel,
    scene: () -> SceneController?,
    onUiEntityTap: (String) -> Boolean = { false },
    onIgnoredEntityTap: (com.pico.spatial.core.ecs.Entity?) -> Boolean = { false },
): Modifier =
    pointerInput(Unit) {
        val presentationDoubleTap = PresentationDoubleTapTracker()
        detectSpatialTapGesture(context, TargetEntity.any()) { tap ->
            val targetName = tap.targetEntity?.getName()
            if (targetName != null && onUiEntityTap(targetName)) return@detectSpatialTapGesture
            if (onIgnoredEntityTap(tap.targetEntity)) return@detectSpatialTapGesture
            if (tap.targetEntity.isStudioUiEntityOrChild()) return@detectSpatialTapGesture
            val controller = scene()
            if (vm.mode == AppMode.PRESENT && vm.presentAnnotationTool == PresentAnnotationTool.ERASER) {
                val annotationId = controller?.annotationIdFor(tap.targetEntity)
                if (annotationId != null && vm.eraseStrokeAnnotation(annotationId)) {
                    controller.syncPageAnnotations()
                } else {
                    vm.handleSceneTap(null)
                }
                return@detectSpatialTapGesture
            }
            controller?.commentIdFor(tap.targetEntity)?.let { commentId ->
                if (vm.mode == AppMode.EDIT) {
                    vm.editAnnotationComment(commentId)
                }
                return@detectSpatialTapGesture
            }
            val objectId = controller?.objectIdFor(tap.targetEntity) ?: targetName
            if (
                objectId != null &&
                vm.canInspectModelInPresent(objectId) &&
                controller?.hasPresentationInspectionTransform(objectId) == true
            ) {
                if (presentationDoubleTap.register(objectId, System.nanoTime() / 1_000_000L)) {
                    controller.resetPresentationInspection(objectId)
                }
                return@detectSpatialTapGesture
            }
            presentationDoubleTap.reset()
            vm.handleSceneTap(objectId, objectId?.let { controller?.visibleTransformForSelection(it) })
        }
    }
