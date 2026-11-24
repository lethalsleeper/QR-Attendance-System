package com.example.qrattendance.domain.usecase

import com.example.qrattendance.domain.model.QrPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Encodes / decodes the QR payload:
 * ```
 * {"sessionId":"<firestore id>","token":"<base64url random token>"}
 * ```
 * Decoding is strict about field presence and format so that random QR codes (URLs,
 * Wi-Fi codes, product codes...) are rejected locally without a network round-trip.
 * The backend re-validates everything; this is only a fast first filter.
 */
object QrPayloadCodec {

    const val MAX_PAYLOAD_LENGTH = 1024

    private val SESSION_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{32,128}$")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Serializable
    private data class QrPayloadDto(
        @SerialName("sessionId") val sessionId: String? = null,
        @SerialName("token") val token: String? = null,
    )

    fun encode(payload: QrPayload): String =
        json.encodeToString(QrPayloadDto.serializer(), QrPayloadDto(payload.sessionId, payload.token))

    /** Returns the parsed payload, or null if [raw] is not a valid attendance QR code. */
    fun decode(raw: String?): QrPayload? {
        val text = raw?.trim() ?: return null
        if (text.isEmpty() || text.length > MAX_PAYLOAD_LENGTH) return null
        if (!text.startsWith("{") || !text.endsWith("}")) return null

        val dto = try {
            json.decodeFromString(QrPayloadDto.serializer(), text)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }

        val sessionId = dto.sessionId?.trim().orEmpty()
        val token = dto.token?.trim().orEmpty()
        if (!SESSION_ID_REGEX.matches(sessionId)) return null
        if (!TOKEN_REGEX.matches(token)) return null
        return QrPayload(sessionId = sessionId, token = token)
    }
}
