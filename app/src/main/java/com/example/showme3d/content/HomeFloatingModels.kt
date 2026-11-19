package com.example.showme3d.content

import android.content.Context
import android.util.Log
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import com.example.showme3d.studio.interaction.ModelGrabSession
import com.example.showme3d.studio.interaction.modelGrabInput
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.example.showme3d.studio.ui.PanelPosition
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * D2 的首轮真机校准器。
 *
 * 模型的原始单位和朝向都不可信，因此先按包围盒归一化到可把玩的尺寸。在校准构建中，
 * 双手捏住模型即可同时移动、旋转和缩放；每次松手都会把最终 Transform 写到 logcat。
 * 用户验收布局后，再将日志中的数据回填为正式默认值，并开启浮动 / LookAt / 回位。
 */
class HomeFloatingModels(private val content: SpatialViewContent) {
    internal var trackedGrab: TrackedHomeGrab? = null
    private val roots = linkedMapOf<String, Entity>()
    private val interactionSizes = mutableMapOf<String, Vector3>()
    private val animationControllers = mutableListOf<AnimationPlaybackController>()
    private val yawDegrees = mutableMapOf<String, Float>()
    private val lastTouchedNanos = mutableMapOf<String, Long>()
    private val appearStartNanos = mutableMapOf<String, Long>()
    private val visibleStates = mutableMapOf<String, Boolean>()
    private val visibleRequestStartNanos = mutableMapOf<String, Long>()
    private val motionUpdateNanos = mutableMapOf<String, Long>()
    private val manipulationTargets = mutableMapOf<String, HomeFloatingTransform>()
    private val manipulationUpdateNanos = mutableMapOf<String, Long>()
    private val riftTransitionBaseScales = mutableMapOf<String, Float>()
    private var lastHomePosition: PanelPosition? = null
    private var lastDecorUpdateNanos = 0L

    suspend fun load() {
        HomeFloatingModelSpecs.forEach { spec ->
            val root = Entity().apply { setName("home_float_${spec.id}") }
            val model = Entity.loadSuspend(spec.assetPath).apply { setName("home_float_${spec.id}_model") }
            root.addChild(model)

            val bounds = model.getVisualBounds(model, recursive = true, enabledOnly = false)
            val longestSide = max(bounds.size.x, max(bounds.size.y, bounds.size.z))
            val scale = if (longestSide.isFinite() && longestSide > 0.0001f) {
                spec.targetSizeMeters / longestSide
            } else {
                1f
            }
            model.components.get(TransformComponent::class.java)?.apply {
                setPosition(Vector3(-bounds.center.x * scale, -bounds.center.y * scale, -bounds.center.z * scale))
                setScaleVector(Vector3(scale))
            }
            val correctedBounds = recenterModelRootToActualBounds(
                modelRoot = model,
                wrapper = root,
                currentPosition = Vector3(-bounds.center.x * scale, -bounds.center.y * scale, -bounds.center.z * scale),
            )
            val interactionSize = correctedBounds.size.takeIf { it.hasUsableSize() }
                ?: Vector3(spec.targetSizeMeters)
            interactionSizes[spec.id] = interactionSize
            addModelInteractions(root, interactionSize)
            startEmbeddedAnimations(model)
            yawDegrees.remove(spec.id)
            root.enabled = false
            visibleStates[spec.id] = false
            appearStartNanos[spec.id] = System.nanoTime()
            roots[spec.id] = root
            content.addEntity(root)
            yield()
        }
    }

