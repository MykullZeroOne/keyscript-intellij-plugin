package com.keyscript.plugin.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.keyscript.plugin.services.BundleService
import com.keyscript.plugin.services.DeploymentService
import com.keyscript.plugin.services.InstalledScriptsService
import com.keyscript.plugin.services.SessionService
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Panel for browsing, viewing, downloading, deploying, and deleting server-side scripts
 * from the Keystone SCRIPT table.
 */
class InstalledScriptsPanel(private val project: Project) {
    private val log = Logger.getInstance(InstalledScriptsPanel::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Toolbar
    private val searchField = JBTextField().apply {
        emptyText.text = "Search scripts by description..."
        columns = 25
    }
    private val searchButton = JButton("Search", AllIcons.Actions.Search)
    private val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)
    private val downloadButton = JButton("Download", AllIcons.Actions.Download).apply { isEnabled = false }
    private val updateButton = JButton("Update", AllIcons.Actions.Upload).apply { isEnabled = false }
    private val installButton = JButton("Install", AllIcons.General.Add)
    private val deleteButton = JButton("Delete", AllIcons.Vcs.Remove).apply { isEnabled = false }

    // Script table
    private val tableModel = object : DefaultTableModel(
        arrayOf("Serial", "Description", "Language", "Category", "Work Area"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val scriptTable = JBTable(tableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        columnModel.getColumn(0).preferredWidth = 70
        columnModel.getColumn(1).preferredWidth = 300
        columnModel.getColumn(2).preferredWidth = 70
        columnModel.getColumn(3).preferredWidth = 70
        columnModel.getColumn(4).preferredWidth = 80
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    // Source code preview — styled to match editor theme
    private val sourceArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
        lineWrap = false
        val scheme = EditorColorsManager.getInstance().globalScheme
        background = scheme.defaultBackground
        foreground = scheme.defaultForeground
        border = JBUI.Borders.empty(4)
    }

    private val statusLabel = JBLabel(" ").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
        border = JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(4))
    }

    // Cached source for download
    private var currentSourceCode: String = ""
    private var currentDescription: String = ""
    private var currentSerial: String = ""

    private val contentCardLayout = CardLayout()
    private val contentCardPanel = JPanel(contentCardLayout)
    private val emptyState = EmptyStatePanel(
        message = "No scripts loaded",
        detail = "Search to browse installed Keystone scripts"
    )

    val component: JComponent

    init {
        // Wire up actions
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }
        refreshButton.addActionListener { doSearch() }
        downloadButton.addActionListener { doDownload() }
        updateButton.addActionListener { doUpdate() }
        installButton.addActionListener { doInstall() }
        deleteButton.addActionListener { doDelete() }

