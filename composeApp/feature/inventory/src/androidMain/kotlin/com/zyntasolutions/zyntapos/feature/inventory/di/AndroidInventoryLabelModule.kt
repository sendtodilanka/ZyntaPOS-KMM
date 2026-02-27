package com.zyntasolutions.zyntapos.feature.inventory.di

import com.zyntasolutions.zyntapos.feature.inventory.label.AndroidLabelPdfRenderer
import com.zyntasolutions.zyntapos.feature.inventory.label.LabelPdfRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Android Koin module for inventory feature platform bindings.
 *
 * Registers [AndroidLabelPdfRenderer] as the [LabelPdfRenderer] for Android.
 * Must be loaded alongside [inventoryModule] in [ZyntaApplication.onCreate].
 */
val androidInventoryLabelModule = module {
    single<LabelPdfRenderer> { AndroidLabelPdfRenderer(context = androidContext()) }
}
