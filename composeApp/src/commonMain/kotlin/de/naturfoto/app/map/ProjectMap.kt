package de.naturfoto.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ProjectMap(projectId: String, modifier: Modifier = Modifier)
