package com.example.showme3d

import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.imports.LocalAssetImporter
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.aigc.RodinGenerationOptions
import com.example.showme3d.studio.aigc.RodinGenerationTier
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.library.PresetCategory
import com.example.showme3d.library.PresetGeometry
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ObjectVersionOrigin
import com.example.showme3d.studio.model.PageAnnotationType
import com.example.showme3d.studio.model.Page
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.StrokePoint
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.PresentAnnotationTool
import com.example.showme3d.studio.state.StudioPanel
import com.example.showme3d.studio.state.StudioViewModel
import com.example.showme3d.studio.state.WhiteboardTool
import org.junit.Assert.*
import org.junit.Test

class DocumentModelTest {
    @Test
    fun pageDeepCopyCreatesIndependentObjectStates() {
        val original = Page("page_1")
        original.states["object_1"] = ObjectState(
            transform = TransformData(px = 1f, py = 2f, pz = -3f, scale = 0.75f),
            visible = true,
            autoRotateY = true,
            groupId = "group_1",
        )
        original.skybox = PageSkybox("file:///sky.jpg", "Sky", rotationY = 0.5f)

        val copied = original.deepCopy("page_2")
        copied.states.getValue("object_1").visible = false
        copied.states.getValue("object_1").transform = TransformData(px = 9f)
        copied.skybox = copied.skybox?.copy(rotationY = 1f)

        assertEquals("page_2", copied.id)
        assertTrue(original.states.getValue("object_1").visible)
        assertEquals(1f, original.states.getValue("object_1").transform.px)
        assertFalse(copied.states.getValue("object_1").visible)
        assertEquals(9f, copied.states.getValue("object_1").transform.px)
        assertTrue(copied.states.getValue("object_1").autoRotateY)
        assertEquals("group_1", copied.states.getValue("object_1").groupId)
        assertEquals(0.5f, original.skybox?.rotationY)
        assertEquals(1f, copied.skybox?.rotationY)
    }

    @Test
    fun showDocumentExposesStableSchemaAndPageLimit() {
        val document = ShowDocument(id = "show_1", name = "Demo")

        assertEquals(10, document.schemaVersion)
        assertEquals(ShowDocument.CURRENT_SCHEMA_VERSION, document.schemaVersion)
        assertEquals(DocumentType.PRESENTATION, document.documentType)
        assertEquals(30, ShowDocument.MAX_PAGES)
        assertFalse(document.anchorEnabled)
    }

    @Test
    fun studioViewModelStoresAutoRotateFaceViewerAndSkyboxAtTheirIntendedScopes() {
        val vm = StudioViewModel()
        vm.newShow(DocumentType.PRESENTATION)

        vm.addPresetObject(PresetLibrary.assets.first())
        val modelId = vm.selectedObjectId!!
        vm.toggleSelectedAutoRotateY()
        assertTrue(vm.currentPage.states.getValue(modelId).autoRotateY)

        vm.addTextObject()
        val textId = vm.selectedObjectId!!
        vm.toggleSelectedFaceViewer()
        assertTrue(vm.document.objects.getValue(textId).faceViewer)

        vm.setCurrentPageSkybox("file:///sky.jpg", "Sky")
        vm.rotateCurrentPageSkybox(0.25f)
        assertEquals(0.25f, vm.currentPageSkybox?.rotationY)
        vm.addPage()
        assertEquals("file:///sky.jpg", vm.currentPageSkybox?.source)
        vm.setCurrentPageSkybox("file:///other.jpg", "Other")
        vm.previousPage()
        assertEquals("file:///sky.jpg", vm.currentPageSkybox?.source)
    }

