package com.zyntasolutions.zyntapos.domain.model

/**
 * Identifies the type of print job a [PrinterProfile] handles.
 *
 * Used for routing: the system selects the correct profile when
 * printing a receipt, kitchen ticket, label, or report.
 */
enum class PrinterJobType {
    /** Thermal or laser receipt printer (customer-facing). */
    RECEIPT,

    /** Kitchen order ticket printer (back-of-house). */
    KITCHEN,

    /** Label printer (Zebra ZPL / TSC TSPL / PDF). */
    LABEL,

    /** Report printer (A4 thermal or laser). */
    REPORT,
}

/**
 * A named, saveable hardware configuration for a single printer.
 *
 * Multiple profiles can exist (e.g. "Main Receipt", "Kitchen", "Label").
 * Each profile specifies a [jobType] so the routing layer can select the
 * appropriate hardware automatically during POS operations.
 *
 * @property id              UUID identifying this profile.
 * @property name            Human-readable name (e.g. "Front Counter Receipt").
 * @property jobType         Job type this profile handles — [PrinterJobType].
 * @property printerType     Transport protocol: "TCP", "SERIAL", "BLUETOOTH", "USB".
 * @property tcpHost         IP address / hostname for TCP printers.
 * @property tcpPort         TCP port (default 9100).
 * @property serialPort      Serial port descriptor (e.g. "/dev/ttyUSB0", "COM3").
 * @property baudRate        Serial baud rate (e.g. 115200).
 * @property btAddress       Bluetooth MAC address for BT printers.
 * @property paperWidthMm    Paper width in millimetres (58 or 80 for thermal).
 * @property isDefault       Whether this is the default profile for its [jobType].
 * @property backupProfileId ID of the backup/failover [PrinterProfile] to use if
 *                           this profile fails; `null` if no backup configured.
 * @property createdAt       Creation timestamp (epoch millis).
 * @property updatedAt       Last-updated timestamp (epoch millis).
 */
data class PrinterProfile(
    val id: String,
    val name: String,
    val jobType: PrinterJobType,
    val printerType: String,
    val tcpHost: String = "",
    val tcpPort: Int = 9100,
    val serialPort: String = "",
    val baudRate: Int = 115_200,
    val btAddress: String = "",
    val paperWidthMm: Int = 80,
    val isDefault: Boolean = false,
    val backupProfileId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
