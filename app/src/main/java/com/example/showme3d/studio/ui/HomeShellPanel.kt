package com.example.showme3d.studio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.R
import com.example.showme3d.studio.examples.HomeExamplePreset
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.state.SceneEnvironmentMode
import com.example.showme3d.studio.state.ShowSummary
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeUnifiedPanel(
    shows: List<ShowSummary>,
    onCreatePreset: (HomeExamplePreset) -> Unit,
    onNewPresentation: () -> Unit,
    onNewWhiteboard: () -> Unit,
    onOpenShow: (Int) -> Unit,
    onMoreShow: (Int) -> Unit,
    onDismissFloatingMenu: () -> Unit,
    onPageGestureActiveChanged: (Boolean) -> Unit = {},
) {
    val examples = remember {
        listOf(
            HomeExampleScene(
                title = "把好奇号装起来",
                detail = "把 NASA 打印件变成可交互的空间装配课。",
                accent = StudioColors.Primary,
                type = DocumentType.PRESENTATION,
                preset = HomeExamplePreset.CURIOSITY_ASSEMBLY,
                coverRes = R.drawable.cover_curiosity_stitch,
            ),
            HomeExampleScene(
                title = "布置一间手绘小屋",
                detail = "从空房间开始，把家具和细节一步步放进去。",
                accent = StudioColors.Warning,
                type = DocumentType.PRESENTATION,
                preset = HomeExamplePreset.IMMERSIVE_INTERIOR,
                coverRes = R.drawable.cover_immersive_interior_cutout,
            ),
            HomeExampleScene(
                title = "恐龙侦探：化石里藏着什么？",
                detail = "检查牙齿、脚印与羽毛证据，拼出一只会动的霸王龙。",
                accent = Color(0xFFC7863D),
                type = DocumentType.PRESENTATION,
                preset = HomeExamplePreset.DINOSAUR_DETECTIVE,
                coverRes = R.drawable.cover_dinosaur_detective,
            ),
        )
    }
    var libraryOpen by remember { mutableStateOf(false) }

    val panelShape = RoundedCornerShape(40.dp)
    Box(
        modifier = Modifier
            .width(HomePanelWidth)
            .height(570.dp)
            .shadow(18.dp, panelShape)
            .clip(panelShape)
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.HomeSurface)
            .border(4.dp, StitchColors.HomeCream, panelShape)
    ) {
        if (libraryOpen) {
            HomeLibraryPage(
                shows = shows,
                onBack = {
                    onDismissFloatingMenu()
                    libraryOpen = false
                },
                onNewPresentation = {
                    libraryOpen = false
                    onDismissFloatingMenu()
                    onNewPresentation()
                },
                onNewWhiteboard = {
                    libraryOpen = false
                    onDismissFloatingMenu()
                    onNewWhiteboard()
                },
                onOpenShow = {
                    libraryOpen = false
                    onDismissFloatingMenu()
                    onOpenShow(it)
                },
                onMoreShow = onMoreShow,
            )
        } else {
            HomeShowcasePage(
                examples = examples,
                onCreatePreset = onCreatePreset,
                onOpenLibrary = {
                    onDismissFloatingMenu()
                    libraryOpen = true
                },
                onPageGestureActiveChanged = onPageGestureActiveChanged,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .width(48.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(StitchColors.HomeCream),
        )
    }
}

@Composable
private fun HomeShowcasePage(
    examples: List<HomeExampleScene>,
    onCreatePreset: (HomeExamplePreset) -> Unit,
    onOpenLibrary: () -> Unit,
    onPageGestureActiveChanged: (Boolean) -> Unit,
) {
    var selectedIndex by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "ShowMeSpatial",
                fontFamily = StitchNunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = StitchColors.HomeBrown,
            )
            Text(
                text = "展现，不只是平面",
                fontFamily = StitchNunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = StitchColors.HomeBrownLight,
            )
        }
        Spacer(Modifier.height(18.dp))
        HomeShowcaseCarousel(
            examples = examples,
            selectedIndex = selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onSelectedIndexChange = { selectedIndex = it },
            onCreatePreset = onCreatePreset,
            onDragActiveChanged = onPageGestureActiveChanged,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
        ) {
            HomeCarouselDots(
                count = examples.size,
                selectedIndex = selectedIndex,
                accent = examples[selectedIndex].accent,
                modifier = Modifier.align(Alignment.Center),
            )
            HomeLibraryButton(
                onClick = onOpenLibrary,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun HomeShowcaseCarousel(
    examples: List<HomeExampleScene>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelectedIndexChange: (Int) -> Unit,
    onCreatePreset: (HomeExamplePreset) -> Unit,
    onDragActiveChanged: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val settleJob = remember { arrayOfNulls<Job>(1) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .border(4.dp, StitchColors.HomeCream, RoundedCornerShape(32.dp))
            .padding(4.dp)
            .clip(RoundedCornerShape(28.dp))
            .spatialHoverEffect()
    ) {
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val threshold = maxOf(48f, pageWidthPx * 0.14f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedIndex, pageWidthPx, examples.size) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onDragActiveChanged(true)
                            dragTotal = 0f
                            settleJob[0]?.cancel()
                            scope.launch { dragOffset.stop() }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragTotal += dragAmount
                            settleJob[0]?.cancel()
                            settleJob[0] = scope.launch {
                                dragOffset.snapTo(
                                    (dragOffset.value + dragAmount).coerceIn(-pageWidthPx, pageWidthPx)
                                )
                            }
                        },
                        onDragEnd = {
                            val requestedDirection = when {
                                dragTotal <= -threshold -> 1
                                dragTotal >= threshold -> -1
                                else -> 0
                            }
                            val direction = requestedDirection.takeIf {
                                examples.size > 2 || selectedIndex + it in examples.indices
                            } ?: 0
                            settleJob[0]?.cancel()
                            settleJob[0] = scope.launch {
                                if (direction == 0) {
                                    dragOffset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                } else {
                                    dragOffset.animateTo(
                                        if (direction > 0) -pageWidthPx else pageWidthPx,
                                        tween(210),
                                    )
                                    dragOffset.snapTo(0f)
                                    val nextIndex = if (examples.size > 2) {
                                        (selectedIndex + direction + examples.size) % examples.size
                                    } else {
                                        selectedIndex + direction
                                    }
                                    onSelectedIndexChange(nextIndex)
                                }
                                onDragActiveChanged(false)
                            }
                        },
                        onDragCancel = {
                            settleJob[0]?.cancel()
                            settleJob[0] = scope.launch {
                                dragOffset.animateTo(0f, tween(180))
                                onDragActiveChanged(false)
                            }
                        },
                    )
                },
        ) {
            examples.forEachIndexed { pageIndex, example ->
                val forward = (pageIndex - selectedIndex + examples.size) % examples.size
                val relativePage = if (examples.size == 2) {
                    pageIndex - selectedIndex
                } else if (forward > examples.size / 2) {
                    forward - examples.size
                } else {
                    forward
                }
                HomeShowcaseHero(
                    example = example,
                    enabled = pageIndex == selectedIndex,
                    pageNumber = pageIndex + 1,
                    pageCount = examples.size,
                    modifier = Modifier
                        .width(pageWidth)
                        .height(pageHeight)
                        .offset {
                            IntOffset(
                                x = (relativePage * pageWidthPx + dragOffset.value).roundToInt(),
                                y = 0,
                            )
                        },
                    onClick = { onCreatePreset(example.preset) },
                )
            }
        }
    }
}

