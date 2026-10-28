package com.example.showme3d.studio.scene

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.showme3d.library.PresetGeometry
import com.example.showme3d.library.PresetLibrary
import com.example.showme3d.studio.model.ObjectColors
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.SceneObject
import com.example.showme3d.studio.model.StrokeData
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.LookAtComponent
import com.pico.spatial.core.ecs.LookAtForwardDirection
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/**
 * 按 [SceneObject.type] 创建可交互的 ECS 实体。
 *
 * 内置几何资源用 PICO [MeshResource] 创建；导入模型由 [SceneController] 异步加载。
 *
 * 每个实体都会补齐碰撞与交互组件：
 * - [CollisionComponent]（盒碰撞，提供可触碰的物理边界）
 * - [InteractableComponent]（开启交互响应）
 * - [HoverEffectComponent]（按需开启悬浮高亮；模型默认关闭，避免遮挡材质细节）
 *
 * 实体名设为 [SceneObject.id]，便于手势回调通过 targetEntity 反查数据层对象。
 */
object ObjectEntityFactory {

    /** 占位立方体单边边长（米）。实际大小由各页 Transform 的 scale 调整。 */
    const val BOX_SIZE = 0.45f
    const val IMPORTED_ASSET_MIN_SIDE = 0.3f
    const val IMPORTED_ASSET_MAX_SIDE = 0.8f
    const val IMPORTED_MODEL_INTERACTION_SIZE = IMPORTED_ASSET_MAX_SIDE
    const val CARD_WIDTH = 0.72f
    const val CARD_HEIGHT = 0.22f
    const val CARD_DEPTH = 0.025f
    private const val SPHERE_RADIUS = 0.24f
    private const val CYLINDER_RADIUS = 0.22f
    private const val CYLINDER_HEIGHT = 0.5f
    private const val CONE_RADIUS = 0.24f
    private const val CONE_HEIGHT = 0.5f
    private const val PLANE_WIDTH = 0.62f
    private const val PLANE_HEIGHT = 0.42f
    private const val VIEWPOINT_WIDTH = 0.145f
    private const val VIEWPOINT_HEIGHT = 0.30f
    private const val VIEWPOINT_DEPTH = 0.165f
    private const val VIEWPOINT_FALLBACK_SCALE = 0.51724136f

