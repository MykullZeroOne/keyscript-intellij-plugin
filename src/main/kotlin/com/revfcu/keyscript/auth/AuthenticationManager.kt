package com.revfcu.keyscript.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AuthenticationManager(private val project: Project) {
    private var sessionId: String? = null
    private var serverUrl: String? = null
    private var userName: String? = null
    private var deviceName: String? = null

    fun setSessionId(id: String?) {
        sessionId = id
    }

    fun getSessionId(): String? = sessionId

    fun setServerUrl(url: String?) {
        serverUrl = url
    }

    fun getServerUrl(): String? = serverUrl

    fun setUserName(name: String?) {
        userName = name
    }

    fun getUserName(): String? = userName

    fun setDeviceName(name: String?) {
        deviceName = name
    }

    fun getDeviceName(): String? = deviceName

    fun isLoggedIn(): Boolean = sessionId != null
}