    fun update(
        homePosition: PanelPosition,
        visible: Boolean,
        viewer: Vector3?,
        frameTimeSeconds: Float,
        followHomeOnly: Boolean = false,
    ) {
        val previousHome = lastHomePosition
        val homeDelta = previousHome?.let {
            Vector3(
                homePosition.x - it.x,
                homePosition.y - it.y,
                homePosition.z - it.z,
            )
        }
        val homeMoved = homeDelta?.let {
            abs(it.x) > 0.000001f || abs(it.y) > 0.000001f || abs(it.z) > 0.000001f
        } == true
        val nowNanos = System.nanoTime()
        val decorUpdateDue = previousHome == null ||
            homeMoved ||
            nowNanos - lastDecorUpdateNanos >= DECOR_UPDATE_INTERVAL_NANOS
        HomeFloatingModelSpecs.forEach { spec ->
            val root = roots[spec.id] ?: return@forEach
            if (!visible) {
                if (visibleStates[spec.id] == true) {
                    root.enabled = false
                    visibleStates[spec.id] = false
                }
                visibleRequestStartNanos.remove(spec.id)
                motionUpdateNanos.remove(spec.id)
                manipulationTargets.remove(spec.id)
                manipulationUpdateNanos.remove(spec.id)
                return@forEach
            }
            if (visibleStates[spec.id] != true) {
                val requestStart = visibleRequestStartNanos.getOrPut(spec.id) { nowNanos }
                val appearDelay = appearDelayFor(spec.id)
                if (nowNanos - requestStart < appearDelay) return@forEach
                root.enabled = true
                visibleStates[spec.id] = true
                appearStartNanos[spec.id] = nowNanos
                motionUpdateNanos.remove(spec.id)
            }
            // Skip SDK component queries between decor ticks; active gestures keep their frame cadence.
            if (!decorUpdateDue && spec.id !in manipulationTargets && spec.id !in motionUpdateNanos) return@forEach
            val transform = root.components.get(TransformComponent::class.java) ?: return@forEach
            if (previousHome != null && followHomeOnly) {
                if (homeMoved) {
                    val delta = requireNotNull(homeDelta)
                    val position = transform.position
                    transform.setPosition(
                        Vector3(
                            position.x + delta.x,
                            position.y + delta.y,
                            position.z + delta.z,
                        )
                    )
                }
                motionUpdateNanos.remove(spec.id)
                return@forEach
            }
            val manipulationTarget = manipulationTargets[spec.id]
            if (manipulationTarget != null) {
                applyManipulationTarget(spec.id, transform, manipulationTarget, nowNanos)
                return@forEach
            }
            val targetPosition = defaultPosition(homePosition, spec, frameTimeSeconds)
            val targetScale = spec.defaultScale * appearanceScaleFor(spec.id, nowNanos)
            val returningToHome = shouldReturnToHome(spec.id, nowNanos, transform, targetPosition, targetScale)
            val appearing = appearanceProgressFor(spec.id, nowNanos) < 1f
            val motionUpdateDue = returningToHome || appearing
            if (!decorUpdateDue && !motionUpdateDue) return@forEach
            if (previousHome == null) {
                transform.setPosition(targetPosition)
                transform.setScaleVector(Vector3(targetScale))
                applyDefaultRotation(spec, transform, transform.position, viewer)
            } else if (homeMoved) {
                val delta = requireNotNull(homeDelta)
                val position = transform.position
                transform.setPosition(
                    Vector3(
                        position.x + delta.x,
                        position.y + delta.y,
                        position.z + delta.z,
                    )
                )
            }
            val idleNanos = nowNanos - (lastTouchedNanos[spec.id] ?: 0L)
            if (idleNanos >= RETURN_DELAY_NANOS) {
                val current = transform.position
                val t = if (motionUpdateDue) {
                    elapsedLerpFactor(spec.id, nowNanos, RETURN_LERP_PER_FRAME)
                } else {
                    RETURN_LERP_PER_FRAME
                }
                transform.setPosition(
                    Vector3(
                        current.x + (targetPosition.x - current.x) * t,
                        current.y + (targetPosition.y - current.y) * t,
                        current.z + (targetPosition.z - current.z) * t,
                    )
                )
                val currentScale = transform.scaleVector.x
                if (currentScale != targetScale) {
                    val scale = if (abs(currentScale - targetScale) < SCALE_SETTLE_EPSILON) targetScale else currentScale + (targetScale - currentScale) * t
                    transform.setScaleVector(Vector3(scale))
                }
                applyDefaultRotation(spec, transform, targetPosition, viewer)
                if (!motionUpdateDue) motionUpdateNanos.remove(spec.id)
            } else {
                val currentScale = transform.scaleVector.x
                if (appearing && currentScale < targetScale) {
                    val t = if (motionUpdateDue) {
                        elapsedLerpFactor(spec.id, nowNanos, APPEAR_LERP_PER_FRAME)
                    } else {
                        APPEAR_LERP_PER_FRAME
                    }
                    transform.setScaleVector(Vector3(currentScale + (targetScale - currentScale) * t))
                }
                if (!motionUpdateDue) motionUpdateNanos.remove(spec.id)
            }
        }
        if (visible && !followHomeOnly && decorUpdateDue) {
            lastDecorUpdateNanos = nowNanos
        }
        lastHomePosition = homePosition
    }

