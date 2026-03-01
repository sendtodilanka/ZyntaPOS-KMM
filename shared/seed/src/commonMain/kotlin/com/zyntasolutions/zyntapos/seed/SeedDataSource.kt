package com.zyntasolutions.zyntapos.seed

import kotlinx.serialization.Serializable

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Category].
 */
@Serializable
data class SeedCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val displayOrder: Int = 0,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Supplier].
 */
@Serializable
data class SeedSupplier(
    val id: String,
    val name: String,
    val contactPerson: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

/**
 * Seed data model for a [Product].
 */
@Serializable
data class SeedProduct(
    val id: String,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: String,
    val unitId: String = "unit-pcs",
    val price: Double,
    val costPrice: Double = 0.0,
    val taxGroupId: String? = null,
    val stockQty: Double = 0.0,
    val minStockQty: Double = 5.0,
    val description: String? = null,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Customer].
 *
 * [phone] is required because it is the unique lookup key in [Customer].
 */
@Serializable
data class SeedCustomer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val loyaltyPoints: Int = 0,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure].
 *
 * Products reference units via [SeedProduct.unitId]. These must be seeded
 * before products to satisfy the FK relationship.
 */
@Serializable
data class SeedUnitOfMeasure(
    val id: String,
    val name: String,
    val abbreviation: String,
    val isBaseUnit: Boolean = false,
    val conversionRate: Double = 1.0,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.TaxGroup].
 */
@Serializable
data class SeedTaxGroup(
    val id: String,
    val name: String,
    val rate: Double,
    val isInclusive: Boolean = false,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.User].
 *
 * [plainPassword] is passed to [UserRepository.create] for hashing — it is
 * never stored in plaintext.
 */
@Serializable
data class SeedUser(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val storeId: String,
    val plainPassword: String = "Test@1234",
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.CashRegister].
 */
@Serializable
data class SeedCashRegister(
    val id: String,
    val name: String,
    val storeId: String,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.ExpenseCategory].
 */
@Serializable
data class SeedExpenseCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val parentId: String? = null,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Employee].
 */
@Serializable
data class SeedEmployee(
    val id: String,
    val storeId: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val hireDate: String,
    val department: String? = null,
    val position: String,
    val salary: Double? = null,
    val salaryType: String = "MONTHLY",
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Expense].
 */
@Serializable
data class SeedExpense(
    val id: String,
    val storeId: String? = null,
    val categoryId: String? = null,
    val amount: Double,
    val description: String,
    val expenseDate: Long,
    val status: String = "APPROVED",
    val createdBy: String? = null,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Coupon].
 */
@Serializable
data class SeedCoupon(
    val id: String,
    val code: String,
    val name: String,
    val discountType: String,
    val discountValue: Double,
    val minimumPurchase: Double = 0.0,
    val maximumDiscount: Double? = null,
    val usageLimit: Int? = null,
    val validFrom: Long,
    val validTo: Long,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Warehouse].
 */
@Serializable
data class SeedWarehouse(
    val id: String,
    val storeId: String,
    val name: String,
    val managerId: String? = null,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val address: String? = null,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Account].
 *
 * [accountType] and [normalBalance] are stored as strings matching the
 * enum names ([AccountType], [NormalBalance]) and parsed in [SeedRunner].
 */
@Serializable
data class SeedAccount(
    val id: String,
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val subCategory: String,
    val description: String? = null,
    val normalBalance: String,
    val parentAccountId: String? = null,
    val isSystemAccount: Boolean = false,
    val isHeaderAccount: Boolean = false,
    val allowTransactions: Boolean = true,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.AccountingPeriod].
 *
 * All seeded periods start in [PeriodStatus.OPEN].
 */
@Serializable
data class SeedAccountingPeriod(
    val id: String,
    val periodName: String,
    val startDate: String,
    val endDate: String,
    val fiscalYearStart: String,
    val isAdjustment: Boolean = false,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.CustomerGroup].
 *
 * [discountType] matches [DiscountType] enum names; [priceType] matches
 * [CustomerGroup.PriceType] names.
 */
@Serializable
data class SeedCustomerGroup(
    val id: String,
    val name: String,
    val description: String? = null,
    val discountType: String? = null,
    val discountValue: Double = 0.0,
    val priceType: String = "RETAIL",
)

/**
 * Seed data model for a key-value setting pair.
 *
 * Settings are upserted via [SettingsRepository.set]. Existing values
 * are skipped to preserve any user-modified configuration.
 */
@Serializable
data class SeedSetting(
    val key: String,
    val value: String,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.CustomRole].
 *
 * [permissions] is a list of [Permission] enum names resolved in [SeedRunner].
 */
@Serializable
data class SeedCustomRole(
    val id: String,
    val name: String,
    val description: String = "",
    val permissions: List<String>,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.LabelTemplate].
 *
 * [paperType] matches [LabelTemplate.PaperType] enum names.
 */
@Serializable
data class SeedLabelTemplate(
    val id: String,
    val name: String,
    val paperType: String = "CONTINUOUS_ROLL",
    val paperWidthMm: Double = 58.0,
    val labelHeightMm: Double = 30.0,
    val columns: Int = 1,
    val rows: Int = 0,
    val gapHorizontalMm: Double = 0.0,
    val gapVerticalMm: Double = 3.0,
    val marginTopMm: Double = 2.0,
    val marginBottomMm: Double = 2.0,
    val marginLeftMm: Double = 2.0,
    val marginRightMm: Double = 2.0,
    val isDefault: Boolean = false,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.PrinterProfile].
 *
 * [jobType] matches [PrinterJobType] enum names; [printerType] is a raw
 * string ("TCP", "SERIAL", "BLUETOOTH", "USB").
 */
@Serializable
data class SeedPrinterProfile(
    val id: String,
    val name: String,
    val jobType: String,
    val printerType: String,
    val tcpHost: String = "",
    val tcpPort: Int = 9100,
    val paperWidthMm: Int = 80,
    val isDefault: Boolean = false,
)

/**
 * Complete seed dataset loaded from JSON files.
 *
 * Extended beyond Phase-1 (categories, suppliers, products, customers) to
 * include the entities needed for full E2E testing of all feature modules.
 */
@Serializable
data class SeedDataSet(
    val categories: List<SeedCategory> = emptyList(),
    val suppliers: List<SeedSupplier> = emptyList(),
    val products: List<SeedProduct> = emptyList(),
    val customers: List<SeedCustomer> = emptyList(),
    val units: List<SeedUnitOfMeasure> = emptyList(),
    val taxGroups: List<SeedTaxGroup> = emptyList(),
    val users: List<SeedUser> = emptyList(),
    val cashRegisters: List<SeedCashRegister> = emptyList(),
    val expenseCategories: List<SeedExpenseCategory> = emptyList(),
    val employees: List<SeedEmployee> = emptyList(),
    val expenses: List<SeedExpense> = emptyList(),
    val coupons: List<SeedCoupon> = emptyList(),
    val warehouses: List<SeedWarehouse> = emptyList(),
    val accounts: List<SeedAccount> = emptyList(),
    val accountingPeriods: List<SeedAccountingPeriod> = emptyList(),
    val customerGroups: List<SeedCustomerGroup> = emptyList(),
    val settings: List<SeedSetting> = emptyList(),
    val customRoles: List<SeedCustomRole> = emptyList(),
    val labelTemplates: List<SeedLabelTemplate> = emptyList(),
    val printerProfiles: List<SeedPrinterProfile> = emptyList(),
)
