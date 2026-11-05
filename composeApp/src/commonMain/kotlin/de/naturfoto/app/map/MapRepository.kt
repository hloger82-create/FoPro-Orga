package de.naturfoto.app.map

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.str
import de.naturfoto.app.data.textOrNull
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonObject

class MapRepository {
    private val db get() = Supabase.client.postgrest
    val userId get() = Supabase.client.auth.currentUserOrNull()?.id

    suspend fun isAdmin(projectId: String): Boolean =
        db["project_members"].select {
            filter {
                eq("project_id", projectId)
                eq("user_id", userId ?: "")
                eq("role", "admin")
            }
        }.decodeList<JsonObject>().isNotEmpty()

    suspend fun saveRegion(projectId: String, polygon: List<Pair<Double, Double>>) {
        val arr = kotlinx.serialization.json.JsonArray(
            polygon.map { (lat, lng) ->
                kotlinx.serialization.json.JsonArray(
                    listOf(
                        kotlinx.serialization.json.JsonPrimitive(lat),
                        kotlinx.serialization.json.JsonPrimitive(lng),
                    ),
                )
            },
        )
        db["projects"].update({ set("region_polygon", arr) }) {
            filter { eq("id", projectId) }
        }
    }

    suspend fun projectRegion(projectId: String): List<Pair<Double, Double>> {
        val p = db["projects"].select { filter { eq("id", projectId) } }
            .decodeList<JsonObject>().firstOrNull() ?: return emptyList()
        val arr = p["region_polygon"] ?: return emptyList()
        val list = (arr as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        return list.mapNotNull { el ->
            val pair = (el as? kotlinx.serialization.json.JsonArray) ?: return@mapNotNull null
            val lat = pair.getOrNull(0)?.textOrNull?.toDoubleOrNull()
            val lng = pair.getOrNull(1)?.textOrNull?.toDoubleOrNull()
            if (lat != null && lng != null) lat to lng else null
        }
    }

    fun isInsidePolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        if (polygon.size < 3) return true // kein Gebiet definiert -> überall erlaubt
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (yI, xI) = polygon[i]   // Polygon speichert (lat, lng) = (y, x)
            val (yJ, xJ) = polygon[j]
            if ((yI > lat) != (yJ > lat) && lng < (xJ - xI) * (lat - yI) / (yJ - yI) + xI) inside = !inside
            j = i
        }
        return inside
    }

    suspend fun listSpots(projectId: String): List<JsonObject> =
        db["map_spots"].select {
            filter { eq("project_id", projectId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<JsonObject>()
            .filter { it.str("visibility") != "private" || it.str("author_id") == userId }

    suspend fun subscribedAreaIds(projectId: String): Set<String> {
        val areas = listAreas(projectId).map { it.str("id") }.toSet()
        val subs = db["area_subscriptions"].select { filter { eq("user_id", userId ?: "") } }
            .decodeList<JsonObject>()
            .associate { it.str("area_id") to (if (it.containsKey("subscribed")) it.bool("subscribed") else true) }
        return areas.filter { subs[it] != false }.toSet()
    }

    suspend fun listAreas(projectId: String): List<JsonObject> =
        db["areas"].select {
            filter { eq("project_id", projectId) }
            order("name", Order.ASCENDING)
        }.decodeList<JsonObject>()

    suspend fun authorName(userId: String): String =
        db["profiles"].select { filter { eq("id", userId) } }
            .decodeList<JsonObject>().firstOrNull()?.str("display_name")?.ifBlank { null } ?: "Unbekannt"

    /** Deterministische Farbe pro Area (aus der ID), weiß wenn keine Area */
    fun areaColor(areaId: String?): Int {
        if (areaId.isNullOrBlank()) return 0xFFFFFFFF.toInt()
        var h = 0
        for (c in areaId) h = 31 * h + c.code
        val hue = (((h % 360) + 360) % 360).toFloat()
        val c = 0.65f
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
        val m = 0.25f
        val (r, g, b) = when (hue) {
            in 0f..60f -> Triple(c, x, 0f)
            in 60f..120f -> Triple(x, c, 0f)
            in 120f..180f -> Triple(0f, c, x)
            in 180f..240f -> Triple(0f, x, c)
            in 240f..300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or ((r + m).coerceIn(0f, 1f) * 255).toInt() shl 16 or
                ((g + m).coerceIn(0f, 1f) * 255).toInt() shl 8 or
                ((b + m).coerceIn(0f, 1f) * 255).toInt()
    }

    suspend fun createSpot(
        projectId: String,
        title: String,
        description: String?,
        lat: Double,
        lng: Double,
        photoBytes: ByteArray?,
        exif: Map<String, String>?,
        visibility: String,
        areaId: String? = null,
    ): String {
        var photoUrl: String? = null
        if (photoBytes != null) {
            val compressed = compressPhoto(photoBytes)
            val path = "spots/${userId}/${System.currentTimeMillis()}.jpg"
            Supabase.client.storage.from("photos").upload(path, compressed)
            photoUrl = Supabase.client.storage.from("photos").publicUrl(path)
        }
        val exifJson = exif?.let { jsonOf(*it.map { (k, v) -> k to v }.toTypedArray()) }
        db["map_spots"].insert(
            jsonOf(
                "project_id" to projectId,
                "author_id" to (userId ?: ""),
                "title" to title,
                "description" to description,
                "lat" to lat,
                "lng" to lng,
                "photo_url" to photoUrl,
                "exif" to exifJson,
                "visibility" to visibility,
                "area_id" to areaId,
            ),
        )
        return photoUrl ?: ""
    }
}
