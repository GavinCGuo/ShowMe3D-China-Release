package com.example.showme3d.studio.examples

import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ModelOriginMode
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.Page
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.TransformData
import java.util.UUID

fun createImmersiveInteriorExampleDocument(
    index: Int,
    assets: ImmersiveInteriorExampleAssets,
    documentId: String = newInteriorExampleId(),
    documentName: String = "布置一间手绘小屋",
): ShowDocument {
    val document = ShowDocument(
        id = documentId,
        name = documentName,
        documentType = DocumentType.PRESENTATION,
    )

    modelDisplayNames.forEach { (id, displayName) ->
        document.objects[id] = SceneObject(
            id = id,
            type = ObjectType.MODEL,
            source = assets.modelSources.getValue(id),
            displayName = displayName,
            isLocked = id == "interior_shell",
            modelOriginMode = ModelOriginMode.SHARED_ORIGIN,
        )
    }
    document.objects["interior_concept_cover"] = SceneObject(
        id = "interior_concept_cover",
        type = ObjectType.IMAGE,
        source = assets.conceptCover,
        displayName = "手绘小屋概念图",
        isLocked = true,
        faceViewer = true,
    )
    document.objects["viewpoint_room"] = SceneObject(
        id = "viewpoint_room",
        type = ObjectType.VIEWPOINT,
        source = "viewpoint",
        displayName = "进入手绘小屋",
        isLocked = false,
    )
    pageCopy.forEachIndexed { pageIndex, copy ->
        document.objects[copyId(pageIndex)] = SceneObject(
            id = copyId(pageIndex),
            type = ObjectType.TEXT,
            source = copy,
            displayName = "第${pageIndex + 1}页说明",
            textColor = 0xFFF4E7D3,
            isLocked = true,
            faceViewer = true,
            textBackgroundEnabled = true,
        )
    }

    val pages = List(7) { pageIndex -> Page(id = "interior_page_${pageIndex + 1}") }
    pages.forEachIndexed { pageIndex, page ->
        document.objects.keys.forEach { objectId -> page.states[objectId] = ObjectState(visible = false) }
        page.show(
            copyId(pageIndex),
            if (pageIndex == 0) {
                TransformData(px = 1.02f, py = 1.36f, pz = -2.20f, scale = 0.62f)
            } else {
                TransformData(px = -0.95f, py = 1.78f, pz = -1.98f, scale = 0.46f)
            },
        )
    }

    pages[0].show(
        "interior_concept_cover",
        TransformData(px = -0.66f, py = 1.08f, pz = -2.36f, scale = 1.88f),
    )

    pages[1].show("interior_shell", roomTransform())

    pages[2].apply {
        show("interior_shell", roomTransform())
        furniturePreviewSpecs.forEach { preview -> show(preview.id, preview.transform()) }
    }

    pages[3].apply {
        show("interior_shell", roomTransform())
        showPlaced(immersiveInteriorFurnitureIds)
        skybox = sketchSkybox(assets)
    }

    pages[4].apply {
        show("interior_shell", roomTransform())
        showPlaced(immersiveInteriorFurnitureIds)
        detailPreviewSpecs.forEach { preview -> show(preview.id, preview.transform()) }
        skybox = sketchSkybox(assets)
    }

    pages[5].apply {
        show("interior_shell", roomTransform())
        showPlaced(immersiveInteriorFurnitureIds)
        showPlaced(immersiveInteriorDetailIds)
        skybox = refinedSkybox(assets)
    }

    pages[6].apply {
        show("interior_shell", roomTransform())
        showPlaced(immersiveInteriorFurnitureIds)
        showPlaced(immersiveInteriorDetailIds)
        show("viewpoint_room", viewpointTransform())
        skybox = refinedSkybox(assets)
    }

    document.pages += pages
    return document
}

val immersiveInteriorFurnitureIds = listOf(
    "furniture_bank",
    "furniture_fridge",
    "furniture_kitchen_island",
    "furniture_bar_chair_1",
    "furniture_bar_chair_2",
    "furniture_sofa",
    "furniture_footrest",
    "furniture_tv_table",
    "furniture_tv",
    "furniture_hallway_table",
    "furniture_table_lamp",
    "furniture_shoe_rack",
    "furniture_clothes_hanger",
)

val immersiveInteriorDetailIds = listOf(
    "detail_faucet",
    "detail_radio",
    "detail_plant",
    "detail_monstera",
    "detail_knife_holder",
    "detail_knife_1",
    "detail_knife_2",
    "detail_knife_3",
    "detail_microwave",
    "detail_shoe_1",
    "detail_shoe_2",
    "detail_glass",
    "detail_cutlery",
    "detail_jacket",
    "detail_artwork",
    "detail_ceiling_lights",
)

