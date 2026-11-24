package com.example.qrattendance.data.remote

/**
 * Firestore collection / field names. Keep in sync with functions/src/handlers.js
 * and firestore.rules.
 *
 * users/{uid}
 * users/{uid}/attendance/{sessionId}          ← student history (written by Cloud Function)
 * sessions/{sessionId}                        ← created by Cloud Function, readable by owner
 * sessions/{sessionId}/attendance/{studentId} ← written by Cloud Function only
 * teacherInvites/{lowercaseEmail}             ← admin-managed allow-list, no client access
 */
object FirestoreSchema {
    const val USERS = "users"
    const val SESSIONS = "sessions"
    const val ATTENDANCE = "attendance"

    object UserFields {
        const val UID = "uid"
        const val NAME = "name"
        const val EMAIL = "email"
        const val ROLE = "role"
        const val ROLL_NUMBER = "rollNumber"
        const val CREATED_AT = "createdAt"
    }

    object SessionFields {
        const val SESSION_ID = "sessionId"
        const val TEACHER_ID = "teacherId"
        const val TEACHER_NAME = "teacherName"
        const val SUBJECT = "subject"
        const val CREATED_AT = "createdAt"
        const val EXPIRES_AT = "expiresAt"
        const val TOKEN = "token"
        const val STATUS = "status"
        const val DURATION_MINUTES = "durationMinutes"
        const val ATTENDANCE_COUNT = "attendanceCount"
        const val ENDED_AT = "endedAt"
    }

    object AttendanceFields {
        const val STUDENT_ID = "studentId"
        const val STUDENT_NAME = "studentName"
        const val ROLL_NUMBER = "rollNumber"
        const val MARKED_AT = "markedAt"
        const val STATUS = "status"
    }

    object HistoryFields {
        const val SESSION_ID = "sessionId"
        const val SUBJECT = "subject"
        const val TEACHER_ID = "teacherId"
        const val TEACHER_NAME = "teacherName"
        const val SESSION_CREATED_AT = "sessionCreatedAt"
        const val MARKED_AT = "markedAt"
        const val STATUS = "status"
    }
}

object CloudFunctionNames {
    const val REGISTER_USER = "registerUser"
    const val CREATE_SESSION = "createSession"
    const val MARK_ATTENDANCE = "markAttendance"
    const val GET_STUDENT_STATS = "getStudentStats"
}
