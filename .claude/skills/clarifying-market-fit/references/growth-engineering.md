# Growth Engineering Reference

## Contents
- Growth levers available in a plugin context
- Team adoption patterns
- Auto-detection as a growth mechanism
- Reducing friction at key handoff points

---

## Growth Levers in a Plugin Context

Web-style growth tactics (SEO, paid ads, email) don't apply to IDE plugins. The available levers are:

| Lever | How It Works | Implementation Hook |
|-------|-------------|---------------------|
| Auto-detection | Plugin activates silently when a Keyscript project is opened | `KeyscriptProjectDetector` |
| Word of mouth | One developer installs → team sees the tool window → asks "what's that?" | Tool window visibility in shared screen sessions |
| CHANGELOG release notes | Developers share interesting releases internally | `CHANGELOG.md` with compelling entry copy |
| JetBrains Marketplace reviews | Satisfied users leave ratings | Post-activation notification prompting review |
| Team-scoped settings | Settings apply per-project, inviting team standardization | `KeyscryptProjectConfigurable` |

---

## Auto-Detection as a Growth Mechanism

`KeyscriptProjectDetector` activates the plugin silently when any of these signals are present:

```kotlin
// KeyscriptProjectDetector.kt — detection criteria
fun isKeyscriptProject(project: Project): Boolean {
    val root = project.basePath ?: return false
    return File(root, "keyscript.bundle.json").exists()
        || File(root, ".keyscript").exists()
        || hasKeyscriptFiles(project)
}
```

This is the most powerful growth mechanism in the plugin: a Keyscript project checked into a shared repo automatically activates the tool windows for every team member who opens it. The messaging implication: **the first impression is the empty state, not the README**. Empty-state copy must orient a new user immediately.

```kotlin
// WorkspacePanel.kt — first-impression copy for auto-activated users
private fun createUnconfiguredState(): JPanel {
    return panel {
        row {
            label("Keyscript IDE detected this project.")
                .bold()
        }
        row {
            label("To get started: Settings > Keyscript IDE > enter your Keystone server address.")
        }
        row {
            button("Open Settings") { openSettings() }
        }
    }
}
```

See the **crafting-empty-states** skill for the full range of auto-detected state patterns.

---

## Reducing Friction at Team Handoff Points

The highest-friction handoff in team adoption is the first-time configuration step. A developer hands off the repo to a colleague, who opens it, sees tool windows, and has no idea what values to enter in settings.

Reduce this friction with inline format hints and a README that anticipates the question:

```kotlin
// KeyscryptSettingsConfigurable.kt — format hints at the point of need
row("Keystone Server:") {
    textField()
        .bindText(settings::keystoneServer)
        .comment("Ask your team lead for the Keystone server address.")
}

row("Supported Instances:") {
    textField()
        .bindText(settings::supportedInstances)
        .comment("Comma-separated. Example: Development,Test,Production")
}
```

README setup section should anticipate hand-off context:

```markdown
## Team Setup

When a teammate shares a Keyscript project with you, the plugin activates automatically.

You'll need:
1. The Keystone server address (ask your project lead — typically `hostname:port`)
2. Your Keystone credentials (same as the web interface)

Enter both in **Settings > Keyscript IDE**, then click the status bar widget to log in.
```

---

## Post-Activation Retention Copy

Users who complete first run but don't return are typically missing discovery of Data Tools or Diagnostics. Surface these via notification after first successful script run:

```kotlin
// RunKeyscriptService.kt — after first successful run
if (isFirstRun) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(
            "Script ran successfully",
            "Check the Data Tools panel to browse Keystone tables, or Diagnostics to inspect network traffic.",
            NotificationType.INFORMATION
        ).notify(project)
}
```

See the **orchestrating-feature-adoption** skill for full feature discovery and nudge patterns.
See the **improving-activation-flow** skill for first-run funnel optimization.
