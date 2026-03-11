package com.keyscript.plugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class KeyscriptSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private val endpointField = JBTextField()
    private val keystoneApiUrlField = JBTextField()
    private val instancesField = JBTextField()
    private val proxyPortField = JBTextField()
    private val servicePortField = JBTextField()
    private val deviceServiceUrlField = JBTextField()

    override fun getDisplayName(): String = "Keyscript IDE"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Keystone Proxy Endpoint:"), endpointField, 1, false)
            .addLabeledComponent(JBLabel("Keystone API URL:"), keystoneApiUrlField, 1, false)
            .addLabeledComponent(JBLabel("Supported Instances (comma-separated):"), instancesField, 1, false)
            .addLabeledComponent(JBLabel("Local Proxy Port:"), proxyPortField, 1, false)
            .addLabeledComponent(JBLabel("Device Service Port:"), servicePortField, 1, false)
            .addLabeledComponent(JBLabel("Device Service URL (optional):"), deviceServiceUrlField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = KeyscriptSettings.getInstance()
        return endpointField.text != s.proxyEndpoint ||
                keystoneApiUrlField.text != s.keystoneApiUrl ||
                instancesField.text != s.supportedInstances.joinToString(", ") ||
                proxyPortField.text != s.proxyPort.toString() ||
                servicePortField.text != s.servicePort.toString() ||
                deviceServiceUrlField.text != s.deviceServiceUrl
    }

    override fun apply() {
        val s = KeyscriptSettings.getInstance()
        s.proxyEndpoint = endpointField.text
        s.keystoneApiUrl = keystoneApiUrlField.text
        s.supportedInstances = instancesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        s.proxyPort = proxyPortField.text.toIntOrNull() ?: 3000
        s.servicePort = servicePortField.text.toIntOrNull() ?: 3001
        s.deviceServiceUrl = deviceServiceUrlField.text
    }

    override fun reset() {
        val s = KeyscriptSettings.getInstance()
        endpointField.text = s.proxyEndpoint
        keystoneApiUrlField.text = s.keystoneApiUrl
        instancesField.text = s.supportedInstances.joinToString(", ")
        proxyPortField.text = s.proxyPort.toString()
        servicePortField.text = s.servicePort.toString()
        deviceServiceUrlField.text = s.deviceServiceUrl
    }

    override fun disposeUIResources() {
        panel = null
    }
}
