# Activation & Onboarding Reference

## Contents
- Activation Funnel
- First-Run Detection Hook
- Configuration Gate Pattern
- Login Empty State (Existing)
- WARNING: Silent Failures
- Test Connection Button

---

## Activation Funnel

The four gates a new user must pass before value is delivered:

```
Install plugin → Open Keyscript project → Configure server endpoint → Login → Run script
     [auto]           [auto-detect]              [MANUAL, no prompt]   [manual]   [manual]
```

Gates 3–5 have no proactive guidance today. Users who don't know to open Settings are stuck.

---

## First-Run Detection Hook

`KeyscryptProjectService$StartupActivity` runs on every Keyscript project open. Add first-run
guidance here — check a persistent flag to avoid repeat prompts.

```kotlin
class StartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!KeyscryptProjectDetector.isKeyscryptProject(project)) return
        getInstance(project).initialize()

        val onboarding = project.service<OnboardingStateService>()
        val settings = KeyscryptSettings.getInstance()

        if (!onboarding.state.shownWelcome) {
            onboarding.state.shownWelcome = true
            if (settings.keystoneServer.isBlank()) {
                showConfigureNotification(project)
            }
        }
    }

    private fun showConfigureNotification(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification(
                "Keyscript IDE",
                "Configure the Keystone server endpoint to enable login and script execution.",
                NotificationType.INFORMATION
            )
            .addAction(NotificationAction.createSimple("Open Settings") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
            })
            .notify(project)
    }
}
```

Register the state service in `plugin.xml`:
```xml
<projectService serviceImplementation="com.keyscript.plugin.services.OnboardingStateService"/>
```

---

## Configuration Gate Pattern

Every tool window panel should check configuration state before rendering content.
Follow the existing `SessionPanel` structure:

```kotlin
// In WorkspacePanel, DataToolsPanel, etc.
private fun buildContent(): JComponent {
    val settings = KeyscryptSettings.getInstance()
    return when {
        settings.keystoneServer.isBlank() -> buildUnconfiguredState()
        !session.isLoggedIn             -> buildLoggedOutState()
        else                             -> buildPrimaryContent()
    }
}

private fun buildUnconfiguredState(): JComponent = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty(16)
    add(JBLabel("<html><b>Not configured</b><br>" +
        "Enter the Keystone server endpoint in Settings to continue.</html>"),
        BorderLayout.NORTH)
    add(ActionLink("Open Keyscript Settings") {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
    }, BorderLayout.CENTER)
}
```

---

## Login Empty State (Existing)

`SessionPanel` already implements this pattern — replicate it in other panels:

```kotlin
// src/.../toolwindow/panels/SessionPanel.kt
if (!session.isLoggedIn) {
    add(JBLabel("Log in to run scripts and browse Keystone data."), BorderLayout.NORTH)
    add(JPanel().apply {
        add(createActionButton("Login") { triggerLoginAction() })
        add(Box.createHorizontalStrut(8))
        add(ActionLink("Open Settings") { openSettings() })
    }, BorderLayout.SOUTH)
}
```

**DO** follow this exact structure: one descriptive sentence + one primary action + one secondary link.
**AVOID** showing raw error messages or stack traces in empty states.

---

## WARNING: Silent Failures at Each Gate

**The Problem:**

```kotlin
// BAD — tool window renders empty when server not configured; user has no idea why
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.contentManager.addContent(
        ContentFactory.getInstance().createContent(TableBrowserPanel(project), "Tables", false)
    )
}
```

**Why This Breaks:**
1. User opens Data Tools → blank panel → no explanation → assumes plugin is broken
2. No path to resolution; user must discover Settings independently
3. Drops activation rate: users uninstall rather than configure

**The Fix:** Gate content creation on configuration state. Render an empty-state panel with a CTA when prerequisites are missing.

---

## Test Connection Button

Add to `KeyscryptSettingsConfigurable` to close the feedback loop after configuration:

```kotlin
val testButton = JButton("Test Connection").apply {
    addActionListener {
        isEnabled = false
        text = "Testing…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = runCatching {
                // HEAD request to keystoneServer
                URL("https://${endpointField.text}/").openConnection()
                    .also { (it as HttpURLConnection).requestMethod = "HEAD" }
                    .connect()
                true
            }.getOrDefault(false)
            SwingUtilities.invokeLater {
                text = if (ok) "Connected ✓" else "Failed ✗"
                isEnabled = true
            }
        }
    }
}
```

See the **intellij-platform** skill for the `Configurable` panel layout pattern.
