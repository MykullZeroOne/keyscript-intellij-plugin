package com.keyscript.plugin.runconfig

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ui.Messages
import com.keyscript.plugin.services.RunKeyscriptService
import com.keyscript.plugin.services.SessionService
import kotlinx.coroutines.runBlocking
import javax.swing.SwingUtilities
import java.util.concurrent.atomic.AtomicBoolean

class KeyscriptRunProfileState(
    private val environment: ExecutionEnvironment,
    private val config: KeyscriptRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
        val project = environment.project

        val session = SessionService.getInstance(project)
        if (session.isLiveDatabase()) {
            val dbName = session.loginData["databaseName"] ?: "unknown"
            val proceed = AtomicBoolean(false)
            SwingUtilities.invokeAndWait {
                val answer = Messages.showYesNoDialog(
                    project,
                    "WARNING: You are connected to a LIVE database ($dbName).\n\nAre you sure you want to run this script?",
                    "Live Database Warning",
                    Messages.getWarningIcon()
                )
                proceed.set(answer == Messages.YES)
            }
            if (!proceed.get()) return null
        }

        runBlocking {
            val result = RunKeyscriptService.getInstance(project).runScript(config.scriptPath)
            if (!result.success) {
                notify(result.error ?: "Run failed")
            }
        }

        return null
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript", message, NotificationType.WARNING)
            .notify(environment.project)
    }
}
