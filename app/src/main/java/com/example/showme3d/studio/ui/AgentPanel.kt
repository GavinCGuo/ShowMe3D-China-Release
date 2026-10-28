package com.example.showme3d.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import com.example.showme3d.R
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

enum class AgentMessageTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

enum class AgentMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

enum class AgentTaskStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ROLLED_BACK,
}

data class AgentUiMessage(
    val id: String,
    val role: AgentMessageRole,
    val text: String,
    val tone: AgentMessageTone = AgentMessageTone.NEUTRAL,
)

data class AgentTaskStep(
    val id: String,
    val index: Int,
    val total: Int,
    val title: String,
    val status: AgentTaskStepStatus,
    val detail: String = "",
)

data class AgentClarification(
    val question: String,
    val options: List<String> = emptyList(),
)

data class AgentSuggestion(
    val title: String,
    val message: String,
    val prompt: String,
    val actionLabel: String = "采纳",
)

data class AgentExample(
    val label: String,
    val prompt: String,
)

data class AgentExampleGroup(
    val title: String,
    val examples: List<AgentExample>,
)

private enum class AgentSidePanelMode {
    EXAMPLES,
    SUGGESTIONS,
}

@Composable
fun AgentPanel(
    prompt: String,
    busy: Boolean,
    keyConfigured: Boolean,
    contextHint: String,
    messages: List<AgentUiMessage>,
    currentStatus: String,
    currentStatusTone: AgentMessageTone,
    taskSteps: List<AgentTaskStep>,
    clarification: AgentClarification? = null,
    suggestions: List<AgentSuggestion> = emptyList(),
    examplesExpanded: Boolean,
    exampleGroups: List<AgentExampleGroup> = DefaultAgentExampleGroups,
    onPromptChange: (String) -> Unit,
    onRun: (String) -> Unit,
    onOpenKeyConfig: () -> Unit,
    onExamplesExpandedChange: (Boolean) -> Unit,
    onClarificationOption: (String) -> Unit = {},
    onClose: () -> Unit,
    toolbarReplacement: Boolean = false,
    visible: Boolean = true,
    panelAlpha: Float = if (visible) 1f else 0f,
    interactionsEnabled: Boolean = panelAlpha > 0.96f,
) {
    var sidePanelMode by remember { mutableStateOf(AgentSidePanelMode.EXAMPLES) }
    val suggestionSignature = suggestions.joinToString("|") { "${it.title}:${it.prompt}:${it.actionLabel}" }
    LaunchedEffect(suggestionSignature) {
        sidePanelMode = if (suggestionSignature.isBlank()) {
            AgentSidePanelMode.EXAMPLES
        } else {
            AgentSidePanelMode.SUGGESTIONS
        }
    }
    var promptDraft by remember { mutableStateOf(prompt) }
    LaunchedEffect(prompt) {
        if (prompt != promptDraft) {
            promptDraft = prompt
        }
    }
    fun updatePrompt(next: String) {
        promptDraft = next
        onPromptChange(next)
    }
    fun submitPrompt() {
        onRun(promptDraft)
    }

    Column(
        modifier = Modifier
            .alpha(panelAlpha.coerceIn(0f, 1f))
            .width(if (toolbarReplacement) 1000.dp else 460.dp)
            .height(if (toolbarReplacement) 540.dp else 590.dp)
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(StitchColors.PanelSurface.copy(alpha = 0.98f))
            .border(3.dp, StitchColors.PanelOutline, RoundedCornerShape(24.dp))
            .padding(if (toolbarReplacement) 14.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (toolbarReplacement) 8.dp else 11.dp),
    ) {
        if (toolbarReplacement) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ActionIconButton(R.drawable.ic_close, "关闭 AI 助手", interactionsEnabled && !busy, onClose)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (taskSteps.isNotEmpty()) {
                    AgentTaskProgressSidePanel(
                        steps = taskSteps,
                        modifier = Modifier
                            .width(196.dp)
                            .fillMaxHeight(),
                    )
                }
                AgentMainColumn(
                    contextHint = contextHint,
                    messages = messages,
                    currentStatus = currentStatus,
                    currentStatusTone = currentStatusTone,
                    clarification = clarification,
                    busy = busy,
                    prompt = promptDraft,
                    keyConfigured = keyConfigured,
                    onPromptChange = ::updatePrompt,
                    onRun = ::submitPrompt,
                    onOpenKeyConfig = onOpenKeyConfig,
                    onClarificationOption = onClarificationOption,
                    interactionsEnabled = interactionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                AgentSidePanel(
                    enabled = !busy,
                    interactionsEnabled = interactionsEnabled,
                    groups = exampleGroups,
                    suggestions = suggestions,
                    mode = sidePanelMode,
                    onModeChange = { sidePanelMode = it },
                    onPromptChange = ::updatePrompt,
                    modifier = Modifier
                        .width(252.dp)
                        .fillMaxHeight(),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!keyConfigured) {
                    AgentKeyBadge(keyConfigured)
                    Spacer(Modifier.width(8.dp))
                }
                ActionIconButton(R.drawable.ic_close, "关闭 AI 助手", interactionsEnabled && !busy, onClose)
            }
            if (contextHint.isNotBlank()) {
                AgentContextHint(contextHint)
            }

            AgentConversationArea(
                messages = messages,
                currentStatus = currentStatus,
                currentStatusTone = currentStatusTone,
                clarification = clarification,
                busy = busy,
                interactionsEnabled = interactionsEnabled,
                onClarificationOption = onClarificationOption,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            AgentExamplesSection(
                expanded = examplesExpanded,
                enabled = !busy,
                groups = exampleGroups,
                onExpandedChange = onExamplesExpandedChange,
                onPromptChange = ::updatePrompt,
            )

            AgentComposerRow(
                busy = busy,
                keyConfigured = keyConfigured,
                prompt = promptDraft,
                onPromptChange = ::updatePrompt,
                onRun = ::submitPrompt,
                onOpenKeyConfig = onOpenKeyConfig,
                clarification = clarification,
                interactionsEnabled = interactionsEnabled,
            )
            AgentCloseContextNotice()
        }
    }
}

