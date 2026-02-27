package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.RegisterMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [RegisterRepository].
 *
 * Session lifecycle is strictly enforced: only one OPEN session per register is allowed.
 * The `expected_balance` is computed at close time as:
 * `openingBalance + ∑(CASH_IN) − ∑(CASH_OUT)`
 * (sales totals are not yet integrated here; full Z-report adds them in Sprint 20–21).
 */
class RegisterRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : RegisterRepository {

    private val sq get() = db.registersQueries
    private val mq get() = db.registersQueries

    override fun getRegisters(): Flow<List<CashRegister>> =
        sq.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    CashRegister(
                        id               = row.id,
                        name             = row.name,
                        storeId          = "", // store_id column not yet in schema — resolved in Phase 2
                        currentSessionId = row.current_session_id,
                        isActive         = row.is_active != 0L,
                    )
                }
            }

    override fun getActive(): Flow<RegisterSession?> =
        sq.getActiveSession()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.let { RegisterMapper.sessionToDomain(it) } }

    override suspend fun openSession(
        registerId: String,
        openingBalance: Double,
        userId: String,
    ): Result<RegisterSession> = withContext(Dispatchers.IO) {
        runCatching {
            // Guard: must not have an existing OPEN session
            val existing = sq.getActiveSessionByRegister(registerId).executeAsOneOrNull()
            if (existing != null) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Register already has an open session: ${existing.id}",
                        field   = "registerId",
                        rule    = "SESSION_ALREADY_OPEN",
                    )
                )
            }
            val now       = Clock.System.now().toEpochMilliseconds()
            val sessionId = IdGenerator.newId()
            db.transaction {
                sq.insertSession(
                    id               = sessionId,
                    register_id      = registerId,
                    opened_by        = userId,
                    closed_by        = null,
                    opening_balance  = openingBalance,
                    closing_balance  = null,
                    expected_balance = null,
                    actual_balance   = null,
                    total_sales      = null,
                    total_refunds    = null,
                    status           = "OPEN",
                    opened_at        = now,
                    closed_at        = null,
                    notes            = null,
                )
                db.registersQueries.updateRegisterSession(
                    current_session_id = sessionId,
                    id                 = registerId,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.REGISTER_SESSION, sessionId, SyncOperation.Operation.INSERT)
            }
            sq.getSessionById(sessionId).executeAsOne()
        }.fold(
            onSuccess = { row -> Result.Success(RegisterMapper.sessionToDomain(row)) },
            onFailure = { t ->
                if (t is ValidationException) Result.Error(t)
                else Result.Error(DatabaseException(t.message ?: "Open session failed", cause = t))
            },
        )
    }

    override suspend fun closeSession(
        sessionId: String,
        actualBalance: Double,
        userId: String,
    ): Result<RegisterSession> = withContext(Dispatchers.IO) {
        runCatching {
            val session = sq.getSessionById(sessionId).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Session not found: $sessionId", operation = "closeSession")
                )
            // Compute expected: opening + total cash_in − total cash_out
            val cashIn  = mq.getCashInTotal(sessionId).executeAsOne().toDouble()
            val cashOut = mq.getCashOutTotal(sessionId).executeAsOne().toDouble()
            val expected = session.opening_balance + cashIn - cashOut
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                sq.closeSession(
                    closed_by        = userId,
                    closing_balance  = actualBalance,
                    expected_balance = expected,
                    actual_balance   = actualBalance,
                    total_sales      = null,  // populated in Z-report feature (Sprint 20–21)
                    total_refunds    = null,
                    closed_at        = now,
                    notes            = null,
                    id               = sessionId,
                )
                db.registersQueries.updateRegisterSession(
                    current_session_id = null,
                    id                 = session.register_id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.REGISTER_SESSION, sessionId, SyncOperation.Operation.UPDATE)
            }
            sq.getSessionById(sessionId).executeAsOne()
        }.fold(
            onSuccess = { row -> Result.Success(RegisterMapper.sessionToDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Close session failed", cause = t)) },
        )
    }

    override suspend fun addCashMovement(movement: CashMovement): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                mq.insertMovement(
                    id          = movement.id,
                    session_id  = movement.sessionId,
                    type        = movement.type.name,
                    amount      = movement.amount,
                    reason      = movement.reason,
                    recorded_by = movement.recordedBy,
                    timestamp   = movement.timestamp.toEpochMilliseconds(),
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CASH_MOVEMENT, movement.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "addCashMovement failed", cause = t)) },
        )
    }

    override suspend fun getSession(sessionId: String): Result<RegisterSession> = withContext(Dispatchers.IO) {
        runCatching {
            sq.getSessionById(sessionId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Register session not found: $sessionId"))
        }.fold(
            onSuccess = { row -> Result.Success(RegisterMapper.sessionToDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load session", cause = t)) },
        )
    }

    override fun getMovements(sessionId: String): Flow<List<CashMovement>> =
        mq.getMovementsBySession(sessionId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(RegisterMapper::movementToDomain) }
}
