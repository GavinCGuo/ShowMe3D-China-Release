package com.example.showme3d.studio.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds a sphere specifically for 2:1 equirectangular panoramas.
 *
 * The longitude seam has duplicated vertices (`u=0` and `u=1`) so UVs never
 * interpolate across the whole texture. The seam sits on +Z, behind the user
 * when the normal workspace is in front at -Z; the image center faces -Z.
 */
internal fun buildEquirectangularSkyboxMesh(
    radius: Float,
    longitudeSegments: Int = 128,
    latitudeSegments: Int = 64,
): EquirectangularSkyboxMesh {
    require(radius > 0f)
    require(longitudeSegments >= 8)
    require(latitudeSegments >= 4)

    val positions = ArrayList<Vector3>((longitudeSegments + 1) * (latitudeSegments + 1))
    val normals = ArrayList<Vector3>(positions.size)
    val uv0 = ArrayList<Vector2>(positions.size)
    val indices = ArrayList<Int>(longitudeSegments * latitudeSegments * 6)

    for (latitude in 0..latitudeSegments) {
        val v = latitude.toFloat() / latitudeSegments
        val polar = v * PI.toFloat()
        val y = cos(polar)
        val ringRadius = sin(polar)
        for (longitude in 0..longitudeSegments) {
            val u = longitude.toFloat() / longitudeSegments
            val azimuth = (u - 0.5f) * 2f * PI.toFloat()
            val normal = Vector3(
                sin(azimuth) * ringRadius,
                y,
                -cos(azimuth) * ringRadius,
            )
            positions += normal * radius
            normals += normal
            // Bitmap textures are sampled with v=0 at the mesh texture bottom on
            // device, so the panorama zenith must use v=1 and the nadir v=0.
            uv0 += Vector2(u, 1f - v)
        }
    }

    val rowSize = longitudeSegments + 1
    for (latitude in 0 until latitudeSegments) {
        for (longitude in 0 until longitudeSegments) {
            val a = latitude * rowSize + longitude
            val b = a + 1
            val d = (latitude + 1) * rowSize + longitude
            val c = d + 1
            // Outward winding. The skybox material culls front faces so the
            // same mesh is rendered from inside without mirroring the UVs.
            indices += a
            indices += b
            indices += c
            indices += a
            indices += c
            indices += d
        }
    }

    return EquirectangularSkyboxMesh(
        model = MeshModel(
            positions = positions,
            triangleIndices = indices,
            normals = normals,
            uv0 = uv0,
        ),
        bounds = BoundingBox(Vector3(0f), Vector3(radius)),
    )
}

internal data class EquirectangularSkyboxMesh(
    val model: MeshModel,
    val bounds: BoundingBox,
) {
    fun createResource(): MeshResource =
        MeshResource.createWithMeshModel(
            model = model,
            bounds = bounds,
            name = "showme3d_equirectangular_skybox",
        )
}
