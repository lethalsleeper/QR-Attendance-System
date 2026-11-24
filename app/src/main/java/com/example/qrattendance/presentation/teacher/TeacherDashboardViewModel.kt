package com.example.qrattendance.presentation.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qrattendance.domain.model.Session
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.model.toAppError
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.domain.repository.SessionRepository
import com.example.qrattendance.utils.LoadState
import com.example.qrattendance.utils.TimeProvider
import com.example.qrattendance.utils.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SessionListItem(
    val session: Session,
    val isActive: Boolean,
)

data class SubjectCount(
    val subject: String,
    val sessions: Int,
    val checkIns: Int,
)
data class TeacherDashboardUiState(
    val user: User? = null,
    val greeting: String = "",
    val isLoading: Boolean = true,
    val sessions: List<SessionListItem> = emptyList(),
    val errorMessage: String? = null,
    val totalSessions: Int = 0,
    val totalCheckIns: Int = 0,
    val activeSessions: Int = 0,
    val subjectCounts: List<SubjectCount> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TeacherDashboardViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    /** Firestore listener → Flow → StateFlow. Re-subscribes when the user changes or on retry. */
    private val sessionsState: Flow<LoadState<List<Session>>> = combine(
        authRepository.currentUser.filterNotNull().map { it.uid }.distinctUntilChanged(),
        retryTrigger,
    ) { uid, _ -> uid }
        .flatMapLatest { uid ->
            sessionRepository.observeTeacherSessions(uid)
                .map<List<Session>, LoadState<List<Session>>> { LoadState.Loaded(it) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Error(it.toAppError().message)) }
        }

    val uiState: StateFlow<TeacherDashboardUiState> = combine(
        authRepository.currentUser,
        sessionsState,
        tickerFlow(STATUS_REFRESH_MS, timeProvider),
    ) { user, load, now ->
        val sessions = (load as? LoadState.Loaded)?.data.orEmpty()
        val items = sessions.map { SessionListItem(it, it.isActive(now)) }
        TeacherDashboardUiState(
            user = user,
            greeting = com.example.qrattendance.utils.DateTimeUtils.greeting(now),
            isLoading = load is LoadState.Loading,
            sessions = items,
            errorMessage = (load as? LoadState.Error)?.message,
            totalSessions = sessions.size,
            totalCheckIns = sessions.sumOf { it.attendanceCount },
            activeSessions = items.count { it.isActive },
            subjectCounts = subjectCountsOf(sessions),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeacherDashboardUiState())

    private fun subjectCountsOf(sessions: List<Session>): List<SubjectCount> =
        sessions
            .groupBy { it.subject.trim().lowercase() }          // buckets: "maths" → [all maths sessions]
            .map { (_, group) ->
                SubjectCount(
                    subject = group.first().subject,             // show the name as the teacher typed it
                    sessions = group.size,                       // how many sessions in this bucket
                    checkIns = group.sumOf { it.attendanceCount },
                )
            }
            .sortedByDescending { it.sessions }                  // most sessions first

    fun retry() = retryTrigger.update { it + 1 }

    private companion object {
        /** How often "Live" badges are re-evaluated against the clock. */
        const val STATUS_REFRESH_MS = 5_000L
    }
}
