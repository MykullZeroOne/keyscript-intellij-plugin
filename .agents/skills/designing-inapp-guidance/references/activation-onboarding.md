# Activation Onboarding Reference

## Contents
- First-Run Detection
- Configuration Gate
- Login Gate
- Notification Registration
- Anti-Patterns

---

## First-Run Detection

`KeyscryptProjectService` runs at project open for every Keyscript project. It is the right place to fire a one-time first-run balloon. Use `PropertiesComponent` to persist the "shown" flag per-installation (not per-project — the user sees it once ever).

```kotlin
// In KeyscryptProjectService.kt — init or postStartupActivity
private fun maybeShowFirstRunTip() {
    val props = PropertiesComponent.getInstance()
    if (props.getBoolean("keyscript.firstRunTipShown", false)) return
    props.setValue("keyscript.firstRunTipShown", true)

    ApplicationManager.getApplication().invokeLater {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Keyscript IDE is ready",
                "Configure your Keystone server in <b>Settings &gt; Keyscript IDE</b>, then click the status bar widget to log in.",
                NotificationType.INFORMATION
            )
            .addAction(NotificationAction.createSimple("Open Settings") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(null, KeyscryptSettingsConfigurable::class.java)
            })
            .notify(project)
    }
}
```

## Configuration Gate

Before showing any data-dependent UI, check `KeyscryptSettings.isConfigured()`. A missing server URL means every downstream call will fail silently — do not proceed.

```kotlin
// Pattern used in tool window factories
private fun KeyscryptSettings.isConfigured(): Boolean =
    keystoneServer.isNotBlank() && supportedInstances.isNotBlank()

fun createPanel(): JComponent {
    if (!KeyscryptSettings.getInstance().isConfigured()) {
        return buildUnconfiguredHint(
            message = "Set your Keystone server in Settings > Keyscript IDE to get started.",
            actionLabel = "Open Settings",
            action = { ShowSettingsUtil.getInstance().showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java) }
        )
    }
    // ... build real content
}
```

## Login Gate

`SessionService` exposes `isLoggedIn`. Check it before making any Keystone API calls and show a login prompt instead of a blank panel.

```kotlin
// SessionPanel.kt already does this — mirror the pattern in other panels
private fun buildSessionAwareContent(): JComponent {
    val session = project.service<SessionService>()
    return if (!session.isLoggedIn) {
        JPanel(BorderLayout()).apply {
            add(JLabel("Not logged in").apply {
                horizontalAlignment = SwingConstants.CENTER
            }, BorderLayout.CENTER)
            add(JButton("Log In").apply {
                addActionListener { project.service<AuthenticationService>().showLoginDialog() }
            }, BorderLayout.SOUTH)
        }
    } else {
        buildContentPanel()
    }
}
```

## Notification Registration

All notifications must be registered in `plugin.xml` before use. Missing registration causes a runtime exception — the notification silently disappears.

```xml
<!-- META-INF/plugin.xml -->
<extensions defaultExtensionNs="com.intellij">
    <notificationGroup id="Keyscript IDE"
                       displayType="BALLOON"
                       isLogByDefault="true"/>
</extensions>
```

## Anti-Patterns

### WARNING: Showing first-run guidance on every project open

**The Problem:**
```kotlin
// BAD — fires the balloon every time the project opens
override fun runActivity(project: Project) {
    showFirstRunBalloon(project)
}
```

**Why This Breaks:** Users with multiple projects see the balloon on every switch. It becomes noise within minutes and trains them to ignore all notifications from the plugin.

**The Fix:** Gate on `PropertiesComponent` as shown above. Use `getInstance()` (global) not `getInstance(project)` — the user only needs onboarding once, not per project.

### WARNING: Navigating to Settings without checking if already configured

**The Problem:**
```kotlin
// BAD — always shows "configure settings" even after configuration is complete
addAction(NotificationAction.createSimple("Configure") { openSettings() })
```

**Why This Breaks:** Users who configured the plugin yesterday see a condescending "configure it" prompt every run. Re-check `isConfigured()` before offering configuration actions and suppress the notification if it's already done.
