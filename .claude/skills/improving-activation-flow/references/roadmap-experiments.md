# Roadmap & Experiments Reference

## Contents
- Prioritized Activation Improvements
- Experiment Patterns for Plugin Features
- Feature Flag Approach
- Rollout Checklist
- Scoping with Jira

---

## Prioritized Activation Improvements

Ranked by estimated impact vs. implementation effort:

| Priority | Improvement | Effort | Impact |
|----------|------------|--------|--------|
| P0 | Notify users when settings are at defaults (wrong server) | Low | High — blocks all activation |
| P0 | Empty state in tool windows when not logged in | Low | High — silent failure today |
| P1 | First-run setup prompt with link to settings | Medium | High — removes guesswork |
| P1 | "Setup Required" status bar text when server unconfigured | Low | Medium — clear signal |
| P2 | Structured event logging for funnel analytics | Medium | Medium — enables measurement |
| P2 | Device ID persistence improvement (suggest from system) | Medium | Medium — reduces login friction |
| P3 | In-line settings validation before saving | Medium | Low — prevents misconfiguration |
| P3 | Post-login "What's Next" panel in Workspace | Medium | Low — feature discovery |

---

## Experiment Patterns for Plugin Features

IntelliJ plugins don't have a built-in feature flag system. For controlled rollouts, use `KeyscryptSettings` as the flag store:

```kotlin
// Add experimental flags to KeyscryptSettings.State
data class State(
    // ... existing fields ...
    var enableSetupWizard: Boolean = false,       // Opt-in setup wizard
    var enableActivationEvents: Boolean = false,   // Structured event logging
    var showEmptyStateGuidance: Boolean = true     // Empty state panels (on by default)
)
```

Then gate new features:

```kotlin
// In tool window factory
if (KeyscryptSettings.getInstance().showEmptyStateGuidance && !session.isLoggedIn) {
    return buildEmptyStatePanel(...)
}
```

**DO:** Default new guidance features to `true` (opt-out) rather than `false` (opt-in). Users who don't read release notes won't benefit from opt-in improvements.

**DON'T:** Gate core functionality (login, run, deploy) behind feature flags. Only gate UI improvements and telemetry.

---

## Feature Flag Approach

For a team-wide rollout, pre-configure flags via a shared `KeyscryptSettings.xml` in the repo:

```xml
<!-- .idea/KeyscryptSettings.xml — commit to repo for team-wide defaults -->
<application>
  <component name="com.keyscript.plugin.settings.KeyscryptSettings">
    <option name="proxyEndpoint" value="keystonedev.yourcompany.com:8443" />
    <option name="supportedInstances" value="Development,Test,Production" />
    <option name="showEmptyStateGuidance" value="true" />
    <option name="enableActivationEvents" value="true" />
  </component>
</application>
```

This eliminates the wrong-default problem at the root: team members who clone the repo get correct settings without any manual configuration.

---

## Rollout Checklist

Use this checklist when shipping an activation improvement:

```
- [ ] Read the affected service code before changing it (SessionService, LoginAction, tool windows)
- [ ] Add empty state before adding new features (users must be able to see the empty state)
- [ ] Test in sandbox IDE: ./gradlew runIde
- [ ] Verify status bar widget appears in a Keyscript project
- [ ] Verify status bar widget does NOT appear in a non-Keyscript project
- [ ] Verify login dialog pre-fills saved credentials correctly
- [ ] Verify login failure shows error message (not silent failure)
- [ ] Verify tool window shows empty state when not logged in
- [ ] Verify tool window refreshes content after login
- [ ] Check IDE log for any exceptions during the activation flow
- [ ] Update CHANGELOG.md with the improvement
```

Validate each step before moving on:

1. `./gradlew runIde` — launches sandbox IDE
2. Open a Keyscript project (must have `keyscript.bundle.json`)
3. Verify status bar shows "KS: Not Logged In" (or "KS: Setup Required")
4. Click widget → login dialog appears with correct server
5. Login succeeds → status bar updates to "KS: username | instance"
6. Click gutter play button on a `.keyscript.js` file → preview loads
7. If any step fails, check the IDE log (`Help > Show Log in Finder`)

---

## Scoping with Jira

When filing activation improvement tickets, use the **scoping-feature-work** skill for proper MVP scoping. Key fields for activation work:

```
Epic: Activation Flow Improvements
Story: As a new Keyscript developer, I can configure the plugin and run my first script without reading documentation

Acceptance Criteria:
- [ ] Opening a Keyscript project shows "KS: Setup Required" if server is unconfigured
- [ ] Clicking the widget opens a notification with a direct link to Settings
- [ ] After configuring settings and logging in, tool windows show content (not blank panels)
- [ ] First script run completes within 10 seconds of clicking gutter play

Out of Scope (follow-on):
- Automated settings discovery from network
- Multi-server support in single login dialog
```

For Jira ticket creation, see the **atlassian:spec-to-backlog** skill.
