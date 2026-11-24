package com.example.qrattendance.data.repository

import com.example.qrattendance.data.mapper.FirestoreMappers
import com.example.qrattendance.data.remote.CloudFunctionNames
import com.example.qrattendance.data.remote.CloudFunctionsDataSource
import com.example.qrattendance.data.remote.FirestoreSchema
import com.example.qrattendance.domain.model.AppError
import com.example.qrattendance.domain.model.AppResult
import com.example.qrattendance.domain.model.RegistrationRequest
import com.example.qrattendance.domain.model.User
import com.example.qrattendance.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: CloudFunctionsDataSource,
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Serialises profile loads so concurrent callers don't issue duplicate reads. */
    private val profileMutex = Mutex()

    override fun isSignedIn(): Boolean = auth.currentUser != null

    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val signedIn = firebaseAuth.currentUser != null
            if (!signedIn) _currentUser.value = null
            trySend(signedIn)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun loadCurrentUser(forceRefresh: Boolean): AppResult<User> = profileMutex.withLock {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            _currentUser.value = null
            AppResult.Failure(AppError.Unauthenticated)
        } else {
            val cached = _currentUser.value
            if (!forceRefresh && cached != null && cached.uid == firebaseUser.uid) {
                AppResult.Success(cached)
            } else {
                fetchProfile(firebaseUser.uid)
            }
        }
    }

    override suspend fun login(email: String, password: String): AppResult<User> {
        val signIn = safeCall { auth.signInWithEmailAndPassword(email.trim(), password).await() }
        val uid = when (signIn) {
            is AppResult.Failure -> return signIn
            is AppResult.Success -> signIn.data.user?.uid
        } ?: return AppResult.Failure(AppError.Unauthenticated)

        val profile = profileMutex.withLock { fetchProfile(uid) }
        if (profile is AppResult.Failure) {
            // Never leave the app half signed-in without a usable profile.
            auth.signOut()
            _currentUser.value = null
        }
        return profile
    }

    override suspend fun register(request: RegistrationRequest): AppResult<User> {
        val email = request.email.trim().lowercase()
        val created = safeCall {
            functions.call(
                CloudFunctionNames.REGISTER_USER,
                mapOf(
                    "name" to request.name.trim(),
                    "email" to email,
                    "password" to request.password,
                    "role" to request.role.value,
                    "rollNumber" to request.rollNumber?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }
        if (created is AppResult.Failure) return created

        return when (val result = login(email, request.password)) {
            is AppResult.Success -> result
            is AppResult.Failure -> AppResult.Failure(
                AppError.Unknown(
                    "Your account was created, but signing in failed (${result.error.message}). Please log in.",
                ),
            )
        }
    }

    override suspend fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    /** Must be called while holding [profileMutex]. */
    private suspend fun fetchProfile(uid: String): AppResult<User> {
        val snapshot = safeCall {
            firestore.collection(FirestoreSchema.USERS).document(uid).get().await()
        }
        return when (snapshot) {
            is AppResult.Failure -> snapshot
            is AppResult.Success -> {
                val document = snapshot.data
                val user = if (document.exists()) {
                    FirestoreMappers.user(document.id, document.data.orEmpty())
                } else {
                    null
                }
                if (user == null) {
                    AppResult.Failure(AppError.ProfileMissing)
                } else {
                    _currentUser.value = user
                    AppResult.Success(user)
                }
            }
        }
    }
}