    fun createGeometryPreview(name: String, geometry: PresetGeometry): Entity {
        val material = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(ObjectColors.DEFAULT_GEOMETRY_COLOR.toColor4())
        }
        return ModelEntity(geometry.createMesh(), material).apply {
            setName(name)
        }
    }

    fun geometryPreviewSize(geometry: PresetGeometry): Vector3 =
        geometry.interactionSize()

    fun createColorSwatch(name: String, color: Long, size: Vector3 = Vector3(0.048f, 0.048f, 0.012f)): Entity {
        val material = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(color.toColor4())
        }
        return ModelEntity(MeshResource.createBox(size), material).apply {
            setName(name)
            addInteraction(this, size)
        }
    }

    fun create(obj: SceneObject): Entity = when (obj.type) {
        ObjectType.MODEL -> createModelLikeEntity(obj)
        ObjectType.VIEWPOINT -> createViewpoint(obj)
        ObjectType.IMAGE -> createImagePlane(obj)
        ObjectType.TEXT -> createTextPlane(obj, TextStyle.TEXT)
        ObjectType.STROKE -> createStrokePlaceholder(obj)
    }

    fun createPlaceholder(obj: SceneObject): Entity = createPlaceholderBox(obj)

    fun interactionSize(obj: SceneObject): Vector3 = when (obj.type) {
        ObjectType.IMAGE -> imagePlaneSize(obj).let { Vector3(it.width, it.height, INTERACTION_DEPTH) }
        ObjectType.TEXT -> textPlaneSize(obj.source.ifBlank { "新增文本" }, TextStyle.TEXT).let {
            Vector3(it.width, it.height, INTERACTION_DEPTH)
        }
        ObjectType.MODEL -> if (obj.source.startsWith("file://")) {
            Vector3(IMPORTED_MODEL_INTERACTION_SIZE, IMPORTED_MODEL_INTERACTION_SIZE, IMPORTED_MODEL_INTERACTION_SIZE)
        } else {
            PresetLibrary.geometryFromSource(obj.source)?.interactionSize()
                ?: Vector3(BOX_SIZE, BOX_SIZE, BOX_SIZE)
        }
        ObjectType.VIEWPOINT -> Vector3(VIEWPOINT_WIDTH, VIEWPOINT_HEIGHT, VIEWPOINT_DEPTH)
        ObjectType.STROKE -> strokeInteractionSize(obj.strokeData)
    }

    fun createCommentCard(name: String, text: String): Entity =
        createTextPlane(
            SceneObject(
                id = name,
                type = ObjectType.TEXT,
                source = text,
                displayName = text.take(12),
                textColor = COMMENT_TEXT_COLOR,
            ),
            TextStyle.COMMENT,
        )

    fun commentCardSize(text: String): Vector3 {
        val size = textPlaneSize(text, TextStyle.COMMENT)
        return Vector3(size.width, size.height, INTERACTION_DEPTH)
    }

    private fun createModelLikeEntity(obj: SceneObject): Entity {
        val geometry = PresetLibrary.geometryFromSource(obj.source)
        return if (geometry != null) {
            createGeometry(obj, geometry)
        } else {
            createPlaceholderBox(obj)
        }
    }

    fun addInteraction(
        entity: Entity,
        size: Vector3 = Vector3(BOX_SIZE, BOX_SIZE, BOX_SIZE),
        hoverEffectEnabled: Boolean = true,
    ) {
        entity.components.set(
            CollisionComponent(
                collisionShape = listOf(ShapeResource.createBox(size)),
                physicsMaterial = PhysicsMaterialResource(),
            )
        )
        entity.components.set(InteractableComponent())
        if (hoverEffectEnabled) {
            entity.components.set(HoverEffectComponent())
        }
    }

    fun importedAssetScaleFor(longestSide: Float): Float {
        if (!longestSide.isFinite() || longestSide <= 0.0001f) return 1f
        return when {
            longestSide > IMPORTED_ASSET_MAX_SIDE -> IMPORTED_ASSET_MAX_SIDE / longestSide
            longestSide < IMPORTED_ASSET_MIN_SIDE -> IMPORTED_ASSET_MIN_SIDE / longestSide
            else -> 1f
        }
    }

    private fun createGeometry(obj: SceneObject, geometry: PresetGeometry): Entity {
        val material = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor((obj.textColor ?: ObjectColors.DEFAULT_GEOMETRY_COLOR).toColor4())
        }
        return ModelEntity(geometry.createMesh(), material).apply {
            setName(obj.id)
            addInteraction(this, geometry.interactionSize(), hoverEffectEnabled = false)
        }
    }

    private fun PresetGeometry.createMesh(): MeshResource = when (this) {
        PresetGeometry.CUBE -> MeshResource.createBox(Vector3(BOX_SIZE, BOX_SIZE, BOX_SIZE))
        PresetGeometry.SPHERE -> MeshResource.createSphere(radius = SPHERE_RADIUS)
        PresetGeometry.CYLINDER -> MeshResource.createCylinder(radius = CYLINDER_RADIUS, height = CYLINDER_HEIGHT)
        PresetGeometry.CONE -> MeshResource.createCone(radius = CONE_RADIUS, height = CONE_HEIGHT)
        PresetGeometry.PLANE -> MeshResource.createPlane(PLANE_WIDTH, PLANE_HEIGHT)
    }

    private fun PresetGeometry.interactionSize(): Vector3 = when (this) {
        PresetGeometry.CUBE -> Vector3(BOX_SIZE, BOX_SIZE, BOX_SIZE)
        PresetGeometry.SPHERE -> Vector3(SPHERE_RADIUS * 2f, SPHERE_RADIUS * 2f, SPHERE_RADIUS * 2f)
        PresetGeometry.CYLINDER -> Vector3(CYLINDER_RADIUS * 2f, CYLINDER_HEIGHT, CYLINDER_RADIUS * 2f)
        PresetGeometry.CONE -> Vector3(CONE_RADIUS * 2f, CONE_HEIGHT, CONE_RADIUS * 2f)
        PresetGeometry.PLANE -> Vector3(PLANE_WIDTH, PLANE_HEIGHT, INTERACTION_DEPTH)
    }

    private fun createPlaceholderBox(obj: SceneObject): Entity {
        val size = Vector3(BOX_SIZE, BOX_SIZE, BOX_SIZE)
        val mesh = MeshResource.createBox(size)
        val material = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor((obj.textColor ?: ObjectColors.DEFAULT_GEOMETRY_COLOR).toColor4())
        }
        return ModelEntity(mesh, material).apply {
            setName(obj.id)
            addInteraction(this, size, hoverEffectEnabled = obj.type != ObjectType.MODEL)
        }
    }

    private fun createStrokePlaceholder(obj: SceneObject): Entity {
        val size = strokeInteractionSize(obj.strokeData)
        val root = Entity().apply { setName(obj.id) }
        addInteraction(root, size, hoverEffectEnabled = false)
        return root
    }

    private fun strokeInteractionSize(data: StrokeData?): Vector3 {
        val points = data?.strokes?.flatMap { it.points }.orEmpty()
        if (points.isEmpty()) {
            return Vector3(STROKE_FALLBACK_SIZE, STROKE_FALLBACK_SIZE, STROKE_INTERACTION_DEPTH)
        }
        val width = ((points.maxOf { it.x } - points.minOf { it.x }).coerceAtLeast(STROKE_MIN_INTERACTION_SIDE)) +
            STROKE_INTERACTION_PADDING
        val height = ((points.maxOf { it.y } - points.minOf { it.y }).coerceAtLeast(STROKE_MIN_INTERACTION_SIDE)) +
            STROKE_INTERACTION_PADDING
        val depth = ((points.maxOf { it.z } - points.minOf { it.z }).coerceAtLeast(STROKE_INTERACTION_DEPTH)) +
            STROKE_INTERACTION_PADDING
        return Vector3(width, height, depth)
    }

    private fun createViewpoint(obj: SceneObject): Entity {
        val root = Entity().apply { setName(obj.id) }
        val s = VIEWPOINT_FALLBACK_SCALE
        val bodyMaterial = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4(0.08f, 0.18f, 0.32f, 1f))
        }
        val headMaterial = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4(0.98f, 0.72f, 0.42f, 1f))
        }
        val accentMaterial = UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4(0.18f, 0.52f, 0.96f, 1f))
        }
        fun child(entity: Entity, x: Float, y: Float, z: Float, scale: Vector3 = Vector3(1f)) {
            entity.components.get(com.pico.spatial.core.ecs.TransformComponent::class.java)?.apply {
                setPosition(Vector3(x, y, z))
                setScaleVector(scale)
            }
            root.addChild(entity)
        }
        child(
            ModelEntity(MeshResource.createSphere(radius = 0.095f * s), headMaterial).apply { setName("${obj.id}_head") },
            0f,
            0.215f * s,
            0f,
        )
        child(
            ModelEntity(MeshResource.createCylinder(radius = 0.075f * s, height = 0.24f * s), bodyMaterial).apply { setName("${obj.id}_body") },
            0f,
            0.015f * s,
            0f,
        )
        child(
            ModelEntity(MeshResource.createBox(Vector3(0.055f * s, 0.18f * s, 0.055f * s)), bodyMaterial).apply { setName("${obj.id}_left_leg") },
            -0.045f * s,
            -0.19f * s,
            0f,
        )
        child(
            ModelEntity(MeshResource.createBox(Vector3(0.055f * s, 0.18f * s, 0.055f * s)), bodyMaterial).apply { setName("${obj.id}_right_leg") },
            0.045f * s,
            -0.19f * s,
            0f,
        )
        child(
            ModelEntity(MeshResource.createCone(radius = 0.055f * s, height = 0.16f * s), accentMaterial).apply { setName("${obj.id}_forward") },
            0f,
            0.075f * s,
            -0.13f * s,
            Vector3(1f, 1f, 1f),
        )
        addInteraction(root, Vector3(VIEWPOINT_WIDTH, VIEWPOINT_HEIGHT, VIEWPOINT_DEPTH))
        return root
    }

    private fun createImagePlane(obj: SceneObject): Entity {
        val size = imagePlaneSize(obj)
        val bitmap = decodeBitmapForTexture(size.file, size.widthPx, size.heightPx)
            ?: return createPlaceholderBox(obj)
        val texture = TextureResource.create(bitmap)
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4(1f, 1f, 1f, 1f))
            setBaseColorTexture(texture)
            setDepthWrite(false)
        }
        return ModelEntity(MeshResource.createPlane(size.width, size.height), material).apply {
            setName(obj.id)
            addInteraction(this, Vector3(size.width, size.height, INTERACTION_DEPTH), hoverEffectEnabled = false)
        }
    }

    private fun createTextPlane(obj: SceneObject, style: TextStyle): Entity {
        val text = when (style) {
            TextStyle.TEXT -> obj.source.ifBlank { "新增文本" }
            TextStyle.COMMENT -> obj.source.ifEmpty { "待评论" }
        }
        val rendered = renderTextBitmap(
            text = text,
            style = style,
            color = obj.textColor,
            backgroundEnabled = style == TextStyle.COMMENT || obj.textBackgroundEnabled,
        )
        val texture = TextureResource.create(rendered.bitmap)
        val material = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4(1f, 1f, 1f, 1f))
            setBaseColorTexture(texture)
            setDepthWrite(false)
        }
        return ModelEntity(MeshResource.createPlane(rendered.size.width, rendered.size.height), material).apply {
            setName(obj.id)
            if (style == TextStyle.COMMENT) {
                components.set(
                    LookAtComponent().apply {
                        setViewerAsTarget()
                        alignLocalUpToWorldUp = false
                        lookAtForwardDirection = LookAtForwardDirection.POSITIVE_Z
                    }
                )
            }
            addInteraction(this, Vector3(rendered.size.width, rendered.size.height, INTERACTION_DEPTH), hoverEffectEnabled = false)
        }
    }

    private fun renderTextBitmap(
        text: String,
        style: TextStyle,
        color: Long?,
        backgroundEnabled: Boolean,
    ): TextRender {
        val paint = textPaint(style, color)
        val spec = textLayoutSpec(text, style, paint)
        val bitmap = android.graphics.Bitmap.createBitmap(spec.widthPx, spec.heightPx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        if (backgroundEnabled) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = COMMENT_CARD_BACKGROUND_COLOR.toArgbColor()
                this.style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = COMMENT_CARD_BORDER_COLOR.toArgbColor()
                this.style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val radius = if (style == TextStyle.COMMENT) 24f else 30f
            val inset = 2f
            canvas.drawRoundRect(
                inset,
                inset,
                spec.widthPx.toFloat() - inset,
                spec.heightPx.toFloat() - inset,
                radius,
                radius,
                backgroundPaint,
            )
            canvas.drawRoundRect(
                inset,
                inset,
                spec.widthPx.toFloat() - inset,
                spec.heightPx.toFloat() - inset,
                radius,
                radius,
                borderPaint,
            )
        }

        canvas.save()
        canvas.translate(spec.horizontalPadding.toFloat(), spec.verticalPadding.toFloat())
        spec.layout.draw(canvas)
        canvas.restore()
        return TextRender(bitmap, textPlaneSize(spec, style))
    }

    private fun textPaint(style: TextStyle, color: Long?): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = (color ?: DEFAULT_TEXT_COLOR).toArgbColor()
            textSize = when (style) {
                TextStyle.TEXT -> 60f
                TextStyle.COMMENT -> 42f
            }
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            if (style != TextStyle.COMMENT) {
                setShadowLayer(5f, 0f, 2f, Color.argb(120, 255, 255, 255))
            }
        }

    private fun textLayoutSpec(text: String, style: TextStyle, paint: TextPaint): TextLayoutSpec {
        val rawText = text.take(
            when (style) {
                TextStyle.TEXT -> 120
                TextStyle.COMMENT -> 96
            }
        )
        val safeText = when (style) {
            TextStyle.TEXT -> rawText.ifBlank { "新增文本" }
            TextStyle.COMMENT -> rawText.ifEmpty { "待评论" }
        }
        val horizontalPadding = when (style) {
            TextStyle.TEXT -> 28
            TextStyle.COMMENT -> 30
        }
        val verticalPadding = when (style) {
            TextStyle.TEXT -> 28
            TextStyle.COMMENT -> 24
        }
        val maxLayoutWidth = when (style) {
            // Chinese bold glyphs are nearly square. Leave enough room for the
            // authored line instead of stranding its final character below.
            TextStyle.TEXT -> 940 - horizontalPadding * 2
            TextStyle.COMMENT -> 640 - horizontalPadding * 2
        }
        val minLayoutWidth = when (style) {
            TextStyle.TEXT -> 132
            TextStyle.COMMENT -> 180
        }
        val desiredLayoutWidth = ceil(StaticLayout.getDesiredWidth(safeText, paint).toDouble())
            .toInt()
            .coerceIn(minLayoutWidth, maxLayoutWidth)
        val measuredLayout = buildTextLayout(safeText, paint, desiredLayoutWidth, style)
        var contentWidth = minLayoutWidth
        for (line in 0 until measuredLayout.lineCount) {
            contentWidth = max(contentWidth, ceil(measuredLayout.getLineWidth(line).toDouble()).toInt())
        }
        val layoutWidth = contentWidth.coerceIn(minLayoutWidth, maxLayoutWidth)
        val layout = buildTextLayout(safeText, paint, layoutWidth, style)
        return TextLayoutSpec(
            layout = layout,
            widthPx = layoutWidth + horizontalPadding * 2,
            heightPx = layout.height + verticalPadding * 2,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
        )
    }

    private fun buildTextLayout(text: String, paint: TextPaint, widthPx: Int, style: TextStyle): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(2f, 1.0f)
            .setMaxLines(
                when (style) {
                    TextStyle.TEXT -> 8
                    TextStyle.COMMENT -> 3
                }
            )
            .build()

    private fun decodeBitmapForTexture(file: File, widthPx: Int, heightPx: Int): android.graphics.Bitmap? {
        val largestSide = max(widthPx, heightPx)
        var sampleSize = 1
        while (largestSide / sampleSize > MAX_TEXTURE_SIDE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun imagePlaneSize(obj: SceneObject): PlaneSize {
        val file = File(obj.source.removePrefix("file://"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val widthPx = bounds.outWidth.takeIf { it > 0 } ?: 1
        val heightPx = bounds.outHeight.takeIf { it > 0 } ?: 1
        val rawWidth = widthPx / IMPORTED_IMAGE_PIXELS_PER_METER
        val rawHeight = heightPx / IMPORTED_IMAGE_PIXELS_PER_METER
        val scale = importedAssetScaleFor(max(rawWidth, rawHeight))
        val width = rawWidth * scale
        val height = rawHeight * scale
        return PlaneSize(width = width, height = height, file = file, widthPx = widthPx, heightPx = heightPx)
    }

    private fun textPlaneSize(text: String, style: TextStyle): PlaneSize =
        textPlaneSize(textLayoutSpec(text, style, textPaint(style, null)), style)

    private fun textPlaneSize(spec: TextLayoutSpec, style: TextStyle): PlaneSize {
        val pixelsPerMeter = when (style) {
            TextStyle.TEXT -> TEXT_PIXELS_PER_METER
            TextStyle.COMMENT -> COMMENT_PIXELS_PER_METER
        }
        return PlaneSize(width = spec.widthPx / pixelsPerMeter, height = spec.heightPx / pixelsPerMeter)
    }

    private fun Long.toArgbColor(): Int =
        Color.argb(
            ((this shr 24) and 0xFF).toInt(),
            ((this shr 16) and 0xFF).toInt(),
            ((this shr 8) and 0xFF).toInt(),
            (this and 0xFF).toInt(),
        )

    fun Long.toColor4(): Color4 =
        Color4(
            ((this shr 16) and 0xFF).toInt() / 255f,
            ((this shr 8) and 0xFF).toInt() / 255f,
            (this and 0xFF).toInt() / 255f,
            ((this shr 24) and 0xFF).toInt() / 255f,
        )

    private enum class TextStyle {
        TEXT,
        COMMENT,
    }

    private data class PlaneSize(
        val width: Float,
        val height: Float,
        val file: File = File(""),
        val widthPx: Int = 1,
        val heightPx: Int = 1,
    )

    private data class TextLayoutSpec(
        val layout: StaticLayout,
        val widthPx: Int,
        val heightPx: Int,
        val horizontalPadding: Int,
        val verticalPadding: Int,
    )

    private data class TextRender(
        val bitmap: android.graphics.Bitmap,
        val size: PlaneSize,
    )

    private const val MAX_TEXTURE_SIDE = 1024
    private const val INTERACTION_DEPTH = 0.04f
    private const val IMPORTED_IMAGE_PIXELS_PER_METER = 1000f
    private const val TEXT_PIXELS_PER_METER = 882.35f
    private const val COMMENT_PIXELS_PER_METER = 920f
    private const val DEFAULT_TEXT_COLOR = 0xFF111111
    private const val COMMENT_TEXT_COLOR = 0xFFF7E8D2
    private const val COMMENT_CARD_BACKGROUND_COLOR = 0xDD1C1815
    private const val COMMENT_CARD_BORDER_COLOR = 0xFFE5A260
    private const val STROKE_FALLBACK_SIZE = 0.48f
    private const val STROKE_MIN_INTERACTION_SIDE = 0.08f
    private const val STROKE_INTERACTION_DEPTH = 0.035f
    private const val STROKE_INTERACTION_PADDING = 0.08f
    private const val TAU = (Math.PI * 2.0).toFloat()

    private fun seed01(index: Int, salt: Int): Float {
        var value = index * 1103515245 + salt * 12345
        value = value xor (value ushr 16)
        return ((value and 0x7fffffff) % 10_000) / 10_000f
    }
}
