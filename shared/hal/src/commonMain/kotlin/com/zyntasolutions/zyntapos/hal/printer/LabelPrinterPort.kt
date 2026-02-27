package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * [LabelPrinterPort] defines the platform-agnostic contract for communicating with a
 * dedicated barcode label printer over any transport (USB, Bluetooth, TCP/IP).
 *
 * Label printers (Zebra, TSC, SATO, etc.) use proprietary command languages (ZPL, TSPL)
 * rather than ESC/POS. This interface accepts pre-built command byte arrays produced by
 * [ZplLabelBuilder] or [TsplLabelBuilder].
 *
 * Implementations live in `androidMain` or `jvmMain`; commonMain business logic depends
 * solely on this interface.
 */
interface LabelPrinterPort {

    /**
     * Opens a connection to the label printer.
     * Idempotent — calling when already connected returns [Result.success] immediately.
     */
    suspend fun connect(): Result<Unit>

    /** Closes the connection and releases all underlying resources. */
    suspend fun disconnect(): Result<Unit>

    /** Returns `true` if the transport is open and the printer is reachable. */
    suspend fun isConnected(): Boolean

    /**
     * Transmits a raw ZPL command byte array to a Zebra-compatible label printer.
     *
     * @param commands ZPL commands assembled by [ZplLabelBuilder].
     * @return [Result.success] when transmitted; [Result.failure] on error.
     */
    suspend fun printZpl(commands: ByteArray): Result<Unit>

    /**
     * Transmits a raw TSPL command byte array to a TSC-compatible label printer.
     *
     * @param commands TSPL commands assembled by [TsplLabelBuilder].
     * @return [Result.success] when transmitted; [Result.failure] on error.
     */
    suspend fun printTspl(commands: ByteArray): Result<Unit>
}
