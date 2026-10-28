package com.example.showme3d.studio.model

/**
 * 一“页” = 一次场景快照。仅保存各 Object 的差异化状态。
 * 新建页 = 深拷贝上一页的 [states]（继承内容）。
 */
class Page(
    val id: String,
    val states: MutableMap<String /* objectId */, ObjectState> = mutableMapOf(),
    val annotations: MutableList<PageAnnotation> = mutableListOf(),
    var skybox: PageSkybox? = null,
) {
    /** 深拷贝本页（用于“新增页继承上一页 / 复制页”）。 */
    fun deepCopy(newId: String): Page {
        val copied = Page(newId)
        states.forEach { (objectId, state) -> copied.states[objectId] = state.copyState() }
        copied.annotations += annotations.map { it.copyAnnotation() }
        copied.skybox = skybox?.copy()
        return copied
    }
}

/** 当前页的用户天空盒引用与水平朝向。 */
data class PageSkybox(
    val source: String,
    val displayName: String,
    val rotationY: Float = 0f,
)
