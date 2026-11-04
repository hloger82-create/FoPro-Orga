package de.naturfoto.app.areas

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AreaDetailScreen(
    areaId: String,
    areaName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { AreasRepository() }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) } // 0 = Pinnwand, 1 = Foren
    var forums by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var posts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var threads by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selectedForum by remember { mutableStateOf<String?>(null) }
    var selectedThread by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewForum by remember { mutableStateOf(false) }
    var showNewPost by remember { mutableStateOf(false) }
    var showNewThread by remember { mutableStateOf(false) }

    var forumName by remember { mutableStateOf("") }
    var postTitle by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }
    var threadTitle by remember { mutableStateOf("") }
    var threadFirstPost by remember { mutableStateOf("") }

    fun loadPosts() {
        scope.launch {
            try {
                posts = repo.listPosts(areaId, selectedForum)
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(areaId) {
        try {
            forums = repo.listForums(areaId)
        } catch (e: Exception) {
            error = e.message
        }
        loadPosts()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when {
                    tab == 0 -> showNewPost = true
                    selectedForum != null && selectedThread == null -> showNewThread = true
                    else -> showNewForum = true
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Neu")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(areaName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; selectedForum = null; loadPosts() }, text = { Text("Pinnwand") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Foren") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            if (tab == 0) {
                PostList(posts, repo = repo, onDelete = { id ->
                    scope.launch {
                        try { repo.deletePost(id); loadPosts() } catch (e: Exception) { error = e.message }
                    }
                })
            } else if (selectedThread != null) {
                ThreadDetail(
                    thread = selectedThread!!,
                    repo = repo,
                    onBack = { selectedThread = null },
                )
            } else if (selectedForum != null) {
                ThreadList(
                    threads = threads,
                    onOpenThread = { selectedThread = it },
                    onBack = {
                        selectedForum = null
                        tab = 0
                        loadPosts()
                    },
                )
            } else {
                ForumList(
                    forums = forums,
                    onSelectForum = { fid ->
                        scope.launch {
                            selectedForum = fid
                            threads = repo.listThreads(fid)
                        }
                    },
                )
            }
        }

        if (showNewForum) {
            AlertDialog(
                onDismissRequest = { showNewForum = false },
                title = { Text("Neues Forum") },
                text = { OutlinedTextField(forumName, { forumName = it }, label = { Text("Name") }, singleLine = true) },
                confirmButton = {
                    TextButton(
                        enabled = forumName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createForum(areaId, forumName.trim())
                                    showNewForum = false
                                    forumName = ""
                                    forums = repo.listForums(areaId)
                                } catch (e: Exception) { error = e.message }
                            }
                        },
                    ) { Text("Erstellen") }
                },
                dismissButton = { TextButton(onClick = { showNewForum = false }) { Text("Abbrechen") } },
            )
        }

        if (showNewThread) {
            AlertDialog(
                onDismissRequest = { showNewThread = false },
                title = { Text("Neuer Thread") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(threadTitle, { threadTitle = it }, label = { Text("Threadtitel (vorgegeben)") }, singleLine = true)
                        OutlinedTextField(threadFirstPost, { threadFirstPost = it }, label = { Text("Erster Beitrag") }, minLines = 3)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = threadTitle.isNotBlank() && selectedForum != null,
                        onClick = {
                            scope.launch {
                                try {
                                    repo.createThread(selectedForum!!, threadTitle.trim(), threadFirstPost.trim())
                                    showNewThread = false
                                    threadTitle = ""
                                    threadFirstPost = ""
                                    threads = repo.listThreads(selectedForum!!)
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
                title = { Text(if (selectedForum == null) "Neuer Pinnwand-Post" else "Neuer Beitrag") },
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
                                    repo.createPost(areaId, selectedForum, postTitle.trim(), postBody.trim())
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
    val key = postId ?: threadId ?: ""

    LaunchedEffect(key) {
        comments = repo.listComments(postId = postId, threadId = threadId)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        comments.forEach { c -> Text("• ${c.str("body")}", style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Kommentar…") },
                singleLine = true,
            )
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    scope.launch {
                        repo.addComment(postId = postId, threadId = threadId, body = input.trim())
                        input = ""
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
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text("← Zurück zu den Foren") }
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
}

@Composable
private fun ThreadDetail(
    thread: JsonObject,
    repo: AreasRepository,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text(thread.str("title"), style = MaterialTheme.typography.headlineSmall)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(thread.str("first_post"), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Text("Diskussion:", style = MaterialTheme.typography.titleSmall)
        CommentSection(repo = repo, threadId = thread.str("id"))
    }
}

@Composable
private fun ForumList(
    forums: List<JsonObject>,
    selectedForum: String?,
    onSelectForum: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(forums, key = { it.str("id") }) { forum ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { onSelectForum(forum.str("id")) },
            ) {
                Text(forum.str("name"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
