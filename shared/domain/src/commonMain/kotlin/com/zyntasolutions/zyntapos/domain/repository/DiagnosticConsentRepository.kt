package com.zyntasolutions.zyntapos.domain.repository

/**
 * Contract for diagnostic consent operations.
 *
 * Allows granting or revoking remote diagnostic access for a Zynta
 * technician session identified by [sessionId].
 */
interface DiagnosticConsentRepository {

    /**
     * Grants consent for the remote diagnostic session.
     *
     * @param sessionId  UUID of the diagnostic session.
     * @param grantedAtMs Epoch milliseconds when consent was granted.
     */
    suspend fun grantConsent(sessionId: String, grantedAtMs: Long)

    /**
     * Revokes consent for the remote diagnostic session.
     *
     * @param sessionId UUID of the diagnostic session.
     */
    suspend fun revokeConsent(sessionId: String)
}
