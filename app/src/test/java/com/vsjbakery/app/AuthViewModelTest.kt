package com.vsjbakery.app

import com.vsjbakery.app.data.auth.AuthRepository
import com.vsjbakery.app.data.auth.AuthResult
import com.vsjbakery.app.data.auth.AuthState
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.UserProfile
import com.vsjbakery.app.ui.auth.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory stand-in for the server, used only by these tests. */
private class FakeAuthRepository(private val role: AppRole) : AuthRepository {
    override val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var signInCalls = 0
    var signOutCalls = 0
    var signUpResult: AuthResult = AuthResult.ConfirmationEmailSent("new@test.local")

    private fun profile(email: String) = UserProfile("id-1", email, "Test User", role)

    override suspend fun signIn(email: String, password: String): AuthResult {
        signInCalls++
        if (password != "correct-password") return AuthResult.Failure("Incorrect email or password.")
        return AuthResult.SignedIn(profile(email)).also { authState.value = AuthState.SignedIn(it.profile) }
    }

    override suspend fun signUp(fullName: String, email: String, password: String) = signUpResult

    override suspend fun signOut() {
        signOutCalls++
        authState.value = AuthState.SignedOut
    }

    override suspend fun refreshProfile() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun invalidEmailIsRejectedWithoutCallingServer() = runTest {
        val repo = FakeAuthRepository(AppRole.CUSTOMER)
        val vm = AuthViewModel(repo)
        vm.signIn("not-an-email", "correct-password", AppRole.CUSTOMER)
        assertEquals("Enter a valid email address", vm.form.value.errorMessage)
        assertEquals(0, repo.signInCalls)
    }

    @Test
    fun adminSigningInThroughAdminEntrySucceeds() = runTest {
        val vm = AuthViewModel(FakeAuthRepository(AppRole.ADMIN))
        vm.signIn("owner@test.local", "correct-password", AppRole.ADMIN)
        assertEquals(AppRole.ADMIN, vm.form.value.signedInAs)
        assertNull(vm.form.value.errorMessage)
    }

    @Test
    fun customerUsingAdminEntryIsSignedOutWithMessage() = runTest {
        val repo = FakeAuthRepository(AppRole.CUSTOMER)
        val vm = AuthViewModel(repo)
        vm.signIn("asha@test.local", "correct-password", AppRole.ADMIN)
        assertEquals("This account does not have administrator access.", vm.form.value.errorMessage)
        assertNull(vm.form.value.signedInAs)
        assertEquals(1, repo.signOutCalls)
        assertTrue(vm.authState.value is AuthState.SignedOut)
    }

    @Test
    fun reportsUserCannotEnterAsAdmin() = runTest {
        val repo = FakeAuthRepository(AppRole.REPORTS)
        val vm = AuthViewModel(repo)
        vm.signIn("accounts@test.local", "correct-password", AppRole.ADMIN)
        assertEquals(1, repo.signOutCalls)
        assertNull(vm.form.value.signedInAs)
    }

    @Test
    fun wrongPasswordShowsServerMessage() = runTest {
        val vm = AuthViewModel(FakeAuthRepository(AppRole.CUSTOMER))
        vm.signIn("asha@test.local", "wrong-password", AppRole.CUSTOMER)
        assertEquals("Incorrect email or password.", vm.form.value.errorMessage)
        assertFalse(vm.form.value.isSubmitting)
    }

    @Test
    fun signUpChecksPasswordsMatch() = runTest {
        val vm = AuthViewModel(FakeAuthRepository(AppRole.CUSTOMER))
        vm.signUp("Asha", "asha@test.local", "password-1", "password-2")
        assertEquals("Passwords do not match", vm.form.value.errorMessage)
    }

    @Test
    fun signUpReportsConfirmationEmail() = runTest {
        val vm = AuthViewModel(FakeAuthRepository(AppRole.CUSTOMER))
        vm.signUp("Asha", "new@test.local", "password-1", "password-1")
        assertEquals("new@test.local", vm.form.value.confirmationSentTo)
    }
}
