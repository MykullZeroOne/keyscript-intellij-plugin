package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import javax.swing.SwingUtilities

/**
 * Lightweight startup: just sets the project path and updates the status bar.
 * The proxy server starts lazily on first actual use (login, run, search).
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