@Composable
private fun AgentMainColumn(
    contextHint: String,
    messages: List<AgentUiMessage>,
    currentStatus: String,
    currentStatusTone: AgentMessageTone,
    clarification: AgentClarification?,
    busy: Boolean,
    prompt: String,
    keyConfigured: Boolean,
    onPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    onOpenKeyConfig: () -> Unit,
    onClarificationOption: (String) -> Unit,
    interactionsEnabled: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (contextHint.isNotBlank()) {
            AgentContextHint(contextHint)
        }
        AgentConversationArea(
            messages = messages,
            currentStatus = currentStatus,
            currentStatusTone = currentStatusTone,
            clarification = clarification,
            busy = busy,
            interactionsEnabled = interactionsEnabled,
            onClarificationOption = onClarificationOption,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        AgentComposerRow(
            busy = busy,
            keyConfigured = keyConfigured,
            prompt = prompt,
            onPromptChange = onPromptChange,
            onRun = onRun,
            onOpenKeyConfig = onOpenKeyConfig,
            clarification = clarification,
            interactionsEnabled = interactionsEnabled,
        )
        AgentCloseContextNotice()
    }
}

@Composable
private fun AgentComposerRow(
    busy: Boolean,
    keyConfigured: Boolean,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    onOpenKeyConfig: () -> Unit,
    clarification: AgentClarification?,
    interactionsEnabled: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputEnabled = interactionsEnabled && !busy
    val sendVisuallyEnabled = !busy && if (keyConfigured) prompt.trim().isNotBlank() else true
    val sendClickable = interactionsEnabled && sendVisuallyEnabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(13.dp))
                .background(StitchColors.PanelSurface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (prompt.isEmpty()) {
                Text(
                    text = if (clarification == null) {
                        "输入指令，例如：加一个立方体 / 把选中的物体变红"
                    } else {
                        "直接回答上一条确认问题"
                    },
                    fontSize = 13.sp,
                    color = StudioColors.TextTertiary,
                )
            }
            BasicTextField(
                value = prompt,
                onValueChange = { if (inputEnabled) onPromptChange(it) },
                textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 15.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (inputEnabled && prompt.trim().isNotBlank()) onRun()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AgentTextButton(
            label = when {
                busy -> "执行中..."
                keyConfigured -> "发送"
                else -> "配置 Key"
            },
            enabled = sendVisuallyEnabled,
            clickableEnabled = sendClickable,
            modifier = Modifier.width(96.dp),
            color = StudioColors.SurfaceRaised,
            background = StudioColors.Primary,
            onClick = if (keyConfigured) onRun else onOpenKeyConfig,
            height = 48.dp,
        )
    }
}

