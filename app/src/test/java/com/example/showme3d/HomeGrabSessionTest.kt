package com.example.showme3d

import com.example.showme3d.content.HandSide
import com.example.showme3d.content.PinchCandidate
import com.example.showme3d.content.TrackedHomeGrab
import com.example.showme3d.content.HomeFloatingTransform
import com.example.showme3d.studio.interaction.ModelGrabSession
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGrabSessionTest {
    private val start = HomeFloatingTransform(Vector3(.2f, .8f, -1f), 1.5f, 30f)
    private var visible = start
    private val active = mutableListOf<Boolean>()
    private val released = mutableListOf<String>()
    private val grab = ModelGrabSession(
        read = { start },
        scaleOf = HomeFloatingTransform::scale,
        transform = { pose, delta, scale, yaw ->
            HomeFloatingTransform(pose.position + delta, scale, pose.yawDegrees + Math.toDegrees(yaw.toDouble()).toFloat())
        },
        preview = { _, next -> visible = next },
        release = { id, next, _ -> visible = next; released.add(id) },
        activeChanged = { active.add(it) },
    )

    @Test fun nativeDragMovesImmediatelyWithoutPointerContactsOrPriorSelection() {
        grab.drag("camera", Vector3(.03f, -.02f, .05f))
        assertEquals(.23f, visible.position.x, .0001f)
        assertEquals(.78f, visible.position.y, .0001f)
        assertEquals(-.95f, visible.position.z, .0001f)
        assertEquals(start.scale, visible.scale, 0f)
        assertEquals(start.yawDegrees, visible.yawDegrees, 0f)
        grab.endDrag(false)
        assertEquals(listOf("camera"), released)
        assertEquals(listOf(true, false), active)
    }

    @Test fun addingAHandPreservesPositionAndSuppressesConcurrentNativeDrag() {
        grab.drag("camera", Vector3(.05f, 0f, 0f))
        val position = visible.position.x
        grab.pairOwnership(true)
        grab.endDrag(true)
        grab.drag("camera", Vector3(.2f, 0f, 0f))
        assertEquals(start.scale, grab.begin("camera")!!, 0f)
        grab.change(Vector3.ZERO, 2f, .5f)
        assertEquals(position, visible.position.x, 0f)
        assertEquals(2f, visible.scale, 0f)
        grab.pairOwnership(false)
        grab.endDrag(false)
        assertEquals(listOf("camera"), released)
        assertEquals(listOf(true, false), active)
    }

    @Test fun trackedSecondPinchTransformsHeldModelWithoutSecondPointerHit() {
        val tracking = TrackedHomeGrab(grab)
        val left = PinchCandidate(HandSide.LEFT, Vector3.ZERO, .012f)
        val right = PinchCandidate(HandSide.RIGHT, Vector3(.2f, 0f, 0f), .012f)
        grab.drag("camera", Vector3(.03f, 0f, 0f))
        tracking.update(listOf(left, right), gazeTarget = "drone")
        grab.endDrag(true)
        tracking.update(listOf(left, right.copy(position = Vector3(0f, 0f, -.3f))))
        assertEquals(2.25f, visible.scale, .0001f)
        assertEquals(120f, visible.yawDegrees, .0001f)
        assertEquals(.23f, visible.position.x, .0001f)
        tracking.update(listOf(left))
        tracking.update(listOf(left.copy(position = Vector3(.04f, 0f, 0f))))
        assertEquals(.27f, visible.position.x, .0001f)
        tracking.update(emptyList())
        tracking.update(emptyList())
        assertEquals(listOf("camera"), released)
    }

    @Test fun pairNeedsModelAndCanStartBeforeFirstDragMoves() {
        val tracking = TrackedHomeGrab(grab)
        val hands = listOf(PinchCandidate(HandSide.LEFT, Vector3.ZERO, .010f),
            PinchCandidate(HandSide.RIGHT, Vector3(.2f, 0f, 0f), .013f))
        tracking.update(hands)
        assertEquals(false, tracking.active)
        tracking.pointerTarget = "camera"
        tracking.update(hands, gazeTarget = "drone")
        assertEquals("camera", grab.held)
        tracking.finish(cancelled = true)
        assertEquals(start, visible)
        assertEquals(listOf("camera"), released)
    }
}
