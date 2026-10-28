package com.example.showme3d.studio.examples

import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.Page
import com.example.showme3d.studio.model.PageAnnotation
import com.example.showme3d.studio.model.PageAnnotationType
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.StrokePoint
import com.example.showme3d.studio.model.TransformData
import java.util.UUID

private const val PI = 3.1415927f

fun createCuriosityExampleDocument(
    index: Int,
    assets: CuriosityExampleAssets,
    documentId: String = newExampleId(),
    documentName: String = "把好奇号装起来",
): ShowDocument {
    val document = ShowDocument(
        id = documentId,
        name = documentName,
        documentType = DocumentType.PRESENTATION,
    )

    val modelSources = linkedMapOf(
        "rover_animated_clean" to ("好奇号展开动画" to assets.animatedCleanRover),
        "rover_complete_dirty" to ("火星服役状态完整好奇号" to assets.completeDirtyRover),
        "rover_body" to ("车身主体" to assets.body),
        "suspension_left" to ("左侧摇臂悬挂" to assets.suspensionLeft),
        "suspension_right" to ("右侧摇臂悬挂" to assets.suspensionRight),
        "wheel_left_front" to ("左前轮" to assets.wheelLeftFront),
        "wheel_left_middle" to ("左中轮" to assets.wheelLeftMiddle),
        "wheel_left_rear" to ("左后轮" to assets.wheelLeftRear),
        "wheel_right_front" to ("右前轮" to assets.wheelRightFront),
        "wheel_right_middle" to ("右中轮" to assets.wheelRightMiddle),
        "wheel_right_rear" to ("右后轮" to assets.wheelRightRear),
        "camera_mast" to ("相机桅杆与 ChemCam" to assets.mast),
        "robotic_arm" to ("五自由度机械臂" to assets.roboticArm),
        "martian_rock" to ("火星岩石样本" to assets.martianRock),
        "martian_ground" to ("火星地表" to assets.martianGround),
    )
    modelSources.forEach { (id, value) ->
        document.objects[id] = SceneObject(
            id = id,
            type = ObjectType.MODEL,
            source = value.second,
            displayName = value.first,
            isLocked = id == "martian_ground",
            modelOriginMode = if (id in cleanAssemblyObjectIds) {
                ModelOriginMode.SHARED_ORIGIN
            } else {
                ModelOriginMode.CENTER_AND_FIT
            },
            playEmbeddedAnimation = id == "rover_animated_clean",
        )
    }

    document.objects["nasa_source_page"] = SceneObject(
        id = "nasa_source_page",
        type = ObjectType.IMAGE,
        source = assets.nasaSourcePage,
        displayName = "NASA 好奇号打印件来源页",
        isLocked = true,
        faceViewer = true,
    )
    document.objects["nasa_intro_copy"] = SceneObject(
        id = "nasa_intro_copy",
        type = ObjectType.TEXT,
        source = "这是一份 NASA 的打印模型\n\n跟随接下来的引导\n我们一起把好奇号装起来\n重新出发火星\n\n准备好了吗？",
        displayName = "项目说明",
        textColor = 0xFFF4E7D3,
        isLocked = true,
        faceViewer = true,
        textBackgroundEnabled = true,
    )

    val pageTitles = listOf(
        "写在前面",
        "先认识一下",
        "确认零件",
        "第一步 · 放好车身",
        "第二步 · 接上悬挂",
        "第三步 · 装上车轮",
        "第四步 · 升起桅杆",
        "第五步 · 接上机械臂",
        "飞向火星",
    )
    pageTitles.forEachIndexed { pageIndex, title ->
        val id = titleId(pageIndex)
        document.objects[id] = SceneObject(
            id = id,
            type = ObjectType.TEXT,
            source = title,
            displayName = title,
            textColor = 0xFFF4E7D3,
            isLocked = true,
            faceViewer = true,
            textBackgroundEnabled = true,
        )
    }

    val pages = List(pageTitles.size) { pageIndex -> Page(id = "curiosity_page_${pageIndex + 1}") }
    pages.forEachIndexed { pageIndex, page ->
        document.objects.keys.forEach { objectId ->
            page.states[objectId] = ObjectState(visible = false)
        }
        page.states[titleId(pageIndex)] = ObjectState(
            transform = TransformData(px = -1.28f, py = 2.23f, pz = -1.25f, scale = 0.58f),
            visible = true,
        )
    }

    pages[0].apply {
        show("nasa_source_page", t(-1.0f, 1.18f, -1.85f, 2.75f))
        show("nasa_intro_copy", t(1.08f, 1.22f, -1.82f, 1.0f))
    }

    pages[1].apply {
        show("rover_animated_clean", completeTransform())
        annotations += comments(
            comment("p1_cycle", "先看它舒展开来\n认识一下完整的好奇号", 0.0f, 0.91f, -2.15f, -1.42f, 1.48f, -1.22f),
        )
    }

    pages[2].apply {
        show("rover_body", stagedPart(-0.02f, 0.10f, -0.05f))
        show("suspension_left", stagedSuspensionLeft())
        show("suspension_right", stagedSuspensionRight())
        explodedWheelTransforms().forEachIndexed { wheelIndex, transform ->
            show(wheelIds[wheelIndex], transform)
        }
        show("camera_mast", stagedPart(-0.55f, 0.72f, 0.02f))
        show("robotic_arm", stagedPart(0.58f, 0.72f, 0.02f))
        annotations += comments(
            comment("p2_body", "车身\n装着它的“大脑”和电源", -0.02f, 1.01f, -2.20f, 0.0f, 1.62f, -1.20f),
            comment("p2_suspension", "两套悬挂\n帮它翻过崎岖的地面", -0.23f, 0.87f, -2.12f, -1.48f, 1.26f, -1.18f),
            comment("p2_wheels", "六个车轮\n每一个都能独立驱动", 0.0f, 0.10f, -1.70f, 0.0f, 0.68f, -0.84f),
        )
    }

    pages[3].apply {
        show("rover_body", bodyTransform())
        stageFutureParts(this, installedThrough = 0)
        annotations += comments(
            comment("p3_body", "先从车身开始\n重要设备都被保护在这里", 0.0f, 0.91f, -2.15f, -1.42f, 1.52f, -1.20f),
            comment("p3_mount", "两侧已经留好位置\n接下来装上悬挂", 0.43f, 0.82f, -2.03f, 1.40f, 1.22f, -1.18f),
        )
    }

    pages[4].apply {
        show("rover_body", bodyTransform())
        show("suspension_left", suspensionLeftTransform())
        show("suspension_right", suspensionRightTransform())
        stageFutureParts(this, installedThrough = 1)
        annotations += comments(
            comment("p4_left", "左侧悬挂，就位", 0.49f, 0.79f, -2.04f, 1.42f, 1.40f, -1.18f),
            comment("p4_right", "右侧也一样\n两边会一起应对起伏", -0.49f, 0.79f, -2.04f, -1.42f, 1.24f, -1.18f),
        )
    }

    pages[5].apply {
        showBaseAssembly(this)
        finalWheelTransforms().forEachIndexed { wheelIndex, transform ->
            show(wheelIds[wheelIndex], transform)
        }
        show("camera_mast", stagedMast())
        show("robotic_arm", stagedArm())
        annotations += comments(
            comment("p5_right", "右边三个车轮\n稳稳落地", 0.59f, 0.54f, -2.07f, 1.42f, 1.08f, -1.18f),
            comment("p5_left", "左边三个车轮\n前后轮还能转向", -0.59f, 0.54f, -1.41f, -1.42f, 1.20f, -1.16f),
        )
    }

    pages[6].apply {
        showBaseAssembly(this)
        finalWheelTransforms().forEachIndexed { wheelIndex, transform ->
            show(wheelIds[wheelIndex], transform)
        }
        show("camera_mast", mastTransform())
        show("robotic_arm", stagedArm())
        annotations += comments(
            comment("p6_mast", "升起桅杆\n用相机和激光观察远方", -0.21f, 1.62f, -1.77f, -1.42f, 1.68f, -1.18f),
        )
    }

    pages[7].apply {
        showBaseAssembly(this)
        finalWheelTransforms().forEachIndexed { wheelIndex, transform ->
            show(wheelIds[wheelIndex], transform)
        }
        show("camera_mast", mastTransform())
        show("robotic_arm", armTransform())
        show("martian_rock", rockTransform())
        annotations += comments(
            comment("p7_arm", "最后，接上机械臂", 0.25f, 0.87f, -1.31f, -1.38f, 1.40f, -1.14f),
            comment("p7_tools", "钻头、相机和光谱仪\n都在它的“手”上", 0.24f, 1.39f, -0.91f, 1.42f, 1.44f, -1.12f),
            comment("p7_rock", "发现目标\n准备靠近取样", 0.86f, 0.30f, -0.64f, 1.42f, 0.70f, -0.58f),
        )
    }

    pages[8].apply {
        show("rover_complete_dirty", completeTransform())
        // Imported models are normalized to a 0.8 m longest side; compensate so
        // the authored 5.8 m ground plane remains room-scale.
        show("martian_ground", t(0.0f, 0.02f, -1.80f, 7.25f))
        show("martian_rock", rockTransform())
        skybox = PageSkybox(
            source = assets.marsPanorama,
            displayName = "Curiosity - Nevado Sajama 360°",
            rotationY = PI,
        )
        annotations += comments(
            comment("p8_power", "核电源日夜供能\n陪它走过漫长岁月", 0.0f, 1.18f, -2.20f, -1.40f, 1.48f, -1.20f),
            comment("p8_mission", "去寻找火星\n曾经宜居的证据", 0.08f, 1.58f, -1.70f, 1.38f, 1.70f, -1.18f),
        )
    }

    pages.forEach { page -> page.applyCompactExhibitLayout(document) }
    document.pages += pages
    return document
}

