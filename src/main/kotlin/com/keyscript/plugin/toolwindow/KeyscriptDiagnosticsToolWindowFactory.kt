package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.WorkspaceUiService
import javax.swing.JComponent

class KeyscriptDiagnosticsToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KeyscriptDiagnosticsPanel(project)
        WorkspaceUiService.getInstance(project).registerDiagnosticsHost(panel)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class KeyscriptDiagnosticsPanel(project: Project) : DiagnosticsTabHost {
    private val tabbedPane = JBTabbedPane()

    val component: JComponent

    init {
        tabbedPane.addTab(DiagnosticsTab.CONSOLE.title, ConsolePanel(project).component)
        tabbedPane.addTab(DiagnosticsTab.NETWORK.title, NetworkPanel(project).component)

        component = createToolWindowShell(
            title = "Keyscript Diagnostics",
            subtitle = "Inspect runtime output and proxy activity without scattering debug information across windows.",
            content = tabbedPane
        )
    }

    override fun selectTab(tab: DiagnosticsTab) {
        tabbedPane.selectedIndex = tab.ordinal
    }
}
