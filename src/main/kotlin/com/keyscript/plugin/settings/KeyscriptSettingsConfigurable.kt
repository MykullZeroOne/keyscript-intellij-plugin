package com.keyscript.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.*

class KeyscriptSettingsConfigurable : BoundConfigurable("Keyscript IDE") {

    override fun createPanel() = panel {
        val settings = KeyscriptSettings.getInstance()

        group("Keystone Server") {
            row("Server Endpoint:") {
                textField()
                    .bindText(settings::proxyEndpoint)
                    .columns(COLUMNS_LARGE)
                    .comment("e.g. keystonedev.revfcu.com:8443")
            }
            row("Instances:") {
                textField()
                    .bindText(
                        getter = { settings.supportedInstances.joinToString(", ") },
                        setter = { settings.supportedInstances = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() } }
                    )
                    .columns(COLUMNS_LARGE)
                    .comment("Comma-separated (first is default, e.g. Development, Test, Production)")
            }
        }
        group("Local Services") {
            row("Proxy Port:") {
                intTextField(1..65535)
                    .bindIntText(settings::proxyPort)
                    .comment("Port for the embedded proxy server (default: 3000)")
            }
            row("Device Service Port:") {
                intTextField(1..65535)
                    .bindIntText(settings::servicePort)
                    .comment("Port for the device service (default: 3001)")
            }
        }
        group("Device") {
            row("Device Identifier:") {
                textField()
                    .bindText(settings::deviceServiceUrl)
                    .columns(COLUMNS_LARGE)
                    .comment("e.g. DEVICE_ID: MICHAELSMIT7921")
            }
        }
        group("Security") {
            row {
                checkBox("Trust self-signed certificates")
                    .bindSelected(settings::trustSelfSigned)
                    .comment("Enable for Keystone servers with self-signed TLS certificates. Disable to use system trust store.")
            }
        }
    }
}
