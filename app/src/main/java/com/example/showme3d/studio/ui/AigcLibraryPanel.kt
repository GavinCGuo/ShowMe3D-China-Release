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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.library.PresetAsset
import com.example.showme3d.studio.aigc.AigcGenerationRecord
import com.example.showme3d.studio.aigc.AigcGenerationRecordStatus
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AigcLibraryPanel(
    imageAssets: List<PresetAsset>,
    modelAssets: List<PresetAsset>,
    records: List<AigcGenerationRecord>,
    onPick: (PresetAsset) -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AigcLibraryTab.IMAGES) }
    Column(
        modifier = Modifier
            .width(320.dp)
            .height(560.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(StitchColors.PanelSurface)
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "本地生成库", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = StitchColors.PanelText)
                Text(
                    text = "${imageAssets.size} 张图片 / ${modelAssets.size} 个 3D / ${records.size} 条记录",
                    fontSize = 12.sp,
                    color = StudioColors.TextSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .spatialHoverEffect()
                    .clickable { onRefresh() }
                    .background(StudioColors.SurfaceMuted)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "刷新", fontSize = 12.sp, color = StudioColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AigcLibraryTab.values().forEach { tab ->
                AigcLibraryTabButton(
                    tab = tab,
                    count = when (tab) {
                        AigcLibraryTab.IMAGES -> imageAssets.size
                        AigcLibraryTab.MODELS -> modelAssets.size
                        AigcLibraryTab.RECORDS -> records.size
                    },
                    selected = selectedTab == tab,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTab = tab },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (selectedTab) {
            AigcLibraryTab.IMAGES -> AigcAssetList(
                assets = imageAssets,
                emptyTitle = "还没有 AI 图片",
                emptyMessage = "Seedream 生图、AI 清理和笔画主视图参考图会出现在这里。",
                onPick = onPick,
                modifier = Modifier.weight(1f),
            )
            AigcLibraryTab.MODELS -> AigcAssetList(
                assets = modelAssets,
                emptyTitle = "还没有 AI 3D",
                emptyMessage = "Rodin 生成模型会出现在这里。",
                onPick = onPick,
                modifier = Modifier.weight(1f),
            )
            AigcLibraryTab.RECORDS -> AigcRecordList(
                records = records,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class AigcLibraryTab(
    val title: String,
) {
    IMAGES("AI 图片"),
    MODELS("AI 3D"),
    RECORDS("生成记录"),
}

@Composable
private fun AigcLibraryTabButton(
    tab: AigcLibraryTab,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .shadow(if (selected) 4.dp else 0.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .spatialHoverEffect()
            .clickable { onClick() }
            .background(if (selected) StitchColors.HomeGreen else StitchColors.PanelSurfaceMuted)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${tab.title} $count",
            fontFamily = StitchNunito,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selected) Color.White else StitchColors.PanelText,
        )
    }
}

@Composable
private fun AigcAssetList(
    assets: List<PresetAsset>,
    emptyTitle: String,
    emptyMessage: String,
    onPick: (PresetAsset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (assets.isEmpty()) {
        EmptyAigcLibraryState(
            title = emptyTitle,
            message = emptyMessage,
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            assets.forEach { asset ->
                AigcLibraryAssetCard(
                    asset = asset,
                    onPick = { onPick(asset) },
                )
            }
        }
    }
}

@Composable
private fun AigcLibraryAssetCard(
    asset: PresetAsset,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .spatialHoverEffect()
            .clickable { onPick() }
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = asset.modelExtensionLabel(), fontSize = 12.sp, color = StudioColors.Primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = asset.displayName, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = StitchColors.PanelText)
            Text(text = "点击加入当前场景", fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
    }
}

@Composable
private fun AigcRecordList(
    records: List<AigcGenerationRecord>,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) {
        EmptyAigcLibraryState(
            title = "还没有生成记录",
            message = "开始 AIGC 生成后，这里会实时显示阶段、产物和失败原因。",
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            records.forEach { record ->
                AigcGenerationRecordCard(record)
            }
        }
    }
}

@Composable
private fun AigcGenerationRecordCard(record: AigcGenerationRecord) {
    val statusColor = when (record.status) {
        AigcGenerationRecordStatus.RUNNING -> StudioColors.Primary
        AigcGenerationRecordStatus.SUCCEEDED -> StudioColors.Success
        AigcGenerationRecordStatus.FAILED -> StudioColors.Danger
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StudioColors.SurfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.title.take(28), fontSize = 15.sp, color = StudioColors.TextPrimary)
                Text(text = "${record.type.displayName} · ${formatRecordTime(record.updatedAt)}", fontSize = 10.sp, color = StudioColors.TextSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = record.status.displayName, fontSize = 11.sp, color = statusColor)
            }
        }
        Text(text = "阶段：${record.stage}", fontSize = 12.sp, color = StudioColors.TextPrimary)
        if (record.sourceName.isNotBlank()) {
            Text(text = "来源：${record.sourceName.take(32)}", fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
        if (record.prompt.isNotBlank()) {
            Text(text = "Prompt：${record.prompt.take(58)}", fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
        if (record.inputSources.isNotEmpty()) {
            Text(text = "输入：${record.inputSources.size} 个文件", fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
        record.outputImageSource?.let {
            Text(text = "图片：${it.substringAfterLast('/').take(38)}", fontSize = 11.sp, color = StudioColors.Primary)
        }
        record.outputModelSource?.let {
            Text(text = "3D：${it.substringAfterLast('/').take(38)}", fontSize = 11.sp, color = StudioColors.Primary)
        }
        record.errorMessage?.let {
            Text(text = "错误：${it.take(72)}", fontSize = 11.sp, color = StudioColors.Danger)
        }
    }
}

@Composable
private fun EmptyAigcLibraryState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StudioColors.SurfaceRaised)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 16.sp, color = StudioColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = StudioColors.TextSecondary,
            )
        }
    }
}

private fun PresetAsset.modelExtensionLabel(): String =
    source.substringBefore('?')
        .substringAfterLast('.', "3D")
        .uppercase()
        .take(4)

private fun formatRecordTime(timestamp: Long): String =
    if (timestamp <= 0L) {
        "--:--"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(timestamp))
    }
