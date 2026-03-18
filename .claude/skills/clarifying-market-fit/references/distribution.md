# Distribution Reference

## Contents
- Distribution channels for this plugin
- JetBrains Marketplace requirements
- Internal ZIP distribution
- Plugin metadata that affects discoverability

---

## Distribution Channels

This plugin has two distribution paths:

| Channel | Artifact | Audience |
|---------|----------|----------|
| JetBrains Marketplace | `build/distributions/keyscript-intellij-plugin-2.0.0.zip` | Public; any IntelliJ user |
| Internal distribution | Same ZIP, shared directly | Keystone team users |

Build the distributable:

```bash
./gradlew buildPlugin
# Output: build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

---

## JetBrains Marketplace Requirements

### Plugin ID and versioning

`plugin.xml` must have a stable, unique plugin ID. NEVER change it after first publish — it breaks existing installations:

```xml
<!-- src/main/resources/META-INF/plugin.xml -->
<id>com.keyscript.plugin</id>          <!-- matches group in build.gradle.kts -->
<name>Keyscript IDE</name>
<version>2.0.0</version>
<vendor email="..." url="...">Keyscript</vendor>
```

### Build range and compatibility

```kotlin
// build.gradle.kts — keep untilBuild current or remove for open-ended support
ideaVersion {
    sinceBuild = "251"    // IntelliJ 2025.1
    untilBuild = "253.*"  // IntelliJ 2025.3.x — update each major release
}
```

NEVER set `untilBuild` to a past version. JetBrains Marketplace will mark the plugin as incompatible and stop serving it to users on newer IDE versions. Remove `untilBuild` entirely to support all future builds (risky for platform API stability).

### Marketplace review checklist

JetBrains reviews new plugins. Common rejection reasons:

- [ ] `<description>` contains only a feature list with no user value framing
- [ ] Plugin name is too generic (e.g., "Script Runner" — use "Keyscript IDE")
- [ ] No `<vendor>` element or missing email
- [ ] Screenshots show placeholder/empty state instead of working UI
- [ ] `sinceBuild`/`untilBuild` range is invalid or expired

---

## Internal ZIP Distribution

For team distribution without marketplace:

1. Build: `./gradlew buildPlugin`
2. Share `build/distributions/keyscript-intellij-plugin-{version}.zip`
3. Install in IntelliJ: `Settings > Plugins > ⚙ > Install Plugin from Disk...`

Include install instructions in the README for non-developer team members:

```markdown
## Installing from ZIP

1. Download `keyscript-intellij-plugin-2.0.0.zip`
2. Open IntelliJ IDEA
3. Go to **Settings > Plugins**
4. Click the **⚙** gear icon → **Install Plugin from Disk...**
5. Select the downloaded ZIP
6. Restart IntelliJ when prompted
```

---

## Plugin Metadata That Affects Discoverability

JetBrains Marketplace uses `<name>`, `<description>`, and tags for search. Since this plugin targets a narrow ICP (Keystone users), discoverability within the platform is less critical than clarity for users who already know they need it. Prioritize specificity over broad keyword coverage.

```xml
<!-- Avoid keyword stuffing — these will get the plugin flagged -->
<!-- BAD -->
<description>JavaScript IDE script runner deploy Keystone Keyscript IntelliJ plugin...</description>

<!-- GOOD — clear, honest, scoped -->
<description><![CDATA[
  <p>Purpose-built IDE for Keyscript development on Keystone servers.
  Requires a Keystone server instance to use.</p>
]]></description>
```

See the **gradle** skill for build configuration details around plugin packaging and version management.
