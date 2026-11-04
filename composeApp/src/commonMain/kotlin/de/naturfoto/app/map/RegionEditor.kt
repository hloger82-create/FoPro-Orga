package de.naturfoto.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

expect fun RegionEditor(
    currentPolygon: List<Pair<Double, Double>>,
    onPolygonChanged: (List<Pair<Double, Double>>) -> Unit,
    modifier: Modifier = Modifier,
)
