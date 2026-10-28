package com.example.showme3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

internal object StudioColors {
    // Fixed art-direction colors approved in the Animal Crossing style pass.
    // They intentionally stay warm and low-contrast instead of inheriting the
    // previous cool blue-gray palette.
    val Surface = Color(0xFFFFF8F0)
    val SurfaceRaised = Color(0xFFFFFCF7)
    val SurfaceMuted = Color(0xFFF1ECDD)
    val SurfaceSubtle = Color(0xFFF8F1E7)
    val SurfaceDisabled = Color(0xFFD8D1C4)
    val Outline = Color(0xFFE8D9C9)
    val OutlineStrong = Color(0xFFD7C7B7)

    val Primary = Color(0xFF79CFA5)
    val PrimaryStrong = Color(0xFF55B98A)
    val PrimarySoft = Color(0xFFD9F2E3)
    val PrimaryLabel = Color(0xFF244C3A)

    val TextPrimary = Color(0xFF41362F)
    val TextSecondary = Color(0xFF776D65)
    val TextTertiary = Color(0xFF9B9188)
    val OnPrimary = Color(0xFFFFFFFF)

    val Danger = Color(0xFFE66E57)
    val DangerStrong = Color(0xFFC95040)
    val DangerSoft = Color(0xFFFFD9D1)
    val Success = Color(0xFF5C9870)
    val SuccessSoft = Color(0xFFDDEEDC)
    val Warning = Color(0xFFB07D43)
    val WarningSoft = Color(0xFFF7E5C8)
}

internal object StudioRadii {
    val PanelLarge = 32.dp
    val Panel = 26.dp
    val Section = 20.dp
    val Button = 18.dp
    val Control = 14.dp
    val Compact = 13.dp
    val Pill = 999.dp
}

internal object StudioSpacing {
    val XSmall = 4.dp
    val Small = 6.dp
    val Medium = 8.dp
    val Regular = 10.dp
    val Large = 12.dp
    val XLarge = 16.dp
    val Panel = 20.dp
    val Dialog = 24.dp
}

internal enum class StudioButtonStyle {
    Primary,
    Secondary,
    Danger,
}

@Composable
internal fun StudioSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StudioRadii.Section))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Section))
            .padding(StudioSpacing.Regular),
        content = content,
    )
}

@Composable
internal fun StudioTextButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    style: StudioButtonStyle = StudioButtonStyle.Secondary,
    height: Dp = 40.dp,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> StudioColors.SurfaceDisabled
        style == StudioButtonStyle.Primary -> StudioColors.Primary
        style == StudioButtonStyle.Danger -> StudioColors.DangerSoft
        else -> StudioColors.SurfaceMuted
    }
    val foreground = when {
        !enabled -> StudioColors.TextSecondary
        style == StudioButtonStyle.Primary -> StudioColors.OnPrimary
        style == StudioButtonStyle.Danger -> StudioColors.DangerStrong
        else -> StudioColors.TextPrimary
    }
    Box(
        modifier = modifier
            .height(height)
            .shadow(if (style == StudioButtonStyle.Primary && enabled) 4.dp else 1.dp, RoundedCornerShape(StudioRadii.Button))
            .clip(RoundedCornerShape(StudioRadii.Button))
            .stitchClickable(enabled = enabled, onClick = onClick)
            .background(background)
            .border(
                1.dp,
                when (style) {
                    StudioButtonStyle.Primary -> StitchColors.HomeGreenDark.copy(alpha = 0.65f)
                    StudioButtonStyle.Danger -> StitchColors.CoralDepth.copy(alpha = 0.55f)
                    StudioButtonStyle.Secondary -> StitchColors.PanelOutline
                },
                RoundedCornerShape(StudioRadii.Button),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = foreground)
    }
}

@Composable
internal fun StudioChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> StudioColors.SurfaceDisabled
        selected -> StudioColors.PrimarySoft
        else -> StudioColors.SurfaceMuted
    }
    val foreground = when {
        !enabled -> StudioColors.TextSecondary
        selected -> StudioColors.PrimaryLabel
        else -> StudioColors.TextPrimary
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(StudioRadii.Control))
            .stitchClickable(enabled = enabled, onClick = onClick)
            .background(background)
            .border(if (selected) 2.dp else 1.dp, if (selected) StitchColors.HomeGreen else StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Control))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = foreground)
    }
}

@Composable
internal fun StudioInputField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    height: Dp = if (singleLine) 42.dp else 68.dp,
    textSize: Int = 14,
    onChange: (String) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(StudioRadii.Control))
            .background(if (enabled) StudioColors.SurfaceMuted else StudioColors.SurfaceDisabled)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Control))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, fontSize = (textSize - 1).sp, color = StudioColors.TextTertiary)
        }
        BasicTextField(
            value = value,
            onValueChange = { if (enabled) onChange(it) },
            enabled = enabled,
            singleLine = singleLine,
            textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = textSize.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
