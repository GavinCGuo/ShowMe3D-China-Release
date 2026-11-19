package com.example.showme3d

import com.example.showme3d.studio.examples.PictureBookExample
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.persistence.ShowFileRepository
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioViewModel
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PictureBookExampleTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aliceOpensInPresentationModeWithSixEnvironmentsAndFiveAnimatedCharacters() {
        val assets = File("src/main/assets/examples/alice")
        val book = PictureBookExample.ALICE.read(ShowFileRepository(temporary.root), temporary.newFolder("alice")) {
            File(assets, it).inputStream()
        }
        val vm = StudioViewModel()
        vm.presentPictureBook(PictureBookExample.ALICE, book)
        assertEquals(AppMode.PRESENT, vm.mode)
        assertEquals("爱丽丝梦游仙境", vm.document.name)
        assertEquals(12, vm.pageCount)
        val models = book.objects.values.filter { it.type == ObjectType.MODEL }
        assertTrue(models.all { File(URI(it.source)).isFile })
        assertEquals(setOf("alice", "rabbit", "queen", "mouse", "guard"),
            models.filter { it.playEmbeddedAnimation }.map { it.id }.toSet())
        assertEquals(6, book.pages.map { it.skybox!!.source }.distinct().size)
        for ((index, page) in book.pages.withIndex()) {
            assertEquals(page, vm.currentPage)
            assertTrue(File(URI(vm.currentPageSkybox!!.source)).isFile)
            assertTrue(page.states.getValue("${page.id}_copy").visible)
            if (index < book.pages.lastIndex) vm.nextPage()
        }
        vm.returnHome()
        assertEquals(AppScreen.HOME, vm.screen)
    }

    @Test fun aliceFinalPagesHaveRenderableInstancesAndIndependentMotions() {
        val assets = File("src/main/assets/examples/alice")
        val book = PictureBookExample.ALICE.read(ShowFileRepository(temporary.root), temporary.newFolder("alice")) {
            File(assets, it).inputStream()
        }
        // Entity.setName accepts ASCII letters, digits and underscores only.
        assertTrue(book.objects.keys.all { it.matches(Regex("[A-Za-z0-9_]+")) })
        fun idsOnPage(number: Int, filename: String) = book.pages[number - 1].states
            .filter { (id, state) -> state.visible && book.objects.getValue(id).source.endsWith("/$filename") }.keys
        val gardeners = idsOnPage(9, "guard.glb")
        assertEquals(3, gardeners.size)
        val remaining = idsOnPage(10, "guard.glb")
        assertEquals(2, remaining.size)
        assertTrue(gardeners.containsAll(remaining))
        val queen = requireNotNull(book.pages[9].states.getValue("queen").pageAnimation)
        assertEquals(2f, queen.durationSeconds, 0f)
        assertTrue(queen.start.px > queen.end.px)

        fun motions(page: Int, filename: String) = idsOnPage(page, filename).map { id ->
            requireNotNull(book.pages[page - 1].states.getValue(id).pageAnimation)
        }
        val cards = motions(11, "flying_card.glb")
        val leaves = motions(12, "fall_leaf.glb")
        assertEquals(5, cards.size)
        assertEquals(5, leaves.size)
        assertTrue(cards.all { it.loopMode == AnimationLoopMode.NONE && it.durationSeconds == 2f && it.end.py > it.start.py })
        assertTrue(cards.maxOf { it.end.px } - cards.minOf { it.end.px } > 1.4f)
        assertTrue(leaves.all { it.loopMode == AnimationLoopMode.NONE && it.durationSeconds == 4f && it.start.py > it.end.py })
    }

    @Test fun packagedBookCanBeOpenedReadToTheEndAndReopened() {
        val assets = File("src/main/assets/examples/three_pigs")
        val repository = ShowFileRepository(temporary.root)
        val directory = temporary.newFolder("story")
        val book = PictureBookExample.THREE_PIGS.read(repository, directory) { File(assets, it).inputStream() }
        val models = book.objects.values.filter { it.type == ObjectType.MODEL }
        assertEquals(27, models.map { it.source }.distinct().size)
        assertTrue(models.all { File(URI(it.source)).isFile })
        assertEquals(8, models.count { it.playEmbeddedAnimation })
        assertEquals(54, book.pages.sumOf { page -> page.states.values.count { it.pageAnimation != null } })
        val vm = StudioViewModel()
        vm.presentPictureBook(PictureBookExample.THREE_PIGS, book)
        assertEquals(AppMode.PRESENT, vm.mode)
        assertEquals(12, vm.pageCount)
        book.pages.forEachIndexed { index, page ->
            assertEquals(index, vm.currentPageIndex)
            assertEquals(page, vm.currentPage)
            assertTrue(File(URI(vm.currentPageSkybox!!.source)).isFile)
            val cards = page.states.filterValues { it.visible }.keys
                .map { book.objects.getValue(it) }.filter { it.type == ObjectType.TEXT }
            assertEquals(setOf("${page.id}_title", "${page.id}_copy"), cards.map { it.id }.toSet())
            assertTrue(cards.all { it.source.isNotBlank() && it.source.length <= 120 && it.faceViewer && it.textBackgroundEnabled })
            assertTrue(cards.all { it.textColor == 0xFFFFF2D8L })
            val title = page.states.getValue("${page.id}_title").transform
            val copy = page.states.getValue("${page.id}_copy").transform
            assertEquals(title.pz, copy.pz, 0.001f)
            assertTrue(title.py > copy.py && copy.py >= 2f)
            assertTrue(copy.scale >= 0.9f && title.scale >= copy.scale)
            if (index < book.pages.lastIndex) vm.nextPage()
        }
        vm.returnHome()
        assertEquals(AppScreen.HOME, vm.screen)
        val second = PictureBookExample.THREE_PIGS.read(repository, directory) {
            assertEquals("book.json", it)
            File(assets, it).inputStream()
        }
        assertNotEquals(book.id, second.id)
        assertEquals(book.pages.first().skybox, second.pages.last().skybox)
    }
}
