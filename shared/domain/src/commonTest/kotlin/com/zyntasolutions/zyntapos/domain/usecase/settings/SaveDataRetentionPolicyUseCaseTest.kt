package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SaveDataRetentionPolicyUseCaseTest {

    @Test
    fun `valid policy writes all three keys`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveDataRetentionPolicyUseCase(repo).invoke(
            DataRetentionPolicy(
                auditLogRetentionDays = 30,
                syncQueueRetentionDays = 30,
                reportRetentionMonths = 6,
            ),
        )
        assertIs<Result.Success<Unit>>(outcome)
        assertEquals("30", repo.get("data_retention.audit_log_days"))
        assertEquals("30", repo.get("data_retention.sync_queue_days"))
        assertEquals("6", repo.get("data_retention.report_months"))
    }

    @Test
    fun `out-of-spec auditLogRetentionDays is rejected with no writes`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveDataRetentionPolicyUseCase(repo).invoke(
            DataRetentionPolicy(auditLogRetentionDays = 60),
        )
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("auditLogRetentionDays", ex.field)
        assertEquals("ENUM", ex.rule)
        assertNull(repo.get("data_retention.audit_log_days"))
    }

    @Test
    fun `out-of-spec syncQueueRetentionDays is rejected`() = runTest {
        val outcome = SaveDataRetentionPolicyUseCase(FakeSettingsRepository()).invoke(
            DataRetentionPolicy(syncQueueRetentionDays = 100),
        )
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("syncQueueRetentionDays", ex.field)
    }

    @Test
    fun `out-of-spec reportRetentionMonths is rejected`() = runTest {
        val outcome = SaveDataRetentionPolicyUseCase(FakeSettingsRepository()).invoke(
            DataRetentionPolicy(reportRetentionMonths = 36),
        )
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("reportRetentionMonths", ex.field)
    }

    @Test
    fun `default policy is valid`() = runTest {
        val outcome = SaveDataRetentionPolicyUseCase(FakeSettingsRepository()).invoke(DataRetentionPolicy())
        assertIs<Result.Success<Unit>>(outcome)
    }
}
