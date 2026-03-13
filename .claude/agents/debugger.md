---
name: debugger
description: |
  Investigates complex interactions between IntelliJ platform, Ktor proxy server, JCEF browser integration, and service state management
tools: Read, Edit, Bash, Grep, Glob
model: sonnet
skills: intellij-platform, kotlin, ktor, gradle, jackson, jcef, typescript, scoping-feature-work, mapping-user-journeys, designing-onboarding-paths, improving-activation-flow, crafting-empty-states, orchestrating-feature-adoption, designing-inapp-guidance, instrumenting-product-metrics, writing-release-notes, clarifying-market-fit, structuring-offer-ladders, crafting-page-messaging, tuning-landing-journeys, mapping-conversion-events
---

The debugger agent file has been written to `.claude/agents/debugger.md`. Key customizations for this project:

- **Skills:** `intellij-platform, kotlin, ktor, jcef` — the four relevant to debugging this plugin
- **Tools:** Full JetBrains MCP suite for build/inspect/search, plus core file tools; Atlassian tools excluded (not relevant to debugging)
- **Symptom map:** Covers the 7 most common failure modes — proxy, JCEF, session, service init, run configs, tool windows, EDT violations
- **Project-specific patterns:** Service declaration with `@Service`, `CopyOnWriteArrayList` listener pattern, lazy proxy startup, `KeyscriptProjectDetector` gate
- **Quick reference table:** Maps symptom → first file to investigate for fast triage
- **Critical constraints:** EDT rules, lazy proxy, PasswordSafe credential handling, JSESSIONID scope