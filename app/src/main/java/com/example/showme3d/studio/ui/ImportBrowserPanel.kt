package com.example.showme3d.studio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.R
import com.example.showme3d.studio.imports.ImportCandidate
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import java.util.Locale

@Composable
fun ImportBrowserPanel(
    title: String,
    currentPath: String?,
    canGoUp: Boolean,
    files: List<ImportCandidate>,
    message: String,
    onOpenDirectory: (ImportCandidate) -> Unit,
    onSelect: (ImportCandidate) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(560.dp)
            .height(430.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(StitchColors.PanelSurface)
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = StitchColors.PanelText)
            }
            ActionIconButton(R.drawable.ic_previous, "返回上级", canGoUp, onUp)
            Spacer(Modifier.width(8.dp))
            ActionIconButton(R.drawable.ic_refresh, "刷新", true, onRefresh)
            Spacer(Modifier.width(8.dp))
            ActionIconButton(R.drawable.ic_close, "关闭导入", true, onClose)
        }
        Spacer(Modifier.height(14.dp))
        if (currentPath != null || message.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(StitchColors.PanelSurfaceMuted)
                    .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (currentPath != null) {
                    Text(text = currentPath, fontSize = 12.sp, color = StudioColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                }
                Text(text = message, fontSize = 13.sp, color = StudioColors.TextPrimary)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (files.isEmpty()) {
            Text(
                text = "没有找到可导入文件。可把文件放到 ShowMe3DInbox 后刷新。",
                fontSize = 15.sp,
                color = StudioColors.TextPrimary,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                files.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .spatialHoverEffect()
                            .clickable {
                                if (file.isDirectory) {
                                    onOpenDirectory(file)
                                } else if (file.importable) {
                                    onSelect(file)
                                }
                            }
                            .background(StitchColors.PanelSurfaceMuted)
                            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StitchSymbol(
                            symbol = stitchSymbolForDrawable(file.iconRes()) ?: StitchSymbols.Folder,
                            contentDescription = file.iconLabel(),
                            size = 25.dp,
                            color = StitchColors.HomeGreenDark,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.displayName.take(48),
                                fontFamily = StitchNunito,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = StitchColors.PanelText,
                            )
                            Text(
                                text = "${file.directoryLabel} | ${file.sizeLabel}",
                                fontSize = 12.sp,
                                color = StudioColors.TextSecondary,
                            )
                        }
                        StitchSymbol(StitchSymbols.ChevronRight, if (file.isDirectory) "打开" else "导入", size = 22.dp, color = StitchColors.PanelText)
                    }
                }
            }
        }
    }
}

private fun ImportCandidate.iconRes(): Int {
    if (isDirectory) return R.drawable.ic_folder
    return when (displayName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "png", "jpg", "jpeg", "webp" -> R.drawable.ic_image
        "usd", "usda", "usdc", "usdz", "glb", "gltf" -> R.drawable.ic_model_cube
        else -> R.drawable.ic_file
    }
}

private fun ImportCandidate.iconLabel(): String {
    if (isDirectory) return "DIR"
    return when (displayName.substringAfterLast('.', "").lowercase(Locale.US)) {
        "png", "jpg", "jpeg", "webp" -> "IMG"
        "usd", "usda", "usdc", "usdz" -> "USD"
        "glb", "gltf" -> "3D"
        else -> "FILE"
    }
}
