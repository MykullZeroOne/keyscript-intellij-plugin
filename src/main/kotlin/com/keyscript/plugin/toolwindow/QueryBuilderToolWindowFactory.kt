package com.keyscript.plugin.toolwindow

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.ProxyServerService
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Query Builder tool window — construct, verify, and post Corelation XML queries.
 * Provides a tree editor for building query XML and viewing results.
 */
class QueryBuilderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = QueryBuilderPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/**
 * Defines a step subelement type with its default properties.
 * @param type The XML element name
 * @param label The display label in the UI
 * @param defaultProperties Default property key-value pairs when the element is created
 * @param parentConstraint Which parent element this type can be added under (null = "step")
 */
private data class SubelementType(
    val type: String,
    val label: String,
    val defaultProperties: Map<String, String>,
    val parentConstraint: String = "step"
)

class QueryBuilderPanel(private val project: Project) {
    private val log = Logger.getInstance(QueryBuilderPanel::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ns = "http://www.corelationinc.com/queryLanguage/v1.0"

    /**
     * Properties that use option="value" attribute syntax rather than text content.
     * Derived from the Keybridge query-language type system and Postman collection.
     */
    private val optionTypeProperties = setOf(
        "operation", "postingMode",
        "includeSelectColumns", "includeTotalHitCount",
        "includeChildren", "includeDetail", "includeMonetary",
        "includeRecordDetail", "includeRowDescriptions",
        "includeTableMetadata", "includeColumnMetadata", "includeAllColumns",
        "category", "targetCategory", "source",
        "formatOption", "orderOption", "reportOption"
    )

    /**
     * All known step subelement types from the Corelation query language,
     * with their default properties and parent constraints.
     */
    private val subelementTypes = listOf(
        SubelementType(
            type = "search",
            label = "Search",
            defaultProperties = linkedMapOf(
                "tableName" to "PERSON",
                "filterName" to "BY_LAST_FIRST_MIDDLE_NAME",
                "returnLimit" to "20",
                "includeSelectColumns" to "",
                "includeTotalHitCount" to "",
                "contents" to ""
            )
        ),
        SubelementType(
            type = "record",
            label = "Record",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "operation" to "V",
                "targetSerial" to "",
                "includeAllColumns" to "",
                "includeTableMetadata" to "",
                "includeColumnMetadata" to "",
                "includeRowDescriptions" to ""
            )
        ),
        SubelementType(
            type = "feeReview",
            label = "Fee Review",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "includeDetail" to ""
            )
        ),
        SubelementType(
            type = "postingRequest",
            label = "Posting Request",
            defaultProperties = linkedMapOf(
                "description" to "",
                "source" to ""
            )
        ),
        SubelementType(
            type = "contentsFrom",
            label = "Contents From",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "columnName" to ""
            )
        ),
        SubelementType(
            type = "tableList",
            label = "Table List",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "includeChildren" to "",
                "includeDetail" to ""
            )
        ),
        SubelementType(
            type = "monetary",
            label = "Monetary",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "amount" to "",
                "description" to ""
            )
        ),
        SubelementType(
            type = "field",
            label = "Field",
            defaultProperties = linkedMapOf(
                "columnName" to "",
                "newContents" to "",
                "operation" to ""
            ),
            parentConstraint = "record"
        ),
        SubelementType(
            type = "parameter",
            label = "Parameter",
            defaultProperties = linkedMapOf(
                "columnName" to "",
                "contents" to ""
            ),
            parentConstraint = "search"
        )
    )

    // Tree editor
    private val rootNode = DefaultMutableTreeNode(QueryNode("query", "Query"))
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    // Properties panel (right side of tree)
    private val propsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(4)
    }

    // XML preview
    private val xmlPreview = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }

    // Results
    private val resultArea = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }

    private val statusLabel = JLabel("Ready")
    private val tabbedPane = JBTabbedPane()

    val component: JComponent

    init {
        // Build default tree structure: query > sequence > transaction > step
        val seqNode = DefaultMutableTreeNode(QueryNode("sequence", "Sequence"))
        val txnNode = DefaultMutableTreeNode(QueryNode("transaction", "Transaction"))
        val stepNode = DefaultMutableTreeNode(QueryNode("step", "Step"))
        txnNode.add(stepNode)
        seqNode.add(txnNode)
        rootNode.add(seqNode)
        treeModel.reload()
        tree.expandRow(0)
        tree.expandRow(1)
        tree.expandRow(2)

        // Tree selection updates properties
        tree.addTreeSelectionListener { updatePropsPanel() }

        // Toolbar buttons
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(createAddDropdownButton())
            add(JButton("Remove").apply {
                addActionListener { removeSelectedNode() }
            })
            add(Box.createHorizontalStrut(16))
            add(JButton("Verify").apply {
                addActionListener { executeQuery(verify = true) }
            })
            add(JButton("Post").apply {
                addActionListener { executeQuery(verify = false) }
            })
            add(JButton("Generate JS").apply {
                addActionListener { generateJavaScript() }
            })
        }

        // Tree + props split
        val treeScroll = JBScrollPane(tree)
        val propsScroll = JBScrollPane(propsPanel)
        val treePropsSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, propsScroll).apply {
            dividerLocation = 250
        }

        // Tabs
        tabbedPane.addTab("Tree", treePropsSplit)
        tabbedPane.addTab("XML Preview", JBScrollPane(xmlPreview))
        tabbedPane.addTab("Results", JBScrollPane(resultArea))

        // Update XML preview when tab changes
        tabbedPane.addChangeListener {
            if (tabbedPane.selectedIndex == 1) {
                xmlPreview.text = buildXml()
            }
        }

        component = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(tabbedPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
            border = JBUI.Borders.empty(4)
        }
    }

    private data class QueryNode(
        val element: String,
        val label: String,
        val properties: MutableMap<String, String> = mutableMapOf()
    ) {
        override fun toString() = if (properties.isEmpty()) label
        else "$label (${properties.entries.joinToString(", ") { "${it.key}=${it.value}" }})"
    }

    /**
     * Creates a dropdown button that shows available subelement types
     * based on the currently selected tree node.
     */
    private fun createAddDropdownButton(): JButton {
        val button = JButton("Add...")
        button.addActionListener {
            val popup = JPopupMenu()
            val availableTypes = getAvailableSubelements()

            if (availableTypes.isEmpty()) {
                val item = JMenuItem("(no elements can be added here)")
                item.isEnabled = false
                popup.add(item)
            } else {
                for (subType in availableTypes) {
                    val item = JMenuItem(subType.label)
                    item.addActionListener { addSubelement(subType) }
                    popup.add(item)
                }
            }

            popup.show(button, 0, button.height)
        }
        return button
    }

    /**
     * Determines which subelement types can be added based on the currently
     * selected node in the tree.
     */
    private fun getAvailableSubelements(): List<SubelementType> {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val selectedQn = selected?.userObject as? QueryNode

        // Determine the effective parent element type
        val parentElement = selectedQn?.element

        return subelementTypes.filter { subType ->
            when (subType.parentConstraint) {
                "step" -> {
                    // Step-level subelements: addable when a step node is selected,
                    // or when any node is selected and we can find a step node
                    parentElement == "step" || (parentElement != "search" && parentElement != "record" && findStepNode() != null)
                }
                "record" -> {
                    // Field is only addable under a record node
                    parentElement == "record"
                }
                "search" -> {
                    // Parameter is only addable under a search node
                    parentElement == "search"
                }
                else -> false
            }
        }
    }

    /**
     * Adds a subelement of the given type under the appropriate parent node.
     */
    private fun addSubelement(subType: SubelementType) {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val selectedQn = selected.userObject as? QueryNode ?: return

        // Determine the target parent node based on the constraint
        val targetParent: DefaultMutableTreeNode = when (subType.parentConstraint) {
            "step" -> {
                if (selectedQn.element == "step") selected
                else findStepNode() ?: return
            }
            "record" -> {
                if (selectedQn.element == "record") selected
                else return
            }
            "search" -> {
                if (selectedQn.element == "search") selected
                else return
            }
            else -> return
        }

        val newNode = DefaultMutableTreeNode(
            QueryNode(
                subType.type,
                subType.label,
                LinkedHashMap(subType.defaultProperties)
            )
        )

        treeModel.insertNodeInto(newNode, targetParent, targetParent.childCount)
        tree.expandPath(javax.swing.tree.TreePath(targetParent.path))
        tree.selectionPath = javax.swing.tree.TreePath(newNode.path)
    }

    private fun findStepNode(): DefaultMutableTreeNode? {
        fun search(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val qn = node.userObject as? QueryNode
            if (qn?.element == "step") return node
            for (i in 0 until node.childCount) {
                val found = search(node.getChildAt(i) as DefaultMutableTreeNode)
                if (found != null) return found
            }
            return null
        }
        return search(rootNode)
    }

    private fun removeSelectedNode() {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val qn = selected.userObject as? QueryNode ?: return
        // Don't allow removing structural nodes
        if (qn.element in setOf("query", "sequence", "transaction", "step")) return
        treeModel.removeNodeFromParent(selected)
    }

    private fun updatePropsPanel() {
        propsPanel.removeAll()
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val qn = selected.userObject as? QueryNode ?: return

        propsPanel.add(JLabel("Element: ${qn.element}"))
        propsPanel.add(Box.createVerticalStrut(8))

        qn.properties.forEach { (key, value) ->
            val label = JLabel("$key:")
            val field = JBTextField(value)
            field.addActionListener {
                qn.properties[key] = field.text
                treeModel.nodeChanged(selected)
            }
            // Also update on focus lost
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) {
                    qn.properties[key] = field.text
                    treeModel.nodeChanged(selected)
                }
            })
            propsPanel.add(label)
            propsPanel.add(field)
            propsPanel.add(Box.createVerticalStrut(4))
        }

        propsPanel.revalidate()
        propsPanel.repaint()
    }

    private fun buildXml(): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        buildNodeXml(rootNode, sb, 0)
        return sb.toString()
    }

    private fun buildNodeXml(node: DefaultMutableTreeNode, sb: StringBuilder, indent: Int) {
        val qn = node.userObject as? QueryNode ?: return
        val pad = "  ".repeat(indent)

        if (qn.element == "query") {
            sb.appendLine("""${pad}<v1:query xmlns:v1="$ns">""")
        } else {
            sb.appendLine("${pad}<v1:${qn.element}>")
        }

        // Render properties as child elements
        qn.properties.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                if (key in optionTypeProperties) {
                    sb.appendLine("""${pad}  <v1:$key option="${escapeXml(value)}"/>""")
                } else if (key == "contents" && qn.element == "search") {
                    // Legacy search contents rendering as inline parameter
                    sb.appendLine("${pad}  <v1:parameter>")
                    sb.appendLine("${pad}    <v1:contents>${escapeXml(value)}</v1:contents>")
                    sb.appendLine("${pad}  </v1:parameter>")
                } else {
                    sb.appendLine("${pad}  <v1:$key>${escapeXml(value)}</v1:$key>")
                }
            }
        }

        // Render children
        for (i in 0 until node.childCount) {
            buildNodeXml(node.getChildAt(i) as DefaultMutableTreeNode, sb, indent + 1)
        }

        sb.appendLine("${pad}</v1:${qn.element}>")
    }

    private fun executeQuery(verify: Boolean) {
        val xml = buildXml()

        // Inject postingMode for verify — uses option attribute syntax
        val finalXml = if (verify) {
            xml.replace("<v1:transaction>", """<v1:transaction>
      <v1:postingMode option="V"/>""")
        } else xml

        statusLabel.text = if (verify) "Verifying..." else "Posting..."

        scope.launch {
            try {
                val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
                val response = postXml("$proxyBase/DirectXMLPostJSON", finalXml)

                SwingUtilities.invokeLater {
                    resultArea.text = formatJson(response)
                    tabbedPane.selectedIndex = 2 // Switch to Results tab

                    val hasError = response.contains("\"exception\"") || response.contains("\"error\"")
                    val result = if (verify) "Verification" else "Post"
                    statusLabel.text = if (hasError) "$result completed with errors" else "$result successful"
                }
            } catch (e: Exception) {
                log.warn("Query execution failed", e)
                SwingUtilities.invokeLater {
                    resultArea.text = "Error: ${e.message}"
                    tabbedPane.selectedIndex = 2
                    statusLabel.text = "Query failed: ${e.message}"
                }
            }
        }
    }

    private fun generateJavaScript() {
        // Build JS code that constructs the same query using CR.XML API
        val sb = StringBuilder()
        sb.appendLine("// Generated Keyscript query")
        sb.appendLine("(function() {")
        sb.appendLine("  var xml = CR.XML.createDocument('query', '$ns');")
        sb.appendLine("  var seq = CR.XML.addElement(xml.documentElement, 'sequence');")
        sb.appendLine("  var txn = CR.XML.addElement(seq, 'transaction');")
        sb.appendLine("  var step = CR.XML.addElement(txn, 'step');")

        // Find operation nodes under step and generate JS recursively
        val stepNode = findStepNode()
        if (stepNode != null) {
            generateJsForChildren(stepNode, "step", sb, "  ")
        }

        sb.appendLine("")
        sb.appendLine("  var xmlStr = CR.XML.serialize(xml);")
        sb.appendLine("  CR.Ajax.request({")
        sb.appendLine("    url: 'DirectXMLPostJSON',")
        sb.appendLine("    method: 'POST',")
        sb.appendLine("    headers: {'Content-Type': 'text/xml'},")
        sb.appendLine("    xmlData: xmlStr,")
        sb.appendLine("    success: function(response) {")
        sb.appendLine("      var data = JSON.parse(response.responseText);")
        sb.appendLine("      console.log('Query result:', data);")
        sb.appendLine("    },")
        sb.appendLine("    failure: function(response) {")
        sb.appendLine("      console.error('Query failed:', response.statusText);")
        sb.appendLine("    }")
        sb.appendLine("  });")
        sb.appendLine("})();")

        resultArea.text = sb.toString()
        tabbedPane.selectedIndex = 2
        statusLabel.text = "JavaScript generated — copy to your script"
    }

    /**
     * Recursively generates JavaScript for child nodes of a parent tree node.
     */
    private fun generateJsForChildren(
        parentTreeNode: DefaultMutableTreeNode,
        parentVarName: String,
        sb: StringBuilder,
        indent: String
    ) {
        for (i in 0 until parentTreeNode.childCount) {
            val childTreeNode = parentTreeNode.getChildAt(i) as DefaultMutableTreeNode
            val op = childTreeNode.userObject as? QueryNode ?: continue

            val varName = "${op.element}${i}"
            sb.appendLine("${indent}var $varName = CR.XML.addElement($parentVarName, '${op.element}');")
            op.properties.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    if (key == "contents" && op.element == "search") {
                        sb.appendLine("${indent}var param = CR.XML.addElement($varName, 'parameter');")
                        sb.appendLine("${indent}CR.XML.addTextElement(param, 'contents', '${escapeJs(value)}');")
                    } else if (key in optionTypeProperties) {
                        sb.appendLine("${indent}var ${key}El = CR.XML.addElement($varName, '$key');")
                        sb.appendLine("${indent}${key}El.setAttribute('option', '${escapeJs(value)}');")
                    } else {
                        sb.appendLine("${indent}CR.XML.addTextElement($varName, '$key', '${escapeJs(value)}');")
                    }
                }
            }

            // Recurse into children (e.g., field under record, parameter under search)
            if (childTreeNode.childCount > 0) {
                generateJsForChildren(childTreeNode, varName, sb, indent)
            }
        }
    }

    // --- Helpers ---

    private fun postXml(url: String, xml: String): String {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "text/xml")
        conn.doOutput = true
        conn.outputStream.use { it.write(xml.toByteArray()) }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun escapeJs(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")

    private fun formatJson(json: String): String {
        // Simple JSON formatter — add newlines after { and , for readability
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        for (c in json) {
            when {
                c == '"' -> { inString = !inString; sb.append(c) }
                inString -> sb.append(c)
                c == '{' || c == '[' -> { sb.append(c); sb.appendLine(); indent++; sb.append("  ".repeat(indent)) }
                c == '}' || c == ']' -> { sb.appendLine(); indent--; sb.append("  ".repeat(indent)); sb.append(c) }
                c == ',' -> { sb.append(c); sb.appendLine(); sb.append("  ".repeat(indent)) }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
