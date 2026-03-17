# In-App Guidance — Release Note Writing Guide

This reference covers how to describe changes to in-app copy — tooltips, empty states, balloon
notifications, error messages, and status text — in release notes for Keyscript IDE.

---

## Why In-App Guidance Appears in Release Notes

Guidance copy changes are user-visible and affect user confidence. They belong in release notes when:
- An error message now provides actionable next steps (not just a code or generic failure text).
- An empty state now explains why data is missing and what to do about it.
- A notification balloon now fires at a more appropriate moment or with clearer instructions.
- A tooltip was added to a previously unlabeled control.

---

## String Patterns by Surface

### Status Bar Widget (`LoginStatusBarWidgetFactory`)

The widget text and tooltip change based on session state. When writing release notes about widget
copy changes, show the before/after strings:

| State | Widget Text | Tooltip |
|-------|-------------|---------|
| Not configured | `KS: Not Configured` | `Click to open Keyscript Settings` |
| Not logged in | `KS: Not Logged In` | `Click to log in to Keystone` |
| Logged in | `KS: [username]` | `Logged in as [username] · [instance]` |
| Session expired | `KS: Session Expired` | `Session timed out — click to re-authenticate` |
| Logging in | `KS: Logging In...` | `Connecting to [server]` |

Entry example:
```
- **Status Bar**: Widget tooltip on the "Session Expired" state now reads
  "Session timed out — click to re-authenticate" rather than the generic
  "Click to log in to Keystone", making the cause of the state clear.
```

### Tool Window Empty States

Each tool window panel should show a meaningful empty state when data is unavailable.

**Keyscript Workspace (right panel)**
- Run Options tab, before parameters are configured:
  `No script parameters. Run a Keyscript file to populate.`
- Session tab, when not logged in:
  `Not logged in. Click "KS: Not Logged In" in the status bar to authenticate.`

**Keyscript Data Tools (bottom panel)**
- Table Browser, not logged in:
  `Log in to browse Keystone tables.`
- Table Browser, logged in but no table selected:
  `Select a table from the list to view columns and search records.`
- Query Builder, empty state:
  `No query loaded. Use the tree to build a Corelation XML query.`

**Keyscript Diagnostics (bottom panel)**
- Console, no output yet:
  `No output. Run a script to see console messages here.`
- Network Monitor, no traffic captured:
  `No requests captured. Run a script to see proxy traffic.`

Entry example:
```
- **Data Tools / Table Browser**: The empty state when no table is selected now reads
  "Select a table from the list to view columns and search records" instead of
  showing a blank panel, reducing confusion on first open.
```

### Notification Balloons (`NotificationGroup: "Keyscript"`)

Balloon notifications fire via `NotificationGroupManager`. When writing release note entries for
notification changes, include the notification text and the trigger condition:

Trigger conditions and suggested notification strings:
- Login success: `Logged in to [instance] as [username]`
- Login failure: `Login failed: [error]. Check your credentials in Settings > Keyscript IDE.`
- Session expired (auto-detected by heartbeat): `Keyscript session expired. Click the status bar to re-authenticate.`
- Deploy success: `[script name] deployed to [instance] successfully.`
- Deploy failure: `Deploy failed: [error]. Check the Diagnostics console for details.`
- Proxy startup failure: `Proxy failed to start on port [port]. Change the port in Settings > Keyscript IDE.`

Entry example:
```
- **Notifications**: Login failure notifications now include the error message from
  Keystone and a direct link to Settings > Keyscript IDE, instead of displaying
  a generic "Login failed" balloon.
```

### Proxy Error Messages

Proxy errors surface in the Diagnostics console and in balloon notifications. Clear error copy is
essential because proxy failures are silent by default. Standard proxy error patterns:

```
Proxy failed to start: port [port] is already in use.
→ Action: "Change proxy port in Settings > Keyscript IDE"

JSESSIONID not available — requests will not be authenticated.
→ Action: "Log in via the status bar before running scripts."

Keystone returned [status]: [path]
→ Action: "Check the Network Monitor tab for full request details."
```

Entry example:
```
- **Diagnostics / Console**: Proxy startup errors now display the conflicting port
  number and a direct action: "Change proxy port in Settings > Keyscript IDE."
  Previously, the error showed only "Address already in use."
```

---

## Writing Principles for Guidance Copy Entries

- **Quote the exact string** when it fits in one line. This lets users verify the change in their own IDE.
- **Show trigger conditions** so readers understand when they will see the new message.
- **Pair the message with its action** — a good error message entry shows both the text and the next step.
- Never describe a guidance change as "improved messaging" without quoting at least the new string.
- Keep guidance copy in sentence case (not Title Case), matching IntelliJ platform conventions.
