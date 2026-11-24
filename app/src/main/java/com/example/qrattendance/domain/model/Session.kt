package com.example.qrattendance.domain.model

enum class SessionStatus(val value: String) {
    ACTIVE("active"),
    EXPIRED("expired");

    companion object {
        /** Anything that is not explicitly "active" is treated as expired (fail closed). */
        fun fromValue(value: String?): SessionStatus = if (value == ACTIVE.value) ACTIVE else EXPIRED
    }
}

/**
 * A classroom session. [token] is only present for the owning teacher; Firestore security
 * rules prevent anyone else from reading session documents.
 */
data class Session(
    val sessionId: String,
    val teacherId: String,
    val teacherName: String,
    val subject: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val token: String?,
    val status: SessionStatus,
    val durationMinutes: Int,
    val attendanceCount: Int,
) {
    fun effectiveStatus(nowMillis: Long): SessionStatus =
        SessionExpiryPolicy.effectiveStatus(status, expiresAtMillis, nowMillis)

    fun isActive(nowMillis: Long): Boolean = effectiveStatus(nowMillis) == SessionStatus.ACTIVE

    fun remainingMillis(nowMillis: Long): Long =
        SessionExpiryPolicy.remainingMillis(status, expiresAtMillis, nowMillis)

    val totalDurationMillis: Long
        get() = (expiresAtMillis - createdAtMillis).coerceAtLeast(1L)
}

data class CreatedSession(
    val sessionId: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)
