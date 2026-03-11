package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.keyscript.plugin.services.KeyscriptProjectDetector
import com.keyscript.plugin.services.WorkspaceUiService
import javax.swing.JComponent

class KeyscriptDataToolsToolWindowFactory : ToolWindowFactory {
    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean =
        KeyscriptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KeyscriptDataToolsPanel(project)
        WorkspaceUiService.getInstance(project).registerDataToolsHost(panel)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class KeyscriptDataToolsPanel(project: Project) : DataToolsTabHost {
    private val tabbedPane = JBTabbedPane()

    val component: JComponent

    init {
        tabbedPane.addTab(DataToolsTab.SEARCH.title, SearchPanel(project).component)
        tabbedPane.addTab(DataToolsTab.TABLE_BROWSER.title, TableBrowserPanel(project).component)
        tabbedPane.addTab(DataToolsTab.QUERY_BUILDER.title, QueryBuilderPanel(project).component)

        component = createToolWindowShell(
            title = "Keyscript Data Tools",
            subtitle = "Search members, browse tables, and build queries from one coordinated workspace.",
            content = tabbedPane
        )
    }

    override fun selectTab(tab: DataToolsTab) {
        tabbedPane.selectedIndex = tab.ordinal
    }
}
