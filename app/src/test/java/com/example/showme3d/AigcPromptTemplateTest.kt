package com.example.showme3d

import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.example.showme3d.studio.aigc.buildAigcPrompt
import com.example.showme3d.studio.aigc.buildAigcPromptDetails
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AigcPromptTemplateTest {
    @Test
    fun buildPromptUsesTextPromptAsSourceDescription() {
        val prompt = buildAigcPrompt(
            useCase = AigcPromptUseCase.TEXT_TO_MODEL,
            template = AigcPromptTemplate.CARTOON,
            userPrompt = "红色小螃蟹",
        )

        assertTrue(prompt.startsWith("根据用户提供的文本描述生成一个完整可旋转查看的 3D 模型"))
        assertTrue(prompt.contains("文本描述：红色小螃蟹"))
        assertTrue(prompt.contains("模板要求：卡通玩具风 3D 模型"))
        assertFalse(prompt.contains("用户额外要求：红色小螃蟹"))
    }

    @Test
    fun buildPromptKeepsFixedSectionWithoutTemplateOrUserInput() {
        val prompt = buildAigcPrompt(
            useCase = AigcPromptUseCase.IMAGE_TO_MODEL,
            template = null,
            userPrompt = "  ",
        )

        assertTrue(prompt.contains("基于参考图片生成一个完整可旋转查看的 3D 模型"))
        assertFalse(prompt.contains("模板要求："))
        assertFalse(prompt.contains("用户额外要求："))
        assertTrue(prompt.isNotBlank())
    }

    @Test
    fun sourceTextAndUserExtraStaySeparatedForTextObject() {
        val prompt = buildAigcPrompt(
            useCase = AigcPromptUseCase.TEXT_TO_IMAGE,
            template = AigcPromptTemplate.LOW_POLY,
            sourceText = "蓝色鲸鱼",
            userPrompt = "电影感灯光",
        )

        assertTrue(prompt.contains("文本描述：蓝色鲸鱼"))
        assertTrue(prompt.contains("模板要求：低多边形风格"))
        assertTrue(prompt.contains("用户额外要求：电影感灯光"))
        assertFalse(prompt.contains("文本描述：电影感灯光"))
    }

    @Test
    fun imageAndModelUseCasesHaveDifferentFixedTasks() {
        val imagePrompt = AigcPromptUseCase.TEXT_TO_IMAGE.fixedPrompt("鲸鱼")
        val modelPrompt = AigcPromptUseCase.TEXT_TO_MODEL.fixedPrompt("鲸鱼")

        assertTrue(imagePrompt.contains("单主体图片"))
        assertTrue(imagePrompt.contains("背景纯净"))
        assertTrue(modelPrompt.contains("3D 模型"))
        assertTrue(modelPrompt.contains("可旋转查看"))
        assertFalse(modelPrompt.contains("生成一张"))
    }

    @Test
    fun strokeDirectModelBuildsSeparateImageAndModelPrompts() {
        val seedreamExtraPrompt = buildAigcPromptDetails(
            useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
            template = AigcPromptTemplate.CARTOON,
            userPrompt = "加一个红色围巾",
        )
        val rodinPrompt = buildAigcPrompt(
            useCase = AigcPromptUseCase.STROKE_TO_MODEL,
            template = AigcPromptTemplate.CARTOON,
            userPrompt = "加一个红色围巾",
        )

        assertTrue(seedreamExtraPrompt.contains("模板要求：卡通玩具风 2D 参考图"))
        assertTrue(seedreamExtraPrompt.contains("用户额外要求：加一个红色围巾"))
        assertFalse(seedreamExtraPrompt.contains("根据输入草图生成一张干净的 2D 参考图"))
        assertFalse(seedreamExtraPrompt.contains("完整可旋转查看的 3D 模型"))

        assertTrue(rodinPrompt.contains("根据输入参考图生成一个完整可旋转查看的 3D 模型"))
        assertTrue(rodinPrompt.contains("尊重主体的主要轮廓和核心构成"))
        assertTrue(rodinPrompt.contains("合理补全不可见的背面、结构和体积"))
        assertTrue(rodinPrompt.contains("模板要求：卡通玩具风 3D 模型"))
        assertTrue(rodinPrompt.contains("用户额外要求：加一个红色围巾"))
        assertFalse(rodinPrompt.contains("生成一张干净的 2D 参考图"))
    }

    @Test
    fun strokeSeedreamDetailsCanBeBlankWithoutTemplateOrUserPrompt() {
        val seedreamExtraPrompt = buildAigcPromptDetails(
            useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
            template = null,
            userPrompt = " ",
        )

        assertTrue(seedreamExtraPrompt.isBlank())
    }
}
