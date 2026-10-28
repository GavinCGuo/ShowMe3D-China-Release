package com.example.showme3d.studio.aigc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.showme3d.studio.imports.ImportedAsset
import com.example.showme3d.studio.model.ObjectType
import com.example.showme3d.studio.model.StrokeData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StrokeImageRenderer {
    fun renderMainViewReference(
        context: Context,
        strokeData: StrokeData,
        displayName: String,
        frontFace: StrokeFrontFace = StrokeFrontFace.POSITIVE_Z,
    ): Result<ImportedAsset> = runCatching {
        val layout = StrokeImageProjection.projectMainView(
            data = strokeData,
            frontFace = frontFace,
        )
        val bitmap = Bitmap.createBitmap(layout.imageSizePx, layout.imageSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 250, 247))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = layout.strokeWidthPx
        }
        layout.paths.forEach { pathLayout ->
            paint.color = pathLayout.color.toReadableReferenceColor()
            val drawPath = Path()
            pathLayout.points.forEachIndexed { index, point ->
                if (index == 0) drawPath.moveTo(point.x, point.y) else drawPath.lineTo(point.x, point.y)
            }
            canvas.drawPath(drawPath, paint)
        }

        val file = newStrokeImageTarget(
            directory = AigcLocalLibrary.imageDirectory(context),
            displayName = displayName,
            suffix = "main_view_reference",
        )
        file.outputStream().use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) { "主视图参考图写入失败" }
        }
        bitmap.recycle()
        ImportedAsset(
            displayName = "${displayName.take(18).ifBlank { "笔画" }}主视图",
            type = ObjectType.IMAGE,
            source = "file://${file.absolutePath}",
        )
    }

    fun renderLineArt(
        context: Context,
        strokeData: StrokeData,
        displayName: String,
    ): Result<ImportedAsset> = runCatching {
        val layout = StrokeImageProjection.project(strokeData)
        val bitmap = Bitmap.createBitmap(layout.imageSizePx, layout.imageSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = layout.strokeWidthPx
        }
        layout.paths.forEach { pathLayout ->
            paint.color = pathLayout.color.toArgbColor()
            val drawPath = Path()
            pathLayout.points.forEachIndexed { index, point ->
                if (index == 0) {
                    drawPath.moveTo(point.x, point.y)
                } else {
                    drawPath.lineTo(point.x, point.y)
                }
            }
            canvas.drawPath(drawPath, paint)
        }

        val file = newStrokeImageTarget(
            directory = AigcLocalLibrary.imageDirectory(context),
            displayName = displayName,
            suffix = "lineart",
        )
        file.outputStream().use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) { "线稿图片写入失败" }
        }
        bitmap.recycle()
        ImportedAsset(
            displayName = "${displayName.take(18).ifBlank { "笔画" }}线稿",
            type = ObjectType.IMAGE,
            source = "file://${file.absolutePath}",
        )
    }

    private fun newStrokeImageTarget(directory: File, displayName: String, suffix: String): File {
        directory.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = displayName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(28)
            .ifBlank { "stroke_lineart" }
        return File(directory, "${stamp}_${safeName}_$suffix.png")
    }

    private fun Long.toArgbColor(): Int {
        val value = toInt()
        return if (value ushr 24 == 0) value or Color.BLACK else value
    }

    private fun Long.toReadableReferenceColor(): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(toArgbColor(), hsv)
        if (hsv[2] > 0.82f) {
            hsv[2] = if (hsv[1] < 0.12f) 0.36f else 0.68f
        }
        return Color.HSVToColor(hsv)
    }

    private const val PNG_QUALITY = 100
}
