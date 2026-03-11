package com.keyscript.plugin.services

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Manages JSESSIONID and login state for the Keystone session.
 * Credentials are stored via IntelliJ PasswordSafe (replaces Electron safeStorage).
 */
@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) {
    private val log = Logger.getInstance(SessionService::class.java)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var jsessionId: String = ""
        private set
    var username: String = ""
        private set
    var instance: String = ""
    var loginData: Map<String, String> = emptyMap()
        private set

    val isLoggedIn: Boolean get() = jsessionId.isNotEmpty()

    fun setSession(jsessionId: String, username: String, loginData: Map<String, String> = emptyMap()) {
        this.jsessionId = jsessionId
        this.username = username
        this.loginData = loginData
        log.info("Session established for $username: ${jsessionId.take(8)}...")
        notifyListeners()
    }

    fun clearSession() {
        jsessionId = ""
        username = ""
        instance = ""
        loginData = emptyMap()
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
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

    companion object {
        fun getInstance(project: Project): SessionService =
            project.getService(SessionService::class.java)
    }
}
