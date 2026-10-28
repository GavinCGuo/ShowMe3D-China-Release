package com.example.showme3d

import com.example.showme3d.studio.agent.AgentSuggestionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSuggestionParserTest {
    @Test
    fun parsesAiSuggestionLinesAndRemovesProtocolFromMessage() {
        val parsed = AgentSuggestionParser.parse(
            """
                已经完成基础搭建。
                SUGGEST:
                - 加动画｜给选中的物体加一个默认动画
                - 加视点｜添加一个视点
            """.trimIndent()
        )

        assertEquals("已经完成基础搭建。", parsed.message)
        assertEquals(2, parsed.suggestions.size)
        assertEquals("加动画", parsed.suggestions[0].actionLabel)
        assertEquals("给选中的物体加一个默认动画", parsed.suggestions[0].prompt)
        assertEquals("", parsed.suggestions[0].message)
        assertEquals("加视点", parsed.suggestions[1].title)
    }

    @Test
    fun keepsAtMostThreeAiSuggestions() {
        val parsed = AgentSuggestionParser.parse(
            """
                可以继续。
                SUGGEST: 一｜指令一
                SUGGEST: 二｜指令二
                SUGGEST: 三｜指令三
                SUGGEST: 四｜指令四
            """.trimIndent()
        )

        assertEquals(3, parsed.suggestions.size)
        assertEquals(listOf("一", "二", "三"), parsed.suggestions.map { it.actionLabel })
    }

    @Test
    fun ignoresSuggestionsWhenAssistantIsClarifying() {
        val parsed = AgentSuggestionParser.parse(
            """
                CLARIFY: 你想加哪种物体？
                SUGGEST: 加立方体｜加一个立方体
            """.trimIndent()
        )

        assertEquals("CLARIFY: 你想加哪种物体？", parsed.message)
        assertTrue(parsed.suggestions.isEmpty())
    }

    @Test
    fun parsesChineseRecommendationSection() {
        val parsed = AgentSuggestionParser.parse(
            """
                已完成。
                建议：
                - 加动画｜给当前选中对象加一个默认动画
            """.trimIndent()
        )

        assertEquals("已完成。", parsed.message)
        assertEquals(listOf("加动画"), parsed.suggestions.map { it.actionLabel })
    }

}
