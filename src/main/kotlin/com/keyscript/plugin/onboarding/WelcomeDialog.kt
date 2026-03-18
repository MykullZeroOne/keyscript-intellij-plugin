package com.keyscript.plugin.onboarding

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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
 * Multi-step welcome wizard shown on first project open.
 * Each step answers: what this does, why it matters, what to click next.
 * Dismissible and re-openable from Keyscript > Getting Started.
 */
class WelcomeDialog(private val project: Project) : DialogWrapper(project, true) {

    private var currentStep = 0
    private val cardPanel = JPanel(CardLayout())
    private val progressLabel = JBLabel()
    private val backButton = JButton("Back")
    private val nextButton = JButton("Next")

    private val steps = listOf(
        StepConfig(
            "Welcome to Keyscript IDE",
            """
            <html><body style='width:380px; font-family:sans-serif;'>
            <p style='font-size:13pt;'>Build, test, and deploy Keystone scripts directly from IntelliJ.</p>
            <p style='color:#888; margin-top:12px;'>Keyscript IDE gives you:</p>
            <ul style='color:#888;'>
              <li><b>Live preview</b> &mdash; run scripts in a split-editor browser panel</li>
              <li><b>CR framework completions</b> &mdash; code assist for CR.Core, CR.XML, Ext.js</li>
              <li><b>One-click deploy</b> &mdash; push scripts to Keystone's SCRIPT table</li>
              <li><b>Data tools</b> &mdash; search tables, browse records, build queries</li>
            </ul>
            <p style='color:#666; margin-top:12px;'>This guide will help you get set up in about 2 minutes.</p>
            </body></html>
            """.trimIndent(),
            AllIcons.General.Information,
            "Let's Get Started"
        ),
        StepConfig(
            "Step 1: Configure Keystone Connection",
            """
            <html><body style='width:380px; font-family:sans-serif;'>
            <p>Tell the plugin where your Keystone server lives.</p>
            <p style='margin-top:10px;'><b>Open:</b> Settings &gt; Keyscript IDE</p>
            <p style='margin-top:10px;'>Fill in these fields:</p>
            <table style='margin-top:6px;'>
              <tr><td style='color:#888; padding-right:12px;'>Server Endpoint</td><td><code>keystonedev.revfcu.com:8443</code></td></tr>
              <tr><td style='color:#888; padding-right:12px;'>Instances</td><td><code>Development, Test</code></td></tr>
              <tr><td style='color:#888; padding-right:12px;'>Device Identifier</td><td>Your Keystone device ID</td></tr>
            </table>
            <p style='color:#4EC9B0; margin-top:14px;'>&#10004; When done, you'll see the values in the settings panel.</p>
            </body></html>
            """.trimIndent(),
            AllIcons.General.Settings,
            "Open Settings",
            action = { ShowSettingsUtil.getInstance().showSettingsDialog(project, "Keyscript IDE") }
        ),
        StepConfig(
            "Step 2: Login to Keystone",
            """
            <html><body style='width:380px; font-family:sans-serif;'>
            <p>Authenticate so the plugin can access your Keystone environment.</p>
            <p style='margin-top:10px;'><b>Click the status bar</b> at the bottom of the IDE:</p>
            <p style='margin:8px 0; padding:6px 12px; background:#2d2d2d; border-radius:4px; display:inline-block;'>
              <code>KS: Not Logged In</code> &rarr; click to open login dialog
            </p>
            <p style='margin-top:10px;'>Enter your Keystone username, password, and select an instance.</p>
            <p style='color:#888; margin-top:10px;'>Your credentials are stored securely in the OS keychain
            via IntelliJ's PasswordSafe. The plugin will auto-reconnect if your session expires.</p>
            <p style='color:#4EC9B0; margin-top:14px;'>&#10004; Success: status bar shows <code>KS: yourname | Development</code></p>
            </body></html>
            """.trimIndent(),
            AllIcons.Actions.Execute,
            null
        ),
        StepConfig(
            "Step 3: Run Your First Script",
            """
            <html><body style='width:380px; font-family:sans-serif;'>
            <p>Open any <code>.keyscript.js</code> file, or a <code>.js</code> file that starts with
            <code>// @keyscript</code>.</p>
            <p style='margin-top:10px;'><b>Three ways to run:</b></p>
            <ol>
              <li>Click the <b>green play icon</b> in the gutter (left margin)</li>
              <li>Press <b>Ctrl+Shift+F10</b></li>
              <li>Right-click &gt; <b>Run Keyscript</b></li>
            </ol>
            <p style='margin-top:10px;'>A split preview opens beside your editor showing the script
            running in a Keystone environment.</p>
            <p style='color:#4EC9B0; margin-top:14px;'>&#10004; Success: the preview panel shows your script output</p>
            </body></html>
            """.trimIndent(),
            AllIcons.Actions.Execute,
            null
        ),
        StepConfig(
            "You're All Set!",
            """
            <html><body style='width:380px; font-family:sans-serif;'>
            <p style='font-size:13pt;'>Keyscript IDE is ready to use.</p>
            <p style='margin-top:12px;'><b>Explore the tool windows:</b></p>
            <table style='margin-top:6px;'>
              <tr><td style='padding:4px 12px 4px 0;'><b>Workspace</b> (right)</td><td style='color:#888;'>Run options &amp; session info</td></tr>
              <tr><td style='padding:4px 12px 4px 0;'><b>Data Tools</b> (bottom)</td><td style='color:#888;'>Search, browse tables, build queries</td></tr>
              <tr><td style='padding:4px 12px 4px 0;'><b>Diagnostics</b> (bottom)</td><td style='color:#888;'>Console output &amp; network monitor</td></tr>
            </table>
            <p style='margin-top:12px;'><b>Key actions:</b></p>
            <table style='margin-top:6px;'>
              <tr><td style='padding:2px 12px 2px 0;'>Deploy script</td><td style='color:#888;'>Right-click &gt; Deploy Script</td></tr>
              <tr><td style='padding:2px 12px 2px 0;'>Bundle project</td><td style='color:#888;'>Keyscript &gt; Bundle (esbuild)</td></tr>
              <tr><td style='padding:2px 12px 2px 0;'>Open in browser</td><td style='color:#888;'>Keyscript &gt; Open Preview in Chrome</td></tr>
            </table>
            <p style='color:#888; margin-top:14px;'>Re-open this guide anytime from <b>Keyscript &gt; Getting Started</b>.</p>
            </body></html>
            """.trimIndent(),
            AllIcons.General.SuccessDialog,
            "Open Workspace",
            action = {
                ToolWindowManager.getInstance(project)
                    .getToolWindow(KeyscriptToolWindowIds.WORKSPACE)?.show()
            }
        )
    )

