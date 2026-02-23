package com.zyntasolutions.zyntapos.data.local.db

import app.cash.sqldelight.db.SqlDriver
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.datetime.Clock

/**
 * ZyntaPOS — DatabaseFactory (commonMain)
 *
 * Singleton orchestrator that coordinates the full encrypted database lifecycle:
 *
 * ```
 * DatabaseKeyProvider.getOrCreateKey()
 *       │
 *       ▼
 * DatabaseDriverFactory.createEncryptedDriver(key)
 *   → WAL mode enabled
 *   → SQLCipher key applied
 *       │
 *       ▼
 * DatabaseMigrations.migrateIfNeeded(driver)
 *   → Schema created on first launch
 *   → Forward migrations applied on upgrades
 *       │
 *       ▼
 * ZyntaDatabase(driver) ← ready for use by Repository implementations
 * ```
 *
 * ## Thread Safety
 * [openDatabase] uses Kotlin's `@Volatile` + double-checked locking pattern to
 * guarantee that only ONE [ZyntaDatabase] instance is created per process lifetime,
 * even when called concurrently from multiple coroutines.
 *
 * [openDatabase] is a suspend function — callers MUST invoke it on a background
 * dispatcher (IO). Typically called once at app startup from the Koin `single { ... }`
 * initializer, which handles the dispatcher internally.
 *
 * ## Closing
 * [closeDatabase] is provided for graceful shutdown (process exit / data wipe).
 * After calling it, [openDatabase] will re-initialize from scratch on the next call.
 *
 * @param keyProvider Platform-specific key retrieval (Keystore / PKCS12)
 * @param driverFactory Platform-specific encrypted driver creation
 * @param migrations Schema migration manager
 */
