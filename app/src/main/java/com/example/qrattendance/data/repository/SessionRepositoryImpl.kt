package com.example.qrattendance.data.repository

import com.example.qrattendance.data.mapper.FirestoreMappers
import com.example.qrattendance.data.mapper.FunctionResponseMapper
import com.example.qrattendance.data.remote.CloudFunctionNames
import com.example.qrattendance.data.remote.CloudFunctionsDataSource
import com.example.qrattendance.data.remote.FirestoreSchema
import com.example.qrattendance.data.remote.FirestoreSchema.AttendanceFields
import com.example.qrattendance.data.remote.FirestoreSchema.SessionFields
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppException
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.domain.model.CreatedSession
import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.SessionStatus
import com.example.qrattendance.domain.repository.SessionRepository
import com.example.qrattendance.utils.TimeProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: CloudFunctionsDataSource,
    private val timeProvider: TimeProvider,
) : SessionRepository {

    private val sessions get() = firestore.collection(FirestoreSchema.SESSIONS)

    override suspend fun createSession(subject: String, durationMinutes: Int): AppResult<CreatedSession> = safeCall {
        val response = functions.call(
            CloudFunctionNames.CREATE_SESSION,
            mapOf("subject" to subject, "durationMinutes" to durationMinutes),
        )
        (response["serverTime"] as? Number)?.toLong()?.let(timeProvider::syncWithServer)
        FunctionResponseMapper.createdSession(response)
            ?: throw AppException(AppError.Unknown("Unexpected response from the server. Please try again."))
    }

    override fun observeTeacherSessions(teacherId: String): Flow<List<Session>> =
        sessions
            .whereEqualTo(SessionFields.TEACHER_ID, teacherId)
            .orderBy(SessionFields.CREATED_AT, Query.Direction.DESCENDING)
            .limit(MAX_SESSIONS)
            .snapshotFlow { snapshot ->
                snapshot.documents.mapNotNull { doc -> FirestoreMappers.session(doc.id, doc.data.orEmpty()) }
            }

    override fun observeSession(sessionId: String): Flow<Session> =
        sessions.document(sessionId).snapshotFlow { doc ->
            FirestoreMappers.session(doc.id, doc.data.orEmpty())
        }

    override fun observeAttendance(sessionId: String): Flow<List<AttendanceRecord>> =
        sessions.document(sessionId)
            .collection(FirestoreSchema.ATTENDANCE)
            .orderBy(AttendanceFields.MARKED_AT, Query.Direction.DESCENDING)
            .snapshotFlow { snapshot ->
                snapshot.documents.map { doc -> FirestoreMappers.attendanceRecord(doc.id, doc.data.orEmpty()) }
            }

    override suspend fun endSession(sessionId: String): AppResult<Unit> = safeCall {
        val write = sessions.document(sessionId).update(
            mapOf(
                SessionFields.STATUS to SessionStatus.EXPIRED.value,
                SessionFields.ENDED_AT to FieldValue.serverTimestamp(),
            ),
        )
        // Offline writes are queued by Firestore and applied locally at once (the snapshot
        // listener reflects them), so don't block the UI forever waiting for the server ack.
        withTimeoutOrNull(WRITE_ACK_TIMEOUT_MS) { write.await() }
        Unit
    }

    private companion object {
        const val MAX_SESSIONS = 100L
        const val WRITE_ACK_TIMEOUT_MS = 10_000L
    }
}
