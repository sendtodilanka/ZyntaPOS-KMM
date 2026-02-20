package com.zyntasolutions.zyntapos.core

import android.os.Build

/**
 * Android [Platform] actual — reports the device's Android API level.
 */
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
