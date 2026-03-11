package com.keyscript.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
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

class RunKeyscriptAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running Keyscript...") {
            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    val result = RunKeyscriptService.getInstance(project).runScript(file)
                    if (!result.success) {
                        val type = if (result.error?.contains("login", ignoreCase = true) == true) {
                            NotificationType.WARNING
                        } else {
                            NotificationType.ERROR
                        }
                        notify(project, result.error ?: "Run failed", type)
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
