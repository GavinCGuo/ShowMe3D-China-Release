package com.example.showme3d.studio.examples

import com.example.showme3d.studio.model.TransformData
import kotlin.math.abs
import kotlin.math.cos

internal data class AssemblyStep(val label: String, val parts: Set<String>)

internal val roverSteps = mapOf(
    "curiosity_page_4" to AssemblyStep("把左右悬挂装到车身两侧", setOf("suspension_left", "suspension_right")),
    "curiosity_page_5" to AssemblyStep("把六个轮子装到对应的悬挂上", setOf(
        "wheel_left_front", "wheel_left_middle", "wheel_left_rear",
        "wheel_right_front", "wheel_right_middle", "wheel_right_rear",
    )),
    "curiosity_page_6" to AssemblyStep("将相机桅杆插入车顶", setOf("camera_mast")),
    "curiosity_page_7" to AssemblyStep("将机械臂接到车头", setOf("robotic_arm")),
)

internal fun TransformData.fitsSocket(target: TransformData): Boolean {
    val dx = px - target.px
    val dy = py - target.py
    val dz = pz - target.pz
    return dx * dx + dy * dy + dz * dz <= 0.10f * 0.10f &&
        cos(rotationY - target.rotationY) >= 0.94f &&
        abs(scale - target.scale) <= target.scale * 0.15f
}

internal data class FossilQuestion(
    val prompt: String,
    val options: List<String>,
    val answer: Int,
    val clue: String,
    val finding: String,
)

internal val fossilQuestions = mapOf(
    "dinosaur_page_2" to FossilQuestion("把牙齿转到侧面，它更适合做什么？", listOf("刺入并抓牢", "磨碎谷粒"), 0,
        "看齿尖的弧度，再找找有没有宽平的磨面。", "弯曲的齿尖能帮助抓牢猎物。"),
    "dinosaur_page_3" to FossilQuestion("只凭眼前这枚脚印，能直接观察什么？", listOf("整段路的步幅", "趾印的形状"), 1,
        "步幅需要比较连续的落脚点。", "趾印保留了脚与地面接触时的形状。"),
    "dinosaur_page_4" to FossilQuestion("放大石板，骨骼周围还能找见什么？", listOf("羽毛的轮廓", "活体的肤色"), 0,
        "沿着翅膀和尾部向外看。", "这块始祖鸟化石保留了羽毛的印痕。"),
    "dinosaur_page_5" to FossilQuestion("把内模转向扫描图，它复原的是什么？", listOf("头部皮肤", "头骨内的空腔"), 1,
        "想象往头骨内部的空间里倒入石膏。", "内模记录了脑腔的大致形状。"),
)
