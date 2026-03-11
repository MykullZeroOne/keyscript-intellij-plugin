package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.WorkspaceUiService
import javax.swing.JComponent

class KeyscriptWorkspaceToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KeyscriptWorkspacePanel(project)
        WorkspaceUiService.getInstance(project).registerWorkspaceHost(panel)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class KeyscriptWorkspacePanel(project: Project) : WorkspaceTabHost {
    private val tabbedPane = JBTabbedPane()

    val component: JComponent

    init {
        tabbedPane.addTab(WorkspaceTab.RUN_OPTIONS.title, ScriptOptionsPanel(project).component)
        tabbedPane.addTab(WorkspaceTab.SESSION.title, SessionPanel(project).component)

        component = createToolWindowShell(
            title = "Keyscript Workspace",
            subtitle = "Manage execution parameters and session state while preview lives in the editor split view.",
            content = tabbedPane
        )
    }

    override fun selectTab(tab: WorkspaceTab) {
        tabbedPane.selectedIndex = when (tab) {
            WorkspaceTab.RUN_OPTIONS -> 0
            WorkspaceTab.SESSION -> 1
        }
    }
}
