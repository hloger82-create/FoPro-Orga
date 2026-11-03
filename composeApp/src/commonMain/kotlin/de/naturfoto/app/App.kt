package de.naturfoto.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.naturfoto.app.areas.AreaDetailScreen
import de.naturfoto.app.areas.AreasRepository
import de.naturfoto.app.areas.AreasScreen
import de.naturfoto.app.auth.AuthRepository
import de.naturfoto.app.chat.ChatScreen
import de.naturfoto.app.auth.AuthScreen
import de.naturfoto.app.map.ProjectMap
import de.naturfoto.app.profile.ProfileScreen
import de.naturfoto.app.theme.NaturfotoTheme

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TIMELINE("Timeline", Icons.Default.Timeline),
    AREAS("Areas", Icons.Default.Forum),
    CHAT("Chat", Icons.Default.ChatBubbleOutline),
    CALENDAR("Kalender", Icons.Default.CalendarMonth),
    MAP("Karte", Icons.Default.Map),
    PROFILE("Profil", Icons.Default.Person),
}

@Composable
fun NaturfotoApp() {
    NaturfotoTheme {
        var signedIn by remember { mutableStateOf(AuthRepository().currentUserId() != null) }
        if (!signedIn) {
            AuthScreen(onSignedIn = { signedIn = true })
            return@NaturfotoTheme
        }
        var tab by remember { mutableStateOf(Tab.TIMELINE) }
        var projectId by remember { mutableStateOf<String?>(null) }
        var selectedArea by remember { mutableStateOf<Pair<String, String>?>(null) }
        LaunchedEffect(Unit) {
            if (projectId == null) projectId = AreasRepository().firstProjectIdForUser()
        }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (tab) {
                    Tab.TIMELINE -> PlaceholderScreen("Timeline")
                    Tab.AREAS -> {
                        val pid = projectId
                        val area = selectedArea
                        if (area != null) {
                            AreaDetailScreen(
                                areaId = area.first,
                                areaName = area.second,
                                onBack = { selectedArea = null },
                            )
                        } else if (pid != null) {
                            AreasScreen(
                                projectId = pid,
                                onOpenArea = { id, name -> selectedArea = id to name },
                            )
                        } else {
                            PlaceholderScreen("Projekt wird geladen…")
                        }
                    }
                    Tab.CHAT -> {
                        val pid = projectId
                        if (pid != null) ChatScreen(projectId = pid) else PlaceholderScreen("Projekt wird geladen…")
                    }
                    Tab.CALENDAR -> PlaceholderScreen("Kalender")
                    Tab.MAP -> ProjectMap(modifier = Modifier)
                    Tab.PROFILE -> ProfileScreen(modifier = Modifier)
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