@Composable
private fun HomeShowcaseHero(
    example: HomeExampleScene,
    enabled: Boolean,
    pageNumber: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(example.accent.copy(alpha = 0.28f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (enabled) onClick()
            },
    ) {
        if (example.coverRes != null) {
            Image(
                painter = painterResource(example.coverRes),
                contentDescription = "${example.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            HomeUpcomingArtwork(example)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, StitchColors.HomeBrown.copy(alpha = 0.88f)),
                    )
                ),
        )
        Text(
            text = pageNumber.toString().padStart(2, '0') + " / " + pageCount.toString().padStart(2, '0'),
            fontFamily = StitchNunito,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = StitchColors.HomeBrown,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(StitchColors.HomeSurface.copy(alpha = 0.92f))
                .border(2.dp, StitchColors.HomeCream, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = example.title,
                    fontFamily = StitchNunito,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 48.sp,
                    color = Color.White,
                )
                Text(
                    text = example.detail,
                    fontFamily = StitchNunito,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.95f),
                )
            }
            Spacer(Modifier.width(20.dp))
            StitchToyTextButton(
                label = if (enabled) "拆开看看" else "即将上线",
                symbol = StitchSymbols.ArrowForward,
                contentDescription = if (enabled) "打开示例" else "即将上线",
                enabled = enabled,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun BoxScope.HomeUpcomingArtwork(example: HomeExampleScene) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        example.accent.copy(alpha = 0.95f),
                        StudioColors.TextPrimary,
                    )
                )
            ),
    )
    Box(
        modifier = Modifier
            .size(250.dp)
            .align(Alignment.Center)
            .clip(RoundedCornerShape(999.dp))
            .background(StudioColors.SurfaceRaised.copy(alpha = 0.08f)),
    )
    Icon(
        painter = painterResource(example.artworkIconRes ?: R.drawable.ic_scene),
        contentDescription = null,
        modifier = Modifier
            .size(118.dp)
            .align(Alignment.Center),
        tint = StudioColors.SurfaceRaised.copy(alpha = 0.74f),
    )
}

