package com.example.qrattendance.data.mapper

import com.example.qrattendance.data.remote.FirestoreSchema.AttendanceFields
import com.example.qrattendance.data.remote.FirestoreSchema.HistoryFields
import com.example.qrattendance.data.remote.FirestoreSchema.SessionFields
import com.example.qrattendance.data.remote.FirestoreSchema.UserFields
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.SessionStatus
import com.example.qrattendance.domain.model.StudentAttendance
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.model.UserRole
import com.google.firebase.Timestamp
import java.util.Date

/** Converts Firestore timestamp-like values (Timestamp, Date, epoch millis) to epoch millis. */
internal fun Any?.toEpochMillis(): Long? = when (this) {
    is Timestamp -> seconds * 1000L + nanoseconds / 1_000_000L
    is Date -> time
    is Number -> toLong()
    else -> null
}

private fun Map<String, Any?>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

/**
 * Pure mapping functions (document data → domain models). They never throw; malformed
 * documents are skipped (null) instead of crashing a real-time listener.
 */
object FirestoreMappers {

    fun user(documentId: String, data: Map<String, Any?>): User? {
        val role = UserRole.fromValue(data[UserFields.ROLE] as? String) ?: return null
        return User(
            uid = data.string(UserFields.UID) ?: documentId,
            name = data.string(UserFields.NAME) ?: "",
            email = data.string(UserFields.EMAIL) ?: "",
            role = role,
            rollNumber = data.string(UserFields.ROLL_NUMBER),
            createdAtMillis = data[UserFields.CREATED_AT].toEpochMillis(),
        )
    }

    fun session(documentId: String, data: Map<String, Any?>): Session? {
        val expiresAt = data[SessionFields.EXPIRES_AT].toEpochMillis() ?: return null
        val createdAt = data[SessionFields.CREATED_AT].toEpochMillis() ?: expiresAt
        return Session(
            sessionId = documentId,
            teacherId = data.string(SessionFields.TEACHER_ID) ?: "",
            teacherName = data.string(SessionFields.TEACHER_NAME) ?: "",
            subject = data.string(SessionFields.SUBJECT) ?: "Untitled session",
            createdAtMillis = createdAt,
            expiresAtMillis = expiresAt,
            token = data.string(SessionFields.TOKEN),
            status = SessionStatus.fromValue(data[SessionFields.STATUS] as? String),
            durationMinutes = data.int(SessionFields.DURATION_MINUTES) ?: 0,
            attendanceCount = data.int(SessionFields.ATTENDANCE_COUNT) ?: 0,
        )
    }

    fun attendanceRecord(documentId: String, data: Map<String, Any?>): AttendanceRecord =
        AttendanceRecord(
            studentId = data.string(AttendanceFields.STUDENT_ID) ?: documentId,
            studentName = data.string(AttendanceFields.STUDENT_NAME) ?: "Unknown student",
            rollNumber = data.string(AttendanceFields.ROLL_NUMBER),
            markedAtMillis = data[AttendanceFields.MARKED_AT].toEpochMillis() ?: 0L,
            status = data.string(AttendanceFields.STATUS) ?: "present",
        )

    fun studentAttendance(documentId: String, data: Map<String, Any?>): StudentAttendance {
        val markedAt = data[HistoryFields.MARKED_AT].toEpochMillis() ?: 0L
        return StudentAttendance(
            sessionId = data.string(HistoryFields.SESSION_ID) ?: documentId,
            subject = data.string(HistoryFields.SUBJECT) ?: "Untitled session",
            teacherName = data.string(HistoryFields.TEACHER_NAME),
            sessionDateMillis = data[HistoryFields.SESSION_CREATED_AT].toEpochMillis() ?: markedAt,
            markedAtMillis = markedAt,
            status = data.string(HistoryFields.STATUS) ?: "present",
        )
    }
}
