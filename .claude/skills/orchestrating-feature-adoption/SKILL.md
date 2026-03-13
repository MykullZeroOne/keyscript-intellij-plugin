7 files written to `.claude/skills/orchestrating-feature-adoption/`:

```
SKILL.md
references/activation-onboarding.md
references/engagement-adoption.md
references/in-app-guidance.md
references/product-analytics.md
references/roadmap-experiments.md
references/feedback-insights.md
```

Key findings surfaced from the codebase that shaped this skill:

**Biggest adoption gap:** All three tool windows use `secondary="true"` — they never auto-open. Users who install the plugin and log in will never see Data Tools or Diagnostics unless they know to look in View > Tool Windows. The `engagement-adoption.md` reference covers the one-time reveal pattern.

**Second gap:** No nudge chain exists between funnel stages. `KeyscryptProjectService.initialize()` just updates the status bar — it doesn't check if settings are configured, and nothing post-login points users toward running a script. `activation-onboarding.md` covers the full 4-stage chain.

**No analytics:** The `product-analytics.md` reference documents this explicitly as a warning and provides a zero-dependency structured logging pattern using the existing `Logger` infrastructure.