package com.example.showme3d.studio.ui

import com.pico.spatial.core.math.Vector3

data class PanelPosition(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    fun toVector3(): Vector3 = Vector3(x, y, z)

    operator fun plus(other: PanelPosition): PanelPosition =
        PanelPosition(x + other.x, y + other.y, z + other.z)
}