    fun applyRiftTransitionScale(progress: Float) {
        if (progress >= 0.999f && riftTransitionBaseScales.isEmpty()) return
        val safeProgress = progress.coerceIn(0f, 1f)
        HomeFloatingModelSpecs.forEach { spec ->
            val root = roots[spec.id] ?: return@forEach
            val transform = root.components.get(TransformComponent::class.java) ?: return@forEach
            if (safeProgress < 0.999f) {
                val baseScale = riftTransitionBaseScales.getOrPut(spec.id) { transform.scaleVector.x }
                transform.setScaleVector(Vector3(baseScale * safeProgress.coerceAtLeast(0.01f)))
                root.enabled = safeProgress > 0.02f
            } else {
                riftTransitionBaseScales.remove(spec.id)?.let { baseScale ->
                    transform.setScaleVector(Vector3(baseScale))
                }
                root.enabled = visibleStates[spec.id] == true
            }
        }
    }

    fun owns(entity: Entity?): Boolean {
        var current = entity
        repeat(MAX_ENTITY_PARENT_SEARCH_DEPTH) {
            if (roots.values.any { it == current }) return true
            current = runCatching { current?.getParent() }.getOrNull()
        }
        return false
    }

    fun entityFor(id: String?): Entity? =
        id?.let(roots::get)

    fun currentTransform(id: String?): HomeFloatingTransform? {
        val root = entityFor(id) ?: return null
        val transform = root.components.get(TransformComponent::class.java) ?: return null
        return HomeFloatingTransform(transform.position, transform.scaleVector.x, yawDegrees[id] ?: 0f)
    }

    fun pushTransform(id: String?, next: HomeFloatingTransform) {
        val root = entityFor(id) ?: return
        val transform = root.components.get(TransformComponent::class.java) ?: return
        manipulationTargets.remove(id)
        manipulationUpdateNanos.remove(id)
        transform.setPosition(next.position)
        transform.setScaleVector(Vector3(next.scale))
        applyYaw(requireNotNull(id), transform, next.yawDegrees)
        markUserInteraction(id)
    }

    fun previewTransform(id: String?, next: HomeFloatingTransform) {
        val safeId = id ?: return
        manipulationTargets[safeId] = next
        markUserInteraction(safeId)
    }

    fun finishPreviewTransform(id: String?, next: HomeFloatingTransform) {
        pushTransform(id, next)
    }

    internal fun viewPointInHome(point: Vector3, space: ViewCoordinateSpace): Vector3 =
        content.convertPosition(point, space, content.localSpatialCoordinateSpace)

    private fun applyYaw(id: String, transform: TransformComponent, yaw: Float) {
        yawDegrees[id] = yaw
        transform.setEulerAngles(EulerAngles(0f, yaw, 0f))
    }

    fun idFor(entity: Entity?): String? = rootFor(entity)?.let { root ->
        roots.entries.firstOrNull { it.value == root }?.key
    }

