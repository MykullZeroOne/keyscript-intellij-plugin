package com.revfcu.keyscript.actions

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
import com.intellij.openapi.vfs.VirtualFile
import com.revfcu.keyscript.api.KeystoneClient
import com.revfcu.keyscript.auth.AuthenticationManager
import com.revfcu.keyscript.options.ScriptOptionsService
import java.io.IOException

class RunKeyscriptAction : AnAction() {
    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && !file.isDirectory && file.extension == "js"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val authManager = project.getService(AuthenticationManager::class.java)

        if (!authManager.isLoggedIn()) {
            showNotification(project, "Please logon to Keystone first", NotificationType.WARNING)
            return
        }

        val serverUrl = authManager.getServerUrl() ?: return
        val sessionId = authManager.getSessionId() ?: return
        val optionsService = ScriptOptionsService.getInstance(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing Keyscript execution...") {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val scriptContent = String(file.contentsToByteArray(), file.charset)
                    val scriptName = file.name

                    // Construct payload
                    val payload = mutableMapOf<String, Any>()
                    
                    val crlogin = mutableMapOf<String, String>()
                    crlogin["sessionID"] = sessionId
                    crlogin["userName"] = authManager.getUserName() ?: ""
                    crlogin["deviceName"] = authManager.getDeviceName() ?: ""
                    // postingDate and branchName can be added if available
                    payload["crlogin"] = crlogin

                    val crscript = mutableMapOf<String, String>()
                    crscript["personSerial"] = optionsService.personSerial
                    crscript["accountSerial"] = optionsService.accountSerial
                    crscript["noteSerial"] = optionsService.noteSerial
                    crscript["commentSerial"] = optionsService.commentSerial
                    crscript["transactionSerial"] = optionsService.transactionSerial
                    crscript["checkSerial"] = optionsService.checkSerial
                    crscript["cardSerial"] = optionsService.cardSerial
                    payload["crscript"] = crscript

                    // Include script content in payload for "better" execution without local script server
                    payload["userScript"] = scriptContent

                    val client = KeystoneClient(serverUrl)
                    val paramsId = client.storeSessionParams(payload)
                    
                    if (paramsId != null) {
                        val runUrl = client.getRunScriptUrl(scriptName, paramsId)
                        BrowserUtil.browse(runUrl)
                    } else {
                        showNotification(project, "Failed to store session parameters", NotificationType.ERROR)
                    }
                } catch (ex: IOException) {
                    showNotification(project, "Execution failed: ${ex.message}", NotificationType.ERROR)
                }
            }
        })
    }

    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript Notifications")
            .createNotification("Keyscript Execution", content, type)
            .notify(project)
    }
}
