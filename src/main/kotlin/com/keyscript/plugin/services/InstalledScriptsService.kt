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
 * Provides CRUD operations on the Keystone SCRIPT table for browsing
 * and managing server-side installed scripts.
 */
@Service(Service.Level.PROJECT)
class InstalledScriptsService(private val project: Project) {
    private val log = Logger.getInstance(InstalledScriptsService::class.java)
    private val mapper = jacksonObjectMapper()

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
     * Search the SCRIPT table by description. An empty search term returns all scripts
     * (up to the return limit).
     */
    fun listScripts(searchTerm: String = "", returnLimit: Int = 50): ServiceResult<List<ScriptRecord>> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val body = buildSearchJson(session.apiSessionId, searchTerm, returnLimit)
        val (responseBody, error) = postToKeystone(body)
        if (error != null) {
            return ServiceResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            log.info("InstalledScripts search raw response (2000 chars): ${responseBody?.take(2000)}")
            val json = mapper.readTree(responseBody)
            // Log the deep structure to find resultRow/resultRows
            val searchNode = findDeep(json, "search")
            if (searchNode != null) {
                log.info("InstalledScripts 'search' node keys: ${searchNode.fieldNames().asSequence().toList()}")
                val resultRow = searchNode.get("resultRow")
                if (resultRow != null) {
                    log.info("InstalledScripts resultRow type=${if (resultRow.isArray) "array[${resultRow.size()}]" else "object"}")
                    if (resultRow.isArray && resultRow.size() > 0) {
                        log.info("InstalledScripts first resultRow: ${resultRow[0].toString().take(500)}")
                    } else if (!resultRow.isArray) {
                        log.info("InstalledScripts resultRow (single): ${resultRow.toString().take(500)}")
                    }
                } else {
                    log.info("InstalledScripts: no 'resultRow' in search node")
                }
            } else {
                log.info("InstalledScripts: no 'search' node found in response")
            }
            val results = extractSearchResults(json)
            log.info("Parsed ${results.size} results, first: ${results.firstOrNull()}")
            ServiceResult(success = true, data = results)
        } catch (e: Exception) {
            log.warn("Failed to parse script search response", e)
            ServiceResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

    /**
     * View a SCRIPT record by serial, returning full details including SOURCE_CODE.
     */
    fun viewScript(serial: String): ServiceResult<ScriptDetail> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val body = buildViewJson(session.apiSessionId, serial)
        val (responseBody, error) = postToKeystone(body)
        if (error != null) {
            return ServiceResult(success = false, error = error, sessionExpired = error == "Session expired")
        }

        return try {
            log.info("View script #$serial response: ${responseBody?.take(800)}")
            val json = mapper.readTree(responseBody)
            val detail = extractScriptDetail(json, serial)
            if (detail != null) {
                log.info("Parsed script detail: desc=${detail.description}, sourceLen=${detail.sourceCode.length}, fields=${detail.fields.keys}")
                ServiceResult(success = true, data = detail)
            } else {
                log.warn("extractScriptDetail returned null for serial=$serial")
                ServiceResult(success = false, error = "Could not parse script record")
            }
        } catch (e: Exception) {
            log.warn("Failed to parse view response for serial=$serial", e)
            ServiceResult(success = false, error = "Failed to parse response: ${e.message}")
        }
    }

    /**
     * Delete a SCRIPT record by serial.
     */
    fun deleteScript(serial: String): ServiceResult<Unit> {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return ServiceResult(success = false, error = "Not logged in")
        }

        val body = buildDeleteJson(session.apiSessionId, serial)
        val (responseBody, error) = postToKeystone(body)
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

    // ─── JSON builders ──────────────────────────────

