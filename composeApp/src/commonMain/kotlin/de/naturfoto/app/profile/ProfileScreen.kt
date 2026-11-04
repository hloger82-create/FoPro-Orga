package de.naturfoto.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.naturfoto.app.data.Supabase
import de.naturfoto.app.data.bool
import de.naturfoto.app.data.jsonOf
import de.naturfoto.app.data.obj
import de.naturfoto.app.data.list
import de.naturfoto.app.data.str
import kotlinx.serialization.json.JsonObject
import de.naturfoto.app.data.textOrNull
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val userId = Supabase.client.auth.currentUserOrNull()?.id

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var homepage by remember { mutableStateOf("") }
    var socials by remember { mutableStateOf("") }
    var skillTags by remember { mutableStateOf("") }
    var showHomepagePublicly by remember { mutableStateOf(false) }
    var showSocialsPublicly by remember { mutableStateOf(false) }
    var ebirdEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (userId == null) {
            error = "Nicht angemeldet"
            loading = false
            return@LaunchedEffect
        }
        try {
            val row = Supabase.client.postgrest["profiles"]
                .select { filter { eq("id", userId) } }
                .decodeSingle<JsonObject>()
            username = row.str("username")
            displayName = row.str("display_name")
            bio = row.str("bio")
            avatarUrl = row.str("avatar_url")
            homepage = row.str("homepage")
            socials = row.obj("socials").entries
                .joinToString(", ") { "${it.key}=${it.value.textOrNull}" }
            skillTags = row.list("skill_tags").joinToString(", ")
            showHomepagePublicly = row.bool("show_homepage_publicly")
            showSocialsPublicly = row.bool("show_socials_publicly")
            ebirdEnabled = row.bool("ebird_enabled")
        } catch (e: Exception) {
            error = e.message ?: "Profil konnte nicht geladen werden"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = avatarUrl.ifBlank { null },
                contentDescription = "Profilbild",
                modifier = Modifier.size(64.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column {
                Text(displayName.ifBlank { username }, style = MaterialTheme.typography.titleLarge)
                Text("@$username", style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(displayName, { displayName = it }, label = { Text("Anzeigename") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it }, label = { Text("Kurzbeschreibung") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(avatarUrl, { avatarUrl = it }, label = { Text("Profilbild-URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(homepage, { homepage = it }, label = { Text("Homepage") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(socials, { socials = it }, label = { Text("Social Media (name=url, komma-getrennt)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(skillTags, { skillTags = it }, label = { Text("Skill-Tags (komma-getrennt)") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Homepage öffentlich sichtbar")
            Switch(showHomepagePublicly, { showHomepagePublicly = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Social Media öffentlich sichtbar")
            Switch(showSocialsPublicly, { showSocialsPublicly = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("eBird-Sichtungen aktivieren (eigener API-Key lokal)")
            Switch(ebirdEnabled, { ebirdEnabled = it })
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (saved) Text("Gespeichert.", color = MaterialTheme.colorScheme.primary)

        Button(
            enabled = !saving && userId != null,
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    saved = false
                    try {
                        Supabase.client.postgrest["profiles"].update({
                            set("display_name", displayName)
                            set("bio", bio.ifBlank { null })
                            set("avatar_url", avatarUrl.ifBlank { null })
                            set("homepage", homepage.ifBlank { null })
                            set("socials", jsonOf(*socials.split(',').mapNotNull {
                                val p = it.split('=', limit = 2)
                                if (p.size == 2 && p[0].isNotBlank()) p[0].trim() to p[1].trim() else null
                            }.toTypedArray()))
                            set("skill_tags", skillTags.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                            set("show_homepage_publicly", showHomepagePublicly)
                            set("show_socials_publicly", showSocialsPublicly)
                            set("ebird_enabled", ebirdEnabled)
                        }) { filter { eq("id", userId!!) } }
                        saved = true
                    } catch (e: Exception) {
                        error = e.message ?: "Speichern fehlgeschlagen"
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Speichern…" else "Speichern")
        }
    }
}
