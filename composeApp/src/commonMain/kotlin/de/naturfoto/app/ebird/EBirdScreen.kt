package de.naturfoto.app.ebird

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import de.naturfoto.app.data.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

@Composable
fun EBirdScreen(
    projectId: String,
    onOpenOnMap: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { EBirdRepository() }
    val mapRepo = remember { de.naturfoto.app.map.MapRepository() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(false) } // false = in der Nähe, true = selten
    var obs by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val key = repo.apiKey()
                if (key.isNullOrBlank()) {
                    error = "Bitte eBird API-Key im Profil hinterlegen (Opt-in)."
                } else {
                    val region = mapRepo.projectRegion(projectId)
                    val center = EBirdRepository.regionCenter(region)
                    obs = if (center != null) {
                        repo.recentObservations(key, center.first, center.second, EBirdRepository.regionRadiusKm(region), backDays = 3, notable = mode)
                    } else emptyList()
                }
            } catch (e: Exception) {
                error = e.message?.take(150)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(projectId, mode) { load() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("eBird-Meldungen im Projektgebiet", style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !mode, onClick = { mode = false }, label = { Text("In der Nähe") })
            SegmentedButton(selected = mode, onClick = { mode = true }, label = { Text("Selten") })
        }
        if (loading) Text("Lade…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(obs, key = { it.str("speciesCode") + it.str("obsDt") + it.str("lat") }) { o ->
                Card(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), onClick = {
                    o.str("lat").toDoubleOrNull()?.let { lat ->
                        o.str("lng").toDoubleOrNull()?.let { lng -> onOpenOnMap(lat, lng) }
                    }
                }) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("🐦 ${o.str("comName").ifBlank { o.str("sciName") }}", style = MaterialTheme.typography.titleMedium)
                        Text("${o.str("obsDt")} ${o.str("obsTime") ?: ""} · ${o.str("locName").ifBlank { "" }}", style = MaterialTheme.typography.bodySmall)
                        if (mode) o.str("obsReviewed") // nur Info
                        Text("📍 ${o.str("lat")}, ${o.str("lng")} — auf Karte öffnen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
