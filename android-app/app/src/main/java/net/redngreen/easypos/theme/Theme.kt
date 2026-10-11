package net.redngreen.easypos.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Light-only theme — the Stitch design system is light, and the tablet is
 * counter hardware in a bright shop. No dynamic color: brand tokens only.
 * The scheme mirrors the "Kedai Runcit Modern POS" Material 3 roles.
 */
private val LightColorScheme =
  lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = SurfaceTint,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    surfaceContainer = SurfaceContainer,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Scrim,
  )

/** Semantic colors Material3 has no slot for (success, warning). */
@Immutable
data class ExtendedColors(
  val success: Color = Success,
  val onSuccess: Color = Color.White,
  val successContainer: Color = SuccessContainer,
  val onSuccessContainer: Color = OnSuccessContainer,
  val warning: Color = Warning,
  val onWarning: Color = OnWarning,
  val warningContainer: Color = WarningContainer,
  val onWarningContainer: Color = OnWarningContainer,
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

/** Success/warning accessors, e.g. `ExtendedTheme.success`. */
object ExtendedTheme {
  val success: Color
    @Composable get() = LocalExtendedColors.current.success
  val successContainer: Color
    @Composable get() = LocalExtendedColors.current.successContainer
  val onSuccessContainer: Color
    @Composable get() = LocalExtendedColors.current.onSuccessContainer
  val warning: Color
    @Composable get() = LocalExtendedColors.current.warning
  val warningContainer: Color
    @Composable get() = LocalExtendedColors.current.warningContainer
  val onWarningContainer: Color
    @Composable get() = LocalExtendedColors.current.onWarningContainer
}

@Composable
fun EasyPOSTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalExtendedColors provides ExtendedColors()) {
    MaterialTheme(
      colorScheme = LightColorScheme,
      typography = Typography,
      shapes = Shapes,
      content = content,
    )
  }
}
