package com.example.qrattendance.domain.repository

import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.RegistrationRequest
import com.example.qrattendance.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** The signed-in user's profile, or null when signed out / not yet loaded. */
    val currentUser: StateFlow<User?>

    fun isSignedIn(): Boolean

    /** Emits true while a Firebase user is signed in. */
    fun observeAuthState(): Flow<Boolean>

    /** Loads the signed-in user's Firestore profile (cached unless [forceRefresh]). */
    suspend fun loadCurrentUser(forceRefresh: Boolean = false): AppResult<User>

    suspend fun login(email: String, password: String): AppResult<User>

    /** Creates the account through the `registerUser` Cloud Function, then signs in. */
    suspend fun register(request: RegistrationRequest): AppResult<User>

    suspend fun logout()
}
