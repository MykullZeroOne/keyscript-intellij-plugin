package com.keyscript.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.AuthenticationService
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.*

class LoginAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val session = SessionService.getInstance(project)

        if (session.isLoggedIn) {
            // Already logged in — confirm logout
            val choice = Messages.showYesNoDialog(
                project,
                "Logged in as ${session.username} (${session.instance}).\nDo you want to log out?",
                "Keyscript Session",
                Messages.getQuestionIcon()
            )
            if (choice == Messages.YES) {
                session.clearSession()
                ProxyServerService.getInstance(project).setSsoSession("")
            }
            return
        }

        // Load credentials off-EDT (PasswordSafe is a slow operation)
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val creds = session.loadCredentials()
            javax.swing.SwingUtilities.invokeLater {
                val dialog = LoginDialog(project, creds)
                dialog.show()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible =
            com.keyscript.plugin.services.KeyscriptProjectDetector.isKeyscriptProject(project)
        if (e.presentation.isVisible) {
            val session = SessionService.getInstance(project)
            e.presentation.text = if (session.isLoggedIn) {
                "Logged in: ${session.username} (${session.instance})"
            } else {
                "Login to Keystone..."
            }
        }
    }
}

/**
 * Login dialog matching the original Keyscript IDE flow:
 * - Shows Keystone server (read-only)
 * - Instance dropdown (from settings)
 * - Device Identifier field
 * - Username & Password
 * - Error display area
 * - Attempts login through the proxy (not directly to Keystone)
 */
private class LoginDialog(
    private val project: Project,
    private val savedCreds: Pair<String, String>?
) : DialogWrapper(project) {
    private val settings = KeyscriptSettings.getInstance()
    private val session = SessionService.getInstance(project)

    // Server display (read-only)
    private val serverField = JBTextField(settings.proxyEndpoint).apply {
        isEditable = false
        background = UIManager.getColor("TextField.disabledBackground")
    }

    // Instance dropdown populated from settings
    private val instanceCombo = JComboBox(settings.supportedInstances.toTypedArray()).apply {
        isEditable = false
        selectedItem = settings.getDefaultInstance()
    }

    // Device ID — persisted in settings (used for proxy login)
    private val deviceIdField = JBTextField(settings.deviceServiceUrl).apply {
        toolTipText = "e.g. MAC: AA-BB-CC-DD-EE-FF"
    }

    // Credentials
    private val usernameField = JBTextField(savedCreds?.first ?: "").apply {
        columns = 20
    }
    private val passwordField = JBPasswordField().apply {
        savedCreds?.second?.let { text = it }
        columns = 20
    }

    // Error display
    private val errorLabel = JBLabel("").apply {
        foreground = Color(0xE5, 0x6B, 0x6B) // Red/orange for errors
        isVisible = false
        border = JBUI.Borders.empty(4, 0)
    }

    // Loading state
    private val statusLabel = JBLabel("").apply {
        isVisible = false
        border = JBUI.Borders.empty(4, 0)
    }

    init {
        title = "Keystone Login"
        setOKButtonText("Login")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Keystone Server:"), serverField, 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Instance:"), instanceCombo, 1, false)
            .addLabeledComponent(JBLabel("Device ID:"), deviceIdField, 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Username:"), usernameField, 1, false)
            .addLabeledComponent(JBLabel("Password:"), passwordField, 1, false)
            .panel

        val bottomPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(errorLabel)
            add(statusLabel)
        }

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(8)
            minimumSize = Dimension(400, 250)
            preferredSize = Dimension(420, 280)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (usernameField.text.isNotEmpty()) passwordField else usernameField

    override fun doValidate(): ValidationInfo? {
        if (usernameField.text.isBlank()) return ValidationInfo("Username is required", usernameField)
        if (passwordField.password.isEmpty()) return ValidationInfo("Password is required", passwordField)
        return null
    }

    override fun doOKAction() {
        val username = usernameField.text.trim()
        val password = String(passwordField.password)
        val instance = instanceCombo.selectedItem?.toString() ?: settings.getDefaultInstance()
        val deviceId = deviceIdField.text.trim()

        // Save device ID for next time
        if (deviceId.isNotBlank()) {
            settings.deviceServiceUrl = deviceId
        }

        // Show loading state
        errorLabel.isVisible = false
        statusLabel.text = "Logging in..."
        statusLabel.isVisible = true
        isOKActionEnabled = false

        // Run login in background
        SwingWorker.execute {
            val result = try {
                runBlocking {
                    AuthenticationService.getInstance(project).login(username, password, instance, deviceId)
                }
            } catch (e: Exception) {
                AuthenticationService.LoginResult(false, error = "Login failed: ${e.message}")
            }

            if (result.success) {
                // PasswordSafe writes are slow and must stay off the EDT.
                session.saveCredentials(username, password)
            }

            SwingUtilities.invokeLater {
                if (result.success) {
                    statusLabel.text = "Login successful — ${result.userName}"
                    statusLabel.foreground = java.awt.Color(0x4E, 0xC9, 0xB0)
                    statusLabel.isVisible = true
                    // Close dialog directly — don't use super.doOKAction() which re-validates
                    close(OK_EXIT_CODE)
                } else {
                    errorLabel.text = result.error ?: "Login failed"
                    errorLabel.isVisible = true
                    statusLabel.isVisible = false
                    isOKActionEnabled = true
                }
            }
        }
    }
}

/**
 * Simple background executor to avoid blocking the EDT.
 */
private object SwingWorker {
    fun execute(block: () -> Unit) {
        Thread(block, "keyscript-login").start()
    }
}
