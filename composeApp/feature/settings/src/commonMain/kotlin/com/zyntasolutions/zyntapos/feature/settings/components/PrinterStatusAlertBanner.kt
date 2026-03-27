package com.zyntasolutions.zyntapos.feature.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.hal.printer.PrinterStatusMonitor
import org.koin.compose.koinInject

/**
 * Alert banner composable for the Printer Settings screen (Connection tab).
 *
 * Displays active hardware warnings sourced from [PrinterStatusMonitor.statusState]:
 * - **Paper Out** — thermal roll is empty.
 * - **Paper Low** — roll near-end sensor triggered.
 * - **Cover Open** — printer lid is open.
 *
 * The banner is hidden when the printer is healthy.
 */
@Composable
fun PrinterStatusAlertBanner(
    modifier: Modifier = Modifier,
    monitor: PrinterStatusMonitor = koinInject(),
) {
    val s = LocalStrings.current
    val status by monitor.statusState.collectAsState()

    val warnings = buildList {
        if (status.isPaperOut) add(s[StringResource.POS_PRINTER_PAPER_OUT])
        else if (status.isPaperLow) add(s[StringResource.POS_PRINTER_PAPER_LOW])
        if (status.isCoverOpen) add(s[StringResource.POS_PRINTER_COVER_OPEN])
    }

    AnimatedVisibility(
        visible = warnings.isNotEmpty(),
        enter = expandVertically(),
        exit  = shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = s[StringResource.POS_PRINTER_WARNING_CD],
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = warnings.joinToString(s[StringResource.COMMON_SEPARATOR_MIDDLE_DOT]),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
