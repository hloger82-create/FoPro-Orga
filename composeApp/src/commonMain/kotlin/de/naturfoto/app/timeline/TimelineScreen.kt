package de.naturfoto.app.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TimelineScreen(
    onOpenItem: (String, String) -> Unit = { _, _ -> },
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { TimelineRepository() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            try {
                items = repo.load(projectId)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(projectId) { load() }

    Column(modifier = modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.IconButton(onClick = { scope.launch { load() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
            }
        }
        DisposableEffect(projectId) {
            scope.launch { load() }
            onDispose { }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { "${it.kind}-${it.id}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenItem(item.kind, item.id) },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                when (item.kind) {
                                    "post" -> "📌 Beitrag"
                                    "thread" -> "🧵 Thread"
                                    "comment" -> "💬 Antwort"
                                    "message" -> "💬 Chat"
                                    else -> "📅 Termin"
                                } + (if (item.areaName.isNotBlank()) " · ${item.areaName}" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(item.createdAt.take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (item.body.isNotBlank()) {
                            var bodyOpen by remember { mutableStateOf(false) }
                            if (item.body.length > 160 && !bodyOpen) {
                                Text(item.body.take(160) + " …", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { bodyOpen = true }) { Text("▼ Mehr") }
                            } else {
                                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                                if (item.body.length > 160) TextButton(onClick = { bodyOpen = false }) { Text("▲ Weniger") }
                            }
                        }
                    }
                }
            }
        }
    }
}
