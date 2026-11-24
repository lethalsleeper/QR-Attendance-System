package com.example.qrattendance.domain

import com.example.qrattendance.domain.model.QrPayload
import com.example.qrattendance.domain.usecase.QrPayloadCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadCodecTest {

    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd" // 42 chars, base64url alphabet
    private val sessionId = "x7Gk2LmPq9RsTuVw"

    @Test
    fun `decodes a valid payload`() {
        val raw = """{"sessionId":"$sessionId","token":"$token"}"""
        assertEquals(QrPayload(sessionId, token), QrPayloadCodec.decode(raw))
    }

    @Test
    fun `tolerates whitespace and unknown fields`() {
        val raw = """
            {
              "sessionId" : "$sessionId",
              "token" : "$token",
              "version" : 2
            }
        """.trimIndent()
        assertEquals(QrPayload(sessionId, token), QrPayloadCodec.decode("  $raw  "))
    }

    @Test
    fun `round trips through encode`() {
        val payload = QrPayload(sessionId, token)
        val encoded = QrPayloadCodec.encode(payload)
        assertTrue(encoded.contains("\"sessionId\":\"$sessionId\""))
        assertTrue(encoded.contains("\"token\":\"$token\""))
        assertEquals(payload, QrPayloadCodec.decode(encoded))
    }

    @Test
    fun `rejects missing fields`() {
        assertNull(QrPayloadCodec.decode("""{"sessionId":"$sessionId"}"""))
        assertNull(QrPayloadCodec.decode("""{"token":"$token"}"""))
        assertNull(QrPayloadCodec.decode("""{}"""))
    }

    @Test
    fun `rejects blank or wrongly typed fields`() {
        assertNull(QrPayloadCodec.decode("""{"sessionId":"","token":"$token"}"""))
        assertNull(QrPayloadCodec.decode("""{"sessionId":"$sessionId","token":"   "}"""))
        assertNull(QrPayloadCodec.decode("""{"sessionId":123,"token":"$token"}"""))
        assertNull(QrPayloadCodec.decode("""{"sessionId":null,"token":"$token"}"""))
    }

    @Test
    fun `rejects tokens that are too short or contain illegal characters`() {
        assertNull(QrPayloadCodec.decode("""{"sessionId":"$sessionId","token":"short"}"""))
        assertNull(QrPayloadCodec.decode("""{"sessionId":"$sessionId","token":"${token.dropLast(1)}+"}"""))
    }

    @Test
    fun `rejects session ids that could be used for path traversal`() {
        assertNull(QrPayloadCodec.decode("""{"sessionId":"../users/abc","token":"$token"}"""))
        assertNull(QrPayloadCodec.decode("""{"sessionId":"a/b","token":"$token"}"""))
    }

    @Test
    fun `rejects non attendance QR codes`() {
        assertNull(QrPayloadCodec.decode(null))
        assertNull(QrPayloadCodec.decode(""))
        assertNull(QrPayloadCodec.decode("https://example.com/?sessionId=abc"))
        assertNull(QrPayloadCodec.decode("WIFI:S:Campus;T:WPA;P:secret;;"))
        assertNull(QrPayloadCodec.decode("""{"sessionId": "abc", "token": """))
        assertNull(QrPayloadCodec.decode("""["$sessionId","$token"]"""))
    }

    @Test
    fun `rejects oversized payloads`() {
        val padding = "x".repeat(QrPayloadCodec.MAX_PAYLOAD_LENGTH)
        assertNull(QrPayloadCodec.decode("""{"sessionId":"$sessionId","token":"$token","pad":"$padding"}"""))
    }
}
