package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SignalYardDarkScheme = darkColorScheme(
    primary = BrassGold,
    onPrimary = GraphiteDeep,
    primaryContainer = ForestGreen,
    onPrimaryContainer = WarmIvoryPaper,
    secondary = WarmSandstone,
    onSecondary = GraphiteDeep,
    secondaryContainer = ForestGreenDeep,
    onSecondaryContainer = WarmIvory,
    tertiary = SignalGreen,
    onTertiary = GraphiteDeep,
    background = GraphiteDeep,
    onBackground = WarmIvoryPaper,
    surface = ForestGreenDeep,
    onSurface = WarmIvoryPaper,
    surfaceVariant = Graphite,
    onSurfaceVariant = WarmIvoryMuted,
    outline = BrassGoldDark,
    error = SignalRed,
    onError = WarmIvoryPaper
)

private val SignalYardLightScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = WarmIvoryPaper,
    primaryContainer = BrassGold,
    onPrimaryContainer = GraphiteDeep,
    secondary = WarmSandstoneDark,
    onSecondary = WarmIvoryPaper,
    secondaryContainer = WarmIvory,
    onSecondaryContainer = GraphiteDeep,
    tertiary = SignalGreen,
    onTertiary = WarmIvoryPaper,
    background = WarmIvoryPaper,
    onBackground = GraphiteDeep,
    surface = WarmIvory,
    onSurface = GraphiteDeep,
    surfaceVariant = WarmSandstoneLight,
    onSurfaceVariant = Graphite,
    outline = WarmSandstoneDark,
    error = SignalRed,
    onError = WarmIvoryPaper
)

val SignalYardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun SignalYardTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SignalYardDarkScheme else SignalYardLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SignalYardTypography,
        shapes = SignalYardShapes,
        content = content
    )
}
