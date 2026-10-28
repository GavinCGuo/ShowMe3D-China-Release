package com.example.showme3d.studio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.R
import com.example.showme3d.library.PresetAsset
import com.example.showme3d.library.PresetCategory
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.aigc.AigcOutputMode
import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.example.showme3d.studio.aigc.DEFAULT_RODIN_FACE_BUDGET
import com.example.showme3d.studio.aigc.DEFAULT_RODIN_GENERATION_TIER
import com.example.showme3d.studio.aigc.RodinGenerationTier
import com.example.showme3d.studio.imports.UserSkyboxAsset
import com.example.showme3d.studio.model.PageSkybox
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

internal object AssetPanelMetrics {
    const val PANEL_WIDTH_DP = 560
    const val PANEL_HEIGHT_DP = 600
    const val PANEL_PADDING_DP = 22
    const val GRID_GAP_DP = 12
    const val CARD_HEIGHT_DP = 186
    const val CARD_PREVIEW_HEIGHT_DP = 126
    const val CARD_PADDING_DP = 12
    const val CARD_PREVIEW_INSET_DP = 22
    const val CARD_COLUMNS = 3
}

data class AssetPreviewSlot(
    val centerOffsetXMeters: Float,
    val centerOffsetYMeters: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val contentWidthMeters: Float,
    val contentHeightMeters: Float,
    val visible: Boolean,
)

