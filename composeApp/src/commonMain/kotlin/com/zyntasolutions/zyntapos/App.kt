package com.zyntasolutions.zyntapos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Root composable for ZyntaPOS.
 *
 * This is the top-level entry point shared across Android and Desktop targets.
 * The full navigation graph and MVI wiring will be assembled here in Sprint 11
 * once :composeApp:navigation is implemented.
 *
 * Platform detection (previously handled via the wizard's `Greeting` scaffold)
 * has been relocated to `:shared:core` as `com.zynta.pos.core.Platform`.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // TODO Sprint 11 — Replace with ZyntaNavGraph(navController)
                Text(
                    text = "ZyntaPOS — Initializing…",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
