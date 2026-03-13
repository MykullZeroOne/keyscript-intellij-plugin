# Feedback & Insights: Capturing and Acting on User Reports

The Keyscript plugin has no feedback mechanism. Issues are reported verbally or via
internal tickets. This document defines surfaces for structured feedback collection,
how to triage incoming reports against the journey map, and how to close the loop.

## The Feedback Surface Taxonomy

There are three places users are likely to report problems:

1. **In the IDE itself** — they see an error notification or blank panel and stop working
2. **Internal ticket system** — they file a bug or request after the fact
3. **Direct developer channel** — Slack/Teams message to the plugin author

For an internal enterprise plugin with a small user base, instrumented logging plus a
lightweight in-plugin "Report Issue" action covers 80% of needs.

## Adding a "Report Issue" Action

Add a menu item under the Keyscript menu that opens a dialog with the current diagnostic
state pre-filled. This replaces vague "it doesn't work" reports with structured context.

```kotlin
// src/main/kotlin/com/keyscript/plugin/actions/ReportIssueAction.kt
package com.keyscript.plugin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.keyscript.plugin.services.KeyscryptSettings
import com.keyscript.plugin.services.ProxyServerService
import com.keyscript.plugin.services.SessionService
import java.awt.datatransfer.StringSelection

class ReportIssueAction : AnAction("Report Keyscript Issue...") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val session = SessionService.getInstance(project)
        val proxy   = ProxyServerService.getInstance(project)
        val settings = KeyscryptSettings.getInstance()

        val diagnostics = buildString {
            appendLine("=== Keyscript Diagnostic Snapshot ===")
            appendLine("Plugin version: 2.0.0")
            appendLine("Logged in: ${session.isLoggedIn}")
            appendLine("Instance: ${session.instance.ifEmpty { "—" }}")
            appendLine("Proxy endpoint: ${settings.proxyEndpoint}")
            appendLine("Proxy port: ${settings.proxyPort}")
            appendLine("Proxy session active: ${proxy.ssoSessionId.isNotEmpty()}")
            appendLine("")
            appendLine("Describe the issue:")
            appendLine("[User fills this in]")
        }

        CopyPasteManager.getInstance()
            .setContents(StringSelection(diagnostics))

        com.intellij.openapi.ui.Messages.showInfoMessage(
            project,
            "Diagnostic info copied to clipboard.\n\nPaste it into your issue report.",
            "Report Keyscript Issue"
        )
    }
}
```

Register in `plugin.xml` under the Keyscript action group.

## Triaging Reports Against the Journey Map

When a report arrives, the first question is: which step of which journey failed?
Use this triage decision tree:

```
User says: "nothing happens when I run the script"
    |
    +-- Is the status bar showing "KS: Not Logged In"?
    |       YES → Journey 1/2 failure: not authenticated
    |       NO  → continue
    |
    +-- Did the gutter icon appear?
    |       NO  → Journey 3 failure: file not detected as Keyscript
    |             Check: does the file have // @keyscript or end in .keyscript.js?
    |       YES → continue
    |
    +-- Does the JCEF panel open but show an error?
    |       YES → Journey 3 failure: proxy or Keystone error
    |             Check: Diagnostics > Network Monitor for the failed request
    |       NO  → continue
    |
    +-- Does the JCEF panel open but show the previous run?
            YES → Journey 3 regression: loadPreparedUrl() not called
                  Check: RunKeyscryptService.showPreview() reached?
```

## Feedback Collection Checklist for Each Journey

For every reported bug, capture:

- [ ] Which journey step failed (use the journey map in `SKILL.md`)
- [ ] What the user expected vs. what they saw
- [ ] Session state at failure time: logged in? which instance? proxy running?
- [ ] Whether a notification balloon appeared or the failure was silent
- [ ] The relevant section of `idea.log` filtered by `com.keyscript.plugin`

## Closing the Loop: From Report to Fix

**Rule:** Every friction point that generates more than one report in a quarter should be
in the roadmap. One report might be user error; two is a pattern.

Categorise fixes:

| Category | Fix location | Time to implement |
|----------|-------------|-------------------|
| Silent failure | Add `NotificationGroupManager` call in service | < 2 hours |
| Empty state | Add empty-state panel to tool window | < 4 hours |
| Unclear error | Add `friendlyError()` message mapping in service | < 1 hour |
| Missing guard | Add `isLoggedIn` check + redirect before action | < 2 hours |
| State desync | Add `notifyListeners()` call in service after state change | < 1 hour |

## Listening to Listener Gaps

The listener pattern in `SessionService` is the primary state propagation mechanism.
If a UI component does not subscribe, it will not update when session state changes.

Audit: find all classes that read `session.isLoggedIn` but do not call `addListener`:

```bash
# Find callers of isLoggedIn that are NOT in SessionService itself
grep -r "isLoggedIn\|session\.username\|session\.instance" \
    src/main/kotlin --include="*.kt" -l \
  | grep -v SessionService
```

For each file in the results, verify it either:
- Calls `session.addListener { ... }` in its `init` or `install` method, OR
- Reads the state only on-demand (e.g. from a button click handler, not a cached field)

**WARNING:** Do not read `session.isLoggedIn` in a field initializer. The session state
at construction time is always "not logged in" because services are instantiated before
the user authenticates. Always read it lazily inside a function or listener callback.

```kotlin
// WRONG — always false at construction time
class MyPanel(project: Project) {
    private val isLoggedIn = SessionService.getInstance(project).isLoggedIn // stale!
}

// CORRECT — reads live state on demand
class MyPanel(project: Project) {
    private val session = SessionService.getInstance(project)

    init {
        session.addListener { rebuild() }
    }

    private fun rebuild() {
        val loggedIn = session.isLoggedIn // fresh
        // update UI
    }
}
```

## Insight: The "Silent Logout" Problem

The most-reported class of issues with session-based plugins is the "silent logout":
the user is working, the session expires due to server timeout, and the next operation
fails with a cryptic network error rather than "please log in again."

The heartbeat in `SessionService` is designed to catch this, but the 2-minute interval
means up to 2 minutes of silent broken state. The `handleSessionExpired()` method fires
`notifyListeners()` and shows a WARNING balloon — but only if the heartbeat catches it
first. If the user runs a script and the session is already expired, `RunKeyscriptService`
will fail at the `SessionStore` POST with a 401, which currently surfaces as
`"Run failed: ..."` rather than `"Session expired — please log in again"`.

This is the highest-impact single fix in the entire codebase: detect 401 responses in
`RunKeyscryptService.preparePreview()` and call `session.handleSessionExpired()` followed
by a user-visible "Session expired. Logging you back in..." message.
