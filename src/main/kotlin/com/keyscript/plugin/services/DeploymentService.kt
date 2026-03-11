package com.keyscript.plugin.services

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.keyscript.plugin.settings.KeyscriptSettings
import java.net.HttpURLConnection
import java.net.URI

/**
 * Deploys Keyscript files to the Keystone SCRIPT table via direct JSON API.
 * Supports insert (new deployment), update (existing serial), and search.
 */
@Service(Service.Level.PROJECT)
class DeploymentService(private val project: Project) {
    private val log = Logger.getInstance(DeploymentService::class.java)
    private val mapper = jacksonObjectMapper()

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
     * Search for scripts in the SCRIPT table by description.
     */
    fun searchByDescription(description: String): Pair<List<ScriptSearchResult>, String?> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return emptyList<ScriptSearchResult>() to "Not logged in"
        }

        val body = buildSearchJson(session.jsessionId, description)
        val (responseBody, error) = postToKeystone(body)
        if (error != null) return emptyList<ScriptSearchResult>() to error

        return try {
            val json = mapper.readTree(responseBody)
            val results = extractSearchResults(json)
            results to null
        } catch (e: Exception) {
            log.warn("Failed to parse search response", e)
            emptyList<ScriptSearchResult>() to "Failed to parse search response: ${e.message}"
        }
    }

    /**
     * Deploy (insert) a new script to the SCRIPT table.
     */
    fun deployNew(
        sourceCode: String,
        description: String,
        workAreaOption: String = "D",
        workAreaTabOption: String = "Y"
    ): DeployResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return DeployResult(success = false, error = "Not logged in. Please login first.")
        }

        val fields = mutableListOf(
            field("DESCRIPTION", description, "S"),
            field("SOURCE_CODE", sourceCode, "S"),
            field("CLIENT_TRAN_WORK_AREA_OPTION", workAreaOption, "S"),
            field("CLIENT_TRAN_W_A_TAB_OPTION", workAreaTabOption, "S")
        )

        val body = buildQueryJson(
            sessionId = session.jsessionId,
            operation = "I",
            targetSerial = null,
            fields = fields
        )

        return executeDeployment(body)
    }

    /**
     * Update an existing script in the SCRIPT table by serial.
     */
    fun deployUpdate(
        targetSerial: String,
        sourceCode: String,
        description: String? = null,
        workAreaOption: String? = null
    ): DeployResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return DeployResult(success = false, error = "Not logged in. Please login first.")
        }

        val fields = mutableListOf(
            field("SOURCE_CODE", sourceCode, "S")
        )
        if (!description.isNullOrBlank()) {
            fields.add(field("DESCRIPTION", description, "S"))
        }
        if (!workAreaOption.isNullOrBlank()) {
            fields.add(field("CLIENT_TRAN_WORK_AREA_OPTION", workAreaOption, "S"))
        }

        val body = buildQueryJson(
            sessionId = session.jsessionId,
            operation = "U",
            targetSerial = targetSerial,
            fields = fields
        )

        return executeDeployment(body)
    }

    private fun field(columnName: String, newContents: String, operation: String): Map<String, Any> {
        val f = linkedMapOf<String, Any>(
            "columnName" to columnName,
            "newContents" to newContents
        )
        if (operation.isNotEmpty()) {
            f["operation"] = mapOf("option" to operation)
        }
        return f
    }

    private fun buildSearchJson(sessionId: String, description: String): String {
        val search = linkedMapOf<String, Any>(
            "tableName" to "SCRIPT",
            "filterName" to "BY_DESCRIPTION",
            "includeSelectColumns" to mapOf("option" to "Y"),
            "includeTotalHitCount" to mapOf("option" to "Y"),
            "returnLimit" to 10,
            "parameter" to mapOf(
                "columnName" to "DESCRIPTION",
                "contents" to description
            )
        )

        val query = linkedMapOf<String, Any>(
            "\$attr" to mapOf("sessionId" to sessionId),
            "sequence" to mapOf(
                "transaction" to mapOf(
                    "step" to mapOf(
                        "search" to search
                    )
                )
            )
        )

        return mapper.writeValueAsString(mapOf("query" to query))
    }

    private fun buildQueryJson(
        sessionId: String,
        operation: String,
        targetSerial: String?,
        fields: List<Map<String, Any>>
    ): String {
        val record = linkedMapOf<String, Any>(
            "\$attr" to mapOf("label" to "Main"),
            "operation" to mapOf("option" to operation),
            "includeRowDescriptions" to mapOf("option" to "Y"),
            "includeAllColumns" to mapOf("option" to "Y"),
            "tableName" to "SCRIPT"
        )

        if (operation == "U" && !targetSerial.isNullOrBlank()) {
            record["targetSerial"] = targetSerial
        }

        record["field"] = fields

        val query = linkedMapOf<String, Any>(
            "\$attr" to mapOf("sessionId" to sessionId),
            "sequence" to mapOf(
                "transaction" to mapOf(
                    "step" to mapOf(
                        "record" to record
                    )
                )
            )
        )

        val payload = mapOf("query" to query)
        return mapper.writeValueAsString(payload)
    }

    /**
     * Build the direct Keystone URL from settings + instance.
     */
    private fun getKeystoneUrl(): String {
        val settings = KeyscriptSettings.getInstance()
        val instance = ScriptParameterService.getInstance(project).instance.ifEmpty {
            settings.getDefaultInstance()
        }
        val baseUrl = settings.getProxyUrl()
        return if (baseUrl.startsWith("http")) "$baseUrl/$instance" else "https://$baseUrl/$instance"
    }

    /**
     * POST JSON directly to Keystone, returning (responseBody, error).
     */
    private fun postToKeystone(jsonBody: String): Pair<String?, String?> {
        val url = getKeystoneUrl()
        log.info("POST to $url, body size=${jsonBody.length}")

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            val session = SessionService.getInstance(project)
            conn.setRequestProperty("Cookie", "JSESSIONID=${session.jsessionId}")
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }

            val status = conn.responseCode
            val responseBody = if (status in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }

            log.info("Keystone response: status=$status, body=${responseBody.take(300)}")

            // Detect session expiry
            if (isSessionExpired(status, responseBody)) {
                SessionService.getInstance(project).handleSessionExpired()
                return null to "Session expired"
            }

            if (status !in 200..299) {
                return null to "HTTP $status: ${responseBody.take(300)}"
            }

            responseBody to null
        } catch (e: Exception) {
            log.error("Keystone API call failed", e)
            null to "Connection failed: ${e.message}"
        }
    }

    private fun executeDeployment(jsonBody: String): DeployResult {
        val (responseBody, error) = postToKeystone(jsonBody)

        if (error != null) {
            val expired = error == "Session expired"
            return DeployResult(success = false, error = error, sessionExpired = expired)
        }

        return parseDeployResponse(responseBody!!)
    }

    private fun extractSearchResults(json: JsonNode): List<ScriptSearchResult> {
        val results = mutableListOf<ScriptSearchResult>()
        val search = findDeep(json, "search") ?: return results
        val rows = search.get("resultRow") ?: return results
        val rowList = if (rows.isArray) rows.toList() else listOf(rows)
        for (row in rowList) {
            val serial = row.path("serial").asText("")
            val desc = row.path("rowDescription").asText(
                row.path("selectColumn")?.firstOrNull()?.path("contents")?.asText("") ?: ""
            )
            if (serial.isNotEmpty()) {
                results.add(ScriptSearchResult(serial, desc))
            }
        }
        return results
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDeployResponse(responseBody: String): DeployResult {
        return try {
            val json = mapper.readValue(responseBody, Map::class.java) as Map<String, Any>
            val query = json["query"] as? Map<String, Any> ?: return DeployResult(success = false, error = "Invalid response: no query element")

            val sequences = query["sequence"]
            val seqList = if (sequences is List<*>) sequences else listOf(sequences)
            val seq = seqList.firstOrNull() as? Map<String, Any>

            val transactions = seq?.get("transaction")
            val txnList = if (transactions is List<*>) transactions else listOf(transactions)
            val txn = txnList.firstOrNull() as? Map<String, Any>

            // Check transaction result
            val txnAttr = txn?.get("\$attr") as? Map<String, Any>
            val txnResult = txnAttr?.get("result") as? String

            // Check for exceptions
            val exceptions = txn?.get("exception")
            if (exceptions != null) {
                val excList = if (exceptions is List<*>) exceptions else listOf(exceptions)
                val messages = excList.mapNotNull { exc ->
                    (exc as? Map<String, Any>)?.get("message")?.toString()
                }
                if (messages.isNotEmpty()) {
                    return DeployResult(success = false, error = messages.joinToString("\n"))
                }
            }

            if (txnResult != "posted") {
                return DeployResult(success = false, error = "Transaction result: $txnResult")
            }

            // Extract serial from the record response
            val steps = txn?.get("step")
            val stepList = if (steps is List<*>) steps else listOf(steps)
            val step = stepList.firstOrNull() as? Map<String, Any>
            val record = step?.get("record") as? Map<String, Any>

            val serial = record?.get("serial")?.toString()
            val rowDesc = record?.get("rowDescription")?.toString()

            DeployResult(
                success = true,
                serial = serial,
                description = rowDesc ?: "Deployed successfully"
            )
        } catch (e: Exception) {
            log.warn("Failed to parse deploy response", e)
            DeployResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

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

    companion object {
        fun getInstance(project: Project): DeploymentService =
            project.getService(DeploymentService::class.java)
    }
}
