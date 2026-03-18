# Product Analytics Reference

## Contents
- Current Analytics State
- What to Instrument
- Where to Hook Events
- Scoping Analytics Slices
- Anti-Patterns

## Current Analytics State

**WARNING:** The plugin has no analytics instrumentation as of v2.0.0. There are no telemetry hooks, event tracking, or usage metrics collected. This means feature adoption decisions are made without data.

When scoping any feature that involves understanding whether users actually use it, include an instrumentation slice. The JetBrains platform provides a telemetry framework via `FUSCounterUsageLogger` for plugin analytics.

## What to Instrument

Prioritize events that answer "is this feature working?" over "how often is it used?":

```
HIGH VALUE (instrument in MVP):
  - Authentication success / failure
  - Script run initiated / completed / failed
  - Deploy initiated / completed / failed
  - Proxy start success / failure
  - Session auto-reconnect triggered / success / failure

MEDIUM VALUE (instrument in follow-on):
  - Tool window tab selected (which tabs get used)
  - Settings panel opened
  - Run configuration created
  - Code completion accepted (CR framework)

LOW VALUE (skip unless debugging):
  - Preview panel loaded
  - Network monitor entries added
  - File opened in split editor
```

## Where to Hook Events

The natural event boundaries in this plugin are service method calls and listener notifications:

```kotlin
// Hook 1: RunKeyscriptService — script execution lifecycle
class RunKeyscriptService(private val project: Project) {
    fun runScript(file: VirtualFile, params: Map<String, String>) {
        // Instrument here: "script_run_initiated"
        // ...
        onSuccess {
            // Instrument here: "script_run_completed"
        }
        onError { error ->
            // Instrument here: "script_run_failed", property: error.type
        }
    }
}

// Hook 2: SessionService — auth state transitions
// Instrument each state transition: login, logout, session_expired, reconnect_success
```

```kotlin
// Hook 3: DeploymentService — deploy lifecycle
class DeploymentService(private val project: Project) {
    fun deploy(scriptPath: String, instance: String) {
        // Instrument: "deploy_initiated", properties: {instance}
        // ...
        // Instrument on result: "deploy_completed" or "deploy_failed"
    }
}
```

## Scoping Analytics Slices

When scoping instrumentation for a feature, use this template in the ticket:

```markdown
## Analytics Requirements

**Events to capture:**
| Event Name | Trigger | Properties |
|-----------|---------|------------|
| script_run_initiated | RunKeyscriptService.runScript() called | instance, has_params |
| script_run_completed | Run returns 200 | duration_ms |
| script_run_failed | Run returns error | error_type, http_status |

**Success metric:** What number proves the feature works?
  → "80% of script_run_initiated events reach script_run_completed"

**Failure signal:** What number means something is broken?
  → "script_run_failed rate > 10% over 24h"
```

## Anti-Patterns

### WARNING: Instrumenting Before the Feature Works

Analytics on a broken feature creates noise that misleads product decisions. Ship the working feature first; add instrumentation in the same PR or immediately after.

### WARNING: No Baseline Before Changing a Flow

If you change the run flow or deploy flow without capturing the current success rate first, you can't tell if the change improved or regressed things.

**The Fix:** When scoping a change to an existing flow, add "capture baseline metric" as the first sub-task in the ticket before any code changes.

### WARNING: Logging PII in Analytics Events

NEVER include `username`, `keystoneServer` hostname, or script file paths in telemetry events. These are user-identifying or environment-identifying details.

**Safe properties to include:** duration_ms, error_type (enum), has_params (boolean), instance (if it's a generic name like "Development" not a client-specific string).
