package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.showme3d.studio.ui.PanelPosition
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import kotlin.math.abs

fun Modifier.workspaceDraggable(
    context: Context,
    dragHandleEntity: () -> Entity?,
    workspacePosition: () -> PanelPosition,
    onWorkspacePreview: (PanelPosition) -> Unit,
    onWorkspaceMoved: (PanelPosition) -> Unit,
    onWorkspaceDragActiveChanged: (Boolean) -> Unit = {},
): Modifier =
    run {
        val active = WorkspaceDragActiveEmitter(onWorkspaceDragActiveChanged)
        pointerInput(dragHandleEntity()) {
            val target = dragHandleEntity() ?: return@pointerInput
            workspaceDragPointerTracker(context, target, active)
        }.pointerInput(dragHandleEntity()) {
            val target = dragHandleEntity() ?: return@pointerInput
            workspaceDragGesture(
                context,
                target,
                workspacePosition,
                onWorkspacePreview,
                onWorkspaceMoved,
                active,
            )
        }
    }

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.workspaceDragPointerTracker(
    context: Context,
    target: Entity,
    active: WorkspaceDragActiveEmitter,
) {
    val activePointerIds = mutableSetOf<Any>()
    try {
        detectSpatialPointerEvent(
            context = context,
            targetedToEntity = TargetEntity.hit(target),
        ) { events ->
            events.forEach { event ->
                if (event.pressed) {
                    activePointerIds.add(event.pointerId)
                } else {
                    activePointerIds.remove(event.pointerId)
                }
            }
            active.setPointerActive(activePointerIds.isNotEmpty())
            false
        }
    } finally {
        activePointerIds.clear()
        active.setPointerActive(false)
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.workspaceDragGesture(
    context: Context,
    target: Entity,
    workspacePosition: () -> PanelPosition,
    onWorkspacePreview: (PanelPosition) -> Unit,
    onWorkspaceMoved: (PanelPosition) -> Unit,
    active: WorkspaceDragActiveEmitter,
) {
    val session = WorkspaceDragSession()
    detectSpatialDragGesture(
        context,
        TargetEntity.hit(target),
        {
            active.setDragActive(true)
            session.begin(workspacePosition())
        },
        {
            session.end(onWorkspaceMoved)
            active.setDragActive(false)
        },
        {
            session.end(onWorkspaceMoved)
            active.setDragActive(false)
        },
    ) { value ->
        active.setDragActive(true)
        val current = session.latest ?: session.base ?: workspacePosition()
        val d = value.dragAmount
        if (abs(d.x) > MAX_RAW_PANEL_DRAG_DELTA || abs(d.y) > MAX_RAW_PANEL_DRAG_DELTA || abs(d.z) > MAX_RAW_PANEL_DRAG_DELTA) {
            return@detectSpatialDragGesture
        }
        val next = PanelPosition(
            x = current.x + (d.x * PANEL_DRAG_SENSITIVITY_XY).coerceIn(-MAX_PANEL_DRAG_STEP_XY, MAX_PANEL_DRAG_STEP_XY),
            y = current.y + (-d.y * PANEL_DRAG_SENSITIVITY_XY).coerceIn(-MAX_PANEL_DRAG_STEP_XY, MAX_PANEL_DRAG_STEP_XY),
            z = current.z + (d.z * PANEL_DRAG_SENSITIVITY_Z).coerceIn(-MAX_PANEL_DRAG_STEP_Z, MAX_PANEL_DRAG_STEP_Z),
        ).clampedToWorkspace()
        session.latest = next
        onWorkspacePreview(next)
    }
}

private class WorkspaceDragActiveEmitter(
    private val onChanged: (Boolean) -> Unit,
) {
    private var pointerActive = false
    private var dragActive = false
    private var emittedActive = false

    fun setPointerActive(active: Boolean) {
        pointerActive = active
        emit()
    }

    fun setDragActive(active: Boolean) {
        dragActive = active
        emit()
    }

    private fun emit() {
        val next = pointerActive || dragActive
        if (emittedActive == next) return
        emittedActive = next
        onChanged(next)
    }
}

private class WorkspaceDragSession {
    var base: PanelPosition? = null
    var latest: PanelPosition? = null

    fun begin(position: PanelPosition) {
        base = position
        latest = position
    }

    fun end(onWorkspaceMoved: (PanelPosition) -> Unit) {
        latest?.let { onWorkspaceMoved(it) }
        base = null
        latest = null
    }
}

private fun PanelPosition.clampedToWorkspace(): PanelPosition =
    PanelPosition(
        x = x.coerceIn(-1.3f, 1.3f),
        y = y.coerceIn(0.18f, 1.75f),
        z = z.coerceIn(-1.75f, -0.65f),
    )

private const val PANEL_DRAG_SENSITIVITY_XY = 0.00042f
private const val PANEL_DRAG_SENSITIVITY_Z = 0.00028f
private const val MAX_PANEL_DRAG_STEP_XY = 0.034f
private const val MAX_PANEL_DRAG_STEP_Z = 0.032f
private const val MAX_RAW_PANEL_DRAG_DELTA = 160f
