package com.keyscript.plugin.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI

/**
 * Provides CRUD operations on the Keystone SCRIPT table for browsing
 * and managing server-side installed scripts.
 *
 * All calls use XML format through the local proxy's /SearchJSON and
 * /DirectXMLPostJSON endpoints — consistent with how the original IDE
 * and all other data tools operate.
 */
@Service(Service.Level.PROJECT)
class InstalledScriptsService(private val project: Project) {
    private val log = Logger.getInstance(InstalledScriptsService::class.java)
    private val mapper = jacksonObjectMapper()

    private val ns = "http://www.corelationinc.com/queryLanguage/v1.0"

    data class ScriptRecord(
        val serial: String,
        val description: String,
        val language: String = "",
        val category: String = "",
        val workAreaOption: String = ""
    )

    data class ScriptDetail(
        val serial: String,
        val description: String,
        val language: String = "",
        val category: String = "",
        val workAreaOption: String = "",
        val sourceCode: String = "",
        val fields: Map<String, String> = emptyMap()
    )

    data class ServiceResult<T>(
        val success: Boolean,
        val data: T? = null,
        val error: String? = null,
        val sessionExpired: Boolean = false
    )

    /**
     * Search the SCRIPT table by description via /SearchJSON.
     */
    fun listScripts(searchTerm: String = "", returnLimit: Int = 50): ServiceResult<List<ScriptRecord>> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val xml = buildSearchXml(searchTerm, returnLimit)
        val (responseBody, error) = postToSearchJson(xml)
        if (error != null) {
            return ServiceResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            log.info("InstalledScripts search response (2000 chars): ${responseBody?.take(2000)}")
            val json = mapper.readTree(responseBody)
            val results = extractSearchResults(json)
            log.info("Parsed ${results.size} results")
            ServiceResult(success = true, data = results)
        } catch (e: Exception) {
            log.warn("Failed to parse script search response", e)
            ServiceResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

    /**
     * View a SCRIPT record by serial via /DirectXMLPostJSON.
     */
    fun viewScript(serial: String): ServiceResult<ScriptDetail> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val xml = buildViewXml(serial)
        val (responseBody, error) = postToDirectXml(xml)
        if (error != null) {
            return ServiceResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            log.info("View script #$serial response: ${responseBody?.take(800)}")
            val json = mapper.readTree(responseBody)
            val detail = extractScriptDetail(json, serial)
            if (detail != null) {
                ServiceResult(success = true, data = detail)
            } else {
                ServiceResult(success = false, error = "Could not parse script record")
            }
        } catch (e: Exception) {
            log.warn("Failed to parse view response for serial=$serial", e)
            ServiceResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

    /**
     * Delete a SCRIPT record by serial via /DirectXMLPostJSON.
     */
    fun deleteScript(serial: String): ServiceResult<Unit> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val xml = buildDeleteXml(serial)
        val (responseBody, error) = postToDirectXml(xml)
        if (error != null) {
            return ServiceResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            val json = mapper.readTree(responseBody)
            val txnResult = extractTransactionResult(json)
            if (txnResult == "posted") {
                ServiceResult(success = true, data = Unit)
            } else {
                val errorMsg = extractExceptionMessages(json)
                ServiceResult(success = false, error = errorMsg.ifEmpty { "Transaction result: $txnResult" })
            }
        } catch (e: Exception) {
            log.warn("Failed to parse delete response for serial=$serial", e)
            ServiceResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

    // ─── XML builders (Corelation namespace) ────────

    private fun buildSearchXml(description: String, returnLimit: Int): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:search>
          <v1:tableName>SCRIPT</v1:tableName>
          <v1:filterName>BY_DESCRIPTION</v1:filterName>
          <v1:includeSelectColumns option="Y"/>
          <v1:includeRowDescriptions option="Y"/>
          <v1:includeTotalHitCount option="Y"/>
          <v1:returnLimit>$returnLimit</v1:returnLimit>
          <v1:parameter>
            <v1:columnName>DESCRIPTION</v1:columnName>
            <v1:contents>${escapeXml(description)}</v1:contents>
          </v1:parameter>
        </v1:search>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""
    }

    private fun buildViewXml(serial: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:record label="Main">
          <v1:operation option="V"/>
          <v1:includeAllColumns option="Y"/>
          <v1:includeRowDescriptions option="Y"/>
          <v1:tableName>SCRIPT</v1:tableName>
          <v1:targetSerial>$serial</v1:targetSerial>
        </v1:record>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""
    }

    private fun buildDeleteXml(serial: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<v1:query xmlns:v1="$ns">
  <v1:sequence>
    <v1:transaction>
      <v1:step>
        <v1:record label="Main">
          <v1:operation option="D"/>
          <v1:tableName>SCRIPT</v1:tableName>
          <v1:targetSerial>$serial</v1:targetSerial>
        </v1:record>
      </v1:step>
    </v1:transaction>
  </v1:sequence>
</v1:query>"""
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")

    // ─── HTTP (through proxy) ───────────────────────

    private fun postToSearchJson(xml: String): Pair<String?, String?> {
        val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
        val url = "$proxyBase/SearchJSON"
        return postXml(url, xml)
    }

    private fun postToDirectXml(xml: String): Pair<String?, String?> {
        val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
        val url = "$proxyBase/DirectXMLPostJSON"
        return postXml(url, xml)
    }

    private fun postXml(url: String, xml: String): Pair<String?, String?> {
        log.info("InstalledScriptsService POST $url, xml size=${xml.length}")

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/xml")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { it.write(xml.toByteArray()) }

            val status = conn.responseCode
            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            log.info("Proxy response: status=$status, body=${responseBody.take(500)}")

            if (isSessionExpired(status, responseBody)) {
                SessionService.getInstance(project).handleSessionExpired()
                return null to "Session expired"
            }

            if (status !in 200..299) {
                return null to "HTTP $status: ${responseBody.take(300)}"
            }

            SessionService.getInstance(project).recordSuccessfulActivity()
            responseBody to null
        } catch (e: Exception) {
            log.error("InstalledScriptsService API call failed", e)
            null to "Connection failed: ${e.message}"
        }
    }

    // ─── Response parsing ───────────────────────────

    private fun extractSearchResults(json: JsonNode): List<ScriptRecord> {
        val results = mutableListOf<ScriptRecord>()

        // SearchJSON returns: {"resultRows":[{"serial":"123","ROW_DESCRIPTION":"..."},...]}
        val rows = json.get("resultRows")
            ?: findDeep(json, "resultRows")
            ?: findDeep(json, "resultRow")
            ?: findDeep(json, "search")?.get("resultRow")

        if (rows == null) {
            log.warn("extractSearchResults: no resultRows/resultRow found. Keys: ${json.fieldNames().asSequence().toList()}")
            return results
        }

        val rowList = if (rows.isArray) rows.toList() else listOf(rows)

        for (row in rowList) {
            val serial = row.path("serial").asText("")
            if (serial.isEmpty()) continue

            // Try multiple field names for description
            var desc = row.path("ROW_DESCRIPTION").asText("")
            if (desc.isEmpty()) desc = row.path("rowDescription").asText("")
            if (desc.isEmpty()) desc = row.path("DESCRIPTION").asText("")

            results.add(ScriptRecord(
                serial = serial,
                description = desc,
                language = row.path("LANGUAGE").asText(""),
                category = row.path("CATEGORY").asText(""),
                workAreaOption = row.path("CLIENT_TRAN_WORK_AREA_OPTION").asText("")
            ))
        }
        return results
    }

    private fun extractScriptDetail(json: JsonNode, serial: String): ScriptDetail? {
        val record = findDeep(json, "record") ?: return null

        val fields = mutableMapOf<String, String>()

        val fieldNode = record.get("field")
        if (fieldNode != null) {
            val fieldList = if (fieldNode.isArray) fieldNode.toList() else listOf(fieldNode)
            for (f in fieldList) {
                val colName = f.path("columnName").asText("")
                val contents = f.path("contents").asText("")
                val newContents = f.path("newContents").asText("")
                if (colName.isNotEmpty()) {
                    fields[colName] = contents.ifEmpty { newContents }
                }
            }
        }

        if (fields.isEmpty()) {
            val skip = setOf("\$attr", "operation", "tableName", "targetSerial",
                "includeAllColumns", "includeRowDescriptions", "serial", "rowDescription")
            val iter = record.fields()
            while (iter.hasNext()) {
                val (key, value) = iter.next()
                if (key in skip) continue
                when {
                    value.isTextual -> fields[key] = value.asText()
                    value.isObject && value.has("contents") -> fields[key] = value.get("contents").asText("")
                    value.isObject && value.has("option") -> fields[key] = value.get("option").asText("")
                    value.isNumber -> fields[key] = value.asText()
                }
            }
        }

        var recordSerial = record.path("serial").asText("")
        if (recordSerial.isEmpty()) {
            val attr = record.get("\$attr")
            if (attr != null) recordSerial = attr.path("serial").asText("")
        }

        return ScriptDetail(
            serial = recordSerial.ifEmpty { serial },
            description = fields["DESCRIPTION"] ?: record.path("rowDescription").asText(""),
            language = fields["LANGUAGE"] ?: "",
            category = fields["CATEGORY"] ?: "",
            workAreaOption = fields["CLIENT_TRAN_WORK_AREA_OPTION"] ?: "",
            sourceCode = fields["SOURCE_CODE"] ?: "",
            fields = fields
        )
    }

    private fun extractTransactionResult(json: JsonNode): String {
        val txn = findDeep(json, "transaction") ?: return ""
        val attr = txn.get("\$attr") ?: return ""
        return attr.path("result").asText("")
    }

    private fun extractExceptionMessages(json: JsonNode): String {
        val messages = mutableListOf<String>()
        findAllDeep(json, "exception") { node ->
            val msg = node.path("message").asText("")
            if (msg.isNotEmpty()) messages.add(msg)
        }
        return messages.joinToString("\n")
    }

    // ─── Helpers ────────────────────────────────────

    private fun isSessionExpired(status: Int, responseBody: String): Boolean {
        if (status == 401 || status == 403) return true
        val lower = responseBody.lowercase()
        return lower.contains("session") && (lower.contains("expired") || lower.contains("invalid"))
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
        fun getInstance(project: Project): InstalledScriptsService =
            project.getService(InstalledScriptsService::class.java)
    }
}
