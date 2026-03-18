# Conversion Optimization Reference

## Contents
- Tier Conversion Touch Points
- Gating Patterns in Kotlin
- Anti-Patterns
- Upgrade Prompt Placement

---

## Tier Conversion Touch Points

The plugin has three conversion moments worth optimizing:

1. **Install → Activation**: User installs but hasn't configured a Keystone server
2. **Community → Ultimate**: User hits JCEF preview or JS tooling; shown the gate
3. **Dev → Production instance**: User is ready to deploy; needs Production access

Each has a different audience and a different message.

---

## Gating Patterns in Kotlin

### Hard Gate (Feature Completely Unavailable)

```kotlin
// src/main/kotlin/com/keyscript/plugin/preview/KeyscriptSplitEditorProvider.kt
override fun accept(file: VirtualFile): Boolean {
    if (!isUltimateEdition()) {
        // Don't register the split editor at all — no confusing disabled state
        return false
    }
    return file.name.endsWith(".keyscript.js") || file.name.endsWith(".js")
}
```

Hard gates prevent Community users from seeing a broken/disabled UI. Never show a greyed-out button without an explanation — users assume it's a bug.

### Soft Gate (Feature Visible, But Prompts Upgrade)

```kotlin
// In DataToolsPanel — show the panel but prompt for Production-tier access
private fun buildQueryBuilderTab(): JComponent {
    val instance = KeyscriptSettings.getInstance().getCurrentInstance()
    return if (instance == "Production") {
        QueryBuilderPanel(project)
    } else {
        JPanel(BorderLayout()).apply {
            add(JLabel(
                "<html><b>Query Builder</b> is available on Production instances.<br>" +
                "Switch your instance in the Session tab to continue.</html>"
            ), BorderLayout.CENTER)
        }
    }
}
```

Soft gates educate without blocking — users understand the ladder, not just the wall.

### Notification-Based Prompt

```kotlin
// Trigger once after first run, if instance is Development
fun promptInstanceUpgrade(project: Project) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Ready to test on a higher environment?",
            "Switch to the Test or Production instance in the Workspace panel to deploy your script.",
            NotificationType.INFORMATION
        )
        .notify(project)
}
```

---

## Anti-Patterns

### WARNING: Silent Feature Removal

**The Problem:**
```kotlin
// BAD — gating by returning null with no explanation
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    if (!isUltimateEdition()) return  // Tool window simply never shows up
}
```

**Why This Breaks:**
1. Users report it as a bug — "Data Tools disappeared"
2. Zero conversion signal; you never know why they churned
3. Community users who would upgrade never get the prompt

**The Fix:**
```kotlin
// GOOD — show a tier panel explaining what they're missing
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val content = if (isUltimateEdition()) {
        DataToolsPanel(project)
    } else {
        TierUpgradePanel("Data Tools require IntelliJ IDEA Ultimate")
    }
    toolWindow.contentManager.addContent(
        toolWindow.contentManager.factory.createContent(content, "", false)
    )
}
```

---

## Upgrade Prompt Placement

Place upgrade prompts at the **moment of intent**, not on a dashboard:

| Moment | Location | Message |
|--------|----------|---------|
| User clicks Run on a `.keyscript.js` file | Notification balloon | "Install complete — configure your Keystone server to run scripts" |
| User opens split editor (Community) | Panel empty state | "Live preview requires IntelliJ IDEA Ultimate" |
| User selects Production instance for first time | Session tab tooltip | "Production deployments are permanent — verify on Test first" |

See the **designing-inapp-guidance** skill for balloon and tooltip implementation patterns.
See the **crafting-empty-states** skill for building the upgrade panel UI component.
