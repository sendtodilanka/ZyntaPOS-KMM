package com.zyntasolutions.zyntapos.license.service

import com.zyntasolutions.zyntapos.license.db.AdminAuditLog
import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
import com.zyntasolutions.zyntapos.license.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.ceil

class AdminLicenseService {

    suspend fun listLicenses(
        page: Int,
        size: Int,
        status: String?,
        edition: String?,
        search: String?
    ): AdminPagedResponse<AdminLicense> = newSuspendedTransaction {
        var query = Licenses.selectAll()

        if (!status.isNullOrBlank()) {
            query = query.adjustWhere { Licenses.status eq status.uppercase() }
        }
        if (!edition.isNullOrBlank()) {
            query = query.adjustWhere { Licenses.edition eq edition.uppercase() }
        }
        if (!search.isNullOrBlank()) {
            val term = "%${search.lowercase()}%"
            query = query.adjustWhere {
                (Licenses.key.lowerCase() like term) or
                (Licenses.customerId.lowerCase() like term)
            }
        }

        val total = query.count()
        val items = query
            .orderBy(Licenses.createdAt, SortOrder.DESC)
            .limit(size).offset((page * size).toLong())
            .map { it.toAdminLicense() }

        AdminPagedResponse(
            data = items,
            page = page,
            size = size,
            total = total.toInt(),
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    suspend fun getLicense(key: String): AdminLicenseWithDevices? = newSuspendedTransaction {
        val license = Licenses.selectAll().where { Licenses.key eq key }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val devices = DeviceRegistrations.selectAll()
            .where { DeviceRegistrations.licenseKey eq key }
            .map { it.toLicenseDevice() }

        AdminLicenseWithDevices(license = license.toAdminLicense(), devices = devices)
    }

    suspend fun getStats(): AdminLicenseStats = newSuspendedTransaction {
        val all = Licenses.selectAll().toList()
        val now = OffsetDateTime.now()
        val in14Days = now.plusDays(14)

        val byEdition = mutableMapOf<String, Int>()
        var active = 0; var expired = 0; var revoked = 0
        var suspended = 0; var expiringSoon = 0

        for (row in all) {
            val status = row[Licenses.status]
            val expiresAt = row[Licenses.expiresAt]
            val edition = row[Licenses.edition]

            byEdition[edition] = (byEdition[edition] ?: 0) + 1

            when (status) {
                "ACTIVE" -> {
                    active++
                    if (expiresAt != null && expiresAt.isAfter(now) && expiresAt.isBefore(in14Days)) {
                        expiringSoon++
                    }
                }
                "EXPIRED" -> expired++
                "REVOKED" -> revoked++
                "SUSPENDED" -> suspended++
            }
        }

        AdminLicenseStats(
            total = all.size,
            active = active,
            expired = expired,
            revoked = revoked,
            suspended = suspended,
            expiringSoon = expiringSoon,
            byEdition = byEdition
        )
    }

    suspend fun getDevices(key: String): List<LicenseDevice>? = newSuspendedTransaction {
        if (Licenses.selectAll().where { Licenses.key eq key }.none()) return@newSuspendedTransaction null
        DeviceRegistrations.selectAll()
            .where { DeviceRegistrations.licenseKey eq key }
            .map { it.toLicenseDevice() }
    }

    suspend fun createLicense(req: AdminCreateLicenseRequest, adminId: String): AdminLicense = newSuspendedTransaction {
        val key = generateLicenseKey()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = req.expiresAt?.let { OffsetDateTime.parse(it) }

        Licenses.insert {
            it[Licenses.key] = key
            it[customerId] = req.customerId
            it[customerName] = req.customerName
            it[edition] = req.edition.uppercase()
            it[maxDevices] = req.maxDevices
            it[status] = "ACTIVE"
            it[issuedAt] = now
            it[Licenses.expiresAt] = expiresAt
            it[createdAt] = now
            it[updatedAt] = now
        }

        logAudit(adminId, "CREATE_LICENSE", key, "edition=${req.edition} maxDevices=${req.maxDevices}")

        Licenses.selectAll().where { Licenses.key eq key }.single().toAdminLicense()
    }

    suspend fun updateLicense(key: String, req: AdminUpdateLicenseRequest, adminId: String): AdminLicense? =
        newSuspendedTransaction {
            val existing = Licenses.selectAll().where { Licenses.key eq key }.singleOrNull()
                ?: return@newSuspendedTransaction null

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            Licenses.update({ Licenses.key eq key }) { stmt ->
                req.edition?.let { stmt[edition] = it.uppercase() }
                req.maxDevices?.let { stmt[maxDevices] = it }
                req.expiresAt?.let { stmt[expiresAt] = OffsetDateTime.parse(it) }
                    ?: run { if (req.clearExpiry == true) stmt[expiresAt] = null }
                req.status?.let { stmt[status] = it.uppercase() }
                if (req.forceSync == true) stmt[forceSyncRequested] = true
                stmt[updatedAt] = now
            }

            val changes = buildString {
                req.edition?.let { append("edition=$it ") }
                req.maxDevices?.let { append("maxDevices=$it ") }
                req.expiresAt?.let { append("expiresAt=$it ") }
                if (req.clearExpiry == true) append("clearExpiry=true ")
                req.status?.let { append("status=$it ") }
                if (req.forceSync == true) append("forceSync=true ")
            }.trim()
            logAudit(adminId, "UPDATE_LICENSE", key, changes.ifEmpty { "no changes" })

            Licenses.selectAll().where { Licenses.key eq key }.single().toAdminLicense()
        }

    suspend fun revokeLicense(key: String, adminId: String): Boolean = newSuspendedTransaction {
        val updated = Licenses.update({ Licenses.key eq key }) {
            it[status] = "REVOKED"
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        if (updated > 0) logAudit(adminId, "REVOKE_LICENSE", key, null)
        updated > 0
    }

    suspend fun deregisterDevice(licenseKey: String, deviceId: String, adminId: String): Boolean =
        newSuspendedTransaction {
            // deviceId here is the row UUID (id column), as sent by the admin panel DeviceList
            val deleted = DeviceRegistrations.deleteWhere {
                (DeviceRegistrations.licenseKey eq licenseKey) and
                (DeviceRegistrations.id eq deviceId)
            }
            if (deleted > 0) logAudit(adminId, "DEREGISTER_DEVICE", licenseKey, "deviceRowId=$deviceId")
            deleted > 0
        }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun generateLicenseKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun segment(n: Int) = (1..n).map { chars.random() }.joinToString("")
        return "${segment(4)}-${segment(4)}-${segment(4)}-${segment(4)}"
    }

    private fun logAudit(adminId: String, action: String, licenseKey: String?, details: String?) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        AdminAuditLog.insert {
            it[id] = UUID.randomUUID().toString()
            it[AdminAuditLog.adminId] = adminId
            it[AdminAuditLog.action] = action
            it[AdminAuditLog.licenseKey] = licenseKey
            it[AdminAuditLog.details] = details
            it[performedAt] = now
        }
    }

    private fun ResultRow.toAdminLicense(): AdminLicense {
        val expiresAt = this[Licenses.expiresAt]
        val now = OffsetDateTime.now()
        val computedStatus = when {
            this[Licenses.status] != "ACTIVE" -> this[Licenses.status]
            expiresAt != null && expiresAt.isBefore(now) -> "EXPIRED"
            expiresAt != null && expiresAt.isBefore(now.plusDays(14)) -> "EXPIRING_SOON"
            else -> this[Licenses.status]
        }

        val activeDevices = DeviceRegistrations.selectAll()
            .where { DeviceRegistrations.licenseKey eq this@toAdminLicense[Licenses.key] }
            .count().toInt()

        return AdminLicense(
            id = this[Licenses.key],
            key = this[Licenses.key],
            customerId = this[Licenses.customerId],
            customerName = this[Licenses.customerName] ?: this[Licenses.customerId],
            edition = this[Licenses.edition],
            status = computedStatus,
            maxDevices = this[Licenses.maxDevices],
            activeDevices = activeDevices,
            activatedAt = this[Licenses.issuedAt].toInstant().toEpochMilli().let {
                java.time.Instant.ofEpochMilli(it).toString()
            },
            expiresAt = expiresAt?.toInstant()?.toString(),
            lastHeartbeatAt = this[Licenses.lastHeartbeatAt]?.toInstant()?.toString(),
            createdAt = this[Licenses.createdAt].toInstant().toString(),
            updatedAt = this[Licenses.updatedAt].toInstant().toString()
        )
    }

    private fun ResultRow.toLicenseDevice() = LicenseDevice(
        id = this[DeviceRegistrations.id],
        licenseKey = this[DeviceRegistrations.licenseKey],
        deviceId = this[DeviceRegistrations.deviceId],
        deviceName = this[DeviceRegistrations.deviceName] ?: "Unknown Device",
        appVersion = this[DeviceRegistrations.appVersion] ?: "",
        os = "Android",
        osVersion = this[DeviceRegistrations.osVersion] ?: "",
        firstSeenAt = this[DeviceRegistrations.registeredAt].toInstant().toString(),
        lastSeenAt = this[DeviceRegistrations.lastSeenAt].toInstant().toString()
    )
}
