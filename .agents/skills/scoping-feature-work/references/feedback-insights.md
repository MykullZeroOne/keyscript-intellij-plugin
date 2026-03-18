# Feedback & Insights Reference

## Contents
- Feedback Sources for This Plugin
- Translating Bug Reports into Scope
- Signal vs Noise
- Feedback-Driven Scoping Workflow
- Anti-Patterns

## Feedback Sources for This Plugin

The plugin is a developer tool for a specific Keystone server workflow, so user feedback comes from a narrow, technical audience:

| Source | Signal Type | Where to Look |
|--------|------------|---------------|
| Direct user reports | Specific bugs, workflow gaps | Internal Jira / team Slack |
| IDE crash logs | JVM exceptions, OOM | IntelliJ's own crash reporter |
| Diagnostics panel | Network failures, proxy errors | `KeyscriptDiagnosticsToolWindowFactory` — users may share screenshots |
| Support escalations | Auth/session issues, deploy failures | Keystone server team |
| Code review comments | Architecture concerns | PRs in this repo |

There is no in-app feedback button or structured feedback mechanism. The Diagnostics panel is the closest thing — users can screenshot the console + network monitor to share context with support.

## Translating Bug Reports into Scope

When a bug report arrives, extract scope from it using this pattern:

```
Bug Report: "Script runs fine locally but after leaving IntelliJ open overnight,
             Run Script does nothing the next morning"

Diagnosis questions:
  1. Which service owns this? → SessionService (heartbeat / session expiry)
  2. Is there a user-visible signal? → Status bar still shows "KS: username" (misleading)
  3. What's the silent failure? → Session expired, action disabled without explanation

Scope output:
  Bug fix ticket:
    Root cause: SessionService heartbeat detects expiry but status bar not updated
    AC: Given session expires overnight, status bar shows "KS: Not Logged In" within 60s
    AC: Given session expires, clicking Run shows "Session expired, click to log in"

  Separate UX ticket (follow-on):
    "Add reconnect-on-wakeup detection using PowerSaveMode listener"
```

## Signal vs Noise

Not all feedback warrants a ticket. Use this filter:

```
HIGH SIGNAL (file a ticket):
  - Reproducible: user can reproduce it consistently
  - Blocks workflow: run, deploy, or auth is broken
  - Affects multiple users: proxy port conflict, session expiry

MEDIUM SIGNAL (track but deprioritize):
  - Cosmetic: wrong icon, minor spacing
  - Workaround exists: "restart the proxy manually"
  - Single user: could be environment-specific

NOISE (close or defer):
  - Feature requests with no current user impact
  - Requests for features out of scope (e.g., "add git integration")
  - Reports without reproduction steps
```

## Feedback-Driven Scoping Workflow

When a cluster of related feedback arrives, use this workflow to translate it into a scoped ticket:

```
Step 1: Group reports by symptom
  → 3 reports about "blank preview after login" = one theme

Step 2: Identify the layer
  → Preview = JCEF panel or PreviewContentService or ProxyServerService?
  → Read the relevant service to find the failure point

Step 3: Write root cause hypothesis
  → "Proxy starts before session is established; iframe loads with no JSESSIONID"

Step 4: Write minimum AC to validate fix
  → "Given first login after IDE start, preview loads within 3s with valid content"

Step 5: Identify follow-on work separately
  → "Show loading spinner in preview while proxy is starting" (separate ticket)
```

Copy this checklist when processing a feedback batch:

```
- [ ] Group reports by symptom (not by reporter)
- [ ] Identify owner service for each symptom group
- [ ] Write root cause hypothesis per group
- [ ] Write minimum AC for the fix
- [ ] File bug ticket with reproduction steps
- [ ] File separate UX improvement ticket if applicable
- [ ] Link related tickets with "is caused by" / "improves" link types
```

## Anti-Patterns

### WARNING: Scoping from a Single Report

One user's workaround-described bug is often misdiagnosed. A user who says "the deploy button does nothing" might actually have a session expiry, a wrong instance name, or a network issue — not a deploy bug.

**The Fix:** Before scoping a bug fix, read the relevant service code (`DeploymentService`, `KeystoneApiClient`) to understand all the ways the operation can silently fail. Write AC that covers the actual failure mode, not just the reported symptom.

### WARNING: Reopening Closed Features Based on One Request

If a feature was explicitly scoped out in a previous ticket (e.g., "configurable retry count deferred to follow-on"), don't reopen it based on a single user request. Validate that it's a pattern before promoting it.
