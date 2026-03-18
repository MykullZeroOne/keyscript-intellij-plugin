# Activation Onboarding Reference

## Contents
- Funnel Stages
- Stage 1: Project Detection Gate
- Stage 2: Settings Configuration
- Stage 3: First Login
- Stage 4: First Run
- Anti-Patterns

---

## Funnel Stages

Install → **Detect** → **Configure** → **Login** → **First Run** → Repeat

Each stage is a separate file. Each stage should nudge to the next — currently none do.

---

## Stage 1: Project Detection Gate

All tool windows check `KeyscryptProjectDetector.isKeyscriptProject(project)`. Users with
existing JS projects get nothing until a marker exists.

**DO:** On project open without markers, show a one-time balloon if JS files are found.

```kotlin
// In KeyscriptProjectService.StartupActivity.execute()
override suspend fun execute(project: Project) {
    if (KeyscriptProjectDetector.isKeyscriptProject(project)) {
        getInstance(project).initialize()
        return
    }
    // Suggest enabling for projects that look like they could be Keyscript
    val hasJs = project.basePath?.let { File(it).walk().maxDepth(2)
        .any { f -> f.extension == "js" } } ?: false
    if (!hasJs) return

    val prefs = PropertiesComponent.getInstance(project)
    if (prefs.getBoolean("keyscript.suggest.enable.shown", false)) return
    prefs.setValue("keyscript.suggest.enable.shown", true)

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript IDE", "JS project detected. Enable Keyscript support?",
            NotificationType.INFORMATION)
        .addAction(NotificationAction.createSimple("Enable") {
            // create .keyscript marker, then initialize
            File(project.basePath!!, ".keyscript").createNewFile()
            getInstance(project).initialize()
        })
        .notify(project)
}
```

---

## Stage 2: Settings Configuration

`KeyscryptSettingsConfigurable` has 6 fields with no defaults shown and no help text.
Users don't know what "Keystone Proxy Endpoint" means vs "Keystone API URL".

**DO:** Detect blank settings at startup and nudge to configure.

```kotlin
// In KeyscriptProjectService.initialize()
private fun maybeNudgeSettings() {
    val settings = KeyscriptSettings.getInstance()
    if (settings.keystoneServer.isBlank()) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification("Keyscript IDE",
                "Configure your Keystone server to get started.",
                NotificationType.WARNING)
            .addAction(NotificationAction.createSimple("Open Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
            })
            .notify(project)
    }
}
```

**DON'T:** Show this on every project open. Gate with `PropertiesComponent`.

---

## Stage 3: First Login

The `SessionPanel` already has an empty state with a Login button. The gap is that the login
dialog gives no hints about Device ID or where credentials come from.

**DO:** Add tooltip and contextual hint to the Device ID field in `LoginAction.kt`.

```kotlin
// In the login dialog builder
deviceField.toolTipText = "Your device MAC address (e.g. AA-BB-CC-DD-EE-FF) " +
    "or leave blank if not required by your Keystone instance"
deviceField.emptyText.text = "Optional — leave blank if unsure"
```

**DON'T:** Add "what is Device ID?" help text in the login dialog body — it creates visual
noise for users who already know. Tooltips are the right surface.

---

## Stage 4: First Run

After first successful login, users need to know: open a `.keyscript.js` file, click the
gutter icon, watch Diagnostics. None of this is explained anywhere post-login.

**DO:** After login success, fire a one-time hint from `AuthenticationService`.

```kotlin
// In AuthenticationService, after setting session data
private fun maybeNudgeFirstRun(project: Project) {
    val prefs = PropertiesComponent.getInstance(project)
    if (prefs.getBoolean("keyscript.nudge.firstrun.shown", false)) return
    prefs.setValue("keyscript.nudge.firstrun.shown", true)

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification("Keyscript IDE",
            "Logged in. Open a .keyscript.js file and click the gutter play button to run.",
            NotificationType.INFORMATION)
        .notify(project)
}
```

---

## Anti-Patterns

### WARNING: Showing nudges on every project open

**The Problem:** Nudges without `PropertiesComponent` persistence fire every time.
**Why This Breaks:** Users dismiss once; seeing it again damages trust and trains them to ignore notifications.
**The Fix:** Always check and set a `keyscript.nudge.X.shown` key before firing.

### WARNING: Gating all adoption on login state

**The Problem:** Showing nothing to unauthenticated users assumes they know how to log in.
**Why This Breaks:** First-time users see empty panels and no call to action — they uninstall.
**The Fix:** Show contextual empty states at every stage, even before login. See the **designing-onboarding-paths** skill.
