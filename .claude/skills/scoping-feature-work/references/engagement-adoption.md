# Engagement & Adoption Reference

## Contents
- Core Engagement Loop
- Feature Adoption Surfaces
- Scoping for Discoverability
- Adoption Slices vs Power Features
- Anti-Patterns

## Core Engagement Loop

The plugin's engagement loop is: **open script → run → see result in preview → iterate**. Features that tighten this loop have the highest adoption impact.

```
Engagement Funnel (in order of friction):
  1. Open Keyscript project         (auto-detected — zero friction)
  2. Authenticate via status bar    (one-click → login dialog)
  3. Open/select a script           (gutter icon or run config)
  4. Configure parameters           (Workspace > Run Options tab)
  5. Run script                     (gutter play button or Run menu)
  6. View result in preview         (JCEF split-editor or browser)
  7. Inspect network calls          (Diagnostics > Network Monitor)
```

When scoping a feature, explicitly state which funnel step it improves or unblocks.

## Feature Adoption Surfaces

| Surface | Class | Adoption Role |
|---------|-------|---------------|
| Status bar widget | `LoginStatusBarWidgetFactory` | Entry point — always visible |
| Gutter play icon | `KeyscriptRunLineMarkerContributor` | Contextual — triggers run |
| Workspace panel | `KeyscriptWorkspaceToolWindowFactory` | Daily-use — parameters, session |
| Data Tools panel | `KeyscryptDataToolsToolWindowFactory` | Power users — table browser, query |
| Run configurations | `KeyscriptRunConfigurationType` | CI/repeat users — saved configs |
| Code completions | `CRCompletionContributor` | Always-on — CR framework hints |

Features that land in high-visibility surfaces (status bar, gutter) get faster adoption. New features hidden in secondary panels need in-app guidance to drive discovery.

## Scoping for Discoverability

When scoping a feature that lives in a non-obvious location, include a discoverability slice:

```kotlin
// Example: New "Query Builder" tab in Data Tools
// MVP includes discoverability:

// Slice 1: Core functionality (QueryBuilderPanel — the actual feature)
// Slice 2: Discovery surface (badge or tooltip on Data Tools tab after first auth)
// Slice 3 (follow-on): Keyboard shortcut, right-click menu entry

// DO NOT ship Slice 1 without Slice 2 — features users can't find don't get adopted
```

## Adoption Slices vs Power Features

Not all features need to be adopted by all users. Categorize explicitly in scope:

```
Category: CORE (everyone needs this)
  - Session auth, running scripts, viewing preview
  - Scope these with zero-config defaults

Category: POWER (experienced Keyscript developers)
  - Query Builder, Network Monitor, custom run configs
  - Can require configuration; document in Diagnostics panel

Category: ADMIN (one-time setup)
  - Settings > Keyscript IDE configuration
  - Bundle management via BundleService
  - Scope these for correctness, not discoverability
```

### Checklist: Scoping a New Tool Window Tab

Copy this checklist when scoping any new tab in an existing tool window:

```
- [ ] Tab content defined: what does the empty state show?
- [ ] Tab title chosen: matches existing naming convention (Title Case, short)
- [ ] Auth gate defined: shown to unauthenticated users? What message?
- [ ] Category assigned: CORE, POWER, or ADMIN
- [ ] Discoverability plan: how does user find this tab?
- [ ] Follow-on features listed and explicitly deferred
```

## Anti-Patterns

### WARNING: Scoping "Nice to Have" into MVP

**The Problem:** Adding keyboard shortcuts, tooltips, and animation polish to an MVP slice that's already solving a hard technical problem (e.g., new proxy route) bloats the ticket and delays shipping.

**Why This Breaks:** The proxy route needs testing with real Keystone traffic. UI polish can be validated with static mocks. Mixing them means neither gets proper review.

**The Fix:**
```
MVP ticket: "Add /query-builder proxy route and panel skeleton"
  Acceptance criteria: Panel loads, route proxies correctly, empty state shows

Follow-on ticket: "Query Builder UX polish"
  Acceptance criteria: Loading indicators, error states, keyboard shortcuts
```

### WARNING: No Empty State in Scope

Every new panel ships to users before it has data. If the empty state isn't in scope, users see a blank white panel. Always include empty state AC in the MVP ticket, even if it's just a single label.
