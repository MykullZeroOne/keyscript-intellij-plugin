# In-App Guidance Reference

## Contents
- Available Guidance Surfaces
- Notification Actions (Actionable Balloons)
- Inline Help Text in Settings
- Status Bar Tooltip Enhancement
- Tool Window Subtitles
- Anti-Patterns

---

## Available Guidance Surfaces

| Surface | API | Best For |
|---------|-----|----------|
| Balloon notification | `NotificationGroupManager` | One-time nudges with CTA button |
| Status bar tooltip | `StatusBarWidget.TextPresentation.getTooltipText()` | Persistent contextual hint |
| Tool window subtitle | `createToolWindowShell(subtitle=...)` | Panel purpose description |
| Form field hint | `JBLabel` with `UIUtil.getContextHelpForeground()` | Settings explanation |
| Empty state label | `JBLabel` inside panel | Data-dependent guidance |
| Field placeholder | `JTextField.emptyText.text` | Input format hints |

The `"Keyscript"` notification group is registered in `plugin.xml` (line 85) as `BALLOON` type.
All nudges MUST use this group — do not create additional notification groups.

---

## Notification Actions (Actionable Balloons)

The most effective guidance pattern: balloon + action button. Already used for session expiry.

```kotlin
// Pattern: notification with a single focused action
fun notifyWithAction(project: Project, message: String, actionLabel: String, action: () -> Unit) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript IDE", message, NotificationType.INFORMATION)
        .addAction(NotificationAction.createSimple(actionLabel, action))
        .notify(project)
}

// Usage — nudge to open settings after detecting blank server config
notifyWithAction(project,
    message = "Keystone server not configured.",
    actionLabel = "Open Settings"
) {
    ShowSettingsUtil.getInstance()
        .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
}
```

---

## Inline Help Text in Settings

`KeyscryptSettingsConfigurable` has six fields with no explanatory text. The `KeyscryptProjectConfigurable`
shows a good pattern with an HTML hint label — replicate it in the application settings.

```kotlin
// Good pattern already in KeyscriptProjectConfigurable.kt:
val hint = JLabel("<html>When enabled, Keyscript tool windows, completions, status bar, and " +
    "run configurations will be active.<br><br>" +
    "This creates a <code>.keyscript</code> marker file in the project root.</html>")
hint.foreground = UIUtil.getContextHelpForeground()

// Replicate in KeyscryptSettingsConfigurable — add after endpoint field:
val endpointHint = JLabel("<html>The Keystone proxy endpoint (host:port). " +
    "Ask your Keystone admin for the correct value.</html>")
endpointHint.foreground = UIUtil.getContextHelpForeground()
endpointHint.font = JBUI.Fonts.smallFont()
```

---

## Status Bar Tooltip Enhancement

The current tooltip in `LoginStatusBarWidgetFactory.kt` is functional. On the logged-out state,
it should tell users exactly what to do next, not just that they can click.

```kotlin
// Current (vague):
"Keyscript: Click to log in to Keystone"

// Better (directive — tells them the prereq):
override fun getTooltipText(): String = if (session.isLoggedIn) {
    "Keyscript: ${session.username} on ${session.instance} | Click to manage session"
} else {
    "Keyscript: Not logged in\nConfigure server in Settings > Keyscript IDE, then click to login"
}
```

---

## Tool Window Subtitles

`createToolWindowShell` in `KeyscryptToolWindowUi.kt` already supports subtitles. Existing
subtitles are good. Ensure any new tool window panels use this pattern:

```kotlin
// ALWAYS use createToolWindowShell for new panels — never create raw JPanel wrappers
component = createToolWindowShell(
    title = "My New Panel",
    subtitle = "One sentence: what this panel does and when to use it.",
    content = myContent
)
```

**DO:** Keep subtitles under 120 characters. They render in `JBFont.small()`.
**DON'T:** Use subtitle as an error message — it's static, not reactive.

---

## Anti-Patterns

### WARNING: Using `JOptionPane` for in-plugin guidance

**The Problem:** Modal dialogs block the IDE entirely.
**Why This Breaks:** Users lose context, can't copy values from other windows, and the IDE
feels broken. IntelliJ plugins must never use `JOptionPane`.
**The Fix:** Use `NotificationGroupManager` for non-blocking feedback, or `DialogWrapper`
for intentional multi-step flows (like the Login dialog).

### WARNING: Hiding guidance behind a "?" help button

**The Problem:** Users don't click help buttons — discovery is passive, not active.
**Why This Breaks:** The users who need guidance most (new users) are least likely to know
they need to look for it.
**The Fix:** Render help text inline using `UIUtil.getContextHelpForeground()` — visible by
default, visually subordinate so power users aren't distracted.
