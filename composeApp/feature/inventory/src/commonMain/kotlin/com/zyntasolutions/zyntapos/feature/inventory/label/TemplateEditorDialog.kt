package com.zyntasolutions.zyntapos.feature.inventory.label

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import kotlin.time.Clock

/**
 * Dialog for creating or editing a [LabelTemplate].
 *
 * - Segmented button toggles between CONTINUOUS_ROLL and A4_SHEET paper types.
 * - CONTINUOUS: width, label height, columns (1–4), gap fields.
 * - A4: columns, rows, 4 margin fields, gap fields.
 * - Shows real-time computed label dimensions.
 *
 * @param template       Existing template to edit, or `null` to create a new one.
 * @param onSave         Called with the finalized template when the user taps Save.
 * @param onDismiss      Dismissal callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorDialog(
    template: LabelTemplate?,
    onSave: (LabelTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = template == null
    val now   = remember { Clock.System.now().toEpochMilliseconds() }

    var name           by remember { mutableStateOf(template?.name ?: "") }
    var paperType      by remember { mutableStateOf(template?.paperType ?: LabelTemplate.PaperType.CONTINUOUS_ROLL) }
    var paperWidthMm   by remember { mutableStateOf(template?.paperWidthMm?.toString() ?: "58") }
    var labelHeightMm  by remember { mutableStateOf(template?.labelHeightMm?.toString() ?: "25") }
    var columns        by remember { mutableStateOf(template?.columns?.toString() ?: "1") }
    var rows           by remember { mutableStateOf(template?.rows?.toString() ?: "8") }
    var gapH           by remember { mutableStateOf(template?.gapHorizontalMm?.toString() ?: "2") }
    var gapV           by remember { mutableStateOf(template?.gapVerticalMm?.toString() ?: "3") }
    var marginTop      by remember { mutableStateOf(template?.marginTopMm?.toString() ?: "2") }
    var marginBottom   by remember { mutableStateOf(template?.marginBottomMm?.toString() ?: "0") }
    var marginLeft     by remember { mutableStateOf(template?.marginLeftMm?.toString() ?: "2") }
    var marginRight    by remember { mutableStateOf(template?.marginRightMm?.toString() ?: "2") }

    val nameError   = name.isBlank()
    val isContinuous = paperType == LabelTemplate.PaperType.CONTINUOUS_ROLL

    // Compute preview dimensions
    val computedWidth: Double? = remember(paperWidthMm, marginLeft, marginRight, gapH, columns) {
        val w  = paperWidthMm.toDoubleOrNull() ?: return@remember null
        val ml = marginLeft.toDoubleOrNull() ?: return@remember null
        val mr = marginRight.toDoubleOrNull() ?: return@remember null
        val gh = gapH.toDoubleOrNull() ?: return@remember null
        val c  = columns.toIntOrNull()?.coerceAtLeast(1) ?: return@remember null
        (w - ml - mr - (c - 1) * gh) / c
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "New Label Template" else "Edit Template",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                // ── Template name ────────────────────────────────────────
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Template Name") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // ── Paper type selector ──────────────────────────────────
                Text("Paper Type", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LabelTemplate.PaperType.entries.forEachIndexed { idx, type ->
                        SegmentedButton(
                            selected = paperType == type,
                            onClick = {
                                paperType = type
                                if (type == LabelTemplate.PaperType.A4_SHEET) {
                                    paperWidthMm = "210"
                                    marginTop = "10"; marginBottom = "10"
                                    marginLeft = "10"; marginRight = "10"
                                    columns = "3"; rows = "8"
                                } else {
                                    paperWidthMm = "58"; columns = "1"; rows = "0"
                                    marginBottom = "0"
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(idx, LabelTemplate.PaperType.entries.size),
                        ) {
                            Text(if (type == LabelTemplate.PaperType.CONTINUOUS_ROLL) "Roll" else "A4 Sheet")
                        }
                    }
                }

                // ── Dimensions ───────────────────────────────────────────
                HorizontalDivider()
                Text("Dimensions (mm)", style = MaterialTheme.typography.labelLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    MmTextField(
                        value = paperWidthMm,
                        onValueChange = { paperWidthMm = it },
                        label = if (isContinuous) "Roll Width" else "Page Width",
                        modifier = Modifier.weight(1f),
                        readOnly = !isContinuous,
                    )
                    MmTextField(
                        value = labelHeightMm,
                        onValueChange = { labelHeightMm = it },
                        label = "Label Height",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    MmTextField(
                        value = columns,
                        onValueChange = { columns = it },
                        label = "Columns",
                        modifier = Modifier.weight(1f),
                    )
                    if (!isContinuous) {
                        MmTextField(
                            value = rows,
                            onValueChange = { rows = it },
                            label = "Rows",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Gaps ─────────────────────────────────────────────────
                HorizontalDivider()
                Text("Gaps (mm)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    MmTextField(
                        value = gapH,
                        onValueChange = { gapH = it },
                        label = "Horizontal Gap",
                        modifier = Modifier.weight(1f),
                    )
                    MmTextField(
                        value = gapV,
                        onValueChange = { gapV = it },
                        label = "Vertical Gap",
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Margins ──────────────────────────────────────────────
                HorizontalDivider()
                Text("Margins (mm)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    MmTextField(
                        value = marginTop,
                        onValueChange = { marginTop = it },
                        label = "Top",
                        modifier = Modifier.weight(1f),
                    )
                    MmTextField(
                        value = marginBottom,
                        onValueChange = { marginBottom = it },
                        label = "Bottom",
                        modifier = Modifier.weight(1f),
                        readOnly = isContinuous,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    MmTextField(
                        value = marginLeft,
                        onValueChange = { marginLeft = it },
                        label = "Left",
                        modifier = Modifier.weight(1f),
                    )
                    MmTextField(
                        value = marginRight,
                        onValueChange = { marginRight = it },
                        label = "Right",
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── Computed label dimension preview ─────────────────────
                if (computedWidth != null && computedWidth > 0) {
                    val heightVal = labelHeightMm.toDoubleOrNull()
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (heightVal != null) {
                                "Each label: %.1f × %.1f mm".format(computedWidth, heightVal)
                            } else {
                                "Label width: %.1f mm".format(computedWidth)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isValid = !nameError &&
                    (paperWidthMm.toDoubleOrNull() ?: -1.0) > 0 &&
                    (labelHeightMm.toDoubleOrNull() ?: -1.0) > 0 &&
                    (columns.toIntOrNull() ?: 0) in 1..4
            FilledTonalButton(
                onClick = {
                    val built = LabelTemplate(
                        id               = template?.id ?: "custom-${Clock.System.now().toEpochMilliseconds()}",
                        name             = name.trim(),
                        paperType        = paperType,
                        paperWidthMm     = paperWidthMm.toDoubleOrNull() ?: 58.0,
                        labelHeightMm    = labelHeightMm.toDoubleOrNull() ?: 25.0,
                        columns          = columns.toIntOrNull()?.coerceIn(1, 4) ?: 1,
                        rows             = rows.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                        gapHorizontalMm  = gapH.toDoubleOrNull() ?: 2.0,
                        gapVerticalMm    = gapV.toDoubleOrNull() ?: 3.0,
                        marginTopMm      = marginTop.toDoubleOrNull() ?: 2.0,
                        marginBottomMm   = if (isContinuous) 0.0 else marginBottom.toDoubleOrNull() ?: 0.0,
                        marginLeftMm     = marginLeft.toDoubleOrNull() ?: 2.0,
                        marginRightMm    = marginRight.toDoubleOrNull() ?: 2.0,
                        isDefault        = template?.isDefault ?: false,
                        createdAt        = template?.createdAt ?: now,
                        updatedAt        = now,
                    )
                    onSave(built)
                },
                enabled = isValid,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
