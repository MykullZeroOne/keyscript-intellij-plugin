package com.revfcu.keyscript.auth

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class LoginDialog(project: Project?) : DialogWrapper(project) {
    private val serverUrlField = JBTextField("http://keystonedev.revfcu.com:52310/Development")
    private val userNameField = JBTextField("rev-api-user")
    private val passwordField = JBPasswordField()
    private val deviceNameField = JBTextField("Keybridge-Rev")

    init {
        title = "Keystone Logon"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server URL: "), serverUrlField, 1, false)
            .addLabeledComponent(JBLabel("Username: "), userNameField, 1, false)
            .addLabeledComponent(JBLabel("Password: "), passwordField, 1, false)
            .addLabeledComponent(JBLabel("Device Name: "), deviceNameField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    fun getServerUrl(): String = serverUrlField.text
    fun getUserName(): String = userNameField.text
    fun getPassword(): String = String(passwordField.password)
    fun getDeviceName(): String = deviceNameField.text

    override fun getPreferredFocusedComponent(): JComponent = passwordField
}
