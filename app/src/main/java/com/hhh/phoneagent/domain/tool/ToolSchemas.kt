package com.hhh.phoneagent.domain.tool

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun objectSchema(properties: Map<String, JsonObject>, required: List<String> = emptyList()): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (key, schema) -> put(key, schema) }
        }
        if (required.isNotEmpty()) {
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
    }

fun stringParam(description: String, enumValues: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    if (enumValues != null) {
        putJsonArray("enum") { enumValues.forEach { add(JsonPrimitive(it)) } }
    }
}

fun integerParam(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

fun booleanParam(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

/** Argument readers. They tolerate numbers and booleans sent as strings, which models do sometimes. */
fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
