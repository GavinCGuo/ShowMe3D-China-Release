package com.example.showme3d.studio.agent

import java.util.Locale
import kotlin.math.PI

object AgentUserMessageFormatter {
    fun format(result: AgentRunResult): String =
        when (result.status) {
            AgentRunStatus.SUCCESS -> formatSuccess(result)
            AgentRunStatus.NO_TOOL -> formatNoTool(result)
            AgentRunStatus.MULTIPLE_TOOLS -> "未执行：${result.message.ifBlank { "一次请少于 20 步，请拆短一点。" }}"
            AgentRunStatus.TOOL_FAILED -> formatToolFailure(result)
            AgentRunStatus.MODEL_ERROR -> "AI 请求失败：${formatModelError(result.message)}"
        }

    fun toolDisplayName(toolName: String?): String =
        when (toolName) {
            "add_object" -> "添加物体"
            "add_text" -> "添加文字"
            "edit_text" -> "修改文字"
            "set_color" -> "设置颜色"
            "get_transform" -> "读取位置"
            "set_transform" -> "调整位置/旋转/缩放"
            "move_object_relative" -> "相对移动"
            "rotate_object_relative" -> "相对旋转"
            "scale_object_relative" -> "相对缩放"
            "delete_object" -> "删除对象"
            "clear_current_page" -> "清空当前页"
            "group_all_models" -> "组合全部模型"
            "ungroup_all" -> "全部解组"
            "duplicate_object" -> "复制对象"
            "add_page" -> "新增页面"
            "duplicate_page" -> "复制页面"
            "delete_page" -> "删除页面"
            "goto_page" -> "跳转页面"
            "enter_present_mode" -> "进入演示"
            "exit_present_mode" -> "退出演示"
            "add_viewpoint" -> "添加视点"
            "enter_viewpoint" -> "进入视点"
            "exit_viewpoint" -> "退出视点"
            "set_animation" -> "设置动画"
            "delete_animation" -> "删除动画"
            "toggle_animation_playback" -> "暂停/继续动画"
            "list_documents" -> "列出作品"
            "create_document" -> "新建作品"
            "switch_document" -> "切换作品"
            "rename_document" -> "重命名作品"
            "duplicate_document" -> "复制作品"
            "delete_document" -> "删除作品"
            "return_home" -> "返回主页"
            "get_scene_summary" -> "读取场景摘要"
            "list_objects" -> "列出对象"
            "select_object" -> "选中对象"
            "undo" -> "撤销"
            "redo" -> "重做"
            "generate_image_from_text" -> "文生图"
            "generate_image_from_image" -> "图生图"
            "generate_image_from_stroke" -> "笔画成图"
            "generate_model_from_text" -> "文生 3D"
            "generate_model_from_image" -> "图生 3D"
            "generate_model_from_stroke" -> "笔画生成 3D"
            null -> "工具"
            else -> toolName
        }

