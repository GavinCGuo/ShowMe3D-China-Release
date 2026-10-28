package com.example.showme3d.studio.examples

import android.content.Context
import com.example.showme3d.R
import java.io.File
import java.io.FileOutputStream

data class ImmersiveInteriorExampleAssets(
    val modelSources: Map<String, String>,
    val conceptCover: String,
    val sketchSkybox: String,
    val refinedSkybox: String,
)

val immersiveInteriorModelFiles = linkedMapOf(
    "interior_shell" to "interior_shell_open.glb",
    "furniture_bank" to "furniture_bank.glb",
    "furniture_fridge" to "furniture_fridge.glb",
    "furniture_kitchen_island" to "furniture_kitchen_island.glb",
    "furniture_bar_chair_1" to "furniture_bar_chair_1.glb",
    "furniture_bar_chair_2" to "furniture_bar_chair_2.glb",
    "furniture_sofa" to "furniture_sofa.glb",
    "furniture_footrest" to "furniture_footrest.glb",
    "furniture_tv_table" to "furniture_tv_table.glb",
    "furniture_tv" to "furniture_tv.glb",
    "furniture_hallway_table" to "furniture_hallway_table.glb",
    "furniture_table_lamp" to "furniture_table_lamp.glb",
    "furniture_shoe_rack" to "furniture_shoe_rack.glb",
    "furniture_clothes_hanger" to "furniture_clothes_hanger.glb",
    "detail_faucet" to "detail_faucet.glb",
    "detail_radio" to "detail_radio.glb",
    "detail_plant" to "detail_plant.glb",
    "detail_monstera" to "detail_monstera.glb",
    "detail_knife_holder" to "detail_knife_holder.glb",
    "detail_knife_1" to "detail_knife_1.glb",
    "detail_knife_2" to "detail_knife_2.glb",
    "detail_knife_3" to "detail_knife_3.glb",
    "detail_microwave" to "detail_microwave.glb",
    "detail_shoe_1" to "detail_shoe_1.glb",
    "detail_shoe_2" to "detail_shoe_2.glb",
    "detail_glass" to "detail_glass.glb",
    "detail_cutlery" to "detail_cutlery.glb",
    "detail_jacket" to "detail_jacket.glb",
    "detail_artwork" to "detail_artwork.glb",
    "detail_ceiling_lights" to "detail_ceiling_lights.glb",
)

/** Installs the packaged example into the existing file:// model pipeline. */
object ImmersiveInteriorExampleAssetInstaller {
    private const val ASSET_ROOT = "examples/interior"
    private const val INSTALL_VERSION = "v5"

    private val files = immersiveInteriorModelFiles.values + listOf(
        "interior_sketch_skybox.png",
        "interior_refined_skybox.png",
    )

    fun install(context: Context): Result<ImmersiveInteriorExampleAssets> = runCatching {
        val destination = File(context.filesDir, "showme3d/builtin/interior/$INSTALL_VERSION")
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

        val coverFile = File(destination, "cover_immersive_interior_cutout.png")
        if (!coverFile.exists() || coverFile.length() == 0L) {
            val temporary = File(destination, "cover_immersive_interior_cutout.png.tmp")
            context.resources.openRawResource(R.drawable.cover_immersive_interior_cutout).use { input ->
                FileOutputStream(temporary).use { outputStream -> input.copyTo(outputStream) }
            }
            if (coverFile.exists()) check(coverFile.delete()) { "Failed to replace interior cover" }
            check(temporary.renameTo(coverFile)) { "Failed to install interior cover" }
        }

        fun source(fileName: String): String = "file://${File(destination, fileName).absolutePath}"
        ImmersiveInteriorExampleAssets(
            modelSources = immersiveInteriorModelFiles.mapValues { (_, fileName) -> source(fileName) },
            conceptCover = source("cover_immersive_interior_cutout.png"),
            sketchSkybox = source("interior_sketch_skybox.png"),
            refinedSkybox = source("interior_refined_skybox.png"),
        )
    }
}
