# Content Copy Reference

## Contents
- Copy inventory: where user-visible text lives
- Voice and tone for Keystone developers
- Patterns for each surface type
- Anti-patterns to avoid

---

## Copy Inventory

All user-visible text in the plugin lives in these locations:

| Location | File(s) | Type |
|----------|---------|------|
| Plugin marketplace listing | `src/main/resources/META-INF/plugin.xml` | `<name>`, `<description>`, `<vendor>` |
| README (user-facing docs) | `README.md` | Hero, feature bullets, setup steps |
| Release notes | `CHANGELOG.md` | Version summaries, feature descriptions |
| Settings UI | `KeyscryptSettingsConfigurable.kt`, `KeyscriptProjectConfigurable.kt` | Field labels, section headers, tooltips |
| Tool window tab names | `plugin.xml` `<toolWindow>` declarations | Tab labels |
| In-plugin notifications | `NotificationGroupManager` calls throughout services | Notification titles and bodies |
| Empty states | Panel `createComponent()` methods in toolwindow/ | Label copy, CTA button text |
| Status bar | `LoginStatusBarWidget.kt` | Widget text |
| Action menu items | `plugin.xml` `<action>` elements | Action names, descriptions |

---

## Voice and Tone

The ICP is a **Keystone developer** using IntelliJ. They are technical, time-constrained, and switching from the Electron IDE. Write for them:

- **Direct**: "Run scripts without leaving IntelliJ" not "Experience a seamless IDE integration"
- **Specific**: Use "Keystone" and "Keyscript" by name; never substitute "server" or "backend"
- **Outcome-focused**: "Deploy in one click" not "Deployment functionality is available"
- **Terse**: Developers skip long explanations; front-load the value, put detail second

AVOID marketing adjectives with no substance: "powerful", "seamless", "robust", "modern". Every adjective must be backed by a concrete capability.

---

## Patterns by Surface

### plugin.xml — Marketplace short description

JetBrains truncates at ~160 characters in search results. The opening sentence is your only guaranteed impression:

```xml
<!-- src/main/resources/META-INF/plugin.xml -->
<name>Keyscript IDE</name>
<description><![CDATA[
  <p>Run, preview, and deploy Keystone scripts directly in IntelliJ IDEA —
  no separate Electron app required.</p>
  <p>Features:</p>
  <ul>
    <li>Embedded proxy with automatic Keystone session authentication</li>
    <li>JCEF live preview alongside your editor</li>
    <li>CR framework code completions with TypeScript definitions</li>
    <li>Data Tools: table browser, query builder, and search</li>
    <li>Diagnostics panel: console output and network monitor</li>
  </ul>
  <p><b>Requires a Keystone server instance.</b></p>
]]></description>
```

### Notification copy in services

Notifications surface through `NotificationGroupManager`. Match the severity level to the copy tone:

```kotlin
// GOOD — specific, actionable, names the cause
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript")
    .createNotification(
        "Keystone session expired",
        "Re-enter credentials in Settings > Keyscript IDE to continue.",
        NotificationType.WARNING
    ).notify(project)

// BAD — vague, no action path
.createNotification("Error", "Something went wrong.", NotificationType.ERROR)
```

### Action names in plugin.xml

Action names appear in menus and keybinding dialogs. Use verb + object format:

```xml
<!-- GOOD -->
<action id="Keyscript.RunScript" text="Run Keyscript" />
<action id="Keyscript.DeployScript" text="Deploy to Keystone" />
<action id="Keyscript.OpenInBrowser" text="Open Preview in Browser" />

<!-- BAD — noun-only, unclear what happens -->
<action id="Keyscript.Run" text="Script Run" />
<action id="Keyscript.Deploy" text="Deployment" />
```

### CHANGELOG.md version entries

Each version entry should open with a one-line summary scoped to user impact, then list specifics:

```markdown
## v2.0.0 — Native IntelliJ Keystone IDE

Full port from Electron. All core IDE capabilities now run inside IntelliJ with no
external app dependency.

### Added
- Embedded Ktor proxy with automatic JSESSIONID injection
- JCEF split-editor preview at `localhost:{port}/{instance}/Keyscript_IDE/RunScript`
- Data Tools panel: table browser, query builder, search tabs
- Diagnostics panel: console output and live network monitor
- CR framework code completions from TypeScript definitions

### Changed
- Session storage migrated from Electron safeStorage → IntelliJ PasswordSafe
```

---

## WARNING: Tech-Stack Copy in User-Facing Surfaces

**The Problem:**

```markdown
<!-- BAD — README feature list -->
- Uses Ktor CIO engine for lightweight proxy with no Netty dependency
- Jackson 2.17.2 serialization for API payloads
- JCEF for browser embedding
```

**Why This Breaks:**
1. Keystone developers don't choose plugins based on internal HTTP library choices
2. It shifts focus from user outcomes to implementation details
3. Developers scanning the README quickly conclude "this isn't for me"

**The Fix:**

```markdown
<!-- GOOD — same features, user-benefit framing -->
- Embedded proxy handles Keystone auth automatically — no browser config needed
- Live script preview updates as you run, identical to the Electron IDE experience
- Full network inspection in the Diagnostics panel
```

See the **writing-release-notes** skill for detailed CHANGELOG conventions.
See the **designing-inapp-guidance** skill for tooltip and balloon copy patterns.
