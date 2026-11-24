package com.example.qrattendance.domain

import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.usecase.MarkAttendanceUseCase
import com.example.qrattendance.testutil.FakeAttendanceRepository
import com.example.qrattendance.testutil.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkAttendanceUseCaseTest {

    private val repository = FakeAttendanceRepository()
    private val network = FakeNetworkMonitor(online = true)
    private val useCase = MarkAttendanceUseCase(repository, network)
    private val validQr = """{"sessionId":"abc123","token":"${"k".repeat(43)}"}"""

    @Test
    fun `invalid QR codes never reach the backend`() = runTest {
        val result = useCase("https://not-an-attendance-code.example")
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.INVALID_QR), result)
        assertTrue(repository.markCalls.isEmpty())
    }

    @Test
    fun `offline scans fail fast with a network error`() = runTest {
        network.online = false
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK), useCase(validQr))
        assertTrue(repository.markCalls.isEmpty())
    }

    @Test
    fun `valid scans are sent to the backend`() = runTest {
        val result = useCase(validQr)
        assertTrue(result is MarkAttendanceResult.Success)
        assertEquals("abc123", repository.markCalls.single().sessionId)
    }
}
