# Gradle Workflows Reference

## Contents
- Development Cycle
- Packaging for Distribution
- Dependency Updates
- Troubleshooting Build Failures

---

## Development Cycle

### Standard iteration loop

```bash
./gradlew clean build    # First time or after build.gradle.kts changes
./gradlew runIde         # Launch sandbox IDE; leave running while developing
# Edit source files — IntelliJ auto-compiles
# Restart sandbox IDE to pick up changes: File > Invalidate Caches / Restart
```

Copy this checklist for a new feature cycle:

- [ ] Run `./gradlew clean build` — confirm baseline compiles
- [ ] Launch `./gradlew runIde` — verify plugin loads in sandbox
- [ ] Make source changes
- [ ] Run `./gradlew build` — confirm no compilation errors
- [ ] Restart sandbox IDE — verify behavior in running plugin
- [ ] Run `./gradlew buildPlugin` — confirm distribution ZIP is valid

### Verifying the plugin loads

After `runIde`, confirm in the sandbox IDE:
1. **Settings > Plugins > Installed** — "Keyscript IDE" appears and is enabled
2. Status bar shows "KS: Not Logged In"
3. No errors in **Help > Show Log in Finder** (`idea.log`)

---

## Packaging for Distribution

```bash
./gradlew buildPlugin
# Output: build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

Before packaging a release:

```kotlin
// build.gradle.kts — re-enable for marketplace releases only
tasks {
    buildSearchableOptions {
        enabled = true  // Adds ~3min; required for settings search indexing on Marketplace
    }
}
```

Validate the ZIP before distributing:

1. Build: `./gradlew buildPlugin`
2. In IntelliJ: **Settings > Plugins > gear icon > Install Plugin from Disk**
3. Select `build/distributions/keyscript-intellij-plugin-2.0.0.zip`
4. Restart and verify plugin loads without errors

### Bumping the version

```kotlin
// build.gradle.kts — change in ONE place only
version = "2.1.0"  // ZIP name and descriptor version both derive from here

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()  // Do not hardcode here
    }
}
```

---

## Dependency Updates

### Updating Ktor

All Ktor artifacts must move together:

```kotlin
val ktorVersion = "2.3.13"  // Change the variable — all artifacts update
```

Validate after updating:

1. `./gradlew build` — compile check
2. `./gradlew runIde` — start proxy server via a script run
3. Confirm proxy starts: Diagnostics panel shows "Proxy listening on :3000"
4. Run a script — confirm network requests appear in Network Monitor

See the **ktor** skill for CIO engine-specific considerations.

### Checking for conflicts after any dependency change

```bash
./gradlew dependencies --configuration runtimeClasspath | grep -E "(FAILED|conflict|->)"
```

Run this after any `build.gradle.kts` change. Dependency conflicts in IntelliJ plugins are silent at compile time but crash the plugin at load time with `PluginException`.

---

## Troubleshooting Build Failures

### Sandbox IDE won't start: `ClassNotFoundException`

**Cause:** A dependency is missing from the plugin classpath but present in the IDE classpath during development.

**Fix:**
```bash
./gradlew dependencies --configuration runtimeClasspath | grep <missing-class-package>
```
If missing, add it as `implementation(...)` in `build.gradle.kts`.

### `runIde` fails: port already in use

The sandbox IDE reuses the Ktor proxy port (default 3000). If a previous sandbox run didn't shut down cleanly:

```bash
lsof -ti:3000 | xargs kill -9
./gradlew runIde
```

### Build cache causes stale artifacts

```bash
./gradlew clean build --rerun-tasks
```

Use `--rerun-tasks` when build output looks correct but the sandbox IDE shows old behavior. Gradle's incremental build cache can retain stale class files after Kotlin refactors.

### `ideaVersion` compatibility check fails on runIde

```
Plugin 'Keyscript IDE' (version '2.0.0') is not compatible with IntelliJ IDEA 2025.x.y
```

Check `sinceBuild`/`untilBuild` in `build.gradle.kts`. The sandbox IDE version is pinned to `intellijIdeaUltimate("2025.1.3")`, so `sinceBuild = "251"` and `untilBuild = "253.*"` must cover build number `251.x`.

See the **intellij-platform** skill for build number mapping.