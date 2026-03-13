# Conversion Optimization Reference

## Contents
- Funnel drop-off points
- Reducing friction in authentication
- Settings configuration abandonment
- JCEF preview activation
- Anti-patterns

The Keyscript funnel has four high-friction points: (1) settings configuration, (2) first authentication, (3) first script run, and (4) JCEF preview load. Each has specific code-level levers.

---

## Funnel Drop-off Points

### 1. Settings Configuration Abandonment

Users open Settings > Keyscript IDE but don't save a Keystone server URL. The form has no inline validation or example placeholder.

**Fix:** Add placeholder text and inline validation in `KeyscryptSettingsConfigurable.kt`:

```kotlin
// KeyscryptSettingsConfigurable.kt
keystoneServerField.emptyText.text = "e.g. keystonedev.acme.com:8443"

// Validate on Apply — surface error before saving
override fun isModified(): Boolean {
    val url = keystoneServerField.text.trim()
    if (url.isNotBlank() && !url.contains(":")) {
        Messages.showErrorDialog("Include the port (host:port)", "Invalid Server Address")
        return false
    }
    return url != settings.keystoneServer
}
```

### 2. Authentication Drop-off

Users who see "KS: Not Logged In" in the status bar but never click it. The widget text is ambiguous — it doesn't signal what to do.

**Fix:** Change idle widget text in `LoginStatusBarWidget.kt`:

```kotlin
// BAD — passive, no call to action
"KS: Not Logged In"

// GOOD — directive
"KS: Click to connect"
```

### 3. First Script Run Friction

The gutter icon only appears on files with `// @keyscript` or `*.keyscript.js`. New users don't know this and see no run button.

**Fix in `KeyscryptRunLineMarkerContributor.kt`:** Expand detection to also show the gutter icon on any file in a project where `keyscript.bundle.json` exists, even without the file-level marker.

```kotlin
override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    val file = element.containingFile ?: return null
    val project = element.project
    val detector = project.service<KeyscryptProjectDetector>()
    // Show gutter if project-level detection passes, even without file marker
    if (!detector.isKeyscriptProject) return null
    // ... rest of existing logic
}
```

---

## DO / DON'T

| | Pattern | Why |
|-|---------|-----|
| DO | Show the Session tab immediately on project open | Reduces the discovery gap — users see their connection state without hunting |
| DON'T | Silently fail proxy startup | Users blame the IDE, not the port conflict. Show a balloon notification with the specific error |
| DO | Pre-fill the instance dropdown with values from `supportedInstances` | Eliminates a manual typing step in the run config dialog |
| DON'T | Require login before showing any tool window content | Show tool windows with a "Connect to get started" empty state instead |

---

## Anti-Pattern: Hiding Empty States Behind Login

**The Problem:**
Tool windows that render nothing until authenticated create a blank-panel experience. Users assume the plugin is broken.

**The Fix:**
Render a `KeyscryptEmptyStatePanel` with a login CTA. See the **crafting-empty-states** skill.

```kotlin
// WorkspacePanel.kt
fun buildContent(): JComponent {
    val session = project.service<SessionService>()
    return if (session.isLoggedIn) buildWorkspaceContent()
    else buildConnectPrompt()  // shows login button + server status
}
```

---

## Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| Proxy fails to start silently | Port 3000 in use; exception swallowed | Catch `BindException` in `ProxyServerService`, show balloon with port number |
| Session expires mid-session with no feedback | Heartbeat fails but UI doesn't update | `SessionService` should notify `LoginStatusBarWidget` listener on heartbeat failure |
| Deploy fails with no error message | `DeploymentService` catches `Exception` without propagating | Log + show notification with cause |
