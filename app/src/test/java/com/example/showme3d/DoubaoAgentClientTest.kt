package com.example.showme3d

import com.example.showme3d.studio.agent.DoubaoAgentClient
import com.example.showme3d.studio.agent.DoubaoAgentTransport
import com.example.showme3d.studio.agent.ToolRegistry
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoAgentClientTest {
    @Test
    fun buildDecisionRequestIncludesSceneSummaryAndFunctionTools() {
        val client = DoubaoAgentClient(apiKey = "test_key")

        val body = client.buildDecisionRequestBody(
            userInput = "加一个立方体",
            sceneSummary = mapOf(
                "object_count" to 0,
                "selected_object" to null,
            ),
        )

        assertEquals("doubao-seed-2-0-mini-260428", body.getString("model"))
        assertEquals(true, body.getBoolean("store"))
        assertEquals(false, body.getBoolean("stream"))

        val input = body.getJSONArray("input")
        assertEquals("system", input.getJSONObject(0).getString("role"))
        assertTrue(input.getJSONObject(0).getString("content").contains("get_scene_summary"))
        assertTrue(input.getJSONObject(0).getString("content").contains("get_transform"))
        assertTrue(input.getJSONObject(0).getString("content").contains("CLARIFY:"))
        assertTrue(input.getJSONObject(0).getString("content").contains("少于 20 个工具调用"))
        assertTrue(input.getJSONObject(0).getString("content").contains("红色的立方体"))
        assertTrue(input.getJSONObject(0).getString("content").contains("set_color(color=red"))
        assertTrue(input.getJSONObject(0).getString("content").contains("SUGGEST: 按钮文案"))
        val user = input.getJSONObject(1)
        assertEquals("user", user.getString("role"))
        assertTrue(user.getString("content").contains("加一个立方体"))
        assertTrue(user.getString("content").contains("\"object_count\":0"))
        assertTrue(user.getString("content").contains("CLARIFY:"))
        assertTrue(user.getString("content").contains("少于 20 步"))
        assertTrue(user.getString("content").contains("SUGGEST: 按钮文案"))

        val tools = body.getJSONArray("tools")
        val addObject = tools.objects().single { it.getString("name") == "add_object" }
        assertEquals("function", addObject.getString("type"))
        val parameters = addObject.getJSONObject("parameters")
        assertEquals("object", parameters.getString("type"))
        assertEquals("preset", parameters.getJSONArray("required").getString(0))
    }

    @Test
    fun parseDecisionResponseExtractsFunctionCallArguments() {
        val client = DoubaoAgentClient(apiKey = "test_key")
        val response = JSONObject()
            .put("id", "resp_123")
            .put("status", "completed")
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "function_call")
                        .put("name", "add_object")
                        .put("call_id", "call_123")
                        .put("arguments", """{"preset":"cube"}""")
                )
            )

        val decision = client.parseDecisionResponse(response)

        assertEquals("resp_123", decision.responseId)
        assertTrue(decision.assistantText.isBlank())
        assertEquals(1, decision.toolCalls.size)
        assertEquals("add_object", decision.singleToolCall!!.name)
        assertEquals("call_123", decision.singleToolCall!!.callId)
        assertEquals("cube", decision.singleToolCall!!.arguments["preset"])
    }

    @Test
    fun parseDecisionResponseExtractsAssistantTextWithoutToolCall() {
        val client = DoubaoAgentClient(apiKey = "test_key")
        val response = JSONObject()
            .put("id", "resp_456")
            .put("status", "completed")
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "message")
                        .put("role", "assistant")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "output_text")
                                    .put("text", "请先选中一个对象。")
                            )
                        )
                )
            )

        val decision = client.parseDecisionResponse(response)

        assertEquals("请先选中一个对象。", decision.assistantText)
        assertTrue(decision.toolCalls.isEmpty())
    }

    @Test
    fun parseDecisionResponseExtractsAiSuggestionsFromAssistantText() {
        val client = DoubaoAgentClient(apiKey = "test_key")
        val response = JSONObject()
            .put("id", "resp_suggest")
            .put("status", "completed")
            .put(
                "output",
                JSONArray().put(
                    JSONObject()
                        .put("type", "message")
                        .put("role", "assistant")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "output_text")
                                    .put(
                                        "text",
                                        """
                                            已完成。
                                            SUGGEST:
                                            - 加动画｜给选中的物体加一个默认动画
                                        """.trimIndent()
                                    )
                            )
                        )
                )
            )

        val decision = client.parseDecisionResponse(response)

        assertEquals("已完成。", decision.assistantText)
        assertEquals(1, decision.suggestions.size)
        assertEquals("加动画", decision.suggestions.single().actionLabel)
        assertEquals("给选中的物体加一个默认动画", decision.suggestions.single().prompt)
        assertEquals("", decision.suggestions.single().message)
    }

    @Test
    fun requestDecisionUsesInjectedTransportWithoutNetwork() {
        val transport = CapturingTransport(
            response = JSONObject()
                .put("id", "resp_789")
                .put(
                    "output",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "function_call")
                            .put("name", "set_color")
                            .put("call_id", "call_789")
                            .put("arguments", """{"object_id":"s1","color":"red"}""")
                    )
                )
        )
        val client = DoubaoAgentClient(
            apiKey = "test_key",
            transport = transport,
        )

        val result = client.requestDecision(
            userInput = "把它变红",
            sceneSummary = mapOf("selected_object" to mapOf("object_id" to "s1")),
            tools = listOf(ToolRegistry.require("set_color")),
        )

        assertTrue(result.isSuccess)
        assertEquals(DoubaoAgentClient.RESPONSES_API_URL, transport.url)
        assertEquals("test_key", transport.apiKey)
        assertEquals(5_000, transport.connectTimeoutMillis)
        assertEquals(60_000, transport.readTimeoutMillis)
        assertEquals("set_color", result.getOrThrow().singleToolCall!!.name)
        assertEquals("red", result.getOrThrow().singleToolCall!!.arguments["color"])
        assertFalse(transport.body!!.getJSONArray("tools").isNull(0))
    }

    @Test
    fun requestDecisionRetriesOneSocketTimeout() {
        val transport = TimeoutThenSuccessTransport(
            response = JSONObject()
                .put("id", "resp_retry")
                .put(
                    "output",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "function_call")
                            .put("name", "add_object")
                            .put("call_id", "call_retry")
                            .put("arguments", """{"preset":"cube"}""")
                    )
                )
        )
        val client = DoubaoAgentClient(apiKey = "test_key", transport = transport)

        val result = client.requestDecision(
            userInput = "加一个立方体",
            sceneSummary = emptyMap(),
            tools = listOf(ToolRegistry.require("add_object")),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, transport.attempts)
        assertEquals("add_object", result.getOrThrow().singleToolCall?.name)
    }

    @Test
    fun requestDecisionStopsAfterSecondSocketTimeout() {
        val transport = AlwaysTimeoutTransport()
        val client = DoubaoAgentClient(apiKey = "test_key", transport = transport)

        val result = client.requestDecision(
            userInput = "加一个立方体",
            sceneSummary = emptyMap(),
            tools = listOf(ToolRegistry.require("add_object")),
        )

        assertTrue(result.isFailure)
        assertEquals(2, transport.attempts)
    }

    private class CapturingTransport(
        private val response: JSONObject,
    ) : DoubaoAgentTransport {
        var url: String? = null
            private set
        var apiKey: String? = null
            private set
        var body: JSONObject? = null
            private set
        var connectTimeoutMillis: Int? = null
            private set
        var readTimeoutMillis: Int? = null
            private set

        override fun postJson(
            url: String,
            apiKey: String,
            body: JSONObject,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
        ): JSONObject {
            this.url = url
            this.apiKey = apiKey
            this.body = body
            this.connectTimeoutMillis = connectTimeoutMillis
            this.readTimeoutMillis = readTimeoutMillis
            return response
        }
    }

    private class TimeoutThenSuccessTransport(
        private val response: JSONObject,
    ) : DoubaoAgentTransport {
        var attempts: Int = 0
            private set

        override fun postJson(
            url: String,
            apiKey: String,
            body: JSONObject,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
        ): JSONObject {
            attempts += 1
            if (attempts == 1) throw SocketTimeoutException("timeout")
            return response
        }
    }

    private class AlwaysTimeoutTransport : DoubaoAgentTransport {
        var attempts: Int = 0
            private set

        override fun postJson(
            url: String,
            apiKey: String,
            body: JSONObject,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
        ): JSONObject {
            attempts += 1
            throw SocketTimeoutException("timeout")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> =
        buildList {
            for (index in 0 until length()) {
                add(getJSONObject(index))
            }
        }
}
