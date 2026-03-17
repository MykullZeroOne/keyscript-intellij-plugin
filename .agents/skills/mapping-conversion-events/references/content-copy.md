# Content Copy Reference

## Contents
- JetBrains Marketplace listing copy
- In-plugin copy surfaces
- Status messages and notifications
- Error copy standards
- Anti-patterns

Keyscript IDE's conversion copy lives in three places: the JetBrains Marketplace listing, in-plugin UI strings (tool window labels, status bar, notifications), and plugin.xml metadata. Copy drives installs and first-run completion.

---

## JetBrains Marketplace Listing

Marketplace copy is defined in `plugin.xml` and the Gradle `pluginConfiguration` block.

```kotlin
// build.gradle.kts
intellijPlatform {
    pluginConfiguration {
        name = "Keyscript IDE"
        // description and change-notes are in plugin.xml
    }
}
```

```xml
<!-- META-INF/plugin.xml -->
<description><![CDATA[
  <p>Full IDE experience for Keystone script development — directly in IntelliJ IDEA.</p>
  <ul>
    <li>Live browser preview via JCEF split editor</li>
    <li>CR framework code completions and live templates</li>
    <li>Integrated table browser and query builder for Keystone data</li>
    <li>One-click deploy to Development, Test, or Production instances</li>
    <li>Automatic session management with secure credential storage</li>
  </ul>
  <p>Replaces the standalone Keyscript Electron IDE — all features, zero context switching.</p>
]]></description>
```

### Marketplace Copy Rules

- Lead with **workflow outcome**, not feature list: "develop scripts faster" > "provides completions"
- Screenshot captions are shown in search results — make them scannable
- `change-notes` in plugin.xml appear on every update notification — keep them user-facing, not technical

---

## In-Plugin Copy Surfaces

### Status Bar Widget

```kotlin
// LoginStatusBarWidget.kt
object Copy {
    const val LOGGED_OUT = "KS: Click to connect"
    const val LOGGED_IN = "KS: %s"          // interpolate username
    const val CONNECTING = "KS: Connecting…"
    const val ERROR = "KS: Connection error" // tooltip explains detail
}
```

### Notification Balloons

```kotlin
// Standard pattern across services
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Keyscript IDE",
        "Connected to ${settings.keystoneServer} as $username",
        NotificationType.INFORMATION
    ).notify(project)
```

**Copy guidelines for notifications:**
- Success: "Connected to {server}" — specific, confirms what happened
- Error: "Could not connect — check server address in Settings > Keyscript IDE" — tells them where to fix it
- NEVER: "An error occurred." — useless, no action path

### Tool Window Labels

| Surface | Current Label | Recommended Label |
|---------|--------------|-------------------|
| Workspace tab | "Session" | "Session & Status" |
| Run button | "Run" | "Run Script" |
| Deploy action | "Deploy" | "Deploy to {instance}" |

---

## Error Copy Standards

```kotlin
// BAD — generic
throw RuntimeException("Connection failed")

// GOOD — actionable
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Proxy Failed to Start",
        "Port ${settings.proxyPort} is in use. Change it in Settings > Keyscript IDE.",
        NotificationType.ERROR
    ).notify(project)
```

**Template:** `[What failed] — [Why it failed] — [Where to fix it]`

---

## Anti-Pattern: Technical Jargon in User-Facing Strings

**The Problem:**
```kotlin
// BAD — exposes internal term, meaningless to users
"JSESSIONID injection failed"
```

**The Fix:**
```kotlin
// GOOD — describes the user experience
"Session expired — please log in again"
```

See the **designing-inapp-guidance** skill for notification placement and timing.
