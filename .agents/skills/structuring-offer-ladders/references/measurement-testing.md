# Measurement & Testing Reference

## Contents
- What to Measure for Tier Ladders
- Instrumentation Points in the Plugin
- Testing Tier Gates
- Anti-Patterns

---

## What to Measure for Tier Ladders

There are no analytics hooks in the plugin today. The plugin uses IntelliJ's `Logger` for internal diagnostics only. Before building measurement, establish what tier transition you care about:

| Transition | Signal to Capture | Where |
|------------|------------------|-------|
| Install → Configured | User saves a valid `keystoneServer` value | `KeyscryptSettingsConfigurable.apply()` |
| Community → Upgrade intent | User hits a tier gate (JCEF panel shown) | `isUltimateEdition()` check in preview provider |
| Dev → Production access | User selects "Production" instance | `SessionService` instance switch |
| First successful run | `RunKeyscriptService` completes without error | `runScript()` success branch |

---

## Instrumentation Points in the Plugin

### Logging Tier Gate Hits (Low-Cost Baseline)

```kotlin
// In KeyscriptSplitEditorProvider.accept() — log when gate is hit
private val LOG = Logger.getInstance(KeyscriptSplitEditorProvider::class.java)

override fun accept(file: VirtualFile): Boolean {
    if (!isUltimateEdition()) {
        LOG.info("TIER_GATE: JCEF preview unavailable — Community Edition")
        return false
    }
    return file.name.endsWith(".keyscript.js")
}
```

Log lines like `TIER_GATE:` are searchable in the IDE's log output and in any log aggregation system receiving IDE diagnostics.

### Tracking First Configuration

```kotlin
// In KeyscryptSettingsConfigurable.apply()
override fun apply() {
    val settings = KeyscryptSettings.getInstance()
    val wasEmpty = settings.keystoneServer.isBlank()

    settings.keystoneServer = serverField.text.trim()

    if (wasEmpty && settings.keystoneServer.isNotBlank()) {
        LOG.info("ACTIVATION: User configured Keystone server for the first time")
    }
}
```

### Tracking Instance Tier Transitions

```kotlin
// In SessionService — log instance changes
fun switchInstance(newInstance: String) {
    val previous = currentInstance
    currentInstance = newInstance
    LOG.info("INSTANCE_SWITCH: $previous → $newInstance")
}
```

---

## Testing Tier Gates

### Simulate Community Edition in Sandbox

```kotlin
// Testable wrapper — inject a fake edition detector
class TierGate(private val editionCheck: () -> Boolean = ::isUltimateEdition) {
    fun isPreviewAvailable(): Boolean = editionCheck()
}

// In test
val communityGate = TierGate { false }
assertFalse(communityGate.isPreviewAvailable())

val ultimateGate = TierGate { true }
assertTrue(ultimateGate.isPreviewAvailable())
```

NEVER test tier logic against `ApplicationInfo.getInstance()` directly — it always returns the real IDE edition in tests and makes unit tests non-deterministic.

### Smoke-Test Tier Copy in Sandbox IDE

```bash
# Launch with Community Edition simulation flag (not available natively)
# Instead: install plugin in a Community Edition IDE to verify gate behavior
./gradlew runIde
```

Manually verify:
- [ ] JCEF preview panel shows upgrade message in Community
- [ ] Workspace / Data Tools panels still appear in Community
- [ ] Notification fires once on first run (not every run)
- [ ] Production instance badge renders red in the Session tab

---

## Anti-Patterns

### WARNING: Using `ApplicationInfo` in Unit Tests

**The Problem:**
```kotlin
// BAD — always returns the actual IDE version running the test
fun isUltimateEdition() = ApplicationInfo.getInstance().fullApplicationName.contains("Ultimate")
```

**Why This Breaks:** Tests run in whatever IDE hosts the test runner. You can't test Community behavior on an Ultimate installation.

**The Fix:** Wrap the check in an injectable lambda (see above). This makes all tier-gating logic unit-testable.

---

See the **instrumenting-product-metrics** skill for broader metrics strategy and event naming conventions.
See the **mapping-conversion-events** skill for structured event schemas if you add an analytics backend.
