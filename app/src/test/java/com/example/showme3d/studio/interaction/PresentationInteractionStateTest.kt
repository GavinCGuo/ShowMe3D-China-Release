package com.example.showme3d.studio.interaction

import com.example.showme3d.studio.model.TransformData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationInteractionStateTest {
    @Test
    fun doubleTapRequiresSameObjectInsideWindow() {
        val tracker = PresentationDoubleTapTracker(windowMillis = 450L)

        assertFalse(tracker.register("model-a", 1_000L))
        assertFalse(tracker.register("model-b", 1_200L))
        assertTrue(tracker.register("model-b", 1_500L))
        assertFalse(tracker.register("model-a", 2_000L))
        assertFalse(tracker.register("model-a", 2_451L))
    }

    @Test
    fun completedDoubleTapStartsANewSequence() {
        val tracker = PresentationDoubleTapTracker(windowMillis = 450L)

        assertFalse(tracker.register("model-a", 1_000L))
        assertTrue(tracker.register("model-a", 1_200L))
        assertFalse(tracker.register("model-a", 1_300L))
    }

    @Test
    fun resetMotionInterpolatesEveryTransformComponentAndFinishesAtTarget() {
        val from = TransformData(px = 1f, py = 2f, pz = -3f, rotationY = 0.8f, scale = 2f)
        val to = TransformData(px = 0f, py = 1f, pz = -1f, rotationY = 0.2f, scale = 1f)
        val motion = PresentationResetMotion(
            from = from,
            to = to,
            startedAtNanos = 1_000L,
            durationNanos = 1_000L,
        )

        assertEquals(from, motion.transformAt(1_000L))
        val midpoint = motion.transformAt(1_500L)
        assertTrue(midpoint.px in to.px..from.px)
        assertTrue(midpoint.py in to.py..from.py)
        assertTrue(midpoint.pz in from.pz..to.pz)
        assertTrue(midpoint.rotationY in to.rotationY..from.rotationY)
        assertTrue(midpoint.scale in to.scale..from.scale)
        assertFalse(motion.isComplete(1_999L))
        val completed = motion.transformAt(2_000L)
        assertEquals(to.px, completed.px, 0.0001f)
        assertEquals(to.py, completed.py, 0.0001f)
        assertEquals(to.pz, completed.pz, 0.0001f)
        assertEquals(to.rotationY, completed.rotationY, 0.0001f)
        assertEquals(to.scale, completed.scale, 0.0001f)
        assertTrue(motion.isComplete(2_000L))
    }
}
