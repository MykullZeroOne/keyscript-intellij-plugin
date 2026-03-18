package com.keyscript.plugin.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI

/**
 * Deploys Keyscript files to the Keystone SCRIPT table via the proxy.
 * Uses XML format through /DirectXMLPostJSON — consistent with how the
 * original IDE and all other data tools operate.
 */
@Service(Service.Level.PROJECT)
class DeploymentService(private val project: Project) {
    private val log = Logger.getInstance(DeploymentService::class.java)
    private val mapper = jacksonObjectMapper()
    private val ns = "http://www.corelationinc.com/queryLanguage/v1.0"

    data class DeployResult(
        val success: Boolean,
        val serial: String? = null,
        val description: String? = null,
        val error: String? = null,
        val sessionExpired: Boolean = false
    )

    data class ScriptSearchResult(
        val serial: String,
        val description: String
    )

    /**
     * Search for scripts by description via /SearchJSON.
     */
    fun searchByDescription(description: String): Pair<List<ScriptSearchResult>, String?> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) return emptyList<ScriptSearchResult>() to "Not logged in"

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence><v1:transaction><v1:step><v1:search>
    <v1:tableName>SCRIPT</v1:tableName>
    <v1:filterName>BY_DESCRIPTION</v1:filterName>
    <v1:includeSelectColumns option="Y"/>
    <v1:includeTotalHitCount option="Y"/>
    <v1:returnLimit>10</v1:returnLimit>
    <v1:parameter>
      <v1:columnName>DESCRIPTION</v1:columnName>
      <v1:contents>${escapeXml(description)}</v1:contents>
    </v1:parameter>
  </v1:search></v1:step></v1:transaction></v1:sequence>
</v1:query>"""

        val (responseBody, error) = postToProxy("/SearchJSON", xml)
        if (error != null) return emptyList<ScriptSearchResult>() to error

        return try {
            val json = mapper.readTree(responseBody)
            val rows = json.get("resultRows") ?: findDeep(json, "resultRow")
            val results = mutableListOf<ScriptSearchResult>()
            if (rows != null) {
                val rowList = if (rows.isArray) rows.toList() else listOf(rows)
                for (row in rowList) {
                    val serial = row.path("serial").asText("")
                    val desc = row.path("ROW_DESCRIPTION").asText(
                        row.path("rowDescription").asText("")
                    )
                    if (serial.isNotEmpty()) results.add(ScriptSearchResult(serial, desc))
                }
            }
            results to null
        } catch (e: Exception) {
            log.warn("Failed to parse search response", e)
            emptyList<ScriptSearchResult>() to "Parse error: ${e.message}"
        }
    }

    /**
     * Deploy (insert) a new script to the SCRIPT table via /DirectXMLPostJSON.
     */
    fun deployNew(
        sourceCode: String,
        description: String,
        workAreaOption: String = "D",
        workAreaTabOption: String = "Y"
    ): DeployResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) return DeployResult(success = false, error = "Not logged in")

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence><v1:transaction><v1:step>
    <v1:record label="Main">
      <v1:operation option="I"/>
      <v1:tableName>SCRIPT</v1:tableName>
      <v1:field>
        <v1:columnName>DESCRIPTION</v1:columnName>
        <v1:newContents>${escapeXml(description)}</v1:newContents>
      </v1:field>
      <v1:field>
        <v1:columnName>LANGUAGE</v1:columnName>
        <v1:newContents>JS</v1:newContents>
      </v1:field>
      <v1:field>
        <v1:columnName>CATEGORY</v1:columnName>
        <v1:newContents>C</v1:newContents>
      </v1:field>
      <v1:field>
        <v1:columnName>SOURCE_CODE</v1:columnName>
        <v1:newContents>${escapeSourceCode(sourceCode)}</v1:newContents>
      </v1:field>
      <v1:field>
        <v1:columnName>CLIENT_TRAN_WORK_AREA_OPTION</v1:columnName>
        <v1:newContents>$workAreaOption</v1:newContents>
      </v1:field>
      <v1:field>
        <v1:columnName>CLIENT_TRAN_W_A_TAB_OPTION</v1:columnName>
        <v1:newContents>$workAreaTabOption</v1:newContents>
      </v1:field>
    </v1:record>
  </v1:step></v1:transaction></v1:sequence>
</v1:query>"""

        return executeDeploy(xml)
    }

    /**
     * Update an existing script by serial via /DirectXMLPostJSON.
     */
    fun deployUpdate(
        targetSerial: String,
        sourceCode: String,
        description: String? = null,
        workAreaOption: String? = null
    ): DeployResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) return DeployResult(success = false, error = "Not logged in")

        val fieldXml = buildString {
            append("""<v1:field><v1:columnName>SOURCE_CODE</v1:columnName><v1:newContents>${escapeSourceCode(sourceCode)}</v1:newContents><v1:operation option="S"/></v1:field>""")
            if (!description.isNullOrBlank()) {
                append("""<v1:field><v1:columnName>DESCRIPTION</v1:columnName><v1:newContents>${escapeXml(description)}</v1:newContents><v1:operation option="S"/></v1:field>""")
            }
            if (!workAreaOption.isNullOrBlank()) {
                append("""<v1:field><v1:columnName>CLIENT_TRAN_WORK_AREA_OPTION</v1:columnName><v1:newContents>$workAreaOption</v1:newContents><v1:operation option="S"/></v1:field>""")
            }
        }

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence><v1:transaction><v1:step>
    <v1:record label="Main">
      <v1:operation option="U"/>
      <v1:tableName>SCRIPT</v1:tableName>
      <v1:targetSerial>$targetSerial</v1:targetSerial>
      $fieldXml
    </v1:record>
  </v1:step></v1:transaction></v1:sequence>
