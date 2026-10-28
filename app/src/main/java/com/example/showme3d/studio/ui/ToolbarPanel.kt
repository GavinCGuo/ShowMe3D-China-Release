package com.example.showme3d.studio.ui

import com.example.showme3d.release.ReleasePolicy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.R
import com.example.showme3d.studio.aigc.AigcImagePreprocessMode
import com.example.showme3d.studio.aigc.AigcPromptTemplate
import com.example.showme3d.studio.aigc.AigcPromptUseCase
import com.example.showme3d.studio.model.AnimationEasing
import com.example.showme3d.studio.model.AnimationLoopMode
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ObjectVersionOrigin
import com.example.showme3d.studio.model.PageAnnotation
import com.example.showme3d.studio.model.PageAnimation
import com.example.showme3d.studio.state.ObjectVersionUiItem
import com.example.showme3d.studio.state.PresentAnnotationTool
import com.example.showme3d.studio.state.SceneEnvironmentMode
import com.example.showme3d.studio.state.ShowSummary
import com.example.showme3d.studio.state.StrokeDraft
import com.example.showme3d.studio.state.WhiteboardTool
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI

@Composable
fun ToolbarPanel(
    documentType: DocumentType,
    showAnimationPlayback: Boolean,
    animationsPaused: Boolean,
    viewpointActive: Boolean,
    sceneEnvironmentMode: SceneEnvironmentMode,
    whiteboardTool: WhiteboardTool,
    assetsOpen: Boolean,
    agentOpen: Boolean,
    filesOpen: Boolean,
    moreOpen: Boolean,
    agentBusy: Boolean = false,
    agentKeyConfigured: Boolean = true,
    agentPrompt: String = "",
    agentStatus: String = "",
    agentStatusTone: AgentMessageTone = AgentMessageTone.NEUTRAL,
    agentMessages: List<AgentUiMessage> = emptyList(),
    agentTaskSteps: List<AgentTaskStep> = emptyList(),
    agentExpanded: Boolean = false,
    agentToolChainOpen: Boolean = false,
    onAssets: () -> Unit,
    onAgent: () -> Unit,
    onToggleAgentExpanded: () -> Unit = {},
    onToggleAgentToolChain: () -> Unit = {},
    onAgentPromptChange: (String) -> Unit = {},
    onRunAgent: (String) -> Unit = {},
    onOpenAgentKeyConfig: () -> Unit = {},
    onAddText: () -> Unit,
    onAddViewpoint: () -> Unit,
    onOpenStrokeTool: () -> Unit,
    onToggleWhiteboardComment: () -> Unit,
    onExitViewpoint: () -> Unit,
    onToggleAnimationPlayback: () -> Unit,
    onToggleMore: () -> Unit,
) {
    val isPresentation = documentType == DocumentType.PRESENTATION
    if (agentOpen && !viewpointActive) {
        AiToolbarBackplate {
            ActionIconButton(
                R.drawable.ic_tools,
                if (agentBusy) "返回手动，AI 将在后台继续" else "返回手动",
                true,
                active = true,
                onClick = onAgent,
            )
            ToolbarDivider()
            AiToolbarContent(
                busy = agentBusy,
                keyConfigured = agentKeyConfigured,
                prompt = agentPrompt,
                status = agentStatus,
                statusTone = agentStatusTone,
                taskSteps = agentTaskSteps,
                expanded = agentExpanded,
                toolChainOpen = agentToolChainOpen,
                onToggleExpanded = onToggleAgentExpanded,
                onToggleToolChain = onToggleAgentToolChain,
                onPromptChange = onAgentPromptChange,
                onRun = onRunAgent,
                onOpenKeyConfig = onOpenAgentKeyConfig,
            )
            ToolbarDivider()
            ActionIconButton(
                R.drawable.ic_more_horizontal,
                if (moreOpen) "收起更多" else "更多",
                true,
                active = moreOpen || filesOpen || sceneEnvironmentMode == SceneEnvironmentMode.VIRTUAL,
                onClick = onToggleMore,
            )
        }
    } else {
        ToolbarBackplate {
            if (viewpointActive) {
                ActionIconButton(R.drawable.ic_exit, "退出视点", true, active = true, onClick = onExitViewpoint)
            } else {
                ActionIconButton(R.drawable.ic_ai_assistant, "AI 助手", true, active = agentOpen, onClick = onAgent)
                ToolbarDivider()
                ActionIconButton(R.drawable.ic_model_cube, "资源", true, active = assetsOpen, onClick = onAssets)
                ToolbarDivider()
                ActionIconButton(R.drawable.ic_text, "文本", true, onAddText)
                ActionIconButton(R.drawable.ic_viewpoint, "视点", true, onAddViewpoint)
                ActionIconButton(
                    R.drawable.ic_brush,
                    if (isPresentation) "画笔" else "白板画笔",
                    true,
                    onOpenStrokeTool,
                )
                if (!isPresentation) {
                    ActionIconButton(
                        R.drawable.ic_comment,
                        if (whiteboardTool == WhiteboardTool.COMMENT) "关闭评论" else "评论",
                        true,
                        active = whiteboardTool == WhiteboardTool.COMMENT,
                        onClick = onToggleWhiteboardComment,
                    )
                }
                if (showAnimationPlayback) {
                    ActionIconButton(
                        if (animationsPaused) R.drawable.ic_play else R.drawable.ic_pause,
                        if (animationsPaused) "播放动画" else "暂停动画",
                        true,
                        active = animationsPaused,
                        onClick = onToggleAnimationPlayback,
                    )
                }
                ActionIconButton(
                    R.drawable.ic_more_horizontal,
                    if (moreOpen) "收起更多" else "更多",
                    true,
                    active = moreOpen || filesOpen || sceneEnvironmentMode == SceneEnvironmentMode.VIRTUAL,
                    onClick = onToggleMore,
                )
            }
        }
    }
}

@Composable
private fun AiToolbarContent(
    busy: Boolean,
    keyConfigured: Boolean,
    prompt: String,
    status: String,
    statusTone: AgentMessageTone,
    taskSteps: List<AgentTaskStep>,
    expanded: Boolean,
    toolChainOpen: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleToolChain: () -> Unit,
    onPromptChange: (String) -> Unit,
    onRun: (String) -> Unit,
    onOpenKeyConfig: () -> Unit,
) {
    var promptDraft by remember { mutableStateOf(prompt) }
    LaunchedEffect(prompt) {
        if (prompt != promptDraft) promptDraft = prompt
    }
    val stateLabel = when {
        busy -> "执行中"
        status.isNotBlank() && statusTone == AgentMessageTone.SUCCESS -> "完成"
        status.isNotBlank() && statusTone == AgentMessageTone.ERROR -> "失败"
        status.isNotBlank() && statusTone == AgentMessageTone.WARNING -> "未执行"
        status.isNotBlank() -> "待确认"
        else -> "待命"
    }
    val stateColor = when {
        busy -> StudioColors.Primary
        status.isNotBlank() -> statusTone.toolbarToneColor()
        else -> StudioColors.TextSecondary
    }
    fun updatePrompt(next: String) {
        promptDraft = next
        onPromptChange(next)
    }
    fun submitPrompt() {
        if (keyConfigured) {
            onRun(promptDraft)
        } else {
            onOpenKeyConfig()
        }
    }

    Row(
        modifier = Modifier.width(570.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(StudioColors.SurfaceMuted)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stateLabel, fontSize = 11.sp, color = stateColor)
                }
                AiPromptField(
                    prompt = promptDraft,
                    busy = busy,
                    expanded = expanded,
                    onPromptChange = ::updatePrompt,
                    onToggleExpanded = onToggleExpanded,
                    onSubmit = {
                        if (!busy && (promptDraft.trim().isNotBlank() || !keyConfigured)) submitPrompt()
                    },
                    modifier = Modifier.weight(1f),
                )
                StudioTextButton(
                    label = when {
                        busy -> "运行中"
                        keyConfigured -> "发送"
                        else -> "配置"
                    },
                    enabled = !busy && (promptDraft.trim().isNotBlank() || !keyConfigured),
                    modifier = Modifier.width(76.dp).height(46.dp),
                    style = StudioButtonStyle.Primary,
                    onClick = { submitPrompt() },
                )
                if (expanded) {
                    ActionIconButton(
                        R.drawable.ic_workflow,
                        if (toolChainOpen) "收起执行内容" else "执行内容",
                        taskSteps.isNotEmpty(),
                        active = toolChainOpen,
                        onClick = onToggleToolChain,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiPromptField(
    prompt: String,
    busy: Boolean,
    expanded: Boolean,
    onPromptChange: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (prompt.isEmpty()) {
                Text(
                    text = "让 AI 帮你改场景、生成物体或整理当前页",
                    fontSize = 13.sp,
                    color = StudioColors.TextTertiary,
                )
            }
            BasicTextField(
                value = prompt,
                onValueChange = { if (!busy) onPromptChange(it) },
                textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 14.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSubmit()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .stitchClickable(onClick = onToggleExpanded)
                .background(if (expanded) StitchColors.HomeGreen.copy(alpha = 0.28f) else StitchColors.PanelSurface),
            contentAlignment = Alignment.Center,
        ) {
            StitchSymbol(
                if (expanded) StitchSymbols.ExpandMore else StitchSymbols.KeyboardArrowUp,
                if (expanded) "收起 AI 历史" else "展开 AI 历史",
                size = 20.dp,
                color = StitchColors.PanelText,
            )
        }
    }
}

@Composable
fun AgentConversationDockPanel(
    expanded: Boolean,
    messages: List<AgentUiMessage>,
    status: String,
    statusTone: AgentMessageTone,
    taskSteps: List<AgentTaskStep>,
) {
    if (expanded) {
        Row(
            modifier = Modifier
                .width(720.dp)
                .clip(RoundedCornerShape(24.dp))
                .backgroundMaterial(true, Material.Regular)
                .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
                .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AiToolbarHistory(
                messages = messages,
                status = status,
                statusTone = statusTone,
                modifier = Modifier
                    .weight(1f)
                    .height(214.dp),
            )
        }
        return
    }
    val latestMessage = messages.lastOrNull { it.text.isNotBlank() }
    val stepSummary = taskSteps.lastOrNull { it.status == AgentTaskStepStatus.RUNNING }?.let { step ->
        "${step.title}${step.detail.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}"
    }
    val text = stepSummary
        ?: status.takeIf { it.isNotBlank() }
        ?: latestMessage?.let { "${it.role.shortLabel()}：${it.text}" }
        ?: "最近一次：等待你的指令"
    Box(
        modifier = Modifier
            .width(570.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text.take(54),
            fontSize = 12.sp,
            color = when {
                statusTone == AgentMessageTone.SUCCESS -> Color(0xFF14532D)
                statusTone == AgentMessageTone.WARNING -> Color(0xFF92400E)
                statusTone == AgentMessageTone.ERROR -> Color(0xFF991B1B)
                else -> StudioColors.TextPrimary
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AiToolbarHistory(
    messages: List<AgentUiMessage>,
    status: String,
    statusTone: AgentMessageTone,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, messages.size, status) {
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(StudioColors.SurfaceMuted.copy(alpha = 0.76f))
            .verticalScroll(scrollState)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(statusTone.softToolbarBackground())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(text = status, fontSize = 12.sp, color = statusTone.toolbarToneColor())
            }
        }
        messages.takeLast(10).forEach { message ->
            AiToolbarMessageBubble(message)
        }
    }
}

@Composable
private fun AiToolbarMessageBubble(message: AgentUiMessage) {
    val isUser = message.role == AgentMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .width(if (isUser) 310.dp else 500.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (isUser) StudioColors.PrimarySoft else StudioColors.SurfaceRaised.copy(alpha = 0.86f))
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = message.role.shortLabel(), fontSize = 10.sp, color = StudioColors.TextSecondary)
            Text(text = message.text, fontSize = 12.sp, color = message.tone.toolbarToneColor())
        }
    }
}

