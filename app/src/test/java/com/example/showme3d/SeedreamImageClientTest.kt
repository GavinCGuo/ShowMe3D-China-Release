package com.example.showme3d

import com.example.showme3d.studio.aigc.SeedreamImageClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedreamImageClientTest {
    @Test
    fun cleanImagePromptRequestsSingleSubjectClean3dInput() {
        val empty = SeedreamImageClient.cleanImagePrompt("")
        val extra = SeedreamImageClient.cleanImagePrompt("白色背景")

        assertTrue(empty.contains("图生 3D 前处理任务"))
        assertTrue(empty.contains("请不要原样返回输入图"))
        assertTrue(empty.contains("保留输入图中最主要的一个主体"))
        assertTrue(empty.contains("请移除遮挡物"))
        assertTrue(empty.contains("合理补全被遮挡的主体部分"))
        assertTrue(empty.contains("主体完整、居中、边缘清晰、背景纯净"))
        assertTrue(empty.contains("不要裁切主体、不要新增主体、不要改变主体类别"))
        assertTrue(empty.contains("额外要求为：无"))
        assertTrue(extra.contains("额外要求为：白色背景"))
    }

    @Test
    fun strokeMainViewPromptAllowsCreativeCompletionAndEmptyDirection() {
        val empty = SeedreamImageClient.strokeMainViewPrompt("")
        val crab = SeedreamImageClient.strokeMainViewPrompt("螃蟹，卡通风格")

        assertTrue(empty.contains("当前观察方向画出的主视图草图"))
        assertTrue(empty.contains("尊重草图的主体数量、主轮廓、核心构成"))
        assertTrue(empty.contains("不需要逐线复刻"))
        assertTrue(empty.contains("大胆丰富材质、色彩、细节与风格"))
        assertTrue(empty.contains("没有额外提示时，直接依据草图发挥，不要向用户追问"))
        assertTrue(empty.contains("额外创作方向：无"))
        assertTrue(crab.contains("额外创作方向：螃蟹，卡通风格"))
    }

    @Test
    fun buildRequestJsonDisablesWatermarkByDefault() {
        val body = SeedreamImageClient.buildRequestJson(
            prompt = "生成一张图片",
            imageInputs = emptyList(),
        )

        assertFalse(body.getBoolean("watermark"))
    }
}
