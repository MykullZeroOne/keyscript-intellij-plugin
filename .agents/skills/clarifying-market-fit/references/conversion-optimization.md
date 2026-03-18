# Conversion Optimization Reference

## Contents
- Conversion surfaces in this plugin
- Copy patterns that convert
- Anti-patterns that repel the ICP
- Iteration workflow

---

## Conversion Surfaces in This Plugin

"Conversion" for an IDE plugin means: install → first script run → regular use → team adoption. Each surface has a specific moment of truth:

| Surface | File | Conversion Goal |
|---------|------|----------------|
| Marketplace listing | `src/main/resources/META-INF/plugin.xml` `<description>` | Install click |
| README hero | `README.md` | Developer shares with team |
| First-run empty state | `WorkspacePanel`, `KeyscriptDataToolsToolWindowFactory` | User completes setup |
| Settings panel labels | `KeyscriptSettingsConfigurable` | User enters correct config |
| Status bar widget | `LoginStatusBarWidget` | User logs in |

---

## Copy Patterns That Convert

### 1. Outcome-first description in plugin.xml

The JetBrains Marketplace shows the first ~160 characters of `<description>`. Lead with the workflow outcome:

```xml
<!-- src/main/resources/META-INF/plugin.xml -->
<description><![CDATA[
  <p><b>Keyscript IDE</b> — run, preview, and deploy Keystone scripts without leaving IntelliJ.</p>
  <p>The plugin embeds a proxy server that handles Keystone authentication automatically,
  so your JCEF preview and CR framework AJAX calls work exactly as in the Electron IDE.</p>
  <ul>
    <li>Live browser preview alongside your editor (JCEF)</li>
    <li>CR framework code completions with TypeScript definitions</li>
    <li>Integrated Data Tools: table browser, query builder, search</li>
    <li>Deploy scripts directly from the IDE</li>
  </ul>
  <p>Requires access to a Keystone server instance.</p>
]]></description>
```

### 2. Settings label copy that reduces friction

Vague labels cause users to enter wrong values and abandon setup. Be specific:

```kotlin
// GOOD — KeyscryptSettingsConfigurable.kt
row("Keystone Server (host:port):") {
    textField()
        .bindText(settings::keystoneServer)
        .comment("e.g. keystonedev.example.com:8443 — no https:// prefix")
}

// BAD — forces user to guess format
row("Server:") { textField().bindText(settings::keystoneServer) }
```

### 3. Status bar widget text that prompts action

```kotlin
// LoginStatusBarWidget.kt — text should name the action, not the state
fun getText(): String = when {
    isLoggedIn -> "KS: ${username}"
    else -> "KS: Click to Log In"   // action-oriented
    // NOT: "KS: Not Logged In"     // describes state but doesn't invite action
}
```

---

## WARNING: Generic Copy Repels the ICP

**The Problem:**

```markdown
<!-- BAD — README.md hero that attracts the wrong audience -->
# Keyscript IDE Plugin
A powerful JavaScript IDE extension for IntelliJ IDEA with code completions,
live preview, and deployment tools.
```

**Why This Breaks:**
1. "JavaScript IDE extension" attracts generic JS developers who will install, find nothing useful, and leave a low rating
2. It undersells the Keystone-specific value (proxy auth, CR framework, session management)
3. JetBrains search ranking degrades with high uninstall rates from wrong-fit users

**The Fix:**

```markdown
<!-- GOOD — scoped to Keystone developers immediately -->
# Keyscript IDE Plugin
Run and deploy Keystone scripts directly in IntelliJ IDEA.
Replaces the standalone Electron IDE with a fully integrated experience —
same CR framework completions, same proxy-based preview, zero CORS setup.
```

---

## Iteration Workflow

1. Edit copy in target file
2. Validate ICP alignment: does every sentence assume the reader is a Keystone developer?
3. Check first 160 characters of plugin.xml description render correctly in marketplace preview
4. If rewriting empty-state copy, cross-check with the **crafting-empty-states** skill
5. If rewriting onboarding flow copy, cross-check with the **designing-onboarding-paths** skill

```bash
# Count characters in plugin.xml short description (first paragraph)
grep -A1 '<description>' src/main/resources/META-INF/plugin.xml | head -2
```
