package com.example.qrattendance.domain.model

/** One student's check-in, as seen by the teacher (sessions/{id}/attendance/{studentUid}). */
data class AttendanceRecord(
    val studentId: String,
    val studentName: String,
    val rollNumber: String?,
    val markedAtMillis: Long,
    val status: String,
)

/** One entry of a student's own history (users/{uid}/attendance/{sessionId}). */
data class StudentAttendance(
    val sessionId: String,
    val subject: String,
    val teacherName: String?,
    val sessionDateMillis: Long,
    val markedAtMillis: Long,
    val status: String,
)

/** Contents of the QR code shown by the teacher. */
data class QrPayload(
    val sessionId: String,
    val token: String,
)

/**
 * Per-course statistics. A "course" is a subject (case-insensitive) taught by one teacher.
 * [conducted] counts that teacher's sessions for the subject created after the student registered.
 */
data class CourseStats(
    val subject: String,
    val teacherName: String?,
    val attended: Int,
    val conducted: Int,
) {
    val percentage: Double?
        get() = if (conducted > 0) attended * 100.0 / conducted else null
}

data class AttendanceStats(
    val attended: Int,
    val conducted: Int,
    val courses: List<CourseStats>,
) {
    /** attended ÷ conducted across all of the student's courses, or null when nothing was conducted. */
    val percentage: Double?
        get() = if (conducted > 0) attended * 100.0 / conducted else null
}

sealed interface MarkAttendanceResult {
    data class Success(
        val sessionId: String,
        val subject: String,
        val teacherName: String?,
        val markedAtMillis: Long,
    ) : MarkAttendanceResult

    data class Failure(val reason: AttendanceFailureReason) : MarkAttendanceResult
}

enum class AttendanceFailureReason(
    val title: String,
    val description: String,
    val canRetry: Boolean,
) {
    SESSION_EXPIRED(
        "QR Code Expired",
        "This attendance session has ended. Ask your teacher to start a new session.",
        true,
    ),
    INVALID_QR(
        "Invalid QR Code",
        "This isn't a valid attendance QR code. Scan the code displayed by your teacher.",
        true,
    ),
    ALREADY_MARKED(
        "Attendance Already Marked",
        "You have already been marked present for this session.",
        false,
    ),
    SESSION_NOT_FOUND(
        "Session Not Found",
        "We couldn't find this session. It may have been removed.",
        true,
    ),
    NETWORK(
        "Unable to connect. Please try again.",
        "Check your internet connection and scan again. Your attendance has NOT been recorded.",
        true,
    ),
    NOT_A_STUDENT(
        "Students Only",
        "Only student accounts can mark attendance.",
        false,
    ),
    UNAUTHENTICATED(
        "Please Log In Again",
        "Your login session has expired. Log out and log in again, then scan.",
        false,
    ),
    PROFILE_MISSING(
        "Profile Not Found",
        "Your student profile is missing. Please contact your administrator.",
        false,
    ),
    SERVICE_UNAVAILABLE(
        "Service Unavailable",
        "The attendance service is unavailable right now. Please try again shortly.",
        true,
    ),
    UNKNOWN(
        "Something Went Wrong",
        "We couldn't record your attendance. Please try again.",
        true,
    ),
}
