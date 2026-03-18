# Product Analytics: Instrumenting Plugin Journeys

The Keyscript plugin has no analytics layer today. This document defines where to add
observability, what to measure, and how to implement it within the IntelliJ platform
constraints (no external network calls without user consent, no PII in logs).

## Guiding Principle

IntelliJ plugins must not phone home without explicit user consent. Use the IDE's own
diagnostic infrastructure — `Logger`, `NotificationGroupManager`, and the `Diagnostics`
tool window — rather than sending data to an external service. The "analytics" here are
local structured logs that developers can read from the IDE's log files or the
Diagnostics panel.

## What to Measure

### Funnel: Activation

| Step | Success signal | Failure signal |
|------|---------------|----------------|
| Project opened | `KeyscryptProjectDetector.isKeyscriptProject = true` | detector returns false silently |
| Status bar widget appears | `LoginStatusBarWidgetFactory.isAvailable = true` | widget absent |
| Login dialog opened | `LoginAction.actionPerformed` called | user does not find widget |
| Login succeeded | `AuthenticationService.LoginResult.success = true` | `LoginResult.success = false` |
| First run executed | `RunKeyscriptService.runScript` called | never called after login |

### Funnel: Script Run Health

| Event | Normal rate | Alert threshold |
|-------|------------|-----------------|
| `preparePreview` success | > 95% | < 90% signals proxy or network issue |
| `SessionStore` POST latency | < 500ms | > 2s is jarring in editor context |
| Heartbeat session expiry | < 5%/day | > 20% signals short server session timeout |
| JCEF load failure | < 1% | > 5% signals JCEF availability problem |

## Implementation: Structured Logger

Use `Logger` with a consistent key=value format that can be parsed by log analysis tools.

Create a `PluginAnalytics` object in `services/`:

```kotlin
// src/main/kotlin/com/keyscript/plugin/services/PluginAnalytics.kt
package com.keyscript.plugin.services

import com.intellij.openapi.diagnostic.Logger

object PluginAnalytics {
    private val log = Logger.getInstance("com.keyscript.plugin.analytics")

    fun track(event: String, vararg props: Pair<String, Any?>) {
        val propsStr = props.joinToString(" ") { (k, v) -> "$k=${v ?: "null"}" }
        log.info("event=$event $propsStr")
    }
}
```

Call it at each key journey step:

```kotlin
// In AuthenticationService.login():
if (result.success) {
    PluginAnalytics.track("login.success", "instance" to instance)
} else {
    PluginAnalytics.track("login.failure",
        "instance" to instance,
        "error_type" to classifyError(result.error)
    )
}

// In RunKeyscriptService.preparePreview():
val start = System.currentTimeMillis()
// ... operation ...
val elapsed = System.currentTimeMillis() - start
PluginAnalytics.track("run.prepare",
    "success" to result.success,
    "latency_ms" to elapsed,
    "error" to result.error?.let { classifyError(it) }
)
```

Error classification avoids logging PII or raw server messages:

```kotlin
private fun classifyError(message: String?): String = when {
    message == null -> "unknown"
    message.contains("Connection refused") -> "proxy_not_started"
    message.contains("timeout", ignoreCase = true) -> "network_timeout"
    message.contains("session", ignoreCase = true) -> "session_expired"
    message.contains("login", ignoreCase = true) -> "auth_failure"
    else -> "other"
}
```

## Surfacing Metrics in the Diagnostics Panel

`KeyscryptDiagnosticsToolWindowFactory` has a Console tab and a Network Monitor tab.
Add a third "Stats" tab that renders the structured log counts from the current session.

```kotlin
// In KeyscryptDiagnosticsToolWindowFactory or DiagnosticsPanel:
class SessionStatsPanel(private val project: Project) {
    private val statsArea = JBTextArea().apply {
        isEditable = false
        font = JBFont.monospaced()
    }

    val component: JComponent = JScrollPane(statsArea)

    fun refresh() {
        val session = SessionService.getInstance(project)
        val proxy = ProxyServerService.getInstance(project)
        val stats = buildString {
            appendLine("=== Session Stats ===")
            appendLine("Logged in: ${session.isLoggedIn}")
            appendLine("User: ${session.username.ifEmpty { "—" }}")
            appendLine("Instance: ${session.instance.ifEmpty { "—" }}")
            appendLine("Proxy running: ${proxy.ssoSessionId.isNotEmpty()}")
        }
        statsArea.text = stats
    }
}
```

## Heartbeat Monitoring

`SessionService.checkSessionValid()` silently handles expiry. Instrument it to surface
expiry events in the Diagnostics console:

```kotlin
// In SessionService.checkSessionValid():
private fun checkSessionValid() {
    if (!isLoggedIn) return
    // ... existing check logic ...
    if (status == 401 || status == 403 || (!hasJsession && status != 200)) {
        log.info("Heartbeat: session appears expired (status=$status)")
        PluginAnalytics.track("session.heartbeat_expired",
            "status" to status,
            "instance" to instance
        )
        handleSessionExpired()
    } else {
        // Optional: track successful heartbeat at DEBUG level only
        log.debug("Heartbeat OK: status=$status")
    }
}
```

## Diagnostics Console Integration

`NetworkMonitorService` already captures HTTP request/response events. Extend it to
emit structured journey events:

```kotlin
// Proposed addition to NetworkMonitorService:
data class JourneyEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val detail: String
)

private val journeyEvents = CopyOnWriteArrayList<JourneyEvent>()

fun recordJourneyEvent(event: String, detail: String = "") {
    journeyEvents.add(JourneyEvent(event = event, detail = detail))
    log.info("journey: event=$event detail=$detail")
}
```

**WARNING:** Never log credential values, session IDs, or full HTTP response bodies
in journey events. Log only event names, latency, and classified error types. A
`JSESSIONID` in the IDE log file is a security exposure.

## Reading the Logs

All analytics events write to the IntelliJ log at `~/.cache/JetBrains/<IDE>/log/idea.log`.
Filter for Keyscript analytics:

```bash
grep "com.keyscript.plugin.analytics" ~/.cache/JetBrains/IntelliJIdea2025.1/log/idea.log \
  | grep "event=" \
  | awk -F'event=' '{print $2}' \
  | sort | uniq -c | sort -rn
```
