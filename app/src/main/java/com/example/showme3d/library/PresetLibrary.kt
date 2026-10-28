package com.example.showme3d.library

import com.example.showme3d.R
import com.example.showme3d.studio.model.ObjectType

/** 资源库一级分类。 */
enum class PresetCategory(
    val displayName: String,
    val description: String,
    val emptyTitle: String,
    val emptyMessage: String,
) {
    PRESET_MODEL(
        displayName = "预置模型",
        description = "基础几何与精选场景模型",
        emptyTitle = "预置模型待补充",
        emptyMessage = "这里会放基础几何与开箱即用的场景模型。",
    ),
    AIGC(
        displayName = "AIGC-2/3D",
        description = "生成图片与 3D 模型",
        emptyTitle = "生成资源待配置",
        emptyMessage = "填写 Rodin Key 和提示词后，生成的 3D 模型会进入我的资源。",
    ),
}

/** 当前内置的基础几何体。 */
enum class PresetGeometry(
    val sourceId: String,
    val displayName: String,
) {
    CUBE("cube", "立方体"),
    SPHERE("sphere", "球体"),
    CYLINDER("cylinder", "圆柱"),
    CONE("cone", "圆锥"),
    PLANE("plane", "平面"),
}

/** 资源库一条预置素材。 */
data class PresetAsset(
    val id: String,
    val displayName: String,
    val category: PresetCategory,
    val type: ObjectType,
    /** 加载来源：AssetBundle 内场景名，或 asset:// 路径。 */
    val source: String,
    val geometry: PresetGeometry? = null,
    /** 预置 GLB 在资源卡片中使用的静态封面；基础几何继续使用空间实时预览。 */
    val thumbnailRes: Int? = null,
)

/**
 * 预置素材清单。基础几何使用运行时网格，精选模型使用 APK 内置 GLB。
 */
object PresetLibrary {
    const val GEOMETRY_SOURCE_PREFIX = "preset://geometry/"

    val categories: List<PresetCategory> = PresetCategory.values().toList()

    val assets: List<PresetAsset> =
        PresetGeometry.values().map { geometry ->
            PresetAsset(
                id = "preset_geometry_${geometry.sourceId}",
                displayName = geometry.displayName,
                category = PresetCategory.PRESET_MODEL,
                type = ObjectType.MODEL,
                source = GEOMETRY_SOURCE_PREFIX + geometry.sourceId,
                geometry = geometry,
            )
        } + listOf(
            presetModel("floating_island", "浮空岛", "floating_island.glb", R.drawable.preset_floating_island),
            presetModel("flower_bed", "花坛", "flower_bed.glb", R.drawable.preset_flower_bed),
            presetModel("road_lamp", "路灯", "road_lamp.glb", R.drawable.preset_road_lamp),
            presetModel("crystal_cluster", "水晶", "crystal_cluster.glb", R.drawable.preset_crystal_cluster),
            presetModel("deer", "小鹿", "deer.glb", R.drawable.preset_deer),
            presetModel("garden_bench", "椅子", "garden_bench.glb", R.drawable.preset_garden_bench),
            presetModel("rock", "石头", "rock.glb", R.drawable.preset_rock),
            presetModel("red_car_complete", "完整的红车", "red_car_complete.glb", R.drawable.preset_red_car_complete),
            presetModel("red_car_crashed", "撞毁的车", "red_car_crashed.glb", R.drawable.preset_red_car_crashed),
        )

    fun assetsFor(category: PresetCategory): List<PresetAsset> =
        assets.filter { it.category == category }

    fun geometryFromSource(source: String): PresetGeometry? {
        val sourceId = source.removePrefix(GEOMETRY_SOURCE_PREFIX).takeIf { it != source } ?: return null
        return PresetGeometry.values().firstOrNull { it.sourceId == sourceId }
    }

    private fun presetModel(id: String, name: String, fileName: String, thumbnailRes: Int) = PresetAsset(
        id = "preset_model_$id",
        displayName = name,
        category = PresetCategory.PRESET_MODEL,
        type = ObjectType.MODEL,
        source = "asset://preset_models/$fileName",
        thumbnailRes = thumbnailRes,
    )
}
