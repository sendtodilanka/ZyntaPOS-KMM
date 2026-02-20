package com.zyntasolutions.zyntapos.hal.scanner

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

/**
 * ZentaPOS — HAL | Desktop (JVM) Implementation
 *
 * [DesktopHidScanner] captures barcode data from a **USB HID keyboard-wedge** scanner
 * — the most common desktop scanner connectivity mode.
 *
 * ### How keyboard-wedge scanners work
 * The scanner enumerates as a standard USB HID keyboard.  When a barcode is decoded
 * the scanner "types" the barcode digits at high speed (typically < 50 ms total) and
 * terminates with a configurable suffix character (usually `\r` or `\n`).
 *
 * ### Distinguishing scan from human keystrokes
 * Two heuristics are combined:
 * 1. **Inter-key timing** — a scan burst arrives at ≥ [MIN_CHARS_PER_SCAN] chars within
 *    [SCAN_WINDOW_MS] milliseconds.  Human typing is far slower (> 50 ms / key).
 * 2. **Prefix character** — if [prefixChar] is non-null the buffer is only accepted as
 *    a barcode when the first key matches that character (prefix is stripped from output).
 * 3. **Terminator** — accumulation stops on [terminatorChar] (default `\r` / Enter).
 *
 * ### AWT integration
 * A [KeyEventDispatcher] is registered with the [KeyboardFocusManager].  It intercepts
 * `KEY_TYPED` events **before** they reach any focused Compose or Swing component.
 * The dispatcher returns `true` (consuming the event) only for events that are
 * confirmed to belong to a scan burst, so normal keyboard input is unaffected.
 *
 * > **Compose interop note:** Compose Desktop routes its own key events through AWT's
 * > focus manager, so this approach works transparently with `ComposeWindow`.
 *
 * @param prefixChar      Optional first character the scanner sends before the barcode
 *                        data (e.g. STX = `\u0002`).  When set, only input starting with
 *                        this char is treated as a scan.  `null` disables prefix filtering.
 * @param terminatorChar  The character that signals end-of-barcode.  Default `'\r'` (Enter).
 * @param minBarcodeLen   Minimum decoded length to emit as a barcode event.  Default 4.
 */
class DesktopHidScanner(
    private val prefixChar: Char? = null,
    private val terminatorChar: Char = '\r',
    private val minBarcodeLen: Int = 4,
) : BarcodeScanner {

    // ──────────────────────────────────────────────────────────────────────────
    // Flow infrastructure
    // ──────────────────────────────────────────────────────────────────────────

    private val _channel = Channel<ScanResult>(capacity = Channel.BUFFERED)
    private val _scanEvents: Flow<ScanResult> = _channel.receiveAsFlow()

    override val scanEvents: Flow<ScanResult> get() = _scanEvents

    // ──────────────────────────────────────────────────────────────────────────
    // AWT dispatcher state
    // ──────────────────────────────────────────────────────────────────────────

    private val buffer   = StringBuilder()
    private var lastKeyTime = 0L
    private var registered  = false

    private val dispatcher = KeyEventDispatcher { event ->
        if (event.id != KeyEvent.KEY_TYPED) return@KeyEventDispatcher false

        val ch  = event.keyChar
        val now = System.currentTimeMillis()

        // Reset buffer if inter-key gap exceeds scan window (human typing pause)
        if (now - lastKeyTime > SCAN_WINDOW_MS && buffer.isNotEmpty()) {
            buffer.clear()
        }
        lastKeyTime = now

        when {
            // Terminator received — evaluate accumulated buffer
            ch == terminatorChar -> {
                val barcode = buffer.toString()
                    .let { if (prefixChar != null && it.startsWith(prefixChar)) it.drop(1) else it }

                buffer.clear()

                if (barcode.length >= minBarcodeLen) {
                    val result = ScanResult.Barcode(
                        value  = barcode,
                        format = inferFormat(barcode),
                    )
                    _channel.trySend(result)
                    true   // consume the Enter keystroke
                } else {
                    false  // too short — not a barcode, pass through
                }
            }

            // Prefix check: if prefix is configured and buffer is empty, require match
            buffer.isEmpty() && prefixChar != null && ch != prefixChar -> {
                false // not a scan sequence — pass to focused component
            }

            // Accumulate scan character
            else -> {
                buffer.append(ch)
                false // don't consume mid-scan chars (allows partial echo if needed)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BarcodeScanner implementation
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun startListening(): Result<Unit> = runCatching {
        if (registered) return@runCatching
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(dispatcher)
        registered = true
    }

    override suspend fun stopListening() {
        if (!registered) return
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .removeKeyEventDispatcher(dispatcher)
        registered = false
        buffer.clear()
        _channel.close()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Format inference (lightweight — scanner sends no symbology metadata)
    // ──────────────────────────────────────────────────────────────────────────

    private fun inferFormat(value: String): BarcodeFormat {
        if (value.all { it.isDigit() }) {
            return when (value.length) {
                13   -> BarcodeFormat.EAN_13
                12   -> BarcodeFormat.UPC_A
                8    -> BarcodeFormat.EAN_8
                else -> BarcodeFormat.UNKNOWN
            }
        }
        return BarcodeFormat.CODE_128 // alphanumeric → most likely Code 128 / Code 39
    }

    companion object {
        /**
         * Maximum inter-key interval (ms) within a scan burst.
         * A keyboard wedge scanner typically delivers all characters in < 20 ms total.
         */
        private const val SCAN_WINDOW_MS = 80L
    }
}
