package com.zyntasolutions.zyntapos.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings

/**
 * Full-screen placeholder shown when a feature is not enabled for the current store.
 *
 * Displayed instead of the actual feature screen when the feature's edition gate is
 * not satisfied by the current store's active edition.
 *
 * @param featureName Human-readable name of the locked feature (e.g. "Staff & HR").
 * @param requiredEdition Human-readable edition name required to unlock this feature
 *   (e.g. "Premium", "Enterprise").
 * @param onNavigateBack Callback invoked when the user taps "Go Back".
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@Composable
fun FeatureLockedScreen(
    featureName: String,
    requiredEdition: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = s[StringResource.DESIGNSYSTEM_LOCKED_CD],
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = s[StringResource.DESIGNSYSTEM_FEATURE_LOCKED],
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = s[StringResource.DESIGNSYSTEM_FEATURE_LOCKED_DESC_FORMAT, featureName, requiredEdition],
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onNavigateBack) {
            Text(s[StringResource.DESIGNSYSTEM_GO_BACK])
        }
    }
}
