package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaDatePicker — Wraps M3 DateRangePicker with preset range chips.
// Presets: Today, This Week, This Month, Custom.
// Used in Reports and Customer History screens.
// ─────────────────────────────────────────────────────────────────────────────

/** Preset date range options. */
enum class DateRangePreset(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom"),
}

/**
 * Date range selector with preset chips and an optional calendar picker for custom ranges.
 *
 * @param selectedPreset  Currently selected preset.
 * @param customFrom      Start [Instant] when [DateRangePreset.CUSTOM] is active, or null.
 * @param customTo        End [Instant] when [DateRangePreset.CUSTOM] is active, or null.
 * @param onPresetSelected Called when the user selects a preset chip.
 * @param onCustomRangeSelected Called with (from, to) when the user confirms a custom range.
 * @param modifier        Optional [Modifier].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZyntaDateRangePicker(
    selectedPreset: DateRangePreset,
    customFrom: Instant? = null,
    customTo: Instant? = null,
    onPresetSelected: (DateRangePreset) -> Unit,
    onCustomRangeSelected: (from: Instant, to: Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCalendar by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateRangePreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        if (preset == DateRangePreset.CUSTOM) {
                            showCalendar = true
                        } else {
                            onPresetSelected(preset)
                        }
                    },
                    label = { Text(preset.label) },
                )
            }

            Spacer(Modifier.weight(1f))

            // Calendar icon shortcut
            IconButton(onClick = { showCalendar = true }) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select date range",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Display selected custom range
        if (selectedPreset == DateRangePreset.CUSTOM && customFrom != null && customTo != null) {
            val tz = TimeZone.currentSystemDefault()
            val fromDate = customFrom.toLocalDateTime(tz).date
            val toDate = customTo.toLocalDateTime(tz).date
            Row(
                modifier = Modifier.padding(top = ZyntaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp).width(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text(
                    text = "$fromDate — $toDate",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ── Custom range calendar dialog ─────────────────────────────────────
    if (showCalendar) {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = (customFrom ?: now).toEpochMilliseconds(),
            initialSelectedEndDateMillis = (customTo ?: now).toEpochMilliseconds(),
            initialDisplayMode = DisplayMode.Picker,
        )

        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMs = state.selectedStartDateMillis
                    val endMs = state.selectedEndDateMillis
                    if (startMs != null && endMs != null) {
                        onPresetSelected(DateRangePreset.CUSTOM)
                        onCustomRangeSelected(
                            Instant.fromEpochMilliseconds(startMs),
                            Instant.fromEpochMilliseconds(endMs),
                        )
                    }
                    showCalendar = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.padding(ZyntaSpacing.md),
                title = { Text("Select Date Range", style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaDatePickerPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaDateRangePicker(
            selectedPreset = DateRangePreset.TODAY,
            onPresetSelected = {},
            onCustomRangeSelected = { _, _ -> },
        )
    }
}
