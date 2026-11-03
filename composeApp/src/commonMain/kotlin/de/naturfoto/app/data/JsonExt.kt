package de.naturfoto.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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

fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) ->
        when (v) {
            null -> put(k, kotlinx.serialization.json.JsonNull)
            is String -> put(k, v)
            is Boolean -> put(k, v)
            is Int -> put(k, v)
            is Long -> put(k, v)
            is Double -> put(k, v)
            is Map<*, *> -> putJsonObject(k) {
                v.forEach { (mk, mv) -> put(mk.toString(), mv.toString()) }
            }
            is List<*> -> putJsonArray(k) { v.forEach { add(JsonPrimitive(it.toString())) } }
            else -> put(k, v.toString())
        }
    }
}
