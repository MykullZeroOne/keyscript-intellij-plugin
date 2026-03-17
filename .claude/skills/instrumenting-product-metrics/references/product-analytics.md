# Product Analytics Reference

## Contents
- Current analytics architecture
- Deriving metrics from existing state
- Adding a new observable event
- WARNING: No external telemetry
- Logging-based analytics

---

## Current Analytics Architecture

> **WARNING: No External Analytics Backend**
>
> This plugin has **no analytics SDK**, no event pipeline, and no telemetry. All product
> insight is derived from:
> 1. `OnboardingStateService` persisted state (`KeyscryptOnboarding.xml`)
> 2. IntelliJ diagnostic logs (via `Logger.getInstance()`)
> 3. Manual review of crash reports via JetBrains Plugin Support
>
> Adding an analytics SDK to a JetBrains Marketplace plugin requires explicit opt-in consent
> UI and Marketplace policy compliance. NEVER add silent telemetry.

---

## Deriving Metrics from Existing State

All currently measurable product metrics are derived in-process:

```kotlin
// Activation rate proxy — read during development/testing with resetOnboarding()
fun getActivationSnapshot(project: Project): Map<String, Boolean> {
    val o = OnboardingStateService.getInstance(project)
    return mapOf(
        "configured_server" to o.configuredServer,
        "completed_login" to o.completedFirstLogin,
        "completed_run" to o.completedFirstRun,
        "completed_deploy" to o.completedFirstDeploy,
        "is_activated" to o.isComplete,
        "dismissed_checklist" to o.dismissed
    )
}
```

```kotlin
// Session health — read at debug time
fun getSessionSnapshot(project: Project): Map<String, Any> {
    val s = SessionService.getInstance(project)
    return mapOf(
        "is_logged_in" to s.isLoggedIn,
        "username" to s.username,
        "instance" to s.instance,
        "is_live_db" to s.isLiveDatabase()
    )
}
```

---

## Adding a New Observable Event

When you need to track a new product event, the pattern is:

1. Decide if it's **durable** (persisted between sessions) or **ephemeral** (this session only)
2. For durable: add to `OnboardingStateService.State`
3. For ephemeral: add an `AtomicInteger` or `@Volatile var` to the relevant service
4. Log it via `Logger` so it appears in IDE logs for debugging

```kotlin
// Durable event — add to OnboardingStateService
data class State(
    // ... existing ...
    var usedTableBrowser: Boolean = false,
    var deployCount: Int = 0  // NOTE: Int fields also persist via PersistentStateComponent
)

// Ephemeral event — in-service counter
@Service(Service.Level.PROJECT)
class InstalledScriptsService(private val project: Project) {
    private val log = Logger.getInstance(InstalledScriptsService::class.java)
    @Volatile private var downloadCount = 0

    fun recordDownload(scriptName: String) {
        downloadCount++
        log.info("Script downloaded: $scriptName (total this session: $downloadCount)")
    }
}
```

---

## Logging-Based Analytics

IntelliJ's `Logger` writes to `idea.log` — the primary instrumentation channel
for development insights:

```kotlin
private val log = Logger.getInstance(RunKeyscriptService::class.java)

// Log key product events with structured context
log.info("Script run: path=$scriptPath, instance=$instance, debugMode=$debugMode")
log.info("Deploy success: serial=$serial, description=$description")
log.info("Session established: user=$username, live=${session.isLiveDatabase()}")
log.warn("Run failed: ${result.error}")  // surface in logs for issue triage
```

Find logs at: `~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log` (macOS)

To analyze usage patterns during development:
```bash
grep "Script run:" ~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log | wc -l
grep "Deploy success:" ~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log
grep "Session established:" ~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log | tail -20
```

---

## Anti-Patterns

### WARNING: Deriving Metrics from UI Component State

**The Problem:**
```kotlin
// BAD — checking JProgressBar value to determine activation state
val isActivated = progressBar.value == progressBar.maximum
```

**Why This Breaks:** UI components are rebuilt on every `rebuildChecklist()` call.
They reflect display state, not source of truth. UI can be dismissed/hidden/rebuilt
while the underlying state is unchanged.

**The Fix:** Always read directly from `OnboardingStateService`:
```kotlin
val isActivated = OnboardingStateService.getInstance(project).isComplete
```

See the **kotlin** skill for `PersistentStateComponent` serialization details.
See the **intellij-platform** skill for `Logger` usage patterns.
