package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.toolwindow.DataToolsTab
import com.keyscript.plugin.toolwindow.DiagnosticsTab
import com.keyscript.plugin.toolwindow.KeyscriptToolWindowIds
import com.keyscript.plugin.toolwindow.WorkspaceTab
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class WorkspaceUiService(private val project: Project) {

    fun showWorkspaceTab(tab: WorkspaceTab) {
        selectContentByName(KeyscriptToolWindowIds.WORKSPACE, tab.title)
    }

    fun showDataToolsTab(tab: DataToolsTab) {
        selectContentByName(KeyscriptToolWindowIds.DATA_TOOLS, tab.title)
    }

    fun showDiagnosticsTab(tab: DiagnosticsTab) {
        selectContentByName(KeyscriptToolWindowIds.DIAGNOSTICS, tab.title)
    }

    private fun selectContentByName(toolWindowId: String, contentName: String) {
        val action = Runnable {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return@Runnable
            toolWindow.show {
                val content = toolWindow.contentManager.contents.firstOrNull { it.displayName == contentName }
                if (content != null) {
                    toolWindow.contentManager.setSelectedContent(content, true)
                }
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            action.run()
        } else {
            UIUtil.invokeLaterIfNeeded(action)
        }
    }

    companion object {
        fun getInstance(project: Project): WorkspaceUiService =
            project.getService(WorkspaceUiService::class.java)
    }
}
