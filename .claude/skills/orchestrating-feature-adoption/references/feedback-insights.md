# Feedback & Insights Reference

## Contents
- Current Feedback Channels
- Error Notification Quality
- Surfacing Errors to the Right Audience
- Closing the Loop: Error → Jira
- Anti-Patterns

---

## Current Feedback Channels

The plugin has one user-facing feedback channel: the `"Keyscript"` balloon notification group.
There is no crash reporter, no in-app feedback form, and no usage survey mechanism.

Current notification callsites:
- `SessionService.notify()` — session lifecycle events
- `AuthenticationService` — login success/failure
- `RunKeyscriptAction.notify()` — run errors (not logged in, bad file)
- `DeployAction` — deploy status

All use the same `"Keyscript"` notification group registered in `plugin.xml`:
```xml
<notificationGroup id="Keyscript" displayType="BALLOON"/>
```

---

## Error Notification Quality

Current error messages tell users what went wrong but not what to do about it.

```kotlin
// Current (tells user the problem, not the fix):
notify("Please login to Keystone first (Keyscript > Login)", NotificationType.WARNING)

// Better (actionable — provides the fix inline):
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript")
    .createNotification("Keyscript IDE",
        "Login required to run scripts.",
        NotificationType.WARNING)
    .addAction(NotificationAction.createSimple("Login Now") {
        ActionManager.getInstance().getAction("Keyscript.Login")
            ?.actionPerformed(AnActionEvent.createFromDataContext("", null, dataContext))
    })
    .notify(project)
```

Apply this pattern to all error notifications that have a clear resolution step:
- "Login required" → add "Login Now" action
- "Proxy failed to start" → add "Open Settings" action
- "File not marked as Keyscript" → add "Add @keyscript marker" action

---

## Surfacing Errors to the Right Audience

The Diagnostics panel (Console tab) is the right place for developer-level error details.
Balloon notifications should be user-level summaries only.

```kotlin
// Two-tier error reporting pattern
fun reportRunError(project: Project, userMessage: String, technicalDetail: String) {
    // User-facing: concise, actionable
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript IDE", userMessage, NotificationType.ERROR)
        .notify(project)

    // Developer-facing: full detail in Diagnostics console
    DiagnosticsService.getInstance(project).logError("Run failed: $technicalDetail")
}
```

---

## Closing the Loop: Error → Jira

When users report errors, the Atlassian MCP tools make it fast to triage and file tickets
without leaving the IDE workflow.

```
Search for existing issues:
mcp__plugin_atlassian_atlassian__searchJiraIssuesUsingJql
  jql: "project = KS AND summary ~ \"proxy failed\" ORDER BY created DESC"

If no duplicate found, create a new issue:
mcp__plugin_atlassian_atlassian__createJiraIssue
  fields: { summary, description, issuetype: "Bug", priority: "High" }
```

**Triage workflow for plugin errors:**

Copy this checklist:
- [ ] Reproduce the error in sandbox IDE (`./gradlew runIde`)
- [ ] Check IDE log: `Help > Show Log in Finder` (macOS) for full stack trace
- [ ] Search Jira for duplicate: `mcp__plugin_atlassian_atlassian__searchJiraIssuesUsingJql`
- [ ] If duplicate: add comment with reproduction steps
- [ ] If new: file with component (`services/`, `proxy/`, etc.), stack trace, and steps

See the **atlassian:triage-issue** skill for the full triage workflow.

---

## Anti-Patterns

### WARNING: Swallowing exceptions silently in service methods

**The Problem:** `catch (e: Exception) { log.warn("error", e) }` without notifying the user.
**Why This Breaks:** Users see no feedback — the run button appears to do nothing. They
file no bug report because they don't know an error occurred.
**The Fix:** Always pair a log statement with a user-facing notification for operations
the user explicitly triggered (run, deploy, login, table load).

```kotlin
// BAD — silent failure
} catch (e: Exception) {
    log.warn("Deploy failed", e)
}

// GOOD — user knows, developer has details
} catch (e: Exception) {
    log.warn("Deploy failed", e)
    notify("Deploy failed: ${e.message ?: "unknown error"}", NotificationType.ERROR)
}
```

### WARNING: Generic "An error occurred" messages

**The Problem:** Vague error messages with no actionable information.
**Why This Breaks:** Users can't self-serve. Every vague error becomes a support ticket.
**The Fix:** Include the specific failure reason from the exception or API response.
The Keystone API returns error messages — surface them directly:
`"Deploy failed: ${result.error ?: "Check Diagnostics for details"}"`.
