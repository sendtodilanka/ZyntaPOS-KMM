package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * ZyntaPOS brand mark — a stylised block-Z drawn as a scalable Compose
 * [ImageVector]. Renders crisply at any size and inherits the current
 * Material theme tint unless overridden.
 *
 * Replaces the `Text("Z")` placeholder that was previously rendered on the
 * login screen while Sprint 9 design-system polish was pending.
 */
@Composable
fun ZyntaBrandMark(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val vector = remember { ZyntaBrandMarkVector }
    Image(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

// Viewbox 24×24 so the icon scales with standard Material sizing.
// Path traces the outline of a block-Z clockwise from top-left:
//   top bar → right edge → diagonal slab (down-left) → bottom bar →
//   bottom edge → left edge → diagonal slab (up-right) → close.
private val ZyntaBrandMarkVector: ImageVector = ImageVector.Builder(
    name = "ZyntaBrandMark",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(3f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(9f)
        lineTo(9f, 18f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        verticalLineTo(15f)
        lineTo(15f, 6f)
        horizontalLineTo(3f)
        close()
    }
}.build()
