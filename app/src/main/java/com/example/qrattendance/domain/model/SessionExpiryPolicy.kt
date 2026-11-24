package com.example.qrattendance.domain.model

import java.util.Locale

/**
 * Client-side expiry rules used for display purposes (countdown, status badges).
 *
 * The authoritative check is performed by the `markAttendance` Cloud Function using
 * server time; this policy mirrors that logic so the UI agrees with the backend:
 * a session is expired when its stored status is not ACTIVE, or when `now >= expiresAt`.
 */
object SessionExpiryPolicy {

    /** Durations (minutes) a teacher may choose. Must match ALLOWED_DURATIONS in functions/src/validation.js. */
    val ALLOWED_DURATIONS_MINUTES: List<Int> = listOf(2, 5, 10, 15, 20)

    const val DEFAULT_DURATION_MINUTES: Int = 5

    fun isExpired(status: SessionStatus, expiresAtMillis: Long, nowMillis: Long): Boolean =
        status != SessionStatus.ACTIVE || nowMillis >= expiresAtMillis

    fun effectiveStatus(status: SessionStatus, expiresAtMillis: Long, nowMillis: Long): SessionStatus =
        if (isExpired(status, expiresAtMillis, nowMillis)) SessionStatus.EXPIRED else SessionStatus.ACTIVE

    fun remainingMillis(status: SessionStatus, expiresAtMillis: Long, nowMillis: Long): Long =
        if (status != SessionStatus.ACTIVE) 0L else (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    fun progress(remainingMillis: Long, totalMillis: Long): Float =
        if (totalMillis <= 0L) 0f else (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)

    /** Formats remaining time as mm:ss, rounding partial seconds up so 0.4s shows as 00:01. */
    fun formatCountdown(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
