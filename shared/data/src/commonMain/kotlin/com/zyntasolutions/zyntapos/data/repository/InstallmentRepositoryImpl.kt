package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Installment_payments
import com.zyntasolutions.zyntapos.db.Installment_plans
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.InstallmentPayment
import com.zyntasolutions.zyntapos.domain.model.InstallmentPlan
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.InstallmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class InstallmentRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : InstallmentRepository {

    private val planQ get() = db.installmentPlansQueries
    private val payQ get() = db.installmentPaymentsQueries

    override fun getPlansByCustomer(customerId: String): Flow<List<InstallmentPlan>> =
        planQ.getInstallmentPlansByCustomer(customerId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toPlanDomain) }

    override suspend fun getPlanById(id: String): Result<InstallmentPlan> = withContext(Dispatchers.IO) {
        runCatching {
            planQ.getInstallmentPlanById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("InstallmentPlan not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toPlanDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getActivePlans(): Result<List<InstallmentPlan>> = withContext(Dispatchers.IO) {
        runCatching {
            planQ.getActiveInstallmentPlans().executeAsList().map(::toPlanDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getDuePayments(dueBefore: Long): Result<List<InstallmentPayment>> = withContext(Dispatchers.IO) {
        runCatching {
            payQ.getDueInstallmentPayments(dueBefore).executeAsList().map(::toPaymentDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getPaymentsByPlan(planId: String): Flow<List<InstallmentPayment>> =
        payQ.getPaymentsByPlan(planId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toPaymentDomain) }

    override suspend fun createPlan(
        plan: InstallmentPlan,
        payments: List<InstallmentPayment>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                planQ.insertInstallmentPlan(
                    id = plan.id, order_id = plan.orderId, customer_id = plan.customerId,
                    total_amount = plan.totalAmount, paid_amount = plan.paidAmount,
                    remaining_amount = plan.remainingAmount,
                    num_installments = plan.numInstallments.toLong(),
                    frequency = plan.frequency.name,
                    start_date = plan.startDate, end_date = plan.endDate,
                    status = plan.status.name, notes = plan.notes,
                    created_at = now, updated_at = now, sync_status = "PENDING",
                )
                payments.forEach { pmt ->
                    payQ.insertInstallmentPayment(
                        id = pmt.id, plan_id = pmt.planId, due_date = pmt.dueDate,
                        amount = pmt.amount, paid_amount = pmt.paidAmount,
                        paid_at = pmt.paidAt, status = pmt.status.name,
                        payment_id = pmt.paymentId,
                        created_at = now, updated_at = now, sync_status = "PENDING",
                    )
                }
                syncEnqueuer.enqueue(SyncOperation.EntityType.INSTALLMENT_PLAN, plan.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun recordPayment(planId: String, payment: InstallmentPayment): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = planQ.getInstallmentPlanById(planId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Plan not found: $planId"))
            val now = Clock.System.now().toEpochMilliseconds()
            val newPaid = plan.paid_amount + payment.paidAmount
            val newRemaining = (plan.remaining_amount - payment.paidAmount).coerceAtLeast(0.0)
            val newStatus = if (newRemaining == 0.0) InstallmentPlan.Status.COMPLETED.name else plan.status
            db.transaction {
                payQ.updateInstallmentPayment(
                    paid_amount = payment.paidAmount, paid_at = payment.paidAt,
                    status = payment.status.name, payment_id = payment.paymentId,
                    updated_at = now, id = payment.id,
                )
                planQ.updateInstallmentPlan(
                    paid_amount = newPaid, remaining_amount = newRemaining,
                    status = newStatus, updated_at = now, id = planId,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.INSTALLMENT_PAYMENT, payment.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Record payment failed", cause = t)) },
        )
    }

    override suspend fun updatePlanStatus(planId: String, status: InstallmentPlan.Status): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = planQ.getInstallmentPlanById(planId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Plan not found: $planId"))
            val now = Clock.System.now().toEpochMilliseconds()
            planQ.updateInstallmentPlan(
                paid_amount = plan.paid_amount,
                remaining_amount = plan.remaining_amount,
                status = status.name,
                updated_at = now, id = planId,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    private fun toPlanDomain(row: Installment_plans) = InstallmentPlan(
        id = row.id,
        orderId = row.order_id,
        customerId = row.customer_id,
        totalAmount = row.total_amount,
        paidAmount = row.paid_amount,
        remainingAmount = row.remaining_amount,
        numInstallments = row.num_installments.toInt(),
        frequency = runCatching { InstallmentPlan.Frequency.valueOf(row.frequency) }.getOrDefault(InstallmentPlan.Frequency.MONTHLY),
        startDate = row.start_date,
        endDate = row.end_date,
        status = runCatching { InstallmentPlan.Status.valueOf(row.status) }.getOrDefault(InstallmentPlan.Status.ACTIVE),
        notes = row.notes,
    )

    private fun toPaymentDomain(row: Installment_payments) = InstallmentPayment(
        id = row.id,
        planId = row.plan_id,
        dueDate = row.due_date,
        amount = row.amount,
        paidAmount = row.paid_amount,
        paidAt = row.paid_at,
        status = runCatching { InstallmentPayment.Status.valueOf(row.status) }.getOrDefault(InstallmentPayment.Status.PENDING),
        paymentId = row.payment_id,
    )
}
