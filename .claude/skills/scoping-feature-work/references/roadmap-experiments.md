# Roadmap & Experiments Reference

## Contents
- Plugin Versioning and Release Cadence
- Feature Flag Patterns
- Rollout Strategy for Plugin Features
- Experiment Scoping Template
- IntelliJ Compatibility Window
- Anti-Patterns

## Plugin Versioning and Release Cadence

The plugin uses semantic versioning (`2.0.0` in `build.gradle.kts` and `plugin.xml`). Updates require users to update via the JetBrains Marketplace or manual install — there's no silent auto-update.

```kotlin
// Current version is defined in two places — keep them in sync:
// build.gradle.kts:
version = "2.0.0"
intellijPlatform {
    pluginConfiguration {
        version = "2.0.0"
    }
}

// When scoping a release:
// PATCH (2.0.x): bug fixes, no new extension points
// MINOR (2.x.0): new features, backward-compatible settings
// MAJOR (x.0.0): breaking settings schema changes, new required config
```

## Feature Flag Patterns

The plugin has no built-in feature flag system. For experimental features, use `KeyscryptSettings` to add an opt-in boolean:

```kotlin
// Pattern: opt-in experimental feature via settings
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings> {
    // Experimental features — default false until validated
    var enableQueryBuilder: Boolean = false
    var enableNetworkMonitorAutoCapture: Boolean = false
}

// In the feature:
if (!settings.enableQueryBuilder) return

// In settings UI (KeyscryptSettingsConfigurable):
// Add checkbox: "Enable Query Builder (experimental)"
// Label clearly as experimental so users understand stability expectations
```

## Rollout Strategy for Plugin Features

Plugin features cannot be feature-flagged at the server level (unlike web apps). Rollout options:

| Strategy | When to Use | How |
|----------|------------|-----|
| Opt-in setting | Risky features, power users only | `KeyscryptSettings` boolean, default `false` |
| Always-on | Low-risk UI improvements, bug fixes | Ship in next release |
| Phased via Marketplace | Large features needing gradual rollout | JetBrains Marketplace has rollout % support |
| Beta channel | Features needing broad testing before GA | Publish to `eap` channel in plugin.xml |

```xml
<!-- plugin.xml: specify release channel for beta features -->
<idea-plugin>
  <!-- For stable releases: no channel attribute needed -->
  <!-- For beta/EAP releases: use channel="EAP" -->
</idea-plugin>
```

## Experiment Scoping Template

When scoping an experiment (i.e., you're not sure if the feature is the right solution):

```markdown
## Experiment: [Feature Name]

**Hypothesis:** If we [change], then [metric] will [improve] because [reason].

**Success threshold:** [Metric] improves by [X%] over [time period]

**Kill criteria:** If [negative signal] occurs, we roll back by [mechanism]

**Implementation approach:**
- Opt-in via `KeyscryptSettings.enable[FeatureName] = false`
- AC includes: settings checkbox is hidden unless `showExperimentalFeatures = true`

**Validation plan:**
1. Ship with opt-in disabled
2. Enable for internal testing (set flag manually in settings)
3. If no regressions after [N] days, flip default to `true`
4. Remove flag in next MINOR release
```

## IntelliJ Compatibility Window

The plugin targets builds 251–253.* (`sinceBuild`/`untilBuild` in `build.gradle.kts`). Any experiment or roadmap feature must work within this window. See the **intellij-platform** skill for details on API availability across builds.

```kotlin
// When scoping features that use newer IntelliJ APIs:
// 1. Check if the API exists in build 251 (IDEA 2025.1)
// 2. If not, either defer or add @RequiresApi annotation with fallback
// 3. Update sinceBuild if the feature requires a newer build
```

## Anti-Patterns

### WARNING: Shipping Experiments as Permanent Features

Features shipped without an explicit "validate and remove flag" plan accumulate indefinitely. Every opt-in boolean in `KeyscryptSettings` is technical debt if it's never promoted to always-on or removed.

**The Fix:** Every experiment ticket must have a linked follow-on ticket: "Promote [feature] to GA or remove." Create it at the same time as the experiment ticket.

### WARNING: Breaking Settings Schema Between Versions

`KeyscryptSettings` is persisted as XML by IntelliJ. Renaming a field or changing its type without migration drops the old value on IDE restart — effectively resetting user config silently.

**The Fix:** When renaming a settings field, keep the old field and migrate in `noStateLoaded()` or add `@Attribute(value = "oldName")` for backward compatibility.
