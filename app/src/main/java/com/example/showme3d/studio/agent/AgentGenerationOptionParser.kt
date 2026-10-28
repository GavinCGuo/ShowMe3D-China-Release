package com.example.showme3d.studio.agent

import com.example.showme3d.studio.aigc.RodinGenerationOptions
import com.example.showme3d.studio.aigc.RodinGenerationTier
import java.util.Locale

object AgentGenerationOptionParser {
    val faceBudgetValues: List<Int> = listOf(4_000, 8_000, 10_000, 20_000, 50_000, 100_000, 500_000)
    val faceBudgetSchemaValues: List<String> = listOf("4k", "8k", "10k", "20k", "50k", "100k", "500k")
    val rodinTierSchemaValues: List<String> = listOf(
        "minimum",
        "extreme_low",
        "low",
        "medium",
        "high",
        "extreme_high",
    )
    val textureSchemaValues: List<String> = listOf("standard", "hd")

    fun parseRodinOptions(
        arguments: Map<String, Any?>,
        defaults: RodinGenerationOptions,
    ): Result<RodinGenerationOptions> = runCatching {
        defaults.copy(
            tier = parseTier(arguments["rodin_tier"] ?: arguments["tier"], defaults.tier),
            qualityOverride = parseFaceBudget(
                arguments["face_budget"] ?: arguments["quality_override"],
                defaults.qualityOverride,
            ),
            meshMode = "Raw",
            hdTexture = parseTexture(arguments["texture"], arguments["hd_texture"], defaults.hdTexture),
            creative = parseBoolean(arguments["creative"], defaults.creative, fieldName = "creative"),
        )
    }

    fun rodinOptionsData(options: RodinGenerationOptions): Map<String, Any?> =
        mapOf(
            "rodin_tier" to options.tier.name.lowercase(Locale.US),
            "face_budget" to options.qualityOverride,
            "mesh_mode" to options.meshMode,
            "texture" to if (options.hdTexture) "hd" else "standard",
            "creative" to options.creative,
        )

    fun strokeExtraPrompt(rawPrompt: String): String {
        val trimmed = rawPrompt.trim()
        if (trimmed.isBlank() || isFullStrokeDefaultPrompt(trimmed)) return ""

        var text = trimmed
        val phraseReplacements = listOf(
            "请" to "",
            "帮我" to "",
            "帮忙" to "",
            "把这段线稿" to "",
            "把这个线稿" to "",
            "把这段草图" to "",
            "把这个草图" to "",
            "把这段笔画" to "",
            "把这个笔画" to "",
            "把这段画线" to "",
            "把这个画线" to "",
            "根据线稿" to "",
            "根据草图" to "",
            "根据笔画" to "",
            "用这段线稿" to "",
            "用这个草图" to "",
            "生成一张图片" to "",
            "生成图片" to "",
            "生成一张图" to "",
            "生成图" to "",
            "生成 3D 模型" to "",
            "生成 3d 模型" to "",
            "生成3D模型" to "",
            "生成3d模型" to "",
            "生成模型" to "",
            "变成图片" to "",
            "变成图" to "",
            "变成 3D 模型" to "",
            "变成3D模型" to "",
            "变成模型" to "",
            "做成图片" to "",
            "做成图" to "",
            "做成 3D 模型" to "",
            "做成3D模型" to "",
            "做成模型" to "",
            "转成图片" to "",
            "转成图" to "",
            "转成 3D 模型" to "",
            "转成3D模型" to "",
            "转成模型" to "",
        )
        phraseReplacements.forEach { (old, new) ->
            text = text.replace(old, new, ignoreCase = true)
        }
        text = text
            .replace(Regex("^(生成|画成|画一个|做一个|做成|转成|变成)"), "")
            .replace(Regex("\\s*(3D|3d)?\\s*(图片|图|模型)\\s*$"), "")
            .replace(Regex("[，。,.！？!?:：；;]+"), " ")
            .trim()

        val generic = text
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), "")
        return if (generic in setOf("", "图片", "图", "模型", "3d", "3d模型", "三视图", "线稿", "草图", "笔画")) {
            ""
        } else {
            text
        }
    }

    private fun isFullStrokeDefaultPrompt(prompt: String): Boolean =
        (prompt.contains("这是一个三视图结构解析任务") &&
            prompt.contains("输入图包含三个带标注的线稿")) ||
            (prompt.contains("参考图是用户从当前观察方向画出的主视图草图") &&
                prompt.contains("额外创作方向"))

    private fun parseTier(raw: Any?, defaultValue: RodinGenerationTier): RodinGenerationTier {
        if (raw == null) return defaultValue
        val rawText = raw.toString().trim()
        if (rawText.isBlank() || rawText.equals("default", ignoreCase = true)) return defaultValue
        val normalized = normalizeEnumToken(rawText)
        return RodinGenerationTier.values().firstOrNull { tier ->
            normalized == normalizeEnumToken(tier.name) ||
                normalized == normalizeEnumToken(tier.apiValue) ||
                rawText == tier.displayName
        } ?: throw IllegalArgumentException("不支持的 Rodin 质量档位：$rawText")
    }

    private fun parseFaceBudget(raw: Any?, defaultValue: Int?): Int? {
        if (raw == null) return defaultValue
        val parsed = when (raw) {
            is Number -> raw.toInt()
            else -> {
                val text = raw.toString().trim().lowercase(Locale.US)
                if (text.isBlank() || text == "default") return defaultValue
                val normalized = text.replace(",", "").replace("_", "").replace(" ", "")
                if (normalized.endsWith("k")) {
                    normalized.removeSuffix("k").toIntOrNull()?.times(1000)
                } else {
                    normalized.toIntOrNull()
                }
            }
        } ?: throw IllegalArgumentException("不支持的面数：$raw")

        if (parsed !in faceBudgetValues) {
            throw IllegalArgumentException("不支持的面数：$raw。可选值：${faceBudgetSchemaValues.joinToString()}")
        }
        return parsed
    }

    private fun parseTexture(rawTexture: Any?, rawHdTexture: Any?, defaultValue: Boolean): Boolean {
        if (rawTexture == null) {
            return parseBoolean(rawHdTexture, defaultValue, fieldName = "hd_texture")
        }
        val text = rawTexture.toString().trim()
        if (text.isBlank() || text.equals("default", ignoreCase = true)) return defaultValue
        return when (normalizeEnumToken(text)) {
            "standard", "std", "normal", "default", "standard_texture" -> false
            "hd", "high", "high_texture", "hd_texture" -> true
            else -> when (text) {
                "标准", "标准贴图" -> false
                "高清", "高清贴图" -> true
                else -> throw IllegalArgumentException("不支持的贴图选项：$text")
            }
        }
    }

    private fun parseBoolean(raw: Any?, defaultValue: Boolean, fieldName: String): Boolean {
        if (raw == null) return defaultValue
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> {
                val text = raw.toString().trim()
                if (text.isBlank() || text.equals("default", ignoreCase = true)) return defaultValue
                when (normalizeEnumToken(text)) {
                    "true", "yes", "y", "1", "on", "enable", "enabled" -> true
                    "false", "no", "n", "0", "off", "disable", "disabled" -> false
                    else -> throw IllegalArgumentException("不支持的布尔参数 $fieldName：$text")
                }
            }
        }
    }

    private fun normalizeEnumToken(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace("-", "_")
            .replace(".", "_")
            .replace(" ", "_")
}
