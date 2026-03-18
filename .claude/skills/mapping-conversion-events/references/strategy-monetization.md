# Strategy & Monetization Reference

## Contents
- Freemium vs. paid plugin models
- Feature gating for Keyscript IDE
- Keystone instance tiers as a value ladder
- Conversion signals for upgrade prompts
- Anti-patterns

Keyscript IDE serves developers at companies using Keystone. Monetization follows B2B tooling patterns: the plugin is free, Keystone server licensing is the revenue event. The plugin's role is to maximize developer activation and retention — which drives Keystone seat renewals.

---

## Monetization Model

Keyscript IDE is a **companion tool**, not a standalone paid product. Revenue flows through:

```
Plugin (free) → Developer adoption → Keystone usage → Keystone seat license (paid)
```

This means conversion optimization for the plugin = reducing churn risk for Keystone licenses. Every developer who fully activates the plugin is a Keystone license retention signal.

---

## Feature Gating Strategy

Recommended tier split:

| Feature | Community (free) | Ultimate (paid/licensed) |
|---------|-----------------|--------------------------|
| Run scripts | ✓ | ✓ |
| JCEF preview | — | ✓ (requires IDEA Ultimate) |
| Data Tools (Table Browser, Query Builder) | ✓ | ✓ |
| Deploy to Production | — | ✓ (gate on instance name) |
| Advanced completions | — | ✓ |

Gate Production deploys by checking instance name:

```kotlin
// DeploymentService.kt
fun deploy(instance: String) {
    if (instance == "Production" && !isUltimateLicensed()) {
        showUpgradePrompt("Deploy to Production requires Keyscript Ultimate")
        return
    }
    // ... proceed with deploy
}

private fun isUltimateLicensed(): Boolean {
    // Check IDE edition or a license key in KeyscryptSettings
    return ApplicationInfo.getInstance().build.isEAP ||
           KeyscryptSettings.getInstance().licenseKey.isNotBlank()
}
```

---

## Keystone Instance Tiers as Value Ladder

The `supportedInstances` setting (Development, Test, Production) maps naturally to a value ladder:

```
Development  →  "Try it out" (zero friction)
Test         →  "It works, now validate" (team-visible)
Production   →  "It's real, protect it" (high-value action)
```

Upgrade prompts should appear at the Production gate — that's the moment of highest intent.

```kotlin
// Upgrade prompt pattern
private fun showUpgradePrompt(reason: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification("Upgrade to Keyscript Ultimate", reason, NotificationType.INFORMATION)
        .addAction(object : AnAction("Learn More") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowserUtil.browse("https://keyscript.com/ultimate")
            }
        })
        .notify(project)
}
```

---

## Conversion Signals → Upgrade Triggers

| Signal | Upgrade Trigger |
|--------|----------------|
| 3+ deploys to Test | Prompt for Production access |
| JCEF preview used on Community IDEA | Prompt to upgrade IDEA edition |
| `supportedInstances` includes Production but user has no license | Show gate on first Production deploy attempt |
| Session active for 14+ days | Show "You're a power user" retention message |

---

## Anti-Pattern: Gating Core Developer Workflow

**The Problem:**
Gating `Run Script` or core completions behind a license key. Developers will uninstall immediately.

**Why This Breaks:**
Developer tools must demonstrate value before asking for money. A gated run button means users never reach the aha moment.

**The Fix:**
Gate only on high-value, boundary-crossing actions (Production deploy, advanced reporting) — not on the core development loop.

See the **structuring-offer-ladders** skill for tier messaging and value ladder design.
See the **clarifying-market-fit** skill for ICP definition and positioning.
