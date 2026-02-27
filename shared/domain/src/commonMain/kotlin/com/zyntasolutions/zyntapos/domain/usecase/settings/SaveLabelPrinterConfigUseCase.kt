package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.repository.LabelPrinterConfigRepository

/**
 * Validates and persists a [LabelPrinterConfig].
 *
 * **Validation rules:**
 * - For TCP types (`*_TCP`): [LabelPrinterConfig.tcpHost] must not be blank and
 *   [LabelPrinterConfig.tcpPort] must be in range 1..65535.
 * - For serial types (`*_USB`): [LabelPrinterConfig.serialPort] must not be blank.
 * - For Bluetooth types (`*_BT`): [LabelPrinterConfig.btAddress] must not be blank.
 * - [LabelPrinterConfig.darknessLevel] must be in 0..15.
 * - [LabelPrinterConfig.speedLevel] must be in 1..12.
 *
 * @param repository Persistence contract for the singleton label-printer config.
 */
class SaveLabelPrinterConfigUseCase(
    private val repository: LabelPrinterConfigRepository,
) {

    /**
     * Saves the given [config] after validation.
     *
     * @return [Result.Success] on success; [Result.Error] wrapping a
     *         [ValidationException] if validation fails.
     */
    suspend operator fun invoke(config: LabelPrinterConfig): Result<Unit> {
        val type = config.printerType.uppercase()

        if (type.endsWith("_TCP")) {
            if (config.tcpHost.isBlank()) {
                return Result.Error(
                    ValidationException("TCP host must not be blank.", field = "tcpHost", rule = "HOST_BLANK")
                )
            }
            if (config.tcpPort !in 1..65535) {
                return Result.Error(
                    ValidationException("TCP port must be in 1..65535.", field = "tcpPort", rule = "PORT_RANGE")
                )
            }
        }

        if (type.endsWith("_USB")) {
            if (config.serialPort.isBlank()) {
                return Result.Error(
                    ValidationException("Serial port must not be blank.", field = "serialPort", rule = "SERIAL_BLANK")
                )
            }
        }

        if (type.endsWith("_BT")) {
            if (config.btAddress.isBlank()) {
                return Result.Error(
                    ValidationException(
                        "Bluetooth address must not be blank.",
                        field = "btAddress",
                        rule = "BT_ADDRESS_BLANK",
                    )
                )
            }
        }

        if (config.darknessLevel !in 0..15) {
            return Result.Error(
                ValidationException(
                    "Darkness level must be in 0..15.",
                    field = "darknessLevel",
                    rule = "DARKNESS_RANGE",
                )
            )
        }

        if (config.speedLevel !in 1..12) {
            return Result.Error(
                ValidationException("Speed level must be in 1..12.", field = "speedLevel", rule = "SPEED_RANGE")
            )
        }

        return repository.save(config)
    }
}
