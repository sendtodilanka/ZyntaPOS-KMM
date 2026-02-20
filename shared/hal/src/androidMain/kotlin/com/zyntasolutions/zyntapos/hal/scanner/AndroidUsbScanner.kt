package com.zyntasolutions.zyntapos.hal.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * ZyntaPOS — Hardware Abstraction Layer · Android USB HID Barcode Scanner
 *
 * Implements [BarcodeScanner] for **USB HID keyboard-wedge scanners** — the most
 * common form factor for fixed-mount POS barcode readers (Honeywell, Zebra, Datalogic,
 * Opticon, etc. in HID mode).
 *
 * ### How keyboard-wedge scanning works on Android
 * When a USB HID scanner reads a barcode, the device presents it to the OS as a
 * sequence of keyboard key-down / key-up events on a [InputDevice] with
 * `SOURCE_KEYBOARD`. Android dispatches these events through the normal input
 * pipeline. This class intercepts them via an [InputManager.InputDeviceListener]
 * and a low-level [KeyEvent] broadcast (registered via a foreground service or
 * via the Activity's `dispatchKeyEvent` override bridged through [injectKeyEvent]).
 *
 * ### Prefix / suffix / separator configuration
 * Most scanner drivers can be programmed to wrap the barcode payload with:
 * - **Prefix character** — a control char (e.g., STX 0x02) sent *before* the payload.
 * - **Suffix / terminator** — typically CR (0x0D), LF (0x0A), or Tab (0x09) *after*.
 *
 * [AndroidUsbScanner] accumulates key characters between the prefix and the
 * terminator. If no prefix is configured, accumulation starts from the first
 * non-whitespace key after the prior scan was emitted.
 *
 * ### Integration with the UI layer
 * The Activity or composable should route all [KeyEvent] objects to [injectKeyEvent]:
 * ```kotlin
 * override fun dispatchKeyEvent(event: KeyEvent): Boolean {
 *     if (usbScanner.injectKeyEvent(event)) return true
 *     return super.dispatchKeyEvent(event)
 * }
 * ```
 *
 * @param context            Application context for [InputManager] and [BroadcastReceiver].
 * @param prefixChar         Optional control character that precedes every scan payload.
 *                           Set to `null` (default) if the scanner sends no prefix.
 * @param terminatorChar     Character that marks end-of-scan (default: `'\r'` — CR).
 * @param minBarcodeLength   Minimum payload length to be considered a valid barcode.
 *                           Shorter sequences are discarded as stray keyboard noise.
 */
class AndroidUsbScanner(
    private val context: Context,
    private val prefixChar: Char? = null,
    private val terminatorChar: Char = '\r',
    private val minBarcodeLength: Int = MIN_BARCODE_LENGTH,
) : BarcodeScanner {

    private val log = Logger.withTag("AndroidUsbScanner")

    private val _scanEvents = MutableSharedFlow<ScanResult>(
        replay = 0,
        extraBufferCapacity = SCAN_BUFFER_CAPACITY,
    )
    override val scanEvents: Flow<ScanResult> = _scanEvents.asSharedFlow()

    /** Accumulates keystrokes from the current scan sequence. */
    private val buffer = StringBuilder()

    /**
     * True when the prefix character has been received and we are accumulating
     * the barcode payload. Always `true` when [prefixChar] is `null`.
     */
    private var accumulating = prefixChar == null

    private var listening = false

    /** Tracks whether any USB keyboard-type device is connected. */
    private val inputManager by lazy {
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    }

    /**
     * Optional device-added/removed listener for logging and future device-specific
     * logic (e.g., switching baud rates or product-ID filtering).
     */
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            val dev = InputDevice.getDevice(deviceId) ?: return
            if (dev.isKeyboard) {
                log.i { "USB keyboard device added: id=$deviceId name=${dev.name}" }
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) = Unit

        override fun onInputDeviceRemoved(deviceId: Int) {
            log.i { "USB keyboard device removed: id=$deviceId" }
        }
    }

    // ── BarcodeScanner impl ─────────────────────────────────────────────────────

    override suspend fun startListening(): Result<Unit> = runCatching {
        if (listening) {
            log.d { "startListening() called while already listening — no-op" }
            return@runCatching
        }
        inputManager.registerInputDeviceListener(
            deviceListener,
            null, // use main thread handler
        )
        buffer.clear()
        accumulating = prefixChar == null
        listening = true
        log.i {
            "USB HID scanner listening — prefix=${prefixChar?.code?.toString(16) ?: "none"} " +
                    "terminator=0x${terminatorChar.code.toString(16)}"
        }
    }

    override suspend fun stopListening() {
        inputManager.unregisterInputDeviceListener(deviceListener)
        buffer.clear()
        accumulating = prefixChar == null
        listening = false
        log.i { "USB HID scanner stopped" }
    }

    // ── Public key-event bridge ──────────────────────────────────────────────────

    /**
     * Routes a [KeyEvent] from the Activity's `dispatchKeyEvent` or a global
     * [KeyEvent] intercept into the scanner's accumulation logic.
     *
     * Returns `true` if the event was consumed by the scanner (caller should
     * **not** let it propagate further); `false` if it should be treated as a
     * normal keyboard event by the UI.
     *
     * Only [KeyEvent.ACTION_DOWN] events are processed; ACTION_UP is ignored.
     */
    fun injectKeyEvent(event: KeyEvent): Boolean {
        if (!listening) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Only process events from keyboard-type sources (HID scanners appear as SOURCE_KEYBOARD)
        if (event.source and InputDevice.SOURCE_KEYBOARD == 0) return false

        val char = event.toChar() ?: return false

        // If a prefix is configured and we see it, start accumulating
        if (prefixChar != null && char == prefixChar) {
            buffer.clear()
            accumulating = true
            return true
        }

        if (!accumulating) return false

        // Terminator received — emit accumulated barcode
        if (char == terminatorChar) {
            val payload = buffer.toString()
            buffer.clear()
            accumulating = prefixChar == null // reset state

            if (payload.length >= minBarcodeLength) {
                // Infer format heuristically from payload length and character set
                val format = inferFormat(payload)
                _scanEvents.tryEmit(ScanResult.Barcode(value = payload, format = format))
                log.d { "USB HID scan emitted: $payload (format=$format, len=${payload.length})" }
            } else {
                log.d { "USB HID payload too short (len=${payload.length}) — discarded: $payload" }
            }
            return true
        }

        buffer.append(char)
        return true // consume — prevent typed characters from reaching a TextField
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Decodes a [KeyEvent] to a printable [Char] using the device's [KeyCharacterMap].
     * Returns `null` for non-printable / modifier keys that should not be accumulated.
     */
    private fun KeyEvent.toChar(): Char? {
        val map = try {
            KeyCharacterMap.load(deviceId)
        } catch (_: Exception) {
            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        }
        val unicodeChar = map.get(keyCode, metaState)
        return if (unicodeChar > 0) unicodeChar.toChar() else null
    }

    /**
     * Heuristic format inference from the raw payload string.
     * A USB HID scanner typically does not report the symbology — this mirrors
     * the logic used by keyboard-wedge drivers in the field.
     *
     * For business-critical lookups, the caller should always first try an exact
     * product / coupon DB lookup regardless of the inferred format.
     */
    private fun inferFormat(payload: String): BarcodeFormat {
        val digits = payload.all { it.isDigit() }
        return when {
            digits && payload.length == 13 -> BarcodeFormat.EAN_13
            digits && payload.length == 8  -> BarcodeFormat.EAN_8
            digits && payload.length == 12 -> BarcodeFormat.UPC_A
            digits && payload.length == 6  -> BarcodeFormat.UPC_E
            payload.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == ' ' }
                                           -> BarcodeFormat.CODE_128
            else                           -> BarcodeFormat.UNKNOWN
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    companion object {
        private const val SCAN_BUFFER_CAPACITY = 8
        private const val MIN_BARCODE_LENGTH = 4
    }
}

// ── Extension ────────────────────────────────────────────────────────────────────

/** `true` when the [InputDevice] is a keyboard or keyboard-like HID device. */
private val InputDevice.isKeyboard: Boolean
    get() = sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
