package com.zyntasolutions.zyntapos.license.di

import com.zyntasolutions.zyntapos.license.config.LicenseConfig
import com.zyntasolutions.zyntapos.license.service.LicenseService
import org.koin.dsl.module

val licenseModule = module {
    single { LicenseConfig.fromEnvironment() }
    single { LicenseService() }
}
