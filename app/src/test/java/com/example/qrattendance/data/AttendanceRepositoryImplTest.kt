package com.example.qrattendance.data

import com.example.qrattendance.data.remote.CloudFunctionNames
import com.example.qrattendance.data.remote.FunctionCallException
import com.example.qrattendance.data.repository.AttendanceRepositoryImpl
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.testutil.FakeCloudFunctionsDataSource
import com.example.qrattendance.testutil.FakeTimeProvider
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AttendanceRepositoryImplTest {

    private val functions = FakeCloudFunctionsDataSource()
    private val timeProvider = FakeTimeProvider(currentTime = 0L)
    private val repository = AttendanceRepositoryImpl(
        firestore = mockk<FirebaseFirestore>(relaxed = true),
        functions = functions,
        timeProvider = timeProvider,
    )
    private val payload = QrPayload("session123", "t".repeat(43))

    @Test
    fun `sends only session id and token to the markAttendance function`() = runTest {
        functions.handler = { _, _ ->
            mapOf("status" to "SUCCESS", "sessionId" to "session123", "subject" to "Maths", "markedAt" to 99L, "serverTime" to 5_000L)
        }

        val result = repository.markAttendance(payload)

        assertEquals(1, functions.calls.size)
        val (name, data) = functions.calls.single()
        assertEquals(CloudFunctionNames.MARK_ATTENDANCE, name)
        assertEquals(mapOf("sessionId" to "session123", "token" to "t".repeat(43)), data)
        assertEquals(MarkAttendanceResult.Success("session123", "Maths", null, 99L), result)
        assertEquals("device clock is synced with server time", 5_000L, timeProvider.now())
    }

    @Test
    fun `duplicate attendance is reported as already marked, not success`() = runTest {
        functions.handler = { _, _ ->
            throw FunctionCallException("ALREADY_EXISTS", "ALREADY_MARKED", "Attendance already marked", false)
        }
        assertEquals(
            MarkAttendanceResult.Failure(AttendanceFailureReason.ALREADY_MARKED),
            repository.markAttendance(payload),
        )
    }

    @Test
    fun `expired and invalid codes are mapped`() = runTest {
        functions.handler = { _, _ -> throw FunctionCallException("FAILED_PRECONDITION", "SESSION_EXPIRED", "expired", false) }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.SESSION_EXPIRED), repository.markAttendance(payload))

        functions.handler = { _, _ -> throw FunctionCallException("PERMISSION_DENIED", "INVALID_TOKEN", "bad", false) }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.INVALID_QR), repository.markAttendance(payload))

        functions.handler = { _, _ -> throw FunctionCallException("NOT_FOUND", "SESSION_NOT_FOUND", "missing", false) }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.SESSION_NOT_FOUND), repository.markAttendance(payload))
    }

    @Test
    fun `network failures during submission are reported as network errors`() = runTest {
        functions.handler = { _, _ -> throw FunctionCallException("INTERNAL", null, "io", isNetworkError = true) }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK), repository.markAttendance(payload))

        functions.handler = { _, _ -> throw IOException("socket closed") }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK), repository.markAttendance(payload))
    }

    @Test
    fun `unexpected exceptions never crash and are not success`() = runTest {
        functions.handler = { _, _ -> throw IllegalStateException("boom") }
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.UNKNOWN), repository.markAttendance(payload))
    }

    @Test
    fun `stats are loaded from the backend`() = runTest {
        functions.handler = { name, _ ->
            assertEquals(CloudFunctionNames.GET_STUDENT_STATS, name)
            mapOf("attended" to 2, "conducted" to 4, "courses" to emptyList<Any>())
        }
        val result = repository.getAttendanceStats()
        assertTrue(result is AppResult.Success)
        assertEquals(50.0, (result as AppResult.Success).data.percentage!!, 0.001)
    }

    @Test
    fun `stats failures are returned as errors`() = runTest {
        functions.handler = { _, _ -> throw FunctionCallException("UNAVAILABLE", null, "offline", isNetworkError = true) }
        assertTrue(repository.getAttendanceStats() is AppResult.Failure)
    }
}
