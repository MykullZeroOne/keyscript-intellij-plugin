package com.keyscript.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "com.keyscript.plugin.settings.KeyscriptSettings",
    storages = [Storage("KeyscriptSettings.xml")]
)
class KeyscriptSettings : PersistentStateComponent<KeyscriptSettings.State> {

    data class State(
        var proxyEndpoint: String = "",
        var keystoneApiUrl: String = "",
        var supportedInstances: String = "",
        var proxyPort: Int = 3000,
        var servicePort: Int = 3001,
        var deviceServiceUrl: String = "",
        var deviceName: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): KeyscriptSettings =
            ApplicationManager.getApplication().getService(KeyscriptSettings::class.java)
    }

    var proxyEndpoint: String
        get() = myState.proxyEndpoint
        set(value) { myState.proxyEndpoint = value }

    var supportedInstances: List<String>
        get() = myState.supportedInstances.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        set(value) { myState.supportedInstances = value.joinToString(",") }

    var proxyPort: Int
        get() = myState.proxyPort
        set(value) { myState.proxyPort = value }

    var servicePort: Int
        get() = myState.servicePort
        set(value) { myState.servicePort = value }

    var keystoneApiUrl: String
        get() = myState.keystoneApiUrl
        set(value) { myState.keystoneApiUrl = value }

    var deviceServiceUrl: String
        get() = myState.deviceServiceUrl
        set(value) { myState.deviceServiceUrl = value }

    var deviceName: String
        get() = myState.deviceName
        set(value) { myState.deviceName = value }

    /** Build the direct Keystone API URL (e.g. http://keystonedev.revfcu.com:52310) */
    fun getKeystoneApiBaseUrl(): String {
        val url = keystoneApiUrl.trimEnd('/')
        return if (url.startsWith("http")) url else "http://$url"
    }

    /** Build the full proxy URL with protocol */
    fun getProxyUrl(): String {
        val endpoint = proxyEndpoint
        val useHttps = endpoint.startsWith("https") || endpoint.endsWith(":8443") || endpoint.endsWith(":443")
        return if (useHttps && !endpoint.startsWith("https")) "https://$endpoint" else endpoint
    }

    /** Get the first supported instance (default) */
    fun getDefaultInstance(): String = supportedInstances.firstOrNull() ?: "Test"
}
