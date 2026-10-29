package app.nightplayer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val bg = Color(0xFF2D332F)
    val card = Color(0xFF38403A)
    val cardOn = Color(0xFF3F4F44)
    val accent = Color(0xFFB8D4BF)
    val onAccent = Color(0xFF1B2B20)
    val text = Color(0xFFE3E8E3)
    val muted = Color(0xFFA7B0A8)
    val faint = Color(0xFF8F978F)
    val outline = Color(0xFF59625B)
    val track = Color(0xFF46504A)
}

@Composable
fun NightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Palette.onAccent,
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.card,
            onSurface = Palette.text,
            surfaceVariant = Palette.card,
            onSurfaceVariant = Palette.muted,
            surfaceContainerHigh = Palette.card,
            secondaryContainer = Palette.cardOn,
            outline = Palette.outline
        ),
        content = content
    )
}
