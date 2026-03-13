package com.keyscript.plugin.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.keyscript.plugin.services.ProxyServerService
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
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
        "formatOption", "orderOption", "reportOption",
        "specifiedFeeOption"
    )

    /**
     * All known step subelement types from the Corelation query language,
     * with their complete properties and parent constraints.
     * Empty defaults mean the property is optional — only included in XML if a value is provided.
     */
    private val subelementTypes = listOf(
        // ── Core step subelements ──────────────────────────────
        SubelementType(
            type = "search",
            label = "Search",
            defaultProperties = linkedMapOf(
                "tableName" to "PERSON",
                "filterName" to "BY_LAST_FIRST_MIDDLE_NAME",
                "returnLimit" to "20",
                "resumeCounter" to "",
                "includeSelectColumns" to "Y",
                "includeTotalHitCount" to "Y",
                "includeRecordDetail" to ""
            )
        ),
        SubelementType(
            type = "record",
            label = "Record",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "operation" to "V",
                "targetSerial" to "",
                "includeAllColumns" to "Y",
                "includeTableMetadata" to "",
                "includeColumnMetadata" to "",
                "includeRowDescriptions" to "",
                "includeRecordDetail" to ""
            )
        ),
        SubelementType(
            type = "feeReview",
            label = "Fee Review",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "targetCategory" to "",
                "includeDetail" to "Y",
                "comment" to "",
                "specifiedFeeOption" to "",
                "postingPolicySerial" to ""
            )
        ),
        SubelementType(
            type = "postingRequest",
            label = "Posting Request",
            defaultProperties = linkedMapOf(
                "targetCategory" to "S",
                "targetSerial" to "",
                "category" to "D",
                "source" to "S",
                "amount" to "",
                "description" to ""
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
                "includeTableMetadata" to "Y",
                "includeColumnMetadata" to "",
                "includeAllColumns" to "",
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
        // ── Additional step subelements from the full API ──────
        SubelementType(
            type = "tranHistory",
            label = "Transaction History",
            defaultProperties = linkedMapOf(
                "targetCategory" to "S",
                "targetSerial" to "",
                "reportOption" to "ALL",
                "formatOption" to "S",
                "orderOption" to "RT",
                "includeMonetary" to "",
                "postingDateMinimum" to "",
                "postingDateMaximum" to "",
                "returnLimit" to "50",
                "resumeBookmark" to ""
            )
        ),
        SubelementType(
            type = "postingStatus",
            label = "Posting Status",
            defaultProperties = linkedMapOf(
                "targetCategory" to "S",
                "targetSerial" to ""
            )
        ),
        SubelementType(
            type = "recordTree",
            label = "Record Tree",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "includeChildren" to "Y",
                "includeDetail" to "Y"
            )
        ),
        SubelementType(
            type = "recordReference",
            label = "Record Reference",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to "",
                "includeTotalHitCount" to "Y"
            )
        ),
        SubelementType(
            type = "searchList",
            label = "Search List",
            defaultProperties = linkedMapOf(
                "tableName" to ""
            )
        ),
        SubelementType(
            type = "loanPayoffRequest",
            label = "Loan Payoff Request",
            defaultProperties = linkedMapOf(
                "targetSerial" to "",
                "payoffDate" to ""
            )
        ),
        SubelementType(
            type = "shareLoanFM",
            label = "Share/Loan FM",
            defaultProperties = linkedMapOf(
                "tableName" to "",
                "targetSerial" to ""
            )
        ),
        SubelementType(
            type = "shareLoanCorrection",
            label = "Share/Loan Correction",
            defaultProperties = linkedMapOf(
                "operation" to "POST",
                "tableName" to "",
                "targetSerial" to "",
                "correctionDate" to "",
                "amount" to "",
                "description" to ""
            )
        ),
        // ── Child-only subelements ─────────────────────────────
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
                "parameterName" to "",
                "contents" to ""
            ),
            parentConstraint = "search"
        ),
        SubelementType(
            type = "selectColumn",
            label = "Select Column",
            defaultProperties = linkedMapOf(
                "columnName" to ""
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

    // JSON preview
    private val jsonPreview = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }

    // Results
    private val resultArea = JBTextArea().apply {
        isEditable = false
        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12)
    }

    // JS generation
    private val jsArea = JBTextArea().apply {
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

        // Speed search on tree
        TreeSpeedSearch.installOn(tree, false) { path -> path.lastPathComponent.toString() }

        // Toolbar actions — "Add..." opens a dynamic popup built at action-perform time
        val addAction = object : AnAction("Add...", "Add a subelement to the query", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                val availableTypes = getAvailableSubelements()
                val popupGroup = DefaultActionGroup()
                if (availableTypes.isEmpty()) {
                    val noOp = object : AnAction("(no elements can be added here)") {
                        override fun actionPerformed(e: AnActionEvent) {}
                        override fun update(e: AnActionEvent) { e.presentation.isEnabled = false }
                        override fun getActionUpdateThread() = ActionUpdateThread.BGT
                    }
                    popupGroup.add(noOp)
                } else {
                    for (subType in availableTypes) {
                        val subAction = object : AnAction(subType.label) {
                            override fun actionPerformed(e: AnActionEvent) { addSubelement(subType) }
                            override fun getActionUpdateThread() = ActionUpdateThread.BGT
                        }
                        popupGroup.add(subAction)
                    }
                }
                val inputEvent = e.inputEvent
                val popup = JBPopupFactory.getInstance().createActionGroupPopup(
                    null, popupGroup, e.dataContext,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false
                )
                val sourceComponent = inputEvent?.component
                if (sourceComponent != null) {
                    popup.show(RelativePoint(sourceComponent, java.awt.Point(0, sourceComponent.height)))
                } else {
                    popup.showInBestPositionFor(e.dataContext)
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        val removeAction = object : AnAction("Remove", "Remove selected node", AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) { removeSelectedNode() }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val verifyAction = object : AnAction("Verify", "Verify query against server", AllIcons.Actions.Execute) {
            override fun actionPerformed(e: AnActionEvent) { executeQuery(verify = true) }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val postAction = object : AnAction("Post", "Post query to server", AllIcons.Actions.Upload) {
            override fun actionPerformed(e: AnActionEvent) { executeQuery(verify = false) }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val copyXmlAction = object : AnAction("Copy XML", "Copy generated XML to clipboard", AllIcons.Actions.Copy) {
            override fun actionPerformed(e: AnActionEvent) {
                copyToClipboard(buildXml())
                val inputEvent = e.inputEvent
                if (inputEvent != null) {
                    val balloon = JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("Copied to clipboard", MessageType.INFO, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                    balloon.show(
                        RelativePoint.getCenterOf(inputEvent.component as JComponent),
                        Balloon.Position.above
                    )
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        val httpClientAction = object : AnAction("HTTP Client", "Open query as an IntelliJ HTTP request file", AllIcons.General.Web) {
            override fun actionPerformed(e: AnActionEvent) { openInHttpClient() }
            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        val toolbarGroup = DefaultActionGroup().apply {
            add(addAction)
            add(removeAction)
            addSeparator()
            add(verifyAction)
            add(postAction)
            addSeparator()
            add(copyXmlAction)
            add(httpClientAction)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("KeyscriptQueryBuilder", toolbarGroup, true)
        toolbar.targetComponent = mainPanel

        // Tree + props split
        val treeScroll = JBScrollPane(tree)
        val propsScroll = JBScrollPane(propsPanel)
        val treePropsSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, propsScroll).apply {
            dividerLocation = 250
        }

        // Tabs
        tabbedPane.addTab("Tree", treePropsSplit)
        tabbedPane.addTab("XML Preview", JBScrollPane(xmlPreview))
        tabbedPane.addTab("JSON Preview", JBScrollPane(jsonPreview))
        tabbedPane.addTab("JavaScript", JBScrollPane(jsArea))
        tabbedPane.addTab("Results", JBScrollPane(resultArea))

        // Update preview when tab changes
        tabbedPane.addChangeListener {
            when (tabbedPane.selectedIndex) {
                1 -> xmlPreview.text = buildXml()
                2 -> jsonPreview.text = buildJson()
                3 -> jsArea.text = buildJavaScript()
            }
        }

        mainPanel.add(toolbar.component, BorderLayout.NORTH)
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        mainPanel.add(statusLabel, BorderLayout.SOUTH)

        component = mainPanel
    }

    private data class QueryNode(
        val element: String,
        val label: String,
        val properties: MutableMap<String, String> = mutableMapOf()
    ) {
        override fun toString(): String {
            val nonEmpty = properties.entries.filter { it.value.isNotEmpty() }
            return if (nonEmpty.isEmpty()) label
            else "$label (${nonEmpty.joinToString(", ") { "${it.key}=${it.value}" }})"
        }
    }

    /**
     * Determines which subelement types can be added based on the currently
     * selected node in the tree.
     */
    private fun getAvailableSubelements(): List<SubelementType> {
        val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val selectedQn = selected?.userObject as? QueryNode

        val parentElement = selectedQn?.element

        return subelementTypes.filter { subType ->
            when (subType.parentConstraint) {
                "step" -> {
                    parentElement == "step" || (parentElement != "search" && parentElement != "record" && findStepNode() != null)
                }
                "record" -> parentElement == "record"
                "search" -> parentElement == "search"
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

        propsPanel.add(JLabel("Element: ${qn.element}").apply {
            font = font.deriveFont(java.awt.Font.BOLD)
        })
        propsPanel.add(Box.createVerticalStrut(4))
        propsPanel.add(JLabel("<html><i>Empty fields are omitted from output.</i></html>").apply {
            foreground = com.intellij.util.ui.UIUtil.getContextHelpForeground()
            font = font.deriveFont(font.size2D - 1f)
        })
        propsPanel.add(Box.createVerticalStrut(8))

        qn.properties.forEach { (key, value) ->
            val isOption = key in optionTypeProperties
            val label = JLabel(if (isOption) "$key (option):" else "$key:")

            val field: JComponent
            // Use dropdown for well-known option values
            val knownOptions = getKnownOptions(key)
            if (knownOptions != null) {
                val combo = JComboBox(arrayOf("") + knownOptions)
                combo.selectedItem = value
                combo.isEditable = true
                combo.addActionListener {
                    qn.properties[key] = combo.selectedItem?.toString() ?: ""
                    treeModel.nodeChanged(selected)
                }
                field = combo
            } else {
                val tf = JBTextField(value)
                tf.addActionListener {
                    qn.properties[key] = tf.text
                    treeModel.nodeChanged(selected)
                }
                tf.addFocusListener(object : java.awt.event.FocusAdapter() {
                    override fun focusLost(e: java.awt.event.FocusEvent?) {
                        qn.properties[key] = tf.text
                        treeModel.nodeChanged(selected)
                    }
                })
                field = tf
            }

            propsPanel.add(label)
            propsPanel.add(field)
            propsPanel.add(Box.createVerticalStrut(4))
        }

        propsPanel.revalidate()
        propsPanel.repaint()
    }

    /**
     * Returns known option values for well-known properties, or null if free-text.
     */
    private fun getKnownOptions(key: String): Array<String>? = when (key) {
        "operation" -> arrayOf("V", "U", "I", "D", "M", "POST")
        "category" -> arrayOf("D", "W", "P", "A", "R", "N", "C")
        "targetCategory" -> arrayOf("S", "L", "K", "A", "C")
        "source" -> arrayOf("S", "K", "C", "A", "H", "M", "a", "P", "B", "F")
        "includeSelectColumns", "includeTotalHitCount", "includeChildren",
        "includeDetail", "includeMonetary", "includeRecordDetail",
        "includeRowDescriptions", "includeTableMetadata",
        "includeColumnMetadata", "includeAllColumns" -> arrayOf("Y", "N")
        "specifiedFeeOption" -> arrayOf("Y", "N")
        "reportOption" -> arrayOf("ALL", "MST", "GLR", "TRN")
        "formatOption" -> arrayOf("S", "T")
        "orderOption" -> arrayOf("T", "RT")
        else -> null
    }

    // ── XML Generation ─────────────────────────────────────────

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

        // Render properties as child elements — skip empty values
        qn.properties.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                if (key in optionTypeProperties) {
                    sb.appendLine("""${pad}  <v1:$key option="${escapeXml(value)}"/>""")
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

    // ── JSON Generation ────────────────────────────────────────

    private fun buildJson(): String {
        val sb = StringBuilder()
        buildNodeJson(rootNode, sb, 0)
        return sb.toString()
    }

    private fun buildNodeJson(node: DefaultMutableTreeNode, sb: StringBuilder, indent: Int) {
        val qn = node.userObject as? QueryNode ?: return
        val pad = "  ".repeat(indent)
        val innerPad = "  ".repeat(indent + 1)

        sb.appendLine("${pad}{")
        sb.appendLine("${innerPad}\"${qn.element}\": {")

        val entries = mutableListOf<String>()

        // Properties — skip empty values
        qn.properties.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                if (key in optionTypeProperties) {
                    entries.add("${innerPad}  \"$key\": { \"option\": \"${escapeJson(value)}\" }")
                } else {
                    entries.add("${innerPad}  \"$key\": \"${escapeJson(value)}\"")
                }
            }
        }

        // Group children by element type for JSON arrays
        val childGroups = linkedMapOf<String, MutableList<DefaultMutableTreeNode>>()
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val childQn = child.userObject as? QueryNode ?: continue
            childGroups.getOrPut(childQn.element) { mutableListOf() }.add(child)
        }

        childGroups.forEach { (elementType, children) ->
            val childSb = StringBuilder()
            if (children.size == 1) {
                buildNodeJsonInline(children[0], childSb, indent + 2)
                entries.add("${innerPad}  \"$elementType\": ${childSb.toString().trim()}")
            } else {
                val items = children.map { child ->
                    val itemSb = StringBuilder()
                    buildNodeJsonInline(child, itemSb, indent + 3)
                    itemSb.toString().trim()
                }
                entries.add("${innerPad}  \"$elementType\": [\n${items.joinToString(",\n") { "${innerPad}    $it" }}\n${innerPad}  ]")
            }
        }

        sb.appendLine(entries.joinToString(",\n"))
        sb.appendLine("${innerPad}}")
        sb.append("${pad}}")
    }

    private fun buildNodeJsonInline(node: DefaultMutableTreeNode, sb: StringBuilder, indent: Int) {
        val qn = node.userObject as? QueryNode ?: return
        val pad = "  ".repeat(indent)

        val entries = mutableListOf<String>()

        qn.properties.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                if (key in optionTypeProperties) {
                    entries.add("\"$key\": { \"option\": \"${escapeJson(value)}\" }")
                } else {
                    entries.add("\"$key\": \"${escapeJson(value)}\"")
                }
            }
        }

        // Children
        val childGroups = linkedMapOf<String, MutableList<DefaultMutableTreeNode>>()
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val childQn = child.userObject as? QueryNode ?: continue
            childGroups.getOrPut(childQn.element) { mutableListOf() }.add(child)
        }

        childGroups.forEach { (elementType, children) ->
            if (children.size == 1) {
                val childSb = StringBuilder()
                buildNodeJsonInline(children[0], childSb, indent + 1)
                entries.add("\"$elementType\": ${childSb.toString().trim()}")
            } else {
                val items = children.map { child ->
                    val itemSb = StringBuilder()
                    buildNodeJsonInline(child, itemSb, indent + 1)
                    itemSb.toString().trim()
                }
                entries.add("\"$elementType\": [${items.joinToString(", ")}]")
            }
        }

        sb.append("${pad}{ ${entries.joinToString(", ")} }")
    }

    // ── JavaScript Generation ──────────────────────────────────

    private fun buildJavaScript(): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated Keyscript query")
        sb.appendLine("(function() {")
        sb.appendLine("  var xml = new CR.XML();")
        sb.appendLine("  var root = xml.getRootElement();")
        sb.appendLine("  var seq = xml.addContainer(root, 'sequence');")
        sb.appendLine("  var txn = xml.addContainer(seq, 'transaction');")
        sb.appendLine("  var step = xml.addContainer(txn, 'step');")

        val stepNode = findStepNode()
        if (stepNode != null) {
            generateJsForChildren(stepNode, "step", sb, "  ")
        }

        sb.appendLine("")
        sb.appendLine("  CR.Core.ajaxRequest({")
        sb.appendLine("    url: 'DirectXMLPostJSON',")
        sb.appendLine("    xmlData: xml.getXMLDocument(),")
        sb.appendLine("    success: function(response) {")
        sb.appendLine("      var data = CR.JSON.parse(response.responseText);")
        sb.appendLine("      console.log('Query result:', data);")
        sb.appendLine("    },")
        sb.appendLine("    failure: function(response) {")
        sb.appendLine("      console.error('Query failed:', response.statusText);")
        sb.appendLine("    }")
        sb.appendLine("  });")
        sb.appendLine("})();")

        return sb.toString()
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
            sb.appendLine("${indent}var $varName = xml.addContainer($parentVarName, '${op.element}');")
            op.properties.forEach { (key, value) ->
                if (value.isNotEmpty()) {
                    if (key in optionTypeProperties) {
                        sb.appendLine("${indent}xml.addOption($varName, '$key', '${escapeJs(value)}');")
                    } else {
                        sb.appendLine("${indent}xml.addText($varName, '$key', '${escapeJs(value)}');")
                    }
                }
            }

            // Recurse into children (e.g., field under record, parameter under search)
            if (childTreeNode.childCount > 0) {
                generateJsForChildren(childTreeNode, varName, sb, indent)
            }
        }
    }

    // ── Query Execution ────────────────────────────────────────

    private fun executeQuery(verify: Boolean) {
        val xml = buildXml()

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
                    tabbedPane.selectedIndex = 4 // Results tab

                    val hasError = response.contains("\"exception\"") || response.contains("\"error\"")
                    val result = if (verify) "Verification" else "Post"
                    statusLabel.text = if (hasError) "$result completed with errors" else "$result successful"
                }
            } catch (e: Exception) {
                log.warn("Query execution failed", e)
                SwingUtilities.invokeLater {
                    resultArea.text = "Error: ${e.message}"
                    tabbedPane.selectedIndex = 4
                    statusLabel.text = "Query failed: ${e.message}"
                }
            }
        }
    }

    // ── HTTP Client Integration ────────────────────────────────

    private fun openInHttpClient() {
        val xml = buildXml()
        val proxyBase = try {
            ProxyServerService.getInstance(project).getProxyBaseUrl()
        } catch (_: Exception) {
            "http://localhost:3000"
        }

        val httpContent = buildString {
            appendLine("### Keyscript Query — Generated by Query Builder")
            appendLine("POST $proxyBase/DirectXMLPostJSON")
            appendLine("Content-Type: text/xml")
            appendLine()
            append(xml)
        }

        SwingUtilities.invokeLater {
            val file = LightVirtualFile("keyscript-query.http", httpContent)
            FileEditorManager.getInstance(project).openFile(file, true)
            statusLabel.text = "Opened in HTTP Client"
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        statusLabel.text = "Copied to clipboard"
    }

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

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun formatJson(json: String): String {
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
