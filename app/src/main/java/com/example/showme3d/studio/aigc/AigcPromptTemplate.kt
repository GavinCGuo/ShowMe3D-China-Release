package com.example.showme3d.studio.aigc

enum class AigcPromptUseCase {
    TEXT_TO_IMAGE,
    TEXT_TO_MODEL,
    IMAGE_TO_IMAGE,
    IMAGE_TO_MODEL,
    STROKE_TO_IMAGE,
    STROKE_TO_MODEL;

    fun fixedPrompt(sourceText: String = ""): String {
        val source = sourceText.trim()
        return when (this) {
            TEXT_TO_IMAGE -> buildString {
                append("根据用户提供的文本描述生成一张适合空间展示和后续 3D 参考的单主体图片。主体需要完整居中，轮廓清晰，背景纯净，不要文字、水印或复杂场景。")
                if (source.isNotBlank()) append("\n文本描述：$source")
            }
            TEXT_TO_MODEL -> buildString {
                append("根据用户提供的文本描述生成一个完整可旋转查看的 3D 模型。模型需要主体明确、体积完整、结构稳固，适合产品级空间展示，避免薄片、文字、水印、底座和复杂背景。")
                if (source.isNotBlank()) append("\n文本描述：$source")
            }
            IMAGE_TO_IMAGE ->
                "基于参考图片生成一张新的图片版本。保留参考图主体的核心身份、主要轮廓和关键特征，输出单主体、完整居中、背景纯净的结果，不要文字、水印或复杂环境。"
            IMAGE_TO_MODEL ->
                "基于参考图片生成一个完整可旋转查看的 3D 模型。保留参考图主体身份和主要轮廓，合理补全不可见背面和体积，避免薄片、文字、水印、底座和背景板。"
            STROKE_TO_IMAGE ->
                ""
            STROKE_TO_MODEL ->
                "根据输入参考图生成一个完整可旋转查看的 3D 模型。尊重主体的主要轮廓和核心构成，合理补全不可见的背面、结构和体积，避免薄片、文字、水印、底座和背景板。"
        }
    }
}

