package de.naturfoto.app.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun ProjectOverviewScreen(
    projectId: String,
    onClose: () -> Unit,
    onLeaveProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { ProjectsRepository() }
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            try {
                members = repo.members(projectId)
                val myId = de.naturfoto.app.data.Supabase.client.auth.currentUserOrNull()?.id
                isAdmin = members.any { it.str("user_id") == myId && it.str("role") == "admin" }
            } catch (e: Exception) { error = e.message }
        }
    }
    LaunchedEffect(projectId) { load() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Projektübersicht", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Schließen") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (isAdmin) {
            OutlinedButton(onClick = {
                scope.launch {
                    try { inviteCode = repo.createInvite(projectId) } catch (e: Exception) { error = e.message }
                }
            }) { Text("Einladungscode erstellen") }
            inviteCode?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Einladungscode: $it — teilen zum Beitreten", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Text("Mitglieder (${members.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(members, key = { it.str("user_id") }) { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.str("display_name"))
                        Text(m.str("role"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        OutlinedButton(onClick = onLeaveProject) { Text("Projekt verlassen") }
    }
}
