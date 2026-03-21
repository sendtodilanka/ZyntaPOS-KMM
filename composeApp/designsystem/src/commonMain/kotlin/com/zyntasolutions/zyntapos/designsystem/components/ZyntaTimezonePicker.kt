package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaTimezonePicker — Timezone selection with UTC offset display.
//
// Provides a dropdown for selecting a timezone, showing the UTC offset
// alongside the timezone region name. Used in Settings and Onboarding.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Timezone entry for the picker.
 *
 * @param id     IANA timezone identifier (e.g., "Asia/Colombo").
 * @param label  Display name (e.g., "Asia/Colombo").
 * @param offset UTC offset string (e.g., "UTC+05:30").
 */
data class TimezoneEntry(
    val id: String,
    val label: String,
    val offset: String,
)

/** Common timezones with pre-computed UTC offsets for display. */
val ZYNTA_COMMON_TIMEZONES = listOf(
    TimezoneEntry("Pacific/Honolulu", "Pacific/Honolulu", "UTC-10:00"),
    TimezoneEntry("America/Los_Angeles", "America/Los Angeles", "UTC-08:00"),
    TimezoneEntry("America/Denver", "America/Denver", "UTC-07:00"),
    TimezoneEntry("America/Chicago", "America/Chicago", "UTC-06:00"),
    TimezoneEntry("America/New_York", "America/New York", "UTC-05:00"),
    TimezoneEntry("America/Sao_Paulo", "America/Sao Paulo", "UTC-03:00"),
    TimezoneEntry("Europe/London", "Europe/London", "UTC+00:00"),
    TimezoneEntry("Europe/Paris", "Europe/Paris", "UTC+01:00"),
    TimezoneEntry("Europe/Berlin", "Europe/Berlin", "UTC+01:00"),
    TimezoneEntry("Africa/Cairo", "Africa/Cairo", "UTC+02:00"),
    TimezoneEntry("Europe/Istanbul", "Europe/Istanbul", "UTC+03:00"),
    TimezoneEntry("Asia/Dubai", "Asia/Dubai", "UTC+04:00"),
    TimezoneEntry("Asia/Kolkata", "Asia/Kolkata", "UTC+05:30"),
    TimezoneEntry("Asia/Colombo", "Asia/Colombo", "UTC+05:30"),
    TimezoneEntry("Asia/Dhaka", "Asia/Dhaka", "UTC+06:00"),
    TimezoneEntry("Asia/Bangkok", "Asia/Bangkok", "UTC+07:00"),
    TimezoneEntry("Asia/Singapore", "Asia/Singapore", "UTC+08:00"),
    TimezoneEntry("Asia/Shanghai", "Asia/Shanghai", "UTC+08:00"),
    TimezoneEntry("Asia/Tokyo", "Asia/Tokyo", "UTC+09:00"),
    TimezoneEntry("Australia/Sydney", "Australia/Sydney", "UTC+11:00"),
    TimezoneEntry("Pacific/Auckland", "Pacific/Auckland", "UTC+13:00"),
)

/**
 * A timezone selection picker showing the selected timezone with UTC offset
 * and a dropdown to choose from common timezones.
 *
 * @param selectedTimezoneId IANA timezone identifier of the current selection.
 * @param onTimezoneSelected Called with the selected [TimezoneEntry].
 * @param modifier           Optional [Modifier].
 * @param timezones          Available timezones (defaults to [ZYNTA_COMMON_TIMEZONES]).
 * @param label              Optional label text above the picker.
 */
@Composable
fun ZyntaTimezonePicker(
    selectedTimezoneId: String,
    onTimezoneSelected: (TimezoneEntry) -> Unit,
    modifier: Modifier = Modifier,
    timezones: List<TimezoneEntry> = ZYNTA_COMMON_TIMEZONES,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = timezones.find { it.id == selectedTimezoneId }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = ZyntaSpacing.xs),
            )
        }

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.label ?: selectedTimezoneId,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = selected?.offset ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select timezone",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        ZyntaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            timezones.forEach { tz ->
                val isSelected = tz.id == selectedTimezoneId
                ZyntaDropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tz.offset,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(72.dp),
                            )
                            Spacer(Modifier.width(ZyntaSpacing.sm))
                            Text(
                                text = tz.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onTimezoneSelected(tz)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}
