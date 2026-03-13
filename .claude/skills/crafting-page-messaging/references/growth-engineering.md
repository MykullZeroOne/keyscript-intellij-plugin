# Growth Engineering Reference

## Contents
- Growth levers for an IntelliJ plugin
- Referral and word-of-mouth surfaces
- In-plugin upgrade and discovery prompts
- Cross-promotion with Keystone ecosystem
- Anti-patterns

## Growth Levers for an IntelliJ Plugin

Plugin growth follows a tight loop for developer tools:

```
Organic search (JetBrains Marketplace + GitHub)
  → Install
    → First value moment (first successful script run)
      → Habitual use
        → Word of mouth (team installs, Slack mentions)
          → More organic search
```

No paid channel. Growth is entirely content + product quality.

## Referral and Word-of-Mouth Surfaces

### Post-activation referral prompt

After the user successfully deploys a script for the first time, show a low-friction referral suggestion:

```kotlin
// In DeploymentService.kt — after successful deploy
if (deployCount == 1) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript")
        .createNotification(
            "Script deployed",
            "${scriptName} is live on ${instance}. Share the plugin with your team?",
            NotificationType.INFORMATION
        )
        .addAction(BrowseAction(
            "Share with team",
            "https://plugins.jetbrains.com/plugin/[plugin-id]"
        ))
        .notify(project)
}
```

Timing: after first **successful** deploy, not just first run. That's the moment of maximum satisfaction.

### GitHub README social proof

Add a "Used by" or rating badge if the plugin accumulates reviews:

```markdown
[![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/[plugin-id])](...)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/[plugin-id])](...)
[![Rating](https://img.shields.io/jetbrains/plugin/r/stars/[plugin-id])](...)
```

These badges update automatically and serve as passive social proof for evaluators.

## In-Plugin Discovery Prompts

Surface features users haven't discovered yet — without being annoying.

```kotlin
// Show once, after 5 script runs, if user hasn't used Query Builder
private val RUN_THRESHOLD = 5

fun onScriptRun() {
    val runCount = state.scriptRunCount++
    if (runCount == RUN_THRESHOLD && !state.hasOpenedQueryBuilder) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript")
            .createNotification(
                "Tip: Query Builder available",
                "Browse and query Keystone tables directly from the Data Tools panel.",
                NotificationType.INFORMATION
            )
            .addAction(OpenDataToolsAction())
            .notify(project)
    }
}
```

Rules for discovery prompts:
- Show each tip **once only** — persist seen state
- Trigger on a meaningful action (not on IDE open)
- One action button, clearly labeled
- Dismissible without penalty

See the **orchestrating-feature-adoption** skill for a full feature discovery sequencing strategy.

## Cross-Promotion with Keystone Ecosystem

The plugin's growth is tied to Keystone server adoption. Copy that acknowledges the broader ecosystem reinforces value:

```markdown
<!-- README — ecosystem framing -->
## Works with your Keystone setup

Keyscript IDE connects to any Keystone instance — Development, Test, or Production.
Configure multiple environments and switch between them from the run options panel.
```

If the Keystone server has its own documentation site, request a backlink to the plugin's Marketplace page — that referral traffic converts well.

## Anti-Patterns

### WARNING: Growth prompts on first launch

**The Problem:** Showing "Share this plugin" or "Rate us" before the user has experienced value.

**Why This Breaks:** You're asking for a favor before delivering one. Users dismiss immediately, and you've spent your one-time prompt on a conversion rate near zero.

**The Fix:** Gate all social/referral prompts behind a meaningful activation event. For this plugin: first successful deploy is the right trigger.

### WARNING: Feature discovery tooltips on every panel

Showing tips for every tool window on first open creates noise that users learn to ignore — and it undermines the tips you actually want them to see.

Show one tip per session maximum, sequenced by feature value. See the **designing-inapp-guidance** skill for tooltip placement strategy.