    fun errorHint(errorCode: String?, toolName: String?, fallback: String): String {
        val hint = when (errorCode) {
            "unknown_tool" -> "豆包选择了未注册工具，已停止执行。"
            "not_implemented" -> "这个工具还没有实现，已停止执行。"
            "missing_parameter" -> missingParameterHint(toolName)
            "invalid_preset" -> "没有找到这个内置物体。可试：加一个立方体 / 球体。"
            "add_failed" -> "资源已生成或动作已触发，但没有成功加入当前场景。"
            "text_update_failed" -> "文字对象已找到，但内容没有成功写入。"
            "no_current_selection" -> "没有当前选中对象。请先选中目标，或先用预输入按钮创建一个对象。"
            "object_not_found" -> "目标对象不在当前页或已经被删除。"
            "invalid_object_type" -> invalidObjectTypeHint(toolName)
            "object_locked" -> "目标对象已锁定，未执行。"
            "invalid_color" -> "暂不支持这个颜色。可试红色、蓝色、绿色、黑色或白色。"
            "selection_rejected" -> "编辑器当前不允许切换到这个对象。"
            "missing_transform" -> "缺少位置、旋转或缩放目标。请明确说放大、移动或旋转到多少。"
            "invalid_parameter" -> "参数超出安全范围或格式不正确，已拒绝执行。"
            "unsupported_rotation_axis" -> "当前只支持绕 Y 轴旋转。"
            "delete_failed" -> "对象删除失败，场景未改变。"
            "duplicate_failed" -> "对象复制失败，场景未改变。"
            "insufficient_models" -> "当前页面至少需要两个可见的 3D 模型才能组合。"
            "unsupported_document_type" -> unsupportedDocumentTypeHint(toolName)
            "page_limit_reached" -> "页面已达到上限，未新增页面。"
            "cannot_delete_last_page" -> "当前只剩一页，不能删除。"
            "invalid_page_index" -> "目标页码超出范围。"
            "page_update_failed" -> "页面操作没有成功生效。"
            "mode_update_failed" -> "模式切换没有成功生效。"
            "not_in_present_mode" -> "当前不在演示态。"
            "not_in_viewpoint" -> "当前不在视点态。"
            "object_not_visible" -> "目标对象当前不可见。"
            "not_in_edit_mode" -> "当前不是普通编辑态，请先退出演示或视点后再操作。"
            "animation_not_found" -> "当前页没有动画，或目标对象还没有动画。"
            "animation_update_failed" -> "动画操作没有成功生效。"
            "invalid_animation_easing" -> "动画缓动不支持。可用：linear、ease_in、ease_out、ease_in_out。"
            "invalid_animation_loop_mode" -> "动画循环模式不支持。可用：none、ping_pong、restart。"
            "invalid_document_type" -> "作品类型不支持。可用：演示文档或白板。"
            "invalid_document_index" -> "目标作品序号超出范围。"
            "document_name_empty" -> "作品名不能为空。"
            "cannot_delete_last_document" -> "最后一个作品不能由 Agent 删除。"
            "document_update_failed" -> "作品操作没有成功生效。"
            "screen_update_failed" -> "页面切换没有成功生效。"
            "nothing_to_undo" -> "现在没有可撤销的操作。"
            "nothing_to_redo" -> "现在没有可重做的操作。"
            "source_unreadable" -> "无法读取源图片文件，请重新导入或换一张图片。"
            "invalid_stroke" -> "笔画点数不足，先画出完整线稿再生成。"
            "generation_in_progress" -> "已有生成任务在执行，请等当前任务结束后再试。"
            "missing_seedream_rodin_key" -> "Seedream 和 Rodin Key 都未配置，请先保存 Key。"
            "missing_seedream_key" -> "Seedream Key 未配置，请先保存 Key。"
            "missing_rodin_key" -> "Rodin Key 未配置，请先保存 Key。"
            "invalid_preprocess" -> "图片前处理参数不支持。本期只支持不处理或本地主体抠图。"
            "invalid_generation_option" -> "生成参数不支持。面数、贴图或 Rodin 档位需要使用面板里的可选值。"
            "preprocess_failed" -> "图片前处理失败，原图没有改变。"
            "lineart_failed" -> "主视图参考图生成失败，原笔画没有改变。"
            "lineart_unreadable" -> "主视图参考图文件无法读取，原笔画没有改变。"
            "seedream_failed" -> "Seedream 成图失败，原笔画没有改变。"
            "seedream_unreadable" -> "Seedream 生成图无法读取，原笔画没有改变。"
            "generation_failed" -> generationFailedHint(toolName, fallback)
            "replace_failed" -> "生成结果已保存，但当前对象没有成功替换。"
            else -> fallback.ifBlank { "工具执行失败。" }
        }
        return if (errorCode.isNullOrBlank()) hint else "$hint（$errorCode）"
    }

    private fun formatSuccess(result: AgentRunResult): String {
        if (result.stepResults.size > 1) {
            val labels = result.stepResults.joinToString("、") { toolDisplayName(it.toolCall.name) }
            val undoText = if (result.batchUndoCreated) "可一次撤销。" else ""
            return "已执行 ${result.stepResults.size} 步：$labels。全部完成。$undoText"
        }
        val toolName = result.toolCall?.name
        val tool = result.toolResult
        val undoText = if (result.batchUndoCreated) "可撤销。" else ""
        return "已执行：${toolDisplayName(toolName)}。${successDetail(toolName, tool)}$undoText"
    }

