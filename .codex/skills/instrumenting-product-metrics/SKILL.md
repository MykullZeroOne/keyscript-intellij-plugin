All 7 files are written. Here's what was created:

**`SKILL.md`** — Quick overview with the activation funnel, milestone-addition pattern, and network usage as engagement signal.

**`references/activation-onboarding.md`** — Deep dive into `OnboardingStateService.State`, the change-guard setter pattern, where milestones are fired (service layer, not UI), and listener wiring in `GettingStartedPanel`. Includes a copy-paste checklist.

**`references/engagement-adoption.md`** — Covers `SessionService.isLiveDatabase()` as a high-signal engagement indicator, `NetworkMonitorService` as a usage-depth proxy (with the 500-event cap warning), and the two patterns for feature adoption tracking (durable vs. ephemeral).

**`references/in-app-guidance.md`** — Documents all 5 guidance surfaces, the `shouldShowWelcome` state gate, `EmptyStatePanel` constructor and usage, and the `NotificationGroupManager` notification pattern with correct `NotificationType` usage.

**`references/product-analytics.md`** — Leads with the critical WARNING that there is no external analytics backend. Documents how to derive all currently-measurable metrics from `OnboardingStateService` and `SessionService`, and how to use `Logger` for logging-based analytics queryable from `idea.log`.

**`references/roadmap-experiments.md`** — Feature gating via `KeyscryptSettings`, in-process A/B variant assignment using username hash, and a validation workflow for confirming persistence via the XML state file.

**`references/feedback-insights.md`** — Checklist dismissal as dropout signal, error signals from `RunResult`/`DeployResult`, session heartbeat failure frequency as a TTL insight, and the WARNING against silencing errors that need user action.