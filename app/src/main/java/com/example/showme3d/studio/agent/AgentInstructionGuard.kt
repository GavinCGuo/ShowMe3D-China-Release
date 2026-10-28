package com.example.showme3d.studio.agent

object AgentInstructionGuard {
    private const val MAX_ACTION_CLAUSES = 19

    private val actionConnector = Regex(
        pattern = "(然后|接着|随后|之后|同时|顺便|并且|并把|并将|再把|再将|然后把|然后将|再|并)",
        option = RegexOption.IGNORE_CASE,
    )

    private val actionHint = Regex(
        pattern = "(加|添加|创建|插入|生成|改成|改为|更改|修改|变成|变为|变红|变蓝|变绿|变大|变小|设置|设为|移动|右移|左移|上移|下移|前移|后移|旋转|右旋|左旋|放大|缩小|复制|删除|删掉|移除|撤销|重做|选中|选择)",
        option = RegexOption.IGNORE_CASE,
    )

    private val generationRequestHint = Regex(
        pattern = "(生成|创建|制作|做).*(图|图片|照片|模型|3d|3D|三维)",
        option = RegexOption.IGNORE_CASE,
    )

    private val generationPlacementHint = Regex(
        pattern = "(加入|加到|添加到|放到|放进|放入|入).*(场景|页面|当前页)",
        option = RegexOption.IGNORE_CASE,
    )

    private val generationModifierHint = Regex(
        pattern = "(风格|材质|颜色|红色|蓝色|绿色|黄色|黑色|白色|卡通|写实|黏土|低多边形|高清|高质|贴图|面数|多边形|创意|保真|去背景|抠图|主体|干净)",
        option = RegexOption.IGNORE_CASE,
    )

    private val independentSceneTargetHint = Regex(
        pattern = "(选中|当前选中|已有|原来|原本|场景里|物体|对象|文字)",
        option = RegexOption.IGNORE_CASE,
    )

    private val independentActionAfterGenerationHint = Regex(
        pattern = "(移动|右移|左移|上移|下移|前移|后移|旋转|右旋|左旋|放大|缩小|复制|删除|删掉|移除|撤销|重做|选中|选择|加一个|添加一个|创建一个|插入)",
        option = RegexOption.IGNORE_CASE,
    )

    fun blockingMessage(userInput: String): String? {
        val text = userInput.trim()
        if (text.isBlank() || !actionConnector.containsMatchIn(text)) return null

        val actionClauses = actionConnector
            .split(text)
            .map { it.trim() }
            .filter { it.isNotBlank() && actionHint.containsMatchIn(it) }

        return if (actionClauses.size > MAX_ACTION_CLAUSES && !looksLikeSingleGenerationRequest(actionClauses)) {
            "这条指令包含太多动作，请拆成少于 20 步的明确任务。"
        } else {
            null
        }
    }

    private fun looksLikeSingleGenerationRequest(actionClauses: List<String>): Boolean {
        val firstAction = actionClauses.firstOrNull() ?: return false
        if (!generationRequestHint.containsMatchIn(firstAction)) return false
        return actionClauses.drop(1).all(::isGenerationContinuationClause)
    }

    private fun isGenerationContinuationClause(clause: String): Boolean {
        if (generationPlacementHint.containsMatchIn(clause)) return true
        if (independentSceneTargetHint.containsMatchIn(clause)) return false
        if (independentActionAfterGenerationHint.containsMatchIn(clause)) return false
        return generationModifierHint.containsMatchIn(clause)
    }
}
