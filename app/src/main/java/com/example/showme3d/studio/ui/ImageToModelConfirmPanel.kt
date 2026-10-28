package com.example.showme3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.example.showme3d.studio.aigc.RodinGenerationTier
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

@Composable
fun ImageToModelConfirmPanel(
    imageName: String,
    prompt: String,
    promptTemplate: AigcPromptTemplate? = null,
    promptTemplateUseCase: AigcPromptUseCase = AigcPromptUseCase.IMAGE_TO_MODEL,
    imagePreprocessMode: AigcImagePreprocessMode,
    advancedOpen: Boolean,
    tier: RodinGenerationTier,
    qualityOverride: Int?,
    meshMode: String,
    hdTexture: Boolean,
    creative: Boolean,
    busy: Boolean,
    message: String,
    onPromptChange: (String) -> Unit,
    onPromptTemplateChange: (AigcPromptTemplate?) -> Unit = {},
    onImagePreprocessModeChange: (AigcImagePreprocessMode) -> Unit,
    onAdvancedToggle: () -> Unit,
    onTierChange: (RodinGenerationTier) -> Unit,
    onMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit,
    onHdTextureChange: (Boolean) -> Unit,
    onCreativeChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    title: String = "图片生成 3D",
    description: String = "将「${imageName.take(18)}」送入 Rodin 图生 3D，成功后用模型替换原图片。",
    showPreprocessSelector: Boolean = true,
    promptPlaceholder: String = "例如：做成干净产品展示模型、保留玩具风格",
    confirmLabel: String = "确认生成",
) {
    Column(
        modifier = Modifier
            .width(430.dp)
            .clip(RoundedCornerShape(22.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.98f))
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StitchSymbol(StitchSymbols.AutoAwesome, "AI 生成", size = 24.dp, color = StitchColors.HomeGreenDark)
                Spacer(Modifier.width(8.dp))
                Text(text = title, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = StitchColors.PanelText)
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary,
            )
        }
        if (showPreprocessSelector) {
            ImageToModelPreprocessSelector(
                selected = imagePreprocessMode,
                enabled = !busy,
                onChange = onImagePreprocessModeChange,
            )
        }
        AigcPromptTemplateSelector(
            useCase = promptTemplateUseCase,
            selectedTemplate = promptTemplate,
            enabled = !busy,
            onTemplateChange = onPromptTemplateChange,
        )
        ImageToModelPromptField(
            value = prompt,
            enabled = !busy,
            label = if (promptTemplate != null) "个人提示词（模板填空）" else "补充提示词（可选）",
            placeholder = if (promptTemplate != null) {
                "只填写主体、材质、用途或额外细节"
            } else {
                promptPlaceholder
            },
            onChange = onPromptChange,
        )
        ImageToModelAdvancedSection(
            open = advancedOpen,
            tier = tier,
            selectedQualityOverride = qualityOverride,
            selectedMeshMode = meshMode,
            hdTexture = hdTexture,
            creative = creative,
            enabled = !busy,
            onToggleOpen = onAdvancedToggle,
            onTierChange = onTierChange,
            onMeshBudgetChange = onMeshBudgetChange,
            onHdTextureChange = onHdTextureChange,
            onCreativeChange = onCreativeChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImageToModelButton(
                label = "返回",
                style = StudioButtonStyle.Secondary,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            ImageToModelButton(
                label = if (busy) "生成中..." else confirmLabel,
                style = StudioButtonStyle.Primary,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                onClick = onGenerate,
            )
        }
    }
}

@Composable
private fun ImageToModelPreprocessSelector(
    selected: AigcImagePreprocessMode,
    enabled: Boolean,
    onChange: (AigcImagePreprocessMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(text = "图生 3D 前处理", fontSize = 13.sp, color = StudioColors.TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AigcImagePreprocessMode.entries.forEach { mode ->
                ImageToModelChoiceChip(
                    label = mode.displayName,
                    selected = selected == mode,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(mode) },
                )
            }
        }
        Text(
            text = when (selected) {
                AigcImagePreprocessMode.NONE -> "直接把当前图片送入 Rodin。"
                AigcImagePreprocessMode.LOCAL_SUBJECT -> "用包内 U²-Net 生成透明主体图。"
                AigcImagePreprocessMode.SEEDREAM_CLEAN -> "用 Seedream 生成底色纯净、主体清晰的 3D 前处理图。"
            },
            fontSize = 11.sp,
            color = StudioColors.TextSecondary,
        )
    }
}

