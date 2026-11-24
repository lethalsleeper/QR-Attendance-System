package com.example.qrattendance.domain.repository

import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.domain.model.StudentAttendance
import kotlinx.coroutines.flow.Flow

interface AttendanceRepository {
    /** Sends the scanned payload to the `markAttendance` Cloud Function for validation + recording. */
    suspend fun markAttendance(payload: QrPayload): MarkAttendanceResult

    /** Real-time attendance history of a student, newest first. Errors are thrown as AppException. */
    fun observeAttendanceHistory(studentId: String, limit: Long? = null): Flow<List<StudentAttendance>>

    /** Server-computed attendance statistics (`getStudentStats` Cloud Function). */
    suspend fun getAttendanceStats(): AppResult<AttendanceStats>
}
