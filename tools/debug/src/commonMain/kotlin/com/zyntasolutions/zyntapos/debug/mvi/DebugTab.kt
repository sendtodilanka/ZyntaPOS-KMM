package com.zyntasolutions.zyntapos.debug.mvi

/**
 * The six tab categories in the Debug Console.
 *
 * Ordering determines the visual left-to-right tab position.
 */
enum class DebugTab(val label: String) {
    Seeds("Seeds"),
    Database("Database"),
    Auth("Auth"),
    Network("Network"),
    Diagnostics("Diagnostics"),
    UiUx("UI / UX"),
}
