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
import androidx.compose.runtime.LaunchedEffect
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
    projectId: String,
    modifier: Modifier = Modifier,
) {
    val repo = remember { TimelineRepository() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        try {
            items = repo.load(projectId)
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "${it.kind}-${it.id}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                when (item.kind) {
                                    "post" -> "📌 Beitrag"
                                    "message" -> "💬 Chat"
                                    else -> "📅 Termin"
                                } + (if (item.areaName.isNotBlank()) " · ${item.areaName}" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(item.createdAt.take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        if (item.body.isNotBlank()) Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
