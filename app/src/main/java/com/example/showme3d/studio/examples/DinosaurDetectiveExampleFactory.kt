package com.example.showme3d.studio.examples

import com.example.showme3d.studio.model.DocumentType
import com.example.showme3d.studio.model.ObjectState
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.Page
import com.example.showme3d.studio.model.PageSkybox
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.ShowDocument
import com.example.showme3d.studio.model.TransformData
import java.util.UUID

fun createDinosaurDetectiveExampleDocument(
    index: Int,
    assets: DinosaurDetectiveExampleAssets,
    documentId: String = newDinosaurExampleId(),
    documentName: String = "恐龙侦探：化石里藏着什么？",
): ShowDocument {
    val document = ShowDocument(
        id = documentId,
        name = documentName,
        documentType = DocumentType.PRESENTATION,
    )

    dinosaurDisplayNames.forEach { (id, displayName) ->
        document.objects[id] = SceneObject(
            id = id,
            type = ObjectType.MODEL,
            source = assets.modelSources.getValue(id),
            displayName = displayName,
            playEmbeddedAnimation = id == "trex_animated",
        )
    }
    dinosaurPageCopy.forEachIndexed { pageIndex, copy ->
        document.objects[copyId(pageIndex)] = SceneObject(
            id = copyId(pageIndex),
            type = ObjectType.TEXT,
            source = copy,
            displayName = "第${pageIndex + 1}页说明",
            textColor = 0xFFF7EAD4,
            isLocked = true,
            faceViewer = true,
            textBackgroundEnabled = true,
        )
    }
    document.objects["trex_ct_scan_panel"] = SceneObject(
        id = "trex_ct_scan_panel",
        type = ObjectType.IMAGE,
        source = assets.ctScanPanel,
        displayName = "霸王龙脑腔 CT 扫描底板",
        isLocked = true,
        faceViewer = true,
    )

    val pages = List(7) { pageIndex ->
        Page(id = "dinosaur_page_${pageIndex + 1}").apply {
            show(copyId(pageIndex), copyTransform(pageIndex))
        }
    }

    pages[0].show(
        "raptor_claw",
        TransformData(px = 0.62f, py = 1.26f, pz = -2.18f, rotationY = -0.28f, scale = 1.74f),
        autoRotateY = true,
    )

    pages[1].apply {
        show("trex_skull", TransformData(px = 0.70f, py = 1.48f, pz = -2.48f, rotationY = 0.18f, scale = 1.78f), autoRotateY = true)
        show("trex_tooth", TransformData(px = 0.74f, py = 0.68f, pz = -1.82f, rotationY = -0.34f, scale = 1.18f), autoRotateY = true)
    }

    pages[2].show(
        "dinosaur_footprint",
        TransformData(px = 0.62f, py = 1.24f, pz = -2.16f, rotationY = -0.12f, scale = 1.76f),
    )

    pages[3].apply {
        show(
            "archaeopteryx_fossil",
            TransformData(px = 0.64f, py = 1.30f, pz = -2.38f, rotationY = -0.10f, scale = 1.76f),
        )
        skybox = PageSkybox(
            source = assets.fossilGallerySkybox,
            displayName = "羽毛化石展厅",
            rotationY = 0.05f,
        )
    }

    pages[4].apply {
        show(
            "trex_ct_scan_panel",
            TransformData(px = 0.62f, py = 1.28f, pz = -2.44f, scale = 1.68f),
        )
        show(
            "trex_brain_endocast",
            TransformData(px = 0.62f, py = 1.28f, pz = -1.94f, rotationY = -0.28f, scale = 1.18f),
            autoRotateY = true,
        )
    }

    pages[5].show(
        "trex_skeleton",
        TransformData(px = 0.42f, py = 1.30f, pz = -3.00f, rotationY = -0.18f, scale = 3.00f),
        autoRotateY = true,
    )

    pages[6].apply {
        show(
            "trex_animated",
            TransformData(px = 0.42f, py = 1.06f, pz = -2.90f, rotationY = -0.30f, scale = 2.68f),
        )
        skybox = PageSkybox(
            source = assets.cretaceousForestSkybox,
            displayName = "白垩纪森林",
            rotationY = -0.10f,
        )
    }

    document.pages += pages
    return document
}

private val dinosaurDisplayNames = linkedMapOf(
    "raptor_claw" to "迅猛龙爪化石",
    "trex_tooth" to "霸王龙牙齿 · YPM VP.004192",
    "trex_skull" to "霸王龙头骨与下颌",
    "dinosaur_footprint" to "侏罗纪兽脚类恐龙脚印",
    "archaeopteryx_fossil" to "始祖鸟化石铸型",
    "trex_brain_endocast" to "霸王龙脑腔内模 · AMNH 5117",
    "trex_skeleton" to "完整霸王龙骨架",
    "trex_animated" to "骨骼动画霸王龙",
)

private val dinosaurPageCopy = listOf(
    """案件开启｜化石里藏着什么？

恐龙已经消失了六千多万年
我们只能从留下的证据开始调查""",
    """证据 01｜牙齿：它吃什么？

粗壮的头骨承受强大的咬合力
弯曲的牙齿负责刺入并撕扯

暴露了捕食者的菜单""",
    """证据 02｜脚印：它怎么走？

脚掌大小，留在一个脚印里
行走方向，藏在一串脚印中

动作却留在了岩石上""",
    """证据 03｜羽毛：它长什么样？

有些化石保存的不只是骨骼
岩石上还留下了清晰的羽毛轮廓

改变了人们对恐龙外表的认识""",
    """证据 04｜脑腔：它如何感知？

头骨内部也留下了空间
科学家用 CT 读取这些隐藏结构

它留下的形状仍能被复原""",
    """拼合证据｜从碎骨到完整骨架

一块块骨骼重新连接
头部、后腿与尾巴恢复原来的关系

逐渐变回一副完整身体""",
    """复原完成｜它再次迈出一步

牙齿、脚印、羽毛、脑腔与骨骼
科学复原把线索组合起来
让消失的恐龙再次迈出一步""",
)

private fun Page.show(id: String, transform: TransformData, autoRotateY: Boolean = false) {
    states[id] = ObjectState(transform = transform, visible = true, autoRotateY = autoRotateY)
}

private fun copyTransform(pageIndex: Int) = if (pageIndex < 5) {
    TransformData(px = -0.94f, py = 1.56f, pz = -2.08f, scale = 0.48f)
} else {
    TransformData(px = -0.96f, py = 1.42f, pz = -2.02f, scale = 0.42f)
}

private fun newDinosaurExampleId(): String = "s" + UUID.randomUUID().toString().replace("-", "")

private fun copyId(pageIndex: Int) = "dinosaur_copy_${pageIndex + 1}"
