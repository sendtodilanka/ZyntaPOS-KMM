package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrintItem
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession
import com.zyntasolutions.zyntapos.domain.model.StocktakeStatus
import com.zyntasolutions.zyntapos.domain.port.EmailPort
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.printer.LabelPrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.LabelPrinterConfigRepository
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildLabelPrinterConfig(
    printerType: String = "ZPL_TCP",
    tcpHost: String = "192.168.1.201",
    tcpPort: Int = 9100,
    darknessLevel: Int = 8,
    speedLevel: Int = 4,
) = LabelPrinterConfig(
    printerType = printerType,
    tcpHost = tcpHost,
    tcpPort = tcpPort,
    darknessLevel = darknessLevel,
    speedLevel = speedLevel,
)

fun buildPrinterProfile(
    id: String = "profile-01",
    name: String = "Main Receipt Printer",
    jobType: PrinterJobType = PrinterJobType.RECEIPT,
    printerType: String = "TCP",
    tcpHost: String = "192.168.1.100",
    tcpPort: Int = 9100,
    paperWidthMm: Int = 80,
    isDefault: Boolean = true,
) = PrinterProfile(
    id = id,
    name = name,
    jobType = jobType,
    printerType = printerType,
    tcpHost = tcpHost,
    tcpPort = tcpPort,
    paperWidthMm = paperWidthMm,
    isDefault = isDefault,
    backupProfileId = null,
    createdAt = Clock.System.now().toEpochMilliseconds(),
    updatedAt = Clock.System.now().toEpochMilliseconds(),
)

