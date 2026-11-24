package com.example.qrattendance.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Thin abstraction over Firebase callable functions so repositories can be unit tested. */
interface CloudFunctionsDataSource {
    /** Calls the callable function [name]. Throws [FunctionCallException] on backend errors. */
    suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?>
}

/**
 * Backend error raised by a callable function.
 * @property code gRPC-style code name, e.g. "PERMISSION_DENIED", "FAILED_PRECONDITION".
 * @property reason machine-readable reason sent by our functions in `details.reason`.
 */
class FunctionCallException(
    val code: String,
    val reason: String?,
    override val message: String,
    val isNetworkError: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)

@Singleton
class FirebaseCloudFunctionsDataSource @Inject constructor(
    private val functions: FirebaseFunctions,
) : CloudFunctionsDataSource {

    override suspend fun call(name: String, data: Map<String, Any?>): Map<String, Any?> {
        try {
            val callable = functions.getHttpsCallable(name)
            callable.setTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val result = callable.call(data).await()
            val map = result.getData() as? Map<*, *> ?: return emptyMap()
            return map.entries.associate { (key, value) -> key.toString() to value }
        } catch (e: FirebaseFunctionsException) {
            val reason = (e.details as? Map<*, *>)?.get("reason") as? String
            val isNetwork = e.cause is IOException ||
                e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
            throw FunctionCallException(
                code = e.code.name,
                reason = reason,
                message = e.message ?: e.code.name,
                isNetworkError = isNetwork,
                cause = e,
            )
        }
    }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 30L
    }
}
