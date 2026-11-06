package de.naturfoto.app.surveys

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.list
import de.naturfoto.app.data.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun SurveysScreen(
    defaultAreaId: String? = null,
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { SurveyRepository() }
    val scope = rememberCoroutineScope()

    var surveys by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var myVotes by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var counts by remember { mutableStateOf<Map<String, Map<Int, Int>>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    var question by remember { mutableStateOf("") }
    var optionsText by remember { mutableStateOf("") }
    var multiple by remember { mutableStateOf(false) }
    var surveyAreaId by remember { mutableStateOf<String?>(defaultAreaId) }
    var surveyAreas by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            try {
                surveys = repo.listSurveys(projectId)
                myVotes = surveys.associate { it.str("id") to repo.myVotes(it.str("id")) }
                counts = surveys.associate { it.str("id") to repo.voteCounts(it.str("id")) }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(projectId) {
        load()
        surveyAreas = try { de.naturfoto.app.areas.AreasRepository().listAreas(projectId) } catch (_: Exception) { emptyList() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "Umfrage erstellen")
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
                items(surveys, key = { it.str("id") }) { s ->
                    val id = s.str("id")
                    val opts = s.list("options")
                    val my = myVotes[id] ?: emptySet()
                    val c = counts[id] ?: emptyMap()
                    val total = c.values.sum().coerceAtLeast(1)
                    val nowIso = kotlinx.datetime.Clock.System.now().toString()
                    val expired = repo.isExpired(s, nowIso)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.str("question"), style = MaterialTheme.typography.titleMedium)
                                if (expired) Text("abgelaufen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            s.str("ends_at").ifBlank { null }?.let {
                                Text("endet ${it.take(16).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(
                                if (s.str("scope") == "area") "Area-Umfrage" else "Projekt-Umfrage",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            opts.forEachIndexed { idx, opt ->
                                val selected = idx in my
                                val pct = (c[idx] ?: 0) * 100 / total
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.bool("multiple_choice")) {
                                        Checkbox(
                                            checked = selected,
                                            enabled = !expired,
                                            onCheckedChange = { checked ->
                                                val next = if (checked) my + idx else my - idx
                                                myVotes = myVotes + (id to next)
                                                scope.launch {
                                                    repo.setVote(id, next.toList())
                                                    counts = counts + (id to repo.voteCounts(id))
                                                }
                                            },
                                        )
                                    } else {
                                        RadioButton(
                                            selected = selected,
                                            enabled = !expired,
                                            onClick = { scope.launch { repo.setVote(id, listOf(idx)); myVotes = myVotes + (id to setOf(idx)); counts = counts + (id to repo.voteCounts(id)) } },
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(opt, style = MaterialTheme.typography.bodyMedium)
                                        LinearProgressIndicator(
                                            progress = { pct / 100f },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Text("$pct% (${c[idx] ?: 0})", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false },
                title = { Text("Neue Umfrage") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(question, { question = it }, label = { Text("Frage") }, singleLine = true)
                        OutlinedTextField(optionsText, { optionsText = it }, label = { Text("Optionen (eine pro Zeile)") }, minLines = 3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(dateText, { dateText = it.filter(Char::isDigit).take(6) }, label = { Text("Datum DDMMYY") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(timeText, { timeText = it.filter { c -> c.isDigit() || c == ':' }.take(5) }, label = { Text("Uhrzeit hh:MM") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mehrfachauswahl")
                            Switch(multiple, { multiple = it })
                        }
                        Text("Gilt für:", style = MaterialTheme.typography.labelMedium)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.FilterChip(selected = surveyAreaId == null, onClick = { surveyAreaId = null }, label = { Text("Ganzes Projekt") })
                            surveyAreas.forEach { a -> androidx.compose.material3.FilterChip(selected = surveyAreaId == a.str("id"), onClick = { surveyAreaId = a.str("id") }, label = { Text(a.str("name")) }) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = question.isNotBlank() && optionsText.lines().count { it.isNotBlank() } >= 2
                                && (dateText.isBlank() || dateText.matches(Regex("\\d{6}"))) && (timeText.isBlank() || timeText.matches(Regex("\\d{2}:\\d{2}")))
                                && !(dateText.isNotBlank() && timeText.isBlank()) && !(dateText.isBlank() && timeText.isNotBlank()),
                        onClick = {
                            scope.launch {
                                try {
                                    val endsAt = parseDeadline(dateText.trim(), timeText.trim())
                                    repo.createSurvey(
                                        projectId = projectId,
                                        scope = if (surveyAreaId == null) "project" else "area",
                                        areaId = surveyAreaId,
                                        question = question.trim(),
                                        options = optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                        multipleChoice = multiple,
                                        endsAt = endsAt,
                                    )
                                    showCreate = false
                                    question = ""
                                    optionsText = ""
                                    dateText = ""
                                    timeText = ""
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

private fun parseDeadline(dateText: String, timeText: String): String? {
    if (dateText.length != 6 || timeText.length != 5) return null
    val dd = dateText.substring(0, 2).toIntOrNull() ?: return null
    val mm = dateText.substring(2, 4).toIntOrNull() ?: return null
    val yy = dateText.substring(4, 6).toIntOrNull() ?: return null
    val hh = timeText.substring(0, 2).toIntOrNull() ?: return null
    val mi = timeText.substring(3, 5).toIntOrNull() ?: return null
    if (mm !in 1..12 || dd !in 1..31 || hh > 23 || mi > 59) return null
    return "%04d-%02d-%02dT%02d:%02d:00+01:00".format(2000 + yy, mm, dd, hh, mi)
}

