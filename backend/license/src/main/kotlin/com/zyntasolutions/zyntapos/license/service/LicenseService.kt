package com.zyntasolutions.zyntapos.license.service

import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
import com.zyntasolutions.zyntapos.license.models.ActivateRequest
import com.zyntasolutions.zyntapos.license.models.ActivateResponse
import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import com.zyntasolutions.zyntapos.license.models.HeartbeatResponse
import com.zyntasolutions.zyntapos.license.models.LicenseStatusResponse
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.util.UUID

class LicenseService {
    private val logger = LoggerFactory.getLogger(LicenseService::class.java)

    suspend fun activate(request: ActivateRequest): ActivateResponse? = newSuspendedTransaction {
        logger.info("Activate: key=****${request.licenseKey.takeLast(4)} device=${request.deviceId}")

        val license = Licenses.selectAll().where { Licenses.key eq request.licenseKey }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val status = license[Licenses.status]
        val edition = license[Licenses.edition]
        val maxDevices = license[Licenses.maxDevices]
        val expiresAt = license[Licenses.expiresAt]

        if (status != "ACTIVE") {
            return@newSuspendedTransaction ActivateResponse(
                isValid = false,
                licenseKey = maskKey(request.licenseKey),
                edition = edition,
                maxDevices = maxDevices,
                activeDevices = 0,
                expiresAt = expiresAt?.toInstant()?.toEpochMilli(),
                errorCode = "LICENSE_$status",
                message = "License is $status"
            )
        }

        if (expiresAt != null && expiresAt.isBefore(OffsetDateTime.now())) {
            return@newSuspendedTransaction ActivateResponse(
                isValid = false,
                licenseKey = maskKey(request.licenseKey),
                edition = edition,
                maxDevices = maxDevices,
                activeDevices = 0,
                expiresAt = expiresAt.toInstant().toEpochMilli(),
                errorCode = "LICENSE_EXPIRED",
                message = "License has expired"
            )
        }

        // Count active devices excluding this one (re-activation case)
        val otherDevices = DeviceRegistrations.selectAll().where {
            (DeviceRegistrations.licenseKey eq request.licenseKey) and
                (DeviceRegistrations.deviceId neq request.deviceId)
        }.count().toInt()

        if (otherDevices >= maxDevices) {
            return@newSuspendedTransaction ActivateResponse(
                isValid = false,
                licenseKey = maskKey(request.licenseKey),
                edition = edition,
                maxDevices = maxDevices,
                activeDevices = otherDevices,
                expiresAt = expiresAt?.toInstant()?.toEpochMilli(),
                errorCode = "DEVICE_LIMIT_REACHED",
                message = "Maximum $maxDevices devices already registered"
            )
        }

        val now = OffsetDateTime.now()
        DeviceRegistrations.upsert(
            DeviceRegistrations.licenseKey, DeviceRegistrations.deviceId
        ) {
            it[id] = UUID.randomUUID().toString()
            it[licenseKey] = request.licenseKey
            it[deviceId] = request.deviceId
            it[deviceName] = request.deviceName
            it[appVersion] = request.appVersion
            it[osVersion] = request.osVersion
            it[lastSeenAt] = now
            it[registeredAt] = now
        }

        ActivateResponse(
            isValid = true,
            licenseKey = maskKey(request.licenseKey),
            edition = edition,
            maxDevices = maxDevices,
            activeDevices = otherDevices + 1,
            expiresAt = expiresAt?.toInstant()?.toEpochMilli(),
            message = "Device activated successfully"
        )
    }

    suspend fun heartbeat(request: HeartbeatRequest): HeartbeatResponse? = newSuspendedTransaction {
        logger.info("Heartbeat: key=****${request.licenseKey.takeLast(4)} device=${request.deviceId}")

        val license = Licenses.selectAll().where { Licenses.key eq request.licenseKey }.singleOrNull()
            ?: return@newSuspendedTransaction null

        // Verify device is registered
        DeviceRegistrations.selectAll().where {
            (DeviceRegistrations.licenseKey eq request.licenseKey) and
                (DeviceRegistrations.deviceId eq request.deviceId)
        }.singleOrNull() ?: return@newSuspendedTransaction null

        val now = OffsetDateTime.now()

        DeviceRegistrations.update({
            (DeviceRegistrations.licenseKey eq request.licenseKey) and
                (DeviceRegistrations.deviceId eq request.deviceId)
        }) {
            it[lastSeenAt] = now
            it[appVersion] = request.appVersion
            it[osVersion] = request.osVersion
        }

        Licenses.update({ Licenses.key eq request.licenseKey }) {
            it[lastHeartbeatAt] = now
            it[updatedAt] = now
        }

        val expiresAt = license[Licenses.expiresAt]
        val daysUntilExpiry = expiresAt?.let {
            java.time.Duration.between(now, it).toDays().toInt()
        }

        val heartbeatStatus = when {
            license[Licenses.status] != "ACTIVE" -> license[Licenses.status]
            expiresAt != null && expiresAt.isBefore(now) -> "EXPIRED"
            daysUntilExpiry != null && daysUntilExpiry <= 7 -> "EXPIRING_SOON"
            else -> "ACTIVE"
        }

        HeartbeatResponse(
            status = heartbeatStatus,
            licenseKey = maskKey(request.licenseKey),
            expiresAt = expiresAt?.toInstant()?.toEpochMilli(),
            daysUntilExpiry = daysUntilExpiry,
            serverTimestamp = System.currentTimeMillis()
        )
    }

    suspend fun getStatus(key: String): LicenseStatusResponse? = newSuspendedTransaction {
        logger.info("Status: key=****${key.takeLast(4)}")

        val license = Licenses.selectAll().where { Licenses.key eq key }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val activeDevices = DeviceRegistrations.selectAll()
            .where { DeviceRegistrations.licenseKey eq key }
            .count().toInt()

        LicenseStatusResponse(
            key = maskKey(key),
            edition = license[Licenses.edition],
            status = license[Licenses.status],
            maxDevices = license[Licenses.maxDevices],
            activeDevices = activeDevices,
            issuedAt = license[Licenses.issuedAt].toInstant().toEpochMilli(),
            expiresAt = license[Licenses.expiresAt]?.toInstant()?.toEpochMilli(),
            lastHeartbeatAt = license[Licenses.lastHeartbeatAt]?.toInstant()?.toEpochMilli()
        )
    }

    private fun maskKey(key: String): String =
        if (key.length > 4) "****${key.takeLast(4)}" else "****"
}
