package com.zyntasolutions.zyntapos.domain.model

/**
 * Domain-layer configuration for the label printer.
 *
 * This is the **domain representation** — it uses plain `String` for the printer
 * type to avoid importing HAL enums into the domain layer. The data layer maps
 * this to/from the HAL `LabelPrinterType` enum.
 *
 * @property printerType  Transport + language string: "NONE", "ZPL_TCP", "ZPL_USB",
 *                        "ZPL_BT", "TSPL_TCP", "TSPL_USB", "TSPL_BT", "PDF_SYSTEM".
 * @property tcpHost      IP address / hostname for TCP label printers.
 * @property tcpPort      TCP port (default 9100).
 * @property serialPort   Serial port descriptor (e.g. "/dev/ttyUSB0", "COM4").
 * @property baudRate     Serial baud rate (default 9600).
 * @property btAddress    Bluetooth MAC address for wireless label printers.
 * @property darknessLevel Print darkness / density (0–15, Zebra/TSC scale).
 * @property speedLevel    Print speed (0–14, Zebra/TSC scale).
 */
data class LabelPrinterConfig(
    val printerType: String = "NONE",
    val tcpHost: String = "",
    val tcpPort: Int = 9100,
    val serialPort: String = "",
    val baudRate: Int = 9600,
    val btAddress: String = "",
    val darknessLevel: Int = 8,
    val speedLevel: Int = 4,
) {
    companion object {
        val DEFAULT = LabelPrinterConfig()
    }
}
