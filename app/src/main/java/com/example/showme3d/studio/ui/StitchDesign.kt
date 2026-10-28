package com.example.showme3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.R
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

internal object StitchColors {
    val HomeSurface = Color(0xFFFAF8EF)
    val HomeCream = Color(0xFFEAE3D2)
    val HomeGreen = Color(0xFF7BB494)
    val HomeGreenDark = Color(0xFF5A8F73)
    val HomeGreenLight = Color(0xFF94C1A5)
    val HomeBrown = Color(0xFF8B735B)
    val HomeBrownLight = Color(0xFFA38E78)

    val PresentSurface = Color(0xFFFFF8F6)
    val PresentSurfacePressed = Color(0xFFFFF1ED)
    val PresentText = Color(0xFF2C160E)
    val PresentIcon = Color(0xFF5F5F57)
    val PresentDepth = Color(0xFFE7E3D1)
    val PresentDivider = Color(0xFFC8C7BD)

    val PanelSurface = Color(0xFFFFFBF4)
    val PanelSurfaceMuted = Color(0xFFF3ECDF)
    val PanelOutline = Color(0xFFE4D6C6)
    val PanelText = Color(0xFF493B32)
    val PanelTextMuted = Color(0xFF7C7067)
    val Coral = Color(0xFFEA806D)
    val CoralDepth = Color(0xFFC75D4F)
}

internal val StitchNunito = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)
internal val StitchNunitoSans = FontFamily(
    Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_sans_extrabold, FontWeight.ExtraBold),
)
internal val StitchQuicksand = FontFamily(
    Font(R.font.quicksand_semibold, FontWeight.SemiBold),
    Font(R.font.quicksand_bold, FontWeight.Bold),
)
private val StitchMaterialSymbols = FontFamily(Font(R.font.material_symbols_outlined))

internal object StitchSymbols {
    const val Add = "\uE145"
    const val AddCircle = "\uE990"
    const val AccountTree = "\uE97A"
    const val Animation = "\uE71C"
    const val Apps = "\uE5C3"
    const val ArrowBack = "\uE5C4"
    const val ArrowBackIosNew = "\uE2EA"
    const val ArrowForward = "\uE5C8"
    const val ArrowForwardIos = "\uE5E1"
    const val ArrowUp = "\uE5D8"
    const val Assignment = "\uE85D"
    const val AutoAwesome = "\uE65F"
    const val AutoFixHigh = "\uE663"
    const val Autorenew = "\uE863"
    const val Brush = "\uE3AE"
    const val Build = "\uF8CD"
    const val Category = "\uE72C"
    const val ChatBubble = "\uE0CB"
    const val CheckCircle = "\uF0BE"
    const val ChevronLeft = "\uE5CB"
    const val ChevronRight = "\uE5CC"
    const val Close = "\uE5CD"
    const val ContentCopy = "\uE14D"
    const val Dashboard = "\uE871"
    const val Delete = "\uE92E"
    const val DeleteOutline = "\uE92E"
    const val DeployedCode = "\uF720"
    const val Draw = "\uE746"
    const val Edit = "\uF097"
    const val ExpandMore = "\uE5CF"
    const val Face = "\uF008"
    const val FileCopy = "\uE173"
    const val Folder = "\uE2C7"
    const val FolderOpen = "\uE2C8"
    const val Gesture = "\uE155"
    const val GridView = "\uE9B0"
    const val Home = "\uE9B2"
    const val Image = "\uE3F4"
    const val Inventory = "\uE1A1"
    const val KeyboardArrowUp = "\uE316"
    const val Landscape = "\uE564"
    const val LinearScale = "\uE260"
    const val Lock = "\uE899"
    const val LockOpen = "\uE898"
    const val Logout = "\uE9BA"
    const val Magic = "\uF136"
    const val MoreHoriz = "\uE5D3"
    const val Pause = "\uE034"
    const val Person = "\uF0D3"
    const val PhotoCamera = "\uE412"
    const val PlayArrow = "\uE037"
    const val Polyline = "\uEBBB"
    const val PresentToAll = "\uE0DF"
    const val Redo = "\uE15A"
    const val Refresh = "\uE5D5"
    const val RotateLeft = "\uE419"
    const val RotateRight = "\uE41A"
    const val Scene = "\uE2A7"
    const val Send = "\uE163"
    const val Settings = "\uE8B8"
    const val SkipPrevious = "\uE045"
    const val Slideshow = "\uE41B"
    const val SmartToy = "\uF06C"
    const val Sync = "\uE627"
    const val Title = "\uE264"
    const val Tools = "\uF8CD"
    const val Toys = "\uE332"
    const val Undo = "\uE166"
    const val ViewInAr = "\uEFC9"
    const val Visibility = "\uE8F4"
    const val Wallpaper = "\uE1BC"
}

