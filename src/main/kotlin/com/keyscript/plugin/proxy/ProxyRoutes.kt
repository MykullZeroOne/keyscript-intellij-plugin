package com.keyscript.plugin.proxy

import com.keyscript.plugin.services.NetworkMonitorService
import com.keyscript.plugin.services.ProxyServerService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.InputStream
import javax.net.ssl.X509TrustManager
import java.net.NetworkInterface
import java.net.URL
import java.net.URLDecoder
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * All proxy route definitions — ported from src/main/proxy.ts (473 lines).
 */
class ProxyRoutes(
    private val proxyEndpoint: String,
    private val supportedInstances: List<String>,
    private val servicePort: Int,
    private val proxyService: ProxyServerService,
    private val networkMonitor: NetworkMonitorService
) {
    private val log = Logger.getInstance(ProxyRoutes::class.java)
    private val ideParamsData = ConcurrentHashMap<String, String>()
    private val ideParamsSeq = AtomicInteger(0)
    private var currentInstance = supportedInstances.firstOrNull() ?: "Test"
    private var deviceIdentifier = ""

    private val useHttps = proxyEndpoint.startsWith("https") ||
            proxyEndpoint.endsWith(":8443") || proxyEndpoint.endsWith(":443")
    private val proxyUrl = if (useHttps && !proxyEndpoint.startsWith("https"))
        "https://$proxyEndpoint" else proxyEndpoint

    private val httpClient = HttpClient(CIO) {
        engine {
            https {
                trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            }
        }
        expectSuccess = false
    }

    private val keybridgeEndpoints = setOf(
        "/DirectXMLPostJSON", "/UserLogin", "/LoginUserInterface",
        "/TableListJSON", "/TableBrowser", "/SearchJSON", "/SessionStore"
    )

    fun configure(app: Application) {
        app.routing {
            // ─── API endpoints ─────────────────────
            postSsoSession()
            postDeviceId()
            postSetProject()
            getGetProject()
            getProjectScripts()
            getDeviceInformation()

            // ─── Dedicated POST handlers ───────────
            postDirectXmlPostJson()
            postSearchJson()

            // ─── GET UserLogin (SSO / Kerberos) ────
            getUserLogin()

            // ─── RunScript handler ─────────────────
            for (inst in supportedInstances) {
                getRunScript(inst)
            }

            // ─── Instance routes ───────────────────
            for (inst in supportedInstances) {
                get("/$inst") {
                    currentInstance = inst
                    call.respondText("""{"instance":"$currentInstance"}""", ContentType.Application.Json)
                }
            }

            // ─── Catch-all POST proxy ──────────────
            post("{path...}") { catchAllPost(call) }

            // ─── Catch-all GET proxy ───────────────
            get("{path...}") { catchAllGet(call) }
        }
    }

    // ─── /api/sso-session ──────────────────────────

    private fun Route.postSsoSession() {
        post("/api/sso-session") {
            val body = call.receiveText()
            val jsessionId = extractJsonField(body, "jsessionId")
            if (jsessionId != null) {
                proxyService.setSsoSession(jsessionId)
                call.respondText("""{"success":true}""", ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.BadRequest, """{"error":"Missing jsessionId"}""")
            }
        }
    }

    // ─── /api/device-id ─────────────────────────────

    private fun Route.postDeviceId() {
        post("/api/device-id") {
            val body = call.receiveText()
            val id = extractJsonField(body, "deviceId")
            if (id != null) {
                deviceIdentifier = id
                call.respondText("""{"success":true}""", ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.BadRequest, """{"error":"Missing deviceId"}""")
            }
        }
    }

    // ─── /api/set-project ──────────────────────────

    private fun Route.postSetProject() {
        post("/api/set-project") {
            val body = call.receiveText()
            proxyService.activeProjectPath = extractJsonField(body, "path") ?: ""
            call.respondText("""{"success":true}""", ContentType.Application.Json)
        }
    }

    private fun Route.getGetProject() {
        get("/api/get-project") {
            call.respondText(
                """{"path":"${proxyService.activeProjectPath}"}""",
                ContentType.Application.Json
            )
        }
    }

    // ─── /project-scripts/* ────────────────────────

    private fun Route.getProjectScripts() {
        get("/project-scripts/{path...}") {
            val path = proxyService.activeProjectPath
            if (path.isEmpty()) {
                call.respond(HttpStatusCode.NotFound, "No project loaded")
                return@get
            }
            val relative = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val overrideContent = proxyService.getPreviewScriptOverride(relative)
            if (overrideContent != null) {
                call.respondText(overrideContent, ContentType.Application.JavaScript)
                return@get
            }
            val file = java.io.File(path, relative)
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound, "Script not found: ${file.absolutePath}")
            }
        }
    }

    // ─── /GetDeviceInformation ─────────────────────

    private fun Route.getDeviceInformation() {
        get("/GetDeviceInformation") {
            val macs = NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.hardwareAddress?.joinToString("-") { b -> "%02X".format(b) }?.let { listOf(it) } ?: emptyList() }
                .filter { it != "00-00-00-00-00-00" }
                .sorted()
                .joinToString(" ")

            val xml = """<?xml version="1.0"?>
<device type="c" xmlns="http://www.corelationinc.com/deviceLanguage/v1.0" version="2.0.0.0">
  <deviceInformation type="c">
  <identifier>MAC: $macs</identifier>
  <userServicePortNumber>$servicePort</userServicePortNumber>
  </deviceInformation>
</device>"""
            call.respondText(xml, ContentType.Text.Xml)
        }
    }

    // ─── /DirectXMLPostJSON ────────────────────────

    private fun Route.postDirectXmlPostJson() {
        post("/DirectXMLPostJSON") {
            val xmlBody = extractXmlBody(call.receiveText())
            val targetUrl = "$proxyUrl/$currentInstance/DirectXMLPostJSON"

            val response = httpClient.post(targetUrl) {
                contentType(ContentType.Text.Xml)
                if (proxyService.ssoSessionId.isNotEmpty()) {
                    header("Cookie", "JSESSIONID=${proxyService.ssoSessionId}")
                }
                setBody(xmlBody)
            }

            val responseBody = response.bodyAsText()
            networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
                id = "dxml-${System.currentTimeMillis()}",
                type = "response",
                url = targetUrl,
                status = response.status.value,
                body = responseBody
            ))

            call.respondText(
                responseBody,
                ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
                response.status
            )
        }
    }

    // ─── /SearchJSON ───────────────────────────────

    private fun Route.postSearchJson() {
        post("/SearchJSON") {
            val xmlBody = extractXmlBody(call.receiveText())
            val targetUrl = "$proxyUrl/$currentInstance/SearchJSON"

            val response = httpClient.post(targetUrl) {
                contentType(ContentType.Text.Xml)
                if (proxyService.ssoSessionId.isNotEmpty()) {
                    header("Cookie", "JSESSIONID=${proxyService.ssoSessionId}")
                }
                setBody(xmlBody)
            }

            val responseBody = response.bodyAsText()
            networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
                id = "search-${System.currentTimeMillis()}",
                type = "response",
                url = targetUrl,
                status = response.status.value,
                body = responseBody
            ))

            call.respondText(
                responseBody,
                ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
                response.status
            )
        }
    }

    // ─── GET /UserLogin ────────────────────────────

    private fun Route.getUserLogin() {
        get("/UserLogin") {
            val targetUrl = "$proxyUrl/$currentInstance/UserLogin"
            val response = httpClient.get(targetUrl)
            call.respondText(response.bodyAsText(), ContentType.Application.Json, response.status)
        }
    }

    // ─── /{instance}/Keyscript_IDE/RunScript ───────

    private fun Route.getRunScript(inst: String) {
        get("/$inst/Keyscript_IDE/RunScript") {
            val scriptPath = call.request.queryParameters["scriptPath"] ?: ""
            val js = scriptPath.removeSuffix(".js")
            val parametersId = call.request.queryParameters["scriptParametersId"] ?: "-"

            // Resolve script file
            val projectPath = proxyService.activeProjectPath
            val projectFile = if (projectPath.isNotEmpty()) java.io.File(projectPath, "$js.js") else null
            val useProject = projectFile?.exists() == true

            // Retrieve stored params
            val storedParams = ideParamsData[parametersId]
            log.info("RunScript: parametersId=$parametersId, found=${storedParams != null}, storedKeys=${ideParamsData.keys}")

            var params: String
            try {
                params = storedParams ?: """{"crlogin":{},"crscript":{}}"""
                // Inject session if available
                if (proxyService.ssoSessionId.isNotEmpty()) {
                    params = injectSessionIntoParams(params, proxyService.ssoSessionId, inst)
                }
            } catch (e: Exception) {
                params = """{"crlogin":{"instance":"$inst"},"crscript":{}}"""
            }

            val scriptSrc = if (useProject) "/project-scripts/$js.js" else "/scripts/$js.js"

            // Load templates from resources
            val headSection = loadTemplate("templates/head-section.html")
                .replace("{{ protocol }}", if (useHttps) "https" else "http")
                .replace("{{ hostPort }}", proxyService.getProxyBaseUrl().substringAfterLast(":"))
                .replace("{{ servicePort }}", servicePort.toString())
                .replace("{{ serviceBaseUrl }}", "http://localhost:$servicePort")
                .replace("{{ instance }}", inst)

            val html = loadTemplate("templates/iframe-target.html")
                .replace("{{ head-section }}", headSection)
                .replace("{{ scriptParameters }}", params)
                .replace("{{ script }}", js)
                .replace("{{ scriptSrc }}", scriptSrc)

            // Set JSESSIONID cookie on response
            if (proxyService.ssoSessionId.isNotEmpty()) {
                call.response.cookies.append("JSESSIONID", proxyService.ssoSessionId, path = "/")
            }

            call.respondText(html, ContentType.Text.Html)
        }
    }

    // ─── Catch-all POST proxy ──────────────────────

    private suspend fun catchAllPost(call: ApplicationCall) {
        val path = call.request.path()
        var body = call.receiveText()

        log.info("catchAllPost: path=$path, bodyLength=${body.length}, contentType=${call.request.contentType()}")

        // Replace JSESSIONID in body
        body = CookieInjector.replaceInBody(body, proxyService.ssoSessionId)

        // SessionStore: intercept params
        var seq: String? = null
        if (path.endsWith("/SessionStore")) {
            seq = "//${ideParamsSeq.incrementAndGet()}//"
            val decoded = java.net.URLDecoder.decode(body, "UTF-8")
            val params = decoded.substringAfter("value=", "").substringBefore("&")
            ideParamsData[seq] = params
            log.info("SessionStore: stored params under seq=$seq, length=${params.length}, bodyPreview=${body.take(200)}")
        }

        // Resolve target URL
        val targetPath = resolvePostPath(path)
        val targetUrl = "$proxyUrl$targetPath"

        // Log request
        val requestId = System.nanoTime().toString(36)
        networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
            id = requestId, type = "request", method = "POST", url = path, body = body.take(500)
        ))

        val response = httpClient.post(targetUrl) {
            contentType(ContentType.parse(call.request.contentType().toString()))
            header("Cookie", CookieInjector.injectCookie(
                call.request.headers["Cookie"], proxyService.ssoSessionId
            ))
            if (deviceIdentifier.isNotEmpty()) {
                header("X-Device-Identifier", deviceIdentifier)
            }
            setBody(body)
        }

        val responseBody = response.bodyAsText()

        // SessionStore response: map seq to real ID
        if (path.endsWith("/SessionStore") && seq != null) {
            try {
                val storeId = extractJsonField(responseBody, "id")
                val success = responseBody.contains(""""success":true""") ||
                        responseBody.contains(""""success": true""")
                if (success && storeId != null) {
                    val params = ideParamsData.remove(seq) ?: ""
                    ideParamsData[storeId] = params
                    log.info("SessionStore: mapped seq=$seq -> storeId=$storeId")
                } else {
                    log.warn("SessionStore: failed to map params. success=$success, storeId=$storeId, response=${responseBody.take(200)}")
                }
            } catch (_: Exception) {}
        }

        // UserLogin response: capture JSESSIONID
        if (path.endsWith("/UserLogin")) {
            val jsessionId = extractJsonField(responseBody, "JSESSIONID")
            if (jsessionId != null) {
                proxyService.setSsoSession(jsessionId)
            }
        }

        networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
            id = requestId, type = "response", status = response.status.value, body = responseBody.take(500)
        ))

        call.respondText(
            responseBody,
            ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
            response.status
        )
    }

    // ─── Catch-all GET proxy ───────────────────────

    private suspend fun catchAllGet(call: ApplicationCall) {
        if (serveBundledStaticIfPresent(call)) {
            return
        }

        val rawPath = call.request.uri  // includes query string
        // Strip /Keyscript_IDE/ from path (same as POST — browser resolves relative URLs
        // against the RunScript page, e.g. /Development/Keyscript_IDE/ext-3.2.2/foo.js)
        val path = if (rawPath.contains("/Keyscript_IDE/")) {
            rawPath.replace("/Keyscript_IDE/", "/")
        } else {
            rawPath
        }
        val targetUrl = "$proxyUrl$path"

        val response = httpClient.get(targetUrl) {
            header("Cookie", CookieInjector.injectCookie(
                call.request.headers["Cookie"], proxyService.ssoSessionId
            ))
        }

        if (response.status.value >= 400) {
            log.warn("GET proxy ${response.status.value}: $rawPath -> $targetUrl")
        }

        val responseBytes = response.readBytes()
        val contentType = response.headers[HttpHeaders.ContentType]

        call.respondBytes(
            responseBytes,
            ContentType.parse(contentType ?: "application/octet-stream"),
            response.status
        )
    }

    // ─── Helpers ───────────────────────────────────

    private fun resolvePostPath(path: String): String {
        // Strip /Keyscript_IDE/ from anywhere in the path (browser resolves relative URLs
        // against the RunScript page URL, producing paths like /Development/Keyscript_IDE/DirectXMLPostJSON)
        var resolved = if (path.contains("/Keyscript_IDE/")) {
            path.replace("/Keyscript_IDE/", "/")
        } else {
            path
        }

        // Known Keybridge endpoints: prepend instance
        if (keybridgeEndpoints.any { resolved == it }) {
            return "/$currentInstance$resolved"
        }
        // Prepend instance if not already present
        if (!resolved.startsWith("/$currentInstance")) {
            return "/$currentInstance$resolved"
        }
        return resolved
    }

    private suspend fun serveBundledStaticIfPresent(call: ApplicationCall): Boolean {
        val requestPath = call.request.path()
        val resourcePath = toBundledResourcePath(requestPath) ?: return false

        val resource = loadResource(resourcePath) ?: return false
        val bytes = resource.use(InputStream::readBytes)
        call.respondBytes(
            bytes = bytes,
            contentType = contentTypeFor(resourcePath),
            status = HttpStatusCode.OK
        )
        return true
    }

    /**
     * Extracts the bundled resource path from the request path.
     * Handles both direct paths (/KeyScript/foo.js) and instance-prefixed paths
     * (/Development/Keyscript_IDE/KeyScript/foo.js) that come from relative URLs in the iframe.
     */
    private fun toBundledResourcePath(path: String): String? {
        val bundledPrefixes = listOf("/KeyScript/", "/Keyscript_IDE/", "/scripts/")

        // Direct bundled path: /KeyScript/foo.js
        for (prefix in bundledPrefixes) {
            if (path.startsWith(prefix)) {
                return path.removePrefix("/")
            }
        }

        // Instance-prefixed path: /Development/Keyscript_IDE/KeyScript/foo.js
        // Strip the instance + /Keyscript_IDE/ prefix and check again
        val stripped = path.replace(Regex("^/[^/]+/Keyscript_IDE/"), "/")
        if (stripped != path) {
            for (prefix in bundledPrefixes) {
                if (stripped.startsWith(prefix)) {
                    return stripped.removePrefix("/")
                }
            }
        }

        return null
    }

    private fun contentTypeFor(resourcePath: String): ContentType {
        return when (resourcePath.substringAfterLast('.', "").lowercase()) {
            "js" -> ContentType.Application.JavaScript
            "css" -> ContentType.Text.CSS
            "html" -> ContentType.Text.Html
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "map" -> ContentType.Application.Json
            else -> ContentType.Application.OctetStream
        }
    }

    private fun extractXmlBody(raw: String): String {
        return if (raw.startsWith("crXMLData=")) {
            URLDecoder.decode(raw.substringAfter("crXMLData="), "UTF-8").replace("+", " ")
        } else {
            raw
        }
    }

    private fun injectSessionIntoParams(params: String, jsessionId: String, instance: String): String {
        // Replace existing JSESSIONID/instance or inject if missing
        var result = params
        // Update or inject JSESSIONID
        if (result.contains(""""JSESSIONID":""")) {
            result = result.replace(Regex(""""JSESSIONID":"[^"]*""""), """"JSESSIONID":"$jsessionId"""")
        } else {
            result = result.replace(""""crlogin":{""", """"crlogin":{"JSESSIONID":"$jsessionId",""")
        }
        // Update or inject instance
        if (result.contains(""""instance":""")) {
            result = result.replace(Regex(""""instance":"[^"]*""""), """"instance":"$instance"""")
        } else {
            result = result.replace(""""crlogin":{""", """"crlogin":{"instance":"$instance",""")
        }
        return result.replace(",}", "}")
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun loadTemplate(resourcePath: String): String {
        return this::class.java.classLoader
            .getResourceAsStream(resourcePath)
            ?.bufferedReader()?.readText()
            ?: "<!-- Template not found: $resourcePath -->"
    }

    private fun loadResource(resourcePath: String): InputStream? {
        return this::class.java.classLoader.getResourceAsStream(resourcePath)
    }
}
