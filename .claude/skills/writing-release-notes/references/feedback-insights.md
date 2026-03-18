# Feedback & Insights — Release Note Writing Guide

This reference covers how to write release notes that acknowledge user pain points, document
fixes driven by real issues, and communicate known limitations honestly in Keyscript IDE.

---

## Why Feedback-Driven Framing Matters

Users who reported a bug or worked around a limitation are the most invested readers of a
release note. When a fix addresses a known pain point, the release note should:
1. Describe the symptom the user experienced (not the internal bug cause).
2. State the condition under which it happened.
3. Confirm the fix in a way the user can verify.

---

## Bug Fix Entry Patterns

### Pattern 1 — Symptom + condition + verification

```
- Fixed blank tool window panels when opening a Keyscript project in the dark IDE theme.
  All panels now paint correctly with the correct background color in both light and dark themes.
```

Not: "Fixed `isOpaque = false` causing transparent panels." — this describes the cause,
not what the user saw.

### Pattern 2 — Silent failure made visible

```
- Fixed the status bar widget not opening the login dialog when clicked. The click
  handler was receiving an empty DataContext, so the action silently did nothing.
  Clicking "KS: Not Logged In" now reliably opens the login dialog.
```

### Pattern 3 — Multi-condition fix

When a bug only appeared under a specific combination of conditions, name them both:

```
- Fixed IDE freeze during login in projects where IntelliJ PasswordSafe access
  and session listener dispatch both occurred on the Event Dispatch Thread. Login
  now runs off the EDT with no UI blocking.
```

---

## Known Keyscript Pain Points and How to Frame Fixes

### Proxy Port Conflicts

The proxy defaults to port 3000, which is commonly used by other development servers.

Fix entry pattern:
```
- Fixed proxy startup failure when port 3000 is occupied by another process.
  The error now displays the specific port number and directs users to change
  it in Settings > Keyscript IDE > Proxy Port.
```

Known limitation entry (if not yet fixed):
```
**Known Issue**: If port 3000 is in use at plugin startup, the proxy fails silently
and script runs return no output. Workaround: change the proxy port to an available
port in Settings > Keyscript IDE before running a script.
```

### Session Timeout Behavior

SessionService monitors session validity via heartbeat. When the session expires:
- The status bar widget should update from `KS: [username]` to `KS: Session Expired`.
- If credentials are saved, auto-relogin should trigger.
- If auto-relogin fails, a balloon notification should fire.

Fix entry pattern:
```
- Fixed session expiry not updating the status bar widget. The widget now transitions
  to "KS: Session Expired" within one heartbeat cycle (~30 seconds) of expiry,
  rather than remaining on the last-known logged-in state indefinitely.
```

Known limitation entry:
```
**Known Issue**: Auto-relogin after session expiry requires saved credentials in
IntelliJ PasswordSafe. If credentials were not saved at login time, the session
expires silently. A future release will prompt for re-entry when no saved credentials
are found.
```

### Gutter Icon Detection Edge Cases

Gutter icons appear on line 1 of files that match `@keyscript` or `.keyscript.js`. Edge cases:

Fix entry pattern:
```
- Fixed gutter run icons not appearing on files where the `// @keyscript` comment
  appears after blank lines at the top of the file. Detection now scans the first
  5 non-empty lines rather than strictly line 1.
```

Known limitation entry:
```
**Known Issue**: Gutter icons do not appear on files opened before the Keyscript
plugin activates (e.g., files from the "Recent Files" list opened before a Keyscript
project is fully loaded). Close and reopen the file to trigger gutter icon rendering.
```

### Script Execution 404 Errors

The proxy must strip `/Keyscript_IDE/` from iframe-relative paths before forwarding.

Fix entry pattern:
```
- Fixed script execution returning 404 from Keystone. The proxy was forwarding the
  full iframe path (`/instance/Keyscript_IDE/RunScript`) without stripping the
  `/Keyscript_IDE/` segment, which Keystone does not recognize. Paths are now
  normalized before forwarding.
```

---

## Writing "Known Issues" and "Known Limitations" Sections

Add a `### Known Issues` subsection for issues that ship unfixed. Use this format:

```markdown
### Known Issues

- **[Surface]**: [What the user sees]. Workaround: [what to do]. Fix planned for v[X.Y].
```

Example:
```markdown
### Known Issues

- **Proxy**: If port 3000 is in use, the proxy starts silently and script runs produce
  no output. Workaround: set a different port in Settings > Keyscript IDE > Proxy Port.

- **Gutter Icons**: Icons do not render on files opened before plugin activation.
  Workaround: close and reopen the file. Fix planned for v2.1.
```

---

## Tone for Feedback-Driven Entries

- Use "Fixed" for confirmed fixes. Use "Addressed" for partial mitigations.
- Never minimize a user-reported bug: "minor display glitch" obscures a real problem.
- For fixes to issues that caused data loss or workflow interruption, lead with the user
  impact before the technical description.
- When a fix resolves a long-standing workaround, say so:
  "This fix removes the need to restart the IDE after changing the server URL."
