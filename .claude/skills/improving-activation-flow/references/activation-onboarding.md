# Activation & Onboarding Reference

## Contents
- Activation Funnel Overview
- Project Detection Gate
- Settings Bootstrapping
- First Login Flow
- First Run Gate
- Anti-Patterns

The plugin's activation funnel has **zero hand-holding** by default. Users must discover that they need to configure settings before they can log in, and must log in before the proxy starts. Every silent dependency in this chain is a drop-off risk.

---

## Activation Funnel Overview

```
Install plugin
    ↓
Open Keyscript project
    ↓ KeyscryptProjectService.StartupActivity.execute()
Status bar widget appears ("KS: Not Logged In")
    ↓ BLOCKER: settings must be configured
Click status bar → Login dialog
    ↓ BLOCKER: device ID, instance, credentials required
Session established → SessionService.setSession()
    ↓ BLOCKER: proxy hasn't started yet
Click gutter play button
    ↓ ProxyServerService starts lazily here
Script runs in JCEF preview ← ACTIVATION
```

---

## Project Detection Gate

`KeyscryptProjectDetector` is the gatekeeper. The status bar widget, tool windows, and all actions are hidden unless `isKeyscriptProject` returns `true`.

```kotlin
// LoginStatusBarWidgetFactory — the widget is invisible until this passes
override fun isAvailable(project: Project): Boolean =
    KeyscryptProjectDetector.isKeyscriptProject(project)

// Detection order (fast-fail first, then shallow file scan)
private fun detect(): Boolean {
    val root = File(project.basePath ?: return false)
    if (File(root, "keyscript.bundle.json").exists()) return true  // fastest
    if (File(root, ".keyscript").exists()) return true             // fast
    return scanForKeyscriptFiles(root, maxDepth = 2)              // slow
}
```

**DO:** Add `keyscript.bundle.json` to the root of every Keyscript project. It's the fastest detection signal and required for `BundleService` anyway.

**DON'T:** Rely on the `// @keyscript` file scan in large repos. The 2-level depth scan reads file contents — it can be slow if the root has many `.js` files.

After detection, `KeyscryptProjectService.StartupActivity` fires:

```kotlin
class StartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!KeyscryptProjectDetector.isKeyscriptProject(project)) return
        getInstance(project).initialize()  // records basePath, updates status bar
    }
}
```

---

## Settings Bootstrapping

`KeyscryptSettings` stores defaults that are environment-specific. **New teams will always have wrong defaults.**

```kotlin
data class State(
    var proxyEndpoint: String = "keystonedev.revfcu.com:8443",  // WRONG for most teams
    var supportedInstances: String = "Test,Development",         // may not match your env
    var proxyPort: Int = 3000,
    var servicePort: Int = 3001,
    var deviceServiceUrl: String = ""                            // blank = user must type it
)
```

**Problem:** There is no first-run "setup required" notification. Users open a Keyscript project, see "KS: Not Logged In", click it, and encounter a login dialog pre-filled with wrong server details.

**Fix pattern — detect unconfigured state and notify:**

```kotlin
// In KeyscryptProjectService.initialize(), add after status bar update:
val settings = KeyscryptSettings.getInstance()
if (settings.proxyEndpoint == "keystonedev.revfcu.com:8443") {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(
            "Keyscript IDE",
            "Configure your Keystone server endpoint in Settings > Keyscript IDE before logging in.",
            NotificationType.WARNING
        )
        .addAction(NotificationAction.createSimple("Open Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
        })
        .notify(project)
}
```

---

## First Login Flow

The `LoginDialog` in `actions/LoginAction.kt` is the central activation step. Key fields:

| Field | Source | Friction |
|-------|--------|---------|
| Keystone Server | `settings.proxyEndpoint` (read-only) | Wrong default causes silent failure |
| Instance | `settings.supportedInstances` dropdown | Must match actual Keystone instances |
| Device ID | `settings.deviceServiceUrl` | Blank by default; unclear what to enter |
| Username / Password | `PasswordSafe` (pre-filled if saved) | Good — reduces friction on repeat login |

```kotlin
// LoginDialog pre-fills credentials from PasswordSafe — good pattern
private val usernameField = JBTextField(savedCreds?.first ?: "")
private val passwordField = JBPasswordField().apply {
    savedCreds?.second?.let { text = it }
}

// Focus lands on password field if username is already filled
override fun getPreferredFocusedComponent(): JComponent =
    if (usernameField.text.isNotEmpty()) passwordField else usernameField
```

**DO:** Pre-fill the device ID from `settings.deviceServiceUrl`. It's already done — but the tooltip ("e.g. MAC: AA-BB-CC-DD-EE-FF") is the only guidance. Add inline label text explaining where to find the device ID for your organization.

**DON'T:** Remove `savedCreds` pre-fill. Auto-relogin on session expiry depends on `PasswordSafe` having stored credentials.

---

## First Run Gate

The proxy is lazy — it only starts when the user actually runs a script. This is correct for startup performance but means the first run has two startup delays back-to-back: proxy startup + script execution.

```kotlin
// ProxyServerService — proxy starts on first call to startIfNeeded()
// RunKeyscryptService calls this before executing
```

**Checklist for first-run activation:**

```
- [ ] keyscript.bundle.json exists at project root
- [ ] Settings > Keyscript IDE: proxyEndpoint matches Keystone server
- [ ] Settings > Keyscript IDE: supportedInstances matches available instances
- [ ] User has clicked status bar and logged in successfully
- [ ] Session is shown in status bar (e.g. "KS: jsmith | Development")
- [ ] Gutter play icon appears on a .keyscript.js file
- [ ] Click gutter play → proxy starts → JCEF preview loads
```

---

## Anti-Patterns

### WARNING: Skipping the detection gate in actions

**The Problem:**
```kotlin
// BAD — action fires in non-Keyscript projects
override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true  // always enabled
}
```

**Why This Breaks:** Users in unrelated projects see Keyscript actions and try to use them. Clicking "Login" with no `SessionService` bound to the project causes a NullPointerException.

**The Fix:**
```kotlin
override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
        && KeyscryptProjectDetector.isKeyscriptProject(project)
}
```

### WARNING: Hardcoded default server in shipped builds

Shipping `keystonedev.revfcu.com:8443` as the default in production builds exposes an internal hostname. Override the default for each deployment environment via a build-time configuration or a setup wizard on first activation.