@Composable
private fun AgentCloseContextNotice() {
    Text(
        text = "关闭该界面后，将会清空上下文",
        fontSize = 11.sp,
        color = StudioColors.TextTertiary,
    )
}

@Composable
private fun AgentConversationArea(
    messages: List<AgentUiMessage>,
    currentStatus: String,
    currentStatusTone: AgentMessageTone,
    clarification: AgentClarification?,
    busy: Boolean,
    interactionsEnabled: Boolean,
    onClarificationOption: (String) -> Unit,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    val lastMessageId = messages.lastOrNull()?.id
    LaunchedEffect(lastMessageId, messages.size, currentStatus, clarification?.question) {
        withFrameNanos { }
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(StitchColors.PanelSurfaceMuted.copy(alpha = 0.72f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .verticalScroll(scrollState)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (currentStatus.isNotBlank()) {
            AgentStatusCard(currentStatus, currentStatusTone)
        }
        messages.forEach { message ->
            AgentMessageBubble(message)
        }
        clarification?.let {
            AgentClarificationCard(
                clarification = it,
                enabled = interactionsEnabled && !busy,
                onOption = onClarificationOption,
            )
        }
    }
}

@Composable
private fun AgentStatusCard(text: String, tone: AgentMessageTone) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tone.softBackground())
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = tone.color())
    }
}

@Composable
private fun AgentStepList(steps: List<AgentTaskStep>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StitchColors.PanelSurface)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        steps.forEach { step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(step.status.color().copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${step.index}/${step.total}",
                        fontSize = 11.sp,
                        color = step.status.color(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = step.title, fontSize = 13.sp, color = StudioColors.TextPrimary)
                    if (step.detail.isNotBlank()) {
                        Text(text = step.detail, fontSize = 11.sp, color = StudioColors.TextSecondary)
                    }
                }
                Text(text = step.status.label(), fontSize = 11.sp, color = step.status.color())
            }
        }
    }
}

@Composable
private fun AgentMessageBubble(message: AgentUiMessage) {
    val isUser = message.role == AgentMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .width(if (isUser) 330.dp else 430.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isUser -> StudioColors.PrimarySoft
                        message.role == AgentMessageRole.SYSTEM -> StudioColors.SurfaceMuted
                        else -> StudioColors.SurfaceRaised.copy(alpha = 0.9f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when (message.role) {
                    AgentMessageRole.USER -> "你"
                    AgentMessageRole.ASSISTANT -> "助手"
                    AgentMessageRole.SYSTEM -> "系统"
                },
                fontSize = 10.sp,
                color = StudioColors.TextSecondary,
            )
            Text(text = message.text, fontSize = 13.sp, color = message.tone.color())
        }
    }
}

@Composable
private fun AgentClarificationCard(
    clarification: AgentClarification,
    enabled: Boolean,
    onOption: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF7ED))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "需要确认", fontSize = 12.sp, color = StudioColors.Warning)
        Text(text = clarification.question, fontSize = 13.sp, color = Color(0xFF7C2D12))
        if (clarification.options.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                clarification.options.take(3).forEach { option ->
                    AgentExampleChip(option, enabled) { onOption(option) }
                }
            }
        }
    }
}

