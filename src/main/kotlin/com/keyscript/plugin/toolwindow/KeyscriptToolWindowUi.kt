package com.keyscript.plugin.toolwindow

import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object KeyscriptToolWindowIds {
    const val WORKSPACE = "Keyscript Workspace"
    const val DATA_TOOLS = "Keyscript Data Tools"
    const val DIAGNOSTICS = "Keyscript Diagnostics"
}

enum class WorkspaceTab(val title: String) {
    RUN_OPTIONS("Run Options"),
    SESSION("Session")
}

enum class DataToolsTab(val title: String) {
    SEARCH("Search"),
    TABLE_BROWSER("Table Browser"),
    QUERY_BUILDER("Query Builder")
}

enum class DiagnosticsTab(val title: String) {
    CONSOLE("Console"),
    NETWORK("Network")
}

interface WorkspaceTabHost {
    fun selectTab(tab: WorkspaceTab)
}

interface DataToolsTabHost {
    fun selectTab(tab: DataToolsTab)
}

interface DiagnosticsTabHost {
    fun selectTab(tab: DiagnosticsTab)
}

fun createToolWindowShell(title: String, subtitle: String, content: JComponent): JComponent {
    val titleLabel = JLabel(title).apply {
        font = JBFont.h3().asBold()
    }
    val subtitleLabel = JLabel("<html>$subtitle</html>").apply {
        font = JBFont.small()
        foreground = UIUtil.getContextHelpForeground()
    }

    val header = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(12, 12, 8, 12)
        add(titleLabel, BorderLayout.NORTH)
        add(subtitleLabel, BorderLayout.SOUTH)
    }

    return JPanel(BorderLayout()).apply {
        add(header, BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
    }
}
