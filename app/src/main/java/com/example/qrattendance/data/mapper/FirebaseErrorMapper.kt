package com.example.qrattendance.data.mapper

import com.example.qrattendance.data.remote.FunctionCallException
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/** Translates Firebase / network exceptions into user-friendly [AppError]s. */
object FirebaseErrorMapper {

    fun map(throwable: Throwable): AppError = when (throwable) {
        is AppException -> throwable.error
        is FunctionCallException -> mapFunctionError(throwable)
        is FirebaseNetworkException -> AppError.NoInternet
        is FirebaseTooManyRequestsException -> AppError.TooManyRequests
        is FirebaseAuthUserCollisionException -> AppError.EmailAlreadyInUse
        // WeakPassword extends InvalidCredentials, so it must be checked first.
        is FirebaseAuthWeakPasswordException -> AppError.WeakPassword
        is FirebaseAuthInvalidUserException ->
            if (throwable.errorCode == "ERROR_USER_DISABLED") AppError.UserDisabled else AppError.InvalidCredentials
        is FirebaseAuthInvalidCredentialsException -> AppError.InvalidCredentials
        is FirebaseAuthException -> AppError.Unknown(throwable.message ?: "Authentication failed. Please try again.")
        is FirebaseFirestoreException -> mapFirestoreError(throwable)
        is IOException -> AppError.NoInternet
        else -> AppError.Unknown()
    }

    fun mapFunctionError(error: FunctionCallException): AppError = when {
        error.isNetworkError -> AppError.NoInternet
        error.reason == "TEACHER_NOT_APPROVED" -> AppError.TeacherNotApproved
        error.reason == "EMAIL_EXISTS" -> AppError.EmailAlreadyInUse
        error.reason == "WEAK_PASSWORD" -> AppError.WeakPassword
        error.reason == "PROFILE_MISSING" -> AppError.ProfileMissing
        error.code == "ALREADY_EXISTS" -> AppError.EmailAlreadyInUse
        error.code == "INVALID_ARGUMENT" -> AppError.Validation(error.message)
        error.code == "UNAUTHENTICATED" -> AppError.Unauthenticated
        error.code == "PERMISSION_DENIED" -> AppError.PermissionDenied
        error.code == "RESOURCE_EXHAUSTED" -> AppError.TooManyRequests
        error.code == "NOT_FOUND" || error.code == "UNIMPLEMENTED" -> AppError.ServiceUnavailable
        error.code == "FAILED_PRECONDITION" -> AppError.Validation(error.message)
        else -> AppError.Unknown()
    }

    private fun mapFirestoreError(error: FirebaseFirestoreException): AppError = when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.Unauthenticated
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.NoInternet
        FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> AppError.TooManyRequests
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            AppError.Unknown("The database is missing a required index. Deploy firestore.indexes.json.")
        else -> AppError.Unknown()
    }
}
