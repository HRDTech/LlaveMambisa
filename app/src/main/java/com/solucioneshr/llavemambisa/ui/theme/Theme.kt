package com.solucioneshr.llavemambisa.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta inspirada en verde oliváceo revolucionario + dorado
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CAF7D),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFF77FFA6),
    secondary = Color(0xFF7CBB9B),
    onSecondary = Color(0xFF003823),
    secondaryContainer = Color(0xFF1A5236),
    onSecondaryContainer = Color(0xFF9AD8B6),
    tertiary = Color(0xFFCFBA73),
    onTertiary = Color(0xFF362D00),
    tertiaryContainer = Color(0xFF4E4200),
    onTertiaryContainer = Color(0xFFEDD68F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C1410),
    onBackground = Color(0xFFDCE5DD),
    surface = Color(0xFF0C1410),
    onSurface = Color(0xFFDCE5DD),
    surfaceVariant = Color(0xFF3B4A3E),
    onSurfaceVariant = Color(0xFFBBC9BD),
    outline = Color(0xFF869488),
    outlineVariant = Color(0xFF3B4A3E),
    inverseSurface = Color(0xFFDCE5DD),
    inverseOnSurface = Color(0xFF0C1410),
    inversePrimary = Color(0xFF006D35),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006D35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF5AD),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF4D6B57),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0F1DA),
    onSecondaryContainer = Color(0xFF0B2016),
    tertiary = Color(0xFF6B5C0D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF7E48B),
    onTertiaryContainer = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF6FBF2),
    onBackground = Color(0xFF191D19),
    surface = Color(0xFFF6FBF2),
    onSurface = Color(0xFF191D19),
    surfaceVariant = Color(0xFFD8E6DA),
    onSurfaceVariant = Color(0xFF3D4B3F),
    outline = Color(0xFF6D7B6E),
)

@Composable
fun LlaveMambisaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

