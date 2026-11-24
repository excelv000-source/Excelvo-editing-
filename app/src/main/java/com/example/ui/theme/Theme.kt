package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.core.model.ThemeIntensity

private fun buildColorScheme(intensity: ThemeIntensity) = darkColorScheme(
    primary = ExcevoColors.AccentPurple,
    onPrimary = ExcevoColors.TextPrimary,
    primaryContainer = ExcevoColors.AccentPurpleContainer,
    onPrimaryContainer = ExcevoColors.AccentLavender,
    secondary = ExcevoColors.AccentVioletGlow,
    onSecondary = ExcevoColors.VoidCharcoal,
    secondaryContainer = ExcevoColors.PlayheadAmberContainer,
    onSecondaryContainer = ExcevoColors.PlayheadAmber,
    tertiary = ExcevoColors.SuccessEmerald,
    onTertiary = ExcevoColors.VoidCharcoal,
    background = if (intensity == ThemeIntensity.DEEP_OBSIDIAN) {
        ExcevoColors.DeepObsidian
    } else {
        ExcevoColors.VoidCharcoal
    },
    onBackground = ExcevoColors.TextPrimary,
    surface = ExcevoColors.StudioPanel,
    onSurface = ExcevoColors.TextPrimary,
    surfaceVariant = ExcevoColors.ElevatedSurface,
    onSurfaceVariant = ExcevoColors.TextSecondary,
    outline = ExcevoColors.HairlineBorder,
    outlineVariant = ExcevoColors.SubtleDivider,
    error = ExcevoColors.DangerRed,
    onError = ExcevoColors.TextPrimary,
)

private val ExcevoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ExcevoTheme(
    themeIntensity: ThemeIntensity = ThemeIntensity.STUDIO_DARK,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = buildColorScheme(themeIntensity),
        typography = ExcevoTypography,
        shapes = ExcevoShapes,
        content = content,
    )
}
