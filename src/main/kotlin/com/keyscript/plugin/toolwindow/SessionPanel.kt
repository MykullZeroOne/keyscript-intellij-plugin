package com.keyscript.plugin.toolwindow

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettingsConfigurable
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class SessionPanel(private val project: Project) {
    private val session = SessionService.getInstance(project)
    private val detailsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
    }
    private val stateLabel = JBLabel()
    private val listener = { refresh() }

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 8, 0, 8)
                add(stateLabel, BorderLayout.WEST)
            },
            BorderLayout.NORTH
        )
        add(JBScrollPane(detailsPanel), BorderLayout.CENTER)
    }

    init {
        session.addListener(listener)
        refresh()
    }

    private fun refresh() {
        detailsPanel.removeAll()
        stateLabel.text = if (session.isLoggedIn) {
            "Connected to Keystone"
        } else {
            "No active Keystone session"
        }
        stateLabel.foreground = UIUtil.getLabelForeground()

        if (!session.isLoggedIn) {
            detailsPanel.add(
                JPanel(BorderLayout()).apply {
                    border = JBUI.Borders.empty(8)
                    add(JBLabel("Log in to run scripts and browse Keystone data."), BorderLayout.NORTH)
                    add(
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            add(createActionButton("Login") { triggerLoginAction() })
                            add(Box.createHorizontalStrut(8))
                            add(ActionLink("Open Settings") { openSettings() })
                        },
                        BorderLayout.SOUTH
                    )
                }
            )
        } else {
            detailsPanel.add(infoRow("User", session.username))
            detailsPanel.add(infoRow("Instance", session.instance))
            detailsPanel.add(infoRow("Session", session.jsessionId))
            detailsPanel.add(infoRow("Database", session.loginData["databaseName"]))
            detailsPanel.add(infoRow("Location", session.loginData["locationName"]))
            detailsPanel.add(infoRow("Posting Date", session.loginData["postingDate"]))
            detailsPanel.add(infoRow("Auth", if (session.loginData["ssoLogin"] == "true") "Kerberos SSO" else "Username / Password"))
            detailsPanel.add(Box.createVerticalStrut(12))
            detailsPanel.add(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(createActionButton("Manage Login") { triggerLoginAction() })
                    add(Box.createHorizontalStrut(8))
                    add(createActionButton("Logout") { logout() })
                    add(Box.createHorizontalStrut(8))
                    add(ActionLink("Open Settings") { openSettings() })
                }
            )
        }

        detailsPanel.revalidate()
        detailsPanel.repaint()
    }

    private fun infoRow(label: String, value: String?): JComponent {
        return JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(0, 0, 8, 0)
            add(JBLabel("$label:").apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
            add(JBLabel(value?.ifBlank { "\u2014" } ?: "\u2014"), BorderLayout.CENTER)
        }
    }

    private fun createActionButton(label: String, onClick: () -> Unit): JButton {
        return JButton(label).apply {
            addActionListener { onClick() }
        }
    }

    private fun triggerLoginAction() {
        val action = ActionManager.getInstance().getAction("Keyscript.Login") ?: return
        val event = AnActionEvent.createFromAnAction(action, null, "KeyscriptSessionPanel", DataContext.EMPTY_CONTEXT)
        action.actionPerformed(event)
    }

    private fun logout() {
        session.clearSession()
        ProxyServerService.getInstance(project).setSsoSession("")
        refresh()
    }

    private fun openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KeyscriptSettingsConfigurable::class.java)
    }
}