@Composable
fun AgentSuggestionDockPanel(
    suggestions: List<AgentSuggestion>,
    showDefaultSuggestions: Boolean,
    busy: Boolean,
    onPromptChange: (String) -> Unit,
) {
    val chips = agentSuggestionChips(
        suggestions = suggestions,
        includeDefaults = showDefaultSuggestions,
    )
    if (chips.isEmpty()) return
    AiToolbarSuggestionRow(
        chips = chips,
        busy = busy,
        onPromptChange = onPromptChange,
    )
}

@Composable
private fun AiToolbarSuggestionRow(
    chips: List<Pair<String, String>>,
    busy: Boolean,
    onPromptChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.take(3).forEach { (label, chipPrompt) ->
            ToolbarChip(label = label, enabled = !busy) {
                onPromptChange(chipPrompt)
            }
        }
    }
}

@Composable
fun AgentToolChainDockPanel(
    steps: List<AgentTaskStep>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(286.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StudioColors.SurfaceRaised.copy(alpha = 0.72f))
        .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "执行路径", fontSize = 13.sp, color = StudioColors.TextPrimary)
            ActionIconButton(R.drawable.ic_close, "关闭执行路径", true, onClick = onClose)
        }
        if (steps.isEmpty()) {
            Text(text = "上一轮还没有工具调用。", fontSize = 12.sp, color = StudioColors.TextSecondary)
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                steps.forEach { step ->
                    AgentToolStepRow(step)
                }
            }
        }
    }
}

@Composable
private fun AgentToolStepRow(step: AgentTaskStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(step.status.color().copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = "${step.index}", fontSize = 11.sp, color = step.status.color())
        Column(modifier = Modifier.weight(1f)) {
            Text(text = step.title, fontSize = 12.sp, color = StudioColors.TextPrimary)
            if (step.detail.isNotBlank()) {
                Text(text = step.detail.take(80), fontSize = 10.sp, color = StudioColors.TextSecondary)
            }
        }
        Text(text = step.status.label(), fontSize = 10.sp, color = step.status.color())
    }
}

private fun agentSuggestionChips(
    suggestions: List<AgentSuggestion>,
    includeDefaults: Boolean,
): List<Pair<String, String>> =
    when {
        suggestions.isNotEmpty() -> suggestions.take(3).map { it.actionLabel to it.prompt }
        includeDefaults ->
        DefaultAgentExampleGroups
            .flatMap { it.examples }
            .take(3)
            .map { it.label to it.prompt }
        else -> emptyList()
    }

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(StudioColors.SurfaceDisabled),
    )
}

@Composable
private fun ToolbarChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .stitchClickable(enabled = enabled, onClick = onClick)
            .background(if (enabled) StudioColors.PrimarySoft else StudioColors.SurfaceDisabled)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (enabled) StudioColors.TextPrimary else StudioColors.TextSecondary,
        )
    }
}

private fun AgentMessageTone.toolbarToneColor(): Color =
    when (this) {
        AgentMessageTone.NEUTRAL -> StudioColors.TextSecondary
        AgentMessageTone.SUCCESS -> StudioColors.Success
        AgentMessageTone.WARNING -> StudioColors.Warning
        AgentMessageTone.ERROR -> StudioColors.Danger
    }

private fun AgentMessageTone.softToolbarBackground(): Color =
    when (this) {
        AgentMessageTone.NEUTRAL -> StudioColors.SurfaceRaised.copy(alpha = 0.72f)
        AgentMessageTone.SUCCESS -> Color(0xFFDCFCE7)
        AgentMessageTone.WARNING -> Color(0xFFFFEDD5)
        AgentMessageTone.ERROR -> Color(0xFFFEE2E2)
    }

private fun AgentMessageRole.shortLabel(): String =
    when (this) {
        AgentMessageRole.USER -> "你"
        AgentMessageRole.ASSISTANT -> "AI"
        AgentMessageRole.SYSTEM -> "系统"
    }

private fun AgentTaskStepStatus.color(): Color =
    when (this) {
        AgentTaskStepStatus.PENDING -> StudioColors.TextTertiary
        AgentTaskStepStatus.RUNNING -> StudioColors.Primary
        AgentTaskStepStatus.SUCCEEDED -> StudioColors.Success
        AgentTaskStepStatus.FAILED -> StudioColors.Danger
        AgentTaskStepStatus.ROLLED_BACK -> StudioColors.Warning
    }

private fun AgentTaskStepStatus.label(): String =
    when (this) {
        AgentTaskStepStatus.PENDING -> "等待"
        AgentTaskStepStatus.RUNNING -> "进行"
        AgentTaskStepStatus.SUCCEEDED -> "完成"
        AgentTaskStepStatus.FAILED -> "失败"
        AgentTaskStepStatus.ROLLED_BACK -> "回滚"
    }

@Composable
fun ToolbarUndoPanel(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    ToolbarCompactBackplate {
        ToolbarCompactIconButton(R.drawable.ic_undo, "撤销", canUndo, onUndo)
        ToolbarCompactIconButton(R.drawable.ic_redo, "重做", canRedo, onRedo)
    }
}

@Composable
private fun ToolbarBackplate(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun AiToolbarBackplate(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(32.dp))
            .padding(horizontal = 17.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ToolbarCompactBackplate(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ToolbarCompactIconButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(38.dp)
            .offset(y = if (pressed && enabled) 2.dp else 0.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .stitchClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .background(
                when {
                    !enabled -> StitchColors.PanelSurfaceMuted
                    pressed -> StudioColors.PrimarySoft
                    else -> StitchColors.PanelSurface
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        val iconColor = if (enabled) StitchColors.PanelText else StitchColors.PanelTextMuted
        val symbol = stitchSymbolForDrawable(iconRes)
        if (symbol != null) {
            StitchSymbol(symbol, contentDescription, size = 21.dp, color = iconColor)
        } else {
            com.pico.spatial.ui.design.Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.width(18.dp).height(18.dp),
                tint = iconColor,
            )
        }
    }
}

@Composable
fun ToolbarMorePanel(
    sceneEnvironmentMode: SceneEnvironmentMode,
    filesOpen: Boolean,
    onHome: () -> Unit,
    onFiles: () -> Unit,
    onToggleSceneEnvironment: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.97f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolbarMenuButton(R.drawable.ic_exit, "返回主页", true, onHome)
        ToolbarMenuButton(R.drawable.ic_file, if (filesOpen) "关闭文件" else "文件", filesOpen, onFiles)
        ToolbarMenuButton(
            R.drawable.ic_scene,
            if (sceneEnvironmentMode == SceneEnvironmentMode.VIRTUAL) "切回实景" else "切到虚拟",
            sceneEnvironmentMode == SceneEnvironmentMode.VIRTUAL,
            onToggleSceneEnvironment,
        )
    }
}

@Composable
private fun ToolbarMenuButton(
    iconRes: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .stitchClickable(onClick = onClick)
            .background(if (active) StitchColors.HomeGreen.copy(alpha = 0.28f) else StitchColors.PanelSurfaceMuted)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stitchSymbolForDrawable(iconRes)?.let {
            StitchSymbol(it, label, size = 23.dp, color = StitchColors.PanelText)
        }
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = StitchColors.PanelText)
    }
}

