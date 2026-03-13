package com.keyscript.plugin.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.NetworkMonitorService
import com.keyscript.plugin.services.NetworkMonitorService.NetworkExchange
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Network monitor tool window — master-detail split pane showing proxy request/response events.
 */
class NetworkToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = NetworkPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class ExchangeTableModel : AbstractTableModel() {
    private val exchanges = mutableListOf<NetworkExchange>()
    private val columnNames = arrayOf("Time", "Method", "URL", "Status")

    private val timeFormat = SimpleDateFormat("HH:mm:ss")

    fun getExchangeAt(row: Int): NetworkExchange? = exchanges.getOrNull(row)

    fun setExchanges(data: List<NetworkExchange>) {
        exchanges.clear()
        exchanges.addAll(data)
        fireTableDataChanged()
    }

    fun addOrUpdate(exchange: NetworkExchange) {
        val idx = exchanges.indexOfFirst { it.id == exchange.id }
        if (idx >= 0) {
            exchanges[idx] = exchange
            fireTableRowsUpdated(idx, idx)
        } else {
            exchanges.add(exchange)
            fireTableRowsInserted(exchanges.size - 1, exchanges.size - 1)
        }
    }

    fun clear() {
        exchanges.clear()
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = exchanges.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val ex = exchanges[rowIndex]
        return when (columnIndex) {
            0 -> timeFormat.format(Date(ex.timestamp))
            1 -> ex.method
            2 -> ex.url
            3 -> if (ex.status > 0) ex.status.toString() else "\u2026"
            else -> ""
        }
    }
}

/**
 * Cell renderer that color-codes the Method column (POST=blue, GET=green, etc.)
 */
private class MethodCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (!isSelected) {
            foreground = when (value?.toString()?.uppercase()) {
                "POST" -> JBColor(Color(0x0366D6), Color(0x58A6FF))
                "GET" -> JBColor(Color(0x28A745), Color(0x3FB950))
                "PUT" -> JBColor(Color(0xD97706), Color(0xF5A623))
                "DELETE" -> JBColor(Color(0xCB2431), Color(0xF85149))
                else -> table.foreground
            }
        }
        font = font.deriveFont(Font.BOLD)
        return comp
    }
}

/**
 * Cell renderer that color-codes status codes (2xx=green, 4xx/5xx=red).
 */
private class StatusCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean,
        hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (!isSelected) {
            val text = value?.toString() ?: ""
            val code = text.toIntOrNull() ?: 0
            foreground = when {
                code in 200..299 -> JBColor(Color(0x28A745), Color(0x3FB950))
                code in 400..599 -> JBColor(Color(0xCB2431), Color(0xF85149))
                else -> table.foreground
            }
        }
        font = font.deriveFont(Font.BOLD)
        horizontalAlignment = CENTER
        return comp
    }
}

class NetworkPanel(private val project: Project) {
    private val tableModel = ExchangeTableModel()
    private val table = JBTable(tableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnModel.getColumn(0).preferredWidth = 70   // Time
        columnModel.getColumn(1).preferredWidth = 55    // Method
        columnModel.getColumn(2).preferredWidth = 350   // URL
        columnModel.getColumn(3).preferredWidth = 50    // Status
        columnModel.getColumn(1).cellRenderer = MethodCellRenderer()
        columnModel.getColumn(3).cellRenderer = StatusCellRenderer()
        rowHeight = JBUI.scale(22)
        emptyText.setText("No network activity captured")
        emptyText.appendLine(
            "Requests appear when scripts run through the proxy",
            SimpleTextAttributes.GRAYED_ATTRIBUTES,
            null
        )
    }

    // --- Detail panel components ---
    private val generalUrlLabel = JBLabel()
    private val generalMethodLabel = JBLabel()
    private val generalStatusLabel = JBLabel()
    private val generalTimeLabel = JBLabel()

