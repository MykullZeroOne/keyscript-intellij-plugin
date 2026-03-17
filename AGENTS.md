# Repository Guidelines

## Project Structure & Module Organization
This repository builds an IntelliJ plugin for Keyscript IDE with Gradle Kotlin DSL. Production code lives in `src/main/kotlin/com/keyscript/plugin`, organized by feature: `actions`, `completion`, `proxy`, `runconfig`, `services`, `settings`, `statusbar`, and `toolwindow`. Plugin metadata and bundled assets live in `src/main/resources`, especially `META-INF/plugin.xml`, `META-INF/javascript-support.xml`, `liveTemplates/`, `cr-types/`, and `templates/`. Build logic stays at the root in `build.gradle.kts`, `settings.gradle.kts`, and the Gradle wrapper files.

## Build, Test, and Development Commands
Use the pinned JetBrains runtime before running Gradle: `sdk env` or otherwise set `JAVA_HOME` to JBR/JDK 21 (`.sdkmanrc` uses `21.0.10-jbr`).

- `./gradlew build` compiles the plugin and produces distributables.
- `./gradlew runIde` launches a sandbox IntelliJ instance with the plugin loaded.
- `./gradlew verifyPlugin` runs IntelliJ plugin verification checks.
- `./gradlew test` runs automated tests when a `src/test/kotlin` suite is present.

If Gradle fails with a Java version parse error, confirm you are not running on JDK 25.

## Coding Style & Naming Conventions
Follow existing Kotlin style: 4-space indentation, standard Kotlin braces, and concise KDoc only where behavior is not obvious. Keep packages under `com.keyscript.plugin`. Use PascalCase for classes and descriptive suffixes that match IntelliJ roles, such as `AuthenticationService`, `LoginAction`, and `PreviewToolWindowFactory`. Resource files use clear lowercase names; HTML templates in `src/main/resources/templates` are hyphenated, for example `iframe-target.html`.

## Testing Guidelines
There is no active test tree in the current checkout, so new features should add coverage under `src/test/kotlin` alongside the production package they exercise. Prefer focused unit tests for services and API parsing logic, and keep test names descriptive, for example `AuthenticationServiceTest` with methods covering success and failure paths. Run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines
Recent history uses short, topic-based commit subjects in Title Case, for example `Execution Engine Integration` and `Plugin Settings & Refinement`. Keep commits narrowly scoped and write the first line as a clear summary of the change. PRs should include a concise description, linked issue or task when available, local verification steps, and screenshots or GIFs for UI changes such as tool windows, settings, or status bar updates.


## Skill Usage Guide

When working on tasks involving these technologies, invoke the corresponding skill:

| Skill | Invoke When |
|-------|-------------|
| gradle | Configures build system, dependency management, and plugin packaging |
| kotlin | Implements IntelliJ plugin services, async operations, and Kotlin patterns |
| intellij-platform | Registers extensions, services, tool windows, and IDE integrations |
| jcef | Integrates Java Chromium Embedded Framework for browser preview |
| ktor | Manages embedded HTTP proxy server and request routing |
| jackson | Handles JSON serialization and API payload deserialization |
| mapping-user-journeys | Maps in-app journeys and identifies friction points in code |
| typescript | Types CR framework definitions and JavaScript library components |
| designing-onboarding-paths | Designs onboarding paths, checklists, and first-run UI |
| orchestrating-feature-adoption | Plans feature discovery, nudges, and adoption flows |
| instrumenting-product-metrics | Defines product events, funnels, and activation metrics |
