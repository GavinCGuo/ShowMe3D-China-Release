package com.example.showme3d.studio.agent

import com.example.showme3d.library.PresetAsset
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.PageAnimation
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.ShowSummary
import com.example.showme3d.studio.state.StudioViewModel
import java.util.Locale
import kotlin.math.abs

class ToolExecutor(
    private val viewModel: StudioViewModel,
    private val registry: ToolRegistry = ToolRegistry,
) : AgentTransactionController {
    override fun shouldStartTransaction(toolCalls: List<DoubaoToolCall>): Boolean {
        if (toolCalls.any { it.name in historyToolNames }) return false
        return toolCalls.any { call ->
            registry.find(call.name) != null && call.name !in nonTransactionalToolNames
        }
    }

    override fun beginTransaction() {
        viewModel.beginAgentEditBatch()
    }

    override fun commitTransaction(): Boolean =
        viewModel.commitAgentEditBatch()

    override fun rollbackTransaction() {
        viewModel.rollbackAgentEditBatch()
    }

    fun execute(toolName: String, arguments: Map<String, Any?> = emptyMap()): AgentToolResult {
        if (registry.find(toolName) == null) {
            return failure(toolName, "unknown_tool", "Unknown tool: $toolName")
        }
        lastFailure = failure(toolName, "unknown_error", "Unknown error.")
        return when (toolName) {
            "add_object" -> addObject(toolName, arguments)
            "add_text" -> addText(toolName, arguments)
            "edit_text" -> editText(toolName, arguments)
            "set_color" -> setColor(toolName, arguments)
            "get_transform" -> getTransform(toolName, arguments)
            "set_transform" -> setTransform(toolName, arguments)
            "move_object_relative" -> moveObjectRelative(toolName, arguments)
            "rotate_object_relative" -> rotateObjectRelative(toolName, arguments)
            "scale_object_relative" -> scaleObjectRelative(toolName, arguments)
            "delete_object" -> deleteObject(toolName, arguments)
            "clear_current_page" -> clearCurrentPage(toolName)
            "group_all_models" -> groupAllModels(toolName)
            "ungroup_all" -> ungroupAll(toolName)
            "duplicate_object" -> duplicateObject(toolName, arguments)
            "add_page" -> addPage(toolName)
            "duplicate_page" -> duplicatePage(toolName)
            "delete_page" -> deletePage(toolName)
            "goto_page" -> gotoPage(toolName, arguments)
            "enter_present_mode" -> enterPresentMode(toolName)
            "exit_present_mode" -> exitPresentMode(toolName)
            "add_viewpoint" -> addViewpoint(toolName)
            "enter_viewpoint" -> enterViewpoint(toolName, arguments)
            "exit_viewpoint" -> exitViewpoint(toolName)
            "set_animation" -> setAnimation(toolName, arguments)
            "delete_animation" -> deleteAnimation(toolName, arguments)
            "toggle_animation_playback" -> toggleAnimationPlayback(toolName)
            "list_documents" -> listDocuments(toolName)
            "create_document" -> createDocument(toolName, arguments)
            "switch_document" -> switchDocument(toolName, arguments)
            "rename_document" -> renameDocument(toolName, arguments)
            "duplicate_document" -> duplicateDocument(toolName, arguments)
            "delete_document" -> deleteDocument(toolName, arguments)
            "return_home" -> returnHome(toolName)
            "get_scene_summary" -> getSceneSummary(toolName)
            "list_objects" -> listObjects(toolName, arguments)
            "select_object" -> selectObject(toolName, arguments)
            "undo" -> undo(toolName)
            "redo" -> redo(toolName)
            else -> failure(toolName, "not_implemented", "Tool is registered but not implemented yet: $toolName")
        }
    }

    private fun addObject(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val presetName = arguments.requiredString(toolName, "preset") ?: return lastFailure
        val preset = findPreset(presetName)
            ?: return failure(toolName, "invalid_preset", "Unknown preset: $presetName")
        viewModel.addPresetObject(preset)
        val objectId = viewModel.selectedObjectId
            ?: return failure(toolName, "add_failed", "Object was not added.")
        val obj = viewModel.document.objects.getValue(objectId)
        return success(
            toolName,
            "Added ${obj.displayName}.",
            objectData(objectId, obj),
        )
    }

    private fun addText(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val initialText = arguments.optionalString("text")
        viewModel.addTextFromToolbar()
        val objectId = viewModel.selectedObjectId
            ?: return failure(toolName, "add_failed", "Text object was not added.")
        val text = initialText ?: viewModel.document.objects.getValue(objectId).source
        if (!viewModel.setTextForAgent(objectId, text, captureHistory = false)) {
            return failure(toolName, "text_update_failed", "Text object was added, but its initial text could not be applied.")
        }
        val obj = viewModel.document.objects.getValue(objectId)
        return success(
            toolName,
            "Added text.",
            objectData(objectId, obj) + ("text" to obj.source),
        )
    }

    private fun editText(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val text = arguments.requiredString(toolName, "text", allowBlank = true) ?: return lastFailure
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.type != ObjectType.TEXT) {
            return failure(toolName, "invalid_object_type", "Target object is not a text object.")
        }
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        if (!viewModel.setTextForAgent(resolved.id, text, captureHistory = true)) {
            return failure(toolName, "text_update_failed", "Text could not be updated.")
        }
        val updated = viewModel.document.objects.getValue(resolved.id)
        return success(
            toolName,
            "Updated text.",
            objectData(resolved.id, updated) + ("text" to updated.source),
        )
    }

    private fun setColor(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val colorName = arguments.requiredString(toolName, "color") ?: return lastFailure
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        if (!supportsColor(resolved.obj)) {
            return failure(toolName, "invalid_object_type", "Target object does not support color changes.")
        }
        val color = resolveColor(colorName, resolved.obj)
            ?: return failure(toolName, "invalid_color", "Unknown color: $colorName")
        selectForSelectedTool(toolName, resolved.id)?.let { return it }
        viewModel.updateSelectedTextColor(color)
        val updated = viewModel.document.objects.getValue(resolved.id)
        return success(
            toolName,
            "Updated color.",
            objectData(resolved.id, updated) + ("color" to color),
        )
    }

    private fun getTransform(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        val transform = viewModel.currentTransform(resolved.id)
            ?: return failure(toolName, "object_not_found", "Target object has no transform on the current page.")
        return success(
            toolName,
            "Read transform.",
            mapOf(
                "object_id" to resolved.id,
                "transform" to transform.toAgentData(),
            ),
        )
    }

    private fun setTransform(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        val current = viewModel.currentTransform(resolved.id)
            ?: return failure(toolName, "object_not_found", "Target object has no transform on the current page.")
        val next = buildTransform(toolName, current, arguments) ?: return lastFailure
        viewModel.commitTransform(resolved.id, next)
        val applied = viewModel.currentTransform(resolved.id) ?: next
        return success(
            toolName,
            "Updated transform.",
            mapOf(
                "object_id" to resolved.id,
                "transform" to applied.toAgentData(),
            ),
        )
    }

    private fun moveObjectRelative(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        val current = viewModel.currentTransform(resolved.id)
            ?: return failure(toolName, "object_not_found", "Target object has no transform on the current page.")
        val delta = arguments.optionalObject("delta")
            ?: return failure(toolName, "missing_transform", "Missing required parameter: delta")
        val dx = delta.optionalFloat(toolName, "delta.x", default = 0f) ?: return lastFailure
        val dy = delta.optionalFloat(toolName, "delta.y", default = 0f) ?: return lastFailure
        val dz = delta.optionalFloat(toolName, "delta.z", default = 0f) ?: return lastFailure
        if (abs(dx) <= FLOAT_EPSILON && abs(dy) <= FLOAT_EPSILON && abs(dz) <= FLOAT_EPSILON) {
            return failure(toolName, "missing_transform", "Provide a non-zero delta.")
        }
        if (!safeDelta(dx) || !safeDelta(dy) || !safeDelta(dz)) {
            return failure(toolName, "invalid_parameter", "Each relative move delta must stay within +/-3 meters.")
        }
        val next = current.copy(
            px = current.px + dx,
            py = current.py + dy,
            pz = current.pz + dz,
        )
        if (!safePosition(next.px) || !safePosition(next.py) || !safePosition(next.pz)) {
            return failure(toolName, "invalid_parameter", "Resulting position must stay within +/-10 meters.")
        }
        viewModel.commitTransform(resolved.id, next)
        val applied = viewModel.currentTransform(resolved.id) ?: next
        return success(
            toolName,
            "Moved object.",
            mapOf(
                "object_id" to resolved.id,
                "delta" to mapOf("x" to dx, "y" to dy, "z" to dz),
                "transform" to applied.toAgentData(),
            ),
        )
    }

    private fun rotateObjectRelative(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        val current = viewModel.currentTransform(resolved.id)
            ?: return failure(toolName, "object_not_found", "Target object has no transform on the current page.")
        val degrees = arguments.requiredFloat(toolName, "degrees") ?: return lastFailure
        if (abs(degrees) <= FLOAT_EPSILON) {
            return failure(toolName, "missing_transform", "Provide a non-zero relative rotation.")
        }
        if (!safeRotationDegrees(degrees)) {
            return failure(toolName, "invalid_parameter", "Relative rotation must stay within +/-360 degrees.")
        }
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val next = current.copy(rotationY = current.rotationY + radians)
        viewModel.commitTransform(resolved.id, next)
        val applied = viewModel.currentTransform(resolved.id) ?: next
        return success(
            toolName,
            "Rotated object.",
            mapOf(
                "object_id" to resolved.id,
                "degrees" to degrees,
                "radians" to radians,
                "transform" to applied.toAgentData(),
            ),
        )
    }

    private fun scaleObjectRelative(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        val current = viewModel.currentTransform(resolved.id)
            ?: return failure(toolName, "object_not_found", "Target object has no transform on the current page.")
        val factor = arguments.requiredFloat(toolName, "scale_factor") ?: return lastFailure
        if (abs(factor - 1f) <= FLOAT_EPSILON) {
            return failure(toolName, "missing_transform", "Provide a scale factor different from 1.")
        }
        if (!safeScaleFactor(factor)) {
            return failure(toolName, "invalid_parameter", "Relative scale factor must be between 0.1 and 5.")
        }
        val nextScale = current.scale * factor
        if (nextScale !in MIN_SCALE..MAX_SCALE) {
            return failure(toolName, "invalid_parameter", "Resulting scale must be between 0.08 and 10.")
        }
        val next = current.copy(scale = nextScale)
        viewModel.commitTransform(resolved.id, next)
        val applied = viewModel.currentTransform(resolved.id) ?: next
        return success(
            toolName,
            "Scaled object.",
            mapOf(
                "object_id" to resolved.id,
                "scale_factor" to factor,
                "transform" to applied.toAgentData(),
            ),
        )
    }

    private fun deleteObject(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        selectForSelectedTool(toolName, resolved.id)?.let { return it }
        viewModel.deleteSelectedObject()
        if (viewModel.currentPage.states.containsKey(resolved.id)) {
            return failure(toolName, "delete_failed", "Object could not be deleted.")
        }
        return success(
            toolName,
            "Deleted object.",
            mapOf("object_id" to resolved.id),
        )
    }

    private fun clearCurrentPage(toolName: String): AgentToolResult {
        val removedCount = viewModel.clearCurrentPageObjects()
        return success(
            toolName,
            if (removedCount == 0) "Current page was already empty." else "Cleared current page.",
            mapOf("removed_count" to removedCount) + pageData(),
        )
    }

    private fun groupAllModels(toolName: String): AgentToolResult {
        val visibleModelCount = viewModel.currentPage.states.count { (objectId, state) ->
            state.visible && viewModel.document.objects[objectId]?.type == ObjectType.MODEL
        }
        val result = viewModel.groupAllVisibleModels()
            ?: return failure(
                toolName,
                "insufficient_models",
                "At least two visible 3D models are required; found $visibleModelCount.",
                mapOf("visible_model_count" to visibleModelCount),
            )
        return success(
            toolName,
            "Grouped ${result.objectIds.size} models.",
            mapOf(
                "group_id" to result.groupId,
                "object_ids" to result.objectIds,
                "grouped_count" to result.objectIds.size,
            ) + pageData(),
        )
    }

    private fun ungroupAll(toolName: String): AgentToolResult {
        val result = viewModel.ungroupAllObjects()
        return success(
            toolName,
            if (result.objectCount == 0) "Current page had no groups." else "Removed all groups on the current page.",
            mapOf(
                "ungrouped_count" to result.objectCount,
                "removed_group_count" to result.groupCount,
            ) + pageData(),
        )
    }

    private fun duplicateObject(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        selectForSelectedTool(toolName, resolved.id)?.let { return it }
        viewModel.duplicateSelectedObject()
        val duplicateId = viewModel.selectedObjectId
            ?: return failure(toolName, "duplicate_failed", "Object could not be duplicated.")
        if (duplicateId == resolved.id || !viewModel.currentPage.states.containsKey(duplicateId)) {
            return failure(toolName, "duplicate_failed", "Object could not be duplicated.")
        }
        val duplicate = viewModel.document.objects.getValue(duplicateId)
        return success(
            toolName,
            "Duplicated object.",
            objectData(duplicateId, duplicate) + ("source_object_id" to resolved.id),
        )
    }

    private fun addPage(toolName: String): AgentToolResult {
        if (viewModel.isWhiteboard) {
            return failure(toolName, "unsupported_document_type", "Whiteboard documents do not support pages.")
        }
        if (!viewModel.canAddPage) {
            return failure(toolName, "page_limit_reached", "The document cannot add more pages.")
        }
        val beforeCount = viewModel.pageCount
        viewModel.addPage()
        if (viewModel.pageCount <= beforeCount) {
            return failure(toolName, "page_update_failed", "Page was not added.")
        }
        return success(toolName, "Added page.", pageData())
    }

    private fun duplicatePage(toolName: String): AgentToolResult {
        if (viewModel.isWhiteboard) {
            return failure(toolName, "unsupported_document_type", "Whiteboard documents do not support pages.")
        }
        if (!viewModel.canAddPage) {
            return failure(toolName, "page_limit_reached", "The document cannot add more pages.")
        }
        val beforeCount = viewModel.pageCount
        viewModel.duplicateCurrentPage()
        if (viewModel.pageCount <= beforeCount) {
            return failure(toolName, "page_update_failed", "Page was not duplicated.")
        }
        return success(toolName, "Duplicated page.", pageData())
    }

    private fun deletePage(toolName: String): AgentToolResult {
        if (viewModel.isWhiteboard) {
            return failure(toolName, "unsupported_document_type", "Whiteboard documents do not support pages.")
        }
        if (!viewModel.canDeletePage) {
            return failure(toolName, "cannot_delete_last_page", "The last page cannot be deleted.")
        }
        val beforeCount = viewModel.pageCount
        viewModel.deleteCurrentPage()
        if (viewModel.pageCount >= beforeCount) {
            return failure(toolName, "page_update_failed", "Page was not deleted.")
        }
        return success(toolName, "Deleted page.", pageData())
    }

    private fun gotoPage(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        if (viewModel.isWhiteboard) {
            return failure(toolName, "unsupported_document_type", "Whiteboard documents do not support page navigation.")
        }
        val index = arguments.requiredInt(toolName, "index") ?: return lastFailure
        if (index !in 1..viewModel.pageCount) {
            return failure(toolName, "invalid_page_index", "Page index is out of range.")
        }
        viewModel.goToPage(index - 1)
        if (viewModel.currentPageIndex != index - 1) {
            return failure(toolName, "page_update_failed", "Page navigation failed.")
        }
        return success(toolName, "Changed page.", pageData())
    }

    private fun enterPresentMode(toolName: String): AgentToolResult {
        if (viewModel.isWhiteboard) {
            return failure(toolName, "unsupported_document_type", "Whiteboard documents do not support present mode.")
        }
        viewModel.enterPresentMode()
        if (viewModel.mode != AppMode.PRESENT) {
            return failure(toolName, "mode_update_failed", "Present mode was not entered.")
        }
        return success(toolName, "Entered present mode.", modeData())
    }

    private fun exitPresentMode(toolName: String): AgentToolResult {
        if (viewModel.mode != AppMode.PRESENT) {
            return failure(toolName, "not_in_present_mode", "The editor is not in present mode.")
        }
        viewModel.exitPresentMode()
        if (viewModel.mode != AppMode.EDIT) {
            return failure(toolName, "mode_update_failed", "Present mode was not exited.")
        }
        return success(toolName, "Exited present mode.", modeData())
    }

    private fun addViewpoint(toolName: String): AgentToolResult {
        viewModel.addViewpointFromToolbar()
        val objectId = viewModel.selectedObjectId
            ?: return failure(toolName, "add_failed", "Viewpoint was not added.")
        val obj = viewModel.document.objects[objectId]
            ?: return failure(toolName, "add_failed", "Viewpoint was not added.")
        if (obj.type != ObjectType.VIEWPOINT) {
            return failure(toolName, "add_failed", "Selected object is not a viewpoint.")
        }
        return success(toolName, "Added viewpoint.", objectData(objectId, obj) + pageData())
    }

    private fun enterViewpoint(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.type != ObjectType.VIEWPOINT) {
            return failure(toolName, "invalid_object_type", "Target object is not a viewpoint.")
        }
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target viewpoint is locked.")
        }
        if (viewModel.currentPage.states[resolved.id]?.visible != true) {
            return failure(toolName, "object_not_visible", "Target viewpoint is hidden on the current page.")
        }
        viewModel.enterViewpoint(resolved.id)
        if (viewModel.viewpointModeObjectId != resolved.id) {
            return failure(toolName, "mode_update_failed", "Viewpoint mode was not entered.")
        }
        return success(
            toolName,
            "Entered viewpoint.",
            objectData(resolved.id, resolved.obj) + ("viewpoint_object_id" to resolved.id),
        )
    }

    private fun exitViewpoint(toolName: String): AgentToolResult {
        val objectId = viewModel.viewpointModeObjectId
            ?: return failure(toolName, "not_in_viewpoint", "The editor is not in viewpoint mode.")
        viewModel.exitViewpoint()
        if (viewModel.viewpointModeObjectId != null) {
            return failure(toolName, "mode_update_failed", "Viewpoint mode was not exited.")
        }
        return success(toolName, "Exited viewpoint.", mapOf("viewpoint_object_id" to objectId))
    }

    private fun setAnimation(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        requireAnimationEditContext(toolName)?.let { return it }
        val duration = if (arguments.containsKey("duration_seconds")) {
            val parsed = arguments.optionalFloat(toolName, "duration_seconds") ?: return lastFailure
            if (parsed !in PageAnimation.MIN_DURATION_SECONDS..PageAnimation.MAX_DURATION_SECONDS) {
                return failure(toolName, "invalid_parameter", "Animation duration must be between 0.5 and 12 seconds.")
            }
            parsed
        } else {
            null
        }
        val easing = arguments.optionalString("easing")?.takeIf { it.isNotBlank() }?.let { raw ->
            parseAnimationEasing(raw)
                ?: return failure(toolName, "invalid_animation_easing", "Unknown animation easing: $raw")
        }
        val loopMode = arguments.optionalString("loop_mode")?.takeIf { it.isNotBlank() }?.let { raw ->
            parseAnimationLoopMode(raw)
                ?: return failure(toolName, "invalid_animation_loop_mode", "Unknown animation loop mode: $raw")
        }
        val startArgument = arguments.optionalTransformObject(toolName, "start")
        if (startArgument == null && arguments.containsKey("start") && arguments["start"] != null) return lastFailure
        val endArgument = arguments.optionalTransformObject(toolName, "end")
        if (endArgument == null && arguments.containsKey("end") && arguments["end"] != null) return lastFailure
        startArgument?.let {
            buildAnimationEndpointTransform(toolName, TransformData(), it, "start") ?: return lastFailure
        }
        endArgument?.let {
            buildAnimationEndpointTransform(toolName, TransformData(), it, "end") ?: return lastFailure
        }
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.type == ObjectType.VIEWPOINT) {
            return failure(toolName, "invalid_object_type", "Viewpoint objects do not support page animations.")
        }
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        selectForSelectedTool(toolName, resolved.id)?.let { return it }

        viewModel.openSelectedAnimationEditor()
        val endpoints = viewModel.animationDraftEndpoints
        if (endpoints.size != 2) {
            return failure(toolName, "animation_update_failed", "Animation draft endpoints were not created.")
        }
        val startEndpoint = endpoints.first()
        val endEndpoint = endpoints.last()
        startArgument?.let {
            val transform = buildAnimationEndpointTransform(toolName, startEndpoint.transform, it, "start") ?: return lastFailure
            viewModel.commitTransform(startEndpoint.id, transform)
        }
        endArgument?.let {
            val transform = buildAnimationEndpointTransform(toolName, endEndpoint.transform, it, "end") ?: return lastFailure
            viewModel.commitTransform(endEndpoint.id, transform)
        }
        duration?.let(viewModel::updateSelectedAnimationDuration)
        easing?.let(viewModel::updateSelectedAnimationEasing)
        loopMode?.let(viewModel::updateSelectedAnimationLoopMode)
        viewModel.confirmSelectedAnimation()

        val state = viewModel.currentPage.states[resolved.id]
            ?: return failure(toolName, "object_not_found", "Target object is no longer on the current page.")
        val animation = state.pageAnimation
            ?: return failure(toolName, "animation_update_failed", "Animation was not applied.")
        return success(toolName, "Set animation.", animationData(resolved.id, resolved.obj, animation))
    }

    private fun deleteAnimation(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        requireAnimationEditContext(toolName)?.let { return it }
        val resolved = resolveObject(toolName, arguments.optionalString("object_id"))
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        if (resolved.obj.type == ObjectType.VIEWPOINT) {
            return failure(toolName, "invalid_object_type", "Viewpoint objects do not support page animations.")
        }
        if (resolved.obj.isLocked) {
            return failure(toolName, "object_locked", "Target object is locked.")
        }
        if (viewModel.currentPage.states[resolved.id]?.pageAnimation == null) {
            return failure(toolName, "animation_not_found", "Target object has no page animation.")
        }
        selectForSelectedTool(toolName, resolved.id)?.let { return it }
        viewModel.deleteSelectedAnimation()
        if (viewModel.currentPage.states[resolved.id]?.pageAnimation != null) {
            return failure(toolName, "animation_update_failed", "Animation was not deleted.")
        }
        return success(toolName, "Deleted animation.", objectData(resolved.id, resolved.obj))
    }

    private fun toggleAnimationPlayback(toolName: String): AgentToolResult {
        if (!currentPageHasAnyAnimation()) {
            return failure(toolName, "animation_not_found", "The current page has no animations.")
        }
        val before = viewModel.animationsPaused
        viewModel.toggleAnimationPlayback()
        if (viewModel.animationsPaused == before) {
            return failure(toolName, "animation_update_failed", "Animation playback state did not change.")
        }
        return success(
            toolName,
            "Toggled animation playback.",
            mapOf("animations_paused" to viewModel.animationsPaused),
        )
    }

    private fun listDocuments(toolName: String): AgentToolResult =
        success(toolName, "Listed documents.", documentsData())

    private fun createDocument(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val rawType = arguments.requiredString(toolName, "type") ?: return lastFailure
        val type = parseDocumentType(rawType)
            ?: return failure(toolName, "invalid_document_type", "Unknown document type: $rawType")
        val beforeCount = documentCount()
        val replacingEmptyDocument = viewModel.objectCount == 0
        viewModel.newShow(type)
        val expectedCount = if (replacingEmptyDocument) beforeCount else beforeCount + 1
        if (documentCount() != expectedCount || viewModel.document.documentType != type) {
            return failure(toolName, "document_update_failed", "Document was not created.")
        }
        return success(toolName, "Created document.", activeDocumentData() + documentsCountData())
    }

    private fun switchDocument(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val index = requiredDocumentIndex(toolName, arguments) ?: return lastFailure
        val target = index - 1
        if (target == viewModel.activeShowIndex && viewModel.screen == AppScreen.EDITOR) {
            return success(toolName, "Document already active.", activeDocumentData() + screenData())
        }
        if (viewModel.screen == AppScreen.HOME) {
            viewModel.openShowFromHome(target)
        } else {
            viewModel.switchShow(target)
        }
        if (viewModel.activeShowIndex != target || viewModel.screen != AppScreen.EDITOR) {
            return failure(toolName, "document_update_failed", "Document was not opened.")
        }
        return success(toolName, "Switched document.", activeDocumentData() + screenData())
    }

    private fun renameDocument(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val index = requiredDocumentIndex(toolName, arguments) ?: return lastFailure
        val name = arguments.requiredString(toolName, "name", allowBlank = true) ?: return lastFailure
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            return failure(toolName, "document_name_empty", "Document name must not be blank.")
        }
        val before = viewModel.showSummaries[index - 1]
        if (before.name == cleaned) {
            return success(toolName, "Document already has that name.", documentData(index, before) + activeDocumentData())
        }
        viewModel.renameShow(index - 1, cleaned)
        val after = viewModel.showSummaries.getOrNull(index - 1)
            ?: return failure(toolName, "document_update_failed", "Document disappeared after rename.")
        if (after.name == before.name) {
            return failure(toolName, "document_update_failed", "Document was not renamed.")
        }
        return success(toolName, "Renamed document.", documentData(index, after) + activeDocumentData())
    }

    private fun duplicateDocument(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val index = requiredDocumentIndex(toolName, arguments) ?: return lastFailure
        val beforeCount = documentCount()
        viewModel.duplicateShow(index - 1)
        if (documentCount() != beforeCount + 1) {
            return failure(toolName, "document_update_failed", "Document was not duplicated.")
        }
        val duplicateIndex = index + 1
        val duplicate = viewModel.showSummaries.getOrNull(duplicateIndex - 1)
            ?: return failure(toolName, "document_update_failed", "Duplicated document was not found.")
        return success(
            toolName,
            "Duplicated document.",
            documentData(duplicateIndex, duplicate) + ("source_document_index" to index) + activeDocumentData(),
        )
    }

    private fun deleteDocument(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val index = requiredDocumentIndex(toolName, arguments) ?: return lastFailure
        if (documentCount() <= 1) {
            return failure(toolName, "cannot_delete_last_document", "The last document cannot be deleted by Agent.")
        }
        val beforeCount = documentCount()
        val removedName = viewModel.showSummaries[index - 1].name
        viewModel.deleteShow(index - 1)
        if (documentCount() != beforeCount - 1) {
            return failure(toolName, "document_update_failed", "Document was not deleted.")
        }
        return success(
            toolName,
            "Deleted document.",
            mapOf(
                "removed_document_index" to index,
                "removed_document_name" to removedName,
            ) + activeDocumentData() + documentsCountData(),
        )
    }

    private fun returnHome(toolName: String): AgentToolResult {
        viewModel.returnHome()
        if (viewModel.screen != AppScreen.HOME) {
            return failure(toolName, "screen_update_failed", "Home screen was not opened.")
        }
        return success(toolName, "Returned home.", activeDocumentData() + screenData())
    }

    private fun getSceneSummary(toolName: String): AgentToolResult {
        val selected = viewModel.selectedObjectId?.let { id ->
            val obj = viewModel.document.objects[id]
            val state = viewModel.currentPage.states[id]
            if (obj != null && state != null) objectSummaryData(id, obj, state) else null
        }
        val currentPageObjects = viewModel.currentPage.states.entries
            .take(MAX_SCENE_SUMMARY_OBJECTS)
            .mapNotNull { (id, state) ->
                viewModel.document.objects[id]?.let { obj -> objectSummaryData(id, obj, state) }
            }
        val objectCount = viewModel.currentPage.states.size
        val objectListTruncated = objectCount > currentPageObjects.size
        val selectedTransform = selected?.get("transform")
        val selectionCapabilities = selected?.let {
            mapOf(
                "can_change_color" to it["supports_color"],
                "can_generate_image_from_this" to (it["type"] == "image" || it["type"] == "stroke"),
                "can_generate_model_from_this" to (it["type"] == "image" || it["type"] == "stroke"),
                "can_edit_text" to (it["type"] == "text"),
                "can_set_animation" to (it["type"] != "viewpoint"),
                "has_animation" to (it["has_animation"] == true),
            )
        }
        return success(
            toolName,
            "Read scene summary.",
            mapOf(
                "document_name" to viewModel.document.name,
                "active_document_index" to viewModel.activeShowIndex + 1,
                "document_count" to documentCount(),
                "screen" to viewModel.screen.name.lowercase(Locale.US),
                "document_type" to viewModel.document.documentType.name.lowercase(Locale.US),
                "mode" to viewModel.mode.name.lowercase(Locale.US),
                "is_whiteboard" to viewModel.isWhiteboard,
                "animations_paused" to viewModel.animationsPaused,
                "current_page_has_animations" to currentPageHasAnyAnimation(),
                "page_index" to viewModel.currentPageIndex + 1,
                "page_count" to viewModel.pageCount,
                "object_count" to objectCount,
                "document_object_count" to viewModel.objectCount,
                "selected_object" to selected,
                "selected_transform" to selectedTransform,
                "selection_capabilities" to selectionCapabilities,
                "current_page_objects" to currentPageObjects,
                "current_page_objects_truncated" to objectListTruncated,
                "current_page_objects_limit" to MAX_SCENE_SUMMARY_OBJECTS,
                "can_undo" to viewModel.canUndo,
                "can_redo" to viewModel.canRedo,
            ),
        )
    }

    private fun listObjects(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val typeFilter = arguments.optionalString("type")?.let { raw ->
            parseObjectType(raw)
                ?: return failure(toolName, "invalid_object_type", "Unknown object type: $raw")
        }
        val objects = viewModel.currentPage.states.keys.mapNotNull { id ->
            val obj = viewModel.document.objects[id] ?: return@mapNotNull null
            if (typeFilter != null && obj.type != typeFilter) return@mapNotNull null
            objectData(id, obj) + mapOf(
                "visible" to (viewModel.currentPage.states[id]?.visible == true),
                "selected" to (viewModel.selectedObjectId == id),
                "group_id" to viewModel.currentPage.states[id]?.groupId,
            )
        }
        return success(
            toolName,
            "Listed objects.",
            mapOf("objects" to objects),
        )
    }

    private fun selectObject(toolName: String, arguments: Map<String, Any?>): AgentToolResult {
        val objectId = arguments.requiredString(toolName, "object_id") ?: return lastFailure
        val resolved = resolveObject(toolName, objectId)
        if (resolved is ObjectResolution.Failed) return resolved.result
        resolved as ObjectResolution.Found
        viewModel.select(resolved.id)
        if (viewModel.selectedObjectId != resolved.id) {
            return failure(toolName, "selection_rejected", "The editor is not currently allowing object selection.")
        }
        return success(
            toolName,
            "Selected object.",
            objectData(resolved.id, resolved.obj),
        )
    }

    private fun undo(toolName: String): AgentToolResult {
        if (!viewModel.canUndo) {
            return failure(toolName, "nothing_to_undo", "There is no operation to undo.")
        }
        viewModel.undo()
        return success(
            toolName,
            "Undo complete.",
            mapOf(
                "can_undo" to viewModel.canUndo,
                "can_redo" to viewModel.canRedo,
            ),
        )
    }

    private fun redo(toolName: String): AgentToolResult {
        if (!viewModel.canRedo) {
            return failure(toolName, "nothing_to_redo", "There is no operation to redo.")
        }
        viewModel.redo()
        return success(
            toolName,
            "Redo complete.",
            mapOf(
                "can_undo" to viewModel.canUndo,
                "can_redo" to viewModel.canRedo,
            ),
        )
    }

    private fun resolveObject(toolName: String, objectId: String?): ObjectResolution {
        val resolvedId = objectId?.takeIf { it.isNotBlank() } ?: viewModel.selectedObjectId
        if (resolvedId == null) {
            return ObjectResolution.Failed(
                failure(toolName, "no_current_selection", "No object_id was provided and no object is selected."),
            )
        }
        val obj = viewModel.document.objects[resolvedId]
            ?: return ObjectResolution.Failed(
                failure(toolName, "object_not_found", "Object not found: $resolvedId"),
            )
        if (!viewModel.currentPage.states.containsKey(resolvedId)) {
            return ObjectResolution.Failed(
                failure(toolName, "object_not_found", "Object is not present on the current page: $resolvedId"),
            )
        }
        return ObjectResolution.Found(resolvedId, obj)
    }

    private fun selectForSelectedTool(toolName: String, objectId: String): AgentToolResult? {
        if (viewModel.selectedObjectId == objectId) return null
        viewModel.select(objectId)
        return if (viewModel.selectedObjectId == objectId) {
            null
        } else {
            failure(toolName, "selection_rejected", "The editor is not currently allowing object selection.")
        }
    }

    private fun buildTransform(
        toolName: String,
        current: TransformData,
        arguments: Map<String, Any?>,
    ): TransformData? {
        var next = current
        var changed = false

        arguments.optionalObject("position")?.let { position ->
            val x = position.requiredFloat(toolName, "position.x") ?: return null
            val y = position.requiredFloat(toolName, "position.y") ?: return null
            val z = position.requiredFloat(toolName, "position.z") ?: return null
            if (!safePosition(x) || !safePosition(y) || !safePosition(z)) {
                lastFailure = failure(toolName, "invalid_parameter", "Position must stay within +/-10 meters.")
                return null
            }
            next = next.copy(px = x, py = y, pz = z)
            changed = true
        }

        arguments.optionalObject("rotation")?.let { rotation ->
            val x = rotation.optionalFloat(toolName, "rotation.x", default = 0f) ?: return null
            val y = rotation.optionalFloat(toolName, "rotation.y")
                ?: rotation.optionalFloat(toolName, "rotation.rotationY")
                ?: run {
                    lastFailure = failure(toolName, "missing_parameter", "Missing required parameter: rotation.y")
                    return null
                }
            val z = rotation.optionalFloat(toolName, "rotation.z", default = 0f) ?: return null
            if (abs(x) > FLOAT_EPSILON || abs(z) > FLOAT_EPSILON) {
                lastFailure = failure(toolName, "unsupported_rotation_axis", "The current editor only supports Y-axis rotation.")
                return null
            }
            if (!y.isFinite()) {
                lastFailure = failure(toolName, "invalid_parameter", "Rotation must be a finite number.")
                return null
            }
            next = next.copy(rotationY = y)
            changed = true
        }

        if (arguments.containsKey("scale")) {
            val scale = arguments.optionalFloat(toolName, "scale") ?: return null
            if (scale !in MIN_SCALE..MAX_SCALE) {
                lastFailure = failure(toolName, "invalid_parameter", "Scale must be between 0.08 and 10.")
                return null
            }
            next = next.copy(scale = scale)
            changed = true
        }

        if (!changed) {
            lastFailure = failure(toolName, "missing_transform", "Provide position, rotation, or scale.")
            return null
        }
        return next
    }

    private fun findPreset(raw: String): PresetAsset? {
        val normalized = raw.normalizedKey()
        return PresetLibrary.assets.firstOrNull { asset ->
            listOfNotNull(
                asset.id,
                asset.geometry?.sourceId,
                asset.displayName,
            ).any { it.normalizedKey() == normalized }
        }
    }

    private fun parseObjectType(raw: String): ObjectType? {
        val normalized = raw.normalizedKey()
        if (normalized == "anchor") return ObjectType.MODEL
        return ObjectType.values().firstOrNull { it.name.normalizedKey() == normalized }
    }

    private fun parseDocumentType(raw: String): DocumentType? =
        when (raw.normalizedKey()) {
            "presentation", "present", "show", "deck", "演示", "演示文档" -> DocumentType.PRESENTATION
            "whiteboard", "board", "白板", "白板作品" -> DocumentType.WHITEBOARD
            else -> null
        }

    private fun supportsColor(obj: SceneObject): Boolean =
        when (obj.type) {
            ObjectType.TEXT,
            ObjectType.STROKE,
            -> true
            ObjectType.IMAGE -> false
            ObjectType.MODEL -> obj.source.startsWith(PresetLibrary.GEOMETRY_SOURCE_PREFIX)
            ObjectType.VIEWPOINT -> false
        }

    private fun resolveColor(raw: String, target: SceneObject): Long? {
        val normalized = raw.normalizedKey()
        val color = when (normalized) {
            "gray", "grey" -> ObjectColors.DEFAULT_GEOMETRY_COLOR
            "red" -> 0xFFEF4444
            "orange" -> 0xFFF97316
            "yellow" -> 0xFFFACC15
            "green" -> 0xFF22C55E
            "blue" -> 0xFF2563EB
            "purple" -> 0xFF7C3AED
            "black" -> if (target.type == ObjectType.TEXT) 0xFF111111 else 0xFF111827
            "white" -> 0xFFFFFFFF
            "teal" -> 0xFF14B8A6
            "sky" -> 0xFF38BDF8
            "pink" -> 0xFFEC4899
            "brown" -> 0xFF92400E
            "rose" -> 0xFFE11D48
            else -> null
        }
        val allowedColors = (ObjectColors.GEOMETRY_PALETTE + ObjectColors.TEXT_PALETTE + ObjectColors.STROKE_PALETTE).toSet()
        return color?.takeIf { it in allowedColors }
    }

    private fun objectData(objectId: String, obj: SceneObject): Map<String, Any?> =
        mapOf(
            "object_id" to objectId,
            "type" to obj.type.name.lowercase(Locale.US),
            "display_name" to obj.displayName,
        )

    private fun pageData(): Map<String, Any?> =
        mapOf(
            "page_index" to viewModel.currentPageIndex + 1,
            "page_count" to viewModel.pageCount,
        )

    private fun modeData(): Map<String, Any?> =
        mapOf("mode" to viewModel.mode.name.lowercase(Locale.US))

    private fun documentsData(): Map<String, Any?> =
        mapOf(
            "documents" to viewModel.showSummaries.mapIndexed { zeroIndex, summary ->
                documentData(zeroIndex + 1, summary)
            },
        ) + activeDocumentData() + documentsCountData() + screenData()

    private fun documentData(index: Int, summary: ShowSummary): Map<String, Any?> =
        mapOf(
            "document_index" to index,
            "document_name" to summary.name,
            "document_type" to summary.documentType.name.lowercase(Locale.US),
            "object_count" to summary.objectCount,
            "page_count" to summary.pageCount,
            "selected" to summary.selected,
        )

    private fun activeDocumentData(): Map<String, Any?> =
        mapOf(
            "active_document_index" to viewModel.activeShowIndex + 1,
            "active_document_name" to viewModel.document.name,
            "active_document_type" to viewModel.document.documentType.name.lowercase(Locale.US),
        ) + documentsCountData() + screenData()

    private fun documentsCountData(): Map<String, Any?> =
        mapOf("document_count" to documentCount())

    private fun screenData(): Map<String, Any?> =
        mapOf("screen" to viewModel.screen.name.lowercase(Locale.US))

    private fun animationData(
        objectId: String,
        obj: SceneObject,
        animation: PageAnimation,
    ): Map<String, Any?> =
        objectData(objectId, obj) + animationSummaryData(animation)

    private fun animationSummaryData(animation: PageAnimation): Map<String, Any?> =
        mapOf(
            "start" to animation.start.toAgentData(),
            "end" to animation.end.toAgentData(),
            "duration_seconds" to animation.durationSeconds,
            "easing" to animation.easing.name.lowercase(Locale.US),
            "loop_mode" to animation.loopMode.name.lowercase(Locale.US),
        )

    private fun objectSummaryData(
        objectId: String,
        obj: SceneObject,
        state: ObjectState,
    ): Map<String, Any?> =
        buildMap {
            putAll(objectData(objectId, obj))
            put("visible", state.visible)
            put("selected", viewModel.selectedObjectId == objectId)
            put("locked", obj.isLocked)
            put("supports_color", supportsColor(obj))
            put("has_animation", state.pageAnimation != null)
            put("group_id", state.groupId)
            put("transform", state.transform.toAgentData())
            state.pageAnimation?.let { put("animation", animationSummaryData(it)) }
            obj.textColor?.let { put("color", it.toAgentColor()) }
            if (obj.type == ObjectType.TEXT) {
                put("text", obj.source.take(MAX_SCENE_SUMMARY_TEXT_CHARS))
                put("text_truncated", obj.source.length > MAX_SCENE_SUMMARY_TEXT_CHARS)
            }
            if (obj.type == ObjectType.STROKE) {
                put("stroke_point_count", obj.strokeData?.pointCount ?: 0)
            }
            obj.versionHistory?.let { history ->
                put("has_version_history", history.versions.size > 1)
                put("active_version_id", history.activeVersionId)
                put("version_count", history.versions.size)
                put(
                    "version_types",
                    history.versions
                        .map { it.type.name.lowercase(Locale.US) }
                        .distinct(),
                )
            }
        }

    private fun TransformData.toAgentData(): Map<String, Any> =
        mapOf(
            "position" to mapOf(
                "x" to px,
                "y" to py,
                "z" to pz,
            ),
            "rotation" to mapOf(
                "x" to 0f,
                "y" to rotationY,
                "z" to 0f,
            ),
            "scale" to scale,
        )

    private fun Map<String, Any?>.requiredString(
        toolName: String,
        key: String,
        allowBlank: Boolean = false,
    ): String? {
        val value = optionalString(key)
        if (value == null || (!allowBlank && value.isBlank())) {
            lastFailure = failure(toolName, "missing_parameter", "Missing required parameter: $key")
            return null
        }
        return value
    }

    private fun Map<String, Any?>.optionalString(key: String): String? =
        this[key]?.toString()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.optionalObject(key: String): Map<String, Any?>? =
        this[key] as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.optionalTransformObject(toolName: String, key: String): Map<String, Any?>? {
        if (!containsKey(key) || this[key] == null) return null
        val value = this[key] as? Map<String, Any?>
        if (value == null) {
            lastFailure = failure(toolName, "invalid_parameter", "$key must be an object.")
        }
        return value
    }

    private fun Map<String, Any?>.optionalFloat(
        toolName: String,
        key: String,
        default: Float? = null,
    ): Float? {
        val raw = this[key.substringAfterLast(".")] ?: return default
        val number = when (raw) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull()
            else -> null
        }
        if (number == null || !number.isFinite()) {
            lastFailure = failure(toolName, "invalid_parameter", "Invalid number for $key.")
            return null
        }
        return number
    }

    private fun Map<String, Any?>.requiredFloat(toolName: String, key: String): Float? {
        val mapKey = key.substringAfterLast(".")
        if (!containsKey(mapKey)) {
            lastFailure = failure(toolName, "missing_parameter", "Missing required parameter: $key")
            return null
        }
        return optionalFloat(toolName, key)
    }

    private fun Map<String, Any?>.requiredInt(toolName: String, key: String): Int? {
        val raw = this[key]
        if (raw == null) {
            lastFailure = failure(toolName, "missing_parameter", "Missing required parameter: $key")
            return null
        }
        val number = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        val integer = number?.takeIf { it.isFinite() && kotlin.math.abs(it % 1.0) < 0.0001 }?.toInt()
        if (integer == null) {
            lastFailure = failure(toolName, "invalid_parameter", "Invalid integer for $key.")
            return null
        }
        return integer
    }

    private fun requiredDocumentIndex(toolName: String, arguments: Map<String, Any?>): Int? {
        val index = arguments.requiredInt(toolName, "index") ?: return null
        if (index !in 1..documentCount()) {
            lastFailure = failure(toolName, "invalid_document_index", "Document index is out of range.")
            return null
        }
        return index
    }

    private fun requireAnimationEditContext(toolName: String): AgentToolResult? {
        if (viewModel.mode != AppMode.EDIT || viewModel.viewpointModeObjectId != null) {
            return failure(toolName, "not_in_edit_mode", "Animation editing is only available in normal edit mode.")
        }
        return null
    }

    private fun buildAnimationEndpointTransform(
        toolName: String,
        base: TransformData,
        data: Map<String, Any?>,
        label: String,
    ): TransformData? {
        var next = base
        val positionData = data.optionalTransformObject(toolName, "position")
        if (positionData == null && data.containsKey("position") && data["position"] != null) return null
        positionData?.let { position ->
            val x = position.optionalFloat(toolName, "$label.position.x", default = next.px) ?: return null
            val y = position.optionalFloat(toolName, "$label.position.y", default = next.py) ?: return null
            val z = position.optionalFloat(toolName, "$label.position.z", default = next.pz) ?: return null
            if (!safePosition(x) || !safePosition(y) || !safePosition(z)) {
                lastFailure = failure(toolName, "invalid_parameter", "$label endpoint position must stay within +/-10 meters.")
                return null
            }
            next = next.copy(px = x, py = y, pz = z)
        }
        val rotationData = data.optionalTransformObject(toolName, "rotation")
        if (rotationData == null && data.containsKey("rotation") && data["rotation"] != null) return null
        rotationData?.let { rotation ->
            val x = rotation.optionalFloat(toolName, "$label.rotation.x", default = 0f) ?: return null
            val y = rotation.optionalFloat(toolName, "$label.rotation.y", default = next.rotationY) ?: return null
            val z = rotation.optionalFloat(toolName, "$label.rotation.z", default = 0f) ?: return null
            if (abs(x) > FLOAT_EPSILON || abs(z) > FLOAT_EPSILON) {
                lastFailure = failure(toolName, "invalid_parameter", "$label endpoint rotation only supports the Y axis.")
                return null
            }
            if (!y.isFinite()) {
                lastFailure = failure(toolName, "invalid_parameter", "$label endpoint rotation must be finite.")
                return null
            }
            next = next.copy(rotationY = y)
        }
        if (data.containsKey("scale")) {
            val scale = data.optionalFloat(toolName, "$label.scale") ?: return null
            if (scale !in MIN_SCALE..MAX_SCALE) {
                lastFailure = failure(toolName, "invalid_parameter", "$label endpoint scale must be between 0.08 and 10.")
                return null
            }
            next = next.copy(scale = scale)
        }
        return next
    }

    private fun parseAnimationEasing(raw: String): AnimationEasing? =
        when (raw.normalizedKey()) {
            "linear", "线性" -> AnimationEasing.LINEAR
            "ease_in", "easein", "in", "入", "缓入" -> AnimationEasing.EASE_IN
            "ease_out", "easeout", "out", "出", "缓出" -> AnimationEasing.EASE_OUT
            "ease_in_out", "easeinout", "in_out", "入出", "缓入缓出" -> AnimationEasing.EASE_IN_OUT
            else -> null
        }

    private fun parseAnimationLoopMode(raw: String): AnimationLoopMode? =
        when (raw.normalizedKey()) {
            "none", "no_loop", "off", "不循环" -> AnimationLoopMode.NONE
            "ping_pong", "pingpong", "yoyo", "loop", "循环", "往复", "来回" -> AnimationLoopMode.PING_PONG
            "restart", "single", "single_direction", "单向", "重新开始" -> AnimationLoopMode.RESTART
            else -> null
        }

    private fun currentPageHasAnyAnimation(): Boolean =
        viewModel.currentPage.states.values.any { it.pageAnimation != null }

    private fun success(toolName: String, message: String, data: Map<String, Any?> = emptyMap()): AgentToolResult =
        AgentToolResult.success(toolName, message, data)

    private fun failure(
        toolName: String,
        errorCode: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
    ): AgentToolResult =
        AgentToolResult.failure(toolName, errorCode, message, data)

    private fun String.normalizedKey(): String =
        trim()
            .lowercase(Locale.US)
            .replace(" ", "_")
            .replace("-", "_")

    private fun safePosition(value: Float): Boolean =
        value.isFinite() && value in -MAX_POSITION_ABS..MAX_POSITION_ABS

    private fun safeDelta(value: Float): Boolean =
        value.isFinite() && value in -MAX_RELATIVE_MOVE_ABS..MAX_RELATIVE_MOVE_ABS

    private fun safeRotationDegrees(value: Float): Boolean =
        value.isFinite() && value in -MAX_RELATIVE_ROTATION_DEGREES_ABS..MAX_RELATIVE_ROTATION_DEGREES_ABS

    private fun safeScaleFactor(value: Float): Boolean =
        value.isFinite() && value in MIN_RELATIVE_SCALE_FACTOR..MAX_RELATIVE_SCALE_FACTOR

    private fun Long.toAgentColor(): String =
        String.format(Locale.US, "#%08X", this)

    private fun documentCount(): Int = viewModel.showSummaries.size

    private sealed class ObjectResolution {
        data class Found(val id: String, val obj: SceneObject) : ObjectResolution()
        data class Failed(val result: AgentToolResult) : ObjectResolution()
    }

    private var lastFailure: AgentToolResult = failure("unknown", "unknown_error", "Unknown error.")

    companion object {
        private val historyToolNames = setOf("undo", "redo")
        private val nonTransactionalToolNames = setOf(
            "get_scene_summary",
            "list_objects",
            "get_transform",
            "list_documents",
        ) + historyToolNames
        private const val MAX_POSITION_ABS = 10f
        private const val MIN_SCALE = 0.08f
        private const val MAX_SCALE = 10f
        private const val FLOAT_EPSILON = 0.0001f
        private const val MAX_SCENE_SUMMARY_OBJECTS = 12
        private const val MAX_SCENE_SUMMARY_TEXT_CHARS = 80
        private const val MAX_RELATIVE_MOVE_ABS = 3f
        private const val MAX_RELATIVE_ROTATION_DEGREES_ABS = 360f
        private const val MIN_RELATIVE_SCALE_FACTOR = 0.1f
        private const val MAX_RELATIVE_SCALE_FACTOR = 5f
    }
}
