package com.zyntasolutions.zyntapos.feature.inventory.di

import com.zyntasolutions.zyntapos.feature.inventory.label.JvmLabelPdfRenderer
import com.zyntasolutions.zyntapos.feature.inventory.label.LabelPdfRenderer
import org.koin.dsl.module

/**
 * JVM Desktop Koin module for inventory feature platform bindings.
 *
 * Registers [JvmLabelPdfRenderer] as the [LabelPdfRenderer] for desktop.
 * Must be loaded alongside [inventoryModule] in the Desktop entry point (main.kt).
 */
val jvmInventoryLabelModule = module {
    single<LabelPdfRenderer> { JvmLabelPdfRenderer() }
}
