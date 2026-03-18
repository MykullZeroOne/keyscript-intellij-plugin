# In-App Guidance Reference

## Contents
- Guidance surfaces in the plugin
- State-gated guidance
- EmptyStatePanel pattern
- Notification-based guidance
- Anti-patterns

---

## Guidance Surfaces in the Plugin

The plugin has three established surfaces for in-app guidance:

| Surface | Component | When It Shows |
|---------|-----------|--------------|
| Welcome dialog | `WelcomeDialog` | First project open (`shouldShowWelcome = true`) |
| Getting Started checklist | `GettingStartedPanel` | Workspace tool window, until dismissed |
| Empty state panels | `EmptyStatePanel` | Inside tool window tabs when no content |
| IDE notifications | `NotificationGroupManager` | Session events (login, session expiry, re-login) |
| Status bar widget | `LoginStatusBarWidgetFactory` | Always visible in Keyscript projects |

---

## State-Gated Guidance

Guidance should only appear when the user needs it. Gate display on `OnboardingStateService`:

```kotlin
// Show guidance only to users who haven't completed setup
val onboarding = OnboardingStateService.getInstance(project)

val panel = if (!onboarding.isComplete) {
    GettingStartedPanel(project).component
} else {
    buildWorkspaceContent(project)
}
```

`shouldShowWelcome` encapsulates the "show once" logic:

```kotlin
// onboarding/OnboardingStateService.kt
val shouldShowWelcome: Boolean
    get() = !myState.shownWelcome && !myState.dismissed

// Usage in KeyscryptProjectService or tool window factory:
if (onboarding.shouldShowWelcome) {
    onboarding.shownWelcome = true  // mark shown before displaying to prevent double-show
    ApplicationManager.getApplication().invokeLater {
        WelcomeDialog(project).show()
    }
}
```

---

## EmptyStatePanel Pattern

`EmptyStatePanel` is the standard component for zero-state guidance inside tool window tabs:

```kotlin
// toolwindow/EmptyStatePanel.kt — constructor signature
EmptyStatePanel(
    message: String,           // Primary label — short, e.g. "No results yet"
    detail: String? = null,    // Secondary hint — e.g. "Run a script to see network traffic"
    actionText: String? = null, // Optional CTA button text
    action: (() -> Unit)? = null // CTA action
)
```

Real usage in the Diagnostics panel (Network Monitor):

```kotlin
// Show empty state until first proxy traffic arrives
if (monitor.getExchanges().isEmpty()) {
    add(EmptyStatePanel(
        message = "No network activity",
        detail = "Run a script to see requests proxied through Keyscript IDE",
        actionText = "Run Script",
        action = { /* trigger run action */ }
    ), BorderLayout.CENTER)
}
```

NEVER use a blank panel or `JBLabel` alone for empty states — always use `EmptyStatePanel`
to stay consistent with IntelliJ HIG.

---

## Notification-Based Guidance

`SessionService` demonstrates the correct pattern for notification-based guidance.
Use `NotificationGroupManager` with the `"Keyscript"` group:

```kotlin
// services/SessionService.kt — notification pattern
private fun notify(message: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript", message, type)
        .notify(project)
}

// Examples of contextual guidance via notification:
notify("Session restored for $username", NotificationType.INFORMATION)
notify("Session expired. Please login again.", NotificationType.WARNING)
```

Use `INFORMATION` for progress confirmation, `WARNING` for recoverable issues requiring
user action, `ERROR` only for unrecoverable failures.

---

## Anti-Patterns

### WARNING: Showing Guidance After Milestone Completion

**The Problem:**
```kotlin
// BAD — checklist step appears even after user completes it
checklistPanel.add(createCheckItem(done = false, title = "Run a Keyscript", ...))
```

**Why This Breaks:** Users who have already run a script see incomplete items, which
erodes trust in the checklist and makes them dismiss it permanently.

**The Fix:** Always pass the live milestone state:
```kotlin
checklistPanel.add(createCheckItem(
    done = onboarding.completedFirstRun,  // GOOD — reflects actual state
    title = "Run a Keyscript",
    ...
))
```

### WARNING: Showing WelcomeDialog on a Background Thread

```kotlin
// BAD — dialog construction on non-EDT thread causes "accessing UI from wrong thread"
Thread { WelcomeDialog(project).show() }.start()

// GOOD — always use invokeLater for any UI construction
ApplicationManager.getApplication().invokeLater {
    WelcomeDialog(project).show()
}
```

See the **designing-inapp-guidance** skill for copy and tone patterns for these surfaces.
See the **intellij-platform** skill for `invokeLater` and EDT requirements.