internal fun stitchSymbolForDrawable(iconRes: Int): String? = when (iconRes) {
    R.drawable.ic_add -> StitchSymbols.Add
    R.drawable.ic_ai_3d_model -> StitchSymbols.ViewInAr
    R.drawable.ic_ai_assistant -> StitchSymbols.SmartToy
    R.drawable.ic_ai_image -> StitchSymbols.AutoAwesome
    R.drawable.ic_animation -> StitchSymbols.Animation
    R.drawable.ic_arrow_right -> StitchSymbols.ArrowForward
    R.drawable.ic_arrow_up -> StitchSymbols.ArrowUp
    R.drawable.ic_branch -> StitchSymbols.Polyline
    R.drawable.ic_brush -> StitchSymbols.Brush
    R.drawable.ic_chevron_down -> StitchSymbols.ExpandMore
    R.drawable.ic_close -> StitchSymbols.Close
    R.drawable.ic_comment -> StitchSymbols.ChatBubble
    R.drawable.ic_copy -> StitchSymbols.ContentCopy
    R.drawable.ic_delete -> StitchSymbols.DeleteOutline
    R.drawable.ic_exit -> StitchSymbols.Home
    R.drawable.ic_file -> StitchSymbols.FolderOpen
    R.drawable.ic_folder -> StitchSymbols.Folder
    R.drawable.ic_image -> StitchSymbols.Image
    R.drawable.ic_import_resource -> StitchSymbols.Inventory
    R.drawable.ic_lock -> StitchSymbols.Lock
    R.drawable.ic_magic_wand -> StitchSymbols.AutoFixHigh
    R.drawable.ic_model_cube -> StitchSymbols.DeployedCode
    R.drawable.ic_more_horizontal -> StitchSymbols.MoreHoriz
    R.drawable.ic_pause -> StitchSymbols.Pause
    R.drawable.ic_play -> StitchSymbols.PlayArrow
    R.drawable.ic_previous -> StitchSymbols.SkipPrevious
    R.drawable.ic_redo -> StitchSymbols.Redo
    R.drawable.ic_refresh -> StitchSymbols.Refresh
    R.drawable.ic_rotate_left -> StitchSymbols.RotateLeft
    R.drawable.ic_rotate_right -> StitchSymbols.RotateRight
    R.drawable.ic_scene -> StitchSymbols.Landscape
    R.drawable.ic_subject_avatar -> StitchSymbols.Person
    R.drawable.ic_text -> StitchSymbols.Title
    R.drawable.ic_tools -> StitchSymbols.Build
    R.drawable.ic_undo -> StitchSymbols.Undo
    R.drawable.ic_unlock -> StitchSymbols.LockOpen
    R.drawable.ic_viewpoint -> StitchSymbols.Visibility
    R.drawable.ic_workflow -> StitchSymbols.AccountTree
    else -> null
}

@Composable
internal fun Modifier.stitchClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    hoverEnabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    val rememberedInteractionSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: rememberedInteractionSource
    val hoverModifier = if (hoverEnabled) this.spatialHoverEffect() else this
    return hoverModifier
        .clickable(
            interactionSource = source,
            indication = null,
            onClick = onClick,
        )
}

@Composable
internal fun StitchSymbol(
    symbol: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = StitchColors.PresentIcon,
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontFamily = StitchMaterialSymbols,
            fontWeight = FontWeight.Normal,
            fontSize = size.value.sp,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
internal fun StitchToyTextButton(
    label: String,
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 154.dp,
    height: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth = 6.dp
    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .width(width)
            .height(height + depth)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .align(Alignment.TopCenter)
                .offset(y = depth)
                .clip(shape)
                .background(StitchColors.HomeGreenDark),
        )
        Row(
            modifier = Modifier
                .width(width)
                .height(height)
                .align(Alignment.TopCenter)
                .offset(y = if (pressed && enabled) depth else 0.dp)
                .clip(shape)
                .then(
                    if (enabled) {
                        Modifier
                            .spatialHoverEffect()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    }
                )
                .background(if (pressed) StitchColors.HomeGreenLight else StitchColors.HomeGreen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(25.dp))
            Text(
                text = label,
                fontFamily = StitchNunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            StitchSymbol(
                symbol = symbol,
                contentDescription = contentDescription,
                size = 20.dp,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun StitchToyIconButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val depth = 5.dp
    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .size(width = size, height = size + depth),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.TopCenter)
                .offset(y = depth)
                .clip(shape)
                .background(StitchColors.HomeGreenDark),
        )
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.TopCenter)
                .offset(y = if (pressed) depth else 0.dp)
                .clip(shape)
                .spatialHoverEffect()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .background(if (pressed) StitchColors.HomeGreenLight else StitchColors.HomeGreen),
            contentAlignment = Alignment.Center,
        ) {
            StitchSymbol(
                symbol = symbol,
                contentDescription = contentDescription,
                size = 26.dp,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun StitchSoftIconButton(
    symbol: String,
    contentDescription: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    val frontColor = when {
        active -> StitchColors.HomeGreen.copy(alpha = 0.28f)
        pressed -> StitchColors.PresentSurfacePressed
        else -> StitchColors.PresentSurface
    }
    val iconColor = if (active) StitchColors.HomeGreenDark else StitchColors.PresentIcon

    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(StitchColors.PresentDepth),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(46.dp)
                .align(Alignment.TopCenter)
                .offset(y = if (pressed && enabled) 2.dp else 0.dp)
                .clip(shape)
                .then(
                    if (enabled) {
                        Modifier
                            .spatialHoverEffect()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                            )
                    } else {
                        Modifier
                    }
                )
                .background(frontColor),
            contentAlignment = Alignment.Center,
        ) {
            StitchSymbol(
                symbol = symbol,
                contentDescription = contentDescription,
                size = 24.dp,
                color = iconColor,
            )
        }
    }
}
