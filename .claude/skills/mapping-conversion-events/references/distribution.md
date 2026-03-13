# Distribution Reference

## Contents
- JetBrains Marketplace publishing
- Plugin packaging
- Version and compatibility targeting
- Update lifecycle
- Anti-patterns

Keyscript IDE distributes exclusively via JetBrains Marketplace and direct ZIP install. Distribution configuration lives in `build.gradle.kts` and `plugin.xml`.

---

## Plugin Packaging

```bash
# Package for distribution
./gradlew buildPlugin
# Output: build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

The ZIP is installable via Settings > Plugins > Install from Disk or publishable to Marketplace.

---

## Version and Compatibility

```kotlin
// build.gradle.kts — compatibility window
ideaVersion {
    sinceBuild = "251"    // IntelliJ 2025.1
    untilBuild = "253.*"  // IntelliJ 2025.3.x
}
```

**Rules:**
- `untilBuild` must be set or Marketplace rejects the plugin for future IDE versions
- `253.*` — the `.*` wildcard covers all patch releases of 253
- When a new major IDE version releases, update `untilBuild` and re-publish within one sprint or risk losing Marketplace visibility

### Semantic Versioning

```kotlin
// build.gradle.kts
version = "2.0.0"  // MAJOR.MINOR.PATCH

// plugin.xml (must stay in sync)
<version>2.0.0</version>
```

NEVER let `build.gradle.kts` and `plugin.xml` version diverge — IntelliJ Platform Gradle Plugin v2 reads from both and will warn on mismatch.

---

## Marketplace Publishing Workflow

Copy this checklist and track progress:
- [ ] Bump version in `build.gradle.kts` AND `plugin.xml`
- [ ] Update `change-notes` in `plugin.xml` with user-facing changelog
- [ ] Run `./gradlew buildPlugin` — confirm no build errors
- [ ] Run `./gradlew verifyPlugin` — check compatibility warnings
- [ ] Upload ZIP to Marketplace or run `./gradlew publishPlugin` with token
- [ ] Verify listing shows correct version and screenshots
- [ ] Tag the release in git: `git tag v2.x.x`

---

## Gradle Publishing Config

```kotlin
// build.gradle.kts — add for automated publish
intellijPlatform {
    publishing {
        token = providers.environmentVariable("MARKETPLACE_TOKEN")
        channels = listOf("stable") // or "eap" for early access
    }
}
```

Run with: `MARKETPLACE_TOKEN=xxx ./gradlew publishPlugin`

---

## Anti-Pattern: Hardcoding `untilBuild` to an Old Version

**The Problem:**
```kotlin
// BAD — plugin blocked from users on 2025.3+
untilBuild = "252.*"
```

**Why This Breaks:**
Marketplace hides plugins incompatible with the user's installed IDE version. Users on 2025.3 won't see the plugin in search results.

**The Fix:**
Advance `untilBuild` with each IDE EAP cycle, or use `provider { null }` to remove the upper bound (risky — only do this if you actively test on EAP builds).

---

## Direct ZIP Distribution

For enterprise teams that can't use Marketplace:

```kotlin
// build.gradle.kts — create a fat ZIP with all dependencies bundled
tasks.named<Jar>("jar") {
    // dependencies are bundled by intellij-platform plugin automatically
}
```

The output at `build/distributions/*.zip` is self-contained. No Maven/Gradle access required at install time.

See the **gradle** skill for build task configuration details.
See the **writing-release-notes** skill for change-notes copy.
