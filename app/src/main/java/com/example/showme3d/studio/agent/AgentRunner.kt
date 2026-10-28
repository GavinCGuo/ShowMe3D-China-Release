package com.example.showme3d.studio.agent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_AGENT_TOOL_STEPS = 19

enum class AgentRunStatus {
    SUCCESS,
    TOOL_FAILED,
    NO_TOOL,
    MULTIPLE_TOOLS,
    MODEL_ERROR,
}

enum class AgentRunStepStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
}

data class AgentToolStepResult(
    val index: Int,
    val total: Int,
    val toolCall: DoubaoToolCall,
    val toolResult: AgentToolResult,
)

data class AgentRunStepProgress(
    val index: Int,
    val total: Int,
    val toolCall: DoubaoToolCall,
    val status: AgentRunStepStatus,
    val toolResult: AgentToolResult? = null,
)

data class AgentRunResult(
    val status: AgentRunStatus,
    val message: String,
    val assistantText: String = "",
    val toolCall: DoubaoToolCall? = null,
    val toolResult: AgentToolResult? = null,
    val stepResults: List<AgentToolStepResult> = emptyList(),
    val rolledBack: Boolean = false,
    val batchUndoCreated: Boolean = false,
    val suggestions: List<AgentSuggestionPlan> = emptyList(),
) {
    val success: Boolean get() = status == AgentRunStatus.SUCCESS
}

interface AgentTransactionController {
    fun shouldStartTransaction(toolCalls: List<DoubaoToolCall>): Boolean
    fun beginTransaction()
    fun commitTransaction(): Boolean
    fun rollbackTransaction()
}

fun interface AgentDecisionClient {
    fun requestDecision(
        userInput: String,
        sceneSummary: Map<String, Any?>,
        tools: List<AgentTool>,
    ): Result<DoubaoAgentDecision>
}

