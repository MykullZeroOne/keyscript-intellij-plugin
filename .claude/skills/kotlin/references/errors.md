# Kotlin Error Handling Reference

## Contents
- Logging
- User-Facing Notifications
- API Error Handling
- Session Expiry Flow
- Runnable Recovery Pattern
- Anti-Patterns

---

## Logging

Use `Logger.getInstance(ClassName::class.java)` — one logger per class. Logs appear in the IDE's **Run** panel when running via `./gradlew runIde`.

```kotlin
private val LOG = Logger.getInstance(DeploymentService::class.java)

// Levels: debug, info, warn, error
LOG.debug("Starting deploy for: $filePath")
LOG.info("Deploy succeeded: serialNumber=$serial")
LOG.warn("Deploy returned unexpected status: $status")
LOG.error("Deploy threw exception", exception)
```

Log levels matter:
- `debug` — verbose, off by default, safe to call frequently
- `info` — lifecycle events, visible in logs
- `warn` — recoverable problems (bad response, retry)
- `error` — unrecoverable failures; always include the exception

NEVER log credentials, session IDs, or full response bodies containing user data. The IDE logs are not secured.

---

## User-Facing Notifications

For errors that the user should act on, use `NotificationGroupManager`. For silent background errors, log only.

```kotlin
// Define the group once in plugin.xml:
// <notificationGroup id="Keyscript" displayType="BALLOON"/>

// Show notification from anywhere (any thread — invokeLater internally)
private fun notifyError(message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(message, NotificationType.ERROR)
        .notify(project)
}

private fun notifySuccess(message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(message, NotificationType.INFORMATION)
        .notify(project)
}
```

Decision guide:
- API call fails → log `warn` + notify user with actionable message
- Proxy fails to start → log `error` + notify user (they can't run scripts)
- Session expires → log `info` + trigger auto-relogin silently (notify only if relogin fails)
- Background heartbeat fails → log `debug` only

---

## API Error Handling

`KeystoneApiClient` and `DeploymentService` return result objects rather than throwing. Callers check `result.success` before using data.

```kotlin
// Inside DeploymentService.deployUpdate()
val result = apiClient.sendQuery(payload)

when {
    result.sessionExpired -> {
        LOG.info("Session expired during deploy, triggering relogin")
        project.service<SessionService>().handleSessionExpired()
        notifyError("Session expired. Please log in again.")
    }
    !result.success -> {
        LOG.warn("Deploy failed: ${result.error}")
        notifyError("Deploy failed: ${result.error ?: "Unknown error"}")
    }
    else -> {
        LOG.info("Deploy succeeded")
        notifySuccess("Script deployed successfully")
    }
}
```

Session expiry detection in `KeystoneApiClient`:
```kotlin
private fun isSessionExpired(status: HttpStatusCode, body: String): Boolean =
    status == HttpStatusCode.Unauthorized ||
    status == HttpStatusCode.Forbidden ||
    body.contains("session", ignoreCase = true) && body.contains("expired", ignoreCase = true)
```

---

## Session Expiry Flow

Session expiry is a cross-cutting concern. When any service detects expiry, it calls `SessionService.handleSessionExpired()` which either auto-relogins or notifies the user.

```kotlin
// In SessionService
fun handleSessionExpired() {
    LOG.info("Session expired")
    val (user, pass) = loadCredentials()
    if (user != null && pass != null) {
        // Attempt silent relogin
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = project.service<AuthenticationService>().login(user, pass, getDefaultInstance())
            if (!result.success) {
                ApplicationManager.getApplication().invokeLater {
                    setLoggedOut()
                    notifyError("Session expired and relogin failed: ${result.error}")
                }
            }
        }
    } else {
        setLoggedOut()
        notifyError("Session expired. Please log in again.")
    }
}
```

---

## Runnable Recovery Pattern

For operations that may fail transiently (network flake, server restart), wrap in `runCatching` and handle both paths:

```kotlin
fun ensureProxyStarted(): Boolean {
    if (isRunning) return true

    return runCatching {
        startProxyServer()
        true
    }.onFailure { e ->
        LOG.error("Proxy server failed to start", e)
        notifyError("Proxy server could not start on port $proxyPort. Check if the port is in use.")
    }.getOrDefault(false)
}
```

`runCatching` is idiomatic Kotlin for try/catch that returns a value. Prefer it over bare try/catch when you need to transform the result.

---

## Anti-Patterns

### WARNING: Swallowing Exceptions Silently

**The Problem:**
```kotlin
// BAD — exception disappears, bug is invisible
try {
    apiClient.deploy(payload)
} catch (e: Exception) {
    // nothing
}
```

**Why This Breaks:** The deploy silently fails. The user sees no feedback. Debugging requires guessing where things went wrong.

**The Fix:**
```kotlin
try {
    apiClient.deploy(payload)
} catch (e: Exception) {
    LOG.warn("Deploy exception", e)
    notifyError("Deploy failed: ${e.message}")
}
```

### WARNING: Throwing Exceptions Across Thread Boundaries

**The Problem:**
```kotlin
// BAD — exception thrown in pooled thread, not caught, crashes silently
ApplicationManager.getApplication().executeOnPooledThread {
    val result = apiClient.fetch() // throws IOException
    // IOException propagates, thread dies, user sees nothing
}
```

**Why This Breaks:** Uncaught exceptions in `executeOnPooledThread` are swallowed by the platform. They don't crash the IDE — they just disappear.

**The Fix:**
```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    try {
        val result = apiClient.fetch()
        ApplicationManager.getApplication().invokeLater { handleResult(result) }
    } catch (e: Exception) {
        LOG.error("Fetch failed", e)
        ApplicationManager.getApplication().invokeLater { notifyError("Fetch failed: ${e.message}") }
    }
}
```

Always wrap the entire body of `executeOnPooledThread` in try/catch.

### WARNING: Showing Error Dialogs from Non-EDT Thread

**The Problem:**
```kotlin
// BAD — called from pooled thread, JOptionPane/IntelliJ dialogs require EDT
ApplicationManager.getApplication().executeOnPooledThread {
    if (failed) {
        Messages.showErrorDialog(project, "Failed!", "Error") // EDT violation
    }
}
```

**The Fix:**
```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    if (failed) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, "Failed!", "Error") // on EDT
        }
    }
}
```

All UI operations — dialog display, component updates, notification balloons — must run on the EDT via `invokeLater`.
