package com.keyscript.plugin.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettingsConfigurable
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class SessionPanel(private val project: Project) : Disposable {
    private val session = SessionService.getInstance(project)
    private val stateLabel = JBLabel()
    private val listener = { refresh() }

    // ─── Persistent value labels (created once) ───────────────────────────────
    private val usernameValue = JBLabel("-")
    private val instanceValue = JBLabel("-")
    private val sessionValue = JBLabel("-")
    private val databaseValue = JBLabel("-")
    private val locationValue = JBLabel("-")
    private val postingDateValue = JBLabel("-")
    private val authValue = JBLabel("-")

    // ─── Card layout switching between logged-in and logged-out views ─────────
    private val cardLayout = CardLayout()
    private val cardContainer = JPanel(cardLayout)

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(
            JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(8, 8, 0, 8)
                add(stateLabel, BorderLayout.WEST)
            },
            BorderLayout.NORTH
        )
        add(JBScrollPane(cardContainer), BorderLayout.CENTER)
    }

    init {
        cardContainer.add(buildEmptyCard(), "empty")
        cardContainer.add(buildInfoCard(), "info")
        session.addListener(listener)
        refresh()
    }

    override fun dispose() {
        session.removeListener(listener)
    }

    private fun buildEmptyCard(): JPanel {
        return JPanel(BorderLayout()).apply {
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
    }

    private fun buildInfoCard(): JPanel {
        val details = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
        }
        details.add(infoRow("User", usernameValue))
        details.add(infoRow("Instance", instanceValue))
        details.add(infoRow("Session", sessionValue))
        details.add(infoRow("Database", databaseValue))
        details.add(infoRow("Location", locationValue))
        details.add(infoRow("Posting Date", postingDateValue))
        details.add(infoRow("Auth", authValue))
        details.add(Box.createVerticalStrut(12))
        details.add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(createActionButton("Manage Login") { triggerLoginAction() })
                add(Box.createHorizontalStrut(8))
                add(createActionButton("Logout") { logout() })
                add(Box.createHorizontalStrut(8))
                add(ActionLink("Open Settings") { openSettings() })
            }
        )
        return details
    }

    private fun infoRow(label: String, valueLabel: JBLabel): JComponent {
        return JPanel(BorderLayout(8, 0)).apply {
            border = JBUI.Borders.empty(0, 0, 8, 0)
            add(JBLabel("$label:").apply { foreground = UIUtil.getContextHelpForeground() }, BorderLayout.WEST)
            add(valueLabel, BorderLayout.CENTER)
        }
    }

    private fun String?.orDash(): String = if (isNullOrBlank()) "\u2014" else this

    fun refresh() {
        stateLabel.text = if (session.isLoggedIn) {
            "Connected to Keystone"
        } else {
            "No active Keystone session"
        }
        stateLabel.foreground = UIUtil.getLabelForeground()

        if (session.isLoggedIn) {
            usernameValue.text = session.username.orDash()
            instanceValue.text = session.instance.orDash()
            sessionValue.text = session.jsessionId.orDash()
            databaseValue.text = session.loginData["databaseName"].orDash()
            locationValue.text = session.loginData["locationName"].orDash()
            postingDateValue.text = session.loginData["postingDate"].orDash()
            authValue.text = if (session.loginData["ssoLogin"] == "true") "Kerberos SSO" else "Username / Password"
            cardLayout.show(cardContainer, "info")
        } else {
            cardLayout.show(cardContainer, "empty")
        }

        cardContainer.revalidate()
        cardContainer.repaint()
    }

    private fun createActionButton(label: String, onClick: () -> Unit): JButton {
        return JButton(label).apply {
            addActionListener { onClick() }
        }
    }

    private fun triggerLoginAction() {
        val action = ActionManager.getInstance().getAction("Keyscript.Login") ?: return
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .build()
        ActionManager.getInstance().tryToExecute(action, null, null, ActionPlaces.TOOLWINDOW_CONTENT, true)
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
