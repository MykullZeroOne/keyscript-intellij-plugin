# Conversion Optimization Reference

## Contents
- Funnel Stages and Drop-off Points
- Reducing Configuration Friction
- CTA Patterns in Plugin UI
- Anti-Patterns

---

## Funnel Stages and Drop-off Points

The Keyscript install-to-value funnel has five measurable stages:

| Stage | Trigger | Drop-off Cause |
|-------|---------|---------------|
| **Discovery** | JetBrains Marketplace search | Weak description, no screenshots |
| **Install** | Click "Install" | Unclear compatibility requirements |
| **Configure** | Settings > Keyscript IDE | No guidance on what "Keystone Server" means |
| **Authenticate** | Status bar click | Widget text doesn't communicate action needed |
| **First run** | Gutter icon or Ctrl+Shift+F10 | File not recognized as Keyscript (missing markers) |

Each stage requires deliberate copy and UI choices to move users forward.

---

## Reducing Configuration Friction

### Pattern: Post-install notification with direct settings link

When users install and open a project but haven't configured a Keystone server, show a dismissible banner rather than waiting for them to discover Settings.

```kotlin
// In KeyscryptProjectService — check on project open
private fun promptConfigurationIfNeeded(project: Project) {
    val settings = KeyscryptSettings.instance
    if (settings.keystoneServer.isBlank()) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Keyscript IDE: Setup Required",
                "Enter your Keystone server address to enable script runs and preview. " +
                "<a href=\"open-settings\">Configure now →</a>",
                NotificationType.WARNING
            )
            .setListener { notification, event ->
                if (event.description == "open-settings") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
                    notification.expire()
                }
            }
            .notify(project)
    }
}
```

### Pattern: Inline validation feedback in settings UI

Users who type an invalid Keystone URL won't know it's wrong until the proxy fails. Add inline validation:

```kotlin
// In KeyscryptSettingsConfigurable — validate on apply
override fun isModified(): Boolean {
    val server = serverField.text.trim()
    val isValidFormat = server.matches(Regex("""^[\w.-]+(:\d+)?$"""))
    serverField.putClientProperty("JComponent.outline", if (isValidFormat || server.isEmpty()) null else "error")
    return server != settings.keystoneServer
}
```

---

## CTA Patterns in Plugin UI

### DO: Make the status bar widget actionable

```kotlin
// GOOD — tells users exactly what action they're taking
class LoginStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    override fun getTooltipText(): String {
        val server = KeyscryptSettings.instance.keystoneServer
        return if (server.isBlank())
            "Keyscript: Configure server in Settings first"
        else
            "Keyscript: Click to authenticate with $server"
    }

    override fun getClickConsumer() = Consumer<MouseEvent> { showLoginDialog() }
}
```

```kotlin
// BAD — passive, gives no instruction
override fun getTooltipText() = "Not logged in"
```

**Why this matters:** A passive status bar widget looks like a status indicator, not a CTA. Users assume it updates automatically and don't know to click it.

### DO: Use "Configure" not "Settings"

In notification copy and tooltips, "Configure" signals an action. "Settings" is a location. Users click to do things, not to navigate.

```kotlin
// GOOD
"<a href=\"configure\">Configure Keystone server →</a>"

// BAD
"<a href=\"settings\">Open Settings</a>"
```

---

## Anti-Patterns

### WARNING: Silent auto-detection failure

**The Problem:**

```kotlin
// BAD — project just doesn't activate; no feedback
fun isKeyscryptProject(project: Project): Boolean {
    return VfsUtil.findFile(Path.of(project.basePath ?: return false, "keyscript.bundle.json"), false) != null
}
```

**Why This Breaks:**
1. Users with an existing Keyscript project that doesn't have `keyscript.bundle.json` at root get zero feedback
2. They assume the plugin isn't working or isn't installed
3. There's no path to manual enable — they don't know `Settings > Languages & Frameworks > Keyscript IDE` exists

**The Fix:**

```kotlin
// GOOD — show a "Did you mean to enable this?" prompt for ambiguous projects
fun checkAndSuggestActivation(project: Project) {
    if (!isKeyscryptProject(project) && hasKeyscryptLikeContent(project)) {
        // Project has .js files but no markers — offer to enable
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Keyscript project detected?",
                "This project contains JavaScript files. " +
                "<a href=\"enable\">Enable Keyscript IDE support</a> or " +
                "<a href=\"dismiss\">dismiss</a>.",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
```

### WARNING: 8-step first-run with no progress indicator

The current first-run requires 8 discrete steps before a script runs. There's no completion indicator. Users who stop at step 5 don't know they're close.

The fix is not to add a wizard (high cost) but to make each step point to the next:
- Settings panel should show "Next: Enable for this project →" after saving server config
- Project settings should show "Next: Click 'KS: Not Logged In' to authenticate" after enabling

See the **designing-onboarding-paths** skill for the full first-run wizard pattern.

---

## Workflow: Conversion Audit Checklist

Copy this checklist when auditing a funnel stage:

- [ ] Read `src/main/resources/META-INF/plugin.xml` — does description answer "who is this for"?
- [ ] Read `README.md` lines 1-10 — does hero copy name the specific benefit?
- [ ] Check `KeyscryptSettingsConfigurable.kt` — does every field have placeholder text explaining format?
- [ ] Check `LoginStatusBarWidget*.kt` — does tooltip explain what clicking does?
- [ ] Check `*Panel.kt` tool windows — do empty states explain why they're empty and what action fills them?
- [ ] Verify the status bar widget registers `getClickConsumer()` — non-clickable widgets are invisible CTAs
