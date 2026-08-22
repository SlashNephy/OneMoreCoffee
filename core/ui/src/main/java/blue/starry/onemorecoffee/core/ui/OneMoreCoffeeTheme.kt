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
    // window_background (app/src/main/res/values/colors.xml) と値を合わせること
    background = Color(0xFFF8F7F4),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFFEFEEE9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4EF),
    surfaceContainerHigh = Color(0xFFE9E8E3),
    surfaceContainerHighest = Color(0xFFE3E2DD),
    surfaceDim = Color(0xFFDDDCD7),
    surfaceBright = Color(0xFFF8F7F4),
    surfaceVariant = Color(0xFFDCE5DB),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC1C9BF),
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF0F1EB),
    inversePrimary = Color(0xFF6ADBA8),
    tertiaryContainer = Color(0xFFD2E4FF),
    onTertiaryContainer = Color(0xFF001C38),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
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
    // window_background (app/src/main/res/values-night/colors.xml) と値を合わせること
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E3DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E3DD),
    surfaceContainer = Color(0xFF1C201B),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181C18),
    surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF313630),
    surfaceDim = Color(0xFF101410),
    surfaceBright = Color(0xFF363A35),
    surfaceVariant = Color(0xFF3F4A42),
    onSurfaceVariant = Color(0xFFBFC9BE),
    outline = Color(0xFF89938B),
    outlineVariant = Color(0xFF3F4A42),
    inverseSurface = Color(0xFFE1E3DD),
    inverseOnSurface = Color(0xFF2F312D),
    inversePrimary = Color(0xFF006241),
    tertiaryContainer = Color(0xFF17497B),
    onTertiaryContainer = Color(0xFFD2E4FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
