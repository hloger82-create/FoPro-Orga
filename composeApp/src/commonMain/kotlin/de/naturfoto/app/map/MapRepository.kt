package de.naturfoto.app.map

import de.naturfoto.app.data.Supabase
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
    private val userId get() = Supabase.client.auth.currentUserOrNull()?.id

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
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
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

    suspend fun createSpot(
        projectId: String,
        title: String,
        description: String?,
        lat: Double,
        lng: Double,
        photoBytes: ByteArray?,
        exif: Map<String, String>?,
        visibility: String,
    ): String {
        var photoUrl: String? = null
        if (photoBytes != null) {
            val path = "spots/${userId}/${System.currentTimeMillis()}.jpg"
            Supabase.client.storage.from("photos").upload(path, photoBytes)
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
            ),
        )
        return photoUrl ?: ""
    }
}
