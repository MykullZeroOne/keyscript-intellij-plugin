# In-App Guidance Reference

## Contents
- Tooltip Conventions
- Inline Hint Labels (Settings)
- Status Bar Guidance Copy
- GutterIcon Tooltips
- Contextual Help Buttons
- Guidance Checklist

---

## Tooltip Conventions

Every interactive element that isn't self-labelling needs `toolTipText`. This is the minimum bar — NEVER ship an icon-only button without a tooltip.

```kotlin
// Standard tooltip — button with icon only
val deployButton = JButton(AllIcons.Actions.Upload).apply {
    toolTipText = "Deploy this script to Keystone (updates existing if serial is set)"
    isBorderPainted = false
    isContentAreaFilled = false
}

// Tooltip on a combo box
val instanceCombo = ComboBox(instanceItems).apply {
    toolTipText = "Keystone instance to target — configured in Settings > Keyscript IDE"
}

// Tooltip on a text field
val personField = JTextField().apply {
    toolTipText = "Person serial — used to scope the script run context"
}
```

Tooltips in IntelliJ render HTML. Use `<b>` for emphasis and `<br>` for line breaks — keep them under 2 lines.

## Inline Hint Labels (Settings)

Settings fields need inline context help. Use `UIUtil.getContextHelpForeground()` for the muted grey color and `JBUI.Fonts.smallFont()` for sub-label sizing — these match IntelliJ's native settings panel styling.

```kotlin
// KeyscryptSettingsConfigurable.kt — after adding a labeled row
fun addFieldWithHint(panel: JPanel, label: String, field: JComponent, hint: String) {
    panel.add(JLabel(label))
    panel.add(field, CC().growX())
    panel.add(JLabel(hint).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBUI.Fonts.smallFont()
    }, CC().skip(1).growX().wrap())
}

// Usage
addFieldWithHint(
    panel,
    "Keystone Server:",
    serverField,
    "Hostname and port only — e.g. keystonedev.example.com:8443. No https:// prefix."
)
addFieldWithHint(
    panel,
    "Supported Instances:",
    instancesField,
    "Comma-separated list — e.g. Development,Test,Production"
)
```

## Status Bar Guidance Copy

`LoginStatusBarWidgetFactory` is always visible in Keyscript projects. Its tooltip and label are high-value guidance real estate. Keep the copy action-oriented.

```kotlin
// LoginStatusBarWidgetFactory.kt
override fun getTooltipText(): String {
    val settings = KeyscryptSettings.getInstance()
    val session = project.service<SessionService>()
    return when {
        !settings.isConfigured() ->
            "<html>Keyscript: not configured<br>Click to open Settings</html>"
        !session.isLoggedIn ->
            "<html>Keyscript: not logged in<br>Click to log in to ${settings.keystoneServer}</html>"
        else ->
            "<html>Keyscript: logged in as <b>${session.username}</b><br>Click to log out</html>"
    }
}

override fun getPresentation(): WidgetPresentation {
    return object : StatusBarWidget.TextPresentation {
        override fun getText() = when {
            !settings.isConfigured() -> "KS: Not configured"
            !session.isLoggedIn      -> "KS: Not logged in"
            else                     -> "KS: ${session.username}"
        }
    }
}
```

## GutterIcon Tooltips

`KeyscriptRunLineMarkerContributor` places run icons in the gutter. Tooltips here explain what "run" means in the Keyscript context — users unfamiliar with the proxy model need this.

```kotlin
// KeyscriptRunLineMarkerContributor.kt
override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    if (!isKeyscriptFile(element)) return null
    return LineMarkerInfo(
        element,
        element.textRange,
        AllIcons.Actions.Execute,
        { "Run '${element.containingFile.name}' via Keyscript proxy (opens split preview)" },
        { _, _ -> runScript(element) },
        GutterIconRenderer.Alignment.LEFT,
        { "Run Keyscript" }
    )
}
```

## Contextual Help Buttons

For complex panels (Query Builder, Table Browser), add a `?` button in the panel header that opens the relevant section of `README.md` or a balloon with a quick-reference tip.

```kotlin
// In QueryBuilderToolWindowFactory.kt header
val helpButton = JButton("?").apply {
    toolTipText = "Query Builder help"
    isBorderPainted = false
    font = JBUI.Fonts.smallFont()
    addActionListener {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Query Builder",
                "Build a Keystone XML query by selecting a table and adding filter rows. " +
                "Click <b>Preview XML</b> to see the generated query, then <b>Execute</b> to run it.",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
```

## Guidance Checklist

Copy this checklist when adding a new panel or interactive feature:

```
- [ ] Every icon-only button has toolTipText
- [ ] Every text field in settings has an inline hint label
- [ ] Status bar widget tooltip reflects current state (unconfigured / logged out / logged in)
- [ ] Any async operation result shows a balloon notification
- [ ] First-time feature use triggers a discovery nudge (gated with PropertiesComponent)
- [ ] Gutter icon tooltip explains the action in Keyscript domain terms
- [ ] Empty panel state includes actionable text (not just "no data")
- [ ] Notification group is registered in plugin.xml
```
