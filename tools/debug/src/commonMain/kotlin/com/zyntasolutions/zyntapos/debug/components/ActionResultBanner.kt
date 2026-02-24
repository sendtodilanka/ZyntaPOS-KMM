package com.zyntasolutions.zyntapos.debug.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.mvi.DebugState

/**
 * Inline banner shown below the tab bar after any debug action completes.
 *
 * Uses green (success) or red (error) background to signal result at a glance.
 * Fades in/out when [result] changes.
 *
 * @param result The current [DebugState.ActionResult], or null when hidden.
 */
@Composable
fun ActionResultBanner(
    result: DebugState.ActionResult?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        if (result != null) {
            val bg = if (result.isError) MaterialTheme.colorScheme.errorContainer
                     else MaterialTheme.colorScheme.primaryContainer
            val fg = if (result.isError) MaterialTheme.colorScheme.onErrorContainer
                     else MaterialTheme.colorScheme.onPrimaryContainer
            val icon = if (result.isError) Icons.Filled.Error else Icons.Filled.CheckCircle

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = fg)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.message,
                        color = fg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