private val pageCopy = listOf(
    """房间装饰的设计美学

好看的房间不只依靠某一件家具
而是由空间、比例、色彩、材质与细节共同形成

让我们通过一个小 Demo
感受房间装饰的美感如何形成""",
    """从空间比例开始

房间结构决定了装饰的基本尺度
先看清墙面、开口和留白
再决定家具应该占据多少空间""",
    """选择合适的主家具

沙发、岛台、座椅和收纳
构成房间的主要体量
大小合适、材质协调，画面会更加统一""",
    """建立清晰的视觉层次

当主要家具回到房间
高低、疏密和颜色深浅会共同形成层次
让每一件家具都有合适的位置""",
    """用小物确定风格

植物、灯具、器皿和装饰画虽然体积不大
却能补充房间的色彩与质感
让整体风格变得更加明确""",
    """让颜色与材质彼此呼应

木色、布艺、绿色植物和暖色细节反复出现
不同区域会产生自然的视觉联系
整个房间也会显得更加完整""",
    """回到真实的观看视角

点击白色的人物
从人的观看高度重新观察房间
检查整体比例、视觉重点与细节密度
感受完整装饰最终呈现的效果""",
)

private val modelDisplayNames = linkedMapOf(
    "interior_shell" to "空白房间布局",
    "furniture_bank" to "厨房柜体",
    "furniture_fridge" to "冰箱",
    "furniture_kitchen_island" to "厨房岛台",
    "furniture_bar_chair_1" to "吧椅一",
    "furniture_bar_chair_2" to "吧椅二",
    "furniture_sofa" to "沙发",
    "furniture_footrest" to "脚凳",
    "furniture_tv_table" to "电视柜",
    "furniture_tv" to "电视",
    "furniture_hallway_table" to "玄关桌",
    "furniture_table_lamp" to "台灯",
    "furniture_shoe_rack" to "鞋架",
    "furniture_clothes_hanger" to "衣架",
    "detail_faucet" to "水龙头",
    "detail_radio" to "收音机",
    "detail_plant" to "吊柜植物",
    "detail_monstera" to "龟背竹",
    "detail_knife_holder" to "刀架",
    "detail_knife_1" to "厨刀一",
    "detail_knife_2" to "厨刀二",
    "detail_knife_3" to "厨刀三",
    "detail_microwave" to "微波炉",
    "detail_shoe_1" to "鞋子一",
    "detail_shoe_2" to "鞋子二",
    "detail_glass" to "玻璃杯",
    "detail_cutlery" to "餐具",
    "detail_jacket" to "外套",
    "detail_artwork" to "墙面装饰画",
    "detail_ceiling_lights" to "成排吊灯",
)

private data class PreviewSpec(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val maxSize: Float,
    val targetX: Float,
    val targetY: Float,
    val targetZ: Float,
    val targetSize: Float,
) {
    fun transform(): TransformData {
        val previewScale = targetSize / maxSize
        val outwardX = targetX + if (targetX < 0.0f) -0.35f else 0.35f
        return TransformData(
            px = outwardX - centerX * previewScale,
            py = targetY - centerY * previewScale,
            pz = targetZ - centerZ * previewScale,
            scale = previewScale,
        )
    }
}

private val furniturePreviewSpecs = listOf(
    preview("furniture_bank", -0.14364f, 0.04124f, 0.05708f, 0.32012f, -1.55f, 0.42f, -2.48f, 0.40f),
    preview("furniture_fridge", -0.04946f, 0.08364f, -0.10732f, 0.15386f, 1.55f, 0.42f, -2.48f, 0.34f),
    preview("furniture_kitchen_island", -0.00058f, 0.03755f, 0.04040f, 0.17486f, -1.55f, 0.78f, -2.48f, 0.38f),
    preview("furniture_bar_chair_1", 0.00775f, 0.03290f, 0.09971f, 0.05370f, 1.55f, 0.76f, -2.48f, 0.30f),
    preview("furniture_bar_chair_2", 0.06604f, 0.03290f, 0.04914f, 0.05370f, 1.55f, 1.08f, -2.48f, 0.30f),
    preview("furniture_sofa", -0.06393f, 0.03380f, 0.26374f, 0.17833f, -1.55f, 1.16f, -2.48f, 0.40f),
    preview("furniture_footrest", 0.01796f, 0.02242f, 0.22607f, 0.05674f, 1.55f, 1.39f, -2.48f, 0.30f),
    preview("furniture_tv_table", 0.06672f, 0.01867f, 0.18333f, 0.06319f, -1.55f, 1.51f, -2.48f, 0.32f),
    preview("furniture_tv", 0.07032f, 0.05110f, 0.18165f, 0.05481f, 1.55f, 1.70f, -2.48f, 0.30f),
    preview("furniture_hallway_table", -0.03308f, 0.03576f, -0.20668f, 0.06041f, -1.55f, 1.84f, -2.48f, 0.32f),
    preview("furniture_table_lamp", -0.03244f, 0.08021f, -0.20564f, 0.02837f, 1.55f, 2.00f, -2.48f, 0.28f),
    preview("furniture_shoe_rack", 0.03653f, 0.01516f, 0.33735f, 0.05952f, -1.55f, 2.15f, -2.48f, 0.30f),
    preview("furniture_clothes_hanger", 0.02678f, 0.14172f, 0.34979f, 0.04984f, 1.55f, 2.30f, -2.48f, 0.30f),
)

