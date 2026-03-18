package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Captures network events from the proxy for display in the Network Monitor tool window.
 */
@Service(Service.Level.PROJECT)
class NetworkMonitorService(private val project: Project) {

    data class NetworkEvent(
        val id: String,
        val type: String, // "request" or "response"
        val method: String = "",
        val url: String = "",
        val status: Int = 0,
        val body: String = "",
        val headers: Map<String, String> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * A correlated request/response pair sharing the same event ID.
     */
    data class NetworkExchange(
        val id: String,
        val request: NetworkEvent?,
        val response: NetworkEvent?
    ) {
        val method: String get() = request?.method ?: response?.method ?: ""
        val url: String get() = request?.url ?: response?.url ?: ""
        val status: Int get() = response?.status ?: 0
        val timestamp: Long get() = request?.timestamp ?: response?.timestamp ?: 0L
    }

    private val events = CopyOnWriteArrayList<NetworkEvent>()
    private val listeners = CopyOnWriteArrayList<(NetworkEvent) -> Unit>()

    fun addEvent(event: NetworkEvent) {
        events.add(event)
        // Keep last 500 events
        while (events.size > 500) events.removeAt(0)
        listeners.forEach { it(event) }
    }

    fun getEvents(): List<NetworkEvent> = events.toList()

    /**
     * Returns correlated request/response exchanges, ordered by timestamp.
     * Each unique event ID produces one exchange that may contain a request, a response, or both.
     */
    fun getExchanges(): List<NetworkExchange> {
        val grouped = events.filter { it.type != "console" }.groupBy { it.id }
        return grouped.map { (id, evts) ->
            NetworkExchange(
                id = id,
                request = evts.firstOrNull { it.type == "request" },
                response = evts.firstOrNull { it.type == "response" }
            )
        }.sortedBy { it.timestamp }
    }

    /**
     * Finds the matching response event for a given request ID, or null if not yet received.
     */
    fun findResponse(requestId: String): NetworkEvent? =
        events.firstOrNull { it.id == requestId && it.type == "response" }

    /**
     * Finds the matching request event for a given ID, or null.
     */
    fun findRequest(requestId: String): NetworkEvent? =
        events.firstOrNull { it.id == requestId && it.type == "request" }

    fun clear() = events.clear()

    fun addListener(listener: (NetworkEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (NetworkEvent) -> Unit) {
        listeners.remove(listener)
    }

    companion object {
        fun getInstance(project: Project): NetworkMonitorService =
            project.getService(NetworkMonitorService::class.java)
    }
}
