7 files written to `.claude/skills/designing-onboarding-paths/`:

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

Key decisions made based on the actual codebase:

- **Grounded in real code** — every pattern references existing files (`SessionPanel`, `KeyscryptProjectService$StartupActivity`, `AuthenticationService.notify()`, `createToolWindowShell()`)
- **Identified the real gap** — the plugin's 4-gate funnel (detect → configure → authenticate → run) has zero guidance at gates 3–5; `product-analytics.md` calls this out directly as a WARNING
- **No invented infrastructure** — no third-party analytics libs suggested; recommendations use `PersistentStateComponent` (already in the codebase) and the existing `NotificationGroupManager` pattern
- **Prioritized backlog** in `roadmap-experiments.md` ordered by impact/effort with the P0 items being the silent empty-state failures that block new users