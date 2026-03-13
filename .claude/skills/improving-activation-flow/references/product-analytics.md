# Product Analytics Reference

## Contents
- Current Instrumentation State
- Key Activation Events to Track
- Implementing Event Logging
- Funnel Analysis Points
- WARNING: Missing Analytics Infrastructure

---

## Current Instrumentation State

The plugin has **no product analytics instrumentation**. The only observability is IntelliJ's built-in `Logger` (debug/info/warn/error), which writes to the IDE log — not to any external analytics system.

```kotlin
// This is ALL the current instrumentation:
private val log = Logger.getInstance(SessionService::class.java)
log.info("Session established for $username: ${jsessionId.take(8)}...")
log.info("Session expired, attempting re-login...")
```

These logs are local only — no funnel visibility, no drop-off detection, no activation rate tracking.

---

## Key Activation Events to Track

For a plugin distributed to developers internally, the most valuable events are:

| Event | Trigger Point | Code Location |
|-------|--------------|---------------|
| `keyscript.project_detected` | `KeyscryptProjectDetector.detect()` returns true | `services/KeyscryptProjectDetector.kt:36` |
| `keyscript.login_attempted` | User clicks OK in `LoginDialog` | `actions/LoginAction.kt:186` |
| `keyscript.login_success` | `result.success == true` in `LoginDialog` | `actions/LoginAction.kt:192` |
| `keyscript.login_failed` | `result.success == false` in `LoginDialog` | `actions/LoginAction.kt:205` |
| `keyscript.first_run` | Proxy starts for first time | `services/ProxyServerService.kt` |
| `keyscript.script_run` | Script execution completes | `services/RunKeyscryptService.kt` |
| `keyscript.deploy_success` | Deploy completes without error | `services/DeploymentService.kt` |
| `keyscript.session_expired` | Heartbeat detects 401/403 | `services/SessionService.kt:175` |
| `keyscript.session_restored` | Auto-relogin succeeds | `services/SessionService.kt:103` |

---

## Implementing Event Logging

Without an external analytics backend, log activation events to a structured format that can be aggregated:

```kotlin
// Simple structured event logger — add to a new ActivationEventLogger.kt
object ActivationEventLogger {
    private val log = Logger.getInstance(ActivationEventLogger::class.java)

    fun track(event: String, properties: Map<String, String> = emptyMap()) {
        val propsJson = properties.entries.joinToString(", ") { "\"${it.key}\": \"${it.value}\"" }
        log.info("KEYSCRIPT_EVENT: {\"event\": \"$event\", $propsJson}")
    }
}

// Usage at activation milestones:
ActivationEventLogger.track("keyscript.login_success", mapOf(
    "instance" to instance,
    "has_saved_credentials" to (loadCredentials() != null).toString()
))
```

This lets you grep the IDE log for `KEYSCRIPT_EVENT:` entries to build funnel data manually until a proper backend is available.

---

## Funnel Analysis Points

The activation funnel has five measurable steps. Each requires an explicit log entry:

```kotlin
// Step 1: Project opened and detected
// In KeyscryptProjectService.initialize():
ActivationEventLogger.track("keyscript.project_detected", mapOf(
    "project" to project.name
))

// Step 2: Settings appear configured (non-default endpoint)
// In KeyscryptProjectService.initialize():
val isConfigured = settings.proxyEndpoint != "keystonedev.revfcu.com:8443"
ActivationEventLogger.track("keyscript.settings_state", mapOf(
    "configured" to isConfigured.toString()
))

// Step 3: Login attempted
// In LoginDialog.doOKAction():
ActivationEventLogger.track("keyscript.login_attempted", mapOf(
    "instance" to instance,
    "has_saved_creds" to (savedCreds != null).toString()
))

// Step 4: Login result
ActivationEventLogger.track(
    if (result.success) "keyscript.login_success" else "keyscript.login_failed",
    mapOf("instance" to instance, "error" to (result.error ?: ""))
)

// Step 5: First script run
ActivationEventLogger.track("keyscript.script_run", mapOf(
    "instance" to session.instance
))
```

---

## WARNING: Missing Analytics Infrastructure

The plugin has no structured analytics pipeline. This means:

1. **No activation rate visibility** — you cannot know what % of users who install the plugin successfully authenticate
2. **No drop-off detection** — if users hit the "Setup Required" state and abandon, you will not know
3. **No session health metrics** — `handleSessionExpired()` fires silently; you cannot know how often sessions drop

**Short-term fix:** Use the structured log pattern above + a log aggregator (Splunk, Datadog, or even a shared log folder) to collect `KEYSCRIPT_EVENT:` lines from developer machines.

**Long-term fix:** Add a lightweight HTTP POST to an internal telemetry endpoint from `ActivationEventLogger`. Keep it opt-in and never include credentials or script content.

```kotlin
// Opt-in telemetry skeleton (requires user consent toggle in settings):
fun trackIfEnabled(event: String, properties: Map<String, String>) {
    if (!KeyscryptSettings.getInstance().telemetryEnabled) return
    // POST to internal analytics endpoint
}
```

See the **ktor** skill for implementing the HTTP client call using the existing Ktor CIO client already in the dependency graph (`io.ktor:ktor-client-cio-jvm`).
