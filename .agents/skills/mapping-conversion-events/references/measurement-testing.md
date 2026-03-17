# Measurement & Testing Reference

## Contents
- Feature Usage Statistics (FUS) instrumentation
- A/B testing approaches in IntelliJ plugins
- Funnel measurement without a data warehouse
- Validating event firing
- Anti-patterns

Keyscript IDE does not yet have a telemetry layer. All patterns below use IntelliJ's built-in Feature Usage Statistics (FUS) framework — it's opt-in for users (respects IDE privacy settings), zero-dependency, and aggregated in JetBrains product analytics.

---

## Feature Usage Statistics (FUS)

### Register an event group

```kotlin
// src/main/kotlin/com/keyscript/plugin/telemetry/KeyscryptFunnel.kt
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class KeyscryptFunnelCollector : CounterUsagesCollector() {
    companion object {
        val GROUP = EventLogGroup("keyscript.funnel", 1)

        val SETTINGS_CONFIGURED = GROUP.registerEvent("settings.configured")
        val AUTH_SUCCESS = GROUP.registerEvent("auth.success",
            EventFields.Boolean("credentials_saved"))
        val SCRIPT_FIRST_RUN = GROUP.registerEvent("script.first_run",
            EventFields.String("trigger", listOf("gutter", "run_config", "action")))
        val DEPLOY_SUCCESS = GROUP.registerEvent("deploy.success",
            EventFields.String("instance", listOf("Development", "Test", "Production")))
        val PREVIEW_OPENED = GROUP.registerEvent("preview.opened")
    }

    override fun getGroup() = GROUP
}
```

### Register in plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">
    <statistics.counterUsagesCollector
        implementationClass="com.keyscript.plugin.telemetry.KeyscryptFunnelCollector"/>
</extensions>
```

### Fire events at the right callsite

```kotlin
// AuthenticationService.kt
KeyscryptFunnelCollector.AUTH_SUCCESS.log(project, saveCredentials)

// RunKeyscryptService.kt
KeyscryptFunnelCollector.SCRIPT_FIRST_RUN.log(project, triggerSource)
```

---

## Funnel Drop-off Measurement

Without a data warehouse, measure drop-off rates by comparing event counts in JetBrains stats dashboard:

```
install.detected    → 100% baseline
settings.configured → ?% (target: >70%)
auth.success        → ?% (target: >60%)
script.first_run    → ?% (target: >40%)
preview.opened      → ?% (target: >25%)
deploy.success      → ?% (target: >15%)
```

Any stage below target is a conversion problem. Fix the stage before optimizing later ones.

---

## Validating Event Firing (Local)

```kotlin
// In a test or scratch file — enable FUS debug output
System.setProperty("idea.fus.internal", "true")
// Events logged to idea.log with prefix [FUS]
```

Run `./gradlew runIde` and reproduce the flow. Search the sandbox IDE log for `[FUS] keyscript.funnel`.

---

## Anti-Pattern: Tracking Outcomes Instead of Behaviors

**The Problem:**
```kotlin
// BAD — tracks result, not cause
GROUP.registerEvent("user.is_active")  // fires daily if session alive
```

**Why This Breaks:**
You can't diagnose what drove activation or retention. Outcome events don't tell you which feature converted the user.

**The Fix:**
```kotlin
// GOOD — tracks specific behaviors at the moment they happen
SCRIPT_FIRST_RUN.log(project, "gutter")   // which trigger caused the run?
DEPLOY_SUCCESS.log(project, instanceName) // which instance was targeted?
```

---

## A/B Testing in IntelliJ Plugins

IntelliJ Platform doesn't have a native A/B framework. Practical options:

| Option | Mechanism | When to use |
|--------|-----------|-------------|
| Registry flags | `Registry.is("keyscript.feature.x")` | Internal dogfooding |
| Settings toggle | Expose in `KeyscryptSettings` | Beta opt-in for users |
| JetBrains EAP channel | Publish to `eap` Marketplace channel | Controlled rollout |

See the **instrumenting-product-metrics** skill for metric schema design.