@Composable
fun AssetPanel(
    presets: List<PresetAsset> = PresetLibrary.assets,
    selectedCategory: PresetCategory,
    scrollState: ScrollState,
    onSelectCategory: (PresetCategory) -> Unit,
    onPick: (PresetAsset) -> Unit,
    onImportAsset: () -> Unit,
    onOpenSkyboxes: () -> Unit,
    onClose: () -> Unit,
    rodinKeyConfigured: Boolean = false,
    imageToImageKeyConfigured: Boolean = false,
    aigcOutputMode: AigcOutputMode = AigcOutputMode.MODEL,
    aigcPrompt: String = "",
    aigcPromptTemplate: AigcPromptTemplate? = null,
    aigcUseImageReference: Boolean = false,
    aigcReferenceImageName: String? = null,
    aigcImagePreprocessMode: AigcImagePreprocessMode = AigcImagePreprocessMode.NONE,
    aigcTier: RodinGenerationTier = DEFAULT_RODIN_GENERATION_TIER,
    aigcQualityOverride: Int? = DEFAULT_RODIN_FACE_BUDGET,
    aigcMeshMode: String = "Raw",
    aigcHdTexture: Boolean = false,
    aigcCreative: Boolean = false,
    aigcBusy: Boolean = false,
    aigcMessage: String = "填写提示词后点击发送生成请求，生成 GLB 后会进入本分类。",
    onOpenAigcKeyConfig: () -> Unit = {},
    onAigcOutputModeChange: (AigcOutputMode) -> Unit = {},
    onAigcPromptChange: (String) -> Unit = {},
    onAigcPromptTemplateChange: (AigcPromptTemplate?) -> Unit = {},
    onAigcUseImageReferenceChange: (Boolean) -> Unit = {},
    onAigcImagePreprocessModeChange: (AigcImagePreprocessMode) -> Unit = {},
    onPickAigcReferenceImage: () -> Unit = {},
    onClearAigcReferenceImage: () -> Unit = {},
    onAigcTierChange: (RodinGenerationTier) -> Unit = {},
    onAigcMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit = { _, _ -> },
    onAigcHdTextureChange: (Boolean) -> Unit = {},
    onAigcCreativeChange: (Boolean) -> Unit = {},
    onGenerateAigc: () -> Unit = {},
    onPreviewSlotChanged: (String, AssetPreviewSlot) -> Unit = { _, _ -> },
) {
    val selectedAssets = presets.filter { it.category == selectedCategory }
    var panelBounds by remember { mutableStateOf<Rect?>(null) }
    var gridBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier = Modifier
            .width(AssetPanelMetrics.PANEL_WIDTH_DP.dp)
            .height(AssetPanelMetrics.PANEL_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(StitchColors.PanelSurface)
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(28.dp))
            .onGloballyPositioned { panelBounds = it.boundsInRoot() }
            .padding(AssetPanelMetrics.PANEL_PADDING_DP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "资源中心", fontFamily = StitchNunito, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 24.sp, color = StitchColors.PanelText)
                Text(text = "预置模型 / AIGC-2/3D / 天空盒", fontSize = 13.sp, color = StudioColors.TextSecondary)
            }
            ActionIconButton(R.drawable.ic_close, "关闭资源中心", true, onClose)
        }
        Spacer(Modifier.height(12.dp))
        ResourcePanelTabs(
            selectedCategory = selectedCategory,
            skyboxSelected = false,
            onPresets = { onSelectCategory(PresetCategory.PRESET_MODEL) },
            onAigc = { onSelectCategory(PresetCategory.AIGC) },
            onSkyboxes = onOpenSkyboxes,
        )
        if (selectedCategory != PresetCategory.AIGC) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .spatialHoverEffect()
                    .clickable { onImportAsset() }
                    .background(StitchColors.HomeGreen.copy(alpha = 0.25f))
                    .border(2.dp, StitchColors.HomeGreen.copy(alpha = 0.58f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StitchSymbol(StitchSymbols.Inventory, "资源", size = 28.dp, color = StitchColors.HomeGreenDark)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "导入资源", fontFamily = StitchNunito, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 19.sp, color = StitchColors.PanelText)
                    Text(text = "从设备文件添加模型或图片", fontSize = 12.sp, color = StudioColors.TextSecondary)
                }
                StitchSymbol(StitchSymbols.ArrowForward, "打开导入", size = 23.dp, color = StitchColors.PanelText)
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(10.dp))
        }
        Text(text = selectedCategory.description, fontSize = 13.sp, color = StudioColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        if (selectedCategory == PresetCategory.AIGC) {
            AigcGeneratorPanel(
                modifier = Modifier.weight(1f),
                rodinKeyConfigured = rodinKeyConfigured,
                imageToImageKeyConfigured = imageToImageKeyConfigured,
                outputMode = aigcOutputMode,
                prompt = aigcPrompt,
                promptTemplate = aigcPromptTemplate,
                useImageReference = aigcUseImageReference,
                referenceImageName = aigcReferenceImageName,
                imagePreprocessMode = aigcImagePreprocessMode,
                tier = aigcTier,
                qualityOverride = aigcQualityOverride,
                meshMode = aigcMeshMode,
                hdTexture = aigcHdTexture,
                creative = aigcCreative,
                busy = aigcBusy,
                message = aigcMessage,
                onOpenKeyConfig = onOpenAigcKeyConfig,
                onOutputModeChange = onAigcOutputModeChange,
                onPromptChange = onAigcPromptChange,
                onPromptTemplateChange = onAigcPromptTemplateChange,
                onUseImageReferenceChange = onAigcUseImageReferenceChange,
                onImagePreprocessModeChange = onAigcImagePreprocessModeChange,
                onPickReferenceImage = onPickAigcReferenceImage,
                onClearReferenceImage = onClearAigcReferenceImage,
                onTierChange = onAigcTierChange,
                onMeshBudgetChange = onAigcMeshBudgetChange,
                onHdTextureChange = onAigcHdTextureChange,
                onCreativeChange = onAigcCreativeChange,
                onGenerate = onGenerateAigc,
            )
        } else if (selectedAssets.isEmpty()) {
            EmptyCategoryState(
                category = selectedCategory,
                modifier = Modifier.weight(1f),
            )
        } else {
            PresetAssetGrid(
                assets = selectedAssets,
                scrollState = scrollState,
                panelBounds = panelBounds,
                gridBounds = gridBounds,
                onGridBounds = { gridBounds = it },
                onPreviewSlotChanged = onPreviewSlotChanged,
                modifier = Modifier.weight(1f),
                onPick = onPick,
            )
        }
    }
}

