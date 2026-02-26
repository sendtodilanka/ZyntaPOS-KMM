package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaButton — Stateless, state hoisted to caller
// Variants: Primary, Secondary, Danger, Ghost, Icon
// Sizes: Small(32dp), Medium(40dp), Large(56dp)
// States: enabled, isLoading, disabled
// ─────────────────────────────────────────────────────────────────────────────

/** Button size token controlling height and horizontal padding. */
enum class ZyntaButtonSize(val height: Dp, val horizontalPadding: Dp) {
    Small(32.dp, 12.dp),
    Medium(40.dp, 16.dp),
    Large(56.dp, 24.dp),
}

/** Visual style variant for [ZyntaButton]. */
enum class ZyntaButtonVariant { Primary, Secondary, Danger, Ghost, Icon }

/**
 * The primary button component for ZyntaPOS.
 *
 * @param text Label text (unused for [ZyntaButtonVariant.Icon]).
 * @param onClick Invoked when the button is tapped and not loading/disabled.
 * @param modifier Optional [Modifier].
 * @param variant Visual style — Primary, Secondary, Danger, Ghost, or Icon.
 * @param size Controls minimum height and padding — Small/Medium/Large.
 * @param isLoading When true, replaces label with [CircularProgressIndicator] and blocks input.
 * @param enabled When false, renders in disabled style and blocks input.
 * @param leadingIcon Optional icon shown to the left of the label.
 * @param iconImageVector Required for [ZyntaButtonVariant.Icon] variant.
 * @param iconContentDescription Accessibility description for [ZyntaButtonVariant.Icon].
 */
@Composable
fun ZyntaButton(
    text: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ZyntaButtonVariant = ZyntaButtonVariant.Primary,
    size: ZyntaButtonSize = ZyntaButtonSize.Medium,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    iconImageVector: ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val isInteractable = enabled && !isLoading

    when (variant) {
        ZyntaButtonVariant.Icon -> {
            IconButton(
                onClick = { if (isInteractable) onClick() },
                enabled = isInteractable,
                modifier = modifier.size(size.height),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size((size.height - 8.dp)),
                        strokeWidth = 2.dp,
                    )
                } else {
                    if (iconImageVector != null) {
                        Icon(
                            imageVector = iconImageVector,
                            contentDescription = iconContentDescription,
                        )
                    }
                }
            }
        }

        ZyntaButtonVariant.Secondary -> {
            OutlinedButton(
                onClick = { if (isInteractable) onClick() },
                enabled = isInteractable,
                modifier = modifier.height(size.height),
                contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
            ) { ButtonContent(text, isLoading, leadingIcon, size) }
        }

        ZyntaButtonVariant.Ghost -> {
            TextButton(
                onClick = { if (isInteractable) onClick() },
                enabled = isInteractable,
                modifier = modifier.height(size.height),
                contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
            ) { ButtonContent(text, isLoading, leadingIcon, size) }
        }

        ZyntaButtonVariant.Danger -> {
            Button(
                onClick = { if (isInteractable) onClick() },
                enabled = isInteractable,
                modifier = modifier.height(size.height),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f),
                ),
                contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
            ) { ButtonContent(text, isLoading, leadingIcon, size) }
        }

        ZyntaButtonVariant.Primary -> {
            Button(
                onClick = { if (isInteractable) onClick() },
                enabled = isInteractable,
                modifier = modifier.height(size.height),
                contentPadding = PaddingValues(horizontal = size.horizontalPadding, vertical = 0.dp),
            ) { ButtonContent(text, isLoading, leadingIcon, size) }
        }
    }
}

@Composable
private fun RowScope.ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: ImageVector?,
    size: ZyntaButtonSize,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size((size.height - 12.dp)),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.CenterVertically),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text)
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaButtonPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaButton(text = "Confirm", onClick = {})
    }
}
