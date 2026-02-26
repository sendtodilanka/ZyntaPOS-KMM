package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaElevation
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSettingsItem — Professional settings list item
// ZyntaSettingsGroup — Card container that groups multiple settings items
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A professional settings list item with icon, title, subtitle, and trailing content.
 *
 * @param icon Leading icon.
 * @param title Primary label.
 * @param modifier Optional [Modifier].
 * @param subtitle Secondary description text.
 * @param iconTint Tint color for the icon background and icon.
 * @param onClick Navigation/action callback.
 * @param trailing Custom trailing content (default: chevron arrow for navigation).
 */
@Composable
fun ZyntaSettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = ZyntaSpacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon in tonal container
        Surface(
            shape = MaterialTheme.shapes.small,
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(ZyntaSpacing.md))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Trailing content
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Toggle variant of settings item with a Switch.
 */
@Composable
fun ZyntaSettingsToggle(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    ZyntaSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        iconTint = iconTint,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

/**
 * Groups multiple [ZyntaSettingsItem]s into a Card container with dividers.
 *
 * @param title Optional group header.
 * @param modifier Optional [Modifier].
 * @param content Settings items to render inside the group.
 */
@Composable
fun ZyntaSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = ZyntaSpacing.md,
                    bottom = ZyntaSpacing.xs,
                ),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1),
            shape = MaterialTheme.shapes.medium,
        ) {
            content()
        }
    }
}

/**
 * Divider between [ZyntaSettingsItem]s within a [ZyntaSettingsGroup].
 */
@Composable
fun ZyntaSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaSettingsItemPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        androidx.compose.foundation.layout.Column {
            ZyntaSettingsItem(
                icon = androidx.compose.material.icons.Icons.Default.Security,
                title = "Security",
                subtitle = "PIN, auto-lock, RBAC",
                onClick = {},
            )
            ZyntaSettingsToggle(
                icon = androidx.compose.material.icons.Icons.Default.Notifications,
                title = "Notifications",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}