private val wheelIds = listOf(
    "wheel_left_front",
    "wheel_left_middle",
    "wheel_left_rear",
    "wheel_right_front",
    "wheel_right_middle",
    "wheel_right_rear",
)

private val cleanAssemblyObjectIds = setOf(
    "rover_animated_clean",
    "rover_body",
    "suspension_left",
    "suspension_right",
    *wheelIds.toTypedArray(),
    "camera_mast",
    "robotic_arm",
)

private fun Page.show(id: String, transform: TransformData) {
    states[id] = ObjectState(transform = transform, visible = true)
}

private fun showBaseAssembly(page: Page) {
    page.show("rover_body", bodyTransform())
    page.show("suspension_left", suspensionLeftTransform())
    page.show("suspension_right", suspensionRightTransform())
}

private fun stageFutureParts(page: Page, installedThrough: Int) {
    if (installedThrough < 1) {
        page.show("suspension_left", stagedSuspensionLeft())
        page.show("suspension_right", stagedSuspensionRight())
    }
    explodedWheelTransforms().forEachIndexed { wheelIndex, transform ->
        page.show(wheelIds[wheelIndex], transform)
    }
    page.show("camera_mast", stagedMast())
    page.show("robotic_arm", stagedArm())
}

private fun bodyTransform() = assemblyTransform()
private fun suspensionLeftTransform() = assemblyTransform()
private fun suspensionRightTransform() = assemblyTransform()
// The rover faces the viewer, so its own left side appears on screen-right.
// Stage each suspension beside the side where it will ultimately be installed.
private fun stagedSuspensionLeft() = stagedPart(1.05f, 0.08f, -0.08f)
private fun stagedSuspensionRight() = stagedPart(-1.05f, 0.08f, -0.08f)
private fun mastTransform() = assemblyTransform()
private fun armTransform() = assemblyTransform()
private fun rockTransform() = t(0.86f, 0.27f, -0.64f, 1.0f, 0.25f)
private fun stagedMast() = stagedPart(-0.72f, 0.62f, -0.12f)
private fun stagedArm() = stagedPart(0.72f, 0.58f, -0.08f)
private fun assemblyTransform() = completeTransform()
private fun stagedPart(dx: Float, dy: Float, dz: Float) =
    assemblyTransform().copy(px = dx, py = 1.02f + dy, pz = -1.85f + dz)
