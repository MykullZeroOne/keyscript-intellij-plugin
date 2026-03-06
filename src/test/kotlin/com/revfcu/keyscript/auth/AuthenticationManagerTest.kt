package com.revfcu.keyscript.auth

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AuthenticationManagerTest {

    @Test
    fun testSessionIdStorage() {
        val project = mock(Project::class.java)
        val authManager = AuthenticationManager(project)
        
        assertNull(authManager.getSessionId())
        assertFalse(authManager.isLoggedIn())
        
        authManager.setSessionId("test-session")
        assertEquals("test-session", authManager.getSessionId())
        assertTrue(authManager.isLoggedIn())
        
        authManager.setSessionId(null)
        assertNull(authManager.getSessionId())
        assertFalse(authManager.isLoggedIn())
    }

    @Test
    fun testServerUrlStorage() {
        val project = mock(Project::class.java)
        val authManager = AuthenticationManager(project)
        
        assertNull(authManager.getServerUrl())
        
        authManager.setServerUrl("http://localhost:8080")
        assertEquals("http://localhost:8080", authManager.getServerUrl())
    }
}
