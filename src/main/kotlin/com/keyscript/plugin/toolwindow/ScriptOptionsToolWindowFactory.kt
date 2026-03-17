package com.keyscript.plugin.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.AuthenticationService
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.ScriptParameterService
import com.keyscript.plugin.services.SessionService
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.net.HttpURLConnection
import java.net.URI
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Always-visible tool window for setting script execution parameters:
 * Instance dropdown, Person Serial (with search), Account Serial (with search).
 * Matches the original Keyscript IDE ScriptOptionsPanel behavior.
 */
class ScriptOptionsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val panel = ScriptOptionsPanel(project, scope)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

class ScriptOptionsPanel(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val log = Logger.getInstance(ScriptOptionsPanel::class.java)
    private val service = ScriptParameterService.getInstance(project)
    private val settings = KeyscriptSettings.getInstance()

    override fun dispose() {
        scope.cancel()
    }

    // Person search filters
    private val personFilters = linkedMapOf(
        "AUTO" to "Auto-detect",
        "BY_LAST_FIRST_MIDDLE_NAME" to "Name",
        "BY_ACCOUNT_NUMBER" to "Account Number",
        "BY_TIN" to "TIN/SSN",
        "BY_PHONE_NUMBER" to "Phone Number",
        "BY_EMAIL_ADDRESS" to "Email Address",
        "BY_CARD_NUMBER" to "Card Number",
        "BY_SERIAL" to "Serial"
    )

    // Account search filters
    private val accountFilters = linkedMapOf(
        "BY_ACCOUNT_NUMBER" to "Account Number",
        "BY_SERIAL" to "Serial",
        "BY_ACCOUNT_TITLE" to "Account Title",
        "BY_VIN" to "VIN",
        "BY_EXTERNAL_ACCOUNT_NUMBER" to "External Account Number"
    )

    // ─── Instance ────────────────────────────────
    private val instanceCombo = JComboBox(settings.supportedInstances.toTypedArray()).apply {
        isEditable = true
        selectedItem = service.instance.ifEmpty { settings.getDefaultInstance() }
        addActionListener {
            val newInstance = selectedItem?.toString() ?: ""
            val oldInstance = service.instance
            service.instance = newInstance
            // Auto-re-login when instance changes
            if (newInstance.isNotBlank() && newInstance != oldInstance) {
                switchInstance(newInstance)
            }
        }
    }

    // ─── Person Search ───────────────────────────
    private val personFilterCombo = JComboBox(personFilters.values.toTypedArray())
    private val personSearchField = JBTextField(service.personSerial).apply {
        emptyText.text = "Enter search query..."
    }
    private val personSearchButton = JButton("Search").apply {
        putClientProperty("JButton.buttonType", "segmented-only")
    }
    private val personResultList = JBList<SearchResult>()
    private val personResultModel = DefaultListModel<SearchResult>()
    private val personResultScroll = JBScrollPane(personResultList).apply {
        preferredSize = Dimension(Int.MAX_VALUE, JBUI.scale(120))
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(120))
        isVisible = false
    }

    // ─── Account Search ──────────────────────────
    private val accountFilterCombo = JComboBox(accountFilters.values.toTypedArray())
    private val accountSearchField = JBTextField(service.accountSerial).apply {
        emptyText.text = "Enter search query..."
    }
    private val accountSearchButton = JButton("Search").apply {
        putClientProperty("JButton.buttonType", "segmented-only")
    }
    private val accountResultList = JBList<SearchResult>()
    private val accountResultModel = DefaultListModel<SearchResult>()
    private val accountResultScroll = JBScrollPane(accountResultList).apply {
        preferredSize = Dimension(Int.MAX_VALUE, JBUI.scale(120))
        maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(120))
        isVisible = false
    }

    private val debugModeCheckbox = JBCheckBox("Debug Mode", service.debugMode).apply {
        toolTipText = "When enabled, passes debug=true as a URL parameter to RunScript"
        addActionListener { service.debugMode = isSelected }
    }

    private val clearButton = JButton("Clear Parameters")
    private val statusLabel = JBLabel(" ").apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }

    lateinit var component: JComponent
        private set

    init {
        personResultList.model = personResultModel
        accountResultList.model = accountResultModel

        // Person search actions
        personSearchButton.addActionListener { doPersonSearch() }
        personSearchField.addActionListener { doPersonSearch() }
        personResultList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = personResultList.selectedValue ?: return@addListSelectionListener
                service.personSerial = selected.serial
                personSearchField.text = selected.serial
                personResultScroll.isVisible = false
                statusLabel.text = "Person: ${selected.description} (#${selected.serial})"
                component.revalidate()
            }
        }

        // Account search actions
        accountSearchButton.addActionListener { doAccountSearch() }
        accountSearchField.addActionListener { doAccountSearch() }
        accountResultList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = accountResultList.selectedValue ?: return@addListSelectionListener
                service.accountSerial = selected.serial
                accountSearchField.text = selected.serial
                accountResultScroll.isVisible = false
                statusLabel.text = "Account: ${selected.description} (#${selected.serial})"
                component.revalidate()
            }
        }

        // Sync manual text edits to service
        personSearchField.addTextChangeListener { service.personSerial = personSearchField.text }
        accountSearchField.addTextChangeListener { service.accountSerial = accountSearchField.text }

        // Clear
        clearButton.addActionListener {
            personSearchField.text = ""
            accountSearchField.text = ""
            service.personSerial = ""
            service.accountSerial = ""
            debugModeCheckbox.isSelected = false
            service.debugMode = false
            personResultScroll.isVisible = false
            accountResultScroll.isVisible = false
            statusLabel.text = "Parameters cleared"
            component.revalidate()
        }

        component = buildLayout()
    }

    private fun buildLayout(): JComponent {
        // ─── Instance Section ────────────────────
        val instanceForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Instance:", instanceCombo)
            .panel

        // ─── Person Section ──────────────────────
        val personSearchRow = createSearchRow(personSearchField, personSearchButton)
        val personForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Filter:", personFilterCombo)
            .addLabeledComponent("Search:", personSearchRow)
            .panel

        // Wrapper that includes form + dynamic result list
        val personSection = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(personForm, BorderLayout.NORTH)
            add(personResultScroll, BorderLayout.CENTER)
        }

        // ─── Account Section ─────────────────────
        val accountSearchRow = createSearchRow(accountSearchField, accountSearchButton)
        val accountForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Filter:", accountFilterCombo)
            .addLabeledComponent("Search:", accountSearchRow)
            .panel

        val accountSection = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(accountForm, BorderLayout.NORTH)
            add(accountResultScroll, BorderLayout.CENTER)
        }

        // ─── Options Section ─────────────────────
        val optionsPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(debugModeCheckbox, BorderLayout.NORTH)
        }

        // ─── Footer (Clear + Status) ────────────
        val footerPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                anchor = GridBagConstraints.WEST
                insets = JBUI.insetsBottom(4)
            }
            add(clearButton, gbc)
            gbc.gridy = 1
            gbc.insets = JBUI.emptyInsets()
            add(statusLabel, gbc)
        }

        // ─── Assemble main panel ─────────────────
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(8, 12)

        mainPanel.add(createTitledSection("Instance", instanceForm))
        mainPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        mainPanel.add(createTitledSection("Person", personSection))
        mainPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        mainPanel.add(createTitledSection("Account", accountSection))
        mainPanel.add(Box.createVerticalStrut(JBUI.scale(4)))
        mainPanel.add(createTitledSection("Options", optionsPanel))
        mainPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        mainPanel.add(footerPanel.apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
        })
        mainPanel.add(Box.createVerticalGlue())

        return JBScrollPane(mainPanel).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }
    }

    /**
     * Creates a titled section with a [TitledSeparator] header and indented content.
     */
    private fun createTitledSection(title: String, content: JComponent): JPanel {
        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = JComponent.LEFT_ALIGNMENT
            add(TitledSeparator(title), BorderLayout.NORTH)
            // Indent content under the separator to align with the title text
            val wrapper = JPanel(BorderLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.emptyLeft(JBUI.scale(12))
                add(content, BorderLayout.CENTER)
            }
            add(wrapper, BorderLayout.CENTER)
        }
    }

    /**
     * Creates a search row with a text field and attached button, using a
     * BorderLayout so the field stretches and the button stays at a fixed width.
     */
    private fun createSearchRow(field: JBTextField, button: JButton): JPanel {
        return JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(field, BorderLayout.CENTER)
            add(button, BorderLayout.EAST)
        }
    }

    // ─── Instance Switch ─────────────────────────

    private fun switchInstance(instance: String) {
        val session = SessionService.getInstance(project)
        val creds = session.loadCredentials() ?: return
        log.info("Switching instance to $instance — auto-re-login")
        scope.launch(Dispatchers.IO) {
            try {
                AuthenticationService.getInstance(project).login(
                    username = creds.first,
                    password = creds.second,
                    instance = instance,
                    deviceId = settings.deviceServiceUrl
                )
            } catch (e: Exception) {
                log.warn("Instance switch login failed", e)
            }
        }
    }

    // ─── Search Logic ────────────────────────────

    private fun doPersonSearch() {
        val query = personSearchField.text.trim()
        if (query.isEmpty()) return

        var filterKey = getFilterKey(personFilterCombo, personFilters)
        if (filterKey == "AUTO") {
            filterKey = detectPersonFilter(query)
        }

        doSearch("PERSON", filterKey, query, personResultModel, personResultScroll)
    }

    private fun doAccountSearch() {
        val query = accountSearchField.text.trim()
        if (query.isEmpty()) return

        val filterKey = getFilterKey(accountFilterCombo, accountFilters)
        doSearch("ACCOUNT", filterKey, query, accountResultModel, accountResultScroll)
    }

    private fun getFilterKey(combo: JComboBox<String>, filters: LinkedHashMap<String, String>): String {
        val selectedLabel = combo.selectedItem?.toString() ?: return filters.keys.first()
        return filters.entries.find { it.value == selectedLabel }?.key ?: filters.keys.first()
    }

    private fun detectPersonFilter(query: String): String {
        return when {
            query.matches(Regex("""\d{3}-\d{2}-\d{4}""")) -> "BY_TIN"
            query.matches(Regex("""\d{2}-\d{7}""")) -> "BY_TIN"
            query.matches(Regex("""\d{3}[.-]\d{3}[.-]\d{4}""")) -> "BY_PHONE_NUMBER"
            query.matches(Regex("""\(\d{3}\)\d{3}-\d{4}""")) -> "BY_PHONE_NUMBER"
            query.contains("@") && query.contains(".") && !query.contains(" ") -> "BY_EMAIL_ADDRESS"
            query.all { it.isDigit() } -> "BY_ACCOUNT_NUMBER"
            else -> "BY_LAST_FIRST_MIDDLE_NAME"
        }
    }

    private fun doSearch(
        tableName: String,
        filterName: String,
        query: String,
        resultModel: DefaultListModel<SearchResult>,
        resultScroll: JBScrollPane
    ) {
        statusLabel.text = "Searching..."
        personSearchButton.isEnabled = false
        accountSearchButton.isEnabled = false
        resultModel.clear()

        scope.launch(Dispatchers.IO) {
            try {
                val xml = buildSearchXml(tableName, filterName, query)
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/SearchJSON", xml)
                val results = parseSearchResults(response)

                withContext(Dispatchers.Main) {
                    personSearchButton.isEnabled = true
                    accountSearchButton.isEnabled = true
                    resultModel.clear()
                    results.forEach { resultModel.addElement(it) }

                    if (results.size == 1) {
                        // Auto-select single result
                        val result = results[0]
                        if (tableName == "PERSON") {
                            service.personSerial = result.serial
                            personSearchField.text = result.serial
                            resultScroll.isVisible = false
                        } else {
                            service.accountSerial = result.serial
                            accountSearchField.text = result.serial
                            resultScroll.isVisible = false
                        }
                        statusLabel.text = "${result.description} (#${result.serial})"
                    } else {
                        resultScroll.isVisible = results.isNotEmpty()
                        statusLabel.text = "${results.size} result(s)"
                    }
                    component.revalidate()
                }
            } catch (e: Exception) {
                log.warn("Search failed", e)
                withContext(Dispatchers.Main) {
                    personSearchButton.isEnabled = true
                    accountSearchButton.isEnabled = true
                    statusLabel.text = "Search error: ${e.message}"
                }
            }
        }
    }

    // ─── XML / HTTP helpers ──────────────────────

    private fun buildSearchXml(tableName: String, filterName: String, query: String): String {
        val ns = "http://www.corelationinc.com/queryLanguage/v1.0"
        return """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:search>
          <v1:tableName>$tableName</v1:tableName>
          <v1:filterName>$filterName</v1:filterName>
          <v1:includeSelectColumns option="Y"/>
          <v1:includeTotalHitCount option="Y"/>
          <v1:returnLimit>20</v1:returnLimit>
          <v1:parameter>
            <v1:contents>${escapeXml(query)}</v1:contents>
          </v1:parameter>
        </v1:search>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""
    }

    private fun postXml(url: String, xml: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml")
        conn.doOutput = true
        conn.outputStream.use { it.write(xml.toByteArray()) }
        return conn.inputStream.bufferedReader().readText()
    }

    private data class SearchResult(val serial: String, val description: String) {
        override fun toString() = "$description  (#$serial)"
    }

    private fun parseSearchResults(json: String): List<SearchResult> {
        return SearchJsonParsers.parseRows(json).map { row ->
            SearchResult(row.serial, row.description)
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun JBTextField.addTextChangeListener(onUpdate: () -> Unit) {
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onUpdate()
            override fun removeUpdate(e: DocumentEvent?) = onUpdate()
            override fun changedUpdate(e: DocumentEvent?) = onUpdate()
        })
    }
}
