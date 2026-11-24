package com.example.qrattendance.domain.usecase

import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.CreatedSession
import com.example.qrattendance.domain.model.SessionExpiryPolicy
import com.example.qrattendance.domain.repository.SessionRepository
import javax.inject.Inject

class StartSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(subject: String, durationMinutes: Int): AppResult<CreatedSession> {
        InputValidator.validateSubject(subject)?.let { return AppResult.Failure(AppError.Validation(it)) }
        if (durationMinutes !in SessionExpiryPolicy.ALLOWED_DURATIONS_MINUTES) {
            return AppResult.Failure(AppError.Validation("Choose a valid session duration"))
        }
        return sessionRepository.createSession(InputValidator.normalizeSubject(subject), durationMinutes)
    }
}
