package com.example.showme3d.studio.ui

import com.example.showme3d.release.ReleasePolicy
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

@Composable
fun AigcKeyConfigPanel(
    rodinApiKey: String,
    imageToImageApiKey: String,
    doubaoApiKey: String,
    modifier: Modifier = Modifier,
    title: String = "AI Key 配置",
    message: String = "Key 仅保存在本机，用于端上直连对应生成服务。",
    embedded: Boolean = false,
    requireRodinKey: Boolean = false,
    requireImageToImageKey: Boolean = false,
    requireDoubaoKey: Boolean = false,
    onSave: (rodinApiKey: String, imageToImageApiKey: String, doubaoApiKey: String) -> Unit,
    onCancel: () -> Unit,
) {
    if (!ReleasePolicy.CLOUD_AI_ENABLED) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(StitchColors.PanelSurface)
                .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "即将开放", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = StitchColors.PanelText)
            Text(text = ReleasePolicy.COMING_SOON, fontSize = 16.sp, color = StudioColors.TextSecondary)
            AigcKeyConfigButton(
                label = "知道了",
                color = StudioColors.SurfaceRaised,
                background = StudioColors.Primary,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel,
            )
        }
        return
    }
    var rodinValue by remember(rodinApiKey) { mutableStateOf(rodinApiKey) }
    var imageToImageValue by remember(imageToImageApiKey) { mutableStateOf(imageToImageApiKey) }
    var doubaoValue by remember(doubaoApiKey) { mutableStateOf(doubaoApiKey) }
    val canSave = (!requireRodinKey || rodinValue.trim().isNotBlank()) &&
        (!requireImageToImageKey || imageToImageValue.trim().isNotBlank()) &&
        (!requireDoubaoKey || doubaoValue.trim().isNotBlank())

    val panelModifier = if (embedded) {
        modifier.padding(top = 4.dp)
    } else {
        modifier
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.98f))
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
            .padding(20.dp)
    }

    Column(
        modifier = panelModifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!embedded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StitchSymbol(StitchSymbols.Settings, "设置", size = 27.dp, color = StitchColors.HomeGreenDark)
                Spacer(Modifier.width(9.dp))
                Text(text = title, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = StitchColors.PanelText)
            }
        }
        Text(text = message, fontSize = 13.sp, color = StudioColors.TextSecondary)
        AigcKeyInputField(
            label = "Rodin API Key",
            value = rodinValue,
            placeholder = "用于文生 / 图生 3D",
            required = requireRodinKey,
            onChange = { rodinValue = it },
        )
        AigcKeyInputField(
            label = "Seedream 图生图 Key",
            value = imageToImageValue,
            placeholder = "用于文生图 / 图生图 / AI 清理",
            required = requireImageToImageKey,
            onChange = { imageToImageValue = it },
        )
        AigcKeyInputField(
            label = "豆包 Key",
            value = doubaoValue,
            placeholder = "用于 AI 助手自然语言编辑",
            required = requireDoubaoKey,
            onChange = { doubaoValue = it },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AigcKeyConfigButton(
                label = "取消",
                color = StudioColors.TextPrimary,
                background = StudioColors.SurfaceMuted,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            AigcKeyConfigButton(
                label = "保存 Key",
                color = StudioColors.SurfaceRaised,
                background = StudioColors.Primary,
                enabled = canSave,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSave(
                        rodinValue.trim(),
                        imageToImageValue.trim(),
                        doubaoValue.trim(),
                    )
                },
            )
        }
        if (requireRodinKey && rodinValue.trim().isBlank()) {
            Text(text = "当前生成需要先配置 Rodin API Key。", fontSize = 12.sp, color = StudioColors.Danger)
        }
        if (requireImageToImageKey && imageToImageValue.trim().isBlank()) {
            Text(text = "当前生成需要先配置 Seedream 图生图 Key。", fontSize = 12.sp, color = StudioColors.Danger)
        }
        if (requireDoubaoKey && doubaoValue.trim().isBlank()) {
            Text(text = "AI 助手需要先配置豆包 Key。", fontSize = 12.sp, color = StudioColors.Danger)
        }
    }
}

@Composable
private fun AigcKeyInputField(
    label: String,
    value: String,
    placeholder: String,
    required: Boolean,
    onChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 13.sp, color = StudioColors.TextPrimary)
            if (required) {
                Spacer(Modifier.width(6.dp))
                Text(text = "必填", fontSize = 11.sp, color = StudioColors.Danger)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StitchColors.PanelSurfaceMuted)
                .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, fontSize = 13.sp, color = StudioColors.TextTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 14.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AigcKeyConfigButton(
    label: String,
    color: Color,
    background: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (enabled) Modifier.spatialHoverEffect().clickable { onClick() } else Modifier)
            .background(background)
            .alpha(if (enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
    }
}
