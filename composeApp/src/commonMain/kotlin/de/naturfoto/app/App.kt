package de.naturfoto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.naturfoto.app.areas.AreaDetailScreen
import de.naturfoto.app.areas.AreasScreen
import de.naturfoto.app.auth.AuthRepository
import kotlinx.coroutines.launch
import de.naturfoto.app.auth.AuthScreen
import de.naturfoto.app.calendar.CalendarScreen
import de.naturfoto.app.chat.ChatScreen
import de.naturfoto.app.map.ProjectMap
import de.naturfoto.app.ebird.EBirdScreen
import de.naturfoto.app.profile.ProfileScreen
import de.naturfoto.app.projects.ProjectOverviewScreen
import de.naturfoto.app.projects.ProjectPickerScreen
import de.naturfoto.app.projects.ProjectsRepository
import de.naturfoto.app.surveys.SurveysScreen
import de.naturfoto.app.timeline.TimelineScreen
import de.naturfoto.app.theme.NaturfotoTheme

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TIMELINE("Timeline", Icons.Default.Timeline),
    AREAS("Areas", Icons.Default.Forum),
    CHAT("Chat", Icons.Default.ChatBubbleOutline),
    MAP("Karte", Icons.Default.Map),
    MORE("Mehr", Icons.Default.MoreVert),
}

private enum class MoreEntry(val label: String) {
    CALENDAR("Kalender"),
    SURVEYS("Umfragen"),
    EBIRD("eBird"),
    PROFILE("Profil"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaturfotoApp() {
    NaturfotoTheme {
        val scope = rememberCoroutineScope()
        var signedIn by remember { mutableStateOf(AuthRepository().currentUserId() != null) }
        if (!signedIn) {
            AuthScreen(onSignedIn = { signedIn = true })
            return@NaturfotoTheme
        }
        var tab by remember { mutableStateOf(Tab.TIMELINE) }
        var moreEntry by remember { mutableStateOf<MoreEntry?>(null) }
        var projectId by remember { mutableStateOf<String?>(null) }
        var projectName by remember { mutableStateOf("") }
        var mapFocus by remember { mutableStateOf<Pair<Double, Double>?>(null) }
        var selectedArea by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showOverview by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }

        if (projectId == null) {
            ProjectPickerScreen(
                onSelectProject = { id, name ->
                    projectId = id
                    projectName = name
                },
                onLogout = {
                    scope.launch { AuthRepository().signOut() }
                    signedIn = false
                    projectId = null
                },
            )
            return@NaturfotoTheme
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(projectName.ifBlank { "Projekt" }) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Projekt-Menü")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Projekt wechseln") },
                                onClick = { menuOpen = false; projectId = null; showOverview = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Projektübersicht & Mitglieder") },
                                onClick = { menuOpen = false; showOverview = true },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; moreEntry = null },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (showOverview) {
                    ProjectOverviewScreen(
                        projectId = projectId!!,
                        onClose = { showOverview = false },
                        onLeaveProject = {
                            scope.launch {
                                ProjectsRepository().leaveProject(projectId!!)
                                showOverview = false
                                projectId = null
                            }
                        },
                    )
                    return@Box
                }
                val pid = projectId
                when {
                    moreEntry == MoreEntry.CALENDAR && pid != null -> CalendarScreen(projectId = pid)
                    moreEntry == MoreEntry.SURVEYS && pid != null -> SurveysScreen(projectId = pid)
                    moreEntry == MoreEntry.EBIRD && pid != null -> EBirdScreen(projectId = pid, onOpenOnMap = { lat, lng ->
                        mapFocus = lat to lng
                        moreEntry = null
                        tab = Tab.MAP
                    })
                    moreEntry == MoreEntry.PROFILE && pid != null -> ProfileScreen(projectId = pid, modifier = Modifier)
                    tab == Tab.TIMELINE && pid != null -> TimelineScreen(projectId = pid)
                    tab == Tab.AREAS -> {
                        val area = selectedArea
                        if (area != null) {
                            AreaDetailScreen(areaId = area.first, areaName = area.second, onBack = { selectedArea = null })
                        } else if (pid != null) {
                            AreasScreen(projectId = pid, onOpenArea = { id, name -> selectedArea = id to name })
                        }
                    }
                    tab == Tab.CHAT && pid != null -> ChatScreen(projectId = pid)
                    tab == Tab.MAP && pid != null -> ProjectMap(projectId = pid, focus = mapFocus, onFocusConsumed = { mapFocus = null }, modifier = Modifier)
                    tab == Tab.MORE -> {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mehr", style = MaterialTheme.typography.headlineSmall)
                            MoreEntry.entries.forEach { e ->
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { moreEntry = e },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(e.label) }
                            }
                        }
                    }
                    else -> PlaceholderScreen("Projekt wird geladen…")
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
