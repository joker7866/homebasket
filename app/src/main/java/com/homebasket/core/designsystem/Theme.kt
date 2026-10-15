package com.homebasket.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homebasket.core.data.ThemeMode

/** Fresh-market palette: sage, deep forest, warm tan and amber. */
object Brand {
    val Forest = Color(0xFF0C3B2E)
    val Sage = Color(0xFF6D9773)
    val Tan = Color(0xFFBB8A52)
    val Amber = Color(0xFFFFBA00)
    val Mint = Color(0xFFE6F2EA)
    val Cream = Color(0xFFFBF8F1)
    val Peach = Color(0xFFFDEFE3)
    val Text = Color(0xFF1C2B24)
    val Warning = Color(0xFFE8A23B)
    val Error = Color(0xFFC44242)
}

/** Status colours that are not part of the Material scheme. */
@Immutable
data class StatusColors(val warning: Color, val onWarning: Color, val warningContainer: Color, val ok: Color, val okContainer: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Brand.Warning, Color(0xFF3A2600), Color(0xFFFFF1DC), Brand.Sage, Brand.Mint)
}

/** Soft page wash drawn behind every screen (mint at the top fading to peach). */
@Immutable
data class Backdrop(val brush: Brush, val isDark: Boolean)

val LocalBackdrop = staticCompositionLocalOf { Backdrop(Brush.verticalGradient(listOf(Brand.Mint, Brand.Cream, Brand.Peach)), false) }

private val LightColors = lightColorScheme(
    primary = Brand.Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBDF),
    onPrimaryContainer = Brand.Forest,
    secondary = Brand.Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEE5),
    onSecondaryContainer = Brand.Forest,
    tertiary = Color(0xFF8C6234),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E6D3),
    onTertiaryContainer = Color(0xFF3B2510),
    background = Brand.Cream,
    onBackground = Brand.Text,
    surface = Color.White,
    onSurface = Brand.Text,
    surfaceVariant = Color(0xFFEFF3EE),
    onSurfaceVariant = Color(0xFF4F5D55),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF5F8F4),
    surfaceContainerHigh = Color(0xFFEDF3EE),
    surfaceContainerHighest = Color(0xFFE4ECE6),
    outline = Color(0xFF7E8A82),
    outlineVariant = Color(0xFFD5DED7),
    error = Brand.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBA3),
    onPrimary = Color(0xFF06261D),
    primaryContainer = Color(0xFF1E4A3B),
    onPrimaryContainer = Color(0xFFCDE8D2),
    secondary = Color(0xFFA9C8AD),
    onSecondary = Color(0xFF14301F),
    secondaryContainer = Color(0xFF29433A),
    onSecondaryContainer = Color(0xFFD3E8D7),
    tertiary = Color(0xFFE5BE8F),
    onTertiary = Color(0xFF432A0E),
    tertiaryContainer = Color(0xFF5C3F1F),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0D1A15),
    onBackground = Color(0xFFDDE6E0),
    surface = Color(0xFF15241D),
    onSurface = Color(0xFFDDE6E0),
    surfaceVariant = Color(0xFF34443B),
    onSurfaceVariant = Color(0xFFBCCBC1),
    surfaceContainerLowest = Color(0xFF0A140F),
    surfaceContainerLow = Color(0xFF17271F),
    surfaceContainer = Color(0xFF1B2C24),
    surfaceContainerHigh = Color(0xFF24362D),
    surfaceContainerHighest = Color(0xFF2E4137),
    outline = Color(0xFF86968C),
    outlineVariant = Color(0xFF34443B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

val NumberStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)

@Composable
fun HomeBasketTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val status = if (dark) StatusColors(Color(0xFFFFC77A), Color(0xFF3A2600), Color(0xFF4A3300), Color(0xFF9CCBA3), Color(0xFF1E4A3B))
    else StatusColors(Brand.Warning, Color(0xFF3A2600), Color(0xFFFFF1DC), Brand.Sage, Brand.Mint)
    val backdrop = if (dark) Backdrop(Brush.verticalGradient(listOf(Color(0xFF10251C), Color(0xFF0D1A15), Color(0xFF1A1712))), true)
    else Backdrop(Brush.verticalGradient(listOf(Brand.Mint, Brand.Cream, Brand.Peach)), false)
    CompositionLocalProvider(LocalStatusColors provides status, LocalBackdrop provides backdrop) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
