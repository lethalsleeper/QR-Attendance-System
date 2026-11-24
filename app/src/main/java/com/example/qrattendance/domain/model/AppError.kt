package com.example.qrattendance.domain.model

/** User-facing error categories. Every message is safe to show directly in the UI. */
sealed class AppError(open val message: String) {
    data object NoInternet :
        AppError("No internet connection. Check your network and try again.")

    data object InvalidCredentials :
        AppError("Incorrect email or password.")

    data object EmailAlreadyInUse :
        AppError("An account with this email already exists. Try logging in instead.")

    data object WeakPassword :
        AppError("Password is too weak. Use at least 8 characters with letters and numbers.")

    data object TooManyRequests :
        AppError("Too many attempts. Please wait a moment and try again.")

    data object UserDisabled :
        AppError("This account has been disabled. Contact your administrator.")

    data object PermissionDenied :
        AppError("You don't have permission to perform this action.")

    data object Unauthenticated :
        AppError("Your login session has expired. Please log in again.")

    data object ProfileMissing :
        AppError("Your user profile could not be found. Contact your administrator or register again.")

    data object TeacherNotApproved :
        AppError("This email is not approved for a teacher account. Ask your administrator to approve it, or register as a student.")

    data object NotFound :
        AppError("The requested item could not be found.")

    data object ServiceUnavailable :
        AppError("The service is unavailable. Make sure the Cloud Functions are deployed, then try again.")

    data class Validation(override val message: String) : AppError(message)

    data class Unknown(override val message: String = "Something went wrong. Please try again.") :
        AppError(message)
}

/** Exception wrapper used to carry an [AppError] through Kotlin Flows. */
class AppException(val error: AppError) : Exception(error.message)

fun Throwable.toAppError(): AppError = (this as? AppException)?.error ?: AppError.Unknown()

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
