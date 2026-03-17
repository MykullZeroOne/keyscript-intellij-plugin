# Roadmap & Experiments Reference

## Contents
- Feature Flags in This Codebase
- Soft Rollout Pattern
- CHANGELOG-Driven Discovery
- Rollout Checklist
- Anti-Patterns

---

## Feature Flags in This Codebase

The plugin has no feature flag system. `KeyscryptSettings` (application-level singleton) is
the correct place to add boolean flags — they persist in IDE settings and can be toggled
without a plugin update.

```kotlin
// In KeyscryptSettings.kt — add new feature flags here
@State(name = "KeyscryptSettings", storages = [Storage("keyscript.xml")])
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings> {
    // ... existing fields ...

    // Feature flags — default false until ready for full rollout
    @Attribute var enableQueryBuilder: Boolean = false
    @Attribute var enableNetworkMonitorV2: Boolean = false
}
```

Users (or admins) can toggle these in the XML state file, or you can expose them in the
Settings UI as an "Experimental Features" section.

---

## Soft Rollout Pattern

Gate new features behind a settings flag to avoid forcing all users onto unfinished work.

```kotlin
// In QueryBuilderPanel.kt — check flag before rendering full UI
fun createContent(): JComponent {
    val settings = KeyscryptSettings.getInstance()
    if (!settings.enableQueryBuilder) {
        return JPanel(BorderLayout()).apply {
            add(JBLabel("<html><b>Query Builder</b><br>Coming soon. " +
                "Enable in Settings > Keyscript IDE > Experimental Features.</html>"),
                BorderLayout.CENTER)
        }
    }
    return buildFullQueryBuilderUI()
}
```

**Workflow for rolling out a new feature:**

Copy this checklist:
- [ ] Implement feature behind `KeyscryptSettings.enableFeatureX = false`
- [ ] Add feature flag to settings UI under "Experimental Features" section
- [ ] Test with flag enabled in sandbox IDE (`./gradlew runIde`)
- [ ] Notify team to enable flag in their IDE settings for early testing
- [ ] After validation, flip default to `true` in next plugin version
- [ ] Add feature to CHANGELOG.md under the correct version header
- [ ] Remove flag after one full version cycle

---

## CHANGELOG-Driven Discovery

`CHANGELOG.md` is the primary mechanism to communicate new features to existing users.
IntelliJ shows plugin changelogs in the Plugins marketplace and after auto-updates.

**DO:** Write CHANGELOG entries from the user's perspective, not the developer's.

```markdown
## [2.1.0]

### New
- **Query Builder** — compose Keystone queries visually from the Data Tools panel
- **Auto-relogin** — session restores automatically when your Keystone session expires

### Improved
- Status bar now shows current instance name alongside username
- Deploy errors now include the Keystone error message in the notification

### Fixed
- Gutter icons no longer appear on non-Keyscript JS files in mixed projects
```

**DON'T:** Write entries like "Refactored DeploymentService to use coroutines" — this is
invisible to users and wastes their attention.

---

## Rollout Checklist

Use this when releasing a new version with adoption impact:

Copy this checklist:
- [ ] Feature is implemented and tested in sandbox (`./gradlew runIde`)
- [ ] CHANGELOG.md updated with user-facing description
- [ ] Any new tool windows or surfaces have subtitle text via `createToolWindowShell`
- [ ] New features have at least one `PropertiesComponent`-gated nudge notification
- [ ] Plugin version bumped in `build.gradle.kts` (`version` and `pluginConfiguration.version`)
- [ ] `buildPlugin` run to generate distribution ZIP: `./gradlew buildPlugin`
- [ ] ZIP tested by installing manually in a clean IDE (Plugins > Install from disk)

---

## Anti-Patterns

### WARNING: Shipping experimental features with no flag

**The Problem:** All users get half-finished UI on plugin update.
**Why This Breaks:** An empty or broken panel is worse than no panel. Users form negative
first impressions that are hard to reverse.
**The Fix:** Always default new `@Attribute var enableX: Boolean = false` until the feature
is fully tested and ready.

### WARNING: Skipping CHANGELOG for "internal" releases

**The Problem:** Treating plugin updates as invisible infrastructure updates.
**Why This Breaks:** Users see a plugin update notification, click it, and find an empty
changelog. They don't know what changed and whether to pay attention.
**The Fix:** Every version bump gets at minimum one user-facing CHANGELOG entry.
