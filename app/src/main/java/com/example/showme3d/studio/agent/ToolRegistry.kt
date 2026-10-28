package com.example.showme3d.studio.agent

import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.aigc.SeedreamImageClient
import com.example.showme3d.studio.model.ObjectType
import java.util.Locale

object ToolRegistry {
    private val presetNames: List<String> =
        PresetLibrary.assets.flatMap { asset ->
            listOfNotNull(
                asset.id,
                asset.geometry?.sourceId,
                asset.displayName,
            )
        }.distinct()

    private val objectTypeNames: List<String> =
        ObjectType.values().map { it.name.lowercase(Locale.US) }

    private val colorNames: List<String> =
        listOf(
            "gray",
            "red",
            "orange",
            "yellow",
            "green",
            "blue",
            "purple",
            "black",
            "white",
            "teal",
            "sky",
            "pink",
            "brown",
            "rose",
        )

    private val strokeSeedreamDefaultPromptForAgent: String =
        SeedreamImageClient.strokeMainViewPrompt("<用户明确指定的主体或创作方向；没有则写“无”>")

    private val strokePromptDescription: String =
        """
        Optional Seedream stroke creative direction. The editor default prompt is:
        $strokeSeedreamDefaultPromptForAgent
        This field is never required. If the user only says to turn the stroke/sketch/line art into an image/model, omit it and submit directly. If the user gives a subject or a creative direction, put only that information here, for example "红色卡通小螃蟹"; do not put generic command text such as "生成图片" or "把这段线稿生成 3D 模型".
        """.trimIndent()

