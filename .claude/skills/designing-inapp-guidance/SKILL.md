All 7 files created at `.claude/skills/designing-inapp-guidance/`:

```
SKILL.md
references/
  activation-onboarding.md
  engagement-adoption.md
  in-app-guidance.md
  product-analytics.md
  roadmap-experiments.md
  feedback-insights.md
```

**What's in the skill:**

- **SKILL.md** — four ready-to-paste Kotlin patterns covering tooltips, balloon notifications, inline settings hints, and status bar copy; decision table mapping each guidance surface to the right Swing primitive
- **activation-onboarding.md** — `PropertiesComponent`-gated first-run balloon, config/login gate patterns, `plugin.xml` notification group registration, two named anti-patterns
- **engagement-adoption.md** — post-run Diagnostics nudge, post-deploy Table Browser nudge, post-action hint with action button, session listener hook for post-login guidance
- **in-app-guidance.md** — tooltip conventions (HTML support), settings inline hints with `UIUtil.getContextHelpForeground()`, status bar copy for all three states, gutter icon tooltip pattern, `?` help button pattern, copyable checklist
- **product-analytics.md** — `ActivationEvents` object with milestone tracking, structured log lines for `idea.log` grep, activation funnel table, instrumentation checklist
- **roadmap-experiments.md** — `FeatureFlags` via `PropertiesComponent`, staged rollout pattern, manual A/B copy approach, table of currently missing guidance surfaces
- **feedback-insights.md** — error notification with next-step + diagnostics link, network monitor guidance-on-first-error pattern, NEVER swallow exceptions rule, error→guidance mapping table, unsaved file pre-flight check