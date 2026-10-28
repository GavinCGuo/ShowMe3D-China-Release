package com.example.showme3d

import com.example.showme3d.studio.examples.ImmersiveInteriorExampleAssets
import com.example.showme3d.studio.examples.createImmersiveInteriorExampleDocument
import com.example.showme3d.studio.examples.immersiveInteriorDetailIds
import com.example.showme3d.studio.examples.immersiveInteriorFurnitureIds
import com.example.showme3d.studio.examples.immersiveInteriorModelFiles
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveInteriorExampleFactoryTest {
    @Test
    fun followsTheSevenPageArrangementSequenceWithStandaloneCopy() {
        val document = createImmersiveInteriorExampleDocument(1, testAssets())
        val pages = document.pages

        assertEquals(7, pages.size)
        assertTrue(pages[0].states.getValue("interior_concept_cover").visible)
        assertEquals(7, document.objects.values.count { it.type == ObjectType.TEXT })
        pages.forEachIndexed { index, page ->
            val copy = document.objects.getValue("interior_copy_${index + 1}")
            assertTrue(page.states.getValue(copy.id).visible)
            assertTrue(copy.faceViewer)
            assertTrue(copy.isLocked)
            assertTrue(copy.textBackgroundEnabled)
        }
        assertTrue(pages.all { it.annotations.isEmpty() })

        assertTrue(pages[1].states.getValue("interior_shell").visible)
        assertTrue(immersiveInteriorFurnitureIds.none { pages[1].states.getValue(it).visible })
        assertTrue(immersiveInteriorDetailIds.none { pages[1].states.getValue(it).visible })

        assertTrue(immersiveInteriorFurnitureIds.all { pages[2].states.getValue(it).visible })
        assertTrue(immersiveInteriorDetailIds.none { pages[2].states.getValue(it).visible })
        assertTrue(immersiveInteriorFurnitureIds.all { pages[3].states.getValue(it).visible })

        assertTrue(immersiveInteriorDetailIds.all { pages[4].states.getValue(it).visible })
        assertTrue(immersiveInteriorDetailIds.all { pages[5].states.getValue(it).visible })
        assertTrue(pages[6].states.getValue("viewpoint_room").visible)
        pages.forEach { page -> assertEquals(document.objects.keys, page.states.keys) }
    }

    @Test
    fun usesOneObjectPerFurnitureOrDetailExceptTheCeilingLightRow() {
        val document = createImmersiveInteriorExampleDocument(1, testAssets())

        assertEquals(13, immersiveInteriorFurnitureIds.size)
        assertEquals(16, immersiveInteriorDetailIds.size)
        assertTrue("detail_ceiling_lights" in immersiveInteriorDetailIds)
        assertTrue("detail_artwork" in immersiveInteriorDetailIds)
        assertEquals(
            30,
            document.objects.values.count { it.type == ObjectType.MODEL },
        )
        assertEquals(
            30,
            document.objects.values.filter { it.type == ObjectType.MODEL }.map { it.source }.toSet().size,
        )
    }

    @Test
    fun previewsAreEnlargedThenEveryPieceReturnsToTheExactSharedFrame() {
        val document = createImmersiveInteriorExampleDocument(1, testAssets())
        val furnitureStage = document.pages[2]
        val furniturePlaced = document.pages[3]
        val detailsStage = document.pages[4]
        val detailsPlaced = document.pages[5]

        immersiveInteriorFurnitureIds.forEach { id ->
            assertNotEquals(furnitureStage.states.getValue(id).transform, furniturePlaced.states.getValue(id).transform)
        }
        immersiveInteriorDetailIds.forEach { id ->
            assertNotEquals(detailsStage.states.getValue(id).transform, detailsPlaced.states.getValue(id).transform)
        }
        val placedIds = immersiveInteriorFurnitureIds + immersiveInteriorDetailIds + "interior_shell"
        assertEquals(1, placedIds.map { document.pages[5].states.getValue(it).transform }.toSet().size)

        val smallDetailIds = immersiveInteriorDetailIds - "detail_ceiling_lights"
        assertTrue(smallDetailIds.all { detailsStage.states.getValue(it).transform.scale > 2.5f })
    }

    @Test
    fun roomUsesTheDeviceCalibratedTransformAndAllPartsStayMovable() {
        val document = createImmersiveInteriorExampleDocument(1, testAssets())
        val modelIds = immersiveInteriorFurnitureIds + immersiveInteriorDetailIds + "interior_shell"

        modelIds.forEach { id ->
            val obj = document.objects.getValue(id)
            assertEquals(id == "interior_shell", obj.isLocked)
            assertEquals(ModelOriginMode.SHARED_ORIGIN, obj.modelOriginMode)
        }
        val room = document.pages[1].states.getValue("interior_shell").transform
        assertEquals(0.10604139f, room.px, 0.000001f)
        assertEquals(0.71439004f, room.py, 0.000001f)
        assertEquals(-2.5826592f, room.pz, 0.000001f)
        assertEquals(-1.3380868f, room.rotationY, 0.000001f)
        assertEquals(2.7302701f, room.scale, 0.000001f)
    }

    @Test
    fun keepsTheTwoExteriorEnvironmentStages() {
        val pages = createImmersiveInteriorExampleDocument(1, testAssets()).pages

        assertEquals("file:///example/interior_sketch_skybox.png", pages[3].skybox?.source)
        assertEquals(pages[3].skybox, pages[4].skybox)
        assertEquals("file:///example/interior_refined_skybox.png", pages[5].skybox?.source)
        assertEquals(pages[5].skybox, pages[6].skybox)
    }

    @Test
    fun usesTheApprovedSevenPageAestheticLessonCopy() {
        val document = createImmersiveInteriorExampleDocument(1, testAssets())
        val copy = document.pages.indices.map { index ->
            document.objects.getValue("interior_copy_${index + 1}").source
        }

        assertTrue(copy[0].startsWith("房间装饰的设计美学\n\n"))
        assertTrue(copy[1].startsWith("从空间比例开始\n\n"))
        assertTrue(copy[2].startsWith("选择合适的主家具\n\n"))
        assertTrue(copy[3].startsWith("建立清晰的视觉层次\n\n"))
        assertTrue(copy[4].startsWith("用小物确定风格\n\n"))
        assertTrue(copy[5].startsWith("让颜色与材质彼此呼应\n\n"))
        assertTrue(copy[6].startsWith("回到真实的观看视角\n\n"))
        assertTrue(copy[6].contains("\n\n点击白色的人物\n"))
        assertTrue(document.pages.all { it.annotations.isEmpty() })

        val nonCoverCopyTransforms = (1..6).map { index ->
            document.pages[index].states.getValue("interior_copy_${index + 1}").transform
        }
        assertEquals(1, nonCoverCopyTransforms.toSet().size)
        assertEquals(-0.95f, nonCoverCopyTransforms.first().px, 0.0001f)
        assertEquals(1.78f, nonCoverCopyTransforms.first().py, 0.0001f)
        assertTrue(nonCoverCopyTransforms.first().py < 2.0f)
    }

    @Test
    fun usesTheDeviceCalibratedFinalViewpoint() {
        val viewpoint = createImmersiveInteriorExampleDocument(1, testAssets())
            .pages[6]
            .states
            .getValue("viewpoint_room")
            .transform

        assertEquals(-0.07399597f, viewpoint.px, 0.000001f)
        assertEquals(0.93245715f, viewpoint.py, 0.000001f)
        assertEquals(-1.9191768f, viewpoint.pz, 0.000001f)
        assertEquals(3.5633793f, viewpoint.rotationY, 0.000001f)
        assertEquals(1.2986841f, viewpoint.scale, 0.000001f)
    }

    @Test
    fun returnsToTheReadOnlyExampleAfterViewpointCalibration() {
        val viewModel = StudioViewModel()
        val originalShowCount = viewModel.showSummaries.size

        viewModel.presentImmersiveInteriorExample(testAssets())

        assertTrue(viewModel.isBuiltInExampleSession)
        assertEquals(AppScreen.EDITOR, viewModel.screen)
        assertEquals(AppMode.PRESENT, viewModel.mode)
        assertEquals(0, viewModel.currentPageIndex)
        assertEquals("布置一间手绘小屋", viewModel.document.name)
        assertEquals(originalShowCount, viewModel.showSummaries.size)
    }

    private fun testAssets() = ImmersiveInteriorExampleAssets(
        modelSources = immersiveInteriorModelFiles.mapValues { (_, fileName) -> "file:///example/$fileName" },
        conceptCover = "file:///example/cover_immersive_interior_cutout.png",
        sketchSkybox = "file:///example/interior_sketch_skybox.png",
        refinedSkybox = "file:///example/interior_refined_skybox.png",
    )
}
