# Engagement & Adoption Reference

## Contents
- Adoption Model for Plugin Features
- Session State as Engagement Signal
- Post-Login Feature Discovery
- Re-engagement After Session Expiry
- WARNING: Don't Over-Notify
- Feature Completion Milestones

---

## Adoption Model for Plugin Features

Feature adoption in an IDE plugin follows a different model than a web app. Users discover
features by encountering them in context — not through email campaigns. The levers are:

| Surface | Trigger | Feature |
|---------|---------|---------|
| Status bar widget | Every file open | Login CTA |
| Tool window Workspace | First Keyscript project open | Run Options, Session |
| Gutter icon | Opening a `.keyscript.js` file | Run script |
| Code completions | Typing `CR.` | CR framework API |
| Deploy action | Run menu | Deploy to Keystone |
| Data Tools | Bottom panel | Search, Table Browser, Query Builder |

The gutter icon and completions are self-discoverable. The Data Tools panel is not — users won't
open it unless they know it exists.

---

## Session State as Engagement Signal

`SessionService` already tracks the highest-value engagement signal: `isLoggedIn`. Use it to
gate feature discovery nudges.

```kotlin
// Show "Did you know?" hint in Workspace panel after first successful login
session.addListener {
    if (session.isLoggedIn && !onboarding.state.shownDataToolsHint) {
        onboarding.state.shownDataToolsHint = true
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification(
                "Keyscript IDE",
                "You can browse Keystone tables and run queries in the Data Tools panel.",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
```

**DO** add listener in the tool window's `init` block, AFTER the service is available.
**AVOID** adding listeners in factory `createToolWindowContent` — factories don't dispose properly.

---

## Post-Login Feature Discovery

After login, surface Data Tools and Diagnostics with a banner in the Workspace panel:

```kotlin
private fun buildPostLoginHint(): JComponent? {
    if (onboarding.state.dismissedDataToolsHint) return null
    return JPanel(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(UIUtil.getSeparatorColor(), 0, 0, 1, 0),
            JBUI.Borders.empty(8, 12)
        )
        background = UIUtil.getPanelBackground()
        add(JBLabel("<html><small>Tip: Browse tables and run queries in <b>Data Tools</b> below.</small></html>"),
            BorderLayout.CENTER)
        add(ActionLink("Dismiss") {
            onboarding.state.dismissedDataToolsHint = true
            parent.remove(this@apply)
            parent.revalidate()
        }, BorderLayout.EAST)
    }
}
```

---

## Re-engagement After Session Expiry

`SessionService.handleSessionExpired()` already attempts auto-relogin. Make the fallback more
actionable when auto-relogin fails:

```kotlin
// In SessionService, when auto-relogin fails
private fun notifyLoginRequired() {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(
            "Keyscript session expired",
            "Your Keystone session has ended. Log in again to continue.",
            NotificationType.WARNING
        )
        .addAction(NotificationAction.createSimple("Log In") {
            ActionManager.getInstance().getAction("com.keyscript.plugin.actions.LoginAction")
                ?.actionPerformed(
                    AnActionEvent.createFromAnAction(
                        it, null, ActionPlaces.NOTIFICATION,
                        DataContext.EMPTY_CONTEXT
                    )
                )
        })
        .notify(project)
}
```

---

## WARNING: Don't Over-Notify

**The Problem:**

```kotlin
// BAD — fires every time the project opens, even after the user has already configured everything
override suspend fun execute(project: Project) {
    showWelcomeNotification(project)  // No state guard
}
```

**Why This Breaks:**
1. Balloon notifications stack up — user dismisses all of them without reading
2. Repeated notifications for already-completed steps signal a broken product
3. IntelliJ users are notification-averse; irrelevant ones train them to ignore all notifications

**The Fix:** Always guard with a `PersistentStateComponent` flag:

```kotlin
if (!onboarding.state.shownWelcome) {
    onboarding.state.shownWelcome = true
    showWelcomeNotification(project)
}
```

---

## Feature Completion Milestones

Track key milestones in `OnboardingStateService` to unlock contextual tips:

```kotlin
data class State(
    var shownWelcome: Boolean = false,
    var completedFirstLogin: Boolean = false,
    var completedFirstRun: Boolean = false,
    var dismissedDataToolsHint: Boolean = false,
    var completedFirstDeploy: Boolean = false
)
```

Set `completedFirstRun = true` in `RunKeyscriptService` after the first successful execution.
Set `completedFirstDeploy = true` in `DeploymentService` after the first successful deploy.

Use these to show progressively more advanced tips rather than all at once. See the
**improving-activation-flow** skill for the full funnel instrumentation approach.