@Composable
private fun ImageToModelAdvancedSection(
    open: Boolean,
    tier: RodinGenerationTier,
    selectedQualityOverride: Int?,
    selectedMeshMode: String,
    hdTexture: Boolean,
    creative: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onTierChange: (RodinGenerationTier) -> Unit,
    onMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit,
    onHdTextureChange: (Boolean) -> Unit,
    onCreativeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .spatialHoverEffect()
                .clickable { if (enabled) onToggleOpen() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "高级参数", fontSize = 13.sp, color = StudioColors.TextPrimary)
                Text(
                    text = imageToModelAdvancedSummary(
                        tier = tier,
                        qualityOverride = selectedQualityOverride,
                        meshMode = selectedMeshMode,
                        hdTexture = hdTexture,
                        creative = creative,
                    ),
                    fontSize = 11.sp,
                    color = StudioColors.TextSecondary,
                )
            }
            Text(
                text = if (open) "收起" else "展开",
                fontSize = 12.sp,
                color = StudioColors.Primary,
            )
        }
        if (open) {
            ImageToModelMeshBudgetSelector(
                selectedQualityOverride = selectedQualityOverride,
                selectedMeshMode = selectedMeshMode,
                enabled = enabled,
                onMeshBudgetChange = onMeshBudgetChange,
            )
            ImageToModelTierSelector(
                selectedTier = tier,
                enabled = enabled,
                onTierChange = onTierChange,
            )
            ImageToModelBooleanSelector(
                title = "贴图",
                falseLabel = "标准贴图",
                trueLabel = "高清贴图",
                selected = hdTexture,
                enabled = enabled,
                onChange = onHdTextureChange,
            )
            ImageToModelBooleanSelector(
                title = "几何模式",
                falseLabel = "保真",
                trueLabel = "创意",
                selected = creative,
                enabled = enabled,
                onChange = onCreativeChange,
            )
        }
    }
}

private data class ImageToModelMeshBudgetOption(
    val displayName: String,
    val qualityOverride: Int?,
    val meshMode: String,
)

private val ImageToModelMeshBudgetOptions = listOf(
    ImageToModelMeshBudgetOption("4K", 4_000, "Raw"),
    ImageToModelMeshBudgetOption("8K", 8_000, "Raw"),
    ImageToModelMeshBudgetOption("10K", 10_000, "Raw"),
    ImageToModelMeshBudgetOption("20K", 20_000, "Raw"),
    ImageToModelMeshBudgetOption("50K", 50_000, "Raw"),
    ImageToModelMeshBudgetOption("100K", 100_000, "Raw"),
    ImageToModelMeshBudgetOption("500K", 500_000, "Raw"),
)

@Composable
private fun ImageToModelMeshBudgetSelector(
    selectedQualityOverride: Int?,
    selectedMeshMode: String,
    enabled: Boolean,
    onMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "面数（三角面 · Raw）", fontSize = 13.sp, color = StudioColors.TextPrimary)
        ImageToModelMeshBudgetOptions.chunked(4).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    ImageToModelChoiceChip(
                        label = option.displayName,
                        selected = option.qualityOverride == selectedQualityOverride &&
                            option.meshMode == selectedMeshMode,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onMeshBudgetChange(option.qualityOverride, option.meshMode) },
                    )
                }
                repeat(4 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageToModelTierSelector(
    selectedTier: RodinGenerationTier,
    enabled: Boolean,
    onTierChange: (RodinGenerationTier) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "质量", fontSize = 13.sp, color = StudioColors.TextPrimary)
        RodinGenerationTier.values().toList().chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    ImageToModelChoiceChip(
                        label = option.displayName,
                        selected = option == selectedTier,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onTierChange(option) },
                    )
                }
                repeat(3 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageToModelBooleanSelector(
    title: String,
    falseLabel: String,
    trueLabel: String,
    selected: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, fontSize = 13.sp, color = StudioColors.TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImageToModelChoiceChip(
                label = falseLabel,
                selected = !selected,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onChange(false) },
            )
            ImageToModelChoiceChip(
                label = trueLabel,
                selected = selected,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onChange(true) },
            )
        }
    }
}

@Composable
private fun ImageToModelChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    StudioChoiceChip(
        label = label,
        selected = selected,
        enabled = enabled,
        modifier = modifier,
        height = 28.dp,
        onClick = onClick,
    )
}

private fun imageToModelAdvancedSummary(
    tier: RodinGenerationTier,
    qualityOverride: Int?,
    meshMode: String,
    hdTexture: Boolean,
    creative: Boolean,
): String {
    val faceBudget = ImageToModelMeshBudgetOptions.firstOrNull {
        it.qualityOverride == qualityOverride && it.meshMode == meshMode
    }?.displayName ?: "默认面数"
    val texture = if (hdTexture) "高清贴图" else "标准贴图"
    val mode = if (creative) "创意" else "保真"
    return "$faceBudget 三角面 / ${tier.displayName} / $texture / $mode"
}

@Composable
private fun ImageToModelOptionToggle(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .spatialHoverEffect()
            .clickable { if (enabled) onChange(!selected) }
            .background(if (enabled) StudioColors.Surface else StudioColors.SurfaceDisabled)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, fontSize = 15.sp, color = StudioColors.TextPrimary)
            Text(text = description, fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(66.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (selected) StudioColors.Primary else StudioColors.SurfaceDisabled)
                .padding(4.dp),
            contentAlignment = if (selected) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(StudioColors.SurfaceRaised),
            )
        }
    }
}

@Composable
private fun ImageToModelPromptField(
    value: String,
    enabled: Boolean,
    label: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 12.sp, color = StudioColors.TextPrimary)
        StudioInputField(
            value = value,
            placeholder = placeholder,
            enabled = enabled,
            height = 58.dp,
            textSize = 13,
            onChange = onChange,
        )
    }
}

@Composable
private fun ImageToModelButton(
    label: String,
    style: StudioButtonStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    StudioTextButton(
        label = label,
        enabled = enabled,
        modifier = modifier,
        style = style,
        height = 40.dp,
        onClick = onClick,
    )
}
