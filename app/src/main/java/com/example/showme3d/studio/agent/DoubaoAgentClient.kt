package com.example.showme3d.studio.agent

import com.example.showme3d.release.ReleasePolicy
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class DoubaoToolCall(
    val name: String,
    val arguments: Map<String, Any?>,
    val callId: String,
    val rawArguments: String,
)

data class DoubaoAgentDecision(
    val responseId: String?,
    val assistantText: String,
    val toolCalls: List<DoubaoToolCall>,
    val suggestions: List<AgentSuggestionPlan> = emptyList(),
) {
    val singleToolCall: DoubaoToolCall? get() = toolCalls.singleOrNull()
}

enum class DoubaoConversationRole(val apiValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

data class DoubaoConversationMessage(
    val role: DoubaoConversationRole,
    val content: String,
)

class DoubaoAgentException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun interface DoubaoAgentTransport {
    fun postJson(
        url: String,
        apiKey: String,
        body: JSONObject,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): JSONObject
}

class DoubaoAgentClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val endpoint: String = RESPONSES_API_URL,
    private val transport: DoubaoAgentTransport = OkHttpDoubaoAgentTransport,
    private val conversationHistory: List<DoubaoConversationMessage> = emptyList(),
) : AgentDecisionClient {
    override fun requestDecision(
        userInput: String,
        sceneSummary: Map<String, Any?>,
        tools: List<AgentTool>,
    ): Result<DoubaoAgentDecision> = runCatching {
        require(apiKey.isNotBlank()) { "请先配置豆包 Key" }
        require(userInput.isNotBlank()) { "请输入要让 AI 执行的指令" }
        val body = buildDecisionRequestBody(
            userInput = userInput.trim(),
            sceneSummary = sceneSummary,
            tools = tools,
            model = model,
        )
        logDoubaoAgent(
            "requestDecision start model=$model tools=${tools.size} promptLength=${userInput.trim().length}",
        )
        val response = requestWithTimeoutRetry(body)
        parseDecisionResponse(response).also { decision ->
            logDoubaoAgent(
                "requestDecision parsed responseId=${decision.responseId.orEmpty()} " +
                    "toolCalls=${decision.toolCalls.map { it.name }} textLength=${decision.assistantText.length}",
            )
        }
    }

    private fun requestWithTimeoutRetry(body: JSONObject): JSONObject {
        var lastTimeout: Throwable? = null
        repeat(MAX_TIMEOUT_ATTEMPTS) { zeroBasedAttempt ->
            val attempt = zeroBasedAttempt + 1
            try {
                return transport.postJson(
                    url = endpoint,
                    apiKey = apiKey.trim(),
                    body = body,
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS,
                    readTimeoutMillis = READ_TIMEOUT_MS,
                )
            } catch (error: Throwable) {
                if (!error.isSocketTimeout() || attempt >= MAX_TIMEOUT_ATTEMPTS) throw error
                lastTimeout = error
                logDoubaoAgent("requestDecision timeout; retrying attempt=${attempt + 1}/$MAX_TIMEOUT_ATTEMPTS")
            }
        }
        throw requireNotNull(lastTimeout)
    }

    internal fun buildDecisionRequestBody(
        userInput: String,
        sceneSummary: Map<String, Any?>,
        tools: List<AgentTool> = ToolRegistry.tools,
        model: String = this.model,
    ): JSONObject =
        JSONObject()
            .put("model", model)
            .put("store", true)
            .put("stream", false)
            .put(
                "input",
                JSONArray().apply {
                    put(message(role = "system", content = SYSTEM_PROMPT))
                    conversationHistory
                        .filter { it.content.isNotBlank() }
                        .takeLast(MAX_CONVERSATION_MESSAGES)
                        .forEach { turn ->
                            put(message(role = turn.role.apiValue, content = turn.content.trim()))
                        }
                    put(
                        message(
                            role = "user",
                            content = buildUserPrompt(userInput = userInput, sceneSummary = sceneSummary),
                        )
                    )
                }
            )
            .put(
                "tools",
                JSONArray(
                    tools.map { tool ->
                        JSONObject()
                            .put("type", "function")
                            .also { target ->
                                tool.toFunctionSpec().forEach { (key, value) ->
                                    target.put(key, value.toJsonValue())
                                }
                            }
                    }
                )
            )

    internal fun parseDecisionResponse(response: JSONObject): DoubaoAgentDecision {
        val output = response.optJSONArray("output") ?: JSONArray()
        val toolCalls = mutableListOf<DoubaoToolCall>()
        val messages = mutableListOf<String>()

        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            when (item.optString("type")) {
                "function_call" -> toolCalls += parseToolCall(item)
                "message" -> item.outputText()?.let(messages::add)
            }
        }

        if (toolCalls.isEmpty() && messages.isEmpty()) {
            val status = response.optString("status").ifBlank { "unknown" }
            throw DoubaoAgentException("豆包未返回工具调用或文本消息，状态：$status")
        }
        val parsedText = AgentSuggestionParser.parse(messages.joinToString("\n").trim())
        return DoubaoAgentDecision(
            responseId = response.optString("id").takeIf { it.isNotBlank() },
            assistantText = parsedText.message,
            toolCalls = toolCalls,
            suggestions = parsedText.suggestions,
        )
    }

    private fun parseToolCall(item: JSONObject): DoubaoToolCall {
        val name = item.optString("name")
        if (name.isBlank()) throw DoubaoAgentException("豆包 function_call 缺少 name")
        val callId = item.optString("call_id")
        if (callId.isBlank()) throw DoubaoAgentException("豆包 function_call 缺少 call_id")
        val rawArguments = item.optString("arguments").trim()
        val arguments = parseArguments(rawArguments)
        return DoubaoToolCall(
            name = name,
            arguments = arguments,
            callId = callId,
            rawArguments = rawArguments,
        )
    }

    private fun parseArguments(rawArguments: String): Map<String, Any?> {
        if (rawArguments.isBlank()) return emptyMap()
        return runCatching { JSONObject(rawArguments).toMap() }
            .getOrElse { error ->
                throw DoubaoAgentException("豆包 function_call arguments 不是合法 JSON 对象", error)
            }
    }

    private fun JSONObject.outputText(): String? {
        val content = optJSONArray("content") ?: return null
        val texts = buildList {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                if (part.optString("type") == "output_text") {
                    part.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return texts.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }

    private fun buildUserPrompt(userInput: String, sceneSummary: Map<String, Any?>): String =
        """
            用户指令：
            $userInput

            当前场景摘要 JSON：
            ${sceneSummary.toJsonValue()}

            可以选择一个或多个工具调用组成有序动作序列，必须少于 20 步。工具会按返回顺序执行；如果没有安全、明确的工具序列可用，请不要调用工具。
            如果用户意图可支持但信息不足，请返回一条以 CLARIFY: 开头的简短中文追问；如果是能力不支持或安全拒绝，直接用一句话说明原因，不要加 CLARIFY:。
            如果你能顺手给出下一步操作建议，可以在同一次回复文本里附加 0 到 3 行建议，格式为：SUGGEST: 按钮文案｜用户点击后要发送的自然语言指令。不要写建议原因；没有明确有用的下一步就不要输出 SUGGEST。
        """.trimIndent()

    private fun message(role: String, content: String): JSONObject =
        JSONObject()
            .put("type", "message")
            .put("role", role)
            .put("content", content)

    private object OkHttpDoubaoAgentTransport : DoubaoAgentTransport {
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .eventListenerFactory { DoubaoNetworkTimingListener() }
            .build()

        override fun postJson(
            url: String,
            apiKey: String,
            body: JSONObject,
            connectTimeoutMillis: Int,
            readTimeoutMillis: Int,
        ): JSONObject {
            ReleasePolicy.requireCloudAi()
            check(connectTimeoutMillis == CONNECT_TIMEOUT_MS)
            check(readTimeoutMillis == READ_TIMEOUT_MS)
            val bodyText = body.toString()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .post(bodyText.toRequestBody(jsonMediaType))
                .build()
            logDoubaoAgent("HTTP POST start bodyLength=${bodyText.length}")
            return client.newCall(request).execute().use { response ->
                response.readJsonResponse()
            }
        }

        private fun Response.readJsonResponse(): JSONObject {
            val text = body?.string().orEmpty()
            logDoubaoAgent(
                "HTTP POST finished code=$code protocol=$protocol responseLength=${text.length}",
            )
            if (!isSuccessful) {
                throw DoubaoAgentException("豆包 HTTP $code: ${text.take(180)}")
            }
            if (text.isBlank()) throw DoubaoAgentException("豆包返回空响应")
            val json = JSONObject(text)
            val errorMessage = json.optString("error")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("detail") }
            if (errorMessage.isNotBlank() && json.optJSONArray("output") == null) {
                throw DoubaoAgentException("豆包返回错误：$errorMessage")
            }
            return json
        }
    }

    companion object {
        const val RESPONSES_API_URL = "https://ark.cn-beijing.volces.com/api/v3/responses"
        const val DEFAULT_MODEL = "doubao-seed-2-0-mini-260428"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_TIMEOUT_ATTEMPTS = 2
        private const val MAX_CONVERSATION_MESSAGES = 10
        private const val SYSTEM_PROMPT = """
            你是 ShowMe3D 应用里的编辑助手，像一个熟悉工作台的老朋友一样帮用户把话落到场景里。语气可以自然、简短、有人味；但一旦需要执行编辑，必须稳、准、按工具规则来。
            你的任务是把用户的一条指令转换成安全、有序的工具调用序列。必须严格使用场景摘要和工具 schema，不要编造对象 id、预设资源、颜色或变换参数。
            只要用户明确要求编辑场景（例如添加、删除、移动、旋转、缩放、改颜色、新增页面、进入演示），就必须返回对应工具调用，不能只用自然语言说已经完成。
            例如“加一个立方体 / 添加方块”必须调用 add_object，preset 使用 cube；“加一个球体”必须调用 add_object，preset 使用 sphere。
            如果添加新物体的同一句话里包含颜色、缩放、移动、旋转或动画要求，不要丢弃这些修饰；先调用 add_object，再依赖新对象会被自动选中的规则继续调用 set_color、scale_object_relative、move_object_relative、rotate_object_relative 或 set_animation。
            例如“要一个红色的立方体”必须按顺序返回两个工具调用：add_object(preset=cube)，然后 set_color(color=red，省略 object_id)。
            例如“加一个大一点的蓝色球体”应调用 add_object(preset=sphere)，再 set_color(color=blue)，再 scale_object_relative(scale_factor=1.2 或更符合用户描述的数值)。
            你可以按实际执行顺序返回少于 20 个工具调用。优先保持计划简洁，但不要拆掉一个本来清楚、可执行的多步请求。如果用户请求不安全或当前工具不支持，不要调用工具；只用一句简短中文说明原因。
            如果用户意图可能支持但缺少必要信息，不要调用工具；只返回一个以 CLARIFY: 开头的简短中文追问。一次只问一个问题。能力不支持或安全拒绝时不要使用 CLARIFY:。
            如果用户输入里包含上一条指令、你之前的澄清问题以及用户的回答，请把它们合并理解为当前意图。如果已经明确，就调用正确的工具序列；如果仍不明确，再问一个 CLARIFY: 问题。
            工具调用会按顺序执行。后续工具可以依赖前一个工具产生的编辑器选中状态；例如 add_object 会选中新对象，因此紧接着 set_color 时可以省略 object_id。
            场景摘要已经足够时，不要在每次编辑前都调用只读工具。如果某一步需要知道一个新生成对象的 id，但前一步不会自动选中该对象，请避免这种计划，并改为追问澄清。
            对于“向右 / 向左 / 向上 / 向下 / 向前 / 向后移动一段距离”这类相对移动请求，直接使用 move_object_relative；不要先调用 get_transform。
            对于“向右 / 向左旋转多少度”这类相对旋转请求，直接使用 rotate_object_relative；不要先调用 get_transform。
            对于“放大 / 缩小多少比例”这类相对缩放请求，直接使用 scale_object_relative；不要先调用 get_transform。
            对于最终绝对变换请求，使用 set_transform，并只输出最终的绝对变换值。
            如果用户询问当前场景里有什么、当前选中了什么、对象数量、是否可撤销 / 重做，使用 get_scene_summary 作为唯一只读工具。
            如果用户要求列出对象，使用 list_objects 作为唯一只读工具。
            如果用户询问选中对象的位置、旋转或缩放，使用 get_transform 作为唯一只读工具。
            如果用户要求清空、清除、擦除或重置当前页面上的所有对象，使用 clear_current_page 作为唯一工具。
            对于页面或演示相关请求，使用 add_page、duplicate_page、delete_page、goto_page、enter_present_mode 或 exit_present_mode 作为唯一工具。goto_page.index 是从 1 开始的页码。
            对于视点相关请求，使用 add_viewpoint、enter_viewpoint 或 exit_viewpoint 作为唯一工具。只有目标是已选中或已知的视点对象时，才使用 enter_viewpoint。
            白板文档不支持页面和演示模式工具，但支持 add_viewpoint、enter_viewpoint 和 exit_viewpoint。
            对于动画请求，使用 set_animation、delete_animation 或 toggle_animation_playback 作为唯一工具。set_animation / delete_animation 支持演示文档和白板，要求当前处于普通编辑模式，并且不能作用于视点对象。
            set_animation 支持可选的起点 / 终点变换、duration_seconds 取值 0.5 到 12、easing 取值 linear / ease_in / ease_out / ease_in_out、loop_mode 取值 none / ping_pong / restart。如果用户要求简单 / 默认动画，省略起点、终点和其他可选字段。
            对于暂停 / 继续播放动画请求，只有 current_page_has_animations 为 true 且请求状态与 animations_paused 不一致时，才使用 toggle_animation_playback；否则只用一句话说明，无需调用工具。
            对于文档 / show 相关请求，使用 list_documents、create_document、switch_document、rename_document、duplicate_document、delete_document 或 return_home 作为唯一工具。
            文档索引从 1 开始。如果用户指的是当前文档 / show，使用场景摘要里的 active_document_index。
            如果用户询问所有文档 / show，或目标文档索引未知，使用 list_documents。不要删除最后一个剩余文档。
            生成任务必须先判断“输出模态”和“输入来源”，不能因为 ShowMe3D 是 3D 应用、AIGC 分类同时包含 2D/3D、场景里有同名预设，或某个主体适合做模型，就自行把图片请求升级成 3D。
            输出模态路由规则是互斥的：用户明确说“图片、图像、照片、画面、二维、2D、文生图”时，只能使用 generate_image_*；用户明确说“3D、三维、模型、立体、可旋转模型、文生 3D”时，才能使用 generate_model_*。用户没有明确要求 3D 时，禁止调用 generate_model_*。
            输入来源路由规则：仅用文字描述新图片时调用 generate_image_from_text；明确要求基于当前已有图片修改、换风格或生成变体时调用 generate_image_from_image；明确要求把笔画或线稿变成图片时调用 generate_image_from_stroke。对应的 3D 请求分别使用 generate_model_from_text、generate_model_from_image、generate_model_from_stroke。
            “生成图片”和“图生图”不是同一件事：前者不需要源图片，后者必须基于已有图片。不要因为当前没有选中图片，就声称不会生成图片或不会图生图；应准确说明是否需要用户先选中源图片。
            多轮对话中，像“我要花坛的”“就做卡通的”“换成蓝色”这样的省略回答，必须继承最近一轮已经明确的输出模态和任务。例：用户先问“会生成图片吗”，你回答可以并询问主体，用户再说“我要花坛的”，应调用 generate_image_from_text 生成花坛图片，绝不能调用 generate_model_from_text。
            如果当前一句没有明确图片或 3D，且最近对话也没有已经确定的输出模态，必须返回 CLARIFY: 追问用户要图片还是 3D 模型，不能猜测。
            能力说明必须与工具一致：当前支持文生图、图生图、笔画成图、文生 3D、图生 3D、笔画生成 3D。回答“会不会”类问题时只说明真实能力和必要前提，不要虚构“不支持”。
            对于生成类工具，prompt 要让下游生成服务知道自己当前在做什么：文生图、图生图、文生 3D、图生 3D、笔画成图或笔画生成 3D。不要只写孤立风格词；要保留用户主体、风格、材质和必要限制。
            每次已经理解的用户请求，在不需要 CLARIFY 且不属于安全拒绝时，都要在同一次回复文本末尾附加 1 到 3 行自然的下一步建议，格式必须是：SUGGEST: 按钮文案｜用户点击后要发送的自然语言指令。
            建议可以和工具调用一起返回，不需要等工具真正执行完。不要输出建议原因、解释或 Markdown；只有确实不存在安全、有用下一步时才可以不输出 SUGGEST。
        """
    }
}

