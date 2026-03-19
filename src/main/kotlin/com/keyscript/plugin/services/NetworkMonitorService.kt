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

    private val events = ArrayDeque<NetworkEvent>(500)
    private val eventsLock = Any()
    private val listeners = CopyOnWriteArrayList<(NetworkEvent) -> Unit>()

    fun addEvent(event: NetworkEvent) {
        synchronized(eventsLock) {
            events.addLast(event)
            while (events.size > 500) events.removeFirst()
        }
        listeners.forEach { it(event) }
    }

    fun getEvents(): List<NetworkEvent> = synchronized(eventsLock) { events.toList() }

    /**
     * Returns correlated request/response exchanges, ordered by timestamp.
     * Each unique event ID produces one exchange that may contain a request, a response, or both.
     */
    fun getExchanges(): List<NetworkExchange> {
        val grouped = getEvents().filter { it.type != "console" }.groupBy { it.id }
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
        getEvents().firstOrNull { it.id == requestId && it.type == "response" }

    /**
     * Finds the matching request event for a given ID, or null.
     */
    fun findRequest(requestId: String): NetworkEvent? =
        getEvents().firstOrNull { it.id == requestId && it.type == "request" }

    fun clear() = synchronized(eventsLock) { events.clear() }

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
