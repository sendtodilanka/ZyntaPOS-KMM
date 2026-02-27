package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeUserRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveUserUseCase].
 *
 * Covers:
 * - Blank name → ValidationException (field=name, rule=REQUIRED) without DB write
 * - Invalid email (no @) → ValidationException (field=email, rule=EMAIL_FORMAT)
 * - New user with short password → ValidationException (field=password, rule=MIN_LENGTH)
 * - Valid new user → delegates to UserRepository.create with plainPassword
 * - Existing user update → delegates to UserRepository.update (not create)
 * - Update ignores password length validation (plainPassword is irrelevant on update)
 * - Repository failure → propagates Result.Error
 */
class SaveUserUseCaseTest {

    private fun makeUseCase(repo: FakeUserRepository = FakeUserRepository()) =
        SaveUserUseCase(repo) to repo

    // ─── Validation: name ────────────────────────────────────────────────────

    @Test
    fun `blank name returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "   ", email = "admin@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "password123")

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertFalse(repo.createCalled, "No DB write should occur for blank name")
    }

    @Test
    fun `empty name returns REQUIRED ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "", email = "admin@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "password123")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("REQUIRED", (result.exception as ValidationException).rule)
    }

    // ─── Validation: email ───────────────────────────────────────────────────

    @Test
    fun `email missing at-sign returns EMAIL_FORMAT ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "invalidemail.com")

        val result = useCase(user, isUpdate = false, plainPassword = "password123")

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("email", ex.field)
        assertEquals("EMAIL_FORMAT", ex.rule)
        assertFalse(repo.createCalled)
    }

    @Test
    fun `email missing domain segment returns EMAIL_FORMAT ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@")

        val result = useCase(user, isUpdate = false, plainPassword = "password123")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("EMAIL_FORMAT", (result.exception as ValidationException).rule)
    }

    @Test
    fun `email missing tld returns EMAIL_FORMAT ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@domain")

        val result = useCase(user, isUpdate = false, plainPassword = "password123")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("EMAIL_FORMAT", (result.exception as ValidationException).rule)
    }

    // ─── Validation: password ────────────────────────────────────────────────

    @Test
    fun `new user with password shorter than 8 chars returns MIN_LENGTH ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "short")

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("password", ex.field)
        assertEquals("MIN_LENGTH", ex.rule)
        assertFalse(repo.createCalled)
    }

    @Test
    fun `new user with exactly 7-char password returns MIN_LENGTH ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "1234567")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("MIN_LENGTH", (result.exception as ValidationException).rule)
    }

    @Test
    fun `new user with exactly 8-char password is accepted`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "12345678")

        assertIs<Result.Success<Unit>>(result)
    }

    // ─── Happy Paths ─────────────────────────────────────────────────────────

    @Test
    fun `valid new user delegates to repository create`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(id = "user-new", name = "Bob", email = "bob@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "securepassword")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.createCalled, "UserRepository.create must be called for a new user")
        assertFalse(repo.updateCalled, "UserRepository.update must NOT be called for a new user")
        assertEquals(1, repo.users.size)
    }

    @Test
    fun `valid new user passes plainPassword to repository create`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Bob", email = "bob@store.com")
        val password = "securepassword"

        useCase(user, isUpdate = false, plainPassword = password)

        assertEquals(password, repo.lastCreatedPassword)
    }

    @Test
    fun `update existing user delegates to repository update not create`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(id = "user-01", name = "Alice Updated", email = "alice@store.com")
        repo.users.add(user)

        val result = useCase(user, isUpdate = true)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.updateCalled, "UserRepository.update must be called for an update")
        assertFalse(repo.createCalled, "UserRepository.create must NOT be called for an update")
        assertNotNull(repo.lastUpdatedUser)
        assertEquals("Alice Updated", repo.lastUpdatedUser!!.name)
    }

    @Test
    fun `update ignores password length validation`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@store.com")

        // Short password must not block an update
        val result = useCase(user, isUpdate = true, plainPassword = "sh")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.updateCalled)
    }

    @Test
    fun `update with empty plainPassword defaults accepted`() = runTest {
        val (useCase, repo) = makeUseCase()
        val user = buildUser(name = "Alice", email = "alice@store.com")

        val result = useCase(user, isUpdate = true)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.updateCalled)
    }

    // ─── Repository failure ───────────────────────────────────────────────────

    @Test
    fun `repository failure on create propagates as Result Error`() = runTest {
        val repo = FakeUserRepository().also { it.shouldFail = true }
        val useCase = SaveUserUseCase(repo)
        val user = buildUser(name = "Bob", email = "bob@store.com")

        val result = useCase(user, isUpdate = false, plainPassword = "securepassword")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `repository failure on update propagates as Result Error`() = runTest {
        val repo = FakeUserRepository().also { it.shouldFail = true }
        val useCase = SaveUserUseCase(repo)
        val user = buildUser(name = "Alice", email = "alice@store.com")

        val result = useCase(user, isUpdate = true)

        assertIs<Result.Error>(result)
    }
}
