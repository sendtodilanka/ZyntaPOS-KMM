package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// Fake UserRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [UserRepository].
 */
class FakeUserRepository : UserRepository {
    val users = mutableListOf<User>()
    var shouldFail = false
    var createCalled = false
    var updateCalled = false
    var lastCreatedUser: User? = null
    var lastCreatedPassword: String? = null
    var lastUpdatedUser: User? = null

    private val _usersFlow = MutableStateFlow<List<User>>(emptyList())

    override fun getAll(storeId: String?): Flow<List<User>> =
        if (storeId == null) _usersFlow
        else _usersFlow.map { list -> list.filter { it.storeId == storeId } }

    override suspend fun getById(id: String): Result<User> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return users.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("User not found: $id"))
    }

    override suspend fun create(user: User, plainPassword: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        createCalled = true
        lastCreatedUser = user
        lastCreatedPassword = plainPassword
        users.add(user)
        _usersFlow.value = users.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(user: User): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        updateCalled = true
        lastUpdatedUser = user
        val idx = users.indexOfFirst { it.id == user.id }
        if (idx < 0) {
            users.add(user)
        } else {
            users[idx] = user
        }
        _usersFlow.value = users.toList()
        return Result.Success(Unit)
    }

    override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = users.indexOfFirst { it.id == userId }
        if (idx < 0) return Result.Error(DatabaseException("User not found: $userId"))
        return Result.Success(Unit)
    }

    override suspend fun deactivate(userId: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = users.indexOfFirst { it.id == userId }
        if (idx < 0) return Result.Error(DatabaseException("User not found: $userId"))
        users[idx] = users[idx].copy(isActive = false)
        _usersFlow.value = users.toList()
        return Result.Success(Unit)
    }
}
