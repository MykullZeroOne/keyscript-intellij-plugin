package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.onboarding.GettingStartedPanel
import com.keyscript.plugin.onboarding.OnboardingStateService
import com.keyscript.plugin.services.KeyscriptProjectDetector

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
            val gettingStarted = factory.createContent(
                GettingStartedPanel(project).component,
                "Getting Started",
                false
            )
            gettingStarted.isCloseable = true
            contentManager.addContent(gettingStarted)
        }

        val runOptions = factory.createContent(
            ScriptOptionsPanel(project).component,
            WorkspaceTab.RUN_OPTIONS.title,
            false
        )
        contentManager.addContent(runOptions)

        val session = factory.createContent(
            SessionPanel(project).component,
            WorkspaceTab.SESSION.title,
            false
        )
        contentManager.addContent(session)
    }
}
