# Content Copy Reference

## Contents
- Copy Surfaces Inventory
- Marketplace Description Patterns
- README Structure
- In-Plugin Copy Rules
- Anti-Patterns

---

## Copy Surfaces Inventory

| Surface | Location | Character Limit | Audience |
|---------|----------|-----------------|----------|
| Plugin name | `plugin.xml` `<name>` | ~30 | Marketplace search results |
| Marketplace description | `plugin.xml` `<description>` | No limit (HTML) | Evaluating devs |
| README hero | `README.md` lines 1-5 | ~80 chars/line | GitHub + internal docs |
| README feature list | `README.md` `## Features` | Per bullet | Technical evaluators |
| Settings field labels | `KeyscryptSettingsConfigurable.kt` | ~25 chars | Configured users |
| Settings placeholder text | UI component creation | ~40 chars | First-time configurers |
| Status bar widget | `LoginStatusBarWidget.kt` | ~25 chars | All authenticated users |
| Tool window tab names | `plugin.xml` toolWindow `id` | ~20 chars | Daily users |
| Notification titles | Service classes | ~60 chars | Error/event states |

---

## Marketplace Description Patterns

The `plugin.xml` `<description>` field renders as HTML on the JetBrains Marketplace. Plain text paragraphs perform worse than structured lists because engineers scan rather than read.

### DO: Lead with the pain, then the solution

```xml
<!-- plugin.xml -->
<description><![CDATA[
<p>Keystone script development belongs in your IDE — not in a legacy Electron app.</p>

<p><b>Keyscript IDE</b> brings the full Keystone development workflow into IntelliJ IDEA:</p>
<ul>
  <li><b>Split-editor JCEF preview</b> — see script output alongside your code, not in a separate window</li>
  <li><b>CR framework completions</b> — CR.XML, CR.Core, CR.Login, CR.Script, Ext.* with type inference</li>
  <li><b>Embedded proxy with session injection</b> — scripts authenticate against real Keystone data automatically</li>
  <li><b>Visual Query Builder</b> — construct Corelation XML queries with tree UI, then export to CR.XML JavaScript</li>
  <li><b>Table Browser</b> — explore Keystone tables, column metadata, and generate CRUD templates</li>
  <li><b>Network Monitor</b> — inspect every HTTP request your script makes</li>
</ul>

<p><b>Requirements:</b> IntelliJ IDEA 2025.1+ · Keystone server access</p>
]]></description>
```

### DON'T: Feature dump without hierarchy

```xml
<!-- BAD — no entry point, no pain statement, no structure -->
<description><![CDATA[
Development environment for Keystone scripts. Provides JCEF preview, embedded proxy,
authentication, script parameters, CR framework completions, and developer tools.
]]></description>
```

**Why this fails:** The current description could describe any developer tool. "Keystone scripts" is jargon with no explanation. There's no reason to install.

---

## README Structure

The README is both documentation and a landing page. Structure it for two audiences: evaluators (top of file) and users (bottom of file).

```markdown
# Keyscript IDE — IntelliJ Plugin
<!-- ONE sentence: what it is + for whom -->
Full Keystone script development inside IntelliJ IDEA — JCEF preview, CR framework completions,
embedded proxy, and integrated Keystone data tools.

## What You Get
<!-- 3-4 bullets max; outcomes not features -->
- **Edit → Preview → Run** in a single split view, no browser switching
- **Type-aware CR completions** across CR.XML, CR.Core, CR.Login, CR.Script
- **Query Builder** generates production-ready CR.XML queries from a visual tree
- **Network Monitor** shows every request your script makes to Keystone

## Requirements
...

## Quick Start
...
```

The current README starts with "Development environment for Keystone scripts, ported from..." — that's provenance, not value. Move it to a "Background" or "About" section below the fold.

---

## In-Plugin Copy Rules

### Settings field labels

Labels must be noun phrases, not imperative verbs. Placeholder text carries the format hint.

```kotlin
// GOOD — label is a noun, placeholder shows expected format
val serverField = JTextField().apply {
    emptyText.text = "keystonedev.example.com:8443"
}
add(JLabel("Keystone Server"), serverField)

// BAD — label is a prompt, not a label; placeholder is generic
val serverField = JTextField().apply {
    emptyText.text = "Enter server address..."
}
add(JLabel("Enter your server:"), serverField)
```

### Notification copy

All in-plugin notifications follow: **[What happened] + [What to do]**

```kotlin
// GOOD — states result, gives action
"Session expired. Click here to re-authenticate."

// GOOD — states problem, gives resolution path
"Deploy failed: script '$scriptName' not found in bundle. Check keyscript.bundle.json."

// BAD — states problem, no next step
"Authentication error."

// BAD — too casual, no specific context
"Something went wrong. Try again."
```

### Tool window tab names

The three tool windows use IDs that become visible tab text:

```xml
<!-- plugin.xml — current -->
<toolWindow id="Keyscript Workspace" .../>
<toolWindow id="Keyscript Data Tools" .../>
<toolWindow id="Keyscript Diagnostics" .../>
```

"Keyscript" prefix on all three is redundant when they're visually grouped. Shorter is cleaner:

```xml
<!-- Consider for future iteration -->
<toolWindow id="Workspace" .../>
<toolWindow id="Data Tools" .../>
<toolWindow id="Diagnostics" .../>
```

Only do this if the plugin has a dedicated tool window stripe icon — otherwise the "Keyscript" prefix aids discoverability in the window switcher.

---

## Anti-Patterns

### WARNING: Jargon without anchoring

**The Problem:** Copy that assumes the reader knows what "Keystone," "CR framework," and "JSESSIONID" mean.

**Why This Breaks:** Your Marketplace listing must convert evaluators who are considering adopting the tool. Even existing Keystone devs from other editors need to understand what they're getting.

**The Fix:** One-line anchors before jargon:

```markdown
<!-- DON'T -->
Provides CR.XML completions and JSESSIONID injection via embedded proxy.

<!-- DO -->
The CR framework (Keystone's JavaScript API layer) gets full type-aware completions.
Session cookies are injected automatically — no manual authentication in scripts.
```

### WARNING: Feature-first vs. outcome-first hierarchy

The README `## Features` section lists tools (Split editor, Run gutter, CR completions) before outcomes. Engineers evaluate tools by asking "what problem does this solve?" not "what features does it have?"

Restructure to lead with the workflow outcome:

```markdown
<!-- OUTCOME-FIRST (DO) -->
### Edit, Preview, Run — Without Switching Windows
Split-editor view shows your script and live Keystone output side by side.
Press Ctrl+Shift+F10 or click the gutter play icon to run.

<!-- FEATURE-FIRST (DON'T) -->
### Split editor preview
JCEF browser panel alongside your code
```

See the **clarifying-market-fit** skill for full ICP and positioning guidance before rewriting hero copy.
