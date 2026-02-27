package com.zyntasolutions.zyntapos.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme
import kotlin.test.Test

// ─────────────────────────────────────────────────────────────────────────────
// DesignSystemUiTest — Compose Desktop rendering tests for ZyntaTheme components.
//
// Requires a display (virtual or physical). Run headlessly with:
//   xvfb-run -a ./gradlew :composeApp:designsystem:jvmTest
//
// These tests exercise actual Compose UI rendering, not just logic.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTestApi::class)
class DesignSystemUiTest {

    @Test
    fun `ZyntaButton displays its label text`() = runDesktopComposeUiTest {
        setContent {
            ZyntaTheme {
                ZyntaButton(text = "Save", onClick = {})
            }
        }
        onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun `ZyntaButton is enabled by default`() = runDesktopComposeUiTest {
        setContent {
            ZyntaTheme {
                ZyntaButton(text = "Confirm", onClick = {})
            }
        }
        onNodeWithText("Confirm").assertIsEnabled()
    }
}
