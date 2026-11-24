package com.example.qrattendance.data

import com.example.qrattendance.data.mapper.FirestoreMappers
import com.example.qrattendance.domain.model.SessionStatus
import com.example.qrattendance.domain.model.UserRole
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreMappersTest {

    private val created = Timestamp(1_700_000_000L, 0)
    private val expires = Timestamp(1_700_000_300L, 500_000_000)

    @Test
    fun `maps a user profile`() {
        val user = FirestoreMappers.user(
            "uid1",
            mapOf("name" to "Asha", "email" to "a@b.com", "role" to "student", "rollNumber" to "21CS045", "createdAt" to created),
        )!!
        assertEquals("uid1", user.uid)
        assertEquals(UserRole.STUDENT, user.role)
        assertEquals("21CS045", user.rollNumber)
        assertEquals(1_700_000_000_000L, user.createdAtMillis)
    }

    @Test
    fun `profiles with an unknown role are rejected`() {
        assertNull(FirestoreMappers.user("uid1", mapOf("name" to "X", "role" to "admin")))
        assertNull(FirestoreMappers.user("uid1", mapOf("name" to "X")))
    }

    @Test
    fun `maps a session including millisecond precision timestamps`() {
        val session = FirestoreMappers.session(
            "s1",
            mapOf(
                "teacherId" to "t1",
                "teacherName" to "Dr. Rao",
                "subject" to "Maths",
                "createdAt" to created,
                "expiresAt" to expires,
                "token" to "tok",
                "status" to "active",
                "durationMinutes" to 5L,
                "attendanceCount" to 12L,
            ),
        )!!
        assertEquals(1_700_000_300_500L, session.expiresAtMillis)
        assertEquals(SessionStatus.ACTIVE, session.status)
        assertEquals(12, session.attendanceCount)
        assertEquals(5, session.durationMinutes)
    }

    @Test
    fun `sessions without an expiry are skipped instead of crashing`() {
        assertNull(FirestoreMappers.session("s1", mapOf("subject" to "Maths")))
    }

    @Test
    fun `maps attendance and history documents with sensible defaults`() {
        val record = FirestoreMappers.attendanceRecord("student1", mapOf("studentName" to "Asha", "markedAt" to created))
        assertEquals("student1", record.studentId)
        assertNull(record.rollNumber)
        assertEquals("present", record.status)

        val history = FirestoreMappers.studentAttendance("s1", mapOf("subject" to "Maths", "markedAt" to created))
        assertEquals("s1", history.sessionId)
        assertEquals(1_700_000_000_000L, history.sessionDateMillis)
    }
}
