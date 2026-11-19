package com.example.showme3d.content

import com.example.showme3d.studio.interaction.GrabContact
import com.example.showme3d.studio.interaction.GrabMotion
import com.example.showme3d.studio.interaction.ModelGrabSession

/** The first SDK hit chooses the model. Hand tracking supplies both pinches in container meters. */
internal class TrackedHomeGrab(private val session: ModelGrabSession<HomeFloatingTransform>) {
    var pointerTarget: String? = null
    var generation = 0
        private set
    val active get() = motion != null
    private var motion: GrabMotion? = null
    private var leftDown = false
    private var rightDown = false
    private var emptyFrames = 0

    fun update(candidates: List<PinchCandidate>, gazeTarget: String? = null) {
        fun contact(side: HandSide, wasDown: Boolean): GrabContact? {
            val hand = candidates.firstOrNull { it.hand == side } ?: return null
            val threshold = if (wasDown) .030f else .020f
            if (hand.pinchDistance !in .0015f..threshold) return null
            return GrabContact(hand.position, null)
        }
        val left = contact(HandSide.LEFT, leftDown)
        val right = contact(HandSide.RIGHT, rightDown)
        val changed = leftDown != (left != null) || rightDown != (right != null) || emptyFrames > 0
        leftDown = left != null
        rightDown = right != null
        val first = left ?: right
        if (first == null) {
            if (active && ++emptyFrames >= 2) finish(cancelled = false)
            return
        }
        emptyFrames = 0
        if (!active) {
            if (left == null || right == null) return
            val target = session.held ?: pointerTarget ?: gazeTarget ?: return
            if (session.begin(target) == null) return
            session.pairOwnership(true)
            generation++
            motion = GrabMotion(session::begin, session::change).also { it.rebase(left, right) }
            return
        }
        val second = right.takeIf { left != null }
        if (changed) motion?.rebase(first, second)
        else session.held?.let { motion?.move(it, first, second) }
    }

    fun finish(cancelled: Boolean) {
        if (!active) return
        motion = null
        session.finish(cancelled)
        session.pairOwnership(false)
        emptyFrames = 0
        leftDown = false
        rightDown = false
        pointerTarget = null
    }
}
