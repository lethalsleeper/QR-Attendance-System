package com.example.qrattendance.presentation

import app.cash.turbine.test
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.presentation.auth.LoginEvent
import com.example.qrattendance.presentation.auth.LoginViewModel
import com.example.qrattendance.testutil.FakeAuthRepository
import com.example.qrattendance.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val viewModel = LoginViewModel(authRepository)

    private val teacher = User("t1", "Dr. Rao", "rao@college.edu", UserRole.TEACHER, null, 0L)

    @Test
    fun `empty fields show validation errors and do not call the backend`() {
        viewModel.login()
        val state = viewModel.uiState.value
        assertEquals("Email is required", state.emailError)
        assertEquals("Password is required", state.passwordError)
        assertTrue(authRepository.loginCalls.isEmpty())
    }

    @Test
    fun `invalid email format is rejected`() {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("whatever1")
        viewModel.login()
        assertNotNull(viewModel.uiState.value.emailError)
        assertTrue(authRepository.loginCalls.isEmpty())
    }

    @Test
    fun `successful login emits the user's role for navigation`() = runTest {
        authRepository.loginResult = AppResult.Success(teacher)
        viewModel.onEmailChange(" rao@college.edu ")
        viewModel.onPasswordChange("secret123")

        viewModel.events.test {
            viewModel.login()
            assertEquals(LoginEvent.LoggedIn(UserRole.TEACHER), awaitItem())
        }
        assertEquals("rao@college.edu" to "secret123", authRepository.loginCalls.single())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed login shows the error message`() {
        authRepository.loginResult = AppResult.Failure(AppError.InvalidCredentials)
        viewModel.onEmailChange("rao@college.edu")
        viewModel.onPasswordChange("wrongpass1")
        viewModel.login()
        assertEquals(AppError.InvalidCredentials.message, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `editing a field clears its error`() {
        viewModel.login()
        viewModel.onEmailChange("a")
        assertEquals(null, viewModel.uiState.value.emailError)
    }
}