enum class AigcPromptTemplate(
    val displayName: String,
    val shortDescription: String,
) {
    CARTOON(
        displayName = "卡通",
        shortDescription = "圆润造型、明快色彩、玩具感",
    ),
    REALISTIC(
        displayName = "写实",
        shortDescription = "真实材质、自然细节、产品级展示",
    ),
    LOW_POLY(
        displayName = "低多边形",
        shortDescription = "切面结构、低面数、游戏资产感",
    ),
    VOXEL(
        displayName = "体素",
        shortDescription = "方块结构、像素审美、轮廓清晰",
    );

    fun stylePrompt(useCase: AigcPromptUseCase): String =
        when (useCase) {
            AigcPromptUseCase.TEXT_TO_IMAGE -> textToImageStyle()
            AigcPromptUseCase.TEXT_TO_MODEL -> textToModelStyle()
            AigcPromptUseCase.IMAGE_TO_IMAGE -> imageToImageStyle()
            AigcPromptUseCase.IMAGE_TO_MODEL -> imageToModelStyle()
            AigcPromptUseCase.STROKE_TO_IMAGE -> strokeToImageStyle()
            AigcPromptUseCase.STROKE_TO_MODEL -> strokeToModelStyle()
        }

    fun lockedPrompt(useCase: AigcPromptUseCase): String = stylePrompt(useCase)

    fun buildPrompt(useCase: AigcPromptUseCase, userPrompt: String): String =
        buildAigcPrompt(
            useCase = useCase,
            template = this,
            sourceText = userPrompt,
            userPrompt = userPrompt,
        )

    private fun textToImageStyle(): String =
        when (this) {
            CARTOON -> "模板要求：卡通风格。造型圆润夸张，边缘干净，色彩明快，纯净浅色背景。"
            REALISTIC -> "模板要求：写实风格。使用真实材质、自然比例、清晰轮廓和柔和棚拍光，纯净背景。"
            LOW_POLY -> "模板要求：低多边形风格。使用清晰切面、简化几何、干净轮廓和少量纯色块，纯净背景。"
            VOXEL -> "模板要求：体素风格。主体由清晰方块体素构成，像素艺术审美，结构完整，轮廓明确，纯净背景。"
        }

    private fun textToModelStyle(): String =
        when (this) {
            CARTOON -> "模板要求：卡通玩具风 3D 模型。造型圆润，比例略夸张，表面干净，颜色明快，轮廓清晰。"
            REALISTIC -> "模板要求：写实 3D 模型。保持真实比例和物理材质质感，细节清晰但结构干净，适合产品级空间展示。"
            LOW_POLY -> "模板要求：低多边形 3D 模型。使用简洁切面、低面数结构、清晰轮廓和有限色块，像游戏道具资产。"
            VOXEL -> "模板要求：体素 3D 模型。主体由规整方块体素构成，像素艺术风格，轮廓明确，结构稳固。"
        }

    private fun imageToImageStyle(): String =
        when (this) {
            CARTOON -> "模板要求：将参考主体重绘为卡通玩具风图片。造型圆润，色彩明快，边缘干净，不要改变主体身份。"
            REALISTIC -> "模板要求：将参考主体重绘为写实产品展示图。材质真实，光照自然，细节清晰，不要改变主体身份。"
            LOW_POLY -> "模板要求：将参考主体重绘为低多边形游戏资产风图片。几何切面清晰，色块简洁，不要改变主体身份。"
            VOXEL -> "模板要求：将参考主体重绘为体素像素风图片。使用方块体素结构和清晰轮廓，不要改变主体身份。"
        }

    private fun imageToModelStyle(): String =
        when (this) {
            CARTOON -> "模板要求：卡通玩具风 3D 模型。保留主体身份和主要轮廓，造型圆润，色彩明快，表面干净。"
            REALISTIC -> "模板要求：写实 3D 模型。保留主体身份和主要轮廓，材质真实，细节清晰，适合产品级展示。"
            LOW_POLY -> "模板要求：低多边形 3D 模型。保留主体身份和主要轮廓，使用清晰切面和低面数几何。"
            VOXEL -> "模板要求：体素 3D 模型。保留主体身份和主要轮廓，用规整方块体素构建主体。"
        }

    private fun strokeToImageStyle(): String =
        when (this) {
            CARTOON -> "模板要求：卡通玩具风 2D 参考图。主体圆润完整、色彩明快、背景纯净。"
            REALISTIC -> "模板要求：写实 2D 参考图。主体比例自然、材质真实、轮廓清晰、背景纯净。"
            LOW_POLY -> "模板要求：低多边形 2D 参考图。主体切面清晰、几何简洁、背景纯净。"
            VOXEL -> "模板要求：体素像素风 2D 参考图。主体由方块体素构成、轮廓清晰、背景纯净。"
        }

    private fun strokeToModelStyle(): String =
        when (this) {
            CARTOON -> "模板要求：卡通玩具风 3D 模型。造型圆润，比例略夸张，颜色明快，补全背面和体积。"
            REALISTIC -> "模板要求：写实 3D 模型。保持自然比例和真实材质，补全背面和体积，适合产品级空间展示。"
            LOW_POLY -> "模板要求：低多边形 3D 模型。使用清晰切面、低面数几何和简洁色块，补全背面和体积。"
            VOXEL -> "模板要求：体素 3D 模型。用规整方块体素构建主体，轮廓明确，补全背面和体积。"
        }
}

fun buildAigcPrompt(
    useCase: AigcPromptUseCase,
    template: AigcPromptTemplate?,
    userPrompt: String,
    sourceText: String? = null,
): String {
    val trimmedUserPrompt = userPrompt.trim()
    val trimmedSourceText = sourceText?.trim().orEmpty()
    val fixedSource = trimmedSourceText.ifBlank {
        when (useCase) {
            AigcPromptUseCase.TEXT_TO_IMAGE,
            AigcPromptUseCase.TEXT_TO_MODEL -> trimmedUserPrompt
            else -> ""
        }
    }
    val shouldAppendUserPrompt = trimmedUserPrompt.isNotBlank() &&
        (sourceText != null || (useCase != AigcPromptUseCase.TEXT_TO_IMAGE && useCase != AigcPromptUseCase.TEXT_TO_MODEL))
    return listOfNotNull(
        useCase.fixedPrompt(fixedSource).takeIf { it.isNotBlank() },
        template?.stylePrompt(useCase),
        trimmedUserPrompt.takeIf { shouldAppendUserPrompt }?.let { "用户额外要求：$it" },
    ).joinToString(separator = "\n\n")
}

fun buildAigcPromptDetails(
    useCase: AigcPromptUseCase,
    template: AigcPromptTemplate?,
    userPrompt: String,
): String {
    val trimmedUserPrompt = userPrompt.trim()
    return listOfNotNull(
        template?.stylePrompt(useCase),
        trimmedUserPrompt.takeIf { it.isNotBlank() }?.let { "用户额外要求：$it" },
    ).joinToString(separator = "\n\n")
}
