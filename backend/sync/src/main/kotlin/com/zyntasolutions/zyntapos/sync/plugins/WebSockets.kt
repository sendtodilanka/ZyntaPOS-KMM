package com.zyntasolutions.zyntapos.sync.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = 30_000L
        timeoutMillis = 60_000L
        maxFrameSize = 1_048_576L // 1 MB — prevents memory exhaustion DoS
        masking = false
    }
}
