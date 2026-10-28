package com.example.showme3d.content

import com.example.showme3d.release.ReleasePolicy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.showme3d.library.PresetAsset
import com.example.showme3d.library.PresetCategory
import com.example.showme3d.library.PresetGeometry
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.aigc.AigcGeneratedAsset
import com.example.showme3d.studio.aigc.AigcGenerationRecord
import com.example.showme3d.studio.aigc.AigcGenerationRecordStatus
import com.example.showme3d.studio.aigc.AigcGenerationRecordType
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.aigc.AigcLocalLibrary
import com.example.showme3d.studio.aigc.AigcOutputMode
import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.example.showme3d.studio.aigc.AigcSettingsStore
import com.example.showme3d.studio.aigc.RodinAigcClient
import com.example.showme3d.studio.aigc.RodinGenerationOptions
import com.example.showme3d.studio.aigc.RodinGenerationTier
import com.example.showme3d.studio.aigc.DEFAULT_RODIN_FACE_BUDGET
import com.example.showme3d.studio.aigc.DEFAULT_RODIN_GENERATION_TIER
import com.example.showme3d.studio.aigc.SeedreamImageClient
import com.example.showme3d.studio.aigc.StrokeFrontFace
import com.example.showme3d.studio.aigc.StrokeImageRenderer
import com.example.showme3d.studio.aigc.buildAigcPrompt
import com.example.showme3d.studio.aigc.buildAigcPromptDetails
import com.example.showme3d.studio.aigc.SubjectCutoutProcessor
import com.example.showme3d.studio.agent.AgentRunStatus
import com.example.showme3d.studio.agent.AgentRunResult
import com.example.showme3d.studio.agent.AgentRunStepProgress
import com.example.showme3d.studio.agent.AgentRunStepStatus
import com.example.showme3d.studio.agent.AgentRunner
import com.example.showme3d.studio.agent.AgentGenerationOptionParser
import com.example.showme3d.studio.agent.AgentSuggestionPlan
import com.example.showme3d.studio.agent.AgentToolResult
import com.example.showme3d.studio.agent.AgentUserMessageFormatter
import com.example.showme3d.studio.agent.DoubaoAgentClient
import com.example.showme3d.studio.agent.DoubaoConversationMessage
import com.example.showme3d.studio.agent.DoubaoConversationRole
import com.example.showme3d.studio.agent.DoubaoToolCall
import com.example.showme3d.studio.agent.ToolExecutor
import com.example.showme3d.studio.examples.CuriosityExampleAssetInstaller
import com.example.showme3d.studio.examples.DinosaurDetectiveExampleAssetInstaller
import com.example.showme3d.studio.examples.HomeExamplePreset
import com.example.showme3d.studio.examples.ImmersiveInteriorExampleAssetInstaller
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.imports.ImportBrowserPage
import com.example.showme3d.studio.imports.ImportCandidate
import com.example.showme3d.studio.imports.LocalAssetImporter
import com.example.showme3d.studio.imports.UserSkyboxAsset
import com.example.showme3d.studio.imports.UserSkyboxLibrary
import com.example.showme3d.studio.interaction.manipulatable
import com.example.showme3d.studio.interaction.selectable
import com.example.showme3d.studio.interaction.trackStudioUiPointer
import com.example.showme3d.studio.interaction.workspaceDraggable
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.StrokeData
import com.example.showme3d.studio.model.StrokePoint
import com.example.showme3d.studio.model.TransformData
import com.example.showme3d.studio.persistence.ShowFileRepository
import com.example.showme3d.studio.scene.ObjectEntityFactory
import com.example.showme3d.studio.scene.SceneController
import com.example.showme3d.studio.state.AppScreen
import com.example.showme3d.studio.state.AppMode
import com.example.showme3d.studio.state.PresentAnnotationTool
import com.example.showme3d.studio.state.SceneEnvironmentMode
import com.example.showme3d.studio.state.StudioPanel
import com.example.showme3d.studio.state.StudioViewModel
import com.example.showme3d.studio.state.WhiteboardTool
import com.example.showme3d.studio.ui.AigcKeyConfigPanel
import com.example.showme3d.studio.ui.AigcLibraryPanel
import com.example.showme3d.studio.ui.AgentClarification
import com.example.showme3d.studio.ui.AgentMessageTone
import com.example.showme3d.studio.ui.AgentMessageRole
import com.example.showme3d.studio.ui.AgentConversationDockPanel
import com.example.showme3d.studio.ui.AgentSuggestion
import com.example.showme3d.studio.ui.AgentSuggestionDockPanel
import com.example.showme3d.studio.ui.AgentToolChainDockPanel
import com.example.showme3d.studio.ui.AgentTaskStep
import com.example.showme3d.studio.ui.AgentTaskStepStatus
import com.example.showme3d.studio.ui.AgentUiMessage
import com.example.showme3d.studio.ui.AssetPanel
import com.example.showme3d.studio.ui.AssetPreviewSlot
import com.example.showme3d.studio.ui.SkyboxPanel
import com.example.showme3d.studio.ui.AnimationEditorPanel
import com.example.showme3d.studio.ui.FilePanel
import com.example.showme3d.studio.ui.HomeDeleteConfirmPanel
import com.example.showme3d.studio.ui.HomeEnvironmentTogglePanel
import com.example.showme3d.studio.ui.HomeFileActionMenuPanel
import com.example.showme3d.studio.ui.HomeUnifiedPanel
import com.example.showme3d.studio.ui.ImportBrowserPanel
import com.example.showme3d.studio.ui.ImageToModelConfirmPanel
import com.example.showme3d.studio.ui.ImageToImagePanel
import com.example.showme3d.studio.ui.ImageSubjectPreprocessChoicePanel
import com.example.showme3d.studio.ui.PageStripPanel
import com.example.showme3d.studio.ui.PanelPosition
import com.example.showme3d.studio.ui.PresentPanel
import com.example.showme3d.studio.ui.RotationControlsPanel
import com.example.showme3d.studio.ui.SelectionBarPanel
import com.example.showme3d.studio.ui.AnnotationManagePanel
import com.example.showme3d.studio.ui.CommentEditorPanel
import com.example.showme3d.studio.ui.StrokeToImagePanel
import com.example.showme3d.studio.ui.StrokeEditorPanel
import com.example.showme3d.studio.ui.TextEditorPanel
import com.example.showme3d.studio.ui.TextToImagePanel
import com.example.showme3d.studio.ui.ToolbarMorePanel
import com.example.showme3d.studio.ui.ToolbarPanel
import com.example.showme3d.studio.ui.ToolbarUndoPanel
import com.example.showme3d.studio.ui.WorkspaceDragHandlePanel
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingData
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stable M1 workspace: one scene, one document, minimal panels.
 */
