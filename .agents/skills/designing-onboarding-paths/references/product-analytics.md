# Product Analytics Reference

## Contents
- WARNING: No Analytics Infrastructure Exists
- What to Measure in an IDE Plugin
- Instrumentation Points
- Lightweight Local Telemetry Pattern
- Funnel Measurement via State Flags
- Privacy Considerations

---

## WARNING: No Analytics Infrastructure Exists

The plugin has no analytics, telemetry, or event tracking. There is no way to measure activation
rates, feature adoption, or drop-off points. **Every product decision is currently made blind.**

Before building any new onboarding or feature discovery UI, decide on a telemetry approach:

| Option | Tradeoff |
|--------|----------|
| IntelliJ's built-in `FUSLogger` (Feature Usage Statistics) | Free, privacy-safe, aggregated. No real-time access. Requires JetBrains Marketplace approval. |
| Local structured log file | Full control, privacy-safe, requires user to share logs manually. Works offline. |
| Keystone server endpoint | Events go to a server you control. Requires user to be logged in. |

For an internal enterprise plugin, a **local log file** is the simplest approach with no
privacy concerns and no external dependencies.

---

## What to Measure in an IDE Plugin

Focus on activation funnel events and feature completion milestones:

| Event | Signal |
|-------|--------|
| `project.detected` | Project opened with Keyscript markers |
| `settings.opened` | User opened Settings > Keyscript IDE |
| `settings.server.configured` | Server endpoint saved (non-blank) |
| `login.attempted` | Login dialog submitted |
| `login.succeeded` | Session established |
| `login.failed` | Authentication error |
| `script.run.triggered` | Gutter icon or Run menu used |
| `script.run.succeeded` | Script executed without error |
| `deploy.triggered` | Deploy action invoked |
| `data_tools.table_browser.opened` | Table Browser tab focused |

---

## Instrumentation Points

Map events to existing service hooks:

```kotlin
// In KeyscryptProjectService.StartupActivity
log.info("[ANALYTICS] project.detected project=${project.name}")

// In AuthenticationService.login()
log.info("[ANALYTICS] login.attempted")
// on success:
log.info("[ANALYTICS] login.succeeded")
// on failure:
log.info("[ANALYTICS] login.failed error=${e.message}")

// In RunKeyscriptService (after execution starts)
log.info("[ANALYTICS] script.run.triggered file=${file.name}")
```

Using `[ANALYTICS]` prefix makes events grep-able from IntelliJ's idea.log without custom
infrastructure. This is the minimum viable approach — extract and parse later.

---

## Lightweight Local Telemetry Pattern

For richer structured data, write events to a local JSON file alongside the IDE log:

```kotlin
@Service(Service.Level.APP)
class TelemetryService {
    private val logFile = File(PathManager.getLogPath(), "keyscrypt-events.jsonl")

    fun track(event: String, props: Map<String, String> = emptyMap()) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val entry = buildJsonObject(event, props)
            logFile.appendText(entry + "\n")
        }
    }

    private fun buildJsonObject(event: String, props: Map<String, String>): String {
        val base = mapOf(
            "event" to event,
            "ts" to System.currentTimeMillis().toString(),
            "ideVersion" to ApplicationInfo.getInstance().fullVersion
        ) + props
        // Use Jackson (already a dependency) for serialization
        return ObjectMapper().writeValueAsString(base)
    }
}
```

Call from services: `TelemetryService.getInstance().track("login.succeeded", mapOf("instance" to instance))`

See the **jackson** skill for `ObjectMapper` configuration patterns.

---

## Funnel Measurement via State Flags

Without external telemetry, `OnboardingStateService` flags double as funnel checkpoints:

```kotlin
// Measure completion rate by checking state across projects
// In a diagnostic command or support output:
fun dumpFunnelState(project: Project): String {
    val s = project.service<OnboardingStateService>().state
    return """
        Funnel:
          shownWelcome:        ${s.shownWelcome}
          completedFirstLogin: ${s.completedFirstLogin}
          completedFirstRun:   ${s.completedFirstRun}
          completedFirstDeploy:${s.completedFirstDeploy}
    """.trimIndent()
}
```

Surface this in the Diagnostics panel under a "Support Info" section to help users self-diagnose
and share state when reporting issues.

---

## Privacy Considerations

NEVER log:
- Credentials, passwords, or session tokens
- File contents or script source code
- Keystone server hostnames (may be internal infrastructure)
- Usernames (log hashed or anonymized IDs only)

ALWAYS:
- Make telemetry opt-in if sending data off-device
- Document what is collected in README.md
- Provide a way to disable (setting in `KeyscryptSettings`)

For the local log-file approach, no opt-in is required since data stays on the user's machine.
