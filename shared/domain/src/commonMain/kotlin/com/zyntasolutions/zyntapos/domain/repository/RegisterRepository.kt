package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlinx.coroutines.flow.Flow

/**
 * Contract for cash-register session lifecycle and cash-movement recording.
 *
 * A register may only have one [RegisterSession] with status OPEN at a time.
 * All monetary amounts are expressed in the store's primary currency (no conversion).
 */
interface RegisterRepository {

    /**
     * Emits all active (non-decommissioned) [CashRegister] terminals, ordered by name.
     * Re-emits whenever register records change.
     *
     * Consumed by [OpenRegisterScreen] to populate the register selector dropdown.
     */
    fun getRegisters(): Flow<List<CashRegister>>

    /**
     * Emits the currently OPEN [RegisterSession], or `null` when no session is active.
     *
     * The [RegisterGuard] composable collects this flow to redirect cashiers to
     * [OpenRegisterScreen] before they can process sales.
     */
    fun getActive(): Flow<RegisterSession?>

    /**
     * Opens a new register session on the hardware terminal identified by [registerId].
     *
     * Pre-conditions enforced by the data layer:
     * - No other session for [registerId] may be in OPEN state.
     * - [openingBalance] must be ≥ 0.
     *
     * @param registerId     UUID of the [CashRegister] being opened.
     * @param openingBalance Physical cash counted in the drawer at open time.
     * @param userId         UUID of the [User] performing the open.
     * @return [Result.Success] with the created [RegisterSession], or
     *         [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.ValidationException]
     *         if a session is already open.
     */
    suspend fun openSession(
        registerId: String,
        openingBalance: Double,
        userId: String,
    ): Result<RegisterSession>

    /**
     * Closes the session identified by [sessionId] and records the counted [actualBalance].
     *
     * The data layer calculates and stores the expected balance:
     * `expectedBalance = openingBalance + ∑(cashIn) − ∑(cashOut) + cashSales`
     *
     * @param sessionId     UUID of the session to close.
     * @param actualBalance Physical cash counted in the drawer at close time.
     * @param userId        UUID of the [User] performing the close.
     * @return [Result.Success] with the updated (CLOSED) [RegisterSession], including
     *         the computed discrepancy, or [Result.Error] on validation failure.
     */
    suspend fun closeSession(
        sessionId: String,
        actualBalance: Double,
        userId: String,
    ): Result<RegisterSession>

    /**
     * Records a cash-in or cash-out [movement] against the current session.
     *
     * [CashMovement.amount] must be > 0; direction is encoded in [CashMovement.type].
     */
    suspend fun addCashMovement(movement: CashMovement): Result<Unit>

    /**
     * Emits all [CashMovement] entries recorded against [sessionId], ordered by timestamp.
     * Re-emits on new insertions.
     */
    fun getMovements(sessionId: String): Flow<List<CashMovement>>
}