        // Table selection drives source preview and button state
        scriptTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && scriptTable.selectedRow >= 0) {
                val serial = tableModel.getValueAt(scriptTable.selectedRow, 0).toString()
                downloadButton.isEnabled = true
                updateButton.isEnabled = true
                deleteButton.isEnabled = true
                loadScriptSource(serial)
            } else if (scriptTable.selectedRow < 0) {
                downloadButton.isEnabled = false
                updateButton.isEnabled = false
                deleteButton.isEnabled = false
                sourceArea.text = ""
                currentSourceCode = ""
                currentDescription = ""
                currentSerial = ""
            }
        }

        // Build toolbar with IntelliJ-style spacing and border
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(6))
            )
            add(searchField)
            add(Box.createHorizontalStrut(JBUI.scale(6)))
            add(searchButton)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(refreshButton)
            add(Box.createHorizontalStrut(JBUI.scale(16)))
            add(JSeparator(SwingConstants.VERTICAL).apply {
                maximumSize = Dimension(JBUI.scale(2), JBUI.scale(24))
            })
            add(Box.createHorizontalStrut(JBUI.scale(16)))
            add(downloadButton)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(updateButton)
            add(Box.createHorizontalStrut(JBUI.scale(4)))
            add(installButton)
            add(Box.createHorizontalStrut(JBUI.scale(16)))
            add(JSeparator(SwingConstants.VERTICAL).apply {
                maximumSize = Dimension(JBUI.scale(2), JBUI.scale(24))
            })
            add(Box.createHorizontalStrut(JBUI.scale(16)))
            add(deleteButton)
            add(Box.createHorizontalGlue())
        }

        // Constrain search field height within toolbar
        searchField.maximumSize = Dimension(
            searchField.preferredSize.width,
            searchField.preferredSize.height
        )

        // Source preview with styled section header
        val sourceHeaderLabel = JBLabel("Source Code:").apply {
            font = JBUI.Fonts.label().asBold()
            border = JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(2), JBUI.scale(4), 0)
        }

        val sourceScrollPane = JBScrollPane(sourceArea).apply {
            border = JBUI.Borders.customLine(JBColor.border())
        }

        val sourcePanel = JPanel(BorderLayout(0, 0)).apply {
            add(sourceHeaderLabel, BorderLayout.NORTH)
            add(sourceScrollPane, BorderLayout.CENTER)
            border = JBUI.Borders.empty(JBUI.scale(4))
        }

        // Split: table on top, source on bottom
        val splitPane = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JBScrollPane(scriptTable),
            sourcePanel
        ).apply {
            resizeWeight = 0.5
            dividerLocation = JBUI.scale(200)
            border = JBUI.Borders.empty()
        }

        contentCardPanel.add(emptyState, "empty")
        contentCardPanel.add(splitPane, "content")
        contentCardLayout.show(contentCardPanel, "empty")

        // Status bar with top border
        val statusBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.empty()
            )
            add(statusLabel, BorderLayout.WEST)
        }

        component = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(contentCardPanel, BorderLayout.CENTER)
            add(statusBar, BorderLayout.SOUTH)
        }
    }

    // ─── Active editor helper ─────────────────────────

    /**
     * Returns the source code from the currently active editor, or null if no editor is open.
     */
    private fun getActiveEditorContent(): Pair<String, String>? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        return document.text to file.nameWithoutExtension
    }

    // ─── Search ───────────────────────────────────────

    private fun doSearch() {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            statusLabel.text = "Not logged in. Please login first."
            return
        }

        val searchTerm = searchField.text.trim()
        searchButton.isEnabled = false
        refreshButton.isEnabled = false
        statusLabel.text = "Searching..."
        tableModel.rowCount = 0
        sourceArea.text = ""
        currentSourceCode = ""
        currentDescription = ""
        currentSerial = ""
        downloadButton.isEnabled = false
        updateButton.isEnabled = false
        deleteButton.isEnabled = false

        scope.launch {
            val service = InstalledScriptsService.getInstance(project)
            val result = service.listScripts(searchTerm)

            SwingUtilities.invokeLater {
                if (result.success && result.data != null) {
                    for (script in result.data) {
                        tableModel.addRow(arrayOf(
                            script.serial,
                            script.description,
                            script.language,
                            script.category,
                            script.workAreaOption
                        ))
                    }
                    contentCardLayout.show(contentCardPanel, if (result.data.isNotEmpty()) "content" else "empty")
                    statusLabel.text = "${result.data.size} script(s) found"
                } else {
                    statusLabel.text = "Error: ${result.error ?: "Unknown error"}"
                }
                searchButton.isEnabled = true
                refreshButton.isEnabled = true
            }
        }
    }

    private fun loadScriptSource(serial: String) {
        statusLabel.text = "Loading script #$serial..."
        sourceArea.text = "Loading..."

        scope.launch {
            val service = InstalledScriptsService.getInstance(project)
            val result = service.viewScript(serial)

            SwingUtilities.invokeLater {
                if (result.success && result.data != null) {
                    currentSourceCode = result.data.sourceCode
                    currentDescription = result.data.description
                    currentSerial = serial
                    sourceArea.text = currentSourceCode
                    sourceArea.caretPosition = 0
                    statusLabel.text = "Script #$serial — ${result.data.description}"
                } else {
                    sourceArea.text = ""
                    currentSourceCode = ""
                    currentDescription = ""
                    currentSerial = ""
                    statusLabel.text = "Failed to load script: ${result.error ?: "Unknown error"}"
                }
            }
        }
    }

    // ─── Download ─────────────────────────────────────

    private fun doDownload() {
        if (currentSourceCode.isEmpty()) {
            statusLabel.text = "No source code to download"
            return
        }

        val descriptor = FileSaverDescriptor(
            "Save Script",
            "Save the script source code to a file",
            "js"
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

        // Suggest a filename based on the description
        val suggestedName = currentDescription
            .replace(Regex("[^a-zA-Z0-9_\\-. ]"), "")
            .replace(" ", "_")
            .ifEmpty { "script" } + ".js"

        val projectDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val wrapper = dialog.save(projectDir, suggestedName)
        if (wrapper != null) {
            try {
                val file = File(wrapper.file.path)
                file.writeText(currentSourceCode)
                statusLabel.text = "Saved to ${file.name}"

                // Refresh VFS so IntelliJ sees the new file
                LocalFileSystem.getInstance().refreshAndFindFileByPath(file.absolutePath)
            } catch (e: Exception) {
                log.warn("Failed to save script file", e)
                statusLabel.text = "Failed to save: ${e.message}"
            }
        }
    }

    // ─── Deploy Update ────────────────────────────────

    private fun doUpdate() {
        val selectedRow = scriptTable.selectedRow
        if (selectedRow < 0) return

        val serial = tableModel.getValueAt(selectedRow, 0).toString()
        val desc = tableModel.getValueAt(selectedRow, 1).toString()

        val editorContent = getActiveEditorContent()
        if (editorContent == null) {
            statusLabel.text = "No editor file open — open a script file to deploy"
            return
        }
        val (sourceCode, fileName) = editorContent

        // Check for bundling
        val bundleService = BundleService.getInstance(project)
        val hasBundleConfig = bundleService.hasBundleConfig()

        val dialog = UpdateConfirmDialog(project, serial, desc, fileName, sourceCode, hasBundleConfig)
        if (!dialog.showAndGet()) return

        val deployCode = if (dialog.shouldBundle) {
            val (result, bundled) = bundleService.bundleToString()
            if (!result.success || bundled == null) {
                statusLabel.text = "Bundle failed: ${result.error ?: result.stderr}"
                return
            }
            bundled
        } else {
            sourceCode
        }

        updateButton.isEnabled = false
        statusLabel.text = "Deploying update to #$serial..."

        scope.launch {
            val deployService = DeploymentService.getInstance(project)
            val result = deployService.deployUpdate(
                targetSerial = serial,
                sourceCode = deployCode
            )

            SwingUtilities.invokeLater {
                if (result.sessionExpired) {
                    statusLabel.text = "Session expired. Please login and try again."
                } else if (result.success) {
                    statusLabel.text = "Updated script #$serial successfully"
                    notify("Updated script #$serial ($desc)", NotificationType.INFORMATION)
                    // Reload source to show the updated code on server
                    loadScriptSource(serial)
                } else {
                    statusLabel.text = "Update failed: ${result.error}"
                    notify("Deploy failed: ${result.error}", NotificationType.ERROR)
                }
                updateButton.isEnabled = true
            }
        }
    }

    // ─── Deploy Install (New) ─────────────────────────

    private fun doInstall() {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            statusLabel.text = "Not logged in. Please login first."
            return
        }

        val editorContent = getActiveEditorContent()
        if (editorContent == null) {
            statusLabel.text = "No editor file open — open a script file to install"
            return
        }
        val (sourceCode, fileName) = editorContent

        val bundleService = BundleService.getInstance(project)
        val hasBundleConfig = bundleService.hasBundleConfig()

        val dialog = InstallNewDialog(project, fileName, sourceCode, hasBundleConfig)
        if (!dialog.showAndGet()) return

        val deployCode = if (dialog.shouldBundle) {
            val (result, bundled) = bundleService.bundleToString()
            if (!result.success || bundled == null) {
                statusLabel.text = "Bundle failed: ${result.error ?: result.stderr}"
                return
            }
            bundled
        } else {
            sourceCode
        }

        installButton.isEnabled = false
        statusLabel.text = "Installing new script..."

        scope.launch {
            val deployService = DeploymentService.getInstance(project)
            val result = deployService.deployNew(
                sourceCode = deployCode,
                description = dialog.description,
                workAreaOption = dialog.workAreaOption
            )

            SwingUtilities.invokeLater {
                if (result.sessionExpired) {
                    statusLabel.text = "Session expired. Please login and try again."
                } else if (result.success) {
                    val newSerial = result.serial ?: "?"
                    statusLabel.text = "Installed as script #$newSerial"
                    notify("Installed script #$newSerial (${dialog.description})", NotificationType.INFORMATION)
                    // Refresh list to show the new script
                    doSearch()
                } else {
                    statusLabel.text = "Install failed: ${result.error}"
                    notify("Install failed: ${result.error}", NotificationType.ERROR)
                }
                installButton.isEnabled = true
            }
        }
    }

    // ─── Delete ───────────────────────────────────────

    private fun doDelete() {
        val selectedRow = scriptTable.selectedRow
        if (selectedRow < 0) return

        val serial = tableModel.getValueAt(selectedRow, 0).toString()
        val desc = tableModel.getValueAt(selectedRow, 1).toString()

        val confirm = JOptionPane.showConfirmDialog(
            component,
            "Delete script #$serial ($desc)?\n\nThis action cannot be undone.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        if (confirm != JOptionPane.YES_OPTION) return

        deleteButton.isEnabled = false
        statusLabel.text = "Deleting script #$serial..."

        scope.launch {
            val service = InstalledScriptsService.getInstance(project)
            val result = service.deleteScript(serial)

            SwingUtilities.invokeLater {
                if (result.success) {
                    statusLabel.text = "Script #$serial deleted"
                    sourceArea.text = ""
                    currentSourceCode = ""
                    currentDescription = ""
                    currentSerial = ""
                    // Refresh the list
                    doSearch()
                } else {
                    statusLabel.text = "Delete failed: ${result.error ?: "Unknown error"}"
                    deleteButton.isEnabled = true
                }
            }
        }
    }

    // ─── Notifications ────────────────────────────────

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript", message, type)
            .notify(project)
    }
}

