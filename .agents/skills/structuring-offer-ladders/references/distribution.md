# Distribution Reference

## Contents
- Distribution Channels
- JetBrains Marketplace Packaging
- Internal Distribution (Manual Install)
- Tier Messaging by Channel
- Checklist

---

## Distribution Channels

Keyscript IDE has two distribution paths:

| Channel | Mechanism | Audience |
|---------|-----------|----------|
| JetBrains Marketplace | `./gradlew publishPlugin` (requires token) | Public / external Keystone developers |
| Manual ZIP install | `./gradlew buildPlugin` → distribute `.zip` | Internal teams, pre-release testers |

Each channel reaches a different segment of the tier ladder. Marketplace installs start at the "unknown" tier (no Keystone access yet); internal installs usually land in Development-instance users.

---

## JetBrains Marketplace Packaging

### Build the Distribution Artifact

```bash
# Creates build/distributions/keyscript-intellij-plugin-2.0.0.zip
./gradlew buildPlugin
```

### Verify the ZIP Before Publishing

```bash
# Inspect the artifact — confirms all JARs and resources are included
unzip -l build/distributions/keyscript-intellij-plugin-2.0.0.zip | grep -E "(jar|xml|js)"
```

### Version Bump Workflow

Copy this checklist before any release:
- [ ] Update `version` in `build.gradle.kts`
- [ ] Update `version` in `intellijPlatform > pluginConfiguration` block
- [ ] Update `CHANGELOG.md` with new tier features prominently listed
- [ ] Run `./gradlew buildPlugin` — no errors
- [ ] Install ZIP in a local IDE to smoke-test tier gating
- [ ] Publish to Marketplace

---

## Internal Distribution (Manual ZIP Install)

```bash
# Build
./gradlew buildPlugin

# Share the ZIP at:
build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

Recipients install via **Settings > Plugins > Install Plugin from Disk**.

For internal distribution, include a README snippet that describes the instance ladder (Dev/Test/Prod) so recipients know which tier they're being onboarded to.

---

## Tier Messaging by Channel

**Marketplace listing** (public, unknown tier):
- Lead with what works in Community; footnote Ultimate features
- Never assume the reader has a Keystone server yet — link to Keystone docs

**Internal ZIP distribution** (known tier, likely Dev):
- Skip the "what is Keystone" explanation
- Tell them which instance to configure: `keystonedev.example.com:8443`
- Remind them that Production deployments require the Production instance

**CHANGELOG.md** (existing users, upgrading):
- Note when a feature moves from Ultimate-only to Community
- Note when a new tier wall is added (e.g., "Production deploy now requires confirming instance")

---

## Validate Tier Build Configuration

```kotlin
// build.gradle.kts — confirmed compatibility range
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"   // IDEA 2025.1 (first build with required platform APIs)
            untilBuild = "253.*" // Keep updated to avoid Marketplace rejection
        }
    }
}
```

NEVER let `untilBuild` expire without updating — the Marketplace will reject the plugin and active users lose auto-updates.

---

See the **gradle** skill for build task details and dependency configuration.
See the **writing-release-notes** skill for CHANGELOG.md copy that reinforces the tier narrative.
