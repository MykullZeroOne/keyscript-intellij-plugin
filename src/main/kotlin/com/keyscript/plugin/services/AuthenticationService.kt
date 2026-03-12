package com.keyscript.plugin.services

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
import javax.net.ssl.X509TrustManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Handles authentication with Keystone.
 * - UI login goes through the local proxy (needed for preview/run)
 * - Also performs a JSON "logon" API call directly to Keystone to get
 *   an API session ID for deploy/search operations
 */
@Service(Service.Level.PROJECT)
class AuthenticationService(private val project: Project) {
    private val log = Logger.getInstance(AuthenticationService::class.java)

    private val httpClient = HttpClient(CIO) {
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            }
        }
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    /**
     * Login to Keystone via the proxy's /UserLogin endpoint (for preview/run),
     * then also obtain a direct API session via JSON logon (for deploy/search).
     */
    suspend fun login(
        username: String,
        password: String,
        instance: String,
        deviceId: String = "",
        deviceName: String = ""
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

            // 3. POST /UserLogin through proxy for preview/run session
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
            log.info("Login response status=${response.status}: ${responseBody.take(200)}")

            // 4. Parse JSON response
            val jsessionId = extractJsonField(responseBody, "JSESSIONID")
            val success = responseBody.contains(""""success":true""") ||
                    responseBody.contains(""""success": true""") ||
                    jsessionId != null

            if (success && jsessionId != null) {
                val loginData = parseLoginResponse(responseBody)
                val userName = extractJsonField(responseBody, "userName") ?: username
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

                // 5. Obtain API session via JSON logon for deploy/search
                obtainApiSession(username, password, instance, deviceName)

                notify("Logged in as $userName ($instance)", NotificationType.INFORMATION)
                LoginResult(true, userName)
            } else {
                val exception = extractJsonField(responseBody, "exception")
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
     * Send a JSON "logon" query directly to the Keystone API to obtain
     * a session ID that works for direct API calls (deploy, search).
     *
     * Uses the logon block instead of $attr:
     * { "query": { "logon": { "userName": "...", "deviceName": "...", "password": "..." } } }
     */
    private fun obtainApiSession(username: String, password: String, instance: String, deviceName: String) {
        try {
            val settings = KeyscriptSettings.getInstance()
            val apiBase = settings.getKeystoneApiBaseUrl()
            val url = "$apiBase/$instance"

            // Use device name from login dialog; fall back to saved setting
            val device = deviceName.ifEmpty { settings.deviceName }
            val logonJson = """{"query":{"logon":{"userName":"$username","deviceName":"$device","password":"${escapeJson(password)}"}}}"""

            // Log the request (mask password)
            val logSafeJson = """{"query":{"logon":{"userName":"$username","deviceName":"$device","password":"***"}}}"""
            log.info("API logon request: POST $url body=$logSafeJson")

            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.outputStream.use { it.write(logonJson.toByteArray()) }

            val status = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            log.info("API logon response: status=$status, body=${responseBody.take(500)}")

            // Extract sessionId from the response
            val apiSessionId = extractJsonField(responseBody, "sessionId")
                ?: extractJsonField(responseBody, "JSESSIONID")

            if (apiSessionId != null) {
                SessionService.getInstance(project).setKeystoneApiSession(apiSessionId)
                log.info("API session obtained via logon: ${apiSessionId.take(8)}...")
            } else {
                log.warn("API logon did not return sessionId. Response: ${responseBody.take(300)}")
            }
        } catch (e: Exception) {
            log.warn("API logon failed (deploy may not work): ${e.message}")
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
            val jsessionId = extractJsonField(body, "JSESSIONID")

            if (jsessionId != null) {
                val loginData = parseLoginResponse(body)
                val userName = extractJsonField(body, "userName") ?: "sso-user"
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

                // Also obtain API session for SSO users
                val creds = session.loadCredentials()
                if (creds != null) {
                    obtainApiSession(creds.first, creds.second, inst, "")
                }

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

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun parseLoginResponse(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = """"(\w+)"\s*:\s*"([^"]*)"""".toRegex()
        pattern.findAll(json).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

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
