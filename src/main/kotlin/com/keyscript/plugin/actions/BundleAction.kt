package com.keyscript.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.keyscript.plugin.services.BundleService
import com.keyscript.plugin.services.KeyscriptProjectDetector
import java.io.File

/**
 * Runs esbuild to bundle a React/Node Keyscript project.
 * Only visible when keyscript.bundle.json exists in the project root.
 */
class BundleAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible =
            KeyscriptProjectDetector.isKeyscriptProject(project) &&
            BundleService.getInstance(project).hasBundleConfig()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val bundleService = BundleService.getInstance(project)

        // Check for esbuild
        if (bundleService.findEsbuild() == null) {
            val choice = javax.swing.JOptionPane.showConfirmDialog(
                null,
                "esbuild is not installed.\nWould you like to install it now?\n\n(npm install --save-dev esbuild)",
                "Keyscript Bundle",
                javax.swing.JOptionPane.YES_NO_OPTION
            )
            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing esbuild...") {
                    override fun run(indicator: ProgressIndicator) {
                        val result = bundleService.installEsbuild()
                        if (result.success) {
                            notify(project, "esbuild installed successfully. Run Bundle again.", NotificationType.INFORMATION)
                        } else {
                            notify(project, "Failed to install esbuild: ${result.error}", NotificationType.ERROR)
                        }
                    }
                })
            }
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Bundling Keyscript...") {
            override fun run(indicator: ProgressIndicator) {
                val result = bundleService.bundle()
                if (result.success) {
                    val sizeKb = result.outputSize / 1024
                    notify(project, "Bundle complete: ${result.outputPath?.substringAfterLast('/')} (${sizeKb}KB)", NotificationType.INFORMATION)

                    // Refresh VFS so the output file appears in the project tree
                    result.outputPath?.let { path ->
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path))
                    }
                } else {
                    val msg = buildString {
                        append("Bundle failed: ${result.error}")
                        if (!result.stderr.isNullOrBlank()) {
                            append("\n\n${result.stderr}")
                        }
                    }
                    notify(project, msg, NotificationType.ERROR)
                }
            }
        })
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript Bundle", message, type)
            .notify(project)
    }
}
