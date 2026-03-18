# Engagement & Adoption Reference

## Contents
- Secondary Feature Discovery
- Tool Window Discoverability
- Post-Login Engagement Hooks
- Session Re-Engagement
- Anti-Patterns

---

## Secondary Feature Discovery

The three tool windows are registered with `secondary="true"` in `plugin.xml`:

```xml
<toolWindow id="Keyscript Data Tools" anchor="bottom" secondary="true"
            factoryClass="com.keyscript.plugin.toolwindow.KeyscryptDataToolsToolWindowFactory"
            icon="AllIcons.Nodes.DataTables"/>
```

`secondary="true"` prevents auto-opening. Users must find them via View > Tool Windows.
**This is the biggest adoption gap** — Data Tools and Diagnostics are invisible after install.

---

## Tool Window Discoverability

**DO:** After first successful login, programmatically open (or flash) secondary tool windows once.

```kotlin
// In AuthenticationService, on successful login
private fun revealToolWindowsOnFirstLogin(project: Project) {
    val prefs = PropertiesComponent.getInstance(project)
    if (prefs.getBoolean("keyscript.toolwindows.revealed", false)) return
    prefs.setValue("keyscript.toolwindows.revealed", true)

    ApplicationManager.getApplication().invokeLater {
        val twm = ToolWindowManager.getInstance(project)
        // Activate but don't steal focus
        twm.getToolWindow("Keyscript Workspace")?.show(null)
        twm.getToolWindow("Keyscript Data Tools")?.show(null)
    }
}
```

**DO:** Use the existing `createToolWindowShell` subtitle in `KeyscryptToolWindowUi.kt` to
explain the window purpose. Current subtitles are good — keep them:

```kotlin
// KeyscriptDataToolsToolWindowFactory.kt — already present, preserve this pattern
createToolWindowShell(
    title = "Keyscript Data Tools",
    subtitle = "Search members, browse tables, and build queries from one coordinated workspace.",
    content = tabbedPane
)
```

---

## Post-Login Engagement Hooks

After login the `SessionPanel` switches from empty-state to a details view. This is the best
moment to surface "what to do next" — currently it shows only session metadata.

**DO:** Add a "Quick Actions" row below session details.

```kotlin
// At the bottom of SessionPanel's logged-in state builder
private fun buildQuickActionsBar(): JComponent = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    border = JBUI.Borders.empty(4, 8)
    add(ActionLink("Run a script") { triggerRunAction() })
    add(Box.createHorizontalStrut(12))
    add(ActionLink("Browse tables") {
        ToolWindowManager.getInstance(project)
            .getToolWindow("Keyscript Data Tools")?.show(null)
    })
    add(Box.createHorizontalStrut(12))
    add(ActionLink("Open Diagnostics") {
        ToolWindowManager.getInstance(project)
            .getToolWindow("Keyscript Diagnostics")?.show(null)
    })
}
```

---

## Session Re-Engagement

`SessionService.handleSessionExpired()` already fires re-login and notifies the user. The
notification text can be improved to re-engage rather than just inform:

```kotlin
// Current (passive):
notify("Session expired. Please login again.", NotificationType.WARNING)

// Better (actionable):
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript")
    .createNotification("Keyscript IDE", "Your session expired.", NotificationType.WARNING)
    .addAction(NotificationAction.createSimple("Login Again") {
        LoginAction().actionPerformed(/* ... */)
    })
    .notify(project)
```

---

## Anti-Patterns

### WARNING: Opening tool windows on every project load

**The Problem:** Unconditionally calling `toolWindow.show()` at startup forces panels open
on every IDE restart, overriding the user's layout preferences.
**Why This Breaks:** IntelliJ remembers tool window state. Overriding it is jarring — like
an app rearranging your desktop on each launch. Users will hate it.
**The Fix:** Gate with `PropertiesComponent` so it fires exactly once per project, post-login.

### WARNING: Using `NotificationType.ERROR` for non-errors

**The Problem:** Overusing `ERROR` type for informational messages (e.g., "session expired").
**Why This Breaks:** Red error badges desensitize users. Real errors get dismissed along with noise.
**The Fix:** `WARNING` for recoverable states (session expired), `INFORMATION` for nudges,
`ERROR` only for unrecoverable failures (proxy crash, auth server unreachable).
