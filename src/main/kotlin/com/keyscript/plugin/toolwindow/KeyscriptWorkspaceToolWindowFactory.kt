package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.onboarding.GettingStartedPanel
import com.keyscript.plugin.onboarding.OnboardingStateService
import com.keyscript.plugin.services.KeyscriptProjectDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KeyscriptWorkspaceToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "KS Workspace"

        val contentManager = toolWindow.contentManager
        val factory = ContentFactory.getInstance()

        // Show Getting Started tab if onboarding is not complete
        val onboarding = OnboardingStateService.getInstance(project)
        if (!onboarding.isComplete || !onboarding.dismissed) {
            val panel = GettingStartedPanel(project)
            val gettingStarted = factory.createContent(
                panel.component,
                "Getting Started",
                false
            )
            gettingStarted.isCloseable = true
            gettingStarted.setDisposer(panel)
            contentManager.addContent(gettingStarted)
        }

        val optionsPanel = ScriptOptionsPanel(project, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val runOptions = factory.createContent(
            optionsPanel.component,
            WorkspaceTab.RUN_OPTIONS.title,
            false
        )
        runOptions.setDisposer(optionsPanel)
        contentManager.addContent(runOptions)

        val sessionPanel = SessionPanel(project)
        val session = factory.createContent(
            sessionPanel.component,
            WorkspaceTab.SESSION.title,
            false
        )
        session.setDisposer(sessionPanel)
        contentManager.addContent(session)
    }
}
