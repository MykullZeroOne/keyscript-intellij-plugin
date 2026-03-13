# Content Copy Reference

## Contents
- Where Tier Copy Lives
- README.md Tier Framing
- plugin.xml Marketplace Description
- In-App Label Copy
- Anti-Patterns

---

## Where Tier Copy Lives

| Surface | File | Audience |
|---------|------|----------|
| JetBrains Marketplace listing | `README.md` (first ~300 chars) | Developers browsing the Marketplace |
| Plugin descriptor short description | `src/main/resources/META-INF/plugin.xml` | Marketplace search results |
| Settings panel labels | `KeyscryptSettingsConfigurable` UI | Authenticated users configuring the plugin |
| Tool window headers & tooltips | `*Panel.kt` files | Active users in the IDE |
| Empty states | `*Panel.kt` tier-gated panels | Users who hit a tier wall |

---

## README.md Tier Framing

The README opener sets the value expectation. Frame what works everywhere first, then flag what requires Ultimate — don't bury the upgrade hook.

```markdown
# Keyscript IDE — IntelliJ Plugin

Development environment for Keystone scripts, ported from the original Electron-based Keyscript IDE.
Provides JCEF preview¹, embedded proxy, authentication, script parameters, CR framework completions,
and developer tools — all inside IntelliJ IDEA.

> ¹ JCEF split-editor preview requires IntelliJ IDEA Ultimate.

| Feature | Community | Ultimate |
|---------|-----------|---------|
| CR framework completions (`CR.XML`, `CR.Core`, etc.) | ✅ | ✅ |
| Run gutter icon + `Ctrl+Shift+F10` shortcut | ✅ | ✅ |
| Workspace, Data Tools, Diagnostics panels | ✅ | ✅ |
| JCEF split-editor live preview | — | ✅ |
| JavaScript language services (type checking, refactoring) | — | ✅ |
```

---

## plugin.xml Marketplace Description

The `<description>` field in `plugin.xml` is plain text shown in search results. Lead with the job-to-be-done, not feature names.

```xml
<!-- src/main/resources/META-INF/plugin.xml -->
<description><![CDATA[
<p>Build and run Keystone scripts without leaving IntelliJ IDEA.</p>
<ul>
  <li>CR framework code completions (CR.XML, CR.Core, CR.Login, CR.Script, Ext.*)</li>
  <li>Run scripts with a gutter icon or Ctrl+Shift+F10</li>
  <li>Browse Keystone tables, build queries visually, and inspect network traffic</li>
  <li>Split-editor browser preview (IntelliJ IDEA Ultimate)</li>
</ul>
<p>Works with IntelliJ IDEA Community and Ultimate — some features require Ultimate.</p>
]]></description>
```

NEVER write "requires Ultimate" without also listing what works without it. Users scan for the minimum viable tier first.

---

## In-App Label Copy

Settings panel labels should describe the field's effect, not its technical name.

```kotlin
// BAD — technical label that says nothing about purpose
JLabel("keystoneServer")

// GOOD — describes the effect, sets expectation
JLabel("Keystone Server URL")
// with tooltip:
component.toolTipText = "e.g. keystonedev.example.com:8443 — the server your scripts connect to"
```

Instance selector label copy should reinforce the ladder:

```kotlin
// In KeyscryptSettingsConfigurable — instance list label
JLabel("Supported Instances (Development → Test → Production)")
```

---

## Anti-Patterns

### WARNING: Feature Name as Copy

**The Problem:**
```
"JCEF Preview — Not Available"
```

**Why This Breaks:** "JCEF" means nothing to a Keystone developer. They don't know what they're missing or why they should care.

**The Fix:**
```
"Live script preview is available in IntelliJ IDEA Ultimate.
Run your script and see results instantly, side-by-side with your code."
```

Lead with the outcome, not the technology.

### WARNING: Passive Tier Messaging

**The Problem:**
```markdown
Note: Some features may not be available in Community Edition.
```

**Why This Breaks:** "May not be available" is too vague to motivate action. Users assume everything just works and file bugs when it doesn't.

**The Fix:**
```markdown
> **IntelliJ IDEA Ultimate** adds live preview and JavaScript language services.
> All other features work in Community Edition.
```

Be specific about what changes and in which direction (more features, not "limited features").

---

See the **clarifying-market-fit** skill for ICP-driven copy decisions before editing README.md or plugin.xml.
See the **writing-release-notes** skill for CHANGELOG.md tier-framing when shipping new gated features.
