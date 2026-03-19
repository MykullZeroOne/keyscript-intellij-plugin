package com.keyscript.plugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.NetworkMonitorService
import com.intellij.icons.AllIcons

class KeyscriptDiagnosticsToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "KS Diagnostics"

        val contentManager = toolWindow.contentManager
        val factory = ContentFactory.getInstance()

        val console = factory.createContent(
            ConsolePanel(project).component,
            DiagnosticsTab.CONSOLE.title,
            false
        )
        contentManager.addContent(console)

        val network = factory.createContent(
            NetworkPanel(project).component,
            DiagnosticsTab.NETWORK.title,
            false
        )
        contentManager.addContent(network)

        // Add clear action to tool window title bar
        val clearAction = object : AnAction(
            "Clear All",
            "Clear console output and network events",
            AllIcons.Actions.GC
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                val proj = e.project ?: return
                NetworkMonitorService.getInstance(proj).clear()
            }
        }
        toolWindow.setTitleActions(listOf(clearAction))
    }
}
