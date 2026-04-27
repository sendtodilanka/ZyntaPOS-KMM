package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDataRetentionPolicyUseCaseTest {

    @Test
    fun `defaults when settings store is empty`() = runTest {
        val policy = GetDataRetentionPolicyUseCase(FakeSettingsRepository()).invoke()
        assertEquals(DataRetentionPolicy(), policy)
    }

    @Test
    fun `reads valid stored values for every field`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("data_retention.audit_log_days", "365")
            put("data_retention.sync_queue_days", "7")
            put("data_retention.report_months", "24")
        }
        val policy = GetDataRetentionPolicyUseCase(repo).invoke()
        assertEquals(365, policy.auditLogRetentionDays)
        assertEquals(7, policy.syncQueueRetentionDays)
        assertEquals(24, policy.reportRetentionMonths)
    }

    @Test
    fun `out-of-spec values fall back to defaults`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("data_retention.audit_log_days", "1000")
            put("data_retention.sync_queue_days", "60")
            put("data_retention.report_months", "48")
        }
        val policy = GetDataRetentionPolicyUseCase(repo).invoke()
        assertEquals(DataRetentionPolicy(), policy)
    }

    @Test
    fun `non-numeric values fall back to defaults`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("data_retention.audit_log_days", "ninety")
            put("data_retention.sync_queue_days", "fortnight")
            put("data_retention.report_months", "year")
        }
        val policy = GetDataRetentionPolicyUseCase(repo).invoke()
        assertEquals(DataRetentionPolicy(), policy)
    }
}
