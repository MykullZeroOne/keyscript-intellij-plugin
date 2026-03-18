# In-App Guidance Reference

## Contents
- Guidance Surfaces Available
- Status Bar Widget Text
- Notification-Based Guidance
- Empty State Panels
- Tooltip & Inline Hints
- Anti-Patterns

The plugin currently has one piece of in-app guidance: the status bar widget text "KS: Not Logged In". Everything else — settings required, how to run a script, what the Data Tools do — is undocumented in-product. This reference covers patterns for adding guidance without cluttering the IDE.

---

## Guidance Surfaces Available

| Surface | Implementation | When to Use |
|---------|---------------|------------|
| Status bar widget text | `LoginStatusBarWidget.getText()` | Persistent state indicator |
| Status bar tooltip | `LoginStatusBarWidget.getTooltipText()` | Expanded state explanation |
| Balloon notification | `NotificationGroupManager` | One-time events (login success, session expire) |
| Tool window empty state | Custom `JPanel` in `createToolWindowContent` | When panel has no data to show |
| Dialog inline labels | `JBLabel` in `FormBuilder` | Field-level hints in settings/login |
| Action presentation text | `e.presentation.text` | Context-sensitive action labels |

---

## Status Bar Widget Text

The widget already adapts its text based on login state. Extend it to provide clearer guidance:

```kotlin
// Current — in LoginStatusBarWidget
override fun getText(): String {
    return if (session.isLoggedIn) {
        "KS: ${session.username} | ${session.instance}"
    } else {
        "KS: Not Logged In"  // Could be more actionable
    }
}

// Better — make the unconfigured state obvious
override fun getText(): String {
    val settings = KeyscryptSettings.getInstance()
    return when {
        settings.proxyEndpoint == "keystonedev.revfcu.com:8443" -> "KS: Setup Required"
        session.isLoggedIn -> "KS: ${session.username} | ${session.instance}"
        else -> "KS: Click to Login"
    }
}

// Better tooltip for the unconfigured case
override fun getTooltipText(): String {
    val settings = KeyscryptSettings.getInstance()
    return when {
        settings.proxyEndpoint == "keystonedev.revfcu.com:8443" ->
            "Keyscript: Configure your Keystone server in Settings > Keyscript IDE"
        session.isLoggedIn ->
            "Keyscript: Logged in as ${session.username} on ${session.instance}\nClick to manage session"
        else ->
            "Keyscript: Click to log in to Keystone"
    }
}
```

---

## Notification-Based Guidance

Use `NotificationGroupManager` for transient guidance. The `"Keyscript"` notification group is already registered in `plugin.xml`.

```kotlin
// One-time setup prompt on first project open (if settings appear unconfigured)
private fun showSetupPromptIfNeeded(project: Project) {
    val settings = KeyscryptSettings.getInstance()
    if (settings.proxyEndpoint.contains("keystonedev.revfcu.com")) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification(
                "Keyscript IDE — Setup Required",
                "Configure your Keystone server before logging in.",
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimple("Open Settings") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
            })
            .notify(project)
    }
}
```

**DO:** Use `NotificationType.INFORMATION` for success events, `WARNING` for recoverable states (session expired, unconfigured settings), `ERROR` only for fatal failures (proxy port in use).

**DON'T:** Show the setup prompt on every project open. Persist a "setup seen" flag in `KeyscryptSettings` and only show it once.

---

## Empty State Panels

Tool windows that render blank panels when data is missing are a silent failure. Add empty states as `JPanel` replacements for the real content.

```kotlin
// Reusable empty state factory
fun buildEmptyStatePanel(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
): JPanel {
    val titleLabel = JBLabel("<html><b>$title</b></html>").apply {
        horizontalAlignment = SwingConstants.CENTER
    }
    val descLabel = JBLabel("<html><center>$description</center></html>").apply {
        horizontalAlignment = SwingConstants.CENTER
        foreground = UIUtil.getLabelDisabledForeground()
    }
    return JPanel(BorderLayout()).apply {
        val center = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(Box.createVerticalGlue())
            add(titleLabel.also { it.alignmentX = Component.CENTER_ALIGNMENT })
            add(Box.createRigidArea(Dimension(0, 8)))
            add(descLabel.also { it.alignmentX = Component.CENTER_ALIGNMENT })
            if (actionLabel != null && onAction != null) {
                add(Box.createRigidArea(Dimension(0, 12)))
                add(JButton(actionLabel).apply {
                    alignmentX = Component.CENTER_ALIGNMENT
                    addActionListener { onAction() }
                })
            }
            add(Box.createVerticalGlue())
        }
        add(center, BorderLayout.CENTER)
        border = JBUI.Borders.empty(16)
    }
}

// Usage in a tool window when not logged in
val content = if (!session.isLoggedIn) {
    buildEmptyStatePanel(
        title = "Not Connected",
        description = "Log in to Keystone to browse tables.",
        actionLabel = "Login",
        onAction = { ActionManager.getInstance().getAction("Keyscript.Login")?.... }
    )
} else {
    TableBrowserPanel(project)
}
```

---

## Tooltip & Inline Hints

The `LoginDialog` already uses `toolTipText` on the Device ID field. Apply this pattern to all non-obvious fields:

```kotlin
// Login dialog — Device ID field (already has a tooltip)
private val deviceIdField = JBTextField(settings.deviceServiceUrl).apply {
    toolTipText = "e.g. MAC: AA-BB-CC-DD-EE-FF"
}

// Settings configurable — add inline help text below fields
FormBuilder.createFormBuilder()
    .addLabeledComponent(JBLabel("Keystone Server:"), proxyEndpointField)
    .addComponentToRightColumn(
        JBLabel("Format: hostname:port (e.g. keystonedev.example.com:8443)").apply {
            foreground = UIUtil.getLabelDisabledForeground()
            font = font.deriveFont(font.size - 1f)
        }
    )
    .panel
```

---

## Anti-Patterns

### WARNING: Using ERROR notifications for warnings

**The Problem:**
```kotlin
// BAD — session expiry is recoverable; ERROR level is alarming
notify("Session expired", NotificationType.ERROR)
```

**Why This Breaks:** IntelliJ ERROR notifications appear in the event log with a red badge and persist. Session expiry is recoverable via auto-relogin — it warrants a WARNING at most.

**The Fix:** Match notification type to severity: `INFORMATION` → `WARNING` → `ERROR`. Session expiry = WARNING. Proxy port conflict = ERROR.

### WARNING: Guidance text that refers to menu paths that don't exist

**The Problem:** Telling users "Go to Tools > Keyscript > Login" when the action is only in the status bar or right-click context menu. Users follow instructions literally.

**The Fix:** Verify all menu paths exist in `plugin.xml` before writing guidance copy. Use `ActionManager.getInstance().getAction("Keyscript.Login")` checks in code to confirm the action ID exists before referencing it.
