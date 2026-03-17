# Growth Engineering Reference

## Contents
- Growth Loops in Plugin Context
- Auto-Detection as Viral Hook
- Referral and Advocacy Patterns
- Retention Mechanics
- Anti-Patterns

---

## Growth Loops in Plugin Context

Traditional growth loops (invite-a-friend, share-on-social) don't apply to developer tools distributed via IDE Marketplace. Plugin growth comes from:

1. **Organic team spread** — one dev installs, others see it and install
2. **Project marker virality** — `.keyscript` files or `// @keyscript` headers in shared repos trigger auto-detection on collaborators' machines
3. **Marketplace search ranking** — review count and install velocity affect ranking
4. **Update quality** — compelling `change-notes` in `plugin.xml` retain existing users through upgrades

---

## Auto-Detection as Viral Hook

The auto-detection system in `KeyscryptProjectDetector` is the plugin's strongest organic growth mechanic. When a Keyscript file is committed to a shared repo, every team member who opens that project gets prompted to enable Keyscript support.

Current detection markers:
- `keyscript.bundle.json` at project root
- `.keyscript` marker file
- `*.keyscript.js` files
- `// @keyscript` in first 5 lines of any `.js` file

The `// @keyscript` header is the highest-leverage marker because it travels with individual scripts in any repo structure.

### Strengthen the detection prompt

When the plugin detects markers but isn't enabled for the project, the notification is the growth moment:

```kotlin
// In KeyscryptProjectDetector — make the prompt specific and actionable
private fun notifyDetected(project: Project, trigger: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Keyscript project detected",
            "Found $trigger — this looks like a Keystone script project. " +
            "<a href=\"enable\">Enable Keyscript IDE</a> for full tooling support.",
            NotificationType.INFORMATION
        )
        .setListener { notification, event ->
            if (event.description == "enable") {
                enableForProject(project)
                notification.expire()
                showQuickStartNotification(project)
            }
        }
        .notify(project)
}
```

The `trigger` parameter tells the user *why* it was detected — more specific = higher enable rate.

---

## Referral and Advocacy Patterns

### Pattern: "Open in Keyscript IDE" deep link in docs

Internal docs or Confluence pages can link directly to opening a script in the IDE. IntelliJ supports custom URI handlers:

```kotlin
// Register a URI handler in plugin.xml
// <applicationService serviceImplementation="com.keyscript.plugin.DeepLinkHandler"/>

// Handler opens the file and activates Keyscript support
class DeepLinkHandler : ApplicationService {
    // keyscript://open?file=/path/to/script.js
    fun handleUri(uri: URI) {
        val filePath = uri.queryParameters["file"] ?: return
        val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        FileEditorManager.getInstance(ProjectManager.getInstance().openProjects.first())
            .openFile(file, true)
    }
}
```

This turns internal documentation into an install funnel: team members who click the link and don't have the plugin get prompted to install.

### Pattern: Marketplace review prompt after first successful run

After a user's first successful deploy (highest-value action), prompt for a review:

```kotlin
// In DeploymentService — after confirmed first deploy
private fun maybePromptForReview(deployCount: Int) {
    if (deployCount == 1) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Keyscript IDE")
            .createNotification(
                "Enjoying Keyscript IDE?",
                "Leave a review on the JetBrains Marketplace — it helps other Keystone " +
                "developers find the plugin. <a href=\"review\">Rate now →</a>",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
```

Timing matters: prompt after value delivery (first deploy), not at install.

---

## Retention Mechanics

### Update changelog as retention tool

Users who see meaningful `change-notes` on update are more likely to stay on the current version and less likely to disable the plugin after updates that break expectations.

```xml
<!-- plugin.xml — change-notes format for maximum retention value -->
<change-notes><![CDATA[
<b>What's new in 2.1.0</b>
<ul>
  <li><b>Deploy confirmation</b> — see a diff before pushing to Keystone</li>
  <li><b>Query Builder export</b> — copy generated CR.XML directly to clipboard</li>
  <li>Fixed: Network Monitor truncating responses over 10KB</li>
</ul>
]]></change-notes>
```

See the **writing-release-notes** skill for full changelog copy patterns.

### Session heartbeat as silent retention mechanic

`SessionService` monitors session validity with a periodic heartbeat and auto-relogins when possible. This removes a major retention friction: users who get silently logged out and don't know how to re-authenticate often uninstall rather than troubleshoot.

Ensure the auto-relogin path shows a notification so users understand what happened:

```kotlin
// In SessionService — after auto-relogin
private fun onAutoReloginSuccess() {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Session renewed",
            "Your Keystone session was refreshed automatically.",
            NotificationType.INFORMATION
        )
        .notify(project)
}
```

---

## Anti-Patterns

### WARNING: Review prompt at install

```kotlin
// BAD — user has zero context for whether the plugin is good yet
class StartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (isFirstInstall()) showReviewPrompt()  // NEVER do this
    }
}
```

**Why This Breaks:** Prompting for a review before the user has derived value gets ignored or generates negative reviews from confused users. Always tie review prompts to value-delivery events (first run, first deploy).

### WARNING: Growth tactics that break silent projects

AVOID auto-creating notification group registrations or startup activities that fire for non-Keyscript projects. The plugin's zero-overhead contract (only activates for Keyscript projects) is what makes team adoption frictionless. Violating it causes IT to blocklist the plugin.

See the **orchestrating-feature-adoption** skill for safe feature introduction patterns that respect project scope.
