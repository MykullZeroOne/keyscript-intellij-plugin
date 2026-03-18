# In-App Guidance Reference

## Contents
- Guidance Surfaces Available
- Status Bar as Primary Signal
- Notification Balloons
- Inline Panel Messages
- Tool Tips and Disabled State Text
- Anti-Patterns

## Guidance Surfaces Available

The plugin has these in-app guidance surfaces. Choose the right one when scoping how users learn about a feature:

| Surface | API | When to Use |
|---------|-----|-------------|
| Status bar widget | `LoginStatusBarWidgetFactory` | Persistent state (auth status, proxy status) |
| Notification balloon | `NotificationGroupManager` | One-time events (login success, deploy complete) |
| Panel placeholder label | `JLabel` in tool window | Empty state guidance |
| Disabled action tooltip | `AnAction.update()` presentation | Why an action is unavailable |
| Settings inline error | `KeyscryptSettingsConfigurable` | Config validation errors |

## Status Bar as Primary Signal

The `LoginStatusBarWidget` is the most visible guidance surface. Its text drives user action more than any other element. When scoping features that change plugin state, specify the status bar copy:

```kotlin
// Status bar states — each needs defined copy in scope:
// "KS: Not Logged In"       → user needs to authenticate
// "KS: {username}"          → active session, ready to use
// "KS: Reconnecting..."     → auto-reconnect in progress
// "KS: Session Error"       → reconnect failed, action required
// "KS: Proxy Starting..."   → proxy starting on first run

// When scoping a new state:
// AC: Status bar shows "KS: {state-text}" when {condition}
// Copy must be ≤20 chars — status bar space is limited
```

## Notification Balloons

Use `NotificationGroupManager` for events that warrant user attention but don't need persistent display:

```kotlin
// Pattern used in the codebase for user-facing notifications:
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Deploy Complete",
        "Script deployed to ${instance} successfully",
        NotificationType.INFORMATION
    )
    .notify(project)

// When scoping, define:
// - Title (≤40 chars)
// - Body (≤120 chars)
// - Type: INFORMATION | WARNING | ERROR
// - Is it dismissible? (all balloons are, but note if it auto-expires)
```

## Inline Panel Messages

When a tool window panel has no content to show, use a `JLabel` centered in the panel:

```kotlin
// Pattern for empty/waiting states in tool window panels:
private fun createEmptyState(message: String): JComponent {
    return JPanel(BorderLayout()).apply {
        add(JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER)
    }
}

// Usage:
// "Log in to run scripts"                  → Workspace panel, unauthenticated
// "No network activity yet"                → Network Monitor, no runs
// "Run a script to see console output"     → Diagnostics console, no runs
// "Configure Keystone server in Settings"  → any panel, settings incomplete
```

## Tool Tips and Disabled State Text

Actions that are contextually unavailable must explain why. This is especially important for `RunScriptAction` and `DeployAction` which have multiple disabling conditions:

```kotlin
// In AnAction.update(), always set presentation text when disabling:
override fun update(e: AnActionEvent) {
    val session = e.project?.getService(SessionService::class.java)
    when {
        session == null -> {
            e.presentation.isEnabled = false
            e.presentation.text = "Run Script (not a Keyscript project)"
        }
        !session.isLoggedIn -> {
            e.presentation.isEnabled = false
            e.presentation.text = "Run Script (log in first)"
        }
        session.isReconnecting -> {
            e.presentation.isEnabled = false
            e.presentation.text = "Run Script (reconnecting...)"
        }
        else -> {
            e.presentation.isEnabled = true
            e.presentation.text = "Run Script"
        }
    }
}

// When scoping a new disable condition, always define the tooltip text in AC
```

## Anti-Patterns

### WARNING: Silent Failures as "Guidance"

**The Problem:**
```kotlin
// BAD — user clicks Run, nothing happens, no explanation
fun actionPerformed(e: AnActionEvent) {
    if (!session.isLoggedIn) return  // silent no-op
    // ...
}
```

**Why This Breaks:** Users retry, assume the plugin is broken, or file support tickets. Silent no-ops are the #1 source of "the plugin doesn't work" reports.

**The Fix:** Every early return in an action must produce user-visible feedback — either via notification, status bar update, or disabled presentation text set in `update()`.

### WARNING: Overusing Notification Balloons

Balloons that fire on every session heartbeat, every proxy request, or every file save create notification fatigue. Users dismiss-all and miss important messages.

**Rule:** Balloons only for user-initiated events or state changes that require user action. Background operations (heartbeat success, proxy keepalive) must be silent.