    val tools: List<AgentTool> = listOf(
        AgentTool(
            name = "add_object",
            description = "Add one preset geometry or library asset to the current page.",
            parameters = listOf(
                AgentToolParameter(
                    name = "preset",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "Preset asset name or id, for example cube, sphere, cylinder.",
                    enumValues = presetNames,
                ),
            ),
            resultDescription = "Returns the new object id, type, and display name.",
            errorDescriptions = listOf("missing_parameter", "invalid_preset", "add_failed"),
        ),
        AgentTool(
            name = "add_text",
            description = "Add one text object to the current page.",
            parameters = listOf(
                AgentToolParameter(
                    name = "text",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Initial text. If omitted, the editor default placeholder is used.",
                ),
            ),
            resultDescription = "Returns the new text object id and text.",
            errorDescriptions = listOf("add_failed", "text_update_failed"),
        ),
        AgentTool(
            name = "edit_text",
            description = "Replace the text of the selected text object or a specific text object.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "text",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "New text content.",
                ),
            ),
            resultDescription = "Returns the target object id and new text.",
            errorDescriptions = listOf("missing_parameter", "no_current_selection", "object_not_found", "invalid_object_type", "object_locked", "text_update_failed"),
        ),
        AgentTool(
            name = "set_color",
            description = "Set the color of the selected object, text, stroke, or a specific supported object.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "color",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "Named palette color.",
                    enumValues = colorNames,
                ),
            ),
            resultDescription = "Returns the target object id and applied color value.",
            errorDescriptions = listOf("missing_parameter", "invalid_color", "no_current_selection", "object_not_found", "invalid_object_type", "object_locked", "selection_rejected"),
        ),
        AgentTool(
            name = "get_transform",
            description = "Read the transform of the selected object or a specific object.",
            parameters = listOf(objectIdParameter(required = false)),
            resultDescription = "Returns position, Y rotation, and uniform scale.",
            errorDescriptions = listOf("no_current_selection", "object_not_found"),
        ),
        AgentTool(
            name = "set_transform",
            description = "Set final position, Y rotation, and/or uniform scale for an object.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "position",
                    type = AgentParameterType.OBJECT,
                    required = false,
                    description = "Final position object with x, y, z in meters.",
                    properties = listOf(
                        numberParameter("x", required = true, description = "Final X position in meters."),
                        numberParameter("y", required = true, description = "Final Y position in meters."),
                        numberParameter("z", required = true, description = "Final Z position in meters."),
                    ),
                ),
                AgentToolParameter(
                    name = "rotation",
                    type = AgentParameterType.OBJECT,
                    required = false,
                    description = "Final rotation object. Current editor supports y in radians; x and z must be omitted or 0.",
                    properties = listOf(
                        numberParameter("x", required = false, description = "X rotation in radians. Must be omitted or 0."),
                        numberParameter("y", required = true, description = "Y rotation in radians."),
                        numberParameter("z", required = false, description = "Z rotation in radians. Must be omitted or 0."),
                    ),
                ),
                AgentToolParameter(
                    name = "scale",
                    type = AgentParameterType.NUMBER,
                    required = false,
                    description = "Final uniform scale, from 0.08 to 10.",
                ),
            ),
            resultDescription = "Returns the target object id and applied transform.",
            errorDescriptions = listOf("missing_parameter", "missing_transform", "invalid_parameter", "unsupported_rotation_axis", "no_current_selection", "object_not_found", "object_locked"),
        ),
        AgentTool(
            name = "move_object_relative",
            description = "Move the selected object or a specific object by a relative delta in workspace meters. Prefer this tool for commands like move right/left/up/down/forward/back by some distance; do not call get_transform first.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "delta",
                    type = AgentParameterType.OBJECT,
                    required = true,
                    description = "Relative movement delta in meters. Positive x moves right, negative x moves left; positive y moves up, negative y moves down; negative z moves forward/away from the user, positive z moves backward/toward the user. Each component must be between -3 and 3, and at least one component must be non-zero.",
                    properties = listOf(
                        numberParameter("x", required = false, description = "Relative X delta in meters. Use + for right, - for left."),
                        numberParameter("y", required = false, description = "Relative Y delta in meters. Use + for up, - for down."),
                        numberParameter("z", required = false, description = "Relative Z delta in meters. Use - for forward/away from user, + for backward/toward user."),
                    ),
                ),
            ),
            resultDescription = "Returns the target object id, applied delta, and final transform.",
            errorDescriptions = listOf("missing_transform", "invalid_parameter", "no_current_selection", "object_not_found", "object_locked"),
        ),
        AgentTool(
            name = "rotate_object_relative",
            description = "Rotate the selected object or a specific object by a relative Y-axis angle in degrees. Prefer this tool for commands like rotate right/left by some degrees; do not call get_transform first.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "degrees",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "Relative Y-axis rotation in degrees. Positive degrees rotate right, negative degrees rotate left. Value must be between -360 and 360 and non-zero.",
                ),
            ),
            resultDescription = "Returns the target object id, applied degrees/radians, and final transform.",
            errorDescriptions = listOf("missing_transform", "invalid_parameter", "no_current_selection", "object_not_found", "object_locked"),
        ),
        AgentTool(
            name = "scale_object_relative",
            description = "Scale the selected object or a specific object by a relative multiplier. Prefer this tool for commands like make it bigger/smaller by a percentage; do not call get_transform first.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "scale_factor",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "Relative scale multiplier. Use 1.2 to enlarge by 20%, 0.8 to shrink by 20%, and 0.5 to halve. Value must be between 0.1 and 5, not equal to 1. Final scale must remain between 0.08 and 10.",
                ),
            ),
            resultDescription = "Returns the target object id, applied scale factor, and final transform.",
            errorDescriptions = listOf("missing_transform", "invalid_parameter", "no_current_selection", "object_not_found", "object_locked"),
        ),
        AgentTool(
            name = "delete_object",
            description = "Delete the selected object or a specific object from the current page.",
            parameters = listOf(objectIdParameter(required = false)),
            resultDescription = "Returns the deleted object id.",
            errorDescriptions = listOf("no_current_selection", "object_not_found", "object_locked", "selection_rejected", "delete_failed"),
        ),
        AgentTool(
            name = "clear_current_page",
            description = "Delete all objects that are present on the current page. Use this when the user asks to clear, empty, wipe, or reset the current page's objects. This does not delete other pages.",
            parameters = emptyList(),
            resultDescription = "Returns the number of removed objects and current page info.",
            errorDescriptions = emptyList(),
        ),
        AgentTool(
            name = "group_all_models",
            description = "Combine every visible 3D model on the current page into one logical scene group. Use this only for a scene-wide request; images, text, strokes, and viewpoints are not included. Existing model groups are flattened into the new group.",
            parameters = emptyList(),
            resultDescription = "Returns the new group id, grouped model ids, and grouped model count.",
            errorDescriptions = listOf("insufficient_models"),
        ),
        AgentTool(
            name = "ungroup_all",
            description = "Remove every logical object grouping on the current page. This does not delete or move any object.",
            parameters = emptyList(),
            resultDescription = "Returns the number of groups removed and objects ungrouped.",
            errorDescriptions = emptyList(),
        ),
        AgentTool(
            name = "duplicate_object",
            description = "Duplicate the selected object or a specific object.",
            parameters = listOf(objectIdParameter(required = false)),
            resultDescription = "Returns the source object id and new duplicate object id.",
            errorDescriptions = listOf("no_current_selection", "object_not_found", "object_locked", "selection_rejected", "duplicate_failed"),
        ),
        AgentTool(
            name = "add_page",
            description = "Add a new page after the current page in a presentation document. Not supported in whiteboard documents.",
            parameters = emptyList(),
            resultDescription = "Returns the new current page index and total page count.",
            errorDescriptions = listOf("unsupported_document_type", "page_limit_reached", "page_update_failed"),
        ),
        AgentTool(
            name = "duplicate_page",
            description = "Duplicate the current page in a presentation document. Not supported in whiteboard documents.",
            parameters = emptyList(),
            resultDescription = "Returns the new current page index and total page count.",
            errorDescriptions = listOf("unsupported_document_type", "page_limit_reached", "page_update_failed"),
        ),
        AgentTool(
            name = "delete_page",
            description = "Delete the current page in a presentation document. Not supported in whiteboard documents, and the last page cannot be deleted.",
            parameters = emptyList(),
            resultDescription = "Returns the remaining current page index and total page count.",
            errorDescriptions = listOf("unsupported_document_type", "cannot_delete_last_page", "page_update_failed"),
        ),
        AgentTool(
            name = "goto_page",
            description = "Navigate to a one-based page index in a presentation document. Not supported in whiteboard documents.",
            parameters = listOf(
                AgentToolParameter(
                    name = "index",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "One-based target page index. Use 1 for the first page.",
                ),
            ),
            resultDescription = "Returns the current page index and total page count.",
            errorDescriptions = listOf("missing_parameter", "invalid_parameter", "unsupported_document_type", "invalid_page_index", "page_update_failed"),
        ),
        AgentTool(
            name = "enter_present_mode",
            description = "Enter present mode for a presentation document. Not supported in whiteboard documents.",
            parameters = emptyList(),
            resultDescription = "Returns the current editor mode.",
            errorDescriptions = listOf("unsupported_document_type", "mode_update_failed"),
        ),
        AgentTool(
            name = "exit_present_mode",
            description = "Exit present mode and return to edit mode.",
            parameters = emptyList(),
            resultDescription = "Returns the current editor mode.",
            errorDescriptions = listOf("not_in_present_mode", "mode_update_failed"),
        ),
        AgentTool(
            name = "add_viewpoint",
            description = "Add a viewpoint marker to the current presentation page or whiteboard space.",
            parameters = emptyList(),
            resultDescription = "Returns the new viewpoint object id, type, display name, and current page info.",
            errorDescriptions = listOf("add_failed"),
        ),
        AgentTool(
            name = "enter_viewpoint",
            description = "Enter first-person viewpoint mode through the selected viewpoint object or a specific viewpoint object.",
            parameters = listOf(objectIdParameter(required = false)),
            resultDescription = "Returns the viewpoint object id.",
            errorDescriptions = listOf("no_current_selection", "object_not_found", "invalid_object_type", "object_locked", "object_not_visible", "mode_update_failed"),
        ),
        AgentTool(
            name = "exit_viewpoint",
            description = "Exit viewpoint mode and return to the normal editor view.",
            parameters = emptyList(),
            resultDescription = "Returns the viewpoint object id that was exited.",
            errorDescriptions = listOf("not_in_viewpoint", "mode_update_failed"),
        ),
        AgentTool(
            name = "set_animation",
            description = "Create or update a page animation for the selected object or a specific object. Not supported for viewpoint objects. If start/end are omitted, the editor default animation path is used.",
            parameters = listOf(
                objectIdParameter(required = false),
                animationTransformParameter(
                    name = "start",
                    description = "Optional animation start transform. Omitted fields keep the current/default start endpoint value.",
                ),
                animationTransformParameter(
                    name = "end",
                    description = "Optional animation end transform. Omitted fields keep the current/default end endpoint value.",
                ),
                AgentToolParameter(
                    name = "duration_seconds",
                    type = AgentParameterType.NUMBER,
                    required = false,
                    description = "Optional duration in seconds, from 0.5 to 12.",
                ),
                AgentToolParameter(
                    name = "easing",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Optional animation easing.",
                    enumValues = listOf("linear", "ease_in", "ease_out", "ease_in_out"),
                ),
                AgentToolParameter(
                    name = "loop_mode",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Optional animation loop mode.",
                    enumValues = listOf("none", "ping_pong", "restart"),
                ),
            ),
            resultDescription = "Returns the target object id, display name, start/end transforms, duration, easing, and loop mode.",
            errorDescriptions = listOf(
                "not_in_edit_mode",
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "selection_rejected",
                "invalid_parameter",
                "invalid_animation_easing",
                "invalid_animation_loop_mode",
                "animation_update_failed",
            ),
        ),
        AgentTool(
            name = "delete_animation",
            description = "Delete the page animation from the selected object or a specific object.",
            parameters = listOf(objectIdParameter(required = false)),
            resultDescription = "Returns the target object id.",
            errorDescriptions = listOf(
                "not_in_edit_mode",
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "selection_rejected",
                "animation_not_found",
                "animation_update_failed",
            ),
        ),
        AgentTool(
            name = "toggle_animation_playback",
            description = "Pause or resume current-page animation playback.",
            parameters = emptyList(),
            resultDescription = "Returns animations_paused.",
            errorDescriptions = listOf("animation_not_found", "animation_update_failed"),
        ),
        AgentTool(
            name = "list_documents",
            description = "List all documents/shows. Use this before switching, renaming, duplicating, or deleting a document if the target index is unknown.",
            parameters = emptyList(),
            resultDescription = "Returns one-based document indexes, names, types, object/page counts, selected state, active document index, document count, and current screen.",
            errorDescriptions = emptyList(),
        ),
        AgentTool(
            name = "create_document",
            description = "Create a new blank presentation or whiteboard document and open it.",
            parameters = listOf(
                AgentToolParameter(
                    name = "type",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "Document type to create.",
                    enumValues = listOf("presentation", "whiteboard"),
                ),
            ),
            resultDescription = "Returns the new current document index, name, type, and document count.",
            errorDescriptions = listOf("missing_parameter", "invalid_document_type", "document_update_failed"),
        ),
        AgentTool(
            name = "switch_document",
            description = "Switch/open a document by one-based index. Use 1 for the first document.",
            parameters = listOf(
                AgentToolParameter(
                    name = "index",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "One-based document index from list_documents or active_document_index in the scene summary.",
                ),
            ),
            resultDescription = "Returns the active document index, name, type, and current screen.",
            errorDescriptions = listOf("missing_parameter", "invalid_parameter", "invalid_document_index", "document_update_failed"),
        ),
        AgentTool(
            name = "rename_document",
            description = "Rename a document by one-based index. For the current document, use active_document_index from the scene summary.",
            parameters = listOf(
                AgentToolParameter(
                    name = "index",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "One-based document index.",
                ),
                AgentToolParameter(
                    name = "name",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "New document name. It must not be blank.",
                ),
            ),
            resultDescription = "Returns the renamed document index, name, type, and active document info.",
            errorDescriptions = listOf("missing_parameter", "invalid_parameter", "invalid_document_index", "document_name_empty", "document_update_failed"),
        ),
        AgentTool(
            name = "duplicate_document",
            description = "Duplicate a document by one-based index. The copy is inserted after the source document.",
            parameters = listOf(
                AgentToolParameter(
                    name = "index",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "One-based source document index.",
                ),
            ),
            resultDescription = "Returns the duplicated document index, name, type, and active document info.",
            errorDescriptions = listOf("missing_parameter", "invalid_parameter", "invalid_document_index", "document_update_failed"),
        ),
        AgentTool(
            name = "delete_document",
            description = "Delete a document by one-based index. The last remaining document cannot be deleted by Agent.",
            parameters = listOf(
                AgentToolParameter(
                    name = "index",
                    type = AgentParameterType.NUMBER,
                    required = true,
                    description = "One-based document index to delete.",
                ),
            ),
            resultDescription = "Returns the removed document index/name, remaining document count, and active document info.",
            errorDescriptions = listOf("missing_parameter", "invalid_parameter", "invalid_document_index", "cannot_delete_last_document", "document_update_failed"),
        ),
        AgentTool(
            name = "return_home",
            description = "Return from the editor to the home document list.",
            parameters = emptyList(),
            resultDescription = "Returns the current screen and active document info.",
            errorDescriptions = listOf("screen_update_failed"),
        ),
        AgentTool(
            name = "get_scene_summary",
            description = "Summarize the current document, page, object count, mode, selection, selected transform/capabilities, and a small current-page object list.",
            parameters = emptyList(),
            resultDescription = "Returns document type, page info, counts, mode, selected object info with transform, selection capabilities, and up to 12 current-page objects.",
            errorDescriptions = emptyList(),
        ),
        AgentTool(
            name = "list_objects",
            description = "List objects on the current page, optionally filtered by object type.",
            parameters = listOf(
                AgentToolParameter(
                    name = "type",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Optional object type filter.",
                    enumValues = objectTypeNames,
                ),
            ),
            resultDescription = "Returns ids, types, display names, visibility, and selected state.",
            errorDescriptions = listOf("invalid_object_type"),
        ),
        AgentTool(
            name = "select_object",
            description = "Select an object by id so later tools can omit object_id.",
            parameters = listOf(objectIdParameter(required = true)),
            resultDescription = "Returns selected object info.",
            errorDescriptions = listOf("missing_parameter", "object_not_found", "selection_rejected"),
        ),
        AgentTool(
            name = "undo",
            description = "Undo the last editor operation.",
            parameters = emptyList(),
            resultDescription = "Returns whether undo succeeded and the remaining undo/redo availability.",
            errorDescriptions = listOf("nothing_to_undo"),
        ),
        AgentTool(
            name = "redo",
            description = "Redo the last undone editor operation.",
            parameters = emptyList(),
            resultDescription = "Returns whether redo succeeded and the remaining undo/redo availability.",
            errorDescriptions = listOf("nothing_to_redo"),
        ),
        AgentTool(
            name = "generate_image_from_text",
            description = "Generate one image from a text prompt with Seedream and add it to the current scene. Use this only when the user explicitly asks to create or generate an image.",
            parameters = listOf(
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "Image generation prompt. Keep the user's subject and style request.",
                ),
            ),
            resultDescription = "Returns the new image object id, display name, and local image source.",
            errorDescriptions = listOf("missing_parameter", "missing_seedream_key", "generation_failed", "add_failed"),
        ),
        AgentTool(
            name = "generate_image_from_image",
            description = "Generate one new image from an existing image object plus a text prompt with Seedream, then add the new image next to the source image. Use this only when the user explicitly asks to modify, restyle, or create a new image based on an existing image.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "Image-to-image generation prompt. Describe the requested change, style, or output while preserving the source subject if the user asks for that.",
                ),
            ),
            resultDescription = "Returns the source image object id, new image object id, display name, and local image source.",
            errorDescriptions = listOf(
                "missing_parameter",
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "source_unreadable",
                "generation_in_progress",
                "missing_seedream_key",
                "generation_failed",
                "add_failed",
            ),
        ),
        AgentTool(
            name = "generate_image_from_stroke",
            description = "Generate one new image from an existing stroke object with Seedream, then add the new image next to the source stroke. Use this only when the user explicitly asks to turn a drawing, sketch, stroke, or three-view line art into an image.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = strokePromptDescription,
                ),
            ),
            resultDescription = "Returns the source stroke object id, new image object id, display name, and local image source.",
            errorDescriptions = listOf(
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "invalid_stroke",
                "generation_in_progress",
                "missing_seedream_key",
                "lineart_failed",
                "lineart_unreadable",
                "generation_failed",
                "add_failed",
            ),
        ),
        AgentTool(
            name = "generate_model_from_text",
            description = "Generate one 3D model from a text prompt with Rodin and add it to the current scene. Use this only when the user explicitly asks to create or generate a 3D model.",
            parameters = listOf(
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = true,
                    description = "3D model generation prompt. Keep the user's subject, style, and material request.",
                ),
            ) + rodinOptionParameters(),
            resultDescription = "Returns the new model object id, display name, and local model source.",
            errorDescriptions = listOf(
                "missing_parameter",
                "invalid_generation_option",
                "generation_in_progress",
                "missing_rodin_key",
                "generation_failed",
                "add_failed",
            ),
        ),
        AgentTool(
            name = "generate_model_from_image",
            description = "Generate one 3D model from an existing image object with Rodin and replace that image object with the generated model. Use this only when the user explicitly asks to turn a selected image or photo into a 3D model.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Optional extra 3D generation prompt. If omitted, Rodin uses the image as the main input.",
                ),
                AgentToolParameter(
                    name = "preprocess",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = "Whether to preprocess the image before Rodin. Use local_subject only when the user asks to remove background, isolate the subject, or keep a clean subject; otherwise omit or use none.",
                    enumValues = listOf("none", "local_subject"),
                ),
            ) + rodinOptionParameters(),
            resultDescription = "Returns the replaced object id, display name, and local model source.",
            errorDescriptions = listOf(
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "source_unreadable",
                "generation_in_progress",
                "missing_rodin_key",
                "invalid_preprocess",
                "invalid_generation_option",
                "preprocess_failed",
                "generation_failed",
                "replace_failed",
            ),
        ),
        AgentTool(
            name = "generate_model_from_stroke",
            description = "Generate one 3D model from an existing stroke object by first turning the stroke into a clean image with Seedream, then generating a 3D model with Rodin. Use this only when the user explicitly asks to turn a drawing, sketch, stroke, or three-view line art into a 3D model.",
            parameters = listOf(
                objectIdParameter(required = false),
                AgentToolParameter(
                    name = "prompt",
                    type = AgentParameterType.STRING,
                    required = false,
                    description = strokePromptDescription,
                ),
            ) + rodinOptionParameters(),
            resultDescription = "Returns the replaced stroke object id, generated image source, display name, and local model source.",
            errorDescriptions = listOf(
                "no_current_selection",
                "object_not_found",
                "invalid_object_type",
                "object_locked",
                "invalid_stroke",
                "generation_in_progress",
                "missing_seedream_rodin_key",
                "missing_seedream_key",
                "missing_rodin_key",
                "invalid_generation_option",
                "lineart_failed",
                "lineart_unreadable",
                "seedream_failed",
                "seedream_unreadable",
                "generation_failed",
                "replace_failed",
            ),
        ),
    )

    private val toolsByName: Map<String, AgentTool> = tools.associateBy { it.name }

    fun find(name: String): AgentTool? = toolsByName[name]

    fun require(name: String): AgentTool =
        find(name) ?: error("Agent tool is not registered: $name")

    fun functionSpecs(): List<Map<String, Any>> =
        tools.map { it.toFunctionSpec() }

    private fun objectIdParameter(required: Boolean): AgentToolParameter =
        AgentToolParameter(
            name = "object_id",
            type = AgentParameterType.STRING,
            required = required,
            description = "Stable object id. If omitted, the current selected object is used.",
        )

    private fun numberParameter(
        name: String,
        required: Boolean,
        description: String,
    ): AgentToolParameter =
        AgentToolParameter(
            name = name,
            type = AgentParameterType.NUMBER,
            required = required,
            description = description,
        )

    private fun animationTransformParameter(name: String, description: String): AgentToolParameter =
        AgentToolParameter(
            name = name,
            type = AgentParameterType.OBJECT,
            required = false,
            description = description,
            properties = listOf(
                AgentToolParameter(
                    name = "position",
                    type = AgentParameterType.OBJECT,
                    required = false,
                    description = "Optional endpoint position in meters. Omitted components keep their current/default values.",
                    properties = listOf(
                        numberParameter("x", required = false, description = "Endpoint X position in meters."),
                        numberParameter("y", required = false, description = "Endpoint Y position in meters."),
                        numberParameter("z", required = false, description = "Endpoint Z position in meters."),
                    ),
                ),
                AgentToolParameter(
                    name = "rotation",
                    type = AgentParameterType.OBJECT,
                    required = false,
                    description = "Optional endpoint rotation. Current editor supports only y in radians.",
                    properties = listOf(
                        numberParameter("y", required = false, description = "Endpoint Y rotation in radians."),
                    ),
                ),
                AgentToolParameter(
                    name = "scale",
                    type = AgentParameterType.NUMBER,
                    required = false,
                    description = "Optional endpoint uniform scale, from 0.08 to 10.",
                ),
            ),
        )

    private fun rodinOptionParameters(): List<AgentToolParameter> =
        listOf(
            AgentToolParameter(
                name = "rodin_tier",
                type = AgentParameterType.STRING,
                required = false,
                description = "Optional Rodin Gen-2.5 quality/model tier. Use only when the user asks for speed/quality level. Default keeps the current editor setting.",
                enumValues = AgentGenerationOptionParser.rodinTierSchemaValues,
            ),
            AgentToolParameter(
                name = "face_budget",
                type = AgentParameterType.STRING,
                required = false,
                description = "Optional triangle face budget. Use only when the user explicitly asks for a face count such as 4K, 20K, 50K, or low/high poly. Default keeps the current editor setting.",
                enumValues = AgentGenerationOptionParser.faceBudgetSchemaValues,
            ),
            AgentToolParameter(
                name = "texture",
                type = AgentParameterType.STRING,
                required = false,
                description = "Optional texture quality. Use hd only when the user asks for HD/high-resolution texture; use standard for normal texture. No-texture export is not supported by the current Rodin path.",
                enumValues = AgentGenerationOptionParser.textureSchemaValues,
            ),
            AgentToolParameter(
                name = "creative",
                type = AgentParameterType.BOOLEAN,
                required = false,
                description = "Optional Rodin geometry instruct mode. true means creative; false means faithful. Omit unless the user explicitly asks for creative/imaginative generation or faithful preservation.",
            ),
        )
}
