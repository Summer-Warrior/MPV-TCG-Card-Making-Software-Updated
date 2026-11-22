package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = BrassGold,
    onPrimary = OnBrassGold,
    primaryContainer = BrassGoldContainer,
    onPrimaryContainer = OnBrassGoldContainer,
    secondary = BlueprintCyan,
    onSecondary = OnBlueprintCyan,
    secondaryContainer = BlueprintCyanContainer,
    onSecondaryContainer = OnBlueprintCyanContainer,
    tertiary = StudioEmerald,
    onTertiary = OnStudioEmerald,
    tertiaryContainer = StudioEmeraldContainer,
    onTertiaryContainer = OnStudioEmeraldContainer,
    error = StudioCrimson,
    onError = OnStudioCrimson,
    errorContainer = StudioCrimsonContainer,
    onErrorContainer = OnStudioCrimsonContainer,
    background = ObsidianBg,
    onBackground = InkOffWhite,
    surface = ObsidianSurface,
    onSurface = InkOffWhite,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
)

private val StudioLightColorScheme = lightColorScheme(
    primary = BurnishedGold,
    onPrimary = OnBurnishedGold,
    primaryContainer = BurnishedGoldContainer,
    onPrimaryContainer = OnBurnishedGoldContainer,
    secondary = TechnicalTeal,
    onSecondary = OnTechnicalTeal,
    secondaryContainer = TechnicalTealContainer,
    onSecondaryContainer = OnTechnicalTealContainer,
    tertiary = ForestEmerald,
    onTertiary = OnBurnishedGold,
    tertiaryContainer = ForestEmeraldContainer,
    onTertiaryContainer = OnStudioEmerald,
    error = DeepCrimson,
    onError = OnBurnishedGold,
    errorContainer = DeepCrimsonContainer,
    onErrorContainer = OnStudioCrimson,
    background = ParchmentBg,
    onBackground = ArchivalInk,
    surface = ParchmentSurface,
    onSurface = ArchivalInk,
    surfaceVariant = ParchmentSurfaceHigh,
    onSurfaceVariant = ArchivalInkMuted,
    surfaceContainerLowest = ParchmentSurface,
    surfaceContainerLow = ParchmentBg,
    surfaceContainer = ParchmentSurfaceHigh,
    surfaceContainerHigh = ParchmentSurfaceHighest,
    surfaceContainerHighest = ParchmentSurfaceHighest,
    outline = ParchmentOutline,
    outlineVariant = ParchmentSurfaceHighest,
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TcgStudioTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
