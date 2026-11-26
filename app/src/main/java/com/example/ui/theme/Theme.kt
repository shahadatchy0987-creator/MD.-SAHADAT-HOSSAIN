package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = Emerald800,
    onPrimary = Color.White,
    primaryContainer = Mint100,
    onPrimaryContainer = Emerald900,
    secondary = Mint600,
    onSecondary = Color.White,
    secondaryContainer = Mint50,
    onSecondaryContainer = Emerald800,
    tertiary = Gold600,
    onTertiary = Color.White,
    tertiaryContainer = Gold100,
    onTertiaryContainer = Gold700,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ExpenseCoral,
    onError = Color.White,
    errorContainer = ExpenseCoralBg,
    onErrorContainer = ExpenseCoral,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Mint300,
    onPrimary = Emerald900,
    primaryContainer = Emerald700,
    onPrimaryContainer = Mint100,
    secondary = Mint500,
    onSecondary = Emerald900,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Mint100,
    tertiary = Gold400,
    onTertiary = Emerald900,
    tertiaryContainer = Gold700,
    onTertiaryContainer = Gold100,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
  )

val AppShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
  )

@Composable
fun PlusnetAccountsTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content,
  )
}
