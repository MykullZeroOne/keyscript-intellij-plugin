# Full SDD workflow

## Configuration
- **Artifacts Path**: {@artifacts_path} → `.zenflow/tasks/{task_id}`

---

## Agent Instructions

If you are blocked and need user clarification, mark the current step with `[!]` in plan.md before stopping.

---

## Workflow Steps

### [x] Step: Requirements
<!-- chat-id: ae758b0a-67c3-49d1-bdbe-16bea8a0778d -->

Create a Product Requirements Document (PRD) based on the feature description.

1. Review existing codebase to understand current architecture and patterns
2. Analyze the feature definition and identify unclear aspects
3. Ask the user for clarifications on aspects that significantly impact scope or user experience
4. Make reasonable decisions for minor details based on context and conventions
5. If user can't clarify, make a decision, state the assumption, and continue

Save the PRD to `{@artifacts_path}/requirements.md`.

### [x] Step: Technical Specification
<!-- chat-id: 7fde206c-a24f-43e2-8cd9-e4b91bc570ec -->

Create a technical specification based on the PRD in `{@artifacts_path}/requirements.md`.

1. Review existing codebase architecture and identify reusable components
2. Define the implementation approach

Save to `{@artifacts_path}/spec.md` with:
- Technical context (language, dependencies)
- Implementation approach referencing existing code patterns
- Source code structure changes
- Data model / API / interface changes
- Delivery phases (incremental, testable milestones)
- Verification approach using project lint/test commands

### [x] Step: Planning
<!-- chat-id: ad25be48-6fe8-4d33-9480-996a29c45017 -->

Create a detailed implementation plan based on `{@artifacts_path}/spec.md`.

1. Break down the work into concrete tasks
2. Each task should reference relevant contracts and include verification steps
3. Replace the Implementation step below with the planned tasks

Rule of thumb for step size: each step should represent a coherent unit of work (e.g., implement a component, add an API endpoint). Avoid steps that are too granular (single function) or too broad (entire feature).

Important: unit tests must be part of each implementation task, not separate tasks. Each task should implement the code and its tests together, if relevant.

If the feature is trivial and doesn't warrant full specification, update this workflow to remove unnecessary steps and explain the reasoning to the user.

Save to `{@artifacts_path}/plan.md`.

### [x] Step: Architecture and Plugin Structure Review
<!-- chat-id: 3e75b27b-f7e5-4d7e-b482-ed7b6da44e69 -->
- Review `plugin.xml` for extensions, actions, and dependencies.
- Analyze service scoping (App vs Project) for `AuthenticationManager`, `ScriptOptionsService`, `KeyscriptSettingsService`.
- Check lifecycle management and `Disposable` usage.

### [x] Step: Security and State Management Review
<!-- chat-id: 9b88c473-77a5-4e36-b4dc-e3cd334ee92e -->
- Audit credential handling in `LoginDialog` and `KeybridgeClient`.
- Verify `PersistentStateComponent` usage in `KeyscriptSettingsService`.
- Check for plain-text storage of sensitive data (passwords, session IDs).

### [x] Step: UI/UX and Integration Review
<!-- chat-id: b972edcf-2a78-47f2-8564-658a042203ff -->
- Evaluate `ScriptOptionsToolWindow` and `LoginDialog` integration.
- Check `RunKeyscriptAction` context awareness.
- Assess `JCEFBrowserManager` for native browser integration opportunities.

### [x] Step: Performance and Threading Review
<!-- chat-id: 63a76b2b-4fc2-4517-8769-e14e701b91fd -->
- Search for EDT violations in network calls (`KeybridgeClient`, `KeystoneClient`).
- Analyze background task usage and coroutine implementation.

### [ ] Step: Marketplace Readiness and Plugin Verifier Audit
- Check compatibility between `build.gradle.kts` and `plugin.xml` (Build 243 vs 253).
- Review plugin metadata for Marketplace standards.

### [ ] Step: Final Report Generation
- Compile findings into the requested sections (Executive Summary, Compliance, etc.).
- Provide prioritized remediation plan and final verdict.
