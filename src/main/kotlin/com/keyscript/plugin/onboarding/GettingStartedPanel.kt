package com.keyscript.plugin.onboarding

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettings
import com.keyscript.plugin.toolwindow.KeyscriptToolWindowIds
import java.awt.*
import javax.swing.*

/**
 * Interactive checklist panel for the Workspace tool window.
 * Shows onboarding progress and provides action buttons for each step.
 * Updates dynamically as the user completes milestones.
 */
class GettingStartedPanel(private val project: Project) : Disposable {

    val component: JComponent
    private val checklistPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
    }
    private val onboarding = OnboardingStateService.getInstance(project)

    private val stateListener: () -> Unit = { SwingUtilities.invokeLater { rebuildChecklist() } }

    init {
        onboarding.addListener(stateListener)
        SessionService.getInstance(project).addListener(stateListener)

        component = JPanel(BorderLayout()).apply {
            // Header
            val header = JPanel(BorderLayout()).apply {
                add(JBLabel("Getting Started").apply {
                    font = JBUI.Fonts.label(14f).asBold()
                }, BorderLayout.WEST)
                add(JBLabel().apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = JBUI.Fonts.smallFont()
                    text = "Complete these steps to start using Keyscript IDE"
                }, BorderLayout.SOUTH)
                border = JBUI.Borders.empty(12, 12, 8, 12)
            }
            add(header, BorderLayout.NORTH)

            // Scrollable checklist
            add(JScrollPane(checklistPanel).apply {
                border = JBUI.Borders.empty()
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }, BorderLayout.CENTER)

            // Footer
            val footer = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                border = JBUI.Borders.empty(8, 12)
                add(JButton("Open Welcome Guide").apply {
                    addActionListener { WelcomeDialog(project).show() }
                })
                add(JButton("Dismiss Checklist").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    isBorderPainted = false
                    isContentAreaFilled = false
                    addActionListener {
                        onboarding.dismissed = true
                        onboarding.shownWelcome = true
                    }
                })
            }
            add(footer, BorderLayout.SOUTH)
        }

        rebuildChecklist()
    }

    private fun rebuildChecklist() {
        checklistPanel.removeAll()

        // Refresh config state
        val settings = KeyscriptSettings.getInstance()
        if (settings.proxyEndpoint.isNotBlank() && settings.keystoneApiUrl.isNotBlank()) {
            onboarding.configuredServer = true
        }
        if (SessionService.getInstance(project).isLoggedIn && !onboarding.completedFirstLogin) {
            onboarding.completedFirstLogin = true
        }

        val completedCount = listOf(
            onboarding.configuredServer,
            onboarding.completedFirstLogin,
            onboarding.completedFirstRun,
            onboarding.completedFirstDeploy
        ).count { it }

        // Progress bar
        checklistPanel.add(createProgressBar(completedCount, 4))
        checklistPanel.add(Box.createVerticalStrut(12))

        // Step 1: Configure
        checklistPanel.add(createCheckItem(
            done = onboarding.configuredServer,
            title = "Configure Keystone Connection",
            detail = "Set your server endpoint, API URL, and instances in Settings > Keyscript IDE",
            actionText = "Open Settings",
            action = { ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keyscript IDE") }
        ))

        // Step 2: Login
        checklistPanel.add(createCheckItem(
            done = onboarding.completedFirstLogin,
            title = "Login to Keystone",
            detail = "Click the \"KS: Not Logged In\" widget in the status bar, or use Keyscript > Login",
            actionText = "Show Status Bar",
            action = {
                com.intellij.openapi.wm.WindowManager.getInstance()
                    .getStatusBar(project)?.updateWidget("KeyscriptLoginStatus")
            }
        ))

        // Step 3: Run a script
        checklistPanel.add(createCheckItem(
            done = onboarding.completedFirstRun,
            title = "Run a Keyscript",
            detail = "Open a .keyscript.js file and press Ctrl+Shift+F10, or click the gutter play icon",
            actionText = null,
            action = null
        ))

        // Step 4: Deploy
        checklistPanel.add(createCheckItem(
            done = onboarding.completedFirstDeploy,
            title = "Deploy a Script",
            detail = "Right-click a Keyscript file > Deploy Script to push it to the SCRIPT table",
            actionText = null,
            action = null
        ))

        // Completion message
        if (completedCount == 4) {
            checklistPanel.add(Box.createVerticalStrut(12))
            checklistPanel.add(JBLabel("<html><b style='color:#4EC9B0;'>All done!</b> " +
                "You've completed the setup. Explore Data Tools and Diagnostics for more.</html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = JBUI.Borders.empty(8)
            })
        }

        checklistPanel.revalidate()
        checklistPanel.repaint()
    }

    private fun createProgressBar(completed: Int, total: Int): JComponent {
        val pct = (completed.toFloat() / total * 100).toInt()
        return JPanel(BorderLayout(8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            add(JProgressBar(0, total).apply {
                value = completed
                isStringPainted = true
                string = "$completed of $total complete"
            }, BorderLayout.CENTER)
        }
    }

    private fun createCheckItem(
        done: Boolean,
        title: String,
        detail: String,
        actionText: String?,
        action: (() -> Unit)?
    ): JComponent {
        val icon = if (done) AllIcons.General.InspectionsOK else AllIcons.RunConfigurations.TestNotRan
        return JPanel(BorderLayout(8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 60)
            border = JBUI.Borders.empty(6, 0)

            add(JBLabel(icon).apply {
                preferredSize = Dimension(20, 20)
                verticalAlignment = SwingConstants.TOP
            }, BorderLayout.WEST)

            val text = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(JBLabel(title).apply {
                    font = if (done) JBUI.Fonts.label().asBold() else JBUI.Fonts.label()
                    foreground = if (done) UIUtil.getContextHelpForeground() else UIUtil.getLabelForeground()
                })
                add(JBLabel(detail).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = UIUtil.getContextHelpForeground()
                })
            }
            add(text, BorderLayout.CENTER)

            if (actionText != null && action != null && !done) {
                add(JButton(actionText).apply {
                    addActionListener { action() }
                    preferredSize = Dimension(preferredSize.width, 28)
                }, BorderLayout.EAST)
            }
        }
    }

    override fun dispose() {
        onboarding.removeListener(stateListener)
        SessionService.getInstance(project).removeListener(stateListener)
    }
}
