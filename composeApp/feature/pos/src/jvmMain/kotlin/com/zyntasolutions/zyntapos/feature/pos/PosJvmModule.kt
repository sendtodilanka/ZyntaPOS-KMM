package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.feature.pos.printer.A4PrintDelegate
import com.zyntasolutions.zyntapos.feature.pos.printer.DesktopA4PrintDelegate
import org.koin.dsl.module

/**
 * JVM/Desktop-specific Koin bindings for the POS feature.
 *
 * Provides the [A4PrintDelegate] implementation for JVM:
 * - [DesktopA4PrintDelegate] — writes to a temp file and opens via [java.awt.Desktop].
 *
 * Must be loaded alongside [posModule] in the desktop app's Koin setup.
 */
val posJvmModule = module {
    single<A4PrintDelegate> { DesktopA4PrintDelegate() }
}
