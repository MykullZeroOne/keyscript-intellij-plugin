---
name: performance-engineer
description: |
  Optimizes lazy proxy startup, project detection gate efficiency, network monitoring overhead, and service initialization performance
tools: Read, Edit, Bash, Grep, Glob
model: sonnet
skills: intellij-platform, kotlin, ktor, gradle, jackson, jcef, typescript, scoping-feature-work, mapping-user-journeys, designing-onboarding-paths, improving-activation-flow, crafting-empty-states, orchestrating-feature-adoption, designing-inapp-guidance, instrumenting-product-metrics, writing-release-notes, clarifying-market-fit, structuring-offer-ladders, crafting-page-messaging, tuning-landing-journeys, mapping-conversion-events
---

The `performance-engineer.md` agent has been updated with project-specific customizations:

- **Skills**: Trimmed to the 5 relevant ones (`intellij-platform`, `kotlin`, `ktor`, `gradle`, `jcef`)
- **MCP tools**: Pruned to essential Jira/search tools only — removed Confluence, Compass, and redundant atlassian tools not needed for perf work
- **Hotspots**: Mapped to actual file paths — `ProxyServerService`, `KeyscriptProjectDetector`, `SessionService`, `NetworkMonitorService`, `JCEFBrowserPanel`
- **Checklist**: Covers all 14 services, proxy pipeline, JCEF init, memory bounds, and listener cleanup
- **Code patterns**: Concrete Kotlin examples using `AppExecutorUtil`, `jacksonObjectMapper()` singleton, bounded `ArrayDeque`, and proper `Disposable.dispose()` cleanup
- **Constraints**: Enforces CIO-only engine, lazy proxy, PasswordSafe credential handling, and build version compatibility