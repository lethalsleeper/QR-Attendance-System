package com.example.qrattendance.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits the current time immediately and then every [periodMillis]. */
fun tickerFlow(periodMillis: Long, timeProvider: TimeProvider): Flow<Long> = flow {
    while (true) {
        emit(timeProvider.now())
        delay(periodMillis)
    }
}

/** Generic loading/content/error wrapper for screen sections backed by a Flow. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}