@Composable
fun AnnotationManagePanel(
    annotationsVisible: Boolean,
    hasStrokeAnnotations: Boolean,
    comments: List<PageAnnotation>,
    onToggleVisible: () -> Unit,
    onDeleteStrokes: () -> Unit,
    onEditComment: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "批注", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = StitchColors.PanelText)
            ActionIconButton(
                R.drawable.ic_scene,
                if (annotationsVisible) "隐藏批注" else "显示批注",
                true,
                active = annotationsVisible,
                onClick = onToggleVisible,
            )
            ActionIconButton(
                R.drawable.ic_delete,
                "删除本页画笔",
                hasStrokeAnnotations,
                onClick = onDeleteStrokes,
            )
        }
        comments.forEachIndexed { index, comment ->
            Row(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StitchColors.PanelSurfaceMuted)
                    .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "评论 ${index + 1}: ${(comment.commentText?.takeIf { it.isNotEmpty() } ?: "待评论").take(14)}",
                    fontSize = 13.sp,
                    color = StudioColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                ActionIconButton(R.drawable.ic_text, "编辑评论", true) { onEditComment(comment.id) }
                ActionIconButton(R.drawable.ic_delete, "删除评论", true) { onDeleteComment(comment.id) }
            }
        }
    }
}

@Composable
fun StrokeEditorPanel(
    draft: StrokeDraft?,
    onColor: (Long) -> Unit,
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
) {
    val current = draft ?: return
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.Brush, "画笔", size = 20.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(8.dp))
            Text(text = "画笔", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            ActionIconButton(R.drawable.ic_undo, "撤销上一画", current.committedStrokeCount > 0 || current.activePath != null, onUndoStroke)
            Spacer(Modifier.width(6.dp))
            ActionIconButton(R.drawable.ic_redo, "重做上一画", current.redoPaths.isNotEmpty(), onRedoStroke)
            Spacer(Modifier.width(6.dp))
            ActionIconButton(R.drawable.ic_add, "结束并生成物体", current.hasDrawableContent, onFinish)
            Spacer(Modifier.width(6.dp))
            ActionIconButton(R.drawable.ic_close, "关闭画笔", true, onClose)
        }
        Text(
            text = "在空间中捏合并移动手指来画线；结束后会合并成可移动、缩放、加动画的正式物体。",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        PanelGroup {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "颜色",
                    fontSize = 13.sp,
                    color = StudioColors.TextPrimary,
                )
                ObjectColors.STROKE_PALETTE.chunked(6).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowColors.forEach { color ->
                            ColorSwatchButton(
                                color = Color(color),
                                contentDescription = "笔刷颜色",
                                active = current.selectedColor == color,
                            ) {
                                onColor(color)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PageStripPanel(
    currentPageIndex: Int,
    pageCount: Int,
    maxPages: Int,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onPresent: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onDeletePage: () -> Unit,
) {
    if (collapsed) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .backgroundMaterial(true, Material.Regular)
                .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
                .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(28.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .stitchClickable(onClick = onToggleCollapsed)
                    .background(StitchColors.PanelSurfaceMuted)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "第 ${currentPageIndex + 1}/$pageCount 页",
                    fontFamily = StitchNunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = StitchColors.PanelText,
                )
            }
            PageStripActionButton(R.drawable.ic_play, "进入演示", enabled = true, large = false, onClick = onPresent)
        }
        return
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pageStripLayout = pageStripLayout(pageCount)
        val pageScrollState = rememberScrollState()
        val pageScrollScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val pageStridePx = with(density) { PAGE_STRIP_PAGE_STRIDE_DP.dp.roundToPx() }
        val pageViewportPx = with(density) { pageStripLayout.viewportWidthDp.dp.roundToPx() }
        val pageBatchPx = pageStridePx * pageStripLayout.visiblePageCount
        LaunchedEffect(currentPageIndex, pageCount, pageScrollState.maxValue, pageStripLayout.showScrollButtons) {
            if (!pageStripLayout.showScrollButtons) {
                if (pageScrollState.value != 0) pageScrollState.scrollTo(0)
                return@LaunchedEffect
            }
            val selectedStart = currentPageIndex * pageStridePx
            val selectedEnd = selectedStart + with(density) { PAGE_STRIP_PAGE_WIDTH_DP.dp.roundToPx() }
            when {
                selectedStart < pageScrollState.value -> pageScrollState.animateScrollTo(selectedStart)
                selectedEnd > pageScrollState.value + pageViewportPx -> {
                    pageScrollState.animateScrollTo(selectedEnd - pageViewportPx)
                }
            }
        }
        if (pageStripLayout.showScrollButtons) {
            PageStripActionButton(
                R.drawable.ic_previous,
                "向前浏览页码",
                enabled = pageScrollState.value > 0,
                large = false,
                onClick = {
                    pageScrollScope.launch {
                        pageScrollState.animateScrollTo((pageScrollState.value - pageBatchPx).coerceAtLeast(0))
                    }
                },
                symbolOverride = StitchSymbols.ChevronLeft,
            )
        }
        Row(
            modifier = Modifier
                .width(pageStripLayout.viewportWidthDp.dp)
                .horizontalScroll(pageScrollState),
            horizontalArrangement = Arrangement.spacedBy(PAGE_STRIP_PAGE_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                PageButton(
                    label = "${index + 1}",
                    selected = index == currentPageIndex,
                    enabled = true,
                    onClick = { onSelectPage(index) },
                )
            }
        }
        if (pageStripLayout.showScrollButtons) {
            PageStripActionButton(
                R.drawable.ic_arrow_right,
                "向后浏览页码",
                enabled = pageScrollState.value < pageScrollState.maxValue,
                large = false,
                onClick = {
                    pageScrollScope.launch {
                        pageScrollState.animateScrollTo(
                            (pageScrollState.value + pageBatchPx).coerceAtMost(pageScrollState.maxValue)
                        )
                    }
                },
                symbolOverride = StitchSymbols.ChevronRight,
            )
        }
        PageStripActionButton(R.drawable.ic_add, "新增页", pageCount < maxPages, large = true, onClick = onAddPage)
        PageStripActionButton(R.drawable.ic_copy, "复制页", pageCount < maxPages, large = false, onClick = onDuplicatePage)
        if (pageCount > 1) {
            PageStripActionButton(R.drawable.ic_delete, "删除页", enabled = true, large = false, onClick = onDeletePage)
        }
        PageStripActionButton(R.drawable.ic_play, "进入演示", enabled = true, large = false, onClick = onPresent)
        PageStripActionButton(R.drawable.ic_chevron_down, "收起页签", enabled = true, large = false, onClick = onToggleCollapsed)
    }
}

private const val PAGE_STRIP_VISIBLE_PAGE_COUNT = 5
private const val PAGE_STRIP_PAGE_WIDTH_DP = 66
private const val PAGE_STRIP_PAGE_GAP_DP = 9
private const val PAGE_STRIP_PAGE_STRIDE_DP = PAGE_STRIP_PAGE_WIDTH_DP + PAGE_STRIP_PAGE_GAP_DP

internal data class PageStripLayout(
    val visiblePageCount: Int,
    val viewportWidthDp: Int,
    val showScrollButtons: Boolean,
)

internal fun pageStripLayout(pageCount: Int): PageStripLayout {
    val visiblePageCount = pageCount.coerceIn(1, PAGE_STRIP_VISIBLE_PAGE_COUNT)
    return PageStripLayout(
        visiblePageCount = visiblePageCount,
        viewportWidthDp = PAGE_STRIP_PAGE_WIDTH_DP * visiblePageCount +
            PAGE_STRIP_PAGE_GAP_DP * (visiblePageCount - 1),
        showScrollButtons = pageCount > PAGE_STRIP_VISIBLE_PAGE_COUNT,
    )
}

