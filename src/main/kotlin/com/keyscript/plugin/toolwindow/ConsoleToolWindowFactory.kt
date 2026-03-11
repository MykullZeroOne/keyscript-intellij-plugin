package com.keyscript.plugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.NetworkMonitorService
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*

/**
 * Console tool window that displays JCEF console.log output captured from the preview.
 */
class ConsoleToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ConsolePanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ConsolePanel(project: Project) {
    private val textArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        val scheme = EditorColorsManager.getInstance().globalScheme
        background = scheme.defaultBackground
        foreground = scheme.defaultForeground
    }

    val component: JComponent

    init {
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JButton("Clear").apply {
                addActionListener {
                    textArea.text = ""
                }
            })
        }

        component = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(JBScrollPane(textArea), BorderLayout.CENTER)
            border = JBUI.Borders.empty()
        }

        val monitor = NetworkMonitorService.getInstance(project)
        monitor.getEvents()
            .filter { it.type == "console" }
            .forEach { appendEvent(it) }

        monitor.addListener { event ->
            if (event.type == "console") {
                appendEvent(event)
            }
        }
    }

    private fun appendEvent(event: NetworkMonitorService.NetworkEvent) {
        SwingUtilities.invokeLater {
            val prefix = when (event.method) {
                "LOGSEVERITY_ERROR" -> "[ERROR]"
                "LOGSEVERITY_WARNING" -> "[WARN] "
                "LOGSEVERITY_INFO" -> "[INFO] "
                else -> "[LOG]  "
            }
            textArea.append("$prefix ${event.body}\n")
            textArea.caretPosition = textArea.document.length
        }
    }
}
