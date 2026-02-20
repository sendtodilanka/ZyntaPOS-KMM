package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * [PrinterPort] defines the platform-agnostic contract for communicating with a
 * physical thermal receipt printer over any transport (USB, Bluetooth, TCP/IP, Serial).
 *
 * All implementations live in androidMain or jvmMain; commonMain business logic
 * depends solely on this interface, never on platform types.
 *
 * ### Error handling
 * Every operation returns [Result] so callers can react gracefully to hardware
 * failures without try/catch propagation through the call stack.
 */
interface PrinterPort {

    /**
     * Opens a connection to the printer.
     *
     * Implementations must be idempotent — calling [connect] when already connected
     * should return [Result.success] without reopening the transport.
     *
     * @return [Result.success] when the transport handshake succeeds,
     *         [Result.failure] with a descriptive [Exception] on any I/O error.
     */
    suspend fun connect(): Result<Unit>

    /**
     * Closes the connection and releases all underlying resources (sockets, file
     * descriptors, USB handles, etc.).
     *
     * Implementations must be safe to call even when already disconnected.
     *
     * @return [Result.success] after resources are released,
     *         [Result.failure] if the transport cannot be closed cleanly.
     */
    suspend fun disconnect(): Result<Unit>

    /**
     * Returns `true` if the transport channel is currently open and the printer
     * is reachable; `false` otherwise.
     *
     * This is a lightweight status check — it must **not** perform I/O.
     */
    suspend fun isConnected(): Boolean

    /**
     * Transmits a raw ESC/POS byte sequence to the printer.
     *
     * The caller is responsible for assembling a well-formed [commands] array
     * (typically via [ReceiptBuilder] or [com.zyntasolutions.zyntapos.hal.escpos.EscPosReceiptBuilder]).
     *
     * @param commands Raw ESC/POS command bytes ready for the printer.
     * @return [Result.success] when all bytes are transmitted without error,
     *         [Result.failure] on any transport or printer error.
     */
    suspend fun print(commands: ByteArray): Result<Unit>

    /**
     * Sends the printer-standard cash drawer kick pulse (ESC p command).
     *
     * Implementations should send the appropriate byte sequence for the connected
     * drawer (typically `ESC p 0 25 250` for a 48V drawer).
     *
     * @return [Result.success] when the pulse is sent,
     *         [Result.failure] if not connected or the drawer command is unsupported.
     */
    suspend fun openCashDrawer(): Result<Unit>

    /**
     * Issues a full or partial paper cut command (GS V).
     *
     * Behaviour depends on the printer model — most ESC/POS printers support at
     * least a partial cut. Full-cut printers will execute a full cut.
     *
     * @return [Result.success] when the cut command is transmitted,
     *         [Result.failure] on transport error.
     */
    suspend fun cutPaper(): Result<Unit>
}
