package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Aggregates [PrinterStatusEvent]s from [PrinterPort.statusEvents] into a single
 * [PrinterStatus] snapshot exposed as a hot [StateFlow].
 *
 * ### Usage (UI layer)
 * ```kotlin
 * val statusState by printerStatusMonitor.statusState.collectAsState()
 * if (statusState.isPaperOut) { ShowPaperOutBanner() }
 * ```
 *
 * @param port  The active [PrinterPort]. Status events are sourced from [PrinterPort.statusEvents].
 * @param scope Optional [CoroutineScope]; defaults to [Dispatchers.IO] + [SupervisorJob].
 */
class PrinterStatusMonitor(
    private val port: PrinterPort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _statusState = MutableStateFlow(PrinterStatus())

    /**
     * Hot [StateFlow] reflecting the latest known printer status.
     * Starts as [PrinterStatus] (all `false`); updated as events arrive from the port.
     */
    val statusState: StateFlow<PrinterStatus> = _statusState.asStateFlow()

    init {
        scope.launch {
            port.statusEvents.collect { event ->
                _statusState.update { current -> current.applyEvent(event) }
            }
        }
    }

    private fun PrinterStatus.applyEvent(event: PrinterStatusEvent): PrinterStatus = when (event) {
        PrinterStatusEvent.Online       -> copy(isOnline = true)
        PrinterStatusEvent.Offline      -> copy(isOnline = false)
        PrinterStatusEvent.PaperOut     -> copy(isPaperOut = true, isPaperLow = true)
        PrinterStatusEvent.PaperLow     -> copy(isPaperLow = true)
        PrinterStatusEvent.CoverOpen    -> copy(isCoverOpen = true)
        PrinterStatusEvent.CoverClosed  -> copy(isCoverOpen = false)
        PrinterStatusEvent.DrawerOpen   -> copy(isDrawerOpen = true)
        PrinterStatusEvent.DrawerClosed -> copy(isDrawerOpen = false)
    }
}
