# Full SDD workflow

## Configuration
- **Artifacts Path**: {@artifacts_path} → `.zenflow/tasks/{task_id}`

---

## Agent Instructions

If you are blocked and need user clarification, mark the current step with `[!]` in plan.md before stopping.

---

## Workflow Steps

### [x] Step: Requirements

Create a Product Requirements Document (PRD) based on the feature description.

1. Review existing codebase to understand current architecture and patterns
2. Analyze the feature definition and identify unclear aspects
3. Ask the user for clarifications on aspects that significantly impact scope or user experience
4. Make reasonable decisions for minor details based on context and conventions
5. If user can't clarify, make a decision, state the assumption, and continue

Save the PRD to `{@artifacts_path}/requirements.md`.

### [x] Step: Technical Specification
<!-- chat-id: 988bdd7f-04c7-4f71-b552-3b6ae2227ae3 -->

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
<!-- chat-id: d3ddafaa-7723-4e35-9a11-fd908855c791 -->

Create a detailed implementation plan based on `{@artifacts_path}/spec.md`.

1. Break down the work into concrete tasks
2. Each task should reference relevant contracts and include verification steps
3. Replace the Implementation step below with the planned tasks

Rule of thumb for step size: each step should represent a coherent unit of work (e.g., implement a component, add an API endpoint). Avoid steps that are too granular (single function) or too broad (entire feature).

Important: unit tests must be part of each implementation task, not separate tasks. Each task should implement the code and its tests together, if relevant.

If the feature is trivial and doesn't warrant full specification, update this workflow to remove unnecessary steps and explain the reasoning to the user.

Save to `{@artifacts_path}/plan.md`.

### [x] Step: Project Initialization
<!-- chat-id: b47cf5b1-c458-486d-a782-971440c095c2 -->
- Initialize Gradle project with `build.gradle.kts` and `settings.gradle.kts`.
- Set up `plugin.xml` with basic metadata.
- Create core directory structure: `com.revfcu.keyscript.{actions,api,auth,options,settings}`.
- **Verification**: Run `./gradlew build` to ensure the project skeleton is valid.

### [x] Step: Keybridge API Client Implementation
<!-- chat-id: f9222ff8-5fdb-42a4-a81e-f0d2b07d8e25 -->
- Implement `KeybridgeClient.kt` using OkHttp to handle the `logon` query.
- Define data models for Logon request/response.
- **Verification**: Add unit tests in `KeybridgeClientTest.kt` with mocked API responses.

### [ ] Step: Authentication & Session Management
- Implement `AuthenticationManager.kt` as a service to manage `sessionId`.
- Create `LoginDialog.kt` UI to capture server URL, username, and password.
- Implement a login action/button.
- **Verification**: Manually test the login flow and verify the `sessionId` is correctly stored in memory.

### [ ] Step: Script Options & Tool Window
- Implement `ScriptOptionsService.kt` using `PersistentStateComponent` to save serials (person, account, etc.).
- Implement `ScriptOptionsToolWindow.kt` to allow users to view/edit these serials.
- Register the tool window in `plugin.xml`.
- **Verification**: Verify that values entered in the tool window persist across IDE restarts.

### [ ] Step: Execution Engine Integration
- Implement `RunKeyscriptAction.kt` and register it in the context menu for `.js` files.
- Implement payload construction logic combining `sessionId` and `scriptOptions`.
- Implement the "Run" trigger (opening the Keystone execution URL in a browser/tab).
- **Verification**: Verify that right-clicking a `.js` file and selecting "Run as Keyscript" correctly passes parameters to the target URL.

### [ ] Step: Plugin Settings & Refinement
- Implement `KeyscriptSettingsConfigurable.kt` for global settings (e.g., default Keybridge URL).
- Refine UI components and error handling.
- **Verification**: Run `check` and `verifyPlugin` Gradle tasks.
