package com.zyntasolutions.zyntapos.feature.reports

import org.koin.dsl.module

/**
 * JVM (Desktop) platform Koin module for the reports feature.
 *
 * Binds [JvmReportExporter] as the [ReportExporter] singleton for Desktop targets.
 * Must be included alongside [reportsModule] in the root Desktop Koin graph:
 *
 * ```kotlin
 * startKoin {
 *     modules(reportsModule, jvmReportsModule, …)
 * }
 * ```
 */
val jvmReportsModule = module {
    single<ReportExporter> { JvmReportExporter() }
}
