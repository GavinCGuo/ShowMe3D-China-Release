package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent

fun Modifier.trackStudioUiPointer(
    context: Context,
    onUiPointerActiveChanged: (Boolean) -> Unit,
): Modifier =
    pointerInput(Unit) {
        val activeUiPointerIds = mutableSetOf<Any>()
        var active = false

        fun emitActiveState(next: Boolean) {
            if (active == next) return
            active = next
            onUiPointerActiveChanged(next)
        }

        try {
            detectSpatialPointerEvent(
                context = context,
                targetedToEntity = TargetEntity.any { entity -> entity.isStudioUiEntityOrChild() },
            ) { events ->
                events.forEach { event ->
                    val pointerId = event.pointerId
                    if (event.pressed && event.targetedEntity.isStudioUiEntityOrChild()) {
                        activeUiPointerIds.add(pointerId)
                    } else {
                        activeUiPointerIds.remove(pointerId)
                    }
                }
                emitActiveState(activeUiPointerIds.isNotEmpty())
                false
            }
        } finally {
            activeUiPointerIds.clear()
            emitActiveState(false)
        }
    }

fun Entity?.isStudioUiEntityOrChild(): Boolean {
    var current = this
    repeat(MAX_UI_PARENT_SEARCH_DEPTH) {
        val name = current?.getName()
        if (name?.startsWith(UI_PANEL_ENTITY_PREFIX) == true) return true
        current = runCatching { current?.getParent() }.getOrNull()
    }
    return false
}

private const val UI_PANEL_ENTITY_PREFIX = "studio_"
private const val MAX_UI_PARENT_SEARCH_DEPTH = 12
