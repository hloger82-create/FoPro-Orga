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
    LaunchedEffect(projectId) { load() }

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.str("question"), style = MaterialTheme.typography.titleMedium)
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
                                        RadioButton(selected = selected, onClick = { scope.launch { repo.setVote(id, listOf(idx)); myVotes = myVotes + (id to setOf(idx)); counts = counts + (id to repo.voteCounts(id)) } })
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mehrfachauswahl")
                            Switch(multiple, { multiple = it })
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = question.isNotBlank() && optionsText.lines().count { it.isNotBlank() } >= 2,
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createSurvey(
                                        projectId = projectId,
                                        scope = "project",
                                        areaId = null,
                                        question = question.trim(),
                                        options = optionsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                        multipleChoice = multiple,
                                    )
                                    showCreate = false
                                    question = ""
                                    optionsText = ""
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

