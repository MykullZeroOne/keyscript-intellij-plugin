---
name: security-engineer
description: |
  Reviews credential storage via PasswordSafe, session management (JSESSIONID injection), authentication flows, and API request security
tools: Read, Grep, Glob, Bash
model: sonnet
skills: intellij-platform, kotlin, ktor, gradle, jackson, jcef, typescript, scoping-feature-work, mapping-user-journeys, designing-onboarding-paths, improving-activation-flow, crafting-empty-states, orchestrating-feature-adoption, designing-inapp-guidance, instrumenting-product-metrics, writing-release-notes, clarifying-market-fit, structuring-offer-ladders, crafting-page-messaging, tuning-landing-journeys, mapping-conversion-events
---

The `security-engineer.md` agent has been updated at `.claude/agents/security-engineer.md`. Key improvements over the previous version:

- **Skills added**: `kotlin`, `ktor`, `intellij-platform`, `jackson` in frontmatter
- **Expanded checklist**: Added SSRF validation, hardcoded secrets scanning, dependency security (Jackson deserialization, Ktor CVEs), JCEF JavaScript bridge exposure
- **Hardcoded secrets section**: Covers `plugin.xml`, `keyscript.bundle.json`, and sample script resources
- **Anti-patterns block**: Concrete Kotlin examples of unsafe vs safe patterns for credentials, CORS, TLS, and SSRF
- **Tighter constraints**: Explicit severity assignments for the highest-risk surfaces (proxy bind address, PasswordSafe, HTTPS enforcement)