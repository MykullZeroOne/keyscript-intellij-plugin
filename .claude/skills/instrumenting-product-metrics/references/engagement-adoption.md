# Engagement & Adoption Reference

## Contents
- Engagement signals in existing services
- Session-based engagement
- Network activity as a proxy for usage depth
- Feature adoption tracking
- Anti-patterns

---

## Engagement Signals in Existing Services

The plugin has no dedicated engagement service, but several services emit implicit signals:

| Signal | Source | What It Means |
|--------|--------|---------------|
| `SessionService.isLoggedIn` | `SessionService` | User has an active session |
| `SessionService.isLiveDatabase()` | `SessionService.loginData["databaseName"]` | User is on production — high-stakes usage |
| `NetworkMonitorService.getExchanges().size` | `NetworkMonitorService` | Scripts are executing (proxy traffic) |
| `OnboardingStateService.completedFirstDeploy` | `OnboardingStateService` | User has progressed beyond exploration |
| `ScriptParameterService.getScriptParameters()` | `ScriptParameterService` | User has configured script inputs |

---

## Session-Based Engagement

`SessionService` carries rich context about who is using the plugin and in what capacity:

```kotlin
val session = SessionService.getInstance(project)

// User identity
val username = session.username
val instance = session.instance

// Engagement context
val isLive = session.isLiveDatabase()  // true if databaseName ends with "LIV"
val loginData = session.loginData      // Map<String, String> from login response

// Time-based engagement (last heartbeat activity)
// lastSuccessfulPing is internal — derive from isLoggedIn + consecutive failures
val isActiveSession = session.isLoggedIn
```

`isLiveDatabase()` is the sharpest engagement signal: a user scripting against a live
production database is high-intent, experienced, and deeply adopted.

---

## Network Activity as a Proxy for Usage Depth

`NetworkMonitorService` captures every request/response pair through the proxy:

```kotlin
val monitor = NetworkMonitorService.getInstance(project)

// Total exchange count = scripts run × API calls per script
val depth = monitor.getExchanges().size

// Distinct URL patterns indicate which features are used
val urls = monitor.getExchanges().mapNotNull { it.url }
val usesDirectXML = urls.any { it.contains("DirectXML") }
val usesDataTools = urls.any { it.contains("DataTools") || it.contains("Search") }

// Error rate = proxy traffic health
val errorRate = monitor.getExchanges()
    .filter { it.status in 400..599 }
    .size.toDouble() / maxOf(monitor.getExchanges().size, 1)
```

Keep in mind: events are capped at 500 (`while (events.size > 500) events.removeAt(0)`).
Don't rely on counts for long sessions — use it as a "has run anything" boolean instead.

---

## Feature Adoption Tracking

To measure adoption of a specific feature (e.g., Data Tools, Query Builder), the pattern
is: add a boolean field to `OnboardingStateService` or create a lightweight counter in
`WorkspaceUiService`/`ScriptParameterService`.

For features beyond first-run:

```kotlin
// Option 1: Add to OnboardingStateService (persisted, good for "ever used")
var usedQueryBuilder: Boolean
    get() = myState.usedQueryBuilder
    set(value) {
        if (myState.usedQueryBuilder != value) {
            myState.usedQueryBuilder = value
            notifyListeners()
        }
    }

// Fire in QueryBuilderToolWindowFactory or the query execution action
OnboardingStateService.getInstance(project).usedQueryBuilder = true
```

```kotlin
// Option 2: Count in memory (WorkspaceUiService pattern — not persisted)
// Good for "how many times this session"
@Volatile private var queryBuilderUses = 0

fun recordQueryBuilderUse() {
    queryBuilderUses++
    notifyListeners()
}
```

---

## Anti-Patterns

### WARNING: Using NetworkMonitorService as a Durable Counter

**The Problem:**
```kotlin
// BAD — NetworkMonitorService is a ring buffer, not a durable log
val totalRuns = NetworkMonitorService.getInstance(project).getExchanges().size
```

**Why This Breaks:**
1. Capped at 500 events — long sessions lose history silently
2. Cleared on `clear()` calls (e.g., user clicks "Clear" in Diagnostics panel)
3. Not persisted across IDE restarts

**The Fix:** Use `OnboardingStateService` for durable "ever-done" facts, and a
`@Volatile var` in a service for in-session counts.

### WARNING: Treating `completedFirstRun` as "is currently running"

`completedFirstRun` is a one-time activation flag, not a live state indicator.
To detect if a script is actively executing, check `SessionService.isLoggedIn`
and whether `ProxyServerService` has an active server:

```kotlin
val proxyReady = ProxyServerService.getInstance(project).isRunning
val sessionActive = SessionService.getInstance(project).isLoggedIn
val canRun = proxyReady && sessionActive
```

See the **orchestrating-feature-adoption** skill for post-activation feature nudge patterns.