// ─── Dialogs ──────────────────────────────────────────

/**
 * Confirmation dialog for updating an existing script.
 * Shows the target serial/description, source file name, and optional bundling.
 */
private class UpdateConfirmDialog(
    project: Project,
    private val serial: String,
    private val description: String,
    private val fileName: String,
    private val sourceCode: String,
    private val hasBundleConfig: Boolean
) : DialogWrapper(project) {

    private val bundleCheckbox = JCheckBox("Bundle before deploying (esbuild)", hasBundleConfig).apply {
        isEnabled = hasBundleConfig
    }

    val shouldBundle: Boolean get() = bundleCheckbox.isSelected

    init {
        title = "Update Script"
        setOKButtonText("Deploy Update")
        init()
    }

    override fun createCenterPanel(): JComponent {
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
            .addLabeledComponent(JBLabel("Target:"), JBLabel("#$serial — $description"))
            .addLabeledComponent(JBLabel("Source:"), JBLabel("$fileName (active editor)"))
            .addComponent(bundleCheckbox)
            .addSeparator()
            .addLabeledComponent(JBLabel("Preview:"), JBScrollPane(previewArea).apply {
                preferredSize = Dimension(480, 150)
            }, 1, true)
            .panel

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(520, 340)
        }
    }
}

/**
 * Dialog for installing a new script.
 * Prompts for description (defaults to file name) and work area option.
 */
