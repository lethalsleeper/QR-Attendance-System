package com.example.qrattendance.data

import com.example.qrattendance.data.mapper.FirebaseErrorMapper
import com.example.qrattendance.data.mapper.FunctionResponseMapper
import com.example.qrattendance.data.remote.FunctionCallException
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FunctionResponseMapperTest {

    private fun error(code: String, reason: String? = null, network: Boolean = false) =
        FunctionCallException(code = code, reason = reason, message = "msg", isNetworkError = network)

    @Test
    fun `maps every backend reason to the right attendance failure`() {
        val expected = mapOf(
            "SESSION_EXPIRED" to AttendanceFailureReason.SESSION_EXPIRED,
            "INVALID_TOKEN" to AttendanceFailureReason.INVALID_QR,
            "INVALID_PAYLOAD" to AttendanceFailureReason.INVALID_QR,
            "ALREADY_MARKED" to AttendanceFailureReason.ALREADY_MARKED,
            "SESSION_NOT_FOUND" to AttendanceFailureReason.SESSION_NOT_FOUND,
            "NOT_A_STUDENT" to AttendanceFailureReason.NOT_A_STUDENT,
            "PROFILE_MISSING" to AttendanceFailureReason.PROFILE_MISSING,
        )
        expected.forEach { (reason, failure) ->
            assertEquals(reason, failure, FunctionResponseMapper.failureReason(error("FAILED_PRECONDITION", reason)))
        }
    }

    @Test
    fun `network errors win over any reason`() {
        assertEquals(
            AttendanceFailureReason.NETWORK,
            FunctionResponseMapper.failureReason(error("INTERNAL", "ALREADY_MARKED", network = true)),
        )
    }

    @Test
    fun `falls back to the status code when no reason is present`() {
        assertEquals(AttendanceFailureReason.UNAUTHENTICATED, FunctionResponseMapper.failureReason(error("UNAUTHENTICATED")))
        assertEquals(AttendanceFailureReason.SERVICE_UNAVAILABLE, FunctionResponseMapper.failureReason(error("NOT_FOUND")))
        assertEquals(AttendanceFailureReason.INVALID_QR, FunctionResponseMapper.failureReason(error("INVALID_ARGUMENT")))
        assertEquals(AttendanceFailureReason.UNKNOWN, FunctionResponseMapper.failureReason(error("INTERNAL")))
    }

    @Test
    fun `parses a successful attendance response`() {
        val result = FunctionResponseMapper.markAttendance(
            mapOf("status" to "SUCCESS", "sessionId" to "s1", "subject" to "Maths", "teacherName" to "Dr. Rao", "markedAt" to 1234L),
            fallbackSessionId = "fallback",
        )
        assertEquals(MarkAttendanceResult.Success("s1", "Maths", "Dr. Rao", 1234L), result)
    }

    @Test
    fun `an unexpected response is never reported as success`() {
        val result = FunctionResponseMapper.markAttendance(mapOf("subject" to "Maths"), "s1")
        assertEquals(MarkAttendanceResult.Failure(AttendanceFailureReason.UNKNOWN), result)
    }

    @Test
    fun `parses createSession and stats responses with mixed number types`() {
        val created = FunctionResponseMapper.createdSession(
            mapOf("sessionId" to "abc", "createdAt" to 1000, "expiresAt" to 301000.0),
        )
        assertEquals("abc", created?.sessionId)
        assertEquals(1000L, created?.createdAtMillis)
        assertEquals(301000L, created?.expiresAtMillis)
        assertNull(FunctionResponseMapper.createdSession(mapOf("sessionId" to "abc")))

        val stats = FunctionResponseMapper.stats(
            mapOf(
                "attended" to 3,
                "conducted" to 4,
                "courses" to listOf(
                    mapOf("subject" to "Maths", "teacherName" to "Dr. Rao", "attended" to 3, "conducted" to 4),
                    mapOf("broken" to true),
                ),
            ),
        )
        assertEquals(3, stats.attended)
        assertEquals(4, stats.conducted)
        assertEquals(75.0, stats.percentage!!, 0.001)
        assertEquals(1, stats.courses.size)
    }

    @Test
    fun `maps registration errors to friendly messages`() {
        assertEquals(AppError.TeacherNotApproved, FirebaseErrorMapper.map(error("PERMISSION_DENIED", "TEACHER_NOT_APPROVED")))
        assertEquals(AppError.EmailAlreadyInUse, FirebaseErrorMapper.map(error("ALREADY_EXISTS", "EMAIL_EXISTS")))
        assertEquals(AppError.WeakPassword, FirebaseErrorMapper.map(error("INVALID_ARGUMENT", "WEAK_PASSWORD")))
        assertEquals(AppError.NoInternet, FirebaseErrorMapper.map(error("INTERNAL", network = true)))
        assertEquals(AppError.ServiceUnavailable, FirebaseErrorMapper.map(error("NOT_FOUND")))
        assertTrue(FirebaseErrorMapper.map(error("INVALID_ARGUMENT", "INVALID_INPUT")) is AppError.Validation)
        assertEquals(AppError.NoInternet, FirebaseErrorMapper.map(IOException("timeout")))
        assertTrue(FirebaseErrorMapper.map(IllegalStateException()) is AppError.Unknown)
    }
}
