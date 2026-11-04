package de.naturfoto.app.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
fun AreaDetailScreen(
    areaId: String,
    areaName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { AreasRepository() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) } // 0 = Pinnwand, 1 = Forum/Threads
    var forumId by remember { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var threads by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selectedThread by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewPost by remember { mutableStateOf(false) }
    var showNewThread by remember { mutableStateOf(false) }

    var postTitle by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }
    var threadTitle by remember { mutableStateOf("") }
    var threadFirstPost by remember { mutableStateOf("") }
    var subscribed by remember { mutableStateOf(true) }

    fun loadPosts() {
        scope.launch {
            try { posts = repo.listPosts(areaId, null) } catch (e: Exception) { error = e.message }
        }
    }

    LaunchedEffect(areaId) {
        try {
            forumId = repo.forumForArea(areaId)
            if (forumId != null) threads = repo.listThreads(forumId!!)
            subscribed = repo.isSubscribed(areaId)
        } catch (e: Exception) { error = e.message }
        loadPosts()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (tab == 0) showNewPost = true else showNewThread = true
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
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; selectedThread = null }, text = { Text("Pinnwand") })
                Tab(selected = tab == 1, onClick = { tab = 1; selectedThread = null }, text = { Text("Forum") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            if (selectedThread != null) {
                ThreadDetail(thread = selectedThread!!, repo = repo, onBack = { selectedThread = null })
            } else if (tab == 0) {
                PostList(posts, repo = repo, onDelete = { id ->
                    scope.launch {
                        try { repo.deletePost(id); loadPosts() } catch (e: Exception) { error = e.message }
                    }
                })
            } else {
                ThreadList(
                    threads = threads,
                    onOpenThread = { selectedThread = it },
                )
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
                                    threads = repo.listThreads(forumId!!)
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
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = postTitle.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createPost(areaId, null, postTitle.trim(), postBody.trim())
                                    showNewPost = false
                                    postTitle = ""
                                    postBody = ""
                                    loadPosts()
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
private fun PostList(posts: List<JsonObject>, repo: AreasRepository, onDelete: (String) -> Unit) {
    var expandedPost by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(posts, key = { it.str("id") }) { post ->
            val id = post.str("id")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(post.str("title"), style = MaterialTheme.typography.titleMedium)
                        if (post.bool("pinned")) Text("📌", style = MaterialTheme.typography.bodyMedium)
                    }
                    post.str("body").let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { expandedPost = if (expandedPost == id) null else id }) {
                            Text(if (expandedPost == id) "Kommentare ausblenden" else "💬 Kommentieren")
                        }
                        TextButton(onClick = { onDelete(id) }) { Text("Löschen") }
                    }
                    if (expandedPost == id) CommentSection(repo = repo, postId = id)
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        comments.forEach { c ->
            val isReply = c.str("reply_to").isNotBlank()
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = if (isReply) 20.dp else 0.dp),
            ) {
                c.str("quoted_text").ifBlank { null }?.let {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .fillMaxHeight()
                                .width(3.dp)
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp)),
                        )
                        Text(
                            "❝ $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(c.str("body"), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    replyTo = c
                    input = ""
                }) { Text("↩ Antworten", style = MaterialTheme.typography.labelSmall) }
            }
        }
        if (replyTo != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Antwort auf: ❝ ${replyTo!!.str("body")}",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { replyTo = null; input = "" }) { Text("✕") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Kommentar…") },
                minLines = 2,
                maxLines = 5,
            )
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    scope.launch {
                        repo.addComment(
                            postId = postId,
                            threadId = threadId,
                            body = input.trim(),
                            replyTo = replyTo?.str("id"),
                            quotedText = replyTo?.str("body"),
                        )
                        input = ""
                        replyTo = null
                        comments = repo.listComments(postId = postId, threadId = threadId)
                    }
                },
            ) { Text("Senden") }
        }
    }
}

@Composable
private fun ThreadList(
    threads: List<JsonObject>,
    onOpenThread: (JsonObject) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(threads, key = { it.str("id") }) { thread ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { onOpenThread(thread) },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(thread.str("title"), style = MaterialTheme.typography.titleMedium)
                    Text(thread.str("first_post").take(120), style = MaterialTheme.typography.bodyMedium)
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(thread.str("first_post"), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item { Text("Diskussion:", style = MaterialTheme.typography.titleSmall) }
            item { CommentSection(repo = repo, threadId = thread.str("id")) }
        }
    }
}
