package com.keyscript.plugin.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.keyscript.plugin.settings.KeyscriptSettings
import java.net.HttpURLConnection
import java.net.URI

/**
 * Direct JSON API client for Keystone — bypasses the local proxy and sends
 * Corelation query-language JSON with an embedded sessionId.
 */
@Service(Service.Level.PROJECT)
class KeystoneApiClient(private val project: Project) {
    private val log = Logger.getInstance(KeystoneApiClient::class.java)
    private val mapper = jacksonObjectMapper()

    data class ApiResult(
        val success: Boolean,
        val json: JsonNode? = null,
        val error: String? = null,
        val sessionExpired: Boolean = false
    )

    /**
     * POST a JSON query directly to Keystone at /{instance}.
     * The sessionId is embedded in query.$attr.sessionId.
     */
    fun post(query: ObjectNode): ApiResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ApiResult(success = false, error = "Not logged in")
        }

        val settings = KeyscriptSettings.getInstance()
        val instance = ScriptParameterService.getInstance(project).instance.ifEmpty {
            settings.getDefaultInstance()
        }
        val baseUrl = settings.getKeystoneApiBaseUrl()
        val url = "$baseUrl/$instance"

        // Inject sessionId into query.$attr
        val queryNode = query.get("query") as? ObjectNode ?: query
        val attrNode = queryNode.putObject("\$attr")
        attrNode.put("sessionId", session.apiSessionId)

        val body = mapper.writeValueAsString(query)

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Cookie", "JSESSIONID=${session.apiSessionId}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            val status = conn.responseCode
            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            log.info("Keystone API: POST $url -> $status (${responseBody.take(200)})")

            // Detect session expiry
            if (isSessionExpired(status, responseBody)) {
                handleSessionExpired()
                return ApiResult(success = false, error = "Session expired", sessionExpired = true)
            }

            if (status !in 200..299) {
                return ApiResult(success = false, error = "HTTP $status: ${responseBody.take(300)}")
            }

            // Record successful API activity to reset keepalive failure counter
            session.recordSuccessfulActivity()

            val json = mapper.readTree(responseBody)
            val errors = extractErrors(json)
            if (errors.isNotEmpty()) {
                ApiResult(success = false, json = json, error = errors.joinToString("\n"))
            } else {
                ApiResult(success = true, json = json)
            }
        } catch (e: Exception) {
            log.error("Keystone API call failed", e)
            ApiResult(success = false, error = "Connection failed: ${e.message}")
        }
    }

    // ─── Query builders ────────────────────────────────

    fun buildSearchQuery(tableName: String, filterName: String, paramColumn: String, paramValue: String): ObjectNode {
        val root = mapper.createObjectNode()
        val query = root.putObject("query")
        val seq = query.putObject("sequence")
        val txn = seq.putObject("transaction")
        val step = txn.putObject("step")
        val search = step.putObject("search")
        search.put("tableName", tableName)
        search.put("filterName", filterName)
        search.putObject("includeSelectColumns").put("option", "Y")
        search.putObject("includeTotalHitCount").put("option", "Y")
        search.put("returnLimit", 10)
        val param = search.putObject("parameter")
        param.put("columnName", paramColumn)
        param.put("contents", paramValue)
        return root
    }

    fun buildRecordUpdate(
        tableName: String,
        targetSerial: String,
        fields: List<Triple<String, String, String>>  // columnName, newContents, operation (S=set)
    ): ObjectNode {
        val root = mapper.createObjectNode()
        val query = root.putObject("query")
        val seq = query.putObject("sequence")
        val txn = seq.putObject("transaction")
        val step = txn.putObject("step")
        val record = step.putObject("record")
        record.putObject("\$attr").put("label", "Main")
        record.putObject("operation").put("option", "U")
        record.putObject("includeRowDescriptions").put("option", "Y")
        record.put("tableName", tableName)
        record.put("targetSerial", targetSerial)
        val fieldArray = record.putArray("field")
        for ((colName, newContents, op) in fields) {
            val field = fieldArray.addObject()
            field.put("columnName", colName)
            field.putObject("operation").put("option", op)
            field.put("newContents", newContents)
        }
        return root
    }

    fun buildRecordInsert(
        tableName: String,
        targetParentSerial: String,
        fields: List<Triple<String, String, String>>
    ): ObjectNode {
        val root = mapper.createObjectNode()
        val query = root.putObject("query")
        val seq = query.putObject("sequence")
        val txn = seq.putObject("transaction")
        val step = txn.putObject("step")
        val record = step.putObject("record")
        record.putObject("\$attr").put("label", "Main")
        record.putObject("operation").put("option", "I")
        record.putObject("includeRowDescriptions").put("option", "Y")
        record.put("tableName", tableName)
        record.put("targetParentSerial", targetParentSerial)
        val fieldArray = record.putArray("field")
        for ((colName, newContents, op) in fields) {
            val field = fieldArray.addObject()
            field.put("columnName", colName)
            field.putObject("operation").put("option", op)
            field.put("newContents", newContents)
        }
        return root
    }

    // ─── Response parsing ──────────────────────────────

    /**
     * Extract search result rows from a Keystone JSON response.
     * Returns list of (serial, description) pairs.
     */
    fun extractSearchResults(json: JsonNode): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        val search = findDeep(json, "search") ?: return results
        val rows = search.get("resultRow") ?: return results
        val rowList = if (rows.isArray) rows else listOf(rows)
        for (row in rowList) {
            val serial = row.path("serial").asText("")
            val desc = row.path("rowDescription").asText(
                row.path("selectColumn")?.firstOrNull()?.path("contents")?.asText("") ?: ""
            )
            if (serial.isNotEmpty()) {
                results.add(serial to desc)
            }
        }
        return results
    }

    private fun extractErrors(json: JsonNode): List<String> {
        val errors = mutableListOf<String>()
        findAllDeep(json, "exception") { node ->
            val msg = node.path("message").asText("")
            if (msg.isNotEmpty()) errors.add(msg)
        }
        findAllDeep(json, "tranResult") { node ->
            if (node.path("category")?.path("option")?.asText("") == "E") {
                val desc = node.path("description").asText("")
                if (desc.isNotEmpty()) errors.add(desc)
            }
        }
        return errors
    }

    private fun isSessionExpired(status: Int, responseBody: String): Boolean {
        if (status == 401 || status == 403) return true
        val lower = responseBody.lowercase()
        return lower.contains("session") && (lower.contains("expired") || lower.contains("invalid"))
    }

    private fun handleSessionExpired() {
        SessionService.getInstance(project).handleSessionExpired()
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
        fun getInstance(project: Project): KeystoneApiClient =
            project.getService(KeystoneApiClient::class.java)
    }
}
