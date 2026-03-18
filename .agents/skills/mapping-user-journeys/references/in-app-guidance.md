# In-App Guidance: Empty States, Tooltips, and Inline Help

The Keyscript plugin has no onboarding wizard. Every tool window, panel, and dialog must
carry enough context for the user to proceed without reading the README. This document
covers each surface that currently lacks guidance and provides implementation patterns.

## The Empty State Problem

An empty state is what the user sees when a panel has loaded but there is no data to show.
The Keyscript plugin has several surfaces that render as blank panels in the unconfigured
or not-logged-in state.

### Rule of thumb
Every panel that can be empty must answer three questions visually:
1. What is this panel for?
2. Why is it empty right now?
3. What should I do to make it not empty?

## Surface-by-Surface Audit

### Workspace Panel — Session Tab

`SessionPanel.kt` renders the current session state. When not logged in, a blank panel
shows. The user has no idea they should click the status bar widget.

**Fix:** Show a centered call-to-action:

```kotlin
// In SessionPanel.kt — build the content based on session state
private fun buildContent(): JComponent {
    return if (session.isLoggedIn) {
        buildLoggedInPanel()
    } else {
        buildEmptyState(
            icon = AllIcons.General.User,
            heading = "Not logged in",
            body = "Click the <b>KS: Not Logged In</b> widget in the status bar\n" +
                   "to authenticate with Keystone.",
            actionText = "Login...",
            action = { ActionManager.getInstance().getAction("Keyscript.Login")
                         ?.actionPerformed(dataContext) }
        )
    }
}
```

Use a shared helper to avoid duplicating this layout:

```kotlin
fun buildEmptyState(
    icon: Icon,
    heading: String,
    body: String,
    actionText: String? = null,
    action: (() -> Unit)? = null
): JComponent {
    val panel = JPanel(GridBagLayout())
    val gbc = GridBagConstraints().apply {
        gridx = 0; gridy = GridBagConstraints.RELATIVE
        insets = JBUI.insets(4)
        anchor = GridBagConstraints.CENTER
    }
    panel.add(JLabel(icon), gbc)
    panel.add(JBLabel("<html><b>$heading</b></html>"), gbc)
    panel.add(JBLabel("<html><center>$body</center></html>").apply {
        foreground = UIUtil.getContextHelpForeground()
    }, gbc)
    if (actionText != null && action != null) {
        panel.add(JButton(actionText).apply { addActionListener { action() } }, gbc)
    }
    return panel
}
```

### Data Tools Panel — Table Browser Tab

`TableBrowserPanel` is empty until the user enters a table name and searches. There is
no placeholder explaining the search syntax or what tables are available.

**Fix:** Add a header label and placeholder text to the search field:

```kotlin
// In TableBrowserPanel.kt — init block
private val tableNameField = JBTextField().apply {
    emptyText.text = "e.g. MEMBER, ACCOUNT, LOAN"
    toolTipText = "Enter a Keystone table name and press Enter to browse its columns"
}
```

### Data Tools Panel — Query Builder Tab

`QueryBuilderPanel` renders a blank form before the user interacts. There is no
explanation that the query builder generates CR framework `DirectXMLPostJSON` calls.

**Fix:** Add a single explanatory label above the form:

```kotlin
// In QueryBuilderPanel.kt — top of panel
private val helpLabel = JBLabel(
    "<html>Build a Keystone query and copy the generated <code>DirectXMLPostJSON</code> " +
    "call into your script.</html>"
).apply {
    foreground = UIUtil.getContextHelpForeground()
    border = JBUI.Borders.emptyBottom(8)
}
```

### Diagnostics Panel — Network Monitor Tab

`NetworkToolWindowFactory` renders the network log table. On first load, the table is
empty because no requests have been proxied yet. The user does not know why the table is
blank or that it only captures requests through the proxy.

**Fix:** Show an instructional empty state with a "Run a script to see proxy traffic" message.

## Tooltip Standards

Every interactive control must have a `toolTipText` set. Audit checklist:

| Control | Current state | Required tooltip |
|---------|--------------|-----------------|
| Status bar "KS: Not Logged In" | Has tooltip | Keep: "Click to log in to Keystone" |
| Status bar logged-in | Has tooltip | Keep: "Click to manage session" |
| Run gutter icon | Via IDE run tooltip | No change needed |
| Login button in dialog | None | "Authenticate with Keystone using the credentials below" |
| Instance dropdown in LoginDialog | None | "Select the Keystone instance to connect to" |
| Device ID field in LoginDialog | Has toolTipText | Keep: "e.g. MAC: AA-BB-CC-DD-EE-FF" |
| Deploy action in toolbar | None | "Deploy this script to the SCRIPT table on Keystone" |

## Settings Panel Guidance

`KeyscryptSettingsConfigurable` renders input fields for server, port, and instances.
New users do not know:
- The difference between `proxyEndpoint` and `keystoneApiUrl`
- What `servicePort` is for (device service)
- That `supportedInstances` is a comma-separated list

Add a context help panel at the top of the settings form:

```kotlin
// In KeyscryptSettingsConfigurable.createComponent():
private val contextHelp = ContextHelpLabel.createWithLink(
    "Proxy Endpoint",
    "The Keystone server hostname:port for the embedded proxy " +
        "(e.g. keystonedev.example.com:8443). Used for login and script preview.",
    "More info",
    "https://docs.keystone.internal"
)
```

**WARNING:** Do not put the Keystone API URL and proxy endpoint in the same field.
They serve different purposes — the proxy endpoint is used for the JCEF preview path
(via the embedded Ktor server), and the API URL is used for direct REST calls from
`DeploymentService`. Conflating them causes deploy to go through the proxy when it
should not.

## In-Dialog Progress Patterns

The `LoginDialog` already implements the right pattern with `statusLabel` and
`errorLabel`. Replicate this in every long-running dialog:

```kotlin
// Pattern: loading state management in any dialog with async action
private val statusLabel = JBLabel("").apply { isVisible = false }
private val errorLabel  = JBLabel("").apply {
    foreground = JBUI.CurrentTheme.NotificationError.foregroundColor()
    isVisible = false
}

private fun showLoading(message: String) {
    statusLabel.text = message
    statusLabel.isVisible = true
    errorLabel.isVisible = false
    isOKActionEnabled = false
}

private fun showError(message: String) {
    errorLabel.text = "<html>$message</html>"
    errorLabel.isVisible = true
    statusLabel.isVisible = false
    isOKActionEnabled = true
}

private fun showSuccess(message: String) {
    statusLabel.text = message
    statusLabel.foreground = JBUI.CurrentTheme.NotificationInfo.foregroundColor()
    statusLabel.isVisible = true
}
```
