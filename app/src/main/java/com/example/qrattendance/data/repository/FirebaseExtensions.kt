package com.example.qrattendance.data.repository

import com.example.qrattendance.data.mapper.FirebaseErrorMapper
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppException
import com.example.qrattendance.domain.model.AppResult
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Runs [block] and converts any failure into an [AppResult.Failure] (cancellation is rethrown). */
internal suspend fun <T> safeCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(FirebaseErrorMapper.map(e))
}

/**
 * Firestore snapshot listener → cold Flow. The listener is registered when collection
 * starts and removed in awaitClose when the collector is cancelled (e.g. the ViewModel
 * stops collecting), so no listener outlives the screen that needs it.
 */
internal fun <T> Query.snapshotFlow(transform: (QuerySnapshot) -> T): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(AppException(FirebaseErrorMapper.map(error)))
            return@addSnapshotListener
        }
        if (snapshot != null) {
            try {
                trySend(transform(snapshot))
            } catch (e: Exception) {
                close(AppException(FirebaseErrorMapper.map(e)))
            }
        }
    }
    awaitClose { registration.remove() }
}

/** Document listener → Flow. A missing document (or one [transform] rejects) closes the flow with NotFound. */
internal fun <T : Any> DocumentReference.snapshotFlow(transform: (DocumentSnapshot) -> T?): Flow<T> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(AppException(FirebaseErrorMapper.map(error)))
            return@addSnapshotListener
        }
        if (snapshot == null) return@addSnapshotListener
        val value = try {
            if (snapshot.exists()) transform(snapshot) else null
        } catch (e: Exception) {
            null
        }
        if (value != null) trySend(value) else close(AppException(AppError.NotFound))
    }
    awaitClose { registration.remove() }
}