private class InstallNewDialog(
    project: Project,
    private val fileName: String,
    private val sourceCode: String,
    private val hasBundleConfig: Boolean
) : DialogWrapper(project) {

    private val descriptionField = JBTextField(fileName).apply { columns = 30 }
    private val workAreaCombo = ComboBox(arrayOf(
        "D — Development",
        "T — Test",
        "P — Production"
    )).apply {
        // Default based on current instance
        selectedIndex = 0
    }
    private val bundleCheckbox = JCheckBox("Bundle before deploying (esbuild)", hasBundleConfig).apply {
        isEnabled = hasBundleConfig
    }

    val description: String get() = descriptionField.text.trim()
    val shouldBundle: Boolean get() = bundleCheckbox.isSelected
    val workAreaOption: String
        get() {
            val selected = workAreaCombo.selectedItem?.toString() ?: "D"
            return selected.substringBefore(" ").trim()
        }

    init {
        title = "Install New Script"
        setOKButtonText("Install")
        init()
    }

    override fun createCenterPanel(): JComponent {
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
            .addLabeledComponent(JBLabel("Description:"), descriptionField, 1, false)
            .addLabeledComponent(JBLabel("Work Area:"), workAreaCombo, 1, false)
            .addLabeledComponent(JBLabel("Source:"), JBLabel("$fileName (active editor)"))
            .addComponent(bundleCheckbox)
            .addSeparator()
            .addLabeledComponent(JBLabel("Preview:"), JBScrollPane(previewArea).apply {
                preferredSize = Dimension(480, 150)
            }, 1, true)
            .panel

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(520, 380)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = descriptionField
}