@Composable
fun SelectionBarPanel(
    documentType: DocumentType,
    selectedType: ObjectType,
    showGeometryColors: Boolean,
    selectedColor: Long,
    locked: Boolean,
    autoRotateY: Boolean,
    faceViewer: Boolean,
    textEditorOpen: Boolean,
    geometryColorPickerOpen: Boolean,
    imageSubjectPreviewActive: Boolean,
    imageSubjectPreviewBusy: Boolean,
    imageToModelBusy: Boolean,
    imageToModelEnabled: Boolean,
    imageToImageBusy: Boolean,
    textToImageBusy: Boolean,
    strokeToSeedreamBusy: Boolean,
    strokeToModelBusy: Boolean,
    objectVersions: List<ObjectVersionUiItem>,
    onSetGeometryColorPickerOpen: (Boolean) -> Unit,
    onToggleImageSubjectPreview: () -> Unit,
    onGenerateImageVariation: () -> Unit,
    onGenerateImageToModel: () -> Unit,
    onGenerateTextToImage: () -> Unit,
    onGenerateStrokeToImage: () -> Unit,
    onGenerateStrokeToModel: () -> Unit,
    onAnimation: () -> Unit,
    onToggleAutoRotateY: () -> Unit,
    onToggleFaceViewer: () -> Unit,
    onEnterViewpoint: () -> Unit,
    onEditText: () -> Unit,
    onCloseTextEditor: () -> Unit,
    onTextColor: (Long) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onSwitchObjectVersion: (String) -> Unit,
    onBranchObjectVersion: (String?) -> Unit,
) {
    var aiToolsOpen by remember(selectedType) { mutableStateOf(false) }
    var textColorPickerOpen by remember(selectedType) { mutableStateOf(false) }
    var versionManagerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(selectedType, locked) {
        aiToolsOpen = false
        textColorPickerOpen = false
    }
    LaunchedEffect(objectVersions.map { it.versionId }, selectedType, locked) {
        versionManagerOpen = false
    }
    val isPresentation = documentType == DocumentType.PRESENTATION
    val showAiTools = selectedType == ObjectType.IMAGE ||
        selectedType == ObjectType.STROKE
    val showTextColors = selectedType == ObjectType.TEXT
    val colorPickerOpen = if (showTextColors) textColorPickerOpen else geometryColorPickerOpen
    fun closeExpandedTools() {
        aiToolsOpen = false
        textColorPickerOpen = false
        if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
        versionManagerOpen = false
    }
    fun openColorPicker() {
        val willOpen = !colorPickerOpen
        aiToolsOpen = false
        versionManagerOpen = false
        if (textEditorOpen) onCloseTextEditor()
        if (showTextColors) {
            textColorPickerOpen = willOpen
            if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
        } else {
            textColorPickerOpen = false
            onSetGeometryColorPickerOpen(willOpen)
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .backgroundMaterial(true, Material.Regular)
                .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
                .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (locked) {
                    ActionIconButton(R.drawable.ic_unlock, "解锁", true, onUnlock)
                } else {
                    if (showAiTools) {
                        ActionIconButton(
                            R.drawable.ic_magic_wand,
                            if (aiToolsOpen) "收起 AI 处理" else "AI 处理",
                            true,
                            active = aiToolsOpen,
                        ) {
                            aiToolsOpen = !aiToolsOpen
                            if (aiToolsOpen) {
                                if (textEditorOpen) onCloseTextEditor()
                                textColorPickerOpen = false
                                if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
                                versionManagerOpen = false
                            }
                        }
                    }
                    if (selectedType == ObjectType.TEXT) {
                        ActionIconButton(
                            R.drawable.ic_ai_image,
                            if (textToImageBusy) "生成中" else "生成图片",
                            !textToImageBusy,
                            active = false,
                            loading = textToImageBusy,
                            onClick = {
                                closeExpandedTools()
                                if (textEditorOpen) onCloseTextEditor()
                                onGenerateTextToImage()
                            },
                        )
                    }
                    if (selectedType == ObjectType.TEXT) {
                        ActionIconButton(
                            R.drawable.ic_text,
                            if (textEditorOpen) "关闭文本编辑" else "编辑文本",
                            true,
                            active = textEditorOpen,
                            onClick = {
                                aiToolsOpen = false
                                textColorPickerOpen = false
                                if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
                                versionManagerOpen = false
                                onEditText()
                            },
                        )
                    }
                    if (selectedType == ObjectType.VIEWPOINT) {
                        ActionIconButton(R.drawable.ic_viewpoint, "进入视点", true, onEnterViewpoint)
                    }
                    if (selectedType == ObjectType.TEXT || selectedType == ObjectType.IMAGE) {
                        ActionIconButton(
                            R.drawable.ic_viewpoint,
                            if (faceViewer) "关闭面向我" else "面向我",
                            true,
                            active = faceViewer,
                            onClick = onToggleFaceViewer,
                        )
                    }
                    if (showTextColors || showGeometryColors) {
                        ColorSwatchButton(
                            color = Color(selectedColor),
                            contentDescription = "当前颜色",
                            active = colorPickerOpen,
                            onClick = { openColorPicker() },
                        )
                    }
                    ActionIconButton(R.drawable.ic_copy, "复制物体", true) {
                        closeExpandedTools()
                        onDuplicate()
                    }
                    ActionIconButton(R.drawable.ic_delete, "删除物体", true) {
                        closeExpandedTools()
                        onDelete()
                    }
                    ActionIconButton(R.drawable.ic_lock, "锁定", true) {
                        closeExpandedTools()
                        onLock()
                    }
                    if (selectedType == ObjectType.MODEL || selectedType == ObjectType.STROKE) {
                        ActionIconButton(
                            R.drawable.ic_rotate_right,
                            if (autoRotateY) "关闭自转" else "自转",
                            true,
                            active = autoRotateY,
                            onClick = onToggleAutoRotateY,
                        )
                    }
                    if (selectedType != ObjectType.VIEWPOINT) {
                        ActionIconButton(R.drawable.ic_animation, "动画", true) {
                            closeExpandedTools()
                            if (textEditorOpen) onCloseTextEditor()
                            onAnimation()
                        }
                    }
                    if (objectVersions.size > 1) {
                        ActionIconButton(
                            R.drawable.ic_branch,
                            "版本管理",
                            true,
                            active = versionManagerOpen,
                        ) {
                            versionManagerOpen = !versionManagerOpen
                            if (versionManagerOpen) {
                                aiToolsOpen = false
                                textColorPickerOpen = false
                                if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
                                if (textEditorOpen) onCloseTextEditor()
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !locked && showAiTools && aiToolsOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            SelectionExpansionPanel {
                AiObjectToolsPanel(
                    selectedType = selectedType,
                    imageSubjectPreviewActive = imageSubjectPreviewActive,
                    imageSubjectPreviewBusy = imageSubjectPreviewBusy,
                    imageToModelBusy = imageToModelBusy,
                    imageToModelEnabled = imageToModelEnabled,
                    imageToImageBusy = imageToImageBusy,
                    strokeToSeedreamBusy = strokeToSeedreamBusy,
                    strokeToModelBusy = strokeToModelBusy,
                    onToggleImageSubjectPreview = onToggleImageSubjectPreview,
                    onGenerateImageVariation = onGenerateImageVariation,
                    onGenerateImageToModel = onGenerateImageToModel,
                    onGenerateStrokeToImage = onGenerateStrokeToImage,
                    onGenerateStrokeToModel = onGenerateStrokeToModel,
                )
            }
        }
        AnimatedVisibility(
            visible = !locked && (showTextColors || showGeometryColors) && colorPickerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            SelectionExpansionPanel {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val palette = if (showTextColors) ObjectColors.TEXT_PALETTE else ObjectColors.GEOMETRY_PALETTE
                    palette.forEach { color ->
                        ColorSwatchButton(Color(color), "选择颜色") {
                            onTextColor(color)
                            textColorPickerOpen = false
                            if (geometryColorPickerOpen) onSetGeometryColorPickerOpen(false)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !locked && objectVersions.size > 1 && versionManagerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            VersionManagerPanel(
                versions = objectVersions,
                onSwitchObjectVersion = onSwitchObjectVersion,
                onBranchObjectVersion = onBranchObjectVersion,
            )
        }
    }
}

@Composable
private fun SelectionExpansionPanel(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun AiObjectToolsPanel(
    selectedType: ObjectType,
    imageSubjectPreviewActive: Boolean,
    imageSubjectPreviewBusy: Boolean,
    imageToModelBusy: Boolean,
    imageToModelEnabled: Boolean,
    imageToImageBusy: Boolean,
    strokeToSeedreamBusy: Boolean,
    strokeToModelBusy: Boolean,
    onToggleImageSubjectPreview: () -> Unit,
    onGenerateImageVariation: () -> Unit,
    onGenerateImageToModel: () -> Unit,
    onGenerateStrokeToImage: () -> Unit,
    onGenerateStrokeToModel: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (selectedType) {
            ObjectType.IMAGE -> {
                ActionIconButton(
                    R.drawable.ic_subject_avatar,
                    when {
                        imageSubjectPreviewBusy -> "处理中"
                        imageSubjectPreviewActive -> "恢复原图"
                        else -> "保留主体"
                    },
                    !imageSubjectPreviewBusy,
                    active = imageSubjectPreviewActive,
                    loading = imageSubjectPreviewBusy,
                    onClick = onToggleImageSubjectPreview,
                )
                ActionIconButton(
                    R.drawable.ic_ai_image,
                    if (imageToImageBusy) "生成中" else "AI 改图",
                    !imageSubjectPreviewBusy && !imageToModelBusy && !imageToImageBusy,
                    active = false,
                    loading = imageToImageBusy,
                    onClick = onGenerateImageVariation,
                )
                ActionIconButton(
                    R.drawable.ic_ai_3d_model,
                    if (imageToModelBusy) "生成中" else "生成 3D",
                    !imageSubjectPreviewBusy && imageToModelEnabled && !imageToModelBusy,
                    active = false,
                    loading = imageToModelBusy,
                    onClick = onGenerateImageToModel,
                )
            }
            ObjectType.STROKE -> {
                val strokeAiBusy = strokeToSeedreamBusy || strokeToModelBusy
                ActionIconButton(
                    R.drawable.ic_ai_image,
                    if (strokeToSeedreamBusy) "生成中" else "AI 成图",
                    !strokeAiBusy,
                    active = false,
                    loading = strokeToSeedreamBusy,
                    onClick = onGenerateStrokeToImage,
                )
                ActionIconButton(
                    R.drawable.ic_ai_3d_model,
                    if (strokeToModelBusy) "生成中" else "生成 3D",
                    !strokeAiBusy,
                    active = false,
                    loading = strokeToModelBusy,
                    onClick = onGenerateStrokeToModel,
                )
            }
            ObjectType.TEXT,
            ObjectType.MODEL,
            ObjectType.VIEWPOINT,
            -> Unit
        }
    }
}

@Composable
private fun VersionManagerPanel(
    versions: List<ObjectVersionUiItem>,
    onSwitchObjectVersion: (String) -> Unit,
    onBranchObjectVersion: (String?) -> Unit,
) {
    val active = versions.firstOrNull { it.active }
    val visibleVersions = if (versions.size <= 6) {
        versions
    } else {
        (listOfNotNull(active) + versions.takeLast(5)).distinctBy { it.versionId }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "版本管理", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = StitchColors.PanelText)
            active?.let {
                Text(
                    text = it.title,
                    fontSize = 12.sp,
                    color = StudioColors.TextSecondary,
                )
            }
            if (versions.size > visibleVersions.size) {
                Text(
                    text = "+${versions.size - visibleVersions.size}",
                    fontSize = 12.sp,
                    color = StudioColors.TextSecondary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            visibleVersions.forEach { version ->
                VersionItemChip(
                    version = version,
                    onClick = { onSwitchObjectVersion(version.versionId) },
                    onBranch = { onBranchObjectVersion(version.versionId) },
                )
            }
        }
    }
}

@Composable
private fun VersionItemChip(
    version: ObjectVersionUiItem,
    onClick: () -> Unit,
    onBranch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .width(178.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (version.active) StitchColors.HomeGreen.copy(alpha = 0.28f) else StitchColors.PanelSurfaceMuted)
            .border(if (version.active) 2.dp else 1.dp, if (version.active) StitchColors.HomeGreen else StitchColors.PanelOutline, RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .stitchClickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersionContentIcon(version = version)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text = version.title, fontSize = 13.sp, color = StudioColors.TextPrimary)
                if (version.detail.isNotBlank()) {
                    Text(text = version.detail, fontSize = 10.sp, color = StudioColors.TextSecondary)
                }
            }
        }
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(9.dp))
                .stitchClickable(onClick = onBranch)
                .background(StudioColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            com.pico.spatial.ui.design.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_add),
                contentDescription = "分叉",
                modifier = Modifier.width(13.dp).height(13.dp),
                tint = StudioColors.SurfaceRaised,
            )
        }
    }
}

@Composable
private fun VersionContentIcon(
    version: ObjectVersionUiItem,
) {
    Box(
        modifier = Modifier
            .width(23.dp)
            .height(23.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (version.active) StudioColors.SurfaceRaised else StudioColors.SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        com.pico.spatial.ui.design.Icon(
            painter = androidx.compose.ui.res.painterResource(version.iconRes()),
            contentDescription = version.title,
            modifier = Modifier.width(14.dp).height(14.dp),
            tint = if (version.active) StudioColors.Primary else StudioColors.TextSecondary,
        )
    }
}

private fun ObjectVersionUiItem.iconRes(): Int =
    when (origin) {
        ObjectVersionOrigin.IMAGE_TO_MODEL,
        ObjectVersionOrigin.STROKE_TO_MODEL -> R.drawable.ic_ai_3d_model
        ObjectVersionOrigin.TEXT_TO_IMAGE,
        ObjectVersionOrigin.IMAGE_TO_IMAGE,
        ObjectVersionOrigin.STROKE_TO_IMAGE -> R.drawable.ic_ai_image
        ObjectVersionOrigin.IMAGE_PREPROCESS_LOCAL_SUBJECT,
        ObjectVersionOrigin.IMAGE_PREPROCESS_SEEDREAM_CLEAN -> R.drawable.ic_subject_avatar
        ObjectVersionOrigin.STROKE_PREVIEW -> R.drawable.ic_image
        ObjectVersionOrigin.ORIGINAL,
        ObjectVersionOrigin.UNKNOWN -> when (type) {
            ObjectType.MODEL -> R.drawable.ic_model_cube
            ObjectType.TEXT -> R.drawable.ic_text
            else -> R.drawable.ic_image
        }
    }

@Composable
fun AnimationEditorPanel(
    animation: PageAnimation?,
    editingEndpoints: Boolean,
    showAdd: Boolean,
    showDelete: Boolean,
    onAdd: () -> Unit,
    onDuration: (Float) -> Unit,
    onEasing: (AnimationEasing) -> Unit,
    onLoopMode: (AnimationLoopMode) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val current = animation ?: return
    var expandedPicker by remember { mutableStateOf<AnimationPicker?>(null) }
    LaunchedEffect(current) {
        expandedPicker = null
    }
    Column(
        modifier = Modifier
            .width(352.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.Animation, "页内动画", size = 21.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(8.dp))
            Text(text = "页内动画", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            if (showAdd) {
                ActionIconButton(R.drawable.ic_add, "添加动画", true, onAdd)
                Spacer(Modifier.width(6.dp))
            }
            if (showDelete) {
                ActionIconButton(R.drawable.ic_delete, "删除动画", true, onDelete)
                Spacer(Modifier.width(6.dp))
            }
            ActionIconButton(R.drawable.ic_close, "关闭动画面板", true, onClose)
        }
        Text(
            text = if (editingEndpoints) "拖动场景里的起点/终点两个副本来定义动画，点对号生效。" else "选中带动画物体会先定住；点动画可编辑起点/终点。",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        PanelGroup {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ParameterSelectChip(
                    label = "时长",
                    value = "${current.durationSeconds.formatOne()}s",
                    iconRes = R.drawable.ic_play,
                    expanded = expandedPicker == AnimationPicker.DURATION,
                    modifier = Modifier.weight(1f),
                ) {
                    expandedPicker = expandedPicker.toggle(AnimationPicker.DURATION)
                }
                ParameterSelectChip(
                    label = "缓动",
                    value = current.easing.displayName(),
                    iconRes = R.drawable.ic_animation,
                    expanded = expandedPicker == AnimationPicker.EASING,
                    modifier = Modifier.weight(1f),
                ) {
                    expandedPicker = expandedPicker.toggle(AnimationPicker.EASING)
                }
                ParameterSelectChip(
                    label = "循环",
                    value = current.loopMode.displayName(),
                    iconRes = R.drawable.ic_refresh,
                    expanded = expandedPicker == AnimationPicker.LOOP,
                    modifier = Modifier.weight(1f),
                ) {
                    expandedPicker = expandedPicker.toggle(AnimationPicker.LOOP)
                }
            }
            if (expandedPicker != null) {
                Spacer(Modifier.height(6.dp))
            }
            when (expandedPicker) {
                AnimationPicker.DURATION -> OptionTray {
                    ANIMATION_DURATION_OPTIONS.forEach { seconds ->
                        TextOptionChip(
                            label = "${seconds.formatOne()}s",
                            active = current.durationSeconds == seconds,
                        ) {
                            expandedPicker = null
                            onDuration(seconds)
                        }
                    }
                }
                AnimationPicker.EASING -> OptionTray {
                    AnimationEasing.entries.forEach { easing ->
                        TextOptionChip(
                            label = easing.displayName(),
                            active = current.easing == easing,
                        ) {
                            expandedPicker = null
                            onEasing(easing)
                        }
                    }
                }
                AnimationPicker.LOOP -> OptionTray {
                    AnimationLoopMode.entries.forEach { mode ->
                        TextOptionChip(
                            label = mode.displayName(),
                            active = current.loopMode == mode,
                        ) {
                            expandedPicker = null
                            onLoopMode(mode)
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
fun RotationControlsPanel(
    reverseDirection: Boolean = false,
    onRotateStart: () -> Unit,
    onRotate: (Float) -> Unit,
    onRotateEnd: () -> Unit,
) {
    val directionSign = if (reverseDirection) -1f else 1f
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .backgroundMaterial(true, Material.Thin)
            .background(StitchColors.PanelSurface.copy(alpha = 0.94f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldRotateButton(R.drawable.ic_rotate_left, "左旋", direction = -1f * directionSign, onRotateStart, onRotate, onRotateEnd)
        HoldRotateButton(R.drawable.ic_rotate_right, "右旋", direction = 1f * directionSign, onRotateStart, onRotate, onRotateEnd)
    }
}

@Composable
private fun HoldRotateButton(
    iconRes: Int,
    contentDescription: String,
    direction: Float,
    onRotateStart: () -> Unit,
    onRotate: (Float) -> Unit,
    onRotateEnd: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        onRotateStart()
        var lastFrameNanos = withFrameNanos { it }
        try {
            while (isActive) {
                val frameNanos = withFrameNanos { it }
                val seconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
                onRotate(direction * ROTATION_BUTTON_RADIANS_PER_SECOND * seconds)
            }
        } finally {
            onRotateEnd()
        }
    }
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (pressed) StudioColors.PrimarySoft else StudioColors.SurfaceMuted)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        StitchSymbol(stitchSymbolForDrawable(iconRes) ?: StitchSymbols.Autorenew, contentDescription, size = 19.dp, color = StitchColors.PanelText)
    }
}

@Composable
fun FilePanel(
    onUnavailableFeature: () -> Unit = {},
    shows: List<ShowSummary>,
    rodinApiKey: String = "",
    imageToImageApiKey: String = "",
    doubaoApiKey: String = "",
    onNewShow: () -> Unit,
    onNewWhiteboard: () -> Unit,
    onSwitchShow: (Int) -> Unit,
    onRenameShow: (Int, String) -> Unit,
    onDeleteShow: (Int) -> Unit,
    onSaveAigcKeys: (rodinApiKey: String, imageToImageApiKey: String, doubaoApiKey: String) -> Unit = { _, _, _ -> },
    onClose: () -> Unit,
) {
    var filePanelMode by remember { mutableStateOf(FilePanelMode.List) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    val pendingDeleteShow = pendingDeleteIndex?.let { index -> shows.firstOrNull { it.index == index } }
    val panelTitle = when (filePanelMode) {
        FilePanelMode.List -> "我的文件"
        FilePanelMode.NewType -> "新建文件"
        FilePanelMode.DeleteConfirm -> "删除文件"
        FilePanelMode.KeyConfig -> "AI Key 配置"
    }

    Column(
        modifier = Modifier
            .width(410.dp)
            .height(520.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(StitchColors.PanelSurface)
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(22.dp))
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (filePanelMode == FilePanelMode.List) {
                StitchSymbol(StitchSymbols.FolderOpen, "文件", size = 25.dp, color = StitchColors.HomeGreenDark)
            } else {
                ActionIconButton(R.drawable.ic_previous, "返回文件列表", true) {
                    pendingDeleteIndex = null
                    filePanelMode = FilePanelMode.List
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(text = panelTitle, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            if (filePanelMode == FilePanelMode.List) {
                ActionIconButton(R.drawable.ic_add, "新建文件", true) {
                    pendingDeleteIndex = null
                    filePanelMode = FilePanelMode.NewType
                }
            }
            ActionIconButton(R.drawable.ic_close, "关闭", true, onClose)
        }

        when (filePanelMode) {
            FilePanelMode.List -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileDialogOption(
                        iconRes = R.drawable.ic_model_cube,
                        title = "AI Key 配置",
                        subtitle = if (!ReleasePolicy.CLOUD_AI_ENABLED) {
                            ReleasePolicy.COMING_SOON
                        } else if (rodinApiKey.isBlank()) {
                            "Rodin Key 未配置，生成前会弹窗要求配置"
                        } else {
                            "Rodin Key 已配置；图生图 / 豆包 Key 可在这里维护"
                        },
                        onClick = {
                            if (ReleasePolicy.CLOUD_AI_ENABLED) {
                                pendingDeleteIndex = null
                                filePanelMode = FilePanelMode.KeyConfig
                            } else {
                                onUnavailableFeature()
                            }
                        },
                    )
                    shows.forEach { show ->
                        FileRow(
                            show = show,
                            canDelete = shows.size > 1,
                            onSwitchShow = onSwitchShow,
                            onRenameShow = onRenameShow,
                            onDeleteShow = {
                                filePanelMode = FilePanelMode.DeleteConfirm
                                pendingDeleteIndex = it
                            },
                        )
                    }
                }
            }

            FilePanelMode.NewType -> {
                FileNewTypeMenu(
                    onNewShow = {
                        filePanelMode = FilePanelMode.List
                        onNewShow()
                    },
                    onNewWhiteboard = {
                        filePanelMode = FilePanelMode.List
                        onNewWhiteboard()
                    },
                    onCancel = { filePanelMode = FilePanelMode.List },
                )
            }

            FilePanelMode.DeleteConfirm -> {
                pendingDeleteShow?.let { show ->
                    FileDeleteConfirmOverlay(
                        showName = show.name,
                        onConfirm = {
                            onDeleteShow(show.index)
                            pendingDeleteIndex = null
                            filePanelMode = FilePanelMode.List
                        },
                        onCancel = {
                            pendingDeleteIndex = null
                            filePanelMode = FilePanelMode.List
                        },
                    )
                }
            }

            FilePanelMode.KeyConfig -> {
                AigcKeyConfigPanel(
                    rodinApiKey = rodinApiKey,
                    imageToImageApiKey = imageToImageApiKey,
                    doubaoApiKey = doubaoApiKey,
                    modifier = Modifier
                        .fillMaxWidth(),
                    embedded = true,
                    onSave = { rodin, imageToImage, doubao ->
                        onSaveAigcKeys(rodin, imageToImage, doubao)
                        filePanelMode = FilePanelMode.List
                    },
                    onCancel = { filePanelMode = FilePanelMode.List },
                )
            }
        }
    }
}

private enum class FilePanelMode {
    List,
    NewType,
    DeleteConfirm,
    KeyConfig,
}

@Composable
private fun FileNewTypeMenu(
    onNewShow: () -> Unit,
    onNewWhiteboard: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "选择一种文件类型开始创作。",
            fontSize = 15.sp,
            color = StudioColors.TextSecondary,
        )
        FileDialogOption(R.drawable.ic_add, "新建演示", "多页演示与 3D 内容编排", onNewShow)
        FileDialogOption(R.drawable.ic_text, "新建白板", "单空间白板与快速记录", onNewWhiteboard)
        FileDialogButton(
            label = "取消",
            color = StudioColors.TextPrimary,
            background = StudioColors.SurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel,
        )
    }
}

@Composable
private fun FileDeleteConfirmOverlay(
    showName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(text = "确认删除「${showName.take(22)}」？", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = StudioColors.TextPrimary)
        Text(
            text = "文件删除后无法恢复。",
            fontSize = 15.sp,
            color = StudioColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FileDialogButton(
                label = "取消",
                color = StudioColors.TextPrimary,
                background = StudioColors.SurfaceMuted,
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            FileDialogButton(
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
private fun FileDialogOption(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .stitchClickable(onClick = onClick)
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stitchSymbolForDrawable(iconRes)?.let { StitchSymbol(it, title, size = 22.dp, color = StitchColors.HomeGreenDark) }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StitchColors.PanelText)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 13.sp, color = StudioColors.TextSecondary)
        }
    }
}

@Composable
private fun FileDialogButton(
    label: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .stitchClickable(onClick = onClick)
            .background(background)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
    }
}

@Composable
private fun FileRow(
    show: ShowSummary,
    canDelete: Boolean,
    onSwitchShow: (Int) -> Unit,
    onRenameShow: (Int, String) -> Unit,
    onDeleteShow: (Int) -> Unit,
) {
    var editing by remember(show.index, show.name) { mutableStateOf(false) }
    var name by remember(show.index, show.name) { mutableStateOf(show.name) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun commitName() {
        val committedName = name.trim().ifBlank { show.name }
        name = committedName
        editing = false
        keyboardController?.hide()
        focusManager.clearFocus()
        onRenameShow(show.index, committedName)
    }

    fun cancelEditing() {
        name = show.name
        editing = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (show.selected) StitchColors.HomeGreen.copy(alpha = 0.28f) else StitchColors.PanelSurfaceMuted)
            .border(
                if (show.selected) 2.dp else 1.dp,
                if (show.selected) StitchColors.HomeGreen else StitchColors.PanelOutline,
                RoundedCornerShape(14.dp),
            )
            .then(if (editing) Modifier else Modifier.stitchClickable { onSwitchShow(show.index) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioColors.SurfaceMuted)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 16.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitName() }),
                    )
                } else {
                    Text(text = show.name, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = StitchColors.PanelText)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = show.summaryText(separator = " / "),
                    fontSize = 13.sp,
                    color = StudioColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (editing) {
                ActionIconButton(R.drawable.ic_close, "取消重命名", true) { cancelEditing() }
                Spacer(Modifier.width(6.dp))
                ActionIconButton(R.drawable.ic_add, "保存名称", true) { commitName() }
            } else {
                ActionIconButton(R.drawable.ic_text, "重命名", true) { editing = true }
                Spacer(Modifier.width(6.dp))
                ActionIconButton(R.drawable.ic_delete, "删除文件", canDelete) { onDeleteShow(show.index) }
            }
        }
    }
}

private fun DocumentType.displayName(): String =
    when (this) {
        DocumentType.PRESENTATION -> "演示"
        DocumentType.WHITEBOARD -> "白板"
    }

fun ShowSummary.summaryText(separator: String): String =
    when (documentType) {
        DocumentType.PRESENTATION -> "${documentType.displayName()} · $pageCount 页$separator$objectCount 个对象"
        DocumentType.WHITEBOARD -> "${documentType.displayName()} · 单空间$separator$objectCount 个对象"
    }

private fun AnimationEasing.displayName(): String =
    when (this) {
        AnimationEasing.LINEAR -> "线性"
        AnimationEasing.EASE_IN -> "入"
        AnimationEasing.EASE_OUT -> "出"
        AnimationEasing.EASE_IN_OUT -> "入出"
    }

private fun AnimationLoopMode.displayName(): String =
    when (this) {
        AnimationLoopMode.NONE -> "不循环"
        AnimationLoopMode.PING_PONG -> "往复"
        AnimationLoopMode.RESTART -> "单向"
    }

private fun Float.formatOne(): String =
    if (this % 1f == 0f) this.toInt().toString() else String.format(java.util.Locale.US, "%.1f", this)

private fun Float.formatTwo(): String =
    String.format(java.util.Locale.US, "%.2f", this)

@Composable
private fun PanelGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StitchColors.PanelSurfaceMuted.copy(alpha = 0.76f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

@Composable
private fun OptionTray(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StitchColors.PanelSurface)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(14.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun TextOptionChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .stitchClickable(onClick = onClick)
            .background(if (active) StitchColors.HomeGreen.copy(alpha = 0.3f) else StitchColors.PanelSurfaceMuted)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StitchColors.PanelText)
    }
}

@Composable
private fun CompactActionChip(label: String, iconRes: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(13.dp))
            .stitchClickable(onClick = onClick)
            .background(StitchColors.PanelSurface)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(13.dp))
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stitchSymbolForDrawable(iconRes)?.let { StitchSymbol(it, label, size = 16.dp, color = StitchColors.PanelText) }
        Text(text = label, fontFamily = StitchNunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StitchColors.PanelText)
    }
}

