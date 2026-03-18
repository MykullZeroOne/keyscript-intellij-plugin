# Engagement and Adoption Reference

## Contents
- Feature Discovery Nudges
- Post-Action Hints
- Tool Window Header Actions
- Session-Aware Nudges
- Anti-Patterns

---

## Feature Discovery Nudges

The three tool windows (Workspace, Data Tools, Diagnostics) are invisible until the user knows to look. Balloon notifications after key events are the primary discovery mechanism — no third-party tour library exists in this stack.

**Trigger: first successful script run → nudge Diagnostics**

```kotlin
// In RunKeyscriptService.kt, after a successful run
private fun onRunSuccess() {
    val props = PropertiesComponent.getInstance()
    if (!props.getBoolean("keyscript.diagnosticsNudgeShown", false)) {
        props.setValue("keyscript.diagnosticsNudgeShown", true)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Tip: watch network traffic",
                "Open the <b>Diagnostics</b> tool window to inspect HTTP requests your script makes via the proxy.",
                NotificationType.INFORMATION
            )
            .addAction(NotificationAction.createSimple("Open Diagnostics") {
                project.service<WorkspaceUiService>().showDiagnosticsConsole()
            })
            .notify(project)
    }
}
```

**Trigger: first deploy → nudge Table Browser**

```kotlin
// In DeploymentService.kt, after successful deploy
private fun onDeploySuccess(scriptName: String) {
    val props = PropertiesComponent.getInstance()
    if (!props.getBoolean("keyscript.tableBrowserNudgeShown", false)) {
        props.setValue("keyscript.tableBrowserNudgeShown", true)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "$scriptName deployed",
                "Use the <b>Data Tools &gt; Table Browser</b> to verify the SCRIPT table record.",
                NotificationType.INFORMATION
            )
            .addAction(NotificationAction.createSimple("Open Table Browser") {
                project.service<WorkspaceUiService>().showDataToolsTableBrowser()
            })
            .notify(project)
    }
}
```

## Post-Action Hints

Every significant action (run, deploy, bundle) should end with a balloon. This closes the feedback loop and seeds discovery of adjacent features.

```kotlin
// BundleAction.kt — success notification with next-step hint
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Bundle complete (${bundleSize}KB)",
        "Run your script or deploy it to Keystone.",
        NotificationType.INFORMATION
    )
    .addAction(NotificationAction.createSimple("Run") {
        ActionManager.getInstance().getAction("keyscript.RunScript")
            ?.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT))
    })
    .notify(project)
```

## Tool Window Header Actions

Add icon buttons to tool window headers for discoverability of secondary actions. `WorkspaceUiService.registerHostPanel()` gives you the `ToolWindow` reference needed.

```kotlin
// In KeyscryptWorkspaceToolWindowFactory.kt — createToolWindowContent
toolWindow.setTitleActions(listOf(
    object : AnAction("Open in Browser", "Open current script in browser", AllIcons.Toolwindows.WebToolWindow) {
        override fun actionPerformed(e: AnActionEvent) {
            project.service<RunKeyscriptService>().openInBrowser()
        }
    }
))
```

## Session-Aware Nudges

`SessionService` fires listener callbacks on login/logout. Hook into these to show context-relevant guidance at exactly the right moment — immediately after the user logs in.

```kotlin
// In WorkspacePanel or KeyscryptProjectService
sessionService.addListener { isLoggedIn ->
    if (isLoggedIn) {
        val props = PropertiesComponent.getInstance()
        if (!props.getBoolean("keyscript.postLoginNudgeShown", false)) {
            props.setValue("keyscript.postLoginNudgeShown", true)
            showRunTipBalloon()
        }
    }
}
```

## Anti-Patterns

### WARNING: Nudging before the user can act

**The Problem:**
```kotlin
// BAD — shows "Open Table Browser" nudge before user has deployed anything
override fun init() {
    showTableBrowserNudge()
}
```

**Why This Breaks:** The Table Browser is empty until a deploy happens. Sending users there before that point wastes attention and erodes trust in notifications. Gate nudges on the triggering action actually completing.

### WARNING: Showing the same nudge repeatedly across sessions

**The Problem:**
```kotlin
// BAD — no persistence, shows on every IDE restart
fun onRunSuccess() {
    showDiagnosticsNudge()
}
```

**Why This Breaks:** Power users see the same tip hundreds of times. Use `PropertiesComponent.getInstance().getBoolean(key, false)` to show each nudge exactly once. See the **intellij-platform** skill for `PropertiesComponent` usage.
