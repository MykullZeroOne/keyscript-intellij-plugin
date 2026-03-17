# Roadmap & Experiments Reference

## Contents
- Empty State Coverage Gaps
- Incremental Improvement Sequence
- Feature Flag Approach
- Testing Empty States Manually
- Proposed Enhancements

---

## Empty State Coverage Gaps

Current coverage across panels (as of v2.0.0):

| Panel | Auth-gated | Unconfigured | Loading | No-selection | Error |
|-------|-----------|--------------|---------|--------------|-------|
| SessionPanel | ✅ Full | ❌ Missing | — | — | ❌ Missing |
| TableBrowserPanel | ❌ Missing | ❌ Missing | ✅ Status label | — | ✅ Status label |
| SearchPanel | ❌ Missing | ❌ Missing | ✅ Button disable | — | ✅ Status label |
| QueryBuilderPanel | — | — | ✅ Status label | — | ✅ Status label |
| NetworkPanel | — | — | — | ✅ CardLayout | — |
| ConsolePanel | — | — | — | — | — |
| ScriptOptionsPanel | — | ❌ Missing | — | — | — |

Priority order for fixing: **TableBrowserPanel auth-gate → SearchPanel auth-gate → SessionPanel error state → ScriptOptionsPanel unconfigured.**

---

## Incremental Improvement Sequence

Work from highest-traffic panels first. The Session tab and Table Browser are opened most by active
users; fix those before QueryBuilder.

**Sprint 1 — Auth gates on data panels:**
```
- [ ] TableBrowserPanel: add session.isLoggedIn check in componentShown handler
- [ ] SearchPanel: add session.isLoggedIn check before search execution
- [ ] Both: show auth-gated empty state with Login button (see activation-onboarding.md)
```

**Sprint 2 — Unconfigured state on ScriptOptionsPanel:**
```
- [ ] Check settings.keystoneServer.isBlank() on panel init
- [ ] Show "Configure server" empty state with link to KeyscryptSettingsConfigurable
- [ ] Re-check on settings change (listen to application-level settings bus or refresh on show)
```

**Sprint 3 — Error states:**
```
- [ ] SessionPanel: show error state when heartbeat fails (not just clear session silently)
- [ ] TableBrowserPanel: distinguish "no tables" from "connection failed"
- [ ] DeploymentService: show balloon notification on deploy failure (currently may be silent)
```

---

## Feature Flag Approach

This plugin has no feature flag infrastructure. For safe incremental rollout of UI changes:

1. Use a `KeyscryptSettings` boolean field as a manual kill switch
2. Gate new empty state UI behind it during development
3. Remove the flag once validated in sandbox IDE

```kotlin
// Temporary flag in KeyscryptSettings for a new empty state experiment
var showNewAuthGatedState: Boolean = false  // default off

// In TableBrowserPanel:
private fun componentShown() {
    val settings = KeyscryptSettings.getInstance()
    if (settings.showNewAuthGatedState && !session.isLoggedIn) {
        showAuthGatedState()
        return
    }
    loadTableList()
}
```

Remove the flag field from settings once the experiment ships permanently — don't leave dead flags.

---

## Testing Empty States Manually

No automated UI tests exist. Manual testing protocol for each state:

```
Auth-gated state:
- [ ] Log out via Session tab
- [ ] Open the panel under test
- [ ] Verify: auth-gated empty state visible, Login button present
- [ ] Click Login, complete login
- [ ] Verify: panel transitions to content state without requiring manual refresh

Unconfigured state:
- [ ] Clear keystoneServer in Settings → Keyscript IDE
- [ ] Close and reopen the panel
- [ ] Verify: unconfigured empty state visible, Settings link present
- [ ] Click Settings link, configure server, close dialog
- [ ] Verify: panel updates or shows correct next state

Loading state:
- [ ] Open panel for the first time (clear hasLoaded flag by restarting sandbox IDE)
- [ ] Verify: loading text appears before data arrives
- [ ] Verify: actions disabled during load
- [ ] Verify: status label updates to result count after load
```

---

## Proposed Enhancements

| Enhancement | Panel | Effort | Impact |
|-------------|-------|--------|--------|
| Auth-gate TableBrowser | TableBrowserPanel | S | High — prevents confusion on first open |
| "Getting Started" checklist panel | New or WorkspacePanel | M | High — onboarding activation |
| Session expiry banner in tool window | All panels | M | Medium — makes silent expiry visible |
| Proxy startup progress in Console | ConsolePanel | S | Medium — demystifies first run |
| Empty results illustration | SearchPanel, TableBrowserPanel | L | Low — aesthetic only |

See the **scoping-feature-work** skill for ticket breakdown and effort estimation.
See the **designing-onboarding-paths** skill for the "Getting Started" checklist design.
