package de.naturfoto.app.chat

import de.naturfoto.app.data.bool
import de.naturfoto.app.data.str
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { ChatRepository() }
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selected by remember { mutableStateOf<JsonObject?>(null) }
    val messages = remember { MutableStateFlow<List<JsonObject>>(emptyList()) }
    val messagesState by messages.collectAsState()
    var muted by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        try {
            channels = repo.listChannels(projectId)
            val general = channels.firstOrNull { it.bool("is_general") } ?: channels.firstOrNull()
            if (general != null) {
                selected = general
                messages.value = repo.listMessages(general.str("id"))
                muted = repo.isMuted(general.str("id"))
                repo.subscribeMessages(general.str("id"), messages)
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            channels.forEach { ch ->
                val isSel = selected?.str("id") == ch.str("id")
                Card(
                    onClick = {
                        scope.launch {
                            selected = ch
                            messages.value = repo.listMessages(ch.str("id"))
                            muted = repo.isMuted(ch.str("id"))
                            repo.subscribeMessages(ch.str("id"), messages)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        ch.str("name"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

        val listState = rememberLazyListState()
        LaunchedEffect(messagesState.size) {
            if (messagesState.isNotEmpty()) listState.animateScrollToItem(messagesState.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messagesState, key = { it.str("id") }) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(msg.str("body"), style = MaterialTheme.typography.bodyLarge)
                        Text(msg.str("created_at").take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected?.let { ch ->
                IconButton(onClick = {
                    scope.launch {
                        val newMuted = !muted
                        repo.setMuted(ch.str("id"), newMuted)
                        muted = newMuted
                    }
                }) {
                    Icon(
                        if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (muted) "Stumm aus" else "Stummschalten",
                    )
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nachricht…") },
                singleLine = true,
            )
            TextButton(
                enabled = input.isNotBlank() && selected != null,
                onClick = {
                    val ch = selected ?: return@TextButton
                    val text = input
                    input = ""
                    scope.launch {
                        try {
                            repo.sendMessage(ch.str("id"), text)
                            messages.value = repo.listMessages(ch.str("id"))
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
            ) { Text("Senden") }
        }
    }
}