    private fun successDetail(toolName: String?, tool: AgentToolResult?): String {
        val data = tool?.data.orEmpty()
        val displayName = data["display_name"]?.toString()?.takeIf { it.isNotBlank() }
        val text = data["text"]?.toString()?.takeIf { it.isNotBlank() }
        return when (toolName) {
            "add_object" -> displayName?.let { "已添加 $it。" } ?: "已添加物体。"
            "add_text" -> text?.let { "已添加文字：${it.shortForUi()}。" } ?: "已添加文字。"
            "edit_text" -> text?.let { "已改为：${it.shortForUi()}。" } ?: "文字已更新。"
            "set_color" -> "颜色已更新。"
            "get_transform" -> transformDetail(data)
            "set_transform" -> "位置、旋转或缩放已更新。"
            "move_object_relative" -> "对象已按相对距离移动。"
            "rotate_object_relative" -> "对象已按相对角度旋转。"
            "scale_object_relative" -> "对象已按相对比例缩放。"
            "delete_object" -> "对象已删除。"
            "clear_current_page" -> clearCurrentPageDetail(data)
            "group_all_models" -> "已将 ${(data["grouped_count"] as? Number)?.toInt() ?: 0} 个模型组合为一个组。"
            "ungroup_all" -> "已解除 ${(data["removed_group_count"] as? Number)?.toInt() ?: 0} 个组，共 ${(data["ungrouped_count"] as? Number)?.toInt() ?: 0} 个对象。"
            "duplicate_object" -> displayName?.let { "已复制为 $it。" } ?: "对象已复制。"
            "add_page" -> pageDetail(data, prefix = "已新增页面")
            "duplicate_page" -> pageDetail(data, prefix = "已复制当前页")
            "delete_page" -> pageDetail(data, prefix = "已删除当前页")
            "goto_page" -> pageDetail(data, prefix = "已跳转")
            "enter_present_mode" -> "已进入演示态。"
            "exit_present_mode" -> "已退出演示态。"
            "add_viewpoint" -> displayName?.let { "已添加 $it。" } ?: "已添加视点。"
            "enter_viewpoint" -> "已进入视点。"
            "exit_viewpoint" -> "已退出视点。"
            "set_animation" -> animationDetail(data)
            "delete_animation" -> "动画已删除。"
            "toggle_animation_playback" -> animationPlaybackDetail(data)
            "list_documents" -> documentListDetail(data)
            "create_document" -> activeDocumentDetail(data, prefix = "已创建并打开")
            "switch_document" -> activeDocumentDetail(data, prefix = "已打开")
            "rename_document" -> documentDetail(data, prefix = "已重命名为")
            "duplicate_document" -> documentDetail(data, prefix = "已复制为")
            "delete_document" -> deletedDocumentDetail(data)
            "return_home" -> "已返回主页。"
            "get_scene_summary" -> sceneSummaryDetail(data)
            "list_objects" -> {
                val count = (data["objects"] as? List<*>)?.size
                count?.let { objectListDetail(data) } ?: "对象列表已读取。"
            }
            "select_object" -> displayName?.let { "已选中 $it。" } ?: "对象已选中。"
            "undo" -> "已撤销上一步。"
            "redo" -> "已重做上一步。"
            "generate_image_from_text",
            "generate_image_from_image",
            "generate_image_from_stroke",
            "generate_model_from_text",
            "generate_model_from_image",
            "generate_model_from_stroke",
            -> tool?.message?.takeIf { it.isNotBlank() }?.ensureTerminalPunctuation() ?: "生成完成。"
            else -> tool?.message?.takeIf { it.isNotBlank() }?.ensureTerminalPunctuation() ?: "动作完成。"
        }
    }

    private fun formatNoTool(result: AgentRunResult): String {
        val text = result.message.ifBlank { result.assistantText }
        val suffix = text.ifBlank { "豆包没有选择可执行工具。请换成更明确的指令。" }
        return "未执行：${suffix.ensureTerminalPunctuation()}"
    }

    private fun formatModelError(message: String): String {
        val text = message.ifBlank { "请检查网络、模型名或豆包 Key。" }
        val lower = text.lowercase(Locale.US)
        return when {
            "timeout" in lower || "timed out" in lower ->
                "AI 服务响应超时，已自动重试一次；请稍后再试。"
            "unable to resolve host" in lower ||
                "no address associated with hostname" in lower ||
                "network is unreachable" in lower ->
                "设备当前无法连接网络或解析豆包域名，请先连接 Wi-Fi 后重试。"
            else -> text
        }
    }

