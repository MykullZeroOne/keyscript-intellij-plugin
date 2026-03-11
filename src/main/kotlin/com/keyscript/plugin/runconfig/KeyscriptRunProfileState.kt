package com.keyscript.plugin.runconfig

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.keyscript.plugin.services.RunKeyscriptService
import kotlinx.coroutines.runBlocking

class KeyscriptRunProfileState(
    private val environment: ExecutionEnvironment,
    private val config: KeyscriptRunConfiguration
) : RunProfileState {

    override fun execute(executor: Executor?, runner: ProgramRunner<*>): ExecutionResult? {
        val project = environment.project

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
