package blue.starry.onemorecoffee.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006241),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8FF8C4),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFFC98A3B),
    onSecondary = Color(0xFF3B2708),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2A1800),
    tertiary = Color(0xFF2E5C8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFFEFEEE9),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6ADBA8),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFF86F8C3),
    secondary = Color(0xFFE7BE7E),
    onSecondary = Color(0xFF432C05),
    secondaryContainer = Color(0xFF5E421A),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF9FC9FF),
    onTertiary = Color(0xFF003257),
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DD),
    surfaceContainer = Color(0xFF1C201B),
    surfaceVariant = Color(0xFF3F4A42),
    onSurfaceVariant = Color(0xFFBFC9BE),
    outline = Color(0xFF89938B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun OneMoreCoffeeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