private val detailPreviewSpecs = listOf(
    preview("detail_faucet", -0.20900f, 0.08690f, 0.14862f, 0.02324f, -1.55f, 0.38f, -2.40f, 0.26f),
    preview("detail_radio", 0.05549f, 0.08310f, -0.00851f, 0.03107f, 1.55f, 0.38f, -2.40f, 0.26f),
    preview("detail_plant", -0.05847f, 0.17664f, -0.10869f, 0.06333f, -1.55f, 0.68f, -2.40f, 0.30f),
    preview("detail_monstera", 0.20913f, 0.05685f, 0.31227f, 0.10051f, 1.55f, 0.70f, -2.40f, 0.34f),
    preview("detail_knife_holder", -0.19856f, 0.08474f, 0.06504f, 0.02144f, -1.55f, 1.00f, -2.40f, 0.25f),
    preview("detail_knife_1", -0.20209f, 0.09738f, 0.06671f, 0.02831f, 1.55f, 1.00f, -2.40f, 0.27f),
    preview("detail_knife_2", -0.19450f, 0.09678f, 0.06216f, 0.02083f, -1.55f, 1.30f, -2.40f, 0.25f),
    preview("detail_knife_3", -0.19044f, 0.09114f, 0.06837f, 0.02326f, 1.55f, 1.30f, -2.40f, 0.25f),
    preview("detail_microwave", -0.18794f, 0.08917f, 0.02248f, 0.04724f, -1.55f, 1.60f, -2.40f, 0.30f),
    preview("detail_shoe_1", 0.05509f, 0.01539f, 0.32874f, 0.01571f, 1.55f, 1.60f, -2.40f, 0.25f),
    preview("detail_shoe_2", 0.02598f, 0.01617f, 0.33046f, 0.01595f, -1.55f, 1.90f, -2.40f, 0.25f),
    preview("detail_glass", -0.03780f, 0.07462f, 0.08339f, 0.01084f, 1.55f, 1.90f, -2.40f, 0.25f),
    preview("detail_cutlery", -0.01756f, 0.07059f, 0.06919f, 0.02930f, -1.55f, 2.20f, -2.40f, 0.27f),
    preview("detail_jacket", 0.04599f, 0.10659f, 0.34346f, 0.07306f, 1.55f, 2.20f, -2.40f, 0.30f),
    preview("detail_artwork", -0.08971f, 0.12345f, 0.30077f, 0.10043f, -0.85f, 2.42f, -2.40f, 0.32f),
    preview("detail_ceiling_lights", 0.03281f, 0.20953f, -0.07146f, 0.40435f, 0.85f, 2.42f, -2.40f, 0.42f),
)

private fun preview(
    id: String,
    centerX: Float,
    centerY: Float,
    centerZ: Float,
    maxSize: Float,
    targetX: Float,
    targetY: Float,
    targetZ: Float,
    targetSize: Float,
) = PreviewSpec(id, centerX, centerY, centerZ, maxSize, targetX, targetY, targetZ, targetSize)

private fun Page.show(id: String, transform: TransformData) {
    states[id] = ObjectState(transform = transform, visible = true)
}

private fun Page.showPlaced(ids: List<String>) {
    ids.forEach { id -> show(id, roomTransform()) }
}

private fun roomTransform() = TransformData(
    px = 0.10604139f,
    py = 0.71439004f,
    pz = -2.5826592f,
    rotationY = -1.3380868f,
    scale = 2.7302701f,
)

private fun viewpointTransform() = TransformData(
    px = -0.07399597f,
    py = 0.93245715f,
    pz = -1.9191768f,
    rotationY = 3.5633793f,
    scale = 1.2986841f,
)

private fun sketchSkybox(assets: ImmersiveInteriorExampleAssets) = PageSkybox(
    source = assets.sketchSkybox,
    displayName = "未完成的草图环境",
)

private fun refinedSkybox(assets: ImmersiveInteriorExampleAssets) = PageSkybox(
    source = assets.refinedSkybox,
    displayName = "温暖的手绘环境",
)

private fun newInteriorExampleId(): String = "s" + UUID.randomUUID().toString().replace("-", "")

private fun copyId(pageIndex: Int) = "interior_copy_${pageIndex + 1}"
