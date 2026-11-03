package de.naturfoto.app.chat

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

    var channels by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<String, Any?>?>(null) }
    val messages = remember { MutableStateFlow<List<Map<String, Any?>>>(emptyList()) }
    val messagesState by messages.collectAsState()
    var muted by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        try {
            channels = repo.listChannels(projectId)
            val general = channels.firstOrNull { it["is_general"] as? Boolean == true } ?: channels.firstOrNull()
            if (general != null) {
                selected = general
                messages.value = repo.listMessages(general["id"].toString())
                muted = repo.isMuted(general["id"].toString())
                repo.subscribeMessages(general["id"].toString(), messages)
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
                val isSel = selected?.get("id") == ch["id"]
                Card(
                    onClick = {
                        scope.launch {
                            selected = ch
                            messages.value = repo.listMessages(ch["id"].toString())
                            muted = repo.isMuted(ch["id"].toString())
                            repo.subscribeMessages(ch["id"].toString(), messages)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        ch["name"].toString(),
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
            items(messagesState, key = { it["id"].toString() }) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(msg["body"].toString(), style = MaterialTheme.typography.bodyLarge)
                        Text(msg["created_at"].toString().take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
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
                        repo.setMuted(ch["id"].toString(), newMuted)
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
                            repo.sendMessage(ch["id"].toString(), text)
                            messages.value = repo.listMessages(ch["id"].toString())
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                },
            ) { Text("Senden") }
        }
    }
}
