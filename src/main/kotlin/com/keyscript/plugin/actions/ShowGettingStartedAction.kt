package com.keyscript.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.keyscript.plugin.onboarding.OnboardingStateService
import com.keyscript.plugin.onboarding.WelcomeDialog
import com.keyscript.plugin.services.KeyscriptProjectDetector

class ShowGettingStartedAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            KeyscriptProjectDetector.isKeyscriptProject(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        OnboardingStateService.getInstance(project).dismissed = false
        WelcomeDialog(project).show()
    }
}