    private fun buildSearchJson(sessionId: String, description: String, returnLimit: Int): String {
        val search = linkedMapOf<String, Any>(
            "tableName" to "SCRIPT",
            "filterName" to "BY_DESCRIPTION",
            "includeSelectColumns" to mapOf("option" to "Y"),
            "includeRowDescriptions" to mapOf("option" to "Y"),
            "includeTotalHitCount" to mapOf("option" to "Y"),
            "returnLimit" to returnLimit,
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

    private fun buildViewJson(sessionId: String, serial: String): String {
        val record = linkedMapOf<String, Any>(
            "\$attr" to mapOf("label" to "Main"),
            "operation" to mapOf("option" to "V"),
            "includeAllColumns" to mapOf("option" to "Y"),
            "includeRowDescriptions" to mapOf("option" to "Y"),
            "tableName" to "SCRIPT",
            "targetSerial" to serial
        )

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

        return mapper.writeValueAsString(mapOf("query" to query))
    }

    private fun buildDeleteJson(sessionId: String, serial: String): String {
        val record = linkedMapOf<String, Any>(
            "\$attr" to mapOf("label" to "Main"),
            "operation" to mapOf("option" to "D"),
            "tableName" to "SCRIPT",
            "targetSerial" to serial
        )

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

        return mapper.writeValueAsString(mapOf("query" to query))
    }

    // ─── HTTP ───────────────────────────────────────

    private fun getKeystoneUrl(): String {
        val settings = KeyscriptSettings.getInstance()
        val instance = ScriptParameterService.getInstance(project).instance.ifEmpty {
            settings.getDefaultInstance()
        }
        val baseUrl = settings.getKeystoneApiBaseUrl()
        return "$baseUrl/$instance"
    }

    private fun postToKeystone(jsonBody: String): Pair<String?, String?> {
        val url = getKeystoneUrl()
        log.info("InstalledScriptsService POST to $url, body size=${jsonBody.length}")

        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            val session = SessionService.getInstance(project)
            conn.setRequestProperty("Cookie", "JSESSIONID=${session.apiSessionId}")
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
        val search = findDeep(json, "search") ?: return results
        val rows = search.get("resultRow") ?: return results
        val rowList = if (rows.isArray) rows.toList() else listOf(rows)

        for (row in rowList) {
            // Serial may be at row.serial or row.$attr.serial
            var serial = row.path("serial").asText("")
            if (serial.isEmpty()) {
                val attr = row.get("\$attr")
                if (attr != null) serial = attr.path("serial").asText("")
            }
            if (serial.isEmpty()) continue

            // Description from rowDescription or ROW_DESCRIPTION
            var desc = row.path("rowDescription").asText("")
            if (desc.isEmpty()) desc = row.path("ROW_DESCRIPTION").asText("")

            // Extract additional columns from selectColumn array.
            // Result-row selectColumns are POSITIONAL — they don't carry columnName.
            // Map them using the search-level selectColumn definitions, or fall back
            // to treating the first one as description if no columnName is present.
            var language = ""
            var category = ""
            var workArea = ""

            val selectColumns = row.get("selectColumn")
            if (selectColumns != null) {
                val colList = if (selectColumns.isArray) selectColumns.toList() else listOf(selectColumns)

                // Build column-name list from the search-level selectColumn definitions
                val searchSelectCols = search.get("selectColumn")
                val colNames = mutableListOf<String>()
                if (searchSelectCols != null) {
                    val defList = if (searchSelectCols.isArray) searchSelectCols.toList() else listOf(searchSelectCols)
                    defList.forEach { colNames.add(it.path("columnName").asText("")) }
                }

                for ((idx, col) in colList.withIndex()) {
                    // Try columnName on the result-row entry first (may not exist)
                    var colName = col.path("columnName").asText("")
                    // Fall back to positional mapping from search-level definitions
                    if (colName.isEmpty() && idx < colNames.size) {
                        colName = colNames[idx]
                    }
                    val contents = col.path("contents").asText("")
                    when (colName) {
                        "LANGUAGE" -> language = contents
                        "CATEGORY" -> category = contents
                        "CLIENT_TRAN_WORK_AREA_OPTION" -> workArea = contents
                        "DESCRIPTION", "ROW_DESCRIPTION" -> if (desc.isEmpty()) desc = contents
                    }
                    // If still no mapping and this is the first column, use as description
                    if (colName.isEmpty() && idx == 0 && desc.isEmpty()) {
                        desc = contents
                    }
                }
            }

            results.add(ScriptRecord(
                serial = serial,
                description = desc,
                language = language,
                category = category,
                workAreaOption = workArea
            ))
        }
        return results
    }

    private fun extractScriptDetail(json: JsonNode, serial: String): ScriptDetail? {
        val record = findDeep(json, "record")
        if (record == null) {
            log.warn("extractScriptDetail: no 'record' node found. Full response: ${json.toString().take(1000)}")
            return null
        }
        log.info("extractScriptDetail: record node keys = ${record.fieldNames().asSequence().toList()}")
        log.info("extractScriptDetail: record = ${record.toString().take(1500)}")

        val fields = mutableMapOf<String, String>()

        // Format 1: "field" array — [{columnName, contents}, ...]
        val fieldNode = record.get("field")
        if (fieldNode != null) {
            val fieldList = if (fieldNode.isArray) fieldNode.toList() else listOf(fieldNode)
            log.info("extractScriptDetail: found ${fieldList.size} field entries")
            for (f in fieldList) {
                val colName = f.path("columnName").asText("")
                val contents = f.path("contents").asText("")
                val newContents = f.path("newContents").asText("")
                if (colName.isNotEmpty()) {
                    fields[colName] = contents.ifEmpty { newContents }
                }
            }
        }

        // Format 2: direct child nodes on record (e.g., record.DESCRIPTION = {contents: "..."})
        if (fields.isEmpty()) {
            log.info("extractScriptDetail: no 'field' array found, trying direct children")
            val skip = setOf("\$attr", "operation", "tableName", "targetSerial",
                "includeAllColumns", "includeRowDescriptions", "includeTableMetadata",
                "includeColumnMetadata", "serial", "rowDescription")
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

        log.info("extractScriptDetail: parsed ${fields.size} fields. Keys: ${fields.keys}")
        if (fields.containsKey("SOURCE_CODE")) {
            log.info("extractScriptDetail: SOURCE_CODE length = ${fields["SOURCE_CODE"]?.length}")
        } else {
            log.warn("extractScriptDetail: SOURCE_CODE not found in fields!")
        }

        // Also try to get serial from record or $attr
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
