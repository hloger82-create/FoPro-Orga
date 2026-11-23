package de.naturfoto.app.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import de.naturfoto.app.data.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AreaDetailScreen(
    areaId: String,
    areaName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { AreasRepository() }
    val scope = rememberCoroutineScope()

    var forumId by remember { mutableStateOf<String?>(null) }
    var feed by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var readIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAdmin by remember { mutableStateOf(false) }
    var selectedThread by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewPost by remember { mutableStateOf(false) }
    var showNewThread by remember { mutableStateOf(false) }

    var postTitle by remember { mutableStateOf("") }
    var postAudience by remember { mutableStateOf("area") }
    var areaNameLocal by remember { mutableStateOf(areaName) }
    var projectName by remember { mutableStateOf("Projekt") }
    var projectIdLocal by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var recipientId by remember { mutableStateOf<String?>(null) }
    var recipientMenuOpen by remember { mutableStateOf(false) }
    var postBody by remember { mutableStateOf("") }
    var threadTitle by remember { mutableStateOf("") }
    var threadFirstPost by remember { mutableStateOf("") }
    var subscribed by remember { mutableStateOf(true) }

    fun loadFeed() {
        scope.launch {
            try { feed = repo.feedForArea(areaId) } catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(areaId) {
        try {
            forumId = repo.forumForArea(areaId)
            subscribed = repo.isSubscribed(areaId)
            val pid = repo.projectIdOfArea(areaId)
            isAdmin = pid != null && repo.projectMembers(pid).any { it.str("user_id") == repo.userIdSafe && it.str("role") == "admin" }
            val names = repo.areaAndProjectNames(areaId)
            areaNameLocal = names.first
            projectName = names.second
            projectIdLocal = repo.projectIdOfArea(areaId)
            if (projectIdLocal != null) members = repo.projectMembers(projectIdLocal!!)
        } catch (e: Exception) { error = e.message }
        loadFeed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showNewThread = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Neu")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← $areaName") }
                androidx.compose.material3.Switch(
                    checked = subscribed,
                    onCheckedChange = { on ->
                        scope.launch {
                            repo.setSubscribed(areaId, on)
                            subscribed = on
                        }
                    },
                )
            }
            Text(
                if (subscribed) "Area abonniert — erscheint in der Timeline" else "Nicht abonniert — nicht in der Timeline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            if (selectedThread != null) {
                ThreadDetail(thread = selectedThread!!, repo = repo, onBack = { selectedThread = null })
            } else {
                FeedList(feed, repo = repo, isAdmin = isAdmin, readIds = readIds, onPin = { id, pin ->
                    scope.launch {
                        try { repo.pinThread(id, pin); loadFeed() } catch (e: Exception) { error = e.message }
                    }
                }, onOpenThread = { selectedThread = it; de.naturfoto.app.theme.ReadStoreHolder.markRead(it.str("id")); readIds = de.naturfoto.app.theme.ReadStoreHolder.readIds() }, onDelete = { id ->
                    scope.launch {
                        try { repo.deletePost(id); loadFeed() } catch (e: Exception) { error = e.message }
                    }
                })
            }
        }

        if (showNewThread) {
            AlertDialog(
                onDismissRequest = { showNewThread = false },
                title = { Text("Neuer Thread") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(threadTitle, { threadTitle = it }, label = { Text("Threadtitel") }, singleLine = true)
                        OutlinedTextField(threadFirstPost, { threadFirstPost = it }, label = { Text("Erster Beitrag") }, minLines = 3)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = threadTitle.isNotBlank() && forumId != null,
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createThread(forumId!!, threadTitle.trim(), threadFirstPost.trim())
                                    showNewThread = false
                                    threadTitle = ""
                                    threadFirstPost = ""
                                    loadFeed()
                                } catch (e: Exception) { error = e.message }
                            }
                        },
                    ) { Text("Eröffnen") }
                },
                dismissButton = { TextButton(onClick = { showNewThread = false }) { Text("Abbrechen") } },
            )
        }

        if (showNewPost) {
            AlertDialog(
                onDismissRequest = { showNewPost = false },
                title = { Text("Neuer Pinnwand-Post") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(postTitle, { postTitle = it }, label = { Text("Titel") }, singleLine = true)
                        OutlinedTextField(postBody, { postBody = it }, label = { Text("Text") }, minLines = 3)
                        Text("Posten in:", style = MaterialTheme.typography.labelMedium)
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = postAudience == "area", onClick = { postAudience = "area" }, label = { Text(areaNameLocal) })
                            FilterChip(selected = postAudience == "project", onClick = { postAudience = "project" }, label = { Text(projectName) })
                            FilterChip(selected = postAudience == "dm", onClick = { postAudience = "dm" }, label = { Text("DM an…") })
                        }
                        if (postAudience == "dm") {
                            Text("Empfänger:", style = MaterialTheme.typography.labelMedium)
                            members.filter { it.str("user_id") != repo.userIdSafe }.forEach { m ->
                                FilterChip(
                                    selected = recipientId == m.str("user_id"),
                                    onClick = { recipientId = m.str("user_id") },
                                    label = { Text(m.str("display_name")) },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = postTitle.isNotBlank() && (postAudience != "dm" || recipientId != null),
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createPost(areaId, null, postTitle.trim(), postBody.trim(), audience = postAudience, recipientId = recipientId)
                                    showNewPost = false
                                    postTitle = ""
                                    postBody = ""
                                    loadFeed()
                                } catch (e: Exception) { error = e.message }
                            }
                        },
                    ) { Text("Posten") }
                },
                dismissButton = { TextButton(onClick = { showNewPost = false }) { Text("Abbrechen") } },
            )
        }
    }
}


