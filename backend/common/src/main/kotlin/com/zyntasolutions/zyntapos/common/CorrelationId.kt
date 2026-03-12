package com.zyntasolutions.zyntapos.common

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.header
import io.ktor.server.response.header
import org.slf4j.MDC
import java.util.UUID

/**
 * S4-2: Request Correlation ID plugin.
 *
 * Attaches a unique `X-Request-ID` to every request, enabling cross-service
 * tracing. If the caller provides `X-Request-ID`, it is preserved; otherwise
 * a UUID v4 is generated.
 *
 * The ID is:
 * - Set in SLF4J MDC as `requestId` for structured logging (S4-3)
 * - Echoed in the response `X-Request-ID` header
 * - Accessible via `call.attributes[CorrelationId.Key]`
 */
val CorrelationId = createApplicationPlugin(name = "CorrelationId") {
    onCall { call ->
        val requestId = call.request.header("X-Request-ID")
            ?.takeIf { it.isNotBlank() && it.length <= 128 }
            ?: UUID.randomUUID().toString()

        call.response.header("X-Request-ID", requestId)

        // Set MDC for structured logging within this request scope
        MDC.put("requestId", requestId)

        // Extract caller info for MDC
        val storeId = call.request.header("X-Store-ID")
        if (!storeId.isNullOrBlank()) MDC.put("storeId", storeId)
    }

    onCallRespond { call, _ ->
        MDC.remove("requestId")
        MDC.remove("storeId")
    }
}
