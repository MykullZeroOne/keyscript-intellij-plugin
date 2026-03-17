# Feedback & Insights Reference

## Contents
- User feedback surfaces in the plugin
- Error signals as implicit feedback
- Session quality indicators
- Handling deploy/run failures as insight
- Anti-patterns

---

## User Feedback Surfaces

The plugin has no dedicated feedback form. User feedback surfaces are:

| Surface | Mechanism | What You Learn |
|---------|-----------|----------------|
| Status bar widget | `LoginStatusBarWidgetFactory` | Login friction (how long "KS: Not Logged In" shows) |
| Getting Started checklist | `GettingStartedPanel` — "Dismiss Checklist" button | Onboarding dropout point |
| IDE notifications | `NotificationGroupManager` | Session expiry frequency |
| IntelliJ logs | `idea.log` | Error patterns, run/deploy failures |
| JetBrains Marketplace reviews | Plugin listing | Post-install experience |

Dismissal tracking in `OnboardingStateService`:

```kotlin
// User clicked "Dismiss Checklist" — they either completed setup or gave up
val dismissed = OnboardingStateService.getInstance(project).dismissed
val completedBeforeDismiss = OnboardingStateService.getInstance(project).isComplete
// If dismissed && !completedBeforeDismiss → early dropout
```

---

## Error Signals as Implicit Feedback

Failed runs and deploys are the sharpest implicit feedback signal. Log them with context:

```kotlin
// services/RunKeyscriptService.kt — log failure with actionable context
val result = preparePreview(scriptPath)
if (!result.success) {
    log.warn("Script run failed: error=${result.error}, script=$scriptPath, " +
             "logged_in=${session.isLoggedIn}, proxy_running=${proxyService.isRunning}")
    // Surface to user via notification so they can act
    notify("Run failed: ${result.error}", NotificationType.ERROR)
}
```

```kotlin
// services/DeploymentService.kt — distinguish session errors from logic errors
val result = executeDeployment(body)
if (!result.success) {
    if (result.sessionExpired) {
        log.info("Deploy failed due to session expiry — re-login triggered")
    } else {
        log.warn("Deploy failed: ${result.error}")  // logic/data error — actionable
    }
}
```

`sessionExpired = true` on a `DeployResult` means the failure is infrastructure, not
user error — don't surface it as an error notification; the session relogin handles it.

---

## Session Quality Indicators

`SessionService` tracks session health via consecutive heartbeat failures:

```kotlin
// Internal constants in SessionService
private const val HEARTBEAT_INTERVAL_MS = 45L * 1000  // ping every 45s
private const val MAX_CONSECUTIVE_FAILURES = 2         // 2 failures → declare expired

// What this tells you:
// - If users frequently see "Session expired. Please login again." notifications,
//   the Keystone server's session TTL is shorter than 90s (2 × 45s)
// - If isLiveDatabase() users see more session expiries, production has tighter TTLs
```

To surface this insight, check logs for the expiry pattern:

```bash
grep "Session expired" ~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log
grep "consecutive failures" ~/Library/Logs/JetBrains/IdeaIC2025.1/idea.log
```

---

## Network Monitor as Quality Signal

The Diagnostics Network Monitor (`NetworkMonitorService`) shows error rate in real-time.
Use it during development to spot proxy issues:

```kotlin
val monitor = NetworkMonitorService.getInstance(project)

// Error exchanges = 4xx/5xx responses
val errorExchanges = monitor.getExchanges().filter { it.status in 400..599 }

// Listen for errors in real-time
monitor.addListener { event ->
    if (event.type == "response" && event.status in 400..599) {
        log.warn("Proxy error: ${event.status} ${event.url}")
    }
}
```

---

## Anti-Patterns

### WARNING: Silencing Errors That Users Need to Act On

**The Problem:**
```kotlin
// BAD — catches exception, does nothing
try {
    runScript(file)
} catch (e: Exception) {
    log.error("Failed", e)  // logged but user has no idea what happened
}
```

**Why This Breaks:** Users see no feedback. They retry repeatedly, then assume the plugin
is broken. Silent failures generate Marketplace 1-star reviews.

**The Fix:** Always surface actionable errors via notification:
```kotlin
try {
    val result = runScript(file)
    if (!result.success) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Run Failed", result.error ?: "Unknown error", NotificationType.ERROR)
            .notify(project)
    }
} catch (e: Exception) {
    log.error("Run failed unexpectedly", e)
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Run Failed", e.message ?: "Unexpected error", NotificationType.ERROR)
        .notify(project)
}
```

See the **designing-inapp-guidance** skill for error notification copy patterns.
See the **mapping-user-journeys** skill for mapping dropout points in the login/run flow.
