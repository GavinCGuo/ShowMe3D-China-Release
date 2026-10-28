package com.example.showme3d

import com.example.showme3d.studio.agent.AgentGenerationOptionParser
import com.example.showme3d.studio.aigc.RodinGenerationOptions
import com.example.showme3d.studio.aigc.RodinGenerationTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentGenerationOptionParserTest {
    @Test
    fun parseRodinOptionsKeepsFastDefaultsWithoutAgentOverrides() {
        val parsed = AgentGenerationOptionParser.parseRodinOptions(
            arguments = emptyMap(),
            defaults = RodinGenerationOptions(),
        ).getOrThrow()

        assertEquals(RodinGenerationTier.EXTREME_LOW, parsed.tier)
        assertEquals(20_000, parsed.qualityOverride)
        assertEquals("Raw", parsed.meshMode)
    }

    @Test
    fun parseRodinOptionsAppliesAgentOverrides() {
        val parsed = AgentGenerationOptionParser.parseRodinOptions(
            arguments = mapOf(
                "rodin_tier" to "high",
                "face_budget" to "50k",
                "texture" to "hd",
                "creative" to true,
            ),
            defaults = RodinGenerationOptions(),
        ).getOrThrow()

        assertEquals(RodinGenerationTier.HIGH, parsed.tier)
        assertEquals(50_000, parsed.qualityOverride)
        assertEquals("Raw", parsed.meshMode)
        assertTrue(parsed.hdTexture)
        assertTrue(parsed.creative)
    }

    @Test
    fun parseRodinOptionsRejectsUnsupportedFaceBudget() {
        val result = AgentGenerationOptionParser.parseRodinOptions(
            arguments = mapOf("face_budget" to "12k"),
            defaults = RodinGenerationOptions(),
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun strokeExtraPromptDropsGenericCommandsButKeepsSubjectStyle() {
        assertEquals("", AgentGenerationOptionParser.strokeExtraPrompt("把这段线稿生成图片"))
        assertEquals("一只红色小螃蟹", AgentGenerationOptionParser.strokeExtraPrompt("把这个草图生成一只红色小螃蟹 3D 模型"))
        assertEquals("红色卡通小螃蟹", AgentGenerationOptionParser.strokeExtraPrompt("红色卡通小螃蟹"))
    }

}