class AgentRunner(
    private val decisionClient: AgentDecisionClient,
    private val toolExecutor: ToolExecutor,
    private val tools: List<AgentTool> = ToolRegistry.tools,
    private val asyncToolHandler: (suspend (DoubaoToolCall) -> AgentToolResult?)? = null,
    private val stepProgressListener: ((AgentRunStepProgress) -> Unit)? = null,
    private val transactionController: AgentTransactionController? = toolExecutor,
) {
    fun runOnce(userInput: String): AgentRunResult {
        val sceneSummary = toolExecutor.execute("get_scene_summary").data
        return runToolDecision(
            decision = decisionClient.requestDecision(
                userInput = userInput,
                sceneSummary = sceneSummary,
                tools = tools,
            )
        )
    }

    suspend fun runOnceAsync(
        userInput: String,
        decisionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): AgentRunResult {
        val sceneSummary = toolExecutor.execute("get_scene_summary").data
        val decision = withContext(decisionDispatcher) {
            decisionClient.requestDecision(
                userInput = userInput,
                sceneSummary = sceneSummary,
                tools = tools,
            )
        }
        return runToolDecisionAsync(decision)
    }

    private fun runToolDecision(
        decision: Result<DoubaoAgentDecision>,
    ): AgentRunResult {
        val value = decision.getOrElse { error ->
            return AgentRunResult(
                status = AgentRunStatus.MODEL_ERROR,
                message = error.message ?: "AI 请求失败",
            )
        }

        if (value.toolCalls.isEmpty()) {
            return AgentRunResult(
                status = AgentRunStatus.NO_TOOL,
                message = value.assistantText.ifBlank { "这句话还没有可执行的工具序列。" },
                assistantText = value.assistantText,
                suggestions = value.suggestions,
            )
        }
        if (value.toolCalls.size > MAX_AGENT_TOOL_STEPS) {
            return tooManyToolCallsResult(value)
        }

        return executeToolSequence(value) { toolCall ->
            toolExecutor.execute(toolCall.name, toolCall.arguments)
        }
    }

    private suspend fun runToolDecisionAsync(
        decision: Result<DoubaoAgentDecision>,
    ): AgentRunResult {
        val value = decision.getOrElse { error ->
            return AgentRunResult(
                status = AgentRunStatus.MODEL_ERROR,
                message = error.message ?: "AI 请求失败",
            )
        }

        if (value.toolCalls.isEmpty()) {
            return AgentRunResult(
                status = AgentRunStatus.NO_TOOL,
                message = value.assistantText.ifBlank { "这句话还没有可执行的工具序列。" },
                assistantText = value.assistantText,
                suggestions = value.suggestions,
            )
        }
        if (value.toolCalls.size > MAX_AGENT_TOOL_STEPS) {
            return tooManyToolCallsResult(value)
        }

        return executeToolSequenceAsync(value) { toolCall ->
            asyncToolHandler?.invoke(toolCall)
                ?: toolExecutor.execute(toolCall.name, toolCall.arguments)
        }
    }

    private fun executeToolSequence(
        decision: DoubaoAgentDecision,
        executeTool: (DoubaoToolCall) -> AgentToolResult,
    ): AgentRunResult {
        val transaction = transactionController?.takeIf { it.shouldStartTransaction(decision.toolCalls) }
        transaction?.beginTransaction()
        val total = decision.toolCalls.size
        val stepResults = mutableListOf<AgentToolStepResult>()
        try {
            for ((zeroIndex, toolCall) in decision.toolCalls.withIndex()) {
                val index = zeroIndex + 1
                stepProgressListener?.invoke(
                    AgentRunStepProgress(
                        index = index,
                        total = total,
                        toolCall = toolCall,
                        status = AgentRunStepStatus.RUNNING,
                    )
                )
                val toolResult = executeTool(toolCall)
                val stepResult = AgentToolStepResult(
                    index = index,
                    total = total,
                    toolCall = toolCall,
                    toolResult = toolResult,
                )
                stepResults += stepResult
                stepProgressListener?.invoke(
                    AgentRunStepProgress(
                        index = index,
                        total = total,
                        toolCall = toolCall,
                        status = if (toolResult.success) AgentRunStepStatus.SUCCEEDED else AgentRunStepStatus.FAILED,
                        toolResult = toolResult,
                    )
                )
                if (!toolResult.success) {
                    transaction?.rollbackTransaction()
                    return toolRunResult(
                        decision = decision,
                        status = AgentRunStatus.TOOL_FAILED,
                        message = toolResult.message,
                        stepResults = stepResults,
                        rolledBack = transaction != null,
                    )
                }
            }
            val batchUndoCreated = transaction?.commitTransaction() == true
            return toolRunResult(
                decision = decision,
                status = AgentRunStatus.SUCCESS,
                message = stepResults.lastOrNull()?.toolResult?.message.orEmpty(),
                stepResults = stepResults,
                batchUndoCreated = batchUndoCreated,
            )
        } catch (error: Throwable) {
            transaction?.rollbackTransaction()
            throw error
        }
    }

    private suspend fun executeToolSequenceAsync(
        decision: DoubaoAgentDecision,
        executeTool: suspend (DoubaoToolCall) -> AgentToolResult,
    ): AgentRunResult {
        val transaction = transactionController?.takeIf { it.shouldStartTransaction(decision.toolCalls) }
        transaction?.beginTransaction()
        val total = decision.toolCalls.size
        val stepResults = mutableListOf<AgentToolStepResult>()
        try {
            for ((zeroIndex, toolCall) in decision.toolCalls.withIndex()) {
                val index = zeroIndex + 1
                stepProgressListener?.invoke(
                    AgentRunStepProgress(
                        index = index,
                        total = total,
                        toolCall = toolCall,
                        status = AgentRunStepStatus.RUNNING,
                    )
                )
                val toolResult = executeTool(toolCall)
                val stepResult = AgentToolStepResult(
                    index = index,
                    total = total,
                    toolCall = toolCall,
                    toolResult = toolResult,
                )
                stepResults += stepResult
                stepProgressListener?.invoke(
                    AgentRunStepProgress(
                        index = index,
                        total = total,
                        toolCall = toolCall,
                        status = if (toolResult.success) AgentRunStepStatus.SUCCEEDED else AgentRunStepStatus.FAILED,
                        toolResult = toolResult,
                    )
                )
                if (!toolResult.success) {
                    transaction?.rollbackTransaction()
                    return toolRunResult(
                        decision = decision,
                        status = AgentRunStatus.TOOL_FAILED,
                        message = toolResult.message,
                        stepResults = stepResults,
                        rolledBack = transaction != null,
                    )
                }
            }
            val batchUndoCreated = transaction?.commitTransaction() == true
            return toolRunResult(
                decision = decision,
                status = AgentRunStatus.SUCCESS,
                message = stepResults.lastOrNull()?.toolResult?.message.orEmpty(),
                stepResults = stepResults,
                batchUndoCreated = batchUndoCreated,
            )
        } catch (error: Throwable) {
            transaction?.rollbackTransaction()
            throw error
        }
    }

    private fun toolRunResult(
        decision: DoubaoAgentDecision,
        status: AgentRunStatus,
        message: String,
        stepResults: List<AgentToolStepResult>,
        rolledBack: Boolean = false,
        batchUndoCreated: Boolean = false,
    ): AgentRunResult {
        val lastStep = stepResults.lastOrNull()
        return AgentRunResult(
            status = status,
            message = message,
            assistantText = decision.assistantText,
            toolCall = lastStep?.toolCall,
            toolResult = lastStep?.toolResult,
            stepResults = stepResults,
            rolledBack = rolledBack,
            batchUndoCreated = batchUndoCreated,
            suggestions = decision.suggestions,
        )
    }

    private fun tooManyToolCallsResult(decision: DoubaoAgentDecision): AgentRunResult =
        AgentRunResult(
            status = AgentRunStatus.MULTIPLE_TOOLS,
            message = "一次请少于 20 步，请拆短一点。",
            assistantText = decision.assistantText,
        )
}
