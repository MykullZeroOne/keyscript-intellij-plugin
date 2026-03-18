All 7 files created. Here's a summary of what was generated at `.Codex/skills/improving-activation-flow/`:

**SKILL.md** — Quick reference covering the 5-step activation funnel (project detected → widget visible → settings configured → first login → first run), with code examples from the real codebase and links to all reference files.

**references/activation-onboarding.md** — Deep dive into `KeyscryptProjectDetector`, settings bootstrapping (the wrong-default problem), `LoginDialog` UX, and the first-run gate. Includes a copy-paste checklist for verifying activation end-to-end.

**references/engagement-adoption.md** — Post-activation surfaces: Workspace/Data Tools/Diagnostics tool windows, auto-relogin as a retention mechanism, feature discovery gaps (Data Tools are invisible), and the deploy feedback problem.

**references/in-app-guidance.md** — Patterns for improving the status bar widget text ("Setup Required" vs "Click to Login"), `NotificationGroupManager` guidance toasts, empty-state panel factory pattern, and tooltip conventions.

**references/product-analytics.md** — Honest assessment: the plugin has zero analytics instrumentation. Provides a structured `ActivationEventLogger` pattern using existing `Logger`, 9 key funnel events to track, and a skeleton for opt-in telemetry using the already-present Ktor CIO client.

**references/roadmap-experiments.md** — Prioritized improvement backlog (P0-P3), feature flag approach via `KeyscryptSettings`, team-wide settings via committed `.idea/KeyscryptSettings.xml`, and a rollout checklist.

**references/feedback-insights.md** — How to read `idea.log` for activation signals, session expiry pattern analysis, login failure triage table, and a step-by-step bug triage workflow.