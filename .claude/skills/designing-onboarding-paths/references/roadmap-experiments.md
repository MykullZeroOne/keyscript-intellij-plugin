# Roadmap & Experiments Reference

## Contents
- Onboarding Improvements Backlog
- Feature Flag Pattern for Experimental UI
- Phased Rollout via Plugin Versioning
- A/B Approach Without Analytics Infrastructure
- Prioritization Framework

---

## Onboarding Improvements Backlog

Ordered by impact-to-effort ratio based on the current activation funnel gaps:

| Priority | Improvement | Effort | Impact |
|----------|-------------|--------|--------|
| P0 | Empty-state panels in all tool windows | Small | Blocks silent drop-off |
| P0 | First-run notification when server not configured | Small | Eliminates zero-guidance install |
| P1 | "Test Connection" button in settings | Small | Closes configure → verify loop |
| P1 | Inline hint text for server/instances fields | Small | Reduces misconfiguration |
| P2 | Getting Started tab in Workspace tool window | Medium | Guides to first run |
| P2 | OnboardingStateService with milestone flags | Medium | Enables progressive disclosure |
| P3 | Configuration validation on save | Medium | Surfaces bad config immediately |
| P3 | Post-login Data Tools discovery notification | Small | Increases Data Tools adoption |
| P4 | Structured telemetry (local JSONL) | Medium | Enables data-driven iteration |

Use the **scoping-feature-work** skill to convert these into Jira tickets with acceptance criteria.

---

## Feature Flag Pattern for Experimental UI

For onboarding UI experiments, gate behind a boolean in `KeyscryptSettings` rather than a
separate feature flag system — the settings already provide PersistentStateComponent storage:

```kotlin
// In KeyscryptSettings
var enableGettingStartedPanel: Boolean = true  // default on for new installs
var enableFirstRunNotification: Boolean = true
```

Users or QA can toggle these in Settings > Keyscript IDE to disable experimental UI without
rebuilding the plugin. Remove the flag and hardcode `true` once the feature is validated.

```kotlin
// In WorkspaceToolWindowFactory
if (KeyscryptSettings.getInstance().enableGettingStartedPanel) {
    tabbedPane.insertTab("Getting Started", null, gettingStartedPanel, null, 0)
}
```

---

## Phased Rollout via Plugin Versioning

Since this is a JetBrains Marketplace plugin (or distributed via ZIP), rollout is controlled
by which version users install. There is no gradual percentage rollout without external infra.

Practical approach:
1. Ship experimental UI in a **pre-release** ZIP distributed to a pilot group
2. Collect qualitative feedback via the Diagnostics panel or direct user interviews
3. Promote to main version once validated

Build the pre-release ZIP:
```bash
./gradlew buildPlugin -Pversion=2.1.0-rc1
# Output: build/distributions/keyscript-intellij-plugin-2.1.0-rc1.zip
```

Distribute the ZIP directly to pilot users — they install via Settings > Plugins > Install from Disk.

---

## A/B Approach Without Analytics Infrastructure

Without telemetry, A/B testing is qualitative. Use this process:

1. Build variant A (current behavior) and variant B (new onboarding UI)
2. Distribute variant B ZIP to 3–5 users new to the plugin
3. Observe: do they reach first login without help? Do they discover Data Tools?
4. Decision criteria: if >80% complete first run unaided, ship variant B

Track outcomes via the `OnboardingStateService` dump in Diagnostics (see product-analytics.md).

```kotlin
// Add to DiagnosticsPanel "Support Info" tab
add(JTextArea(project.service<OnboardingStateService>().dumpFunnelState()).apply {
    isEditable = false
    font = JBFont.regular().asItalic()
})
```

---

## Prioritization Framework

When deciding which onboarding improvement to build next, use this decision tree:

```
Is there a gate where users get NO guidance on failure?
  YES → Fix that empty state first (P0)
  NO  ↓
Does the user know what to do after completing the current step?
  NO  → Add contextual notification or inline hint (P1)
  YES ↓
Is there a feature users should adopt but don't discover?
  YES → Add post-activation discovery nudge (P2)
  NO  → Instrument telemetry to confirm assumptions (P3)
```

Use the **mapping-user-journeys** skill to systematically audit each gate before prioritizing.
Use the **scoping-feature-work** skill to write acceptance criteria once a priority is chosen.