    @Test
    fun imageSupportsManualYawAndViewerFacingMode() {
        val vm = StudioViewModel()
        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "说明图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/instructions.png",
            ),
        )
        val imageId = vm.selectedObjectId!!

        vm.beginSelectedRotation()
        vm.previewSelectedRotation(0.35f)
        vm.commitSelectedRotation()
        assertEquals(0.35f, vm.currentTransform(imageId)?.rotationY ?: 0f, 0.0001f)

        vm.toggleSelectedFaceViewer()
        assertTrue(vm.selectedObjectFacesViewer)
        assertTrue(vm.document.objects.getValue(imageId).faceViewer)
        vm.toggleSelectedFaceViewer()
        assertFalse(vm.document.objects.getValue(imageId).faceViewer)
        assertEquals(0.35f, vm.currentTransform(imageId)?.rotationY ?: 0f, 0.0001f)
    }

    @Test
    fun studioViewModelCreatesAndSummarizesWhiteboardDocuments() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)

        assertEquals(AppScreen.EDITOR, vm.screen)
        assertEquals(DocumentType.WHITEBOARD, vm.document.documentType)
        assertTrue(vm.document.name.startsWith("Whiteboard_"))
        assertEquals(1, vm.showSummaries.size)
        assertEquals(DocumentType.WHITEBOARD, vm.showSummaries.single().documentType)
    }

    @Test
    fun emptyShowsAreDiscardedInsteadOfSaved() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.PRESENTATION)
        assertEquals(1, vm.showSummaries.size)
        assertTrue(vm.savedState().shows.isEmpty())

        vm.returnHome()
        assertEquals(AppScreen.HOME, vm.screen)
        assertTrue(vm.showSummaries.isEmpty())

        vm.newShow(DocumentType.WHITEBOARD)
        vm.addTextObject()
        vm.returnHome()

        assertEquals(1, vm.showSummaries.size)
        assertEquals(1, vm.savedState().shows.size)
        assertEquals(DocumentType.WHITEBOARD, vm.showSummaries.single().documentType)

        vm.openShowFromHome(0)
        vm.newShow(DocumentType.PRESENTATION)
        assertEquals(2, vm.showSummaries.size)
        vm.switchShow(0)

        assertEquals(1, vm.showSummaries.size)
        assertEquals(DocumentType.WHITEBOARD, vm.document.documentType)
    }

    @Test
    fun whiteboardStaysInSingleSpaceEditMode() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)

        assertTrue(vm.isWhiteboard)
        assertFalse(vm.isPresentation)
        assertEquals(1, vm.pageCount)
        assertFalse(vm.canAddPage)
        assertFalse(vm.canDeletePage)

        vm.enterPresentMode()
        assertEquals(AppMode.EDIT, vm.mode)

        vm.addPage()
        vm.duplicateCurrentPage()
        vm.deleteCurrentPage()
        vm.goToPage(0)

        assertEquals(1, vm.pageCount)
        assertEquals(0, vm.currentPageIndex)
        assertEquals(AppMode.EDIT, vm.mode)
    }

    @Test
    fun whiteboardKeepsCreationAnimationAndViewpointToolsButDisablesPresentationOnlyTools() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)

        vm.addTextObject()
        val textId = vm.selectedObjectId!!
        assertEquals(ObjectType.TEXT, vm.document.objects.getValue(textId).type)

        vm.openSelectedAnimationEditor()
        assertNotNull(vm.animationDraft)
        assertEquals(StudioPanel.ANIMATION, vm.panel)

        vm.addViewpointFromToolbar()
        val viewpointId = vm.selectedObjectId!!
        assertEquals(ObjectType.VIEWPOINT, vm.document.objects.getValue(viewpointId).type)

        vm.enterSelectedViewpoint()
        assertEquals(viewpointId, vm.viewpointModeObjectId)
        assertNotNull(vm.activeViewpointTransform)

        vm.exitViewpoint()
        assertNull(vm.viewpointModeObjectId)

        vm.openStrokeTool()
        vm.beginStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.endStrokePath()
        vm.finishStrokeDraft()

        assertTrue(vm.document.objects.values.any { it.type == ObjectType.STROKE })
        assertEquals(1, vm.pageCount)
    }

    @Test
    fun whiteboardCreatesEditableSingleSpaceComments() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)
        vm.toggleWhiteboardCommentTool()
        assertEquals(WhiteboardTool.COMMENT, vm.whiteboardTool)

        val anchor = StrokePoint(-0.1f, 1.1f, -1.4f)
        val panel = StrokePoint(0.25f, 1.35f, -1.25f)
        vm.beginWhiteboardComment(anchor)
        vm.updateAnnotationComment(panel)
        val commentId = vm.finishWhiteboardComment()!!

        assertEquals(WhiteboardTool.NONE, vm.whiteboardTool)
        assertEquals(1, vm.pageCount)
        assertEquals(1, vm.currentPage.annotations.size)
        assertEquals(PageAnnotationType.COMMENT, vm.currentPage.annotations.single().type)
        assertEquals(anchor, vm.currentPage.annotations.single().anchorPoint)
        assertEquals(panel, vm.currentPage.annotations.single().panelPoint)
        assertEquals(commentId, vm.editingAnnotationComment!!.id)

        vm.updateEditingAnnotationComment("白板想法")
        assertEquals("白板想法", vm.currentPage.annotations.single().commentText)

        vm.deleteAnnotationComment(commentId)
        assertFalse(vm.currentPageHasAnnotations)
    }

    @Test
    fun studioViewModelDuplicatesShowsAsIndependentDocuments() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val originalName = vm.document.name
        val originalObjectId = vm.document.objects.keys.single()

        vm.returnHome()
        vm.duplicateShow(0)

        assertEquals(AppScreen.HOME, vm.screen)
        assertEquals(2, vm.showSummaries.size)
        val duplicateSummary = vm.showSummaries[1]
        val duplicateName = duplicateSummary.name
        assertNotEquals(originalName, duplicateName)

        vm.openShowFromHome(1)
        val duplicateObjectId = vm.document.objects.keys.single()
        assertNotEquals(originalObjectId, duplicateObjectId)

        vm.select(duplicateObjectId)
        vm.openSelectedTextEditor()
        vm.updateSelectedText("副本文本")
        vm.openShowFromHome(0)

        assertEquals("新增文本", vm.document.objects.getValue(originalObjectId).source)
    }

    @Test
    fun studioViewModelCreatesPresetShowsWithStarterContent() {
        val vm = StudioViewModel()

        vm.createPresetShow(DocumentType.PRESENTATION)

        assertEquals(AppScreen.EDITOR, vm.screen)
        assertEquals(DocumentType.PRESENTATION, vm.document.documentType)
        assertTrue(vm.document.name.startsWith("产品展示空间_"))
        assertTrue(vm.document.objects.isNotEmpty())
        assertTrue(vm.currentPage.states.isNotEmpty())
    }

    @Test
    fun tappingSelectedObjectAgainClearsSelection() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!

        vm.handleSceneTap(objectId)

        assertNull(vm.selectedObjectId)
        assertEquals(StudioPanel.NONE, vm.panel)
    }

    @Test
    fun lockedObjectCanBeSelectedButNotEditedUntilUnlocked() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        val original = vm.currentTransform(objectId)!!
        val originalObjectCount = vm.document.objects.size

        vm.lockSelectedObject()
        vm.select(null)
        vm.handleSceneTap(objectId)

        assertEquals(objectId, vm.selectedObjectId)
        assertTrue(vm.selectedObjectLocked)

        vm.commitTransform(objectId, original.copy(px = original.px + 1f))
        vm.deleteSelectedObject()
        vm.duplicateSelectedObject()
        vm.openSelectedTextEditor()

        assertEquals(original.px, vm.currentTransform(objectId)!!.px)
        assertTrue(vm.currentPage.states.containsKey(objectId))
        assertEquals(originalObjectCount, vm.document.objects.size)
        assertEquals(StudioPanel.NONE, vm.panel)

        vm.unlockSelectedObject()
        vm.commitTransform(objectId, original.copy(px = original.px + 1f))

        assertFalse(vm.selectedObjectLocked)
        assertEquals(original.px + 1f, vm.currentTransform(objectId)!!.px)
    }

    @Test
    fun transformPreviewHandoffCommitsOnceFromOriginalBase() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        val original = vm.currentTransform(objectId)!!

        vm.beginTransformPreview(objectId, original)
        val dragged = original.copy(px = original.px + 0.42f, py = original.py + 0.08f)
        vm.previewTransform(objectId, dragged)

        assertEquals(original, vm.currentTransform(objectId))
        assertEquals(dragged, vm.activeManipulationTransform(objectId))

        vm.beginTransformPreview(objectId, dragged)
        val scaled = dragged.copy(scale = dragged.scale * 1.5f)
        vm.previewTransform(objectId, scaled)
        vm.commitTransform(objectId, scaled)

        assertEquals(scaled, vm.currentTransform(objectId))

        vm.undo()

        assertEquals(original, vm.currentTransform(objectId))
    }

    @Test
    fun generatedModelCanReplaceSelectedImageAndUndoRestoresImage() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val objectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(objectId)!!.copy(px = 0.35f, py = 1.15f, pz = -1.4f, scale = 0.72f)
        vm.commitTransform(objectId, originalTransform)

        val replaced = vm.replaceSelectedImageWithGeneratedModel(
            ImportedAsset(
                displayName = "产品模型",
                type = ObjectType.MODEL,
                source = "file:///tmp/product.glb",
            ),
        )

        assertTrue(replaced)
        assertEquals(objectId, vm.selectedObjectId)
        assertEquals(ObjectType.MODEL, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.glb", vm.document.objects.getValue(objectId).source)
        assertEquals("产品模型", vm.document.objects.getValue(objectId).displayName)
        assertEquals(originalTransform, vm.currentTransform(objectId))
        assertEquals(1, vm.document.objects.size)

        vm.undo()

        assertEquals(objectId, vm.selectedObjectId)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.png", vm.document.objects.getValue(objectId).source)
        assertEquals(originalTransform, vm.currentTransform(objectId))
    }

    @Test
    fun generatedImageCanReplaceTextObjectAndUndoRestoresText() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.updateSelectedText("一只蓝色鲸鱼")
        val originalTransform = vm.currentTransform(objectId)!!.copy(px = 0.18f, py = 1.2f, pz = -1.35f, scale = 0.8f)
        vm.commitTransform(objectId, originalTransform)

        val replaced = vm.replaceTextWithGeneratedImage(
            objectId = objectId,
            asset = ImportedAsset(
                displayName = "鲸鱼海报",
                type = ObjectType.IMAGE,
                source = "file:///tmp/whale.png",
            ),
        )

        assertTrue(replaced)
        assertEquals(objectId, vm.selectedObjectId)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/whale.png", vm.document.objects.getValue(objectId).source)
        assertEquals("鲸鱼海报", vm.document.objects.getValue(objectId).displayName)
        assertEquals(originalTransform, vm.currentTransform(objectId))
        assertEquals(1, vm.document.objects.size)
        assertEquals(2, vm.document.objects.getValue(objectId).versionHistory?.versions?.size)
        assertEquals(ObjectVersionOrigin.TEXT_TO_IMAGE, vm.document.objects.getValue(objectId).versionHistory?.versions?.last()?.origin)

        vm.undo()

        assertEquals(objectId, vm.selectedObjectId)
        assertEquals(ObjectType.TEXT, vm.document.objects.getValue(objectId).type)
        assertEquals("一只蓝色鲸鱼", vm.document.objects.getValue(objectId).source)
        assertEquals(originalTransform, vm.currentTransform(objectId))
    }

    @Test
    fun generatedImageFromImageReplacesSourceAndCanSwitchBack() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "参考图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/reference.png",
            ),
        )
        val sourceObjectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(sourceObjectId)!!.copy(px = 0.2f, py = 1.25f, pz = -1.3f, scale = 0.9f)
        vm.commitTransform(sourceObjectId, originalTransform)

        val added = vm.addImageGeneratedFromImageObject(
            sourceObjectId = sourceObjectId,
            asset = ImportedAsset(
                displayName = "改图结果",
                type = ObjectType.IMAGE,
                source = "file:///tmp/variant.png",
            ),
        )

        assertTrue(added)
        assertEquals(sourceObjectId, vm.selectedObjectId)
        assertEquals(1, vm.document.objects.size)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(sourceObjectId).type)
        assertEquals("file:///tmp/variant.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(originalTransform, vm.currentTransform(sourceObjectId))
        assertEquals(2, vm.selectedObjectVersions.size)
        assertEquals(ObjectVersionOrigin.IMAGE_TO_IMAGE, vm.document.objects.getValue(sourceObjectId).versionHistory?.versions?.last()?.origin)

        assertTrue(vm.switchSelectedObjectVersion(vm.selectedObjectVersions.first().versionId))
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(sourceObjectId).type)
        assertEquals("file:///tmp/reference.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(originalTransform, vm.currentTransform(sourceObjectId))
        assertEquals(listOf("原始版本", "AI 生成图"), vm.selectedObjectVersions.map { it.title })

        val sharedTransform = originalTransform.copy(px = -0.35f, py = 1.05f, rotationY = 0.4f, scale = 1.2f)
        vm.commitTransform(sourceObjectId, sharedTransform)
        assertTrue(vm.switchSelectedObjectVersion(vm.selectedObjectVersions.last().versionId))
        assertEquals("file:///tmp/variant.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(sharedTransform, vm.currentTransform(sourceObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(sourceObjectId))
        assertEquals("file:///tmp/reference.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(sharedTransform, vm.currentTransform(sourceObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(sourceObjectId))
        assertEquals("file:///tmp/reference.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(originalTransform, vm.currentTransform(sourceObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(sourceObjectId))
        assertEquals("file:///tmp/variant.png", vm.document.objects.getValue(sourceObjectId).source)
        assertEquals(2, vm.document.objects.getValue(sourceObjectId).versionHistory?.versions?.size)
        assertEquals(originalTransform, vm.currentTransform(sourceObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(sourceObjectId))
        assertEquals("file:///tmp/reference.png", vm.document.objects.getValue(sourceObjectId).source)
        assertNull(vm.document.objects.getValue(sourceObjectId).versionHistory)
        assertEquals(originalTransform, vm.currentTransform(sourceObjectId))
    }

    @Test
    fun generatedImageFromStrokeReplacesStrokeAndCanSwitchBack() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.openStrokeTool()
        vm.beginStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.endStrokePath()
        vm.finishStrokeDraft()
        val strokeObjectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(strokeObjectId)!!.copy(px = -0.15f, py = 1.18f, pz = -1.2f, scale = 1.1f)
        vm.commitTransform(strokeObjectId, originalTransform)

        val added = vm.addImageGeneratedFromStrokeObject(
            sourceObjectId = strokeObjectId,
            asset = ImportedAsset(
                displayName = "笔画线稿",
                type = ObjectType.IMAGE,
                source = "file:///tmp/stroke_lineart.png",
            ),
        )

        assertTrue(added)
        assertEquals(strokeObjectId, vm.selectedObjectId)
        assertEquals(1, vm.document.objects.size)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(strokeObjectId).type)
        assertEquals("file:///tmp/stroke_lineart.png", vm.document.objects.getValue(strokeObjectId).source)
        assertEquals(originalTransform, vm.currentTransform(strokeObjectId))
        assertEquals(2, vm.selectedObjectVersions.size)
        assertEquals(ObjectVersionOrigin.STROKE_TO_IMAGE, vm.document.objects.getValue(strokeObjectId).versionHistory?.versions?.last()?.origin)

        assertTrue(vm.switchSelectedObjectVersion(vm.selectedObjectVersions.first().versionId))
        assertEquals(ObjectType.STROKE, vm.document.objects.getValue(strokeObjectId).type)
        assertEquals(originalTransform, vm.currentTransform(strokeObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(strokeObjectId))
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(strokeObjectId).type)
        assertEquals(originalTransform, vm.currentTransform(strokeObjectId))

        vm.undo()

        assertEquals(1, vm.document.objects.size)
        assertTrue(vm.document.objects.containsKey(strokeObjectId))
        assertEquals(ObjectType.STROKE, vm.document.objects.getValue(strokeObjectId).type)
        assertNull(vm.document.objects.getValue(strokeObjectId).versionHistory)
        assertEquals(originalTransform, vm.currentTransform(strokeObjectId))
    }

    @Test
    fun generatedModelCanRestoreBackToSourceImage() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val objectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(objectId)!!.copy(px = -0.2f, py = 1.08f, pz = -1.25f, scale = 0.64f)
        vm.commitTransform(objectId, originalTransform)

        assertTrue(
            vm.replaceSelectedImageWithGeneratedModel(
                ImportedAsset(
                    displayName = "产品模型",
                    type = ObjectType.MODEL,
                    source = "file:///tmp/product.glb",
                ),
            ),
        )
        assertTrue(vm.selectedGeneratedModelCanRestoreImage)
        val generatedModelTransform = originalTransform.copy(px = 0.45f, scale = 1.8f)
        vm.commitTransform(objectId, generatedModelTransform)

        assertTrue(vm.restoreSelectedGeneratedModelToImage())
        assertEquals(objectId, vm.selectedObjectId)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.png", vm.document.objects.getValue(objectId).source)
        assertEquals("产品图", vm.document.objects.getValue(objectId).displayName)
        assertEquals(generatedModelTransform, vm.currentTransform(objectId))
        assertFalse(vm.selectedGeneratedModelCanRestoreImage)
        assertTrue(vm.selectedImageCanUseGeneratedModel)

        val movedImageTransform = generatedModelTransform.copy(px = 0.12f, scale = 0.52f)
        vm.commitTransform(objectId, movedImageTransform)
        assertTrue(vm.switchSelectedImageToGeneratedModel())

        assertEquals(ObjectType.MODEL, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.glb", vm.document.objects.getValue(objectId).source)
        assertEquals(movedImageTransform, vm.currentTransform(objectId))
        assertTrue(vm.selectedGeneratedModelCanRestoreImage)

        vm.undo()

        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.png", vm.document.objects.getValue(objectId).source)
        assertEquals(movedImageTransform, vm.currentTransform(objectId))
        assertTrue(vm.selectedImageCanUseGeneratedModel)
    }

    @Test
    fun preprocessedImageVersionCanBeReusedForModelWithoutDuplicatingPreprocess() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val objectId = vm.selectedObjectId!!

        assertTrue(
            vm.replaceImageWithPreprocessedVersion(
                objectId = objectId,
                asset = ImportedAsset(
                    displayName = "产品主体图",
                    type = ObjectType.IMAGE,
                    source = "file:///tmp/product_cutout.png",
                ),
                mode = AigcImagePreprocessMode.LOCAL_SUBJECT,
            )
        )
        assertEquals(AigcImagePreprocessMode.LOCAL_SUBJECT, vm.currentImageVersionPreprocessMode(objectId))
        assertEquals(2, vm.document.objects.getValue(objectId).versionHistory?.versions?.size)

        assertTrue(
            vm.replaceImageWithGeneratedModel(
                objectId = objectId,
                asset = ImportedAsset(
                    displayName = "产品模型",
                    type = ObjectType.MODEL,
                    source = "file:///tmp/product.glb",
                ),
                preprocessingAsset = ImportedAsset(
                    displayName = "产品主体图",
                    type = ObjectType.IMAGE,
                    source = "file:///tmp/product_cutout.png",
                ),
                preprocessingMode = AigcImagePreprocessMode.LOCAL_SUBJECT,
            )
        )

        val history = vm.document.objects.getValue(objectId).versionHistory!!
        assertEquals(3, history.versions.size)
        assertEquals(
            listOf(ObjectVersionOrigin.ORIGINAL, ObjectVersionOrigin.IMAGE_PREPROCESS_LOCAL_SUBJECT, ObjectVersionOrigin.IMAGE_TO_MODEL),
            history.versions.map { it.origin },
        )
        assertEquals(ObjectType.MODEL, vm.document.objects.getValue(objectId).type)
    }

    @Test
    fun branchingCurrentVersionCreatesIndependentObjectHistory() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val sourceObjectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(sourceObjectId)!!.copy(px = -0.2f, py = 1.1f, pz = -1.2f, scale = 0.8f)
        vm.commitTransform(sourceObjectId, originalTransform)
        assertTrue(
            vm.addImageGeneratedFromImageObject(
                sourceObjectId = sourceObjectId,
                asset = ImportedAsset(
                    displayName = "改图结果",
                    type = ObjectType.IMAGE,
                    source = "file:///tmp/variant.png",
                ),
            )
        )

        assertTrue(vm.branchSelectedObjectVersion())

        val branchObjectId = vm.selectedObjectId!!
        assertNotEquals(sourceObjectId, branchObjectId)
        assertEquals(2, vm.document.objects.size)
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(branchObjectId).type)
        assertEquals("file:///tmp/variant.png", vm.document.objects.getValue(branchObjectId).source)
        assertEquals(1, vm.document.objects.getValue(branchObjectId).versionHistory?.versions?.size)
        assertEquals(originalTransform.copy(px = originalTransform.px + 0.38f), vm.currentTransform(branchObjectId))
        assertEquals(2, vm.document.objects.getValue(sourceObjectId).versionHistory?.versions?.size)
    }

    @Test
    fun generatedImageModelSwitchKeepsActiveStrokeDraft() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val objectId = vm.selectedObjectId!!
        assertTrue(
            vm.replaceSelectedImageWithGeneratedModel(
                ImportedAsset(
                    displayName = "产品模型",
                    type = ObjectType.MODEL,
                    source = "file:///tmp/product.glb",
                ),
            ),
        )

        vm.openStrokeTool()
        vm.beginStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendStrokePoint(StrokePoint(0.12f, 1.18f, -1.36f))

        assertEquals(StudioPanel.STROKE, vm.panel)
        assertNotNull(vm.strokeDraft?.activePath)
        assertEquals(2, vm.strokeDraft?.activePath?.points?.size)

        assertTrue(vm.restoreGeneratedModelToImage(objectId))

        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(objectId).type)
        assertEquals(StudioPanel.STROKE, vm.panel)
        assertNotNull(vm.strokeDraft?.activePath)
        assertEquals(2, vm.strokeDraft?.activePath?.points?.size)

        assertTrue(vm.switchImageToGeneratedModel(objectId))

        assertEquals(ObjectType.MODEL, vm.document.objects.getValue(objectId).type)
        assertEquals(StudioPanel.STROKE, vm.panel)
        assertNotNull(vm.strokeDraft?.activePath)
        assertEquals(2, vm.strokeDraft?.activePath?.points?.size)
    }

    @Test
    fun objectVersionHistorySurvivesSavedStateAndSwitchesWithoutRegeneration() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val objectId = vm.selectedObjectId!!
        val originalTransform = vm.currentTransform(objectId)!!.copy(px = -0.16f, py = 1.12f, pz = -1.38f, scale = 0.7f)
        vm.commitTransform(objectId, originalTransform)
        assertTrue(
            vm.replaceSelectedImageWithGeneratedModel(
                ImportedAsset(
                    displayName = "产品模型",
                    type = ObjectType.MODEL,
                    source = "file:///tmp/product.glb",
                ),
            ),
        )
        assertTrue(vm.restoreSelectedGeneratedModelToImage())

        val restored = StudioViewModel()
        assertTrue(restored.restoreSavedState(vm.savedState()))
        restored.openShowFromHome(0)
        restored.select(objectId)

        assertEquals(ObjectType.IMAGE, restored.document.objects.getValue(objectId).type)
        assertTrue(restored.selectedImageCanUseGeneratedModel)
        assertEquals(2, restored.document.objects.getValue(objectId).versionHistory?.versions?.size)
        assertTrue(restored.switchSelectedImageToGeneratedModel())
        assertEquals(ObjectType.MODEL, restored.document.objects.getValue(objectId).type)
        assertEquals("file:///tmp/product.glb", restored.document.objects.getValue(objectId).source)
        assertEquals(originalTransform, restored.currentTransform(objectId))
    }

    @Test
    fun generatedModelReplacementRejectsLockedOrNonImageTargets() {
        val modelAsset = ImportedAsset(
            displayName = "替换模型",
            type = ObjectType.MODEL,
            source = "file:///tmp/replacement.glb",
        )
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addImportedAsset(
            ImportedAsset(
                displayName = "产品图",
                type = ObjectType.IMAGE,
                source = "file:///tmp/product.png",
            ),
        )
        val imageId = vm.selectedObjectId!!
        vm.lockSelectedObject()

        assertFalse(vm.replaceSelectedImageWithGeneratedModel(modelAsset))
        assertEquals(ObjectType.IMAGE, vm.document.objects.getValue(imageId).type)
        assertEquals("file:///tmp/product.png", vm.document.objects.getValue(imageId).source)

        vm.unlockSelectedObject()
        vm.addTextObject()
        val textId = vm.selectedObjectId!!

        assertFalse(vm.replaceSelectedImageWithGeneratedModel(modelAsset))
        assertEquals(ObjectType.TEXT, vm.document.objects.getValue(textId).type)
    }

    @Test
    fun studioViewModelDeletesFromHomeWithoutLeavingHome() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)
        vm.addTextObject()
        vm.returnHome()

        vm.deleteShow(0)

        assertEquals(AppScreen.HOME, vm.screen)
        assertEquals(StudioPanel.NONE, vm.panel)
        assertTrue(vm.showSummaries.isEmpty())
    }

    @Test
    fun studioViewModelCanDeleteAllShowsFromHome() {
        val vm = StudioViewModel()

        vm.returnHome()
        vm.deleteShow(0)

        assertEquals(AppScreen.HOME, vm.screen)
        assertEquals(StudioPanel.NONE, vm.panel)
        assertTrue(vm.showSummaries.isEmpty())
    }

    @Test
    fun studioViewModelKeepsLastShowInsideEditorFilePanel() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.deleteShow(0)

        assertEquals(AppScreen.EDITOR, vm.screen)
        assertEquals(1, vm.showSummaries.size)
    }

    @Test
    fun studioViewModelStartsAtHomeAndCanReturnFromEditor() {
        val vm = StudioViewModel()

        assertEquals(AppScreen.HOME, vm.screen)

        vm.openShowFromHome(0)
        assertEquals(AppScreen.EDITOR, vm.screen)

        vm.returnHome()
        assertEquals(AppScreen.HOME, vm.screen)
        assertEquals(StudioPanel.NONE, vm.panel)
    }

    @Test
    fun studioViewModelCreatesAndExitsViewpointMode() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addViewpointFromToolbar()
        val objectId = vm.selectedObjectId!!

        assertEquals(ObjectType.VIEWPOINT, vm.document.objects.getValue(objectId).type)
        assertEquals(objectId, vm.currentPage.states.keys.single())

        vm.enterSelectedViewpoint()

        assertEquals(objectId, vm.viewpointModeObjectId)
        assertNotNull(vm.activeViewpointTransform)
        assertNull(vm.selectedObjectId)

        vm.exitViewpoint()

        assertNull(vm.viewpointModeObjectId)
        assertNull(vm.activeViewpointTransform)
    }

    @Test
    fun presentModeTapCanEnterViewpoint() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addViewpointFromToolbar()
        val viewpointId = vm.selectedObjectId!!
        vm.enterPresentMode()

        vm.handleSceneTap(viewpointId)

        assertEquals(AppMode.PRESENT, vm.mode)
        assertEquals(viewpointId, vm.viewpointModeObjectId)
        assertNull(vm.selectedObjectId)
    }

    @Test
    fun viewpointModeTreatsViewpointsAsHiddenChrome() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addViewpointFromToolbar()
        val viewpointId = vm.selectedObjectId!!

        assertTrue(vm.isViewpointRelatedObject(viewpointId))

        vm.enterSelectedViewpoint()

        assertEquals(viewpointId, vm.viewpointModeObjectId)
        assertTrue(vm.isViewpointRelatedObject(viewpointId))
    }

    @Test
    fun selectedRotationPreviewsThenCommitsAsSingleUndoStep() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!

        vm.beginSelectedRotation()
        vm.previewSelectedRotation(0.25f)
        vm.previewSelectedRotation(0.25f)

        assertEquals(0f, vm.currentTransform(objectId)!!.rotationY)
        vm.commitSelectedRotation()
        assertEquals(0.5f, vm.currentTransform(objectId)!!.rotationY)

        vm.undo()
        assertEquals(0f, vm.currentTransform(objectId)!!.rotationY)
    }

    @Test
    fun selectedAnimationCanBeCreatedUpdatedDeletedAndUndone() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!

        vm.openSelectedAnimationEditor()
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)
        assertEquals(2, vm.animationDraftEndpoints.size)

        vm.updateSelectedAnimationLoopMode(AnimationLoopMode.RESTART)
        vm.confirmSelectedAnimation()
        assertEquals(AnimationLoopMode.RESTART, vm.currentPage.states.getValue(objectId).pageAnimation!!.loopMode)
        assertTrue(vm.currentPageHasAnimations)

        vm.deleteSelectedAnimation()
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)
        assertFalse(vm.currentPageHasAnimations)

        vm.undo()
        assertNotNull(vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    fun whiteboardSelectedAnimationCanBeCreatedAndPlaybackToggled() {
        val vm = StudioViewModel()

        vm.newShow(DocumentType.WHITEBOARD)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!

        vm.openSelectedAnimationEditor()
        assertEquals(2, vm.animationDraftEndpoints.size)

        vm.confirmSelectedAnimation()

        assertNotNull(vm.currentPage.states.getValue(objectId).pageAnimation)
        assertTrue(vm.currentPageHasAnimations)

        vm.toggleAnimationPlayback()

        assertTrue(vm.animationsPaused)
    }

    @Test
    fun studioViewModelCreatesStrokeObjectFromDraft() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.openStrokeTool()
        vm.beginStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.endStrokePath()
        vm.finishStrokeDraft()

        val objectId = vm.selectedObjectId!!
        val stroke = vm.document.objects.getValue(objectId)
        val state = vm.currentPage.states.getValue(objectId)

        assertEquals(ObjectType.STROKE, stroke.type)
        assertNotNull(stroke.strokeData)
        assertEquals(1, stroke.strokeData!!.strokes.size)
        assertEquals(0f, stroke.strokeData!!.strokes.single().points.map { it.x }.average().toFloat(), 0.0001f)
        assertEquals(0f, state.transform.rotationY)
    }

    @Test
    fun strokeGeneratedModelReplacesStrokeAndCanReturnToSeedreamImage() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.openStrokeTool()
        vm.beginStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.endStrokePath()
        vm.finishStrokeDraft()
        val objectId = vm.selectedObjectId!!
        val objectCountBefore = vm.document.objects.size
        val originalState = vm.currentPage.states.getValue(objectId).copyState()

        assertTrue(
            vm.addModelGeneratedFromStrokeImage(
                sourceObjectId = objectId,
                imageAsset = ImportedAsset(
                    displayName = "Seedream 参考图",
                    type = ObjectType.IMAGE,
                    source = "file:///tmp/stroke_seedream.png",
                ),
                modelAsset = ImportedAsset(
                    displayName = "Rodin 模型",
                    type = ObjectType.MODEL,
                    source = "file:///tmp/stroke_model.glb",
                ),
            )
        )

        val model = vm.document.objects.getValue(objectId)
        assertEquals(ObjectType.MODEL, model.type)
        assertEquals(objectId, vm.selectedObjectId)
        assertEquals("file:///tmp/stroke_model.glb", model.source)
        assertEquals(3, model.versionHistory?.versions?.size)
        assertEquals(
            listOf(ObjectVersionOrigin.ORIGINAL, ObjectVersionOrigin.STROKE_TO_IMAGE, ObjectVersionOrigin.STROKE_TO_MODEL),
            model.versionHistory?.versions?.map { it.origin },
        )
        assertEquals(originalState.transform, vm.currentPage.states.getValue(objectId).transform)
        assertEquals(objectCountBefore, vm.document.objects.size)

        assertTrue(vm.restoreGeneratedModelToImage(objectId))
        val image = vm.document.objects.getValue(objectId)
        assertEquals(ObjectType.IMAGE, image.type)
        assertEquals("file:///tmp/stroke_seedream.png", image.source)
        assertEquals(originalState.transform, vm.currentPage.states.getValue(objectId).transform)

        assertTrue(vm.switchSelectedObjectVersion(vm.selectedObjectVersions.first().versionId))
        val stroke = vm.document.objects.getValue(objectId)
        assertEquals(ObjectType.STROKE, stroke.type)
        assertNotNull(stroke.strokeData)
        assertEquals(originalState.transform, vm.currentPage.states.getValue(objectId).transform)
    }

    @Test
    fun presentationBrushCreatesPageScopedAnnotations() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.enterPresentMode()
        vm.togglePresentStrokeTool()
        vm.beginAnnotationStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendAnnotationStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.finishAnnotationStrokePath()

        assertEquals(1, vm.currentPage.annotations.size)
        assertTrue(vm.currentPageHasAnnotations)
        assertTrue(vm.currentPageHasStrokeAnnotations)
        assertTrue(vm.document.objects.none { it.value.type == ObjectType.STROKE })

        vm.duplicateCurrentPage()

        assertEquals(1, vm.currentPage.annotations.size)
        assertTrue(vm.currentPageHasStrokeAnnotations)
    }

    @Test
    fun presentationEraserDeletesTargetedStrokeAnnotation() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.enterPresentMode()
        vm.togglePresentStrokeTool()
        vm.beginAnnotationStrokePath(StrokePoint(-0.2f, 1.1f, -1.4f))
        vm.appendAnnotationStrokePoint(StrokePoint(-0.1f, 1.2f, -1.4f))
        vm.finishAnnotationStrokePath()
        vm.beginAnnotationStrokePath(StrokePoint(0.1f, 1.1f, -1.4f))
        vm.appendAnnotationStrokePoint(StrokePoint(0.2f, 1.2f, -1.4f))
        vm.finishAnnotationStrokePath()
        val targetId = vm.currentPage.annotations.first().id

        vm.togglePresentEraserTool()

        assertTrue(vm.eraseStrokeAnnotation(targetId))
        assertEquals(1, vm.currentPage.annotations.size)
        assertFalse(vm.currentPage.annotations.any { it.id == targetId })
        assertFalse(vm.eraseStrokeAnnotation(targetId))
    }

    @Test
    fun presentationAnnotationsCanBeHiddenAndDeletedFromEditor() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.enterPresentMode()
        vm.togglePresentStrokeTool()
        vm.beginAnnotationStrokePath(StrokePoint(-0.1f, 1.1f, -1.4f))
        vm.appendAnnotationStrokePoint(StrokePoint(0.1f, 1.2f, -1.4f))
        vm.finishAnnotationStrokePath()
        vm.exitPresentMode()

        assertTrue(vm.annotationsVisible)
        vm.toggleAnnotationsVisible()
        assertFalse(vm.annotationsVisible)

        vm.deleteCurrentPageStrokeAnnotations()

        assertFalse(vm.currentPageHasAnnotations)
        assertFalse(vm.currentPageHasStrokeAnnotations)
    }

    @Test
    fun presentationCommentsArePageScopedAndEditable() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.enterPresentMode()
        vm.togglePresentCommentTool()
        val anchor = StrokePoint(-0.1f, 1.1f, -1.4f)
        val panel = StrokePoint(0.25f, 1.35f, -1.25f)
        vm.beginAnnotationComment(anchor)
        vm.updateAnnotationComment(panel)

        assertEquals(anchor, vm.annotationCommentDraft!!.anchorPoint)
        assertEquals(panel, vm.annotationCommentDraft!!.currentPoint)

        val commentId = vm.finishAnnotationComment()!!

        assertEquals(1, vm.currentPage.annotations.size)
        assertEquals(PageAnnotationType.COMMENT, vm.currentPage.annotations.single().type)
        assertEquals("", vm.currentPage.annotations.single().commentText)
        assertEquals(anchor, vm.currentPage.annotations.single().anchorPoint)
        assertEquals(panel, vm.currentPage.annotations.single().panelPoint)
        assertNull(vm.annotationCommentDraft)
        assertEquals(PresentAnnotationTool.NONE, vm.presentAnnotationTool)
        assertEquals(commentId, vm.editingAnnotationComment!!.id)

        vm.updateEditingAnnotationComment("这里是重点")

        assertEquals("这里是重点", vm.currentPage.annotations.single().commentText)
        vm.updateEditingAnnotationComment("   ")
        assertEquals("   ", vm.currentPage.annotations.single().commentText)
        vm.updateEditingAnnotationComment("这里是重点")
        assertTrue(vm.currentPageHasAnnotations)
        assertFalse(vm.currentPageHasStrokeAnnotations)

        vm.closeEditingAnnotationComment()
        vm.editAnnotationComment(commentId)
        assertNull(vm.editingAnnotationComment)

        vm.exitPresentMode()
        vm.editAnnotationComment(commentId)
        assertEquals("这里是重点", vm.editingAnnotationComment!!.text)

        vm.deleteAnnotationComment(commentId)

        assertFalse(vm.currentPageHasAnnotations)
        assertNull(vm.editingAnnotationComment)
    }

    @Test
    fun presentationInspectionAllowsOnlyUnlockedModelsWithoutAnnotationTools() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addPresetObject(PresetLibrary.assets.first())
        val modelId = vm.selectedObjectId!!
        vm.addTextObject()
        val textId = vm.selectedObjectId!!

        vm.enterPresentMode()

        assertTrue(vm.canInspectModelInPresent(modelId))
        assertFalse(vm.canInspectModelInPresent(textId))

        vm.togglePresentStrokeTool()
        assertFalse(vm.canInspectModelInPresent(modelId))
        vm.closePresentAnnotationTool()
        assertTrue(vm.canInspectModelInPresent(modelId))

        vm.exitPresentMode()
        vm.select(modelId)
        vm.lockSelectedObject()
        vm.enterPresentMode()
        assertFalse(vm.canInspectModelInPresent(modelId))
    }

    @Test
    fun selectingAnimatedObjectFreezesOnlyThatObject() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        vm.confirmSelectedAnimation()
        vm.toggleAnimationPlayback()
        assertTrue(vm.animationsPaused)
        vm.toggleAnimationPlayback()
        assertFalse(vm.animationsPaused)

        vm.select(objectId, TransformData(px = 1.25f, py = 0.5f, pz = -1.5f))

        assertFalse(vm.animationsPaused)
        assertEquals(objectId, vm.animationFrozenObjectId)
        assertEquals(1.25f, vm.animationFrozenTransform!!.px)
    }

    @Test
    fun animationEndpointDraftsCommitIntoPageAnimation() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        val endpoints = vm.animationDraftEndpoints
        assertEquals(listOf("Start", "End"), endpoints.map { it.label })

        vm.select(endpoints.first().id)
        vm.commitTransform(endpoints.first().id, TransformData(px = -0.5f, scale = 0.8f))
        vm.select(endpoints.last().id)
        vm.commitTransform(endpoints.last().id, TransformData(px = 0.75f, rotationY = 0.4f))
        vm.confirmSelectedAnimation()

        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertEquals(-0.5f, animation.start.px)
        assertEquals(0.8f, animation.start.scale)
        assertEquals(0.75f, animation.end.px)
        assertEquals(0.4f, animation.end.rotationY)
        assertEquals(0.125f, vm.currentTransform(objectId)!!.px)
        assertEquals(0.2f, vm.currentTransform(objectId)!!.rotationY)
    }

    @Test
    fun closingAnimationEditorCancelsDraftWithoutAddingAnimation() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        val endpoints = vm.animationDraftEndpoints

        vm.commitTransform(endpoints.first().id, TransformData(px = -1f, py = 1f, pz = -1.4f, scale = 0.5f))
        vm.commitTransform(endpoints.last().id, TransformData(px = 1f, py = 1.4f, pz = -1.0f, rotationY = 1f, scale = 1.5f))
        vm.closePanel()

        assertNull(vm.animationDraft)
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    fun movingAnimatedObjectShiftsAnimationPathBySameDelta() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        val endpoints = vm.animationDraftEndpoints
        vm.commitTransform(
            endpoints.first().id,
            TransformData(px = -0.5f, py = 1f, pz = -1.5f, rotationY = 0.1f, scale = 0.8f),
        )
        vm.commitTransform(
            endpoints.last().id,
            TransformData(px = 0.5f, py = 1.4f, pz = -1.3f, rotationY = 0.5f, scale = 1.2f),
        )
        vm.confirmSelectedAnimation()

        vm.select(objectId, TransformData(px = 0f, py = 1.2f, pz = -1.4f, rotationY = 0.2f, scale = 1f))
        vm.commitTransform(
            objectId,
            TransformData(px = 0.2f, py = 1.1f, pz = -1.1f, rotationY = 0.4f, scale = 1.5f),
        )

        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertClose(-0.3f, animation.start.px)
        assertClose(0.9f, animation.start.py)
        assertClose(-1.2f, animation.start.pz)
        assertClose(0.3f, animation.start.rotationY)
        assertClose(1.2f, animation.start.scale)
        assertClose(0.7f, animation.end.px)
        assertClose(1.3f, animation.end.py)
        assertClose(-1f, animation.end.pz)
        assertClose(0.7f, animation.end.rotationY)
        assertClose(1.8f, animation.end.scale)

        vm.undo()

        val restored = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertClose(-0.5f, restored.start.px)
        assertClose(0.5f, restored.end.px)
    }

    @Test
    fun movingAnimatedObjectUsesSelectedVisibleFrameAsAnimationShiftBase() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        val endpoints = vm.animationDraftEndpoints
        vm.commitTransform(
            endpoints.first().id,
            TransformData(px = -1f, py = 1f, pz = -1.5f, rotationY = 0.1f, scale = 0.8f),
        )
        vm.commitTransform(
            endpoints.last().id,
            TransformData(px = 1f, py = 1.4f, pz = -1.1f, rotationY = 0.5f, scale = 1.2f),
        )
        vm.confirmSelectedAnimation()

        val selectedVisibleFrame = TransformData(px = 0.5f, py = 1.3f, pz = -1.2f, rotationY = 0.4f, scale = 1.1f)
        vm.select(objectId, selectedVisibleFrame)
        vm.beginTransformPreview(objectId, selectedVisibleFrame)
        vm.previewTransform(objectId, TransformData(px = 0.8f, py = 1.1f, pz = -1.4f, rotationY = 0.8f, scale = 2.2f))
        vm.commitTransform(objectId, TransformData(px = 0.8f, py = 1.1f, pz = -1.4f, rotationY = 0.8f, scale = 2.2f))

        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertClose(-0.7f, animation.start.px)
        assertClose(0.8f, animation.start.py)
        assertClose(-1.7f, animation.start.pz)
        assertClose(0.5f, animation.start.rotationY)
        assertClose(1.6f, animation.start.scale)
        assertClose(1.3f, animation.end.px)
        assertClose(1.2f, animation.end.py)
        assertClose(-1.3f, animation.end.pz)
        assertClose(0.9f, animation.end.rotationY)
        assertClose(2.4f, animation.end.scale)
    }

    @Test
    fun transformPreviewHandoffKeepsOriginalBaseForAnimatedObjectShift() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        val endpoints = vm.animationDraftEndpoints
        vm.commitTransform(
            endpoints.first().id,
            TransformData(px = -1f, py = 1f, pz = -1.4f, rotationY = 0.1f, scale = 0.5f),
        )
        vm.commitTransform(
            endpoints.last().id,
            TransformData(px = 1f, py = 1.4f, pz = -1.0f, rotationY = 0.5f, scale = 1.5f),
        )
        vm.confirmSelectedAnimation()

        val original = vm.currentTransform(objectId)!!
        vm.beginTransformPreview(objectId, original)
        val dragged = original.copy(px = original.px + 0.4f)
        vm.previewTransform(objectId, dragged)
        vm.beginTransformPreview(objectId, dragged)
        val scaled = dragged.copy(scale = original.scale * 2f)
        vm.previewTransform(objectId, scaled)
        vm.commitTransform(objectId, scaled)

        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertClose(-0.6f, animation.start.px)
        assertClose(1f, animation.start.py)
        assertClose(-1.4f, animation.start.pz)
        assertClose(0.1f, animation.start.rotationY)
        assertClose(1f, animation.start.scale)
        assertClose(1.4f, animation.end.px)
        assertClose(1.4f, animation.end.py)
        assertClose(-1.0f, animation.end.pz)
        assertClose(0.5f, animation.end.rotationY)
        assertClose(3f, animation.end.scale)
    }

    @Test
    fun pageSwitchClearsFrozenAnimatedSelectionAfterMovingDuplicatedPageObject() {
        val vm = StudioViewModel()

        vm.openShowFromHome(0)
        vm.addTextObject()
        val objectId = vm.selectedObjectId!!
        vm.openSelectedAnimationEditor()
        vm.confirmSelectedAnimation()
        val originalAnimation = vm.currentPage.states.getValue(objectId).pageAnimation!!

        vm.duplicateCurrentPage()
        vm.select(objectId, TransformData(px = 0.2f, py = 1f, pz = -1.2f, scale = 1f))
        assertEquals(objectId, vm.animationFrozenObjectId)

        vm.beginTransformPreview(objectId, TransformData(px = 0.2f, py = 1f, pz = -1.2f, scale = 1f))
        vm.commitTransform(objectId, TransformData(px = 0.7f, py = 1f, pz = -1.2f, scale = 1f))
        assertNotEquals(originalAnimation, vm.currentPage.states.getValue(objectId).pageAnimation)

        vm.previousPage()

        assertNull(vm.selectedObjectId)
        assertNull(vm.animationFrozenObjectId)
        assertNull(vm.animationFrozenTransform)
        assertEquals(originalAnimation, vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    fun objectDefinitionIsSeparatedFromPerPageState() {
        val document = ShowDocument(id = "show_1", name = "Demo")
        val objectId = "object_1"
        document.objects[objectId] = SceneObject(
            id = objectId,
            type = ObjectType.TEXT,
            source = "Hello",
            displayName = "Hello",
            textColor = 0xFF111111,
        )
        document.pages.add(
            Page("page_1").also {
                it.states[objectId] = ObjectState(transform = TransformData(px = -0.5f), visible = true)
            }
        )
        document.pages.add(
            Page("page_2").also {
                it.states[objectId] = ObjectState(transform = TransformData(px = 0.5f), visible = false)
            }
        )

        assertEquals(ObjectType.TEXT, document.objects.getValue(objectId).type)
        assertEquals(-0.5f, document.pages[0].states.getValue(objectId).transform.px)
        assertEquals(0.5f, document.pages[1].states.getValue(objectId).transform.px)
        assertTrue(document.pages[0].states.getValue(objectId).visible)
        assertFalse(document.pages[1].states.getValue(objectId).visible)
    }

    @Test
    fun toolbarPanelsToggleClosedWhenPressedAgain() {
        val vm = StudioViewModel()

        vm.toggleAssets()
        assertEquals(StudioPanel.ASSETS, vm.panel)
        vm.toggleAssets()
        assertEquals(StudioPanel.NONE, vm.panel)

        vm.toggleFiles()
        assertEquals(StudioPanel.FILES, vm.panel)
        vm.toggleFiles()
        assertEquals(StudioPanel.NONE, vm.panel)
    }

    @Test
    fun importBrowserRootLocationsIncludeSharedStorageAndCommonFolders() {
        assertEquals(
            listOf("全部文件", "ShowMe3DInbox", "App files", "Download", "Documents", "Pictures", "DCIM", "Movies"),
            LocalAssetImporter.rootDirectoryLabels,
        )
    }

    @Test
    fun presetLibraryIncludesPresetModelsAndAigcCategory() {
        assertEquals(
            listOf(PresetCategory.PRESET_MODEL, PresetCategory.AIGC),
            PresetLibrary.categories,
        )
        assertEquals(
            PresetGeometry.values().toList(),
            PresetLibrary.assetsFor(PresetCategory.PRESET_MODEL).mapNotNull { it.geometry },
        )
        val sceneModels = PresetLibrary.assetsFor(PresetCategory.PRESET_MODEL).filter { it.thumbnailRes != null }
        assertEquals(
            listOf("浮空岛", "花坛", "路灯", "水晶", "小鹿", "椅子", "石头", "完整的红车", "撞毁的车"),
            sceneModels.map { it.displayName },
        )
        assertTrue(sceneModels.all { it.source.startsWith("asset://preset_models/") })
        assertTrue(PresetLibrary.assetsFor(PresetCategory.AIGC).isEmpty())
    }

    @Test
    fun rodinGenerationDefaultsUseExtremeLowTwentyKOutput() {
        val options = RodinGenerationOptions()

        assertEquals(RodinGenerationTier.EXTREME_LOW, options.tier)
        assertEquals(20_000, options.qualityOverride)
        assertEquals("glb", options.geometryFileFormat)
        assertEquals("Raw", options.meshMode)
        assertEquals("Shaded", options.material)
        assertEquals("faithful", options.geometryInstructMode)
        assertTrue(RodinGenerationTier.values().map { it.apiValue }.contains("Gen-2.5-Extreme-High"))

        val advanced = RodinGenerationOptions(
            tier = RodinGenerationTier.HIGH,
            qualityOverride = 50_000,
            meshMode = "Raw",
            hdTexture = true,
            creative = true,
        )
        assertEquals(RodinGenerationTier.HIGH, advanced.tier)
        assertEquals(50_000, advanced.qualityOverride)
        assertEquals("Raw", advanced.meshMode)
        assertTrue(advanced.hdTexture)
        assertEquals("creative", advanced.geometryInstructMode)
    }

    @Test
    fun presetLibraryResolvesGeometrySources() {
        PresetGeometry.values().forEach { geometry ->
            assertEquals(
                geometry,
                PresetLibrary.geometryFromSource(PresetLibrary.GEOMETRY_SOURCE_PREFIX + geometry.sourceId),
            )
        }
        assertNull(PresetLibrary.geometryFromSource("asset://chair.glb"))
    }

    @Test
    fun geometryPaletteStartsWithDefaultGray() {
        assertEquals(0xFF9CA3AF, ObjectColors.DEFAULT_GEOMETRY_COLOR)
        assertEquals(ObjectColors.DEFAULT_GEOMETRY_COLOR, ObjectColors.GEOMETRY_PALETTE.first())
        assertTrue(ObjectColors.GEOMETRY_PALETTE.size >= 6)
    }
}

private fun assertClose(expected: Float, actual: Float) {
    assertEquals(expected, actual, 0.0001f)
}
