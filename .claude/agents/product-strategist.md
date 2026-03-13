---
name: product-strategist
description: |
  In-product journeys, activation, and feature adoption for app flows
tools: Read, Edit, Write, Glob, Grep
model: sonnet
skills: intellij-platform, kotlin, ktor, gradle, jackson, jcef, typescript, scoping-feature-work, mapping-user-journeys, designing-onboarding-paths, improving-activation-flow, crafting-empty-states, orchestrating-feature-adoption, designing-inapp-guidance, instrumenting-product-metrics, writing-release-notes, clarifying-market-fit, structuring-offer-ladders, crafting-page-messaging, tuning-landing-journeys, mapping-conversion-events
---

The `product-strategist.md` agent has been written to `.claude/agents/`. Here's what was customized for this project:

**Activation funnel table** — maps all 4 gates (detect → configure → authenticate → run) to exact file locations, with the current broken state of each gate called out.

**Key surfaces** — exact file paths for every touchable product surface: status bar widget, 3 tool windows, 5 core services, settings files.

**Ground rules grounded in the codebase** — uses only existing IntelliJ APIs (`NotificationGroupManager`, `PropertiesComponent`, `JBLabel`), preserves the `CopyOnWriteArrayList` listener pattern, no third-party analytics.

**Analytics stopgap** — since v2.0.0 has zero instrumentation, structured `[keyscript.*]` log lines in `idea.log` are the pragmatic approach until a proper telemetry layer exists.

**P0–P2 gap backlog** — 7 prioritized known issues surfaced from the codebase, from silent empty states blocking new users (P0) to undiscovered Data Tools (P2).

**Skills loaded:** `scoping-feature-work`, `mapping-user-journeys`, `designing-onboarding-paths`, `improving-activation-flow`, `crafting-empty-states`, `orchestrating-feature-adoption`, `designing-inapp-guidance`, `instrumenting-product-metrics`, `writing-release-notes`, `mapping-conversion-events`.