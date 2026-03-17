# Engagement & Adoption Reference

## Contents
- Post-Activation Surfaces
- Session Panel & Workspace Tool Window
- Feature Discovery Gaps
- Data Tools Adoption
- Deployment Flow Adoption
- Anti-Patterns

Once a user successfully runs a script (activation), the next challenge is adoption of deeper features: the Data Tools panel (Table Browser, Query Builder), the deploy workflow, and the network diagnostics. These surfaces are invisible until the user discovers them.

---

## Post-Activation Surfaces

After first login, the user has access to three tool windows gated by `isKeyscriptProject`:

| Tool Window | Factory | Key Adoption Barrier |
|-------------|---------|---------------------|
| Workspace (right) | `KeyscryptWorkspaceToolWindowFactory` | No visible entry point if panel opens empty |
| Data Tools (bottom) | `KeyscryptDataToolsToolWindowFactory` | Users don't know Search/Table Browser exist |
| Diagnostics (bottom) | `KeyscryptDiagnosticsToolWindowFactory` | Console only shows output after a run |

```kotlin
// Tool windows only appear in Keyscript projects — this is correct
// But the tool window content has no empty-state messaging to guide new users
class KeyscryptWorkspaceToolWindowFactory : ToolWindowFactory {
    override fun isApplicable(project: Project): Boolean =
        KeyscryptProjectDetector.isKeyscriptProject(project)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Currently renders panels without checking session state
        // A logged-out user sees empty/broken panels with no guidance
    }
}
```

---

## Session Panel & Workspace Tool Window

The `SessionPanel` in the Workspace tool window is the best surface for post-login engagement. It shows the current session user and instance.

**Opportunity:** Add a "What's Next" section to `SessionPanel` after first login:

```kotlin
// In SessionPanel — listen to session changes and show contextual guidance
private fun buildPostLoginPanel(session: SessionService): JPanel {
    return JPanel(BorderLayout()).apply {
        val tips = JBLabel("""<html>
            <b>You're connected!</b><br/>
            • Open a <tt>.keyscript.js</tt> file and click ▶ to run it<br/>
            • Use <b>Data Tools</b> (bottom panel) to browse tables<br/>
            • Press <tt>Ctrl+Alt+K</tt> to deploy the current script
        </html>""")
        add(tips, BorderLayout.CENTER)
    }
}
```

---

## Feature Discovery Gaps

### Data Tools

The Table Browser and Query Builder are powerful but undiscoverable — they live in a bottom panel tab that users don't know to look for.

**Pattern: Add a contextual hint from the Workspace panel after successful run:**

```kotlin
// After RunKeyscryptService fires a success event, show a balloon
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript")
    .createNotification(
        "Script ran successfully",
        "Use the Data Tools panel to browse tables updated by this script.",
        NotificationType.INFORMATION
    )
    .notify(project)
```

### Deploy Action

`DeployAction` is in the menu bar and toolbar but has no gutter icon or keyboard shortcut documented in the UI. Users who come from the Electron IDE expect `Ctrl+Alt+D` (or equivalent).

```kotlin
// actions/DeployAction.kt — ensure the action has a shortcut registered in plugin.xml
// <action id="Keyscript.Deploy" ... >
//     <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt d"/>
// </action>
```

---

## Deployment Flow Adoption

The deploy path is: `DeployAction` → `DeploymentService.deploy()` → `KeystoneApiClient` → Keystone API.

Users who successfully run scripts often don't know deployment is separate. The status bar only shows login state — not deploy state.

**DO:** After deploy succeeds, notify with a toast that includes the deployed script name and instance:

```kotlin
// In DeploymentService after successful deploy:
notify("Deployed ${scriptName} to ${session.instance}", NotificationType.INFORMATION)
```

**DON'T:** Silently succeed. A deploy that shows no feedback trains users to assume it failed and retry, causing duplicate deploys.

---

## Auto-Relogin as Engagement Enabler

`SessionService.handleSessionExpired()` auto-relogins users when heartbeat detects an expired session. This is critical for long sessions (developers who leave the IDE open overnight).

```kotlin
// Heartbeat runs every 2 minutes — lightweight GET to UserLogin
private const val HEARTBEAT_INTERVAL_MS = 2L * 60 * 1000

// Auto-relogin path — only fires if PasswordSafe has saved credentials
private fun handleSessionExpired() {
    val creds = loadCredentials() ?: run {
        notify("Session expired. Please login again.", NotificationType.WARNING)
        return
    }
    // Background thread re-login attempt
    Thread({ authService.login(creds.first, creds.second, savedInstance) }, "keyscript-relogin").start()
}
```

**Adoption impact:** Users who save credentials (the default when login succeeds) never see the login dialog again during a working day. This dramatically reduces abandonment caused by session timeouts. ALWAYS ensure `session.saveCredentials()` is called after successful login.

---

## Anti-Patterns

### WARNING: Rendering tool window content without session check

**The Problem:**
```kotlin
// BAD — renders panels before checking if the user is logged in
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = TableBrowserPanel(project)
    toolWindow.contentManager.addContent(
        toolWindow.contentManager.factory.createContent(panel, "Table Browser", false)
    )
}
```

**Why This Breaks:** The Table Browser makes API calls via `KeystoneApiClient`. If the user isn't logged in, these fail silently or throw — and the user sees a blank panel with no explanation.

**The Fix:**
```kotlin
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val session = SessionService.getInstance(project)
    val content = if (session.isLoggedIn) {
        TableBrowserPanel(project)
    } else {
        buildLoginPromptPanel(project) // links to LoginAction
    }
    toolWindow.contentManager.addContent(
        toolWindow.contentManager.factory.createContent(content, "Table Browser", false)
    )
    // Re-render when session state changes
    session.addListener { refreshContent(project, toolWindow) }
}
```

### WARNING: No feedback on async operations

Any operation that takes > 200ms (login, run, deploy, table fetch) needs a visible loading state. The `LoginDialog` does this correctly with `statusLabel.text = "Logging in..."`. Apply the same pattern everywhere else.
