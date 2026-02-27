package com.zyntasolutions.zyntapos.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact chip showing label printer or receipt printer connection status.
 *
 * @param isConnected  True when the printer is reachable and ready.
 * @param label        Optional override for the displayed text.
 */
@Composable
fun PrinterStatusBadge(
    isConnected: Boolean,
    label: String = if (isConnected) "Connected" else "Disconnected",
    modifier: Modifier = Modifier,
) {
    val dotColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935)
    val bgColor  = if (isConnected)
        Color(0xFF4CAF50).copy(alpha = 0.12f)
    else
        Color(0xFFE53935).copy(alpha = 0.12f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = dotColor,
        )
    }
}
