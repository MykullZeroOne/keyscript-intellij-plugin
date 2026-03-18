# Feedback and Insights Reference

## Contents
- Collecting Feedback Inside an IntelliJ Plugin
- Error Notifications as Feedback Signals
- Network Monitor as Diagnostic Feedback
- Surfacing Errors to Users (Not Just Logs)
- Feedback-Driven Guidance Improvements
- Anti-Patterns

---

## Collecting Feedback Inside an IntelliJ Plugin

There is no in-app feedback widget. The plugin is internally distributed, so feedback channels are:
1. **Error balloons with "Report Issue" links** — users click through to a Jira/email URL
2. **Diagnostics panel copy** — log messages that help users self-diagnose and report
3. **Session panel status copy** — clear error states users can screenshot and share

## Error Notifications as Feedback Signals

Every error notification is also a feedback opportunity. Include enough context that users can report the issue without extra steps.

```kotlin
// BAD — user can't act on this
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification("Login failed", NotificationType.ERROR)
    .notify(project)

// GOOD — error + next step + diagnostics hint
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Login failed",
        "Could not authenticate with <b>${settings.keystoneServer}</b>. " +
        "Check credentials and verify the server is reachable.",
        NotificationType.ERROR
    )
    .addAction(NotificationAction.createSimple("Open Diagnostics") {
        project.service<WorkspaceUiService>().showDiagnosticsConsole()
    })
    .notify(project)
```

## Network Monitor as Diagnostic Feedback

`NetworkMonitorService` captures every proxy request/response. The Diagnostics > Network panel is the primary self-service feedback tool — users can see exactly what's failing.

Guide users to it proactively on first error:

```kotlin
// In AuthenticationService.kt — on login failure
private fun onLoginFailure(reason: String) {
    LOG.warn("[keyscript] login failed: $reason")
    val props = PropertiesComponent.getInstance()
    if (!props.getBoolean("keyscript.networkMonitorHintShown", false)) {
        props.setValue("keyscript.networkMonitorHintShown", true)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Login failed",
                "Check the <b>Diagnostics &gt; Network</b> tab to see the full request details.",
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimple("Open Network Monitor") {
                project.service<WorkspaceUiService>().showDiagnosticsNetwork()
            })
            .notify(project)
    }
}
```

## Surfacing Errors to Users (Not Just Logs)

NEVER swallow exceptions silently into `LOG.warn` without also notifying the user. Silent failures are the #1 source of "it's broken but I don't know why" feedback.

```kotlin
// GOOD — both log and notify
fun handleProxyError(e: Exception) {
    LOG.error("[keyscript] proxy error", e)
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Proxy server error",
            "${e.message ?: "Unknown error"}. The preview may not load correctly.",
            NotificationType.ERROR
        )
        .notify(project)
}
```

## Feedback-Driven Guidance Improvements

Use error patterns to identify where guidance is missing. Common failure modes and the guidance they suggest:

| Common failure | Root cause | Guidance to add |
|----------------|-----------|----------------|
| "Login failed" on first try | Server URL has `https://` prefix | Settings field hint: "No https:// prefix" |
| Preview shows blank | Proxy not started | Run error balloon: "Check proxy status in Diagnostics" |
| Deploy fails silently | Script not saved before deploy | Deploy action: warn if file has unsaved changes |
| Gutter icons not showing | File not detected as Keyscript | New file action: tooltip explaining detection rules |

```kotlin
// Proactive validation in DeployAction.kt
fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val document = FileDocumentManager.getInstance().getDocument(file)
    if (document != null && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
        // Save first, then deploy — don't let users deploy stale content silently
        FileDocumentManager.getInstance().saveDocument(document)
    }
    // ... proceed with deploy
}
```

## Anti-Patterns

### WARNING: Logging errors without surfacing them to users

**The Problem:**
```kotlin
// BAD — error visible only in idea.log, user sees nothing
try {
    keystoneApiClient.deploy(script)
} catch (e: Exception) {
    LOG.error("Deploy failed", e)
}
```

**Why This Breaks:** Users think the deploy succeeded. They'll re-run, see stale results, and report a confusing bug. Every caught exception in a user-triggered action must produce a visible notification with the error message and next step.

### WARNING: Generic "something went wrong" messages

**The Problem:**
```kotlin
// BAD — user cannot act on this
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification("Error", "Something went wrong.", NotificationType.ERROR)
    .notify(project)
```

**Why This Breaks:** Users copy-paste this into bug reports, which tells you nothing. Always include the actual error message (`e.message`), the server it was talking to, and the action they were attempting. See the **clarifying-market-fit** skill for copy principles that apply to error states too.
