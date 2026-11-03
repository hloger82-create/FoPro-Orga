package de.naturfoto.app.areas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    var forums by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var posts by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selectedForum by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewForum by remember { mutableStateOf(false) }
    var showNewPost by remember { mutableStateOf(false) }

    var forumName by remember { mutableStateOf("") }
    var postTitle by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }

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
                if (tab == 0) showNewPost = true else showNewForum = true
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
                PostList(posts, onDelete = { id ->
                    scope.launch {
                        try { repo.deletePost(id); loadPosts() } catch (e: Exception) { error = e.message }
                    }
                })
            } else {
                ForumList(
                    forums = forums,
                    selectedForum = selectedForum,
                    onSelectForum = { fid ->
                        selectedForum = fid
                        tab = 0
                        loadPosts()
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
private fun PostList(posts: List<Map<String, Any?>>, onDelete: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(posts, key = { it["id"].toString() }) { post ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(post["title"].toString(), style = MaterialTheme.typography.titleMedium)
                        if (post["pinned"] as? Boolean == true) Text("📌", style = MaterialTheme.typography.bodyMedium)
                    }
                    post["body"]?.toString()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { onDelete(post["id"].toString()) }) { Text("Löschen") }
                }
            }
        }
    }
}

@Composable
private fun ForumList(
    forums: List<Map<String, Any?>>,
    selectedForum: String?,
    onSelectForum: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(forums, key = { it["id"].toString() }) { forum ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { onSelectForum(forum["id"].toString()) },
            ) {
                Text(forum["name"].toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
