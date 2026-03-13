# Roadmap & Experiments: UX Improvements to Prioritize

This document translates the friction points found during journey analysis into
prioritized roadmap items. Each item includes the affected service/file, the proposed
change, and a minimal experiment design so you can validate the fix before committing to
the full implementation.

## Priority Stack

Rank by: (severity of friction) × (frequency of the journey step).

| Priority | Item | Affected files | Effort |
|----------|------|---------------|--------|
| P0 | Session expiry must notify the user and offer re-login | `SessionService`, `LoginStatusBarWidgetFactory` | Small |
| P0 | Proxy startup error must surface a balloon notification | `ProxyServerService`, `AuthenticationService` | Small |
| P1 | Empty states in all tool window panels | `SessionPanel`, `TableBrowserPanel`, `QueryBuilderPanel`, `NetworkToolWindowFactory` | Medium |
| P1 | Deploy confirmation shows target instance | `DeployAction` | Small |
| P1 | Settings validation before LoginDialog | `LoginAction` | Small |
| P2 | New Project wizard routes to Settings after creation | `KeyscryptModuleBuilder` | Small |
| P2 | Gutter icon robustness for BOM/shebang files | `KeyscryptRunLineMarkerContributor` | Small |
| P3 | Loading indicator in JCEF panel during run | `RunKeyscriptService`, `KeyscryptPreviewComponent` | Medium |
| P3 | Diagnostics Stats tab showing session health | `KeyscryptDiagnosticsToolWindowFactory` | Medium |

## P0: Session Expiry Notification

**Current behaviour:** `SessionService.handleSessionExpired()` clears the session and
fires `notifyListeners()`. The status bar widget updates, but if the user is not looking
at the status bar (e.g. they are in the editor), there is no proactive notification.

**Experiment:** Show a sticky `NotificationType.WARNING` balloon when expiry is detected
by heartbeat (as opposed to expiry during an active operation, which already shows a
balloon). Measure whether users re-login faster after the change by comparing time
between expiry event log and next `login.success` event.

```kotlin
// In SessionService.handleSessionExpired(), after clearing session:
if (!reloginInProgress.getAndSet(true)) {
    // The existing re-login background thread handles this path.
    // If no saved creds, the WARNING notification fires.
} else {
    // Heartbeat-detected expiry with no re-login path: notify explicitly.
    notify(
        "Your Keystone session has expired. Click the status bar widget to log back in.",
        NotificationType.WARNING
    )
}
```

## P0: Proxy Startup Error Balloon

**Current behaviour:** `ProxyServerService.ensureStarted()` swallows exceptions in the
`KtorProxyServer.start()` call. If port 3000 is already in use, the server fails to
bind silently. The user sees a connection error on the next run attempt, not at startup.

**Experiment:** Wrap the startup call and report failures immediately:

```kotlin
// In ProxyServerService.ensureStarted():
try {
    server = KtorProxyServer(...)
    server!!.start()
    log.info("Proxy server started on port ${settings.proxyPort}")
} catch (e: Exception) {
    server = null
    log.error("Proxy server failed to start on port ${settings.proxyPort}", e)
    // Surface immediately — this blocks all run/login operations
    val msg = when {
        e.message?.contains("Address already in use") == true ->
            "Port ${settings.proxyPort} is already in use. " +
            "Change the proxy port in Settings > Keyscript IDE."
        else -> "Proxy server failed to start: ${e.message}"
    }
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript Proxy Error", msg, NotificationType.ERROR)
        .notify(project)
    throw e // re-throw so callers can abort
}
```

## P1: Deploy Confirmation Shows Target Instance

**Current behaviour:** Deploy fires immediately after triggering, often silently using
the wrong instance (whichever was last set in the Run Options tab).

**Experiment:** Add a one-line confirmation dialog. Track whether deploy failures
(wrong-instance class) decrease in the log after the change.

The confirmation dialog implementation is in `engagement-adoption.md`. Track the event:

```kotlin
// After the deploy action completes:
PluginAnalytics.track("deploy.initiated",
    "instance" to targetInstance,
    "confirmed" to confirmed,
    "file" to file.name
)
```

## P2: Progressive Settings Validation

Rather than blocking the login dialog with a full settings check every time, use a
one-time flag stored in `KeyscryptSettings` to show the validation prompt only on first
login per installation:

```kotlin
// In KeyscryptSettings.State:
data class State(
    // ... existing fields ...
    var hasCompletedInitialSetup: Boolean = false
)

// In LoginAction.actionPerformed():
val settings = KeyscryptSettings.getInstance()
if (!settings.hasCompletedInitialSetup) {
    val isDefaultServer = settings.proxyEndpoint == "keystonedev.revfcu.com:8443"
    if (isDefaultServer) {
        val go = Messages.showYesNoDialog(...)
        if (go == Messages.YES) {
            ShowSettingsUtil.getInstance().showSettingsDialog(...)
            return
        }
    }
    settings.hasCompletedInitialSetup = true
}
```

This ensures the check fires once and never again after the user has configured the
server, avoiding repeated nags on every login.

## P3: JCEF Loading State

The JCEF panel shows the previous run's content while the new run is being prepared.
This creates a false sense that the script ran immediately when in fact it is still
hitting the `SessionStore` endpoint.

The simplest experiment is to load a static "loading" HTML page into the JCEF panel
before `preparePreview()` starts:

```kotlin
// In RunKeyscriptService — before preparePreview() call:
private fun showLoadingState(scriptFile: VirtualFile) {
    val editorManager = FileEditorManager.getInstance(project)
    val preview = editorManager.getAllEditors(scriptFile)
        .filterIsInstance<TextEditorWithPreview>()
        .firstOrNull()
        ?.previewEditor as? KeyscryptPreviewFileEditor
    preview?.loadUrl("about:blank") // clears stale content immediately
}
```

A more polished version loads a local HTML file with a spinner, but `about:blank` is
sufficient to validate whether users notice the state change.

## Experiment Checklist

Before shipping any of these changes:

- [ ] Manual test in sandbox IDE (`./gradlew runIde`) against all five journeys
- [ ] Verify the notification group `"Keyscript"` is registered in `plugin.xml`
      (required for `NotificationGroupManager` calls to not silently fail)
- [ ] Check that new `KeyscryptSettings.State` fields have sensible defaults that
      survive XML deserialization from an older settings file (add them with defaults
      in the `data class State(...)` declaration)
- [ ] No new thread creation outside of `Thread("keyscript-*")` naming pattern —
      named threads are visible in thread dumps and aid debugging