@Composable
fun SkyboxPanel(
    assets: List<UserSkyboxAsset>,
    current: PageSkybox?,
    onOpenObjects: () -> Unit,
    onOpenAigc: () -> Unit,
    onOpenResource: () -> Unit,
    onSelect: (UserSkyboxAsset) -> Unit,
    onClear: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetRotation: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(AssetPanelMetrics.PANEL_WIDTH_DP.dp)
            .height(AssetPanelMetrics.PANEL_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(StitchColors.PanelSurface)
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(28.dp))
            .padding(AssetPanelMetrics.PANEL_PADDING_DP.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "资源中心", fontFamily = StitchNunito, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 24.sp, color = StitchColors.PanelText)
                Text(text = "每页可使用不同的 2:1 天空背景", fontSize = 13.sp, color = StudioColors.TextSecondary)
            }
            ActionIconButton(R.drawable.ic_close, "关闭资源中心", true, onClose)
        }
        Spacer(Modifier.height(12.dp))
        ResourcePanelTabs(
            selectedCategory = PresetCategory.PRESET_MODEL,
            skyboxSelected = true,
            onPresets = onOpenObjects,
            onAigc = onOpenAigc,
            onSkyboxes = {},
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .spatialHoverEffect()
                    .clickable { onOpenResource() }
                .background(StitchColors.HomeGreen.copy(alpha = 0.25f))
                .border(2.dp, StitchColors.HomeGreen.copy(alpha = 0.58f), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StitchSymbol(StitchSymbols.Inventory, "资源", size = 28.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "打开资源", fontFamily = StitchNunito, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 19.sp, color = StitchColors.PanelText)
                Text(text = "导入接近 2:1 的 PNG / JPG / WebP", fontSize = 12.sp, color = StudioColors.TextSecondary)
            }
            StitchSymbol(StitchSymbols.ArrowForward, "打开资源", size = 23.dp, color = StitchColors.PanelText)
        }
        Spacer(Modifier.height(14.dp))
        if (current != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(StudioColors.SurfaceRaised)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = current.displayName, fontSize = 16.sp, color = StudioColors.TextPrimary)
                    Text(text = "当前页 · 调整水平朝向", fontSize = 12.sp, color = StudioColors.TextSecondary)
                }
                ActionIconButton(R.drawable.ic_rotate_left, "天空背景向左转", true, onRotateLeft)
                ActionIconButton(R.drawable.ic_refresh, "重置天空背景朝向", true, onResetRotation)
                ActionIconButton(R.drawable.ic_rotate_right, "天空背景向右转", true, onRotateRight)
                ActionIconButton(R.drawable.ic_delete, "移除当前页天空背景", true, onClear)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (assets.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "还没有用过的天空背景", fontSize = 16.sp, color = StudioColors.TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                assets.forEach { asset ->
                    val selected = asset.source == current?.source
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .spatialHoverEffect()
                            .clickable { onSelect(asset) }
                            .background(if (selected) StudioColors.PrimarySoft else StudioColors.SurfaceRaised)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(R.drawable.ic_scene, if (selected) "使用中" else "天空")
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = asset.displayName, fontSize = 16.sp, color = StudioColors.TextPrimary)
                            Text(
                                text = "${asset.width} × ${asset.height}${if (selected) " · 当前页使用中" else ""}",
                                fontSize = 12.sp,
                                color = StudioColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcePanelTabs(
    selectedCategory: PresetCategory,
    skyboxSelected: Boolean,
    onPresets: () -> Unit,
    onAigc: () -> Unit,
    onSkyboxes: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            Triple("预置模型", PresetCategory.PRESET_MODEL, onPresets),
            Triple("AIGC-2/3D", PresetCategory.AIGC, onAigc),
            Triple("天空盒", null, onSkyboxes),
        ).forEach { (label, category, onClick) ->
            val selected = if (category == null) skyboxSelected else !skyboxSelected && category == selectedCategory
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .spatialHoverEffect()
                    .clickable { onClick() }
                    .background(if (selected) StitchColors.HomeGreen.copy(alpha = 0.3f) else StitchColors.PanelSurfaceMuted)
                    .border(if (selected) 2.dp else 1.dp, if (selected) StitchColors.HomeGreen else StitchColors.PanelOutline, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    color = if (selected) StudioColors.PrimaryLabel else StudioColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun PresetAssetGrid(
    assets: List<PresetAsset>,
    scrollState: ScrollState,
    panelBounds: Rect?,
    gridBounds: Rect?,
    onGridBounds: (Rect) -> Unit,
    onPreviewSlotChanged: (String, AssetPreviewSlot) -> Unit,
    modifier: Modifier = Modifier,
    onPick: (PresetAsset) -> Unit,
) {
    val density = LocalDensity.current
    val lengthConverter = LocalPhysicalLengthConverter.current

    Column(
        modifier = modifier
            .onGloballyPositioned { onGridBounds(it.boundsInRoot()) }
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(AssetPanelMetrics.GRID_GAP_DP.dp),
    ) {
        val geometryAssets = assets.filter { it.geometry != null }
        val modelAssets = assets.filter { it.geometry == null }
        if (geometryAssets.isNotEmpty()) {
            AssetGridSectionTitle("基础几何", "快速搭建结构与占位")
            AssetGridRows(
                assets = geometryAssets,
                panelBounds = panelBounds,
                gridBounds = gridBounds,
                density = density,
                lengthConverter = lengthConverter,
                onPreviewSlotChanged = onPreviewSlotChanged,
                onPick = onPick,
            )
        }
        if (modelAssets.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            AssetGridSectionTitle("场景模型", "带材质的精选预置 GLB")
            AssetGridRows(
                assets = modelAssets,
                panelBounds = panelBounds,
                gridBounds = gridBounds,
                density = density,
                lengthConverter = lengthConverter,
                onPreviewSlotChanged = onPreviewSlotChanged,
                onPick = onPick,
            )
        }
    }
}

@Composable
private fun AssetGridSectionTitle(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(text = title, fontSize = 17.sp, color = StudioColors.TextPrimary)
        Spacer(Modifier.width(8.dp))
        Text(text = detail, fontSize = 11.sp, color = StudioColors.TextSecondary)
    }
}

@Composable
private fun AssetGridRows(
    assets: List<PresetAsset>,
    panelBounds: Rect?,
    gridBounds: Rect?,
    density: androidx.compose.ui.unit.Density,
    lengthConverter: PhysicalLengthConverter,
    onPreviewSlotChanged: (String, AssetPreviewSlot) -> Unit,
    onPick: (PresetAsset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AssetPanelMetrics.GRID_GAP_DP.dp)) {
        assets.chunked(AssetPanelMetrics.CARD_COLUMNS).forEach { rowAssets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AssetPanelMetrics.GRID_GAP_DP.dp),
            ) {
                rowAssets.forEach { asset ->
                    AssetCard(
                        asset = asset,
                        panelBounds = panelBounds,
                        gridBounds = gridBounds,
                        density = density,
                        lengthConverter = lengthConverter,
                        onPreviewSlotChanged = onPreviewSlotChanged,
                        modifier = Modifier.weight(1f),
                        onPick = onPick,
                    )
                }
                repeat(AssetPanelMetrics.CARD_COLUMNS - rowAssets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: PresetAsset,
    panelBounds: Rect?,
    gridBounds: Rect?,
    density: androidx.compose.ui.unit.Density,
    lengthConverter: PhysicalLengthConverter,
    onPreviewSlotChanged: (String, AssetPreviewSlot) -> Unit,
    modifier: Modifier = Modifier,
    onPick: (PresetAsset) -> Unit,
) {
    Column(
        modifier = modifier
            .height(AssetPanelMetrics.CARD_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(20.dp))
            .spatialHoverEffect()
            .clickable { onPick(asset) }
            .background(StitchColors.PanelSurfaceMuted)
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(AssetPanelMetrics.CARD_PADDING_DP.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AssetPanelMetrics.CARD_PREVIEW_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (asset.geometry == null) StudioColors.SurfaceMuted else Color.Transparent)
                .onGloballyPositioned { coordinates ->
                    val panel = panelBounds ?: return@onGloballyPositioned
                    val grid = gridBounds ?: return@onGloballyPositioned
                    val bounds = coordinates.boundsInRoot()
                    val centerXDp = with(density) {
                        (bounds.left + bounds.width / 2f - panel.left).toDp().value
                    }
                    val centerYDp = with(density) {
                        (bounds.top + bounds.height / 2f - panel.top).toDp().value
                    }
                    val panelWidthDp = with(density) { panel.width.toDp().value }
                    val panelHeightDp = with(density) { panel.height.toDp().value }
                    val widthDp = with(density) { bounds.width.toDp().value }
                    val heightDp = with(density) { bounds.height.toDp().value }
                    val contentWidthDp = (widthDp - AssetPanelMetrics.CARD_PREVIEW_INSET_DP * 2f)
                        .coerceAtLeast(1f)
                    val contentHeightDp = (heightDp - AssetPanelMetrics.CARD_PREVIEW_INSET_DP * 2f)
                        .coerceAtLeast(1f)
                    val intersectsViewport = bounds.right > grid.left + 2f &&
                        bounds.left < grid.right - 2f &&
                        bounds.bottom > grid.top + 2f &&
                        bounds.top < grid.bottom - 2f
                    onPreviewSlotChanged(
                        asset.id,
                        AssetPreviewSlot(
                            centerOffsetXMeters = lengthConverter.dpToLength(
                                (centerXDp - panelWidthDp / 2f).dp,
                                LengthUnit.Meters,
                            ),
                            centerOffsetYMeters = lengthConverter.dpToLength(
                                (panelHeightDp / 2f - centerYDp).dp,
                                LengthUnit.Meters,
                            ),
                            widthMeters = lengthConverter.dpToLength(widthDp.dp, LengthUnit.Meters),
                            heightMeters = lengthConverter.dpToLength(heightDp.dp, LengthUnit.Meters),
                            contentWidthMeters = lengthConverter.dpToLength(
                                contentWidthDp.dp,
                                LengthUnit.Meters,
                            ),
                            contentHeightMeters = lengthConverter.dpToLength(
                                contentHeightDp.dp,
                                LengthUnit.Meters,
                            ),
                            visible = intersectsViewport,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (asset.thumbnailRes != null) {
                Image(
                    painter = painterResource(asset.thumbnailRes),
                    contentDescription = asset.displayName,
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            } else if (asset.geometry == null) {
                Text(text = "生成", fontSize = 14.sp, color = StudioColors.TextSecondary)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = asset.displayName, fontSize = 16.sp, color = StudioColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(text = "+", fontSize = 19.sp, color = StitchColors.HomeGreenDark)
        }
    }
}

@Composable
private fun AigcGeneratorPanel(
    modifier: Modifier = Modifier,
    rodinKeyConfigured: Boolean,
    imageToImageKeyConfigured: Boolean,
    outputMode: AigcOutputMode,
    prompt: String,
    promptTemplate: AigcPromptTemplate?,
    useImageReference: Boolean,
    referenceImageName: String?,
    imagePreprocessMode: AigcImagePreprocessMode,
    tier: RodinGenerationTier,
    qualityOverride: Int?,
    meshMode: String,
    hdTexture: Boolean,
    creative: Boolean,
    busy: Boolean,
    message: String,
    onOpenKeyConfig: () -> Unit,
    onOutputModeChange: (AigcOutputMode) -> Unit,
    onPromptChange: (String) -> Unit,
    onPromptTemplateChange: (AigcPromptTemplate?) -> Unit,
    onUseImageReferenceChange: (Boolean) -> Unit,
    onImagePreprocessModeChange: (AigcImagePreprocessMode) -> Unit,
    onPickReferenceImage: () -> Unit,
    onClearReferenceImage: () -> Unit,
    onTierChange: (RodinGenerationTier) -> Unit,
    onMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit,
    onHdTextureChange: (Boolean) -> Unit,
    onCreativeChange: (Boolean) -> Unit,
    onGenerate: () -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StitchColors.PanelSurfaceMuted.copy(alpha = 0.72f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(22.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StitchSymbol(StitchSymbols.AutoAwesome, "AIGC", size = 22.dp, color = StitchColors.HomeGreenDark)
                    Spacer(Modifier.width(7.dp))
                    Text(text = "AIGC 生成 · Rodin / Seedream", fontFamily = StitchNunito, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 18.sp, color = StitchColors.PanelText)
                }
                Text(
                    text = aigcKeySummary(
                        outputMode = outputMode,
                        imagePreprocessMode = imagePreprocessMode,
                        rodinKeyConfigured = rodinKeyConfigured,
                        imageToImageKeyConfigured = imageToImageKeyConfigured,
                    ),
                    fontSize = 11.sp,
                    color = if (
                        aigcRequiredKeysConfigured(
                            outputMode = outputMode,
                            imagePreprocessMode = imagePreprocessMode,
                            rodinKeyConfigured = rodinKeyConfigured,
                            imageToImageKeyConfigured = imageToImageKeyConfigured,
                        )
                    ) StudioColors.Success else StudioColors.DangerStrong,
                )
            }
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .spatialHoverEffect()
                    .clickable { onOpenKeyConfig() }
                    .background(StudioColors.SurfaceMuted)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "配置 Key", fontSize = 12.sp, color = StudioColors.TextPrimary)
            }
        }
        AigcOutputTabs(
            selected = outputMode,
            enabled = !busy,
            onChange = onOutputModeChange,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(StitchColors.PanelSurface)
                .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(text = "输入方式", fontSize = 13.sp, color = StudioColors.TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AigcChoiceChip(
                    label = if (outputMode == AigcOutputMode.IMAGE) "文生图" else "文生 3D",
                    selected = !useImageReference,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onUseImageReferenceChange(false) },
                )
                AigcChoiceChip(
                    label = if (outputMode == AigcOutputMode.IMAGE) "图生图" else "图片生成 3D",
                    selected = useImageReference,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    onClick = { onUseImageReferenceChange(true) },
                )
            }
        }
        if (useImageReference) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StitchColors.PanelSurface)
                    .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "参考图", fontSize = 13.sp, color = StudioColors.TextPrimary)
                    Text(
                        text = referenceImageName?.take(38) ?: "未选择图片，支持 PNG / JPG / WebP",
                        fontSize = 12.sp,
                        color = StudioColors.TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(StudioRadii.Control))
                        .then(if (!busy) Modifier.spatialHoverEffect().clickable { onPickReferenceImage() } else Modifier)
                        .background(if (busy) StudioColors.SurfaceDisabled else StudioColors.SurfaceRaised)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "选择图片", fontSize = 12.sp, color = if (busy) StudioColors.TextSecondary else StudioColors.TextPrimary)
                }
                if (referenceImageName != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(StudioRadii.Control))
                            .then(if (!busy) Modifier.spatialHoverEffect().clickable { onClearReferenceImage() } else Modifier)
                            .background(if (busy) StudioColors.SurfaceDisabled else StudioColors.SurfaceRaised)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "清除", fontSize = 12.sp, color = if (busy) StudioColors.TextSecondary else StudioColors.Danger)
                    }
                }
            }
            if (outputMode == AigcOutputMode.MODEL) {
                AigcImagePreprocessSelector(
                    selected = imagePreprocessMode,
                    enabled = !busy,
                    onChange = onImagePreprocessModeChange,
                )
            }
        }
        if (outputMode == AigcOutputMode.MODEL) {
            AigcAdvancedSection(
                open = advancedOpen,
                tier = tier,
                selectedQualityOverride = qualityOverride,
                selectedMeshMode = meshMode,
                hdTexture = hdTexture,
                creative = creative,
                enabled = !busy,
                onToggleOpen = { advancedOpen = !advancedOpen },
                onTierChange = onTierChange,
                onMeshBudgetChange = onMeshBudgetChange,
                onHdTextureChange = onHdTextureChange,
                onCreativeChange = onCreativeChange,
            )
        }
        if (!useImageReference) {
            AigcPromptTemplateSelector(
                useCase = if (outputMode == AigcOutputMode.IMAGE) {
                    AigcPromptUseCase.TEXT_TO_IMAGE
                } else {
                    AigcPromptUseCase.TEXT_TO_MODEL
                },
                selectedTemplate = promptTemplate,
                enabled = !busy,
                onTemplateChange = onPromptTemplateChange,
            )
        }
        AigcInputField(
            value = prompt,
            placeholder = if (outputMode == AigcOutputMode.IMAGE && useImageReference) {
                "描述如何改图，例如：换成玩具风格，背景更干净"
            } else if (promptTemplate != null) {
                "模板模式：只填写主体、颜色、用途或额外细节"
            } else if (outputMode == AigcOutputMode.IMAGE) {
                "描述想生成的图片，例如：一只低多边形蓝色鲸鱼海报"
            } else if (useImageReference) {
                "可选：补充 3D 生成要求，例如：透明主体，做成玩具风格"
            } else {
                "描述想生成的 3D 模型，例如：一只低多边形蓝色鲸鱼"
            },
            singleLine = false,
            onChange = onPromptChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            StudioTextButton(
                label = when {
                    busy -> "生成中..."
                    outputMode == AigcOutputMode.IMAGE -> "生成图片并加入场景"
                    else -> "发送生成请求"
                },
                enabled = !busy,
                style = StudioButtonStyle.Primary,
                height = 46.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = onGenerate,
            )
        }
    }
}

