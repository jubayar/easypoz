package net.redngreen.easypos.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * "Kedai Runcit Modern POS" corner language (Stitch): 8dp micro elements
 * (badges, chips), 16dp base components (buttons, inputs, cards), 24dp large
 * containers and sheets.
 */
val Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )
