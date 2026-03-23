package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns all stores a user has active access to (C3.2).
 *
 * This includes additional store grants beyond the user's primary
 * [com.zyntasolutions.zyntapos.domain.model.User.storeId].
 */
class GetUserAccessibleStoresUseCase(
    private val repository: UserStoreAccessRepository,
) {
    operator fun invoke(userId: String): Flow<List<UserStoreAccess>> =
        repository.getAccessibleStores(userId)
}
