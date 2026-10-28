package com.example.showme3d.studio.agent

data class AgentSuggestionPlan(
    val title: String,
    val message: String,
    val prompt: String,
    val actionLabel: String = "采纳",
)

data class ParsedAgentAssistantText(
    val message: String,
    val suggestions: List<AgentSuggestionPlan>,
)

object AgentSuggestionParser {
    private const val MAX_SUGGESTIONS = 3

    fun parse(rawText: String): ParsedAgentAssistantText {
        if (rawText.isBlank()) return ParsedAgentAssistantText("", emptyList())
        val messageLines = mutableListOf<String>()
        val suggestions = mutableListOf<AgentSuggestionPlan>()
        var readingSuggestions = false

        rawText.lines().forEach { rawLine ->
            val trimmed = rawLine.trim()
            when {
                trimmed.equals("SUGGEST:", ignoreCase = true) ||
                    trimmed.equals("SUGGESTIONS:", ignoreCase = true) ||
                    trimmed.equals("建议：") ||
                    trimmed.equals("建议:") ||
                    trimmed.equals("推荐：") ||
                    trimmed.equals("推荐:") -> {
                    readingSuggestions = true
                }
                trimmed.startsWith("SUGGEST:", ignoreCase = true) -> {
                    parseSuggestionLine(trimmed.substringAfter(':'))
                        ?.let { if (suggestions.size < MAX_SUGGESTIONS) suggestions += it }
                }
                trimmed.startsWith("建议：") || trimmed.startsWith("建议:") ||
                    trimmed.startsWith("推荐：") || trimmed.startsWith("推荐:") -> {
                    parseSuggestionLine(trimmed.substringAfter(':', trimmed.substringAfter('：')))
                        ?.let { if (suggestions.size < MAX_SUGGESTIONS) suggestions += it }
                }
                readingSuggestions -> {
                    parseSuggestionLine(trimmed)
                        ?.let { if (suggestions.size < MAX_SUGGESTIONS) suggestions += it }
                }
                else -> messageLines += rawLine
            }
        }

        val message = messageLines.joinToString("\n").trim()
        val visibleSuggestions = if (
            message.startsWith("CLARIFY:", ignoreCase = true) ||
            message.startsWith("CLARIFY：", ignoreCase = true)
        ) {
            emptyList()
        } else {
            suggestions.take(MAX_SUGGESTIONS)
        }
        return ParsedAgentAssistantText(message, visibleSuggestions)
    }

    private fun parseSuggestionLine(rawLine: String): AgentSuggestionPlan? {
        val line = rawLine
            .trim()
            .removePrefix("-")
            .trim()
            .replace(Regex("^\\d+[.)]\\s*"), "")
            .trim()
        if (line.isBlank()) return null

        val parts = line.split('|', '｜')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val actionLabel = parts[0].take(12)
        val prompt = parts[1]
        return AgentSuggestionPlan(
            title = actionLabel,
            message = "",
            prompt = prompt,
            actionLabel = actionLabel,
        )
    }
}
