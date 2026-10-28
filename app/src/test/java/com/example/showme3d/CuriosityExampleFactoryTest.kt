package com.example.showme3d

import com.example.showme3d.studio.examples.CuriosityExampleAssets
import com.example.showme3d.studio.examples.HomeExamplePreset
import com.example.showme3d.studio.examples.createCuriosityExampleDocument
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.PageAnnotationType
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuriosityExampleFactoryTest {
    @Test
    fun createsIntroAndEightCompleteEditableAssemblyPagesWithoutViewpoints() {
        val document = createCuriosityExampleDocument(1, testAssets())

        assertEquals(9, document.pages.size)
        assertEquals("把好奇号装起来", document.name)
        assertTrue(document.pages.first().states.getValue("nasa_source_page").visible)
        assertTrue(document.pages.first().states.getValue("nasa_intro_copy").visible)
        assertTrue(document.objects.getValue("nasa_source_page").faceViewer)
        assertEquals(
            1.43f,
            document.pages.first().states.getValue("nasa_source_page").transform.scale,
            0.0001f,
        )
        assertTrue(document.objects.values.none { it.type == ObjectType.VIEWPOINT })
        document.pages.forEachIndexed { index, page ->
            assertEquals(document.objects.keys, page.states.keys)
            if (index > 0) assertTrue(page.annotations.isNotEmpty())
            assertTrue(page.annotations.all { it.type == PageAnnotationType.COMMENT })
            assertEquals(
                1,
                page.states.count { (id, state) ->
                    id.startsWith("curiosity_title_") && state.visible
                },
            )
        }
    }

    @Test
    fun pageTransitionsMoveTheActiveAssemblyGroupIntoPlace() {
        val pages = createCuriosityExampleDocument(1, testAssets()).pages

        assertNotEquals(
            pages[3].states.getValue("suspension_left").transform,
            pages[4].states.getValue("suspension_left").transform,
        )
        assertNotEquals(
            pages[4].states.getValue("wheel_left_front").transform,
            pages[5].states.getValue("wheel_left_front").transform,
        )
        assertNotEquals(
            pages[5].states.getValue("camera_mast").transform,
            pages[6].states.getValue("camera_mast").transform,
        )
        assertNotEquals(
            pages[6].states.getValue("robotic_arm").transform,
            pages[7].states.getValue("robotic_arm").transform,
        )
    }

    @Test
    fun uninstalledSuspensionsAreStagedClearOfTheBody() {
        val pages = createCuriosityExampleDocument(1, testAssets()).pages
        val inventoryPage = pages[2]
        val bodyX = inventoryPage.states.getValue("rover_body").transform.px
        val leftX = inventoryPage.states.getValue("suspension_left").transform.px
        val rightX = inventoryPage.states.getValue("suspension_right").transform.px

        // The sample's final compact-layout pass scales authored positions by 0.52.
        assertTrue(leftX >= bodyX + 0.5f)
        assertTrue(rightX <= bodyX - 0.5f)
        assertEquals(leftX, pages[3].states.getValue("suspension_left").transform.px)
        assertEquals(rightX, pages[3].states.getValue("suspension_right").transform.px)
    }

    @Test
    fun installedPartsShareOneAuthoredAssemblyFrame() {
        val document = createCuriosityExampleDocument(1, testAssets())
        val assembledPage = document.pages[7]
        val assemblyIds = listOf(
            "rover_body",
            "suspension_left",
            "suspension_right",
            "wheel_left_front",
            "wheel_left_middle",
            "wheel_left_rear",
            "wheel_right_front",
            "wheel_right_middle",
            "wheel_right_rear",
            "camera_mast",
            "robotic_arm",
        )
        val transforms = assemblyIds.map { assembledPage.states.getValue(it).transform }.toSet()

        assertEquals(1, transforms.size)
        assemblyIds.forEach { id ->
            assertEquals(ModelOriginMode.SHARED_ORIGIN, document.objects.getValue(id).modelOriginMode)
        }
    }

    @Test
    fun secondPageUsesBakedEmbeddedSkeletalAnimation() {
        val document = createCuriosityExampleDocument(1, testAssets())
        val page = document.pages[1]

        assertTrue(page.states.getValue("rover_animated_clean").visible)
        assertTrue(document.objects.getValue("rover_animated_clean").playEmbeddedAnimation)
        assertEquals(ModelOriginMode.SHARED_ORIGIN, document.objects.getValue("rover_animated_clean").modelOriginMode)
        assertTrue(page.states.values.none { it.pageAnimation != null })
    }

    @Test
    fun usesFriendlyTitlesAndHighContrastTextCards() {
        val document = createCuriosityExampleDocument(1, testAssets())

        assertEquals(
            listOf(
                "写在前面",
                "先认识一下",
                "确认零件",
                "第一步 · 放好车身",
                "第二步 · 接上悬挂",
                "第三步 · 装上车轮",
                "第四步 · 升起桅杆",
                "第五步 · 接上机械臂",
                "飞向火星",
            ),
            document.pages.indices.map { index ->
                document.objects.getValue("curiosity_title_${index + 1}").source
            },
        )
        document.pages.indices.forEach { index ->
            assertTrue(document.objects.getValue("curiosity_title_${index + 1}").textBackgroundEnabled)
        }
        assertTrue(document.objects.getValue("nasa_intro_copy").textBackgroundEnabled)
        assertTrue(document.objects.getValue("nasa_intro_copy").source.startsWith("这是一份 NASA 的打印模型"))
        assertTrue(document.pages[2].annotations.none { it.id == "p2_science" })
    }

    @Test
    fun wheelAnnotationsStayOnTheirOwnVisualSides() {
        val wheelPage = createCuriosityExampleDocument(1, testAssets()).pages[5]
        val right = wheelPage.annotations.single { it.id == "p5_right" }
        val left = wheelPage.annotations.single { it.id == "p5_left" }

        assertTrue(requireNotNull(right.anchorPoint).x > 0f)
        assertTrue(requireNotNull(right.panelPoint).x > 0f)
        assertTrue(requireNotNull(left.anchorPoint).x < 0f)
        assertTrue(requireNotNull(left.panelPoint).x < 0f)
    }

    @Test
    fun finalPageSwitchesToCompleteRoverAndMarsEnvironment() {
        val document = createCuriosityExampleDocument(1, testAssets())
        val finalPage = document.pages.last()

        assertTrue(finalPage.states.getValue("rover_complete_dirty").visible)
        assertFalse(finalPage.states.getValue("rover_animated_clean").visible)
        assertTrue(finalPage.states.getValue("martian_ground").visible)
        assertTrue(finalPage.states.getValue("martian_rock").visible)
        assertFalse(finalPage.states.getValue("rover_body").visible)
        assertNotNull(finalPage.skybox)
        assertEquals("file:///example/mars_panorama.jpg", finalPage.skybox?.source)
        assertEquals(ObjectType.MODEL, document.objects.getValue("rover_complete_dirty").type)
    }

    @Test
    fun compactLayoutStaysBehindTheMenuSafeZone() {
        val document = createCuriosityExampleDocument(1, testAssets())

        document.pages.forEach { page ->
            val visibleNonViewpoints = page.states.filter { (id, state) ->
                state.visible && document.objects.getValue(id).type != ObjectType.VIEWPOINT
            }
            visibleNonViewpoints
                .filterKeys { it != "martian_ground" }
                .values
                .forEach { state ->
                    assertTrue("content too close: ${state.transform}", state.transform.pz <= -1.80f)
                }
            page.annotations.forEach { annotation ->
                assertTrue((annotation.anchorPoint?.z ?: -9f) <= -1.77f)
                assertTrue((annotation.panelPoint?.z ?: -9f) <= -1.77f)
                assertTrue(kotlin.math.abs(annotation.panelPoint?.x ?: 0f) <= 0.90f)
            }
        }

        val firstPageRover = document.pages[1].states.getValue("rover_animated_clean").transform
        assertEquals(1.43f, firstPageRover.scale, 0.0001f)
        assertEquals(-2.45f, firstPageRover.pz, 0.0001f)

        val explodedPage = document.pages[2]
        val visiblePartCenters = explodedPage.states.filter { (id, state) ->
            state.visible && document.objects.getValue(id).type == ObjectType.MODEL
        }.values
        assertTrue(visiblePartCenters.maxOf { kotlin.math.abs(it.transform.px) } <= 0.77f)
    }

    @Test
    fun legacyExampleIsRestoredOnceAndKeepsItsFileIdentity() {
        val viewModel = StudioViewModel()
        viewModel.createCuriosityExampleShow(testAssets())
        val originalId = viewModel.document.id
        viewModel.document.objects.remove("nasa_source_page")
        viewModel.document.objects.remove("rover_animated_clean")
        viewModel.document.pages.clear()

        assertTrue(viewModel.upgradeLegacyCuriosityExample(testAssets()))
        assertEquals(originalId, viewModel.document.id)
        assertEquals("把好奇号装起来", viewModel.document.name)
        assertEquals(9, viewModel.document.pages.size)
        assertTrue("nasa_source_page" in viewModel.document.objects)
        assertFalse(viewModel.upgradeLegacyCuriosityExample(testAssets()))
    }

    @Test
    fun firstV6LayoutIsUpgradedForWiderSuspensionStaging() {
        val viewModel = StudioViewModel()
        viewModel.createCuriosityExampleShow(testAssets())
        viewModel.document.pages[2].states.getValue("suspension_left").transform =
            viewModel.document.pages[2].states.getValue("suspension_left").transform.copy(px = -0.37f)

        assertTrue(viewModel.upgradeLegacyCuriosityExample(testAssets()))
        assertTrue(
            kotlin.math.abs(
                viewModel.document.pages[2].states.getValue("suspension_left").transform.px,
            ) >= 0.5f &&
                viewModel.document.pages[2].states.getValue("suspension_left").transform.px > 0f,
        )
    }

    @Test
    fun legacyNumberedFileNameIsRemovedDuringUpgrade() {
        val viewModel = StudioViewModel()
        viewModel.createCuriosityExampleShow(testAssets())
        viewModel.document.name = "把好奇号装起来_1"

        assertTrue(viewModel.upgradeLegacyCuriosityExample(testAssets()))
        assertEquals("把好奇号装起来", viewModel.document.name)
    }

    @Test
    fun builtInCuriosityPlaysReadOnlyWithoutEnteringSavedShows() {
        val viewModel = StudioViewModel()
        val savedDocumentId = viewModel.document.id
        val savedShowCount = viewModel.showSummaries.size

        viewModel.presentCuriosityExample(testAssets())

        assertTrue(viewModel.isBuiltInExampleSession)
        assertEquals(AppScreen.EDITOR, viewModel.screen)
        assertEquals(AppMode.PRESENT, viewModel.mode)
        assertEquals(0, viewModel.currentPageIndex)
        assertEquals(savedShowCount, viewModel.showSummaries.size)
        assertTrue(viewModel.document.name.startsWith("把好奇号装起来"))
        assertTrue(viewModel.savedState().shows.none { it.id == viewModel.document.id })

        viewModel.nextPage()
        viewModel.exitPresentMode()

        assertFalse(viewModel.isBuiltInExampleSession)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(AppMode.EDIT, viewModel.mode)
        assertEquals(savedDocumentId, viewModel.document.id)
        assertEquals(0, viewModel.currentPageIndex)
    }

    @Test
    fun unfinishedEngineExampleUsesTheSameReadOnlyPlaybackSemantics() {
        val viewModel = StudioViewModel()
        val originalDocumentId = viewModel.document.id

        listOf(
            HomeExamplePreset.FOUR_STROKE_ENGINE to "发动机为什么会转",
        ).forEach { (preset, expectedName) ->
            viewModel.presentExamplePlaceholder(preset)

            assertTrue(viewModel.isBuiltInExampleSession)
            assertEquals(AppMode.PRESENT, viewModel.mode)
            assertEquals(expectedName, viewModel.document.name)
            assertTrue(viewModel.savedState().shows.none { it.id == viewModel.document.id })

            viewModel.exitPresentMode()
            assertEquals(AppScreen.HOME, viewModel.screen)
            assertEquals(originalDocumentId, viewModel.document.id)
        }
    }

    private fun testAssets() = CuriosityExampleAssets(
        body = "file:///example/body.glb",
        suspensionLeft = "file:///example/suspension_left.glb",
        suspensionRight = "file:///example/suspension_right.glb",
        wheelLeftFront = "file:///example/wheel_left_front.glb",
        wheelLeftMiddle = "file:///example/wheel_left_middle.glb",
        wheelLeftRear = "file:///example/wheel_left_rear.glb",
        wheelRightFront = "file:///example/wheel_right_front.glb",
        wheelRightMiddle = "file:///example/wheel_right_middle.glb",
        wheelRightRear = "file:///example/wheel_right_rear.glb",
        mast = "file:///example/mast.glb",
        roboticArm = "file:///example/robotic_arm.glb",
        completeCleanRover = "file:///example/curiosity_clean_complete.glb",
        animatedCleanRover = "file:///example/curiosity_clean_animated.glb",
        completeDirtyRover = "file:///example/curiosity_complete.glb",
        martianRock = "file:///example/martian_rock.glb",
        martianGround = "file:///example/martian_ground.glb",
        marsPanorama = "file:///example/mars_panorama.jpg",
        nasaSourcePage = "file:///example/nasa_source_page.png",
    )
}
