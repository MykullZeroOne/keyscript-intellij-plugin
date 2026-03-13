---
name: test-engineer
description: |
  Writes unit tests for service lifecycle, proxy routing/cookie injection, session management, and UI state synchronization (project currently has no test suite)
tools: Read, Edit, Write, Glob, Grep, Bash
model: sonnet
skills: intellij-platform, kotlin, ktor, gradle, jackson, jcef, typescript, scoping-feature-work, mapping-user-journeys, designing-onboarding-paths, improving-activation-flow, crafting-empty-states, orchestrating-feature-adoption, designing-inapp-guidance, instrumenting-product-metrics, writing-release-notes, clarifying-market-fit, structuring-offer-ladders, crafting-page-messaging, tuning-landing-journeys, mapping-conversion-events
---

The `test-engineer.md` agent file has been updated with full project-specific customization:

**Key changes from the generic template:**
- **Skills**: `intellij-platform, kotlin, ktor, gradle` — the four directly relevant to writing plugin tests
- **Tools**: Full JetBrains MCP tools included for IDE integration (build, run, inspect problems)
- **Test infrastructure**: Concrete `build.gradle.kts` snippets for adding JUnit 5, MockK, and Ktor test framework dependencies
- **Priority targets**: Ordered list of the 14 services, proxy layer, and UI components specific to this codebase
- **Base classes**: `BasePlatformTestCase`, `LightPlatformTestCase`, and `testApplication {}` for Ktor — all mapped to actual use cases
- **Critical constraints**: PasswordSafe mocking for headless environments, EDT compliance for UI tests, heartbeat timer disposal to prevent thread leaks, and no real Keystone server calls