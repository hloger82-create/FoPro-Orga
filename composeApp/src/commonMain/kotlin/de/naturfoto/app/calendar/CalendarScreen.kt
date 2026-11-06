package de.naturfoto.app.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import de.naturfoto.app.data.str
import de.naturfoto.app.map.LocationPicker
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject

@Composable
fun CalendarScreen(
    defaultAreaId: String? = null,
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { CalendarRepository() }
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var rsvps by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val today = remember { kotlinx.datetime.LocalDate(2026, 11, 3) }
    var month by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var selectedDay by remember { mutableStateOf(today.dayOfMonth) }
    var showCreate by remember { mutableStateOf(false) }
    var areas by remember { mutableStateOf<List<kotlinx.serialization.json.JsonObject>>(emptyList()) }
    var eventAreaId by remember { mutableStateOf<String?>(defaultAreaId) }
    var showMapPicker by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            try {
                events = repo.listEvents(projectId)
                rsvps = events.associate { it.str("id") to (repo.myRsvp(it.str("id")) ?: "") }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(projectId) {
        areas = try { repo.listAreas(projectId) } catch (_: Exception) { emptyList() }
 load() }

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("on_site") }
    var time by remember { mutableStateOf("18:00") }
    var duration by remember { mutableStateOf("120") }
    var locName by remember { mutableStateOf("") }
    var locLat by remember { mutableStateOf<Double?>(null) }
    var locLng by remember { mutableStateOf<Double?>(null) }

    val eventDays = remember(events, month) {
        events.mapNotNull { it.str("start_time").take(10) }
            .filter { it.startsWith("${month.year}-${month.monthNumber.toString().padStart(2, '0')}") }
            .map { it.substringAfterLast('-').toInt() }
            .toSet()
    }
    val dayEvents = remember(events, month, selectedDay) {
        val prefix = "${month.year}-${month.monthNumber.toString().padStart(2, '0')}-${selectedDay.toString().padStart(2, '0')}"
        events.filter { it.str("start_time").startsWith(prefix) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                title = ""
                showCreate = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Termin anlegen")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { month = month.prevMonth() }) { Text("‹") }
                Text("${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { month = month.nextMonth() }) { Text("›") }
            }

            MonthCalendar(
                month = month,
                eventDays = eventDays,
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Text(
                "Termine am $selectedDay.${month.monthNumber}.${month.year}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(dayEvents, key = { it.str("id") }) { ev ->
                    val id = ev.str("id")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${ev.str("start_time").substring(11, 16)} – ${ev.str("title")}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ev.str("kind") == "online") "Online" else "📍 ${ev.str("location_name")}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("yes" to "Zusagen", "no" to "Absagen", "maybe" to "Vielleicht").forEach { (status, label) ->
                                    FilterChip(
                                        selected = rsvps[id] == status,
                                        onClick = {
                                            scope.launch {
                                                repo.setRsvp(id, status)
                                                rsvps = rsvps + (id to status)
                                            }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                                TextButton(onClick = { expanded = if (expanded == id) null else id }) {
                                    Text(if (expanded == id) "Weniger" else "Kommentare")
                                }
                            }
                            if (expanded == id) CommentSection(eventId = id, repo = repo)
                        }
                    }
                }
            }
        }

        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false },
                title = { Text("Termin am $selectedDay.${month.monthNumber}.${month.year}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true)
                        OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(time, { time = it }, label = { Text("Uhrzeit (hh:mm)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(duration, { duration = it }, label = { Text("Dauer (Min)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = kind == "on_site", onClick = { kind = "on_site" }, label = { Text("Vor Ort") })
                        Text("Für:", style = MaterialTheme.typography.labelMedium)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = eventAreaId == null, onClick = { eventAreaId = null }, label = { Text("Ganzes Projekt") })
                            areas.forEach { a -> FilterChip(selected = eventAreaId == a.str("id"), onClick = { eventAreaId = a.str("id") }, label = { Text(a.str("name")) }) }
                        }
                            FilterChip(selected = kind == "online", onClick = { kind = "online" }, label = { Text("Online") })
                        }
                        if (kind == "on_site") {
                            OutlinedTextField(locName, { locName = it }, label = { Text("Ort") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = { showMapPicker = true }) {
                                Text(if (locLat != null) "📍 ${"%.4f".format(locLat)}, ${"%.4f".format(locLng)} – auf Karte ändern" else "📍 Ort auf Karte wählen")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = title.isNotBlank() && time.matches(Regex("\\d{2}:\\d{2}")),
                        onClick = {
                            scope.launch {
                                try {
                                    val startIso = "${month.year}-${month.monthNumber.toString().padStart(2, '0')}-${selectedDay.toString().padStart(2, '0')}T${time.trim()}:00+01:00"
                                    val durMin = duration.trim().toIntOrNull() ?: 0
                                    val endIso = if (durMin > 0) {
                                        val t = LocalTime.parse(time.trim())
                                        val end = t.plusMinutes(durMin)
                                        "${month.year}-${month.monthNumber.toString().padStart(2, '0')}-${selectedDay.toString().padStart(2, '0')}T${end.hour.toString().padStart(2, '0')}:${end.minute.toString().padStart(2, '0')}:00+01:00"
                                    } else null
                                    repo.createEvent(
                                        projectId = projectId,
                                        title = title.trim(),
                                        description = desc.trim().ifBlank { null },
                                        kind = kind,
                                        locationName = locName.trim().ifBlank { null },
                                        lat = locLat,
                                        lng = locLng,
                                        start = startIso,
                                        end = endIso,
                                        maxParticipants = null,
                                    areaId = eventAreaId,
                                    )
                                    showCreate = false
                                    load()
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        },
                    ) { Text("Erstellen") }
                },
                dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Abbrechen") } },
            )
        }

        if (showMapPicker) {
            AlertDialog(
                onDismissRequest = { showMapPicker = false },
                confirmButton = { TextButton(onClick = { showMapPicker = false }) { Text("Fertig") } },
                text = {
                    LocationPicker(
                        initialLat = locLat,
                        initialLng = locLng,
                        onPicked = { la, ln, name ->
                            locLat = la
                            locLng = ln
                            if (name.isNotBlank()) locName = name
                        },
                    )
                },
            )
        }
    }
}

private fun LocalDate.prevMonth(): LocalDate =
    if (monthNumber == 1) LocalDate(year - 1, 12, 1) else LocalDate(year, monthNumber - 1, 1)

private fun LocalDate.nextMonth(): LocalDate =
    if (monthNumber == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, monthNumber + 1, 1)

private fun LocalTime.plusMinutes(min: Int): LocalTime =
    LocalTime.fromSecondOfDay(((hour * 60 + minute + min) % 1440 + 1440) % 1440 * 60)

@Composable
private fun CommentSection(eventId: String, repo: CalendarRepository) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(eventId) { comments = repo.listComments(eventId) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        comments.forEach { c -> Text("• ${c.str("body")}", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Kommentar…") },
                singleLine = true,
            )
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    scope.launch {
                        repo.addComment(eventId, input.trim())
                        input = ""
                        comments = repo.listComments(eventId)
                    }
                },
            ) { Text("Senden") }
        }
    }
}