class DatabaseFactory(
    private val keyProvider: DatabaseKeyProvider,
    private val driverFactory: DatabaseDriverFactory,
    private val migrations: DatabaseMigrations,
    private val passwordHasher: PasswordHashPort,
) {

    @Volatile
    private var cachedDriver: SqlDriver? = null

    @Volatile
    private var cachedDatabase: ZyntaDatabase? = null

    /**
     * Opens (or returns the cached) encrypted [ZyntaDatabase].
     *
     * On first call:
     * 1. Retrieves/generates the AES-256 key via [DatabaseKeyProvider]
     * 2. Creates the encrypted [SqlDriver] via [DatabaseDriverFactory]
     * 3. Applies schema migrations via [DatabaseMigrations]
     * 4. Constructs and caches the [ZyntaDatabase]
     *
     * Subsequent calls return the cached instance immediately.
     *
     * @throws com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException
     *   if key retrieval or driver creation fails
     */
    fun openDatabase(): ZyntaDatabase {
        // Fast path — return cached instance without lock acquisition
        cachedDatabase?.let { return it }

        // Slow path — synchronized initialization
        return synchronized(this) {
            cachedDatabase ?: run {
                ZyntaLogger.i(TAG, "Initializing ZyntaDatabase (first open).")

                val key: ByteArray = keyProvider.getOrCreateKey()

                val driver: SqlDriver = driverFactory.createEncryptedDriver(key)
                    .also { cachedDriver = it }

                migrations.migrateIfNeeded(driver)

                ZyntaDatabase(driver).also { db ->
                    cachedDatabase = db
                    seedDefaultAdminIfEmpty(db)
                    ZyntaLogger.i(TAG, "ZyntaDatabase ready — encrypted, WAL, migrations applied.")
                }
            }
        }
    }

    /**
     * Closes the active database connection and clears the cached instance.
     * Safe to call multiple times. After this call, [openDatabase] will
     * re-initialize the database on its next invocation.
     *
     * Typically called during:
     * - App process termination (coroutineScope.invokeOnCompletion)
     * - Data wipe / logout flows that require a fresh database
     */
    fun closeDatabase() {
        synchronized(this) {
            cachedDriver?.close()
            cachedDriver = null
            cachedDatabase = null
            ZyntaLogger.i(TAG, "ZyntaDatabase connection closed and cache cleared.")
        }
    }

    /** Returns `true` if the database has been opened and is currently cached. */
    val isOpen: Boolean get() = cachedDatabase != null

    /**
     * Seeds a default admin account on first launch so the offline-only MVP is usable
     * without a server. No-ops if any user already exists.
     *
     * Default credentials: admin@zentapos.com / admin123
     */
    private fun seedDefaultAdminIfEmpty(db: ZyntaDatabase) {
        try {
            val hasAdmin = db.usersQueries.getUserByEmail("admin@zentapos.com")
                .executeAsOneOrNull() != null
            if (hasAdmin) return

            val now = Clock.System.now().toEpochMilliseconds()
            val passwordHash = passwordHasher.hash("admin123")
            db.usersQueries.insertUser(
                id            = "00000000-0000-0000-0000-000000000001",
                name          = "Admin",
                email         = "admin@zentapos.com",
                password_hash = passwordHash,
                role          = "ADMIN",
                pin_hash      = null,
                store_id      = "default-store",
                is_active     = 1L,
                created_at    = now,
                updated_at    = now,
                sync_status   = "SYNCED",
            )
            ZyntaLogger.i(TAG, "Seeded default admin user (admin@zentapos.com).")
            seedTestData(db, now)
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Failed to seed default admin user: ${e.message}")
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Test Data Seeding — Sprint 24 QA (14.1.1 E2E, 14.1.4 search benchmark)
    // ═════════════════════════════════════════════════════════════════════════════

    @Suppress("TooGenericExceptionCaught")
    private fun seedTestData(db: ZyntaDatabase, now: Long) {
        try {
            seedUnits(db, now)
            seedTaxGroups(db, now)
            seedCategories(db, now)
            seedProducts(db, now)
            seedSuppliers(db, now)
            seedRegistersAndSessions(db, now)
            seedOrders(db, now)
            ZyntaLogger.i(TAG, "Test data seeded (25 products, 15 orders, 2 registers).")
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Test data seeding failed: ${e.message}")
        }
    }

    private fun seedUnits(db: ZyntaDatabase, now: Long) {
        db.units_of_measureQueries.insertUnit("unit-pcs", "Pieces", "pcs", 1L, 1.0, now, now, null, "SYNCED")
        db.units_of_measureQueries.insertUnit("unit-kg", "Kilograms", "kg", 0L, 1.0, now, now, null, "SYNCED")
        db.units_of_measureQueries.insertUnit("unit-l", "Liters", "L", 0L, 1.0, now, now, null, "SYNCED")
    }

    private fun seedTaxGroups(db: ZyntaDatabase, now: Long) {
        db.tax_groupsQueries.insertTaxGroup("tax-std", "Standard (10%)", 10.0, 0L, 1L, now, now, "SYNCED")
        db.tax_groupsQueries.insertTaxGroup("tax-zero", "Zero Rate", 0.0, 0L, 1L, now, now, "SYNCED")
    }

    private fun seedCategories(db: ZyntaDatabase, now: Long) {
        val cats = listOf("cat-drinks" to "Drinks", "cat-food" to "Food", "cat-retail" to "Retail", "cat-services" to "Services")
        for ((i, c) in cats.withIndex()) {
            db.categoriesQueries.insertCategory(c.first, c.second, null, null, (i + 1).toLong(), 1L, now, now, "SYNCED")
        }
    }

    @Suppress("LongMethod")
    private fun seedProducts(db: ZyntaDatabase, now: Long) {
        data class P(val id: String, val n: String, val bc: String, val cat: String, val pr: Double, val cp: Double, val sq: Double, val ms: Double)
        val ps = listOf(
            P("p-001","Latte","4901234567890","cat-drinks",4.50,1.80,150.0,20.0),
            P("p-002","Cappuccino","4901234567891","cat-drinks",4.50,1.80,120.0,20.0),
            P("p-003","Espresso","4901234567892","cat-drinks",3.50,1.20,200.0,30.0),
            P("p-004","Green Tea","4901234567893","cat-drinks",3.00,0.90,80.0,15.0),
            P("p-005","Orange Juice","4901234567894","cat-drinks",5.50,2.50,45.0,10.0),
            P("p-006","Iced Americano","4901234567895","cat-drinks",4.00,1.50,90.0,15.0),
            P("p-007","Hot Chocolate","4901234567896","cat-drinks",4.50,1.80,60.0,10.0),
            P("p-008","Sparkling Water","4901234567897","cat-drinks",2.50,0.80,200.0,30.0),
            P("p-009","Croissant","4901234567900","cat-food",3.00,1.20,40.0,10.0),
            P("p-010","Avocado Toast","4901234567901","cat-food",12.50,5.00,25.0,5.0),
            P("p-011","Club Sandwich","4901234567902","cat-food",10.00,4.00,30.0,5.0),
            P("p-012","Caesar Salad","4901234567903","cat-food",9.50,3.50,20.0,5.0),
            P("p-013","Blueberry Muffin","4901234567904","cat-food",3.50,1.20,50.0,10.0),
            P("p-014","Cheese Danish","4901234567905","cat-food",3.50,1.30,35.0,8.0),
            P("p-015","Grilled Panini","4901234567906","cat-food",8.50,3.20,18.0,5.0),
            P("p-016","Chocolate Cake","4901234567907","cat-food",6.00,2.50,15.0,5.0),
            P("p-017","Coffee Beans 250g","4901234567910","cat-retail",14.99,8.00,50.0,10.0),
            P("p-018","Travel Mug","4901234567911","cat-retail",19.99,9.00,30.0,5.0),
            P("p-019","Gift Card $25","4901234567912","cat-retail",25.00,25.00,100.0,10.0),
            P("p-020","Straw Set","4901234567913","cat-retail",7.99,3.00,40.0,5.0),
            P("p-021","Catering /head","4901234567920","cat-services",35.00,15.00,999.0,0.0),
            P("p-022","Delivery Fee","4901234567921","cat-services",5.00,2.00,999.0,0.0),
            P("p-023","Matcha Latte","4901234567930","cat-drinks",5.50,2.50,3.0,10.0),
            P("p-024","Tuna Wrap","4901234567931","cat-food",8.00,3.50,2.0,5.0),
            P("p-025","Ceramic Mug","4901234567932","cat-retail",12.99,5.00,1.0,5.0),
        )
        for (p in ps) {
            db.productsQueries.insertProduct(p.id, p.n, p.bc, "SKU-${p.id.takeLast(3)}", p.cat, "unit-pcs", p.pr, p.cp, "tax-std", p.sq, p.ms, null, null, 1L, now, now, "SYNCED")
        }
    }

    private fun seedSuppliers(db: ZyntaDatabase, now: Long) {
        db.suppliersQueries.insertSupplier("sup-01","Bean Origin Co.","James Miller","+1-555-0101","james@beanorigin.com",null,null,1L,now,now,"SYNCED")
        db.suppliersQueries.insertSupplier("sup-02","Fresh Bakery Supply","Sarah Chen","+1-555-0102","sarah@freshbakery.com",null,null,1L,now,now,"SYNCED")
        db.suppliersQueries.insertSupplier("sup-03","Retail Goods Wholesale","Mike Johnson","+1-555-0103","mike@retailgoods.com",null,null,1L,now,now,"SYNCED")
    }

    private fun seedRegistersAndSessions(db: ZyntaDatabase, now: Long) {
        val admin = "00000000-0000-0000-0000-000000000001"
        db.registersQueries.insertRegister("reg-01", "Register 1", null, 1L, now, now)
        db.registersQueries.insertRegister("reg-02", "Register 2", null, 1L, now, now)
        db.registersQueries.insertSession("ses-01","reg-01",admin,null,200.0,null,null,null,null,null,"OPEN",now,null,null)
        db.registersQueries.insertSession("ses-02","reg-02",admin,null,150.0,null,null,null,null,null,"OPEN",now,null,null)
        db.registersQueries.updateRegisterSession("ses-01", "reg-01")
        db.registersQueries.updateRegisterSession("ses-02", "reg-02")
    }

    @Suppress("LongMethod")
    private fun seedOrders(db: ZyntaDatabase, now: Long) {
        val admin = "00000000-0000-0000-0000-000000000001"
        val day = 86_400_000L
        data class OI(val name: String, val pid: String, val qty: Double, val up: Double)
        data class O(val id: String, val num: String, val sub: Double, val tax: Double, val disc: Double, val total: Double, val method: String, val tend: Double, val chg: Double, val ago: Int, val items: List<OI>)

        val orders = listOf(
            O("o-01","ORD-1001",42.50,4.25,1.25,45.50,"CASH",50.0,4.50,0, listOf(OI("Latte","p-001",2.0,4.50),OI("Croissant","p-009",1.0,3.00),OI("Avocado Toast","p-010",1.0,12.50),OI("Club Sandwich","p-011",1.0,10.00),OI("Espresso","p-003",2.0,3.50))),
            O("o-02","ORD-1002",25.50,2.55,0.05,28.00,"CARD",28.0,0.0,0, listOf(OI("Cappuccino","p-002",2.0,4.50),OI("Blueberry Muffin","p-013",3.0,3.50),OI("Green Tea","p-004",2.0,3.00))),
            O("o-03","ORD-1003",18.00,1.80,0.0,19.80,"CASH",20.0,0.20,0, listOf(OI("Latte","p-001",4.0,4.50))),
            O("o-04","ORD-1004",56.68,5.67,0.0,62.35,"CARD",62.35,0.0,1, listOf(OI("Avocado Toast","p-010",2.0,12.50),OI("Caesar Salad","p-012",2.0,9.50),OI("Orange Juice","p-005",2.0,5.50))),
            O("o-05","ORD-1005",30.00,3.00,0.0,33.00,"CASH",35.0,2.0,1, listOf(OI("Club Sandwich","p-011",3.0,10.00))),
            O("o-06","ORD-1006",14.00,1.40,0.0,15.40,"MOBILE",15.40,0.0,2, listOf(OI("Coffee Beans 250g","p-017",1.0,14.99))),
            O("o-07","ORD-1007",48.00,4.80,0.0,52.80,"CARD",52.80,0.0,2, listOf(OI("Grilled Panini","p-015",2.0,8.50),OI("Latte","p-001",2.0,4.50),OI("Chocolate Cake","p-016",2.0,6.00),OI("Espresso","p-003",2.0,3.50))),
            O("o-08","ORD-1008",79.50,7.95,0.0,87.45,"SPLIT",87.45,0.0,3, listOf(OI("Catering /head","p-021",2.0,35.00),OI("Delivery Fee","p-022",1.0,5.00))),
            O("o-09","ORD-1009",20.00,2.00,0.0,22.00,"CASH",25.0,3.0,3, listOf(OI("Travel Mug","p-018",1.0,19.99))),
            O("o-10","ORD-1010",35.00,3.50,0.0,38.50,"CARD",38.50,0.0,4, listOf(OI("Avocado Toast","p-010",1.0,12.50),OI("Cappuccino","p-002",2.0,4.50),OI("Croissant","p-009",2.0,3.00),OI("Espresso","p-003",1.0,3.50))),
            O("o-11","ORD-1011",65.00,6.50,0.0,71.50,"CASH",75.0,3.50,5, listOf(OI("Catering /head","p-021",2.0,35.00))),
            O("o-12","ORD-1012",15.00,1.50,0.0,16.50,"MOBILE",16.50,0.0,5, listOf(OI("Latte","p-001",2.0,4.50),OI("Cheese Danish","p-014",1.0,3.50),OI("Sparkling Water","p-008",1.0,2.50))),
            O("o-13","ORD-1013",41.00,4.10,0.0,45.10,"CARD",45.10,0.0,6, listOf(OI("Gift Card \$25","p-019",1.0,25.00),OI("Straw Set","p-020",2.0,7.99))),
            O("o-14","ORD-1014",27.00,2.70,0.0,29.70,"CASH",30.0,0.30,6, listOf(OI("Latte","p-001",6.0,4.50))),
            O("o-15","ORD-1015",50.00,5.00,0.0,55.00,"CARD",55.0,0.0,0, listOf(OI("Avocado Toast","p-010",4.0,12.50))),
        )
        for (o in orders) {
            val ts = now - (o.ago * day)
            db.ordersQueries.insertOrder(o.id,o.num,"SALE","COMPLETED",null,admin,"default-store","ses-01",o.sub,o.tax,o.disc,o.total,o.method,null,o.tend,o.chg,null,null,ts,ts,"SYNCED")
            for ((i, it) in o.items.withIndex()) {
                val lt = it.qty * it.up
                db.ordersQueries.insertOrderItem("${o.id}-i${i+1}",o.id,it.pid,it.name,it.up,it.qty,0.0,"NONE",10.0,lt*0.1,lt)
            }
        }
    }

    private companion object {
        const val TAG = "DatabaseFactory"
    }
}
