# In-App Guidance Reference

## Contents
- Guidance Surfaces Available in IntelliJ Plugins
- Inline Hint Text in Settings (Existing)
- Tool Window Header Subtitles (Existing)
- Contextual Notifications
- WARNING: Tooltip Abuse
- Getting Started Panel

---

## Guidance Surfaces Available in IntelliJ Plugins

| Surface | When to Use | IntelliJ API |
|---------|-------------|--------------|
| Balloon notification | One-time events (login success, session expiry) | `NotificationGroupManager` |
| Tool window empty state | When prerequisites aren't met | Custom `JPanel` in tool window content |
| Settings hint label | Explain config fields | `JBLabel` with HTML in `Configurable` panel |
| Status bar tooltip | Persistent state indicator | `getTooltipText()` in widget |
| Inline action hint | Dismissible banner in panel | `JPanel` + `ActionLink("Dismiss")` |
| Gutter icon tooltip | File-level context | `getLineMarkerInfo()` tooltip |

Avoid modal dialogs for guidance — they block the IDE and users dismiss without reading.

---

## Inline Hint Text in Settings (Existing)

`KeyscryptProjectConfigurable` already uses HTML hint labels — follow this pattern for all
settings fields that need explanation:

```kotlin
// src/.../settings/KeyscryptProjectConfigurable.kt
val hint = JLabel("<html>When enabled, Keyscript tool windows, completions, status bar, " +
    "and run configurations will be active.<br><br>" +
    "This creates a <code>.keyscript</code> marker file in the project root.<br>" +
    "Auto-detected if the project contains <code>keyscript.bundle.json</code> or " +
    "<code>*.keyscript.js</code> files.</html>").apply {
    foreground = UIUtil.getContextHelpForeground()
    font = JBFont.small()
    border = JBUI.Borders.empty(4, 0, 0, 0)
}
```

Add similar hints to `KeyscryptSettingsConfigurable` for the `keystoneServer` and `supportedInstances`
fields — users routinely misconfigure these because the expected format is not obvious.

```kotlin
// Add below the keystoneServer field
val serverHint = JLabel("<html>Hostname and port only — no https:// prefix.<br>" +
    "Example: <code>keystonedev.example.com:8443</code></html>").apply {
    foreground = UIUtil.getContextHelpForeground()
    font = JBFont.small()
}
```

---

## Tool Window Header Subtitles (Existing)

Every tool window already uses `createToolWindowShell()` from `KeyscryptToolWindowUi`:

```kotlin
// src/.../toolwindow/KeyscryptWorkspaceToolWindowFactory.kt
createToolWindowShell(
    title = "Keyscript Workspace",
    subtitle = "Manage script run options and session authentication",
    content = tabbedPane
)
```

The subtitle is rendered in `UIUtil.getContextHelpForeground()` with small font — ideal for
one-line explanations of what each panel does. Keep subtitles under 60 characters.

**DO** update the Data Tools subtitle to name the tabs explicitly:
```kotlin
subtitle = "Search scripts, browse tables, and build Keystone queries"
```

---

## Contextual Notifications

The `NotificationGroupManager` pattern (already established in `AuthenticationService`) supports
three notification types with distinct visual treatment:

```kotlin
// INFORMATION — blue, non-urgent (use for feature discovery, first-run tips)
NotificationType.INFORMATION

// WARNING — yellow, action required (use for session expiry, config issues)
NotificationType.WARNING

// ERROR — red, blocking issue (use for login failure, proxy startup failure)
NotificationType.ERROR
```

Add actions to notifications to make them actionable:

```kotlin
.addAction(NotificationAction.createSimple("Open Data Tools") {
    ToolWindowManager.getInstance(project).getToolWindow("Keyscript Data Tools")?.show()
})
```

---

## WARNING: Tooltip Abuse

**The Problem:**

```kotlin
// BAD — putting all guidance in tooltips
component.toolTipText = "This field requires the Keystone server hostname without https://, " +
    "e.g. keystonedev.example.com:8443. The port is required. Leave blank to disable..."
```

**Why This Breaks:**
1. Tooltips require hover — users never discover them until they're already confused
2. Long tooltip text is truncated or wraps awkwardly in IntelliJ's tooltip renderer
3. Tooltips disappear; inline hint labels persist and are always visible

**The Fix:** Use `JBLabel` hint text below fields for non-obvious configuration. Reserve tooltips
for single-sentence clarifications of icon buttons.

---

## Getting Started Panel

Add a "Getting Started" tab to `KeyscryptWorkspaceToolWindowFactory` for new users. Remove it
(or mark it complete) once `completedFirstRun` is true:

```kotlin
private fun buildGettingStartedPanel(project: Project): JComponent {
    val onboarding = project.service<OnboardingStateService>()
    if (onboarding.state.completedFirstRun) return JPanel() // collapsed/hidden

    val steps = listOf(
        "Configure server endpoint" to { ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java) },
        "Log in to Keystone" to { triggerLoginAction(project) },
        "Open a .keyscript.js file and click the gutter ▶ button" to null,
        "Explore tables in Data Tools → Table Browser" to {
            ToolWindowManager.getInstance(project).getToolWindow("Keyscript Data Tools")?.show()
        }
    )

    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(12)
        steps.forEachIndexed { i, (label, action) ->
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JBLabel("${i + 1}. $label"))
                action?.let { add(ActionLink("→") { it() }) }
            })
        }
    }
}
```

See the **intellij-platform** skill for `ToolWindowManager` usage and service resolution patterns.