@Composable
private fun AgentSuggestionCard(
    suggestions: List<AgentSuggestion>,
    enabled: Boolean,
    onAccept: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE7F8EE))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "接下来可以", fontSize = 13.sp, color = StudioColors.Success)
        Text(text = "如果有用，点一下继续执行；不需要就直接继续输入。", fontSize = 12.sp, color = StudioColors.Success)
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(StudioColors.SurfaceRaised.copy(alpha = 0.78f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = suggestion.title, fontSize = 12.sp, color = Color(0xFF14532D))
                }
                AgentExampleChip(suggestion.actionLabel, enabled) { onAccept(index) }
            }
        }
    }
}

@Composable
private fun AgentExamplesSection(
    expanded: Boolean,
    enabled: Boolean,
    groups: List<AgentExampleGroup>,
    onExpandedChange: (Boolean) -> Unit,
    onPromptChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AgentTextButton(
            label = if (expanded) "收起示例指令" else "展开示例指令",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            color = StudioColors.TextPrimary,
            background = StudioColors.SurfaceMuted,
            onClick = { onExpandedChange(!expanded) },
        )
        if (expanded) {
            Column(
                modifier = Modifier
                    .height(150.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                groups.forEach { group ->
                    Text(text = group.title, fontSize = 11.sp, color = StudioColors.TextSecondary)
                    AgentChipFlow(
                        examples = group.examples,
                        enabled = enabled,
                        onPromptChange = onPromptChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentTaskProgressSidePanel(
    steps: List<AgentTaskStep>,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(StitchColors.PanelSurfaceMuted.copy(alpha = 0.72f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "任务进度", fontFamily = StitchNunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = StitchColors.PanelText)
        Text(text = "执行步骤会显示在这里。", fontSize = 11.sp, color = StudioColors.TextSecondary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AgentStepList(steps)
        }
    }
}

@Composable
private fun AgentSidePanel(
    enabled: Boolean,
    interactionsEnabled: Boolean,
    groups: List<AgentExampleGroup>,
    suggestions: List<AgentSuggestion>,
    mode: AgentSidePanelMode,
    onModeChange: (AgentSidePanelMode) -> Unit,
    onPromptChange: (String) -> Unit,
    modifier: Modifier,
) {
    val hasSuggestions = suggestions.isNotEmpty()
    val actualMode = if (hasSuggestions) mode else AgentSidePanelMode.EXAMPLES
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(StitchColors.PanelSurfaceMuted.copy(alpha = 0.72f))
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (actualMode == AgentSidePanelMode.SUGGESTIONS) "推荐操作" else "示例指令",
                fontFamily = StitchNunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = StitchColors.PanelText,
            )
            Spacer(Modifier.weight(1f))
            if (hasSuggestions) {
                AgentMiniToggleButton(
                    label = if (actualMode == AgentSidePanelMode.SUGGESTIONS) "示例" else "推荐",
                    enabled = true,
                    clickableEnabled = interactionsEnabled,
                    onClick = {
                        onModeChange(
                            if (actualMode == AgentSidePanelMode.SUGGESTIONS) {
                                AgentSidePanelMode.EXAMPLES
                            } else {
                                AgentSidePanelMode.SUGGESTIONS
                            }
                        )
                    },
                )
            }
        }
        Text(
            text = if (actualMode == AgentSidePanelMode.SUGGESTIONS) {
                "点选后填入输入框，不会直接执行。"
            } else {
                "点选后填入输入框，不会直接执行。"
            },
            fontSize = 11.sp,
            color = StudioColors.TextSecondary,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AnimatedVisibility(
                visible = actualMode == AgentSidePanelMode.SUGGESTIONS,
                enter = fadeIn(animationSpec = tween(90)),
                exit = fadeOut(animationSpec = tween(70)),
            ) {
                AgentSuggestionList(
                    suggestions = suggestions.take(3),
                    enabled = enabled,
                    interactionsEnabled = interactionsEnabled,
                    onAccept = { suggestion -> onPromptChange(suggestion.prompt) },
                )
            }
            AnimatedVisibility(
                visible = actualMode == AgentSidePanelMode.EXAMPLES,
                enter = fadeIn(animationSpec = tween(90)),
                exit = fadeOut(animationSpec = tween(70)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    groups.forEach { group ->
                        Text(text = group.title, fontSize = 11.sp, color = StudioColors.TextSecondary)
                        AgentChipFlow(
                            examples = group.examples,
                            enabled = enabled,
                            interactionsEnabled = interactionsEnabled,
                            onPromptChange = onPromptChange,
                            itemsPerRow = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSuggestionList(
    suggestions: List<AgentSuggestion>,
    enabled: Boolean,
    interactionsEnabled: Boolean,
    onAccept: (AgentSuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(StitchColors.HomeGreen.copy(alpha = 0.22f))
                    .border(1.dp, StitchColors.HomeGreen.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(text = suggestion.title, fontSize = 13.sp, color = Color(0xFF14532D))
                AgentTextButton(
                    label = suggestion.actionLabel,
                    enabled = enabled,
                    clickableEnabled = interactionsEnabled && enabled,
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioColors.SurfaceRaised,
                    background = Color(0xFF16A34A),
                    onClick = { onAccept(suggestion) },
                )
            }
        }
    }
}

@Composable
private fun AgentMiniToggleButton(
    label: String,
    enabled: Boolean,
    clickableEnabled: Boolean = enabled,
    onClick: () -> Unit,
) {
    // Keep modifier chain structurally stable so Spatial hover material state never
    // gets torn down / rebuilt across enabled changes.
    val toggleBg = if (enabled) StudioColors.SurfaceMuted else StudioColors.SurfaceDisabled
    val toggleTextColor = if (enabled) StudioColors.TextPrimary else StudioColors.TextSecondary
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .spatialHoverEffect()
            .clickable { if (clickableEnabled) onClick() }
            .background(toggleBg)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 11.sp, color = toggleTextColor)
    }
}

@Composable
private fun AgentChipFlow(
    examples: List<AgentExample>,
    enabled: Boolean,
    interactionsEnabled: Boolean = enabled,
    onPromptChange: (String) -> Unit,
    itemsPerRow: Int = 3,
) {
    val rows = examples.chunked(itemsPerRow.coerceAtLeast(1))
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                row.forEach { example ->
                    AgentExampleChip(
                        label = example.label,
                        enabled = enabled,
                        clickableEnabled = interactionsEnabled && enabled,
                    ) {
                        onPromptChange(example.prompt)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentContextHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = StudioColors.TextPrimary)
    }
}

@Composable
private fun AgentInputBox(
    value: String,
    busy: Boolean,
    placeholder: String,
    onChange: (String) -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StitchColors.PanelSurfaceMuted)
            .border(1.dp, StitchColors.PanelOutline, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 13.sp,
                color = StudioColors.TextTertiary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { if (!busy) onChange(it) },
            textStyle = TextStyle(color = StudioColors.TextPrimary, fontSize = 15.sp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (!busy && value.trim().isNotBlank()) onRun()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AgentKeyBadge(keyConfigured: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (keyConfigured) Color(0xFFE7F8EE) else Color(0xFFFFF1F2))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (keyConfigured) "Key 已配置" else "Key 未配置",
            fontSize = 11.sp,
            color = if (keyConfigured) StudioColors.Success else StudioColors.Danger,
        )
    }
}

@Composable
private fun AgentExampleChip(
    label: String,
    enabled: Boolean,
    clickableEnabled: Boolean = enabled,
    onClick: () -> Unit,
) {
    // Keep modifier chain structurally stable so Spatial hover material state never
    // gets torn down / rebuilt across enabled changes.  Disabled state uses a
    // desaturated solid background rather than alpha to avoid blending with the
    // panel surface (which would read as "greyed out" in an unpredictable way).
    val chipBg = if (enabled) StudioColors.SurfaceMuted else StudioColors.SurfaceDisabled
    val chipTextColor = if (enabled) StudioColors.TextPrimary else StudioColors.TextSecondary
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .spatialHoverEffect()
            .clickable { if (clickableEnabled) onClick() }
            .background(chipBg)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 12.sp, color = chipTextColor)
    }
}

@Composable
private fun AgentTextButton(
    label: String,
    enabled: Boolean,
    clickableEnabled: Boolean = enabled,
    modifier: Modifier,
    color: Color,
    background: Color,
    onClick: () -> Unit,
    height: Dp = 40.dp,
) {
    // Keep modifier chain structurally stable so Spatial hover material state never
    // gets torn down / rebuilt across enabled changes (which can leave a stale
    // white-ish base color).  Disabled visual state uses solid colors instead of
    // alpha so there is no translucent blend with the panel background.
    // Disabled state uses a fully opaque desaturated color so it never blends
    // with whatever is behind the button (which previously caused a white-ish
    // base when alpha < 1 stacked on the white panel).
    val bgColor = if (enabled) background else StudioColors.SurfaceDisabled
    val textColor = if (enabled) color else StudioColors.TextSecondary
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .spatialHoverEffect()
            .clickable { if (clickableEnabled) onClick() }
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 15.sp, color = textColor)
    }
}

private fun AgentMessageTone.color(): Color =
    when (this) {
        AgentMessageTone.NEUTRAL -> StudioColors.TextSecondary
        AgentMessageTone.SUCCESS -> StudioColors.Success
        AgentMessageTone.WARNING -> StudioColors.Warning
        AgentMessageTone.ERROR -> StudioColors.Danger
    }

private fun AgentMessageTone.softBackground(): Color =
    when (this) {
        AgentMessageTone.NEUTRAL -> StudioColors.SurfaceMuted
        AgentMessageTone.SUCCESS -> Color(0xFFE7F8EE)
        AgentMessageTone.WARNING -> Color(0xFFFFF7ED)
        AgentMessageTone.ERROR -> Color(0xFFFFF1F2)
    }

private fun AgentTaskStepStatus.color(): Color =
    when (this) {
        AgentTaskStepStatus.PENDING -> StudioColors.TextSecondary
        AgentTaskStepStatus.RUNNING -> StudioColors.Primary
        AgentTaskStepStatus.SUCCEEDED -> StudioColors.Success
        AgentTaskStepStatus.FAILED -> StudioColors.Danger
        AgentTaskStepStatus.ROLLED_BACK -> StudioColors.Warning
    }

private fun AgentTaskStepStatus.label(): String =
    when (this) {
        AgentTaskStepStatus.PENDING -> "等待"
        AgentTaskStepStatus.RUNNING -> "执行中"
        AgentTaskStepStatus.SUCCEEDED -> "完成"
        AgentTaskStepStatus.FAILED -> "失败"
        AgentTaskStepStatus.ROLLED_BACK -> "已回退"
    }

val DefaultAgentExampleGroups: List<AgentExampleGroup> = listOf(
    AgentExampleGroup(
        title = "快速创作",
        examples = listOf(
            AgentExample("加方块", "加一个立方体"),
            AgentExample("加标题", "加一段文字：我的 3D 展示"),
            AgentExample("玩具车图", "生成一张红色玩具小车图片"),
            AgentExample("小螃蟹 3D", "生成一只红色小螃蟹 3D 模型"),
            AgentExample("第二页", "新增一页，再加一段文字：第二页"),
            AgentExample("看场景", "现在场景里有什么？"),
            AgentExample("清空本页", "清空当前页所有物体"),
        ),
    ),
    AgentExampleGroup(
        title = "选中后试试",
        examples = listOf(
            AgentExample("变红", "把选中的物体变成红色"),
            AgentExample("放大一点", "把选中的物体放大 20%"),
            AgentExample("右移", "把选中的物体向右移动 0.3 米"),
            AgentExample("右旋", "把选中的物体向右旋转 30 度"),
            AgentExample("复制", "复制选中的物体"),
            AgentExample("加动画", "让选中的物体左右往复动画 2 秒"),
            AgentExample("改文字", "把选中的文字改成：AI 标题"),
            AgentExample("黏土风", "把选中的图片改成黏土玩具风格"),
        ),
    ),
)
