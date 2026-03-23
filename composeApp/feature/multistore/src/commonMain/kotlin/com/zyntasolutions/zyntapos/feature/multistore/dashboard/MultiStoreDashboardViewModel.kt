package com.zyntasolutions.zyntapos.feature.multistore.dashboard

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetMultiStoreKPIsUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * MVI ViewModel for the multi-store global dashboard (C3.3).
 *
 * Displays aggregated KPIs across all stores the current user has access to,
 * per-store comparison data, and a store switcher for context switching.
 */
class MultiStoreDashboardViewModel(
    private val storeRepository: StoreRepository,
    private val authRepository: AuthRepository,
    private val getMultiStoreKPIs: GetMultiStoreKPIsUseCase,
) : BaseViewModel<MultiStoreDashboardState, MultiStoreDashboardIntent, MultiStoreDashboardEffect>(
    initialState = MultiStoreDashboardState(),
) {

    init {
        observeStores()
        dispatch(MultiStoreDashboardIntent.LoadDashboard)
    }

    override suspend fun handleIntent(intent: MultiStoreDashboardIntent) {
        when (intent) {
            is MultiStoreDashboardIntent.LoadDashboard -> loadKPIs()
            is MultiStoreDashboardIntent.SelectPeriod -> {
                updateState { copy(selectedPeriod = intent.period) }
                loadKPIs()
            }
            is MultiStoreDashboardIntent.SwitchStore -> switchStore(intent.store)
            is MultiStoreDashboardIntent.Refresh -> loadKPIs()
        }
    }

    private fun observeStores() {
        storeRepository.getAllStores()
            .onEach { stores ->
                val currentUser = authRepository.getSession().first()
                val activeStore = currentUser?.let { user ->
                    stores.find { store -> store.id == user.storeId }
                } ?: stores.firstOrNull()
                updateState { copy(stores = stores, activeStore = this.activeStore ?: activeStore) }
            }
            .catch { e ->
                sendEffect(MultiStoreDashboardEffect.ShowError(e.message ?: "Failed to load stores"))
            }
            .launchIn(viewModelScope)
    }

    private suspend fun loadKPIs() {
        updateState { copy(isLoading = true, error = null) }

        try {
            val period = state.value.selectedPeriod
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val today = now.toLocalDateTime(tz).date
            val from = today.minus(period.days - 1, DateTimeUnit.DAY).atStartOfDayIn(tz)
            val to = now

            val storeData = getMultiStoreKPIs(from, to).first()

            val totalRevenue = storeData.sumOf { sd -> sd.totalRevenue }
            val totalOrders = storeData.sumOf { sd -> sd.orderCount }
            val overallAOV = if (totalOrders > 0) totalRevenue / totalOrders else 0.0

            updateState {
                copy(
                    storeComparison = storeData,
                    totalRevenue = totalRevenue,
                    totalOrders = totalOrders,
                    overallAOV = overallAOV,
                    isLoading = false,
                    error = null,
                )
            }
        } catch (e: Exception) {
            updateState { copy(isLoading = false, error = e.message ?: "Failed to load KPIs") }
            sendEffect(MultiStoreDashboardEffect.ShowError(e.message ?: "Failed to load dashboard"))
        }
    }

    private suspend fun switchStore(store: Store) {
        updateState { copy(activeStore = store) }
        sendEffect(MultiStoreDashboardEffect.StoreSwitched(store.id, store.name))
    }
}
