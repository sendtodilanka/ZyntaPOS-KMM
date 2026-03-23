package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import kotlinx.coroutines.flow.Flow

/**
 * Contract for managing multi-store user access grants.
 *
 * This repository tracks which stores a user can access beyond their primary
 * [com.zyntasolutions.zyntapos.domain.model.User.storeId]. Each grant may
 * optionally carry a per-store role override.
 *
 * All mutating operations enqueue a sync event for cloud replication.
 */
interface UserStoreAccessRepository {

    /**
     * Returns all active store access grants for a given user.
     * Re-emits whenever grants are added, modified, or revoked.
     */
    fun getAccessibleStores(userId: String): Flow<List<UserStoreAccess>>

    /**
     * Returns all users who have been granted access to [storeId].
     * Includes both primary store users and those with explicit grants.
     */
    fun getUsersForStore(storeId: String): Flow<List<UserStoreAccess>>

    /**
     * Returns a specific access grant by ID.
     */
    suspend fun getById(id: String): Result<UserStoreAccess>

    /**
     * Returns the access grant for a specific user-store pair, if one exists.
     */
    suspend fun getByUserAndStore(userId: String, storeId: String): Result<UserStoreAccess?>

    /**
     * Grants a user access to a store. Upserts — if a grant already exists for
     * the same user-store pair, it updates the role and reactivates.
     *
     * @param access The access grant to create or update.
     */
    suspend fun grantAccess(access: UserStoreAccess): Result<Unit>

    /**
     * Revokes a user's access to a store by setting [UserStoreAccess.isActive] to false.
     *
     * @param userId  The user whose access is being revoked.
     * @param storeId The store to revoke access from.
     */
    suspend fun revokeAccess(userId: String, storeId: String): Result<Unit>

    /**
     * Checks whether [userId] has active access to [storeId], either via their
     * primary store assignment or via an explicit grant.
     */
    suspend fun hasAccess(userId: String, storeId: String): Boolean

    /**
     * Upserts a grant from sync (no sync enqueue — prevents feedback loops).
     */
    suspend fun upsertFromSync(access: UserStoreAccess): Result<Unit>
}
