# Activation & Onboarding Reference

## Contents
- First-Run State Detection
- Auth-Gated Panel Pattern
- Settings-Gated Panel Pattern
- Status Bar as Onboarding Anchor
- Anti-Patterns

---

## First-Run State Detection

The plugin distinguishes three "not ready" conditions that need different messaging:

| Condition | Check | Message |
|-----------|-------|---------|
| Server unconfigured | `settings.keystoneServer.isBlank()` | "Configure server endpoint first" |
| Not logged in | `!session.isLoggedIn` | "Log in to continue" |
| No Keystone data | API returns empty list | "No data found — verify your instance" |

Check in this order. A user can't log in if the server isn't configured, so showing a login prompt before the server check is misleading.

```kotlin
private fun determineState(): PanelState = when {
    KeyscryptSettings.getInstance().keystoneServer.isBlank() -> PanelState.UNCONFIGURED
    !session.isLoggedIn -> PanelState.NOT_LOGGED_IN
    else -> PanelState.READY
}
```

---

## Auth-Gated Panel Pattern

The gold standard in this codebase is `SessionPanel`. Use this exact structure for any panel that requires authentication:

```kotlin
class MyDataPanel(private val project: Project) {
    private val session = SessionService.getInstance(project)
    val component = JPanel(BorderLayout())

    init {
        session.addListener(::refresh)
        refresh()
    }

    private fun refresh() {
        component.removeAll()
        component.add(
            when {
                !session.isLoggedIn -> buildAuthState()
                else -> buildContentState()
            },
            BorderLayout.CENTER
        )
        component.revalidate()
        component.repaint()
    }

    private fun buildAuthState(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridwidth = GridBagConstraints.REMAINDER
            insets = Insets(4, 0, 4, 0)
        }
        panel.add(JBLabel("Log in to view data").apply {
            font = font.deriveFont(Font.BOLD)
        }, gbc)
        panel.add(JBLabel("<html>Authenticate to connect to Keystone.</html>").apply {
            foreground = UIUtil.getContextHelpForeground()
        }, gbc)
        panel.add(createActionButton("Login") {
            ActionManager.getInstance().getAction("Keyscript.Login")
                ?.actionPerformed(AnActionEvent.createFromInputEvent(null, "", null, DataContext.EMPTY_CONTEXT))
        }, gbc)
        return panel
    }
}
```

---

## Settings-Gated Panel Pattern

When settings are missing, link directly to the relevant configurable. Don't just say "check settings" — open the right panel.

```kotlin
private fun buildUnconfiguredState(): JComponent {
    val panel = JPanel(GridBagLayout())
    val gbc = GridBagConstraints().apply { gridwidth = GridBagConstraints.REMAINDER; insets = Insets(4, 0, 4, 0) }

    panel.add(JBLabel("Keystone server not configured").apply {
        font = font.deriveFont(Font.BOLD)
    }, gbc)
    panel.add(JBLabel("<html>Add the server URL in <b>Settings → Keyscript IDE</b>.</html>").apply {
        foreground = UIUtil.getContextHelpForeground()
    }, gbc)
    panel.add(ActionLink("Open Keyscript Settings") {
        ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
    }, gbc)
    return panel
}
```

---

## Status Bar as Onboarding Anchor

`LoginStatusBarWidget` surfaces auth state project-wide. The "KS: Not Logged In" text is the first
onboarding touchpoint users see. Keep the status bar copy consistent with empty state copy in panels.

```kotlin
// LoginStatusBarWidget — the canonical text users see before logging in
override fun getText(): String = if (session.isLoggedIn)
    "KS: ${session.username} | ${session.instance}"
else
    "KS: Not Logged In"   // matches language used in SessionPanel empty state

override fun getTooltipText(): String = if (session.isLoggedIn)
    "Keyscript: Logged in as ${session.username} on ${session.instance}\nClick to manage session"
else
    "Keyscript: Click to log in to Keystone"
```

Keep "Not Logged In" in the widget and "No active Keystone session" in `SessionPanel` — both are
correct at their respective scopes. Don't collapse them to the same string; the widget has character
limits and the panel needs a full explanation.

---

## Anti-Patterns

### WARNING: Blank Panel on Auth Failure

**The Problem:**
```kotlin
// BAD — panel shows nothing if session check is skipped
override fun createContent() {
    add(buildSessionTable())  // throws or returns empty if not logged in
}
```

**Why This Breaks:**
1. Users see a blank white rectangle with zero guidance
2. There's no way to recover from within the panel
3. Support load increases as users assume the plugin is broken

**The Fix:** Always implement `refresh()` with an explicit not-logged-in branch (see Auth-Gated Pattern above).

### WARNING: Checking Auth State on EDT in Init

**The Problem:**
```kotlin
// BAD — session state may not be initialized yet at panel construction time
init {
    if (session.isLoggedIn) buildTable() else buildEmptyState()
    // isLoggedIn can be false because SessionService heartbeat hasn't run
}
```

**The Fix:** Defer state checks to `refresh()`, which is also called by the session listener after login completes.

---

See the **designing-onboarding-paths** skill for the full first-run wizard design.
See the **improving-activation-flow** skill for funnel instrumentation and step reduction.
