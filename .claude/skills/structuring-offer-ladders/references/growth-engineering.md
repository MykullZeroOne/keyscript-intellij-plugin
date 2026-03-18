# Growth Engineering Reference

## Contents
- Growth Levers in the Plugin
- Auto-Detection as a Growth Mechanic
- Virality via Shared Artifacts
- In-Plugin Referral Hooks
- Anti-Patterns

---

## Growth Levers in the Plugin

The plugin has two organic growth mechanics today:

1. **Auto-detection**: Any developer who clones a Keyscript project gets automatic plugin activation — no manual setup
2. **Shared scripts**: `*.keyscript.js` files with the `// @keyscript` marker are self-describing — new team members who open them get prompted to enable Keyscript support

These are the highest-value growth mechanics: zero friction, embedded in the artifact itself.

---

## Auto-Detection as a Growth Mechanic

The `KeyscriptProjectDetector` already handles this. Reinforce it in onboarding copy:

```kotlin
// Detection markers — document these prominently in README.md for viral spread
// Projects are auto-activated if they contain:
//   keyscript.bundle.json
//   .keyscript marker file
//   *.keyscript.js file
//   *.js file with "// @keyscript" in first 5 lines
```

The `// @keyscript` marker is the strongest growth mechanic: it's a one-line change to any existing JS file that causes the plugin to activate for every developer who opens that project.

**Promote this in README.md:**

```markdown
### Make any JS file a Keyscript file

Add `// @keyscript` to the first line of any `.js` file — the plugin detects it
automatically and activates for all team members who open the project.
```

---

## Virality via Shared Artifacts

The `keyscript.bundle.json` at the project root is a shared artifact that triggers plugin activation. Teams that commit this file propagate the plugin requirement:

```json
// keyscript.bundle.json — minimal form that triggers detection
{
  "name": "my-keyscript-project",
  "version": "1.0.0"
}
```

Add a note in the New Project wizard's generated README:

```markdown
This project includes `keyscript.bundle.json` — team members who open it in IntelliJ
will be prompted to install the Keyscript IDE plugin automatically.
```

The **KeyscryptModuleBuilder** generates this file in new projects. Verify it's in every template.

---

## In-Plugin Referral Hooks

When a user successfully runs a script for the first time, that's a high-satisfaction moment. Use it:

```kotlin
// In RunKeyscriptService — after first successful run
fun onFirstSuccessfulRun(project: Project) {
    val prefs = PropertiesComponent.getInstance(project)
    if (prefs.getBoolean("keyscript.sharedSuccessPromptShown", false)) return

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Script ran successfully!",
            "Share this project with your team — the Keyscript IDE plugin auto-activates " +
            "for anyone who opens it in IntelliJ.",
            NotificationType.INFORMATION
        )
        .notify(project)

    prefs.setValue("keyscript.sharedSuccessPromptShown", true)
}
```

Show this prompt once, not on every run. `PropertiesComponent.getInstance(project)` persists per-project.

---

## Anti-Patterns

### WARNING: Growth Messaging in Error States

**The Problem:**
```kotlin
// BAD — using an error notification to pitch sharing
NotificationGroupManager.getInstance()
    .getNotificationGroup("Keyscript IDE")
    .createNotification(
        "Script failed — but you can share this project with your team!",
        NotificationType.ERROR
    )
```

**Why This Breaks:** Users in error states are frustrated. Growth nudges during failures feel tone-deaf and erode trust. Growth moments must be high-satisfaction.

**The Fix:** Only trigger referral hooks on success events — first run, first deploy, first table query.

### WARNING: Gating the Auto-Detection

NEVER add a settings toggle that disables project auto-detection by default. The viral loop depends on detection being on for anyone who clones a Keyscript project. If you add a "require explicit opt-in" toggle, the growth mechanic dies.

---

See the **orchestrating-feature-adoption** skill for sequencing growth nudges within the user lifecycle.
See the **improving-activation-flow** skill for the full install-to-first-run funnel that feeds growth.
