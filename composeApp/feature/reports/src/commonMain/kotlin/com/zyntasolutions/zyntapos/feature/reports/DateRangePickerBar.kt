package com.zyntasolutions.zyntapos.feature.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
import kotlinx.datetime.Instant

/**
 * Reusable date-range selection bar for report screens.
 *
 * Displays four preset [FilterChip]s — Today, This Week, This Month, Custom.
 * Selecting "Custom" opens an M3 [DatePickerDialog] with a [DateRangePicker]
 * to allow arbitrary start/end date selection.
 *
 * @param selectedRange   Currently active [DateRange] preset.
 * @param onRangeSelected Callback invoked when a preset chip is tapped.
 * @param onCustomRange   Callback invoked when the user confirms a custom date range.
 *                        Receives `from` and `to` as UTC [Instant]s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerBar(
    selectedRange: DateRange,
    onRangeSelected: (DateRange) -> Unit,
    onCustomRange: (from: Instant, to: Instant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangeState = rememberDateRangePickerState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = {
                        if (range == DateRange.CUSTOM) showDatePicker = true
                        else onRangeSelected(range)
                    },
                    label = { Text(range.label(s)) },
                )
            }
        }

        // Current range descriptor text
        if (selectedRange != DateRange.CUSTOM) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = selectedRange.description(s),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    // ── Custom date range picker dialog ────────────────────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = dateRangeState.selectedStartDateMillis
                        val endMs   = dateRangeState.selectedEndDateMillis
                        if (startMs != null && endMs != null) {
                            onCustomRange(
                                Instant.fromEpochMilliseconds(startMs),
                                Instant.fromEpochMilliseconds(endMs + MILLIS_END_OF_DAY),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text(s[StringResource.COMMON_APPLY]) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        ) {
            DateRangePicker(
                state = dateRangeState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private const val MILLIS_END_OF_DAY = 86_399_999L  // 23:59:59.999 offset

private fun DateRange.label(s: StringResolver): String = when (this) {
    DateRange.TODAY      -> s[StringResource.COMMON_TODAY]
    DateRange.THIS_WEEK  -> s[StringResource.COMMON_THIS_WEEK]
    DateRange.THIS_MONTH -> s[StringResource.COMMON_THIS_MONTH]
    DateRange.CUSTOM     -> s[StringResource.REPORTS_CUSTOM]
}

private fun DateRange.description(s: StringResolver): String = when (this) {
    DateRange.TODAY      -> s[StringResource.REPORTS_DATE_RANGE_TODAY_DESC]
    DateRange.THIS_WEEK  -> s[StringResource.REPORTS_DATE_RANGE_WEEK_DESC]
    DateRange.THIS_MONTH -> s[StringResource.REPORTS_DATE_RANGE_MONTH_DESC]
    DateRange.CUSTOM     -> ""
}
