package com.example.showme3d

import com.example.showme3d.studio.examples.DinosaurDetectiveExampleAssets
import com.example.showme3d.studio.examples.createDinosaurDetectiveExampleDocument
import com.example.showme3d.studio.examples.dinosaurDetectiveModelFiles
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DinosaurDetectiveExampleFactoryTest {
    @Test
    fun buildsTheSevenPageEvidenceToReconstructionStory() {
        val document = createDinosaurDetectiveExampleDocument(1, testAssets())

        assertEquals("恐龙侦探：化石里藏着什么？", document.name)
        assertEquals(7, document.pages.size)
        assertEquals(8, document.objects.values.count { it.type == ObjectType.MODEL })
        assertEquals(1, document.objects.values.count { it.type == ObjectType.IMAGE })
        assertEquals(7, document.objects.values.count { it.type == ObjectType.TEXT })
        assertEquals(
            dinosaurDetectiveModelFiles.keys,
            document.objects.filterValues { it.type == ObjectType.MODEL }.keys,
        )

        document.pages.forEachIndexed { index, page ->
            val copy = document.objects.getValue("dinosaur_copy_${index + 1}")
            assertTrue(page.states.getValue(copy.id).visible)
            assertTrue(copy.faceViewer)
            assertTrue(copy.isLocked)
            assertTrue(copy.textBackgroundEnabled)
            assertTrue(page.annotations.isEmpty())
        }
    }

    @Test
    fun keepsFivePagesInRealityAndUsesOnlyTwoPurposefulSkyboxes() {
        val pages = createDinosaurDetectiveExampleDocument(1, testAssets()).pages

        listOf(0, 1, 2, 4, 5).forEach { pageIndex -> assertNull(pages[pageIndex].skybox) }
        assertEquals("file:///example/fossil_gallery_skybox.png", pages[3].skybox?.source)
        assertEquals("羽毛化石展厅", pages[3].skybox?.displayName)
        assertEquals("file:///example/cretaceous_forest_skybox.png", pages[6].skybox?.source)
        assertEquals("白垩纪森林", pages[6].skybox?.displayName)
    }

    @Test
    fun givesEachClueItsOwnBeatAndSavesAnimationForTheFinalPage() {
        val document = createDinosaurDetectiveExampleDocument(1, testAssets())
        val pages = document.pages

        assertEquals(setOf("dinosaur_copy_1", "raptor_claw"), pages[0].states.keys)
        assertEquals(setOf("dinosaur_copy_2", "trex_skull", "trex_tooth"), pages[1].states.keys)
        assertEquals(setOf("dinosaur_copy_3", "dinosaur_footprint"), pages[2].states.keys)
        assertEquals(setOf("dinosaur_copy_4", "archaeopteryx_fossil"), pages[3].states.keys)
        assertEquals(setOf("dinosaur_copy_5", "trex_ct_scan_panel", "trex_brain_endocast"), pages[4].states.keys)
        assertEquals(setOf("dinosaur_copy_6", "trex_skeleton"), pages[5].states.keys)
        assertEquals(setOf("dinosaur_copy_7", "trex_animated"), pages[6].states.keys)

        assertTrue(pages[1].states.getValue("trex_tooth").transform.scale >= 1.1f)
        assertTrue(pages[2].states.getValue("dinosaur_footprint").transform.py >= 1.0f)

        assertTrue(document.objects.getValue("trex_animated").playEmbeddedAnimation)
        assertTrue(
            document.objects
                .filterKeys { it != "trex_animated" }
                .values
                .none { it.playEmbeddedAnimation },
        )
    }

    @Test
    fun copyStaysCompactAndConnectsEvidenceToReconstruction() {
        val document = createDinosaurDetectiveExampleDocument(1, testAssets())
        val copy = document.pages.indices.map { document.objects.getValue("dinosaur_copy_${it + 1}").source }

        assertTrue(copy[0].startsWith("案件开启｜化石里藏着什么？"))
        assertTrue(copy[1].startsWith("证据 01｜牙齿"))
        assertTrue(copy[1].contains("捕食者的菜单"))
        assertTrue(copy[2].contains("动作却留在了岩石上"))
        assertTrue(copy[3].contains("羽毛轮廓"))
        assertTrue(copy[4].contains("CT"))
        assertTrue(copy[6].contains("让消失的恐龙再次迈出一步"))
        assertTrue(copy.all { it.lines().size <= 6 })
    }

    @Test
    fun dinosaurLessonPlaysAsAReadOnlyBuiltInExample() {
        val viewModel = StudioViewModel()
        val savedDocumentId = viewModel.document.id
        val savedShowCount = viewModel.showSummaries.size

        viewModel.presentDinosaurDetectiveExample(testAssets())

        assertTrue(viewModel.isBuiltInExampleSession)
        assertEquals(AppScreen.EDITOR, viewModel.screen)
        assertEquals(AppMode.PRESENT, viewModel.mode)
        assertEquals(savedShowCount, viewModel.showSummaries.size)
        assertEquals("恐龙侦探：化石里藏着什么？", viewModel.document.name)
        assertTrue(viewModel.savedState().shows.none { it.id == viewModel.document.id })

        viewModel.exitPresentMode()
        assertFalse(viewModel.isBuiltInExampleSession)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(savedDocumentId, viewModel.document.id)
    }

    private fun testAssets() = DinosaurDetectiveExampleAssets(
        modelSources = dinosaurDetectiveModelFiles.mapValues { (_, fileName) -> "file:///example/$fileName" },
        ctScanPanel = "file:///example/trex_ct_scan_panel.png",
        fossilGallerySkybox = "file:///example/fossil_gallery_skybox.png",
        cretaceousForestSkybox = "file:///example/cretaceous_forest_skybox.png",
    )
}
