# Activation & Onboarding Journey

The first-run journey is the highest-risk UX path. A developer who cannot get a script
running in the first session will not return. This document maps every step from plugin
installation to first successful preview and identifies where the journey breaks.

## Journey Map: First Run

```
[Install plugin]
      |
      v
[Open/create project]
      |
      v (KeyscryptProjectDetector scans up to 2 levels for markers)
[Plugin activates?] -- NO --> invisible; status bar widget not shown
      |
      YES
      v
[Status bar shows "KS: Not Logged In"]
      |
      v (user has no idea what to do next; tooltip is the only hint)
[User clicks status bar widget]
      |
      v
[LoginAction.actionPerformed()]
      |
      v (proxy starts lazily here via AuthenticationService → ProxyServerService)
[LoginDialog shown]
      |
      v
[User fills credentials, clicks Login]
      |
      v
[AuthenticationService.login() → POST /UserLogin through proxy]
      |
      v
[SessionService.setSession() → notifyListeners()]
      |
      v
[Status bar updates: "KS: username | instance"]
      |
      v
[User opens .keyscript.js file]
      |
      v (KeyscryptRunLineMarkerContributor fires on first leaf element)
[Gutter icon appears]
      |
      v
[Run config executed → RunKeyscriptService.runScript()]
      |
      v
[ProxyServerService.ensureStarted() — already running from login]
      |
      v
[JCEF split-editor opens, script runs in preview]
```

## Friction Points and Fixes

### 1. Project detection is silent — no feedback when it fails

`KeyscryptProjectDetector` caches the result but never tells the user why the plugin
did not activate. A developer with a `.js` file lacking `// @keyscript` sees no status
bar widget and no error.

**Fix: add a one-time notification on first open of any `.js` file when not detected.**

```kotlin
// In KeyscryptProjectService.kt — init block
if (!KeyscryptProjectDetector.isKeyscriptProject(project)) {
    // Offer a single "Did you mean to use Keyscript?" balloon
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(
            "Keyscript not detected",
            "Add a <code>keyscript.bundle.json</code> or <code>// @keyscript</code> " +
                "marker to activate the Keyscript plugin for this project.",
            NotificationType.INFORMATION
        )
        .notify(project)
}
```

### 2. Settings must be configured before login, but there is no prompt

`KeyscryptSettings` defaults to `keystonedev.revfcu.com:8443`. On a fresh install
pointing at a different server, the first login silently fails with a connection error
because no one told the user to visit **Settings > Keyscript IDE** first.

**WARNING:** Do not skip the settings check and hardcode a fallback URL. Silently
connecting to a wrong server can result in credentials being sent to an unintended
endpoint.

**Fix: validate settings are non-default before showing the LoginDialog.**

```kotlin
// In LoginAction.actionPerformed(), before showing the dialog:
val settings = KeyscryptSettings.getInstance()
if (settings.proxyEndpoint == "keystonedev.revfcu.com:8443") {
    val go = Messages.showYesNoDialog(
        project,
        "Keyscript is using the default server (${settings.proxyEndpoint}).\n" +
            "Open Settings > Keyscript IDE to configure your Keystone server?",
        "Configure Keyscript",
        Messages.getQuestionIcon()
    )
    if (go == Messages.YES) {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
        return
    }
}
```

### 3. Proxy startup during login is not visible to the user

`AuthenticationService.login()` calls `ProxyServerService.getInstance(project).getProxyBaseUrl()`
which triggers `ensureStarted()` synchronously inside a coroutine. If the proxy takes
more than a second to bind the port, the login dialog button stays greyed-out with no
feedback.

The `LoginDialog` already has a `statusLabel`. Expand it to cover proxy startup:

```kotlin
// In LoginDialog.doOKAction(), update statusLabel text stages:
statusLabel.text = "Starting proxy server..."
statusLabel.isVisible = true

// Background thread:
val proxyBase = try {
    ProxyServerService.getInstance(project).getProxyBaseUrl()
} catch (e: Exception) {
    SwingUtilities.invokeLater {
        errorLabel.text = "Proxy failed to start on port ${settings.proxyPort}: ${e.message}"
        errorLabel.isVisible = true
        statusLabel.isVisible = false
        isOKActionEnabled = true
    }
    return@execute
}

statusLabel.text = "Logging in..."
```

### 4. New Project wizard does not open Settings after creation

`KeyscryptModuleBuilder` creates the project and bundle file but does not guide the
user to configure the Keystone server endpoint. First-run activation therefore
requires the user to discover **Settings > Keyscript IDE** on their own.

**Fix: open settings immediately after module creation.**

```kotlin
// In KeyscryptModuleBuilder.setupRootModel() or commitModule():
ApplicationManager.getApplication().invokeLater {
    ShowSettingsUtil.getInstance()
        .showSettingsDialog(module.project, KeyscryptSettingsConfigurable::class.java)
}
```

## Onboarding Checklist

- [ ] Project detection fires correctly for each marker type
- [ ] Status bar widget is visible immediately after detection
- [ ] Tooltip on "KS: Not Logged In" says "Click to log in to Keystone" (it does — keep it)
- [ ] Settings are validated before the first login attempt
- [ ] Proxy startup has visible feedback in the login dialog
- [ ] LoginDialog pre-fills saved credentials from PasswordSafe
- [ ] After first successful login, Session tab in Workspace panel reflects state
- [ ] New Project wizard routes to Settings after creation
