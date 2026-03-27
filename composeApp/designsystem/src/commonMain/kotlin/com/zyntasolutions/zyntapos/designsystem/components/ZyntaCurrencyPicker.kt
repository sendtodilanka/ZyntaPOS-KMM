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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaCurrencyPicker — Currency selection dropdown for multi-currency support.
//
// Displays the currently selected currency with code and symbol.
// Opens a dropdown with supported currencies (C2.2 Multi-Currency).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Supported currency definition.
 *
 * @param code     ISO 4217 currency code (e.g., "LKR", "USD").
 * @param symbol   Currency symbol for display (e.g., "Rs", "$").
 * @param name     Full currency name (e.g., "Sri Lankan Rupee").
 */
data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String,
)

/** Default set of currencies supported by ZyntaPOS. */
val ZYNTA_SUPPORTED_CURRENCIES = listOf(
    CurrencyOption("LKR", "Rs", "Sri Lankan Rupee"),
    CurrencyOption("USD", "$", "US Dollar"),
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("GBP", "£", "British Pound"),
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("AUD", "A$", "Australian Dollar"),
    CurrencyOption("SGD", "S$", "Singapore Dollar"),
    CurrencyOption("AED", "د.إ", "UAE Dirham"),
    CurrencyOption("JPY", "¥", "Japanese Yen"),
)

/**
 * A currency selection picker showing the selected currency code/symbol
 * with a dropdown to choose from supported currencies.
 *
 * @param selectedCode       ISO 4217 code of the currently selected currency.
 * @param onCurrencySelected Called with the selected [CurrencyOption].
 * @param modifier           Optional [Modifier].
 * @param currencies         Available currencies (defaults to [ZYNTA_SUPPORTED_CURRENCIES]).
 * @param label              Optional label text above the picker.
 */
@Composable
fun ZyntaCurrencyPicker(
    selectedCode: String,
    onCurrencySelected: (CurrencyOption) -> Unit,
    modifier: Modifier = Modifier,
    currencies: List<CurrencyOption> = ZYNTA_SUPPORTED_CURRENCIES,
    label: String? = null,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val selected = currencies.find { it.code == selectedCode }

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
                Text(
                    text = selected?.symbol ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.code ?: selectedCode,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = selected?.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = s[StringResource.DESIGNSYSTEM_SELECT_CURRENCY_CD],
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        ZyntaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            currencies.forEach { currency ->
                val isSelected = currency.code == selectedCode
                ZyntaDropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currency.symbol,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp),
                            )
                            Spacer(Modifier.width(ZyntaSpacing.sm))
                            Column {
                                Text(
                                    text = currency.code,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = currency.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onCurrencySelected(currency)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = s[StringResource.DESIGNSYSTEM_SELECTED_CD],
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