private fun completeTransform() = t(0.0f, 1.02f, -1.85f, 2.75f)

private fun finalWheelTransforms() = listOf(
    assemblyTransform(),
    assemblyTransform(),
    assemblyTransform(),
    assemblyTransform(),
    assemblyTransform(),
    assemblyTransform(),
)

private fun explodedWheelTransforms() = listOf(
    // Left-side wheels occupy the viewer's right half, inner to outer.
    stagedPart(-0.32f, -0.52f, -0.05f),
    stagedPart(0.07f, -0.46f, 0.61f),
    stagedPart(0.60f, -0.38f, 1.21f),
    // Right-side wheels mirror them on the viewer's left half.
    stagedPart(0.32f, -0.52f, -0.05f),
    stagedPart(-0.07f, -0.46f, 0.61f),
    stagedPart(-0.60f, -0.38f, 1.21f),
)

private fun t(
    x: Float,
    y: Float,
    z: Float,
    scale: Float,
    rotationY: Float = 0.0f,
) = TransformData(px = x, py = y, pz = z, rotationY = rotationY, scale = scale)

private fun titleId(pageIndex: Int) = "curiosity_title_${pageIndex + 1}"

private fun comment(
    id: String,
    text: String,
    anchorX: Float,
    anchorY: Float,
    anchorZ: Float,
    panelX: Float,
    panelY: Float,
    panelZ: Float,
) = PageAnnotation(
    id = id,
    type = PageAnnotationType.COMMENT,
    commentText = text,
    anchorPoint = StrokePoint(anchorX, anchorY, anchorZ),
    panelPoint = StrokePoint(panelX, panelY, panelZ),
)

