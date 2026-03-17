# In-App Guidance Reference

## Contents
- Inline Help Text Patterns
- Action Buttons in Empty States
- Context Help Color
- HTML Labels for Rich Guidance
- QueryBuilder Empty Field Hint
- Anti-Patterns

---

## Inline Help Text Patterns

Two mechanisms exist in this codebase for inline guidance:

| Mechanism | Use case | Component |
|-----------|----------|-----------|
| `JBLabel` with context help color | Explanatory subtitle under a heading | `UIUtil.getContextHelpForeground()` |
| `TitledBorder` | Section grouping in detail panels | `BorderFactory.createTitledBorder()` |
| HTML `<html>` label | Multi-line guidance with bold emphasis | `JBLabel("<html>...</html>")` |
| Status label at bottom | Operation feedback | `JBLabel` pinned to `BorderLayout.SOUTH` |

---

## Context Help Color

All secondary/explanatory text uses `UIUtil.getContextHelpForeground()`, which automatically adapts
to the IDE's light/dark theme. NEVER hardcode `Color.GRAY` or `JBColor.GRAY` for hint text — it
breaks in dark mode.

```kotlin
// GOOD — theme-aware hint text
val hintLabel = JBLabel("Log in to run scripts and browse Keystone data.").apply {
    foreground = UIUtil.getContextHelpForeground()
}

// BAD — hardcoded gray breaks in dark themes
val hintLabel = JBLabel("Log in first").apply {
    foreground = Color.GRAY  // NEVER do this
}
```

The one exception is `NetworkPanel`'s no-selection label which uses `JBColor.GRAY` — this is a
pre-existing inconsistency to align when refactoring that panel.

---

## HTML Labels for Rich Guidance

Use HTML in `JBLabel` when you need bold keywords, line breaks, or clickable-looking emphasis. The
IntelliJ Swing renderer supports a subset of HTML 3.2.

```kotlin
// GOOD — draws attention to the specific setting location
JBLabel("<html>Set the server URL in <b>Settings → Keyscript IDE</b>.</html>").apply {
    foreground = UIUtil.getContextHelpForeground()
}

// GOOD — multi-line explanation in a constrained panel
JBLabel("<html><center>No requests captured yet.<br>Run a script to see network activity.</center></html>").apply {
    foreground = UIUtil.getContextHelpForeground()
    horizontalAlignment = SwingConstants.CENTER
}
```

AVOID HTML for simple single-line labels — it adds parsing overhead and can break if not well-formed.

---

## Action Buttons in Empty States

Empty states that require user action MUST include a direct action button. Don't rely on users
knowing where to find the relevant menu item.

```kotlin
// Pattern from SessionPanel — button + settings link together
val loginButton = JButton("Login").apply {
    addActionListener { triggerLoginAction() }
}
val settingsLink = ActionLink("Open Settings") {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, KeyscryptSettingsConfigurable::class.java)
}

// Layout: heading → explanation → primary action → secondary link
panel.add(headingLabel, gbc)
panel.add(explanationLabel, gbc)
panel.add(loginButton, gbc)
panel.add(settingsLink, gbc)
```

Primary action = button. Secondary/escape hatch = `ActionLink`. Don't make both buttons — the
hierarchy matters.

---

## QueryBuilder Empty Field Hint

`QueryBuilderPanel` uses a property-panel help text pattern for fields that are optional:

```kotlin
// Shown in the properties panel when all fields are empty or optional
val emptyFieldHint = JBLabel("<html><i>Empty fields are omitted from output.</i></html>").apply {
    foreground = UIUtil.getContextHelpForeground()
}
```

Use this pattern in any properties/form panel where empty fields have defined behavior. It sets user
expectations and prevents unnecessary "required field" anxiety.

---

## NetworkPanel No-Selection State

The Network panel's `CardLayout` approach is the correct pattern for master-detail panels. The empty
state is always present in the DOM — `CardLayout` just shows/hides it:

```kotlin
// NetworkPanel pattern — no-selection hint in detail area
private val noSelectionLabel = JBLabel("Select a request to view details").apply {
    foreground = JBColor.GRAY  // note: should be UIUtil.getContextHelpForeground() in future
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.CENTER
}

private val cardLayout = CardLayout()
private val detailArea = JPanel(cardLayout).apply {
    add(noSelectionLabel, "empty")
    add(requestDetailPanel, "detail")
}

// In selection listener:
cardLayout.show(detailArea, if (hasSelection) "detail" else "empty")
```

---

## Anti-Patterns

### WARNING: Tooltip-Only Guidance

**The Problem:**
```kotlin
// BAD — tooltip is invisible until hover; users won't find it
loadButton.toolTipText = "Configure Keystone server in Settings before loading"
loadButton.isEnabled = false
```

**Why This Breaks:** Disabled buttons with tooltip-only guidance leave users guessing. A user who
has never configured the server won't hover every disabled button.

**The Fix:** Show the unconfigured state panel with a direct settings link instead of disabling the
button silently.

### WARNING: Generic Error Messages

**The Problem:**
```kotlin
// BAD — tells the user nothing actionable
statusLabel.text = "Error occurred"
```

**The Fix:**
```kotlin
// GOOD — specific error with recovery hint
statusLabel.text = "Could not connect to Keystone: ${e.message}. Check server settings."
```

---

See the **intellij-platform** skill for `ActionLink`, `JBUI.Borders`, and `ShowSettingsUtil` APIs.
See the **designing-onboarding-paths** skill for checklist and wizard guidance patterns.
