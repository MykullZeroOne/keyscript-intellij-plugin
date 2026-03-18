# Feedback & Insights Reference

## Contents
- Current Feedback Channels
- Reading the IDE Log for Signal
- Session Expiry as a Feedback Signal
- Login Failure Analysis
- Triage Workflow for Activation Bugs

---

## Current Feedback Channels

The plugin has no in-product feedback mechanism. Insights come from:

| Source | What It Tells You | How to Access |
|--------|------------------|--------------|
| IDE log (`idea.log`) | Exceptions, `log.info/warn/error` calls | `Help > Show Log in Finder` in sandbox IDE |
| `KEYSCRIPT_EVENT:` log lines | Activation funnel events (if implemented) | Grep `idea.log` for `KEYSCRIPT_EVENT:` |
| User-reported issues | Login failures, blank panels, missing features | Direct developer feedback or Jira |
| Keystone server logs | 401/403 responses, session expiry patterns | Keystone admin access required |

---

## Reading the IDE Log for Signal

Every `log.info/warn` in the plugin writes to IntelliJ's `idea.log`. The key log signatures for activation issues:

```bash
# Find all Keyscript log lines in the current session
grep -i "keyscript\|keystone\|jsessionid\|session" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log | tail -100

# Find session expiry events
grep "Session expired\|Re-login\|Heartbeat" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log

# Find proxy startup issues
grep "ProxyServerService\|KtorProxyServer\|port" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log
```

Key log signatures and what they mean:

```
# GOOD — activation complete
Session established for jsmith: a3f7b2c1...
Keyscript IDE initializing for project: my-project

# WARN — session expiry (should auto-recover)
Session expired, attempting re-login...
Re-login successful for jsmith

# ERROR — activation blocked
Heartbeat check failed (network): Connection refused  → Keystone server unreachable
No saved credentials — user must login manually       → User didn't save password

# SILENT FAILURE — missing log entries
If there's no "Session established" line after login, check for exceptions above it
```

---

## Session Expiry as a Feedback Signal

`SessionService.handleSessionExpired()` is called when:
1. The 2-minute heartbeat detects a 401/403 from Keystone (`services/SessionService.kt:175`)
2. Any `KeystoneApiClient` call receives a session-expired response

Frequent session expiry indicates one of:
- Keystone session timeout is shorter than expected (check Keystone server config)
- Network issues causing false 401s (check heartbeat connectivity)
- Users leaving the IDE idle for hours (expected — auto-relogin handles it)

```kotlin
// The heartbeat check — if this fires frequently, users have a poor session experience
private fun checkSessionValid() {
    val status = conn.responseCode
    if (status == 401 || status == 403 || (!hasJsession && status != 200)) {
        log.info("Heartbeat: session appears expired (status=$status)")
        handleSessionExpired()  // Triggers auto-relogin if credentials saved
    }
}
```

**Insight action:** If users report being repeatedly asked to login despite saving credentials, check whether `PasswordSafe` is persisting credentials correctly. On macOS, credentials are stored in Keychain — verify the service name `"Keyscript/KeystoneLogin"` exists.

---

## Login Failure Analysis

When users report login failures, the `LoginDialog` shows the error text from `AuthenticationService.LoginResult.error`. Common failure modes:

| Error Pattern | Root Cause | Fix |
|--------------|-----------|-----|
| `Connection refused` | Wrong server/port in settings | Check `proxyEndpoint` in settings |
| `401 Unauthorized` | Wrong credentials | User must re-enter credentials |
| `SSL handshake failed` | HTTPS misconfiguration | Verify server uses HTTPS on port 8443 |
| `Login failed: null` | Unexpected response format | Check Keystone API response shape |
| Dialog shows nothing | Error not surfaced to UI | Check `AuthenticationService` error handling |

```kotlin
// In LoginDialog — error IS surfaced for auth failures
SwingUtilities.invokeLater {
    if (result.success) {
        close(OK_EXIT_CODE)
    } else {
        errorLabel.text = result.error ?: "Login failed"  // Shows error to user
        errorLabel.isVisible = true
        isOKActionEnabled = true
    }
}
```

The dialog correctly shows errors. If users report "nothing happens on login," the issue is likely a network timeout (no response at all) rather than an auth failure.

---

## Triage Workflow for Activation Bugs

When a user reports an activation issue, follow this sequence:

```
- [ ] Ask: Which step fails? (project detected / settings / login / first run)
- [ ] Ask: What does the status bar show? ("KS: Not Logged In" / missing entirely)
- [ ] Get IDE log: Help > Show Log in Finder → share idea.log
- [ ] Search log for: "Keyscript", "Session", "Proxy", "KeystoneApi"
- [ ] Identify: Is there a stack trace near the failure point?
- [ ] Check: Is `keyscript.bundle.json` present at project root?
- [ ] Check: Is proxyEndpoint set to a reachable host?
- [ ] Reproduce: ./gradlew runIde → simulate their setup
- [ ] If proxy issue: check port 3000 is free (`lsof -i :3000`)
```

For complex session bugs (intermittent login drops), use the **atlassian:triage-issue** skill to search for existing Jira tickets before filing a new one.

**DO:** Always reproduce in the sandbox IDE (`./gradlew runIde`) before concluding a fix. Session and proxy bugs depend on timing that's hard to reason about statically.

**DON'T:** Accept "it works on my machine" as a resolution. The most common activation bugs are environment-specific (wrong server endpoint, port conflict, network policy blocking proxy port 3000).
