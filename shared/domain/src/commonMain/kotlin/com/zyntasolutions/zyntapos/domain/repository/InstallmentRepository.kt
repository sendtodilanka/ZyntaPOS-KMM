package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.InstallmentPayment
import com.zyntasolutions.zyntapos.domain.model.InstallmentPlan
import kotlinx.coroutines.flow.Flow

/**
 * Contract for installment plan lifecycle management.
 */
interface InstallmentRepository {

    /** Emits all installment plans for a customer, most recent first. */
    fun getPlansByCustomer(customerId: String): Flow<List<InstallmentPlan>>

    /** Returns a single plan by [id]. */
    suspend fun getPlanById(id: String): Result<InstallmentPlan>

    /** Returns all currently active plans (status = ACTIVE). */
    suspend fun getActivePlans(): Result<List<InstallmentPlan>>

    /**
     * Returns installment payments that are due on or before [dueBefore] epoch millis
     * and have not been fully settled.
     */
    suspend fun getDuePayments(dueBefore: Long): Result<List<InstallmentPayment>>

    /** Returns all payments for a plan ordered by due date. */
    fun getPaymentsByPlan(planId: String): Flow<List<InstallmentPayment>>

    /** Creates a new installment plan and its scheduled payment entries atomically. */
    suspend fun createPlan(plan: InstallmentPlan, payments: List<InstallmentPayment>): Result<Unit>

    /** Records a payment against a plan. Updates the plan's [InstallmentPlan.paidAmount] atomically. */
    suspend fun recordPayment(planId: String, payment: InstallmentPayment): Result<Unit>

    /** Marks a plan as COMPLETED or DEFAULTED. */
    suspend fun updatePlanStatus(planId: String, status: InstallmentPlan.Status): Result<Unit>
}
