package com.keyscript.plugin.proxy

import com.keyscript.plugin.services.NetworkMonitorService
import com.keyscript.plugin.services.ProxyServerService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*

/**
 * Embedded Ktor HTTP server that replicates the Express proxy from the Electron IDE.
 * Uses CIO engine (pure-Kotlin, lightweight) instead of Netty for fast startup.
 */
class KtorProxyServer(
    private val proxyPort: Int,
    private val proxyEndpoint: String,
    private val supportedInstances: List<String>,
    private val servicePort: Int,
    private val proxyService: ProxyServerService,
    private val networkMonitor: NetworkMonitorService
) {
    private var server: ApplicationEngine? = null

    fun start() {
        server = embeddedServer(CIO, port = proxyPort, host = "0.0.0.0") {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }

            val routes = ProxyRoutes(
                proxyEndpoint = proxyEndpoint,
                supportedInstances = supportedInstances,
                servicePort = servicePort,
                proxyService = proxyService,
                networkMonitor = networkMonitor
            )
            routes.configure(this)
        }
        server!!.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
