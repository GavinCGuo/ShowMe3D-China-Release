package com.example.showme3d

import com.example.showme3d.studio.agent.AgentInstructionGuard
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgentInstructionGuardTest {
    @Test
    fun allowsSingleGenerationPromptWithStyleContinuation() {
        assertNull(
            AgentInstructionGuard.blockingMessage("生成一张小车图片并改成卡通风格")
        )
    }

    @Test
    fun allowsSingleGenerationPromptWithScenePlacementContinuation() {
        assertNull(
            AgentInstructionGuard.blockingMessage("生成一个小车 3D 模型并加入场景")
        )
    }

    @Test
    fun allowsClearEditThenEditInstruction() {
        assertNull(
            AgentInstructionGuard.blockingMessage("加一个立方体再把它变红")
        )
    }

    @Test
    fun allowsGenerationThenIndependentSceneEdit() {
        assertNull(
            AgentInstructionGuard.blockingMessage("生成一张小车图片并删除选中的物体")
        )
    }

    @Test
    fun blocksTwentyIndependentActions() {
        val text = (1..20).joinToString("再") { "加一个立方体" }

        assertNotNull(
            AgentInstructionGuard.blockingMessage(text)
        )
    }
}
