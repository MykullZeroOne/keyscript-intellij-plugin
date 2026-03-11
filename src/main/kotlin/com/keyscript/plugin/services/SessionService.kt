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
import javax.swing.SwingUtilities

/**
 * Manages JSESSIONID and login state for the Keystone session.
 * Credentials are stored via IntelliJ PasswordSafe (replaces Electron safeStorage).
 *
 * Session lifecycle:
 * - Monitors session validity with periodic heartbeat checks
 * - Auto-relogins when session expires if saved credentials exist
 * - Notifies listeners (status bar, session panel) on state changes
 */
@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(SessionService::class.java)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var heartbeatTimer: Timer? = null
    private val reloginInProgress = AtomicBoolean(false)

    /** JSESSIONID from proxy login — used for preview/run through the proxy */
    var jsessionId: String = ""
        private set
    /** JSESSIONID from direct Keystone API login — used for deploy/search API calls */
    var keystoneApiSessionId: String = ""
        private set
    var username: String = ""
        private set
    var instance: String = ""
    var loginData: Map<String, String> = emptyMap()
        private set

    val isLoggedIn: Boolean get() = jsessionId.isNotEmpty()

    /** The session ID to use for direct Keystone API calls (falls back to proxy session) */
    val apiSessionId: String get() = keystoneApiSessionId.ifEmpty { jsessionId }

    fun setSession(jsessionId: String, username: String, loginData: Map<String, String> = emptyMap()) {
        this.jsessionId = jsessionId
        this.username = username
        this.loginData = loginData
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

        // Clear current session state first so UI shows logged-out
        val oldJsessionId = jsessionId
        jsessionId = ""
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
                val result = runBlocking {
                    authService.login(creds.first, creds.second, savedInstance)
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

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    // ─── Session heartbeat ─────────────────────────────

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer("keyscript-heartbeat", true).apply {
            // Check session every 2 minutes
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    checkSessionValid()
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    /**
     * Lightweight session check — sends a GET to UserLogin on Keystone.
     * If the session is invalid, Keystone returns an error or redirect.
     */
    private fun checkSessionValid() {
        if (!isLoggedIn) return

        try {
            val settings = KeyscriptSettings.getInstance()
            val inst = instance.ifEmpty { settings.getDefaultInstance() }
            val baseUrl = settings.getProxyUrl()
            val url = if (baseUrl.startsWith("http")) "$baseUrl/$inst/UserLogin" else "https://$baseUrl/$inst/UserLogin"

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

            // Check if session is still valid
            val hasJsession = body.contains("JSESSIONID") || body.contains("userName")
            if (status == 401 || status == 403 || (!hasJsession && status != 200)) {
                log.info("Heartbeat: session appears expired (status=$status)")
                handleSessionExpired()
            }
        } catch (e: Exception) {
            // Network error — don't treat as expired, just log
            log.info("Heartbeat check failed (network): ${e.message}")
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
        private const val HEARTBEAT_INTERVAL_MS = 2L * 60 * 1000 // 2 minutes

        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)
    }
}
