package com.example.qrattendance.data.repository

import com.example.qrattendance.data.mapper.FirestoreMappers
import com.example.qrattendance.data.mapper.FunctionResponseMapper
import com.example.qrattendance.data.remote.CloudFunctionNames
import com.example.qrattendance.data.remote.CloudFunctionsDataSource
import com.example.qrattendance.data.remote.FirestoreSchema
import com.example.qrattendance.data.remote.FirestoreSchema.HistoryFields
import com.example.qrattendance.data.remote.FunctionCallException
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.domain.model.StudentAttendance
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.utils.TimeProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: CloudFunctionsDataSource,
    private val timeProvider: TimeProvider,
) : AttendanceRepository {

    /**
     * Attendance is never written from the client. The Cloud Function validates the
     * token / expiry / role and writes the record in a transaction; success is reported
     * only after that transaction has committed.
     */
    override suspend fun markAttendance(payload: QrPayload): MarkAttendanceResult = try {
        val response = functions.call(
            CloudFunctionNames.MARK_ATTENDANCE,
            mapOf("sessionId" to payload.sessionId, "token" to payload.token),
        )
        (response["serverTime"] as? Number)?.toLong()?.let(timeProvider::syncWithServer)
        FunctionResponseMapper.markAttendance(response, payload.sessionId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FunctionCallException) {
        MarkAttendanceResult.Failure(FunctionResponseMapper.failureReason(e))
    } catch (e: FirebaseNetworkException) {
        MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK)
    } catch (e: IOException) {
        MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK)
    } catch (e: Exception) {
        MarkAttendanceResult.Failure(AttendanceFailureReason.UNKNOWN)
    }

    override fun observeAttendanceHistory(studentId: String, limit: Long?): Flow<List<StudentAttendance>> {
        val base = firestore.collection(FirestoreSchema.USERS)
            .document(studentId)
            .collection(FirestoreSchema.ATTENDANCE)
            .orderBy(HistoryFields.MARKED_AT, Query.Direction.DESCENDING)
        val query = if (limit != null) base.limit(limit) else base
        return query.snapshotFlow { snapshot ->
            snapshot.documents.map { doc -> FirestoreMappers.studentAttendance(doc.id, doc.data.orEmpty()) }
        }
    }

    override suspend fun getAttendanceStats(): AppResult<AttendanceStats> = safeCall {
        val response = functions.call(CloudFunctionNames.GET_STUDENT_STATS, emptyMap())
        (response["serverTime"] as? Number)?.toLong()?.let(timeProvider::syncWithServer)
        FunctionResponseMapper.stats(response)
    }
}
