package com.example.showme3d.studio.model

/** 一份演示文档。 */
class ShowDocument(
    val id: String,
    var name: String,
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    var documentType: DocumentType = DocumentType.PRESENTATION,
    val objects: MutableMap<String, SceneObject> = mutableMapOf(),
    val pages: MutableList<Page> = mutableListOf(),
    /** P1：简化锚点开关。 */
    var anchorEnabled: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 10
        const val MAX_PAGES = 30
    }
}

fun ShowDocument.hasNoElements(): Boolean = objects.isEmpty()