@Composable
fun StudioStage() {
    val context = LocalContext.current
    val vm = remember { StudioViewModel() }
    val showRepository = remember(context) { ShowFileRepository(context.applicationContext) }
    val aigcStore = remember(context) { AigcSettingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var scene by remember { mutableStateOf<SceneController?>(null) }
    var homeFloatingModels by remember { mutableStateOf<HomeFloatingModels?>(null) }
    var homeFloatingSelectedId by remember { mutableStateOf<String?>(null) }
    var homeFloatingRevision by remember { mutableStateOf(0) }
    var homeFloatingManipulationActive by remember { mutableStateOf(false) }
    var homeFloatingTargetPointerActive by remember { mutableStateOf(false) }
    var storageReady by remember { mutableStateOf(false) }
    var lastPageRevision by remember { mutableStateOf(vm.pageRevision) }
    var editorSceneSyncedOnce by remember { mutableStateOf(false) }
    var browserDirectory by remember { mutableStateOf<String?>(null) }
    var importBrowserPurpose by remember { mutableStateOf(ImportBrowserPurpose.ASSET) }
    var importBrowserExpectedType by remember { mutableStateOf<ObjectType?>(null) }
    var importBrowserTitle by remember { mutableStateOf("导入资源") }
    var importBrowserParent by remember { mutableStateOf<String?>(null) }
    var importCandidates by remember { mutableStateOf<List<ImportCandidate>>(emptyList()) }
    var importBrowserMessage by remember { mutableStateOf("选择一个位置浏览文件。") }
    var workspacePosition by remember { mutableStateOf(DefaultWorkspacePosition) }
    var homePosition by remember { mutableStateOf(DefaultHomePosition) }
    val liveHomePosition = remember { PanelPositionHolder(DefaultHomePosition) }
    var homeEnvironmentSwitchBusy by remember { mutableStateOf(false) }
    var homeRiftController by remember { mutableStateOf<HomeEnvironmentRiftController?>(null) }
    var homeRiftCapturing by remember { mutableStateOf(false) }
    var homeRiftTransitioning by remember { mutableStateOf(false) }
    var homePageGestureActive by remember { mutableStateOf(false) }
    var homePageGestureBlockedUntilNanos by remember { mutableStateOf(0L) }
    val homeRiftSceneProgress = remember { Animatable(1f) }
    val homeRiftSession = remember { HomeEnvironmentRiftGestureSession() }
    val homeRiftClickGuard = remember { HomeRiftClickGuard() }
    val requestHomeEnvironmentToggle = {
        if (!homeEnvironmentSwitchBusy) {
            homeEnvironmentSwitchBusy = true
            vm.toggleSceneEnvironment()
        }
    }
    DisposableEffect(homeRiftController) {
        // Capture the controller that belongs to this effect instance. Reading the mutable
        // state again in onDispose can destroy the newly-created controller when the initial
        // null-key effect is replaced after SpatialView.initial.
        val ownedController = homeRiftController
        onDispose { ownedController?.destroy() }
    }
    var panelEntities by remember { mutableStateOf<Map<String, Entity>>(emptyMap()) }
    var assetPreviewEntities by remember { mutableStateOf<Map<String, AssetPreviewEntity>>(emptyMap()) }
    var assetPreviewSlots by remember { mutableStateOf<Map<String, AssetPreviewSlot>>(emptyMap()) }
    var workspaceDragHandle by remember { mutableStateOf<Entity?>(null) }
    val viewerPose = remember { ViewerPoseState() }
    val panelDragState = remember { PanelDragRuntimeState() }
    var homeActionShowIndex by remember { mutableStateOf<Int?>(null) }
    var homeDeleteShowIndex by remember { mutableStateOf<Int?>(null) }
    var uiPointerActive by remember { mutableStateOf(false) }
    var workspaceDragPointerActive by remember { mutableStateOf(false) }
    var lastUiPointerNanos by remember { mutableStateOf(0L) }
    var lastWorkspaceDragPointerNanos by remember { mutableStateOf(0L) }
    var chromeVisibleForAnimation by remember { mutableStateOf(false) }
    var assetPanelVisibleForAnimation by remember { mutableStateOf(false) }
    var agentPanelVisibleForAnimation by remember { mutableStateOf(false) }
    var agentPanelInteractive by remember { mutableStateOf(false) }
    var selectedAssetCategory by remember { mutableStateOf(PresetCategory.PRESET_MODEL) }
    var resourcePanelMode by remember { mutableStateOf(ResourcePanelMode.PRESETS) }
    var skyboxAssets by remember { mutableStateOf(UserSkyboxLibrary.load(context)) }
    var geometryColorPickerOpen by remember { mutableStateOf(false) }
    var toolbarMoreOpen by remember { mutableStateOf(false) }
    var pageStripCollapsed by remember { mutableStateOf(false) }
    var rodinApiKey by remember { mutableStateOf(aigcStore.rodinApiKey()) }
    var imageToImageApiKey by remember { mutableStateOf(aigcStore.imageToImageApiKey()) }
    var doubaoApiKey by remember { mutableStateOf(aigcStore.doubaoApiKey()) }
    var agentPanelOpen by remember { mutableStateOf(false) }
    var agentToolbarExpanded by remember { mutableStateOf(false) }
    var agentToolChainOpen by remember { mutableStateOf(false) }
    var agentDefaultSuggestionsVisible by remember { mutableStateOf(true) }
    var agentPrompt by remember { mutableStateOf("") }
    var agentBusy by remember { mutableStateOf(false) }
    var agentMessage by remember { mutableStateOf("") }
    var agentMessageTone by remember { mutableStateOf(AgentMessageTone.NEUTRAL) }
    var agentMessages by remember {
        mutableStateOf(
            listOf(
                AgentUiMessage(
                    id = "agent_message_initial",
                    role = AgentMessageRole.ASSISTANT,
                    text = "想要创作点什么？",
                )
            )
        )
    }
    var agentTaskSteps by remember { mutableStateOf<List<AgentTaskStep>>(emptyList()) }
    var agentExamplesExpanded by remember { mutableStateOf(false) }
    var agentMessageCounter by remember { mutableStateOf(0) }
    var agentConversationEpoch by remember { mutableStateOf(0) }
    var pendingAgentClarification by remember { mutableStateOf<PendingAgentClarification?>(null) }
    var pendingAgentSuggestions by remember { mutableStateOf<List<AgentSuggestionPlan>>(emptyList()) }
    val agentSuggestionsVisible = agentDefaultSuggestionsVisible || pendingAgentSuggestions.isNotEmpty()
    var aigcKeyConfigPromptOpen by remember { mutableStateOf(false) }
    var aigcKeyConfigRequireRodin by remember { mutableStateOf(false) }
    var aigcKeyConfigRequireImage by remember { mutableStateOf(false) }
    var aigcKeyConfigRequireDoubao by remember { mutableStateOf(false) }
    var aigcKeyConfigTitle by remember { mutableStateOf("AI Key 配置") }
    var aigcKeyConfigMessage by remember { mutableStateOf("Key 仅保存在本机，用于端上直连对应生成服务。") }
    var aigcOutputMode by remember { mutableStateOf(AigcOutputMode.MODEL) }
    var aigcPrompt by remember { mutableStateOf("") }
    var aigcPromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var aigcUseImageReference by remember { mutableStateOf(false) }
    var aigcReferenceImagePath by remember { mutableStateOf<String?>(null) }
    var aigcReferenceImageName by remember { mutableStateOf<String?>(null) }
    var aigcImagePreprocessMode by remember { mutableStateOf(AigcImagePreprocessMode.NONE) }
    var aigcTier by remember { mutableStateOf(DEFAULT_RODIN_GENERATION_TIER) }
    var aigcQualityOverride by remember { mutableStateOf<Int?>(DEFAULT_RODIN_FACE_BUDGET) }
    var aigcMeshMode by remember { mutableStateOf("Raw") }
    var aigcHdTexture by remember { mutableStateOf(false) }
    var aigcCreative by remember { mutableStateOf(false) }
    var aigcBusy by remember { mutableStateOf(false) }
    var aigcMessage by remember { mutableStateOf("填写提示词后点击发送生成请求，生成 GLB 后会进入本分类。") }
    var generatedAigcAssets by remember { mutableStateOf<List<AigcGeneratedAsset>>(aigcStore.generatedAssets()) }
    var generatedAigcImages by remember { mutableStateOf<List<AigcGeneratedAsset>>(aigcStore.generatedImages()) }
    var aigcGenerationRecords by remember { mutableStateOf<List<AigcGenerationRecord>>(aigcStore.generationRecords()) }
    var imageSubjectPreviewBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var imageToModelConfirmObjectId by remember { mutableStateOf<String?>(null) }
    var imageToModelPrompt by remember { mutableStateOf("") }
    var imageToModelPromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var imageToModelPreprocessMode by remember { mutableStateOf(AigcImagePreprocessMode.LOCAL_SUBJECT) }
    var imageToModelAdvancedOpen by remember { mutableStateOf(false) }
    var imageToModelTier by remember { mutableStateOf(DEFAULT_RODIN_GENERATION_TIER) }
    var imageToModelQualityOverride by remember { mutableStateOf<Int?>(DEFAULT_RODIN_FACE_BUDGET) }
    var imageToModelMeshMode by remember { mutableStateOf("Raw") }
    var imageToModelHdTexture by remember { mutableStateOf(false) }
    var imageToModelCreative by remember { mutableStateOf(false) }
    var imageToModelBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var imageToModelMessage by remember { mutableStateOf("确认后会生成 3D 模型并替换原图片，失败时原图保持不变。") }
    var imageSubjectPreprocessChoiceObjectId by remember { mutableStateOf<String?>(null) }
    var imageSubjectPreprocessPrompt by remember { mutableStateOf("") }
    var imageSubjectPreprocessMessage by remember { mutableStateOf("请选择保留主体算法。") }
    var imageToImageObjectId by remember { mutableStateOf<String?>(null) }
    var imageToImagePrompt by remember { mutableStateOf("") }
    var imageToImagePromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var imageToImageBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var imageToImageMessage by remember { mutableStateOf("基于当前图片生成一张新版本，成功后替换当前物体。") }
    var textToImageObjectId by remember { mutableStateOf<String?>(null) }
    var textToImagePromptExtra by remember { mutableStateOf("") }
    var textToImagePromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var textToImageBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var textToImageMessage by remember { mutableStateOf("基于当前文本生成图片，并替换为图片对象。") }
    var strokeToSeedreamObjectId by remember { mutableStateOf<String?>(null) }
    var strokeToSeedreamPromptExtra by remember { mutableStateOf("") }
    var strokeToSeedreamPromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var strokeToSeedreamBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var strokeToModelObjectId by remember { mutableStateOf<String?>(null) }
    var strokeToModelPromptExtra by remember { mutableStateOf("") }
    var strokeToModelPromptTemplate by remember { mutableStateOf<AigcPromptTemplate?>(null) }
    var strokeToModelAdvancedOpen by remember { mutableStateOf(false) }
    var strokeToModelTier by remember { mutableStateOf(DEFAULT_RODIN_GENERATION_TIER) }
    var strokeToModelQualityOverride by remember { mutableStateOf<Int?>(DEFAULT_RODIN_FACE_BUDGET) }
    var strokeToModelMeshMode by remember { mutableStateOf("Raw") }
    var strokeToModelHdTexture by remember { mutableStateOf(false) }
    var strokeToModelCreative by remember { mutableStateOf(false) }
    var strokeToModelBusyObjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var strokeToModelMessage by remember {
        mutableStateOf("将当前观察方向的主视图送 Seedream 发散成图，再直接提交 Rodin 生成 3D。")
    }
    var strokeToSeedreamMessage by remember {
        mutableStateOf("将当前观察方向的主视图发送给 Seedream，自动补全并生成 2D 图片。")
    }
    val assetScrollState = rememberScrollState()
    val chromeProgress = remember { Animatable(0f) }
    val assetPanelProgress = remember { Animatable(0f) }
    val agentPanelProgress = remember { Animatable(0f) }
    val activeSelectionPopupObjectId = if (aigcKeyConfigPromptOpen) {
        null
    } else {
        imageToModelConfirmObjectId
            ?: imageToImageObjectId
            ?: imageSubjectPreprocessChoiceObjectId
            ?: textToImageObjectId
            ?: strokeToSeedreamObjectId
            ?: strokeToModelObjectId
    }
    val latestUiPointerActive by rememberUpdatedState(uiPointerActive || workspaceDragPointerActive)
    val latestLastUiPointerNanos by rememberUpdatedState(maxOf(lastUiPointerNanos, lastWorkspaceDragPointerNanos))
    val latestWorkspaceDragPointerActive by rememberUpdatedState(workspaceDragPointerActive)
    val latestLastWorkspaceDragPointerNanos by rememberUpdatedState(lastWorkspaceDragPointerNanos)
    val latestWorkspaceDragHandle by rememberUpdatedState(workspaceDragHandle)
    fun refreshImportBrowser(directoryPath: String? = browserDirectory) {
        val purpose = importBrowserPurpose
        val expectedType = importBrowserExpectedType
        browserDirectory = directoryPath
        importBrowserTitle = if (directoryPath == null) purpose.rootTitle else importBrowserTitle
        importBrowserParent = null
        importCandidates = emptyList()
        importBrowserMessage = if (purpose != ImportBrowserPurpose.ASSET) {
            "扫描图片文件中..."
        } else {
            "扫描设备文件中..."
        }
        vm.openImportBrowser()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LocalAssetImporter.listBrowserPage(context, expectedType = expectedType, directoryPath)
            }
            result
                .onSuccess { page: ImportBrowserPage ->
                    importBrowserTitle = purpose.titleFor(page)
                    browserDirectory = page.currentPath
                    importBrowserParent = page.parentPath
                    importCandidates = page.entries
                    importBrowserMessage = purpose.messageFor(page)
                    LocalAssetImporter.rememberBrowserDirectory(context, page.currentPath)
                }
                .onFailure {
                    importBrowserTitle = purpose.rootTitle
                    importBrowserParent = null
                    importCandidates = emptyList()
                    importBrowserMessage = "扫描失败：${it.message ?: it.javaClass.simpleName}"
                }
        }
    }

    fun openFileBrowser() {
        importBrowserPurpose = ImportBrowserPurpose.ASSET
        importBrowserExpectedType = null
        val lastDirectory = LocalAssetImporter.lastBrowserDirectory(context)
        browserDirectory = lastDirectory
        refreshImportBrowser(lastDirectory)
    }

    fun openAigcReferenceImageBrowser() {
        importBrowserPurpose = ImportBrowserPurpose.AIGC_REFERENCE_IMAGE
        importBrowserExpectedType = ObjectType.IMAGE
        val lastDirectory = LocalAssetImporter.lastBrowserDirectory(context)
        browserDirectory = lastDirectory
        refreshImportBrowser(lastDirectory)
    }

    fun openSkyboxBrowser() {
        importBrowserPurpose = ImportBrowserPurpose.SKYBOX
        importBrowserExpectedType = ObjectType.IMAGE
        val lastDirectory = LocalAssetImporter.lastBrowserDirectory(context)
        browserDirectory = lastDirectory
        refreshImportBrowser(lastDirectory)
    }

    fun importCandidate(candidate: ImportCandidate) {
        if (candidate.isDirectory) {
            refreshImportBrowser(candidate.path)
            return
        }
        when (importBrowserPurpose) {
            ImportBrowserPurpose.ASSET -> {
                vm.reportImportStarted(null)
                scope.launch {
                    val imported = withContext(Dispatchers.IO) {
                        LocalAssetImporter.importFromPath(context, candidate.path)
                    }
                    imported
                        .onSuccess { vm.addImportedAsset(it) }
                        .onFailure { vm.reportImportError(it.message ?: it.javaClass.simpleName) }
                }
            }

            ImportBrowserPurpose.AIGC_REFERENCE_IMAGE -> {
                vm.reportImportStarted(ObjectType.IMAGE)
                scope.launch {
                    val imported = withContext(Dispatchers.IO) {
                        LocalAssetImporter.importFromPath(context, candidate.path, expectedType = ObjectType.IMAGE)
                    }
                    imported
                        .onSuccess { asset ->
                            aigcReferenceImagePath = asset.source.removePrefix("file://")
                            aigcReferenceImageName = asset.displayName
                            aigcUseImageReference = true
                            selectedAssetCategory = PresetCategory.AIGC
                            aigcMessage = "已选择参考图：${asset.displayName.take(20)}。可补充提示词后发送生成请求。"
                            vm.toggleAssets()
                        }
                        .onFailure {
                            aigcMessage = "参考图选择失败：${it.message ?: it.javaClass.simpleName}"
                            vm.reportImportError(it.message ?: it.javaClass.simpleName)
                        }
                }
            }

            ImportBrowserPurpose.SKYBOX -> {
                scope.launch {
                    val imported = withContext(Dispatchers.IO) {
                        UserSkyboxLibrary.importFromPath(context, candidate.path)
                    }
                    imported
                        .onSuccess { asset ->
                            skyboxAssets = UserSkyboxLibrary.load(context)
                            vm.setCurrentPageSkybox(asset.source, asset.displayName)
                            resourcePanelMode = ResourcePanelMode.SKYBOX
                            vm.toggleAssets()
                        }
                        .onFailure {
                            importBrowserMessage = "天空背景导入失败：${it.message ?: it.javaClass.simpleName}"
                        }
                }
            }
        }
    }

    fun blockCloudAi(): Boolean {
        if (ReleasePolicy.CLOUD_AI_ENABLED) return false
        aigcKeyConfigPromptOpen = true
        return true
    }

    fun openAigcKeyConfig(
        requireRodin: Boolean = false,
        requireImage: Boolean = false,
        requireDoubao: Boolean = false,
        title: String = "AI Key 配置",
        message: String = "Key 仅保存在本机，用于端上直连对应生成服务。",
    ) {
        if (blockCloudAi()) return
        aigcKeyConfigRequireRodin = requireRodin
        aigcKeyConfigRequireImage = requireImage
        aigcKeyConfigRequireDoubao = requireDoubao
        aigcKeyConfigTitle = title
        aigcKeyConfigMessage = message
        aigcKeyConfigPromptOpen = true
    }

    fun saveAigcKeys(rodin: String, imageToImage: String, doubao: String) {
        if (blockCloudAi()) return
        rodinApiKey = rodin.trim()
        imageToImageApiKey = imageToImage.trim()
        doubaoApiKey = doubao.trim()
        aigcStore.saveRodinApiKey(rodinApiKey)
        aigcStore.saveImageToImageApiKey(imageToImageApiKey)
        aigcStore.saveDoubaoApiKey(doubaoApiKey)
        if (
            (!aigcKeyConfigRequireRodin || rodinApiKey.isNotBlank()) &&
            (!aigcKeyConfigRequireImage || imageToImageApiKey.isNotBlank()) &&
            (!aigcKeyConfigRequireDoubao || doubaoApiKey.isNotBlank())
        ) {
            aigcKeyConfigPromptOpen = false
            aigcKeyConfigRequireRodin = false
            aigcKeyConfigRequireImage = false
            aigcKeyConfigRequireDoubao = false
            aigcMessage = "AI Key 已配置，填写提示词后可直接发送生成请求。"
            if (agentPanelOpen) {
                agentMessage = "豆包 Key 已保存，可以继续发送指令。"
                agentMessageTone = AgentMessageTone.SUCCESS
            }
        }
    }

    fun openAgentKeyConfig() {
        openAigcKeyConfig(
            requireDoubao = true,
            title = "配置豆包 Key",
            message = "AI 助手会用豆包把一句自然语言转换成一个本地编辑工具调用。Key 仅保存在本机。",
        )
    }

    fun nextAgentMessageId(): String {
        agentMessageCounter += 1
        return "agent_message_$agentMessageCounter"
    }

    fun appendAgentMessage(
        role: AgentMessageRole,
        text: String,
        tone: AgentMessageTone = AgentMessageTone.NEUTRAL,
    ) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        agentMessages = (agentMessages + AgentUiMessage(nextAgentMessageId(), role, cleaned, tone)).takeLast(18)
    }

    fun resetAgentConversation() {
        agentConversationEpoch += 1
        agentPrompt = ""
        agentTaskSteps = emptyList()
        agentExamplesExpanded = false
        agentDefaultSuggestionsVisible = true
        pendingAgentClarification = null
        pendingAgentSuggestions = emptyList()
        val keyReady = doubaoApiKey.isNotBlank()
        agentMessage = if (keyReady) {
            ""
        } else {
            "请先配置豆包 Key，再执行自然语言编辑。"
        }
        agentMessageTone = if (keyReady) AgentMessageTone.NEUTRAL else AgentMessageTone.WARNING
        agentMessages = listOf(
            AgentUiMessage(
                id = nextAgentMessageId(),
                role = AgentMessageRole.ASSISTANT,
                text = "想要创作点什么？",
                tone = agentMessageTone,
            )
        )
    }

    fun closeAgentPanel() {
        agentPanelOpen = false
        agentToolbarExpanded = false
        agentToolChainOpen = false
    }

    fun setPlanningAgentStep(detail: String) {
        agentTaskSteps = listOf(
            AgentTaskStep(
                id = "agent_planning_step",
                index = 1,
                total = 1,
                title = "规划动作",
                status = AgentTaskStepStatus.RUNNING,
                detail = detail,
            )
        )
    }

    fun setSingleAgentStep(status: AgentTaskStepStatus, detail: String) {
        agentTaskSteps = listOf(
            AgentTaskStep(
                id = "agent_single_step",
                index = 1,
                total = 1,
                title = "单步执行",
                status = status,
                detail = detail,
            )
        )
    }

    fun updateAgentStepProgress(progress: AgentRunStepProgress) {
        val baseSteps = if (agentTaskSteps.size == progress.total) {
            agentTaskSteps
        } else {
            (1..progress.total).map { index ->
                AgentTaskStep(
                    id = "agent_step_$index",
                    index = index,
                    total = progress.total,
                    title = "等待执行",
                    status = AgentTaskStepStatus.PENDING,
                )
            }
        }
        agentTaskSteps = baseSteps.map { step ->
            if (step.index != progress.index) {
                step
            } else {
                step.copy(
                    title = AgentUserMessageFormatter.toolDisplayName(progress.toolCall.name),
                    status = progress.status.toUiStatus(),
                    detail = progress.toolResult?.let { result ->
                        if (result.success) {
                            result.message
                        } else {
                            AgentUserMessageFormatter.errorHint(
                                errorCode = result.errorCode,
                                toolName = progress.toolCall.name,
                                fallback = result.message,
                            )
                        }
                    }.orEmpty(),
                )
            }
        }
    }

    fun setAgentStepsFromRunResult(runResult: AgentRunResult) {
        if (runResult.stepResults.isEmpty()) {
            setSingleAgentStep(
                status = if (runResult.status == AgentRunStatus.SUCCESS) {
                    AgentTaskStepStatus.SUCCEEDED
                } else {
                    AgentTaskStepStatus.FAILED
                },
                detail = agentMessage,
            )
            return
        }
        agentTaskSteps = runResult.stepResults.map { step ->
            AgentTaskStep(
                id = "agent_step_${step.index}",
                index = step.index,
                total = step.total,
                title = AgentUserMessageFormatter.toolDisplayName(step.toolCall.name),
                status = if (step.toolResult.success) {
                    AgentTaskStepStatus.SUCCEEDED
                } else {
                    AgentTaskStepStatus.FAILED
                },
                detail = if (step.toolResult.success) {
                    step.toolResult.message
                } else {
                    AgentUserMessageFormatter.errorHint(
                        errorCode = step.toolResult.errorCode,
                        toolName = step.toolCall.name,
                        fallback = step.toolResult.message,
                    )
                },
            )
        }
    }

    fun updateRunningAgentStepDetail(detail: String) {
        agentTaskSteps = agentTaskSteps.map { step ->
            if (step.status == AgentTaskStepStatus.RUNNING) step.copy(detail = detail) else step
        }
    }

    fun agentContextHint(): String {
        val selected = vm.selectedObject
        return selected?.let { "选了 ${it.displayName.shortAgentHint()}" }.orEmpty()
    }

    fun toggleAgentPanel() {
        if (blockCloudAi()) return
        toolbarMoreOpen = false
        if (!agentPanelOpen) {
            if (vm.panel != StudioPanel.NONE) {
                vm.closePanel()
            }
            resetAgentConversation()
            agentPanelOpen = true
        } else {
            closeAgentPanel()
        }
    }

    fun defaultAgentRodinOptions(): RodinGenerationOptions =
        RodinGenerationOptions(
            tier = aigcTier,
            qualityOverride = aigcQualityOverride,
            meshMode = aigcMeshMode,
            hdTexture = aigcHdTexture,
            creative = aigcCreative,
        )

    fun agentRodinOptionsData(options: RodinGenerationOptions): Map<String, Any?> =
        AgentGenerationOptionParser.rodinOptionsData(options)

    suspend fun runAgentGenerationTool(
        call: DoubaoToolCall,
        conversationEpoch: Int,
    ): AgentToolResult? {
        fun updateVisibleAgentProgress(message: String) {
            if (agentConversationEpoch != conversationEpoch) return
            agentMessage = message
            updateRunningAgentStepDetail(message)
        }
        val isTextToModel = call.name == "generate_model_from_text"
        val isImageToModel = call.name == "generate_model_from_image"
        val isTextToImage = call.name == "generate_image_from_text"
        val isImageToImage = call.name == "generate_image_from_image"
        val isStrokeToImage = call.name == "generate_image_from_stroke"
        val isStrokeToModel = call.name == "generate_model_from_stroke"
        if (!isTextToModel && !isImageToModel && !isTextToImage && !isImageToImage && !isStrokeToImage && !isStrokeToModel) return null
        val prompt = (call.arguments["prompt"] as? String)?.trim().orEmpty()
        if ((isTextToModel || isTextToImage || isImageToImage) && prompt.isBlank()) {
            return AgentToolResult.failure(
                toolName = call.name,
                errorCode = "missing_parameter",
                message = if (isTextToModel) "缺少模型生成提示词。" else "缺少图片生成提示词。",
            )
        }
        val agentRodinOptions = if (isTextToModel || isImageToModel || isStrokeToModel) {
            AgentGenerationOptionParser.parseRodinOptions(
                arguments = call.arguments,
                defaults = defaultAgentRodinOptions(),
            ).getOrElse { error ->
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "invalid_generation_option",
                    message = error.message ?: "生成参数不支持。",
                )
            }
        } else {
            null
        }

        if (isTextToModel) {
            val key = rodinApiKey.trim()
            if (key.isBlank()) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "missing_rodin_key",
                    message = "Rodin Key 未配置，请先保存 Key。",
                )
            }
            if (aigcBusy) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "generation_in_progress",
                    message = "已有 AIGC 生成任务在执行，请稍后再试。",
                )
            }

            aigcStore.saveRodinApiKey(key)
            val now = System.currentTimeMillis()
            val record = AigcGenerationRecord(
                id = "aigc_record_${now}_${AigcGenerationRecordType.TEXT_TO_MODEL.name.lowercase()}",
                type = AigcGenerationRecordType.TEXT_TO_MODEL,
                title = "AI 助手文生 3D",
                status = AigcGenerationRecordStatus.RUNNING,
                stage = "Rodin 生成中",
                prompt = prompt,
                createdAt = now,
                updatedAt = now,
            )
            aigcGenerationRecords = aigcStore.upsertGenerationRecord(record)
            fun updateAgentModelRecord(
                status: AigcGenerationRecordStatus,
                stage: String,
                outputModelSource: String? = null,
                errorMessage: String? = null,
            ) {
                val existing = aigcGenerationRecords.firstOrNull { it.id == record.id }
                    ?: aigcStore.generationRecords().firstOrNull { it.id == record.id }
                    ?: record
                aigcGenerationRecords = aigcStore.upsertGenerationRecord(
                    existing.copy(
                        status = status,
                        stage = stage,
                        outputModelSource = outputModelSource ?: existing.outputModelSource,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
            aigcBusy = true
            updateVisibleAgentProgress("正在调用 Rodin 生成 3D 模型...")
            aigcMessage = "AI 助手已提交 Rodin 文生 3D，通常需要几十秒到数分钟。"
            val result = try {
                withContext(Dispatchers.IO) {
                    RodinAigcClient.generateTextToModel(
                        context = context.applicationContext,
                        apiKey = key,
                        prompt = prompt,
                        options = requireNotNull(agentRodinOptions),
                    )
                }
            } finally {
                aigcBusy = false
            }
            return result.fold(
                onSuccess = { asset ->
                    generatedAigcAssets = aigcStore.recordGeneratedAsset(asset)
                    updateAgentModelRecord(
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Rodin 生成完成",
                        outputModelSource = asset.source,
                    )
                    vm.addImportedAsset(asset)
                    selectedAssetCategory = PresetCategory.AIGC
                    val objectId = vm.selectedObjectId
                    if (objectId == null || !vm.currentPage.states.containsKey(objectId)) {
                        aigcMessage = "AI 助手模型生成完成：${asset.displayName}，但未能加入当前场景。"
                        AgentToolResult.failure(
                            toolName = call.name,
                            errorCode = "add_failed",
                            message = "模型已生成并保存，但未能加入当前场景。",
                            data = mapOf(
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                        )
                    } else {
                        aigcMessage = "AI 助手模型生成完成：${asset.displayName}，已保存并加入场景。"
                        AgentToolResult.success(
                            toolName = call.name,
                            message = "模型生成完成：${asset.displayName}",
                            data = mapOf(
                                "object_id" to objectId,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                        )
                    }
                },
                onFailure = { error ->
                    updateAgentModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Rodin 生成失败",
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    aigcMessage = "AI 助手模型生成失败：${error.message ?: error.javaClass.simpleName}"
                    AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "generation_failed",
                        message = "模型生成失败：${error.message ?: error.javaClass.simpleName}",
                    )
                },
            )
        }

        var sourceObjectId: String? = null
        var sourceName = ""
        var referenceSource: String? = null
        var sourceFile: File? = null
        var strokeData: StrokeData? = null
        var strokeFrontFace = StrokeFrontFace.POSITIVE_Z
        if (isImageToModel || isImageToImage || isStrokeToImage || isStrokeToModel) {
            val resolvedObjectId = call.arguments["object_id"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: vm.selectedObjectId
                ?: return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "no_current_selection",
                    message = if (isStrokeToImage || isStrokeToModel) "请先选中一段笔画，或提供笔画 object_id。" else "请先选中一张图片，或提供图片 object_id。",
                )
            val sourceObject = vm.document.objects[resolvedObjectId]
                ?: return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "object_not_found",
                    message = "目标对象不存在：$resolvedObjectId",
                )
            if (!vm.currentPage.states.containsKey(resolvedObjectId)) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "object_not_found",
                    message = "目标对象不在当前页面：$resolvedObjectId",
                )
            }
            val expectedType = if (isStrokeToImage || isStrokeToModel) ObjectType.STROKE else ObjectType.IMAGE
            if (sourceObject.type != expectedType) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "invalid_object_type",
                    message = if (isStrokeToImage || isStrokeToModel) "目标对象不是笔画。" else "目标对象不是图片。",
                )
            }
            if (sourceObject.isLocked) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "object_locked",
                    message = if (isStrokeToImage || isStrokeToModel) "目标笔画已锁定。" else "目标图片已锁定。",
                )
            }
            val busy = when {
                isImageToModel -> resolvedObjectId in imageToModelBusyObjectIds
                isImageToImage -> resolvedObjectId in imageToImageBusyObjectIds
                else -> resolvedObjectId in strokeToSeedreamBusyObjectIds ||
                    resolvedObjectId in strokeToModelBusyObjectIds
            }
            if (busy) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "generation_in_progress",
                    message = if (isStrokeToImage || isStrokeToModel) "这段笔画已有生成任务在执行。" else "这张图片已有生成任务在执行。",
                )
            }
            if (isImageToModel || isImageToImage) {
                val resolvedSource = vm.renderObject(resolvedObjectId)?.source ?: sourceObject.source
                val resolvedFile = File(resolvedSource.removePrefix("file://"))
                if (!resolvedFile.isFile || !resolvedFile.canRead()) {
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "source_unreadable",
                        message = "无法读取原图片：${sourceObject.displayName}",
                    )
                }
                referenceSource = resolvedSource
                sourceFile = resolvedFile
            } else {
                val resolvedStrokeData = sourceObject.strokeData?.copyData()
                if (resolvedStrokeData == null || resolvedStrokeData.pointCount < 2) {
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "invalid_stroke",
                        message = "笔画没有足够点数，无法生成图片。",
                    )
                }
                strokeData = resolvedStrokeData
                strokeFrontFace = strokeFrontFaceForViewer(
                    transform = vm.currentTransform(resolvedObjectId),
                    viewer = viewerPose.position,
                )
            }
            sourceObjectId = resolvedObjectId
            sourceName = sourceObject.displayName
        }

        if (isImageToModel) {
            val key = rodinApiKey.trim()
            if (key.isBlank()) {
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "missing_rodin_key",
                    message = "Rodin Key 未配置，请先保存 Key。",
                )
            }

            val objectId = requireNotNull(sourceObjectId)
            val preprocessMode = when (val raw = call.arguments["preprocess"]?.toString()?.trim()?.lowercase()) {
                null, "", "none" -> AigcImagePreprocessMode.NONE
                "local_subject" -> AigcImagePreprocessMode.LOCAL_SUBJECT
                else -> return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "invalid_preprocess",
                    message = "不支持的图片前处理方式：$raw",
                )
            }
            val activeObjectSource = vm.document.objects[objectId]?.source
            val committedPreprocessAlreadyMatches = preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT &&
                vm.currentImageVersionPreprocessMode(objectId) == AigcImagePreprocessMode.LOCAL_SUBJECT
            val previewAlreadyMatchesPreprocess = preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT &&
                vm.activeImageSubjectPreviewMode(objectId) == AigcImagePreprocessMode.LOCAL_SUBJECT &&
                referenceSource != null &&
                referenceSource != activeObjectSource
            aigcStore.saveRodinApiKey(key)
            val now = System.currentTimeMillis()
            val record = AigcGenerationRecord(
                id = "aigc_record_${now}_${AigcGenerationRecordType.IMAGE_TO_MODEL.name.lowercase()}",
                type = AigcGenerationRecordType.IMAGE_TO_MODEL,
                title = "AI 助手图生 3D",
                status = AigcGenerationRecordStatus.RUNNING,
                stage = if (preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT) "本地主体前处理中" else "Rodin 图生 3D 中",
                sourceName = sourceName,
                prompt = prompt,
                inputSources = referenceSource?.let { listOf(it) }.orEmpty(),
                createdAt = now,
                updatedAt = now,
            )
            aigcGenerationRecords = aigcStore.upsertGenerationRecord(record)
            fun updateAgentImageModelRecord(
                status: AigcGenerationRecordStatus,
                stage: String,
                outputImageSource: String? = null,
                outputModelSource: String? = null,
                errorMessage: String? = null,
            ) {
                val existing = aigcGenerationRecords.firstOrNull { it.id == record.id }
                    ?: aigcStore.generationRecords().firstOrNull { it.id == record.id }
                    ?: record
                aigcGenerationRecords = aigcStore.upsertGenerationRecord(
                    existing.copy(
                        status = status,
                        stage = stage,
                        outputImageSource = outputImageSource ?: existing.outputImageSource,
                        outputModelSource = outputModelSource ?: existing.outputModelSource,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }

            imageToModelBusyObjectIds = imageToModelBusyObjectIds + objectId
            updateVisibleAgentProgress("正在基于图片调用 Rodin 生成 3D 模型...")
            aigcMessage = if (preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT) {
                "AI 助手正在执行图片主体前处理，随后提交 Rodin 图生 3D。"
            } else {
                "AI 助手正在直接提交 Rodin 图生 3D。"
            }
            var recordFailureStage = if (preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT) {
                "本地主体前处理失败"
            } else {
                "Rodin 图生 3D 失败"
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val originalFile = requireNotNull(sourceFile)
                        var preprocessingModeForHistory: AigcImagePreprocessMode? = null
                        val prepared = if (committedPreprocessAlreadyMatches || previewAlreadyMatchesPreprocess) {
                            originalFile to "已复用当前本地抠图结果"
                        } else if (preprocessMode == AigcImagePreprocessMode.LOCAL_SUBJECT) {
                            SubjectCutoutProcessor.reusableTransparentSubjectImage(
                                context = context.applicationContext,
                                inputFile = originalFile,
                            )?.let { reusable ->
                                reusable to "已复用保存的透明主体图"
                            } ?: run {
                                val cutout = SubjectCutoutProcessor.createTransparentSubjectImage(
                                    context = context.applicationContext,
                                    inputFile = originalFile,
                                ).getOrElse { error ->
                                    throw IllegalStateException(
                                        "包内 U²-Net 主体切割失败：${error.message ?: error.javaClass.simpleName}",
                                        error,
                                    )
                                }
                                (if (cutout.usedPreprocessedImage) cutout.outputFile else originalFile) to cutout.summary
                            }
                        } else {
                            originalFile to null
                        }
                        val imageForRodin = prepared.first
                        val preprocessingMessage = prepared.second
                        val preprocessingImageSource = when {
                            previewAlreadyMatchesPreprocess -> referenceSource
                            !committedPreprocessAlreadyMatches && imageForRodin.absolutePath != originalFile.absolutePath ->
                                "file://${imageForRodin.absolutePath}"
                            else -> null
                        }
                        if (preprocessingImageSource != null) {
                            preprocessingModeForHistory = preprocessMode
                        }

                        recordFailureStage = "Rodin 图生 3D 失败"
                        AigcGenerationRunResult(
                            asset = RodinAigcClient.generateImageToModel(
                                context = context.applicationContext,
                                apiKey = key,
                                imageFiles = listOf(imageForRodin),
                                prompt = prompt.takeIf { it.isNotBlank() },
                                options = requireNotNull(agentRodinOptions),
                            ).getOrThrow(),
                            preprocessingMessage = preprocessingMessage,
                            preprocessingImageSource = preprocessingImageSource,
                            preprocessingMode = preprocessingModeForHistory,
                        )
                    }
                }
            } finally {
                imageToModelBusyObjectIds = imageToModelBusyObjectIds - objectId
            }
            return result.fold(
                onSuccess = { run ->
                    val asset = run.asset
                    val preprocessingAsset = run.preprocessingImageSource?.let { source ->
                        imagePreprocessAsset(
                            baseName = sourceName,
                            source = source,
                            mode = run.preprocessingMode ?: preprocessMode,
                        ).also { generatedAigcImages = aigcStore.recordGeneratedImage(it) }
                    }
                    generatedAigcAssets = aigcStore.recordGeneratedAsset(asset)
                    updateAgentImageModelRecord(
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Rodin 图生 3D 完成",
                        outputImageSource = run.preprocessingImageSource,
                        outputModelSource = asset.source,
                    )
                    selectedAssetCategory = PresetCategory.AIGC
                    val shouldSelectReplacement = vm.selectedObjectId == objectId
                    val preprocessing = run.preprocessingMessage?.let { "$it；" }.orEmpty()
                    if (
                        vm.replaceImageWithGeneratedModel(
                            objectId = objectId,
                            asset = asset,
                            selectReplacedObject = shouldSelectReplacement,
                            preprocessingAsset = preprocessingAsset,
                            preprocessingMode = run.preprocessingMode,
                            recordId = record.id,
                            prompt = prompt,
                            inputSources = referenceSource?.let { listOf(it) }.orEmpty(),
                        )
                    ) {
                        imageToModelMessage = "${preprocessing}AI 助手图生 3D 完成，已替换原图片。"
                        aigcMessage = "${preprocessing}AI 助手图生 3D 完成：${asset.displayName}，已替换原图片。"
                        AgentToolResult.success(
                            toolName = call.name,
                            message = "图生 3D 完成：${asset.displayName}",
                            data = mapOf(
                                "object_id" to objectId,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                                "preprocess" to preprocessMode.name.lowercase(),
                                "preprocess_source" to run.preprocessingImageSource,
                            ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                        )
                    } else {
                        imageToModelMessage = "${preprocessing}AI 助手图生 3D 完成，但原图片已不可替换。"
                        aigcMessage = "${preprocessing}AI 助手图生 3D 完成：${asset.displayName}，模型已保存到本地资源库。"
                        AgentToolResult.failure(
                            toolName = call.name,
                            errorCode = "replace_failed",
                            message = "模型已生成并保存，但未能替换原图片。",
                            data = mapOf(
                                "object_id" to objectId,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                                "preprocess" to preprocessMode.name.lowercase(),
                                "preprocess_source" to run.preprocessingImageSource,
                            ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                        )
                    }
                },
                onFailure = { error ->
                    updateAgentImageModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = recordFailureStage,
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    imageToModelMessage = "AI 助手图生 3D 失败：${error.message ?: error.javaClass.simpleName}。原图片未改变。"
                    aigcMessage = imageToModelMessage
                    AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = if (recordFailureStage.contains("前处理")) "preprocess_failed" else "generation_failed",
                        message = "图生 3D 失败：${error.message ?: error.javaClass.simpleName}",
                    )
                },
            )
        }

        if (isStrokeToModel) {
            val seedreamKey = imageToImageApiKey.trim()
            val rodinKey = rodinApiKey.trim()
            if (seedreamKey.isBlank() || rodinKey.isBlank()) {
                val errorCode = when {
                    seedreamKey.isBlank() && rodinKey.isBlank() -> "missing_seedream_rodin_key"
                    seedreamKey.isBlank() -> "missing_seedream_key"
                    else -> "missing_rodin_key"
                }
                return AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = errorCode,
                    message = when (errorCode) {
                        "missing_seedream_rodin_key" -> "Seedream / Rodin Key 未配置，请先保存 Key。"
                        "missing_seedream_key" -> "Seedream Key 未配置，请先保存 Key。"
                        else -> "Rodin Key 未配置，请先保存 Key。"
                    },
                )
            }

            val objectId = requireNotNull(sourceObjectId)
            val strokeExtraPrompt = AgentGenerationOptionParser.strokeExtraPrompt(prompt)
            val seedreamExtraPrompt = buildAigcPromptDetails(
                useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
                template = null,
                userPrompt = strokeExtraPrompt,
            )
            val seedreamPrompt = SeedreamImageClient.strokeMainViewPrompt(seedreamExtraPrompt)
            val rodinPrompt = buildAigcPrompt(
                useCase = AigcPromptUseCase.STROKE_TO_MODEL,
                template = null,
                userPrompt = strokeExtraPrompt,
            )
            val historyPrompt = "Seedream 2D prompt：\n$seedreamPrompt\n\nRodin 3D prompt：\n$rodinPrompt"
            aigcStore.saveImageToImageApiKey(seedreamKey)
            aigcStore.saveRodinApiKey(rodinKey)
            val now = System.currentTimeMillis()
            val record = AigcGenerationRecord(
                id = "aigc_record_${now}_${AigcGenerationRecordType.STROKE_TO_MODEL.name.lowercase()}",
                type = AigcGenerationRecordType.STROKE_TO_MODEL,
                title = "AI 助手笔画生成 3D",
                status = AigcGenerationRecordStatus.RUNNING,
                stage = "主视图参考图生成中",
                sourceName = sourceName,
                prompt = historyPrompt,
                createdAt = now,
                updatedAt = now,
            )
            aigcGenerationRecords = aigcStore.upsertGenerationRecord(record)
            fun updateAgentStrokeModelRecord(
                status: AigcGenerationRecordStatus,
                stage: String,
                inputSources: List<String>? = null,
                outputImageSource: String? = null,
                outputModelSource: String? = null,
                errorMessage: String? = null,
            ) {
                val existing = aigcGenerationRecords.firstOrNull { it.id == record.id }
                    ?: aigcStore.generationRecords().firstOrNull { it.id == record.id }
                    ?: record
                aigcGenerationRecords = aigcStore.upsertGenerationRecord(
                    existing.copy(
                        status = status,
                        stage = stage,
                        inputSources = inputSources?.filter { it.isNotBlank() } ?: existing.inputSources,
                        outputImageSource = outputImageSource ?: existing.outputImageSource,
                        outputModelSource = outputModelSource ?: existing.outputModelSource,
                        errorMessage = errorMessage,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }

            strokeToModelBusyObjectIds = strokeToModelBusyObjectIds + objectId
            updateVisibleAgentProgress("正在把笔画生成 3D 模型...")
            aigcMessage = "AI 助手正在生成主视图参考图，随后调用 Seedream 和 Rodin。"
            try {
                val lineArtAsset = withContext(Dispatchers.IO) {
                    StrokeImageRenderer.renderMainViewReference(
                        context = context.applicationContext,
                        strokeData = requireNotNull(strokeData),
                        displayName = sourceName.ifBlank { "笔画" },
                        frontFace = strokeFrontFace,
                    )
                }.getOrElse { error ->
                    updateAgentStrokeModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "主视图参考图生成失败",
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    val message = "笔画生成 3D 失败：主视图参考图生成失败，${error.message ?: error.javaClass.simpleName}"
                    strokeToModelMessage = message
                    aigcMessage = message
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "lineart_failed",
                        message = message,
                    )
                }
                generatedAigcImages = aigcStore.recordGeneratedImage(lineArtAsset)
                updateAgentStrokeModelRecord(
                    status = AigcGenerationRecordStatus.RUNNING,
                    stage = "Seedream 成图中",
                    inputSources = listOf(lineArtAsset.source),
                )

                val lineArtFile = File(lineArtAsset.source.removePrefix("file://"))
                if (!lineArtFile.isFile || !lineArtFile.canRead()) {
                    val message = "无法读取主视图参考图：${lineArtFile.name}"
                    updateAgentStrokeModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Seedream 成图前读取线稿失败",
                        errorMessage = message,
                    )
                    strokeToModelMessage = "笔画生成 3D 失败：$message"
                    aigcMessage = strokeToModelMessage
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "lineart_unreadable",
                        message = message,
                    )
                }

                val seedreamAsset = withContext(Dispatchers.IO) {
                    SeedreamImageClient.generateImageToImage(
                        context = context.applicationContext,
                        apiKey = seedreamKey,
                        imageFiles = listOf(lineArtFile),
                        prompt = seedreamPrompt,
                    )
                }.map { asset ->
                    asset.copy(displayName = "${sourceName.ifBlank { "笔画" }.take(14)}AI成图")
                }.getOrElse { error ->
                    updateAgentStrokeModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Seedream 成图失败",
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    val message = "笔画生成 3D 失败：Seedream 成图失败，${error.message ?: error.javaClass.simpleName}"
                    strokeToModelMessage = message
                    aigcMessage = message
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "seedream_failed",
                        message = message,
                    )
                }
                generatedAigcImages = aigcStore.recordGeneratedImage(seedreamAsset)
                updateAgentStrokeModelRecord(
                    status = AigcGenerationRecordStatus.RUNNING,
                    stage = "Rodin 图生 3D 中",
                    outputImageSource = seedreamAsset.source,
                )

                val seedreamFile = File(seedreamAsset.source.removePrefix("file://"))
                if (!seedreamFile.isFile || !seedreamFile.canRead()) {
                    val message = "无法读取 Seedream 生成图：${seedreamFile.name}"
                    updateAgentStrokeModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Rodin 生成前读取 Seedream 图失败",
                        errorMessage = message,
                    )
                    strokeToModelMessage = "笔画生成 3D 失败：$message"
                    aigcMessage = strokeToModelMessage
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "seedream_unreadable",
                        message = message,
                    )
                }

                val modelAsset = withContext(Dispatchers.IO) {
                    RodinAigcClient.generateImageToModel(
                        context = context.applicationContext,
                        apiKey = rodinKey,
                        imageFiles = listOf(seedreamFile),
                        prompt = rodinPrompt.takeIf { it.isNotBlank() },
                        options = requireNotNull(agentRodinOptions),
                    )
                }.map { asset ->
                    asset.copy(displayName = "${sourceName.ifBlank { "笔画" }.take(14)}AI模型")
                }.getOrElse { error ->
                    updateAgentStrokeModelRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Rodin 图生 3D 失败",
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    val message = "笔画生成 3D 失败：Rodin 图生 3D 失败，${error.message ?: error.javaClass.simpleName}"
                    strokeToModelMessage = message
                    aigcMessage = message
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "generation_failed",
                        message = message,
                    )
                }
                generatedAigcAssets = aigcStore.recordGeneratedAsset(modelAsset)
                updateAgentStrokeModelRecord(
                    status = AigcGenerationRecordStatus.SUCCEEDED,
                    stage = "Rodin 图生 3D 完成",
                    outputImageSource = seedreamAsset.source,
                    outputModelSource = modelAsset.source,
                )
                selectedAssetCategory = PresetCategory.AIGC
                return if (
                    vm.addModelGeneratedFromStrokeImage(
                        sourceObjectId = objectId,
                        imageAsset = seedreamAsset,
                        modelAsset = modelAsset,
                        selectNewObject = true,
                        recordId = record.id,
                        prompt = historyPrompt,
                        inputSources = listOf(lineArtAsset.source, seedreamAsset.source),
                    )
                ) {
                    strokeToModelMessage = "笔画生成 3D 完成，已替代原画笔物体，可在版本管理查看 Seedream 2D 图。"
                    aigcMessage = "AI 助手笔画生成 3D 完成：${modelAsset.displayName}，已替代原画笔物体。"
                    AgentToolResult.success(
                        toolName = call.name,
                        message = "笔画生成 3D 完成：${modelAsset.displayName}",
                        data = mapOf(
                            "object_id" to objectId,
                            "line_art_source" to lineArtAsset.source,
                            "seedream_image_source" to seedreamAsset.source,
                            "display_name" to modelAsset.displayName,
                            "source" to modelAsset.source,
                        ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                    )
                } else {
                    strokeToModelMessage = "笔画生成 3D 完成但原笔画已不可用，模型已保存到本地资源库。"
                    aigcMessage = strokeToModelMessage
                    AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "replace_failed",
                        message = "模型已生成并保存，但未能替换原笔画。",
                        data = mapOf(
                            "object_id" to objectId,
                            "line_art_source" to lineArtAsset.source,
                            "seedream_image_source" to seedreamAsset.source,
                            "display_name" to modelAsset.displayName,
                            "source" to modelAsset.source,
                        ) + agentRodinOptionsData(requireNotNull(agentRodinOptions)),
                    )
                }
            } finally {
                strokeToModelBusyObjectIds = strokeToModelBusyObjectIds - objectId
            }
        }

        val key = imageToImageApiKey.trim()
        if (key.isBlank()) {
            return AgentToolResult.failure(
                toolName = call.name,
                errorCode = "missing_seedream_key",
                message = "Seedream Key 未配置，请先保存 Key。",
            )
        }

        aigcStore.saveImageToImageApiKey(key)
        val now = System.currentTimeMillis()
        val seedreamPrompt = if (isStrokeToImage) {
            SeedreamImageClient.strokeMainViewPrompt(
                buildAigcPromptDetails(
                    useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
                    template = null,
                    userPrompt = AgentGenerationOptionParser.strokeExtraPrompt(prompt),
                )
            )
        } else {
            prompt
        }
        val recordType = when {
            isImageToImage -> AigcGenerationRecordType.IMAGE_TO_IMAGE
            isStrokeToImage -> AigcGenerationRecordType.STROKE_TO_IMAGE
            else -> AigcGenerationRecordType.TEXT_TO_IMAGE
        }
        val record = AigcGenerationRecord(
            id = "aigc_record_${now}_${recordType.name.lowercase()}",
            type = recordType,
            title = when {
                isImageToImage -> "AI 助手图生图"
                isStrokeToImage -> "AI 助手笔画成图"
                else -> "AI 助手文生图"
            },
            status = AigcGenerationRecordStatus.RUNNING,
            stage = when {
                isImageToImage -> "Seedream 图生图中"
                isStrokeToImage -> "主视图参考图生成中"
                else -> "Seedream 文生图中"
            },
            sourceName = sourceName,
            prompt = seedreamPrompt,
            inputSources = referenceSource?.let { listOf(it) }.orEmpty(),
            createdAt = now,
            updatedAt = now,
        )
        aigcGenerationRecords = aigcStore.upsertGenerationRecord(record)
        fun updateAgentRecord(
            status: AigcGenerationRecordStatus,
            stage: String,
            inputSources: List<String>? = null,
            outputImageSource: String? = null,
            errorMessage: String? = null,
        ) {
            val existing = aigcGenerationRecords.firstOrNull { it.id == record.id }
                ?: aigcStore.generationRecords().firstOrNull { it.id == record.id }
                ?: record
            aigcGenerationRecords = aigcStore.upsertGenerationRecord(
                existing.copy(
                    status = status,
                    stage = stage,
                    inputSources = inputSources?.filter { it.isNotBlank() } ?: existing.inputSources,
                    outputImageSource = outputImageSource ?: existing.outputImageSource,
                    errorMessage = errorMessage,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }

        when {
            isImageToImage -> imageToImageBusyObjectIds = imageToImageBusyObjectIds + requireNotNull(sourceObjectId)
            isStrokeToImage -> strokeToSeedreamBusyObjectIds = strokeToSeedreamBusyObjectIds + requireNotNull(sourceObjectId)
        }
        val visibleProgressMessage = when {
            isImageToImage -> "正在调用 Seedream 基于图片生成新图..."
            isStrokeToImage -> "正在生成主视图参考图并调用 Seedream..."
            else -> "正在调用 Seedream 生成图片..."
        }
        updateVisibleAgentProgress(visibleProgressMessage)
        aigcMessage = when {
            isImageToImage -> "AI 助手正在调用 Seedream 图生图。"
            isStrokeToImage -> "AI 助手正在生成主视图参考图并调用 Seedream。"
            else -> "AI 助手正在调用 Seedream 文生图。"
        }
        when {
            isImageToImage -> imageToImageMessage = "AI 助手正在调用 Seedream 基于当前图片生成新图。"
            isStrokeToImage -> strokeToSeedreamMessage = "AI 助手正在生成主视图参考图并调用 Seedream。"
        }
        val seedreamResult = try {
            if (isStrokeToImage) {
                val lineArtAsset = withContext(Dispatchers.IO) {
                    StrokeImageRenderer.renderMainViewReference(
                        context = context.applicationContext,
                        strokeData = requireNotNull(strokeData),
                        displayName = sourceName.ifBlank { "笔画" },
                        frontFace = strokeFrontFace,
                    )
                }.getOrElse { error ->
                    updateAgentRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "主视图参考图生成失败",
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    )
                    val message = "笔画 AI 成图失败：主视图参考图生成失败，${error.message ?: error.javaClass.simpleName}"
                    strokeToSeedreamMessage = message
                    aigcMessage = message
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "lineart_failed",
                        message = message,
                    )
                }
                generatedAigcImages = aigcStore.recordGeneratedImage(lineArtAsset)
                referenceSource = lineArtAsset.source
                updateAgentRecord(
                    status = AigcGenerationRecordStatus.RUNNING,
                    stage = "Seedream 成图中",
                    inputSources = listOf(lineArtAsset.source),
                )
                val lineArtFile = File(lineArtAsset.source.removePrefix("file://"))
                if (!lineArtFile.isFile || !lineArtFile.canRead()) {
                    val message = "无法读取主视图参考图：${lineArtFile.name}"
                    updateAgentRecord(
                        status = AigcGenerationRecordStatus.FAILED,
                        stage = "Seedream 成图前读取线稿失败",
                        errorMessage = message,
                    )
                    strokeToSeedreamMessage = "笔画 AI 成图失败：$message"
                    aigcMessage = strokeToSeedreamMessage
                    return AgentToolResult.failure(
                        toolName = call.name,
                        errorCode = "lineart_unreadable",
                        message = message,
                    )
                }
                withContext(Dispatchers.IO) {
                    SeedreamImageClient.generateImageToImage(
                        context = context.applicationContext,
                        apiKey = key,
                        imageFiles = listOf(lineArtFile),
                        prompt = seedreamPrompt,
                    )
                }.map { asset ->
                    asset.copy(displayName = "${sourceName.ifBlank { "笔画" }.take(14)}AI成图")
                }
            } else if (isImageToImage) {
                withContext(Dispatchers.IO) {
                    SeedreamImageClient.generateImageToImage(
                        context = context.applicationContext,
                        apiKey = key,
                        imageFiles = listOf(requireNotNull(sourceFile)),
                        prompt = seedreamPrompt,
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    SeedreamImageClient.generateTextToImage(
                        context = context.applicationContext,
                        apiKey = key,
                        prompt = seedreamPrompt,
                    )
                }
            }
        } finally {
            when {
                isImageToImage -> imageToImageBusyObjectIds = imageToImageBusyObjectIds - requireNotNull(sourceObjectId)
                isStrokeToImage -> strokeToSeedreamBusyObjectIds = strokeToSeedreamBusyObjectIds - requireNotNull(sourceObjectId)
            }
        }
        return seedreamResult.fold(
            onSuccess = { asset ->
                generatedAigcImages = aigcStore.recordGeneratedImage(asset)
                updateAgentRecord(
                    status = AigcGenerationRecordStatus.SUCCEEDED,
                    stage = when {
                        isImageToImage -> "Seedream 图生图完成"
                        isStrokeToImage -> "Seedream 成图完成"
                        else -> "Seedream 文生图完成"
                    },
                    outputImageSource = asset.source,
                )
                selectedAssetCategory = PresetCategory.AIGC
                if (isImageToImage) {
                    val added = vm.addImageGeneratedFromImageObject(
                        sourceObjectId = requireNotNull(sourceObjectId),
                        asset = asset,
                        selectNewObject = true,
                        recordId = record.id,
                        prompt = seedreamPrompt,
                        inputSources = referenceSource?.let { listOf(it) }.orEmpty(),
                    )
                    if (!added) {
                        imageToImageMessage = "图片生成完成但原图片已不可用，已保存到本地图片库。"
                        aigcMessage = "AI 助手图片生成完成：${asset.displayName}，但未能替换当前图片。"
                        AgentToolResult.failure(
                            toolName = call.name,
                            errorCode = "add_failed",
                            message = "图片已生成并保存，但未能替换当前图片。",
                            data = mapOf(
                                "source_object_id" to sourceObjectId,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ),
                        )
                    } else {
                        imageToImageMessage = "图片生成完成，已替换为当前图片新版本。"
                        aigcMessage = "AI 助手图片生成完成：${asset.displayName}，已保存并替换当前图片。"
                        AgentToolResult.success(
                            toolName = call.name,
                            message = "图片生成完成：${asset.displayName}",
                            data = mapOf(
                                "source_object_id" to sourceObjectId,
                                "object_id" to vm.selectedObjectId,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ),
                        )
                    }
                } else if (isStrokeToImage) {
                    val added = vm.addImageGeneratedFromStrokeObject(
                        sourceObjectId = requireNotNull(sourceObjectId),
                        asset = asset,
                        selectNewObject = true,
                        recordId = record.id,
                        prompt = seedreamPrompt,
                        inputSources = referenceSource?.let { listOf(it) }.orEmpty(),
                    )
                    if (!added) {
                        strokeToSeedreamMessage = "Seedream 图片生成完成但原笔画已不可用，已保存到本地图片库。"
                        aigcMessage = "AI 助手笔画成图完成：${asset.displayName}，但未能替换当前笔画。"
                        AgentToolResult.failure(
                            toolName = call.name,
                            errorCode = "add_failed",
                            message = "图片已生成并保存，但未能替换当前笔画。",
                            data = mapOf(
                                "source_object_id" to sourceObjectId,
                                "line_art_source" to referenceSource,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ),
                        )
                    } else {
                        strokeToSeedreamMessage = "Seedream 图片生成完成，已替换为当前笔画的新图片版本。"
                        aigcMessage = "AI 助手笔画成图完成：${asset.displayName}，已保存并替换当前笔画。"
                        AgentToolResult.success(
                            toolName = call.name,
                            message = "笔画成图完成：${asset.displayName}",
                            data = mapOf(
                                "source_object_id" to sourceObjectId,
                                "object_id" to vm.selectedObjectId,
                                "line_art_source" to referenceSource,
                                "display_name" to asset.displayName,
                                "source" to asset.source,
                            ),
                        )
                    }
                } else {
                    vm.addImportedAsset(asset)
                    aigcMessage = "AI 助手图片生成完成：${asset.displayName}，已保存并加入场景。"
                    AgentToolResult.success(
                        toolName = call.name,
                        message = "图片生成完成：${asset.displayName}",
                        data = mapOf(
                            "object_id" to vm.selectedObjectId,
                            "display_name" to asset.displayName,
                            "source" to asset.source,
                        ),
                    )
                }
            },
            onFailure = { error ->
                updateAgentRecord(
                    status = AigcGenerationRecordStatus.FAILED,
                    stage = when {
                        isImageToImage -> "Seedream 图生图失败"
                        isStrokeToImage -> "Seedream 成图失败"
                        else -> "Seedream 文生图失败"
                    },
                    errorMessage = error.message ?: error.javaClass.simpleName,
                )
                aigcMessage = "AI 助手图片生成失败：${error.message ?: error.javaClass.simpleName}"
                if (isImageToImage) {
                    imageToImageMessage = "图片生成失败：${error.message ?: error.javaClass.simpleName}"
                } else if (isStrokeToImage) {
                    strokeToSeedreamMessage = "笔画 AI 成图失败：Seedream 成图失败，${error.message ?: error.javaClass.simpleName}"
                }
                AgentToolResult.failure(
                    toolName = call.name,
                    errorCode = "generation_failed",
                    message = "图片生成失败：${error.message ?: error.javaClass.simpleName}",
                )
            },
        )
    }

    fun runAgentPrompt(
        inputOverride: String? = null,
        resolvedPrompt: String? = null,
        recordUserMessage: Boolean = true,
    ) {
        if (blockCloudAi()) return
        val key = doubaoApiKey.trim()
        val prompt = (resolvedPrompt ?: inputOverride ?: agentPrompt).trim()
        Log.w(
            AGENT_TAG,
            "runAgentPrompt enter keyConfigured=${key.isNotBlank()} keyLength=${key.length} " +
                "promptLength=${prompt.length} busy=$agentBusy resolved=${resolvedPrompt != null}",
        )
        if (key.isBlank()) {
            agentMessage = "豆包 Key 未配置，请先保存 Key。"
            agentMessageTone = AgentMessageTone.WARNING
            appendAgentMessage(
                role = AgentMessageRole.SYSTEM,
                text = agentMessage,
                tone = AgentMessageTone.WARNING,
            )
            openAgentKeyConfig()
            Log.w(AGENT_TAG, "runAgentPrompt blocked: missing Doubao key")
            return
        }
        if (prompt.isBlank()) {
            agentMessage = "请先输入一句要执行的编辑指令。"
            agentMessageTone = AgentMessageTone.WARNING
            appendAgentMessage(
                role = AgentMessageRole.SYSTEM,
                text = agentMessage,
                tone = AgentMessageTone.WARNING,
            )
            Log.w(AGENT_TAG, "runAgentPrompt blocked: blank prompt")
            return
        }
        if (agentBusy) {
            Log.w(AGENT_TAG, "runAgentPrompt blocked: already busy")
            return
        }

        pendingAgentSuggestions = emptyList()
        agentDefaultSuggestionsVisible = false
        if (resolvedPrompt == null) {
            pendingAgentClarification?.let { clarification ->
                val answer = prompt
                val clarifiedPrompt = buildClarifiedAgentPrompt(
                    originalPrompt = clarification.originalPrompt,
                    question = clarification.question,
                    answer = answer,
                )
                pendingAgentClarification = null
                agentPrompt = ""
                appendAgentMessage(AgentMessageRole.USER, answer)
                appendAgentMessage(
                    role = AgentMessageRole.SYSTEM,
                    text = "已把回答补充到上一条追问，继续让 AI 规划。",
                    tone = AgentMessageTone.NEUTRAL,
                )
                runAgentPrompt(
                    resolvedPrompt = clarifiedPrompt,
                    recordUserMessage = false,
                )
                return
            }
        }

        pendingAgentClarification = null
        aigcStore.saveDoubaoApiKey(key)
        val conversationHistory = agentMessages.mapNotNull { message ->
            val role = when (message.role) {
                AgentMessageRole.USER -> DoubaoConversationRole.USER
                AgentMessageRole.ASSISTANT -> DoubaoConversationRole.ASSISTANT
                AgentMessageRole.SYSTEM -> null
            }
            role?.let { DoubaoConversationMessage(it, message.text) }
        }
        agentBusy = true
        agentMessage = "AI 正在判断可执行工具..."
        agentMessageTone = AgentMessageTone.NEUTRAL
        if (recordUserMessage) {
            appendAgentMessage(AgentMessageRole.USER, prompt)
        }
        agentPrompt = ""
        setPlanningAgentStep("AI 正在规划可执行动作...")
        val runConversationEpoch = agentConversationEpoch
        scope.launch {
            val result = runCatching {
                Log.w(AGENT_TAG, "Agent decision request start promptLength=${prompt.length}")
                AgentRunner(
                    decisionClient = DoubaoAgentClient(
                        apiKey = key,
                        conversationHistory = conversationHistory,
                    ),
                    toolExecutor = ToolExecutor(vm),
                    asyncToolHandler = { call -> runAgentGenerationTool(call, runConversationEpoch) },
                    stepProgressListener = { progress ->
                        if (agentConversationEpoch == runConversationEpoch) {
                            updateAgentStepProgress(progress)
                        }
                    },
                ).runOnceAsync(prompt)
            }
            try {
                if (agentConversationEpoch == runConversationEpoch) {
                    result.onSuccess { runResult ->
                        Log.w(
                            AGENT_TAG,
                            "Agent decision finished status=${runResult.status} " +
                                "tool=${runResult.toolCall?.name.orEmpty()} steps=${runResult.stepResults.size} " +
                                "toolSuccess=${runResult.toolResult?.success}",
                        )
                        if (runResult.toolResult?.errorCode == "missing_seedream_rodin_key") {
                            openAigcKeyConfig(
                                requireRodin = true,
                                requireImage = true,
                                title = "配置 Seedream / Rodin Key",
                                message = "AI 助手笔画生成 3D 需要 Seedream Key 和 Rodin Key。Key 仅保存在本机，不会写入代码。",
                            )
                        }
                        if (runResult.toolResult?.errorCode == "missing_seedream_key") {
                            openAigcKeyConfig(
                                requireImage = true,
                                title = "配置 Seedream Key",
                                message = "AI 助手图片生成需要 Seedream Key。Key 仅保存在本机，不会写入代码。",
                            )
                        }
                        if (runResult.toolResult?.errorCode == "missing_rodin_key") {
                            openAigcKeyConfig(
                                requireRodin = true,
                                title = "配置 Rodin Key",
                                message = "AI 助手生成 3D 模型需要 Rodin Key。Key 仅保存在本机，不会写入代码。",
                            )
                        }
                        val clarificationQuestion = runResult.llmClarificationQuestion()
                        if (clarificationQuestion != null) {
                            pendingAgentClarification = PendingAgentClarification(
                                originalPrompt = prompt,
                                question = clarificationQuestion,
                            )
                            agentTaskSteps = emptyList()
                            agentMessage = clarificationQuestion
                            agentMessageTone = AgentMessageTone.WARNING
                            appendAgentMessage(
                                role = AgentMessageRole.ASSISTANT,
                                text = "$clarificationQuestion 可以直接在输入框回答。",
                                tone = AgentMessageTone.WARNING,
                            )
                        } else {
                            agentMessageTone = when (runResult.status) {
                                AgentRunStatus.SUCCESS -> AgentMessageTone.SUCCESS
                                AgentRunStatus.NO_TOOL,
                                AgentRunStatus.MULTIPLE_TOOLS,
                                -> AgentMessageTone.WARNING
                                AgentRunStatus.TOOL_FAILED,
                                AgentRunStatus.MODEL_ERROR,
                                -> AgentMessageTone.ERROR
                            }
                            val assistantReply = AgentUserMessageFormatter.format(runResult)
                            Log.w(
                                AGENT_TAG,
                                "Agent run status=${runResult.status} tool=${runResult.toolCall?.name.orEmpty()} " +
                                    "success=${runResult.toolResult?.success} message=${assistantReply.take(220)}",
                            )
                            agentMessage = assistantReply
                            setAgentStepsFromRunResult(runResult)
                            appendAgentMessage(
                                role = AgentMessageRole.ASSISTANT,
                                text = assistantReply,
                                tone = agentMessageTone,
                            )
                            pendingAgentSuggestions = runResult.suggestions
                        }
                    }.onFailure { error ->
                        val assistantReply = "AI 执行失败：${error.message ?: error.javaClass.simpleName}"
                        Log.e(AGENT_TAG, "Agent run crashed: ${assistantReply.take(220)}", error)
                        agentMessage = assistantReply
                        agentMessageTone = AgentMessageTone.ERROR
                        setSingleAgentStep(AgentTaskStepStatus.FAILED, assistantReply)
                        appendAgentMessage(
                            role = AgentMessageRole.ASSISTANT,
                            text = assistantReply,
                            tone = AgentMessageTone.ERROR,
                        )
                    }
                } else {
                    Log.w(
                        AGENT_TAG,
                        "Agent run finished after conversation reset; keeping the new conversation empty",
                    )
                }
            } catch (error: Throwable) {
                Log.e(AGENT_TAG, "Agent result handling failed", error)
                if (agentConversationEpoch == runConversationEpoch) {
                    agentMessage = "AI 结果处理失败，请重试。"
                    agentMessageTone = AgentMessageTone.ERROR
                    setSingleAgentStep(AgentTaskStepStatus.FAILED, agentMessage)
                }
            } finally {
                agentBusy = false
            }
        }
    }

    fun startAigcRecord(
        type: AigcGenerationRecordType,
        title: String,
        stage: String,
        sourceName: String = "",
        prompt: String = "",
        inputSources: List<String> = emptyList(),
    ): String {
        val now = System.currentTimeMillis()
        val record = AigcGenerationRecord(
            id = "aigc_record_${now}_${type.name.lowercase()}",
            type = type,
            title = title.take(40),
            status = AigcGenerationRecordStatus.RUNNING,
            stage = stage,
            sourceName = sourceName.take(40),
            prompt = prompt,
            inputSources = inputSources.filter { it.isNotBlank() },
            createdAt = now,
            updatedAt = now,
        )
        aigcGenerationRecords = aigcStore.upsertGenerationRecord(record)
        return record.id
    }

    fun updateAigcRecord(
        recordId: String?,
        status: AigcGenerationRecordStatus? = null,
        stage: String? = null,
        inputSources: List<String>? = null,
        outputImageSource: String? = null,
        outputModelSource: String? = null,
        errorMessage: String? = null,
    ) {
        if (recordId == null) return
        val existing = aigcGenerationRecords.firstOrNull { it.id == recordId }
            ?: aigcStore.generationRecords().firstOrNull { it.id == recordId }
            ?: return
        val next = existing.copy(
            status = status ?: existing.status,
            stage = stage ?: existing.stage,
            inputSources = inputSources?.filter { it.isNotBlank() } ?: existing.inputSources,
            outputImageSource = outputImageSource ?: existing.outputImageSource,
            outputModelSource = outputModelSource ?: existing.outputModelSource,
            errorMessage = errorMessage,
            updatedAt = System.currentTimeMillis(),
        )
        aigcGenerationRecords = aigcStore.upsertGenerationRecord(next)
    }

    fun failAigcRecord(recordId: String?, stage: String, error: Throwable) {
        updateAigcRecord(
            recordId = recordId,
            status = AigcGenerationRecordStatus.FAILED,
            stage = stage,
            errorMessage = error.message ?: error.javaClass.simpleName,
        )
    }

    fun templatedPrompt(
        template: AigcPromptTemplate?,
        useCase: AigcPromptUseCase,
        userPrompt: String,
        sourceText: String? = null,
    ): String =
        buildAigcPrompt(
            useCase = useCase,
            template = template,
            userPrompt = userPrompt,
            sourceText = sourceText,
        )

    fun promptDetails(
        template: AigcPromptTemplate?,
        useCase: AigcPromptUseCase,
        userPrompt: String,
    ): String =
        buildAigcPromptDetails(
            useCase = useCase,
            template = template,
            userPrompt = userPrompt,
        )

    fun generateAigcImage() {
        if (blockCloudAi()) return
        val key = imageToImageApiKey.trim()
        val prompt = if (aigcUseImageReference) {
            templatedPrompt(null, AigcPromptUseCase.IMAGE_TO_IMAGE, aigcPrompt)
        } else {
            templatedPrompt(aigcPromptTemplate, AigcPromptUseCase.TEXT_TO_IMAGE, aigcPrompt)
        }
        if (key.isBlank()) {
            aigcMessage = "Seedream 图生图 Key 未配置，请先在弹窗或 文件 > AI Key 配置 中保存。"
            openAigcKeyConfig(
                requireImage = true,
                title = "配置 Seedream Key",
                message = "文生图 / 图生图需要 Seedream 图生图 Key。Key 仅保存在本机。",
            )
            return
        }
        val referenceImage = aigcReferenceImagePath?.let(::File)
        if (aigcUseImageReference && (referenceImage == null || !referenceImage.isFile || !referenceImage.canRead())) {
            aigcMessage = "请先选择一张参考图片。"
            return
        }
        if (aigcBusy) return
        aigcStore.saveImageToImageApiKey(key)
        val recordId = startAigcRecord(
            type = if (aigcUseImageReference) AigcGenerationRecordType.IMAGE_TO_IMAGE else AigcGenerationRecordType.TEXT_TO_IMAGE,
            title = if (aigcUseImageReference) "资源图生图" else "资源文生图",
            stage = "Seedream 图片生成中",
            sourceName = aigcReferenceImageName.orEmpty(),
            prompt = prompt,
            inputSources = referenceImage?.let { listOf("file://${it.absolutePath}") }.orEmpty(),
        )
        aigcBusy = true
        aigcMessage = "已提交 Seedream 图片生成请求，成功后会直接加入场景。"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (aigcUseImageReference) {
                    SeedreamImageClient.generateImageToImage(
                        context = context.applicationContext,
                        apiKey = key,
                        imageFiles = listOf(requireNotNull(referenceImage)),
                        prompt = prompt,
                    )
                } else {
                    SeedreamImageClient.generateTextToImage(
                        context = context.applicationContext,
                        apiKey = key,
                        prompt = prompt,
                    )
                }
            }
            result
                .onSuccess { asset ->
                    generatedAigcImages = aigcStore.recordGeneratedImage(asset)
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Seedream 图片生成完成",
                        outputImageSource = asset.source,
                    )
                    vm.addImportedAsset(asset)
                    selectedAssetCategory = PresetCategory.AIGC
                    val libraryPath = AigcLocalLibrary.imageDirectory(context).absolutePath
                    aigcMessage = "图片生成完成：${asset.displayName}，已保存到 $libraryPath 并加入场景。"
                }
                .onFailure {
                    failAigcRecord(recordId, "Seedream 图片生成失败", it)
                    aigcMessage = "图片生成失败：${it.message ?: it.javaClass.simpleName}"
                }
            aigcBusy = false
        }
    }

    fun generateAigcModel() {
        if (blockCloudAi()) return
        if (aigcOutputMode == AigcOutputMode.IMAGE) {
            generateAigcImage()
            return
        }
        val key = rodinApiKey.trim()
        val prompt = if (aigcUseImageReference) {
            templatedPrompt(null, AigcPromptUseCase.IMAGE_TO_MODEL, aigcPrompt)
        } else {
            templatedPrompt(aigcPromptTemplate, AigcPromptUseCase.TEXT_TO_MODEL, aigcPrompt)
        }
        val cleanExtraPrompt = aigcPrompt.trim()
        if (key.isBlank()) {
            aigcMessage = "Rodin Key 未配置，请先在弹窗或 文件 > AI Key 配置 中保存。"
            openAigcKeyConfig(
                requireRodin = true,
                title = "配置 Rodin Key",
                message = "生成 3D 模型需要 Rodin API Key。Key 仅保存在本机。",
            )
            return
        }
        val seedreamKey = imageToImageApiKey.trim()
        if (aigcUseImageReference && aigcImagePreprocessMode == AigcImagePreprocessMode.SEEDREAM_CLEAN && seedreamKey.isBlank()) {
            aigcMessage = "Seedream 图生图 Key 未配置，无法执行 AI 清理。"
            openAigcKeyConfig(
                requireRodin = true,
                requireImage = true,
                title = "配置 Rodin / Seedream Key",
                message = "AI 清理后生成 3D 需要同时配置 Seedream 图生图 Key 和 Rodin API Key。",
            )
            return
        }
        val referenceImage = aigcReferenceImagePath?.let(::File)
        if (aigcUseImageReference && (referenceImage == null || !referenceImage.isFile || !referenceImage.canRead())) {
            aigcMessage = "请先选择一张参考图片。"
            return
        }
        if (aigcBusy) return
        aigcStore.saveRodinApiKey(key)
        val recordId = startAigcRecord(
            type = if (aigcUseImageReference) AigcGenerationRecordType.IMAGE_TO_MODEL else AigcGenerationRecordType.TEXT_TO_MODEL,
            title = if (aigcUseImageReference) "资源图片生成 3D" else "资源文生 3D",
            stage = if (aigcUseImageReference && aigcImagePreprocessMode != AigcImagePreprocessMode.NONE) {
                "准备图片前处理"
            } else {
                "Rodin 生成中"
            },
            sourceName = aigcReferenceImageName.orEmpty(),
            prompt = prompt,
            inputSources = referenceImage?.let { listOf("file://${it.absolutePath}") }.orEmpty(),
        )
        aigcBusy = true
        aigcMessage = if (aigcUseImageReference && aigcImagePreprocessMode != AigcImagePreprocessMode.NONE) {
            "正在准备参考图前处理结果，随后提交 Rodin 生成任务。"
        } else {
            "已提交 Rodin 生成任务，通常需要几十秒到数分钟。"
        }
        var recordFailureStage = if (aigcUseImageReference && aigcImagePreprocessMode != AigcImagePreprocessMode.NONE) {
            "图片前处理失败"
        } else {
            "Rodin 生成失败"
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val options = RodinGenerationOptions(
                        tier = aigcTier,
                        qualityOverride = aigcQualityOverride,
                        meshMode = aigcMeshMode,
                        hdTexture = aigcHdTexture,
                        creative = aigcCreative,
                    )
                    var preprocessingMessage: String? = null
                    var preprocessingImageSource: String? = null
                    val asset = if (aigcUseImageReference) {
                        var imageForRodin = requireNotNull(referenceImage)
                        if (aigcImagePreprocessMode != AigcImagePreprocessMode.NONE) {
                            val prepared = when (aigcImagePreprocessMode) {
                                AigcImagePreprocessMode.NONE -> imageForRodin to null
                                AigcImagePreprocessMode.LOCAL_SUBJECT -> {
                                    recordFailureStage = "本地 U²-Net 抠图失败"
                                    val reusable = SubjectCutoutProcessor.reusableTransparentSubjectImage(
                                        context = context.applicationContext,
                                        inputFile = imageForRodin,
                                    )
                                    if (reusable != null) {
                                        reusable to "已复用保存的透明主体图"
                                    } else {
                                        val cutout = SubjectCutoutProcessor.createTransparentSubjectImage(
                                            context = context.applicationContext,
                                            inputFile = imageForRodin,
                                        ).getOrElse { error ->
                                            throw IllegalStateException(
                                                "包内 U²-Net 主体切割失败：${error.message ?: error.javaClass.simpleName}",
                                                error,
                                            )
                                        }
                                        (if (cutout.usedPreprocessedImage) cutout.outputFile else imageForRodin) to cutout.summary
                                    }
                                }
                                AigcImagePreprocessMode.SEEDREAM_CLEAN -> {
                                    recordFailureStage = "Seedream AI 清理失败"
                                    val cleaned = SeedreamImageClient.cleanImageFor3d(
                                        context = context.applicationContext,
                                        apiKey = seedreamKey,
                                        imageFile = imageForRodin,
                                        extraPrompt = cleanExtraPrompt,
                                    ).getOrThrow()
                                    cleaned to "Seedream 已生成纯净主体图"
                                }
                            }
                            imageForRodin = prepared.first
                            preprocessingMessage = prepared.second
                            if (imageForRodin.absolutePath != referenceImage.absolutePath) {
                                preprocessingImageSource = "file://${imageForRodin.absolutePath}"
                            }
                        }
                        recordFailureStage = "Rodin 图生 3D 失败"
                        RodinAigcClient.generateImageToModel(
                            context = context.applicationContext,
                            apiKey = key,
                            imageFiles = listOf(imageForRodin),
                            prompt = prompt.takeIf { it.isNotBlank() },
                            options = options,
                        ).getOrThrow()
                    } else {
                        RodinAigcClient.generateTextToModel(
                            context = context.applicationContext,
                            apiKey = key,
                            prompt = prompt,
                            options = options,
                        ).getOrThrow()
                    }
                    AigcGenerationRunResult(
                        asset = asset,
                        preprocessingMessage = preprocessingMessage,
                        preprocessingImageSource = preprocessingImageSource,
                    )
                }
            }
            result
                .onSuccess { run ->
                    val asset = run.asset
                    run.preprocessingImageSource?.let { source ->
                        generatedAigcImages = aigcStore.recordGeneratedImage(
                            ImportedAsset(
                                displayName = "3D前处理图",
                                type = ObjectType.IMAGE,
                                source = source,
                            )
                        )
                    }
                    generatedAigcAssets = aigcStore.recordGeneratedAsset(asset)
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Rodin 生成完成",
                        outputImageSource = run.preprocessingImageSource,
                        outputModelSource = asset.source,
                    )
                    vm.addImportedAsset(asset)
                    selectedAssetCategory = PresetCategory.AIGC
                    val libraryPath = AigcLocalLibrary.rootDirectory(context).absolutePath
                    val preprocessing = run.preprocessingMessage?.let { "$it；" }.orEmpty()
                    aigcMessage = "${preprocessing}生成完成：${asset.displayName}，已保存到 $libraryPath 并加入场景。"
                }
                .onFailure {
                    failAigcRecord(recordId, recordFailureStage, it)
                    aigcMessage = "生成失败：${it.message ?: it.javaClass.simpleName}"
                }
            aigcBusy = false
        }
    }

    fun aigcPresetAssets(): List<PresetAsset> =
        (generatedAigcImages + generatedAigcAssets).sortedByDescending { it.createdAt }.map { asset ->
            PresetAsset(
                id = asset.id,
                displayName = asset.displayName,
                category = PresetCategory.AIGC,
                type = asset.type,
                source = asset.source,
            )
        }

    fun aigcImagePresetAssets(): List<PresetAsset> =
        generatedAigcImages.map { asset ->
            PresetAsset(
                id = asset.id,
                displayName = asset.displayName,
                category = PresetCategory.AIGC,
                type = ObjectType.IMAGE,
                source = asset.source,
            )
        }

    fun aigcModelPresetAssets(): List<PresetAsset> =
        generatedAigcAssets.map { asset ->
            PresetAsset(
                id = asset.id,
                displayName = asset.displayName,
                category = PresetCategory.AIGC,
                type = ObjectType.MODEL,
                source = asset.source,
            )
        }

    fun refreshAigcLibrary() {
        generatedAigcAssets = aigcStore.generatedAssets()
        generatedAigcImages = aigcStore.generatedImages()
        aigcGenerationRecords = aigcStore.generationRecords()
        aigcMessage = "已刷新本地 AIGC 生成库。"
    }

    fun toggleSelectedImageSubjectPreview() {
        val objectId = vm.selectedObjectId ?: return
        val selected = vm.selectedObject ?: return
        if (selected.type != ObjectType.IMAGE || selected.isLocked) return
        if (vm.selectedImageSubjectPreviewActive) {
            vm.restoreImageSubjectPreview(objectId)
            return
        }
        val sourceFile = File(selected.source.removePrefix("file://"))
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            vm.reportImageSubjectPreviewError("无法读取原图")
            return
        }
        imageSubjectPreprocessChoiceObjectId = objectId
        imageSubjectPreprocessPrompt = ""
        imageSubjectPreprocessMessage = "请选择本地抠图或 Seedream AI 清理。"
    }

    fun commitImagePreprocessVersion(
        objectId: String,
        baseName: String,
        source: String,
        mode: AigcImagePreprocessMode,
        recordId: String? = null,
        prompt: String = "",
        inputSources: List<String> = emptyList(),
        successMessage: String,
    ): Boolean {
        val current = vm.document.objects[objectId] ?: return false
        if (current.source == source && vm.currentImageVersionPreprocessMode(objectId) == mode) {
            imageSubjectPreprocessChoiceObjectId = null
            imageSubjectPreprocessMessage = successMessage
            aigcMessage = successMessage
            return true
        }
        val asset = imagePreprocessAsset(baseName, source, mode)
        generatedAigcImages = aigcStore.recordGeneratedImage(asset)
        val committed = vm.replaceImageWithPreprocessedVersion(
            objectId = objectId,
            asset = asset,
            mode = mode,
            selectReplacedObject = true,
            recordId = recordId,
            prompt = prompt,
            inputSources = inputSources,
        )
        imageSubjectPreprocessChoiceObjectId = null
        imageSubjectPreprocessMessage = if (committed) {
            successMessage
        } else {
            "处理完成但当前图片已不可替换，结果已保存到本地图片库。"
        }
        aigcMessage = imageSubjectPreprocessMessage
        return committed
    }

    fun runImageSubjectPreprocess(mode: AigcImagePreprocessMode) {
        if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN && blockCloudAi()) return
        val objectId = imageSubjectPreprocessChoiceObjectId ?: vm.selectedObjectId ?: return
        val selected = vm.document.objects[objectId] ?: return
        if (selected.type != ObjectType.IMAGE || selected.isLocked) return
        if (objectId in imageSubjectPreviewBusyObjectIds) return
        if (mode != AigcImagePreprocessMode.NONE && vm.currentImageVersionPreprocessMode(objectId) == mode) {
            imageSubjectPreprocessChoiceObjectId = null
            imageSubjectPreprocessMessage = when (mode) {
                AigcImagePreprocessMode.LOCAL_SUBJECT -> "当前图片已经是本地抠图版本，可直接继续生成。"
                AigcImagePreprocessMode.SEEDREAM_CLEAN -> "当前图片已经是 Seedream AI 清理版本，可直接继续生成。"
                AigcImagePreprocessMode.NONE -> "当前图片无需前处理。"
            }
            vm.reportImageSubjectPreviewNoChange(imageSubjectPreprocessMessage)
            return
        }
        val sourceFile = File(selected.source.removePrefix("file://"))
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            vm.reportImageSubjectPreviewError("无法读取原图")
            imageSubjectPreprocessMessage = "无法读取原图。"
            return
        }
        if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN && imageToImageApiKey.trim().isBlank()) {
            imageSubjectPreprocessMessage = "Seedream 图生图 Key 未配置。"
            openAigcKeyConfig(
                requireImage = true,
                title = "配置 Seedream Key",
                message = "AI 清理图片需要 Seedream 图生图 Key。Key 仅保存在本机。",
            )
            return
        }
        if (mode == AigcImagePreprocessMode.LOCAL_SUBJECT) {
            vm.cachedImageSubjectPreviewSource(objectId)?.let { cachedSource ->
                val cachedFile = File(cachedSource.removePrefix("file://"))
                if (cachedFile.isFile && cachedFile.canRead()) {
                    commitImagePreprocessVersion(
                        objectId = objectId,
                        baseName = selected.displayName,
                        source = cachedSource,
                        mode = AigcImagePreprocessMode.LOCAL_SUBJECT,
                        inputSources = listOf(selected.source),
                        successMessage = "已复用上次本地抠图结果，并保存为当前图片新版本。",
                    )
                    return
                }
                vm.clearImageSubjectPreviewCache(objectId)
            }
            SubjectCutoutProcessor.reusableTransparentSubjectImage(context.applicationContext, sourceFile)?.let { reusableFile ->
                commitImagePreprocessVersion(
                    objectId = objectId,
                    baseName = selected.displayName,
                    source = "file://${reusableFile.absolutePath}",
                    mode = AigcImagePreprocessMode.LOCAL_SUBJECT,
                    inputSources = listOf(selected.source),
                    successMessage = "已复用保存的本地抠图结果，并保存为当前图片新版本。",
                )
                return
            }
        }
        imageSubjectPreviewBusyObjectIds = imageSubjectPreviewBusyObjectIds + objectId
        val preprocessRecordId = startAigcRecord(
            type = AigcGenerationRecordType.IMAGE_PREPROCESS,
            title = if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN) "Seedream AI 清理" else "本地主体抠图",
            stage = if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN) "Seedream AI 清理中" else "本地 U²-Net 抠图中",
            sourceName = selected.displayName,
            prompt = imageSubjectPreprocessPrompt.trim(),
            inputSources = listOf(selected.source),
        )
        vm.reportImageSubjectPreviewStarted(
            if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN) {
                "正在使用 Seedream AI 清理图片..."
            } else {
                "正在使用包内 U²-Net 主体模型生成透明图..."
            }
        )
        imageSubjectPreprocessMessage = if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN) {
            "正在用 Seedream 清理图片。"
        } else {
            "正在执行本地 U²-Net 主体切割。"
        }
        imageSubjectPreprocessChoiceObjectId = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (mode) {
                        AigcImagePreprocessMode.NONE -> Result.success(sourceFile to "原图无需前处理")
                        AigcImagePreprocessMode.LOCAL_SUBJECT ->
                            SubjectCutoutProcessor.createTransparentSubjectImage(
                                context = context.applicationContext,
                                inputFile = sourceFile,
                            ).map { cutout ->
                                (if (cutout.usedPreprocessedImage) cutout.outputFile else sourceFile) to cutout.summary
                            }
                        AigcImagePreprocessMode.SEEDREAM_CLEAN ->
                            SeedreamImageClient.cleanImageFor3d(
                                context = context.applicationContext,
                                apiKey = imageToImageApiKey.trim(),
                                imageFile = sourceFile,
                                extraPrompt = imageSubjectPreprocessPrompt,
                            ).map { file -> file to "Seedream 已生成纯净主体图" }
                    }
                }
                result
                    .onSuccess { (outputFile, summary) ->
                        val outputSource = "file://${outputFile.absolutePath}"
                        updateAigcRecord(
                            recordId = preprocessRecordId,
                            status = AigcGenerationRecordStatus.SUCCEEDED,
                            stage = summary,
                            outputImageSource = outputSource.takeIf { outputFile.absolutePath != sourceFile.absolutePath },
                        )
                        if (outputFile.absolutePath != sourceFile.absolutePath) {
                            commitImagePreprocessVersion(
                                objectId = objectId,
                                baseName = selected.displayName,
                                source = outputSource,
                                mode = mode,
                                recordId = preprocessRecordId,
                                prompt = imageSubjectPreprocessPrompt.trim(),
                                inputSources = listOf(selected.source),
                                successMessage = "$summary；结果已保存为当前图片新版本。",
                            )
                        } else {
                            vm.reportImageSubjectPreviewNoChange(summary)
                            imageSubjectPreprocessMessage = summary
                        }
                    }
                    .onFailure {
                        failAigcRecord(preprocessRecordId, "图片前处理失败", it)
                        vm.reportImageSubjectPreviewError(it.message ?: it.javaClass.simpleName)
                        imageSubjectPreprocessMessage = "处理失败：${it.message ?: it.javaClass.simpleName}"
                    }
            } finally {
                imageSubjectPreviewBusyObjectIds = imageSubjectPreviewBusyObjectIds - objectId
            }
        }
    }

    fun openImageToImagePanel() {
        if (blockCloudAi()) return
        val selected = vm.selectedObject ?: return
        if (selected.type != ObjectType.IMAGE || selected.isLocked || selected.id in imageToImageBusyObjectIds) return
        imageToImageObjectId = selected.id
        imageToImagePrompt = ""
        imageToImagePromptTemplate = null
        imageToImageMessage = "基于当前图片生成一张新版本，成功后替换当前物体。"
    }

    fun generateImageVariationFromSelectedImage() {
        if (blockCloudAi()) return
        val objectId = imageToImageObjectId ?: return
        val target = vm.document.objects[objectId]
        if (target == null || target.type != ObjectType.IMAGE || target.isLocked) {
            imageToImageMessage = "当前图片已不可用，请重新选择后再试。"
            return
        }
        val key = imageToImageApiKey.trim()
        if (key.isBlank()) {
            imageToImageMessage = "Seedream 图生图 Key 未配置，请先保存 Key。"
            openAigcKeyConfig(
                requireImage = true,
                title = "配置 Seedream Key",
                message = "图片生成新图需要 Seedream 图生图 Key。Key 仅保存在本机。",
            )
            return
        }
        val prompt = templatedPrompt(
            template = imageToImagePromptTemplate,
            useCase = AigcPromptUseCase.IMAGE_TO_IMAGE,
            userPrompt = imageToImagePrompt,
        )
        if (prompt.isBlank()) {
            imageToImageMessage = "请描述要生成的新图。"
            return
        }
        val referenceSource = vm.renderObject(objectId)?.source ?: target.source
        val sourceFile = File(referenceSource.removePrefix("file://"))
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            imageToImageMessage = "无法读取原图片：${target.displayName}"
            return
        }
        if (objectId in imageToImageBusyObjectIds) return
        aigcStore.saveImageToImageApiKey(key)
        val recordId = startAigcRecord(
            type = AigcGenerationRecordType.IMAGE_TO_IMAGE,
            title = "图片 AI 改图",
            stage = "Seedream 图生图生成中",
            sourceName = target.displayName,
            prompt = prompt,
            inputSources = listOf(referenceSource),
        )
        imageToImageBusyObjectIds = imageToImageBusyObjectIds + objectId
        imageToImageMessage = "正在调用 Seedream 基于当前图片生成新图。"
        imageToImageObjectId = null
        imageToImagePrompt = ""
        imageToImagePromptTemplate = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SeedreamImageClient.generateImageToImage(
                    context = context.applicationContext,
                    apiKey = key,
                    imageFiles = listOf(sourceFile),
                    prompt = prompt,
                )
            }
            result
                .onSuccess { asset ->
                    generatedAigcImages = aigcStore.recordGeneratedImage(asset)
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Seedream 图生图完成",
                        outputImageSource = asset.source,
                    )
                    if (
                        vm.addImageGeneratedFromImageObject(
                            sourceObjectId = objectId,
                            asset = asset,
                            selectNewObject = true,
                            recordId = recordId,
                            prompt = prompt,
                            inputSources = listOf(referenceSource),
                        )
                    ) {
                        imageToImageObjectId = null
                        imageToImagePrompt = ""
                        imageToImageMessage = "图片生成完成，已替换为当前图片新版本。"
                        aigcMessage = "图片生成完成：${asset.displayName}，已保存并替换当前图片。"
                    } else {
                        imageToImageMessage = "图片生成完成但原图片已不可用，已保存到本地图片库。"
                        aigcMessage = "图片生成完成：${asset.displayName}，已保存到本地图片库。"
                    }
                }
                .onFailure {
                    failAigcRecord(recordId, "Seedream 图生图失败", it)
                    imageToImageMessage = "图片生成失败：${it.message ?: it.javaClass.simpleName}"
                    aigcMessage = "图片生成失败：${it.message ?: it.javaClass.simpleName}"
                }
            imageToImageBusyObjectIds = imageToImageBusyObjectIds - objectId
        }
    }

    fun openImageToModelConfirm() {
        val selected = vm.selectedObject ?: return
        if (selected.type != ObjectType.IMAGE || selected.isLocked || selected.id in imageToModelBusyObjectIds) return
        if (vm.selectedImageCanUseGeneratedModel) {
            vm.switchSelectedImageToGeneratedModel()
            return
        }
        if (blockCloudAi()) return
        imageToModelConfirmObjectId = selected.id
        imageToModelPrompt = ""
        imageToModelPromptTemplate = null
        imageToModelPreprocessMode = AigcImagePreprocessMode.LOCAL_SUBJECT
        imageToModelAdvancedOpen = false
        imageToModelTier = aigcTier
        imageToModelQualityOverride = aigcQualityOverride
        imageToModelMeshMode = aigcMeshMode
        imageToModelHdTexture = aigcHdTexture
        imageToModelCreative = aigcCreative
        imageToModelMessage = "确认后会生成 3D 模型并替换原图片，失败时原图保持不变。"
    }

    fun generateImageToModelReplacement() {
        if (blockCloudAi()) return
        val objectId = imageToModelConfirmObjectId ?: return
        val target = vm.document.objects[objectId]
        if (target == null || target.type != ObjectType.IMAGE || target.isLocked) {
            imageToModelMessage = "当前图片已不可用，请重新选择图片后再试。"
            return
        }
        val key = rodinApiKey.trim()
        if (key.isBlank()) {
            imageToModelMessage = "Rodin Key 未配置，请先保存 Key。"
            openAigcKeyConfig(
                requireRodin = true,
                title = "配置 Rodin Key",
                message = "图片生成 3D 需要 Rodin API Key。Key 仅保存在本机。",
            )
            return
        }
        val seedreamKey = imageToImageApiKey.trim()
        if (imageToModelPreprocessMode == AigcImagePreprocessMode.SEEDREAM_CLEAN && seedreamKey.isBlank()) {
            imageToModelMessage = "Seedream 图生图 Key 未配置，无法执行 AI 清理。"
            openAigcKeyConfig(
                requireRodin = true,
                requireImage = true,
                title = "配置 Rodin / Seedream Key",
                message = "AI 清理后生成 3D 需要同时配置 Seedream 图生图 Key 和 Rodin API Key。",
            )
            return
        }
        val activePreviewMode = vm.activeImageSubjectPreviewMode(objectId)
        val previewSource = vm.renderObject(objectId)
            ?.source
            ?.takeIf {
                it != target.source &&
                    activePreviewMode == imageToModelPreprocessMode &&
                    imageToModelPreprocessMode != AigcImagePreprocessMode.NONE
            }
        val committedPreprocessAlreadyMatches = imageToModelPreprocessMode != AigcImagePreprocessMode.NONE &&
            vm.currentImageVersionPreprocessMode(objectId) == imageToModelPreprocessMode
        val previewAlreadyMatchesPreprocess = previewSource != null
        val referenceSource = when {
            committedPreprocessAlreadyMatches -> target.source
            previewAlreadyMatchesPreprocess -> requireNotNull(previewSource)
            else -> target.source
        }
        val sourceFile = File(referenceSource.removePrefix("file://"))
        if (!sourceFile.isFile || !sourceFile.canRead()) {
            imageToModelMessage = "无法读取原图片：${target.displayName}"
            return
        }
        if (objectId in imageToModelBusyObjectIds) return
        val prompt = templatedPrompt(
            template = imageToModelPromptTemplate,
            useCase = AigcPromptUseCase.IMAGE_TO_MODEL,
            userPrompt = imageToModelPrompt,
        )
        val cleanExtraPrompt = imageToModelPrompt.trim()
        val preprocessMode = imageToModelPreprocessMode
        val options = RodinGenerationOptions(
            tier = imageToModelTier,
            qualityOverride = imageToModelQualityOverride,
            meshMode = imageToModelMeshMode,
            hdTexture = imageToModelHdTexture,
            creative = imageToModelCreative,
        )
        aigcStore.saveRodinApiKey(key)
        val recordId = startAigcRecord(
            type = AigcGenerationRecordType.IMAGE_TO_MODEL,
            title = "图片对象生成 3D",
            stage = if (preprocessMode != AigcImagePreprocessMode.NONE) {
                "准备图片前处理"
            } else {
                "Rodin 图生 3D 中"
            },
            sourceName = target.displayName,
            prompt = prompt,
            inputSources = listOf(referenceSource),
        )
        imageToModelBusyObjectIds = imageToModelBusyObjectIds + objectId
        imageToModelConfirmObjectId = null
        imageToModelPromptTemplate = null
        imageToModelMessage = if (committedPreprocessAlreadyMatches) {
            "正在复用当前已保存的前处理版本，随后提交 Rodin 图生 3D。"
        } else if (previewAlreadyMatchesPreprocess) {
            "正在复用当前前处理图，随后提交 Rodin 图生 3D。"
        } else if (preprocessMode != AigcImagePreprocessMode.NONE) {
            "正在准备前处理图，随后提交 Rodin 图生 3D。"
        } else {
            "正在提交 Rodin 图生 3D，生成通常需要几十秒到数分钟。"
        }
        var recordFailureStage = if (!committedPreprocessAlreadyMatches &&
            !previewAlreadyMatchesPreprocess &&
            preprocessMode != AigcImagePreprocessMode.NONE
        ) {
            "图片前处理失败"
        } else {
            "图片生成 3D 失败"
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var preprocessingMessage: String? = null
                    var preprocessingImageSource: String? = null
                    var preprocessingModeForHistory: AigcImagePreprocessMode? = null
                    var imageForRodin = sourceFile
                    if (committedPreprocessAlreadyMatches || previewAlreadyMatchesPreprocess) {
                        preprocessingMessage = when (preprocessMode) {
                            AigcImagePreprocessMode.LOCAL_SUBJECT -> "已复用当前本地抠图结果"
                            AigcImagePreprocessMode.SEEDREAM_CLEAN -> "已复用当前 Seedream AI 清理图"
                            AigcImagePreprocessMode.NONE -> null
                        }
                        if (previewAlreadyMatchesPreprocess) {
                            preprocessingImageSource = referenceSource
                            preprocessingModeForHistory = preprocessMode
                        }
                    } else if (preprocessMode != AigcImagePreprocessMode.NONE) {
                        val prepared = when (preprocessMode) {
                            AigcImagePreprocessMode.NONE -> imageForRodin to null
                            AigcImagePreprocessMode.LOCAL_SUBJECT -> {
                                recordFailureStage = "本地 U²-Net 抠图失败"
                                val reusable = SubjectCutoutProcessor.reusableTransparentSubjectImage(
                                    context = context.applicationContext,
                                    inputFile = sourceFile,
                                )
                                if (reusable != null) {
                                    reusable to "已复用保存的透明主体图"
                                } else {
                                    val cutout = SubjectCutoutProcessor.createTransparentSubjectImage(
                                        context = context.applicationContext,
                                        inputFile = sourceFile,
                                    ).getOrElse { error ->
                                        throw IllegalStateException(
                                            "包内 U²-Net 主体切割失败：${error.message ?: error.javaClass.simpleName}",
                                            error,
                                        )
                                    }
                                    (if (cutout.usedPreprocessedImage) cutout.outputFile else sourceFile) to cutout.summary
                                }
                            }
                            AigcImagePreprocessMode.SEEDREAM_CLEAN -> {
                                recordFailureStage = "Seedream AI 清理失败"
                                val cleaned = SeedreamImageClient.cleanImageFor3d(
                                    context = context.applicationContext,
                                    apiKey = seedreamKey,
                                    imageFile = sourceFile,
                                    extraPrompt = cleanExtraPrompt,
                                ).getOrThrow()
                                cleaned to "Seedream 已生成纯净主体图"
                            }
                        }
                        imageForRodin = prepared.first
                        preprocessingMessage = prepared.second
                        if (imageForRodin.absolutePath != sourceFile.absolutePath) {
                            preprocessingImageSource = "file://${imageForRodin.absolutePath}"
                            preprocessingModeForHistory = preprocessMode
                        }
                    }
                    recordFailureStage = "图片生成 3D 失败"
                    AigcGenerationRunResult(
                        asset = RodinAigcClient.generateImageToModel(
                            context = context.applicationContext,
                            apiKey = key,
                            imageFiles = listOf(imageForRodin),
                            prompt = prompt.takeIf { it.isNotBlank() },
                            options = options,
                        ).getOrThrow(),
                        preprocessingMessage = preprocessingMessage,
                        preprocessingImageSource = preprocessingImageSource,
                        preprocessingMode = preprocessingModeForHistory,
                    )
                }
            }
            result
                .onSuccess { run ->
                    val asset = run.asset
                    val preprocessingAsset = run.preprocessingImageSource?.let { source ->
                        imagePreprocessAsset(
                            baseName = target.displayName,
                            source = source,
                            mode = run.preprocessingMode ?: preprocessMode,
                        ).also { generatedAigcImages = aigcStore.recordGeneratedImage(it) }
                    }
                    generatedAigcAssets = aigcStore.recordGeneratedAsset(asset)
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Rodin 图生 3D 完成",
                        outputImageSource = run.preprocessingImageSource,
                        outputModelSource = asset.source,
                    )
                    selectedAssetCategory = PresetCategory.AIGC
                    val libraryPath = AigcLocalLibrary.rootDirectory(context).absolutePath
                    val preprocessing = run.preprocessingMessage?.let { "$it；" }.orEmpty()
                    val shouldSelectReplacement = vm.selectedObjectId == objectId
                    if (
                        vm.replaceImageWithGeneratedModel(
                            objectId = objectId,
                            asset = asset,
                            selectReplacedObject = shouldSelectReplacement,
                            preprocessingAsset = preprocessingAsset,
                            preprocessingMode = run.preprocessingMode,
                            recordId = recordId,
                            prompt = prompt,
                            inputSources = listOf(referenceSource),
                        )
                    ) {
                        if (imageToModelConfirmObjectId == objectId) {
                            imageToModelConfirmObjectId = null
                        }
                        imageToModelMessage = "${preprocessing}生成完成：${asset.displayName}，已替换原图片。"
                        aigcMessage = "${preprocessing}生成完成：${asset.displayName}，已保存到 $libraryPath 并替换原图片。"
                    } else {
                        imageToModelMessage = "${preprocessing}生成完成并已保存到本地生成库，但原图片已不可替换。"
                        aigcMessage = "${preprocessing}生成完成：${asset.displayName}，已保存到 $libraryPath。"
                    }
                }
                .onFailure {
                    failAigcRecord(recordId, recordFailureStage, it)
                    imageToModelMessage = "生成失败：${it.message ?: it.javaClass.simpleName}。原图片未改变。"
                    aigcMessage = "图片生成 3D 失败：${it.message ?: it.javaClass.simpleName}。原图片未改变。"
                }
            imageToModelBusyObjectIds = imageToModelBusyObjectIds - objectId
        }
    }

    fun openTextToImagePanel() {
        if (blockCloudAi()) return
        val selected = vm.selectedObject ?: return
        if (selected.type != ObjectType.TEXT || selected.isLocked || selected.id in textToImageBusyObjectIds) return
        textToImageObjectId = selected.id
        textToImagePromptExtra = ""
        textToImagePromptTemplate = null
        textToImageMessage = "基于当前文本生成图片，并替换为图片对象。"
    }

    fun generateSelectedTextImage() {
        if (blockCloudAi()) return
        val objectId = textToImageObjectId ?: return
        val target = vm.document.objects[objectId]
        if (target == null || target.type != ObjectType.TEXT || target.isLocked) {
            textToImageMessage = "当前文本已不可用，请重新选择后再试。"
            return
        }
        val key = imageToImageApiKey.trim()
        if (key.isBlank()) {
            textToImageMessage = "Seedream 图生图 Key 未配置，请先保存 Key。"
            openAigcKeyConfig(
                requireImage = true,
                title = "配置 Seedream Key",
                message = "文本生成图片需要 Seedream 图生图 Key。Key 仅保存在本机。",
            )
            return
        }
        val baseText = target.source.trim()
        if (baseText.isBlank()) {
            textToImageMessage = "当前文本为空，无法生成图片。"
            return
        }
        if (objectId in textToImageBusyObjectIds) return
        val prompt = templatedPrompt(
            template = textToImagePromptTemplate,
            useCase = AigcPromptUseCase.TEXT_TO_IMAGE,
            userPrompt = textToImagePromptExtra,
            sourceText = baseText,
        )
        aigcStore.saveImageToImageApiKey(key)
        val recordId = startAigcRecord(
            type = AigcGenerationRecordType.TEXT_TO_IMAGE,
            title = "文本对象生成图片",
            stage = "Seedream 文生图中",
            sourceName = target.displayName,
            prompt = prompt,
        )
        textToImageBusyObjectIds = textToImageBusyObjectIds + objectId
        textToImageMessage = "正在调用 Seedream 生成图片。"
        textToImageObjectId = null
        textToImagePromptExtra = ""
        textToImagePromptTemplate = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SeedreamImageClient.generateTextToImage(
                    context = context.applicationContext,
                    apiKey = key,
                    prompt = prompt,
                )
            }
            result
                .onSuccess { asset ->
                    generatedAigcImages = aigcStore.recordGeneratedImage(asset)
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Seedream 文生图完成",
                        outputImageSource = asset.source,
                    )
                    if (
                        vm.replaceTextWithGeneratedImage(
                            objectId = objectId,
                            asset = asset,
                            selectReplacedObject = true,
                            recordId = recordId,
                            prompt = prompt,
                            inputSources = listOf(target.source),
                        )
                    ) {
                        textToImageObjectId = null
                        textToImagePromptExtra = ""
                        textToImagePromptTemplate = null
                        textToImageMessage = "图片生成完成，已替换文本对象。"
                        aigcMessage = "图片生成完成：${asset.displayName}，已保存并替换文本对象。"
                    } else {
                        textToImageMessage = "图片生成完成但文本对象已不可替换。"
                        aigcMessage = "图片生成完成：${asset.displayName}，已保存到本地图片库。"
                    }
                }
                .onFailure {
                    failAigcRecord(recordId, "文本生成图片失败", it)
                    textToImageMessage = "图片生成失败：${it.message ?: it.javaClass.simpleName}"
                    aigcMessage = "文本生成图片失败：${it.message ?: it.javaClass.simpleName}"
                }
            textToImageBusyObjectIds = textToImageBusyObjectIds - objectId
        }
    }

    fun openStrokeToSeedreamPanel() {
        if (blockCloudAi()) return
        val selected = vm.selectedObject ?: return
        if (
            selected.type != ObjectType.STROKE ||
            selected.isLocked ||
            selected.id in strokeToSeedreamBusyObjectIds ||
            selected.id in strokeToModelBusyObjectIds
        ) return
        strokeToModelObjectId = null
        strokeToSeedreamObjectId = selected.id
        strokeToSeedreamPromptExtra = ""
        strokeToSeedreamPromptTemplate = null
        strokeToSeedreamMessage = "将当前观察方向的主视图发送给 Seedream，自动补全并生成 2D 图片。"
    }

    fun openStrokeToModelPanel() {
        if (blockCloudAi()) return
        val selected = vm.selectedObject ?: return
        if (
            selected.type != ObjectType.STROKE ||
            selected.isLocked ||
            selected.id in strokeToSeedreamBusyObjectIds ||
            selected.id in strokeToModelBusyObjectIds
        ) return
        strokeToSeedreamObjectId = null
        strokeToModelObjectId = selected.id
        strokeToModelPromptExtra = ""
        strokeToModelPromptTemplate = null
        strokeToModelAdvancedOpen = false
        strokeToModelTier = aigcTier
        strokeToModelQualityOverride = aigcQualityOverride
        strokeToModelMeshMode = aigcMeshMode
        strokeToModelHdTexture = aigcHdTexture
        strokeToModelCreative = aigcCreative
        strokeToModelMessage = "将当前观察方向的主视图送 Seedream 发散成图，再直接提交 Rodin 生成 3D。"
    }

    fun generateStrokeViaSeedream(
        objectId: String,
        outputMode: AigcOutputMode,
        seedreamExtraPrompt: String,
        closeImagePanelOnStart: Boolean,
        closeModelPanelOnStart: Boolean = false,
        rodinPrompt: String = "",
        modelOptions: RodinGenerationOptions? = null,
    ) {
        if (blockCloudAi()) return
        val isModel = outputMode == AigcOutputMode.MODEL
        fun updateStrokeGenerationMessage(message: String) {
            if (isModel) {
                strokeToModelMessage = message
            } else {
                strokeToSeedreamMessage = message
            }
            aigcMessage = message
        }
        val target = vm.document.objects[objectId]
        if (target == null || target.type != ObjectType.STROKE || target.isLocked) {
            updateStrokeGenerationMessage("当前笔画已不可用，请重新选择后再试。")
            return
        }
        val seedreamKey = imageToImageApiKey.trim()
        val rodinKey = rodinApiKey.trim()
        if (seedreamKey.isBlank() || (isModel && rodinKey.isBlank())) {
            val message = when {
                seedreamKey.isBlank() && isModel && rodinKey.isBlank() ->
                    "Seedream / Rodin Key 未配置，请先保存 Key。"
                seedreamKey.isBlank() -> "Seedream 图生图 Key 未配置，请先保存 Key。"
                else -> "Rodin Key 未配置，请先保存 Key。"
            }
            updateStrokeGenerationMessage(message)
            openAigcKeyConfig(
                requireRodin = isModel,
                requireImage = true,
                title = if (isModel) "配置 Seedream / Rodin Key" else "配置 Seedream Key",
                message = if (isModel) {
                    "笔画生成 3D 需要 Seedream 图生图 Key 和 Rodin API Key。Key 仅保存在本机。"
                } else {
                    "笔画 AI 成图需要 Seedream 图生图 Key。Key 仅保存在本机。"
                },
            )
            return
        }
        val strokeData = target.strokeData?.copyData()
        if (strokeData == null || strokeData.pointCount < 2) {
            updateStrokeGenerationMessage("笔画没有足够点数，无法生成图片。")
            return
        }
        if (
            objectId in strokeToSeedreamBusyObjectIds ||
            objectId in strokeToModelBusyObjectIds
        ) return
        val displayName = target.displayName.ifBlank { "笔画" }
        val frontFace = strokeFrontFaceForViewer(
            transform = vm.currentTransform(objectId),
            viewer = viewerPose.position,
        )
        val rodinOptions = modelOptions ?: RodinGenerationOptions(
            tier = aigcTier,
            qualityOverride = aigcQualityOverride,
            meshMode = aigcMeshMode,
            hdTexture = aigcHdTexture,
            creative = aigcCreative,
        )
        aigcStore.saveImageToImageApiKey(seedreamKey)
        if (isModel) {
            aigcStore.saveRodinApiKey(rodinKey)
        }
        val seedreamPrompt = SeedreamImageClient.strokeMainViewPrompt(seedreamExtraPrompt)
        val modelPrompt = rodinPrompt.trim()
        val historyPrompt = if (isModel) {
            "Seedream 2D prompt：\n$seedreamPrompt\n\nRodin 3D prompt：\n$modelPrompt"
        } else {
            seedreamPrompt
        }
        val recordId = startAigcRecord(
            type = if (isModel) AigcGenerationRecordType.STROKE_TO_MODEL else AigcGenerationRecordType.STROKE_TO_IMAGE,
            title = if (isModel) "笔画生成 3D" else "笔画 AI 成图",
            stage = "主视图参考图生成中",
            sourceName = displayName,
            prompt = historyPrompt,
        )
        if (isModel) {
            strokeToModelBusyObjectIds = strokeToModelBusyObjectIds + objectId
        } else {
            strokeToSeedreamBusyObjectIds = strokeToSeedreamBusyObjectIds + objectId
        }
        updateStrokeGenerationMessage(if (isModel) {
            "正在生成主视图参考图、Seedream 图片，并提交 Rodin 生成 3D。"
        } else {
            "正在生成主视图参考图并调用 Seedream。"
        })
        if (closeImagePanelOnStart && strokeToSeedreamObjectId == objectId) {
            strokeToSeedreamObjectId = null
            strokeToSeedreamPromptExtra = ""
            strokeToSeedreamPromptTemplate = null
        }
        if (closeModelPanelOnStart && strokeToModelObjectId == objectId) {
            strokeToModelObjectId = null
            strokeToModelPromptExtra = ""
            strokeToModelPromptTemplate = null
            strokeToModelAdvancedOpen = false
        }
        scope.launch {
            try {
                val lineArtAsset = withContext(Dispatchers.IO) {
                    StrokeImageRenderer.renderMainViewReference(
                        context = context.applicationContext,
                        strokeData = strokeData,
                        displayName = displayName,
                        frontFace = frontFace,
                    )
                }.getOrElse {
                    failAigcRecord(recordId, "主视图参考图生成失败", it)
                    val prefix = if (isModel) "笔画 AI 生成 3D 失败" else "笔画 AI 成图失败"
                    strokeToSeedreamMessage = "$prefix：主视图参考图生成失败，${it.message ?: it.javaClass.simpleName}"
                    aigcMessage = strokeToSeedreamMessage
                    return@launch
                }
                generatedAigcImages = aigcStore.recordGeneratedImage(lineArtAsset)
                updateAigcRecord(
                    recordId = recordId,
                    stage = "Seedream 成图中",
                    inputSources = listOf(lineArtAsset.source),
                )

                val lineArtFile = File(lineArtAsset.source.removePrefix("file://"))
                if (!lineArtFile.isFile || !lineArtFile.canRead()) {
                    val error = IllegalStateException("无法读取主视图参考图：${lineArtFile.name}")
                    failAigcRecord(recordId, "Seedream 成图前读取线稿失败", error)
                    val prefix = if (isModel) "笔画 AI 生成 3D 失败" else "笔画 AI 成图失败"
                    strokeToSeedreamMessage = "$prefix：${error.message}"
                    aigcMessage = strokeToSeedreamMessage
                    return@launch
                }

                val seedreamAsset = withContext(Dispatchers.IO) {
                    SeedreamImageClient.generateImageToImage(
                        context = context.applicationContext,
                        apiKey = seedreamKey,
                        imageFiles = listOf(lineArtFile),
                        prompt = seedreamPrompt,
                    )
                }.map { asset ->
                    asset.copy(
                        displayName = "${displayName.take(14)}AI成图",
                    )
                }.getOrElse {
                    failAigcRecord(recordId, "Seedream 成图失败", it)
                    val prefix = if (isModel) "笔画 AI 生成 3D 失败" else "笔画 AI 成图失败"
                    strokeToSeedreamMessage = "$prefix：Seedream 成图失败，${it.message ?: it.javaClass.simpleName}"
                    aigcMessage = strokeToSeedreamMessage
                    return@launch
                }
                generatedAigcImages = aigcStore.recordGeneratedImage(seedreamAsset)

                if (!isModel) {
                    updateAigcRecord(
                        recordId = recordId,
                        status = AigcGenerationRecordStatus.SUCCEEDED,
                        stage = "Seedream 成图完成",
                        outputImageSource = seedreamAsset.source,
                    )
                    if (
                        vm.addImageGeneratedFromStrokeObject(
                            sourceObjectId = objectId,
                            asset = seedreamAsset,
                            selectNewObject = true,
                            recordId = recordId,
                            prompt = seedreamPrompt,
                            inputSources = listOf(lineArtAsset.source),
                        )
                    ) {
                        strokeToSeedreamMessage = "Seedream 图片生成完成，已替换为当前笔画的新图片版本。"
                        aigcMessage = "笔画 AI 成图完成：${seedreamAsset.displayName}，已保存并替换当前笔画。"
                        selectedAssetCategory = PresetCategory.AIGC
                    } else {
                        strokeToSeedreamMessage = "Seedream 图片生成完成但原笔画已不可用，已保存到本地图片库。"
                        aigcMessage = "笔画 AI 成图完成：${seedreamAsset.displayName}，已保存到本地图片库。"
                    }
                    return@launch
                }

                updateAigcRecord(
                    recordId = recordId,
                    stage = "Rodin 图生 3D 中",
                    outputImageSource = seedreamAsset.source,
                )
                val seedreamFile = File(seedreamAsset.source.removePrefix("file://"))
                if (!seedreamFile.isFile || !seedreamFile.canRead()) {
                    val error = IllegalStateException("无法读取 Seedream 生成图：${seedreamFile.name}")
                    failAigcRecord(recordId, "Rodin 生成前读取 Seedream 图失败", error)
                    strokeToSeedreamMessage = "笔画 AI 生成 3D 失败：${error.message}"
                    aigcMessage = strokeToSeedreamMessage
                    return@launch
                }

                val modelAsset = withContext(Dispatchers.IO) {
                    RodinAigcClient.generateImageToModel(
                        context = context.applicationContext,
                        apiKey = rodinKey,
                        imageFiles = listOf(seedreamFile),
                        prompt = modelPrompt.takeIf { it.isNotBlank() },
                        options = rodinOptions,
                    )
                }.map { asset ->
                    asset.copy(
                        displayName = "${displayName.take(14)}AI模型",
                    )
                }.getOrElse {
                    failAigcRecord(recordId, "Rodin 图生 3D 失败", it)
                    strokeToSeedreamMessage = "笔画 AI 生成 3D 失败：Rodin 图生 3D 失败，${it.message ?: it.javaClass.simpleName}"
                    aigcMessage = strokeToSeedreamMessage
                    return@launch
                }
                generatedAigcAssets = aigcStore.recordGeneratedAsset(modelAsset)
                updateAigcRecord(
                    recordId = recordId,
                    status = AigcGenerationRecordStatus.SUCCEEDED,
                    stage = "Rodin 图生 3D 完成",
                    outputImageSource = seedreamAsset.source,
                    outputModelSource = modelAsset.source,
                )
                if (
                    vm.addModelGeneratedFromStrokeImage(
                        sourceObjectId = objectId,
                        imageAsset = seedreamAsset,
                        modelAsset = modelAsset,
                        selectNewObject = true,
                        recordId = recordId,
                        prompt = historyPrompt,
                        inputSources = listOf(lineArtAsset.source, seedreamAsset.source),
                    )
                ) {
                    strokeToSeedreamMessage = "笔画生成 3D 完成，已替代原画笔物体，可在版本管理查看 Seedream 2D 图。"
                    aigcMessage = "笔画生成 3D 完成：${modelAsset.displayName}，已替代原画笔物体。"
                    selectedAssetCategory = PresetCategory.AIGC
                } else {
                    strokeToSeedreamMessage = "笔画生成 3D 完成但原笔画已不可用，模型已保存到本地资源库。"
                    aigcMessage = strokeToSeedreamMessage
                }
            } finally {
                if (isModel) {
                    strokeToModelBusyObjectIds = strokeToModelBusyObjectIds - objectId
                } else {
                    strokeToSeedreamBusyObjectIds = strokeToSeedreamBusyObjectIds - objectId
                }
            }
        }
    }

    fun generateSelectedStrokeSeedreamImage() {
        val objectId = strokeToSeedreamObjectId ?: return
        generateStrokeViaSeedream(
            objectId = objectId,
            outputMode = AigcOutputMode.IMAGE,
            seedreamExtraPrompt = promptDetails(
                template = strokeToSeedreamPromptTemplate,
                useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
                userPrompt = strokeToSeedreamPromptExtra,
            ),
            closeImagePanelOnStart = true,
        )
    }

    fun generateSelectedStrokeSeedreamModel() {
        val objectId = strokeToModelObjectId ?: return
        val options = RodinGenerationOptions(
            tier = strokeToModelTier,
            qualityOverride = strokeToModelQualityOverride,
            meshMode = strokeToModelMeshMode,
            hdTexture = strokeToModelHdTexture,
            creative = strokeToModelCreative,
        )
        generateStrokeViaSeedream(
            objectId = objectId,
            outputMode = AigcOutputMode.MODEL,
            seedreamExtraPrompt = promptDetails(
                template = strokeToModelPromptTemplate,
                useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
                userPrompt = strokeToModelPromptExtra,
            ),
            closeImagePanelOnStart = false,
            closeModelPanelOnStart = true,
            rodinPrompt = templatedPrompt(
                template = strokeToModelPromptTemplate,
                useCase = AigcPromptUseCase.STROKE_TO_MODEL,
                userPrompt = strokeToModelPromptExtra,
            ),
            modelOptions = options,
        )
    }

    LaunchedEffect(showRepository) {
        val result = withContext(Dispatchers.IO) { showRepository.load() }
        result
            .onSuccess { saved ->
                vm.restoreSavedState(saved)
                val needsCuriosityRestore = saved.shows.any { show ->
                    val isCuriosityExample = show.name == "把好奇号装起来" ||
                        show.name.startsWith("把好奇号装起来_")
                    if (!isCuriosityExample) return@any false
                    val stagedLeftX = show.pages.getOrNull(2)
                        ?.states
                        ?.get("suspension_left")
                        ?.transform
                        ?.px
                    val stagedRightX = show.pages.getOrNull(2)
                        ?.states
                        ?.get("suspension_right")
                        ?.transform
                        ?.px
                    show.name != "把好奇号装起来" ||
                        "nasa_source_page" !in show.objects ||
                        "rover_animated_clean" !in show.objects ||
                        show.objects["curiosity_title_1"]?.source != "写在前面" ||
                        show.objects["curiosity_title_1"]?.textBackgroundEnabled != true ||
                        show.pages.getOrNull(5)?.annotations?.any { it.id == "p5_right" } != true ||
                        stagedLeftX == null ||
                        stagedRightX == null ||
                        stagedLeftX < 0.5f ||
                        stagedRightX > -0.5f
                }
                if (needsCuriosityRestore) {
                    withContext(Dispatchers.IO) {
                        CuriosityExampleAssetInstaller.install(context.applicationContext)
                    }.onSuccess(vm::upgradeLegacyCuriosityExample)
                }
                storageReady = true
                if (saved.skippedFileCount > 0) {
                    withContext(Dispatchers.IO) { showRepository.save(vm.savedState()) }
                        .onFailure { vm.reportStorageError(it.message ?: it.javaClass.simpleName) }
                }
            }
            .onFailure {
                storageReady = true
                vm.reportStorageError(it.message ?: it.javaClass.simpleName)
            }
    }

    LaunchedEffect(storageReady, vm.saveRevision) {
        if (!storageReady) return@LaunchedEffect
        val snapshot = vm.savedState()
        withContext(Dispatchers.IO) { showRepository.save(snapshot) }
            .onFailure { vm.reportStorageError(it.message ?: it.javaClass.simpleName) }
    }

    LaunchedEffect(vm.screen, vm.mode, vm.panel) {
        val shouldShowChrome = vm.screen == AppScreen.EDITOR &&
            vm.mode == AppMode.EDIT &&
            !workspaceOverlayOpen(vm.panel)
        if (shouldShowChrome) {
            chromeVisibleForAnimation = true
            chromeProgress.animateTo(1.06f, animationSpec = tween(180))
            chromeProgress.animateTo(1f, animationSpec = tween(90))
        } else if (chromeVisibleForAnimation) {
            chromeProgress.animateTo(0f, animationSpec = tween(160))
            chromeVisibleForAnimation = false
        }
    }

    LaunchedEffect(vm.screen, vm.mode, vm.panel) {
        val shouldShow = vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && vm.panel == StudioPanel.ASSETS
        if (shouldShow) {
            assetPanelVisibleForAnimation = true
            assetPanelProgress.animateTo(1.04f, animationSpec = tween(190))
            assetPanelProgress.animateTo(1f, animationSpec = tween(90))
        } else if (assetPanelVisibleForAnimation) {
            assetPanelProgress.animateTo(0f, animationSpec = tween(165))
            assetPanelVisibleForAnimation = false
        }
    }

    LaunchedEffect(vm.screen, vm.mode, agentPanelOpen) {
        val shouldShow = vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && agentPanelOpen
        if (shouldShow) {
            agentPanelVisibleForAnimation = true
            agentPanelInteractive = false
            // EaseOutBack curve — standard overshoot easing used by most
            // platform panel transitions (iOS spring-like, Material
            // Expressive, Framer default).  Single clean overshoot with no
            // oscillation, feels snappy but has weight.
            agentPanelProgress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = CubicBezierEasing(0.28f, 1.4f, 0.58f, 1f),
                ),
            )
            agentPanelInteractive = true
        } else if (agentPanelVisibleForAnimation) {
            agentPanelInteractive = false
            // EaseInBack — symmetric pair to the enter curve.  Accelerates
            // inward with a small overshoot past 0 (the panel "sucks in"
            // slightly before disappearing), feels snappy and intentional.
            agentPanelProgress.animateTo(
                0f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = CubicBezierEasing(0.42f, 0f, 0.58f, -0.4f),
                ),
            )
            agentPanelVisibleForAnimation = false
        } else {
            agentPanelInteractive = false
        }
    }

    LaunchedEffect(vm.selectedObjectId, vm.screen, vm.mode) {
        geometryColorPickerOpen = false
        val selectedId = vm.selectedObjectId
        if (imageSubjectPreprocessChoiceObjectId != null && imageSubjectPreprocessChoiceObjectId != selectedId) {
            imageSubjectPreprocessChoiceObjectId = null
        }
        if (textToImageObjectId != null && textToImageObjectId != selectedId) {
            textToImageObjectId = null
        }
        if (strokeToSeedreamObjectId != null && strokeToSeedreamObjectId != selectedId) {
            strokeToSeedreamObjectId = null
        }
        if (strokeToModelObjectId != null && strokeToModelObjectId != selectedId) {
            strokeToModelObjectId = null
        }
    }

    LaunchedEffect(vm.selectedObjectId, vm.screen, vm.mode, vm.revision, imageToModelBusyObjectIds) {
        val objectId = imageToModelConfirmObjectId ?: return@LaunchedEffect
        val target = vm.document.objects[objectId]
        val stillCurrentImage = vm.screen == AppScreen.EDITOR &&
            vm.mode == AppMode.EDIT &&
            vm.selectedObjectId == objectId &&
            target?.type == ObjectType.IMAGE &&
            !target.isLocked
        if (!stillCurrentImage) {
            imageToModelConfirmObjectId = null
        }
    }

    LaunchedEffect(vm.selectedObjectId, vm.screen, vm.mode, vm.revision, strokeToModelBusyObjectIds) {
        val objectId = strokeToModelObjectId ?: return@LaunchedEffect
        val target = vm.document.objects[objectId]
        val stillCurrentStroke = vm.screen == AppScreen.EDITOR &&
            vm.mode == AppMode.EDIT &&
            vm.selectedObjectId == objectId &&
            target?.type == ObjectType.STROKE &&
            !target.isLocked
        if (!stillCurrentStroke) {
            strokeToModelObjectId = null
        }
    }

    LaunchedEffect(vm.revision, vm.animationDraft, scene, vm.screen) {
        if (vm.screen == AppScreen.HOME) {
            scene?.setWorkspaceVisible(false)
            lastPageRevision = vm.pageRevision
            return@LaunchedEffect
        }
        val animatePageChange = vm.pageRevision != lastPageRevision || !editorSceneSyncedOnce
        if (!vm.objectManipulationInProgress) {
            scene?.sync(animate = animatePageChange)
            editorSceneSyncedOnce = true
            lastPageRevision = vm.pageRevision
            delay(350L)
            scene?.prewarmAdjacentPages()
        }
        lastPageRevision = vm.pageRevision
    }

    LaunchedEffect(scene, vm.activeViewpointTransform, vm.viewpointModeObjectId) {
        val viewerForward = viewerPose.rotation?.rotateVector(Vector3(0f, 0f, -1f))
        scene?.syncViewpointMode(viewerPose.position, viewerForward)
    }

    LaunchedEffect(scene, vm.sceneEnvironmentMode, vm.screen, vm.pageRevision) {
        try {
            scene?.syncEnvironmentMode(
                mode = vm.sceneEnvironmentMode,
                visible = vm.screen == AppScreen.EDITOR || vm.screen == AppScreen.HOME,
                userSkyboxVisible = vm.screen == AppScreen.EDITOR,
            )
        } finally {
            homeEnvironmentSwitchBusy = false
        }
    }

    LaunchedEffect(scene) {
        if (scene == null) return@LaunchedEffect
        val provider = HMDTrackingProvider()
        val startResult = provider.start()
        Log.i(HAND_STROKE_TAG, "HMD tracking start=$startResult support=${provider.supportState}")
        try {
            provider.dataFlow.collect { trackingData ->
                val position = trackingData.hmdPose.position.takeIf { it.isFinite() }
                val rotation = trackingData.hmdPose.rotation
                viewerPose.position = position
                viewerPose.rotation = rotation
            }
        } finally {
            viewerPose.position = null
            viewerPose.rotation = null
            provider.stop()
            Log.i(HAND_STROKE_TAG, "HMD tracking stopped")
        }
    }

    LaunchedEffect(scene, vm.screen) {
        val rift = homeRiftController ?: return@LaunchedEffect
        if (vm.screen != AppScreen.HOME) {
            homeRiftSession.cancel()
            homeRiftCapturing = false
            rift.hide()
            return@LaunchedEffect
        }
        val provider = HandTrackingProvider()
        provider.start()
        try {
            provider.dataFlow.collect { trackingData ->
                val pinchCandidates = trackingData.pinchCandidates()
                if (pinchCandidates.hasTwoHandRiftPinch()) {
                    homeRiftClickGuard.suppressFor(HOME_RIFT_CLICK_SUPPRESS_NANOS)
                }
                val homeFloatingGazeTargeted = if (pinchCandidates.hasAnyHomeRiftPinch()) {
                    val viewerPosition = viewerPose.position
                    val viewerRotation = viewerPose.rotation
                    viewerPosition != null && viewerRotation != null &&
                        homeFloatingModels?.isViewerTargetingModel(viewerPosition, viewerRotation) == true
                } else {
                    false
                }
                val pageGestureBlocked = homePageGestureActive ||
                    System.nanoTime() < homePageGestureBlockedUntilNanos
                if (!homeRiftSession.isLocked && (
                    pageGestureBlocked ||
                    workspaceDragPointerActive ||
                    panelDragState.active ||
                    homeFloatingGazeTargeted ||
                    homeFloatingTargetPointerActive ||
                    homeFloatingManipulationActive ||
                    vm.objectManipulationInProgress
                )) {
                    homeRiftSession.rejectCurrentGesture()
                    homeRiftCapturing = false
                    homeRiftTransitioning = false
                    rift.hide()
                    return@collect
                }
                when (val event = homeRiftSession.update(pinchCandidates)) {
                    is HomeEnvironmentRiftEvent.Tearing -> {
                        homeRiftCapturing = true
                        homeRiftTransitioning = event.progress >= HOME_RIFT_CHROME_HIDE_PROGRESS
                        rift.show(event.left, event.right, viewerPose.position, event.progress, vm.sceneEnvironmentMode == SceneEnvironmentMode.REAL)
                    }
                    is HomeEnvironmentRiftEvent.Commit -> {
                        homeRiftCapturing = true
                        homeRiftTransitioning = true
                        val targetIsVirtual = vm.sceneEnvironmentMode == SceneEnvironmentMode.REAL
                        rift.show(event.left, event.right, viewerPose.position, 1f, targetIsVirtual, expansionProgress = 0f)
                        val expansionStartNanos = withFrameNanos { it }
                        var expansionProgress = 0f
                        while (expansionProgress < 1f && isActive) {
                            val frameNanos = withFrameNanos { it }
                            expansionProgress = (
                                (frameNanos - expansionStartNanos).toFloat() /
                                    (HOME_RIFT_COMMIT_EXPAND_MS * 1_000_000f)
                                ).coerceIn(0f, 1f)
                            rift.show(
                                event.left,
                                event.right,
                                viewerPose.position,
                                1f,
                                targetIsVirtual,
                                expansionProgress = expansionProgress,
                            )
                        }
                        requestHomeEnvironmentToggle()
                        delay(HOME_RIFT_POST_SWITCH_HOLD_MS)
                        rift.hide()
                        homeRiftTransitioning = false
                        homeRiftCapturing = false
                    }
                    HomeEnvironmentRiftEvent.Heal -> {
                        delay(HOME_RIFT_HEAL_MS)
                        rift.hide()
                        homeRiftTransitioning = false
                        homeRiftCapturing = false
                    }
                    HomeEnvironmentRiftEvent.None -> Unit
                }
            }
        } finally {
            homeRiftSession.cancel()
            rift.hide()
            homeRiftCapturing = false
            homeRiftTransitioning = false
            provider.stop()
        }
    }

    LaunchedEffect(homeRiftTransitioning) {
        homeRiftSceneProgress.animateTo(
            targetValue = if (homeRiftTransitioning) 0f else 1f,
            animationSpec = tween(if (homeRiftTransitioning) HOME_RIFT_CHROME_EXIT_MS.toInt() else HOME_RIFT_SCENE_SETTLE_MS.toInt()),
        )
    }

    val strokePreviewPaths = vm.strokeDraft?.paths
    val strokePreviewColor = vm.strokeDraft?.selectedColor
    LaunchedEffect(scene, strokePreviewPaths, strokePreviewColor, vm.panel) {
        if (vm.strokeDraft != null || vm.panel == StudioPanel.STROKE) {
            scene?.syncStrokeDraft()
        }
    }

    LaunchedEffect(scene, vm.screen, vm.mode, vm.panel) {
        val controller = scene ?: return@LaunchedEffect
        if (vm.screen != AppScreen.EDITOR || vm.mode != AppMode.EDIT || vm.panel != StudioPanel.STROKE) {
            return@LaunchedEffect
        }
        val provider = HandTrackingProvider()
        val session = PinchStrokeSession()
        val startResult = provider.start()
        Log.i(HAND_STROKE_TAG, "Hand stroke tracking start=$startResult support=${provider.supportState}")
        try {
            provider.dataFlow.collect { trackingData ->
                if (!isActive || vm.screen != AppScreen.EDITOR || vm.mode != AppMode.EDIT || vm.panel != StudioPanel.STROKE) {
                    return@collect
                }
                if (vm.strokeDraft == null) {
                    session.finish(controller, vm)
                    return@collect
                }
                session.update(
                    candidates = trackingData.pinchCandidates(),
                    scene = controller,
                    vm = vm,
                    uiPointerActive = latestUiPointerActive,
                    lastUiPointerNanos = latestLastUiPointerNanos,
                    workspaceDragPointerActive = latestWorkspaceDragPointerActive,
                    lastWorkspaceDragPointerNanos = latestLastWorkspaceDragPointerNanos,
                    workspaceDragHandleEntity = latestWorkspaceDragHandle,
                )
            }
        } finally {
            session.finish(controller, vm)
            provider.stop()
            Log.i(HAND_STROKE_TAG, "Hand stroke tracking stopped")
        }
    }

    val annotationDraftPath = vm.annotationStrokeDraft?.activePath
    val annotationDraftColor = vm.annotationStrokeDraft?.selectedColor
    val annotationCommentDraft = vm.annotationCommentDraft
    val annotationCount = vm.currentPage.annotations.size
    LaunchedEffect(
        scene,
        annotationDraftPath,
        annotationDraftColor,
        annotationCommentDraft,
        annotationCount,
        vm.annotationsVisible,
        vm.currentPageIndex,
        vm.editingAnnotationComment?.id,
    ) {
        scene?.syncPageAnnotations()
        scene?.syncAnnotationDraft()
    }

    LaunchedEffect(scene, vm.screen, vm.mode, vm.presentAnnotationTool, vm.whiteboardTool) {
        val controller = scene ?: return@LaunchedEffect
        val trackingMode = when {
            vm.screen == AppScreen.EDITOR &&
                vm.mode == AppMode.PRESENT &&
                vm.presentAnnotationTool != PresentAnnotationTool.NONE &&
                vm.presentAnnotationTool != PresentAnnotationTool.ERASER -> AnnotationTrackingMode.PRESENT
            vm.screen == AppScreen.EDITOR &&
                vm.mode == AppMode.EDIT &&
                vm.whiteboardTool == WhiteboardTool.COMMENT -> AnnotationTrackingMode.WHITEBOARD_COMMENT
            else -> null
        }
        if (trackingMode == null) {
            return@LaunchedEffect
        }
        val provider = HandTrackingProvider()
        val session = SpatialAnnotationInputSession(trackingMode)
        val startResult = provider.start()
        Log.i(HAND_STROKE_TAG, "Spatial annotation tracking mode=$trackingMode start=$startResult support=${provider.supportState}")
        try {
            provider.dataFlow.collect { trackingData ->
                val stillActive = when (trackingMode) {
                    AnnotationTrackingMode.PRESENT -> vm.screen == AppScreen.EDITOR &&
                        vm.mode == AppMode.PRESENT &&
                        vm.presentAnnotationTool != PresentAnnotationTool.NONE &&
                        vm.presentAnnotationTool != PresentAnnotationTool.ERASER
                    AnnotationTrackingMode.WHITEBOARD_COMMENT -> vm.screen == AppScreen.EDITOR &&
                        vm.mode == AppMode.EDIT &&
                        vm.whiteboardTool == WhiteboardTool.COMMENT
                }
                if (
                    !isActive ||
                    !stillActive
                ) {
                    return@collect
                }
                session.update(
                    candidates = trackingData.pinchCandidates(),
                    scene = controller,
                    vm = vm,
                    uiPointerActive = latestUiPointerActive,
                    lastUiPointerNanos = latestLastUiPointerNanos,
                )
            }
        } finally {
            session.finish(controller, vm)
            provider.stop()
            Log.i(HAND_STROKE_TAG, "Spatial annotation tracking stopped")
        }
    }

    LaunchedEffect(scene, vm.screen, vm.mode, vm.panel, vm.selectedObjectId, vm.animationFrozenObjectId, vm.animationsPaused) {
        val controller = scene ?: return@LaunchedEffect
        while (isActive && vm.screen == AppScreen.EDITOR) {
            val frameNanos = withFrameNanos { it }
            controller.tickPageAnimations(
                frameTimeSeconds = frameNanos / 1_000_000_000f,
                paused = vm.mode == AppMode.EDIT && vm.animationsPaused,
                frozenObjectId = vm.animationFrozenObjectId.takeIf { vm.mode == AppMode.EDIT },
            )
            if (vm.mode == AppMode.EDIT && vm.animationDraft != null) {
                controller.flushAnimationEndpointChrome()
                listOf(SELECTION_ID, SELECTION_POPUP_ID, ANIMATION_START_CONTROLS_ID, ANIMATION_END_CONTROLS_ID).forEach { panelId ->
                    val entity = panelEntities[panelId] ?: return@forEach
                    setPanelPosition(
                        entity,
                        panelPosition(
                            panelId = panelId,
                            workspacePosition = workspacePosition,
                            homePosition = homePosition,
                            scene = scene,
                            selectedObjectId = vm.selectedObjectId,
                            selectionPopupObjectId = activeSelectionPopupObjectId,
                            mode = vm.mode,
                            panel = vm.panel,
                            selectedAssetCategory = selectedAssetCategory,
                            screen = vm.screen,
                            chromeVisible = chromeVisibleForAnimation,
                            chromeProgress = chromeProgress.value,
                            assetPanelVisible = assetPanelVisibleForAnimation,
                            assetPanelProgress = assetPanelProgress.value,
                            toolbarMoreOpen = toolbarMoreOpen,
                            homeActionShowIndex = homeActionShowIndex,
                            homeDeleteShowIndex = homeDeleteShowIndex,
                            homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                            homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                            aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                            agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                            agentToolbarExpanded = agentToolbarExpanded,
                            agentToolChainOpen = agentToolChainOpen,
                            agentPanelProgress = agentPanelProgress.value,
                            agentSuggestionsVisible = agentSuggestionsVisible,
                            commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                        ),
                        viewerPose.position,
                    )
                }
            }
        }
    }

    LaunchedEffect(
        panelEntities,
        workspaceDragHandle,
        scene,
        aigcKeyConfigPromptOpen,
        agentPanelOpen,
        imageToModelConfirmObjectId,
        strokeToModelObjectId,
        activeSelectionPopupObjectId,
    ) {
        if (scene == null) return@LaunchedEffect
        while (isActive) {
            delay(UI_LOOK_AT_UPDATE_INTERVAL_MS)
            if (panelDragState.active) continue
            val nowNanos = System.nanoTime()
            val uiPointerRecentlyActive = latestUiPointerActive ||
                (
                    latestLastUiPointerNanos > 0L &&
                        nowNanos - latestLastUiPointerNanos < UI_LOOK_AT_POINTER_FREEZE_NANOS
                )
            if (uiPointerRecentlyActive) continue
            val viewer = viewerPose.position
            panelEntities.forEach { (panelId, entity) ->
                if (!entity.enabled) return@forEach
                val placement = panelPosition(
                    panelId = panelId,
                    workspacePosition = workspacePosition,
                    homePosition = homePosition,
                    scene = scene,
                    selectedObjectId = vm.selectedObjectId,
                    selectionPopupObjectId = activeSelectionPopupObjectId,
                    mode = vm.mode,
                    panel = vm.panel,
                    selectedAssetCategory = selectedAssetCategory,
                    screen = vm.screen,
                    chromeVisible = chromeVisibleForAnimation,
                    chromeProgress = chromeProgress.value,
                    assetPanelVisible = assetPanelVisibleForAnimation,
                    assetPanelProgress = assetPanelProgress.value,
                    toolbarMoreOpen = toolbarMoreOpen,
                    homeActionShowIndex = homeActionShowIndex,
                    homeDeleteShowIndex = homeDeleteShowIndex,
                    homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                    homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                    aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                    agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                    agentToolbarExpanded = agentToolbarExpanded,
                    agentToolChainOpen = agentToolChainOpen,
                    agentPanelProgress = agentPanelProgress.value,
                    agentSuggestionsVisible = agentSuggestionsVisible,
                    commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                ) ?: return@forEach
                if (!placement.faceViewer) return@forEach
                entity.components[TransformComponent::class.java]?.setEulerAngles(
                    viewerFacingRotationFor(placement.position.toVector3(), viewer)
                )
            }
            val dragHandlePosition = workspaceDragHandlePosition(
                vm.screen,
                workspacePosition,
                homePosition,
                agentPanelOpen || agentPanelVisibleForAnimation,
                agentToolbarExpanded,
                agentPanelProgress.value,
                agentSuggestionsVisible,
            )
            workspaceDragHandle?.components[TransformComponent::class.java]?.setEulerAngles(
                viewerFacingRotationFor(dragHandlePosition.toVector3(), viewer)
            )
        }
    }

    LaunchedEffect(homeFloatingModels, vm.screen) {
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            homeFloatingModels?.update(
                homePosition = liveHomePosition.value,
                visible = vm.screen == AppScreen.HOME,
                viewer = viewerPose.position,
                frameTimeSeconds = frameNanos / 1_000_000_000f,
                followHomeOnly = vm.screen == AppScreen.HOME && panelDragState.active,
            )
            homeFloatingModels?.applyRiftTransitionScale(homeRiftSceneProgress.value)
        }
    }

    LaunchedEffect(
        workspacePosition,
        homePosition,
        panelEntities,
        scene?.sceneVersion,
        vm.selectedObjectId,
        vm.revision,
        vm.mode,
        vm.viewpointModeObjectId,
        vm.panel,
        vm.screen,
        chromeVisibleForAnimation,
        chromeProgress.value,
        assetPanelVisibleForAnimation,
        assetPanelProgress.value,
        selectedAssetCategory,
        assetScrollState.value,
        assetPreviewSlots,
        geometryColorPickerOpen,
        toolbarMoreOpen,
        homeActionShowIndex,
        homeDeleteShowIndex,
        homeFloatingSelectedId,
        homeFloatingRevision,
        homeFloatingManipulationActive,
        aigcKeyConfigPromptOpen,
        agentPanelOpen,
        agentToolbarExpanded,
        agentToolChainOpen,
        agentPanelVisibleForAnimation,
        agentPanelProgress.value,
        agentSuggestionsVisible,
        imageToModelConfirmObjectId,
        strokeToModelObjectId,
        activeSelectionPopupObjectId,
        vm.editingAnnotationComment?.id,
        vm.editingAnnotationComment?.panelPoint,
    ) {
        panelEntities.forEach { (panelId, entity) ->
            setPanelPosition(
                entity,
                panelPosition(
                    panelId = panelId,
                    workspacePosition = workspacePosition,
                    homePosition = homePosition,
                    scene = scene,
                    selectedObjectId = vm.selectedObjectId,
                    selectionPopupObjectId = activeSelectionPopupObjectId,
                    mode = vm.mode,
                    panel = vm.panel,
                    selectedAssetCategory = selectedAssetCategory,
                    screen = vm.screen,
                    chromeVisible = chromeVisibleForAnimation,
                    chromeProgress = chromeProgress.value,
                    assetPanelVisible = assetPanelVisibleForAnimation,
                    assetPanelProgress = assetPanelProgress.value,
                    toolbarMoreOpen = toolbarMoreOpen,
                    homeActionShowIndex = homeActionShowIndex,
                    homeDeleteShowIndex = homeDeleteShowIndex,
                    homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                    homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                    aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                    agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                    agentToolbarExpanded = agentToolbarExpanded,
                    agentToolChainOpen = agentToolChainOpen,
                    agentPanelProgress = agentPanelProgress.value,
                    agentSuggestionsVisible = agentSuggestionsVisible,
                    commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                ),
                viewerPose.position,
            )
        }
        applyDragHandleTransform(
            entity = workspaceDragHandle,
            screen = vm.screen,
            workspace = workspacePosition,
            home = homePosition,
            agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
            agentToolbarExpanded = agentToolbarExpanded,
            agentPanelProgress = agentPanelProgress.value,
            agentSuggestionsVisible = agentSuggestionsVisible,
            viewer = viewerPose.position,
        )
        workspaceDragHandle?.enabled = when (vm.screen) {
            AppScreen.HOME -> true
            AppScreen.EDITOR -> vm.viewpointModeObjectId == null &&
                (vm.mode == AppMode.EDIT || vm.mode == AppMode.PRESENT)
        }
        updateAssetPreviewEntities(
            previews = assetPreviewEntities,
            slots = assetPreviewSlots,
            panelProgress = assetPanelProgress.value,
            visible = assetPanelVisibleForAnimation &&
                assetPanelProgress.value > 0.05f &&
                selectedAssetCategory == PresetCategory.PRESET_MODEL &&
                vm.screen == AppScreen.EDITOR &&
                vm.mode == AppMode.EDIT,
        )
    }

    LaunchedEffect(
        scene?.sceneVersion,
        vm.selectedObjectId,
        vm.previewRevision,
        vm.mode,
        vm.panel,
        vm.screen,
        chromeVisibleForAnimation,
        chromeProgress.value,
        activeSelectionPopupObjectId,
    ) {
        listOf(SELECTION_ID, SELECTION_POPUP_ID, ANIMATION_START_CONTROLS_ID, ANIMATION_END_CONTROLS_ID).forEach { panelId ->
            val entity = panelEntities[panelId] ?: return@forEach
            setPanelPosition(
                entity,
                panelPosition(
                    panelId = panelId,
                    workspacePosition = workspacePosition,
                    homePosition = homePosition,
                    scene = scene,
                    selectedObjectId = vm.selectedObjectId,
                    selectionPopupObjectId = activeSelectionPopupObjectId,
                    mode = vm.mode,
                    panel = vm.panel,
                    selectedAssetCategory = selectedAssetCategory,
                    screen = vm.screen,
                    chromeVisible = chromeVisibleForAnimation,
                    chromeProgress = chromeProgress.value,
                    assetPanelVisible = assetPanelVisibleForAnimation,
                    assetPanelProgress = assetPanelProgress.value,
                    toolbarMoreOpen = toolbarMoreOpen,
                    homeActionShowIndex = homeActionShowIndex,
                    homeDeleteShowIndex = homeDeleteShowIndex,
                    homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                    homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                    aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                    agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                    agentToolbarExpanded = agentToolbarExpanded,
                    agentToolChainOpen = agentToolChainOpen,
                    agentPanelProgress = agentPanelProgress.value,
                    agentSuggestionsVisible = agentSuggestionsVisible,
                    commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                ),
                viewerPose.position,
            )
        }
    }

    fun movePanels(panelIds: Collection<String>, workspace: PanelPosition, home: PanelPosition) {
        val viewer = panelDragState.viewerPosition ?: viewerPose.position
        panelIds.forEach { panelId ->
            val entity = panelEntities[panelId] ?: return@forEach
            setPanelPosition(
                entity,
                panelPosition(
                    panelId = panelId,
                    workspacePosition = workspace,
                    homePosition = home,
                    scene = scene,
                    selectedObjectId = vm.selectedObjectId,
                    selectionPopupObjectId = activeSelectionPopupObjectId,
                    mode = vm.mode,
                    panel = vm.panel,
                    selectedAssetCategory = selectedAssetCategory,
                    screen = vm.screen,
                    chromeVisible = chromeVisibleForAnimation,
                    chromeProgress = chromeProgress.value,
                    assetPanelVisible = assetPanelVisibleForAnimation,
                    assetPanelProgress = assetPanelProgress.value,
                    toolbarMoreOpen = toolbarMoreOpen,
                    homeActionShowIndex = homeActionShowIndex,
                    homeDeleteShowIndex = homeDeleteShowIndex,
                    homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                    homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                    aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                    agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                    agentToolbarExpanded = agentToolbarExpanded,
                    agentToolChainOpen = agentToolChainOpen,
                    agentPanelProgress = agentPanelProgress.value,
                    agentSuggestionsVisible = agentSuggestionsVisible,
                    commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                ),
                viewer,
            )
        }
    }

    fun previewPanelGroup(position: PanelPosition) {
        if (!panelDragState.active) {
            panelDragState.active = true
            panelDragState.viewerPosition = viewerPose.position
        }
        val viewer = panelDragState.viewerPosition ?: viewerPose.position
        val isHome = vm.screen == AppScreen.HOME
        if (isHome) {
            liveHomePosition.value = position
            movePanels(HomeMovablePanelIds, workspacePosition, position)
        } else {
            movePanels(workspaceMovablePanelIds(vm.mode, vm.panel), position, homePosition)
        }
        applyDragHandleTransform(
            entity = workspaceDragHandle,
            screen = vm.screen,
            workspace = if (isHome) workspacePosition else position,
            home = if (isHome) position else homePosition,
            agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
            agentToolbarExpanded = agentToolbarExpanded,
            agentPanelProgress = agentPanelProgress.value,
            agentSuggestionsVisible = agentSuggestionsVisible,
            viewer = viewer,
        )
        if (!isHome && vm.mode == AppMode.EDIT && vm.panel == StudioPanel.ASSETS) {
            updateAssetPreviewEntities(
                previews = assetPreviewEntities,
                slots = assetPreviewSlots,
                panelProgress = assetPanelProgress.value,
                visible = assetPanelVisibleForAnimation &&
                    assetPanelProgress.value > 0.05f &&
                    selectedAssetCategory == PresetCategory.PRESET_MODEL,
            )
        }
    }

    fun commitPanelGroup(position: PanelPosition) {
        if (vm.screen == AppScreen.HOME) {
            homePosition = position
            liveHomePosition.value = position
        } else {
            workspacePosition = position
        }
        panelDragState.active = false
        panelDragState.viewerPosition = null
    }

    SpatialView(
        modifier = Modifier
            .trackStudioUiPointer(context) {
                uiPointerActive = it
                lastUiPointerNanos = System.nanoTime()
            }
            .homeFloatingModelManipulatable(
                context,
                { if (homeRiftCapturing) null else homeFloatingModels },
                { homeFloatingSelectedId = it },
                { homeFloatingSelectedId },
                { homeFloatingTargetPointerActive = it },
                { homeFloatingManipulationActive = it },
                { homeFloatingRevision++ },
            )
            .selectable(
                context,
                vm,
                { scene },
                onIgnoredEntityTap = { entity ->
                    if (
                        vm.viewpointModeObjectId != null &&
                        entity.isEntityOrChildOfPanel(TOOLBAR_ID)
                    ) {
                        vm.exitViewpoint()
                        true
                    } else {
                        homeFloatingModels?.idFor(entity)?.let {
                            homeFloatingSelectedId = it
                            homeFloatingRevision++
                            true
                        } == true
                    }
                },
            )
            .manipulatable(context, vm, { scene }, { vm.selectedObjectId?.let { scene?.entityFor(it) } })
            .workspaceDraggable(
                context,
                { if (homeRiftCapturing) null else workspaceDragHandle },
                { if (vm.screen == AppScreen.HOME) homePosition else workspacePosition },
                ::previewPanelGroup,
                ::commitPanelGroup,
                onWorkspaceDragActiveChanged = {
                    workspaceDragPointerActive = it
                    lastWorkspaceDragPointerNanos = System.nanoTime()
                },
            ),
        initial = { content, attachments ->
            val controller = SceneController(content, vm)
            scene = controller
            controller.setWorkspaceVisible(false)
            homeRiftController = HomeEnvironmentRiftController(content).also { rift ->
                rift.prepare()
                scope.launch {
                    controller.prewarmVirtualEnvironment()
                    controller.cloneVirtualEnvironmentForPortal()?.let(rift::setVirtualEnvironmentPreview)
                }
            }
            val floatingModels = HomeFloatingModels()
            homeFloatingModels = floatingModels
            scope.launch {
                floatingModels.load(content)
            }
            workspaceDragHandle = createWorkspaceDragHandle().also { content.addEntity(it) }
            panelEntities = PanelIds.mapNotNull { panelId ->
                val entity = attachments.entity(panelId) ?: return@mapNotNull null
                attachPanel(
                    content,
                    entity,
                    panelId,
                    panelPosition(
                        panelId = panelId,
                        workspacePosition = workspacePosition,
                        homePosition = homePosition,
                        scene = controller,
                        selectedObjectId = vm.selectedObjectId,
                        selectionPopupObjectId = activeSelectionPopupObjectId,
                        mode = vm.mode,
                        panel = vm.panel,
                        selectedAssetCategory = selectedAssetCategory,
                        screen = vm.screen,
                        chromeVisible = chromeVisibleForAnimation,
                        chromeProgress = chromeProgress.value,
                        assetPanelVisible = assetPanelVisibleForAnimation,
                        assetPanelProgress = assetPanelProgress.value,
                        toolbarMoreOpen = toolbarMoreOpen,
                        homeActionShowIndex = homeActionShowIndex,
                        homeDeleteShowIndex = homeDeleteShowIndex,
                        homeFloatingRotationPosition = homeFloatingModels?.rotationControlsPosition(homeFloatingSelectedId),
                        homeFloatingRotationScale = homeFloatingModels?.rotationControlsScale(homeFloatingSelectedId),
                        aigcKeyConfigOpen = aigcKeyConfigPromptOpen,
                        agentPanelOpen = agentPanelOpen || agentPanelVisibleForAnimation,
                        agentToolbarExpanded = agentToolbarExpanded,
                        agentToolChainOpen = agentToolChainOpen,
                        agentPanelProgress = agentPanelProgress.value,
                        agentSuggestionsVisible = agentSuggestionsVisible,
                        commentEditorPoint = vm.editingAnnotationComment?.panelPoint,
                    ),
                )
                panelId to entity
            }.toMap()
            assetPreviewEntities = createAssetPreviewEntities().also { previews ->
                panelEntities[ASSET_ID]?.let { assetPanel ->
                    previews.values.forEach { preview -> assetPanel.addChild(preview.root) }
                } ?: previews.values.forEach { preview -> content.addEntity(preview.root) }
                updateAssetPreviewEntities(
                    previews = previews,
                    slots = assetPreviewSlots,
                    panelProgress = assetPanelProgress.value,
                    visible = false,
                )
            }
        },
        attachments = {
            AttachmentPanel(id = TOOLBAR_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    (vm.mode == AppMode.EDIT || vm.viewpointModeObjectId != null)
                ) {
                    ToolbarPanel(
                        documentType = vm.document.documentType,
                        showAnimationPlayback = vm.currentPageHasAnimations,
                        animationsPaused = vm.animationsPaused,
                        viewpointActive = vm.viewpointModeObjectId != null,
                        sceneEnvironmentMode = vm.sceneEnvironmentMode,
                        whiteboardTool = vm.whiteboardTool,
                        assetsOpen = vm.panel == StudioPanel.ASSETS,
                        agentOpen = agentPanelOpen,
                        filesOpen = vm.panel == StudioPanel.FILES,
                        moreOpen = toolbarMoreOpen,
                        agentBusy = agentBusy,
                        agentKeyConfigured = doubaoApiKey.isNotBlank(),
                        agentPrompt = agentPrompt,
                        agentStatus = agentMessage,
                        agentStatusTone = agentMessageTone,
                        agentMessages = agentMessages,
                        agentTaskSteps = agentTaskSteps,
                        agentExpanded = agentToolbarExpanded,
                        agentToolChainOpen = agentToolChainOpen,
                        onAssets = {
                            closeAgentPanel()
                            vm.toggleAssets()
                        },
                        onAgent = { toggleAgentPanel() },
                        onToggleAgentExpanded = {
                            agentToolbarExpanded = !agentToolbarExpanded
                            if (!agentToolbarExpanded) {
                                agentToolChainOpen = false
                            }
                        },
                        onToggleAgentToolChain = {
                            agentToolChainOpen = !agentToolChainOpen
                        },
                        onAgentPromptChange = { agentPrompt = it },
                        onRunAgent = { promptText -> runAgentPrompt(inputOverride = promptText) },
                        onOpenAgentKeyConfig = { openAgentKeyConfig() },
                        onAddText = { vm.addTextFromToolbar() },
                        onAddViewpoint = { vm.addViewpointFromToolbar() },
                        onOpenStrokeTool = { vm.openStrokeTool() },
                        onToggleWhiteboardComment = { vm.toggleWhiteboardCommentTool() },
                        onExitViewpoint = { vm.exitViewpoint() },
                        onToggleAnimationPlayback = { vm.toggleAnimationPlayback() },
                        onToggleMore = {
                            toolbarMoreOpen = !toolbarMoreOpen
                        },
                    )
                }
            }
            AttachmentPanel(id = TOOLBAR_UNDO_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    !agentPanelOpen
                ) {
                    ToolbarUndoPanel(
                        canUndo = vm.canUndo,
                        canRedo = vm.canRedo,
                        onUndo = { vm.undo() },
                        onRedo = { vm.redo() },
                    )
                }
            }
            AttachmentPanel(id = TOOLBAR_MORE_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    toolbarMoreOpen
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.88f),
                        exit = fadeOut() + scaleOut(targetScale = 0.88f),
                    ) {
                        ToolbarMorePanel(
                            sceneEnvironmentMode = vm.sceneEnvironmentMode,
                            filesOpen = vm.panel == StudioPanel.FILES,
                            onHome = {
                                toolbarMoreOpen = false
                                vm.returnHome()
                            },
                            onFiles = {
                                toolbarMoreOpen = false
                                vm.toggleFiles()
                            },
                            onToggleSceneEnvironment = {
                                toolbarMoreOpen = false
                                vm.toggleSceneEnvironment()
                            },
                        )
                    }
                }
            }
            AttachmentPanel(id = PAGES_ID) {
                if (vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && vm.viewpointModeObjectId == null && vm.isPresentation) {
                    PageStripPanel(
                        currentPageIndex = vm.currentPageIndex,
                        pageCount = vm.pageCount,
                        maxPages = ShowDocument.MAX_PAGES,
                        onPresent = { vm.enterPresentMode() },
                        onSelectPage = { vm.goToPage(it) },
                        collapsed = pageStripCollapsed,
                        onToggleCollapsed = { pageStripCollapsed = !pageStripCollapsed },
                        onAddPage = { vm.addPage() },
                        onDuplicatePage = { vm.duplicateCurrentPage() },
                        onDeletePage = { vm.deleteCurrentPage() },
                    )
                }
            }
            AttachmentPanel(id = ANNOTATION_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    vm.currentPageHasAnnotations
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    ) {
                        AnnotationManagePanel(
                            annotationsVisible = vm.annotationsVisible,
                            hasStrokeAnnotations = vm.currentPageHasStrokeAnnotations,
                            comments = vm.currentPageCommentAnnotations,
                            onToggleVisible = { vm.toggleAnnotationsVisible() },
                            onDeleteStrokes = { vm.deleteCurrentPageStrokeAnnotations() },
                            onEditComment = { vm.editAnnotationComment(it) },
                            onDeleteComment = { vm.deleteAnnotationComment(it) },
                        )
                    }
                }
            }
            AttachmentPanel(id = COMMENT_ID) {
                val editingComment = vm.editingAnnotationComment
                if (vm.screen == AppScreen.EDITOR && editingComment != null) {
                    CommentEditorPanel(
                        currentText = editingComment.text,
                        focusKey = editingComment.id,
                        onChange = { vm.updateEditingAnnotationComment(it) },
                        onDelete = { vm.deleteAnnotationComment(editingComment.id) },
                        onClose = { vm.closeEditingAnnotationComment() },
                    )
                }
            }
            AttachmentPanel(id = WORKSPACE_DRAG_HANDLE_PANEL_ID) {
                AnimatedVisibility(
                    visible = (vm.screen == AppScreen.HOME && !homeRiftTransitioning) ||
                    (
                        vm.screen == AppScreen.EDITOR &&
                            vm.viewpointModeObjectId == null &&
                            (vm.mode == AppMode.EDIT || vm.mode == AppMode.PRESENT)
                        ),
                    enter = fadeIn() + scaleIn(initialScale = 0.82f),
                    exit = fadeOut() + scaleOut(targetScale = 0.82f),
                ) {
                    WorkspaceDragHandlePanel()
                }
            }
            AttachmentPanel(id = HOME_ENVIRONMENT_ID) {
                AnimatedVisibility(
                    visible = vm.screen == AppScreen.HOME && !homeRiftTransitioning,
                    enter = fadeIn() + scaleIn(initialScale = 0.82f),
                    exit = fadeOut() + scaleOut(targetScale = 0.82f),
                ) {
                    HomeEnvironmentTogglePanel(
                        sceneEnvironmentMode = vm.sceneEnvironmentMode,
                        loading = homeEnvironmentSwitchBusy,
                        gazeArmed = false,
                        onToggleSceneEnvironment = {
                            // A two-hand pinch aimed at this attachment also produces a normal
                            // Compose click. Give tracking a short priority window before using
                            // the legacy one-hand/click fallback.
                            scope.launch {
                                delay(HOME_RIFT_CLICK_GUARD_MS)
                                if (!homeRiftCapturing && !homeRiftClickGuard.isSuppressed()) {
                                    requestHomeEnvironmentToggle()
                                }
                            }
                        },
                    )
                }
            }
            AttachmentPanel(id = HOME_FLOATING_ROTATION_ID) {
                AnimatedVisibility(
                    visible = vm.screen == AppScreen.HOME &&
                        homeFloatingSelectedId != null &&
                        !homeFloatingManipulationActive &&
                        !homeRiftTransitioning,
                    enter = fadeIn() + scaleIn(initialScale = 0.82f),
                    exit = fadeOut() + scaleOut(targetScale = 0.82f),
                ) {
                    RotationControlsPanel(
                        onRotateStart = {},
                        onRotate = { deltaRadians ->
                            homeFloatingModels?.rotate(homeFloatingSelectedId, deltaRadians)
                            homeFloatingRevision++
                        },
                        onRotateEnd = {
                            homeFloatingModels?.logCalibrationSnapshot()
                            homeFloatingRevision++
                        },
                    )
                }
            }
            AttachmentPanel(id = ASSET_ID) {
                if (assetPanelVisibleForAnimation) {
                    if (resourcePanelMode == ResourcePanelMode.SKYBOX) {
                        SkyboxPanel(
                            assets = skyboxAssets,
                            current = vm.currentPageSkybox,
                            onOpenObjects = {
                                selectedAssetCategory = PresetCategory.PRESET_MODEL
                                resourcePanelMode = ResourcePanelMode.PRESETS
                            },
                            onOpenAigc = {
                                if (!blockCloudAi()) {
                                    selectedAssetCategory = PresetCategory.AIGC
                                    resourcePanelMode = ResourcePanelMode.PRESETS
                                }
                            },
                            onOpenResource = { openSkyboxBrowser() },
                            onSelect = { asset: UserSkyboxAsset ->
                                UserSkyboxLibrary.touch(context, asset)
                                skyboxAssets = UserSkyboxLibrary.load(context)
                                vm.setCurrentPageSkybox(asset.source, asset.displayName)
                            },
                            onClear = { vm.clearCurrentPageSkybox() },
                            onRotateLeft = { vm.rotateCurrentPageSkybox(-SKYBOX_ROTATION_STEP_RADIANS) },
                            onRotateRight = { vm.rotateCurrentPageSkybox(SKYBOX_ROTATION_STEP_RADIANS) },
                            onResetRotation = { vm.resetCurrentPageSkyboxRotation() },
                            onClose = { vm.closePanel() },
                        )
                    } else {
                        AssetPanel(
                            presets = PresetLibrary.assets + aigcPresetAssets(),
                            selectedCategory = selectedAssetCategory,
                            scrollState = assetScrollState,
                            onSelectCategory = {
                                if (it != PresetCategory.AIGC || !blockCloudAi()) selectedAssetCategory = it
                            },
                            onPick = { vm.addPresetObject(it) },
                            onImportAsset = { openFileBrowser() },
                            onOpenSkyboxes = {
                                resourcePanelMode = ResourcePanelMode.SKYBOX
                                assetPreviewSlots = emptyMap()
                            },
                            onClose = { vm.closePanel() },
                            rodinKeyConfigured = rodinApiKey.isNotBlank(),
                            imageToImageKeyConfigured = imageToImageApiKey.isNotBlank(),
                            aigcOutputMode = aigcOutputMode,
                            aigcPrompt = aigcPrompt,
                            aigcPromptTemplate = aigcPromptTemplate,
                            aigcUseImageReference = aigcUseImageReference,
                            aigcReferenceImageName = aigcReferenceImageName,
                            aigcImagePreprocessMode = aigcImagePreprocessMode,
                            aigcTier = aigcTier,
                            aigcQualityOverride = aigcQualityOverride,
                            aigcMeshMode = aigcMeshMode,
                            aigcHdTexture = aigcHdTexture,
                            aigcCreative = aigcCreative,
                            aigcBusy = aigcBusy,
                            aigcMessage = aigcMessage,
                            onOpenAigcKeyConfig = { openAigcKeyConfig() },
                            onAigcOutputModeChange = { aigcOutputMode = it },
                            onAigcPromptChange = { aigcPrompt = it },
                            onAigcPromptTemplateChange = { aigcPromptTemplate = it },
                            onAigcUseImageReferenceChange = {
                                aigcUseImageReference = it
                                if (it) aigcPromptTemplate = null
                            },
                            onAigcImagePreprocessModeChange = { aigcImagePreprocessMode = it },
                            onPickAigcReferenceImage = { openAigcReferenceImageBrowser() },
                            onClearAigcReferenceImage = {
                                aigcReferenceImagePath = null
                                aigcReferenceImageName = null
                                aigcMessage = "已清除参考图。"
                            },
                            onAigcTierChange = { aigcTier = it },
                            onAigcMeshBudgetChange = { qualityOverride, meshMode ->
                                aigcQualityOverride = qualityOverride
                                aigcMeshMode = meshMode
                            },
                            onAigcHdTextureChange = { aigcHdTexture = it },
                            onAigcCreativeChange = { aigcCreative = it },
                            onGenerateAigc = { generateAigcModel() },
                            onPreviewSlotChanged = { assetId, slot ->
                                if (assetPreviewSlots[assetId] != slot) {
                                    assetPreviewSlots = assetPreviewSlots + (assetId to slot)
                                }
                            },
                        )
                    }
                }
            }
            AttachmentPanel(id = AIGC_LIBRARY_ID) {
                if (assetPanelVisibleForAnimation && selectedAssetCategory == PresetCategory.AIGC) {
                    AigcLibraryPanel(
                        imageAssets = aigcImagePresetAssets(),
                        modelAssets = aigcModelPresetAssets(),
                        records = aigcGenerationRecords,
                        onPick = { vm.addPresetObject(it) },
                        onRefresh = { refreshAigcLibrary() },
                    )
                }
            }
            AttachmentPanel(id = IMPORT_BROWSER_ID) {
                AnimatedVisibility(
                    visible = vm.mode == AppMode.EDIT && vm.panel == StudioPanel.IMPORT_BROWSER,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    ImportBrowserPanel(
                        title = importBrowserTitle,
                        currentPath = browserDirectory,
                        canGoUp = browserDirectory != null,
                        files = importCandidates,
                        message = importBrowserMessage,
                        onOpenDirectory = { refreshImportBrowser(it.path) },
                        onSelect = { importCandidate(it) },
                        onUp = { refreshImportBrowser(importBrowserParent) },
                        onRefresh = { refreshImportBrowser(browserDirectory) },
                        onClose = { vm.closePanel() },
                    )
                }
            }
            AttachmentPanel(id = FILE_ID) {
                AnimatedVisibility(
                    visible = vm.mode == AppMode.EDIT && vm.panel == StudioPanel.FILES,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    FilePanel(
                        shows = vm.showSummaries,
                        onUnavailableFeature = { blockCloudAi() },
                        rodinApiKey = rodinApiKey,
                        imageToImageApiKey = imageToImageApiKey,
                        doubaoApiKey = doubaoApiKey,
                        onNewShow = { vm.newShow(DocumentType.PRESENTATION) },
                        onNewWhiteboard = { vm.newShow(DocumentType.WHITEBOARD) },
                        onSwitchShow = { vm.switchShow(it) },
                        onRenameShow = { index, name -> vm.renameShow(index, name) },
                        onDeleteShow = { vm.deleteShow(it) },
                        onSaveAigcKeys = { rodin, imageToImage, doubao ->
                            saveAigcKeys(rodin, imageToImage, doubao)
                        },
                        onClose = { vm.closePanel() },
                    )
                }
            }
            AttachmentPanel(id = AIGC_KEY_CONFIG_ID) {
                AnimatedVisibility(
                    visible = aigcKeyConfigPromptOpen && vm.screen == AppScreen.EDITOR,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    AigcKeyConfigPanel(
                        rodinApiKey = rodinApiKey,
                        imageToImageApiKey = imageToImageApiKey,
                        doubaoApiKey = doubaoApiKey,
                        modifier = Modifier.width(430.dp),
                        title = aigcKeyConfigTitle,
                        message = aigcKeyConfigMessage,
                        requireRodinKey = aigcKeyConfigRequireRodin,
                        requireImageToImageKey = aigcKeyConfigRequireImage,
                        requireDoubaoKey = aigcKeyConfigRequireDoubao,
                        onSave = { rodin, imageToImage, doubao ->
                            saveAigcKeys(rodin, imageToImage, doubao)
                        },
                        onCancel = {
                            aigcKeyConfigPromptOpen = false
                            aigcKeyConfigRequireRodin = false
                            aigcKeyConfigRequireImage = false
                            aigcKeyConfigRequireDoubao = false
                        },
                    )
                }
            }
            AttachmentPanel(id = AGENT_CONVERSATION_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    agentPanelOpen
                ) {
                    AgentConversationDockPanel(
                        expanded = agentToolbarExpanded,
                        messages = agentMessages,
                        status = agentMessage,
                        statusTone = agentMessageTone,
                        taskSteps = agentTaskSteps,
                    )
                }
            }
            AttachmentPanel(id = AGENT_TOOL_CHAIN_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    agentPanelOpen &&
                    agentToolbarExpanded &&
                    agentToolChainOpen
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.88f),
                        exit = fadeOut() + scaleOut(targetScale = 0.88f),
                    ) {
                        AgentToolChainDockPanel(
                            steps = agentTaskSteps,
                            onClose = { agentToolChainOpen = false },
                            modifier = Modifier.width(260.dp),
                        )
                    }
                }
            }
            AttachmentPanel(id = AGENT_ID) {
                if (
                    vm.screen == AppScreen.EDITOR &&
                    vm.mode == AppMode.EDIT &&
                    vm.viewpointModeObjectId == null &&
                    agentPanelOpen
                ) {
                    val suggestions = pendingAgentSuggestions.map { suggestion ->
                        AgentSuggestion(
                            title = suggestion.title,
                            message = suggestion.message,
                            prompt = suggestion.prompt,
                            actionLabel = suggestion.actionLabel,
                        )
                    }
                    AgentSuggestionDockPanel(
                        suggestions = suggestions,
                        showDefaultSuggestions = agentDefaultSuggestionsVisible,
                        busy = agentBusy,
                        onPromptChange = {
                            agentPrompt = it
                        },
                    )
                }
            }
            AttachmentPanel(id = TEXT_ID) {
            }
            AttachmentPanel(id = SELECTION_ID) {
                val revision = vm.revision
                val selectedId = vm.selectedObjectId
                val selected = selectedId?.let { scene?.objectFor(it) ?: vm.selectedObject }
                if (vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && selected != null) {
                    val imageToModelPanelActive = imageToModelConfirmObjectId == selected.id &&
                        selected.type == ObjectType.IMAGE
                    val imageSubjectChoicePanelActive = imageSubjectPreprocessChoiceObjectId == selected.id &&
                        selected.type == ObjectType.IMAGE
                    val imageToImagePanelActive = imageToImageObjectId == selected.id &&
                        selected.type == ObjectType.IMAGE
                    val textToImagePanelActive = textToImageObjectId == selected.id &&
                        selected.type == ObjectType.TEXT
                    val strokeToSeedreamPanelActive = strokeToSeedreamObjectId == selected.id &&
                        selected.type == ObjectType.STROKE
                    val strokeToModelPanelActive = strokeToModelObjectId == selected.id &&
                        selected.type == ObjectType.STROKE
                    val selectionChromeMode = when {
                        imageToModelPanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.IMAGE_TO_MODEL
                        }
                        imageToImagePanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.IMAGE_TO_IMAGE
                        }
                        imageSubjectChoicePanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.IMAGE_SUBJECT_PREPROCESS
                        }
                        textToImagePanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.TEXT_TO_IMAGE
                        }
                        strokeToSeedreamPanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.STROKE_TO_IMAGE
                        }
                        strokeToModelPanelActive -> if (aigcKeyConfigPromptOpen) {
                            SelectionChromeMode.HIDDEN
                        } else {
                            SelectionChromeMode.STROKE_TO_MODEL
                        }
                        else -> SelectionChromeMode.TOOLBAR
                    }
                    var lastSelectionPopupMode by remember(selected.id) { mutableStateOf<SelectionChromeMode?>(null) }
                    val selectionPopupActive = selectionChromeMode.isPopup
                    val popupChromeMode = if (selectionPopupActive) selectionChromeMode else lastSelectionPopupMode
                    LaunchedEffect(selected.id, selectionChromeMode) {
                        if (selectionPopupActive) {
                            lastSelectionPopupMode = selectionChromeMode
                        }
                    }
                    AnimatedVisibility(
                        visible = !vm.objectManipulationInProgress,
                        enter = fadeIn() + scaleIn(initialScale = 0.82f),
                        exit = fadeOut() + scaleOut(targetScale = 0.82f),
                    ) {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selectionChromeMode == SelectionChromeMode.TOOLBAR,
                                    enter = fadeIn(animationSpec = tween(90)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(120)),
                                    exit = fadeOut(animationSpec = tween(70)) +
                                        scaleOut(targetScale = 0.94f, animationSpec = tween(90)),
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (
                                            vm.animationDraft == null &&
                                            !selected.isLocked &&
                                            selected.type != ObjectType.TEXT
                                        ) {
                                            RotationControlsPanel(
                                                reverseDirection = selected.type == ObjectType.VIEWPOINT,
                                                onRotateStart = { vm.beginSelectedRotation() },
                                                onRotate = { deltaRadians ->
                                                    vm.previewSelectedRotation(deltaRadians)?.let { (objectId, transform) ->
                                                        scene?.pushTransform(objectId, transform)
                                                    }
                                                },
                                                onRotateEnd = {
                                                    vm.commitSelectedRotation()?.let { (objectId, transform) ->
                                                        scene?.pushTransform(objectId, transform)
                                                    }
                                                },
                                            )
                                        }
                                        if (vm.selectedAnimationEndpoint == null && vm.animationDraft == null) {
                                            val selectedImageSubjectPreviewBusy = selected.id in imageSubjectPreviewBusyObjectIds
                                            val selectedImageToModelBusy = selected.id in imageToModelBusyObjectIds
                                            val selectedImageToImageBusy = selected.id in imageToImageBusyObjectIds
                                            val selectedTextToImageBusy = selected.id in textToImageBusyObjectIds
                                            val selectedStrokeToSeedreamBusy = selected.id in strokeToSeedreamBusyObjectIds
                                            val selectedStrokeToModelBusy = selected.id in strokeToModelBusyObjectIds
                                            SelectionBarPanel(
                                                documentType = vm.document.documentType,
                                                selectedType = selected.type,
                                                showGeometryColors = selected.type == ObjectType.MODEL &&
                                                    selected.source.startsWith(PresetLibrary.GEOMETRY_SOURCE_PREFIX),
                                                selectedColor = selected.textColor ?: ObjectColors.DEFAULT_GEOMETRY_COLOR,
                                                locked = selected.isLocked,
                                                autoRotateY = vm.selectedObjectAutoRotateY,
                                                faceViewer = vm.selectedObjectFacesViewer,
                                                textEditorOpen = vm.panel == StudioPanel.TEXT && selected.type == ObjectType.TEXT,
                                                geometryColorPickerOpen = geometryColorPickerOpen,
                                                imageSubjectPreviewActive = vm.selectedImageSubjectPreviewActive,
                                                imageSubjectPreviewBusy = selectedImageSubjectPreviewBusy,
                                                imageToModelBusy = selectedImageToModelBusy,
                                                imageToModelEnabled = true,
                                                imageToImageBusy = selectedImageToImageBusy,
                                                textToImageBusy = selectedTextToImageBusy,
                                                strokeToSeedreamBusy = selectedStrokeToSeedreamBusy,
                                                strokeToModelBusy = selectedStrokeToModelBusy,
                                                objectVersions = vm.selectedObjectVersions,
                                                onSetGeometryColorPickerOpen = { geometryColorPickerOpen = it },
                                                onToggleImageSubjectPreview = { toggleSelectedImageSubjectPreview() },
                                                onGenerateImageVariation = { openImageToImagePanel() },
                                                onGenerateImageToModel = { openImageToModelConfirm() },
                                                onGenerateTextToImage = { openTextToImagePanel() },
                                                onGenerateStrokeToImage = { openStrokeToSeedreamPanel() },
                                                onGenerateStrokeToModel = { openStrokeToModelPanel() },
                                                onAnimation = {
                                                    vm.openSelectedAnimationEditor()
                                                },
                                                onToggleAutoRotateY = { vm.toggleSelectedAutoRotateY() },
                                                onToggleFaceViewer = { vm.toggleSelectedFaceViewer() },
                                                onEnterViewpoint = { vm.enterSelectedViewpoint() },
                                                onEditText = { vm.openSelectedTextEditor() },
                                                onCloseTextEditor = { if (vm.panel == StudioPanel.TEXT) vm.closePanel() },
                                                onTextColor = {
                                                    vm.updateSelectedTextColor(it)
                                                    geometryColorPickerOpen = false
                                                },
                                                onDuplicate = { vm.duplicateSelectedObject() },
                                                onDelete = { vm.deleteSelectedObject() },
                                                onLock = {
                                                    geometryColorPickerOpen = false
                                                    vm.lockSelectedObject()
                                                },
                                                onUnlock = { vm.unlockSelectedObject() },
                                                onSwitchObjectVersion = { vm.switchSelectedObjectVersion(it) },
                                                onBranchObjectVersion = { vm.branchSelectedObjectVersion(it) },
                                            )
                                        }
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = false,
                                    enter = fadeIn(animationSpec = tween(90)) +
                                        scaleIn(initialScale = 0.94f, animationSpec = tween(120)),
                                    exit = fadeOut(animationSpec = tween(70)) +
                                        scaleOut(targetScale = 0.94f, animationSpec = tween(90)),
                                ) {
                                    when (popupChromeMode) {
                                        SelectionChromeMode.IMAGE_TO_MODEL -> {
                                            ImageToModelConfirmPanel(
                                                imageName = selected.displayName.ifBlank { "图片" },
                                                prompt = imageToModelPrompt,
                                                promptTemplate = imageToModelPromptTemplate,
                                                promptTemplateUseCase = AigcPromptUseCase.IMAGE_TO_MODEL,
                                                imagePreprocessMode = imageToModelPreprocessMode,
                                                advancedOpen = imageToModelAdvancedOpen,
                                                tier = imageToModelTier,
                                                qualityOverride = imageToModelQualityOverride,
                                                meshMode = imageToModelMeshMode,
                                                hdTexture = imageToModelHdTexture,
                                                creative = imageToModelCreative,
                                                busy = selected.id in imageToModelBusyObjectIds,
                                                message = imageToModelMessage,
                                                onPromptChange = { imageToModelPrompt = it },
                                                onPromptTemplateChange = { imageToModelPromptTemplate = it },
                                                onImagePreprocessModeChange = { imageToModelPreprocessMode = it },
                                                onAdvancedToggle = { imageToModelAdvancedOpen = !imageToModelAdvancedOpen },
                                                onTierChange = { imageToModelTier = it },
                                                onMeshBudgetChange = { qualityOverride, meshMode ->
                                                    imageToModelQualityOverride = qualityOverride
                                                    imageToModelMeshMode = meshMode
                                                },
                                                onHdTextureChange = { imageToModelHdTexture = it },
                                                onCreativeChange = { imageToModelCreative = it },
                                                onGenerate = { generateImageToModelReplacement() },
                                                onCancel = {
                                                    imageToModelConfirmObjectId = null
                                                    imageToModelPromptTemplate = null
                                                },
                                            )
                                        }
                                        SelectionChromeMode.IMAGE_TO_IMAGE -> {
                                            ImageToImagePanel(
                                                imageName = selected.displayName,
                                                prompt = imageToImagePrompt,
                                                promptTemplate = imageToImagePromptTemplate,
                                                busy = selected.id in imageToImageBusyObjectIds,
                                                message = imageToImageMessage,
                                                onPromptChange = { imageToImagePrompt = it },
                                                onPromptTemplateChange = { imageToImagePromptTemplate = it },
                                                onGenerate = { generateImageVariationFromSelectedImage() },
                                                onCancel = {
                                                    imageToImageObjectId = null
                                                    imageToImagePromptTemplate = null
                                                },
                                            )
                                        }
                                        SelectionChromeMode.IMAGE_SUBJECT_PREPROCESS -> {
                                            ImageSubjectPreprocessChoicePanel(
                                                extraPrompt = imageSubjectPreprocessPrompt,
                                                busy = selected.id in imageSubjectPreviewBusyObjectIds,
                                                message = imageSubjectPreprocessMessage,
                                                onExtraPromptChange = { imageSubjectPreprocessPrompt = it },
                                                onChoose = { runImageSubjectPreprocess(it) },
                                                onCancel = { imageSubjectPreprocessChoiceObjectId = null },
                                            )
                                        }
                                        SelectionChromeMode.TEXT_TO_IMAGE -> {
                                            TextToImagePanel(
                                                baseText = selected.source,
                                                extraPrompt = textToImagePromptExtra,
                                                promptTemplate = textToImagePromptTemplate,
                                                busy = selected.id in textToImageBusyObjectIds,
                                                message = textToImageMessage,
                                                onExtraPromptChange = { textToImagePromptExtra = it },
                                                onPromptTemplateChange = { textToImagePromptTemplate = it },
                                                onGenerate = { generateSelectedTextImage() },
                                                onCancel = {
                                                    textToImageObjectId = null
                                                    textToImagePromptTemplate = null
                                                },
                                            )
                                        }
                                        SelectionChromeMode.STROKE_TO_IMAGE -> {
                                            StrokeToImagePanel(
                                                strokeName = selected.displayName,
                                                extraPrompt = strokeToSeedreamPromptExtra,
                                                promptTemplate = strokeToSeedreamPromptTemplate,
                                                busy = selected.id in strokeToSeedreamBusyObjectIds,
                                                message = strokeToSeedreamMessage,
                                                onExtraPromptChange = { strokeToSeedreamPromptExtra = it },
                                                onPromptTemplateChange = { strokeToSeedreamPromptTemplate = it },
                                                onGenerate = { generateSelectedStrokeSeedreamImage() },
                                                onCancel = {
                                                    strokeToSeedreamObjectId = null
                                                    strokeToSeedreamPromptTemplate = null
                                                },
                                            )
                                        }
                                        SelectionChromeMode.STROKE_TO_MODEL -> {
                                            ImageToModelConfirmPanel(
                                                imageName = selected.displayName.ifBlank { "笔画" },
                                                prompt = strokeToModelPromptExtra,
                                                promptTemplate = strokeToModelPromptTemplate,
                                                promptTemplateUseCase = AigcPromptUseCase.STROKE_TO_MODEL,
                                                imagePreprocessMode = AigcImagePreprocessMode.NONE,
                                                advancedOpen = strokeToModelAdvancedOpen,
                                                tier = strokeToModelTier,
                                                qualityOverride = strokeToModelQualityOverride,
                                                meshMode = strokeToModelMeshMode,
                                                hdTexture = strokeToModelHdTexture,
                                                creative = strokeToModelCreative,
                                                busy = selected.id in strokeToModelBusyObjectIds,
                                                message = strokeToModelMessage,
                                                onPromptChange = { strokeToModelPromptExtra = it },
                                                onPromptTemplateChange = { strokeToModelPromptTemplate = it },
                                                onImagePreprocessModeChange = {},
                                                onAdvancedToggle = { strokeToModelAdvancedOpen = !strokeToModelAdvancedOpen },
                                                onTierChange = { strokeToModelTier = it },
                                                onMeshBudgetChange = { qualityOverride, meshMode ->
                                                    strokeToModelQualityOverride = qualityOverride
                                                    strokeToModelMeshMode = meshMode
                                                },
                                                onHdTextureChange = { strokeToModelHdTexture = it },
                                                onCreativeChange = { strokeToModelCreative = it },
                                                onGenerate = { generateSelectedStrokeSeedreamModel() },
                                                onCancel = {
                                                    strokeToModelObjectId = null
                                                    strokeToModelPromptTemplate = null
                                                },
                                                title = "笔画生成 3D",
                                                description = "根据当前观察方向的主视图先生成 2D 参考图，再自动送入 Rodin 生成模型。",
                                                showPreprocessSelector = false,
                                                promptPlaceholder = "可选：补充主体或风格，例如：玩具螃蟹、低多边形、干净产品模型",
                                                confirmLabel = "开始生成",
                                            )
                                        }
                                        SelectionChromeMode.TOOLBAR,
                                        SelectionChromeMode.HIDDEN,
                                        null -> Unit
                                    }
                                }
                            }
                            if (vm.panel == StudioPanel.ANIMATION) {
                                AnimationEditorPanel(
                                    animation = vm.selectedPageAnimation,
                                    editingEndpoints = vm.animationDraft != null,
                                    showAdd = !vm.animationDraftHadExistingAnimation,
                                    showDelete = vm.animationDraftHadExistingAnimation,
                                    onAdd = { vm.confirmSelectedAnimation() },
                                    onDuration = { vm.updateSelectedAnimationDuration(it) },
                                    onEasing = { vm.updateSelectedAnimationEasing(it) },
                                    onLoopMode = { vm.updateSelectedAnimationLoopMode(it) },
                                    onDelete = { vm.deleteSelectedAnimation() },
                                    onClose = { vm.closePanel() },
                                )
                            }
                            if (vm.panel == StudioPanel.TEXT && selected.type == ObjectType.TEXT) {
                                TextEditorPanel(
                                    currentText = selected.source,
                                    onChange = { vm.updateSelectedText(it) },
                                )
                            }
                        }
                    }
                }
            }
            AttachmentPanel(id = SELECTION_POPUP_ID) {
                val selectedId = vm.selectedObjectId
                val selected = selectedId?.let { scene?.objectFor(it) ?: vm.selectedObject }
                if (vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && selected != null) {
                    val currentPopupMode = when {
                        aigcKeyConfigPromptOpen -> null
                        imageToModelConfirmObjectId == selected.id && selected.type == ObjectType.IMAGE ->
                            SelectionChromeMode.IMAGE_TO_MODEL
                        imageToImageObjectId == selected.id && selected.type == ObjectType.IMAGE ->
                            SelectionChromeMode.IMAGE_TO_IMAGE
                        imageSubjectPreprocessChoiceObjectId == selected.id && selected.type == ObjectType.IMAGE ->
                            SelectionChromeMode.IMAGE_SUBJECT_PREPROCESS
                        textToImageObjectId == selected.id && selected.type == ObjectType.TEXT ->
                            SelectionChromeMode.TEXT_TO_IMAGE
                        strokeToSeedreamObjectId == selected.id && selected.type == ObjectType.STROKE ->
                            SelectionChromeMode.STROKE_TO_IMAGE
                        strokeToModelObjectId == selected.id && selected.type == ObjectType.STROKE ->
                            SelectionChromeMode.STROKE_TO_MODEL
                        else -> null
                    }
                    var lastPopupMode by remember(selected.id) { mutableStateOf<SelectionChromeMode?>(null) }
                    val popupChromeMode = currentPopupMode ?: lastPopupMode
                    LaunchedEffect(selected.id, currentPopupMode) {
                        if (currentPopupMode != null) {
                            lastPopupMode = currentPopupMode
                        }
                    }
                    AnimatedVisibility(
                        visible = !vm.objectManipulationInProgress && currentPopupMode != null,
                        enter = fadeIn(animationSpec = tween(90)) +
                            scaleIn(initialScale = 0.94f, animationSpec = tween(120)),
                        exit = fadeOut(animationSpec = tween(70)) +
                            scaleOut(targetScale = 0.94f, animationSpec = tween(90)),
                    ) {
                        when (popupChromeMode) {
                            SelectionChromeMode.IMAGE_TO_MODEL -> {
                                ImageToModelConfirmPanel(
                                    imageName = selected.displayName.ifBlank { "图片" },
                                    prompt = imageToModelPrompt,
                                    promptTemplate = imageToModelPromptTemplate,
                                    promptTemplateUseCase = AigcPromptUseCase.IMAGE_TO_MODEL,
                                    imagePreprocessMode = imageToModelPreprocessMode,
                                    advancedOpen = imageToModelAdvancedOpen,
                                    tier = imageToModelTier,
                                    qualityOverride = imageToModelQualityOverride,
                                    meshMode = imageToModelMeshMode,
                                    hdTexture = imageToModelHdTexture,
                                    creative = imageToModelCreative,
                                    busy = selected.id in imageToModelBusyObjectIds,
                                    message = imageToModelMessage,
                                    onPromptChange = { imageToModelPrompt = it },
                                    onPromptTemplateChange = { imageToModelPromptTemplate = it },
                                    onImagePreprocessModeChange = { imageToModelPreprocessMode = it },
                                    onAdvancedToggle = { imageToModelAdvancedOpen = !imageToModelAdvancedOpen },
                                    onTierChange = { imageToModelTier = it },
                                    onMeshBudgetChange = { qualityOverride, meshMode ->
                                        imageToModelQualityOverride = qualityOverride
                                        imageToModelMeshMode = meshMode
                                    },
                                    onHdTextureChange = { imageToModelHdTexture = it },
                                    onCreativeChange = { imageToModelCreative = it },
                                    onGenerate = { generateImageToModelReplacement() },
                                    onCancel = {
                                        imageToModelConfirmObjectId = null
                                        imageToModelPromptTemplate = null
                                    },
                                )
                            }
                            SelectionChromeMode.IMAGE_TO_IMAGE -> {
                                ImageToImagePanel(
                                    imageName = selected.displayName,
                                    prompt = imageToImagePrompt,
                                    promptTemplate = imageToImagePromptTemplate,
                                    busy = selected.id in imageToImageBusyObjectIds,
                                    message = imageToImageMessage,
                                    onPromptChange = { imageToImagePrompt = it },
                                    onPromptTemplateChange = { imageToImagePromptTemplate = it },
                                    onGenerate = { generateImageVariationFromSelectedImage() },
                                    onCancel = {
                                        imageToImageObjectId = null
                                        imageToImagePromptTemplate = null
                                    },
                                )
                            }
                            SelectionChromeMode.IMAGE_SUBJECT_PREPROCESS -> {
                                ImageSubjectPreprocessChoicePanel(
                                    extraPrompt = imageSubjectPreprocessPrompt,
                                    busy = selected.id in imageSubjectPreviewBusyObjectIds,
                                    message = imageSubjectPreprocessMessage,
                                    onExtraPromptChange = { imageSubjectPreprocessPrompt = it },
                                    onChoose = { runImageSubjectPreprocess(it) },
                                    onCancel = { imageSubjectPreprocessChoiceObjectId = null },
                                )
                            }
                            SelectionChromeMode.TEXT_TO_IMAGE -> {
                                TextToImagePanel(
                                    baseText = selected.source,
                                    extraPrompt = textToImagePromptExtra,
                                    promptTemplate = textToImagePromptTemplate,
                                    busy = selected.id in textToImageBusyObjectIds,
                                    message = textToImageMessage,
                                    onExtraPromptChange = { textToImagePromptExtra = it },
                                    onPromptTemplateChange = { textToImagePromptTemplate = it },
                                    onGenerate = { generateSelectedTextImage() },
                                    onCancel = {
                                        textToImageObjectId = null
                                        textToImagePromptTemplate = null
                                    },
                                )
                            }
                            SelectionChromeMode.STROKE_TO_IMAGE -> {
                                StrokeToImagePanel(
                                    strokeName = selected.displayName,
                                    extraPrompt = strokeToSeedreamPromptExtra,
                                    promptTemplate = strokeToSeedreamPromptTemplate,
                                    busy = selected.id in strokeToSeedreamBusyObjectIds,
                                    message = strokeToSeedreamMessage,
                                    onExtraPromptChange = { strokeToSeedreamPromptExtra = it },
                                    onPromptTemplateChange = { strokeToSeedreamPromptTemplate = it },
                                    onGenerate = { generateSelectedStrokeSeedreamImage() },
                                    onCancel = {
                                        strokeToSeedreamObjectId = null
                                        strokeToSeedreamPromptTemplate = null
                                    },
                                )
                            }
                            SelectionChromeMode.STROKE_TO_MODEL -> {
                                ImageToModelConfirmPanel(
                                    imageName = selected.displayName.ifBlank { "笔画" },
                                    prompt = strokeToModelPromptExtra,
                                    promptTemplate = strokeToModelPromptTemplate,
                                    promptTemplateUseCase = AigcPromptUseCase.STROKE_TO_MODEL,
                                    imagePreprocessMode = AigcImagePreprocessMode.NONE,
                                    advancedOpen = strokeToModelAdvancedOpen,
                                    tier = strokeToModelTier,
                                    qualityOverride = strokeToModelQualityOverride,
                                    meshMode = strokeToModelMeshMode,
                                    hdTexture = strokeToModelHdTexture,
                                    creative = strokeToModelCreative,
                                    busy = selected.id in strokeToModelBusyObjectIds,
                                    message = strokeToModelMessage,
                                    onPromptChange = { strokeToModelPromptExtra = it },
                                    onPromptTemplateChange = { strokeToModelPromptTemplate = it },
                                    onImagePreprocessModeChange = {},
                                    onAdvancedToggle = { strokeToModelAdvancedOpen = !strokeToModelAdvancedOpen },
                                    onTierChange = { strokeToModelTier = it },
                                    onMeshBudgetChange = { qualityOverride, meshMode ->
                                        strokeToModelQualityOverride = qualityOverride
                                        strokeToModelMeshMode = meshMode
                                    },
                                    onHdTextureChange = { strokeToModelHdTexture = it },
                                    onCreativeChange = { strokeToModelCreative = it },
                                    onGenerate = { generateSelectedStrokeSeedreamModel() },
                                    onCancel = {
                                        strokeToModelObjectId = null
                                        strokeToModelPromptTemplate = null
                                    },
                                    title = "笔画生成 3D",
                                    description = "根据当前观察方向的主视图先生成 2D 参考图，再自动送入 Rodin 生成模型。",
                                    showPreprocessSelector = false,
                                    promptPlaceholder = "可选：补充主体或风格，例如：玩具螃蟹、低多边形、干净产品模型",
                                    confirmLabel = "开始生成",
                                )
                            }
                            SelectionChromeMode.TOOLBAR,
                            SelectionChromeMode.HIDDEN,
                            null -> Unit
                        }
                    }
                }
            }
            AttachmentPanel(id = ANIMATION_START_CONTROLS_ID) {
                AnimationEndpointRotatePanel(
                    endpointId = vm.animationDraft?.startEndpointId,
                    vm = vm,
                    scene = scene,
                )
            }
            AttachmentPanel(id = ANIMATION_END_CONTROLS_ID) {
                AnimationEndpointRotatePanel(
                    endpointId = vm.animationDraft?.endEndpointId,
                    vm = vm,
                    scene = scene,
                )
            }
            AttachmentPanel(id = STROKE_ID) {
                AnimatedVisibility(
                    visible = vm.screen == AppScreen.EDITOR && vm.mode == AppMode.EDIT && vm.panel == StudioPanel.STROKE,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    StrokeEditorPanel(
                        draft = vm.strokeDraft,
                        onColor = { vm.setStrokeColor(it) },
                        onUndoStroke = {
                            vm.undoLastStrokePath()
                            scene?.syncStrokeDraft()
                        },
                        onRedoStroke = {
                            vm.redoLastStrokePath()
                            scene?.syncStrokeDraft()
                        },
                        onFinish = {
                            val shouldCreateStrokeObject = vm.strokeDraft?.hasDrawableContent == true
                            vm.finishStrokeDraft()
                            if (!shouldCreateStrokeObject) {
                                scene?.syncStrokeDraft()
                            }
                        },
                        onClose = {
                            val shouldCreateStrokeObject = vm.strokeDraft?.hasDrawableContent == true
                            vm.finishStrokeDraft()
                            if (!shouldCreateStrokeObject) {
                                scene?.syncStrokeDraft()
                            }
                        },
                    )
                }
            }
            AttachmentPanel(id = PRESENT_ID) {
                if (vm.screen == AppScreen.EDITOR && vm.mode == AppMode.PRESENT && vm.viewpointModeObjectId == null) {
                    PresentPanel(
                        title = vm.document.name,
                        currentPageIndex = vm.currentPageIndex,
                        pageCount = vm.pageCount,
                        annotationTool = vm.presentAnnotationTool,
                        selectedColor = vm.annotationStrokeDraft?.selectedColor ?: ObjectColors.STROKE_PALETTE.first(),
                        onPrevious = { vm.previousPage() },
                        onNext = { vm.nextPage() },
                        onToggleComment = { vm.togglePresentCommentTool() },
                        onToggleBrush = { vm.togglePresentStrokeTool() },
                        onSelectBrush = { vm.selectPresentStrokeTool() },
                        onToggleEraser = { vm.togglePresentEraserTool() },
                        onColor = { vm.setAnnotationStrokeColor(it) },
                        onExit = { vm.exitPresentMode() },
                    )
                }
            }
            AttachmentPanel(id = HOME_PRESET_ID) {
                AnimatedVisibility(
                    visible = vm.screen == AppScreen.HOME && !homeRiftTransitioning,
                    enter = fadeIn() + scaleIn(initialScale = 0.82f),
                    exit = fadeOut() + scaleOut(targetScale = 0.82f),
                ) {
                    HomeUnifiedPanel(
                        shows = vm.showSummaries,
                        onCreatePreset = { preset ->
                            when (preset) {
                                HomeExamplePreset.CURIOSITY_ASSEMBLY -> {
                                    scope.launch {
                                        val installed = withContext(Dispatchers.IO) {
                                            CuriosityExampleAssetInstaller.install(context.applicationContext)
                                        }
                                        installed
                                            .onSuccess(vm::presentCuriosityExample)
                                            .onFailure {
                                                vm.reportSceneError(
                                                    "好奇号示例安装失败：${it.message ?: it.javaClass.simpleName}"
                                                )
                                            }
                                    }
                                }
                                HomeExamplePreset.IMMERSIVE_INTERIOR -> {
                                    scope.launch {
                                        val installed = withContext(Dispatchers.IO) {
                                            ImmersiveInteriorExampleAssetInstaller.install(context.applicationContext)
                                        }
                                        installed
                                            .onSuccess(vm::presentImmersiveInteriorExample)
                                            .onFailure {
                                                vm.reportSceneError(
                                                    "室内示例安装失败：${it.message ?: it.javaClass.simpleName}"
                                                )
                                            }
                                    }
                                }
                                HomeExamplePreset.DINOSAUR_DETECTIVE -> {
                                    scope.launch {
                                        val installed = withContext(Dispatchers.IO) {
                                            DinosaurDetectiveExampleAssetInstaller.install(context.applicationContext)
                                        }
                                        installed
                                            .onSuccess(vm::presentDinosaurDetectiveExample)
                                            .onFailure {
                                                vm.reportSceneError(
                                                    "恐龙示例安装失败：${it.message ?: it.javaClass.simpleName}"
                                                )
                                            }
                                    }
                                }
                                HomeExamplePreset.FOUR_STROKE_ENGINE -> vm.presentExamplePlaceholder(preset)
                            }
                        },
                        onNewPresentation = { vm.newShow(DocumentType.PRESENTATION) },
                        onNewWhiteboard = { vm.newShow(DocumentType.WHITEBOARD) },
                        onOpenShow = {
                            homeActionShowIndex = null
                            homeDeleteShowIndex = null
                            vm.openShowFromHome(it)
                        },
                        onMoreShow = {
                            homeActionShowIndex = it
                            homeDeleteShowIndex = null
                        },
                        onDismissFloatingMenu = {
                            homeActionShowIndex = null
                            homeDeleteShowIndex = null
                        },
                        onPageGestureActiveChanged = { active ->
                            homePageGestureActive = active
                            homePageGestureBlockedUntilNanos =
                                System.nanoTime() + HOME_RIFT_PAGE_GESTURE_COOLDOWN_NANOS
                        },
                    )
                }
            }
            AttachmentPanel(id = HOME_ACTION_MENU_ID) {
                val showIndex = homeActionShowIndex
                val show = showIndex?.let { index -> vm.showSummaries.firstOrNull { it.index == index } }
                if (vm.screen == AppScreen.HOME && !homeRiftTransitioning && showIndex != null && show != null) {
                    AnimatedVisibility(
                        visible = true,
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                    ) {
                        HomeFileActionMenuPanel(
                            onDuplicate = {
                                vm.duplicateShow(showIndex)
                                homeActionShowIndex = null
                            },
                            onDelete = {
                                homeDeleteShowIndex = showIndex
                                homeActionShowIndex = null
                            },
                            onClose = { homeActionShowIndex = null },
                        )
                    }
                }
            }
            AttachmentPanel(id = HOME_DELETE_CONFIRM_ID) {
                val showIndex = homeDeleteShowIndex
                val show = showIndex?.let { index -> vm.showSummaries.firstOrNull { it.index == index } }
                if (vm.screen == AppScreen.HOME && !homeRiftTransitioning && showIndex != null && show != null) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    ) {
                        HomeDeleteConfirmPanel(
                            showName = show.name,
                            onConfirm = {
                                vm.deleteShow(showIndex)
                                homeDeleteShowIndex = null
                                homeActionShowIndex = null
                            },
                            onCancel = { homeDeleteShowIndex = null },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AnimationEndpointRotatePanel(
    endpointId: String?,
    vm: StudioViewModel,
    scene: SceneController?,
) {
    if (vm.screen != AppScreen.EDITOR || vm.mode != AppMode.EDIT || endpointId == null) return
    var activeTransform by remember(endpointId) { mutableStateOf<TransformData?>(null) }
    AnimatedVisibility(
        visible = vm.selectedAnimationEndpoint?.id == endpointId,
        enter = fadeIn() + scaleIn(initialScale = 0.82f),
        exit = fadeOut() + scaleOut(targetScale = 0.82f),
    ) {
        RotationControlsPanel(
            onRotateStart = {
                activeTransform = vm.currentTransform(endpointId)
                vm.beginTransformPreview(endpointId, activeTransform)
            },
            onRotate = { deltaRadians ->
                val current = activeTransform ?: vm.currentTransform(endpointId) ?: return@RotationControlsPanel
                val next = current.copy(rotationY = current.rotationY + deltaRadians)
                activeTransform = next
                vm.previewTransform(endpointId, next)
                scene?.pushTransform(endpointId, next)
            },
            onRotateEnd = {
                val current = activeTransform ?: vm.currentTransform(endpointId) ?: return@RotationControlsPanel
                vm.commitTransform(endpointId, current)
                scene?.pushTransform(endpointId, current)
                activeTransform = null
            },
        )
    }
}

private const val TOOLBAR_ID = "studio_toolbar"
private const val TOOLBAR_UNDO_ID = "studio_toolbar_undo"
private const val TOOLBAR_MORE_ID = "studio_toolbar_more"
private const val PAGES_ID = "studio_pages"
private const val ASSET_ID = "studio_assets"
private const val SKYBOX_ROTATION_STEP_RADIANS = 0.2617994f
private const val AIGC_LIBRARY_ID = "studio_aigc_library"
private const val SELECTION_ID = "studio_selection"
private const val SELECTION_POPUP_ID = "studio_selection_popup"
private const val ANNOTATION_ID = "studio_annotation"
private const val ANIMATION_START_CONTROLS_ID = "studio_animation_start_controls"
private const val ANIMATION_END_CONTROLS_ID = "studio_animation_end_controls"
private const val PRESENT_ID = "studio_present"
private const val FILE_ID = "studio_files"
private const val AIGC_KEY_CONFIG_ID = "studio_aigc_key_config"
private const val AGENT_CONVERSATION_ID = "studio_agent_conversation"
private const val AGENT_TOOL_CHAIN_ID = "studio_agent_tool_chain"
private const val AGENT_ID = "studio_agent"
private const val IMPORT_BROWSER_ID = "studio_import_browser"
private const val TEXT_ID = "studio_text"
private const val COMMENT_ID = "studio_comment"
private const val STROKE_ID = "studio_stroke"
private const val HOME_PRESET_ID = "studio_home_preset"
private const val HOME_ACTION_MENU_ID = "studio_home_action_menu"
private const val HOME_DELETE_CONFIRM_ID = "studio_home_delete_confirm"
private const val HOME_ENVIRONMENT_ID = "studio_home_environment"
private const val HOME_FLOATING_ROTATION_ID = "studio_home_floating_rotation"
private const val WORKSPACE_DRAG_HANDLE_ID = "studio_workspace_drag_handle"
private const val WORKSPACE_DRAG_HANDLE_PANEL_ID = "studio_workspace_drag_handle_panel"

private val PanelIds = listOf(
    TOOLBAR_ID,
    TOOLBAR_UNDO_ID,
    TOOLBAR_MORE_ID,
    PAGES_ID,
    WORKSPACE_DRAG_HANDLE_PANEL_ID,
    ASSET_ID,
    AIGC_LIBRARY_ID,
    ANNOTATION_ID,
    SELECTION_ID,
    SELECTION_POPUP_ID,
    ANIMATION_START_CONTROLS_ID,
    ANIMATION_END_CONTROLS_ID,
    PRESENT_ID,
    FILE_ID,
    AIGC_KEY_CONFIG_ID,
    AGENT_CONVERSATION_ID,
    AGENT_TOOL_CHAIN_ID,
    AGENT_ID,
    IMPORT_BROWSER_ID,
    TEXT_ID,
    COMMENT_ID,
    STROKE_ID,
    HOME_PRESET_ID,
    HOME_ACTION_MENU_ID,
    HOME_DELETE_CONFIRM_ID,
    HOME_ENVIRONMENT_ID,
    HOME_FLOATING_ROTATION_ID,
)

private val DefaultWorkspacePosition = PanelPosition(0f, 0.76f, -1.25f)
private val DefaultHomePosition = PanelPosition(0f, 1.2f, -1.66f)
private val HiddenPanelPosition = PanelPosition(0f, -2f, -2f)

private val WorkspacePanelOffsets = mapOf(
    PAGES_ID to PanelPosition(0f, 0.245f, -0.012f),
    TOOLBAR_ID to PanelPosition(0f, 0.115f, 0f),
    TOOLBAR_UNDO_ID to PanelPosition(0f, 0.032f, 0.014f),
    WORKSPACE_DRAG_HANDLE_PANEL_ID to PanelPosition(0f, -0.023f, 0.02f),
    TOOLBAR_MORE_ID to PanelPosition(0.42f, 0.04f, 0.12f),
    ANNOTATION_ID to PanelPosition(0.43f, 0.18f, -0.005f),
    PRESENT_ID to PanelPosition(0f, 0.14f, 0f),
)

private val WorkspaceOverlayPanelOffset = PanelPosition(0f, 0.29f, -0.01f)
private val AgentConversationPanelOffset = PanelPosition(0f, 0.178f, 0.016f)
private val AgentToolChainPanelOffset = PanelPosition(0.47f, 0.255f, 0.045f)
private val AgentSuggestionPanelOffset = PanelPosition(0f, 0.044f, 0.024f)
private const val AGENT_EXPANDED_CONVERSATION_SHIFT_Y = 0.092f
private const val AGENT_EXPANDED_PAGES_SHIFT_Y = 0.22f
private const val AGENT_EMPTY_SUGGESTIONS_DRAG_SHIFT_Y = 0.07f
private val AigcLibraryPanelOffset = PanelPosition(0.52f, 0.29f, 0.005f)
private const val SELECTION_POPUP_PANEL_Y_OFFSET = 0.145f
private val WorkspaceChromePanelIds = setOf(
    TOOLBAR_ID,
    TOOLBAR_UNDO_ID,
    TOOLBAR_MORE_ID,
    PAGES_ID,
    ANNOTATION_ID,
    PRESENT_ID,
    AGENT_CONVERSATION_ID,
    AGENT_TOOL_CHAIN_ID,
    AGENT_ID,
    WORKSPACE_DRAG_HANDLE_PANEL_ID,
)
private val WorkspaceAssetPanelDragIds = WorkspaceChromePanelIds + ASSET_ID + AIGC_LIBRARY_ID + AIGC_KEY_CONFIG_ID
private val WorkspaceFilePanelDragIds = WorkspaceChromePanelIds + FILE_ID + AIGC_KEY_CONFIG_ID
private val WorkspaceImportBrowserDragIds = WorkspaceChromePanelIds + IMPORT_BROWSER_ID + AIGC_KEY_CONFIG_ID
private val WorkspaceStrokePanelDragIds = WorkspaceChromePanelIds + STROKE_ID + AIGC_KEY_CONFIG_ID
private val HomeMovablePanelIds = setOf(
    HOME_PRESET_ID,
    HOME_ACTION_MENU_ID,
    HOME_DELETE_CONFIRM_ID,
    HOME_ENVIRONMENT_ID,
    HOME_FLOATING_ROTATION_ID,
    WORKSPACE_DRAG_HANDLE_PANEL_ID,
)

private val FixedPanelPositions = mapOf(
    TEXT_ID to PanelPosition(0f, 1.04f, -1.25f),
    COMMENT_ID to PanelPosition(0f, 1.02f, -1.18f),
)

private val HomePresetPanelOffset = PanelPosition(0.015f, 0f, 0f)
private val HomeDragHandleOffset = PanelPosition(0.015f, -0.43f, 0.02f)
private val HomeEnvironmentOffset = PanelPosition(0.015f, -0.335f, 0.025f)
private val HomeFloatingRotationOffset = PanelPosition(0.015f, -0.245f, 0.030f)
private val HomeActionMenuOffset = PanelPosition(0.43f, -0.02f, 0.12f)
private val HomeDeleteConfirmOffset = PanelPosition(0.015f, 0.03f, 0.31f)

private const val ASSET_PREVIEW_SURFACE_Z = 0.004f
private const val ASSET_PREVIEW_MODEL_Z = 0.004f
private const val COMMENT_EDITOR_PANEL_Z_OFFSET = 0.04f

private data class PanelPlacement(
    val position: PanelPosition,
    val scale: Float = 1f,
    val rotation: EulerAngles = EulerAngles(0f, 0f, 0f),
    val enabled: Boolean = true,
    val faceViewer: Boolean = true,
)

private fun PanelPosition.lerpTo(target: PanelPosition, progress: Float): PanelPosition {
    val t = progress.coerceIn(0f, 1f)
    return PanelPosition(
        x = x + (target.x - x) * t,
        y = y + (target.y - y) * t,
        z = z + (target.z - z) * t,
    )
}

private fun easeOutCubic(progress: Float): Float {
    val inverse = 1f - progress.coerceIn(0f, 1f)
    return 1f - inverse * inverse * inverse
}

private enum class ImportBrowserPurpose(
    val rootTitle: String,
) {
    ASSET("导入资源"),
    AIGC_REFERENCE_IMAGE("选择参考图"),
    SKYBOX("打开天空背景");

    fun titleFor(page: ImportBrowserPage): String =
        if (this != ASSET && page.currentPath == null) {
            rootTitle
        } else {
            page.title
        }

    fun messageFor(page: ImportBrowserPage): String =
        if (this == AIGC_REFERENCE_IMAGE) {
            if (page.currentPath == null) {
                "选择一张 PNG / JPG / WebP 作为 Rodin 单图生成参考。"
            } else if (page.entries.none { it.importable }) {
                "这个目录没有可用图片。"
            } else {
                "找到 ${page.entries.count { it.importable }} 张可用图片，${page.entries.count { it.isDirectory }} 个文件夹。"
            }
        } else if (this == SKYBOX) {
            if (page.currentPath == null) {
                "选择接近 2:1 的 PNG / JPG / WebP 天空背景。"
            } else if (page.entries.none { it.importable }) {
                "这个目录没有可用图片。"
            } else {
                "找到 ${page.entries.count { it.importable }} 张图片；导入时会校验 2:1 比例。"
            }
        } else {
            page.message
        }
}

private enum class ResourcePanelMode {
    PRESETS,
    SKYBOX,
}

private class ViewerPoseState {
    @Volatile
    var position: Vector3? = null

    @Volatile
    var rotation: Quat? = null
}

private sealed interface HomeEnvironmentRiftEvent {
    data object None : HomeEnvironmentRiftEvent
    data class Tearing(val left: Vector3, val right: Vector3, val progress: Float) : HomeEnvironmentRiftEvent
    data class Commit(val left: Vector3, val right: Vector3) : HomeEnvironmentRiftEvent
    data object Heal : HomeEnvironmentRiftEvent
}

private class HomeEnvironmentRiftGestureSession {
    private var tearing = false
    private var missingFrames = 0
    private var startDistance = 0f
    private var leftPinchSinceNanos = 0L
    private var rightPinchSinceNanos = 0L
    private var initialPairDistance: Float? = null
    private var pairRejectedUntilFullRelease = false
    private var lastLeft: Vector3? = null
    private var lastRight: Vector3? = null
    var lastCenter: Vector3? = null
        private set
    val isLocked: Boolean
        get() = tearing

    fun update(candidates: List<PinchCandidate>): HomeEnvironmentRiftEvent {
        val now = System.nanoTime()
        val left = candidates.firstOrNull { it.hand == HandSide.LEFT && it.isHomeRiftPinchInProgress() }
        val right = candidates.firstOrNull { it.hand == HandSide.RIGHT && it.isHomeRiftPinchInProgress() }
        if (!tearing) {
            if (pairRejectedUntilFullRelease) {
                if (left == null && right == null) reset()
                return HomeEnvironmentRiftEvent.None
            }
            if (left == null) leftPinchSinceNanos = 0L else if (leftPinchSinceNanos == 0L) leftPinchSinceNanos = now
            if (right == null) rightPinchSinceNanos = 0L else if (rightPinchSinceNanos == 0L) rightPinchSinceNanos = now
            if (left == null || right == null) {
                initialPairDistance = null
                return HomeEnvironmentRiftEvent.None
            }
            val pairDistance = left.position.distanceTo(right.position)
            if (initialPairDistance == null) {
                initialPairDistance = pairDistance
                val pinchStartDelta = kotlin.math.abs(leftPinchSinceNanos - rightPinchSinceNanos)
                if (
                    pinchStartDelta > HOME_RIFT_PINCH_START_WINDOW_NANOS ||
                    pairDistance >= HOME_RIFT_MAX_INITIAL_HAND_DISTANCE_METERS
                ) {
                    pairRejectedUntilFullRelease = true
                    return HomeEnvironmentRiftEvent.None
                }
            }
            val bothStableSince = maxOf(leftPinchSinceNanos, rightPinchSinceNanos)
            if (now - bothStableSince < HOME_RIFT_PINCH_STABLE_NANOS) return HomeEnvironmentRiftEvent.None
            startDistance = requireNotNull(initialPairDistance)
            lastCenter = left.position.midpoint(right.position)
            lastLeft = left.position
            lastRight = right.position
            tearing = true
            missingFrames = 0
            lastProgress = ((pairDistance - startDistance) / HOME_RIFT_EXTRA_DISTANCE_METERS).coerceIn(0f, 1f)
            return HomeEnvironmentRiftEvent.Tearing(left.position, right.position, lastProgress)
        }
        if (left == null || right == null) {
            missingFrames++
            if (missingFrames < HOME_RIFT_RELEASE_GRACE_FRAMES) return HomeEnvironmentRiftEvent.None
            val commit = lastProgress >= 1f
            val releaseEvent = if (commit && lastLeft != null && lastRight != null) {
                HomeEnvironmentRiftEvent.Commit(requireNotNull(lastLeft), requireNotNull(lastRight))
            } else {
                HomeEnvironmentRiftEvent.Heal
            }
            reset()
            return releaseEvent
        }
        missingFrames = 0
        lastCenter = left.position.midpoint(right.position)
        lastLeft = left.position
        lastRight = right.position
        lastProgress = ((left.position.distanceTo(right.position) - startDistance) / HOME_RIFT_EXTRA_DISTANCE_METERS)
            .coerceIn(0f, 1f)
        return HomeEnvironmentRiftEvent.Tearing(left.position, right.position, lastProgress)
    }

    private var lastProgress = 0f

    fun cancel() = reset()

    fun rejectCurrentGesture() {
        reset()
        pairRejectedUntilFullRelease = true
    }

    private fun reset() {
        tearing = false
        missingFrames = 0
        startDistance = 0f
        leftPinchSinceNanos = 0L
        rightPinchSinceNanos = 0L
        initialPairDistance = null
        pairRejectedUntilFullRelease = false
        lastLeft = null
        lastRight = null
        lastProgress = 0f
        lastCenter = null
    }
}

private class HomeRiftClickGuard {
    @Volatile
    private var suppressedUntilNanos = 0L

    fun suppressFor(durationNanos: Long) {
        suppressedUntilNanos = maxOf(suppressedUntilNanos, System.nanoTime() + durationNanos)
    }

    fun isSuppressed(): Boolean = System.nanoTime() < suppressedUntilNanos
}

private fun List<PinchCandidate>.hasTwoHandRiftPinch(): Boolean =
    any { it.hand == HandSide.LEFT && it.isHomeRiftPinchInProgress() } &&
        any { it.hand == HandSide.RIGHT && it.isHomeRiftPinchInProgress() }

private fun List<PinchCandidate>.hasAnyHomeRiftPinch(): Boolean =
    any { it.isHomeRiftPinchInProgress() }

private fun PinchCandidate.isHomeRiftPinchInProgress(): Boolean =
    pinchDistance in HOME_RIFT_MIN_PINCH_DISTANCE_METERS..HOME_RIFT_RELEASE_DISTANCE_METERS

private fun Vector3.midpoint(other: Vector3): Vector3 = Vector3((x + other.x) * 0.5f, (y + other.y) * 0.5f, (z + other.z) * 0.5f)

private class PanelPositionHolder(initial: PanelPosition) {
    @Volatile
    var value: PanelPosition = initial
}

private enum class SelectionChromeMode {
    IMAGE_TO_MODEL,
    IMAGE_TO_IMAGE,
    IMAGE_SUBJECT_PREPROCESS,
    TEXT_TO_IMAGE,
    STROKE_TO_IMAGE,
    STROKE_TO_MODEL,
    TOOLBAR,
    HIDDEN,
}

private val SelectionChromeMode.isPopup: Boolean
    get() = this != SelectionChromeMode.TOOLBAR && this != SelectionChromeMode.HIDDEN

private fun strokeFrontFaceForViewer(transform: TransformData?, viewer: Vector3?): StrokeFrontFace {
    if (transform == null || viewer == null) return StrokeFrontFace.POSITIVE_Z
    val dx = viewer.x - transform.px
    val dz = viewer.z - transform.pz
    val yaw = transform.rotationY.toDouble()
    val cosYaw = cos(yaw).toFloat()
    val sinYaw = sin(yaw).toFloat()
    val localX = dx * cosYaw - dz * sinYaw
    val localZ = dx * sinYaw + dz * cosYaw
    return StrokeFrontFace.fromLocalViewerDirection(localX, localZ)
}

private class PanelDragRuntimeState {
    @Volatile
    var active: Boolean = false

    @Volatile
    var viewerPosition: Vector3? = null
}

private fun panelPosition(
    panelId: String,
    workspacePosition: PanelPosition,
    homePosition: PanelPosition,
    scene: SceneController?,
    selectedObjectId: String?,
    selectionPopupObjectId: String?,
    mode: AppMode,
    panel: StudioPanel,
    selectedAssetCategory: PresetCategory,
    screen: AppScreen,
    chromeVisible: Boolean,
    chromeProgress: Float,
    assetPanelVisible: Boolean,
    assetPanelProgress: Float,
    toolbarMoreOpen: Boolean,
    homeActionShowIndex: Int?,
    homeDeleteShowIndex: Int?,
    homeFloatingRotationPosition: PanelPosition?,
    homeFloatingRotationScale: Float?,
    aigcKeyConfigOpen: Boolean,
    agentPanelOpen: Boolean,
    agentToolbarExpanded: Boolean,
    agentToolChainOpen: Boolean = false,
    agentPanelProgress: Float,
    agentSuggestionsVisible: Boolean = false,
    commentEditorPoint: StrokePoint?,
): PanelPlacement? =
    when {
        panelId == ANIMATION_START_CONTROLS_ID || panelId == ANIMATION_END_CONTROLS_ID -> {
            val targetRoleIsStart = panelId == ANIMATION_START_CONTROLS_ID
            scene?.animationEndpointControlsPosition(targetRoleIsStart)?.let { position ->
                PanelPlacement(
                    position = PanelPosition(position.x, position.y, position.z),
                    scale = scene.animationEndpointControlsScale(targetRoleIsStart) ?: 1f,
                )
            }
        }
        panelId == HOME_PRESET_ID -> if (screen == AppScreen.HOME) {
            PanelPlacement(homePosition + HomePresetPanelOffset, scale = HOME_WING_UI_SCALE)
        } else {
            null
        }
        panelId == HOME_ACTION_MENU_ID -> if (screen == AppScreen.HOME && homeActionShowIndex != null) {
            PanelPlacement(homePosition + HomeActionMenuOffset, scale = HOME_FLOATING_UI_SCALE)
        } else {
            null
        }
        panelId == HOME_DELETE_CONFIRM_ID -> if (screen == AppScreen.HOME && homeDeleteShowIndex != null) {
            PanelPlacement(homePosition + HomeDeleteConfirmOffset, scale = HOME_FLOATING_UI_SCALE)
        } else {
            null
        }
        panelId == HOME_ENVIRONMENT_ID -> if (screen == AppScreen.HOME) {
            PanelPlacement(homePosition + HomeEnvironmentOffset, scale = HOME_FLOATING_UI_SCALE)
        } else {
            null
        }
        panelId == HOME_FLOATING_ROTATION_ID -> if (screen == AppScreen.HOME && homeFloatingRotationPosition != null) {
            PanelPlacement(homeFloatingRotationPosition, scale = homeFloatingRotationScale ?: HOME_FLOATING_UI_SCALE)
        } else {
            null
        }
        panelId == WORKSPACE_DRAG_HANDLE_PANEL_ID -> if (
            screen == AppScreen.HOME ||
            mode == AppMode.EDIT ||
            mode == AppMode.PRESENT
        ) {
            PanelPlacement(
                if (screen == AppScreen.HOME) {
                    homePosition + HomeDragHandleOffset
                } else {
                    workspacePosition + WorkspacePanelOffsets.getValue(panelId) +
                        agentSuggestionDragHandleOffset(agentPanelOpen, agentSuggestionsVisible)
                },
                scale = if (screen == AppScreen.HOME) HOME_FLOATING_UI_SCALE else 1f,
            )
        } else {
            null
        }
        screen == AppScreen.HOME -> null
        panelId == AIGC_KEY_CONFIG_ID -> if (mode == AppMode.EDIT && aigcKeyConfigOpen) {
            PanelPlacement(
                workspacePosition + WorkspaceOverlayPanelOffset + PanelPosition(0f, 0.02f, 0.09f),
                scale = 1.04f,
            )
        } else {
            null
        }
        panelId == AGENT_ID -> if (mode == AppMode.EDIT && chromeVisible && agentPanelOpen) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            PanelPlacement(
                position = workspacePosition + AgentSuggestionPanelOffset +
                    PanelPosition(0f, -0.025f * (1f - progress.coerceIn(0f, 1f)), 0.02f * (1f - progress.coerceIn(0f, 1f))),
                scale = MAIN_WORKSPACE_UI_SCALE * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == AGENT_CONVERSATION_ID -> if (mode == AppMode.EDIT && chromeVisible && agentPanelOpen) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            val settled = progress.coerceIn(0f, 1f)
            PanelPlacement(
                position = workspacePosition + AgentConversationPanelOffset +
                    agentExpandedOffset(agentPanelOpen, agentToolbarExpanded, AGENT_EXPANDED_CONVERSATION_SHIFT_Y) +
                    PanelPosition(0f, -0.025f * (1f - settled), 0.02f * (1f - settled)),
                scale = MAIN_WORKSPACE_UI_SCALE * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == AGENT_TOOL_CHAIN_ID -> if (
            mode == AppMode.EDIT &&
            chromeVisible &&
            agentPanelOpen &&
            agentToolbarExpanded &&
            agentToolChainOpen
        ) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            val settled = progress.coerceIn(0f, 1f)
            PanelPlacement(
                position = workspacePosition + AgentToolChainPanelOffset +
                    PanelPosition(0f, -0.025f * (1f - settled), 0.025f * (1f - settled)),
                scale = MAIN_WORKSPACE_UI_SCALE * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == TOOLBAR_MORE_ID -> if (mode == AppMode.EDIT && toolbarMoreOpen && chromeVisible) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            val settled = progress.coerceIn(0f, 1f)
            PanelPlacement(
                position = workspacePosition + WorkspacePanelOffsets.getValue(panelId) +
                    PanelPosition(0f, -0.04f * (1f - settled), 0.035f * (1f - settled)),
                scale = MAIN_WORKSPACE_UI_SCALE * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == SELECTION_ID -> selectedObjectId
            ?.let { objectId ->
                scene?.selectionToolbarPosition(objectId)?.let { position ->
                    PanelPlacement(
                        position = PanelPosition(position.x, position.y, position.z),
                        scale = scene.selectionToolbarScale(objectId) ?: 1f,
                    )
                }
            }
        panelId == SELECTION_POPUP_ID -> selectionPopupObjectId
            ?.let { objectId ->
                scene?.selectionToolbarPosition(objectId)?.let { position ->
                    PanelPlacement(
                        position = PanelPosition(position.x, position.y - SELECTION_POPUP_PANEL_Y_OFFSET, position.z),
                        scale = scene.selectionToolbarScale(objectId) ?: 1f,
                    )
                }
            }
        panelId == TOOLBAR_UNDO_ID -> if (mode == AppMode.EDIT && chromeVisible && !agentPanelOpen) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            val baseOffset = WorkspacePanelOffsets.getValue(panelId)
            PanelPlacement(
                position = workspacePosition + baseOffset + PanelPosition(0f, -0.04f * (1f - progress.coerceIn(0f, 1f)), 0.035f * (1f - progress.coerceIn(0f, 1f))),
                scale = MAIN_WORKSPACE_UI_SCALE * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == TOOLBAR_ID || panelId == PAGES_ID || panelId == ANNOTATION_ID -> if ((mode == AppMode.EDIT && chromeVisible) || mode == AppMode.PRESENT) {
            val progress = chromeProgress.coerceIn(0f, 1.08f)
            val baseOffset = WorkspacePanelOffsets.getValue(panelId) + when (panelId) {
                PAGES_ID -> agentExpandedOffset(agentPanelOpen, agentToolbarExpanded, AGENT_EXPANDED_PAGES_SHIFT_Y)
                else -> PanelPosition(0f, 0f, 0f)
            }
            PanelPlacement(
                position = workspacePosition + baseOffset + PanelPosition(0f, -0.04f * (1f - progress.coerceIn(0f, 1f)), 0.035f * (1f - progress.coerceIn(0f, 1f))),
                scale = MAIN_WORKSPACE_UI_SCALE * if (mode == AppMode.PRESENT) 1f else progress,
                enabled = mode == AppMode.PRESENT || progress > 0.03f,
            )
        } else {
            null
        }
        panelId == PRESENT_ID -> if (mode == AppMode.PRESENT) {
            PanelPlacement(workspacePosition + WorkspacePanelOffsets.getValue(panelId))
        } else {
            null
        }
        panelId == ASSET_ID -> if (mode == AppMode.EDIT && assetPanelVisible) {
            val progress = assetPanelProgress.coerceIn(0f, 1.08f)
            val settled = progress.coerceIn(0f, 1f)
            PanelPlacement(
                position = workspacePosition + WorkspaceOverlayPanelOffset + PanelPosition(0f, -0.045f * (1f - settled), 0.04f * (1f - settled)),
                scale = 0.88f + 0.12f * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == AIGC_LIBRARY_ID -> if (
            mode == AppMode.EDIT &&
            assetPanelVisible &&
            selectedAssetCategory == PresetCategory.AIGC
        ) {
            val progress = assetPanelProgress.coerceIn(0f, 1.08f)
            val settled = progress.coerceIn(0f, 1f)
            PanelPlacement(
                position = workspacePosition + AigcLibraryPanelOffset +
                    PanelPosition(0.04f * (1f - settled), -0.045f * (1f - settled), 0.04f * (1f - settled)),
                scale = 0.82f + 0.18f * progress,
                enabled = progress > 0.03f,
            )
        } else {
            null
        }
        panelId == IMPORT_BROWSER_ID -> if (mode == AppMode.EDIT && panel == StudioPanel.IMPORT_BROWSER) {
            PanelPlacement(workspacePosition + WorkspaceOverlayPanelOffset)
        } else {
            null
        }
        panelId == FILE_ID -> if (mode == AppMode.EDIT && panel == StudioPanel.FILES) {
            PanelPlacement(workspacePosition + WorkspaceOverlayPanelOffset)
        } else {
            null
        }
        panelId == STROKE_ID -> if (mode == AppMode.EDIT && panel == StudioPanel.STROKE) {
            PanelPlacement(workspacePosition + WorkspaceOverlayPanelOffset)
        } else {
            null
        }
        panelId == COMMENT_ID && commentEditorPoint != null -> PanelPlacement(
            PanelPosition(
                x = commentEditorPoint.x,
                y = commentEditorPoint.y,
                z = commentEditorPoint.z + COMMENT_EDITOR_PANEL_Z_OFFSET,
            )
        )
        else -> FixedPanelPositions[panelId]?.let { PanelPlacement(it) }
    }

private fun agentExpandedOffset(
    agentPanelOpen: Boolean,
    agentToolbarExpanded: Boolean,
    y: Float,
): PanelPosition =
    if (agentPanelOpen && agentToolbarExpanded) PanelPosition(0f, y, 0f) else PanelPosition(0f, 0f, 0f)

private fun agentSuggestionDragHandleOffset(
    agentPanelOpen: Boolean,
    agentSuggestionsVisible: Boolean,
): PanelPosition =
    if (agentPanelOpen && !agentSuggestionsVisible) {
        PanelPosition(0f, AGENT_EMPTY_SUGGESTIONS_DRAG_SHIFT_Y, 0f)
    } else {
        PanelPosition(0f, 0f, 0f)
    }

private fun workspaceOverlayOpen(panel: StudioPanel): Boolean =
    panel == StudioPanel.ASSETS || panel == StudioPanel.IMPORT_BROWSER || panel == StudioPanel.FILES || panel == StudioPanel.STROKE

private fun workspaceMovablePanelIds(mode: AppMode, panel: StudioPanel): Set<String> =
    if (mode == AppMode.EDIT) {
        when (panel) {
            StudioPanel.ASSETS -> WorkspaceAssetPanelDragIds
            StudioPanel.FILES -> WorkspaceFilePanelDragIds
            StudioPanel.IMPORT_BROWSER -> WorkspaceImportBrowserDragIds
            StudioPanel.STROKE -> WorkspaceStrokePanelDragIds
            else -> WorkspaceChromePanelIds
        }
    } else {
        WorkspaceChromePanelIds
    }

private fun attachPanel(content: SpatialViewContent, entity: Entity, panelId: String, placement: PanelPlacement?) {
    setPanelPosition(entity, placement, viewer = null)
    content.addEntity(entity)
}

private fun setPanelPosition(entity: Entity, placement: PanelPlacement?, viewer: Vector3?) {
    entity.enabled = placement?.enabled == true
    val position = placement?.position ?: HiddenPanelPosition
    entity.components[TransformComponent::class.java]?.apply {
        setPosition(position.toVector3())
        setScaleVector(Vector3(placement?.scale ?: 1f))
        setEulerAngles(placement?.let { panelRotation(it, position, viewer) } ?: EulerAngles(0f, 0f, 0f))
    }
}

private fun Entity?.isEntityOrChildOfPanel(panelId: String): Boolean {
    var current = this
    repeat(MAX_PANEL_PARENT_SEARCH_DEPTH) {
        if (current?.getName() == panelId) return true
        current = runCatching { current?.getParent() }.getOrNull()
    }
    return false
}

private fun panelRotation(placement: PanelPlacement, position: PanelPosition, viewer: Vector3?): EulerAngles =
    if (placement.faceViewer) {
        viewerFacingRotationFor(position.toVector3(), viewer)
    } else {
        placement.rotation
    }

private fun viewerFacingRotationFor(position: Vector3, viewer: Vector3?): EulerAngles {
    val target = viewer ?: Vector3(0f, position.y, 0f)
    val dx = target.x - position.x
    val dy = target.y - position.y
    val dz = target.z - position.z
    val horizontalDistance = kotlin.math.sqrt(dx * dx + dz * dz)
    if (!horizontalDistance.isFinite() || horizontalDistance < 0.0001f) {
        return EulerAngles(0f, 0f, 0f)
    }
    val pitchRadians = -kotlin.math.atan2(dy, horizontalDistance)
    val yawRadians = kotlin.math.atan2(dx, dz)
    return EulerAngles(
        Math.toDegrees(pitchRadians.toDouble()).toFloat(),
        Math.toDegrees(yawRadians.toDouble()).toFloat(),
        0f,
    )
}

private fun applyDragHandleTransform(
    entity: Entity?,
    screen: AppScreen,
    workspace: PanelPosition,
    home: PanelPosition,
    agentPanelOpen: Boolean,
    agentToolbarExpanded: Boolean,
    agentPanelProgress: Float,
    agentSuggestionsVisible: Boolean,
    viewer: Vector3?,
) {
    entity?.components[TransformComponent::class.java]?.apply {
        val position = workspaceDragHandlePosition(
            screen = screen,
            workspace = workspace,
            home = home,
            agentPanelOpen = agentPanelOpen,
            agentToolbarExpanded = agentToolbarExpanded,
            agentPanelProgress = agentPanelProgress,
            agentSuggestionsVisible = agentSuggestionsVisible,
        )
        setPosition(position.toVector3())
        setScaleVector(Vector3(if (screen == AppScreen.HOME) HOME_FLOATING_UI_SCALE else 1f))
        setEulerAngles(viewerFacingRotationFor(position.toVector3(), viewer))
    }
}

private fun workspaceDragHandlePosition(
    screen: AppScreen,
    workspace: PanelPosition,
    home: PanelPosition,
    agentPanelOpen: Boolean,
    agentToolbarExpanded: Boolean,
    agentPanelProgress: Float,
    agentSuggestionsVisible: Boolean = false,
): PanelPosition =
    if (screen == AppScreen.HOME) {
        home + HomeDragHandleOffset
    } else {
        workspace + WorkspacePanelOffsets.getValue(WORKSPACE_DRAG_HANDLE_PANEL_ID) +
            agentSuggestionDragHandleOffset(agentPanelOpen, agentSuggestionsVisible)
    }

private fun createAssetPreviewEntities(): Map<String, AssetPreviewEntity> =
    PresetLibrary.assets.mapNotNull { asset ->
        val geometry = asset.geometry ?: return@mapNotNull null
        val root = Entity().apply { setName("preview_root_${asset.id}") }
        val model = ObjectEntityFactory.createGeometryPreview("preview_model_${asset.id}", geometry)
        root.addChild(model)
        model.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0f, ASSET_PREVIEW_MODEL_Z))
        asset.id to AssetPreviewEntity(root, model)
    }.toMap()

private fun updateAssetPreviewEntities(
    previews: Map<String, AssetPreviewEntity>,
    slots: Map<String, AssetPreviewSlot>,
    panelProgress: Float,
    visible: Boolean,
) {
    val progress = panelProgress.coerceIn(0f, 1.08f)
    val animationScale = 0.88f + 0.12f * progress
    val layout = assetPreviewLayout(slots)
    layout.forEach { preview ->
        val entities = previews[preview.asset.id] ?: return@forEach
        entities.root.enabled = visible && preview.placement.visible
        entities.root.components[TransformComponent::class.java]?.apply {
            val finalScale = preview.scale * animationScale
            setPosition(preview.placement.position.toVector3())
            setScaleVector(Vector3(1f))
            setEulerAngles(EulerAngles(0f, 0f, 0f))
            entities.model.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, 0f, ASSET_PREVIEW_MODEL_Z))
                setScaleVector(Vector3(finalScale))
                setEulerAngles(preview.rotation)
            }
        }
    }
    previews.filterKeys { key -> layout.none { it.asset.id == key } }.values.forEach { it.root.enabled = false }
}

private fun assetPreviewLayout(slots: Map<String, AssetPreviewSlot>): List<AssetPreviewLayout> {
    val geometryAssets = PresetLibrary.assets.filter { it.category == PresetCategory.PRESET_MODEL && it.geometry != null }
    return geometryAssets.mapNotNull { asset ->
        val slot = slots[asset.id] ?: return@mapNotNull null
        AssetPreviewLayout(
            asset = asset,
            placement = assetPreviewPlacement(slot),
            scale = asset.geometry?.let { geometry -> assetPreviewScaleFor(geometry, slot) } ?: 1f,
            rotation = asset.geometry?.let(::assetPreviewRotationFor) ?: EulerAngles(0f, 0f, 0f),
        )
    }
}

private fun assetPreviewPlacement(slot: AssetPreviewSlot): AssetPreviewPlacement {
    return AssetPreviewPlacement(
        position = PanelPosition(
            x = slot.centerOffsetXMeters,
            y = slot.centerOffsetYMeters,
            z = ASSET_PREVIEW_SURFACE_Z,
        ),
        visible = slot.visible,
    )
}

private data class AssetPreviewPlacement(
    val position: PanelPosition,
    val visible: Boolean,
)

private data class AssetPreviewLayout(
    val asset: PresetAsset,
    val placement: AssetPreviewPlacement,
    val scale: Float,
    val rotation: EulerAngles,
)

private data class AssetPreviewEntity(
    val root: Entity,
    val model: Entity,
)

private fun assetPreviewScaleFor(geometry: PresetGeometry, slot: AssetPreviewSlot): Float {
    val size = ObjectEntityFactory.geometryPreviewSize(geometry)
    val availableWidth = slot.contentWidthMeters.coerceAtLeast(0.0001f)
    val availableHeight = slot.contentHeightMeters.coerceAtLeast(0.0001f)
    val visualWidth = when (geometry) {
        PresetGeometry.PLANE -> size.x
        else -> size.x
    }.coerceAtLeast(0.0001f)
    val visualHeight = when (geometry) {
        PresetGeometry.PLANE -> size.y
        else -> size.y
    }.coerceAtLeast(0.0001f)
    return min(availableWidth / visualWidth, availableHeight / visualHeight)
}

private fun assetPreviewRotationFor(geometry: PresetGeometry): EulerAngles =
    when (geometry) {
        PresetGeometry.PLANE -> EulerAngles(0f, 0f, 0f)
        else -> EulerAngles(0f, 0f, 0f)
    }

private fun createWorkspaceDragHandle(): Entity {
    return Entity().apply {
        setName(WORKSPACE_DRAG_HANDLE_ID)
        components.set(
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(Vector3(0.34f, 0.08f, 0.05f))),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
        components.set(InteractableComponent())
        components.set(HoverEffectComponent())
        applyDragHandleTransform(
            this,
            AppScreen.EDITOR,
            DefaultWorkspacePosition,
            DefaultHomePosition,
            agentPanelOpen = false,
            agentToolbarExpanded = false,
            agentPanelProgress = 0f,
            agentSuggestionsVisible = false,
            viewer = null,
        )
    }
}

private fun Entity.containsWorkspaceDragHandlePinch(position: Vector3): Boolean {
    if (!enabled) return false
    val local = runCatching { convertPositionFrom(position, null) }
        .getOrNull()
        ?.takeIf { it.isFinite() }
        ?: return false
    return abs(local.x) <= WORKSPACE_DRAG_HANDLE_HIT_HALF_X &&
        abs(local.y) <= WORKSPACE_DRAG_HANDLE_HIT_HALF_Y &&
        abs(local.z) <= WORKSPACE_DRAG_HANDLE_HIT_HALF_Z
}

private class PinchStrokeSession {
    private var activeHand: HandSide? = null
    private var drawing = false
    private var missingFrames = 0
    private var startCandidateHand: HandSide? = null
    private var startCandidateFrames = 0
    private var appendedPoints = 0
    private var beginNanos = 0L
    private var lastDebugLogNanos = 0L
    private var uiBlockedUntilRelease = true

    fun update(
        candidates: List<PinchCandidate>,
        scene: SceneController,
        vm: StudioViewModel,
        uiPointerActive: Boolean,
        lastUiPointerNanos: Long,
        workspaceDragPointerActive: Boolean,
        lastWorkspaceDragPointerNanos: Long,
        workspaceDragHandleEntity: Entity?,
    ) {
        val nowNanos = System.nanoTime()
        val uiRecentlyActive = uiPointerActive ||
            (lastUiPointerNanos > 0L && nowNanos - lastUiPointerNanos < STROKE_UI_INPUT_BLOCK_NANOS)
        val workspaceDragRecentlyActive = workspaceDragPointerActive ||
            (
                lastWorkspaceDragPointerNanos > 0L &&
                    nowNanos - lastWorkspaceDragPointerNanos < STROKE_UI_INPUT_BLOCK_NANOS
                )
        val pinchOnWorkspaceDragHandle = candidates.any { candidate ->
            candidate.isPotentialPinchInProgress() &&
                workspaceDragHandleEntity?.containsWorkspaceDragHandlePinch(candidate.position) == true
        }
        val workspaceDragBlocking = workspaceDragRecentlyActive || pinchOnWorkspaceDragHandle
        if (drawing) {
            if (workspaceDragBlocking) {
                cancel(scene, vm, "workspace drag handle active")
                return
            }
            if (
                uiRecentlyActive &&
                    appendedPoints <= STROKE_UI_BLOCK_CANCEL_MAX_POINTS &&
                    nowNanos - beginNanos <= STROKE_UI_BLOCK_CANCEL_WINDOW_NANOS
            ) {
                cancel(scene, vm, "ui pointer became active near stroke start")
                return
            }
            val candidate = candidates.firstOrNull { it.hand == activeHand }
            if (candidate == null || candidate.pinchDistance > PINCH_RELEASE_DISTANCE_METERS) {
                missingFrames++
                if (missingFrames >= PINCH_RELEASE_FRAME_GRACE) {
                    finish(scene, vm)
                }
                return
            }
            missingFrames = 0
            val point = scene.strokePointFromTrackingPosition(candidate.position) ?: return
            if (vm.appendStrokePoint(point, notify = false)) {
                appendedPoints++
                if (appendedPoints <= STROKE_DEBUG_INITIAL_POINTS || appendedPoints % STROKE_DEBUG_POINT_INTERVAL == 0) {
                    Log.i(HAND_STROKE_TAG, "append point=$appendedPoints pos=(${point.x}, ${point.y}, ${point.z}) pinch=${candidate.pinchDistance}")
                }
            }
            scene.appendStrokeInput(point, currentStrokeColor(vm))
            return
        }

        val pinchInProgress = candidates.any { it.isPotentialPinchInProgress() }
        if (workspaceDragBlocking) {
            if (!uiBlockedUntilRelease) {
                Log.i(HAND_STROKE_TAG, "block stroke start: workspace drag handle active")
            }
            uiBlockedUntilRelease = true
            startCandidateHand = null
            startCandidateFrames = 0
        }
        if (uiRecentlyActive && pinchInProgress) {
            if (!uiBlockedUntilRelease) {
                Log.i(HAND_STROKE_TAG, "block stroke start: ui pointer active")
            }
            uiBlockedUntilRelease = true
            startCandidateHand = null
            startCandidateFrames = 0
        }
        if (uiBlockedUntilRelease) {
            if (!pinchInProgress && !uiRecentlyActive) {
                uiBlockedUntilRelease = false
            }
            return
        }

        val starter = candidates
            .filter { it.isReliablePinchStart() }
            .minByOrNull { it.pinchDistance }
            ?: run {
                startCandidateHand = null
                startCandidateFrames = 0
                logIdleCandidates(candidates)
                return
            }
        if (starter.hand == startCandidateHand) {
            startCandidateFrames++
        } else {
            startCandidateHand = starter.hand
            startCandidateFrames = 1
        }
        if (startCandidateFrames < PINCH_START_FRAME_GRACE) return
        val point = scene.strokePointFromTrackingPosition(starter.position) ?: return
        activeHand = starter.hand
        drawing = true
        missingFrames = 0
        startCandidateHand = null
        startCandidateFrames = 0
        appendedPoints = 1
        beginNanos = System.nanoTime()
        vm.beginStrokePath(point)
        scene.beginStrokeInput(point, currentStrokeColor(vm))
        Log.i(HAND_STROKE_TAG, "begin hand=${starter.hand} pos=(${point.x}, ${point.y}, ${point.z}) pinch=${starter.pinchDistance}")
    }

    fun finish(scene: SceneController, vm: StudioViewModel) {
        if (!drawing) return
        Log.i(HAND_STROKE_TAG, "finish points=$appendedPoints")
        vm.endStrokePath()
        scene.syncStrokeDraft()
        scene.endStrokeInput()
        activeHand = null
        drawing = false
        missingFrames = 0
        startCandidateHand = null
        startCandidateFrames = 0
        appendedPoints = 0
        beginNanos = 0L
        uiBlockedUntilRelease = false
    }

    private fun cancel(scene: SceneController, vm: StudioViewModel, reason: String) {
        if (!drawing) return
        Log.i(HAND_STROKE_TAG, "cancel points=$appendedPoints reason=$reason")
        vm.cancelActiveStrokePath()
        scene.endStrokeInput()
        activeHand = null
        drawing = false
        missingFrames = 0
        startCandidateHand = null
        startCandidateFrames = 0
        appendedPoints = 0
        beginNanos = 0L
        uiBlockedUntilRelease = true
    }

    private fun logIdleCandidates(candidates: List<PinchCandidate>) {
        val now = System.nanoTime()
        if (now - lastDebugLogNanos < STROKE_DEBUG_IDLE_INTERVAL_NANOS) return
        lastDebugLogNanos = now
        val summary = candidates.joinToString { "${it.hand}:${it.pinchDistance}" }
        Log.i(HAND_STROKE_TAG, "idle candidates=${candidates.size} [$summary]")
    }
}

private enum class AnnotationTrackingMode {
    PRESENT,
    WHITEBOARD_COMMENT,
}

private class SpatialAnnotationInputSession(
    private val mode: AnnotationTrackingMode,
) {
    private var activeHand: HandSide? = null
    private var drawing = false
    private var missingFrames = 0
    private var uiBlockedUntilRelease = true

    fun update(
        candidates: List<PinchCandidate>,
        scene: SceneController,
        vm: StudioViewModel,
        uiPointerActive: Boolean,
        lastUiPointerNanos: Long,
    ) {
        val pinchInProgress = candidates.any { it.isPotentialPinchInProgress() }
        val uiRecentlyActive = uiPointerActive ||
            (lastUiPointerNanos > 0L && System.nanoTime() - lastUiPointerNanos < PRESENT_UI_INPUT_BLOCK_NANOS)
        if (!drawing && uiRecentlyActive) {
            uiBlockedUntilRelease = true
            return
        }
        if (!drawing && uiBlockedUntilRelease) {
            if (!pinchInProgress && !uiRecentlyActive) {
                uiBlockedUntilRelease = false
            }
            return
        }
        when (mode) {
            AnnotationTrackingMode.WHITEBOARD_COMMENT -> updateComment(candidates, scene, vm)
            AnnotationTrackingMode.PRESENT -> when (vm.presentAnnotationTool) {
                PresentAnnotationTool.COMMENT -> updateComment(candidates, scene, vm)
                PresentAnnotationTool.STROKE -> updateStroke(candidates, scene, vm)
                PresentAnnotationTool.ERASER -> Unit
                PresentAnnotationTool.NONE -> finish(scene, vm)
            }
        }
    }

    private fun updateComment(
        candidates: List<PinchCandidate>,
        scene: SceneController,
        vm: StudioViewModel,
    ) {
        if (drawing) {
            val candidate = candidates.firstOrNull { it.hand == activeHand }
            if (candidate == null || candidate.pinchDistance > PINCH_RELEASE_DISTANCE_METERS) {
                missingFrames++
                if (missingFrames >= PINCH_RELEASE_FRAME_GRACE) {
                    finishComment(scene, vm)
                }
                return
            }
            missingFrames = 0
            val point = scene.strokePointFromTrackingPosition(candidate.position) ?: return
            if (vm.updateAnnotationComment(point, notify = false)) {
                scene.syncAnnotationDraft()
            }
            return
        }
        val starter = candidates
            .filter { it.isReliablePinchStart() }
            .minByOrNull { it.pinchDistance }
            ?: return
        val point = scene.strokePointFromTrackingPosition(starter.position) ?: return
        activeHand = starter.hand
        drawing = true
        missingFrames = 0
        if (mode == AnnotationTrackingMode.WHITEBOARD_COMMENT) {
            vm.beginWhiteboardComment(point)
        } else {
            vm.beginAnnotationComment(point)
        }
        scene.syncAnnotationDraft()
    }

    private fun updateStroke(
        candidates: List<PinchCandidate>,
        scene: SceneController,
        vm: StudioViewModel,
    ) {
        if (drawing) {
            val candidate = candidates.firstOrNull { it.hand == activeHand }
            if (candidate == null || candidate.pinchDistance > PINCH_RELEASE_DISTANCE_METERS) {
                missingFrames++
                if (missingFrames >= PINCH_RELEASE_FRAME_GRACE) {
                    finish(scene, vm)
                }
                return
            }
            missingFrames = 0
            val point = scene.strokePointFromTrackingPosition(candidate.position) ?: return
            if (vm.appendAnnotationStrokePoint(point, notify = false)) {
                scene.appendAnnotationStrokeInput(point, currentAnnotationStrokeColor(vm))
            }
            return
        }
        val starter = candidates
            .filter { it.isReliablePinchStart() }
            .minByOrNull { it.pinchDistance }
            ?: return
        val point = scene.strokePointFromTrackingPosition(starter.position) ?: return
        activeHand = starter.hand
        drawing = true
        missingFrames = 0
        vm.beginAnnotationStrokePath(point)
        scene.beginAnnotationStrokeInput(point, currentAnnotationStrokeColor(vm))
    }

    fun finish(scene: SceneController, vm: StudioViewModel) {
        if (!drawing) return
        if (vm.annotationCommentDraft != null) {
            finishComment(scene, vm)
            return
        }
        vm.finishAnnotationStrokePath()
        scene.endAnnotationStrokeInput()
        scene.syncPageAnnotations()
        resetAfterInput()
    }

    private fun finishComment(scene: SceneController, vm: StudioViewModel) {
        if (!drawing) return
        if (mode == AnnotationTrackingMode.WHITEBOARD_COMMENT) {
            vm.finishWhiteboardComment()
        } else {
            vm.finishAnnotationComment()
        }
        scene.syncAnnotationDraft()
        scene.syncPageAnnotations()
        resetAfterInput()
    }

    private fun resetAfterInput() {
        activeHand = null
        drawing = false
        missingFrames = 0
        uiBlockedUntilRelease = true
    }
}

private fun PinchCandidate.isReliablePinchStart(): Boolean =
    pinchDistance in PINCH_MIN_VALID_DISTANCE_METERS..PINCH_START_DISTANCE_METERS

private fun PinchCandidate.isPotentialPinchInProgress(): Boolean =
    pinchDistance in PINCH_MIN_VALID_DISTANCE_METERS..PINCH_RELEASE_DISTANCE_METERS

private fun currentStrokeColor(vm: StudioViewModel): Long =
    vm.strokeDraft?.activePath?.color
        ?: vm.strokeDraft?.selectedColor
        ?: ObjectColors.STROKE_PALETTE.first()

private fun currentAnnotationStrokeColor(vm: StudioViewModel): Long =
    vm.annotationStrokeDraft?.activePath?.color
        ?: vm.annotationStrokeDraft?.selectedColor
        ?: ObjectColors.STROKE_PALETTE.first()

private fun HandTrackingData.pinchCandidates(): List<PinchCandidate> =
    listOfNotNull(
        left?.pinchCandidate(HandSide.LEFT),
        right?.pinchCandidate(HandSide.RIGHT),
    )

private fun HandPose.pinchCandidate(hand: HandSide): PinchCandidate? {
    val thumb = runCatching { joint(HandJoint.Index.THUMB_TIP).position }.getOrNull()?.takeIf { it.isFinite() }
        ?: return null
    val index = runCatching { joint(HandJoint.Index.INDEX_TIP).position }.getOrNull()?.takeIf { it.isFinite() }
        ?: return null
    val distance = thumb.distanceTo(index)
    if (!distance.isFinite()) return null
    return PinchCandidate(
        hand = hand,
        position = Vector3(
            (thumb.x + index.x) * 0.5f,
            (thumb.y + index.y) * 0.5f,
            (thumb.z + index.z) * 0.5f,
        ),
        pinchDistance = distance,
    )
}

private fun Vector3.distanceTo(other: Vector3): Float {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun Vector3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

private fun String.shortAgentHint(maxLength: Int = 18): String {
    val value = trim()
    return if (value.length <= maxLength) value else value.take(maxLength) + "..."
}

private data class PendingAgentClarification(
    val originalPrompt: String,
    val question: String,
)

private fun AgentRunResult.llmClarificationQuestion(): String? {
    if (status != AgentRunStatus.NO_TOOL) return null
    val text = assistantText.ifBlank { message }.trim()
    val prefix = when {
        text.startsWith(LLM_CLARIFICATION_PREFIX) -> LLM_CLARIFICATION_PREFIX
        text.startsWith(LLM_CLARIFICATION_PREFIX_FULL_WIDTH) -> LLM_CLARIFICATION_PREFIX_FULL_WIDTH
        else -> return null
    }
    return text.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
}

private fun buildClarifiedAgentPrompt(
    originalPrompt: String,
    question: String,
    answer: String,
): String =
    """
        这是一轮澄清后的用户意图，请结合上下文判断并执行。

        原始用户指令：
        $originalPrompt

        AI 追问：
        $question

        用户回答：
        $answer

        如果信息已经足够明确，请直接给出工具调用；如果仍不明确，可以继续用 CLARIFY: 开头追问一句。
    """.trimIndent()

private const val LLM_CLARIFICATION_PREFIX = "CLARIFY:"
private const val LLM_CLARIFICATION_PREFIX_FULL_WIDTH = "CLARIFY："

private fun AgentRunStepStatus.toUiStatus(): AgentTaskStepStatus =
    when (this) {
        AgentRunStepStatus.RUNNING -> AgentTaskStepStatus.RUNNING
        AgentRunStepStatus.SUCCEEDED -> AgentTaskStepStatus.SUCCEEDED
        AgentRunStepStatus.FAILED -> AgentTaskStepStatus.FAILED
    }

private data class AigcGenerationRunResult(
    val asset: ImportedAsset,
    val preprocessingMessage: String?,
    val preprocessingImageSource: String? = null,
    val preprocessingMode: AigcImagePreprocessMode? = null,
)

private fun imagePreprocessAsset(
    baseName: String,
    source: String,
    mode: AigcImagePreprocessMode,
): ImportedAsset =
    ImportedAsset(
        displayName = if (mode == AigcImagePreprocessMode.SEEDREAM_CLEAN) {
            "${baseName.take(14)}AI清理"
        } else {
            "${baseName.take(14)}主体图"
        },
        type = ObjectType.IMAGE,
        source = source,
    )

internal data class PinchCandidate(
    val hand: HandSide,
    val position: Vector3,
    val pinchDistance: Float,
)

internal enum class HandSide {
    LEFT,
    RIGHT,
}

private const val MAIN_WORKSPACE_UI_SCALE = 1.22f
private const val HOME_WING_UI_SCALE = 1.08f
private const val HOME_FLOATING_UI_SCALE = 1.08f
private const val UI_LOOK_AT_UPDATE_INTERVAL_MS = 33L
private const val UI_LOOK_AT_POINTER_FREEZE_NANOS = 320_000_000L
private const val MAX_PANEL_PARENT_SEARCH_DEPTH = 12
private const val HAND_STROKE_TAG = "ShowMe3D.HandStroke"
private const val AGENT_TAG = "ShowMe3D.Agent"
private const val PINCH_MIN_VALID_DISTANCE_METERS = 0.006f
private const val PINCH_START_DISTANCE_METERS = 0.015f
private const val PINCH_START_FRAME_GRACE = 1
private const val PINCH_RELEASE_DISTANCE_METERS = 0.02f
private const val PINCH_RELEASE_FRAME_GRACE = 2
private const val STROKE_UI_INPUT_BLOCK_NANOS = 220_000_000L
private const val STROKE_UI_BLOCK_CANCEL_MAX_POINTS = 2
private const val STROKE_UI_BLOCK_CANCEL_WINDOW_NANOS = 120_000_000L
private const val WORKSPACE_DRAG_HANDLE_HIT_HALF_X = 0.23f
private const val WORKSPACE_DRAG_HANDLE_HIT_HALF_Y = 0.09f
private const val WORKSPACE_DRAG_HANDLE_HIT_HALF_Z = 0.10f
private const val PRESENT_UI_INPUT_BLOCK_NANOS = 220_000_000L
private const val HOME_RIFT_EXTRA_DISTANCE_METERS = 0.34f
private const val HOME_RIFT_RELEASE_GRACE_FRAMES = 2
private const val HOME_RIFT_PINCH_START_WINDOW_NANOS = 300_000_000L
private const val HOME_RIFT_PINCH_STABLE_NANOS = 100_000_000L
private const val HOME_RIFT_MAX_INITIAL_HAND_DISTANCE_METERS = 0.30f
private const val HOME_RIFT_CHROME_HIDE_PROGRESS = 0.72f
private const val HOME_RIFT_CHROME_EXIT_MS = 220L
private const val HOME_RIFT_SCENE_SETTLE_MS = 360L
private const val HOME_RIFT_COMMIT_EXPAND_MS = 420L
private const val HOME_RIFT_POST_SWITCH_HOLD_MS = 70L
private const val HOME_RIFT_HEAL_MS = 260L
private const val HOME_RIFT_CLICK_GUARD_MS = 150L
private const val HOME_RIFT_CLICK_SUPPRESS_NANOS = 1_200_000_000L
private const val HOME_RIFT_PAGE_GESTURE_COOLDOWN_NANOS = 500_000_000L
private const val HOME_RIFT_MIN_PINCH_DISTANCE_METERS = 0.0015f
private const val HOME_RIFT_RELEASE_DISTANCE_METERS = 0.030f
private const val STROKE_DEBUG_INITIAL_POINTS = 4
private const val STROKE_DEBUG_POINT_INTERVAL = 30
private const val STROKE_DEBUG_IDLE_INTERVAL_NANOS = 1_000_000_000L
