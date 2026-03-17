# Distribution Reference

## Contents
- Distribution Channels
- JetBrains Marketplace Distribution
- Internal/Team Distribution
- Plugin ZIP Packaging
- Anti-Patterns

---

## Distribution Channels

The Keyscript IDE plugin has two distribution paths:

| Channel | Mechanism | Audience |
|---------|-----------|----------|
| **JetBrains Marketplace** | `buildPlugin` → upload ZIP or CI publish | External Keystone devs |
| **Internal distribution** | ZIP shared via team channel / internal URL | Internal teams, early access |

Both channels use the same artifact: `build/distributions/keyscript-intellij-plugin-2.0.0.zip`.

---

## JetBrains Marketplace Distribution

### Build the plugin ZIP

```bash
# Produces build/distributions/keyscript-intellij-plugin-2.0.0.zip
./gradlew buildPlugin
```

The ZIP filename derives from `rootProject.name` in `settings.gradle.kts` and `version` in `build.gradle.kts`.

```kotlin
// build.gradle.kts — version controls ZIP filename and Marketplace listing version
version = "2.0.0"

intellijPlatform {
    pluginConfiguration {
        version = "2.0.0"  // Must match top-level version
    }
}
```

### Verify before publishing

```bash
# Run built-in plugin verifier to catch compatibility issues
./gradlew verifyPlugin

# Sign the plugin (required for Marketplace submission)
./gradlew signPlugin
```

### Marketplace listing metadata lives in plugin.xml

The Marketplace pulls these fields from `src/main/resources/META-INF/plugin.xml`:

```xml
<id>com.keyscript.plugin</id>          <!-- Immutable after first publish -->
<name>Keyscript IDE</name>              <!-- Display name in search results -->
<vendor>Keyscript</vendor>              <!-- Vendor page grouping -->
<description><![CDATA[ ... ]]></description>  <!-- Full listing body (HTML) -->
<change-notes><![CDATA[ ... ]]></change-notes>  <!-- What's new for this version -->
```

NEVER change `<id>` after publishing — it's the stable identifier users' IDEs track for updates. Changing it creates a duplicate listing.

### `sinceBuild` / `untilBuild` controls compatibility badge

```kotlin
// build.gradle.kts
ideaVersion {
    sinceBuild = "251"   // 2025.1
    untilBuild = "253.*" // 2025.3.x
}
```

Marketplace shows a "Compatible with IntelliJ IDEA 2025.1–2025.3" badge. NEVER set `untilBuild` to a past version — it blocks installs for current IDE users.

---

## Internal/Team Distribution

For internal teams without Marketplace access, the ZIP can be installed manually:

1. Build: `./gradlew buildPlugin`
2. Share `build/distributions/keyscript-intellij-plugin-2.0.0.zip`
3. Recipients install via: **Settings > Plugins > ⚙ > Install Plugin from Disk**

### Custom plugin repository (optional)

For teams that want automatic updates without Marketplace, IntelliJ supports a custom plugin XML feed:

```xml
<!-- updatePlugins.xml — host this on an internal server -->
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin
    id="com.keyscript.plugin"
    url="https://internal.example.com/plugins/keyscript-intellij-plugin-2.0.0.zip"
    version="2.0.0"/>
</plugins>
```

Users add the repository URL at **Settings > Plugins > ⚙ > Manage Plugin Repositories**.

---

## Plugin ZIP Packaging

The ZIP must contain the plugin JAR and all bundled dependencies. The IntelliJ Gradle plugin handles this automatically, but verify that Ktor and Jackson JARs are bundled:

```bash
# Inspect ZIP contents after buildPlugin
unzip -l build/distributions/keyscript-intellij-plugin-2.0.0.zip | grep -E "ktor|jackson"
```

If Ktor or Jackson JARs are missing, they've been excluded from the runtime classpath. In `build.gradle.kts`, use `implementation` (not `compileOnly`) for all runtime deps:

```kotlin
// CORRECT — bundled in ZIP
implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")

// WRONG — available at compile time only; ClassNotFoundException at runtime
compileOnly("io.ktor:ktor-server-core-jvm:$ktorVersion")
```

See the **gradle** skill for full dependency scope guidance.

---

## Anti-Patterns

### WARNING: Mismatched version numbers

```kotlin
// BAD — top-level version and pluginConfiguration version differ
version = "2.0.0"  // top-level

intellijPlatform {
    pluginConfiguration {
        version = "2.1.0"  // different!
    }
}
```

**Why This Breaks:** The ZIP filename uses the top-level version. The Marketplace listing version uses `pluginConfiguration.version`. They'll be out of sync, causing update detection failures on installed plugins.

**The Fix:** Keep both identical. Bump both when releasing.

### WARNING: Publishing without `change-notes`

The `<change-notes>` field in `plugin.xml` populates the "What's new" tab on Marketplace and the update balloon in users' IDEs. Omitting it means users have no context for updates — they often skip.

```xml
<!-- plugin.xml — always fill this before publishing -->
<change-notes><![CDATA[
  <b>2.0.0</b>
  <ul>
    <li>Complete rewrite from Electron to IntelliJ platform</li>
    <li>JCEF split-editor preview</li>
    <li>Visual Query Builder with 9 step subelements</li>
    <li>Network Monitor for request inspection</li>
  </ul>
]]></change-notes>
```

See the **writing-release-notes** skill for CHANGELOG.md and `change-notes` copy patterns.
