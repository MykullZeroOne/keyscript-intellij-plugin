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
