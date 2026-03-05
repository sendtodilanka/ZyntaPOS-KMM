package com.zyntasolutions.zyntapos.sync.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = 30_000L
        timeoutMillis = 60_000L
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
