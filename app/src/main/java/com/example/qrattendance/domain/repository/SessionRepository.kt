package com.example.qrattendance.domain.repository

import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.domain.model.CreatedSession
import com.example.qrattendance.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /** Creates a session via the `createSession` Cloud Function (server generates id, token, expiry). */
    suspend fun createSession(subject: String, durationMinutes: Int): AppResult<CreatedSession>

    /** Real-time list of the teacher's sessions, newest first. Errors are thrown as AppException. */
    fun observeTeacherSessions(teacherId: String): Flow<List<Session>>

    /** Real-time updates of a single session. Errors are thrown as AppException. */
    fun observeSession(sessionId: String): Flow<Session>

    /** Real-time attendance list of a session, newest first. Errors are thrown as AppException. */
    fun observeAttendance(sessionId: String): Flow<List<AttendanceRecord>>

    /** Ends an active session early (or marks a timed-out session as expired). */
    suspend fun endSession(sessionId: String): AppResult<Unit>
}
