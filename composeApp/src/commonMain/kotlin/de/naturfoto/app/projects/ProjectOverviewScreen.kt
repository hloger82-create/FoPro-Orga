package de.naturfoto.app.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import io.github.jan.supabase.postgrest.postgrest
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
    var memberProfiles by remember { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var isAdmin by remember { mutableStateOf(false) }
    var myIdLocal by remember { mutableStateOf<String?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var memberProfile by remember { mutableStateOf<JsonObject?>(null) }

    fun load() {
        scope.launch {
            try {
                members = repo.members(projectId)
                myIdLocal = de.naturfoto.app.data.Supabase.client.auth.currentUserOrNull()?.id
                val profs = de.naturfoto.app.data.Supabase.client.postgrest["profiles"].select {
                    filter { `in`("id", members.map { it.str("user_id") }) }
                }.decodeList<JsonObject>()
                memberProfiles = profs.associateBy { it.str("id") }
                isAdmin = members.any { it.str("user_id") == myIdLocal && it.str("role") == "admin" }
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
                Card(modifier = Modifier.fillMaxWidth(), onClick = { memberProfile = m }) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(m.str("display_name"))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(m.str("role"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            if (isAdmin && m.str("role") != "admin" && m.str("user_id") != myIdLocal) {
                                TextButton(onClick = {
                                    scope.launch {
                                        try { repo.setMemberRole(projectId, m.str("user_id"), "admin"); load() } catch (e: Exception) { error = e.message }
                                    }
                                }) { Text("Zum Admin") }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onLeaveProject) { Text("Projekt verlassen") }
        if (isAdmin) {
            androidx.compose.material3.Button(
                onClick = { showDeleteConfirm = true },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text("Projekt löschen") }
        }
    }

    memberProfile?.let { mp ->
        AlertDialog(
            onDismissRequest = { memberProfile = null },
            title = { Text(mp.str("display_name")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rolle: ${mp.str("role")}")
                    val p = memberProfiles[mp.str("user_id")]
                    p?.str("bio")?.takeIf { it.isNotBlank() }?.let { Text("„$it“") }
                    p?.str("skill_tags")?.takeIf { it.isNotBlank() }?.let { Text("Skills: $it") }
                }
            },
            confirmButton = { TextButton(onClick = { memberProfile = null }) { Text("Schließen") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Projekt wirklich löschen?") },
            text = { Text("Alle Areas, Chats, Spots, Termine und Umfragen dieses Projekts werden unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repo.deleteProject(projectId)
                                showDeleteConfirm = false
                                onLeaveProject()
                            } catch (e: Exception) { error = e.message }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Ja, löschen") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } },
        )
    }
}