@Composable
private fun HomeCarouselDots(
    count: Int,
    selectedIndex: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index == selectedIndex) StitchColors.HomeGreen else StitchColors.HomeCream),
            )
        }
    }
}

@Composable
private fun HomeLibraryButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    StitchToyIconButton(
        symbol = StitchSymbols.Add,
        contentDescription = "新建和文件",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun HomeLibraryPage(
    shows: List<ShowSummary>,
    onBack: () -> Unit,
    onNewPresentation: () -> Unit,
    onNewWhiteboard: () -> Unit,
    onOpenShow: (Int) -> Unit,
    onMoreShow: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeBackButton(onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "我的空间", fontSize = 31.sp, color = StudioColors.TextPrimary)
                Text(text = "开始新的创作，或者继续之前的项目。", fontSize = 16.sp, color = StudioColors.TextSecondary)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier.width(310.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                HomeSectionLabel("新建")
                CreationModeCard(
                    title = "分页",
                    subtitle = "适合多页展示",
                    iconRes = R.drawable.ic_scene,
                    accent = StudioColors.Primary,
                    onClick = onNewPresentation,
                )
                CreationModeCard(
                    title = "白板",
                    subtitle = "适合随意创作",
                    iconRes = R.drawable.ic_text,
                    accent = StudioColors.Warning,
                    onClick = onNewWhiteboard,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                HomeSectionLabel("最近文件")
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (shows.isEmpty()) {
                        HomeEmptyFilesState()
                    } else {
                        shows.forEach { show ->
                            RecentProjectRow(
                                show = show,
                                onOpenShow = onOpenShow,
                                onMoreShow = onMoreShow,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .spatialHoverEffect()
            .clickable { onClick() }
            .background(StudioColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_previous),
            contentDescription = "返回示例",
            modifier = Modifier.size(23.dp),
            tint = StudioColors.TextPrimary,
        )
    }
}

private val HomePanelWidth = 980.dp

@Composable
fun HomeEnvironmentTogglePanel(
    sceneEnvironmentMode: SceneEnvironmentMode,
    loading: Boolean,
    gazeArmed: Boolean = false,
    onToggleSceneEnvironment: () -> Unit,
) {
    val virtual = sceneEnvironmentMode == SceneEnvironmentMode.VIRTUAL
    val label = when {
        loading -> "切换中..."
        virtual -> "切回实景"
        else -> "切到虚拟"
    }
    val accent = if (virtual || loading) StudioColors.Primary else StudioColors.TextPrimary
    Row(
        modifier = Modifier
            .width(172.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(999.dp))
            .spatialHoverEffect()
            .clickable {
                if (!loading) onToggleSceneEnvironment()
            }
            // The Spatial material modifier renders a rectangular backing surface, whose
            // corners can protrude beyond this strongly rounded pill on device.
            .background(if (virtual || loading || gazeArmed) StitchColors.HomeGreen.copy(alpha = 0.28f) else StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, if (virtual || loading || gazeArmed) StitchColors.HomeGreen else StitchColors.PanelOutline, RoundedCornerShape(999.dp))
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            HomeLoadingIndicator(label)
        } else {
            StitchSymbol(StitchSymbols.Landscape, label, size = 24.dp, color = accent)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = StitchNunito,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = accent,
        )
    }
}

@Composable
private fun HomeLoadingIndicator(contentDescription: String) {
    val transition = rememberInfiniteTransition(label = "${contentDescription}_loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "${contentDescription}_rotation",
    )
    Canvas(
        modifier = Modifier
            .size(23.dp)
            .rotate(rotation),
    ) {
        drawArc(
            color = StudioColors.Primary,
            startAngle = 0f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun HomeSectionLabel(label: String) {
    Text(text = label, fontSize = 21.sp, color = StudioColors.TextPrimary)
}

private data class HomeExampleScene(
    val title: String,
    val detail: String,
    val accent: Color,
    val type: DocumentType,
    val preset: HomeExamplePreset,
    val coverRes: Int? = null,
    val artworkIconRes: Int? = null,
)

@Composable
private fun CreationModeCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(24.dp))
            .spatialHoverEffect()
            .clickable { onClick() }
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StudioColors.SurfaceRaised.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = title,
                modifier = Modifier.size(23.dp),
                tint = accent,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, fontSize = 19.sp, color = StudioColors.TextPrimary)
            Text(text = subtitle, fontSize = 13.sp, color = StudioColors.TextSecondary)
        }
    }
}

@Composable
private fun RecentProjectRow(
    show: ShowSummary,
    onOpenShow: (Int) -> Unit,
    onMoreShow: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (show.selected) {
                    StudioColors.PrimarySoft
                } else {
                    StudioColors.Surface
                }
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .spatialHoverEffect()
                .clickable { onOpenShow(show.index) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SimplePreview(
                type = show.documentType,
                accent = if (show.documentType == DocumentType.WHITEBOARD) StudioColors.Warning else StudioColors.Primary,
                compact = true,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = show.name, fontSize = 20.sp, color = StudioColors.TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = show.summaryText(separator = " · "),
                    fontSize = 14.sp,
                    color = StudioColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .spatialHoverEffect()
                .clickable { onMoreShow(show.index) }
                .background(StudioColors.SurfaceRaised.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_horizontal),
                contentDescription = "更多操作",
                modifier = Modifier.size(28.dp),
                tint = StudioColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun HomeEmptyFilesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(StudioColors.Surface)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "还没有文件", fontSize = 25.sp, color = StudioColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(text = "选一个模式开始你的第一个空间。", fontSize = 16.sp, color = StudioColors.TextSecondary)
    }
}

@Composable
fun HomeFileActionMenuPanel(
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(248.dp)
            .height(188.dp)
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(210.dp)
                .clip(RoundedCornerShape(26.dp))
                .backgroundMaterial(true, Material.Regular)
                .background(StitchColors.PanelSurface.copy(alpha = 0.98f))
                .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(26.dp))
                .clickable { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeMenuRow(R.drawable.ic_copy, "复制", StudioColors.TextPrimary, onDuplicate)
            HomeMenuRow(R.drawable.ic_delete, "删除", StudioColors.Danger, onDelete)
        }
    }
}

