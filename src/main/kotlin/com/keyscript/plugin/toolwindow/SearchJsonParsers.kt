package com.keyscript.plugin.toolwindow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.diagnostic.Logger

object SearchJsonParsers {
    private val log = Logger.getInstance(SearchJsonParsers::class.java)
    private val mapper = jacksonObjectMapper()

    data class SearchRow(
        val serial: String,
        val description: String,
        val status: String = ""
    )

    fun parseRows(json: String): List<SearchRow> {
        log.info("SearchJsonParsers.parseRows input: ${json.take(800)}")
        val root = mapper.readTree(json)

        // Try multiple field names — XML-to-JSON may use singular or plural
        val rows = findArray(root, "resultRow")
            ?: findArray(root, "resultRows")
            ?: return emptyList<SearchRow>().also {
                log.warn("SearchJsonParsers: no resultRow/resultRows found")
            }

        // Build column-name list from search-level selectColumn definitions (positional mapping)
        val searchNode = findDeep(root, "search")
        val colNames = mutableListOf<String>()
        if (searchNode != null) {
            val searchSelectCols = searchNode["selectColumn"]
            if (searchSelectCols != null) {
                val defList = if (searchSelectCols.isArray) searchSelectCols.toList() else listOf(searchSelectCols)
                defList.forEach { colNames.add(it.textOrEmpty("columnName")) }
            }
        }

        return rows.mapNotNull { row ->
            log.info("SearchJsonParsers row: ${row.toString().take(300)}")

            // Serial may be direct or in $attr
            var serial = row.textOrEmpty("serial", "SERIAL")
            if (serial.isEmpty()) {
                val attr = row.get("\$attr")
                if (attr != null) serial = attr.textOrEmpty("serial", "SERIAL")
            }
            if (serial.isEmpty()) return@mapNotNull null

            var desc = row.textOrEmpty(
                "rowDescription",
                "ROW_DESCRIPTION",
                "description",
                "DESCRIPTION",
                "accountAssociation",
                "ACCOUNT_ASSOCIATION"
            )

            // Fall back to positional selectColumn contents
            if (desc.isEmpty()) {
                val selectColumns = row["selectColumn"]
                if (selectColumns != null) {
                    val colList = if (selectColumns.isArray) selectColumns.toList() else listOf(selectColumns)
                    for ((idx, col) in colList.withIndex()) {
                        val contents = col.textOrEmpty("contents")
                        val colName = if (idx < colNames.size) colNames[idx] else ""
                        if (contents.isNotEmpty() && (colName == "ROW_DESCRIPTION" || colName == "DESCRIPTION" || idx == 0)) {
                            desc = contents
                            break
                        }
                    }
                }
            }

            SearchRow(
                serial = serial,
                description = desc,
                status = row.textOrEmpty("rowStatus", "ROW_STATUS", "status", "STATUS")
            )
        }
    }

    private fun JsonNode.textOrEmpty(vararg fieldNames: String): String {
        for (fieldName in fieldNames) {
            val node = this[fieldName] ?: continue
            if (node.isNull) continue
            return if (node.isTextual) node.asText() else node.toString().trim('"')
        }
        return ""
    }

    private fun findDeep(node: JsonNode, key: String): JsonNode? {
        if (node.has(key)) return node[key]
        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findDeep(child, key)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findArray(node: JsonNode, key: String): Iterable<JsonNode>? {
        if (node.has(key)) {
            val found = node[key]
            return if (found.isArray) found else listOf(found)
        }

        for (child in node) {
            if (child.isObject || child.isArray) {
                val found = findArray(child, key)
                if (found != null) return found
            }
        }
        return null
    }
}
