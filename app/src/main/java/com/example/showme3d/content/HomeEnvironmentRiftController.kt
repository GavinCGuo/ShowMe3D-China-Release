package com.example.showme3d.content

import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.annotation.ExperimentalSpatialApi
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.PortalComponent
import com.pico.spatial.core.ecs.PortalWorldComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.portal.PortalBackgroundMode
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PortalMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 首页环境切换的临时空间裂缝。
 *
 * 裂缝是独立于 [SceneController] 的短生命周期 ECS 树：正式环境仍只由
 * StudioViewModel.sceneEnvironmentMode + SceneController.syncEnvironmentMode() 管理。
 * Portal 在 Mixed Stage 的表现以真机为准；若 Portal 创建失败，仍保留不规则裂缝
 * 边缘，使手势反馈可见，但不使用假的目标环境预览。
 */
@OptIn(ExperimentalSpatialApi::class)
class HomeEnvironmentRiftController(
    private val content: SpatialViewContent,
) {
    private val spaceAnchor = Entity().apply {
        setName("home_environment_rift_space_anchor")
    }
    private val root = Entity().apply {
        setName("home_environment_rift_root")
        enabled = false
    }
    private val virtualPortalWorld = Entity().apply {
        setName("home_environment_rift_virtual_portal_world")
        components.set(PortalWorldComponent())
    }
    private val passthroughPortalWorld = Entity().apply {
        setName("home_environment_rift_passthrough_portal_world")
        components.set(PortalWorldComponent())
    }
    private var portalSurface: Entity? = null
    private var edgeSurface: Entity? = null
    private var virtualEnvironmentPreview: Entity? = null
    private var portalAvailable = false
    private var prepared = false
    private var destroyed = false
    private var shown = false

    init {
        content.addEntity(virtualPortalWorld)
        content.addEntity(passthroughPortalWorld)
        content.addEntity(spaceAnchor)
        spaceAnchor.addChild(root)
    }

    /** One-time mesh/material setup. Safe to call repeatedly from the homepage lifecycle. */
    fun prepare() {
        if (prepared || destroyed) return
        prepared = true
        try {
            val apertureMesh = createApertureMesh()
            val edgeMesh = createTornEdgeMesh()
            edgeSurface = ModelEntity(
                edgeMesh,
                UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                    setBaseColor(EDGE_COLOR)
                },
            ).apply {
                setName("home_environment_rift_torn_edge")
                transform().setPosition(Vector3(0f, 0f, EDGE_Z_OFFSET))
                root.addChild(this)
            }
            portalSurface = runCatching {
                ModelEntity(apertureMesh, PortalMaterial()).apply {
                    setName("home_environment_rift_portal")
                    components.set(
                        PortalComponent(
                            virtualPortalWorld,
                            true,
                            true,
                            true,
                            false,
                        ),
                    )
                    transform().setPosition(Vector3(0f, 0f, 0f))
                    root.addChild(this)
                }
            }.onFailure {
                Log.w(TAG, "Portal unavailable; keeping torn edge without a fake preview", it)
            }.getOrNull()
            portalAvailable = portalSurface != null
        } catch (error: Throwable) {
            Log.w(TAG, "Rift mesh setup failed", error)
            root.enabled = false
        }
    }

    fun setVirtualEnvironmentPreview(environmentClone: Entity) {
        if (destroyed) {
            runCatching { environmentClone.destroy(true) }
            return
        }
        runCatching {
            virtualPortalWorld.removeAllChildren()
            environmentClone.enabled = true
            virtualPortalWorld.addChild(environmentClone)
            virtualEnvironmentPreview = environmentClone
        }.onFailure { error ->
            Log.w(TAG, "Portal environment prewarm result arrived after lifecycle change", error)
            runCatching { environmentClone.destroy(true) }
        }
    }

    fun show(
        left: Vector3,
        right: Vector3,
        viewer: Vector3?,
        progress: Float,
        targetIsVirtual: Boolean,
        expansionProgress: Float = 0f,
    ) {
        if (destroyed) return
        prepare()
        val rootTransform = root.components.get(TransformComponent::class.java)
        if (rootTransform == null) {
            Log.e(TAG, "Rift root is no longer valid; ignore show instead of crashing")
            return
        }
        val safeProgress = progress.coerceIn(0f, 1f)
        // Hand joints arrive in tracking/world space. The rift lives under a SpatialView entity,
        // so convert all points into the stable anchor's local space before deriving its pose.
        val localLeft = spaceAnchor.convertPositionFrom(left, null)
        val localRight = spaceAnchor.convertPositionFrom(right, null)
        val localViewer = viewer?.let { spaceAnchor.convertPositionFrom(it, null) }
        val center = localLeft.midpoint(localRight)
        val handVector = localRight - localLeft
        val handSpan = handVector.length().coerceAtLeast(MIN_RIFT_WIDTH)
        val xAxis = handVector.normalize()
        val towardViewer = localViewer
            ?.minus(center)
            ?.normalize()
            ?: Vector3.FORWARD
        val zAxis = orthogonalized(towardViewer, xAxis)
        val yAxis = Vector3.cross(zAxis, xAxis).normalize()
        val rotation = validatedQuaternionFromBasis(xAxis, yAxis, zAxis)
        val expansion = easeOutCubic(expansionProgress.coerceIn(0f, 1f))
        val normalHeight = lerp(MIN_RIFT_HEIGHT, MAX_RIFT_HEIGHT, safeProgress)
        val displayCenter = localViewer?.let { pose ->
            Vector3(
                center.x,
                lerp(center.y, pose.y, expansion * COMMIT_VIEWER_Y_BLEND),
                center.z,
            )
        } ?: center
        val firstShowFrame = !shown
        shown = true
        root.enabled = true
        rootTransform.apply {
            setPosition(displayCenter)
            setQuaternion(rotation)
            setScaleVector(
                Vector3(
                    lerp(handSpan, COMMIT_RIFT_WIDTH, expansion),
                    lerp(normalHeight, COMMIT_RIFT_HEIGHT, expansion),
                    1f,
                ),
            )
        }
        if (firstShowFrame) {
            val renderedLeft = center + rotation.rotateVector(Vector3(-handSpan * 0.5f, 0f, 0f))
            val renderedRight = center + rotation.rotateVector(Vector3(handSpan * 0.5f, 0f, 0f))
            Log.i(
                TAG,
                "show targetVirtual=$targetIsVirtual portal=$portalAvailable " +
                    "leftError=${Vector3.distance(renderedLeft, localLeft)} " +
                    "rightError=${Vector3.distance(renderedRight, localRight)} " +
                    "rawLeft=$left rawRight=$right localLeft=$localLeft localRight=$localRight",
            )
        }
        if (portalAvailable) {
            runCatching {
                portalSurface?.components?.get(PortalComponent::class.java)?.apply {
                    targetEntity = if (targetIsVirtual) virtualPortalWorld else passthroughPortalWorld
                    backgroundMode = if (targetIsVirtual) {
                        PortalBackgroundMode.SOLID_COLOR
                    } else {
                        PortalBackgroundMode.PASSTHROUGH
                    }
                    enable = true
                }
            }.onFailure { error ->
                Log.w(TAG, "Portal show failed; keeping torn edge without a fake preview", error)
                portalAvailable = false
                portalSurface?.enabled = false
            }
        }
        edgeSurface?.enabled = true
    }

    fun hide() {
        if (destroyed) return
        shown = false
        root.enabled = false
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        runCatching { spaceAnchor.destroy(true) }
        runCatching { virtualPortalWorld.destroy(true) }
        runCatching { passthroughPortalWorld.destroy(true) }
        portalSurface = null
        edgeSurface = null
        virtualEnvironmentPreview = null
    }

    private fun Entity.transform(): TransformComponent =
        requireNotNull(components.get(TransformComponent::class.java))

    private fun createApertureMesh(): MeshResource {
        val boundary = riftBoundary(OUTER_JITTER)
        val positions = mutableListOf(Vector3(0f, 0f, 0f))
        positions += boundary
        val indices = buildList {
            boundary.indices.forEach { index ->
                val next = ((index + 1) % boundary.size) + 1
                add(0)
                add(index + 1)
                add(next)
                // Portal/edge must remain visible if the runtime chooses the opposite face.
                add(0)
                add(next)
                add(index + 1)
            }
        }
        return MeshResource.createWithMeshModel(
            MeshModel(
                positions = positions,
                triangleIndices = indices,
                normals = List(positions.size) { Vector3.FORWARD },
            ),
            BoundingBox(Vector3(0f), Vector3(0.6f, 0.55f, 0.02f)),
            "home_environment_rift_aperture",
        )
    }

    private fun createTornEdgeMesh(): MeshResource {
        val inner = riftBoundary(INNER_JITTER)
        val outer = riftBoundary(OUTER_JITTER).map { Vector3(it.x, it.y * EDGE_EXPAND, EDGE_DEPTH) }
        val positions = inner + outer
        val indices = buildList {
            inner.indices.forEach { index ->
                val next = (index + 1) % inner.size
                add(index)
                add(inner.size + index)
                add(next)
                add(next)
                add(inner.size + index)
                add(inner.size + next)
                // Duplicate the ring triangles with reverse winding for two-sided visibility.
                add(index)
                add(next)
                add(inner.size + index)
                add(next)
                add(inner.size + next)
                add(inner.size + index)
            }
        }
        return MeshResource.createWithMeshModel(
            MeshModel(
                positions = positions,
                triangleIndices = indices,
                normals = List(positions.size) { Vector3.FORWARD },
            ),
            BoundingBox(Vector3(0f), Vector3(0.8f, 0.7f, 0.04f)),
            "home_environment_rift_edge",
        )
    }

    private fun riftBoundary(jitter: Float): List<Vector3> {
        fun point(index: Int, upper: Boolean): Vector3 {
            val t = index.toFloat() / RIFT_HALF_SEGMENTS
            val x = -0.5f + t
            // Zero height at both tips and a controlled belly in the middle. Power < 1 keeps
            // most of the opening slender while making the endpoints visibly sharp.
            val envelope = sin(PI.toFloat() * t).coerceAtLeast(0f).pow(RIFT_PROFILE_POWER)
            val seedIndex = if (upper) index else index + 31
            val irregularity = 1f + jitter * (
                sin(seedIndex * 2.17f) * 0.62f + cos(seedIndex * 4.71f) * 0.38f
            )
            val side = if (upper) 1f else -1f
            return Vector3(x, side * 0.5f * envelope * irregularity, 0f)
        }
        val upper = (0..RIFT_HALF_SEGMENTS).map { point(it, true) }
        val lower = (RIFT_HALF_SEGMENTS - 1 downTo 1).map { point(it, false) }
        return upper + lower
    }

    private fun orthogonalized(candidate: Vector3, axis: Vector3): Vector3 {
        val projected = candidate - axis * Vector3.dot(candidate, axis)
        if (projected.length() > BASIS_EPSILON) return projected.normalize()
        val fallback = if (kotlin.math.abs(Vector3.dot(axis, Vector3.UP)) < 0.9f) Vector3.UP else Vector3.FORWARD
        return (fallback - axis * Vector3.dot(fallback, axis)).normalize()
    }

    private fun validatedQuaternionFromBasis(xAxis: Vector3, yAxis: Vector3, zAxis: Vector3): Quat {
        val basis = quaternionFromBasis(xAxis, yAxis, zAxis)
        val direct = rotationBetween(Vector3.RIGHT, xAxis)
        val candidates = listOf(basis, basis.conjugate(), direct, direct.conjugate())
        return candidates.maxBy { candidate ->
            val rightScore = Vector3.dot(candidate.rotateVector(Vector3.RIGHT).normalize(), xAxis)
            val facingScore = Vector3.dot(candidate.rotateVector(Vector3.FORWARD).normalize(), zAxis)
            rightScore * ENDPOINT_ALIGNMENT_WEIGHT + facingScore
        }
    }

    private fun quaternionFromBasis(xAxis: Vector3, yAxis: Vector3, zAxis: Vector3): Quat {
        val m00 = xAxis.x
        val m01 = yAxis.x
        val m02 = zAxis.x
        val m10 = xAxis.y
        val m11 = yAxis.y
        val m12 = zAxis.y
        val m20 = xAxis.z
        val m21 = yAxis.z
        val m22 = zAxis.z
        val trace = m00 + m11 + m22
        return when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f
                Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                Quat(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                Quat((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                Quat((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
            }
        }.normalize()
    }

    private fun rotationBetween(from: Vector3, to: Vector3): Quat {
        val source = from.normalize()
        val target = to.normalize()
        val dot = Vector3.dot(source, target).coerceIn(-1f, 1f)
        if (dot > 1f - BASIS_EPSILON) return Quat.identity()
        if (dot < -1f + BASIS_EPSILON) {
            val axis = Vector3.cross(source, Vector3.UP).takeIf { it.length() > BASIS_EPSILON }
                ?: Vector3.cross(source, Vector3.FORWARD)
            return Quat(axis.normalize().x, axis.normalize().y, axis.normalize().z, 0f)
        }
        val cross = Vector3.cross(source, target)
        return Quat(cross.x, cross.y, cross.z, 1f + dot).normalize()
    }

    private fun Vector3.midpoint(other: Vector3): Vector3 =
        Vector3((x + other.x) * 0.5f, (y + other.y) * 0.5f, (z + other.z) * 0.5f)

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress

    private fun easeOutCubic(progress: Float): Float {
        val inverse = 1f - progress
        return 1f - inverse * inverse * inverse
    }

    private companion object {
        const val TAG = "ShowMe3D.HomeRift"
        const val RIFT_HALF_SEGMENTS = 16
        const val RIFT_PROFILE_POWER = 0.72f
        const val BASIS_EPSILON = 0.0001f
        const val ENDPOINT_ALIGNMENT_WEIGHT = 4f
        const val COMMIT_RIFT_WIDTH = 3.2f
        const val COMMIT_RIFT_HEIGHT = 2.2f
        const val COMMIT_VIEWER_Y_BLEND = 0.72f
        const val INNER_JITTER = 0.045f
        const val OUTER_JITTER = 0.14f
        const val EDGE_EXPAND = 1.14f
        const val EDGE_DEPTH = 0.008f
        const val EDGE_Z_OFFSET = 0.014f
        const val MIN_RIFT_WIDTH = 0.075f
        const val MIN_RIFT_HEIGHT = 0.055f
        const val MAX_RIFT_HEIGHT = 0.22f
        val EDGE_COLOR = Color4(0.46f, 0.67f, 1f, 1f)
    }
}
