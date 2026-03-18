# Conversion Optimization Reference

## Contents
- Conversion surfaces in this plugin
- Above-the-fold hierarchy
- CTA placement patterns
- Anti-patterns
- Validation checklist

## Conversion Surfaces in This Plugin

The plugin has two distinct conversion funnels:

1. **Acquisition funnel** — GitHub README / Marketplace listing → Install
2. **Activation funnel** — Post-install → First successful script run

Treat these separately. Acquisition copy sells the install. Activation copy reduces time-to-value.

## Above-the-Fold Hierarchy (README / Marketplace)

```markdown
# [What it is — noun + differentiator]         ← H1: 6-10 words
[One sentence: who it's for + what it solves]   ← Subhead: under 20 words
[Install badge or CTA]                           ← Primary action
[3 proof bullets]                                ← Secondary proof
[Screenshot/GIF]                                 ← Visual trust
```

Example applying to this plugin:
```markdown
# Keyscript IDE for IntelliJ IDEA

Write, run, and deploy Keyscript scripts directly in IntelliJ —
no Electron app, no context switching.

[![Install](https://img.shields.io/badge/JetBrains-Install-orange)](...)

- CR framework completions with full type support
- Live JCEF browser preview, session-authenticated via proxy
- One-click deploy to any Keystone instance
```

## CTA Placement Patterns

### Primary CTA: Install / Get Plugin
- Place immediately after the hero subhead
- One CTA per screen section — don't dilute with "Learn More" siblings
- Use the JetBrains Marketplace badge as the primary trigger

### Secondary CTAs: In-plugin actions
In UI panels, secondary CTAs guide to first value:

```kotlin
// Empty Workspace panel — guide to first run
val emptyState = JPanel().apply {
    add(JLabel("No script selected"))
    add(JLabel("Open a .keyscript.js file to see run options here."))
    // Link-style button:
    add(ActionLink("Browse project files") { /* open file tree */ })
}
```

### Tertiary CTAs: Settings completion
When the server isn't configured, lead with the gap and the fix:

```kotlin
// BAD: error tone
JLabel("Error: No server configured")

// GOOD: directional
JLabel("Add your Keystone server in Settings > Keyscript IDE to get started.")
```

## Anti-Patterns

### WARNING: Multiple competing CTAs

**The Problem:**
```markdown
[Install Plugin] [View Docs] [See Changelog] [Star on GitHub]
```

**Why This Breaks:** Choice paralysis. Each additional CTA reduces click rate on all of them. For an evaluator, one decision matters: install or not.

**The Fix:** One primary CTA. Surface secondary links below the fold or in a "Resources" section.

### WARNING: Feature list without context

**The Problem:**
```markdown
- Proxy server
- Code completions
- Tool windows
- Run configurations
```

**Why This Breaks:** Features without outcomes don't create desire. Developers reading this don't know if any of it solves their problem.

**The Fix:**
```markdown
- Run scripts without leaving IntelliJ (embedded proxy handles auth)
- Get completions for the full CR framework API
- See JCEF preview side-by-side with your code
- Run configs with gutter icons — like any other IntelliJ project
```

## Validation Checklist

Copy this checklist before publishing/merging README or Marketplace changes:

- [ ] H1 names the tool and its differentiator (not just "Keyscript Plugin")
- [ ] Subhead names the ICP (Keystone developers) and the job-to-be-done
- [ ] Primary CTA is above the fold on desktop
- [ ] Maximum one primary CTA per screen section
- [ ] Every feature bullet leads with the user outcome, not the implementation
- [ ] No internal terms without explanation (JSESSIONID, CIO, JCEF)
- [ ] Screenshot or GIF shows the running plugin, not a blank install screen

See the **clarifying-market-fit** skill for ICP and positioning review before running this checklist.
