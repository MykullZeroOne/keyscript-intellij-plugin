# Gradle Patterns Reference

## Contents
- Dependency Management
- IntelliJ Platform Plugin v2 API
- Task Configuration
- Anti-Patterns

---

## Dependency Management

### Ktor: keep all artifacts on the same version

```kotlin
val ktorVersion = "2.3.12"
implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
// ... all Ktor artifacts use the same variable
```

Ktor modules have tight cross-module coupling. Mixed versions produce `NoClassDefFoundError` or `AbstractMethodError` at runtime — bugs that only surface when the proxy server starts, not at compile time. See the **ktor** skill for server/client configuration.

### Jackson: Kotlin module must match core version

```kotlin
// GOOD — versions aligned
implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
// jackson-databind is a transitive dependency of ktor-serialization-jackson-jvm
// Do NOT pin jackson-databind to a different version here
```

If `jackson-databind` and `jackson-module-kotlin` diverge by a minor version, serialization of Kotlin data classes silently returns `{}`. Always verify with `./gradlew dependencies --configuration runtimeClasspath | grep jackson`.

### Checking the resolved dependency tree

```bash
./gradlew dependencies --configuration runtimeClasspath | head -80
```

Run this when a `NoClassDefFoundError` appears at runtime. The compiled classpath and the packaged plugin classpath can differ — `runtimeClasspath` reflects what ships in the ZIP.

---

## IntelliJ Platform Plugin v2 API

This project uses `org.jetbrains.intellij.platform` **2.2.1** — the v2 API. The v1 plugin (`org.jetbrains.intellij`) is incompatible; do not mix documentation from the two.

### Platform declaration (v2 style)

```kotlin
intellijPlatform {
    pluginConfiguration {
        name = "Keyscript IDE"
        version = "2.0.0"

        ideaVersion {
            sinceBuild = "251"    // IDEA 2025.1
            untilBuild = "253.*"  // IDEA 2025.3.x — wildcard minor required
        }
    }
}
```

`untilBuild` uses `253.*` not `253` — without the wildcard, any 2025.3.x patch release fails the compatibility check and the plugin won't load.

### Bundled plugins

```kotlin
intellijPlatform {
    intellijIdeaUltimate("2025.1.3")
    bundledPlugin("com.intellij.java")   // Java PSI, module system
    bundledPlugin("JavaScript")           // JS file type, PSI for .js files
}
```

Only declare plugins you actually use. Each bundled plugin adds to compile and sandbox setup time. The `JavaScript` plugin is required because Keyscript files are `.js`-based.

---

## Task Configuration

### WARNING: Never run `buildSearchableOptions` in development

```kotlin
// GOOD — disabled in this project
tasks {
    buildSearchableOptions {
        enabled = false
    }
}
```

`buildSearchableOptions` launches a full headless IDE instance to index settings UI. It adds 2–3 minutes per build and is only needed for JetBrains Marketplace indexing. Re-enable only when preparing a marketplace release.

### Controlling sandbox JVM memory

```kotlin
tasks {
    runIde {
        jvmArgs(
            "-Dsun.java2d.metal=false",  // macOS: disables Metal; prevents JCEF blank renders
            "-Xmx2g",
            "-Xms512m"
        )
    }
}
```

`-Dsun.java2d.metal=false` is **required on macOS** when JCEF is active. Without it, the browser preview panel renders blank on Apple Silicon hardware.

---

## Anti-Patterns

### WARNING: Hardcoding platform version strings

**The Problem:**

```kotlin
// BAD — version string duplicated in two places
version = "2.0.0"

intellijPlatform {
    pluginConfiguration {
        version = "2.0.0"  // Must be kept in sync manually
    }
}
```

**Why This Breaks:**
1. Version drifts between `group/version` and `pluginConfiguration.version`
2. The packaged ZIP name uses the top-level `version`; the plugin descriptor uses `pluginConfiguration.version` — a mismatch causes "wrong version" errors on install

**The Fix:**

```kotlin
version = "2.0.0"

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()  // Single source of truth
    }
}
```

### WARNING: Using `implementation` for IntelliJ platform APIs

```kotlin
// BAD — platform APIs must NOT be in implementation scope
implementation("com.jetbrains.intellij.platform:core:2025.1.3")
```

Platform APIs are provided by the IDE at runtime. Including them as `implementation` bloats the plugin ZIP and causes `ClassLoader` conflicts. Use only the `intellijPlatform {}` block for SDK and bundled plugin declarations.