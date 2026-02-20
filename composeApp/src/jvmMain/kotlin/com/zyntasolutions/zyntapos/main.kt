package com.zyntasolutions.zyntapos

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ZyntaPOS",
    ) {
        App()
    }
}