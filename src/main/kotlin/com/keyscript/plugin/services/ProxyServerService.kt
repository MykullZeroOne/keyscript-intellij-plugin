package com.keyscript.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.keyscript.plugin.proxy.KtorProxyServer
import com.keyscript.plugin.settings.KeyscriptSettings

/**
 * Manages the lifecycle of the embedded Ktor proxy server.
 * The proxy starts lazily on first use (login, run, search) to avoid
 * slowing down IDE startup.
 */
@Service(Service.Level.PROJECT)
class ProxyServerService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(ProxyServerService::class.java)
    private var server: KtorProxyServer? = null
    private val lock = Any()

    @Volatile
    var ssoSessionId: String = ""
        private set

    @Volatile
    var activeProjectPath: String = ""

    @Volatile
    private var startFailed: Boolean = false

    fun setSsoSession(jsessionId: String) {
        ssoSessionId = jsessionId
        log.info("SSO session updated")
    }

    /** Ensure the proxy is running. Safe to call multiple times. */
    fun ensureStarted() {
        if (server != null) return
        if (startFailed) return // don't retry a failed start in the same session
        synchronized(lock) {
            if (server != null) return
            val settings = KeyscriptSettings.getInstance()
            if (activeProjectPath.isEmpty()) {
                activeProjectPath = project.basePath ?: ""
            }
            val port = settings.proxyPort
            val endpoint = settings.getProxyUrl()
            val instances = settings.supportedInstances
            log.warn("PROXY STARTING: port=$port, endpoint=$endpoint, instances=$instances")
            try {
                val proxyServer = KtorProxyServer(
                    proxyPort = port,
                    proxyEndpoint = endpoint,
                    jsonApiUrl = settings.getKeystoneApiBaseUrl(),
                    supportedInstances = instances,
                    servicePort = settings.servicePort,
                    proxyService = this,
                    networkMonitor = NetworkMonitorService.getInstance(project),
                    session = SessionService.getInstance(project)
                )
                proxyServer.start()
                server = proxyServer
                log.warn("PROXY STARTED OK on port $port")
            } catch (e: Exception) {
                startFailed = true
                log.error("PROXY START FAILED on port $port", e)
            }
        }
    }

    val isRunning: Boolean get() = server != null

    @Deprecated("Use ensureStarted()", replaceWith = ReplaceWith("ensureStarted()"))
    fun start() = ensureStarted()

    fun stop() {
        synchronized(lock) {
            server?.stop()
            server = null
            startFailed = false
            log.info("Proxy server stopped")
        }
    }

    fun restart() {
        stop()
        ensureStarted()
    }

    fun getProxyBaseUrl(): String {
        ensureStarted()
        if (!isRunning) {
            log.error("Proxy server is not running — login will fail")
        }
        val settings = KeyscriptSettings.getInstance()
        return "http://localhost:${settings.proxyPort}"
    }

    fun setPreviewScriptOverride(relativePath: String, content: String) {
        PreviewContentService.getInstance(project).setScriptOverride(relativePath, content)
    }

    fun getPreviewScriptOverride(relativePath: String): String? {
        return PreviewContentService.getInstance(project).getScriptOverride(relativePath)
    }

    fun removePreviewScriptOverride(relativePath: String) {
        PreviewContentService.getInstance(project).removeScriptOverride(relativePath)
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): ProxyServerService =
            project.getService(ProxyServerService::class.java)
    }
}
