package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AmoledDarkColorScheme = darkColorScheme(
    primary = RearAmberPrimary,
    onPrimary = AmoledBlack,
    primaryContainer = RearAmberContainer,
    onPrimaryContainer = RearAmberBright,
    secondary = FrontCyanPrimary,
    onSecondary = AmoledBlack,
    secondaryContainer = FrontCyanContainer,
    onSecondaryContainer = FrontCyanBright,
    tertiary = StatusEmerald,
    onTertiary = AmoledBlack,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = SurfaceObsidian,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    outlineVariant = SurfaceMuted,
    error = StatusCoralError,
    onError = AmoledBlack,
    errorContainer = StatusErrorContainer,
    onErrorContainer = StatusCoralError,
)

@Composable
fun DualTorchTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AmoledDarkColorScheme,
        typography = DualTorchTypography,
        content = content,
    )
}
