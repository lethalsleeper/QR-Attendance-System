package com.example.qrattendance.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Consistent, locale-aware formatting for all timestamps shown in the app. */
object DateTimeUtils {

    private fun zoned(millis: Long): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

    /** e.g. "Sat, 14 Nov 2026" */
    fun formatDate(millis: Long): String = formatter("EEE, d MMM yyyy").format(zoned(millis))

    /** e.g. "10:42 AM" */
    fun formatTime(millis: Long): String = formatter("h:mm a").format(zoned(millis))

    /** e.g. "14 Nov 2026, 10:42 AM" */
    fun formatDateTime(millis: Long): String = formatter("d MMM yyyy, h:mm a").format(zoned(millis))

    /** e.g. "10:42:07 AM" */
    fun formatTimeWithSeconds(millis: Long): String = formatter("h:mm:ss a").format(zoned(millis))

    /** e.g. "November 2026" */
    fun formatMonth(millis: Long): String = formatter("MMMM yyyy").format(zoned(millis))

    fun greeting(nowMillis: Long): String = when (zoned(nowMillis).hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