    private fun formatToolFailure(result: AgentRunResult): String {
        if (result.rolledBack && result.stepResults.isNotEmpty()) {
            val failed = result.stepResults.last()
            val hint = errorHint(
                errorCode = failed.toolResult.errorCode,
                toolName = failed.toolCall.name,
                fallback = failed.toolResult.message,
            )
            return "未完成：第 ${failed.index}/${failed.total} 步“${toolDisplayName(failed.toolCall.name)}”失败：$hint 已回滚到执行前。"
        }
        if (result.stepResults.size > 1) {
            val failed = result.stepResults.last()
            val hint = errorHint(
                errorCode = failed.toolResult.errorCode,
                toolName = failed.toolCall.name,
                fallback = failed.toolResult.message,
            )
            val completed = result.stepResults.count { it.toolResult.success }
            val completedText = if (completed > 0) "已完成前 $completed 步，" else ""
            return "未完成：${completedText}第 ${failed.index}/${failed.total} 步“${toolDisplayName(failed.toolCall.name)}”失败：$hint"
        }
        val tool = result.toolResult
        val hint = errorHint(
            errorCode = tool?.errorCode,
            toolName = result.toolCall?.name,
            fallback = tool?.message ?: result.message,
        )
        return "未执行：$hint"
    }

    private fun missingParameterHint(toolName: String?): String =
        when (toolName) {
            "add_object" -> "缺少要添加的物体类型。"
            "add_text",
            "edit_text",
            -> "缺少文字内容。"
            "set_color" -> "缺少目标颜色。"
            "rotate_object_relative" -> "缺少相对旋转角度。"
            "scale_object_relative" -> "缺少相对缩放比例。"
            "goto_page" -> "缺少目标页码。"
            "create_document" -> "缺少要新建的作品类型。"
            "switch_document",
            "duplicate_document",
            "delete_document",
            -> "缺少目标作品序号。"
            "rename_document" -> "缺少目标作品序号或新作品名。"
            "generate_image_from_text",
            "generate_image_from_image",
            -> "缺少图片生成提示词。"
            "generate_model_from_text" -> "缺少 3D 模型生成提示词。"
            else -> "参数不完整，已停止执行。"
        }

    private fun unsupportedDocumentTypeHint(toolName: String?): String =
        when (toolName) {
            "add_page",
            "duplicate_page",
            "delete_page",
            "goto_page",
            "enter_present_mode",
            -> "当前是白板作品，不支持页面或演示操作。"
            else -> "当前作品类型不支持这个操作。"
        }

    private fun invalidObjectTypeHint(toolName: String?): String =
        when (toolName) {
            "edit_text" -> "选中的不是文字对象，请先选中文字。"
            "set_color" -> "选中的对象不支持改色。图片和外部模型暂不支持直接改色。"
            "generate_image_from_image",
            "generate_model_from_image",
            -> "选中的不是图片，请先选中图片。"
            "generate_image_from_stroke",
            "generate_model_from_stroke",
            -> "选中的不是笔画，请先选中一段笔画。"
            "enter_viewpoint" -> "选中的不是视点，请先选中视点小人。"
            "set_animation",
            "delete_animation",
            -> "选中的对象不能设置动画，视点不支持动画。"
            else -> "选中的对象类型不适合这个动作。"
        }