@Composable
fun HomeDeleteConfirmPanel(
    showName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(32.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.98f))
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(32.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "删除？", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = StitchColors.PanelText)
        Text(
            text = "${showName.take(22)} 将被移除，这个操作不能撤销。",
            fontSize = 17.sp,
            color = StudioColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeDialogButton(
                label = "取消",
                color = StudioColors.TextPrimary,
                background = StudioColors.SurfaceMuted,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            HomeDialogButton(
                label = "确认删除",
                color = StudioColors.SurfaceRaised,
                background = StudioColors.Danger,
                modifier = Modifier.weight(1f),
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun HomeMenuRow(
    iconRes: Int,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .spatialHoverEffect()
            .clickable { onClick() }
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stitchSymbolForDrawable(iconRes)?.let { StitchSymbol(it, label, size = 21.dp, color = color) }
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
    }
}

@Composable
private fun HomeDialogButton(
    label: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .spatialHoverEffect()
            .clickable { onClick() }
            .background(background)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
    }
}

@Composable
private fun SimplePreview(
    type: DocumentType,
    accent: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val width = if (compact) 116.dp else 292.dp
    val height = if (compact) 78.dp else 188.dp
    val previewModifier = if (compact) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = previewModifier
            .height(height)
            .clip(RoundedCornerShape(if (compact) 20.dp else 24.dp))
            .background(StudioColors.SurfaceSubtle)
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(if (compact) 15.dp else 18.dp))
                .background(StudioColors.SurfaceRaised.copy(alpha = 0.92f)),
        )
        Box(
            modifier = Modifier
                .width(if (compact) 88.dp else 210.dp)
                .height(if (compact) 1.dp else 2.dp)
                .align(Alignment.Center)
                .background(StudioColors.SurfaceDisabled),
        )
        if (type == DocumentType.WHITEBOARD) {
            WhiteboardPreviewMarks(accent = accent, compact = compact)
        } else {
            PresentationPreviewObjects(accent = accent, compact = compact)
        }
        Text(
            text = type.shortLabel(),
            fontSize = if (compact) 14.sp else 17.sp,
            color = accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(999.dp))
                .background(StudioColors.SurfaceRaised.copy(alpha = 0.88f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BoxScope.PresentationPreviewObjects(accent: Color, compact: Boolean) {
    Box(
        modifier = Modifier
            .width(if (compact) 64.dp else 144.dp)
            .height(if (compact) 22.dp else 42.dp)
            .align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.10f)),
    )
    Box(
        modifier = Modifier
            .width(if (compact) 26.dp else 54.dp)
            .height(if (compact) 26.dp else 54.dp)
            .align(Alignment.Center)
            .clip(RoundedCornerShape(if (compact) 10.dp else 16.dp))
            .background(accent.copy(alpha = 0.86f)),
    )
    Box(
        modifier = Modifier
            .width(if (compact) 22.dp else 46.dp)
            .height(if (compact) 36.dp else 70.dp)
            .align(Alignment.CenterEnd)
            .clip(RoundedCornerShape(999.dp))
            .background(StudioColors.TextPrimary.copy(alpha = 0.16f)),
    )
    Box(
        modifier = Modifier
            .width(if (compact) 18.dp else 38.dp)
            .height(if (compact) 18.dp else 38.dp)
            .align(Alignment.CenterStart)
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.24f)),
    )
}

@Composable
private fun BoxScope.WhiteboardPreviewMarks(accent: Color, compact: Boolean) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .width(if (compact) 72.dp else 176.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 11.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 1) 0.78f else if (index == 2) 0.56f else 1f)
                    .height(if (compact) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index == 0) accent.copy(alpha = 0.45f) else StudioColors.TextSecondary.copy(alpha = 0.34f)),
            )
        }
    }
    Box(
        modifier = Modifier
            .width(if (compact) 18.dp else 38.dp)
            .height(if (compact) 18.dp else 38.dp)
            .align(Alignment.BottomEnd)
            .clip(RoundedCornerShape(if (compact) 7.dp else 11.dp))
            .background(accent.copy(alpha = 0.18f)),
    )
}

private fun DocumentType.displayName(): String =
    when (this) {
        DocumentType.PRESENTATION -> "演示"
        DocumentType.WHITEBOARD -> "白板"
    }

private fun DocumentType.shortLabel(): String =
    when (this) {
        DocumentType.PRESENTATION -> "演示"
        DocumentType.WHITEBOARD -> "白板"
    }