@Composable
private fun AigcOutputTabs(
    selected: AigcOutputMode,
    enabled: Boolean,
    onChange: (AigcOutputMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "生成类型", fontSize = 13.sp, color = StudioColors.TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AigcOutputTab(
                mode = AigcOutputMode.MODEL,
                selected = selected == AigcOutputMode.MODEL,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onChange(AigcOutputMode.MODEL) },
            )
            AigcOutputTab(
                mode = AigcOutputMode.IMAGE,
                selected = selected == AigcOutputMode.IMAGE,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onChange(AigcOutputMode.IMAGE) },
            )
        }
    }
}

@Composable
private fun AigcOutputTab(
    mode: AigcOutputMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background = if (selected) StitchColors.HomeGreen else StitchColors.PanelSurface
    val primary = if (selected) StudioColors.SurfaceRaised else StudioColors.TextPrimary
    val secondary = if (selected) StudioColors.PrimarySoft else StudioColors.TextSecondary
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(StudioRadii.Section))
            .then(if (enabled) Modifier.spatialHoverEffect().clickable { onClick() } else Modifier)
            .background(if (enabled) background else StudioColors.SurfaceDisabled)
            .border(if (selected) 2.dp else 1.dp, if (selected) StitchColors.HomeGreenDark else StitchColors.PanelOutline, RoundedCornerShape(StudioRadii.Section))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StitchSymbol(if (mode == AigcOutputMode.MODEL) StitchSymbols.ViewInAr else StitchSymbols.Image, mode.displayName, size = 26.dp, color = primary)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = mode.displayName, fontSize = 16.sp, color = primary)
                Text(
                    text = if (mode == AigcOutputMode.MODEL) "Rodin 模型" else "Seedream 图片",
                    fontSize = 10.sp,
                    color = if (enabled) secondary else StudioColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AigcAdvancedSection(
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
            .background(StitchColors.PanelSurface)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .spatialHoverEffect()
                .clickable { if (enabled) onToggleOpen() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "高级参数", fontSize = 14.sp, color = StudioColors.TextPrimary)
                Text(
                    text = aigcAdvancedSummary(
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
            AigcMeshBudgetSelector(
                selectedQualityOverride = selectedQualityOverride,
                selectedMeshMode = selectedMeshMode,
                enabled = enabled,
                onMeshBudgetChange = onMeshBudgetChange,
            )
            AigcTierSelector(
                selectedTier = tier,
                enabled = enabled,
                onTierChange = onTierChange,
            )
            AigcBooleanSelector(
                title = "贴图",
                falseLabel = "标准贴图",
                trueLabel = "高清贴图",
                selected = hdTexture,
                enabled = enabled,
                onChange = onHdTextureChange,
            )
            AigcBooleanSelector(
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

private data class AigcMeshBudgetOption(
    val displayName: String,
    val qualityOverride: Int?,
    val meshMode: String,
)

private val AigcMeshBudgetOptions = listOf(
    AigcMeshBudgetOption("4K", 4_000, "Raw"),
    AigcMeshBudgetOption("8K", 8_000, "Raw"),
    AigcMeshBudgetOption("10K", 10_000, "Raw"),
    AigcMeshBudgetOption("20K", 20_000, "Raw"),
    AigcMeshBudgetOption("50K", 50_000, "Raw"),
    AigcMeshBudgetOption("100K", 100_000, "Raw"),
    AigcMeshBudgetOption("500K", 500_000, "Raw"),
)

@Composable
private fun AigcMeshBudgetSelector(
    selectedQualityOverride: Int?,
    selectedMeshMode: String,
    enabled: Boolean,
    onMeshBudgetChange: (qualityOverride: Int?, meshMode: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "面数（三角面 · Raw）", fontSize = 13.sp, color = StudioColors.TextPrimary)
        AigcMeshBudgetOptions.chunked(4).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    AigcChoiceChip(
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
private fun AigcTierSelector(
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
                    AigcChoiceChip(
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
private fun AigcBooleanSelector(
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
            AigcChoiceChip(
                label = falseLabel,
                selected = !selected,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onChange(false) },
            )
            AigcChoiceChip(
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
private fun AigcImagePreprocessSelector(
    selected: AigcImagePreprocessMode,
    enabled: Boolean,
    onChange: (AigcImagePreprocessMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "图生 3D 前处理", fontSize = 13.sp, color = StudioColors.TextPrimary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AigcImagePreprocessMode.entries.forEach { mode ->
                AigcChoiceChip(
                    label = mode.displayName,
                    selected = selected == mode,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(mode) },
                )
            }
        }
    }
}

private fun aigcRequiredKeysConfigured(
    outputMode: AigcOutputMode,
    imagePreprocessMode: AigcImagePreprocessMode,
    rodinKeyConfigured: Boolean,
    imageToImageKeyConfigured: Boolean,
): Boolean =
    when {
        outputMode == AigcOutputMode.IMAGE -> imageToImageKeyConfigured
        imagePreprocessMode == AigcImagePreprocessMode.SEEDREAM_CLEAN -> rodinKeyConfigured && imageToImageKeyConfigured
        else -> rodinKeyConfigured
    }

private fun aigcKeySummary(
    outputMode: AigcOutputMode,
    imagePreprocessMode: AigcImagePreprocessMode,
    rodinKeyConfigured: Boolean,
    imageToImageKeyConfigured: Boolean,
): String =
    when {
        outputMode == AigcOutputMode.IMAGE && imageToImageKeyConfigured -> "Seedream Key 已配置，生图会直接加入场景"
        outputMode == AigcOutputMode.IMAGE -> "Seedream Key 未配置，发送时会弹出配置窗"
        imagePreprocessMode == AigcImagePreprocessMode.SEEDREAM_CLEAN && rodinKeyConfigured && imageToImageKeyConfigured ->
            "Rodin / Seedream Key 已配置"
        imagePreprocessMode == AigcImagePreprocessMode.SEEDREAM_CLEAN -> "需要 Rodin Key 和 Seedream 图生图 Key"
        rodinKeyConfigured -> "Rodin Key 已配置"
        else -> "Rodin Key 未配置，发送时会弹出配置窗"
    }

private fun aigcAdvancedSummary(
    tier: RodinGenerationTier,
    qualityOverride: Int?,
    meshMode: String,
    hdTexture: Boolean,
    creative: Boolean,
): String {
    val faceBudget = AigcMeshBudgetOptions.firstOrNull {
        it.qualityOverride == qualityOverride && it.meshMode == meshMode
    }?.displayName ?: "默认面数"
    val texture = if (hdTexture) "高清贴图" else "标准贴图"
    val mode = if (creative) "创意" else "保真"
    return "$faceBudget 三角面 / ${tier.displayName} / $texture / $mode"
}

@Composable
private fun AigcChoiceChip(
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
        height = 30.dp,
        onClick = onClick,
    )
}

@Composable
private fun AigcInputField(
    value: String,
    placeholder: String,
    singleLine: Boolean,
    onChange: (String) -> Unit,
) {
    StudioInputField(
        value = value,
        placeholder = placeholder,
        enabled = true,
        singleLine = singleLine,
        onChange = onChange,
    )
}

@Composable
private fun EmptyCategoryState(
    category: PresetCategory,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(StudioColors.SurfaceRaised)
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(
            iconRes = R.drawable.ic_model_cube,
            label = category.displayName,
        )
        Spacer(Modifier.height(14.dp))
        Text(text = category.emptyTitle, fontSize = 18.sp, color = StudioColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = category.emptyMessage, fontSize = 13.sp, color = StudioColors.TextSecondary)
    }
}

@Composable
internal fun ActionIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ActionIconButton(
        iconRes = iconRes,
        contentDescription = contentDescription,
        enabled = enabled,
        active = false,
        onClick = onClick,
    )
}

@Composable
internal fun ActionIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    active: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val clickable = enabled && !loading
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        loading || active -> StudioColors.PrimarySoft
        !enabled -> StitchColors.PanelSurfaceMuted
        pressed -> StitchColors.PanelSurfaceMuted
        else -> StitchColors.PanelSurface
    }
    val tint = if (active) StitchColors.HomeGreenDark else if (enabled || loading) StitchColors.PanelText else StitchColors.PanelTextMuted
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(50.dp)
            .offset(y = if (pressed && clickable) 2.dp else 0.dp)
            .shadow(5.dp, shape)
            .clip(shape)
            .stitchClickable(
                enabled = clickable,
                interactionSource = interactionSource,
                hoverEnabled = !active,
                onClick = onClick,
            )
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            ActionLoadingIndicator(contentDescription)
        } else {
            val symbol = stitchSymbolForDrawable(iconRes)
            if (symbol != null) {
                StitchSymbol(
                    symbol = symbol,
                    contentDescription = contentDescription,
                    size = 25.dp,
                    color = tint,
                )
            } else {
                val iconSize = when (iconRes) {
                    R.drawable.ic_subject_avatar,
                    R.drawable.ic_ai_image,
                    R.drawable.ic_ai_3d_model -> 27.dp
                    else -> 22.dp
                }
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                    tint = tint,
                )
            }
        }
    }
}

@Composable
private fun ActionLoadingIndicator(contentDescription: String) {
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
            .size(24.dp)
            .rotate(rotation),
    ) {
        drawArc(
            color = StudioColors.PrimaryStrong,
            startAngle = 0f,
            sweepAngle = 285f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun IconTile(iconRes: Int, label: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .width(if (compact) 38.dp else 46.dp)
            .height(if (compact) 32.dp else 42.dp)
            .clip(RoundedCornerShape(if (compact) 14.dp else 16.dp))
            .background(StudioColors.SurfaceSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            tint = StudioColors.TextPrimary,
        )
    }
}
