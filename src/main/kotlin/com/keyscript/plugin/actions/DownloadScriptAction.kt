package com.keyscript.plugin.actions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.DeploymentService
import com.keyscript.plugin.services.ScriptParameterService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettings
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*

/**
 * Downloads a deployed script from the Keystone SCRIPT table into the local project.
 * Users can search by description or enter a serial number directly, preview the
 * source code, and save to a chosen location.
 */
class DownloadScriptAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            notify(project, "Please login to Keystone first.", NotificationType.WARNING)
            return
        }

        val dialog = DownloadScriptDialog(project)
        if (dialog.showAndGet()) {
            val sourceCode = dialog.sourceCode
            val targetFile = dialog.targetFile
            if (sourceCode.isNullOrBlank() || targetFile == null) {
                notify(project, "No source code or file location selected.", NotificationType.WARNING)
                return
            }

            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Saving downloaded script...") {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            targetFile.parentFile?.mkdirs()
                            targetFile.writeText(sourceCode)

                            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
                            if (vFile != null) {
                                SwingUtilities.invokeLater {
                                    FileEditorManager.getInstance(project).openFile(vFile, true)
                                }
                            }
                            notify(project, "Script saved to ${targetFile.name}", NotificationType.INFORMATION)
                        } catch (ex: Exception) {
                            notify(project, "Failed to save script: ${ex.message}", NotificationType.ERROR)
                        }
                    }
                }
            )
        }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript Download", message, type)
            .notify(project)
    }
}

/**
 * Dialog for downloading a script from Keystone.
 * Provides search by description, direct serial entry, source code preview,
 * and file save location selection.
 */
