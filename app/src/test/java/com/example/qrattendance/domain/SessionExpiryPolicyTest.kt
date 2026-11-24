package com.example.qrattendance.domain

import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.SessionExpiryPolicy
import com.example.qrattendance.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryPolicyTest {

    private val created = 1_700_000_000_000L
    private val expires = created + 5 * 60_000L

    private fun session(status: SessionStatus = SessionStatus.ACTIVE) = Session(
        sessionId = "s1",
        teacherId = "t1",
        teacherName = "Dr. Rao",
        subject = "Maths",
        createdAtMillis = created,
        expiresAtMillis = expires,
        token = "token",
        status = status,
        durationMinutes = 5,
        attendanceCount = 0,
    )

    @Test
    fun `session is active before expiry`() {
        assertTrue(session().isActive(expires - 1))
        assertEquals(SessionStatus.ACTIVE, session().effectiveStatus(created))
    }

    @Test
    fun `session expires exactly at expiresAt (matches backend)`() {
        assertFalse(session().isActive(expires))
        assertTrue(SessionExpiryPolicy.isExpired(SessionStatus.ACTIVE, expires, expires))
        assertFalse(session().isActive(expires + 1))
    }

    @Test
    fun `ended session is expired even before its expiry time`() {
        assertFalse(session(SessionStatus.EXPIRED).isActive(created))
        assertEquals(0L, session(SessionStatus.EXPIRED).remainingMillis(created))
    }

    @Test
    fun `unknown stored status fails closed`() {
        assertEquals(SessionStatus.EXPIRED, SessionStatus.fromValue("paused"))
        assertEquals(SessionStatus.EXPIRED, SessionStatus.fromValue(null))
        assertEquals(SessionStatus.ACTIVE, SessionStatus.fromValue("active"))
    }

    @Test
    fun `remaining time never goes negative`() {
        assertEquals(60_000L, session().remainingMillis(expires - 60_000L))
        assertEquals(0L, session().remainingMillis(expires + 10_000L))
    }

    @Test
    fun `progress is clamped between 0 and 1`() {
        assertEquals(1f, SessionExpiryPolicy.progress(10, 10), 0.0001f)
        assertEquals(0.5f, SessionExpiryPolicy.progress(5, 10), 0.0001f)
        assertEquals(0f, SessionExpiryPolicy.progress(0, 10), 0.0001f)
        assertEquals(1f, SessionExpiryPolicy.progress(20, 10), 0.0001f)
        assertEquals(0f, SessionExpiryPolicy.progress(5, 0), 0.0001f)
    }

    @Test
    fun `countdown is formatted as mm-ss and rounds partial seconds up`() {
        assertEquals("05:00", SessionExpiryPolicy.formatCountdown(300_000))
        assertEquals("04:59", SessionExpiryPolicy.formatCountdown(299_000))
        assertEquals("00:01", SessionExpiryPolicy.formatCountdown(400))
        assertEquals("00:00", SessionExpiryPolicy.formatCountdown(0))
        assertEquals("00:00", SessionExpiryPolicy.formatCountdown(-5_000))
        assertEquals("15:00", SessionExpiryPolicy.formatCountdown(900_000))
    }

    @Test
    fun `allowed durations match the backend`() {
        assertEquals(listOf(2, 5, 10, 15, 20), SessionExpiryPolicy.ALLOWED_DURATIONS_MINUTES)
        assertTrue(SessionExpiryPolicy.DEFAULT_DURATION_MINUTES in SessionExpiryPolicy.ALLOWED_DURATIONS_MINUTES)
    }
}
