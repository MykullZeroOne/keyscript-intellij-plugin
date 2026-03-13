package com.keyscript.plugin.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.ScriptParameterService
import com.keyscript.plugin.settings.KeyscriptSettings
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Person/Account search tool window — sends SearchJSON requests via the proxy.
 */
class SearchToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = SearchPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class SearchPanel(private val project: Project) {
    private val log = Logger.getInstance(SearchPanel::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val searchTypeCombo = JComboBox(arrayOf("Person", "Account"))
    private val filterCombo = JComboBox<String>()
    private val searchField = JBTextField()
    private val searchButton = JButton("Search")

    private val resultModel = DefaultTableModel(arrayOf("Serial", "Description", "Status"), 0)
    private val resultTable = JBTable(resultModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        columnModel.getColumn(0).preferredWidth = 80
        columnModel.getColumn(1).preferredWidth = 300
        columnModel.getColumn(2).preferredWidth = 60
        emptyText.setText("Search for persons or accounts above")
    }

    private val statusLabel = JLabel(" ")

    // Person search filters
    private val personFilters = linkedMapOf(
        "AUTO" to "Auto-detect",
        "BY_LAST_FIRST_MIDDLE_NAME" to "Name",
        "BY_ACCOUNT_NUMBER" to "Account Number",
        "BY_TIN" to "TIN/SSN",
        "BY_PHONE_NUMBER" to "Phone",
        "BY_EMAIL_ADDRESS" to "Email",
        "BY_CARD_NUMBER" to "Card Number",
        "BY_SERIAL" to "Serial"
    )

    // Account search filters
    private val accountFilters = linkedMapOf(
        "BY_ACCOUNT_NUMBER" to "Account Number",
        "BY_SERIAL" to "Serial",
        "BY_ACCOUNT_TITLE" to "Title",
        "BY_VIN" to "Vehicle ID",
        "BY_EXTERNAL_ACCOUNT_NUMBER" to "External ID"
    )

    val component: JComponent

    init {
        updateFilters()
        searchTypeCombo.addActionListener { updateFilters() }

        searchButton.addActionListener { doSearch() }
        searchField.addActionListener { doSearch() }

        // Use selected result to populate Script Options
        resultTable.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && resultTable.selectedRow >= 0) {
                val serial = resultModel.getValueAt(resultTable.selectedRow, 0).toString()
                val params = ScriptParameterService.getInstance(project)
                if (searchTypeCombo.selectedItem == "Person") {
                    params.personSerial = serial
                } else {
                    params.accountSerial = serial
                }
            }
        }

        val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(searchTypeCombo)
            add(filterCombo)
        }

        val searchRow = JPanel(BorderLayout(4, 0)).apply {
            add(searchField, BorderLayout.CENTER)
            add(searchButton, BorderLayout.EAST)
            border = JBUI.Borders.empty(2, 0)
        }

        val controlPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(topRow)
            add(searchRow)
        }

        component = JPanel(BorderLayout()).apply {
            add(controlPanel, BorderLayout.NORTH)
            add(JBScrollPane(resultTable), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
        }
    }

    private fun updateFilters() {
        filterCombo.removeAllItems()
        val filters = if (searchTypeCombo.selectedItem == "Person") personFilters else accountFilters
        filters.values.forEach { filterCombo.addItem(it) }
    }

    private fun getSelectedFilterKey(): String {
        val filters = if (searchTypeCombo.selectedItem == "Person") personFilters else accountFilters
        val selectedLabel = filterCombo.selectedItem?.toString() ?: return filters.keys.first()
        return filters.entries.find { it.value == selectedLabel }?.key ?: filters.keys.first()
    }

    private fun doSearch() {
        val query = searchField.text.trim()
        if (query.isEmpty()) return

        val tableName = if (searchTypeCombo.selectedItem == "Person") "PERSON" else "ACCOUNT"
        var filterName = getSelectedFilterKey()

        // Auto-detect filter for Person searches
        if (filterName == "AUTO" && tableName == "PERSON") {
            filterName = detectPersonFilter(query)
        }

        searchButton.isEnabled = false
        statusLabel.text = "Searching..."
        resultModel.rowCount = 0

        scope.launch {
            try {
                val xml = buildSearchXml(tableName, filterName, query)
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/SearchJSON", xml)

                val rows = parseSearchResults(response)
                SwingUtilities.invokeLater {
                    rows.forEach { row ->
                        resultModel.addRow(arrayOf(row.serial, row.description, row.status))
                    }
                    statusLabel.text = "${rows.size} result(s) found"
                    searchButton.isEnabled = true
                }
            } catch (e: Exception) {
                log.warn("Search failed", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error: ${e.message}"
                    searchButton.isEnabled = true
                }
            }
        }
    }

    private fun detectPersonFilter(query: String): String {
        return when {
            query.matches(Regex("""\d{3}-\d{2}-\d{4}""")) -> "BY_TIN"
            query.matches(Regex("""\d{2}-\d{7}""")) -> "BY_TIN"
            query.matches(Regex("""\d{3}-\d{3}-\d{4}""")) -> "BY_PHONE_NUMBER"
            query.matches(Regex("""\(\d{3}\)\d{3}-\d{4}""")) -> "BY_PHONE_NUMBER"
            query.contains("@") -> "BY_EMAIL_ADDRESS"
            query.all { it.isDigit() } -> "BY_ACCOUNT_NUMBER"
            else -> "BY_LAST_FIRST_MIDDLE_NAME"
        }
    }

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
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml")
        conn.doOutput = true
        conn.outputStream.use { it.write(xml.toByteArray()) }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun parseSearchResults(json: String): List<SearchResult> {
        return SearchJsonParsers.parseRows(json).map { row ->
            SearchResult(
                serial = row.serial,
                description = row.description,
                status = row.status
            )
        }
    }

    private data class SearchResult(val serial: String, val description: String, val status: String)

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
