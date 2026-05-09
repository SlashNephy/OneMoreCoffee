package blue.starry.onemorecoffee.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006241),
    secondary = Color(0xFFC98A3B),
    tertiary = Color(0xFF2E5C8A),
    background = Color(0xFFF8F7F4),
    surface = Color.White,
)

@Composable
fun OneMoreCoffeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
