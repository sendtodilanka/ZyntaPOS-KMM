package com.zyntasolutions.zyntapos.domain.model

/**
 * Represents the sync state of a locally-created entity relative to the remote server.
 *
 * Entities transition: PENDING → SYNCING → SYNCED (happy path)
 *                                        → FAILED (after max retries)
 *
 * @property retryCount Number of failed sync attempts. Resets to 0 on SYNCED.
 * @property lastAttempt Epoch-millis of the most recent sync attempt. Null if never attempted.
 */
data class SyncStatus(
    val state: State,
    val retryCount: Int = 0,
    val lastAttempt: Long? = null,
) {
    /** The actual sync lifecycle state. */
    enum class State {
        /** Created locally; not yet submitted to the server. */
        PENDING,

        /** Currently being transmitted to the server. */
        SYNCING,

        /** Successfully acknowledged by the server. */
        SYNCED,

        /** All retry attempts exhausted. Requires manual intervention or re-trigger. */
        FAILED,
    }

    companion object {
        /** Convenience factory for a freshly-created local record. */
        fun pending(): SyncStatus = SyncStatus(state = State.PENDING)

        /** Convenience factory for a successfully synced record. */
        fun synced(): SyncStatus = SyncStatus(state = State.SYNCED)
    }
}
