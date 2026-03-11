package com.keyscript.plugin.actions

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.keyscript.plugin.services.KeyscriptFileSupport
import com.keyscript.plugin.services.RunKeyscriptService
import kotlinx.coroutines.runBlocking

/**
 * Opens the current script's preview in the default web browser using the same
 * prepared preview URL that the split editor uses.
 */
class OpenInChromeAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            file != null &&
            KeyscriptFileSupport.isKeyscriptFile(file)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Opening Keyscript Preview...") {
            override fun run(indicator: ProgressIndicator) {
                val service = RunKeyscriptService.getInstance(project)
                service.primePreviewOverride(file)
                val scriptPath = RunKeyscriptService.resolveScriptPath(project, file)

                runBlocking {
                    val result = service.preparePreview(scriptPath)
                    if (result.success && result.url != null) {
                        BrowserUtil.browse(result.url)
                    } else {
                        notify(project, result.error ?: "Unable to open browser preview", NotificationType.WARNING)
                    }
                }
            }
        })
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript", message, type)
            .notify(project)
    }
}
