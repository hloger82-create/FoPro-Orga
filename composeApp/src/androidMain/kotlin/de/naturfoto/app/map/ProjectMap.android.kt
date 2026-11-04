package de.naturfoto.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.naturfoto.app.data.str
import de.naturfoto.app.theme.ScrimTop
import de.naturfoto.app.theme.translucentSurface
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
actual fun ProjectMap(projectId: String, modifier: Modifier) {
    val repo = remember { MapRepository() }
    val scope = rememberCoroutineScope()

    var region by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var spots by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var pending by remember { mutableStateOf<GeoPoint?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var admin by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("project") }

    LaunchedEffect(projectId) {
        try {
            region = repo.projectRegion(projectId)
            spots = repo.listSpots(projectId)
            admin = repo.isAdmin(projectId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(51.1657, 10.4515))
                    if (region.size >= 3) {
                        overlays.add(
                            Polygon().apply {
                                points = region.map { GeoPoint(it.first, it.second) }
                                outlinePaint.color = 0xFF7FB069.toInt()
                                outlinePaint.strokeWidth = 6f
                                setFillColor(0x227FB069)
                            },
                        )
                    }
                    overlays.add(
                        org.osmdroid.views.overlay.MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    if (p != null) pending = p
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint?): Boolean = false
                            },
                        ),
                    )
                }
            },
            update = { mapView ->
                mapView.overlays.removeAll { it is Marker }
                spots.forEach { s ->
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = GeoPoint(
                                s.str("lat").toDoubleOrNull() ?: 0.0,
                                s.str("lng").toDoubleOrNull() ?: 0.0,
                            )
                            title = s.str("title")
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        },
                    )
                }
                mapView.invalidate()
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(ScrimTop, androidx.compose.ui.graphics.Color.Transparent)))
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(translucentSurface())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("Projektgebiet · ${spots.size} Spots", style = MaterialTheme.typography.titleMedium)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        if (editingRegion) {
            AlertDialog(
                onDismissRequest = { editingRegion = false },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            repo.saveRegion(projectId, region)
                            editingRegion = false
                        }
                    }) { Text("Speichern") }
                },
                dismissButton = { TextButton(onClick = { editingRegion = false }) { Text("Abbrechen") } },
                text = {
                    RegionEditor(
                        currentPolygon = region,
                        onPolygonChanged = { region = it },
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                    )
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (admin) {
                FloatingActionButton(
                    onClick = { editingRegion = true },
                    containerColor = translucentSurface(),
                    contentColor = MaterialTheme.colorScheme.secondary,
                ) {
                    Icon(Icons.Default.Layers, contentDescription = "Projektgebiet bearbeiten")
                }
            }
            FloatingActionButton(
                onClick = { },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Eigenen Standort")
            }
            FloatingActionButton(
                onClick = { },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Ebenen")
            }
            FloatingActionButton(
                onClick = { pending = GeoPoint(51.1657, 10.4515) },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = "Spot melden")
            }
        }

        if (pending != null) {
            val p = pending!!
            val inside = repo.isInsidePolygon(p.latitude, p.longitude, region)
            AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text("Spot melden") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!inside) {
                            Text(
                                "⚠ Außerhalb des Projektgebiets — Spot wird abgelehnt.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text("📍 ${"%.5f".format(p.latitude)}, ${"%.5f".format(p.longitude)}", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true)
                        OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung") })
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = visibility == "project", onClick = { visibility = "project" }, label = { Text("Projekt") })
                            FilterChip(selected = visibility == "area_only", onClick = { visibility = "area_only" }, label = { Text("Nur Area") })
                            FilterChip(selected = visibility == "private", onClick = { visibility = "private" }, label = { Text("Privat") })
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = title.isNotBlank() && inside,
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createSpot(
                                        projectId = projectId,
                                        title = title.trim(),
                                        description = desc.trim().ifBlank { null },
                                        lat = p.latitude,
                                        lng = p.longitude,
                                        photoBytes = null,
                                        exif = null,
                                        visibility = visibility,
                                    )
                                    pending = null
                                    title = ""
                                    desc = ""
                                    spots = repo.listSpots(projectId)
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        },
                    ) { Text("Speichern") }
                },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("Abbrechen") } },
            )
        }
    }
}
