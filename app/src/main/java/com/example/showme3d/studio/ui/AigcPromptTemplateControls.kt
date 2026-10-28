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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

@Composable
fun AigcPromptTemplateSelector(
    useCase: AigcPromptUseCase,
    selectedTemplate: AigcPromptTemplate?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTemplateChange: (AigcPromptTemplate?) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioRadii.Section))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Section))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "风格模板", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = StitchColors.PanelText)
                Text(
                    text = selectedTemplate?.let { "模板模式 · 会追加风格要求，只填写个人补充" }
                        ?: "可选：进入模板模式后追加固定风格要求",
                    fontSize = 11.sp,
                    color = StudioColors.TextSecondary,
                )
            }
            if (selectedTemplate != null) {
                AigcTemplateClearButton(
                    enabled = enabled,
                    onClick = { onTemplateChange(null) },
                )
            }
        }
        AigcPromptTemplate.entries.chunked(2).forEach { rowTemplates ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTemplates.forEach { template ->
                    AigcTemplateChip(
                        template = template,
                        selected = selectedTemplate == template,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onTemplateChange(template) },
                    )
                }
                if (rowTemplates.size < 2) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        selectedTemplate?.let { template ->
            Text(
                text = template.stylePrompt(useCase).compactForUi(),
                fontSize = 11.sp,
                color = StudioColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AigcTemplateChip(
    template: AigcPromptTemplate,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(StudioRadii.Control))
            .spatialHoverEffect()
            .background(
                when {
                    !enabled -> StudioColors.SurfaceDisabled
                    selected -> StitchColors.HomeGreen
                    else -> StitchColors.PanelSurface
                }
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) StitchColors.HomeGreenDark else StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Control))
            .clickable { if (enabled) onClick() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = template.displayName,
            fontFamily = StitchNunito,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = when {
                !enabled -> StudioColors.TextSecondary
                selected -> StudioColors.SurfaceRaised
                else -> StudioColors.TextPrimary
            },
        )
        Text(
            text = template.shortDescription,
            fontSize = 10.sp,
            color = when {
                !enabled -> StudioColors.TextSecondary
                selected -> StudioColors.PrimarySoft
                else -> StudioColors.TextSecondary
            },
        )
    }
}

@Composable
private fun AigcTemplateClearButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(StudioRadii.Control))
            .spatialHoverEffect()
            .clickable { if (enabled) onClick() }
            .background(if (enabled) StudioColors.SurfaceRaised else StudioColors.SurfaceDisabled)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "取消模板", fontSize = 11.sp, color = if (enabled) StudioColors.Danger else StudioColors.TextSecondary)
    }
}

private fun String.compactForUi(): String =
    replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .let { if (it.length > 58) "${it.take(58)}..." else it }
