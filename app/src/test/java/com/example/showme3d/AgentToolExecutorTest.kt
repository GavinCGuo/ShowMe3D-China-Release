package com.example.showme3d

import com.example.showme3d.studio.agent.ToolExecutor
import com.example.showme3d.studio.agent.ToolRegistry
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.PageAnimation
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.StudioPanel
import com.example.showme3d.studio.state.StudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolExecutorTest {
    @Test
    fun registryDefinesM1ObjectAndQueryTools() {
        val names = ToolRegistry.tools.map { it.name }.toSet()

        assertTrue(names.contains("add_object"))
        assertTrue(names.contains("add_text"))
        assertTrue(names.contains("edit_text"))
        assertTrue(names.contains("set_color"))
        assertTrue(names.contains("get_transform"))
        assertTrue(names.contains("set_transform"))
        assertTrue(names.contains("move_object_relative"))
        assertTrue(names.contains("rotate_object_relative"))
        assertTrue(names.contains("scale_object_relative"))
        assertTrue(names.contains("delete_object"))
        assertTrue(names.contains("clear_current_page"))
        assertTrue(names.contains("group_all_models"))
        assertTrue(names.contains("ungroup_all"))
        assertTrue(names.contains("duplicate_object"))
        assertTrue(names.contains("add_page"))
        assertTrue(names.contains("duplicate_page"))
        assertTrue(names.contains("delete_page"))
        assertTrue(names.contains("goto_page"))
        assertTrue(names.contains("enter_present_mode"))
        assertTrue(names.contains("exit_present_mode"))
        assertTrue(names.contains("add_viewpoint"))
        assertTrue(names.contains("enter_viewpoint"))
        assertTrue(names.contains("exit_viewpoint"))
        assertTrue(names.contains("set_animation"))
        assertTrue(names.contains("delete_animation"))
        assertTrue(names.contains("toggle_animation_playback"))
        assertTrue(names.contains("list_documents"))
        assertTrue(names.contains("create_document"))
        assertTrue(names.contains("switch_document"))
        assertTrue(names.contains("rename_document"))
        assertTrue(names.contains("duplicate_document"))
        assertTrue(names.contains("delete_document"))
        assertTrue(names.contains("return_home"))
        assertTrue(names.contains("get_scene_summary"))
        assertTrue(names.contains("list_objects"))
        assertTrue(names.contains("select_object"))
        assertTrue(names.contains("undo"))
        assertTrue(names.contains("redo"))
        assertTrue(names.contains("generate_image_from_text"))
        assertTrue(names.contains("generate_image_from_image"))
        assertTrue(names.contains("generate_image_from_stroke"))
        assertTrue(names.contains("generate_model_from_text"))
        assertTrue(names.contains("generate_model_from_image"))
        assertTrue(names.contains("generate_model_from_stroke"))

        val addObject = ToolRegistry.require("add_object")
        val preset = addObject.parameters.single { it.name == "preset" }
        assertTrue(preset.required)
        assertTrue(preset.enumValues.contains("cube"))

        val setTransform = ToolRegistry.require("set_transform")
        assertNotNull(setTransform.parameters.singleOrNull { it.name == "position" })
        assertNotNull(setTransform.parameters.singleOrNull { it.name == "rotation" })
        assertNotNull(setTransform.parameters.singleOrNull { it.name == "scale" })

        val moveRelative = ToolRegistry.require("move_object_relative")
        val delta = moveRelative.parameters.single { it.name == "delta" }
        assertTrue(delta.required)
        assertTrue(delta.description.contains("Positive x moves right"))
        assertEquals(setOf("x", "y", "z"), delta.properties.map { it.name }.toSet())

        val rotateRelative = ToolRegistry.require("rotate_object_relative")
        val degrees = rotateRelative.parameters.single { it.name == "degrees" }
        assertTrue(degrees.required)
        assertTrue(degrees.description.contains("Positive degrees rotate right"))

        val scaleRelative = ToolRegistry.require("scale_object_relative")
        val scaleFactor = scaleRelative.parameters.single { it.name == "scale_factor" }
        assertTrue(scaleFactor.required)
        assertTrue(scaleFactor.description.contains("1.2 to enlarge by 20%"))

        val clearCurrentPage = ToolRegistry.require("clear_current_page")
        assertTrue(clearCurrentPage.parameters.isEmpty())
        assertTrue(clearCurrentPage.description.contains("current page"))

        val groupAllModels = ToolRegistry.require("group_all_models")
        assertTrue(groupAllModels.parameters.isEmpty())
        assertTrue(groupAllModels.description.contains("visible 3D model"))
        assertTrue(ToolRegistry.require("ungroup_all").parameters.isEmpty())

        val generateImage = ToolRegistry.require("generate_image_from_text")
        val prompt = generateImage.parameters.single { it.name == "prompt" }
        assertTrue(prompt.required)

        val gotoPage = ToolRegistry.require("goto_page")
        val pageIndex = gotoPage.parameters.single { it.name == "index" }
        assertTrue(pageIndex.required)
        assertTrue(pageIndex.description.contains("One-based"))

        val enterViewpoint = ToolRegistry.require("enter_viewpoint")
        assertFalse(enterViewpoint.parameters.single { it.name == "object_id" }.required)

        val setAnimation = ToolRegistry.require("set_animation")
        assertFalse(setAnimation.parameters.single { it.name == "object_id" }.required)
        assertEquals(listOf("linear", "ease_in", "ease_out", "ease_in_out"), setAnimation.parameters.single { it.name == "easing" }.enumValues)
        assertEquals(listOf("none", "ping_pong", "restart"), setAnimation.parameters.single { it.name == "loop_mode" }.enumValues)
        val animationStart = setAnimation.parameters.single { it.name == "start" }
        assertEquals(setOf("position", "rotation", "scale"), animationStart.properties.map { it.name }.toSet())
        assertFalse(setAnimation.description.contains("whiteboard", ignoreCase = true))
        assertFalse(setAnimation.errorDescriptions.contains("unsupported_document_type"))
        assertTrue(ToolRegistry.require("delete_animation").errorDescriptions.contains("animation_not_found"))
        val toggleAnimation = ToolRegistry.require("toggle_animation_playback")
        assertTrue(toggleAnimation.errorDescriptions.contains("animation_not_found"))
        assertFalse(toggleAnimation.errorDescriptions.contains("unsupported_document_type"))

        val createDocument = ToolRegistry.require("create_document")
        val documentType = createDocument.parameters.single { it.name == "type" }
        assertTrue(documentType.required)
        assertEquals(listOf("presentation", "whiteboard"), documentType.enumValues)

        val switchDocument = ToolRegistry.require("switch_document")
        val documentIndex = switchDocument.parameters.single { it.name == "index" }
        assertTrue(documentIndex.required)
        assertTrue(documentIndex.description.contains("One-based"))

        val renameDocument = ToolRegistry.require("rename_document")
        assertTrue(renameDocument.parameters.single { it.name == "name" }.required)

        val generateImageFromImage = ToolRegistry.require("generate_image_from_image")
        val imagePrompt = generateImageFromImage.parameters.single { it.name == "prompt" }
        val imageObjectId = generateImageFromImage.parameters.single { it.name == "object_id" }
        assertTrue(imagePrompt.required)
        assertFalse(imageObjectId.required)

        val generateImageFromStroke = ToolRegistry.require("generate_image_from_stroke")
        val strokePrompt = generateImageFromStroke.parameters.single { it.name == "prompt" }
        val strokeObjectId = generateImageFromStroke.parameters.single { it.name == "object_id" }
        assertFalse(strokePrompt.required)
        assertFalse(strokeObjectId.required)
        assertTrue(strokePrompt.description.contains("当前观察方向画出的主视图草图"))

        val generateModel = ToolRegistry.require("generate_model_from_text")
        val modelPrompt = generateModel.parameters.single { it.name == "prompt" }
        val modelTier = generateModel.parameters.single { it.name == "rodin_tier" }
        val modelFaceBudget = generateModel.parameters.single { it.name == "face_budget" }
        val modelTexture = generateModel.parameters.single { it.name == "texture" }
        val modelCreative = generateModel.parameters.single { it.name == "creative" }
        assertTrue(modelPrompt.required)
        assertFalse(modelTier.required)
        assertFalse(modelFaceBudget.required)
        assertFalse(modelTexture.required)
        assertFalse(modelCreative.required)
        assertTrue(modelTier.enumValues.contains("high"))
        assertTrue(modelFaceBudget.enumValues.contains("50k"))
        assertEquals(listOf("standard", "hd"), modelTexture.enumValues)

        val generateModelFromImage = ToolRegistry.require("generate_model_from_image")
        val modelImagePrompt = generateModelFromImage.parameters.single { it.name == "prompt" }
        val modelImageObjectId = generateModelFromImage.parameters.single { it.name == "object_id" }
        val modelImagePreprocess = generateModelFromImage.parameters.single { it.name == "preprocess" }
        val modelImageTexture = generateModelFromImage.parameters.single { it.name == "texture" }
        assertFalse(modelImagePrompt.required)
        assertFalse(modelImageObjectId.required)
        assertFalse(modelImagePreprocess.required)
        assertFalse(modelImageTexture.required)
        assertEquals(listOf("none", "local_subject"), modelImagePreprocess.enumValues)

        val generateModelFromStroke = ToolRegistry.require("generate_model_from_stroke")
        val modelStrokePrompt = generateModelFromStroke.parameters.single { it.name == "prompt" }
        val modelStrokeObjectId = generateModelFromStroke.parameters.single { it.name == "object_id" }
        val modelStrokeFaceBudget = generateModelFromStroke.parameters.single { it.name == "face_budget" }
        assertFalse(modelStrokePrompt.required)
        assertFalse(modelStrokeObjectId.required)
        assertFalse(modelStrokeFaceBudget.required)
        assertTrue(modelStrokePrompt.description.contains("当前观察方向画出的主视图草图"))
        assertTrue(generateModelFromStroke.errorDescriptions.contains("missing_seedream_rodin_key"))
        assertTrue(generateModelFromStroke.errorDescriptions.contains("invalid_generation_option"))
        assertTrue(generateModelFromStroke.errorDescriptions.contains("seedream_failed"))
        assertTrue(generateModelFromStroke.errorDescriptions.contains("generation_failed"))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun registryExportsFunctionSpecsWithJsonSchemaParameters() {
        val specs = ToolRegistry.functionSpecs()
        val setTransform = specs.single { it["name"] == "set_transform" }
        val parameters = setTransform.getValue("parameters") as Map<String, Any?>
        val properties = parameters.getValue("properties") as Map<String, Any?>
        val position = properties.getValue("position") as Map<String, Any?>
        val positionProperties = position.getValue("properties") as Map<String, Any?>
        val rotation = properties.getValue("rotation") as Map<String, Any?>
        val rotationRequired = rotation.getValue("required") as List<String>

        assertEquals("object", parameters["type"])
        assertEquals(false, parameters["additionalProperties"])
        assertEquals(listOf<String>(), parameters["required"])
        assertEquals(setOf("x", "y", "z"), positionProperties.keys)
        assertEquals(listOf("y"), rotationRequired)
    }

    @Test
    fun addTextWithInitialTextIsOneUndoableAgentAction() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val result = executor.execute("add_text", mapOf("text" to "产品标题"))

        assertTrue(result.message, result.success)
        val objectId = result.data.getValue("object_id") as String
        assertEquals(ObjectType.TEXT, vm.document.objects.getValue(objectId).type)
        assertEquals("产品标题", vm.document.objects.getValue(objectId).source)
        assertEquals(StudioPanel.NONE, vm.panel)

        vm.undo()

        assertFalse(vm.document.objects.containsKey(objectId))
        assertFalse(vm.currentPage.states.containsKey(objectId))
    }

    @Test
    fun editTextUsesSingleUndoStep() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val add = executor.execute("add_text", mapOf("text" to "旧标题"))
        val objectId = add.data.getValue("object_id") as String

        val edit = executor.execute("edit_text", mapOf("object_id" to objectId, "text" to "新标题"))

        assertTrue(edit.message, edit.success)
        assertEquals("新标题", vm.document.objects.getValue(objectId).source)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertEquals("旧标题", vm.document.objects.getValue(objectId).source)

        val redo = executor.execute("redo")

        assertTrue(redo.message, redo.success)
        assertEquals("新标题", vm.document.objects.getValue(objectId).source)
    }

    @Test
    fun objectToolsAddColorTransformDuplicateAndDelete() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String

        assertTrue(added.message, added.success)
        assertEquals(ObjectType.MODEL, vm.document.objects.getValue(objectId).type)

        val color = executor.execute("set_color", mapOf("object_id" to objectId, "color" to "red"))

        assertTrue(color.message, color.success)
        assertEquals(0xFFEF4444, vm.document.objects.getValue(objectId).textColor)

        val nextTransform = TransformData(px = 0.4f, py = 1.2f, pz = -1.8f, rotationY = 1.57f, scale = 2.5f)
        val transform = executor.execute(
            "set_transform",
            mapOf(
                "object_id" to objectId,
                "position" to mapOf("x" to nextTransform.px, "y" to nextTransform.py, "z" to nextTransform.pz),
                "rotation" to mapOf("y" to nextTransform.rotationY),
                "scale" to nextTransform.scale,
            ),
        )

        assertTrue(transform.message, transform.success)
        assertEquals(nextTransform, vm.currentTransform(objectId))

        val duplicate = executor.execute("duplicate_object", mapOf("object_id" to objectId))

        assertTrue(duplicate.message, duplicate.success)
        val duplicateId = duplicate.data.getValue("object_id") as String
        assertNotEquals(objectId, duplicateId)
        assertTrue(vm.currentPage.states.containsKey(duplicateId))

        val delete = executor.execute("delete_object", mapOf("object_id" to duplicateId))

        assertTrue(delete.message, delete.success)
        assertFalse(vm.currentPage.states.containsKey(duplicateId))
    }

    @Test
    fun clearCurrentPageRemovesOnlyCurrentPageObjectsAndIsUndoable() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val firstPageObject = executor.execute("add_object", mapOf("preset" to "cube"))
            .data
            .getValue("object_id") as String
        executor.execute("add_page")
        assertEquals(2, vm.pageCount)
        assertTrue(vm.currentPage.states.containsKey(firstPageObject))
        val secondPageOnlyObject = executor.execute("add_text", mapOf("text" to "只在第二页"))
            .data
            .getValue("object_id") as String

        val cleared = executor.execute("clear_current_page")

        assertTrue(cleared.message, cleared.success)
        assertEquals(2, cleared.data["removed_count"])
        assertTrue(vm.currentPage.states.isEmpty())
        assertTrue(vm.document.pages[0].states.containsKey(firstPageObject))
        assertTrue(vm.document.objects.containsKey(firstPageObject))
        assertFalse(vm.document.objects.containsKey(secondPageOnlyObject))

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertTrue(vm.currentPage.states.containsKey(firstPageObject))
        assertTrue(vm.currentPage.states.containsKey(secondPageOnlyObject))
        assertTrue(vm.document.objects.containsKey(secondPageOnlyObject))
    }

    @Test
    fun sceneGroupingToolsGroupVisibleModelsMoveThemTogetherAndUngroupAll() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val firstId = executor.execute("add_object", mapOf("preset" to "cube")).data.getValue("object_id") as String
        val secondId = executor.execute("add_object", mapOf("preset" to "sphere")).data.getValue("object_id") as String
        val textId = executor.execute("add_text", mapOf("text" to "不参与模型组合")).data.getValue("object_id") as String
        val firstBefore = vm.currentTransform(firstId)!!
        val secondBefore = vm.currentTransform(secondId)!!

        val grouped = executor.execute("group_all_models")

        assertTrue(grouped.message, grouped.success)
        assertEquals(2, grouped.data["grouped_count"])
        val groupId = grouped.data["group_id"] as String
        assertEquals(groupId, vm.currentPage.states.getValue(firstId).groupId)
        assertEquals(groupId, vm.currentPage.states.getValue(secondId).groupId)
        assertNull(vm.currentPage.states.getValue(textId).groupId)

        vm.select(secondId)
        assertEquals(firstId, vm.selectedObjectId)
        vm.beginTransformPreview(firstId, firstBefore)
        vm.previewTransform(firstId, firstBefore.copy(px = firstBefore.px + 0.2f))
        val previewTransforms = vm.groupTransformsFor(firstId)
        assertEquals(firstBefore.px + 0.2f, previewTransforms.getValue(firstId).px, 0.0001f)
        assertEquals(secondBefore.px + 0.2f, previewTransforms.getValue(secondId).px, 0.0001f)
        vm.cancelTransformPreview(firstId, firstBefore)

        val moved = executor.execute(
            "move_object_relative",
            mapOf("object_id" to firstId, "delta" to mapOf("x" to 0.3f)),
        )
        assertTrue(moved.message, moved.success)
        assertEquals(firstBefore.px + 0.3f, vm.currentTransform(firstId)!!.px, 0.0001f)
        assertEquals(secondBefore.px + 0.3f, vm.currentTransform(secondId)!!.px, 0.0001f)

        val ungrouped = executor.execute("ungroup_all")

        assertTrue(ungrouped.message, ungrouped.success)
        assertEquals(1, ungrouped.data["removed_group_count"])
        assertEquals(2, ungrouped.data["ungrouped_count"])
        assertNull(vm.currentPage.states.getValue(firstId).groupId)
        assertNull(vm.currentPage.states.getValue(secondId).groupId)
    }

    @Test
    fun groupAllModelsRequiresAtLeastTwoVisibleModels() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        executor.execute("add_object", mapOf("preset" to "cube"))

        val result = executor.execute("group_all_models")

        assertFalse(result.success)
        assertEquals("insufficient_models", result.errorCode)
        assertEquals(1, result.data["visible_model_count"])
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun moveObjectRelativeAppliesDeltaAndIsUndoable() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val before = vm.currentTransform(objectId)!!

        val moved = executor.execute(
            "move_object_relative",
            mapOf(
                "object_id" to objectId,
                "delta" to mapOf("x" to 0.3f, "y" to 0f, "z" to 0f),
            ),
        )

        assertTrue(moved.message, moved.success)
        val after = vm.currentTransform(objectId)!!
        assertEquals(before.px + 0.3f, after.px, 0.001f)
        assertEquals(before.py, after.py, 0.001f)
        assertEquals(before.pz, after.pz, 0.001f)
        val delta = moved.data.getValue("delta") as Map<String, Any?>
        assertEquals(0.3f, delta.getValue("x") as Float, 0.001f)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertEquals(before, vm.currentTransform(objectId))
    }

    @Test
    fun moveObjectRelativeRejectsUnsafeDeltaWithoutChangingTransform() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val before = vm.currentTransform(objectId)!!

        val moved = executor.execute(
            "move_object_relative",
            mapOf(
                "object_id" to objectId,
                "delta" to mapOf("x" to 4f),
            ),
        )

        assertFalse(moved.success)
        assertEquals("invalid_parameter", moved.errorCode)
        assertEquals(before, vm.currentTransform(objectId))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun rotateObjectRelativeAppliesDegreesAndIsUndoable() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val before = vm.currentTransform(objectId)!!

        val rotated = executor.execute(
            "rotate_object_relative",
            mapOf(
                "object_id" to objectId,
                "degrees" to 30f,
            ),
        )

        assertTrue(rotated.message, rotated.success)
        val after = vm.currentTransform(objectId)!!
        assertEquals(before.rotationY + Math.toRadians(30.0).toFloat(), after.rotationY, 0.001f)
        assertEquals(before.scale, after.scale, 0.001f)
        val radians = rotated.data.getValue("radians") as Float
        assertEquals(Math.toRadians(30.0).toFloat(), radians, 0.001f)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertEquals(before, vm.currentTransform(objectId))
    }

    @Test
    fun scaleObjectRelativeAppliesFactorAndIsUndoable() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val before = vm.currentTransform(objectId)!!

        val scaled = executor.execute(
            "scale_object_relative",
            mapOf(
                "object_id" to objectId,
                "scale_factor" to 1.2f,
            ),
        )

        assertTrue(scaled.message, scaled.success)
        val after = vm.currentTransform(objectId)!!
        assertEquals(before.scale * 1.2f, after.scale, 0.001f)
        assertEquals(before.rotationY, after.rotationY, 0.001f)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertEquals(before, vm.currentTransform(objectId))
    }

    @Test
    fun relativeRotateAndScaleRejectUnsafeValuesWithoutChangingTransform() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val before = vm.currentTransform(objectId)!!

        val rotated = executor.execute(
            "rotate_object_relative",
            mapOf(
                "object_id" to objectId,
                "degrees" to 720f,
            ),
        )
        val scaled = executor.execute(
            "scale_object_relative",
            mapOf(
                "object_id" to objectId,
                "scale_factor" to 20f,
            ),
        )

        assertFalse(rotated.success)
        assertEquals("invalid_parameter", rotated.errorCode)
        assertFalse(scaled.success)
        assertEquals("invalid_parameter", scaled.errorCode)
        assertEquals(before, vm.currentTransform(objectId))
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun queryToolsReturnSceneSummaryObjectsAndTransform() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        executor.execute("add_object", mapOf("preset" to "sphere"))
        val text = executor.execute("add_text", mapOf("text" to "说明"))
        val textId = text.data.getValue("object_id") as String

        val summary = executor.execute("get_scene_summary")

        assertTrue(summary.message, summary.success)
        assertEquals(vm.document.name, summary.data["document_name"])
        assertEquals(1, summary.data["active_document_index"])
        assertEquals(1, summary.data["document_count"])
        assertEquals("editor", summary.data["screen"])
        assertEquals("presentation", summary.data["document_type"])
        assertEquals(1, summary.data["page_index"])
        assertEquals(2, summary.data["object_count"])
        val selected = summary.data["selected_object"] as Map<String, Any?>
        assertEquals(textId, selected["object_id"])
        assertEquals("text", selected["type"])
        assertEquals(false, selected["locked"])
        assertEquals(true, selected["supports_color"])
        assertEquals("说明", selected["text"])
        assertEquals(false, selected["text_truncated"])
        assertNotNull(selected["transform"])
        assertEquals(selected["transform"], summary.data["selected_transform"])
        val capabilities = summary.data["selection_capabilities"] as Map<String, Any?>
        assertEquals(true, capabilities["can_edit_text"])
        assertEquals(false, capabilities["can_generate_model_from_this"])
        val currentPageObjects = summary.data["current_page_objects"] as List<Map<String, Any?>>
        assertEquals(2, currentPageObjects.size)
        assertEquals(false, summary.data["current_page_objects_truncated"])
        assertEquals(12, summary.data["current_page_objects_limit"])
        assertTrue(currentPageObjects.any { it["object_id"] == textId && it["selected"] == true })

        val list = executor.execute("list_objects", mapOf("type" to "text"))
        val objects = list.data.getValue("objects") as List<Map<String, Any?>>

        assertEquals(1, objects.size)
        assertEquals(textId, objects.single()["object_id"])

        val transform = executor.execute("get_transform", mapOf("object_id" to textId))
        val transformData = transform.data.getValue("transform") as Map<String, Any?>
        val position = transformData.getValue("position") as Map<String, Any?>

        assertEquals(vm.currentTransform(textId)!!.px, position.getValue("x") as Float, 0.001f)
    }

    @Test
    fun pageAndPresentToolsModifyPresentationState() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val addPage = executor.execute("add_page")

        assertTrue(addPage.message, addPage.success)
        assertEquals(2, vm.pageCount)
        assertEquals(1, vm.currentPageIndex)
        assertEquals(2, addPage.data["page_index"])
        assertEquals(2, addPage.data["page_count"])

        val duplicatePage = executor.execute("duplicate_page")

        assertTrue(duplicatePage.message, duplicatePage.success)
        assertEquals(3, vm.pageCount)
        assertEquals(2, vm.currentPageIndex)

        val gotoPage = executor.execute("goto_page", mapOf("index" to 1))

        assertTrue(gotoPage.message, gotoPage.success)
        assertEquals(0, vm.currentPageIndex)
        assertEquals(1, gotoPage.data["page_index"])
        assertEquals(3, gotoPage.data["page_count"])

        val deletePage = executor.execute("delete_page")

        assertTrue(deletePage.message, deletePage.success)
        assertEquals(2, vm.pageCount)
        assertEquals(0, vm.currentPageIndex)

        val enterPresent = executor.execute("enter_present_mode")

        assertTrue(enterPresent.message, enterPresent.success)
        assertEquals(AppMode.PRESENT, vm.mode)
        assertEquals("present", enterPresent.data["mode"])

        val exitPresent = executor.execute("exit_present_mode")

        assertTrue(exitPresent.message, exitPresent.success)
        assertEquals(AppMode.EDIT, vm.mode)
        assertEquals("edit", exitPresent.data["mode"])
    }

    @Test
    fun viewpointToolsAddEnterAndExitViewpoint() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val addViewpoint = executor.execute("add_viewpoint")

        assertTrue(addViewpoint.message, addViewpoint.success)
        val viewpointId = addViewpoint.data.getValue("object_id") as String
        assertEquals(ObjectType.VIEWPOINT, vm.document.objects.getValue(viewpointId).type)
        assertEquals(viewpointId, vm.selectedObjectId)

        val enterViewpoint = executor.execute("enter_viewpoint")

        assertTrue(enterViewpoint.message, enterViewpoint.success)
        assertEquals(viewpointId, vm.viewpointModeObjectId)
        assertNull(vm.selectedObjectId)

        val exitViewpoint = executor.execute("exit_viewpoint")

        assertTrue(exitViewpoint.message, exitViewpoint.success)
        assertNull(vm.viewpointModeObjectId)
        assertEquals(viewpointId, exitViewpoint.data["viewpoint_object_id"])
    }

    @Test
    fun pageAndPresentToolsRejectWhiteboardWhileViewpointToolsRemainAvailable() {
        val vm = StudioViewModel()
        vm.newShow(DocumentType.WHITEBOARD)
        val executor = ToolExecutor(vm)

        val addPage = executor.execute("add_page")
        val present = executor.execute("enter_present_mode")
        val viewpoint = executor.execute("add_viewpoint")

        assertFalse(addPage.success)
        assertEquals("unsupported_document_type", addPage.errorCode)
        assertFalse(present.success)
        assertEquals("unsupported_document_type", present.errorCode)
        assertTrue(viewpoint.message, viewpoint.success)
        val viewpointId = viewpoint.data.getValue("object_id") as String
        assertEquals(ObjectType.VIEWPOINT, vm.document.objects.getValue(viewpointId).type)

        val entered = executor.execute("enter_viewpoint")
        assertTrue(entered.message, entered.success)
        assertEquals(viewpointId, vm.viewpointModeObjectId)
        assertEquals(1, vm.pageCount)
        assertEquals(AppMode.EDIT, vm.mode)
        assertEquals(1, vm.currentPage.states.size)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun documentToolsManageShowsAndHomeScreen() {
        val vm = openEditor()
        vm.addTextObject()
        val executor = ToolExecutor(vm)

        val listInitial = executor.execute("list_documents")

        assertTrue(listInitial.message, listInitial.success)
        assertEquals(1, listInitial.data["document_count"])
        assertEquals(1, listInitial.data["active_document_index"])
        val initialDocuments = listInitial.data.getValue("documents") as List<Map<String, Any?>>
        assertEquals(1, initialDocuments.size)
        assertEquals(1, initialDocuments.single()["document_index"])
        assertEquals("presentation", initialDocuments.single()["document_type"])

        val createWhiteboard = executor.execute("create_document", mapOf("type" to "whiteboard"))

        assertTrue(createWhiteboard.message, createWhiteboard.success)
        assertEquals(2, createWhiteboard.data["document_count"])
        assertEquals(2, createWhiteboard.data["active_document_index"])
        assertEquals(DocumentType.WHITEBOARD, vm.document.documentType)
        assertEquals(AppScreen.EDITOR, vm.screen)

        val switchFirst = executor.execute("switch_document", mapOf("index" to 1))

        assertTrue(switchFirst.message, switchFirst.success)
        assertEquals(0, vm.activeShowIndex)
        assertEquals(DocumentType.PRESENTATION, vm.document.documentType)
        assertEquals(AppScreen.EDITOR, vm.screen)
        assertEquals(1, vm.showSummaries.size)

        val rename = executor.execute("rename_document", mapOf("index" to 1, "name" to "Agent 测试作品"))

        assertTrue(rename.message, rename.success)
        assertEquals("Agent 测试作品", vm.showSummaries[0].name)
        assertEquals("Agent 测试作品", rename.data["document_name"])

        val duplicate = executor.execute("duplicate_document", mapOf("index" to 1))

        assertTrue(duplicate.message, duplicate.success)
        assertEquals(2, vm.showSummaries.size)
        assertEquals(2, duplicate.data["document_index"])
        assertEquals(1, duplicate.data["source_document_index"])
        assertTrue(vm.showSummaries[1].name.contains("副本"))

        val delete = executor.execute("delete_document", mapOf("index" to 2))

        assertTrue(delete.message, delete.success)
        assertEquals(1, vm.showSummaries.size)
        assertEquals(1, delete.data["document_count"])
        assertEquals("Agent 测试作品_副本", delete.data["removed_document_name"])

        val home = executor.execute("return_home")

        assertTrue(home.message, home.success)
        assertEquals(AppScreen.HOME, vm.screen)
        assertEquals("home", home.data["screen"])

        val openFromHome = executor.execute("switch_document", mapOf("index" to 1))

        assertTrue(openFromHome.message, openFromHome.success)
        assertEquals(AppScreen.EDITOR, vm.screen)
        assertEquals(0, vm.activeShowIndex)
    }

    @Test
    fun documentToolsRejectInvalidOrUnsafeRequests() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val invalidType = executor.execute("create_document", mapOf("type" to "spreadsheet"))
        val invalidIndex = executor.execute("switch_document", mapOf("index" to 2))
        val blankName = executor.execute("rename_document", mapOf("index" to 1, "name" to "   "))
        val deleteLast = executor.execute("delete_document", mapOf("index" to 1))

        assertFalse(invalidType.success)
        assertEquals("invalid_document_type", invalidType.errorCode)
        assertFalse(invalidIndex.success)
        assertEquals("invalid_document_index", invalidIndex.errorCode)
        assertFalse(blankName.success)
        assertEquals("document_name_empty", blankName.errorCode)
        assertFalse(deleteLast.success)
        assertEquals("cannot_delete_last_document", deleteLast.errorCode)
        assertEquals(1, vm.showSummaries.size)
        assertEquals(AppScreen.EDITOR, vm.screen)
    }

    @Test
    fun setAnimationCreatesDefaultAnimationAndIsUndoable() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_text", mapOf("text" to "动效对象"))
        val objectId = added.data.getValue("object_id") as String

        val result = executor.execute("set_animation")

        assertTrue(result.message, result.success)
        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertEquals(PageAnimation.DEFAULT_DURATION_SECONDS, animation.durationSeconds, 0.001f)
        assertEquals(AnimationEasing.EASE_IN_OUT, animation.easing)
        assertEquals(AnimationLoopMode.PING_PONG, animation.loopMode)
        assertEquals(PageAnimation.DEFAULT_DURATION_SECONDS, result.data["duration_seconds"] as Float, 0.001f)
        assertEquals("ease_in_out", result.data["easing"])
        assertEquals("ping_pong", result.data["loop_mode"])
        assertTrue(vm.currentPageHasAnimations)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun setAnimationAppliesCustomEndpointsDurationEasingAndLoop() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String

        val result = executor.execute(
            "set_animation",
            mapOf(
                "object_id" to objectId,
                "start" to mapOf(
                    "position" to mapOf("x" to -0.4f, "y" to 1.0f, "z" to -1.4f),
                    "rotation" to mapOf("y" to 0.1f),
                    "scale" to 0.8f,
                ),
                "end" to mapOf(
                    "position" to mapOf("x" to 0.6f, "y" to 1.2f, "z" to -1.2f),
                    "rotation" to mapOf("y" to 0.5f),
                    "scale" to 1.4f,
                ),
                "duration_seconds" to 3f,
                "easing" to "linear",
                "loop_mode" to "restart",
            ),
        )

        assertTrue(result.message, result.success)
        val animation = vm.currentPage.states.getValue(objectId).pageAnimation!!
        assertEquals(-0.4f, animation.start.px, 0.001f)
        assertEquals(1.0f, animation.start.py, 0.001f)
        assertEquals(-1.4f, animation.start.pz, 0.001f)
        assertEquals(0.1f, animation.start.rotationY, 0.001f)
        assertEquals(0.8f, animation.start.scale, 0.001f)
        assertEquals(0.6f, animation.end.px, 0.001f)
        assertEquals(1.2f, animation.end.py, 0.001f)
        assertEquals(-1.2f, animation.end.pz, 0.001f)
        assertEquals(0.5f, animation.end.rotationY, 0.001f)
        assertEquals(1.4f, animation.end.scale, 0.001f)
        assertEquals(3f, animation.durationSeconds, 0.001f)
        assertEquals(AnimationEasing.LINEAR, animation.easing)
        assertEquals(AnimationLoopMode.RESTART, animation.loopMode)
        assertEquals(0.1f, vm.currentTransform(objectId)!!.px, 0.001f)
        assertEquals(1.1f, vm.currentTransform(objectId)!!.py, 0.001f)
        assertEquals(-1.3f, vm.currentTransform(objectId)!!.pz, 0.001f)
        val start = result.data.getValue("start") as Map<String, Any?>
        val startPosition = start.getValue("position") as Map<String, Any?>
        assertEquals(-0.4f, startPosition.getValue("x") as Float, 0.001f)
    }

    @Test
    fun deleteAnimationRemovesAnimationAndUndoRestoresIt() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        executor.execute("set_animation", mapOf("object_id" to objectId))

        val deleted = executor.execute("delete_animation", mapOf("object_id" to objectId))

        assertTrue(deleted.message, deleted.success)
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)

        val undo = executor.execute("undo")

        assertTrue(undo.message, undo.success)
        assertNotNull(vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    fun toggleAnimationPlaybackRequiresAnimationAndToggles() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val missing = executor.execute("toggle_animation_playback")

        assertFalse(missing.success)
        assertEquals("animation_not_found", missing.errorCode)

        executor.execute("add_object", mapOf("preset" to "cube"))
        executor.execute("set_animation")
        val pause = executor.execute("toggle_animation_playback")

        assertTrue(pause.message, pause.success)
        assertTrue(vm.animationsPaused)
        assertEquals(true, pause.data["animations_paused"])

        val resume = executor.execute("toggle_animation_playback")

        assertTrue(resume.message, resume.success)
        assertFalse(vm.animationsPaused)
        assertEquals(false, resume.data["animations_paused"])
    }

    @Test
    fun animationToolsRejectUnsafeTargetsAndParameters() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String

        val invalidDuration = executor.execute("set_animation", mapOf("object_id" to objectId, "duration_seconds" to 20f))
        val invalidEasing = executor.execute("set_animation", mapOf("object_id" to objectId, "easing" to "bounce"))
        val invalidLoopMode = executor.execute("set_animation", mapOf("object_id" to objectId, "loop_mode" to "forever"))
        val invalidEndpoint = executor.execute(
            "set_animation",
            mapOf(
                "object_id" to objectId,
                "start" to mapOf("position" to mapOf("x" to 11f)),
            ),
        )

        assertFalse(invalidDuration.success)
        assertEquals("invalid_parameter", invalidDuration.errorCode)
        assertFalse(invalidEasing.success)
        assertEquals("invalid_animation_easing", invalidEasing.errorCode)
        assertFalse(invalidLoopMode.success)
        assertEquals("invalid_animation_loop_mode", invalidLoopMode.errorCode)
        assertFalse(invalidEndpoint.success)
        assertEquals("invalid_parameter", invalidEndpoint.errorCode)
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)

        val viewpoint = executor.execute("add_viewpoint")
        val viewpointId = viewpoint.data.getValue("object_id") as String
        val viewpointAnimation = executor.execute("set_animation", mapOf("object_id" to viewpointId))

        assertFalse(viewpointAnimation.success)
        assertEquals("invalid_object_type", viewpointAnimation.errorCode)

        vm.enterPresentMode()
        val notEdit = executor.execute("set_animation", mapOf("object_id" to objectId))

        assertFalse(notEdit.success)
        assertEquals("not_in_edit_mode", notEdit.errorCode)
    }

    @Test
    fun animationToolsSupportWhiteboardDocuments() {
        val vm = StudioViewModel()
        vm.newShow(DocumentType.WHITEBOARD)
        val executor = ToolExecutor(vm)
        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String

        val set = executor.execute(
            "set_animation",
            mapOf("object_id" to objectId, "duration_seconds" to 2f, "loop_mode" to "restart"),
        )

        assertTrue(set.message, set.success)
        assertNotNull(vm.currentPage.states.getValue(objectId).pageAnimation)
        assertTrue(vm.currentPageHasAnimations)

        val pause = executor.execute("toggle_animation_playback")

        assertTrue(pause.message, pause.success)
        assertTrue(vm.animationsPaused)

        val deleted = executor.execute("delete_animation", mapOf("object_id" to objectId))

        assertTrue(deleted.message, deleted.success)
        assertNull(vm.currentPage.states.getValue(objectId).pageAnimation)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun sceneSummaryLimitsCurrentPageObjectList() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)
        repeat(13) {
            executor.execute("add_object", mapOf("preset" to "cube"))
        }

        val summary = executor.execute("get_scene_summary")

        assertTrue(summary.message, summary.success)
        assertEquals(13, summary.data["object_count"])
        assertEquals(true, summary.data["current_page_objects_truncated"])
        assertEquals(12, summary.data["current_page_objects_limit"])
        val currentPageObjects = summary.data["current_page_objects"] as List<Map<String, Any?>>
        assertEquals(12, currentPageObjects.size)
        assertTrue(currentPageObjects.all { it.containsKey("transform") })
    }

    @Test
    fun executorReturnsStructuredErrorsForUnsafeCalls() {
        val vm = openEditor()
        val executor = ToolExecutor(vm)

        val missingSelection = executor.execute("edit_text", mapOf("text" to "无目标"))

        assertFalse(missingSelection.success)
        assertEquals("no_current_selection", missingSelection.errorCode)

        val invalidPreset = executor.execute("add_object", mapOf("preset" to "spaceship"))

        assertFalse(invalidPreset.success)
        assertEquals("invalid_preset", invalidPreset.errorCode)

        val added = executor.execute("add_object", mapOf("preset" to "cube"))
        val objectId = added.data.getValue("object_id") as String
        val invalidRotation = executor.execute(
            "set_transform",
            mapOf(
                "object_id" to objectId,
                "rotation" to mapOf("x" to 0.2f, "y" to 0f, "z" to 0f),
            ),
        )

        assertFalse(invalidRotation.success)
        assertEquals("unsupported_rotation_axis", invalidRotation.errorCode)

        vm.select(null)
        assertNull(vm.selectedObjectId)
    }

    private fun openEditor(): StudioViewModel =
        StudioViewModel().also { it.openShowFromHome(0) }
}
