package com.example.qrattendance.utils

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of "now" for countdowns and status badges. Every Cloud Function response
 * carries `serverTime`, which is used to correct for a wrong device clock so that the
 * countdown shown to the teacher matches the server's expiry check.
 */
interface TimeProvider {
    fun now(): Long
    fun syncWithServer(serverTimeMillis: Long)
}

@Singleton
class ServerSyncedTimeProvider @Inject constructor() : TimeProvider {

    @Volatile
    private var offsetMillis: Long = 0L

    override fun now(): Long = System.currentTimeMillis() + offsetMillis

    override fun syncWithServer(serverTimeMillis: Long) {
        if (serverTimeMillis > 0L) {
            offsetMillis = serverTimeMillis - System.currentTimeMillis()
        }
    }
}
