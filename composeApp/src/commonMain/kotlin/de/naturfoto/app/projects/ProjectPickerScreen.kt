package de.naturfoto.app.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.naturfoto.app.data.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun ProjectPickerScreen(
    onSelectProject: (String, String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { ProjectsRepository() }
    val scope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            try { projects = repo.myProjects() } catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Deine Projekte", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text("Abmelden") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(projects, key = { it.str("id") }) { p ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { onSelectProject(p.str("id"), p.str("name")) }) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.str("name"), style = MaterialTheme.typography.titleMedium)
                        Text("Rolle: ${p.str("my_role")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCreate = true }) { Text("Projekt erstellen") }
            OutlinedButton(onClick = { showJoin = true }) { Text("Mit Code beitreten") }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Neues Projekt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Projektname") }, singleLine = true)
                    OutlinedTextField(newDesc, { newDesc = it }, label = { Text("Beschreibung (optional)") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                val id = repo.createProject(newName.trim(), newDesc.trim().ifBlank { null })
                                showCreate = false
                                onSelectProject(id, newName.trim())
                            } catch (e: Exception) { error = e.message }
                        }
                    },
                ) { Text("Erstellen (du wirst Admin)") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Abbrechen") } },
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = { showJoin = false },
            title = { Text("Projekt beitreten") },
            text = { OutlinedTextField(joinCode, { joinCode = it }, label = { Text("Einladungscode") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = joinCode.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val res = repo.joinByCode(joinCode)
                            res.onSuccess { id ->
                                showJoin = false
                                load()
                                onSelectProject(id, projects.firstOrNull { it.str("id") == id }?.str("name") ?: "Projekt")
                            }.onFailure { error = it.message }
                        }
                    },
                ) { Text("Beitreten") }
            },
            dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Abbrechen") } },
        )
    }
}