    init {
        title = "Keyscript IDE"
        setOKButtonText("Finish")
        setCancelButtonText("Skip")
        init()
        updateStepUi()
    }

    override fun createCenterPanel(): JComponent {
        for ((i, step) in steps.withIndex()) {
            cardPanel.add(createStepPanel(step), "step$i")
        }

        val nav = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(progressLabel, BorderLayout.WEST)
            val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
            backButton.addActionListener { navigateBack() }
            nextButton.addActionListener { navigateNext() }
            buttons.add(backButton)
            buttons.add(nextButton)
            add(buttons, BorderLayout.EAST)
        }

        return JPanel(BorderLayout()).apply {
            add(cardPanel, BorderLayout.CENTER)
            add(nav, BorderLayout.SOUTH)
            preferredSize = Dimension(500, 420)
            border = JBUI.Borders.empty(12)
        }
    }

    private fun createStepPanel(step: StepConfig): JPanel {
        return JPanel(BorderLayout(0, 12)).apply {
            // Header
            val header = JPanel(BorderLayout(10, 0)).apply {
                add(JBLabel(step.icon).apply {
                    preferredSize = Dimension(32, 32)
                }, BorderLayout.WEST)
                add(JBLabel(step.title).apply {
                    font = JBUI.Fonts.label(16f).asBold()
                }, BorderLayout.CENTER)
                border = JBUI.Borders.emptyBottom(4)
            }

            // Content
            val content = JBLabel(step.content).apply {
                verticalAlignment = SwingConstants.TOP
            }

            // Action button (if applicable)
            val actionPanel = if (step.actionButtonText != null && step.action != null) {
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)).apply {
                    add(JButton(step.actionButtonText).apply {
                        addActionListener { step.action.invoke() }
                    })
                }
            } else null

            add(header, BorderLayout.NORTH)
            val body = JPanel(BorderLayout())
            body.add(JScrollPane(content).apply {
                border = JBUI.Borders.empty()
                viewportBorder = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
            if (actionPanel != null) {
                body.add(actionPanel, BorderLayout.SOUTH)
            }
            add(body, BorderLayout.CENTER)
        }
    }

    private fun navigateBack() {
        if (currentStep > 0) {
            currentStep--
            updateStepUi()
        }
    }

    private fun navigateNext() {
        if (currentStep < steps.lastIndex) {
            currentStep++
            updateStepUi()
        }
    }

    private fun updateStepUi() {
        (cardPanel.layout as CardLayout).show(cardPanel, "step$currentStep")
        progressLabel.text = "Step ${currentStep + 1} of ${steps.size}"
        progressLabel.foreground = UIUtil.getContextHelpForeground()
        backButton.isVisible = currentStep > 0
        nextButton.text = if (currentStep == 0) steps[0].nextButtonOverride ?: "Next" else "Next"
        nextButton.isVisible = currentStep < steps.lastIndex
        okAction.isEnabled = true
    }

    override fun doOKAction() {
        val onboarding = OnboardingStateService.getInstance(project)
        onboarding.shownWelcome = true

        // Check if server is already configured
        val settings = KeyscriptSettings.getInstance()
        if (settings.proxyEndpoint.isNotBlank()) {
            onboarding.configuredServer = true
        }
        if (SessionService.getInstance(project).isLoggedIn) {
            onboarding.completedFirstLogin = true
        }

        super.doOKAction()
    }

    override fun doCancelAction() {
        val onboarding = OnboardingStateService.getInstance(project)
        onboarding.shownWelcome = true
        super.doCancelAction()
    }

    private data class StepConfig(
        val title: String,
        val content: String,
        val icon: Icon,
        val nextButtonOverride: String? = null,
        val actionButtonText: String? = nextButtonOverride,
        val action: (() -> Unit)? = null
    )
}
