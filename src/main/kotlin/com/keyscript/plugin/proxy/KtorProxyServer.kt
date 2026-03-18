package com.keyscript.plugin.proxy

import com.intellij.openapi.diagnostic.Logger
import com.keyscript.plugin.services.NetworkMonitorService
import com.keyscript.plugin.services.ProxyServerService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Embedded Ktor HTTP server that replicates the Express proxy from the Electron IDE.
 * Uses CIO engine (pure-Kotlin, lightweight) instead of Netty for fast startup.
 */
class KtorProxyServer(
    private val proxyPort: Int,
    private val proxyEndpoint: String,
    private val jsonApiUrl: String,
    private val supportedInstances: List<String>,
    private val servicePort: Int,
    private val proxyService: ProxyServerService,
    private val networkMonitor: NetworkMonitorService,
    private val session: com.keyscript.plugin.services.SessionService
) {
    private val log = Logger.getInstance(KtorProxyServer::class.java)
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        log.info("Starting Ktor proxy on port $proxyPort, endpoint=$proxyEndpoint, instances=$supportedInstances")

        server = embeddedServer(CIO, port = proxyPort, host = "0.0.0.0") {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }

            val routes = ProxyRoutes(
                proxyEndpoint = proxyEndpoint,
                jsonApiUrl = jsonApiUrl,
                supportedInstances = supportedInstances,
                servicePort = servicePort,
                proxyService = proxyService,
                networkMonitor = networkMonitor,
                session = session
            )
            routes.configure(this)
        }
        server!!.start(wait = false)

        // Wait for the port to actually be listening
        waitForPort(proxyPort, timeoutMs = 5000)
        log.info("Ktor proxy server verified listening on port $proxyPort")
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    private fun waitForPort(port: Int, timeoutMs: Long) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val conn = URI("http://localhost:$port/").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 200
                conn.readTimeout = 200
                conn.requestMethod = "GET"
                conn.responseCode // triggers connection
                conn.disconnect()
                return // port is listening
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        throw IllegalStateException("Proxy server failed to start on port $port within ${timeoutMs}ms")
    }
}
