package com.keyscript.plugin.toolwindow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.keyscript.plugin.KeyscriptJson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.ProxyServerService
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

/**
 * Table Browser tool window — browse Keystone table metadata, search records,
 * view record details, and generate CRUD operation templates.
 */
class TableBrowserToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TableBrowserPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class TableBrowserPanel(private val project: Project) {
    private val log = Logger.getInstance(TableBrowserPanel::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mapper: ObjectMapper = KeyscriptJson.mapper

    // Sidebar: table list with filter
    private val filterField = JBTextField()
    private val tableListModel = DefaultListModel<TableEntry>()
    private val tableList = JBList(tableListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        emptyText.setText("No tables available")
        emptyText.appendLine("Login to load table metadata", SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
    }
    @Suppress("unused")
    private val tableListSpeedSearch = object : ListSpeedSearch<TableEntry>(tableList) {
        override fun getElementText(element: Any?): String? = (element as? TableEntry)?.name
    }
    private var allTables = listOf<TableEntry>()

    // Detail tabs
    private val tabbedPane = JBTabbedPane()

    // Columns tab
    private val columnsModel = DefaultTableModel(
        arrayOf("Ordinal", "Name", "Description", "Type", "Null", "Max Length", "Default", "References"), 0
    )
    private val columnsTable = JBTable(columnsModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        emptyText.setText("Select a table from the sidebar")
    }

    // Search tab
    private val searchFilterCombo = JComboBox<String>()
    private val searchParamField = JBTextField()
    private val searchRecordsButton = JButton("Search")
    private val viewRecordButton = JButton("View Record")
    private val searchResultModel = DefaultTableModel(arrayOf("Serial", "Description"), 0)
    private val searchResultTable = JBTable(searchResultModel).apply {
        emptyText.setText("Use a filter to search records")
    }

    // Search tab - filter detail & template preview
    private val searchFilterDetailModel = DefaultTableModel(arrayOf("Column Name", "Data Type"), 0)
    private val searchFilterDetailTable = JBTable(searchFilterDetailModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
    }
    private val searchTemplateArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val searchShowXmlButton = JToggleButton("Show XML", true)
    private val searchShowJsButton = JToggleButton("Show JS", false)
    private val searchCopyTemplateButton = JButton("Copy")

    // Record tab
    private val recordModel = DefaultTableModel(arrayOf("Field", "Value"), 0)
    private val recordTable = JBTable(recordModel).apply {
        emptyText.setText("Search for a record, then select it and click 'View Record'")
    }

    // Record Operations tab
    private val opViewButton = JToggleButton("V")
    private val opInsertButton = JToggleButton("I")
    private val opUpdateButton = JToggleButton("U")
    private val opDeleteButton = JToggleButton("D")
    private val opButtonGroup = ButtonGroup().apply {
        add(opViewButton)
        add(opInsertButton)
        add(opUpdateButton)
        add(opDeleteButton)
    }
    private val opXmlArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val opJsArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val opCopyXmlButton = JButton("Copy XML")
    private val opCopyJsButton = JButton("Copy JS")

    private val statusLabel = JLabel("Loading tables...")

    // Cached data for current table
    private var currentTableName: String? = null
    private var currentColumns: List<Map<String, String>> = emptyList()
    private var currentParentTableName: String = ""
    private var currentFilters: List<SearchFilterEntry> = emptyList()

    val component: JComponent

    init {
        // Table list selection
        tableList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selected = tableList.selectedValue
                if (selected != null) loadTableDetail(selected.name)
            }
        }

        // Filter
        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = applyFilter()
            override fun removeUpdate(e: DocumentEvent?) = applyFilter()
            override fun changedUpdate(e: DocumentEvent?) = applyFilter()
        })

        searchRecordsButton.addActionListener { doTableSearch() }
        searchParamField.addActionListener { doTableSearch() }
        viewRecordButton.addActionListener {
            val row = searchResultTable.selectedRow
            if (row >= 0) {
                val serial = searchResultModel.getValueAt(row, 0).toString()
                val tableName = tableList.selectedValue?.name ?: return@addActionListener
                loadRecord(tableName, serial)
            }
        }
        viewRecordButton.isEnabled = false
        searchResultTable.selectionModel.addListSelectionListener {
            viewRecordButton.isEnabled = searchResultTable.selectedRow >= 0
        }

        // Record view on double-click in search results
        searchResultTable.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2 && searchResultTable.selectedRow >= 0) {
                    val serial = searchResultModel.getValueAt(searchResultTable.selectedRow, 0).toString()
                    val tableName = tableList.selectedValue?.name ?: return
                    loadRecord(tableName, serial)
                }
            }
        })

        // Search filter combo listener - show filter details when selection changes
        searchFilterCombo.addActionListener {
            val selectedFilterName = searchFilterCombo.selectedItem?.toString() ?: return@addActionListener
            updateSearchFilterDetail(selectedFilterName)
        }

        // Search template toggle buttons
        val searchTemplateGroup = ButtonGroup().apply {
            add(searchShowXmlButton)
            add(searchShowJsButton)
        }
        searchShowXmlButton.addActionListener { updateSearchTemplate() }
        searchShowJsButton.addActionListener { updateSearchTemplate() }
        searchCopyTemplateButton.addActionListener {
            copyToClipboard(searchTemplateArea.text)
        }

        // Record operations buttons
        val opListener = java.awt.event.ActionListener { updateRecordOperationTemplates() }
        opViewButton.addActionListener(opListener)
        opInsertButton.addActionListener(opListener)
        opUpdateButton.addActionListener(opListener)
        opDeleteButton.addActionListener(opListener)

        opCopyXmlButton.addActionListener { copyToClipboard(opXmlArea.text) }
        opCopyJsButton.addActionListener { copyToClipboard(opJsArea.text) }

        // Build tabs
        tabbedPane.addTab("Columns", JBScrollPane(columnsTable))
        tabbedPane.addTab("Search Records", buildSearchPanel())
        tabbedPane.addTab("Record View", JBScrollPane(recordTable))
        tabbedPane.addTab("Record Operations", buildRecordOperationsPanel())

        // Sidebar
        val sidebar = JPanel(BorderLayout(0, 4)).apply {
            preferredSize = Dimension(220, 0)
            add(filterField, BorderLayout.NORTH)
            add(JBScrollPane(tableList), BorderLayout.CENTER)
        }

        // Main layout
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, tabbedPane).apply {
            dividerLocation = 220
        }

        component = JPanel(BorderLayout()).apply {
            add(splitPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
        }

        // Load table list — waits for login if not yet authenticated
        loadTableList()
    }

    // ─── Panel builders ─────────────────────────────

    private fun buildSearchPanel(): JPanel {
        // Top controls: filter combo + param field + search + view buttons
        val buttonPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0)).apply {
            add(searchRecordsButton)
            add(viewRecordButton)
        }
        val controls = JPanel(BorderLayout(4, 0)).apply {
            add(searchFilterCombo, BorderLayout.WEST)
            add(searchParamField, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.EAST)
        }

        // Filter detail: parameter table showing column names and data types
        val filterDetailPanel = JPanel(BorderLayout(4, 4)).apply {
            border = JBUI.Borders.emptyTop(4)
            val detailScroll = JBScrollPane(searchFilterDetailTable).apply {
                preferredSize = Dimension(0, 100)
            }
            add(JLabel("Filter Parameters:"), BorderLayout.NORTH)
            add(detailScroll, BorderLayout.CENTER)
        }

        // Template toggle + preview
        val templateTogglePanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0)).apply {
            add(searchShowXmlButton)
            add(searchShowJsButton)
            add(searchCopyTemplateButton)
        }

        val templatePanel = JPanel(BorderLayout(4, 4)).apply {
            border = JBUI.Borders.emptyTop(4)
            add(templateTogglePanel, BorderLayout.NORTH)
            add(JBScrollPane(searchTemplateArea).apply {
                preferredSize = Dimension(0, 150)
            }, BorderLayout.CENTER)
        }

        // Bottom area: filter detail + template preview
        val bottomPanel = JPanel(BorderLayout(4, 4)).apply {
            add(filterDetailPanel, BorderLayout.NORTH)
            add(templatePanel, BorderLayout.CENTER)
        }

        // Main split: search results on top, filter detail + template on bottom
        val resultScroll = JBScrollPane(searchResultTable)
        val searchSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, resultScroll, bottomPanel).apply {
            resizeWeight = 0.5
        }

        return JPanel(BorderLayout(4, 4)).apply {
            add(controls, BorderLayout.NORTH)
            add(searchSplit, BorderLayout.CENTER)
        }
    }

    private fun buildRecordOperationsPanel(): JPanel {
        // Top: operation toggle buttons
        val buttonBar = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 4)).apply {
            add(JLabel("Operation:"))
            add(opViewButton)
            add(opInsertButton)
            add(opUpdateButton)
            add(opDeleteButton)
            add(Box.createHorizontalStrut(16))
            add(opCopyXmlButton)
            add(opCopyJsButton)
        }

        // Split pane: XML on top, JS on bottom
        val xmlPanel = JPanel(BorderLayout(4, 2)).apply {
            add(JLabel("XML Template"), BorderLayout.NORTH)
            add(JBScrollPane(opXmlArea), BorderLayout.CENTER)
        }
        val jsPanel = JPanel(BorderLayout(4, 2)).apply {
            add(JLabel("JS Template"), BorderLayout.NORTH)
            add(JBScrollPane(opJsArea), BorderLayout.CENTER)
        }

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, xmlPanel, jsPanel).apply {
            resizeWeight = 0.5
        }

        return JPanel(BorderLayout(4, 4)).apply {
            add(buttonBar, BorderLayout.NORTH)
            add(splitPane, BorderLayout.CENTER)
        }
    }

    // ─── Data classes ───────────────────────────────

    private data class TableEntry(val name: String, val description: String, val viewGroup: String) {
        override fun toString() = "$name — $description"
    }

    private data class SearchFilterEntry(
        val filterName: String,
        val parameters: List<FilterParameter>
    )

    private data class FilterParameter(
        val columnName: String,
        val dataType: String
    )

    // ─── Table loading ──────────────────────────────

    private fun loadTableList() {
        val session = com.keyscript.plugin.services.SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            statusLabel.text = "Not logged in — login first, then reopen this tab"
            // Listen for login and auto-load
            session.addListener(loginListener)
            return
        }
        scope.launch {
            try {
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postToProxy("$proxyBase/TableBrowser", "")
                val tables = parseTableList(response)
                allTables = tables
                SwingUtilities.invokeLater {
                    applyFilter()
                    statusLabel.text = "${tables.size} tables loaded"
                }
            } catch (e: Exception) {
                log.warn("Failed to load table list", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error loading tables: ${e.message}"
                }
            }
        }
    }

    private val loginListener: () -> Unit = {
        val session = com.keyscript.plugin.services.SessionService.getInstance(project)
        if (session.isLoggedIn && allTables.isEmpty()) {
            session.removeListener(loginListener)
            loadTableList()
        }
    }

    private fun applyFilter() {
        val filter = filterField.text.trim().lowercase()
        tableListModel.clear()
        allTables.filter { filter.isEmpty() || it.name.lowercase().contains(filter) || it.description.lowercase().contains(filter) }
            .forEach { tableListModel.addElement(it) }
    }

    private fun loadTableDetail(tableName: String) {
        scope.launch {
            try {
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()

                // Load columns
                val colResponse = postToProxy("$proxyBase/TableBrowser", "step=columnList&tableName=$tableName")
                val columns = parseColumns(colResponse)
                val parentTable = parseParentTableName(colResponse)

                // Load search filters (with parameter details)
                val filterResponse = postToProxy("$proxyBase/TableBrowser", "step=searchList&tableName=$tableName")
                log.info("searchList response for $tableName: ${filterResponse.take(2000)}")
                val filters = parseSearchFiltersDetailed(filterResponse)

                log.warn("TABLE_DETAIL: parsed ${columns.size} columns, ${filters.size} filters for $tableName")
                SwingUtilities.invokeLater {
                    currentTableName = tableName
                    currentColumns = columns
                    currentParentTableName = parentTable
                    currentFilters = filters

                    columnsModel.rowCount = 0
                    columns.forEach { col ->
                        columnsModel.addRow(arrayOf(
                            col["ordinal"], col["name"], col["description"],
                            col["type"], col["null"], col["maxLength"],
                            col["default"], col["references"]
                        ))
                    }

                    searchFilterCombo.removeAllItems()
                    filters.forEach { searchFilterCombo.addItem(it.filterName) }
                    log.warn("TABLE_DETAIL: UI updated — ${columnsModel.rowCount} column rows, ${searchFilterCombo.itemCount} filters in combo")

                    // Clear search template and filter detail
                    searchFilterDetailModel.rowCount = 0
                    searchTemplateArea.text = ""

                    // Clear record operations
                    opButtonGroup.clearSelection()
                    opXmlArea.text = ""
                    opJsArea.text = ""
                }
            } catch (e: Exception) {
                log.warn("Failed to load table detail: $tableName", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error loading $tableName: ${e.message}"
                }
            }
        }
    }

    // ─── Search filter detail & template ────────────

    private fun updateSearchFilterDetail(filterName: String) {
        log.warn("FILTER_DETAIL: updateSearchFilterDetail('$filterName'), currentFilters.size=${currentFilters.size}")
        val filter = currentFilters.find { it.filterName == filterName }
        if (filter == null) {
            log.warn("FILTER_DETAIL: filter '$filterName' NOT FOUND in currentFilters: ${currentFilters.map { it.filterName }}")
            return
        }
        log.warn("FILTER_DETAIL: filter '$filterName' has ${filter.parameters.size} params: ${filter.parameters.map { "${it.columnName}(${it.dataType})" }}")

        searchFilterDetailModel.rowCount = 0
        filter.parameters.forEach { param ->
            searchFilterDetailModel.addRow(arrayOf(param.columnName, param.dataType))
        }
        log.warn("FILTER_DETAIL: searchFilterDetailModel now has ${searchFilterDetailModel.rowCount} rows")

        updateSearchTemplate()
    }

    private fun updateSearchTemplate() {
        val tableName = currentTableName ?: return
        val filterName = searchFilterCombo.selectedItem?.toString() ?: return
        val filter = currentFilters.find { it.filterName == filterName } ?: return

        val template = if (searchShowXmlButton.isSelected) {
            generateSearchXml(tableName, filterName, filter.parameters)
        } else {
            generateSearchJs(tableName, filterName, filter.parameters)
        }
        searchTemplateArea.text = template
        searchTemplateArea.caretPosition = 0
    }

    private fun generateSearchXml(tableName: String, filterName: String, params: List<FilterParameter>): String {
        val sb = StringBuilder()
        sb.appendLine("""<query xmlns="http://www.corelationinc.com/queryLanguage/v1.0">""")
        sb.appendLine("  <sequence>")
        sb.appendLine("    <transaction>")
        sb.appendLine("      <step>")
        sb.appendLine("""        <search label="${tableName}_search">""")
        sb.appendLine("          <tableName>$tableName</tableName>")
        sb.appendLine("          <filterName>$filterName</filterName>")
        sb.appendLine("""          <includeSelectColumns option="Y"/>""")
        sb.appendLine("""          <includeTotalHitCount option="Y"/>""")
        sb.appendLine("          <returnLimit>10</returnLimit>")
        for (param in params) {
            sb.appendLine("          <parameter>")
            sb.appendLine("            <columnName>${param.columnName}</columnName>")
            sb.appendLine("            <contents>[${param.dataType}]</contents>")
            sb.appendLine("          </parameter>")
        }
        sb.appendLine("        </search>")
        sb.appendLine("      </step>")
        sb.appendLine("    </transaction>")
        sb.appendLine("  </sequence>")
        sb.appendLine("</query>")
        return sb.toString().trimEnd()
    }

    private fun generateSearchJs(tableName: String, filterName: String, params: List<FilterParameter>): String {
        val sb = StringBuilder()
        sb.appendLine("var xml = new CR.XML();")
        sb.appendLine("var sequence = xml.addContainer(xml.getRootElement(), 'sequence');")
        sb.appendLine("var transaction = xml.addContainer(sequence, 'transaction');")
        sb.appendLine("var step = xml.addContainer(transaction, 'step');")
        sb.appendLine("var search = xml.addContainer(step, 'search');")
        sb.appendLine("xml.setAttribute(search, 'label', '${tableName}_search');")
        sb.appendLine("xml.addText(search, 'tableName', '$tableName');")
        sb.appendLine("xml.addText(search, 'filterName', '$filterName');")
        sb.appendLine("xml.addOption(search, 'includeSelectColumns', 'Y');")
        sb.appendLine("xml.addOption(search, 'includeTotalHitCount', 'Y');")
        sb.appendLine("xml.addCount(search, 'returnLimit', 10);")
        for ((index, param) in params.withIndex()) {
            val varName = if (index == 0) "parameter" else "parameter$index"
            sb.appendLine("var $varName = xml.addContainer(search, 'parameter');")
            sb.appendLine("xml.addText($varName, 'columnName', '${param.columnName}');")
            sb.appendLine("xml.addText($varName, 'contents', '[${param.dataType}]');")
        }
        return sb.toString().trimEnd()
    }

    // ─── Record Operations templates ────────────────

    private fun updateRecordOperationTemplates() {
        val tableName = currentTableName ?: return

        val operation = when {
            opViewButton.isSelected -> "V"
            opInsertButton.isSelected -> "I"
            opUpdateButton.isSelected -> "U"
            opDeleteButton.isSelected -> "D"
            else -> return
        }

        opXmlArea.text = generateRecordXml(tableName, operation)
        opXmlArea.caretPosition = 0
        opJsArea.text = generateRecordJs(tableName, operation)
        opJsArea.caretPosition = 0
    }

    private fun generateRecordXml(tableName: String, operation: String): String {
        val sb = StringBuilder()
        sb.appendLine("""<query xmlns="http://www.corelationinc.com/queryLanguage/v1.0">""")
        sb.appendLine("  <sequence>")
        sb.appendLine("    <transaction>")
        sb.appendLine("      <step>")
        sb.appendLine("""        <record label="${tableName}_record">""")
        sb.appendLine("          <tableName>$tableName</tableName>")
        sb.appendLine("""          <operation option="$operation"/>""")

        when (operation) {
            "V" -> {
                sb.appendLine("          <targetSerial>[serial]</targetSerial>")
                sb.appendLine("""          <includeTableMetadata option="N"/>""")
                sb.appendLine("""          <includeColumnMetadata option="N"/>""")
                sb.appendLine("""          <includeRowDescriptions option="Y"/>""")
                sb.appendLine("""          <includeAllColumns option="Y"/>""")
            }
            "I" -> {
                if (currentParentTableName.isNotEmpty()) {
                    sb.appendLine("          <targetParentSerial>[serial]</targetParentSerial>")
                }
                sb.appendLine("""          <includeTableMetadata option="N"/>""")
                sb.appendLine("""          <includeColumnMetadata option="N"/>""")
                sb.appendLine("""          <includeRowDescriptions option="Y"/>""")
                // For insert: list writable fields
                for (col in currentColumns) {
                    val colName = col["name"] ?: continue
                    val dataType = col["type"] ?: ""
                    sb.appendLine("          <field>")
                    sb.appendLine("            <columnName>$colName</columnName>")
                    sb.appendLine("            <newContents>[$dataType]</newContents>")
                    sb.appendLine("          </field>")
                }
            }
            "U" -> {
                sb.appendLine("          <targetSerial>[serial]</targetSerial>")
                sb.appendLine("""          <includeTableMetadata option="N"/>""")
                sb.appendLine("""          <includeColumnMetadata option="N"/>""")
                sb.appendLine("""          <includeRowDescriptions option="Y"/>""")
                // For update: list writable fields
                for (col in currentColumns) {
                    val colName = col["name"] ?: continue
                    val dataType = col["type"] ?: ""
                    sb.appendLine("          <field>")
                    sb.appendLine("            <columnName>$colName</columnName>")
                    sb.appendLine("            <newContents>[$dataType]</newContents>")
                    sb.appendLine("          </field>")
                }
            }
            "D" -> {
                sb.appendLine("          <targetSerial>[serial]</targetSerial>")
                sb.appendLine("""          <includeTableMetadata option="N"/>""")
                sb.appendLine("""          <includeColumnMetadata option="N"/>""")
                sb.appendLine("""          <includeRowDescriptions option="Y"/>""")
                sb.appendLine("""          <includeAllColumns option="Y"/>""")
            }
        }

        sb.appendLine("        </record>")
        sb.appendLine("      </step>")
        sb.appendLine("    </transaction>")
        sb.appendLine("  </sequence>")
        sb.appendLine("</query>")
        return sb.toString().trimEnd()
    }

    private fun generateRecordJs(tableName: String, operation: String): String {
        val sb = StringBuilder()
        sb.appendLine("var xml = new CR.XML();")
        sb.appendLine("var sequence = xml.addContainer(xml.getRootElement(), 'sequence');")
        sb.appendLine("var transaction = xml.addContainer(sequence, 'transaction');")
        sb.appendLine("var step = xml.addContainer(transaction, 'step');")
        sb.appendLine("var record = xml.addContainer(step, 'record');")
        sb.appendLine("xml.setAttribute(record, 'label', '${tableName}_record');")
        sb.appendLine("xml.addText(record, 'tableName', '$tableName');")
        sb.appendLine("xml.addOption(record, 'operation', '$operation');")

        when (operation) {
            "V" -> {
                sb.appendLine("xml.addText(record, 'targetSerial', '[serial]');")
                sb.appendLine("xml.addOption(record, 'includeTableMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeColumnMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeRowDescriptions', 'Y');")
                sb.appendLine("xml.addOption(record, 'includeAllColumns', 'Y');")
            }
            "I" -> {
                if (currentParentTableName.isNotEmpty()) {
                    sb.appendLine("xml.addText(record, 'targetParentSerial', '[serial]');")
                }
                sb.appendLine("xml.addOption(record, 'includeTableMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeColumnMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeRowDescriptions', 'Y');")
                for ((index, col) in currentColumns.withIndex()) {
                    val colName = col["name"] ?: continue
                    val dataType = col["type"] ?: ""
                    val varName = if (index == 0) "field" else "field$index"
                    sb.appendLine("var $varName = xml.addContainer(record, 'field');")
                    sb.appendLine("xml.addText($varName, 'columnName', '$colName');")
                    sb.appendLine("xml.addText($varName, 'newContents', '[$dataType]');")
                }
            }
            "U" -> {
                sb.appendLine("xml.addText(record, 'targetSerial', '[serial]');")
                sb.appendLine("xml.addOption(record, 'includeTableMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeColumnMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeRowDescriptions', 'Y');")
                for ((index, col) in currentColumns.withIndex()) {
                    val colName = col["name"] ?: continue
                    val dataType = col["type"] ?: ""
                    val varName = if (index == 0) "field" else "field$index"
                    sb.appendLine("var $varName = xml.addContainer(record, 'field');")
                    sb.appendLine("xml.addText($varName, 'columnName', '$colName');")
                    sb.appendLine("xml.addText($varName, 'newContents', '[$dataType]');")
                }
            }
            "D" -> {
                sb.appendLine("xml.addText(record, 'targetSerial', '[serial]');")
                sb.appendLine("xml.addOption(record, 'includeTableMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeColumnMetadata', 'N');")
                sb.appendLine("xml.addOption(record, 'includeRowDescriptions', 'Y');")
                sb.appendLine("xml.addOption(record, 'includeAllColumns', 'Y');")
            }
        }

        return sb.toString().trimEnd()
    }

    // ─── Search execution ───────────────────────────

    private fun doTableSearch() {
        val tableName = tableList.selectedValue?.name ?: return
        val filterName = searchFilterCombo.selectedItem?.toString() ?: return
        val query = searchParamField.text.trim()
        if (query.isEmpty()) return

        searchRecordsButton.isEnabled = false
        searchResultModel.rowCount = 0

        scope.launch {
            try {
                val ns = "http://www.corelationinc.com/queryLanguage/v1.0"
                val xml = """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:search>
          <v1:tableName>$tableName</v1:tableName>
          <v1:filterName>$filterName</v1:filterName>
          <v1:includeSelectColumns option="Y"/>
          <v1:includeTotalHitCount option="Y"/>
          <v1:returnLimit>50</v1:returnLimit>
          <v1:parameter>
            <v1:contents>${escapeXml(query)}</v1:contents>
          </v1:parameter>
        </v1:search>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""

                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/SearchJSON", xml)
                val rows = parseSearchRows(response)

                SwingUtilities.invokeLater {
                    rows.forEach { searchResultModel.addRow(arrayOf(it.first, it.second)) }
                    statusLabel.text = "${rows.size} record(s) found"
                    searchRecordsButton.isEnabled = true
                }
            } catch (e: Exception) {
                log.warn("Table search failed", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Search error: ${e.message}"
                    searchRecordsButton.isEnabled = true
                }
            }
        }
    }

    private fun loadRecord(tableName: String, serial: String) {
        log.warn("RECORD_VIEW: loadRecord called for $tableName/$serial")
        scope.launch {
            try {
                val ns = "http://www.corelationinc.com/queryLanguage/v1.0"
                val xml = """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:record>
          <v1:tableName>$tableName</v1:tableName>
          <v1:operation option="V"/>
          <v1:targetSerial>$serial</v1:targetSerial>
        </v1:record>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""

                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/DirectXMLPostJSON", xml)
                log.info("Record view response for $tableName/$serial: ${response.take(2000)}")
                val fields = parseRecordFields(response)

                log.warn("RECORD_VIEW: parsed ${fields.size} fields, updating UI...")
                SwingUtilities.invokeLater {
                    log.warn("RECORD_VIEW: on EDT, adding ${fields.size} rows to recordModel")
                    recordModel.rowCount = 0
                    fields.forEach { (k, v) -> recordModel.addRow(arrayOf(k, v)) }
                    log.warn("RECORD_VIEW: recordModel now has ${recordModel.rowCount} rows, switching to tab 2")
                    tabbedPane.selectedIndex = 2 // Switch to Record View tab
                    statusLabel.text = "$tableName #$serial — ${fields.size} fields"
                }
            } catch (e: Exception) {
                log.warn("Failed to load record $tableName/$serial", e)
                SwingUtilities.invokeLater {
                    statusLabel.text = "Error loading record: ${e.message}"
                }
            }
        }
    }

    // ─── Clipboard helper ───────────────────────────

    private fun copyToClipboard(text: String) {
        if (text.isNotBlank()) {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            statusLabel.text = "Copied to clipboard"
        }
    }

    // ─── HTTP helpers ──────────────────────────────

    private fun postToProxy(url: String, body: String): String {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val status = conn.responseCode
        if (status !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            log.warn("postToProxy $url returned HTTP $status: ${errorBody.take(200)}")
            throw RuntimeException("HTTP $status from $url")
        }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun postXml(url: String, xml: String): String {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml")
        conn.instanceFollowRedirects = false
        conn.doOutput = true
        conn.outputStream.use { it.write(xml.toByteArray()) }
        val status = conn.responseCode
        if (status !in 200..299) {
            val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
            log.warn("postXml $url returned HTTP $status: ${errorBody.take(200)}")
            throw RuntimeException("HTTP $status from $url")
        }
        return conn.inputStream.bufferedReader().readText()
    }

    // ─── JSON parsers (Jackson-based) ──────────────

    private fun parseTableList(json: String): List<TableEntry> {
        val root = mapper.readTree(json)
        val results = mutableListOf<TableEntry>()
        // Response may be an array or wrapped in an object
        val tables = when {
            root.isArray -> root
            root.has("table") -> asArray(root["table"])
            else -> root
        }
        for (table in tables) {
            val name = table.textOrEmpty("tableName")
            if (name.isNotEmpty()) {
                results.add(TableEntry(
                    name = name,
                    description = table.textOrEmpty("tableDescription"),
                    viewGroup = table.textOrEmpty("viewGroup")
                ))
            }
        }
        return results.sortedBy { it.name }
    }

    private fun parseColumns(json: String): List<Map<String, String>> {
        val root = mapper.readTree(json)
        val fields = findArray(root, "field") ?: return emptyList()
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Map<String, String>>()

        for (field in fields) {
            val ordinal = field.textOrEmpty("columnOrdinal")
            val name = field.textOrEmpty("columnName")
            // Deduplicate by ordinal+name
            val key = "$ordinal:$name"
            if (!seen.add(key)) continue

            results.add(mapOf(
                "ordinal" to ordinal,
                "name" to name,
                "description" to field.textOrEmpty("columnDescription"),
                "type" to field.textOrEmpty("dataType"),
                "null" to field.textOrEmpty("nullAllowed"),
                "maxLength" to field.textOrEmpty("maximumLength"),
                "default" to field.textOrEmpty("defaultContents"),
                "references" to field.textOrEmpty("referenceTableName")
            ))
        }
        return results
    }

    private fun parseParentTableName(json: String): String {
        val root = mapper.readTree(json)
        // Try to find parentTableName at the top level of the column response
        val parentNode = root["parentTableName"]
        if (parentNode != null && parentNode.isTextual) return parentNode.asText()
        // Also check inside the wrapper
        val tableNode = findDeep(root, "parentTableName")
        return tableNode?.asText() ?: ""
    }

    private fun parseSearchFiltersDetailed(json: String): List<SearchFilterEntry> {
        val root = mapper.readTree(json)

        // Keystone returns filters as individual "search" objects within the step array:
        // query.sequence[].transaction[].step[] → [{searchList:{...}}, {search:{...}}, ...]
        val searchNodes = mutableListOf<JsonNode>()
        collectAllDeep(root, "search", searchNodes)

        log.warn("FILTER_PARSE: collectAllDeep('search') found ${searchNodes.size} nodes")
        if (searchNodes.isNotEmpty()) {
            log.warn("FILTER_PARSE: first node keys=${searchNodes[0].fieldNames().asSequence().toList()}")
            log.warn("FILTER_PARSE: first node filterName=${searchNodes[0].path("filterName").asText("MISSING")}")
        }

        if (searchNodes.isEmpty()) {
            log.warn("FILTER_PARSE: no 'search' nodes. Root keys=${root.fieldNames().asSequence().toList()}")
            log.warn("FILTER_PARSE: response=${json.take(2000)}")
            return emptyList()
        }

        val seen = mutableSetOf<String>()
        val results = mutableListOf<SearchFilterEntry>()

        for (filter in searchNodes) {
            val name = filter.textOrEmpty("filterName")
            log.warn("FILTER_PARSE: processing filter '$name', keys=${filter.fieldNames().asSequence().toList()}")
            if (name.isEmpty() || !seen.add(name)) continue

            val params = mutableListOf<FilterParameter>()
            val paramNode = filter["parameter"] ?: filter["parameters"]
            log.warn("FILTER_PARSE: filter '$name' paramNode=${if (paramNode != null) "found, isArray=${paramNode.isArray}" else "NULL"}")

            if (paramNode != null) {
                val paramArray = asArray(paramNode)
                for (p in paramArray) {
                    val colName = p.textOrEmpty("columnName")
                    val dataType = p.textOrEmpty("dataType")
                    if (colName.isNotEmpty()) {
                        params.add(FilterParameter(colName, dataType))
                    }
                }
            }

            log.warn("FILTER_PARSE: filter '$name' has ${params.size} params")
            results.add(SearchFilterEntry(name, params))
        }
        log.warn("FILTER_PARSE: TOTAL ${results.size} filters parsed")
        return results
    }

    private fun parseSearchRows(json: String): List<Pair<String, String>> {
        val root = mapper.readTree(json)
        log.info("parseSearchRows: keys=${root.fieldNames().asSequence().toList()}, json=${json.take(2000)}")

        // Try flat format first: {"resultRows":[...]}
        val flatRows = root.get("resultRows") ?: findDeep(root, "resultRows")
        if (flatRows != null && flatRows.isArray) {
            return flatRows.mapNotNull { row ->
                val serial = row.textOrEmpty("serial")
                val desc = row.textOrEmpty("ROW_DESCRIPTION").ifEmpty { row.textOrEmpty("rowDescription") }
                if (serial.isNotEmpty()) serial to desc else null
            }
        }

        // Nested format: query.sequence[].transaction[].step[].search.resultRow[]
        val search = findDeep(root, "search")
        val resultRow = search?.get("resultRow") ?: findDeep(root, "resultRow")
        if (resultRow != null) {
            val rowList = if (resultRow.isArray) resultRow.toList() else listOf(resultRow)
            return rowList.mapNotNull { row ->
                var serial = row.path("serial").asText("")
                if (serial.isEmpty()) serial = row.get("\$attr")?.path("serial")?.asText("") ?: ""
                val desc = row.textOrEmpty("rowDescription").ifEmpty { row.textOrEmpty("ROW_DESCRIPTION") }
                if (serial.isNotEmpty()) serial to desc else null
            }
        }

        log.warn("parseSearchRows: no result rows found in response")
        return emptyList()
    }

    private fun parseRecordFields(json: String): List<Pair<String, String>> {
        val root = mapper.readTree(json)
        val results = mutableListOf<Pair<String, String>>()

        log.warn("RECORD_PARSE: root type=${root.nodeType}, keys=${root.fieldNames().asSequence().toList()}")

        val record = findDeep(root, "record")
        log.warn("RECORD_PARSE: findDeep('record') = ${if (record != null) "found, keys=${record.fieldNames().asSequence().toList()}" else "NULL"}")

        val skip = setOf("\$attr", "operation", "tableName", "targetSerial", "includeAllColumns",
            "includeRowDescriptions", "includeColumnMetadata", "includeTableMetadata")

        // Try "field" array first (structured format from DirectXMLPostJSON)
        val fieldArray = record?.get("field") ?: findDeep(root, "field")
        log.warn("RECORD_PARSE: field array = ${if (fieldArray != null) "found, isArray=${fieldArray.isArray}, size=${if (fieldArray.isArray) fieldArray.size() else 1}" else "NULL"}")

        if (fieldArray != null) {
            val fields = if (fieldArray.isArray) fieldArray.toList() else listOf(fieldArray)
            for (f in fields) {
                val colName = f.path("columnName").asText("")
                val contents = f.path("contents").asText("")
                val newContents = f.path("newContents").asText("")
                if (colName.isNotEmpty()) {
                    results.add(colName to contents.ifEmpty { newContents })
                }
            }
            log.warn("RECORD_PARSE: from field array got ${results.size} fields")
            if (results.isNotEmpty()) return results
        }

        // Try flat record fields (key=value directly on record node)
        if (record != null) {
            val fields = record.properties()
            while (fields.hasNext()) {
                val (key, value) = fields.next()
                if (key in skip) continue
                val text = nodeToString(value)
                results.add(key to text)
            }
            log.warn("RECORD_PARSE: from flat record got ${results.size} fields")
            if (results.isNotEmpty()) return results
        }

        // Last resort: collect ALL field nodes from anywhere in the tree
        val allFields = mutableListOf<JsonNode>()
        collectAllDeep(root, "field", allFields)
        log.warn("RECORD_PARSE: collectAllDeep('field') found ${allFields.size} field nodes")
        for (f in allFields) {
            val colName = f.path("columnName").asText("")
            val contents = f.path("contents").asText("")
            val newContents = f.path("newContents").asText("")
            if (colName.isNotEmpty()) {
                results.add(colName to contents.ifEmpty { newContents })
            }
        }

        log.warn("RECORD_PARSE: final result = ${results.size} fields, first 3: ${results.take(3)}")
        return results
    }

    // ─── Jackson helpers ─────────────────────────────

    private fun JsonNode.textOrEmpty(field: String): String {
        val node = this[field] ?: return ""
        return nodeToString(node)
    }

    /** Extract a display string from a JSON node, handling Keystone option objects. */
    private fun nodeToString(node: JsonNode): String = when {
        node.isTextual -> node.asText()
        node.isNumber -> node.asText()
        node.isBoolean -> node.asText()
        // Keystone option objects: {"option":"S","text":"Text"} → "Text"
        node.isObject && node.has("text") -> node["text"].asText()
        // Fallback for option-only: {"option":"Y"} → "Y"
        node.isObject && node.has("option") -> node["option"].asText()
        // Keystone content objects: {"contents":"value"} → "value"
        node.isObject && node.has("contents") -> node["contents"].asText()
        else -> node.asText("")
    }

    /** Normalize a JSON node to an iterable — handles both single object and array. */
    private fun asArray(node: JsonNode?): Iterable<JsonNode> {
        if (node == null) return emptyList()
        return if (node.isArray) node else listOf(node)
    }

    /** Recursively find an array or normalizable node by key name. */
    private fun findArray(node: JsonNode, key: String): Iterable<JsonNode>? {
        if (node.has(key)) return asArray(node[key])
        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findArray(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    /** Recursively find the first object node by key name. */
    private fun findDeep(node: JsonNode, key: String): JsonNode? {
        if (node.has(key)) return node[key]
        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findDeep(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    /** Recursively collect ALL nodes with the given key name. */
    private fun collectAllDeep(node: JsonNode, key: String, results: MutableList<JsonNode>) {
        if (node.has(key)) {
            val target = node[key]
            if (target.isArray) target.forEach { results.add(it) } else results.add(target)
        }
        for (child in node) {
            if (child.isObject || child.isArray) {
                collectAllDeep(child, key, results)
            }
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
