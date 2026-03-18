# Growth Engineering Reference

## Contents
- Viral/advocacy loops for a developer tool
- In-plugin sharing surfaces
- Community and ecosystem signals
- Referral and discovery patterns
- Anti-patterns

Keyscript IDE grows through developer-to-developer recommendation. The primary loop is: developer uses plugin → gets value (faster script iteration) → shares with team → team installs. Engineering growth means instrumenting and amplifying that loop.

---

## The Core Loop

```
Install → Activate (first run) → Aha moment (preview + deploy working) → Share with team
```

The aha moment is the first time a script runs, the JCEF preview loads, and the result is visible — all in the IDE without switching to a browser or terminal. Engineering must make this moment fast and reliable.

---

## In-Plugin Sharing Surfaces

### Post-Deploy Share Prompt

After a successful deploy, prompt the user to share the plugin with their team:

```kotlin
// DeploymentService.kt — after deploy success
private var deployCount = 0

fun onDeploySuccess(instance: String) {
    deployCount++
    if (deployCount == 3) {  // trigger after 3 successful deploys
        showSharePrompt()
    }
}

private fun showSharePrompt() {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "Enjoying Keyscript IDE?",
            "Share the plugin with your team.",
            NotificationType.INFORMATION
        )
        .addAction(object : AnAction("Copy Marketplace Link") {
            override fun actionPerformed(e: AnActionEvent) {
                CopyPasteManager.getInstance().setContents(
                    StringSelection("https://plugins.jetbrains.com/plugin/keyscript-ide"))
                deployCount = -999  // suppress future prompts
            }
        })
        .notify(project)
}
```

### "Open in Browser" as a Share Vector

The `OpenInBrowserAction` already opens the running script in a system browser. Add clipboard copy of the Keystone URL as a secondary action — useful for sharing a running script with a stakeholder:

```kotlin
// OpenInBrowserAction.kt
override fun actionPerformed(e: AnActionEvent) {
    val url = buildKeystoneUrl(project)
    BrowserUtil.browse(url)
    // Secondary: copy to clipboard
    CopyPasteManager.getInstance().setContents(StringSelection(url))
    showHint(e.project, "URL copied to clipboard")
}
```

---

## Community Signals

Growth for a JetBrains Marketplace plugin is measured by:

| Signal | Source | Target |
|--------|--------|--------|
| Install count | Marketplace dashboard | — |
| Rating | Marketplace reviews | ≥ 4.5 stars |
| GitHub stars | Repository | — |
| Error reports | IDE exception reporter | < 1% crash rate |

Prompt for Marketplace reviews after the aha moment (3+ successful deploys):

```kotlin
// trigger review prompt — IDE will handle rate limiting
ReviewManager.getInstance().requestReview(project)
// Available in IntelliJ Platform since 2023.1
```

---

## DO / DON'T

| | Pattern | Why |
|-|---------|-----|
| DO | Trigger share prompt after proven value (3 deploys) | Asking before value = ignored; after = genuine enthusiasm |
| DON'T | Show share prompts on first run | User hasn't validated value yet; creates friction |
| DO | Track team-level install patterns (multiple users, same `keystoneServer`) | Signals organic team spread |
| DON'T | Gate any core feature behind sharing or rating | Dark pattern; damages trust with developer users |

---

## Anti-Pattern: Nagging for Reviews

**The Problem:**
Showing a review prompt on every project open until dismissed.

**Why This Breaks:**
Developers will leave 1-star reviews specifically because of nagging. It's a well-documented developer-tool anti-pattern.

**The Fix:**
Fire the prompt once, after the third deploy, and never again. Persist the suppression flag in `KeyscryptSettings`:

```kotlin
var hasRequestedReview: Boolean = false  // in KeyscryptSettings
```

See the **orchestrating-feature-adoption** skill for timing and sequencing in-product nudges.
