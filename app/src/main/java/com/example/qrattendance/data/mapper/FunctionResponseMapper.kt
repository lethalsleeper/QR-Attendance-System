package com.example.qrattendance.data.mapper

import com.example.qrattendance.data.remote.FunctionCallException
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.CourseStats
import com.example.qrattendance.domain.model.CreatedSession
import com.example.qrattendance.domain.model.MarkAttendanceResult

/** Maps Cloud Function responses / errors to domain models. Pure and unit tested. */
object FunctionResponseMapper {

    fun createdSession(response: Map<String, Any?>): CreatedSession? {
        val sessionId = response["sessionId"] as? String ?: return null
        val expiresAt = (response["expiresAt"] as? Number)?.toLong() ?: return null
        val createdAt = (response["createdAt"] as? Number)?.toLong() ?: expiresAt
        return CreatedSession(sessionId = sessionId, createdAtMillis = createdAt, expiresAtMillis = expiresAt)
    }

    fun markAttendance(response: Map<String, Any?>, fallbackSessionId: String): MarkAttendanceResult {
        if (response["status"] != "SUCCESS") {
            return MarkAttendanceResult.Failure(AttendanceFailureReason.UNKNOWN)
        }
        return MarkAttendanceResult.Success(
            sessionId = response["sessionId"] as? String ?: fallbackSessionId,
            subject = response["subject"] as? String ?: "",
            teacherName = (response["teacherName"] as? String)?.takeIf { it.isNotBlank() },
            markedAtMillis = (response["markedAt"] as? Number)?.toLong() ?: 0L,
        )
    }

    fun failureReason(error: FunctionCallException): AttendanceFailureReason {
        if (error.isNetworkError) return AttendanceFailureReason.NETWORK
        return when (error.reason) {
            "SESSION_EXPIRED" -> AttendanceFailureReason.SESSION_EXPIRED
            "INVALID_TOKEN", "INVALID_PAYLOAD" -> AttendanceFailureReason.INVALID_QR
            "ALREADY_MARKED" -> AttendanceFailureReason.ALREADY_MARKED
            "SESSION_NOT_FOUND" -> AttendanceFailureReason.SESSION_NOT_FOUND
            "NOT_A_STUDENT" -> AttendanceFailureReason.NOT_A_STUDENT
            "PROFILE_MISSING" -> AttendanceFailureReason.PROFILE_MISSING
            else -> when (error.code) {
                "UNAUTHENTICATED" -> AttendanceFailureReason.UNAUTHENTICATED
                "INVALID_ARGUMENT" -> AttendanceFailureReason.INVALID_QR
                // A NOT_FOUND without our reason means the function itself is not deployed.
                "NOT_FOUND", "UNAVAILABLE", "UNIMPLEMENTED" -> AttendanceFailureReason.SERVICE_UNAVAILABLE
                else -> AttendanceFailureReason.UNKNOWN
            }
        }
    }

    fun stats(response: Map<String, Any?>): AttendanceStats {
        val courses = (response["courses"] as? List<*>).orEmpty().mapNotNull { item ->
            val course = item as? Map<*, *> ?: return@mapNotNull null
            CourseStats(
                subject = course["subject"] as? String ?: return@mapNotNull null,
                teacherName = (course["teacherName"] as? String)?.takeIf { it.isNotBlank() },
                attended = (course["attended"] as? Number)?.toInt() ?: 0,
                conducted = (course["conducted"] as? Number)?.toInt() ?: 0,
            )
        }
        return AttendanceStats(
            attended = (response["attended"] as? Number)?.toInt() ?: 0,
            conducted = (response["conducted"] as? Number)?.toInt() ?: 0,
            courses = courses,
        )
    }
}
