package com.example.qrattendance.presentation

import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.usecase.MarkAttendanceUseCase
import com.example.qrattendance.presentation.scanner.ScannerUiState
import com.example.qrattendance.presentation.scanner.ScannerViewModel
import com.example.qrattendance.testutil.FakeAttendanceRepository
import com.example.qrattendance.testutil.FakeNetworkMonitor
import com.example.qrattendance.testutil.FakeTimeProvider
import com.example.qrattendance.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAttendanceRepository()
    private val network = FakeNetworkMonitor()
    private val time = FakeTimeProvider(currentTime = 1_000_000L)
    private val viewModel = ScannerViewModel(MarkAttendanceUseCase(repository, network), time)

    private val validQr = """{"sessionId":"session1","token":"${"q".repeat(43)}"}"""

    @Test
    fun `successful scan shows the success state`() = runTest {
        repository.nextResult = MarkAttendanceResult.Success("session1", "Maths", "Dr. Rao", 42L)
        viewModel.onQrCodeDetected(validQr)
        assertEquals(ScannerUiState.Success("Maths", "Dr. Rao", 42L), viewModel.uiState.value)
    }

    @Test
    fun `QR is processed only once while validation is in progress`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        viewModel.onQrCodeDetected(validQr)
        assertEquals(ScannerUiState.Validating, viewModel.uiState.value)

        // The analyzer keeps reporting the same (and other) codes while the request is in flight.
        repeat(10) { viewModel.onQrCodeDetected(validQr) }
        viewModel.onQrCodeDetected("""{"sessionId":"other","token":"${"z".repeat(43)}"}""")

        gate.complete(Unit)
        assertEquals(1, repository.markCalls.size)
        assertTrue(viewModel.uiState.value is ScannerUiState.Success)
    }

    @Test
    fun `detections after success are ignored`() = runTest {
        viewModel.onQrCodeDetected(validQr)
        time.currentTime += 60_000
        viewModel.onQrCodeDetected(validQr)
        assertEquals(1, repository.markCalls.size)
    }

    @Test
    fun `already marked is surfaced as a failure without retry`() = runTest {
        repository.nextResult = MarkAttendanceResult.Failure(AttendanceFailureReason.ALREADY_MARKED)
        viewModel.onQrCodeDetected(validQr)
        val state = viewModel.uiState.value as ScannerUiState.Failure
        assertEquals(AttendanceFailureReason.ALREADY_MARKED, state.reason)
        assertEquals(false, state.reason.canRetry)
    }

    @Test
    fun `invalid QR fails locally without calling the backend`() = runTest {
        viewModel.onQrCodeDetected("hello world")
        assertEquals(ScannerUiState.Failure(AttendanceFailureReason.INVALID_QR), viewModel.uiState.value)
        assertTrue(repository.markCalls.isEmpty())
    }

    @Test
    fun `scan again ignores the same code for a short cooldown, then accepts it`() = runTest {
        repository.nextResult = MarkAttendanceResult.Failure(AttendanceFailureReason.SESSION_EXPIRED)
        viewModel.onQrCodeDetected(validQr)
        viewModel.scanAgain()
        assertEquals(ScannerUiState.Scanning, viewModel.uiState.value)

        // Same code still in front of the camera → ignored.
        viewModel.onQrCodeDetected(validQr)
        assertEquals(ScannerUiState.Scanning, viewModel.uiState.value)
        assertEquals(1, repository.markCalls.size)

        // After the cooldown the student can deliberately retry it.
        time.currentTime += ScannerViewModel.SAME_CODE_COOLDOWN_MS
        viewModel.onQrCodeDetected(validQr)
        assertEquals(2, repository.markCalls.size)
    }

    @Test
    fun `scan again accepts a different code immediately`() = runTest {
        repository.nextResult = MarkAttendanceResult.Failure(AttendanceFailureReason.SESSION_EXPIRED)
        viewModel.onQrCodeDetected(validQr)
        viewModel.scanAgain()
        repository.nextResult = MarkAttendanceResult.Success("session2", "Physics", null, 7L)
        viewModel.onQrCodeDetected("""{"sessionId":"session2","token":"${"w".repeat(43)}"}""")
        assertEquals(ScannerUiState.Success("Physics", null, 7L), viewModel.uiState.value)
    }

    @Test
    fun `network failure is shown and nothing is reported as success`() = runTest {
        network.online = false
        viewModel.onQrCodeDetected(validQr)
        assertEquals(ScannerUiState.Failure(AttendanceFailureReason.NETWORK), viewModel.uiState.value)
    }
}