</v1:query>"""

        return executeDeploy(xml)
    }

    private fun executeDeploy(xml: String): DeployResult {
        val (responseBody, error) = postToProxy("/DirectXMLPostJSON", xml)
        if (error != null) {
            return DeployResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            val json = mapper.readTree(responseBody)
            val txn = findDeep(json, "transaction")
            val txnAttr = txn?.get("\$attr")
            val txnResult = txnAttr?.path("result")?.asText("")

            // Check for exceptions
            val exceptions = mutableListOf<String>()
            findAllDeep(json, "exception") { node ->
                val msg = node.path("message").asText("")
                if (msg.isNotEmpty()) exceptions.add(msg)
            }
            if (exceptions.isNotEmpty()) {
                return DeployResult(success = false, error = exceptions.joinToString("\n"))
            }

            if (txnResult != "posted") {
                return DeployResult(success = false, error = "Transaction result: $txnResult")
            }

            val record = findDeep(json, "record")
            val serial = record?.path("serial")?.asText("")
            val rowDesc = record?.path("rowDescription")?.asText("")

            val result = DeployResult(success = true, serial = serial, description = rowDesc)
            com.keyscript.plugin.onboarding.OnboardingStateService.getInstance(project).completedFirstDeploy = true
            result
        } catch (e: Exception) {
            log.warn("Failed to parse deploy response", e)
            DeployResult(success = false, error = "Parse error: ${e.message}")
        }
    }

    // ─── HTTP ───────────────────────────────────────

    private fun postToProxy(endpoint: String, xml: String): Pair<String?, String?> {
        val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
        val url = "$proxyBase$endpoint"
        log.info("DeploymentService POST $url, xml size=${xml.length}")

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/xml")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.outputStream.use { it.write(xml.toByteArray()) }

            val status = conn.responseCode
            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            log.warn("DEPLOY response: status=$status, bodyLen=${responseBody.length}")
            log.warn("DEPLOY response HEAD: ${responseBody.take(1000)}")
            log.warn("DEPLOY response TAIL: ${responseBody.takeLast(2000)}")

            if (status == 401 || status == 403) {
                SessionService.getInstance(project).handleSessionExpired()
                return null to "Session expired"
            }

            if (status !in 200..299) return null to "HTTP $status: ${responseBody.take(300)}"

            SessionService.getInstance(project).recordSuccessfulActivity()
            responseBody to null
        } catch (e: Exception) {
            log.error("DeploymentService API call failed", e)
            null to "Connection failed: ${e.message}"
        }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    /**
     * Escape source code for XML embedding. Handles all characters that
     * are invalid in XML 1.0, including control characters that Keystone
     * rejects with "cannot contain an invalid character".
     */
    private fun escapeSourceCode(s: String): String {
        val sb = StringBuilder(s.length + s.length / 10)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(ch) // valid XML whitespace
                ch.code < 0x20 -> {} // strip invalid XML control characters
                ch.code in 0xD800..0xDFFF -> {} // strip surrogate pairs (invalid in XML)
                ch.code == 0xFFFE || ch.code == 0xFFFF -> {} // strip BOM/nonchars
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun findDeep(node: JsonNode, key: String): JsonNode? {
        if (node.has(key)) return node.get(key)
        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findDeep(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAllDeep(node: JsonNode, key: String, action: (JsonNode) -> Unit) {
        if (node.has(key)) {
            val target = node.get(key)
            if (target.isArray) target.forEach(action) else action(target)
        }
        for (child in node) {
            if (child.isObject || child.isArray) findAllDeep(child, key, action)
        }
    }

    companion object {
        fun getInstance(project: Project): DeploymentService =
            project.getService(DeploymentService::class.java)
    }
}
