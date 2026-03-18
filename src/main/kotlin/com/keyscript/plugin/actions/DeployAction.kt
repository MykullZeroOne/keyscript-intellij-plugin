package com.keyscript.plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

/**
 * Quick deploy the current Keyscript file to a Keystone environment.
 * For full script management (browse, update existing, install new),
 * use the Installed Scripts tool window instead.
 */
class DeployAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            file != null &&
            KeyscriptFileSupport.isKeyscriptFile(file, project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            notify(project, "Please login to Keystone first.", NotificationType.WARNING)
            return
        }

        // Get file content — use unsaved document content if available
        val document = FileDocumentManager.getInstance().getDocument(file)
        val sourceCode = document?.text ?: file.contentsToByteArray().toString(Charsets.UTF_8)

        // Check if this is a bundled project
        val bundleService = BundleService.getInstance(project)
        val hasBundleConfig = bundleService.hasBundleConfig()

        val dialog = DeployDialog(project, file.nameWithoutExtension, sourceCode, hasBundleConfig)
        if (dialog.showAndGet()) {
            val deployCode = if (dialog.shouldBundle) {
                val (result, bundled) = bundleService.bundleToString()
                if (!result.success || bundled == null) {
                    notify(project, "Bundle failed: ${result.error ?: result.stderr}", NotificationType.ERROR)
                    return
                }
                notify(project, "Bundled ${result.outputSize} bytes", NotificationType.INFORMATION)
                bundled
            } else {
                sourceCode
            }

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Deploying Keyscript...") {
                    override fun run(indicator: ProgressIndicator) {
                        val deployService = DeploymentService.getInstance(project)
                        val result = if (dialog.isUpdate && dialog.targetSerial.isNotBlank()) {
                            deployService.deployUpdate(
                                targetSerial = dialog.targetSerial,
                                sourceCode = deployCode,
                                description = dialog.description.ifBlank { null },
                                workAreaOption = dialog.workAreaOption.ifBlank { null }
                            )
                        } else {
                            deployService.deployNew(
                                sourceCode = deployCode,
                                description = dialog.description,
                                workAreaOption = dialog.workAreaOption
                            )
                        }

                        if (result.sessionExpired) {
                            notify(project, "Session expired. Please login and try again.", NotificationType.WARNING)
                        } else if (result.success) {
                            val msg = if (dialog.isUpdate) {
                                "Updated script #${dialog.targetSerial}"
                            } else {
                                "Deployed as script #${result.serial ?: "?"}"
                            }
                            notify(project, msg, NotificationType.INFORMATION)
                        } else {
                            notify(project, "Deploy failed: ${result.error}", NotificationType.ERROR)
                        }
                    }
                }
            )
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript Deploy", message, type)
            .notify(project)
    }
}

/**
 * Simplified deployment dialog for quick right-click deploys.
 * Searches by description to auto-detect existing scripts.
 */
private class DeployDialog(
    private val project: Project,
    private val fileName: String,
    private val sourceCode: String,
    private val hasBundleConfig: Boolean
) : DialogWrapper(project) {

    // Mode toggle
    private val newRadio = JRadioButton("New Script", true)
    private val updateRadio = JRadioButton("Update Existing")

    // Fields
    private val descriptionField = JBTextField(fileName)
    private val serialField = JBTextField("").apply { isEnabled = false }
    private val searchButton = JButton("Find Existing").apply {
        toolTipText = "Search for existing script by description"
    }
    private val searchStatusLabel = JBLabel("").apply {
        foreground = java.awt.Color.GRAY
    }
    private val workAreaCombo = ComboBox(arrayOf(
        "D — Development",
        "T — Test",
        "P — Production"
    )).apply { selectedIndex = 0 }
    private val bundleCheckbox = JCheckBox("Bundle before deploying", hasBundleConfig).apply {
        isEnabled = hasBundleConfig
    }

    val isUpdate: Boolean get() = updateRadio.isSelected
    val targetSerial: String get() = serialField.text.trim()
    val description: String get() = descriptionField.text.trim()
    val shouldBundle: Boolean get() = bundleCheckbox.isSelected
    val workAreaOption: String
        get() {
            val selected = workAreaCombo.selectedItem?.toString() ?: "D"
            return selected.substringBefore(" ").trim()
        }

    init {
        title = "Deploy Script"
        setOKButtonText("Deploy")
        init()

        val group = ButtonGroup()
        group.add(newRadio)
        group.add(updateRadio)
        newRadio.addActionListener { serialField.isEnabled = false }
        updateRadio.addActionListener { serialField.isEnabled = true }

        searchButton.addActionListener { searchForExistingScript() }
    }

    private fun searchForExistingScript() {
        val desc = descriptionField.text.trim()
        if (desc.isBlank()) {
            searchStatusLabel.text = "Enter a description first"
            return
        }

        searchButton.isEnabled = false
        searchStatusLabel.text = "Searching..."

        Thread({
            val deployService = DeploymentService.getInstance(project)
            val (results, error) = deployService.searchByDescription(desc)

            SwingUtilities.invokeLater {
                searchButton.isEnabled = true
                if (error != null) {
                    searchStatusLabel.text = "Search failed: $error"
                } else if (results.isEmpty()) {
                    searchStatusLabel.text = "No match — will create new"
                    newRadio.isSelected = true
                    serialField.isEnabled = false
                    serialField.text = ""
                } else {
                    val first = results.first()
                    searchStatusLabel.text = "Found: #${first.serial} — ${first.description}"
                    updateRadio.isSelected = true
                    serialField.isEnabled = true
                    serialField.text = first.serial
                    if (results.size > 1) {
                        searchStatusLabel.text = "${results.size} matches — using #${first.serial}"
                    }
                }
            }
        }, "keyscript-search").start()
    }

    override fun createCenterPanel(): JComponent {
        val modePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)).apply {
            add(newRadio)
            add(updateRadio)
        }

        val descPanel = JPanel(BorderLayout(4, 0)).apply {
            add(descriptionField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }

        val previewArea = JBTextArea().apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            lineWrap = true
            val lines = sourceCode.lines()
            text = if (lines.size > 15) {
                lines.take(15).joinToString("\n") + "\n... (${lines.size} total lines)"
            } else {
                sourceCode
            }
        }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Mode:"), modePanel, 1, false)
            .addLabeledComponent(JBLabel("Serial:"), serialField, 1, false)
            .addSeparator()
            .addLabeledComponent(JBLabel("Description:"), descPanel, 1, false)
            .addComponent(searchStatusLabel)
            .addLabeledComponent(JBLabel("Work Area:"), workAreaCombo, 1, false)
            .addComponent(bundleCheckbox)
            .addSeparator()
            .addLabeledComponent(JBLabel("Preview:"), JScrollPane(previewArea).apply {
                preferredSize = Dimension(480, 120)
            }, 1, true)
            .panel

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(520, 420)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = descriptionField
}
