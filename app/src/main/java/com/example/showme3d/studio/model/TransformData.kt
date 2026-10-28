package com.example.showme3d.studio.model

/**
 * 资源在某一页的变换。注意：Stage 为右手系、+Z 朝向用户，
 * 因此“用户前方”对应 pz 取负值。
 */
data class TransformData(
    val px: Float = 0f,
    val py: Float = 0f,
    val pz: Float = 0f,
    /** 仅 Y 轴旋转（弧度）。 */
    val rotationY: Float = 0f,
    /** 等比缩放，单边 ≤ 10m（在 ViewModel 层 clamp）。 */
    val scale: Float = 1f,
)
