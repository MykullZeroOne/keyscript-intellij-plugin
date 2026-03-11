package com.keyscript.plugin.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.ScriptParameterService
import com.keyscript.plugin.settings.KeyscriptSettings
import java.awt.BorderLayout
import java.awt.Dimension
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
        val panel = ScriptOptionsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ScriptOptionsPanel(private val project: Project) {
    private val log = Logger.getInstance(ScriptOptionsPanel::class.java)
    private val service = ScriptParameterService.getInstance(project)
    private val settings = KeyscriptSettings.getInstance()

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
        addActionListener { service.instance = selectedItem?.toString() ?: "" }
    }

    // ─── Person Search ───────────────────────────
    private val personFilterCombo = JComboBox(personFilters.values.toTypedArray())
    private val personSearchField = JBTextField(service.personSerial)
    private val personSearchButton = JButton("Search")
    private val personResultList = JBList<SearchResult>()
    private val personResultModel = DefaultListModel<SearchResult>()
    private val personResultScroll = JBScrollPane(personResultList).apply {
        preferredSize = Dimension(Int.MAX_VALUE, 120)
        maximumSize = Dimension(Int.MAX_VALUE, 120)
        isVisible = false
    }

    // ─── Account Search ──────────────────────────
    private val accountFilterCombo = JComboBox(accountFilters.values.toTypedArray())
    private val accountSearchField = JBTextField(service.accountSerial)
    private val accountSearchButton = JButton("Search")
    private val accountResultList = JBList<SearchResult>()
    private val accountResultModel = DefaultListModel<SearchResult>()
    private val accountResultScroll = JBScrollPane(accountResultList).apply {
        preferredSize = Dimension(Int.MAX_VALUE, 120)
        maximumSize = Dimension(Int.MAX_VALUE, 120)
        isVisible = false
    }

    private val clearButton = JButton("Clear Parameters")
    private val statusLabel = JBLabel(" ")

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
            personResultScroll.isVisible = false
            accountResultScroll.isVisible = false
            statusLabel.text = "Parameters cleared"
            component.revalidate()
        }

        // Build layout
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)
        mainPanel.border = JBUI.Borders.empty(8)

        // Instance row
        mainPanel.add(createLabeledRow("Instance:", instanceCombo))
        mainPanel.add(Box.createVerticalStrut(8))
        mainPanel.add(JSeparator())
        mainPanel.add(Box.createVerticalStrut(8))

        // Person section
        mainPanel.add(JBLabel("Person Serial:"))
        mainPanel.add(Box.createVerticalStrut(2))
        mainPanel.add(createLabeledRow("Filter:", personFilterCombo))
        mainPanel.add(Box.createVerticalStrut(2))
        mainPanel.add(createSearchRow(personSearchField, personSearchButton))
        mainPanel.add(personResultScroll)
        mainPanel.add(Box.createVerticalStrut(8))
        mainPanel.add(JSeparator())
        mainPanel.add(Box.createVerticalStrut(8))

        // Account section
        mainPanel.add(JBLabel("Account Serial:"))
        mainPanel.add(Box.createVerticalStrut(2))
        mainPanel.add(createLabeledRow("Filter:", accountFilterCombo))
        mainPanel.add(Box.createVerticalStrut(2))
        mainPanel.add(createSearchRow(accountSearchField, accountSearchButton))
        mainPanel.add(accountResultScroll)
        mainPanel.add(Box.createVerticalStrut(8))

        // Clear + status
        mainPanel.add(clearButton)
        mainPanel.add(Box.createVerticalStrut(4))
        mainPanel.add(statusLabel)

        // Fill remaining space
        mainPanel.add(Box.createVerticalGlue())

        component = JBScrollPane(mainPanel)
    }

    private fun createLabeledRow(label: String, field: JComponent): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(JBLabel(label), BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun createSearchRow(field: JBTextField, button: JButton): JPanel {
        return JPanel(BorderLayout(4, 0)).apply {
            add(field, BorderLayout.CENTER)
            add(button, BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
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
        resultModel.clear()

        Thread({
            try {
                val xml = buildSearchXml(tableName, filterName, query)
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/SearchJSON", xml)
                val results = parseSearchResults(response)

                SwingUtilities.invokeLater {
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
                SwingUtilities.invokeLater {
                    statusLabel.text = "Search error: ${e.message}"
                }
            }
        }, "keyscript-search").start()
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
