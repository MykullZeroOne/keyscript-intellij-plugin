package com.revfcu.keyscript.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class KeybridgeClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: KeybridgeClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = KeybridgeClient(server.url("/Development").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `logon success returns sessionId`() {
        val jsonResponse = """
            {
                "result": {
                    "logon": {
                        "sessionId": "fake-session-123"
                    }
                }
            }
        """.trimIndent()
        
        server.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val sessionId = client.logon("user", "device", "pass")
        
        assertEquals("fake-session-123", sessionId)
        
        val recordedRequest = server.takeRequest()
        assertEquals("/Development", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"))
    }

    @Test
    fun `logon with error returns IOException`() {
        val jsonResponse = """
            {
                "error": {
                    "message": "Invalid credentials",
                    "code": 401
                }
            }
        """.trimIndent()
        
        server.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val exception = assertThrows(IOException::class.java) {
            client.logon("user", "device", "pass")
        }
        
        assertEquals("Keybridge API Error: Invalid credentials", exception.message)
    }

    @Test
    fun `logon server error returns IOException`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(IOException::class.java) {
            client.logon("user", "device", "pass")
        }
    }
}