private fun Throwable.isSocketTimeout(): Boolean =
    generateSequence(this) { it.cause }.any { it is SocketTimeoutException }

private class DoubaoNetworkTimingListener : EventListener() {
    private val startedAtNanos = System.nanoTime()

    private fun elapsedMillis(): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    override fun dnsStart(call: Call, domainName: String) {
        logDoubaoAgent("network dns_start host=$domainName elapsedMs=${elapsedMillis()}")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        logDoubaoAgent(
            "network dns_end addresses=${inetAddressList.size} elapsedMs=${elapsedMillis()}",
        )
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        logDoubaoAgent(
            "network connect_start proxy=${proxy.type()} elapsedMs=${elapsedMillis()}",
        )
    }

    override fun secureConnectStart(call: Call) {
        logDoubaoAgent("network tls_start elapsedMs=${elapsedMillis()}")
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        logDoubaoAgent("network tls_end elapsedMs=${elapsedMillis()}")
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        logDoubaoAgent("network connect_end protocol=$protocol elapsedMs=${elapsedMillis()}")
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        logDoubaoAgent(
            "network connect_failed type=${ioe.javaClass.simpleName} elapsedMs=${elapsedMillis()}",
        )
    }

    override fun requestBodyStart(call: Call) {
        logDoubaoAgent("network upload_start elapsedMs=${elapsedMillis()}")
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        logDoubaoAgent("network upload_end bytes=$byteCount elapsedMs=${elapsedMillis()}")
    }