@Composable
private fun ParameterSelectChip(
    label: String,
    value: String,
    iconRes: Int,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (expanded) StudioColors.SurfaceRaised else StudioColors.Surface)
            .stitchClickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.pico.spatial.ui.design.Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.width(12.dp).height(12.dp),
                tint = StudioColors.TextSecondary,
            )
            Text(text = label, fontSize = 11.sp, color = StudioColors.TextSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, fontSize = 13.sp, color = StudioColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            com.pico.spatial.ui.design.Icon(
                painter = androidx.compose.ui.res.painterResource(if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_chevron_down),
                contentDescription = "展开",
                modifier = Modifier.width(12.dp).height(12.dp),
                tint = StudioColors.TextSecondary,
            )
        }
    }
}

private fun AnimationPicker?.toggle(target: AnimationPicker): AnimationPicker? =
    if (this == target) null else target

private enum class AnimationPicker {
    DURATION,
    EASING,
    LOOP,
}

private val ANIMATION_DURATION_OPTIONS = listOf(1f, 2f, 4f, 8f, 12f)

@Composable
fun TextToImagePanel(
    baseText: String,
    extraPrompt: String,
    promptTemplate: AigcPromptTemplate?,
    busy: Boolean,
    message: String,
    onExtraPromptChange: (String) -> Unit,
    onPromptTemplateChange: (AigcPromptTemplate?) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.AutoAwesome, "AI", size = 21.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(7.dp))
            Text(text = "文本生成图片", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            ActionIconButton(R.drawable.ic_close, "取消生成图片", !busy, onCancel)
        }
        Text(
            text = "基础文本：${baseText.ifBlank { "空文本" }.take(36)}",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        AigcPromptTemplateSelector(
            useCase = AigcPromptUseCase.TEXT_TO_IMAGE,
            selectedTemplate = promptTemplate,
            enabled = !busy,
            onTemplateChange = onPromptTemplateChange,
        )
        StudioInputField(
            value = extraPrompt,
            placeholder = if (promptTemplate != null) {
                "模板模式：只填写主体、颜色或额外细节"
            } else {
                "可选：补充风格、构图、材质，例如：电影感，纯净背景"
            },
            enabled = !busy,
            height = 70.dp,
            textSize = 13,
            onChange = onExtraPromptChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextActionButton(
                label = "取消",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Secondary,
                onClick = onCancel,
            )
            TextActionButton(
                label = if (busy) "生成中..." else "生成并替换",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Primary,
                onClick = onGenerate,
            )
        }
    }
}

