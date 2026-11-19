package com.example.showme3d.studio.interaction

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import androidx.compose.ui.input.pointer.PointerInputScope
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.gesture.SpatialPointerInfo
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import kotlin.math.atan2
import kotlin.math.hypot

private const val MIN_SPAN = .015f
private const val DRAG_SLOP = .007f

internal fun horizontalTurn(previous: Vector3, next: Vector3): Float {
    if (hypot(previous.x, previous.z) < MIN_SPAN || hypot(next.x, next.z) < MIN_SPAN) return 0f
    return atan2(previous.z * next.x - previous.x * next.z, previous.x * next.x + previous.z * next.z)
}

internal data class GrabContact(val hit: Vector3, val hand: Vector3?) {
    // Keep the original reach along the pointing ray even when it crosses a new surface.
    fun pointAt(reach: Float?): Vector3? {
        if (reach == null) return hit
        val origin = hand ?: return null
        if (reach < .1f) return origin
        val ray = hit - origin
        val length = ray.length()
        return if (length > .001f) origin + ray * (reach / length) else null
    }
}

private fun SpatialPointerInfo.contact(convert: (Vector3, ViewCoordinateSpace) -> Vector3): GrabContact? {
    val hit = convert(Vector3(x, y, z), ViewCoordinateSpace.Local)
    if (!hit.length().isFinite()) return null
    val raw = inputDevicePose.rawPosition
    val hand = if (raw.x == 0f && raw.y == 0f && raw.z == 0f) null
        else convert(Vector3(raw.x, raw.y, raw.z), ViewCoordinateSpace.Global).takeIf { it.length().isFinite() }
    return GrabContact(hit, hand)
}

internal class GrabMotion(
    private val begin: (String) -> Float?,
    private val change: (Vector3, Float, Float) -> Unit,
) {
    var scale: Float? = null
        private set
    private var reach: Float? = null
    private var lastPoint: Vector3? = null
    private var lastSpan: Vector3? = null
    private var useHands = false
    private var initialDistance = 0f
    private var initialScale = 1f

    private fun span(a: GrabContact, b: GrabContact): Vector3 =
        if (useHands) requireNotNull(b.hand) - requireNotNull(a.hand) else b.hit - a.hit

    fun rebase(a: GrabContact, b: GrabContact?) {
        if (b == null) {
            reach = a.hand?.let { (a.hit - it).length() }
            lastPoint = a.pointAt(reach)
        } else {
            useHands = a.hand != null && b.hand != null
            lastSpan = span(a, b)
            initialDistance = lastSpan!!.length()
            initialScale = scale ?: 1f
        }
    }

    fun move(id: String, a: GrabContact, b: GrabContact?) {
        if (b == null) {
            if ((a.hand != null) != (reach != null)) {
                rebase(a, null)
                return
            }
            val next = a.pointAt(reach)
            val last = lastPoint
            if (next == null || last == null) {
                lastPoint = next
                return
            }
            val delta = next - last
            val distance = delta.length()
            if (scale == null && distance < DRAG_SLOP) return
            lastPoint = next
            if (distance <= 0f || distance > .15f) return
            val current = scale ?: begin(id) ?: return
            scale = current
            change(delta, current, 0f)
            return
        }

        if (useHands != (a.hand != null && b.hand != null)) {
            rebase(a, b)
            return
        }
        val next = span(a, b)
        val distance = next.length()
        val previous = lastSpan ?: return
        if (initialDistance < MIN_SPAN || previous.length() < MIN_SPAN || distance < MIN_SPAN ||
            distance / previous.length() !in .5f..2f ||
            Vector3.dot(previous.normalize(), next.normalize()) < -.985f
        ) {
            rebase(a, b)
            return
        }
        if (scale == null) {
            if ((next - previous).length() < DRAG_SLOP) return
            initialScale = begin(id) ?: return
        }
        val nextScale = (initialScale * distance / initialDistance).coerceIn(.08f, 10f)
        lastSpan = next
        scale = nextScale
        change(Vector3.ZERO, nextScale, horizontalTurn(previous, next))
    }
}

