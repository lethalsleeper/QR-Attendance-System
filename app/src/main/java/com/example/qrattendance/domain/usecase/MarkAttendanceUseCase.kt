package com.example.qrattendance.domain.usecase

import com.example.qrattendance.domain.model.AttendanceFailureReason
import com.example.qrattendance.domain.model.MarkAttendanceResult
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.utils.NetworkMonitor
import javax.inject.Inject

/**
 * Scanned text → attendance result.
 * 1. Parse and sanity-check the QR payload locally (rejects unrelated QR codes instantly).
 * 2. Fail fast when the device is offline (nothing is recorded).
 * 3. Delegate to the backend, which performs the authoritative validation.
 */
class MarkAttendanceUseCase @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(rawQrValue: String): MarkAttendanceResult {
        val payload = QrPayloadCodec.decode(rawQrValue)
            ?: return MarkAttendanceResult.Failure(AttendanceFailureReason.INVALID_QR)

        if (!networkMonitor.isCurrentlyOnline()) {
            return MarkAttendanceResult.Failure(AttendanceFailureReason.NETWORK)
        }
        return attendanceRepository.markAttendance(payload)
    }
}
