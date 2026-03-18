# Product Analytics Reference

## Contents
- Current State (No Analytics)
- Funnel Events to Instrument
- Lightweight Event Logging Pattern
- PropertiesComponent as a Local Signal Store
- Anti-Patterns

---

## Current State: No Analytics

**WARNING: This plugin has no product analytics instrumentation.** There is no way to know:
- How many users complete the settings → login → first-run funnel
- Which tool windows are actually used
- Where users drop off

The plugin uses IntelliJ `Logger` for developer diagnostics only. For a developer tool
distributed to an internal team, structured local logging is the pragmatic starting point
before adding external telemetry.

---

## Funnel Events to Instrument

These are the highest-value events to capture:

| Event | Where to Add | Signal |
|-------|-------------|--------|
| `plugin.activated` | `KeyscriptProjectService.initialize()` | Project is a Keyscript project |
| `settings.configured` | `KeyscryptSettingsConfigurable.apply()` | User saved settings |
| `login.attempted` | `AuthenticationService.login()` | User tried to log in |
| `login.succeeded` | `AuthenticationService.login()` on success | Activation milestone |
| `login.failed` | `AuthenticationService.login()` on failure | Drop-off signal |
| `script.run` | `RunKeyscriptService` or `RunKeyscriptAction` | Core value delivery |
| `script.deployed` | `DeploymentService` | Power-user action |
| `toolwindow.opened` | Per `ToolWindowFactory.createToolWindowContent()` | Discovery signal |

---

## Lightweight Event Logging Pattern

Without an external analytics backend, use structured log output that can be parsed from
the IDE's log file. This costs zero dependencies.

```kotlin
// Add to a new file: src/main/kotlin/com/keyscript/plugin/analytics/ProductEvents.kt
object ProductEvents {
    private val log = Logger.getInstance("keyscript.product")

    fun track(event: String, properties: Map<String, Any> = emptyMap()) {
        val props = properties.entries.joinToString(", ") { "${it.key}=${it.value}" }
        log.info("EVENT $event ${if (props.isNotEmpty()) "[$props]" else ""}")
    }
}

// Usage — in AuthenticationService on successful login:
ProductEvents.track("login.succeeded", mapOf("instance" to instance, "authMethod" to "password"))

// Usage — in KeyscriptProjectService.initialize():
ProductEvents.track("plugin.activated", mapOf("project" to project.name))
```

Log output: `INFO keyscript.product - EVENT login.succeeded [instance=Development, authMethod=password]`

---

## PropertiesComponent as a Local Signal Store

For tracking adoption milestones without a backend, use `PropertiesComponent` as a local
event store. Useful for "has user ever done X?" questions.

```kotlin
// Record first-run milestones
object AdoptionMilestones {
    private const val KEY_FIRST_LOGIN = "keyscript.milestone.first_login"
    private const val KEY_FIRST_RUN = "keyscript.milestone.first_run"
    private const val KEY_FIRST_DEPLOY = "keyscript.milestone.first_deploy"

    fun recordFirstLogin(project: Project) {
        PropertiesComponent.getInstance(project)
            .setValue(KEY_FIRST_LOGIN, System.currentTimeMillis().toString())
    }

    fun hasCompletedFirstRun(project: Project): Boolean =
        PropertiesComponent.getInstance(project).getValue(KEY_FIRST_RUN) != null
}
```

---

## Anti-Patterns

### WARNING: Adding external telemetry without user consent

**The Problem:** Sending usage data to an external server from an IntelliJ plugin without
an explicit privacy disclosure violates JetBrains Marketplace policies and user trust.
**Why This Breaks:** JetBrains will reject the plugin submission; enterprise users may
flag it as a security concern.
**The Fix:** Use local log files and `PropertiesComponent` for now. If external telemetry
is needed later, add an opt-in setting with a clear privacy statement.

### WARNING: Logging credentials or JSESSIONID in event properties

**The Problem:** Passing sensitive values into `ProductEvents.track()` properties.
**Why This Breaks:** Log files are often shared for debugging. A JSESSIONID in a log file
is a live session hijack vector.
**The Fix:** Only log non-sensitive identifiers: event name, instance name, boolean flags.
NEVER log: passwords, session IDs, device IDs, usernames.
