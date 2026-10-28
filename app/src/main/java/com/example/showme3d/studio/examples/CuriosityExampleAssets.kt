package com.example.showme3d.studio.examples

import android.content.Context
import java.io.File
import java.io.FileOutputStream

data class CuriosityExampleAssets(
    val body: String,
    val suspensionLeft: String,
    val suspensionRight: String,
    val wheelLeftFront: String,
    val wheelLeftMiddle: String,
    val wheelLeftRear: String,
    val wheelRightFront: String,
    val wheelRightMiddle: String,
    val wheelRightRear: String,
    val mast: String,
    val roboticArm: String,
    val completeCleanRover: String,
    val animatedCleanRover: String,
    val completeDirtyRover: String,
    val martianRock: String,
    val martianGround: String,
    val marsPanorama: String,
    val nasaSourcePage: String,
)

/**
 * Installs APK assets into a stable filesDir location because imported scene
 * models and page skyboxes share ShowMe3D's existing file:// loading pipeline.
 */
object CuriosityExampleAssetInstaller {
    private const val ASSET_ROOT = "examples/curiosity"
    private const val INSTALL_VERSION = "v6"

    private val files = listOf(
        "curiosity_clean_body.glb",
        "curiosity_clean_suspension_left.glb",
        "curiosity_clean_suspension_right.glb",
        "curiosity_clean_wheel_left_front.glb",
        "curiosity_clean_wheel_left_middle.glb",
        "curiosity_clean_wheel_left_rear.glb",
        "curiosity_clean_wheel_right_front.glb",
        "curiosity_clean_wheel_right_middle.glb",
        "curiosity_clean_wheel_right_rear.glb",
        "curiosity_clean_mast.glb",
        "curiosity_clean_robotic_arm.glb",
        "curiosity_clean_complete.glb",
        "curiosity_clean_animated.glb",
        "curiosity_complete.glb",
        "martian_rock.glb",
        "martian_ground.glb",
        "mars_panorama.jpg",
        "nasa_source_page.png",
    )

    fun install(context: Context): Result<CuriosityExampleAssets> = runCatching {
        val destination = File(context.filesDir, "showme3d/builtin/curiosity/$INSTALL_VERSION")
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
        CuriosityExampleAssets(
            body = source("curiosity_clean_body.glb"),
            suspensionLeft = source("curiosity_clean_suspension_left.glb"),
            suspensionRight = source("curiosity_clean_suspension_right.glb"),
            wheelLeftFront = source("curiosity_clean_wheel_left_front.glb"),
            wheelLeftMiddle = source("curiosity_clean_wheel_left_middle.glb"),
            wheelLeftRear = source("curiosity_clean_wheel_left_rear.glb"),
            wheelRightFront = source("curiosity_clean_wheel_right_front.glb"),
            wheelRightMiddle = source("curiosity_clean_wheel_right_middle.glb"),
            wheelRightRear = source("curiosity_clean_wheel_right_rear.glb"),
            mast = source("curiosity_clean_mast.glb"),
            roboticArm = source("curiosity_clean_robotic_arm.glb"),
            completeCleanRover = source("curiosity_clean_complete.glb"),
            animatedCleanRover = source("curiosity_clean_animated.glb"),
            completeDirtyRover = source("curiosity_complete.glb"),
            martianRock = source("martian_rock.glb"),
            martianGround = source("martian_ground.glb"),
            marsPanorama = source("mars_panorama.jpg"),
            nasaSourcePage = source("nasa_source_page.png"),
        )
    }
}
