# Measurement and Testing Reference

## Contents
- What to measure for plugin copy
- Marketplace analytics
- A/B testing copy in-plugin
- Tracking activation via in-plugin events
- Validation criteria

## What to Measure

For an IntelliJ plugin, conversion metrics map to:

| Stage | Metric | Where measured |
|-------|--------|---------------|
| Discovery | Marketplace page views | JetBrains Marketplace analytics |
| Acquisition | Install count | Marketplace analytics |
| Activation | First script run | In-plugin event (if instrumented) |
| Retention | DAU/WAU | Marketplace stats (approximate) |
| Re-engagement | Update install rate | Marketplace analytics |

## Marketplace Analytics

JetBrains Marketplace provides plugin developers with:
- Install and uninstall counts per version
- Country and IDE version breakdown
- Rating and review trends

Access at: `plugins.jetbrains.com/plugin/[plugin-id]/analytics` (requires plugin ownership).

Use install velocity (installs/day in first 7 days post-release) as the primary signal for copy effectiveness on the Marketplace listing.

## A/B Testing In-Plugin Copy

IntelliJ plugins don't have a native A/B framework. For copy experiments, use feature flags via settings:

```kotlin
// KeyscriptSettings.kt — add experiment flag
var emptyStateVariant: String = "A"  // "A" or "B"

// In WorkspacePanel.kt
val emptyStateText = when (settings.emptyStateVariant) {
    "B" -> "Open a Keyscript file to see run options"
    else -> "No script selected. Open a .keyscript.js file to get started."
}
```

Deploy variant B to a subset of users by shipping with `emptyStateVariant = "B"` as default, then measuring session-to-run conversion via the Diagnostics console log count.

This is a rough proxy — not statistically rigorous, but better than nothing for a developer tool with a small user base.

## Tracking Activation via In-Plugin Events

The plugin currently logs to the IntelliJ console via `Logger`. To measure activation:

```kotlin
// In RunKeyscriptService.kt — first run event
private var hasTrackedFirstRun = false

fun runScript(file: VirtualFile) {
    if (!hasTrackedFirstRun) {
        hasTrackedFirstRun = true
        logger.info("ACTIVATION_EVENT: first_script_run user=${settings.keystoneServer}")
    }
    // ... run logic
}
```

These log lines can be scraped from support submissions or internal test runs to understand where users drop off. For production instrumentation, see the **instrumenting-product-metrics** skill.

## Copy Validation Criteria

Before shipping copy changes, validate against:

```
Copy Validation Checklist:
- [ ] Hero copy tested with one non-Keystone developer (do they understand what it does?)
- [ ] Settings labels have placeholder examples (not just field names)
- [ ] Error messages include a resolution action, not just a description
- [ ] CHANGELOG entry uses capability headline, not "Fixed bug" format
- [ ] Marketplace description updated alongside README (no stale content)
- [ ] All JLabel text in tool windows reviewed for jargon
```

## Anti-Patterns

### WARNING: Measuring downloads as the only signal

**The Problem:** High install count with low activation (first run) means your acquisition copy oversells and your onboarding doesn't deliver.

**Why This Breaks:** Users uninstall quickly, hurting your Marketplace rating. Install count is a vanity metric without activation data alongside it.

**The Fix:** Pair install count with "days to first script run" — even a rough estimate from user research is more actionable than raw download numbers.

See the **mapping-conversion-events** skill for full funnel instrumentation strategy.
