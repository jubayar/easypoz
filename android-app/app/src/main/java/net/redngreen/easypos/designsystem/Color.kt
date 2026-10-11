package net.redngreen.easypos.designsystem

import androidx.compose.ui.graphics.Color

/**
 * "Kedai Runcit Modern POS" design system — the Stitch project "EasyPOS Grocery
 * Tablet System" (id 9798094753620694253). Material 3 tonal palette:
 * primary #004AC6 / primary-container #2563EB, secondary green #006E2D,
 * tertiary amber #784B00, error #BA1A1A, and blue-tinted neutral surfaces
 * (#F8F9FF → #D3E4FE). Values mirror the Stitch designTheme exactly.
 */

// Primary
val Primary = Color(0xFF004AC6)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF2563EB)
val OnPrimaryContainer = Color(0xFFEEEFFF)
val InversePrimary = Color(0xFFB4C5FF)

// Secondary (green)
val Secondary = Color(0xFF006E2D)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFF7CF994)
val OnSecondaryContainer = Color(0xFF007230)

// Tertiary (amber-brown)
val Tertiary = Color(0xFF784B00)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFF996100)
val OnTertiaryContainer = Color(0xFFFFEEDD)

// Error
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

// Surfaces & neutrals
val Surface = Color(0xFFF8F9FF)
val OnSurface = Color(0xFF0B1C30)
val SurfaceDim = Color(0xFFCBD8F5)
val SurfaceBright = Color(0xFFF8F9FF)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFEFF4FF)
val SurfaceContainer = Color(0xFFE5EEFF)
val SurfaceContainerHigh = Color(0xFFDCE9FF)
val SurfaceContainerHighest = Color(0xFFD3E4FE)
val SurfaceVariant = Color(0xFFD3E4FE)
val OnSurfaceVariant = Color(0xFF434655)
val InverseSurface = Color(0xFF213145)
val InverseOnSurface = Color(0xFFEAF1FF)
val SurfaceTint = Color(0xFF0053DB)
val Outline = Color(0xFF737686)
val OutlineVariant = Color(0xFFC3C6D7)
val Scrim = Color(0xFF000000)

// Extended semantic roles (success / warning) — Stitch secondary-green and
// tertiary-amber fixed tones; Material 3 has no slot for either.
val Success = Color(0xFF006E2D)
val SuccessContainer = Color(0xFF7CF994)
val OnSuccessContainer = Color(0xFF007230)
val Warning = Color(0xFFFFB95F)
val OnWarning = Color(0xFF2A1700)
val WarningContainer = Color(0xFFFFDDB8)
val OnWarningContainer = Color(0xFF653E00)
