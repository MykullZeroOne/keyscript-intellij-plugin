package com.keyscript.plugin.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.keyscript.plugin.settings.KeyscriptSettings
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.net.ssl.X509TrustManager

/**
 * Handles authentication with Keystone via the local proxy.
 * All API calls go through the proxy for consistent session management.
 */
@Service(Service.Level.PROJECT)
class AuthenticationService(private val project: Project) {
    private val log = Logger.getInstance(AuthenticationService::class.java)
    private val mapper = jacksonObjectMapper()

    private val httpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }
            }
        }
    }

    /**
     * Login to Keystone via the proxy's /UserLogin endpoint.
     * The proxy captures the JSESSIONID and uses it for all subsequent requests.
     */
    suspend fun login(
        username: String,
        password: String,
        instance: String,
        deviceId: String = ""
    ): LoginResult = withContext(Dispatchers.IO) {
        try {
            val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()

            // 1. Set instance on proxy
            httpClient.get("$proxyBase/$instance")

            // 2. Send device identifier to proxy
            if (deviceId.isNotBlank()) {
                httpClient.post("$proxyBase/api/device-id") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"deviceId":"${deviceId.replace("\"", "\\\"")}"}""")
                }
            }

            // 3. POST /UserLogin through proxy
            val body = listOf(
                "loginUsername" to username,
                "loginPassword" to password,
                "loginDeviceIdentifier" to deviceId,
                "loginDeviceInsertOption" to "N"
            ).joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

            val response = httpClient.post("$proxyBase/UserLogin") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(body)
            }

            val responseBody = response.bodyAsText()
            val logSafeBody = responseBody.take(500).replace(Regex("\"loginPassword\":\\s*\"[^\"]*\""), "\"loginPassword\":\"***\"")
            log.info("Login response status=${response.status}: $logSafeBody")

            // 4. Parse JSON response (proxy injects JSESSIONID into body)
            val responseMap = try {
                mapper.readValue<Map<String, Any>>(responseBody)
            } catch (e: Exception) {
                log.warn("Failed to parse login response JSON", e)
                emptyMap()
            }

            val jsessionId = responseMap["JSESSIONID"] as? String
            val success = responseMap["success"] == true || jsessionId != null

            if (success && jsessionId != null) {
                val loginData = responseMap.filterValues { it is String } as Map<String, String>
                val userName = responseMap["userName"] as? String ?: username
                val session = SessionService.getInstance(project)
                session.setSession(jsessionId, userName, loginData)
                session.instance = instance

                // Tell proxy about the session
                ProxyServerService.getInstance(project).setSsoSession(jsessionId)
                try {
                    httpClient.post("$proxyBase/api/sso-session") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"jsessionId":"$jsessionId"}""")
                    }
                } catch (_: Exception) {}

                notify("Logged in as $userName ($instance)", NotificationType.INFORMATION)
                LoginResult(true, userName)
            } else {
                val exception = responseMap["exception"] as? String
                val errorMsg = exception ?: "Login failed — no JSESSIONID in response"
                notify(errorMsg, NotificationType.ERROR)
                LoginResult(false, error = errorMsg)
            }
        } catch (e: Exception) {
            log.error("Login failed", e)
            val errorMsg = "Connection failed: ${e.message}"
            notify(errorMsg, NotificationType.ERROR)
            LoginResult(false, error = errorMsg)
        }
    }

    /**
     * Attempt Kerberos SSO login via GET /UserLogin through the proxy.
     */
    suspend fun attemptSsoLogin(): LoginResult = withContext(Dispatchers.IO) {
        try {
            val settings = KeyscriptSettings.getInstance()
            val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
            val inst = settings.getDefaultInstance()

            httpClient.get("$proxyBase/$inst")

            val response = httpClient.get("$proxyBase/UserLogin")
            val body = response.bodyAsText()

            val responseMap = try {
                mapper.readValue<Map<String, Any>>(body)
            } catch (e: Exception) {
                emptyMap()
            }

            val jsessionId = responseMap["JSESSIONID"] as? String

            if (jsessionId != null) {
                val loginData = responseMap.filterValues { it is String } as Map<String, String>
                val userName = responseMap["userName"] as? String ?: "sso-user"
                val session = SessionService.getInstance(project)
                session.setSession(jsessionId, userName, loginData)
                session.instance = inst

                ProxyServerService.getInstance(project).setSsoSession(jsessionId)
                try {
                    httpClient.post("$proxyBase/api/sso-session") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"jsessionId":"$jsessionId"}""")
                    }
                } catch (_: Exception) {}

                notify("SSO login successful: $userName ($inst)", NotificationType.INFORMATION)
                LoginResult(true, userName)
            } else {
                LoginResult(false, error = "SSO not available")
            }
        } catch (e: Exception) {
            log.info("SSO login not available: ${e.message}")
            LoginResult(false, error = e.message)
        }
    }

    data class LoginResult(
        val success: Boolean,
        val userName: String? = null,
        val error: String? = null
    )

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript", message, type)
            .notify(project)
    }

    companion object {
        fun getInstance(project: Project): AuthenticationService =
            project.getService(AuthenticationService::class.java)
    }
}