    /**
     * Reserves a two-hand gesture for a floating model before the SDK scale recognizer starts.
     *
     * HMD tracking and the model roots both use container space. The ray is converted into each
     * root's local space so the test follows the same oriented box used by CollisionComponent,
     * including the model's current translation, rotation and scale.
     */
    fun viewerTargetedModel(viewerPosition: Vector3, viewerRotation: Quat): String? {
        val forward = viewerRotation.rotateVector(HOME_GAZE_FORWARD)
        if (!forward.isUsableDirection()) return null
        val worldDirection = forward.normalize()
        val worldRayEnd = viewerPosition + worldDirection
        return roots.entries.firstOrNull { (id, root) ->
            if (!root.enabled || visibleStates[id] != true) return@firstOrNull false
            val localOrigin = runCatching { root.convertPositionFrom(viewerPosition, null) }.getOrNull()
                ?.takeIf { it.isFiniteVector() }
                ?: return@firstOrNull false
            val localRayEnd = runCatching { root.convertPositionFrom(worldRayEnd, null) }.getOrNull()
                ?.takeIf { it.isFiniteVector() }
                ?: return@firstOrNull false
            val localDirection = localRayEnd - localOrigin
            if (!localDirection.isUsableDirection()) return@firstOrNull false
            val size = interactionSizes[id] ?: return@firstOrNull false
            val scale = root.components.get(TransformComponent::class.java)
                ?.scaleVector
                ?.x
                ?.let(::abs)
                ?.takeIf { it > GAZE_SCALE_EPSILON }
                ?: 1f
            val padding = HOME_GAZE_HIT_PADDING_METERS / scale
            val halfExtents = Vector3(
                size.x * 0.5f + padding,
                size.y * 0.5f + padding,
                size.z * 0.5f + padding,
            )
            rayIntersectsCenteredBox(localOrigin, localDirection.normalize(), halfExtents)
        }?.key
    }

    fun logCalibrationSnapshot() {
        roots.forEach { (id, root) ->
            val transform = root.components.get(TransformComponent::class.java) ?: return@forEach
            Log.i(TAG, "D2_CALIBRATION id=$id position=${transform.position} scale=${transform.scaleVector} quaternion=${transform.quaternion}")
        }
    }

    private fun rootFor(entity: Entity?): Entity? {
        var current = entity
        repeat(MAX_ENTITY_PARENT_SEARCH_DEPTH) {
            if (roots.values.any { it == current }) return current
            current = runCatching { current?.getParent() }.getOrNull()
        }
        return null
    }

    private fun markUserInteraction(id: String?) {
        if (id != null) lastTouchedNanos[id] = System.nanoTime()
    }

    private fun defaultPosition(homePosition: PanelPosition, spec: HomeFloatingModelSpec, frameTimeSeconds: Float): Vector3 {
        val bob = sin(frameTimeSeconds * FLOAT_SPEED + spec.floatPhase) * FLOAT_AMPLITUDE_METERS
        return Vector3(
            homePosition.x + spec.offset.x,
            homePosition.y + spec.offset.y + bob,
            homePosition.z + spec.offset.z,
        )
    }

    private fun applyDefaultRotation(
        spec: HomeFloatingModelSpec,
        transform: TransformComponent,
        position: Vector3,
        viewer: Vector3?,
    ) {
        val yaw = defaultYawDegreesFor(spec, position, viewer)
        if (abs(yaw - (yawDegrees[spec.id] ?: Float.POSITIVE_INFINITY)) < 0.12f) return
        yawDegrees[spec.id] = yaw
        transform.setEulerAngles(EulerAngles(0f, yaw, 0f))
    }

    private fun defaultYawDegreesFor(spec: HomeFloatingModelSpec, position: Vector3, viewer: Vector3?): Float {
        val target = viewer ?: return spec.defaultYawDegrees
        val dx = target.x - position.x
        val dz = target.z - position.z
        val horizontalDistance = sqrt(dx * dx + dz * dz)
        if (!horizontalDistance.isFinite() || horizontalDistance < 0.0001f) {
            return spec.defaultYawDegrees
        }
        val facingYaw = Math.toDegrees(atan2(dx, dz).toDouble()).toFloat()
        return facingYaw + spec.defaultYawDegrees
    }

