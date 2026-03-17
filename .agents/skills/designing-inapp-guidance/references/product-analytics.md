# Product Analytics Reference

## Contents
- What Can Be Measured Without External Analytics
- PropertiesComponent as Lightweight Event Store
- Activation Funnel Checkpoints
- WARNING: No Analytics SDK
- Instrumentation Checklist

---

## What Can Be Measured Without External Analytics

The plugin has no external analytics SDK. Measurement relies on:
1. **`PropertiesComponent`** — persistent boolean/counter flags per installation
2. **IntelliJ Logger** — structured log lines readable from the IDE's internal log
3. **Notification interaction** — action button clicks as implicit engagement signals

This is intentional for an internal-distribution plugin. If full analytics are ever needed, the right integration point is `KeyscriptProjectService.postStartupActivity()` via an app-level service.

## PropertiesComponent as Lightweight Event Store

Use global `PropertiesComponent.getInstance()` (not project-scoped) to track activation milestones that need to survive project close.

```kotlin
object ActivationEvents {
    private val props get() = PropertiesComponent.getInstance()

    // Call these at each milestone
    fun recordConfigured()    = props.setValue("keyscript.evt.configured", true)
    fun recordLoggedIn()      = props.setValue("keyscript.evt.firstLogin", true)
    fun recordFirstRun()      = props.setValue("keyscript.evt.firstRun", true)
    fun recordFirstDeploy()   = props.setValue("keyscript.evt.firstDeploy", true)

    // Read for guidance gating
    fun hasConfigured()   = props.getBoolean("keyscript.evt.configured", false)
    fun hasLoggedIn()     = props.getBoolean("keyscript.evt.firstLogin", false)
    fun hasRun()          = props.getBoolean("keyscript.evt.firstRun", false)
    fun hasDeployed()     = props.getBoolean("keyscript.evt.firstDeploy", false)
}
```

Call `recordConfigured()` in `KeyscryptSettingsConfigurable.apply()`, `recordLoggedIn()` in `SessionService` on first successful login, etc.

## Activation Funnel Checkpoints

These are the milestones that matter for understanding whether users reach value. Each maps to a guidance surface.

| Milestone | Where to fire | Guidance to show |
|-----------|--------------|-----------------|
| Plugin detected project | `KeyscryptProjectService.init()` | First-run balloon if not yet configured |
| Settings configured | `KeyscryptSettingsConfigurable.apply()` | "Now log in via the status bar" balloon |
| First login | `SessionService` login callback | "Try running a script" balloon |
| First script run | `RunKeyscriptService` success callback | "Check Diagnostics panel" nudge |
| First deploy | `DeploymentService` success callback | "Verify in Table Browser" nudge |

## Structured Log Lines

Use `Logger` with a consistent prefix so log lines can be grepped from `idea.log`:

```kotlin
// In each service, emit structured log lines at milestones
private val LOG = Logger.getInstance(RunKeyscriptService::class.java)

fun onRunSuccess(scriptName: String) {
    LOG.info("[keyscript-analytics] event=script_run script=$scriptName")
    ActivationEvents.recordFirstRun()
}
```

Grep from IntelliJ's log: `grep "keyscript-analytics" ~/Library/Logs/JetBrains/IntelliJIdea*/idea.log`

## WARNING: No Analytics SDK

This plugin has no Mixpanel, Amplitude, Segment, or equivalent SDK. NEVER add one without explicit approval — it creates data privacy and compliance obligations for internal tooling.

If quantitative funnel data is needed, use log parsing on `idea.log` as a proxy. The structured log pattern above makes this tractable.

## Instrumentation Checklist

Copy this checklist when adding a new feature that crosses an activation boundary:

```
- [ ] Record milestone in ActivationEvents when the action succeeds (not starts)
- [ ] Log a structured line: [keyscript-analytics] event=<name> <key>=<value>
- [ ] Gate any related guidance nudge on the milestone NOT having fired yet
- [ ] Verify PropertiesComponent key uses consistent naming: keyscript.evt.<name>
- [ ] Check that recording happens on success path only, not on attempt
```

See the **instrumenting-product-metrics** skill for deeper event design patterns.
