package com.keyscript.plugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.intellij.icons.AllIcons

class KeyscriptDataToolsToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "KS Data"

        val contentManager = toolWindow.contentManager
        val factory = ContentFactory.getInstance()

        val search = factory.createContent(
            SearchPanel(project).component,
            DataToolsTab.SEARCH.title,
            false
        )
        contentManager.addContent(search)

        val tableBrowser = factory.createContent(
            TableBrowserPanel(project).component,
            DataToolsTab.TABLE_BROWSER.title,
            false
        )
        contentManager.addContent(tableBrowser)

        val queryBuilder = factory.createContent(
            QueryBuilderPanel(project).component,
            DataToolsTab.QUERY_BUILDER.title,
            false
        )
        contentManager.addContent(queryBuilder)

        val installedScripts = factory.createContent(
            InstalledScriptsPanel(project).component,
            DataToolsTab.INSTALLED_SCRIPTS.title,
            false
        )
        contentManager.addContent(installedScripts)

        // Add refresh action to tool window title bar
        val refreshAction = object : AnAction("Refresh", "Refresh data tools content", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                // Re-select the current tab to trigger a refresh
                val selected = contentManager.selectedContent
                if (selected != null) {
                    contentManager.setSelectedContent(selected, true)
                }
            }
        }
        toolWindow.setTitleActions(listOf(refreshAction))
    }
}
