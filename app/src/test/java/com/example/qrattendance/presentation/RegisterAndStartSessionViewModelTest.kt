package com.example.qrattendance.presentation

import app.cash.turbine.test
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.domain.usecase.StartSessionUseCase
import com.example.qrattendance.presentation.auth.RegisterEvent
import com.example.qrattendance.presentation.auth.RegisterViewModel
import com.example.qrattendance.presentation.teacher.StartSessionEvent
import com.example.qrattendance.presentation.teacher.StartSessionViewModel
import com.example.qrattendance.testutil.FakeAuthRepository
import com.example.qrattendance.testutil.FakeSessionRepository
import com.example.qrattendance.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RegisterAndStartSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ------------------------------------------------------------------ Register

    private fun filledRegisterViewModel(auth: FakeAuthRepository) = RegisterViewModel(auth).apply {
        onNameChange("Asha Verma")
        onEmailChange("asha@college.edu")
        onPasswordChange("secret123")
        onConfirmPasswordChange("secret123")
        onRollNumberChange("21CS045")
    }

    @Test
    fun `register validates matching passwords and roll number`() {
        val auth = FakeAuthRepository()
        val viewModel = filledRegisterViewModel(auth).apply {
            onConfirmPasswordChange("secret124")
            onRollNumberChange("")
        }
        viewModel.register()
        assertEquals("Passwords do not match", viewModel.uiState.value.confirmPasswordError)
        assertNotNull(viewModel.uiState.value.rollNumberError)
        assertTrue(auth.registerCalls.isEmpty())
    }

    @Test
    fun `teachers may register without a roll number`() = runTest {
        val auth = FakeAuthRepository()
        auth.registerResult = AppResult.Success(User("t1", "Dr. Rao", "rao@college.edu", UserRole.TEACHER, null, 0L))
        val viewModel = filledRegisterViewModel(auth).apply {
            onRoleChange(UserRole.TEACHER)
            onRollNumberChange("")
        }
        viewModel.events.test {
            viewModel.register()
            assertEquals(RegisterEvent.Registered(UserRole.TEACHER), awaitItem())
        }
        assertEquals(UserRole.TEACHER, auth.registerCalls.single().role)
        assertEquals(null, auth.registerCalls.single().rollNumber)
    }

    @Test
    fun `duplicate email and unapproved teacher errors are shown`() {
        val auth = FakeAuthRepository()
        auth.registerResult = AppResult.Failure(AppError.EmailAlreadyInUse)
        val viewModel = filledRegisterViewModel(auth)
        viewModel.register()
        assertEquals(AppError.EmailAlreadyInUse.message, viewModel.uiState.value.errorMessage)

        auth.registerResult = AppResult.Failure(AppError.TeacherNotApproved)
        viewModel.onRoleChange(UserRole.TEACHER)
        viewModel.register()
        assertEquals(AppError.TeacherNotApproved.message, viewModel.uiState.value.errorMessage)
    }

    // ------------------------------------------------------------------ Start session

    @Test
    fun `start session requires a subject`() {
        val sessions = FakeSessionRepository()
        val viewModel = StartSessionViewModel(StartSessionUseCase(sessions))
        viewModel.start()
        assertEquals("Subject name is required", viewModel.uiState.value.subjectError)
        assertTrue(sessions.createCalls.isEmpty())
    }

    @Test
    fun `start session sends the normalised subject and chosen duration`() = runTest {
        val sessions = FakeSessionRepository()
        val viewModel = StartSessionViewModel(StartSessionUseCase(sessions))
        viewModel.onSubjectChange("  Data   Structures ")
        viewModel.onDurationSelected(10)
        viewModel.events.test {
            viewModel.start()
            assertEquals(StartSessionEvent.SessionStarted("session-1"), awaitItem())
        }
        assertEquals("Data Structures" to 10, sessions.createCalls.single())
    }

    @Test
    fun `durations outside the allowed list are ignored`() {
        val viewModel = StartSessionViewModel(StartSessionUseCase(FakeSessionRepository()))
        viewModel.onDurationSelected(60)
        assertEquals(5, viewModel.uiState.value.durationMinutes)
    }

    @Test
    fun `backend errors are shown when a session cannot be created`() {
        val sessions = FakeSessionRepository().apply { createResult = AppResult.Failure(AppError.PermissionDenied) }
        val viewModel = StartSessionViewModel(StartSessionUseCase(sessions))
        viewModel.onSubjectChange("Maths")
        viewModel.start()
        assertEquals(AppError.PermissionDenied.message, viewModel.uiState.value.errorMessage)
    }
}
