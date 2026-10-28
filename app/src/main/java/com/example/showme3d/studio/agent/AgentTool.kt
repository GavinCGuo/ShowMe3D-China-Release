package com.example.showme3d.studio.agent

enum class AgentParameterType(val jsonType: String) {
    STRING("string"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    OBJECT("object"),
}

data class AgentToolParameter(
    val name: String,
    val type: AgentParameterType,
    val required: Boolean,
    val description: String,
    val enumValues: List<String> = emptyList(),
    val properties: List<AgentToolParameter> = emptyList(),
)

data class AgentTool(
    val name: String,
    val description: String,
    val parameters: List<AgentToolParameter>,
    val resultDescription: String,
    val errorDescriptions: List<String>,
)

fun AgentTool.toFunctionSpec(): Map<String, Any> =
    mapOf(
        "name" to name,
        "description" to description,
        "parameters" to parametersSchema(parameters),
    )

fun AgentToolParameter.toJsonSchema(): Map<String, Any> =
    buildMap {
        put("type", type.jsonType)
        put("description", description)
        if (enumValues.isNotEmpty()) {
            put("enum", enumValues)
        }
        if (properties.isNotEmpty()) {
            put("properties", properties.associate { it.name to it.toJsonSchema() })
            put("required", properties.filter { it.required }.map { it.name })
            put("additionalProperties", false)
        }
    }

private fun parametersSchema(parameters: List<AgentToolParameter>): Map<String, Any> =
    mapOf(
        "type" to AgentParameterType.OBJECT.jsonType,
        "properties" to parameters.associate { it.name to it.toJsonSchema() },
        "required" to parameters.filter { it.required }.map { it.name },
        "additionalProperties" to false,
    )

data class AgentToolResult(
    val toolName: String,
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?> = emptyMap(),
    val errorCode: String? = null,
) {
    companion object {
        fun success(
            toolName: String,
            message: String,
            data: Map<String, Any?> = emptyMap(),
        ): AgentToolResult =
            AgentToolResult(
                toolName = toolName,
                success = true,
                message = message,
                data = data,
            )

        fun failure(
            toolName: String,
            errorCode: String,
            message: String,
            data: Map<String, Any?> = emptyMap(),
        ): AgentToolResult =
            AgentToolResult(
                toolName = toolName,
                success = false,
                message = message,
                data = data,
                errorCode = errorCode,
            )
    }
}