    override fun responseHeadersStart(call: Call) {
        logDoubaoAgent("network first_byte elapsedMs=${elapsedMillis()}")
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        logDoubaoAgent("network download_end bytes=$byteCount elapsedMs=${elapsedMillis()}")
    }

    override fun callEnd(call: Call) {
        logDoubaoAgent("network call_end totalMs=${elapsedMillis()}")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        logDoubaoAgent(
            "network call_failed type=${ioe.javaClass.simpleName} totalMs=${elapsedMillis()}",
        )
    }
}

private const val DOUBAO_AGENT_TAG = "ShowMe3D.Doubao"

private fun logDoubaoAgent(message: String) {
    runCatching { Log.w(DOUBAO_AGENT_TAG, message) }
}

private fun Any?.toJsonValue(): Any? =
    when (this) {
        null -> JSONObject.NULL
        JSONObject.NULL -> JSONObject.NULL
        is JSONObject,
        is JSONArray,
        is String,
        is Number,
        is Boolean,
        -> this
        is Map<*, *> -> JSONObject().also { obj ->
            forEach { (key, value) ->
                if (key != null) obj.put(key.toString(), value.toJsonValue())
            }
        }
        is Iterable<*> -> JSONArray().also { array ->
            forEach { array.put(it.toJsonValue()) }
        }
        is Array<*> -> JSONArray().also { array ->
            forEach { array.put(it.toJsonValue()) }
        }
        else -> toString()
    }

private fun JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key ->
        if (isNull(key)) null else opt(key).toKotlinValue()
    }

private fun JSONArray.toList(): List<Any?> =
    buildList {
        for (index in 0 until length()) {
            add(if (isNull(index)) null else opt(index).toKotlinValue())
        }
    }

private fun Any?.toKotlinValue(): Any? =
    when (this) {
        null,
        JSONObject.NULL,
        -> null
        is JSONObject -> toMap()
        is JSONArray -> toList()
        else -> this
    }
