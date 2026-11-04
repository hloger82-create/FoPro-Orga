package de.naturfoto.app.map

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.naturfoto.app.data.str
import de.naturfoto.app.theme.ScrimTop
import de.naturfoto.app.theme.translucentSurface
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private enum class SpotMode { MAP_PICK }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
actual fun ProjectMap(
    projectId: String,
    focus: Pair<Double, Double>?,
    onFocusConsumed: () -> Unit,
    modifier: Modifier,
) {
    val repo = remember { MapRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var region by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var spots by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var areas by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var subscribedAreas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var authors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var admin by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf(false) }
    var layerHiking by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var locOverlay by remember { mutableStateOf<org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) locOverlay?.enableMyLocation() }

    // Spot-Dialog
    var showSpotDialog by remember { mutableStateOf(false) }
    var spotMode by remember { mutableStateOf<SpotMode?>(null) } // null = Auswahl, MAP_PICK = warte auf Karten-Tap
    var spotCoord by remember { mutableStateOf<GeoPoint?>(null) }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("project") }
    var spotArea by remember { mutableStateOf<String?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exif by remember { mutableStateOf<Map<String, String>?>(null) }
    var areaMenuOpen by remember { mutableStateOf(false) }

    // Spot-Detail
    var selectedSpot by remember { mutableStateOf<JsonObject?>(null) }

    // Drawer-Filter
    val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
    var showAll by remember { mutableStateOf(true) }
    var hiddenAreas by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPrivate by remember { mutableStateOf(true) }
    var showEBird by remember { mutableStateOf(false) }
    var ebirdObs by remember { mutableStateOf<List<JsonObject>>(emptyList()) }

    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        mapViewRef?.controller?.animateTo(GeoPoint(f.first, f.second), 15.0, 800L)
        onFocusConsumed()
    }

    fun loadEBird() {
        scope.launch {
            try {
                val key = de.naturfoto.app.ebird.EBirdRepository().apiKey() ?: return@launch
                val center = de.naturfoto.app.ebird.EBirdRepository.regionCenter(region) ?: return@launch
                ebirdObs = de.naturfoto.app.ebird.EBirdRepository().recentObservations(
                    key, center.first, center.second, de.naturfoto.app.ebird.EBirdRepository.regionRadiusKm(region), backDays = 3,
                )
            } catch (_: Exception) { }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    photoBytes = bytes
                    val ex = mutableMapOf<String, String>()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ExifInterface(stream).let { e ->
                            e.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let { ex["gps_latitude"] = it }
                            e.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)?.let { ex["gps_longitude"] = it }
                            e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { ex["datetime"] = it }
                            e.getAttribute(ExifInterface.TAG_MODEL)?.let { ex["camera"] = it }
                        }
                    }
                    // GPS aus EXIF in Dezimalgrad umrechnen
                    val latRef = ex["gps_latitude"]
                    if (latRef != null) {
                        val parts = latRef.split(",").map { it.trim() }
                        val lat = parts[0].toDouble() + parts[1].toDouble() / 60.0 + parts[2].toDouble() / 3600.0
                        val lon = ex["gps_longitude"]?.split(",")?.map { it.trim() }?.let {
                            it[0].toDouble() + it[1].toDouble() / 60.0 + it[2].toDouble() / 3600.0
                        }
                        if (lon != null) {
                            spotCoord = GeoPoint(lat, lon)
                            exif = ex
                        }
                    }
                } catch (e: Exception) {
                    error = "Foto/EXIF: ${e.message}"
                }
            }
        }
    }

    fun loadSpots() {
        scope.launch {
            try {
                spots = repo.listSpots(projectId)
                authors = spots.map { it.str("author_id") }.distinct().associateWith { repo.authorName(it) }
            } catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(projectId) {
        try {
            region = repo.projectRegion(projectId)
            areas = repo.listAreas(projectId)
            subscribedAreas = repo.subscribedAreaIds(projectId)
            admin = repo.isAdmin(projectId)
        } catch (e: Exception) { error = e.message }
        loadSpots()
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier.fillMaxSize(),
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Column(modifier = Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Spots anzeigen", style = MaterialTheme.typography.titleMedium)
                    FilterRow("Alle", showAll) { showAll = it }
                    areas.forEach { a ->
                        FilterRow(a.str("name"), showAll || a.str("id") !in hiddenAreas) { on ->
                            hiddenAreas = if (on) hiddenAreas - a.str("id") else hiddenAreas + a.str("id")
                            showAll = false
                        }
                    }
                    FilterRow("Privat (eigene)", showPrivate) { showPrivate = it }
                    FilterRow("eBird-Meldungen", showEBird) { showEBird = it; if (it) loadEBird() }
                }
            }
        },
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(51.1657, 10.4515))
                    locOverlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
                        org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(ctx), this,
                    ).apply { disableMyLocation() }
                    overlays.add(locOverlay!!)
                    overlays.add(
                        MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    if (p != null && spotMode == SpotMode.MAP_PICK) {
                                        spotCoord = p
                                        spotMode = null
                                    }
                                    return true
                                }
                                // Wird unten nicht gebraucht, MapEventsReceiver verlangt beide Methoden
                                override fun longPressHelper(p: GeoPoint?): Boolean {
                                    if (p != null && spotMode == SpotMode.MAP_PICK) {
                                        spotCoord = p
                                        spotMode = null
                                        return true
                                    }
                                    return false
                                }
                            },
                        ),
                    )
                }
            },
            update = { mapView ->
                mapViewRef = mapView
                mapView.overlays.removeAll { it is Marker || it is Polygon }
                if (region.size >= 3) {
                    mapView.overlays.add(
                        object : Polygon() {
                            override fun onSingleTapUp(e: android.view.MotionEvent, mapView: MapView): Boolean = false
                        }.apply {
                            points = region.map { GeoPoint(it.first, it.second) }
                            outlinePaint.color = 0xFF7FB069.toInt()
                            outlinePaint.strokeWidth = 6f
                            setFillColor(0x227FB069)
                        },
                    )
                }
                spots.forEach { s ->
                    val spotArea = s.str("area_id").ifBlank { null }
                    // Nur anzeigen: eigene/private Spots, keine Area, oder abonnierte Area
                    val visible = s.str("author_id") == repo.userId
                            || spotArea == null
                            || spotArea in subscribedAreas
                    if (!visible) return@forEach
                    if (!showAll && spotArea != null && spotArea in hiddenAreas) return@forEach
                    if (!showPrivate && s.str("visibility") == "private") return@forEach
                    if (showAll && spotArea != null) return@forEach
                    val color = repo.areaColor(spotArea)
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = GeoPoint(
                                s.str("lat").toDoubleOrNull() ?: 0.0,
                                s.str("lng").toDoubleOrNull() ?: 0.0,
                            )
                            title = s.str("title")
                            icon?.mutate()?.setTint(color)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                selectedSpot = s
                                true
                            }
                        },
                    )
                }
                if (showEBird) {
                    ebirdObs.forEach { o ->
                        o.str("lat").toDoubleOrNull()?.let { lat ->
                            o.str("lng").toDoubleOrNull()?.let { lng ->
                                mapView.overlays.add(
                                    Marker(mapView).apply {
                                        position = GeoPoint(lat, lng)
                                        title = "🐦 ${o.str("comName")}"
                                        icon?.mutate()?.setTint(0xFF3B82F6.toInt())
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        setOnMarkerClickListener { _, _ ->
                                            selectedSpot = null
                                            error = "eBird: ${o.str("comName")} — ${o.str("obsDt")} ${o.str("locName")}"
                                            true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                mapView.invalidate()
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(ScrimTop, Color.Transparent)))
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
                Text(
                    if (region.size >= 3) "Projektgebiet: ${region.size} Grenzpunkte · ${spots.size} Locations" else "Kein Projektgebiet · ${spots.size} Locations",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            androidx.compose.material3.IconButton(
                onClick = { scope.launch { drawerState.open() } },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Filter öffnen", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (admin) {
                MapFab(Icons.Default.EditLocation, "Gebiet bearbeiten", MaterialTheme.colorScheme.secondary) { editingRegion = true }
            }
            MapFab(Icons.Default.MyLocation, "Mein Standort", MaterialTheme.colorScheme.secondary) {
                mapViewRef?.let { mv ->
                    locOverlay?.enableMyLocation()
                    locOverlay?.enableFollowLocation()
                    locOverlay?.myLocation?.let { mv.controller.animateTo(it, 16.0, 800L) }
                }
            }
            MapFab(Icons.Default.Layers, if (layerHiking) "Ebenen: Topo an" else "Ebenen: Topo aus", MaterialTheme.colorScheme.secondary) {
                layerHiking = !layerHiking
                mapViewRef?.setTileSource(if (layerHiking) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
            }
            MapFab(Icons.Default.AddLocationAlt, "Location eintragen", MaterialTheme.colorScheme.primary) {
                showSpotDialog = true
                spotMode = null
                spotCoord = null
                title = ""
                desc = ""
                spotArea = null
                photoBytes = null
                exif = null
            }
        }

        if (spotMode == SpotMode.MAP_PICK) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(translucentSurface())
                    .padding(12.dp),
            ) {
                Text("Tippe auf die Karte, um die Location zu wählen")
            }
        }

        if (editingRegion) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { editingRegion = false }) {
                Card(modifier = Modifier.fillMaxWidth().height(520.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Text("Projektgebiet festlegen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                        RegionEditor(currentPolygon = region, onPolygonChanged = { region = it }, modifier = Modifier.fillMaxWidth().height(400.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingRegion = false }) { Text("Abbrechen") }
                            TextButton(onClick = {
                                scope.launch {
                                    repo.saveRegion(projectId, region)
                                    editingRegion = false
                                }
                            }) { Text("Speichern") }
                        }
                    }
                }
            }
        }

        if (showSpotDialog && spotMode != SpotMode.MAP_PICK) {
            AlertDialog(
                onDismissRequest = { showSpotDialog = false },
                title = { Text("Location eintragen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                        if (spotMode == null && spotCoord == null) {
                            Text("Koordinaten bestimmen:", style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = {
                                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                                locOverlay?.enableMyLocation()
                                scope.launch {
                                    repeat(10) {
                                        kotlinx.coroutines.delay(1000)
                                        locOverlay?.myLocation?.let { spotCoord = it }
                                        if (spotCoord != null) return@repeat
                                    }
                                    if (spotCoord == null) error = "Kein GPS-Fix — im Emulator: rechts „…“ → Location → Koordinaten → Send"
                                }
                            }) { Text("📍 Mein Standort") }
                            TextButton(onClick = { spotMode = SpotMode.MAP_PICK }) { Text("🗺 Auf Karte auswählen") }
                            TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("🖼 Geo-Daten aus Foto") }
                        }
                        spotCoord?.let {
                            val insideNow = repo.isInsidePolygon(it.latitude, it.longitude, region)
                            Text("📍 ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}", style = MaterialTheme.typography.labelSmall)
                            if (!insideNow) Text("⚠ Außerhalb des Projektgebiets — Spot kann nicht gespeichert werden.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedTextField(title, { title = it }, label = { Text("Titel *") }, singleLine = true)
                        OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung (optional)") }, minLines = 2)
                        ExposedDropdownMenuBox(expanded = areaMenuOpen, onExpandedChange = { areaMenuOpen = it }) {
                            OutlinedTextField(
                                value = areas.firstOrNull { it.str("id") == spotArea }?.str("name") ?: "Keine Area",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Area (optional)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(areaMenuOpen) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                            )
                            androidx.compose.material3.DropdownMenu(expanded = areaMenuOpen, onDismissRequest = { areaMenuOpen = false }) {
                                DropdownMenuItem(text = { Text("Keine Area") }, onClick = { spotArea = null; areaMenuOpen = false })
                                areas.forEach { a ->
                                    DropdownMenuItem(text = { Text(a.str("name")) }, onClick = { spotArea = a.str("id"); areaMenuOpen = false })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = visibility == "project", onClick = { visibility = "project" }, label = { Text("Projekt") })
                            FilterChip(selected = visibility == "area_only", onClick = { visibility = "area_only" }, label = { Text("Nur Area") })
                            FilterChip(selected = visibility == "private", onClick = { visibility = "private" }, label = { Text("Privat") })
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = title.isNotBlank() && spotCoord != null,
                        onClick = {
                            val p = spotCoord!!
                            val inside = repo.isInsidePolygon(p.latitude, p.longitude, region)
                            if (!inside) {
                                error = "Außerhalb des Projektgebiets — nicht speicherbar."
                                return@TextButton
                            }
                            scope.launch {
                                try {
                                    repo.createSpot(
                                        projectId = projectId,
                                        title = title.trim(),
                                        description = desc.trim().ifBlank { null },
                                        lat = p.latitude,
                                        lng = p.longitude,
                                        photoBytes = photoBytes,
                                        exif = exif,
                                        visibility = visibility,
                                        areaId = spotArea,
                                    )
                                    showSpotDialog = false
                                    loadSpots()
                                } catch (e: Exception) { error = e.message }
                            }
                        },
                    ) { Text("Speichern") }
                },
                dismissButton = { TextButton(onClick = { showSpotDialog = false; spotMode = null }) { Text("Abbrechen") } },
            )
        }

        selectedSpot?.let { s ->
            AlertDialog(
                onDismissRequest = { selectedSpot = null },
                title = { Text(s.str("title")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Von: ${authors[s.str("author_id")] ?: "…"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        s.str("description").ifBlank { null }?.let { Text(it) }
                        Text("📍 ${s.str("lat")}, ${s.str("lng")}", style = MaterialTheme.typography.labelSmall)
                        val areaName = areas.firstOrNull { it.str("id") == s.str("area_id") }?.str("name")
                        if (areaName != null) Text("Area: $areaName", style = MaterialTheme.typography.labelSmall)
                        Text("Sichtbarkeit: ${s.str("visibility")}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                confirmButton = { TextButton(onClick = { selectedSpot = null }) { Text("Schließen") } },
            )
        }
    }
}

@Composable
private fun FilterRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Checkbox(checked, onChecked)
    }
}

@Composable
private fun MapFab(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FloatingActionButton(onClick = onClick, containerColor = translucentSurface(), contentColor = color) {
            Icon(icon, contentDescription = label)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .height(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(translucentSurface(0.6f))
                .padding(horizontal = 6.dp),
        )
    }
}