fun buildStocktakeSession(
    id: String = "stocktake-01",
    startedBy: String = "user-01",
    status: StocktakeStatus = StocktakeStatus.IN_PROGRESS,
    counts: Map<String, Int> = emptyMap(),
) = StocktakeSession(
    id = id,
    startedBy = startedBy,
    startedAt = Clock.System.now().toEpochMilliseconds(),
    status = status,
    counts = counts,
    completedAt = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeLabelPrinterConfigRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeLabelPrinterConfigRepository : LabelPrinterConfigRepository {
    var config: LabelPrinterConfig? = null
    var shouldFailSave: Boolean = false

    override suspend fun get(): Result<LabelPrinterConfig?> = Result.Success(config)

    override suspend fun save(config: LabelPrinterConfig): Result<Unit> {
        if (shouldFailSave) return Result.Error(DatabaseException("Save failed"))
        this.config = config
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakePrinterProfileRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakePrinterProfileRepository : PrinterProfileRepository {
    val profiles = mutableListOf<PrinterProfile>()
    private val _flow = MutableStateFlow<List<PrinterProfile>>(emptyList())
    var shouldFailSave: Boolean = false

    override fun getAll(): Flow<List<PrinterProfile>> = _flow

    override suspend fun getById(id: String): Result<PrinterProfile> =
        profiles.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Profile '$id' not found"))

    override suspend fun getDefault(jobType: PrinterJobType): Result<PrinterProfile?> {
        val profile = profiles.firstOrNull { it.jobType == jobType && it.isDefault }
        return Result.Success(profile)
    }

    override suspend fun save(profile: PrinterProfile): Result<Unit> {
        if (shouldFailSave) return Result.Error(DatabaseException("Save failed"))
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) profiles[index] = profile else profiles.add(profile)
        _flow.value = profiles.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        profiles.removeAll { it.id == id }
        _flow.value = profiles.toList()
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeStocktakeRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeStocktakeRepository : StocktakeRepository {
    val sessions = mutableListOf<StocktakeSession>()
    val counts = mutableMapOf<String, MutableMap<String, Int>>() // sessionId → (barcode → qty)
    var shouldFailStart: Boolean = false
    var shouldFailComplete: Boolean = false
    private var sessionCounter = 1

    override suspend fun startSession(userId: String): Result<StocktakeSession> {
        if (shouldFailStart) return Result.Error(DatabaseException("Start failed"))
        val session = buildStocktakeSession(
            id = "stocktake-${sessionCounter++}",
            startedBy = userId,
        )
        sessions.add(session)
        counts[session.id] = mutableMapOf()
        return Result.Success(session)
    }

    override suspend fun updateCount(sessionId: String, barcode: String, qty: Int): Result<Unit> {
        val sessionCounts = counts[sessionId]
            ?: return Result.Error(DatabaseException("Session '$sessionId' not found"))
        sessionCounts[barcode] = qty
        return Result.Success(Unit)
    }

    override suspend fun getSession(id: String): Result<StocktakeSession> {
        val session = sessions.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Session '$id' not found"))
        return Result.Success(session)
    }

    override suspend fun getCountsForSession(sessionId: String): Result<List<StocktakeCount>> {
        val sessionCounts = counts[sessionId] ?: emptyMap()
        val countList = sessionCounts.map { (barcode, qty) ->
            StocktakeCount(
                productId = "prod-$barcode",
                barcode = barcode,
                productName = "Product $barcode",
                systemQty = 10,
                countedQty = qty,
                scannedAt = Clock.System.now().toEpochMilliseconds(),
            )
        }
        return Result.Success(countList)
    }

    override suspend fun complete(sessionId: String): Result<Map<String, Int>> {
        if (shouldFailComplete) return Result.Error(DatabaseException("Complete failed"))
        val session = sessions.firstOrNull { it.id == sessionId }
            ?: return Result.Error(DatabaseException("Session '$sessionId' not found"))
        val sessionCounts = counts[sessionId] ?: emptyMap()
        // Variance = counted - system (default system qty = 10 in fake)
        val variances = sessionCounts.mapKeys { "prod-${it.key}" }
            .mapValues { (_, qty) -> qty - 10 }
        val completedSession = session.copy(
            status = StocktakeStatus.COMPLETED,
            completedAt = Clock.System.now().toEpochMilliseconds(),
        )
        val index = sessions.indexOfFirst { it.id == sessionId }
        sessions[index] = completedSession
        return Result.Success(variances)
    }

    override suspend fun cancel(sessionId: String): Result<Unit> {
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return Result.Error(DatabaseException("Session '$sessionId' not found"))
        sessions[index] = sessions[index].copy(status = StocktakeStatus.CANCELLED)
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeLabelPrinterPort
// ─────────────────────────────────────────────────────────────────────────────

class FakeLabelPrinterPort : LabelPrinterPort {
    val zplJobs = mutableListOf<ByteArray>()
    val tsplJobs = mutableListOf<ByteArray>()
    data class LabelJob(val items: List<LabelPrintItem>, val template: LabelTemplate)
    val labelJobs = mutableListOf<LabelJob>()
    var shouldFail: Boolean = false

    override suspend fun printZpl(commands: ByteArray): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("ZPL print failed"))
        zplJobs.add(commands)
        return Result.Success(Unit)
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("TSPL print failed"))
        tsplJobs.add(commands)
        return Result.Success(Unit)
    }

    override suspend fun printLabels(items: List<LabelPrintItem>, template: LabelTemplate): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Label print failed"))
        labelJobs.add(LabelJob(items, template))
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeA4InvoicePrinterPort
// ─────────────────────────────────────────────────────────────────────────────

class FakeA4InvoicePrinterPort : A4InvoicePrinterPort {
    val invoiceJobs = mutableListOf<Order>()
    val zReportJobs = mutableListOf<RegisterSession>()
    val salesReportJobs = mutableListOf<GenerateSalesReportUseCase.SalesReport>()
    var shouldFail: Boolean = false

    override suspend fun printA4Invoice(order: Order): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("A4 invoice print failed"))
        invoiceJobs.add(order)
        return Result.Success(Unit)
    }

    override suspend fun printA4ZReport(session: RegisterSession): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("A4 Z-report print failed"))
        zReportJobs.add(session)
        return Result.Success(Unit)
    }

    override suspend fun printA4SalesReport(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("A4 sales report print failed"))
        salesReportJobs.add(report)
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeReceiptPrinterPort
// ─────────────────────────────────────────────────────────────────────────────

class FakeReceiptPrinterPort : ReceiptPrinterPort {
    val printedOrders = mutableListOf<Pair<Order, String>>() // (order, cashierId)
    var shouldFail: Boolean = false

    override suspend fun print(order: Order, cashierId: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Receipt print failed"))
        printedOrders.add(Pair(order, cashierId))
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeEmailPort
// ─────────────────────────────────────────────────────────────────────────────

class FakeEmailPort : EmailPort {
    data class EmailSent(val to: String, val subject: String, val pdfBytes: ByteArray)
    val emailsSent = mutableListOf<EmailSent>()
    var shouldFail: Boolean = false

    override suspend fun sendReceiptEmail(to: String, subject: String, pdfBytes: ByteArray): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Email send failed"))
        emailsSent.add(EmailSent(to, subject, pdfBytes))
        return Result.Success(Unit)
    }
}
