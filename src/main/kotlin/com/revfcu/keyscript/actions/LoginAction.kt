package com.revfcu.keyscript.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.revfcu.keyscript.api.KeybridgeClient
import com.revfcu.keyscript.auth.AuthenticationManager
import com.revfcu.keyscript.auth.LoginDialog

class LoginAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = LoginDialog(project)
        
        if (dialog.showAndGet()) {
            val serverUrl = dialog.getServerUrl()
            val userName = dialog.getUserName()
            val password = dialog.getPassword()
            val deviceName = dialog.getDeviceName()

            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logging into Keystone...") {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val client = KeybridgeClient(serverUrl)
                        val sessionId = client.logon(userName, deviceName, password)
                        
                        if (sessionId != null) {
                            val authManager = project.getService(AuthenticationManager::class.java)
                            authManager.setSessionId(sessionId)
                            authManager.setServerUrl(serverUrl)
                            authManager.setUserName(userName)
                            authManager.setDeviceName(deviceName)
                            
                            showNotification(project, "Logged in successfully", NotificationType.INFORMATION)
                        } else {
                            showNotification(project, "Logon failed: No sessionId returned", NotificationType.ERROR)
                        }
                    } catch (ex: Exception) {
                        showNotification(project, "Logon failed: ${ex.message}", NotificationType.ERROR)
                    }
                }
            })
        }
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript Notifications")
            .createNotification("Keystone Logon", content, type)
            .notify(project)
    }
}
