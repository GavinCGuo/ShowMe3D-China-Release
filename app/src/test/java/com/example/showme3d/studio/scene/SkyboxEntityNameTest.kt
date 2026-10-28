package com.example.showme3d.studio.scene

import com.example.showme3d.studio.model.PageSkybox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyboxEntityNameTest {
    @Test
    fun userFacingSymbolsNeverReachSpatialEntityName() {
        val skybox = PageSkybox(
            source = "file:///example/火星 panorama.jpg",
            displayName = "Curiosity - Nevado Sajama 360°",
        )

        val name = skyboxEntityName(skybox)

        assertTrue(name.matches(Regex("[a-z0-9_]+")))
        assertEquals(name, skyboxEntityName(skybox))
    }
}
