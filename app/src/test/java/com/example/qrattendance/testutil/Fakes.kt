package com.example.qrattendance.testutil

import com.example.qrattendance.data.remote.CloudFunctionsDataSource
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.AttendanceRecord
import com.example.qrattendance.domain.model.AttendanceStats
import com.example.qrattendance.domain.model.CreatedSession
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.domain.model.RegistrationRequest
import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.StudentAttendance
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.domain.repository.SessionRepository
import com.example.qrattendance.utils.NetworkMonitor
import com.example.qrattendance.utils.TimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

class FakeTimeProvider(var currentTime: Long = 1_700_000_000_000L) : TimeProvider {
    override fun now(): Long = currentTime
    override fun syncWithServer(serverTimeMillis: Long) {
        currentTime = serverTimeMillis
    }
}

class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    private val state = MutableStateFlow(online)
    override val isOnline: Flow<Boolean> = state
    override fun isCurrentlyOnline(): Boolean = online
}

class FakeAttendanceRepository : AttendanceRepository {
    val markCalls = mutableListOf<QrPayload>()
    var nextResult: MarkAttendanceResult = MarkAttendanceResult.Success("s1", "Maths", "Dr. Rao", 1L)

    /** When set, markAttendance suspends until this deferred completes (simulates network latency). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun markAttendance(payload: QrPayload): MarkAttendanceResult {
        markCalls += payload
        gate?.await()
        return nextResult
    }

    override fun observeAttendanceHistory(studentId: String, limit: Long?): Flow<List<StudentAttendance>> = emptyFlow()

    override suspend fun getAttendanceStats(): AppResult<AttendanceStats> =
        AppResult.Success(AttendanceStats(0, 0, emptyList()))
}

class FakeAuthRepository(initialUser: User? = null) : AuthRepository {
    private val userState = MutableStateFlow(initialUser)
    override val currentUser: StateFlow<User?> = userState

    var loginResult: AppResult<User> = AppResult.Failure(com.example.qrattendance.domain.model.AppError.InvalidCredentials)
    var registerResult: AppResult<User>? = null
    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<RegistrationRequest>()

    override fun isSignedIn(): Boolean = userState.value != null
    override fun observeAuthState(): Flow<Boolean> = userState.map { it != null }
    override suspend fun loadCurrentUser(forceRefresh: Boolean): AppResult<User> =
        userState.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(com.example.qrattendance.domain.model.AppError.Unauthenticated)

    override suspend fun login(email: String, password: String): AppResult<User> {
        loginCalls += email to password
        if (loginResult is AppResult.Success) userState.value = (loginResult as AppResult.Success<User>).data
        return loginResult
    }

    override suspend fun register(request: RegistrationRequest): AppResult<User> {
        registerCalls += request
        val result = registerResult ?: loginResult
        if (result is AppResult.Success) userState.value = result.data
        return result
    }

    override suspend fun logout() {
        userState.value = null
    }
}

class FakeSessionRepository : SessionRepository {
    val createCalls = mutableListOf<Pair<String, Int>>()
    var createResult: AppResult<CreatedSession> = AppResult.Success(CreatedSession("session-1", 0L, 300_000L))

    override suspend fun createSession(subject: String, durationMinutes: Int): AppResult<CreatedSession> {
        createCalls += subject to durationMinutes
        return createResult
    }

    override fun observeTeacherSessions(teacherId: String): Flow<List<Session>> = emptyFlow()
    override fun observeSession(sessionId: String): Flow<Session> = emptyFlow()
    override fun observeAttendance(sessionId: String): Flow<List<AttendanceRecord>> = emptyFlow()
    override suspend fun endSession(sessionId: String): AppResult<Unit> = AppResult.Success(Unit)
}

/** Programmable callable-function data source. */
class FakeCloudFunctionsDataSource : CloudFunctionsDataSource {
    val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
    var handler: (String, Map<String, Any?>) -> Map<String, Any?> = { _, _ -> emptyMap() }

    override suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> {
        calls += name to data
        return handler(name, data)
    }
}