    private fun generationFailedHint(toolName: String?, fallback: String): String {
        val lower = fallback.lowercase(Locale.US)
        if (
            "failed to connect" in lower ||
            "unable to resolve host" in lower ||
            "network is unreachable" in lower ||
            "timeout" in lower ||
            "timed out" in lower
        ) {
            return "生成服务或图片下载连接超时，已自动重试；请检查网络后再试，场景没有被破坏。"
        }
        return when (toolName) {
            "generate_model_from_text",
            "generate_model_from_image",
            "generate_model_from_stroke",
            -> "Rodin 生成失败，场景没有被破坏。"
            "generate_image_from_text",
            "generate_image_from_image",
            "generate_image_from_stroke",
            -> "Seedream 生成失败，场景没有被破坏。"
            else -> "生成失败，场景没有被破坏。"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pageDetail(data: Map<String, Any?>, prefix: String): String {
        val pageIndex = data["page_index"].asInt()
        val pageCount = data["page_count"].asInt()
        return if (pageIndex != null && pageCount != null) {
            "$prefix：第 $pageIndex/$pageCount 页。"
        } else {
            "$prefix。"
        }
    }

    private fun clearCurrentPageDetail(data: Map<String, Any?>): String {
        val removedCount = data["removed_count"].asInt() ?: 0
        val pageText = data["page_index"].asInt()?.let { "第 $it 页" } ?: "当前页"
        return if (removedCount == 0) {
            "$pageText 本来就是空的。"
        } else {
            "$pageText 已清空 $removedCount 个物体。"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun documentListDetail(data: Map<String, Any?>): String {
        val documents = data["documents"] as? List<Map<String, Any?>> ?: return "作品列表已读取。"
        val activeIndex = data["active_document_index"].asInt()
        val activeName = data["active_document_name"]?.toString()?.shortForUi(maxLength = 16)
        val count = data["document_count"].asInt() ?: documents.size
        return if (activeIndex != null && activeName != null) {
            "已列出 $count 个作品。当前是第 $activeIndex 个：$activeName。"
        } else {
            "已列出 $count 个作品。"
        }
    }

    private fun activeDocumentDetail(data: Map<String, Any?>, prefix: String): String {
        val name = data["active_document_name"]?.toString()?.takeIf { it.isNotBlank() }?.shortForUi(maxLength = 18)
        val index = data["active_document_index"].asInt()
        val count = data["document_count"].asInt()
        val countText = count?.let { "当前共有 $it 个作品。" }.orEmpty()
        return when {
            name != null && index != null -> "$prefix：第 $index 个，$name。$countText"
            name != null -> "$prefix：$name。$countText"
            else -> "$prefix。$countText"
        }
    }

    private fun documentDetail(data: Map<String, Any?>, prefix: String): String {
        val name = data["document_name"]?.toString()?.takeIf { it.isNotBlank() }?.shortForUi(maxLength = 18)
        val index = data["document_index"].asInt()
        return when {
            name != null && index != null -> "$prefix：第 $index 个，$name。"
            name != null -> "$prefix：$name。"
            else -> "$prefix。"
        }
    }

    private fun deletedDocumentDetail(data: Map<String, Any?>): String {
        val name = data["removed_document_name"]?.toString()?.takeIf { it.isNotBlank() }?.shortForUi(maxLength = 18)
        val count = data["document_count"].asInt()
        val countText = count?.let { "剩余 $it 个作品。" }.orEmpty()
        return if (name != null) "已删除：$name。$countText" else "已删除作品。$countText"
    }

    private fun animationDetail(data: Map<String, Any?>): String {
        val duration = data["duration_seconds"].asDouble()?.formatNumber()
        val easing = animationEasingLabel(data["easing"]?.toString())
        val loopMode = animationLoopModeLabel(data["loop_mode"]?.toString())
        val parts = buildList {
            duration?.let { add("${it}秒") }
            easing?.let { add(it) }
            loopMode?.let { add(it) }
        }
        return if (parts.isEmpty()) "动画已设置。" else "动画已设置：${parts.joinToString("，")}。"
    }

    private fun animationPlaybackDetail(data: Map<String, Any?>): String =
        if (data["animations_paused"] == true) "动画已暂停。" else "动画已继续播放。"

    @Suppress("UNCHECKED_CAST")
    private fun sceneSummaryDetail(data: Map<String, Any?>): String {
        val pageIndex = data["page_index"].asInt()
        val pageCount = data["page_count"].asInt()
        val objectCount = data["object_count"].asInt()
        val selected = data["selected_object"] as? Map<String, Any?>
        val pageText = when {
            pageIndex != null && pageCount != null && objectCount != null -> "第 $pageIndex/$pageCount 页有 $objectCount 个对象。"
            objectCount != null -> "当前页有 $objectCount 个对象。"
            else -> "场景摘要已读取。"
        }
        val selectedText = selected?.let { "当前选中 ${objectLabel(it)}。" } ?: "当前未选中对象。"
        val undoText = undoRedoText(data["can_undo"] as? Boolean, data["can_redo"] as? Boolean)
        return pageText + selectedText + undoText
    }

    @Suppress("UNCHECKED_CAST")
    private fun objectListDetail(data: Map<String, Any?>): String {
        val objects = data["objects"] as? List<Map<String, Any?>> ?: return "对象列表已读取。"
        if (objects.isEmpty()) return "当前页没有匹配对象。"
        val visibleObjects = objects.take(MAX_OBJECTS_IN_MESSAGE)
        val labels = visibleObjects.joinToString("、") { objectLabel(it) }
        val suffix = if (objects.size > visibleObjects.size) "，另有 ${objects.size - visibleObjects.size} 个未显示" else ""
        return "当前页找到 ${objects.size} 个对象：$labels$suffix。"
    }

    @Suppress("UNCHECKED_CAST")
    private fun transformDetail(data: Map<String, Any?>): String {
        val transform = data["transform"] as? Map<String, Any?> ?: return "已读取位置、旋转和缩放。"
        val position = transform["position"] as? Map<String, Any?>
        val rotation = transform["rotation"] as? Map<String, Any?>
        val x = position?.get("x").asDouble()?.formatNumber()
        val y = position?.get("y").asDouble()?.formatNumber()
        val z = position?.get("z").asDouble()?.formatNumber()
        val degrees = rotation?.get("y").asDouble()?.let { (it * 180.0 / PI).formatNumber() }
        val scale = transform["scale"].asDouble()?.formatNumber()
        val parts = buildList {
            if (x != null && y != null && z != null) add("位置 x=$x、y=$y、z=$z")
            degrees?.let { add("Y 轴旋转 $it 度") }
            scale?.let { add("缩放 $it") }
        }
        return if (parts.isEmpty()) "已读取位置、旋转和缩放。" else parts.joinToString("；") + "。"
    }

    private fun objectLabel(data: Map<String, Any?>): String {
        val name = data["display_name"]?.toString()?.takeIf { it.isNotBlank() }?.shortForUi(maxLength = 14)
        val type = objectTypeLabel(data["type"]?.toString())
        val selectedSuffix = if (data["selected"] == true) "，已选中" else ""
        val lockedSuffix = if (data["locked"] == true) "，已锁定" else ""
        return when {
            name != null && type != null -> "$name（$type$selectedSuffix$lockedSuffix）"
            name != null -> "$name$selectedSuffix$lockedSuffix"
            type != null -> "$type$selectedSuffix$lockedSuffix"
            else -> "对象$selectedSuffix$lockedSuffix"
        }
    }

    private fun objectTypeLabel(type: String?): String? =
        when (type) {
            "model" -> "模型"
            "image" -> "图片"
            "text" -> "文字"
            "stroke" -> "笔画"
            "anchor" -> "锚点"
            "viewpoint" -> "视点"
            null,
            "",
            -> null
            else -> type
        }

    private fun animationEasingLabel(easing: String?): String? =
        when (easing) {
            "linear" -> "线性"
            "ease_in" -> "缓入"
            "ease_out" -> "缓出"
            "ease_in_out" -> "缓入缓出"
            null,
            "",
            -> null
            else -> easing
        }

    private fun animationLoopModeLabel(loopMode: String?): String? =
        when (loopMode) {
            "none" -> "不循环"
            "ping_pong" -> "往复循环"
            "restart" -> "单向循环"
            null,
            "",
            -> null
            else -> loopMode
        }

    private fun undoRedoText(canUndo: Boolean?, canRedo: Boolean?): String =
        when {
            canUndo == null && canRedo == null -> ""
            canUndo == true && canRedo == true -> "可撤销，也可重做。"
            canUndo == true -> "可撤销。"
            canRedo == true -> "可重做。"
            else -> "当前没有可撤销或重做的操作。"
        }

    private fun String.shortForUi(maxLength: Int = 24): String =
        trim().let { value ->
            if (value.length <= maxLength) value else value.take(maxLength) + "..."
        }

    private fun Any?.asInt(): Int? =
        when (this) {
            is Number -> toInt()
            is String -> toIntOrNull()
            else -> null
        }

    private fun Any?.asDouble(): Double? =
        when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        }

    private fun Double.formatNumber(): String =
        String.format(Locale.US, "%.2f", this)
            .trimEnd('0')
            .trimEnd('.')

    private fun String.ensureTerminalPunctuation(): String {
        val value = trim()
        if (value.isBlank()) return value
        val last = value.last()
        return if (last in listOf('。', '！', '？', '.', '!', '?')) value else "$value。"
    }

    private const val MAX_OBJECTS_IN_MESSAGE = 5
}
