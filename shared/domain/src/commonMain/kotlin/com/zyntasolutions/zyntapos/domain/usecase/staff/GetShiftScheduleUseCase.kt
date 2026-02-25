package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of the weekly shift schedule for a store.
 *
 * @param storeId The store to query.
 * @param weekStart ISO date: YYYY-MM-DD (Monday of the week).
 * @param weekEnd ISO date: YYYY-MM-DD (Sunday of the week).
 */
class GetShiftScheduleUseCase(
    private val shiftRepository: ShiftRepository,
) {
    operator fun invoke(storeId: String, weekStart: String, weekEnd: String): Flow<List<ShiftSchedule>> =
        shiftRepository.getWeeklySchedule(storeId, weekStart, weekEnd)
}