private fun comments(vararg annotations: PageAnnotation) = annotations.toList()

private fun newExampleId(): String = "s" + UUID.randomUUID().toString().replace("-", "")

/**
 * The source layout was authored as a room-scale exhibit. The home/editor panels
 * occupy the user's near field, so the built-in sample is fitted into a compact
 * presentation volume centered 2.45 m ahead:
 *
 * - complete rover longest side: ~1.14 m (was ~2.20 m)
 * - exploded parts width: ~1.9 m including labels
 * - nearest labels: at least ~1.77 m ahead
 * - title height: ~1.64 m
 */
private fun Page.applyCompactExhibitLayout(document: ShowDocument) {
    states.forEach { (objectId, state) ->
        if (!state.visible) return@forEach
        val compact = state.transform.toCompactExhibitTransform()
        state.transform = when {
            objectId == "martian_ground" -> compact.copy(
                py = 0.54f,
                pz = COMPACT_EXHIBIT_CENTER_Z,
                scale = state.transform.scale * COMPACT_EXHIBIT_SCALE,
            )
            document.objects[objectId]?.type == ObjectType.VIEWPOINT -> compact.copy(scale = 1.0f)
            document.objects[objectId]?.type == ObjectType.TEXT -> compact.copy(scale = 0.42f)
            else -> compact.copy(scale = state.transform.scale * COMPACT_EXHIBIT_SCALE)
        }
    }
    annotations.forEach { annotation ->
        annotation.anchorPoint = annotation.anchorPoint?.toCompactExhibitPoint()
        annotation.panelPoint = annotation.panelPoint?.toCompactExhibitPoint()
    }
}

private fun TransformData.toCompactExhibitTransform(): TransformData = copy(
    px = px * COMPACT_EXHIBIT_SCALE,
    py = COMPACT_EXHIBIT_CENTER_Y + (py - SOURCE_EXHIBIT_CENTER_Y) * COMPACT_EXHIBIT_SCALE,
    pz = COMPACT_EXHIBIT_CENTER_Z + (pz - SOURCE_EXHIBIT_CENTER_Z) * COMPACT_EXHIBIT_SCALE,
)

private fun StrokePoint.toCompactExhibitPoint(): StrokePoint = copy(
    x = x * COMPACT_EXHIBIT_SCALE,
    y = COMPACT_EXHIBIT_CENTER_Y + (y - SOURCE_EXHIBIT_CENTER_Y) * COMPACT_EXHIBIT_SCALE,
    z = COMPACT_EXHIBIT_CENTER_Z + (z - SOURCE_EXHIBIT_CENTER_Z) * COMPACT_EXHIBIT_SCALE,
)

private const val COMPACT_EXHIBIT_SCALE = 0.52f
private const val SOURCE_EXHIBIT_CENTER_Y = 1.0f
private const val SOURCE_EXHIBIT_CENTER_Z = -1.85f
private const val COMPACT_EXHIBIT_CENTER_Y = 1.0f
private const val COMPACT_EXHIBIT_CENTER_Z = -2.45f
