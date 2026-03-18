package com.keyscript.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * CompletionContributor for CR.* namespace — provides context-aware completions
 * for the CR framework (CR.XML, CR.Core, CR.Login, CR.Script, CR.JSON, etc.)
 * and Ext.* methods. Works in Community Edition (no JavaScript plugin required).
 */
class CRCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            CRCompletionProvider()
        )
    }
}

private class CRCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.editor.project ?: return
        if (!com.keyscript.plugin.services.KeyscriptProjectDetector.isKeyscriptProject(project)) return

        val position = parameters.position
        val document = parameters.editor.document
        val offset = parameters.offset
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        val textBefore = document.getText(com.intellij.openapi.util.TextRange(lineStart, offset))

        when {
            textBefore.endsWith("CR.") -> addCRNamespaceCompletions(result)
            textBefore.endsWith("CR.Core.") -> addCRCoreCompletions(result)
            textBefore.endsWith("CR.Login.") -> addCRLoginCompletions(result)
            textBefore.endsWith("CR.Script.") -> addCRScriptCompletions(result)
            textBefore.endsWith("CR.JSON.") -> addCRJsonCompletions(result)
            textBefore.endsWith("Ext.") -> addExtCompletions(result)
            textBefore.endsWith("Ext.Msg.") -> addExtMsgCompletions(result)
            Regex("""\b(xml|doc|xdoc)\.$""").containsMatchIn(textBefore) -> addXmlInstanceCompletions(result)
        }
    }

    private fun addCRNamespaceCompletions(result: CompletionResultSet) {
        val items = listOf(
            "XML" to "XML document builder",
            "JSON" to "JSON parse/stringify",
            "Core" to "Core utilities",
            "Login" to "Session info",
            "Script" to "Script execution",
            "Panel" to "UI Panel",
            "Settings" to "User/role settings",
            "Storage" to "Local storage wrapper",
            "KeyStoneService" to "Service endpoint",
            "GridPanel" to "Data grid component",
            "EditorGridPanel" to "Editable grid",
            "FormPanel" to "Form layout",
            "TabPanel" to "Tabbed container",
            "TextField" to "Text input field",
            "DateField" to "Date picker field",
            "MoneyField" to "Currency input field"
        )
        items.forEach { (name, desc) ->
            result.addElement(LookupElementBuilder.create(name)
                .withTypeText(desc)
                .withIcon(AllIcons.Nodes.Class))
        }
    }

    private fun addCRCoreCompletions(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("ajaxRequest")
            .withTailText("({url, xmlData, success, failure})")
            .withTypeText("Make AJAX request to Keystone")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("displayExceptions")
            .withTailText("({items})")
            .withTypeText("Display error dialog")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("defer")
            .withTailText("(fn)")
            .withTypeText("Defer until Ext.onReady")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("viewPort")
            .withTypeText("Main Ext.Viewport reference")
            .withIcon(AllIcons.Nodes.Property))
        result.addElement(LookupElementBuilder.create("keyStoneWebAppURL")
            .withTypeText("Keystone base URL")
            .withIcon(AllIcons.Nodes.Property))
        result.addElement(LookupElementBuilder.create("findFields")
            .withTailText("(component)")
            .withTypeText("Find CR fields in a container")
            .withIcon(AllIcons.Nodes.Method))
    }

    private fun addCRLoginCompletions(result: CompletionResultSet) {
        listOf(
            "userName" to "Current user name",
            "userSerial" to "Current user serial",
            "sessionID" to "Active session ID",
            "JSESSIONID" to "Java session ID",
            "postingDate" to "Current posting date",
            "locationName" to "Current location",
            "databaseName" to "Database name",
            "instance" to "Keystone instance"
        ).forEach { (name, desc) ->
            result.addElement(LookupElementBuilder.create(name)
                .withTypeText(desc)
                .withIcon(AllIcons.Nodes.Property))
        }
    }

    private fun addCRScriptCompletions(result: CompletionResultSet) {
        listOf(
            "personSerial" to "Script context person serial",
            "accountSerial" to "Script context account serial",
            "scriptDefaultPanelId" to "Default panel ID",
            "scriptDescription" to "Script description",
        ).forEach { (name, desc) ->
            result.addElement(LookupElementBuilder.create(name)
                .withTypeText(desc)
                .withIcon(AllIcons.Nodes.Property))
        }
        result.addElement(LookupElementBuilder.create("runScript")
            .withTailText("(scriptName)")
            .withTypeText("Run another script")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("runForm")
            .withTailText("(config)")
            .withTypeText("Run a form script")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("includeJSCSS")
            .withTailText("(config)")
            .withTypeText("Include external JS/CSS")
            .withIcon(AllIcons.Nodes.Method))
    }

    private fun addCRJsonCompletions(result: CompletionResultSet) {
        result.addElement(LookupElementBuilder.create("parse")
            .withTailText("(jsonString)")
            .withTypeText("Parse JSON string")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("stringify")
            .withTailText("(value)")
            .withTypeText("Stringify to JSON")
            .withIcon(AllIcons.Nodes.Method))
    }

    private fun addExtCompletions(result: CompletionResultSet) {
        listOf(
            Triple("Msg", "Message dialogs", AllIcons.Nodes.Module),
            Triple("Viewport", "ExtJS Viewport", AllIcons.Nodes.Class),
            Triple("Panel", "ExtJS Panel", AllIcons.Nodes.Class),
            Triple("Ajax", "AJAX utilities", AllIcons.Nodes.Module),
        ).forEach { (name, desc, icon) ->
            result.addElement(LookupElementBuilder.create(name).withTypeText(desc).withIcon(icon))
        }
        result.addElement(LookupElementBuilder.create("each")
            .withTailText("(array, fn)")
            .withTypeText("Iterate over array")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("apply")
            .withTailText("(target, source)")
            .withTypeText("Copy properties")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("getCmp")
            .withTailText("(id)")
            .withTypeText("Get component by ID")
            .withIcon(AllIcons.Nodes.Method))
        result.addElement(LookupElementBuilder.create("onReady")
            .withTailText("(fn)")
            .withTypeText("Run when DOM ready")
            .withIcon(AllIcons.Nodes.Method))
    }

    private fun addExtMsgCompletions(result: CompletionResultSet) {
        listOf(
            "alert" to "Show alert dialog",
            "confirm" to "Show confirm dialog",
            "prompt" to "Show prompt dialog",
            "show" to "Show message box"
        ).forEach { (name, desc) ->
            result.addElement(LookupElementBuilder.create(name)
                .withTypeText(desc)
                .withIcon(AllIcons.Nodes.Method))
        }
    }

    private fun addXmlInstanceCompletions(result: CompletionResultSet) {
        listOf(
            "getRootElement" to "Get XML root element",
            "getXMLDocument" to "Get the XML document string",
            "addContainer" to "Add container element",
            "addText" to "Add text element",
            "addOption" to "Add option element",
            "addCount" to "Add count element",
            "addMoney" to "Add money element",
            "addDate" to "Add date element",
            "addRate" to "Add rate element"
        ).forEach { (name, desc) ->
            result.addElement(LookupElementBuilder.create(name)
                .withTypeText(desc)
                .withIcon(AllIcons.Nodes.Method))
        }
    }
}
