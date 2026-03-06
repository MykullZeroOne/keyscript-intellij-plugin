package com.revfcu.keyscript.options

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.FormBuilder
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ScriptOptionsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val scriptOptionsToolWindow = ScriptOptionsToolWindow(project)
        val content = ContentFactory.getInstance().createContent(scriptOptionsToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ScriptOptionsToolWindow(private val project: Project) {
    private val service = ScriptOptionsService.getInstance(project)

    private val personSerialField = JBTextField(service.personSerial)
    private val accountSerialField = JBTextField(service.accountSerial)
    private val noteSerialField = JBTextField(service.noteSerial)
    private val commentSerialField = JBTextField(service.commentSerial)
    private val transactionSerialField = JBTextField(service.transactionSerial)
    private val checkSerialField = JBTextField(service.checkSerial)
    private val cardSerialField = JBTextField(service.cardSerial)

    init {
        setupListeners()
    }

    private fun setupListeners() {
        personSerialField.addDocumentListener { service.personSerial = personSerialField.text }
        accountSerialField.addDocumentListener { service.accountSerial = accountSerialField.text }
        noteSerialField.addDocumentListener { service.noteSerial = noteSerialField.text }
        commentSerialField.addDocumentListener { service.commentSerial = commentSerialField.text }
        transactionSerialField.addDocumentListener { service.transactionSerial = transactionSerialField.text }
        checkSerialField.addDocumentListener { service.checkSerial = checkSerialField.text }
        cardSerialField.addDocumentListener { service.cardSerial = cardSerialField.text }
    }

    fun getContent(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Person Serial: "), personSerialField, 1, false)
            .addLabeledComponent(JBLabel("Account Serial: "), accountSerialField, 1, false)
            .addLabeledComponent(JBLabel("Note Serial: "), noteSerialField, 1, false)
            .addLabeledComponent(JBLabel("Comment Serial: "), commentSerialField, 1, false)
            .addLabeledComponent(JBLabel("Transaction Serial: "), transactionSerialField, 1, false)
            .addLabeledComponent(JBLabel("Check Serial: "), checkSerialField, 1, false)
            .addLabeledComponent(JBLabel("Card Serial: "), cardSerialField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    private fun JBTextField.addDocumentListener(onUpdate: () -> Unit) {
        this.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onUpdate()
            override fun removeUpdate(e: DocumentEvent?) = onUpdate()
            override fun changedUpdate(e: DocumentEvent?) = onUpdate()
        })
    }
}
