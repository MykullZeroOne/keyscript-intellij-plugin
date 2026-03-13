---
name: gradle
description: |
  Manages build configuration, dependency resolution, and plugin packaging for the Keyscript IntelliJ plugin.
  Use when: modifying build.gradle.kts, adding/updating dependencies, running build tasks, packaging the plugin for distribution, configuring sandbox IDE runs, or troubleshooting build failures.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash, mcp__jetbrains__execute_run_configuration, mcp__jetbrains__get_run_configurations, mcp__jetbrains__build_project, mcp__jetbrains__get_file_problems, mcp__jetbrains__get_project_dependencies, mcp__jetbrains__execute_terminal_command
---

# Gradle

Kotlin DSL build system for the Keyscript IntelliJ plugin. Uses the `org.jetbrains.intellij.platform` plugin (v2.x) — a major API break from the legacy `org.jetbrains.intellij` (v1.x). All configuration goes in `build.gradle.kts`.

## Quick Start

### Run common tasks

```bash
./gradlew build          # Compile + test + package
./gradlew runIde         # Launch sandbox IDE with plugin loaded
./gradlew buildPlugin    # Produce distributable ZIP
./gradlew clean build    # Full clean rebuild
```

### Add a dependency

```kotlin
// build.gradle.kts — inside dependencies {}
implementation("io.ktor:ktor-server-websockets-jvm:2.3.12")
```

### Configure sandbox JVM args

```kotlin
// build.gradle.kts
tasks {
    runIde {
        jvmArgs(
            "-Dsun.java2d.metal=false",  // Required on macOS — Metal renderer causes JCEF crashes
            "-Xmx2g",
            "-Xms512m"
        )
    }
}
```

## Key Configuration

| Setting | Location | Value |
|---------|----------|-------|
| Platform version | `intellijPlatform {}` | `2025.1.3` (Ultimate) |
| Build range | `ideaVersion {}` | `251` – `253.*` |
| Kotlin toolchain | `kotlin {}` | JVM 21 |
| Proxy port default | `KeyscryptSettings` | 3000 |

## Common Patterns

### Disable expensive tasks

```kotlin
tasks {
    buildSearchableOptions {
        enabled = false  // Saves ~2min on each build; re-enable before marketplace publish
    }
}
```

### Bundled plugin declarations

```kotlin
intellijPlatform {
    intellijIdeaUltimate("2025.1.3")
    bundledPlugin("com.intellij.java")
    bundledPlugin("JavaScript")  // Required for JS file type support
}
```

## See Also

- [patterns](references/patterns.md) — Dependency management, platform API, version constraints
- [workflows](references/workflows.md) — Build, test, package, and publish workflows

## Related Skills

- See the **kotlin** skill for JVM toolchain and language-level configuration
- See the **intellij-platform** skill for platform SDK, extension points, and plugin.xml
- See the **ktor** skill for Ktor dependency versioning and CIO engine configuration