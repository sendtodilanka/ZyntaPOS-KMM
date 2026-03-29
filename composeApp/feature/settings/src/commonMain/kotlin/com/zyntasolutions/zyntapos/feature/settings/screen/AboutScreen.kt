package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.platform.AppInfoProvider
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import org.koin.compose.koinInject

// ─────────────────────────────────────────────────────────────────────────────
// AboutScreen — app name, version, build date, open-source licences, support.
// Sprint 23 — Step 13.1.8
// ─────────────────────────────────────────────────────────────────────────────

private data class Licence(val library: String, val spdx: String, val url: String)

private val OPEN_SOURCE_LICENCES = listOf(
    Licence("Kotlin",                    "Apache-2.0", "https://kotlinlang.org"),
    Licence("Compose Multiplatform",     "Apache-2.0", "https://www.jetbrains.com/lp/compose-multiplatform/"),
    Licence("SQLDelight",                "Apache-2.0", "https://cashapp.github.io/sqldelight/"),
    Licence("SQLCipher",                 "BSD-style",  "https://www.zetetic.net/sqlcipher/"),
    Licence("Koin",                      "Apache-2.0", "https://insert-koin.io/"),
    Licence("Ktor",                      "Apache-2.0", "https://ktor.io/"),
    Licence("Coil",                      "Apache-2.0", "https://coil-kt.github.io/coil/"),
    Licence("Kotlinx.datetime",          "Apache-2.0", "https://github.com/Kotlin/kotlinx-datetime"),
    Licence("Kotlinx.serialization",     "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    Licence("Kotlinx.coroutines",        "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Licence("Material Design 3",         "Apache-2.0", "https://m3.material.io/"),
    Licence("Mockative",                 "MIT",        "https://github.com/mockative/mockative"),
)

/**
 * About screen displaying app identity, version, and open-source licence notices.
 *
 * Version and build date are resolved from the platform-specific [AppInfoProvider]
 * via Koin injection — no hardcoded defaults needed.
 *
 * @param onBack       Back navigation.
 * @param appInfo      Platform-specific build metadata provider (injected via Koin).
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AboutScreen(
    onBack: () -> Unit,
    appInfo: AppInfoProvider = koinInject(),
) {
    val s = LocalStrings.current
    val buildDate = appInfo.buildDate
    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_ABOUT],
        onNavigateBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── App identity card ─────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = s[StringResource.COMMON_APP_NAME],
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = s[StringResource.SETTINGS_ABOUT_TAGLINE],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = ZyntaSpacing.lg),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ── Build info ────────────────────────────────────────────────────
            item {
                SectionHeader(s[StringResource.SETTINGS_ABOUT_BUILD_INFO])
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_VERSION], value = appInfo.fullVersionString)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_BUILD_DATE], value = buildDate)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_PLATFORM], value = appInfo.platformName)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_UI_FRAMEWORK], value = s[StringResource.SETTINGS_ABOUT_COMPOSE_MULTIPLATFORM])
                }
            }

            // ── Support contact ───────────────────────────────────────────────
            item {
                SectionHeader(s[StringResource.SETTINGS_ABOUT_SUPPORT])
                Spacer(Modifier.height(ZyntaSpacing.sm))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_EMAIL_LABEL], value = s[StringResource.SETTINGS_ABOUT_SUPPORT_EMAIL])
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                    InfoRow(label = s[StringResource.SETTINGS_ABOUT_WEBSITE_LABEL], value = s[StringResource.SETTINGS_ABOUT_WEBSITE_URL])
                }
            }

            // ── Open-source licences ──────────────────────────────────────────
            item { SectionHeader(s[StringResource.SETTINGS_ABOUT_OPEN_SOURCE_LICENCES]) }

            items(OPEN_SOURCE_LICENCES) { licence ->
                LicenceRow(licence)
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun LicenceRow(licence: Licence) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(licence.library, style = MaterialTheme.typography.bodyMedium) },
            supportingContent = {
                Text(
                    text = "${licence.spdx}  •  ${licence.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun AboutScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        AboutScreen(onBack = {})
    }
}