    private val requestBodyArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
        lineWrap = true
        wrapStyleWord = true
    }

    private val responseBodyArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
        lineWrap = true
        wrapStyleWord = true
    }

    private val detailPanel: JPanel
    private val noSelectionLabel = JBLabel("Select a request to view details").apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.GRAY
    }
    private val detailCardPanel: JPanel
    private val cardLayout = CardLayout()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")

    val component: JComponent

    init {
        // ---- Toolbar ----
        val clearAction = object : AnAction("Clear All", "Clear all captured network traffic", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) { clearAll() }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val toolbarGroup = DefaultActionGroup(clearAction)
        val toolbar = ActionManager.getInstance().createActionToolbar("KeyscriptNetwork", toolbarGroup, true)

        // ---- Detail panel ----
        detailPanel = buildDetailPanel()

        detailCardPanel = JPanel(cardLayout).apply {
            add(noSelectionLabel, "empty")
            add(JBScrollPane(detailPanel).apply {
                border = JBUI.Borders.empty()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }, "detail")
        }
        cardLayout.show(detailCardPanel, "empty")

        // ---- Split pane ----
        val splitPane = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            JBScrollPane(table),
            detailCardPanel
        ).apply {
            dividerLocation = JBUI.scale(200)
            resizeWeight = 0.5
            border = JBUI.Borders.empty()
        }

        component = JPanel(BorderLayout()).apply {
            toolbar.targetComponent = this
            add(toolbar.component, BorderLayout.NORTH)
            add(splitPane, BorderLayout.CENTER)
            border = JBUI.Borders.empty()
        }

        // ---- Selection listener ----
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val row = table.selectedRow
                if (row >= 0) {
                    val exchange = tableModel.getExchangeAt(row)
                    if (exchange != null) {
                        showExchangeDetail(exchange)
                        cardLayout.show(detailCardPanel, "detail")
                    }
                } else {
                    cardLayout.show(detailCardPanel, "empty")
                }
            }
        }

        // ---- Load existing events & listen for new ones ----
        val monitor = NetworkMonitorService.getInstance(project)
        tableModel.setExchanges(monitor.getExchanges())

        monitor.addListener { event ->
            if (event.type != "console") {
                SwingUtilities.invokeLater {
                    // Build an updated exchange for this event's ID
                    val request = monitor.findRequest(event.id)
                    val response = monitor.findResponse(event.id)
                    val exchange = NetworkExchange(
                        id = event.id,
                        request = request,
                        response = response
                    )
                    val selectedBefore = table.selectedRow
                    val selectedExchange = if (selectedBefore >= 0) tableModel.getExchangeAt(selectedBefore) else null

                    tableModel.addOrUpdate(exchange)

                    // If we updated the currently-selected exchange, refresh the detail view
                    if (selectedExchange?.id == exchange.id) {
                        showExchangeDetail(exchange)
                    }
                }
            }
        }
    }

    private fun buildDetailPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(6, 8)
        }

        // ---- General section ----
        val generalSection = createSection("General")
        val generalGrid = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 4)
        }

        fun addLabelRow(grid: JPanel, c: GridBagConstraints, row: Int, label: String, valueComp: JBLabel) {
            c.gridx = 0; c.gridy = row; c.weightx = 0.0; c.fill = GridBagConstraints.NONE
            grid.add(JBLabel(label).apply { font = font.deriveFont(Font.BOLD) }, c)
            c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL
            grid.add(valueComp, c)
        }

        addLabelRow(generalGrid, gbc, 0, "URL:", generalUrlLabel)
        addLabelRow(generalGrid, gbc, 1, "Method:", generalMethodLabel)
        addLabelRow(generalGrid, gbc, 2, "Status:", generalStatusLabel)
        addLabelRow(generalGrid, gbc, 3, "Time:", generalTimeLabel)

        generalSection.add(generalGrid)
        panel.add(generalSection)
        panel.add(Box.createVerticalStrut(JBUI.scale(6)))

        // ---- Request Body section ----
        val requestSection = createSection("Request Body")
        requestSection.add(JBScrollPane(requestBodyArea).apply {
            preferredSize = Dimension(0, JBUI.scale(150))
            minimumSize = Dimension(0, JBUI.scale(80))
            border = JBUI.Borders.empty()
        })
        panel.add(requestSection)
        panel.add(Box.createVerticalStrut(JBUI.scale(6)))

        // ---- Response Body section ----
        val responseSection = createSection("Response Body")
        responseSection.add(JBScrollPane(responseBodyArea).apply {
            preferredSize = Dimension(0, JBUI.scale(150))
            minimumSize = Dimension(0, JBUI.scale(80))
            border = JBUI.Borders.empty()
        })
        panel.add(responseSection)

        // Push everything to the top
        panel.add(Box.createVerticalGlue())

        return panel
    }

    /**
     * Creates a labeled section panel with a titled border, using BoxLayout Y_AXIS inside.
     */
    private fun createSection(title: String): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(JBColor.border(), 1, true),
                    title,
                    TitledBorder.DEFAULT_JUSTIFICATION,
                    TitledBorder.DEFAULT_POSITION,
                    UIManager.getFont("Label.font")?.deriveFont(Font.BOLD)
                ),
                JBUI.Borders.empty(4, 6)
            )
            alignmentX = Component.LEFT_ALIGNMENT
        }
    }

    private fun showExchangeDetail(exchange: NetworkExchange) {
        generalUrlLabel.text = exchange.url
        generalMethodLabel.text = exchange.method
        generalStatusLabel.text = if (exchange.status > 0) exchange.status.toString() else "Pending\u2026"
        generalTimeLabel.text = timeFormat.format(Date(exchange.timestamp))

        requestBodyArea.text = formatBody(exchange.request?.body)
        requestBodyArea.caretPosition = 0

        responseBodyArea.text = formatBody(exchange.response?.body)
        responseBodyArea.caretPosition = 0
    }

    private fun formatBody(body: String?): String {
        if (body.isNullOrBlank()) return "(empty)"
        return body
    }

    private fun clearAll() {
        NetworkMonitorService.getInstance(project).clear()
        tableModel.clear()
        requestBodyArea.text = ""
        responseBodyArea.text = ""
        generalUrlLabel.text = ""
        generalMethodLabel.text = ""
        generalStatusLabel.text = ""
        generalTimeLabel.text = ""
        cardLayout.show(detailCardPanel, "empty")
    }
}
