package de.naturfoto.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun JsonObject.str(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

fun JsonObject.list(key: String): List<String> =
    (this[key] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList()

fun JsonObject.obj(key: String): JsonObject =
    (this[key] as? JsonObject) ?: JsonObject(emptyMap())

val JsonElement?.textOrNull: String?
    get() = (this as? JsonPrimitive)?.contentOrNull
