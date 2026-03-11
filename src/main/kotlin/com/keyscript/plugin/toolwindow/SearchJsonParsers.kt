package com.keyscript.plugin.toolwindow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object SearchJsonParsers {
    private val mapper = jacksonObjectMapper()

    data class SearchRow(
        val serial: String,
        val description: String,
        val status: String = ""
    )

    fun parseRows(json: String): List<SearchRow> {
        val root = mapper.readTree(json)
        val rows = findArray(root, "resultRows") ?: return emptyList()
        return rows.mapNotNull { row ->
            val serial = row.textOrEmpty("serial", "SERIAL")
            if (serial.isEmpty()) return@mapNotNull null

            SearchRow(
                serial = serial,
                description = row.textOrEmpty(
                    "rowDescription",
                    "ROW_DESCRIPTION",
                    "description",
                    "DESCRIPTION",
                    "accountAssociation",
                    "ACCOUNT_ASSOCIATION"
                ),
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
