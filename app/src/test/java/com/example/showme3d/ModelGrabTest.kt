package com.example.showme3d

import com.example.showme3d.studio.interaction.GrabContact
import com.example.showme3d.studio.interaction.GrabMotion
import com.example.showme3d.studio.interaction.horizontalTurn
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class ModelGrabTest {
    private class HeldModel {
        var begins = 0
        var distance = Vector3.ZERO
        var scale = 1f
        var yaw = 0f
        val grab = GrabMotion({ begins++; scale }) { delta, size, turn ->
            distance += delta
            scale = size
            yaw += turn
        }
    }

    private fun hit(x: Float, y: Float = 0f, z: Float = 0f) = GrabContact(Vector3(x,y,z), null)

    @Test fun dragAndTwoHandTurnAreOneContinuousGrab() {
        val model = HeldModel()
        model.grab.rebase(hit(0f), null)
        model.grab.move("part", hit(.08f), null)
        model.grab.rebase(hit(.08f), hit(.28f))
        model.grab.move("part", hit(.08f), hit(.08f, z = -.4f))
        assertEquals(2f, model.scale, .0001f)
        assertEquals(PI.toFloat()/2f, model.yaw, .0001f)
        assertEquals(.08f, model.distance.x, .0001f)
        model.grab.rebase(hit(.08f, z = -.4f), null)
        model.grab.move("part", hit(.11f, z = -.4f), null)
        assertEquals(.11f, model.distance.x, .0001f)
        assertEquals(1, model.begins)
    }

    @Test fun raisingAHandChangesSpanWithoutTurningTheModel() {
        assertEquals(0f, horizontalTurn(Vector3(.2f,0f,0f), Vector3(.2f,.2f,0f)), 0f)
        assertEquals(0f, horizontalTurn(Vector3(.001f,.4f,0f), Vector3(-.001f,.4f,0f)), 0f)
    }

    @Test fun scalingUsesPhysicalHandsAndRecoversFromTrackingJumps() {
        val model = HeldModel()
        fun hand(x: Float) = GrabContact(Vector3(0f, 0f, -1f), Vector3(x,0f,0f))
        model.grab.rebase(hand(0f), hand(.2f))
        model.grab.move("part", hand(0f), hand(.3f))
        assertEquals(1.5f, model.scale, .0001f)
        model.grab.move("part", hand(0f), hand(1f))
        assertEquals(1.5f, model.scale, .0001f)
        model.grab.move("part", hand(0f), hand(.9f))
        assertEquals(1.35f, model.scale, .0001f)
    }

    @Test fun distantGrabKeepsReachWhenTheRayHitsAnotherSurface() {
        val model = HeldModel()
        fun ray(hitDepth: Float, handDepth: Float) =
            GrabContact(Vector3(0f,0f,hitDepth), Vector3(0f,0f,handDepth))
        model.grab.rebase(ray(-1.5f,-.2f), null)
        model.grab.move("part", ray(-4f,-.2f), null)
        assertEquals(0, model.begins)
        model.grab.move("part", ray(-4f,-.15f), null)
        assertEquals(.05f, model.distance.z, .0001f)
    }
}
