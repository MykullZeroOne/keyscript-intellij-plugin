package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.toolwindow.DataToolsTab
import com.keyscript.plugin.toolwindow.DataToolsTabHost
import com.keyscript.plugin.toolwindow.DiagnosticsTab
import com.keyscript.plugin.toolwindow.DiagnosticsTabHost
import com.keyscript.plugin.toolwindow.KeyscriptToolWindowIds
import com.keyscript.plugin.toolwindow.WorkspaceTab
import com.keyscript.plugin.toolwindow.WorkspaceTabHost
import javax.swing.SwingUtilities

@Service(Service.Level.PROJECT)
class WorkspaceUiService(private val project: Project) {
    private var workspaceHost: WorkspaceTabHost? = null
    private var dataToolsHost: DataToolsTabHost? = null
    private var diagnosticsHost: DiagnosticsTabHost? = null

    fun registerWorkspaceHost(host: WorkspaceTabHost) {
        workspaceHost = host
    }

    fun registerDataToolsHost(host: DataToolsTabHost) {
        dataToolsHost = host
    }

    fun registerDiagnosticsHost(host: DiagnosticsTabHost) {
        diagnosticsHost = host
    }

    fun showWorkspaceTab(tab: WorkspaceTab) {
        showToolWindow(KeyscriptToolWindowIds.WORKSPACE) {
            workspaceHost?.selectTab(tab)
        }
    }

    fun showDataToolsTab(tab: DataToolsTab) {
        showToolWindow(KeyscriptToolWindowIds.DATA_TOOLS) {
            dataToolsHost?.selectTab(tab)
        }
    }

    fun showDiagnosticsTab(tab: DiagnosticsTab) {
        showToolWindow(KeyscriptToolWindowIds.DIAGNOSTICS) {
            diagnosticsHost?.selectTab(tab)
        }
    }

    private fun showToolWindow(id: String, afterShow: () -> Unit) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(id)
        if (toolWindow == null) {
            afterShow()
            return
        }

        val action = {
            toolWindow.show {
                afterShow()
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            UIUtil.invokeLaterIfNeeded(action)
        }
    }

    companion object {
        fun getInstance(project: Project): WorkspaceUiService =
            project.getService(WorkspaceUiService::class.java)
    }
}
