package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioGold,
    onPrimary = OnStudioGold,
    primaryContainer = StudioGoldContainer,
    onPrimaryContainer = OnStudioGoldContainer,
    secondary = SlateAccent,
    onSecondary = OnSlateAccent,
    secondaryContainer = SlateAccentContainer,
    onSecondaryContainer = OnSlateAccentContainer,
    tertiary = StatusConnected,
    onTertiary = OnStatusConnected,
    tertiaryContainer = StatusConnectedContainer,
    onTertiaryContainer = OnStatusConnectedContainer,
    error = StudioError,
    onError = OnStudioError,
    errorContainer = StudioErrorContainer,
    onErrorContainer = OnStudioErrorContainer,
    background = GraphiteBg,
    onBackground = StudioTextPrimary,
    surface = GraphiteSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = GraphiteSurfaceHigh,
    onSurfaceVariant = StudioTextMuted,
    surfaceContainerLowest = GraphiteBg,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteSurfaceHigh,
    surfaceContainerHigh = GraphiteSurfaceHighest,
    surfaceContainerHighest = GraphiteSurfaceHighest,
    outline = GraphiteOutline,
    outlineVariant = GraphiteOutlineSubtle,
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LightGold,
    onPrimary = OnLightGold,
    primaryContainer = LightGoldContainer,
    onPrimaryContainer = OnLightGoldContainer,
    secondary = LightSlateAccent,
    onSecondary = OnLightSlateAccent,
    secondaryContainer = LightSlateAccentContainer,
    onSecondaryContainer = OnLightSlateAccentContainer,
    tertiary = LightConnected,
    onTertiary = OnLightGold,
    tertiaryContainer = LightConnectedContainer,
    onTertiaryContainer = OnStatusConnected,
    error = LightError,
    onError = OnLightGold,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnStudioError,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBg,
    surfaceContainer = LightSurfaceHigh,
    surfaceContainerHigh = LightSurfaceHighest,
    surfaceContainerHighest = LightSurfaceHighest,
    outline = LightOutline,
    outlineVariant = LightSurfaceHighest,
)

enum class WorkspaceVisualTheme {
    MSE_DESIGNER,
    DATA_EDITOR_X_CODER,
}

private val DataEditorXDarkColorScheme = darkColorScheme(
    primary = DexCyan,
    onPrimary = OnDexCyan,
    primaryContainer = DexCyanContainer,
    onPrimaryContainer = OnDexCyanContainer,
    secondary = DexSteelSecondary,
    onSecondary = OnDexSteel,
    secondaryContainer = DexSteelContainer,
    onSecondaryContainer = OnDexSteelContainer,
    tertiary = StatusConnected,
    onTertiary = OnStatusConnected,
    tertiaryContainer = StatusConnectedContainer,
    onTertiaryContainer = OnStatusConnectedContainer,
    error = StudioError,
    onError = OnStudioError,
    errorContainer = StudioErrorContainer,
    onErrorContainer = OnStudioErrorContainer,
    background = DexDarkBg,
    onBackground = DexTextPrimary,
    surface = DexDarkSurface,
    onSurface = DexTextPrimary,
    surfaceVariant = DexDarkSurfaceHigh,
    onSurfaceVariant = DexTextMuted,
    surfaceContainerLowest = DexDarkCodeBg,
    surfaceContainerLow = DexDarkSurface,
    surfaceContainer = DexDarkSurfaceHigh,
    surfaceContainerHigh = DexDarkSurfaceHighest,
    surfaceContainerHighest = DexDarkSurfaceHighest,
    outline = DexDarkOutline,
    outlineVariant = DexDarkOutlineSubtle,
)

private val DataEditorXLightColorScheme = lightColorScheme(
    primary = DexLightCyan,
    onPrimary = OnDexLightCyan,
    primaryContainer = DexLightCyanContainer,
    onPrimaryContainer = OnDexLightCyanContainer,
    secondary = DexLightSteel,
    onSecondary = OnDexLightSteel,
    secondaryContainer = DexLightSteelContainer,
    onSecondaryContainer = OnDexLightSteelContainer,
    tertiary = LightConnected,
    onTertiary = OnDexLightCyan,
    tertiaryContainer = LightConnectedContainer,
    onTertiaryContainer = OnStatusConnected,
    error = LightError,
    onError = OnDexLightCyan,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnStudioError,
    background = DexLightBg,
    onBackground = LightTextPrimary,
    surface = DexLightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = DexLightSurfaceHigh,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLowest = DexLightSurface,
    surfaceContainerLow = DexLightBg,
    surfaceContainer = DexLightSurfaceHigh,
    surfaceContainerHigh = DexLightSurfaceHighest,
    surfaceContainerHighest = DexLightSurfaceHighest,
    outline = DexLightOutline,
    outlineVariant = DexLightSurfaceHighest,
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun TcgStudioTheme(
    darkTheme: Boolean = true,
    workspaceTheme: WorkspaceVisualTheme = WorkspaceVisualTheme.MSE_DESIGNER,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (workspaceTheme) {
        WorkspaceVisualTheme.MSE_DESIGNER -> if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
        WorkspaceVisualTheme.DATA_EDITOR_X_CODER -> if (darkTheme) DataEditorXDarkColorScheme else DataEditorXLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
