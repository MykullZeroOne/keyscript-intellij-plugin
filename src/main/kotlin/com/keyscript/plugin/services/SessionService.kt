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

    /** JSESSIONID from proxy login — used for all Keystone API calls */
    var jsessionId: String = ""
        private set
    var username: String = ""
        private set
    var instance: String = ""
        set(value) {
            field = value
            notifyListeners()
        }

    var deviceId: String = ""
        set(value) {
            field = value
            notifyListeners()
        }

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

    /** The session ID used for all Keystone API calls (through proxy or direct) */
    val apiSessionId: String get() = jsessionId

    fun setSession(jsessionId: String, username: String, loginData: Map<String, String> = emptyMap()) {
        this.jsessionId = jsessionId
        this.username = username
        this.loginData = loginData
        this.consecutiveFailures = 0
        this.lastSuccessfulPing.set(System.currentTimeMillis())
        log.info("Session established for $username")
        notifyListeners()
        startHeartbeat()
    }

    fun clearSession() {
        val wasLoggedIn = isLoggedIn
        jsessionId = ""
        username = ""
        instance = ""
        deviceId = ""
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
                        deviceId = settings.deviceServiceUrl
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
     * Sends keepalive ping through the proxy to keep the session alive.
     * If it fails consecutively, triggers re-login.
     */
    private fun keepAlive() {
        if (!isLoggedIn) return

        val alive = keepAliveProxy()

        if (alive) {
            consecutiveFailures = 0
            lastSuccessfulPing.set(System.currentTimeMillis())
        } else {
            consecutiveFailures++
            log.info("Keepalive failed (consecutive failures: $consecutiveFailures)")

            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                log.info("Session appears expired after $consecutiveFailures consecutive keepalive failures")
                handleSessionExpired()
            }
        }
    }

    /**
     * Keepalive for the JSESSIONID session via the proxy.
     * Returns true if the session appears valid.
     */
    private fun keepAliveProxy(): Boolean {
        if (jsessionId.isEmpty()) return false

        return try {
            val settings = KeyscriptSettings.getInstance()
            val proxyBase = "http://localhost:${settings.proxyPort}"
            val inst = instance.ifEmpty { settings.getDefaultInstance() }
            val url = "$proxyBase/$inst/UserLogin"

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

            if (status == 401 || status == 403) {
                log.info("Proxy keepalive: session expired (HTTP $status)")
                return false
            }

            // If we got a login form back, session is expired
            if (status == 200 && body.contains("<form", ignoreCase = true)) {
                log.info("Proxy keepalive: got login form — session expired")
                return false
            }

            log.debug("Proxy keepalive OK (status=$status)")
            true
        } catch (e: Exception) {
            log.info("Proxy keepalive network error: ${e.message}")
            true // assume OK on network errors (could be transient)
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
        /** Keepalive ping every 45 seconds */
        private const val HEARTBEAT_INTERVAL_MS = 45L * 1000

        /** Number of consecutive failures before treating session as expired */
        private const val MAX_CONSECUTIVE_FAILURES = 2

        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)
    }
}
