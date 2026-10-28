package com.example.showme3d

import com.example.showme3d.studio.agent.AgentRunResult
import com.example.showme3d.studio.agent.AgentRunStatus
import com.example.showme3d.studio.agent.AgentToolResult
import com.example.showme3d.studio.agent.AgentToolStepResult
import com.example.showme3d.studio.agent.AgentUserMessageFormatter
import com.example.showme3d.studio.agent.DoubaoToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentUserMessageFormatterTest {
    @Test
    fun formatSuccessUsesChineseToolNameAndResultDetail() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.SUCCESS,
                message = "Added text.",
                toolCall = DoubaoToolCall(
                    name = "add_text",
                    arguments = mapOf("text" to "ShowMe3D"),
                    callId = "call_1",
                    rawArguments = """{"text":"ShowMe3D"}""",
                ),
                toolResult = AgentToolResult.success(
                    toolName = "add_text",
                    message = "Added text.",
                    data = mapOf("object_id" to "text_1", "text" to "ShowMe3D"),
                ),
            )
        )

        assertEquals("已执行：添加文字。已添加文字：ShowMe3D。", message)
    }

    @Test
    fun formatSelectionFailureGivesActionableHint() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.TOOL_FAILED,
                message = "No object_id was provided and no object is selected.",
                toolCall = DoubaoToolCall(
                    name = "set_color",
                    arguments = mapOf("color" to "red"),
                    callId = "call_2",
                    rawArguments = """{"color":"red"}""",
                ),
                toolResult = AgentToolResult.failure(
                    toolName = "set_color",
                    errorCode = "no_current_selection",
                    message = "No object_id was provided and no object is selected.",
                ),
            )
        )

        assertTrue(message.contains("没有当前选中对象"))
        assertTrue(message.contains("no_current_selection"))
    }

    @Test
    fun formatInvalidGenerationOptionMentionsSupportedValues() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.TOOL_FAILED,
                message = "Unsupported face_budget: 2m",
                toolCall = DoubaoToolCall(
                    name = "generate_model_from_stroke",
                    arguments = mapOf("face_budget" to "2m"),
                    callId = "call_3",
                    rawArguments = """{"face_budget":"2m"}""",
                ),
                toolResult = AgentToolResult.failure(
                    toolName = "generate_model_from_stroke",
                    errorCode = "invalid_generation_option",
                    message = "Unsupported face_budget: 2m",
                ),
            )
        )

        assertTrue(message.contains("生成参数不支持"))
        assertTrue(message.contains("invalid_generation_option"))
    }

    @Test
    fun formatNoToolKeepsAssistantText() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.NO_TOOL,
                message = "请先选中一个对象",
                assistantText = "请先选中一个对象",
            )
        )

        assertEquals("未执行：请先选中一个对象。", message)
    }

    @Test
    fun formatMultiStepSuccessSummarizesAllSteps() {
        val addText = DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}""")
        val addObject = DoubaoToolCall("add_object", mapOf("preset" to "cube"), "call_2", """{"preset":"cube"}""")
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.SUCCESS,
                message = "Added cube.",
                toolCall = addObject,
                toolResult = AgentToolResult.success("add_object", "Added cube."),
                stepResults = listOf(
                    AgentToolStepResult(1, 2, addText, AgentToolResult.success("add_text", "Added text.")),
                    AgentToolStepResult(2, 2, addObject, AgentToolResult.success("add_object", "Added cube.")),
                ),
                batchUndoCreated = true,
            )
        )

        assertEquals("已执行 2 步：添加文字、添加物体。全部完成。可一次撤销。", message)
    }

    @Test
    fun formatMultiStepFailureMentionsFailedStep() {
        val addText = DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}""")
        val addObject = DoubaoToolCall("add_object", mapOf("preset" to "spaceship"), "call_2", """{"preset":"spaceship"}""")
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.TOOL_FAILED,
                message = "Unknown preset: spaceship",
                toolCall = addObject,
                toolResult = AgentToolResult.failure("add_object", "invalid_preset", "Unknown preset: spaceship"),
                stepResults = listOf(
                    AgentToolStepResult(1, 2, addText, AgentToolResult.success("add_text", "Added text.")),
                    AgentToolStepResult(
                        2,
                        2,
                        addObject,
                        AgentToolResult.failure("add_object", "invalid_preset", "Unknown preset: spaceship"),
                    ),
                ),
            )
        )

        assertTrue(message.contains("第 2/2 步"))
        assertTrue(message.contains("添加物体"))
        assertTrue(message.contains("invalid_preset"))
    }

    @Test
    fun formatRolledBackMultiStepFailureMentionsRollback() {
        val addText = DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}""")
        val addObject = DoubaoToolCall("add_object", mapOf("preset" to "spaceship"), "call_2", """{"preset":"spaceship"}""")
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.TOOL_FAILED,
                message = "Unknown preset: spaceship",
                toolCall = addObject,
                toolResult = AgentToolResult.failure("add_object", "invalid_preset", "Unknown preset: spaceship"),
                stepResults = listOf(
                    AgentToolStepResult(1, 2, addText, AgentToolResult.success("add_text", "Added text.")),
                    AgentToolStepResult(
                        2,
                        2,
                        addObject,
                        AgentToolResult.failure("add_object", "invalid_preset", "Unknown preset: spaceship"),
                    ),
                ),
                rolledBack = true,
            )
        )

        assertTrue(message.contains("第 2/2 步"))
        assertTrue(message.contains("已回滚到执行前"))
        assertFalse(message.contains("已完成前 1 步"))
    }

    @Test
    fun formatSceneSummaryIncludesCountSelectionAndHistoryState() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.SUCCESS,
                message = "Read scene summary.",
                toolCall = DoubaoToolCall(
                    name = "get_scene_summary",
                    arguments = emptyMap(),
                    callId = "call_summary",
                    rawArguments = "{}",
                ),
                toolResult = AgentToolResult.success(
                    toolName = "get_scene_summary",
                    message = "Read scene summary.",
                    data = mapOf(
                        "page_index" to 1,
                        "page_count" to 2,
                        "object_count" to 3,
                        "selected_object" to mapOf(
                            "display_name" to "Cube",
                            "type" to "model",
                            "selected" to true,
                        ),
                        "can_undo" to true,
                        "can_redo" to false,
                    ),
                ),
            )
        )

        assertEquals("已执行：读取场景摘要。第 1/2 页有 3 个对象。当前选中 Cube（模型，已选中）。可撤销。", message)
    }

    @Test
    fun formatListObjectsIncludesObjectNamesAndTypes() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.SUCCESS,
                message = "Listed objects.",
                toolCall = DoubaoToolCall(
                    name = "list_objects",
                    arguments = emptyMap(),
                    callId = "call_list",
                    rawArguments = "{}",
                ),
                toolResult = AgentToolResult.success(
                    toolName = "list_objects",
                    message = "Listed objects.",
                    data = mapOf(
                        "objects" to listOf(
                            mapOf(
                                "display_name" to "Cube",
                                "type" to "model",
                            ),
                            mapOf(
                                "display_name" to "Title",
                                "type" to "text",
                                "selected" to true,
                            ),
                        ),
                    ),
                ),
            )
        )

        assertEquals("已执行：列出对象。当前页找到 2 个对象：Cube（模型）、Title（文字，已选中）。", message)
    }

    @Test
    fun formatTransformIncludesReadableValues() {
        val message = AgentUserMessageFormatter.format(
            AgentRunResult(
                status = AgentRunStatus.SUCCESS,
                message = "Read transform.",
                toolCall = DoubaoToolCall(
                    name = "get_transform",
                    arguments = emptyMap(),
                    callId = "call_transform",
                    rawArguments = "{}",
                ),
                toolResult = AgentToolResult.success(
                    toolName = "get_transform",
                    message = "Read transform.",
                    data = mapOf(
                        "transform" to mapOf(
                            "position" to mapOf("x" to 0.3f, "y" to 1.2f, "z" to -1.5f),
                            "rotation" to mapOf("y" to Math.PI / 2.0),
                            "scale" to 1.25f,
                        ),
                    ),
                ),
            )
        )

        assertTrue(message.contains("位置 x=0.3、y=1.2、z=-1.5"))
        assertTrue(message.contains("Y 轴旋转 90 度"))
        assertTrue(message.contains("缩放 1.25"))
    }
}
