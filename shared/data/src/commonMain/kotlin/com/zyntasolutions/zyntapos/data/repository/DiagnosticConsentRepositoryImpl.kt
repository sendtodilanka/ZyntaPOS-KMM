package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.domain.repository.DiagnosticConsentRepository

/**
 * Implementation of [DiagnosticConsentRepository] backed by [ApiService].
 */
class DiagnosticConsentRepositoryImpl(
    private val apiService: ApiService,
) : DiagnosticConsentRepository {

    override suspend fun grantConsent(sessionId: String, grantedAtMs: Long) {
        apiService.grantDiagnosticConsent(sessionId, grantedAtMs)
    }

    override suspend fun revokeConsent(sessionId: String) {
        apiService.revokeDiagnosticConsent(sessionId)
    }
}
