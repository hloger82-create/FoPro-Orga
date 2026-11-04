package de.naturfoto.app.ebird

import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.str
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

class EBirdRepository {
    private val http = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun apiKey(): String? {
        val uid = Supabase.client.auth.currentUserOrNull()?.id ?: return null
        return Supabase.client.postgrest["profiles"].select {
            filter { eq("id", uid) }
        }.decodeList<JsonObject>().firstOrNull()?.str("ebird_api_key")?.ifBlank { null }
    }

    suspend fun setApiKey(key: String) {
        val uid = Supabase.client.auth.currentUserOrNull()?.id ?: return
        Supabase.client.postgrest["profiles"].update({
            set("ebird_api_key", key.trim())
        }) { filter { eq("id", uid) } }
    }

    /** Letzte Meldungen (back Tage) im Umkreis um das Projektgebiets-Zentrum, deutsch */
    suspend fun recentObservations(
        apiKey: String,
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double,
        backDays: Int = 3,
        notable: Boolean = false,
    ): List<JsonObject> {
        val path = if (notable) "recent/notable" else "recent"
        val res = http.get("https://api.ebird.org/v2/data/obs/geo/$path") {
            header("X-eBirdAPIToken", apiKey)
            parameter("lat", centerLat)
            parameter("lng", centerLng)
            parameter("dist", radiusKm.coerceAtMost(50.0))
            parameter("back", backDays)
            parameter("maxResults", 100)
            parameter("detailLevel", "simple")
            parameter("locale", "de")
        }.body<JsonArray>()
        return res.mapNotNull { it as? JsonObject }
    }

    companion object {
        fun regionCenter(polygon: List<Pair<Double, Double>>): Pair<Double, Double>? {
            if (polygon.isEmpty()) return null
            val lat = polygon.map { it.first }.average()
            val lng = polygon.map { it.second }.average()
            return lat to lng
        }

        fun regionRadiusKm(polygon: List<Pair<Double, Double>>): Double {
            val c = regionCenter(polygon) ?: return 25.0
            val maxDist = polygon.maxOfOrNull { p ->
                val dLat = (p.first - c.first) * 111.0
                val dLng = (p.second - c.second) * 111.0 * kotlin.math.cos(Math.toRadians(c.first))
                kotlin.math.sqrt(dLat * dLat + dLng * dLng)
            } ?: 25.0
            return (maxDist * 1.2).coerceIn(5.0, 50.0)
        }
    }
}