@Composable
fun StrokeToImagePanel(
    strokeName: String,
    extraPrompt: String,
    promptTemplate: AigcPromptTemplate?,
    busy: Boolean,
    message: String,
    onExtraPromptChange: (String) -> Unit,
    onPromptTemplateChange: (AigcPromptTemplate?) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.Gesture, "笔画 AI", size = 21.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(7.dp))
            Text(text = "笔画 AI 成图", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            ActionIconButton(R.drawable.ic_close, "取消 AI 成图", !busy, onCancel)
        }
        Text(
            text = "参考：${strokeName.ifBlank { "画笔" }.take(18)} 当前观察方向的主视图会直接发送给 Seedream。",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        AigcPromptTemplateSelector(
            useCase = AigcPromptUseCase.STROKE_TO_IMAGE,
            selectedTemplate = promptTemplate,
            enabled = !busy,
            onTemplateChange = onPromptTemplateChange,
        )
        StudioInputField(
            value = extraPrompt,
            placeholder = if (promptTemplate != null) {
                "模板模式：只填写主体、颜色或额外细节"
            } else {
                "可选：补充主体或风格，例如：螃蟹，卡通风格，纯净背景"
            },
            enabled = !busy,
            height = 70.dp,
            textSize = 13,
            onChange = onExtraPromptChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextActionButton(
                label = "取消",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Secondary,
                onClick = onCancel,
            )
            TextActionButton(
                label = if (busy) "生成中..." else "发送 Seedream",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Primary,
                onClick = onGenerate,
            )
        }
    }
}

