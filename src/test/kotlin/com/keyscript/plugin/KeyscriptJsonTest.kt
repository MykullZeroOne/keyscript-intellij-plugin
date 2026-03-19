package com.keyscript.plugin

import org.junit.Assert.*
import org.junit.Test

class KeyscriptJsonTest {

    @Test
    fun `mapper is singleton instance`() {
        val a = KeyscriptJson.mapper
        val b = KeyscriptJson.mapper
        assertSame(a, b)
    }

    @Test
    fun `mapper serializes map to JSON`() {
        val json = KeyscriptJson.mapper.writeValueAsString(mapOf("key" to "value"))
        assertEquals("""{"key":"value"}""", json)
    }

    @Test
    fun `mapper deserializes JSON to map`() {
        val map = KeyscriptJson.mapper.readValue("""{"key":"value"}""", Map::class.java)
        assertEquals("value", map["key"])
    }

    @Test
    fun `mapper handles special characters in values`() {
        val json = KeyscriptJson.mapper.writeValueAsString(mapOf("deviceId" to """MAC: "test" & <special>"""))
        assertTrue(json.contains("MAC:"))
        // Round-trip
        val map = KeyscriptJson.mapper.readValue(json, Map::class.java)
        assertEquals("""MAC: "test" & <special>""", map["deviceId"])
    }

    @Test
    fun `mapper handles nested objects`() {
        val data = mapOf(
            "crlogin" to mapOf("JSESSIONID" to "abc123", "instance" to "Test"),
            "crscript" to emptyMap<String, String>()
        )
        val json = KeyscriptJson.mapper.writeValueAsString(data)
        assertTrue(json.contains("JSESSIONID"))
        assertTrue(json.contains("abc123"))
    }
}
