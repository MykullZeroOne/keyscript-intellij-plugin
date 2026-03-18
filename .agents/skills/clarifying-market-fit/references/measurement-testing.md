# Measurement and Testing Reference

## Contents
- What can be measured in a plugin context
- Proxy-level instrumentation for behavioral signals
- Session and activation metrics
- Messaging test approaches

---

## What Can Be Measured

IDE plugins have no web analytics. Measurement is limited to:

| Signal | How to Capture | What It Indicates |
|--------|---------------|-------------------|
| First script run | `RunKeyscriptService` event | Activation (user completed setup) |
| Login attempts | `AuthenticationService` events | Funnel entry rate |
| Login success/failure rate | `SessionService` state changes | Setup friction |
| Deploy actions | `DeploymentService` call count | Power-user engagement |
| Proxy request volume | `NetworkMonitorService` | Active use vs idle install |
| Tool window opens | `KeyscriptWorkspaceToolWindowFactory` visibility events | Feature discovery |

These signals matter for messaging decisions: if logins are frequent but first-run activations are low, the setup copy in settings is failing the user.

---

## Proxy-Level Instrumentation

`NetworkMonitorService` already captures request/response events for the Diagnostics panel. This infrastructure can serve double duty for anonymous usage metrics if you add a telemetry sink:

```kotlin
// NetworkMonitorService.kt — existing event capture
fun onRequestCaptured(event: NetworkEvent) {
    _listeners.forEach { it.onNetworkEvent(event) }
    // Add telemetry here if needed:
    // TelemetryService.getInstance().record("proxy.request", event.method, event.path)
}
```

NEVER log authentication tokens, session IDs, or user credentials in telemetry. The proxy injects `JSESSIONID` — ensure telemetry strips cookie headers before recording.

---

## Session and Activation Metrics

The activation funnel for this plugin:

```
Install → Open Keyscript Project → Configure Settings → Login → First Script Run
```

Each step has a measurable drop-off point. Instrument `SessionService` state transitions to capture the critical middle steps:

```kotlin
// SessionService.kt — state transition logging
enum class SessionState { NOT_CONFIGURED, LOGGED_OUT, LOGGING_IN, LOGGED_IN, SESSION_EXPIRED }

fun transitionTo(newState: SessionState) {
    val previous = _state
    _state = newState
    logger.info("Session transition: $previous → $newState")
    // ^ This log line gives you a funnel trace in sandbox runs
    _listeners.forEach { it.onSessionStateChanged(newState) }
}
```

In sandbox development (`./gradlew runIde`), reading these log lines from the IDE's Run panel is the primary way to verify funnel steps work as expected.

---

## Messaging Test Approaches

Without A/B infrastructure, messaging tests are manual and qualitative:

### Readability test (5-second rule)

Show README.md hero and plugin.xml description to a Keystone developer who hasn't seen the plugin. After 5 seconds, ask:
- "What does this plugin do?"
- "Is it for you?"

If either answer is wrong or uncertain, the copy needs revision.

### Setup completion test

Walk a new user through first-run with no verbal guidance. Observe where they stop or ask questions. Each stall is a copy failure — either a label is ambiguous or a missing field hint causes them to guess the wrong format.

```kotlin
// KeyscryptSettingsConfigurable.kt — add .comment() hints to reduce stalls
row("Keystone Server (host:port):") {
    textField()
        .bindText(settings::keystoneServer)
        .comment("Format: hostname:port, no protocol prefix. Example: keystone.internal:8443")
        .focused()
}
```

### Log-based funnel verification

After a fresh install in the sandbox IDE, verify the funnel completes by checking the Run panel logs:

1. `./gradlew runIde`
2. Open a Keyscript project
3. Configure settings
4. Log in
5. Run a script
6. Check logs for: `Session transition: LOGGED_OUT → LOGGED_IN` and `RunKeyscriptService: script execution started`

If any step is missing from logs, the funnel has a silent failure.

See the **instrumenting-product-metrics** skill for detailed event instrumentation patterns.
See the **improving-activation-flow** skill for funnel optimization at each step.