@Composable
fun ImageToImagePanel(
    imageName: String,
    prompt: String,
    promptTemplate: AigcPromptTemplate?,
    busy: Boolean,
    message: String,
    onPromptChange: (String) -> Unit,
    onPromptTemplateChange: (AigcPromptTemplate?) -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.Image, "图片生成", size = 21.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(7.dp))
            Text(text = "图片生成新版本", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            ActionIconButton(R.drawable.ic_close, "取消图片生成", !busy, onCancel)
        }
        Text(
            text = "参考图：${imageName.ifBlank { "图片" }.take(36)}",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        AigcPromptTemplateSelector(
            useCase = AigcPromptUseCase.IMAGE_TO_IMAGE,
            selectedTemplate = promptTemplate,
            enabled = !busy,
            onTemplateChange = onPromptTemplateChange,
        )
        StudioInputField(
            value = prompt,
            placeholder = if (promptTemplate != null) {
                "模板模式：只填写想保留/强化的主体细节"
            } else {
                "描述要生成的新版本，例如：换成像素风，背景更干净"
            },
            enabled = !busy,
            height = 70.dp,
            textSize = 13,
            onChange = onPromptChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextActionButton(
                label = "取消",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Secondary,
                onClick = onCancel,
            )
            TextActionButton(
                label = if (busy) "生成中..." else "生成版本",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Primary,
                onClick = onGenerate,
            )
        }
    }
}

@Composable
fun ImageSubjectPreprocessChoicePanel(
    extraPrompt: String,
    busy: Boolean,
    message: String,
    onExtraPromptChange: (String) -> Unit,
    onChoose: (AigcImagePreprocessMode) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(StitchSymbols.Person, "主体", size = 21.dp, color = StitchColors.HomeGreenDark)
            Spacer(Modifier.width(7.dp))
            Text(text = "保留主体", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = StitchColors.PanelText)
            Spacer(Modifier.weight(1f))
            ActionIconButton(R.drawable.ic_close, "取消保留主体", !busy, onCancel)
        }
        Text(
            text = "选择本地 U²-Net 或 Seedream AI 清理。AI 清理会使用固定 3D 前处理 prompt，可选追加要求。",
            fontSize = 12.sp,
            color = StudioColors.TextSecondary,
        )
        StudioInputField(
            value = extraPrompt,
            placeholder = "可选：额外要求，例如：只保留主体，白色背景",
            enabled = !busy,
            height = 58.dp,
            textSize = 13,
            onChange = onExtraPromptChange,
        )
        Text(text = message, fontSize = 11.sp, color = StudioColors.TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextActionButton(
                label = "本地抠图",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Secondary,
                onClick = { onChoose(AigcImagePreprocessMode.LOCAL_SUBJECT) },
            )
            TextActionButton(
                label = if (busy) "处理中..." else "AI 清理",
                enabled = !busy,
                modifier = Modifier.weight(1f),
                style = StudioButtonStyle.Primary,
                onClick = { onChoose(AigcImagePreprocessMode.SEEDREAM_CLEAN) },
            )
        }
    }
}

