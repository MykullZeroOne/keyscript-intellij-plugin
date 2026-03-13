package com.keyscript.plugin.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.*

class KeyscriptSettingsConfigurable : BoundConfigurable("Keyscript IDE") {

    override fun createPanel() = panel {
        val settings = KeyscriptSettings.getInstance()

        group("Keystone Server") {
            row("Proxy Endpoint:") {
                textField()
                    .bindText(settings::proxyEndpoint)
                    .columns(COLUMNS_LARGE)
                    .comment("e.g. keystonedev.revfcu.com:8443")
            }
            row("API URL:") {
                textField()
                    .bindText(settings::keystoneApiUrl)
                    .columns(COLUMNS_LARGE)
                    .comment("Direct JSON API endpoint, e.g. http://keystonedev.revfcu.com:52310")
            }
            row("Supported Instances:") {
                textField()
                    .bindText(
                        getter = { settings.supportedInstances.joinToString(", ") },
                        setter = { settings.supportedInstances = it.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() } }
                    )
                    .columns(COLUMNS_LARGE)
                    .comment("Comma-separated list of Keystone instances (e.g. Development, Test)")
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
                    .comment("Port for the mock device service (default: 3001)")
            }
        }
        group("Device") {
            row("Device Service URL:") {
                textField()
                    .bindText(settings::deviceServiceUrl)
                    .columns(COLUMNS_LARGE)
                    .comment("URL for the device service (optional)")
            }
            row("Device Name:") {
                textField()
                    .bindText(settings::deviceName)
                    .columns(COLUMNS_LARGE)
                    .comment("Keystone device name for API access")
            }
        }
    }
}
