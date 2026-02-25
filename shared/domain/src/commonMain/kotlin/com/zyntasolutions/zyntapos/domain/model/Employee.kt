package com.zyntasolutions.zyntapos.domain.model

/**
 * An employee profile in the Staff/HR module.
 *
 * @property id Unique identifier (UUID v4).
 * @property userId Optional linked system user account.
 * @property storeId Store this employee belongs to.
 * @property firstName Employee's first name.
 * @property lastName Employee's last name.
 * @property email Email address.
 * @property phone Phone number.
 * @property address Physical address.
 * @property dateOfBirth ISO date string: YYYY-MM-DD. Nullable.
 * @property hireDate ISO date string: YYYY-MM-DD.
 * @property department Department name (e.g., "Sales", "Warehouse").
 * @property position Job position/title.
 * @property salary Base salary amount.
 * @property salaryType Pay frequency/type.
 * @property commissionRate Sales commission percentage (0.0–100.0).
 * @property emergencyContact Emergency contact details.
 * @property documents Attached documents (certificates, contracts).
 * @property isActive Whether the employee is currently active.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class Employee(
    val id: String,
    val userId: String? = null,
    val storeId: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val dateOfBirth: String? = null,
    val hireDate: String,
    val department: String? = null,
    val position: String,
    val salary: Double? = null,
    val salaryType: SalaryType = SalaryType.MONTHLY,
    val commissionRate: Double = 0.0,
    val emergencyContact: EmergencyContact? = null,
    val documents: List<EmployeeDocument> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Full display name. */
    val fullName: String get() = "$firstName $lastName"

    /** Hourly rate derived from [salary] and [salaryType]. Returns null if salary is null. */
    val hourlyRate: Double?
        get() = salary?.let { s ->
            when (salaryType) {
                SalaryType.HOURLY -> s
                SalaryType.DAILY -> s / 8.0
                SalaryType.WEEKLY -> s / 40.0
                SalaryType.MONTHLY -> s / 160.0
            }
        }
}

/**
 * Emergency contact information for an employee.
 */
data class EmergencyContact(
    val name: String,
    val phone: String,
    val relationship: String,
)

/**
 * A document attached to an employee profile (contract, certificate, ID).
 */
data class EmployeeDocument(
    val name: String,
    val url: String,
    val type: String,
)

/** Pay frequency type for salary calculations. */
enum class SalaryType {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
}