private class DownloadScriptDialog(
    private val project: Project
) : DialogWrapper(project) {

    private val log = Logger.getInstance(DownloadScriptDialog::class.java)
    private val mapper = jacksonObjectMapper()

    // Search controls
    private val searchField = JBTextField()
    private val searchButton = JButton("Search")
    private val resultListModel = DefaultListModel<ScriptListEntry>()
    private val resultList = JBList(resultListModel)
    private val resultScroll = JBScrollPane(resultList).apply {
        preferredSize = Dimension(500, 120)
    }

    // Serial direct entry
    private val serialField = JBTextField()
    private val fetchButton = JButton("Fetch")

    // Preview
    private val previewArea = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11)
        lineWrap = true
    }
    private val previewScroll = JBScrollPane(previewArea).apply {
        preferredSize = Dimension(500, 200)
    }

    // File save
    private val fileNameField = JBTextField()
    private val saveLocationField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Save Location",
            "Choose directory to save the script",
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    // Status
    private val statusLabel = JBLabel(" ").apply {
        foreground = java.awt.Color.GRAY
    }

    // Results
    var sourceCode: String? = null
        private set
    val targetFile: File?
        get() {
            val name = fileNameField.text.trim()
            val dir = saveLocationField.text.trim()
            if (name.isBlank() || dir.isBlank()) return null
            return File(dir, name)
        }

    init {
        title = "Download Script from Keystone"
        setOKButtonText("Save")
        isOKActionEnabled = false
        init()

        // Default save location to project root
        saveLocationField.text = project.basePath ?: ""

        // Wire up search
        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }

        // Wire up fetch by serial
        fetchButton.addActionListener { doFetchBySerial() }
        serialField.addActionListener { doFetchBySerial() }

        // Wire up result selection
        resultList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = resultList.selectedValue ?: return@addListSelectionListener
                serialField.text = selected.serial
                doFetchBySerial()
            }
        }
    }

    private fun doSearch() {
        val query = searchField.text.trim()
        if (query.isBlank()) {
            statusLabel.text = "Enter a search term"
            return
        }

        searchButton.isEnabled = false
        statusLabel.text = "Searching..."
        resultListModel.clear()

        Thread({
            val deployService = DeploymentService.getInstance(project)
            val (results, error) = deployService.searchByDescription(query)

            SwingUtilities.invokeLater {
                searchButton.isEnabled = true
                if (error != null) {
                    statusLabel.text = "Search failed: $error"
                } else if (results.isEmpty()) {
                    statusLabel.text = "No scripts found"
                } else {
                    resultListModel.clear()
                    results.forEach { r ->
                        resultListModel.addElement(ScriptListEntry(r.serial, r.description))
                    }
                    statusLabel.text = "${results.size} script(s) found"
                }
            }
        }, "keyscript-download-search").start()
    }

    private fun doFetchBySerial() {
        val serial = serialField.text.trim()
        if (serial.isBlank()) {
            statusLabel.text = "Enter a serial number"
            return
        }

        fetchButton.isEnabled = false
        statusLabel.text = "Fetching script #$serial..."

        Thread({
            val result = fetchScriptSource(serial)

            SwingUtilities.invokeLater {
                fetchButton.isEnabled = true
                if (result.error != null) {
                    statusLabel.text = "Fetch failed: ${result.error}"
                    previewArea.text = ""
                    sourceCode = null
                    isOKActionEnabled = false
                } else {
                    sourceCode = result.sourceCode
                    previewArea.text = result.sourceCode ?: ""
                    previewArea.caretPosition = 0

                    // Set default filename from description
                    val desc = result.description
                    if (desc != null && fileNameField.text.isBlank()) {
                        val safeName = desc.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
                            .replace(Regex("\\s+"), "-")
                            .lowercase()
                        fileNameField.text = "$safeName.keyscript.js"
                    }

                    statusLabel.text = "Script #$serial loaded (${result.sourceCode?.length ?: 0} chars)"
                    isOKActionEnabled = result.sourceCode?.isNotBlank() == true
                }
            }
        }, "keyscript-download-fetch").start()
    }

    private data class FetchResult(
        val sourceCode: String? = null,
        val description: String? = null,
        val error: String? = null
    )

    /**
     * Fetch a script record using the View operation on the SCRIPT table.
     * Extracts SOURCE_CODE and DESCRIPTION from the response fields.
     */
    private fun fetchScriptSource(serial: String): FetchResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return FetchResult(error = "Not logged in")
        }

        val body = buildViewJson(session.apiSessionId, serial)
        val (responseBody, error) = postToKeystone(body)
        if (error != null) return FetchResult(error = error)

        return try {
            val json = mapper.readTree(responseBody)

            // Check for exceptions in response
            val exceptionMsg = extractException(json)
            if (exceptionMsg != null) {
                return FetchResult(error = exceptionMsg)
            }

            // Find the record node and extract fields
            val record = findDeep(json, "record")
            if (record == null) {
                return FetchResult(error = "No record found in response")
            }

            val fields = record.get("field")
            if (fields == null) {
                return FetchResult(error = "No fields found in record")
            }

            val fieldList = if (fields.isArray) fields.toList() else listOf(fields)

            var sourceCodeValue: String? = null
            var descriptionValue: String? = null

            for (field in fieldList) {
                val columnName = field.path("columnName").asText("")
                val contents = field.path("contents").asText("")
                when (columnName) {
                    "SOURCE_CODE" -> sourceCodeValue = contents
                    "DESCRIPTION" -> descriptionValue = contents
                }
            }

            if (sourceCodeValue.isNullOrBlank()) {
                // Also check rowDescription as a fallback for description
                if (descriptionValue == null) {
                    descriptionValue = record.path("rowDescription").asText(null)
                }
                return FetchResult(error = "Script #$serial has no source code")
            }

            if (descriptionValue == null) {
                descriptionValue = record.path("rowDescription").asText(null)
            }

            FetchResult(sourceCode = sourceCodeValue, description = descriptionValue)
        } catch (e: Exception) {
            log.warn("Failed to parse script view response", e)
            FetchResult(error = "Failed to parse response: ${e.message}")
        }
    }

    private fun buildViewJson(sessionId: String, targetSerial: String): String {
        val record = linkedMapOf<String, Any>(
            "\$attr" to mapOf("label" to "Main"),
            "operation" to mapOf("option" to "V"),
            "includeAllColumns" to mapOf("option" to "Y"),
            "includeRowDescriptions" to mapOf("option" to "Y"),
            "tableName" to "SCRIPT",
            "targetSerial" to targetSerial
        )

        val query = linkedMapOf<String, Any>(
            "\$attr" to mapOf("sessionId" to sessionId),
            "sequence" to mapOf(
                "transaction" to mapOf(
                    "step" to mapOf(
                        "record" to record
                    )
                )
            )
        )

        return mapper.writeValueAsString(mapOf("query" to query))
    }

    private fun getKeystoneUrl(): String {
        val settings = KeyscriptSettings.getInstance()
        val instance = ScriptParameterService.getInstance(project).instance.ifEmpty {
            settings.getDefaultInstance()
        }
        val baseUrl = settings.getKeystoneApiBaseUrl()
        return "$baseUrl/$instance"
    }

    private fun postToKeystone(jsonBody: String): Pair<String?, String?> {
        val url = getKeystoneUrl()

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            val session = SessionService.getInstance(project)
            conn.setRequestProperty("Cookie", "JSESSIONID=${session.apiSessionId}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }

            val status = conn.responseCode
            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            // Detect session expiry
            if (status == 401 || status == 403) {
                SessionService.getInstance(project).handleSessionExpired()
                return null to "Session expired"
            }
            val lower = responseBody.lowercase()
            if (lower.contains("session") && (lower.contains("expired") || lower.contains("invalid"))) {
                SessionService.getInstance(project).handleSessionExpired()
                return null to "Session expired"
            }

            if (status !in 200..299) {
                return null to "HTTP $status: ${responseBody.take(300)}"
            }

            SessionService.getInstance(project).recordSuccessfulActivity()
            responseBody to null
        } catch (e: Exception) {
            log.error("Keystone API call failed", e)
            null to "Connection failed: ${e.message}"
        }
    }

    private fun extractException(json: JsonNode): String? {
        val exceptions = mutableListOf<String>()
        findAllDeep(json, "exception") { node ->
            val msg = node.path("message").asText("")
            if (msg.isNotEmpty()) exceptions.add(msg)
        }
        return if (exceptions.isNotEmpty()) exceptions.joinToString("\n") else null
    }

    private fun findDeep(node: JsonNode, key: String): JsonNode? {
        if (node.has(key)) return node.get(key)
        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findDeep(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAllDeep(node: JsonNode, key: String, action: (JsonNode) -> Unit) {
        if (node.has(key)) {
            val target = node.get(key)
            if (target.isArray) target.forEach(action) else action(target)
        }
        for (child in node) {
            if (child.isObject || child.isArray) findAllDeep(child, key, action)
        }
    }

    override fun createCenterPanel(): JComponent {
        // Search section
        val searchPanel = JPanel(BorderLayout(4, 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
        }

        // Serial section
        val serialPanel = JPanel(BorderLayout(4, 0)).apply {
            add(serialField, BorderLayout.CENTER)
            add(fetchButton, BorderLayout.EAST)
        }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Search by description:"), searchPanel, 1, false)
            .addLabeledComponent(JBLabel("Results:"), resultScroll, 1, true)
            .addSeparator()
            .addLabeledComponent(JBLabel("Script serial:"), serialPanel, 1, false)
            .addComponent(statusLabel)
            .addSeparator()
            .addLabeledComponent(JBLabel("Source preview:"), previewScroll, 1, true)
            .addSeparator()
            .addLabeledComponent(JBLabel("File name:"), fileNameField, 1, false)
            .addLabeledComponent(JBLabel("Save to:"), saveLocationField, 1, false)
            .panel

        return JPanel(BorderLayout()).apply {
            add(form, BorderLayout.CENTER)
            border = JBUI.Borders.empty(8)
            preferredSize = Dimension(600, 560)
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField

    private data class ScriptListEntry(val serial: String, val description: String) {
        override fun toString() = "#$serial — $description"
    }
}
