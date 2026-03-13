# Activation & Onboarding Reference

## Contents
- First-Run Flow
- Project Auto-Detection Gate
- Settings Configuration as Onboarding
- Empty States
- Anti-Patterns

## First-Run Flow

The plugin's "activation" moment is: user opens a Keyscript project → plugin auto-detects it → tool windows appear → user configures Settings > Keyscript IDE → authenticates via status bar widget.

This entire flow is gated by `KeyscriptProjectDetector`. A feature is "activated" only after the user has a valid `JSESSIONID` in `SessionService`.

```kotlin
// KeyscriptProjectDetector determines if tool windows/services activate
// Detection criteria (ANY of these):
//   - keyscript.bundle.json at project root
//   - .keyscript marker file
//   - *.keyscript.js files
//   - *.js files with "// @keyscript" in first 5 lines

// When scoping onboarding features, ask:
// "Does this require an active session, or just project detection?"
// Session-required features cannot show until KeyscriptSettings.keystoneServer is set
```

## Project Auto-Detection Gate

MVP slice for any feature that surfaces to new users must specify **which detection state** triggers it:

```
Detection States:
  1. NOT_KEYSCRIPT_PROJECT  → plugin completely dormant; no UI shown
  2. KEYSCRIPT_DETECTED     → tool windows visible, settings may be empty
  3. SETTINGS_CONFIGURED    → keystoneServer + supportedInstances set
  4. AUTHENTICATED          → SessionService.isLoggedIn == true

Rule: Never show interactive features to users in state 1 or 2
      that require state 3 or 4 to function.
```

## Settings Configuration as Onboarding

The Settings > Keyscript IDE panel (`KeyscryptSettingsConfigurable`) is the primary onboarding surface. When scoping features that need new settings:

```kotlin
// DO: Add fields to KeyscryptSettings with sensible defaults
// Users shouldn't need to configure optional features to proceed

class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings> {
    var keystoneServer: String = ""           // Required — empty blocks auth
    var supportedInstances: String = ""       // Required — empty blocks instance selector
    var proxyPort: Int = 3000                 // Optional — default works for most users
    var servicePort: Int = 1337               // Optional — default works for most users
}

// AVOID: Required fields with no default that silently break features
// If a setting is required, validate it at the Settings panel level and
// show an inline error rather than letting the feature fail at runtime
```

## Empty States

Three tool windows have empty states that are part of the first-run experience:

| Tool Window | Empty State Trigger | What to Show |
|-------------|--------------------|----|
| Workspace | Not authenticated | "Log in to run scripts" + login button |
| Data Tools | Not authenticated | "Log in to browse tables" |
| Diagnostics | No runs yet | Console placeholder text |

When scoping a feature that adds a new tab or panel:

```kotlin
// Every new panel needs an explicit empty state defined in scope:
// AC: Given user is not authenticated, when Data Tools > [NewTab] is visible,
//     then show "[feature] requires an active session" with login CTA

// This prevents blank/broken panels reaching users who haven't completed onboarding
```

## Anti-Patterns

### WARNING: Assuming Settings Are Configured

**The Problem:**
```kotlin
// BAD — assumes keystoneServer is set
val url = "https://${settings.keystoneServer}/api/endpoint"
```

**Why This Breaks:** `keystoneServer` is empty string on first run. This produces `https:///api/endpoint` and a cryptic network error — not an onboarding prompt.

**The Fix:**
```kotlin
// GOOD — gate on settings validity before making API calls
if (settings.keystoneServer.isBlank()) {
    notifyUser("Configure Keystone server in Settings > Keyscript IDE")
    return
}
val url = "https://${settings.keystoneServer}/api/endpoint"
```

### WARNING: Skipping the Detection Gate

**The Problem:** Registering a service or action without a `condition` in `plugin.xml` means it activates in ALL projects, not just Keyscript ones.

**The Fix:** All tool windows and project services must include the Keyscript project condition. When scoping a new surface, explicitly call out in the AC: "Only visible in Keyscript projects."
