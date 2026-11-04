package de.naturfoto.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import de.naturfoto.app.data.textOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val http = HttpClient(Android) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private fun primitiveContent(el: kotlinx.serialization.json.JsonElement?): String? =
    el?.textOrNull

private suspend fun searchRegion(query: String): List<Pair<Double, Double>>? {
    if (query.isBlank()) return null
    val response = http.get("https://nominatim.openstreetmap.org/search") {
        parameter("q", query)
        parameter("format", "json")
        parameter("polygon_geojson", "1")
        parameter("limit", "1")
        header("User-Agent", "FoPro-Orga/0.1 (local-dev)")
    }
    val raw = response.body<String>()
    if (!raw.trimStart().startsWith("[")) {
        error("HTTP ${response.status.value}: ${raw.take(200)}")
    }
    val res = Json.parseToJsonElement(raw) as? JsonArray ?: error("Kein JSON-Array: ${raw.take(120)}")
    val first = res.firstOrNull() as? JsonObject ?: return null
    val geom = (first["geojson"] ?: first["geometry"])?.jsonObject ?: return null
    val coords = geom["coordinates"]?.jsonArray ?: return null
    fun ring(arr: JsonArray): List<Pair<Double, Double>> = arr.mapNotNull { c ->
        val pair = c.jsonArray
        val lng = primitiveContent(pair.getOrNull(0))?.toDoubleOrNull()
        val lat = primitiveContent(pair.getOrNull(1))?.toDoubleOrNull()
        if (lat != null && lng != null) lat to lng else null
    }
    val rings: List<List<Pair<Double, Double>>> = when (primitiveContent(geom["type"])) {
        "Polygon" -> listOf(ring(coords.firstOrNull()?.jsonArray ?: return null))
        "MultiPolygon" -> coords.mapNotNull { ring(it.jsonArray.firstOrNull()?.jsonArray ?: return@mapNotNull null) }
        else -> {
            val lat = primitiveContent(first["lat"])?.toDoubleOrNull()
            val lon = primitiveContent(first["lon"])?.toDoubleOrNull()
            return if (lat != null && lon != null) listOf(lat to lon) else null
        }
    }
    // Nur den größten Ring (nach Punktzahl) verwenden und ausdünnen, damit das Polygon nicht self-intersectt
    val largest = rings.maxByOrNull { it.size } ?: return null
    val step = (largest.size / 500).coerceAtLeast(1)
    return largest.filterIndexed { i, _ -> i % step == 0 }
}

@Composable
actual fun RegionEditor(
    currentPolygon: List<Pair<Double, Double>>,
    onPolygonChanged: (List<Pair<Double, Double>>) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var drawing by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var drawn by remember { mutableStateOf(currentPolygon) }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Region suchen (z. B. Hannover, Hessen)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                scope.launch {
                    status = "Suche läuft…"
                    try {
                        val result = searchRegion(query)
                        if (result != null && result.size >= 3) {
                            drawn = result
                            onPolygonChanged(result)
                            status = "Gefunden: ${result.size} Punkte"
                        } else {
                            status = "Kein Gebiet gefunden (${result?.size ?: 0} Punkte)"
                        }
                    } catch (e: Exception) {
                        status = "Fehler: ${e.message?.take(200)}"
                    }
                }
            }) { Text("Suchen") }
            TextButton(onClick = {
                drawing = emptyList()
                drawn = emptyList()
                onPolygonChanged(emptyList())
                status = null
            }) { Text("Löschen") }
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            "Tippe auf die Karte, um das Gebiet zu zeichnen (mind. 3 Punkte), oder nutze die Suche.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(51.1657, 10.4515))
                    overlays.add(
                        Polygon().apply {
                            points = drawn.map { GeoPoint(it.first, it.second) }.toMutableList()
                            outlinePaint.color = 0xFFE8B44B.toInt()
                            outlinePaint.strokeWidth = 5f
                            setFillColor(0x33E8B44B)
                        },
                    )
                    overlays.add(
                        Polyline().apply {
                            setPoints(drawing)
                            outlinePaint.color = 0xFF7FB069.toInt()
                            outlinePaint.strokeWidth = 4f
                        },
                    )
                    overlays.add(
                        MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    if (p != null) {
                                        drawing = drawing + p
                                        val poly = overlays.first { it is Polygon } as Polygon
                                        poly.points = drawing.toMutableList()
                                        invalidate()
                                        if (drawing.size >= 3) {
                                            drawn = drawing.map { it.latitude to it.longitude }
                                            onPolygonChanged(drawn)
                                        }
                                    }
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint?): Boolean = false
                            },
                        ),
                    )
                }
            },
            update = { mapView ->
                val poly = mapView.overlays.first { it is Polygon } as Polygon
                poly.points = drawn.map { GeoPoint(it.first, it.second) }.toMutableList()
                mapView.invalidate()
            },
        )
    }
}
