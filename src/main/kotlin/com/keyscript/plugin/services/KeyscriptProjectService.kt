package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import com.keyscript.plugin.onboarding.OnboardingStateService
import com.keyscript.plugin.onboarding.WelcomeDialog
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.runBlocking
import javax.swing.SwingUtilities

/**
 * Lightweight startup: sets project path, updates status bar, triggers onboarding,
 * and auto-logins if credentials are already configured.
 */
@Service(Service.Level.PROJECT)
class KeyscriptProjectService(private val project: Project) {
    private val log = Logger.getInstance(KeyscriptProjectService::class.java)

    suspend fun initialize() {
        log.info("Keyscript IDE initializing for project: ${project.name}")

        // Just record the project path — proxy starts on demand
        ProxyServerService.getInstance(project).activeProjectPath = project.basePath ?: ""

        // Update status bar widget
        SwingUtilities.invokeLater {
            WindowManager.getInstance().getStatusBar(project)?.updateWidget("KeyscriptLoginStatus")
        }

        // Show welcome wizard for first-time users
        val onboarding = OnboardingStateService.getInstance(project)
        if (onboarding.shouldShowWelcome) {
            SwingUtilities.invokeLater {
                WelcomeDialog(project).show()
            }
        }

        // Auto-login if all credentials and device info are already configured
        attemptAutoLogin()
    }

    private fun attemptAutoLogin() {
        val session = SessionService.getInstance(project)
        if (session.isLoggedIn) return

        val settings = KeyscriptSettings.getInstance()
        val creds = session.loadCredentials()

        val hasCredentials = creds != null && creds.first.isNotBlank() && creds.second.isNotBlank()
        val hasDeviceInfo = settings.deviceServiceUrl.isNotBlank() && settings.deviceName.isNotBlank()
        val hasServer = settings.proxyEndpoint.isNotBlank()

        if (!hasCredentials || !hasDeviceInfo || !hasServer) {
            log.info("Auto-login skipped: missing config (creds=$hasCredentials, device=$hasDeviceInfo, server=$hasServer)")
            return
        }

        log.info("Auto-login: credentials available, logging in on project open")
        Thread({
            try {
                val result = runBlocking {
                    AuthenticationService.getInstance(project).login(
                        username = creds!!.first,
                        password = creds.second,
                        instance = settings.getDefaultInstance(),
                        deviceId = settings.deviceServiceUrl,
                        deviceName = settings.deviceName
                    )
                }
                if (result.success) {
                    log.info("Auto-login successful for ${result.userName}")
                } else {
                    log.info("Auto-login failed: ${result.error}")
                }
            } catch (e: Exception) {
                log.warn("Auto-login error", e)
            }
        }, "keyscript-auto-login-startup").start()
    }

    class StartupActivity : ProjectActivity {
        override suspend fun execute(project: Project) {
            if (!KeyscriptProjectDetector.isKeyscriptProject(project)) return
            getInstance(project).initialize()
        }
    }

    companion object {
        fun getInstance(project: Project): KeyscriptProjectService =
            project.getService(KeyscriptProjectService::class.java)
    }
}
