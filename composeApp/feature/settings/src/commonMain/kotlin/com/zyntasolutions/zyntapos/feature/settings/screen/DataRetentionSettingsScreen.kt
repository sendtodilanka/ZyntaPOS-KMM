package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.feature.settings.DataRetentionIntent
import com.zyntasolutions.zyntapos.feature.settings.DataRetentionState

/** Bisect stub — temporarily strip everything to isolate the build failure. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DataRetentionSettingsScreen(
    state: DataRetentionState,
    onIntent: (DataRetentionIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    @Suppress("UNUSED_PARAMETER")
    val unused = listOf(state, onIntent)
    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_DATA_RETENTION],
        onNavigateBack = onBack,
    ) { _ ->
        Text("Data Retention (bisect stub)")
    }
}