    private fun appearanceScaleFor(id: String, nowNanos: Long): Float {
        val progress = appearanceProgressFor(id, nowNanos)
        if (progress >= 1f) return 1f
        val eased = easeOutCubic(progress)
        return APPEAR_INITIAL_SCALE + (1f - APPEAR_INITIAL_SCALE) * eased
    }

    private fun appearanceProgressFor(id: String, nowNanos: Long): Float {
        val start = appearStartNanos[id] ?: return 1f
        return ((nowNanos - start).toFloat() / APPEAR_DURATION_NANOS).coerceIn(0f, 1f)
    }

    private fun easeOutCubic(progress: Float): Float {
        val inverse = 1f - progress.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    private fun appearDelayFor(id: String): Long {
        val index = HomeFloatingModelSpecs.indexOfFirst { it.id == id }.coerceAtLeast(0)
        return index * APPEAR_STAGGER_NANOS
    }

    private fun shouldReturnToHome(
        id: String,
        nowNanos: Long,
        transform: TransformComponent,
        targetPosition: Vector3,
        targetScale: Float,
    ): Boolean {
        val idleNanos = nowNanos - (lastTouchedNanos[id] ?: 0L)
        if (idleNanos < RETURN_DELAY_NANOS) return false
        val current = transform.position
        return abs(current.x - targetPosition.x) > POSITION_SETTLE_EPSILON ||
            abs(current.y - targetPosition.y) > POSITION_SETTLE_EPSILON ||
            abs(current.z - targetPosition.z) > POSITION_SETTLE_EPSILON ||
            abs(transform.scaleVector.x - targetScale) > SCALE_SETTLE_EPSILON
    }

    private fun elapsedLerpFactor(id: String, nowNanos: Long, baseFactor: Float): Float {
        return elapsedLerpFactor(id, nowNanos, baseFactor, motionUpdateNanos)
    }

    private fun elapsedLerpFactor(
        id: String,
        nowNanos: Long,
        baseFactor: Float,
        updateTimes: MutableMap<String, Long>,
    ): Float {
        val previous = updateTimes[id] ?: (nowNanos - DECOR_UPDATE_INTERVAL_NANOS)
        updateTimes[id] = nowNanos
        val intervalCount = ((nowNanos - previous).coerceAtLeast(0L).toFloat() / DECOR_UPDATE_INTERVAL_NANOS)
            .coerceIn(0f, 1f)
        return (1f - Math.pow((1f - baseFactor).toDouble(), intervalCount.toDouble())).toFloat()
    }

    private fun applyManipulationTarget(
        id: String,
        transform: TransformComponent,
        target: HomeFloatingTransform,
        nowNanos: Long,
    ) {
        val t = elapsedLerpFactor(id, nowNanos, MANIPULATION_LERP_PER_FRAME, manipulationUpdateNanos)
        val current = transform.position
        transform.setPosition(
            Vector3(
                current.x + (target.position.x - current.x) * t,
                current.y + (target.position.y - current.y) * t,
                current.z + (target.position.z - current.z) * t,
            )
        )
        val currentScale = transform.scaleVector.x
        transform.setScaleVector(Vector3(currentScale + (target.scale - currentScale) * t))
        applyYaw(id, transform, target.yawDegrees)
    }

    private fun recenterModelRootToActualBounds(
        modelRoot: Entity,
        wrapper: Entity,
        currentPosition: Vector3,
    ): com.pico.spatial.core.ecs.BoundingBox {
        val actualBounds = wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
        val actualCenter = actualBounds.center
        if (actualBounds.size.hasUsableSize()) {
            modelRoot.components.get(TransformComponent::class.java)?.apply {
                setPosition(
                    Vector3(
                        currentPosition.x - actualCenter.x,
                        currentPosition.y - actualCenter.y,
                        currentPosition.z - actualCenter.z,
                    )
                )
            }
        }
        return wrapper.getVisualBounds(wrapper, recursive = true, enabledOnly = false)
    }

    /** Lightweight box hit area: keeps D2 manipulation responsive without per-mesh convex colliders. */
    private fun addModelInteractions(wrapper: Entity, fallbackSize: Vector3) {
        addFallbackInteraction(wrapper, fallbackSize)
        Log.i(TAG, "Added lightweight box interaction for ${wrapper.getName()}")
    }

    private fun addFallbackInteraction(entity: Entity, size: Vector3) {
        entity.components.set(CollisionComponent(listOf(ShapeResource.createBox(size)), PhysicsMaterialResource()))
        entity.components.set(InteractableComponent())
    }

    private fun startEmbeddedAnimations(modelRoot: Entity) {
        modelRoot.findSkinnedMeshEntity().forEach { skinnedMeshEntity ->
            runCatching {
                skinnedMeshEntity.getAnimationResources().firstOrNull()?.use { animation ->
                    animationControllers += skinnedMeshEntity.playAnimation(animation)
                }
            }.onFailure { Log.w(TAG, "Unable to start home model animation", it) }
        }
    }

    companion object {
        private const val TAG = "HomeFloatingModels"
        private const val MAX_ENTITY_PARENT_SEARCH_DEPTH = 32
        private const val RETURN_DELAY_NANOS = 3_000_000_000L
        private const val RETURN_LERP_PER_FRAME = 0.075f
        private const val APPEAR_LERP_PER_FRAME = 0.22f
        private const val MANIPULATION_LERP_PER_FRAME = 0.72f
        private const val APPEAR_DURATION_NANOS = 260_000_000f
        private const val APPEAR_INITIAL_SCALE = 0.72f
        private const val APPEAR_STAGGER_NANOS = 75_000_000L
        private const val DECOR_UPDATE_INTERVAL_NANOS = 33_333_333L
        private const val POSITION_SETTLE_EPSILON = 0.002f
        private const val SCALE_SETTLE_EPSILON = 0.002f
        private const val FLOAT_AMPLITUDE_METERS = 0.018f
        private const val FLOAT_SPEED = 1.15f
        private const val HOME_GAZE_HIT_PADDING_METERS = 0.035f
        private const val GAZE_DIRECTION_EPSILON = 0.0001f
        private const val GAZE_SCALE_EPSILON = 0.0001f
        private val HOME_GAZE_FORWARD = Vector3(0f, 0f, -1f)

        private fun rayIntersectsCenteredBox(origin: Vector3, direction: Vector3, halfExtents: Vector3): Boolean {
            var near = 0f
            var far = Float.POSITIVE_INFINITY
            fun intersectAxis(axisOrigin: Float, axisDirection: Float, halfExtent: Float): Boolean {
                if (abs(axisDirection) <= GAZE_DIRECTION_EPSILON) {
                    return axisOrigin in -halfExtent..halfExtent
                }
                val first = (-halfExtent - axisOrigin) / axisDirection
                val second = (halfExtent - axisOrigin) / axisDirection
                val axisNear = minOf(first, second)
                val axisFar = maxOf(first, second)
                near = maxOf(near, axisNear)
                far = minOf(far, axisFar)
                return near <= far
            }
            return intersectAxis(origin.x, direction.x, halfExtents.x) &&
                intersectAxis(origin.y, direction.y, halfExtents.y) &&
                intersectAxis(origin.z, direction.z, halfExtents.z) &&
                far >= 0f
        }

        private fun Vector3.isUsableDirection(): Boolean = isFiniteVector() && length() > GAZE_DIRECTION_EPSILON

        private fun Vector3.isFiniteVector(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
    }
}

data class HomeFloatingTransform(
    val position: Vector3,
    val scale: Float,
    val yawDegrees: Float,
)

fun Modifier.homeFloatingModelManipulatable(
    context: Context,
    models: () -> HomeFloatingModels?,
    onTargetPointerActiveChanged: (Boolean) -> Unit,
    onManipulationActiveChanged: (Boolean) -> Unit,
): Modifier {
    val controller = models() ?: return this
    return composed {
        val activity = rememberUpdatedState(onManipulationActiveChanged)
        val session = remember(controller) {
            ModelGrabSession(
                read = { id -> controller.currentTransform(id)?.also { controller.previewTransform(id, it) } },
                scaleOf = HomeFloatingTransform::scale,
                transform = { pose, delta, scale, yaw ->
                    HomeFloatingTransform(pose.position + delta, scale, pose.yawDegrees + Math.toDegrees(yaw.toDouble()).toFloat())
                },
                preview = controller::previewTransform,
                release = { id, pose, cancelled ->
                    controller.finishPreviewTransform(id, pose)
                    if (!cancelled) controller.logCalibrationSnapshot()
                },
                activeChanged = { activity.value(it) },
            )
        }
        val pointerActivity = rememberUpdatedState(onTargetPointerActiveChanged)
        val tracked = remember(session) { TrackedHomeGrab(session) }
        DisposableEffect(controller, tracked) {
            controller.trackedGrab = tracked
            onDispose {
                tracked.finish(cancelled = true)
                controller.trackedGrab = null
            }
        }
        modelGrabInput(context, session, controller::idFor, controller::viewPointInHome,
            trackedActive = { tracked.active }, trackedGeneration = { tracked.generation }) { target ->
            tracked.pointerTarget = target
            pointerActivity.value(target != null)
        }
    }
}

private fun Vector3.hasUsableSize(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite() && max(x, max(y, z)) > 0.0001f

private data class HomeFloatingModelSpec(
    val id: String,
    val assetPath: String,
    val offset: PanelPosition,
    val targetSizeMeters: Float,
    val defaultScale: Float,
    val defaultYawDegrees: Float,
    val floatPhase: Float,
)

private val HomeFloatingModelSpecs = listOf(
    HomeFloatingModelSpec("after_effects", "asset://home_floating/after_effects_ui.glb", PanelPosition(-0.24156973f, 0.7039557f, 0.0109866f), 0.30f, 1.4882141f, 22.5f, 0.0f),
    HomeFloatingModelSpec("bike", "asset://home_floating/cyberpunk_bike.glb", PanelPosition(-0.92407155f, -0.1710481f, 0.0526460f), 0.38f, 2.4480958f, 66.667f, 0.8f),
    HomeFloatingModelSpec("croissant", "asset://home_floating/low_poly_croissant_on_paper.glb", PanelPosition(0.4826742f, -0.59434f, 0.1452006f), 0.26f, 2.048121f, 0f, 1.6f),
    HomeFloatingModelSpec("drone", "asset://home_floating/quadcopter-drone-animated.glb", PanelPosition(-0.9342763f, 0.3592607f, 0.0949327f), 0.32f, 1.4963349f, 33.333f, 2.4f),
    HomeFloatingModelSpec("terrarium", "asset://home_floating/smol_ame_in_an_upcycled_terrarium_hololiveen.glb", PanelPosition(0.94048065f, -0.1773268f, 0.2265413f), 0.32f, 1.3722707f, -56.667f, 3.2f),
    HomeFloatingModelSpec("camera", "asset://home_floating/sony_alpha_3.glb", PanelPosition(-0.35120496f, -0.5537114f, 0.2615302f), 0.32f, 1.0f, 0f, 4.0f),
    HomeFloatingModelSpec("walkman", "asset://home_floating/sony_wolkman_mk90.glb", PanelPosition(0.32800418f, 0.6694992f, -0.0847761f), 0.28f, 1.0f, 97.5f, 4.8f),
    HomeFloatingModelSpec("diorama", "asset://home_floating/stylised_sky_player_home_dioroma.glb", PanelPosition(0.92783827f, 0.6136984f, 0.0650205f), 0.36f, 2.6439342f, -76.667f, 5.6f),
)
