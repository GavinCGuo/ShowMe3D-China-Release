package com.example.showme3d.studio.model

/**
 * 某个 Object 在“某一页”的差异化状态：变换 + 是否可见。
 * 跨页同一 ID 的 Object 通过不同 ObjectState 实现“3D PPT”过渡。
 */
class ObjectState(
    var transform: TransformData = TransformData(),
    var visible: Boolean = true,
    var pageAnimation: PageAnimation? = null,
    var autoRotateY: Boolean = false,
    /** Objects with the same non-null id form one logical group on this page. */
    var groupId: String? = null,
) {
    fun copyState(): ObjectState = ObjectState(
        transform = transform,
        visible = visible,
        pageAnimation = pageAnimation,
        autoRotateY = autoRotateY,
        groupId = groupId,
    )
}