@Composable
private fun FeedList(
    feed: List<JsonObject>,
    readIds: Set<String> = emptySet(),
    repo: AreasRepository,
    isAdmin: Boolean,
    onPin: (String, Boolean) -> Unit,
    onOpenThread: (JsonObject) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    var collapsedPosts by remember { mutableStateOf<Set<String>>(emptySet()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(feed, key = { it.str("kind") + it.str("id") }) { item ->
            val id = item.str("id")
            val isThread = item.str("kind") == "thread"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                onClick = { if (isThread) onOpenThread(item) },
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (id !in readIds) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                                )
                            }
                            Text((if (isThread) "🧵 " else "📌 ") + item.str("title"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        if (isThread && isAdmin) {
                            androidx.compose.material3.IconButton(onClick = { onPin(id, !item.bool("pinned")) }) {
                                Text(if (item.bool("pinned")) "📌" else "📍", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else if (item.bool("pinned")) Text("📌", style = MaterialTheme.typography.bodyMedium)
                    }
                    val body = item.str("body")
                    val expandedBody = collapsedPosts.contains(id)
                    if (body.length > 240 && !expandedBody) {
                        Text(body.take(240) + " …", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { collapsedPosts = collapsedPosts - id }) {
                            Text("▼ Mehr anzeigen")
                        }
                    } else {
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                        if (body.length > 240) {
                            TextButton(onClick = { collapsedPosts = collapsedPosts + id }) {
                                Text("▲ Weniger anzeigen")
                            }
                        }
                    }
                    Text(
                        if (isThread) "Thread — tippen zum Öffnen" else "Beitrag · ${item.str("audience")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    if (!isThread) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { expanded = if (expanded == id) null else id }) {
                                Text(if (expanded == id) "Kommentare ausblenden" else "💬 Kommentieren")
                            }
                            TextButton(onClick = { onDelete(id) }) { Text("Löschen") }
                        }
                        if (expanded == id) CommentSection(repo = repo, postId = id)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentSection(repo: AreasRepository, postId: String? = null, threadId: String? = null) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<JsonObject?>(null) }
    val key = postId ?: threadId ?: ""

    LaunchedEffect(key) {
        comments = repo.listComments(postId = postId, threadId = threadId)
    }

    val topLevel = comments.filter { it.str("reply_to").isBlank() }
    val repliesByParent = comments.filter { it.str("reply_to").isNotBlank() }.groupBy { it.str("reply_to") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        topLevel.forEach { c ->
            CommentItem(c, isReply = false, onReply = { replyTo = c; input = "" })
            (repliesByParent[c.str("id")] ?: emptyList()).forEach { r ->
                CommentItem(r, isReply = true, onReply = { replyTo = r; input = "" })
            }
        }
        if (replyTo != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Antwort auf: \u275d ${replyTo!!.str("body")}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    TextButton(onClick = { replyTo = null }) { Text("✕") }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Kommentar") },
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            repo.addComment(postId = postId, threadId = threadId, body = input.trim(), replyTo = replyTo?.str("id"))
                            input = ""
                            replyTo = null
                            comments = repo.listComments(postId = postId, threadId = threadId)
                        } catch (e: Exception) { }
                    }
                },
            ) { Text("Senden") }
        }
    }
}

@Composable
private fun CommentItem(c: JsonObject, isReply: Boolean, onReply: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isReply) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 24.dp else 0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(12.dp)) {
            c.str("quoted_text").ifBlank { null }?.let {
                Row(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(end = 8.dp).fillMaxHeight().width(3.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp)),
                    )
                    Text("\u275d $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Text(c.str("body"), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onReply) { Text("\u21a9 Antworten", style = MaterialTheme.typography.labelSmall) }
        }
    }
} 

@Composable
private fun ThreadDetail(
    thread: JsonObject,
    repo: AreasRepository,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück zu den Threads") }
        Text(thread.str("title"), style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Text(thread.str("first_post"), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                var discussionOpen by remember { mutableStateOf(true) }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Diskussion:", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { discussionOpen = !discussionOpen }) {
                        Text(if (discussionOpen) "▲ Zuklappen" else "▼ Aufklappen")
                    }
                }
                if (discussionOpen) CommentSection(repo = repo, threadId = thread.str("id"))
            }
        }
    }
}
