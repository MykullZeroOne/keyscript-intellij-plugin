package com.keyscript.plugin.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URI
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

/**
 * Manages JSESSIONID and login state for the Keystone session.
 * Credentials are stored via IntelliJ PasswordSafe (replaces Electron safeStorage).
 *
 * Session lifecycle:
 * - Monitors session validity with periodic heartbeat/keepalive checks
 * - Sends keepalive pings to both proxy session and API session
 * - Auto-relogins when session expires if saved credentials exist
 * - Notifies listeners (status bar, session panel) on state changes
 */
@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(SessionService::class.java)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var heartbeatTimer: Timer? = null
    private val reloginInProgress = AtomicBoolean(false)

    /** Tracks when the last successful keepalive/activity occurred */
    private val lastSuccessfulPing = AtomicLong(0L)

    /** Counts consecutive heartbeat failures to avoid single-failure false positives */
    @Volatile
    private var consecutiveFailures = 0

    /** JSESSIONID from proxy login — used for preview/run through the proxy */
    var jsessionId: String = ""
        private set
    /** Session ID from direct Keystone API login — used for deploy/search API calls */
    var keystoneApiSessionId: String = ""
        private set
    var username: String = ""
        private set
    var instance: String = ""
    var loginData: Map<String, String> = emptyMap()
        private set

    val isLoggedIn: Boolean get() = jsessionId.isNotEmpty()

    /**
     * Returns true if the current session is connected to a live/production database.
     * The convention is that live database names end with "LIV".
     */
    fun isLiveDatabase(): Boolean {
        val dbName = loginData["databaseName"] ?: return false
        return dbName.trim().uppercase().endsWith("LIV")
    }

    /** The session ID to use for direct Keystone API calls (falls back to proxy session) */
    val apiSessionId: String get() = keystoneApiSessionId.ifEmpty { jsessionId }

    fun setSession(jsessionId: String, username: String, loginData: Map<String, String> = emptyMap()) {
        this.jsessionId = jsessionId
        this.username = username
        this.loginData = loginData
        this.consecutiveFailures = 0
        this.lastSuccessfulPing.set(System.currentTimeMillis())
        log.info("Session established for $username: ${jsessionId.take(8)}...")
        notifyListeners()
        startHeartbeat()
    }

    fun setKeystoneApiSession(sessionId: String) {
        this.keystoneApiSessionId = sessionId
        log.info("Keystone API session established: ${sessionId.take(8)}...")
    }

    fun clearSession() {
        val wasLoggedIn = isLoggedIn
        jsessionId = ""
        keystoneApiSessionId = ""
        username = ""
        instance = ""
        loginData = emptyMap()
        consecutiveFailures = 0
        stopHeartbeat()
        notifyListeners()
        if (wasLoggedIn) {
            log.info("Session cleared")
        }
    }

    /**
     * Called when an API call detects the session has expired.
     * Attempts to re-login using saved credentials.
     */
    fun handleSessionExpired() {
        if (!reloginInProgress.compareAndSet(false, true)) {
            return // another relogin already in progress
        }

        log.info("Session expired, attempting re-login...")
        val savedInstance = instance
        val creds = loadCredentials()

        // Clear current session state so UI shows logged-out
        jsessionId = ""
        keystoneApiSessionId = ""
        consecutiveFailures = 0
        stopHeartbeat()
        notifyListeners()

        if (creds == null) {
            log.info("No saved credentials — user must login manually")
            notify("Session expired. Please login again.", NotificationType.WARNING)
            reloginInProgress.set(false)
            return
        }

        // Attempt re-login in background
        Thread({
            try {
                val authService = AuthenticationService.getInstance(project)
                val settings = KeyscriptSettings.getInstance()
                val result = runBlocking {
                    authService.login(
                        username = creds.first,
                        password = creds.second,
                        instance = savedInstance.ifEmpty { settings.getDefaultInstance() },
                        deviceId = settings.deviceServiceUrl,
                        deviceName = settings.deviceName
                    )
                }
                if (result.success) {
                    log.info("Re-login successful for ${result.userName}")
                    notify("Session restored for ${result.userName}", NotificationType.INFORMATION)
                } else {
                    log.info("Re-login failed: ${result.error}")
                    notify("Session expired. Re-login failed: ${result.error}", NotificationType.WARNING)
                }
            } catch (e: Exception) {
                log.warn("Re-login error", e)
                notify("Session expired. Re-login failed: ${e.message}", NotificationType.WARNING)
            } finally {
                reloginInProgress.set(false)
            }
        }, "keyscript-relogin").start()
    }

    /**
     * Record that an API call succeeded, resetting failure counter.
     * Called from KeystoneApiClient and DeploymentService on successful responses.
     */
    fun recordSuccessfulActivity() {
        consecutiveFailures = 0
        lastSuccessfulPing.set(System.currentTimeMillis())
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    // ─── Session heartbeat & keepalive ─────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer("keyscript-heartbeat", true).apply {
            // Send keepalive every 45 seconds — frequent enough to prevent
            // server-side session timeout (typically 2-5 minutes on Keystone)
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        keepAlive()
                    } catch (e: Exception) {
                        log.warn("Heartbeat task error", e)
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    /**
     * Sends keepalive pings to both the proxy session and the API session.
     * If both fail consecutively, triggers re-login.
     */
    private fun keepAlive() {
        if (!isLoggedIn) return

        val proxyAlive = keepAliveProxy()
        val apiAlive = keepAliveApi()

        if (proxyAlive || apiAlive) {
            consecutiveFailures = 0
            lastSuccessfulPing.set(System.currentTimeMillis())
        } else {
            consecutiveFailures++
            log.info("Keepalive failed (consecutive failures: $consecutiveFailures)")

            // Require 2 consecutive failures before declaring session expired,
            // to avoid false positives from transient network blips
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.info("Session appears expired after $consecutiveFailures consecutive keepalive failures")
                handleSessionExpired()
            }
        }
    }

    /**
     * Keepalive for the proxy/JSESSIONID session.
     * Sends a lightweight GET to the Keystone server via proxy endpoint.
     * Returns true if the session appears valid.
     */
    private fun keepAliveProxy(): Boolean {
        if (jsessionId.isEmpty()) return false

        return try {
            val settings = KeyscriptSettings.getInstance()
            val inst = instance.ifEmpty { settings.getDefaultInstance() }
            val proxyUrl = settings.getProxyUrl()
            val url = if (proxyUrl.startsWith("http")) {
                "$proxyUrl/$inst/UserLogin"
            } else {
                "https://$proxyUrl/$inst/UserLogin"
            }

            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Cookie", "JSESSIONID=$jsessionId")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = false

            val status = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            // 401/403 means session is definitely expired
            if (status == 401 || status == 403) {
                log.info("Proxy keepalive: session expired (HTTP $status)")
                return false
            }

            // Check for session indicators in response
            val hasSession = body.contains("JSESSIONID") || body.contains("userName") ||
                    body.contains("\"success\"")

            // If 200 but no session indicators, the session likely expired
            // and Keystone returned a login form instead
            if (status == 200 && !hasSession && body.contains("<form", ignoreCase = true)) {
                log.info("Proxy keepalive: got login form — session expired")
                return false
            }

            // 200 with session data, or 302 redirect (still has session) = OK
            log.debug("Proxy keepalive OK (status=$status)")
            true
        } catch (e: Exception) {
            // Network error — don't treat as expired, could be transient
            log.info("Proxy keepalive network error: ${e.message}")
            true // assume OK on network errors (counted separately)
        }
    }

    /**
     * Keepalive for the direct Keystone API session.
     * Sends a lightweight "view" query that touches the session without side effects.
     * Returns true if the session appears valid.
     */
    private fun keepAliveApi(): Boolean {
        val apiSid = keystoneApiSessionId
        if (apiSid.isEmpty()) return false

        return try {
            val settings = KeyscriptSettings.getInstance()
            val inst = instance.ifEmpty { settings.getDefaultInstance() }
            val url = "${settings.getKeystoneApiBaseUrl()}/$inst"

            // Lightweight query — just a search with minimal results to keep session alive
            val keepAliveJson = """{"query":{"\u0024attr":{"sessionId":"$apiSid"},"sequence":{"transaction":{"step":{"search":{"tableName":"SCRIPT","filterName":"BY_DESCRIPTION","returnLimit":1,"parameter":{"columnName":"DESCRIPTION","contents":"__keepalive__"}}}}}}}"""

            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Cookie", "JSESSIONID=$apiSid")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.outputStream.use { it.write(keepAliveJson.toByteArray()) }

            val status = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            if (status == 401 || status == 403) {
                log.info("API keepalive: session expired (HTTP $status)")
                return false
            }

            // Check for session expiry indicators in response body
            val lower = body.lowercase()
            if (lower.contains("session") && (lower.contains("expired") || lower.contains("invalid"))) {
                log.info("API keepalive: session expired (response body)")
                return false
            }

            log.debug("API keepalive OK (status=$status)")
            true
        } catch (e: Exception) {
            log.info("API keepalive network error: ${e.message}")
            true // assume OK on network errors
        }
    }

    // ─── Credential Persistence via PasswordSafe ──────────────────

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName("Keyscript", "KeystoneLogin"))

    fun saveCredentials(user: String, password: String) {
        PasswordSafe.instance.set(credentialAttributes(), Credentials(user, password))
    }

    fun loadCredentials(): Pair<String, String>? {
        val cred = PasswordSafe.instance.get(credentialAttributes()) ?: return null
        val user = cred.userName ?: return null
        val pass = cred.getPasswordAsString() ?: return null
        return user to pass
    }

    fun clearCredentials() {
        PasswordSafe.instance.set(credentialAttributes(), null)
    }

    private fun notifyListeners() {
        val notify = Runnable { listeners.forEach { it() } }
        if (SwingUtilities.isEventDispatchThread()) {
            notify.run()
        } else {
            SwingUtilities.invokeLater(notify)
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript", message, type)
            .notify(project)
    }

    override fun dispose() {
        stopHeartbeat()
    }

    companion object {
        /** Keepalive ping every 45 seconds — well within typical Keystone session TTL */
        private const val HEARTBEAT_INTERVAL_MS = 45L * 1000

        /** Number of consecutive failures before treating session as expired */
        private const val MAX_CONSECUTIVE_FAILURES = 2

        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)
    }
}
