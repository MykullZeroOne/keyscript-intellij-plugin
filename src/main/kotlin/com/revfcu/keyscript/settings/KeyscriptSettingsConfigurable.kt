package com.revfcu.keyscript.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class KeyscriptSettingsConfigurable : Configurable {

    private var mySettingsComponent: JPanel? = null
    private val keybridgeUrlField = JBTextField()
    private val keystoneExecutionUrlField = JBTextField()

    override fun getDisplayName(): String = "Keyscript IDE"

    override fun createComponent(): JComponent? {
        mySettingsComponent = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Keybridge API URL: "), keybridgeUrlField, 1, false)
            .addLabeledComponent(JBLabel("Keystone Execution URL: "), keystoneExecutionUrlField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return mySettingsComponent
    }

    override fun isModified(): Boolean {
        val settings = KeyscriptSettingsService.getInstance()
        return keybridgeUrlField.text != settings.keybridgeUrl ||
               keystoneExecutionUrlField.text != settings.keystoneExecutionUrl
    }

    override fun apply() {
        val settings = KeyscriptSettingsService.getInstance()
        settings.keybridgeUrl = keybridgeUrlField.text
        settings.keystoneExecutionUrl = keystoneExecutionUrlField.text
    }

    override fun reset() {
        val settings = KeyscriptSettingsService.getInstance()
        keybridgeUrlField.text = settings.keybridgeUrl
        keystoneExecutionUrlField.text = settings.keystoneExecutionUrl
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}
