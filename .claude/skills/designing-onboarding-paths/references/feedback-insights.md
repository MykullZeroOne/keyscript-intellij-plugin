# Feedback & Insights Reference

## Contents
- Feedback Collection in an IDE Plugin
- Diagnostics Panel as Self-Service Support
- Support Info Dump Pattern
- In-Plugin Feedback Action
- Using Jira for Onboarding Issue Triage
- Common Onboarding Failure Modes

---

## Feedback Collection in an IDE Plugin

An IntelliJ plugin has no form submissions, no user sessions in the traditional sense, and no
in-app chat. Feedback surfaces are limited to:

| Channel | When | How to Instrument |
|---------|------|-------------------|
| idea.log | Always | `Logger.getInstance(...)` — already used throughout |
| Diagnostics Console tab | When plugin is active | `DiagnosticsPanel` — already exists |
| Network Monitor tab | During script runs | Already captures request/response |
| Manual "Copy Support Info" button | On request | New: dump state + logs to clipboard |
| Notification with "Report Issue" link | On errors | Add to `NotificationType.ERROR` notifications |

---

## Diagnostics Panel as Self-Service Support

The existing `DiagnosticsToolWindow` (Console + Network tabs) is the primary support surface.
Add a "Support Info" section to help users self-diagnose and share context when reporting issues:

```kotlin
// In DiagnosticsPanel, add a third tab: "Support Info"
private fun buildSupportInfoPanel(project: Project): JComponent {
    val settings = KeyscryptSettings.getInstance()
    val session = SessionService.getInstance(project)
    val onboarding = project.service<OnboardingStateService>()

    val info = """
        === Keyscript IDE Support Info ===
        Plugin version: ${PluginManagerCore.getPlugin(PluginId.getId("com.keyscript.plugin"))?.version}
        IDE version:    ${ApplicationInfo.getInstance().fullVersion}
        Server:         ${if (settings.keystoneServer.isBlank()) "(not configured)" else "(configured)"}
        Logged in:      ${session.isLoggedIn}
        Instance:       ${session.instance.ifBlank { "(none)" }}
        Proxy running:  ${ProxyServerService.getInstance(project).isRunning}

        Funnel:
          configured:     ${settings.keystoneServer.isNotBlank()}
          loggedIn:       ${onboarding.state.completedFirstLogin}
          firstRun:       ${onboarding.state.completedFirstRun}
    """.trimIndent()

    return JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(12)
        add(JScrollPane(JTextArea(info).apply { isEditable = false }), BorderLayout.CENTER)
        add(JButton("Copy to Clipboard").apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(info), null)
            }
        }, BorderLayout.SOUTH)
    }
}
```

---

## Support Info Dump Pattern

When surfacing diagnostic info, always redact sensitive values — show presence, not content:

```kotlin
// GOOD — shows whether configured without exposing the value
"Server: ${if (settings.keystoneServer.isBlank()) "(not configured)" else "(configured)"}"

// BAD — exposes internal hostname
"Server: ${settings.keystoneServer}"
```

---

## In-Plugin Feedback Action

Add a "Report Issue" notification action to `NotificationType.ERROR` notifications:

```kotlin
private fun notify(message: String, type: NotificationType) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript", message, type)

    if (type == NotificationType.ERROR) {
        // Link to internal issue tracker — replace with actual URL
        notification.addAction(NotificationAction.createSimple("Report Issue") {
            BrowserUtil.browse("https://your-jira-instance/issues")
        })
    }
    notification.notify(project)
}
```

Use the **atlassian:triage-issue** skill to check for duplicate issues before filing new ones.

---

## Using Jira for Onboarding Issue Triage

When a user reports "I couldn't figure out how to get started", file it with:

- **Component**: Plugin / Onboarding
- **Labels**: `ux`, `onboarding`, `first-run`
- **Description**: Which step they got stuck at, what they expected vs what happened
- **Steps to reproduce**: From fresh install with no prior config

Use the **atlassian:triage-issue** skill to search for duplicates before creating new tickets.
Use the **atlassian:spec-to-backlog** skill to convert the onboarding backlog into Jira epics.

---

## Common Onboarding Failure Modes

Based on the current plugin's architecture, these are the most likely user confusion points:

| Failure | Root Cause | Fix |
|---------|-----------|-----|
| "Tool windows don't appear" | Project not detected as Keyscript | Add empty-state or notification if `.keyscript.js` files exist but marker is missing |
| "Login does nothing" | Server not configured | Empty-state in SessionPanel must check server config, not just login state |
| "Run button not visible" | File not recognized as Keyscript | Improve gutter icon: show tooltip "Add `// @keyscript` to enable run" |
| "Proxy errors on first run" | Port 3000 in use | Surface port conflict error with instructions to change `proxyPort` in settings |
| "Preview is blank" | Server endpoint wrong format (includes `https://`) | Add validation/hint in `keystoneServer` field |

Each of these maps to a specific code location — use the **mapping-user-journeys** skill to
trace the full code path for each failure mode before implementing fixes.
