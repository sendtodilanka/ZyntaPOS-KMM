package com.zyntasolutions.zyntapos.feature.reports

import android.content.Context
import org.koin.dsl.module

/**
 * Android-specific Koin module for the reports feature.
 * Binds [AndroidReportExporter] as the [ReportExporter] implementation.
 */
fun androidReportsModule(context: Context) = module {
    single<ReportExporter> { AndroidReportExporter(context) }
}
