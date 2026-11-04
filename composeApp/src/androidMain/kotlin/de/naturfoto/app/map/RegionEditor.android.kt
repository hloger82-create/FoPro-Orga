package de.naturfoto.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private val http = HttpClient(Android) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private suspend fun searchRegion(query: String): List<Pair<Double, Double>>? {
    if (query.isBlank()) return null
    val res = http.get("https://nominatim.openstreetmap.org/search") {
        parameter("q", query)
        parameter("format", "json")
        parameter("polygon_geojson", "1")
        parameter("limit", "1")
        header("User-Agent", "FoPro-Orga/0.1 (contact: local-dev)")
    }.body<JsonArray>()
    val first = res.firstOrNull() as? JsonObject ?: return null
    val geom = first["geometry"]?.jsonObject ?: return null
    val coords = geom["coordinates"]?.jsonArray ?: return null
    fun ring(arr: JsonArray): List<Pair<Double, Double>> = arr.mapNotNull { c ->
        val pair = c.jsonArray
        val lng = pair.getOrNull(0)?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> p.content } }?.toDoubleOrNull()
        val lat = pair.getOrNull(1)?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> p.content } }?.toDoubleOrNull()
        if (lat != null && lng != null) lat to lng else null
    }
    return when (geom["type"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }) {
        "Polygon" -> ring(coords.firstOrNull()?.jsonArray ?: return null)
        "MultiPolygon" -> coords.flatMap { ring(it.jsonArray.firstOrNull()?.jsonArray ?: return@flatMap emptyList()) }
        else -> {
            val lat = first["lat"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.toDoubleOrNull()
            val lon = first["lon"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.toDoubleOrNull()
            if (lat != null && lon != null) listOf(lat to lon) else null
        }
    }
}

@Composable
actual fun RegionEditor(
    currentPolygon: List<Pair<Double, Double>>,
    onPolygonChanged: (List<Pair<Double, Double>>) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var drawing by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var drawn by remember { mutableStateOf<List<Pair<Double, Double>>>(currentPolygon) }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        var query by remember { mutableStateOf("") }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { androidx.compose.material3.Text("Region suchen (z. B. Hannover, Hessen)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    searchRegion(query)?.let {
                        drawn = it
                        onPolygonChanged(it)
                    }
                }
            }) { androidx.compose.material3.Text("Suchen") }
            androidx.compose.material3.TextButton(onClick = { drawing = emptyList(); drawn = emptyList(); onPolygonChanged(emptyList()) }) {
                androidx.compose.material3.Text("Löschen")
            }
        }
        androidx.compose.material3.Text(
            "Tippe auf die Karte, um das Gebiet zu zeichnen (mind. 3 Punkte), oder nutze die Suche.",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(51.1657, 10.4515))
                    overlays.add(
                        Polygon().apply {
                            id = "region"
                            points = drawn.map { GeoPoint(it.first, it.second) }
                            outlinePaint.color = 0xFFE8B44B.toInt()
                            outlinePaint.strokeWidth = 5f
                            setFillColor(0x33E8B44B)
                        },
                    )
                    overlays.add(
                        Polyline().apply {
                            id = "drawing"
                            setPoints(drawing)
                            outlinePaint.color = 0xFF7FB069.toInt()
                            outlinePaint.strokeWidth = 4f
                        },
                    )
                    overlays.add(
                        org.osmdroid.views.overlay.MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    if (p != null) {
                                        drawing = drawing + p
                                        val poly = overlays.first { it is Polygon } as Polygon
                                        poly.points = (drawing + p).toMutableList()
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
