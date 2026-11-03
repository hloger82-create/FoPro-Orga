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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.naturfoto.app.data.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun CalendarScreen(
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { CalendarRepository() }
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var rsvps by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(projectId) { load() }

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("on_site") }
    var loc by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("2026-11-10T18:00:00+01:00") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Termin erstellen")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(events, key = { it.str("id") }) { ev ->
                    val id = ev.str("id")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(ev.str("title"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${ev.str("start_time").take(16).replace('T', ' ')} · ${if (ev.str("kind") == "online") "online" else ev.str("location_name")}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ev.str("description").ifBlank { null }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
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
                                val counts = run { rsvps }
                                TextButton(onClick = { expanded = if (expanded == id) null else id }) {
                                    Text(if (expanded == id) "Weniger" else "Kommentare")
                                }
                            }
                            if (expanded == id) {
                                CommentSection(eventId = id, repo = repo)
                            }
                        }
                    }
                }
            }
        }

        if (showCreate) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showCreate = false },
                title = { Text("Neuer Termin") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true)
                        OutlinedTextField(desc, { desc = it }, label = { Text("Beschreibung") })
                        OutlinedTextField(start, { start = it }, label = { Text("Start (z. B. 2026-11-10T18:00:00+01:00)") }, singleLine = true)
                        OutlinedTextField(loc, { loc = it }, label = { Text("Ort (nur bei vor Ort)") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = kind == "on_site", onClick = { kind = "on_site" }, label = { Text("Vor Ort") })
                            FilterChip(selected = kind == "online", onClick = { kind = "online" }, label = { Text("Online") })
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createEvent(
                                        projectId = projectId,
                                        title = title.trim(),
                                        description = desc.trim().ifBlank { null },
                                        kind = kind,
                                        locationName = loc.trim().ifBlank { null },
                                        lat = null,
                                        lng = null,
                                        start = start.trim(),
                                        end = null,
                                        maxParticipants = null,
                                    )
                                    showCreate = false
                                    title = ""
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
    }
}

@Composable
private fun CommentSection(eventId: String, repo: CalendarRepository) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(eventId) {
        comments = repo.listComments(eventId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        comments.forEach { c ->
            Text("• ${c.str("body")}", style = MaterialTheme.typography.bodySmall)
        }
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
