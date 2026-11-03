package de.naturfoto.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.naturfoto.app.theme.ScrimTop
import de.naturfoto.app.theme.translucentSurface
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

@Composable
actual fun ProjectMap(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(51.1657, 10.4515))
                    overlays.add(
                        Polygon().apply {
                            points = listOf(
                                GeoPoint(47.2, 5.8),
                                GeoPoint(55.1, 5.8),
                                GeoPoint(55.1, 15.1),
                                GeoPoint(47.2, 15.1),
                            )
                            outlinePaint.color = 0xFF7FB069.toInt()
                            outlinePaint.strokeWidth = 6f
                            setFillColor(0x227FB069)
                        },
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(ScrimTop, Color.Transparent),
                    ),
                )
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(translucentSurface())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Projektgebiet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FloatingActionButton(
                onClick = { },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Eigenen Standort")
            }
            FloatingActionButton(
                onClick = { },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Ebenen")
            }
            FloatingActionButton(
                onClick = { },
                containerColor = translucentSurface(),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = "Spot melden")
            }
        }

    }
}
