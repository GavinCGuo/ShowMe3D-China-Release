package com.example.showme3d

import com.example.showme3d.studio.agent.AgentDecisionClient
import com.example.showme3d.studio.agent.AgentRunStatus
import com.example.showme3d.studio.agent.AgentRunner
import com.example.showme3d.studio.agent.AgentTool
import com.example.showme3d.studio.agent.AgentToolResult
import com.example.showme3d.studio.agent.DoubaoAgentDecision
import com.example.showme3d.studio.agent.DoubaoToolCall
import com.example.showme3d.studio.agent.ToolExecutor
import com.example.showme3d.studio.state.StudioViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunnerTest {
    @Test
    fun runOnceExecutesOneToolCallAgainstToolExecutor() {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_1",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "add_object",
                            arguments = mapOf("preset" to "cube"),
                            callId = "call_1",
                            rawArguments = """{"preset":"cube"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("加一个立方体")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("add_object", result.toolCall!!.name)
        assertEquals(1, vm.currentPage.states.size)
    }

    @Test
    fun runOnceReturnsAssistantTextWhenNoToolCall() {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_2",
                    assistantText = "请先选中一个对象。",
                    toolCalls = emptyList(),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("把它变大")

        assertFalse(result.success)
        assertEquals(AgentRunStatus.NO_TOOL, result.status)
        assertEquals("请先选中一个对象。", result.message)
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceExecutesMultipleToolCallsInOrder() {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_3",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}"""),
                        DoubaoToolCall("add_object", mapOf("preset" to "cube"), "call_2", """{"preset":"cube"}"""),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("加标题再加立方体")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals(2, result.stepResults.size)
        assertEquals("add_text", result.stepResults[0].toolCall.name)
        assertEquals("add_object", result.stepResults[1].toolCall.name)
        assertEquals(2, vm.currentPage.states.size)
        assertTrue(result.batchUndoCreated)
    }

    @Test
    fun runOnceAllowsLaterToolToUseSelectionFromPreviousStep() {
        val vm = openEditor()
        val fakeClient = FakeDecisionClient(
            decision = DoubaoAgentDecision(
                responseId = "resp_guard",
                assistantText = "",
                toolCalls = listOf(
                    DoubaoToolCall("add_object", mapOf("preset" to "cube"), "call_guard", """{"preset":"cube"}"""),
                    DoubaoToolCall("set_color", mapOf("color" to "red"), "call_color", """{"color":"red"}"""),
                ),
            ),
        )
        val runner = AgentRunner(
            decisionClient = fakeClient,
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("加一个立方体再把它变红")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals(1, fakeClient.requestCount)
        assertEquals(1, vm.currentPage.states.size)
        val objectId = vm.currentPage.states.keys.single()
        assertEquals(0xFFEF4444, vm.document.objects.getValue(objectId).textColor)

        vm.undo()

        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceRollsBackCompletedStepsAfterFirstFailedStep() {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_failed_sequence",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}"""),
                        DoubaoToolCall("add_object", mapOf("preset" to "spaceship"), "call_2", """{"preset":"spaceship"}"""),
                        DoubaoToolCall("add_object", mapOf("preset" to "cube"), "call_3", """{"preset":"cube"}"""),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("加标题再加飞船再加立方体")

        assertFalse(result.success)
        assertEquals(AgentRunStatus.TOOL_FAILED, result.status)
        assertEquals(2, result.stepResults.size)
        assertTrue(result.rolledBack)
        assertEquals("add_object", result.toolCall!!.name)
        assertEquals("invalid_preset", result.toolResult!!.errorCode)
        assertEquals(0, vm.currentPage.states.size)
        assertFalse(vm.canUndo)
    }

    @Test
    fun runOnceRejectsTwentyToolCallsBeforeExecuting() {
        val vm = openEditor()
        val calls = (1..20).map { index ->
            DoubaoToolCall(
                name = "add_text",
                arguments = mapOf("text" to "标题 $index"),
                callId = "call_$index",
                rawArguments = """{"text":"标题 $index"}""",
            )
        }
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_too_many",
                    assistantText = "",
                    toolCalls = calls,
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("连续加二十段文字")

        assertFalse(result.success)
        assertEquals(AgentRunStatus.MULTIPLE_TOOLS, result.status)
        assertTrue(result.message.contains("少于 20 步"))
        assertEquals(0, result.stepResults.size)
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncRollsBackTransactionWhenToolThrows() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_throw",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall("add_text", mapOf("text" to "标题"), "call_1", """{"text":"标题"}"""),
                        DoubaoToolCall(
                            name = "generate_image_from_text",
                            arguments = mapOf("prompt" to "红色玩具车"),
                            callId = "call_throw",
                            rawArguments = """{"prompt":"红色玩具车"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                if (call.name == "generate_image_from_text") {
                    error("Synthetic generation crash")
                }
                null
            },
        )

        val result = runCatching { runner.runOnceAsync("加标题再生成图片") }

        assertTrue(result.isFailure)
        assertEquals(0, vm.currentPage.states.size)
        assertFalse(vm.canUndo)
    }

    @Test
    fun runOnceSurfacesToolFailureWithoutChangingScene() {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_4",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "add_object",
                            arguments = mapOf("preset" to "spaceship"),
                            callId = "call_4",
                            rawArguments = """{"preset":"spaceship"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
        )

        val result = runner.runOnce("加一个飞船")

        assertFalse(result.success)
        assertEquals(AgentRunStatus.TOOL_FAILED, result.status)
        assertEquals("invalid_preset", result.toolResult!!.errorCode)
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesGenerateToolsToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val fakeClient = FakeDecisionClient(
            decision = DoubaoAgentDecision(
                responseId = "resp_5",
                assistantText = "",
                toolCalls = listOf(
                    DoubaoToolCall(
                        name = "generate_image_from_text",
                        arguments = mapOf("prompt" to "一只红色玩具车"),
                        callId = "call_5",
                        rawArguments = """{"prompt":"一只红色玩具车"}""",
                    ),
                ),
            ),
        )
        val runner = AgentRunner(
            decisionClient = fakeClient,
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated image.",
                    data = mapOf("prompt" to call.arguments["prompt"]),
                )
            },
        )

        val result = runner.runOnceAsync("生成一张红色玩具车图片")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_image_from_text", result.toolCall!!.name)
        assertEquals("一只红色玩具车", result.toolResult!!.data["prompt"])
        assertEquals(1, fakeClient.requestCount)
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesImageToImageToolToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_6",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_image_from_image",
                            arguments = mapOf(
                                "object_id" to "image_1",
                                "prompt" to "改成水彩风格",
                            ),
                            callId = "call_6",
                            rawArguments = """{"object_id":"image_1","prompt":"改成水彩风格"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated image variation.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("把这张图改成水彩风格")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_image_from_image", result.toolCall!!.name)
        assertEquals("image_1", result.toolResult!!.data["object_id"])
        assertEquals("改成水彩风格", result.toolResult!!.data["prompt"])
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesStrokeToImageToolToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_7",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_image_from_stroke",
                            arguments = mapOf("object_id" to "stroke_1"),
                            callId = "call_7",
                            rawArguments = """{"object_id":"stroke_1"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated image from stroke.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("把这段线稿生成图片")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_image_from_stroke", result.toolCall!!.name)
        assertEquals("stroke_1", result.toolResult!!.data["object_id"])
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesTextToModelToolToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_8",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_model_from_text",
                            arguments = mapOf(
                                "prompt" to "低多边形红色小螃蟹",
                                "face_budget" to "8k",
                                "texture" to "standard",
                            ),
                            callId = "call_8",
                            rawArguments = """{"prompt":"低多边形红色小螃蟹","face_budget":"8k","texture":"standard"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated model.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("生成一个低多边形红色小螃蟹 3D 模型")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_model_from_text", result.toolCall!!.name)
        assertEquals("低多边形红色小螃蟹", result.toolResult!!.data["prompt"])
        assertEquals("8k", result.toolResult!!.data["face_budget"])
        assertEquals("standard", result.toolResult!!.data["texture"])
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesImageToModelToolToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_9",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_model_from_image",
                            arguments = mapOf(
                                "object_id" to "image_2",
                                "prompt" to "保留玩具质感",
                                "preprocess" to "local_subject",
                                "rodin_tier" to "high",
                                "face_budget" to "50k",
                                "texture" to "hd",
                                "creative" to true,
                            ),
                            callId = "call_9",
                            rawArguments = """{"object_id":"image_2","prompt":"保留玩具质感","preprocess":"local_subject","rodin_tier":"high","face_budget":"50k","texture":"hd","creative":true}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated model from image.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("把这张图片生成 3D 模型")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_model_from_image", result.toolCall!!.name)
        assertEquals("image_2", result.toolResult!!.data["object_id"])
        assertEquals("保留玩具质感", result.toolResult!!.data["prompt"])
        assertEquals("local_subject", result.toolResult!!.data["preprocess"])
        assertEquals("high", result.toolResult!!.data["rodin_tier"])
        assertEquals("50k", result.toolResult!!.data["face_budget"])
        assertEquals("hd", result.toolResult!!.data["texture"])
        assertEquals(true, result.toolResult!!.data["creative"])
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesStrokeToModelToolToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_10",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_model_from_stroke",
                            arguments = mapOf(
                                "object_id" to "stroke_2",
                                "prompt" to "红色卡通小螃蟹",
                                "face_budget" to "20k",
                                "texture" to "hd",
                            ),
                            callId = "call_10",
                            rawArguments = """{"object_id":"stroke_2","prompt":"红色卡通小螃蟹","face_budget":"20k","texture":"hd"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated model from stroke.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("把这个草图生成红色卡通小螃蟹 3D 模型")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_model_from_stroke", result.toolCall!!.name)
        assertEquals("stroke_2", result.toolResult!!.data["object_id"])
        assertEquals("红色卡通小螃蟹", result.toolResult!!.data["prompt"])
        assertEquals("20k", result.toolResult!!.data["face_budget"])
        assertEquals("hd", result.toolResult!!.data["texture"])
        assertEquals(0, vm.currentPage.states.size)
    }

    @Test
    fun runOnceAsyncDelegatesStrokeToModelToolWithDefaultPromptToAsyncHandler() = runBlocking {
        val vm = openEditor()
        val runner = AgentRunner(
            decisionClient = FakeDecisionClient(
                decision = DoubaoAgentDecision(
                    responseId = "resp_11",
                    assistantText = "",
                    toolCalls = listOf(
                        DoubaoToolCall(
                            name = "generate_model_from_stroke",
                            arguments = mapOf("object_id" to "stroke_3"),
                            callId = "call_11",
                            rawArguments = """{"object_id":"stroke_3"}""",
                        ),
                    ),
                ),
            ),
            toolExecutor = ToolExecutor(vm),
            asyncToolHandler = { call ->
                AgentToolResult.success(
                    toolName = call.name,
                    message = "Generated model from stroke with default prompt.",
                    data = call.arguments,
                )
            },
        )

        val result = runner.runOnceAsync("把这段线稿生成 3D 模型")

        assertTrue(result.message, result.success)
        assertEquals(AgentRunStatus.SUCCESS, result.status)
        assertEquals("generate_model_from_stroke", result.toolCall!!.name)
        assertEquals("stroke_3", result.toolResult!!.data["object_id"])
        assertFalse(result.toolResult!!.data.containsKey("prompt"))
        assertEquals(0, vm.currentPage.states.size)
    }

    private class FakeDecisionClient(
        private val decision: DoubaoAgentDecision? = null,
        private val error: Throwable? = null,
    ) : AgentDecisionClient {
        var requestCount: Int = 0
            private set

        override fun requestDecision(
            userInput: String,
            sceneSummary: Map<String, Any?>,
            tools: List<AgentTool>,
        ): Result<DoubaoAgentDecision> {
            requestCount += 1
            return if (error != null) Result.failure(error) else Result.success(decision!!)
        }
    }

    private fun openEditor(): StudioViewModel =
        StudioViewModel().also { it.openShowFromHome(0) }
}
