package com.example.showme3d.studio.examples

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class DinosaurDetectiveExampleAssets(
    val modelSources: Map<String, String>,
    val ctScanPanel: String,
    val fossilGallerySkybox: String,
    val cretaceousForestSkybox: String,
)

val dinosaurDetectiveModelFiles = linkedMapOf(
    "raptor_claw" to "raptor_claw.glb",
    "trex_tooth" to "trex_tooth.glb",
    "trex_skull" to "trex_skull.glb",
    "dinosaur_footprint" to "dinosaur_footprint.glb",
    "archaeopteryx_fossil" to "archaeopteryx_fossil.glb",
    "trex_brain_endocast" to "trex_brain_endocast.glb",
    "trex_skeleton" to "trex_skeleton.glb",
    "trex_animated" to "trex_animated.glb",
)

/** Installs the packaged lesson into the existing file:// model pipeline. */
object DinosaurDetectiveExampleAssetInstaller {
    private const val ASSET_ROOT = "examples/dinosaur"
    private const val INSTALL_VERSION = "v3"

    private val files = dinosaurDetectiveModelFiles.values + listOf(
        "trex_ct_scan_panel.png",
        "fossil_gallery_skybox.png",
        "cretaceous_forest_skybox.png",
    )

    fun install(context: Context): Result<DinosaurDetectiveExampleAssets> = runCatching {
        val destination = File(context.filesDir, "showme3d/builtin/dinosaur/$INSTALL_VERSION")
        destination.mkdirs()
        files.forEach { fileName ->
            val output = File(destination, fileName)
            if (!output.exists() || output.length() == 0L) {
                val temporary = File(destination, "$fileName.tmp")
                context.assets.open("$ASSET_ROOT/$fileName").use { input ->
                    FileOutputStream(temporary).use { outputStream -> input.copyTo(outputStream) }
                }
                if (output.exists()) check(output.delete()) { "Failed to replace $fileName" }
                check(temporary.renameTo(output)) { "Failed to install $fileName" }
            }
        }

        fun source(fileName: String): String = "file://${File(destination, fileName).absolutePath}"
        DinosaurDetectiveExampleAssets(
            modelSources = dinosaurDetectiveModelFiles.mapValues { (_, fileName) -> source(fileName) },
            ctScanPanel = source("trex_ct_scan_panel.png"),
            fossilGallerySkybox = source("fossil_gallery_skybox.png"),
            cretaceousForestSkybox = source("cretaceous_forest_skybox.png"),
        )
    }
}
