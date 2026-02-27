package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Immutable configuration snapshot for the label printer transport and print quality.
 *
 * Loaded from [LabelPrinterConfigRepository] and injected into [LabelPrinterManager].
 * The [type] field determines which underlying port implementation is used.
 *
 * @property type          Command language + transport combination.
 * @property tcpHost       IP address or hostname for ZPL_TCP / TSPL_TCP connections.
 * @property tcpPort       TCP port for network connections; standard is 9100.
 * @property serialPort    Serial port path for USB-serial connections (e.g. "/dev/ttyUSB0").
 * @property baudRate      Baud rate for serial connections; common values: 9600, 115200.
 * @property btAddress     Bluetooth device MAC address for BT connections.
 * @property darknessLevel Print darkness level 0–15 (ZPL: ~D command, TSPL: DENSITY).
 * @property speedLevel    Print speed level 0–14 (ZPL: ^PR command, TSPL: SPEED).
 */
data class LabelPrinterConfig(
    val type: LabelPrinterType = LabelPrinterType.NONE,
    val tcpHost: String = "",
    val tcpPort: Int = DEFAULT_TCP_PORT,
    val serialPort: String = "",
    val baudRate: Int = DEFAULT_BAUD_RATE,
    val btAddress: String = "",
    val darknessLevel: Int = DEFAULT_DARKNESS,
    val speedLevel: Int = DEFAULT_SPEED,
) {
    init {
        require(darknessLevel in 0..15) { "darknessLevel must be 0–15, got $darknessLevel" }
        require(speedLevel in 0..14) { "speedLevel must be 0–14, got $speedLevel" }
    }

    companion object {
        const val DEFAULT_TCP_PORT = 9100
        const val DEFAULT_BAUD_RATE = 9600
        const val DEFAULT_DARKNESS = 8
        const val DEFAULT_SPEED = 4

        val DEFAULT = LabelPrinterConfig()
    }
}
