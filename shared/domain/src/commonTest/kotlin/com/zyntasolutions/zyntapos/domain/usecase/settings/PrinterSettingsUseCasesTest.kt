package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLabelPrinterConfigRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakePrinterProfileRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPrinterProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for printer settings use cases:
 * [SaveLabelPrinterConfigUseCase], [GetLabelPrinterConfigUseCase],
 * [SavePrinterProfileUseCase], [GetPrinterProfilesUseCase], [DeletePrinterProfileUseCase].
 */
class PrinterSettingsUseCasesTest {

    // ─── SaveLabelPrinterConfigUseCase ─────────────────────────────────────────

    @Test
    fun `save label config - valid TCP config - persists successfully`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(printerType = "ZPL_TCP", tcpHost = "192.168.1.201", tcpPort = 9100)

        val result = useCase(config)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(config, repo.config)
    }

    @Test
    fun `save label config - TCP type with blank host - returns HOST_BLANK error`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(printerType = "ZPL_TCP", tcpHost = "")

        val result = useCase(config)

        assertIs<Result.Error>(result)
        assertNull(repo.config)
    }

    @Test
    fun `save label config - TCP type with port 0 - returns PORT_RANGE error`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(printerType = "TSPL_TCP", tcpHost = "192.168.1.1", tcpPort = 0)

        val result = useCase(config)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `save label config - darkness out of range - returns DARKNESS_RANGE error`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(darknessLevel = 16)

        val result = useCase(config)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `save label config - speed out of range - returns SPEED_RANGE error`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(speedLevel = 13)

        val result = useCase(config)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `save label config - NONE type - bypasses TCP validation`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = SaveLabelPrinterConfigUseCase(repo)
        val config = buildLabelPrinterConfig(printerType = "NONE")

        val result = useCase(config)

        assertIs<Result.Success<Unit>>(result)
    }

    // ─── GetLabelPrinterConfigUseCase ──────────────────────────────────────────

    @Test
    fun `get label config - no config saved - returns null`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val useCase = GetLabelPrinterConfigUseCase(repo)

        val result = useCase()

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `get label config - config saved - returns it`() = runTest {
        val repo = FakeLabelPrinterConfigRepository()
        val config = buildLabelPrinterConfig()
        repo.config = config
        val useCase = GetLabelPrinterConfigUseCase(repo)

        val result = useCase()

        assertIs<Result.Success<*>>(result)
        assertEquals(config, (result as Result.Success).data)
    }

    // ─── SavePrinterProfileUseCase ─────────────────────────────────────────────

    @Test
    fun `save printer profile - valid profile - persists successfully`() = runTest {
        val repo = FakePrinterProfileRepository()
        val useCase = SavePrinterProfileUseCase(repo)
        val profile = buildPrinterProfile()

        val result = useCase(profile)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.profiles.size)
    }

    @Test
    fun `save printer profile - blank name - returns NAME_BLANK error`() = runTest {
        val repo = FakePrinterProfileRepository()
        val useCase = SavePrinterProfileUseCase(repo)
        val profile = buildPrinterProfile(name = "")

        val result = useCase(profile)

        assertIs<Result.Error>(result)
        assertTrue(repo.profiles.isEmpty())
    }

    @Test
    fun `save printer profile - blank printer type - returns PRINTER_TYPE_BLANK error`() = runTest {
        val repo = FakePrinterProfileRepository()
        val useCase = SavePrinterProfileUseCase(repo)
        val profile = buildPrinterProfile(printerType = "")

        val result = useCase(profile)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `save printer profile - paper width zero - returns PAPER_WIDTH_INVALID error`() = runTest {
        val repo = FakePrinterProfileRepository()
        val useCase = SavePrinterProfileUseCase(repo)
        val profile = buildPrinterProfile(paperWidthMm = 0)

        val result = useCase(profile)

        assertIs<Result.Error>(result)
    }

    // ─── GetPrinterProfilesUseCase ─────────────────────────────────────────────

    @Test
    fun `get printer profiles - no filter - returns all profiles`() = runTest {
        val repo = FakePrinterProfileRepository()
        repo.save(buildPrinterProfile(id = "p1", jobType = PrinterJobType.RECEIPT))
        repo.save(buildPrinterProfile(id = "p2", jobType = PrinterJobType.KITCHEN))
        val useCase = GetPrinterProfilesUseCase(repo)

        val profiles = useCase().first()

        assertEquals(2, profiles.size)
    }

    @Test
    fun `get printer profiles - filtered by job type - returns matching profiles only`() = runTest {
        val repo = FakePrinterProfileRepository()
        repo.save(buildPrinterProfile(id = "p1", jobType = PrinterJobType.RECEIPT))
        repo.save(buildPrinterProfile(id = "p2", jobType = PrinterJobType.KITCHEN))
        repo.save(buildPrinterProfile(id = "p3", jobType = PrinterJobType.RECEIPT))
        val useCase = GetPrinterProfilesUseCase(repo)

        val profiles = useCase(PrinterJobType.RECEIPT).first()

        assertEquals(2, profiles.size)
        assertTrue(profiles.all { it.jobType == PrinterJobType.RECEIPT })
    }

    // ─── DeletePrinterProfileUseCase ──────────────────────────────────────────

    @Test
    fun `delete printer profile - existing id - removes profile`() = runTest {
        val repo = FakePrinterProfileRepository()
        repo.save(buildPrinterProfile(id = "p1"))
        val useCase = DeletePrinterProfileUseCase(repo)

        val result = useCase("p1")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.profiles.isEmpty())
    }

    @Test
    fun `delete printer profile - non-existent id - returns success (idempotent)`() = runTest {
        val repo = FakePrinterProfileRepository()
        val useCase = DeletePrinterProfileUseCase(repo)

        val result = useCase("non-existent")

        // Fake always returns Success on delete (idempotent), consistent with SQL DELETE
        assertIs<Result.Success<Unit>>(result)
    }
}
