package de.naturfoto.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Leaf,
    onPrimary = ForestNight,
    secondary = Amber,
    onSecondary = ForestNight,
    background = ForestNight,
    onBackground = Sand,
    surface = ForestDeep,
    onSurface = Sand,
    surfaceVariant = Moss,
    onSurfaceVariant = Fog,
    outline = Moss,
)

@Composable
fun NaturfotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

fun translucentSurface(alpha: Float = 0.72f): Color =
    ForestDeep.copy(alpha = alpha)