/** One target and one edit transaction from first motion to last release. */
internal suspend fun PointerInputScope.detectModelGrab(
    context: Context,
    objectFor: (Entity?) -> String?,
    convert: (Vector3, ViewCoordinateSpace) -> Vector3,
    begin: (String) -> Float?,
    change: (Vector3, Float, Float) -> Unit,
    finish: (cancelled: Boolean) -> Unit,
    contactTarget: (String?) -> Unit = {},
    // When a native drag recognizer handles single-hand input, only take over
    // after a second contact arrives. Keep ownership through the last release.
    onPairOwnership: ((Boolean) -> Unit)? = null,
    trackedActive: () -> Boolean = { false },
    trackedGeneration: () -> Int = { 0 },
) {
    val contacts = linkedMapOf<Long, GrabContact>()
    var owner: String? = null
    var paired = false
    var generation = trackedGeneration()
    var motion = GrabMotion(begin, change)

    fun release(cancelled: Boolean) {
        if (motion.scale != null || paired) finish(cancelled)
        if (paired) onPairOwnership?.invoke(false)
        paired = false
        contacts.clear()
        owner = null
        motion = GrabMotion(begin, change)
        contactTarget(null)
    }
    try {
        detectSpatialPointerEvent(context) { events ->
            // A tracked hand pair can take over even if the SDK reports only one pointer.
            // Drop the previous pointer baseline without releasing the shared model.
            if (generation != trackedGeneration()) {
                generation = trackedGeneration()
                contacts.clear()
                owner = null
                paired = false
                motion = GrabMotion(begin, change)
                contactTarget(null)
            }
            if (trackedActive()) return@detectSpatialPointerEvent false
            var countChanged = false
            var ownedEvents = 0
            for (event in events) {
                val key = event.pointerId.value
                if (event.isDownEvent() && key !in contacts) {
                    val target = objectFor(event.targetedEntity)
                    if (contacts.size == 2 || (owner == null && target == null)) continue
                    if (owner != null && target != owner && event.targetedEntity != null) continue
                    val contact = event.contact(convert) ?: continue
                    owner = owner ?: target
                    contacts[key] = contact
                    countChanged = true
                } else if (key in contacts) {
                    if (!event.pressed) {
                        contacts.remove(key)
                        countChanged = true
                    } else {
                        event.contact(convert)?.let { contacts[key] = it }
                    }
                } else continue
                ownedEvents++
            }
            val id = owner ?: return@detectSpatialPointerEvent false
            if (ownedEvents == 0) return@detectSpatialPointerEvent false
            val activeBefore = motion.scale != null
            val it = contacts.values.iterator()
            if (!it.hasNext()) {
                release(false)
            } else {
                val first = it.next()
                val second = if (it.hasNext()) it.next() else null
                if (countChanged) {
                    contactTarget(id)
                    if (second != null && !paired) {
                        paired = true
                        onPairOwnership?.invoke(true)
                    }
                    motion.rebase(first, second)
                } else if (onPairOwnership == null || paired) motion.move(id, first, second)
            }
            (activeBefore || motion.scale != null) && ownedEvents == events.size
        }
    } finally {
        if (owner != null && !trackedActive() && generation == trackedGeneration()) release(true)
        else contactTarget(null)
    }
}

internal class ModelGrabSession<T : Any>(
    private val read: (String) -> T?,
    private val scaleOf: (T) -> Float,
    private val transform: (T, Vector3, Float, Float) -> T,
    private val preview: (String, T) -> Unit,
    private val release: (String, T, Boolean) -> Unit,
    private val activeChanged: (Boolean) -> Unit,
) {
    var held: String? = null
        private set
    private var start: T? = null
    private var latest: T? = null
    private var paired = false

    fun begin(id: String): Float? {
        if (held != null) return latest?.let(scaleOf)?.takeIf { held == id }
        val pose = read(id) ?: return null
        held = id
        start = pose
        latest = pose
        activeChanged(true)
        return scaleOf(pose)
    }

    fun drag(id: String, delta: Vector3) {
        if (paired || !delta.length().isFinite() || delta.length() == 0f) return
        val scale = begin(id) ?: return
        change(delta, scale, 0f)
    }

    fun change(delta: Vector3, scale: Float, yaw: Float) {
        val id = held ?: return
        val previous = latest ?: return
        val next = transform(previous, delta, scale, yaw)
        latest = next
        preview(id, next)
    }

    fun pairOwnership(active: Boolean) {
        paired = active
        // A pair can release without having moved enough to produce a transform.
        if (!active) finish(false)
    }

    fun endDrag(cancelled: Boolean) {
        if (!paired) finish(cancelled)
    }

    fun finish(cancelled: Boolean) {
        val id = held ?: return
        val pose = (if (cancelled) start else latest) ?: return
        held = null
        start = null
        latest = null
        release(id, pose, cancelled)
        activeChanged(false)
    }
}

internal fun <T : Any> Modifier.modelGrabInput(
    context: Context,
    session: ModelGrabSession<T>,
    objectFor: (Entity?) -> String?,
    convert: (Vector3, ViewCoordinateSpace) -> Vector3,
    trackedActive: () -> Boolean = { false },
    trackedGeneration: () -> Int = { 0 },
    contactTarget: (String?) -> Unit = {},
): Modifier = pointerInput(session) {
    var owner: String? = null
    fun end(cancelled: Boolean) {
        if (owner != null) session.endDrag(cancelled)
        owner = null
    }
    try {
        detectSpatialDragGesture(context, TargetEntity.any { objectFor(it) != null },
            onDragEnd = { end(false) }, onDragCancel = { end(true) }) { drag ->
            val id = owner ?: objectFor(drag.targetEntity) ?: return@detectSpatialDragGesture
            owner = id
            val d = drag.dragAmount
            val delta = convert(Vector3(d.x, d.y, d.z), ViewCoordinateSpace.Local) -
                convert(Vector3.ZERO, ViewCoordinateSpace.Local)
            session.drag(id, delta)
        }
    } finally {
        end(true)
    }
}.pointerInput(session) {
    detectModelGrab(
        context,
        objectFor = objectFor,
        convert = convert,
        begin = session::begin,
        change = session::change,
        finish = session::finish,
        contactTarget = contactTarget,
        trackedActive = trackedActive,
        trackedGeneration = trackedGeneration,
        onPairOwnership = session::pairOwnership,
    )
}
