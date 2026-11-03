package de.naturfoto.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

expect fun LocationPicker(
    initialLat: Double? = null,
    initialLng: Double? = null,
    onPicked: (lat: Double, lng: Double, name: String) -> Unit,
    modifier: Modifier = Modifier.height(300.dp),
)