@Composable
private fun TextActionButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    style: StudioButtonStyle,
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

@Composable
fun TextEditorPanel(
    currentText: String,
    onChange: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentText) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(currentText) {
        value = currentText
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StitchColors.PanelSurfaceMuted)
                .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onChange(it.ifBlank { "新增文本" })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 18.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    },
                ),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(text = "新增文本", fontSize = 18.sp, color = StudioColors.TextTertiary)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
fun CommentEditorPanel(
    currentText: String,
    focusKey: Any? = currentText,
    onChange: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var value by remember { mutableStateOf(currentText) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(currentText) {
        value = currentText
    }
    LaunchedEffect(focusKey) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Column(
        modifier = Modifier
            .width(360.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(StitchColors.PanelSurface.copy(alpha = 0.97f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(it)
            },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(StitchColors.PanelSurfaceMuted)
                .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
                .focusRequester(focusRequester)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 18.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                },
            ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = "待评论", fontSize = 18.sp, color = StudioColors.TextTertiary)
                }
                inner()
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommentEditorTextButton("关闭", StudioColors.SurfaceMuted, StudioColors.TextPrimary, onClose)
            CommentEditorTextButton("删除", StudioColors.DangerSoft, StudioColors.DangerStrong, onDelete)
        }
    }
}

@Composable
private fun CommentEditorTextButton(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .stitchClickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 14.sp, color = foreground)
    }
}

@Composable
private fun InlineEditorPanel(
    title: String?,
    initialValue: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onClose: () -> Unit,
    extraContent: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = false,
    autoFocusKey: Any? = Unit,
    showCloseButton: Boolean = true,
) {
    var value by remember { mutableStateOf(initialValue) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(initialValue) {
        value = initialValue
    }
    LaunchedEffect(autoFocus, autoFocusKey) {
        if (!autoFocus) return@LaunchedEffect
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    val compact = title == null || title == "Text"
    Column(
        modifier = Modifier
            .width(if (compact) 360.dp else 420.dp)
            .clip(RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
            .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(if (compact) 18.dp else 22.dp))
            .padding(if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
    ) {
        if (title != null || showCloseButton) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(text = title, fontSize = if (compact) 15.sp else 20.sp, color = StudioColors.TextPrimary)
                    Spacer(Modifier.width(12.dp))
                }
                if (showCloseButton) {
                    ActionIconButton(R.drawable.ic_close, "关闭", true, onClose)
                }
            }
        }
        extraContent?.invoke()
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                onChange(it.ifBlank { placeholder })
            },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .backgroundMaterial(true, Material.Thin)
                .background(StudioColors.SurfaceMuted.copy(alpha = 0.9f))
                .focusRequester(focusRequester)
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
            textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 18.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
            ),
            decorationBox = { inner ->
                if (value.isBlank()) {
                    Text(text = placeholder, fontSize = 18.sp, color = StudioColors.TextTertiary)
                }
                inner()
            },
        )
    }
}

private const val ROTATION_BUTTON_RADIANS_PER_SECOND = (PI / 2.4).toFloat()

@Composable
fun PresentPanel(
    title: String,
    currentPageIndex: Int,
    pageCount: Int,
    annotationTool: PresentAnnotationTool,
    selectedColor: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleComment: () -> Unit,
    onToggleBrush: () -> Unit,
    onSelectBrush: () -> Unit,
    onToggleEraser: () -> Unit,
    onColor: (Long) -> Unit,
    onExit: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .shadow(8.dp, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .backgroundMaterial(true, Material.Thin)
                .background(StitchColors.PresentSurface)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                fontFamily = StitchNunitoSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = StitchColors.PresentText,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(48.dp))
                .clip(RoundedCornerShape(48.dp))
                .backgroundMaterial(true, Material.Regular)
                .background(StitchColors.PresentSurface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StitchSoftIconButton(
                symbol = StitchSymbols.ArrowBackIosNew,
                contentDescription = "上一页",
                enabled = currentPageIndex > 0,
                onClick = onPrevious,
            )
            Box(
                modifier = Modifier.width(66.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${currentPageIndex + 1}/$pageCount",
                    fontFamily = StitchQuicksand,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    color = StitchColors.PresentText,
                )
            }
            StitchSoftIconButton(
                symbol = StitchSymbols.ArrowForwardIos,
                contentDescription = "下一页",
                enabled = currentPageIndex < pageCount - 1,
                onClick = onNext,
            )
            PresentStitchDivider()
            StitchSoftIconButton(
                symbol = StitchSymbols.ChatBubble,
                contentDescription = if (annotationTool == PresentAnnotationTool.COMMENT) "关闭评论" else "评论",
                active = annotationTool == PresentAnnotationTool.COMMENT,
                onClick = onToggleComment,
            )
            StitchSoftIconButton(
                symbol = StitchSymbols.Brush,
                contentDescription = if (annotationTool == PresentAnnotationTool.STROKE || annotationTool == PresentAnnotationTool.ERASER) "关闭画线" else "画线",
                active = annotationTool == PresentAnnotationTool.STROKE || annotationTool == PresentAnnotationTool.ERASER,
                onClick = onToggleBrush,
            )
            AnimatedVisibility(
                visible = annotationTool == PresentAnnotationTool.STROKE || annotationTool == PresentAnnotationTool.ERASER,
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.88f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ObjectColors.STROKE_PALETTE.forEach { color ->
                        ColorSwatchButton(
                            color = Color(color),
                            contentDescription = "演示画线颜色",
                            active = selectedColor == color,
                        ) {
                            onColor(color)
                        }
                    }
                    StitchSoftIconButton(
                        symbol = StitchSymbols.Brush,
                        contentDescription = "画笔",
                        active = annotationTool == PresentAnnotationTool.STROKE,
                        onClick = onSelectBrush,
                    )
                    StitchSoftIconButton(
                        symbol = StitchSymbols.DeleteOutline,
                        contentDescription = "橡皮",
                        active = annotationTool == PresentAnnotationTool.ERASER,
                        onClick = onToggleEraser,
                    )
                }
            }
            PresentStitchDivider()
            StitchSoftIconButton(
                symbol = StitchSymbols.Logout,
                contentDescription = "退出演示",
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun PresentStitchDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(StitchColors.PresentDivider.copy(alpha = 0.3f)),
    )
}

@Composable
fun WorkspaceDragHandlePanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(18.dp))
                .backgroundMaterial(true, Material.Thin)
                .background(StitchColors.PanelSurface.copy(alpha = 0.96f))
                .border(2.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(7.dp)
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(StitchColors.HomeBrown.copy(alpha = 0.82f)),
            )
        }
    }
}

@Composable
private fun ColorSwatchButton(
    color: Color,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(14.dp))
            .stitchClickable(onClick = onClick)
            .background(if (active) StudioColors.PrimarySoft else StudioColors.SurfaceMuted)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color),
        )
    }
}

@Composable
private fun PageButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .width(if (label.length <= 2) 66.dp else 84.dp)
            .height(54.dp)
            .offset(y = if (pressed && enabled) 2.dp else 0.dp)
            .shadow(if (selected) 5.dp else 2.dp, shape)
            .clip(shape)
            .alpha(if (enabled) 1f else 0.35f)
            .stitchClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .background(
                when {
                    selected && pressed -> StitchColors.HomeGreenLight
                    selected -> StitchColors.HomeGreen
                    pressed -> StudioColors.PrimarySoft
                    else -> StitchColors.PanelSurfaceMuted
                }
            )
            .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.72f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = StitchQuicksand,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (selected) Color.White else StitchColors.PanelText,
        )
    }
}

@Composable
private fun PageStripActionButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    large: Boolean,
    onClick: () -> Unit,
    symbolOverride: String? = null,
) {
    val width = if (large) 66.dp else 38.dp
    val height = if (large) 54.dp else 36.dp
    val shape = RoundedCornerShape(if (large) 19.dp else 13.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        !enabled -> StitchColors.PanelSurfaceMuted
        pressed && large -> StitchColors.HomeGreenLight
        pressed -> StudioColors.PrimarySoft
        large -> StitchColors.HomeGreen
        else -> StitchColors.PanelSurface
    }
    val iconColor = when {
        !enabled -> StitchColors.PanelTextMuted
        large -> Color.White
        else -> StitchColors.PanelText
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .offset(y = if (pressed && enabled) 2.dp else 0.dp)
            .shadow(3.dp, shape)
            .clip(shape)
            .stitchClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val symbol = symbolOverride ?: stitchSymbolForDrawable(iconRes)
        if (symbol != null) {
            StitchSymbol(
                symbol,
                contentDescription,
                size = if (large) 25.dp else 19.dp,
                color = iconColor,
            )
        }
    }
}
