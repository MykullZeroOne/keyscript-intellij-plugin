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

    fun setSsoSession(jsessionId: String) {
        ssoSessionId = jsessionId
        log.info("SSO session set: ${jsessionId.take(8)}...")
    }

    /** Ensure the proxy is running. Safe to call multiple times. */
    fun ensureStarted() {
        if (server != null) return
        synchronized(lock) {
            if (server != null) return
            val settings = KeyscriptSettings.getInstance()
            if (activeProjectPath.isEmpty()) {
                activeProjectPath = project.basePath ?: ""
            }
            try {
                log.info("Starting proxy: port=${settings.proxyPort}, endpoint=${settings.getProxyUrl()}, instances=${settings.supportedInstances}")
                val proxyServer = KtorProxyServer(
                    proxyPort = settings.proxyPort,
                    proxyEndpoint = settings.getProxyUrl(),
                    supportedInstances = settings.supportedInstances,
                    servicePort = settings.servicePort,
                    proxyService = this,
                    networkMonitor = NetworkMonitorService.getInstance(project),
                    session = SessionService.getInstance(project)
                )
                proxyServer.start()
                // Give CIO engine time to bind the port
                Thread.sleep(500)
                server = proxyServer
                log.info("Proxy server started on port ${settings.proxyPort}")
            } catch (e: Exception) {
                log.error("Failed to start proxy server on port ${settings.proxyPort}", e)
            }
        }
    }

    @Deprecated("Use ensureStarted()", replaceWith = ReplaceWith("ensureStarted()"))
    fun start() = ensureStarted()

    fun stop() {
        synchronized(lock) {
            server?.stop()
            server = null
            log.info("Proxy server stopped")
        }
    }

    fun restart() {
        stop()
        ensureStarted()
    }

    fun getProxyBaseUrl(): String {
        ensureStarted()
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
